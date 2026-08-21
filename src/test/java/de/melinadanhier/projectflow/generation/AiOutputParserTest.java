package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.client.AiOutputValidationException;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedElementOrigin;
import de.melinadanhier.projectflow.generation.parser.GenerationResponseParser;
import de.melinadanhier.projectflow.generation.parser.PreCheckResponseParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AiOutputParserTest {

    @Autowired
    private PreCheckResponseParser preCheckParser;

    @Autowired
    private GenerationResponseParser generationParser;

    @Test
    void parsesPreCheckWithoutProblems() {
        var result = preCheckParser.parse("{\"schemaVersion\":\"1.0\",\"problems\":[]}");

        assertThat(result.problems()).isEmpty();
        assertThat(result.hasPlausibilityIssues()).isFalse();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void parsesWarningWithActionableSuggestion() {
        var result = preCheckParser.parse(problemJson("WARNING", "Zeitraum ist knapp."));

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(AiPreCheckSeverity.WARNING);
            assertThat(problem.suggestion()).isEqualTo("Plane mehr Zeit ein.");
        });
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void parsesErrorAsBusinessProblemRatherThanTechnicalFailure() {
        var result = preCheckParser.parse(problemJson("ERROR", "Das Ziel ist in einem Tag nicht erreichbar."));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.problems()).singleElement()
                .extracting("severity").isEqualTo(AiPreCheckSeverity.ERROR);
    }

    @Test
    void parsesMultipleProblems() {
        var result = preCheckParser.parse("""
                {"schemaVersion":"1.0","problems":[
                  {"severity":"WARNING","message":"Knapp.","suggestion":"Mehr Zeit einplanen."},
                  {"severity":"ERROR","message":"Ziel widerspricht der Frist.","suggestion":"Ziel reduzieren."}
                ]}
                """);

        assertThat(result.problems()).hasSize(2);
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsUnknownSeverityAsTechnicalOutputFailure() {
        assertThatThrownBy(() -> preCheckParser.parse(problemJson("INFO", "Hinweis")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsUnsupportedPreCheckSchemaVersion() {
        assertThatThrownBy(() -> preCheckParser.parse("{\"schemaVersion\":\"2.0\",\"problems\":[]}"))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("Schemaversion");
    }

    @Test
    void rejectsMalformedAndIncompletePreCheckJsonAsTechnicalOutputFailures() {
        assertThatThrownBy(() -> preCheckParser.parse("{not-json"))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> preCheckParser.parse("""
                {"schemaVersion":"1.0","problems":[{"severity":"ERROR","message":"Fehler"}]}
                """))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void parsesGenerationWithTemporaryIdsAndBothOrigins() {
        var result = generationParser.parse(validGenerationJson());

        assertThat(result.schemaVersion()).isEqualTo("1.0");
        assertThat(result.phases()).singleElement().satisfies(phase -> {
            assertThat(phase.tempId()).isEqualTo("phase-1");
            assertThat(phase.tasks()).extracting("tempId")
                    .containsExactly("task-1", "task-2");
            assertThat(phase.tasks()).extracting("origin")
                    .containsExactly(GeneratedElementOrigin.USER_INPUT, GeneratedElementOrigin.AI_INFERRED);
            assertThat(phase.milestones()).singleElement()
                    .extracting("tempId").isEqualTo("milestone-1");
        });
    }

    @Test
    void rejectsMissingAndInvalidGenerationFields() {
        assertThatThrownBy(() -> generationParser.parse(validGenerationJson().replace(
                "\"title\":\"Umzugskartons packen\",", "")))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> generationParser.parse(validGenerationJson().replace(
                "\"origin\":\"USER_INPUT\"", "\"origin\":\"TEMPLATE\"")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsDuplicateTemporaryIds() {
        assertThatThrownBy(() -> generationParser.parse(validGenerationJson().replace(
                "\"tempId\":\"task-2\"", "\"tempId\":\"task-1\"")))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("temporäre ID");
    }

    @Test
    void rejectsApplicationManagedReviewStateAndExistingProjectData() {
        assertThatThrownBy(() -> generationParser.parse(validGenerationJson().replace(
                "\"schemaVersion\":\"1.0\",",
                "\"schemaVersion\":\"1.0\",\"projectTitle\":\"Nicht übernehmen\",")))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> generationParser.parse(validGenerationJson().replace(
                "\"origin\":\"USER_INPUT\",",
                "\"origin\":\"USER_INPUT\",\"reviewed\":true,")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    private String problemJson(String severity, String message) {
        return """
                {"schemaVersion":"1.0","problems":[{
                  "severity":"%s","message":"%s","suggestion":"Plane mehr Zeit ein."
                }]}
                """.formatted(severity, message);
    }

    private String validGenerationJson() {
        return """
                {
                  "schemaVersion":"1.0",
                  "metadata":{"summary":"Beispielplan","assumptions":[]},
                  "phases":[{
                    "tempId":"phase-1","title":"Vorbereitung","description":"Alles vorbereiten","order":1,
                    "tasks":[
                      {"tempId":"task-1","title":"Umzugskartons packen","description":"Zimmerweise packen",
                       "estimatedHours":4,"startDate":"2026-08-25","dueDate":"2026-08-26",
                       "criticalAssumption":"Kartons sind vorhanden","origin":"USER_INPUT","order":1},
                      {"tempId":"task-2","title":"Transport organisieren","description":"Fahrzeug reservieren",
                       "estimatedHours":2,"startDate":"2026-08-25","dueDate":"2026-08-26",
                       "origin":"AI_INFERRED","order":2}
                    ],
                    "milestones":[{"tempId":"milestone-1","title":"Vorbereitung abgeschlossen",
                                   "date":"2026-08-27","order":1}]
                  }]
                }
                """;
    }
}
