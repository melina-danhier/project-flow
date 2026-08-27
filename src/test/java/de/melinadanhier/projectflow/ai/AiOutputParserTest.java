package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedElementOrigin;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.parser.generation.GeneratedPlanResponseParser;
import de.melinadanhier.projectflow.ai.parser.precheck.PreCheckResponseParser;
import de.melinadanhier.projectflow.ai.validation.precheck.PreCheckResultValidator;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.common.exception.GenerationException;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AiOutputParserTest {

    @Autowired
    private PreCheckResponseParser preCheckParser;

    @Autowired
    private GeneratedPlanResponseParser generationParser;

    @Autowired
    private PreCheckResultValidator preCheckValidator;

    @Autowired
    private AiWorkflowPayloadCodec snapshotCodec;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void parsesPreCheckWithoutProblems() {
        var result = preCheckParser.parse("{\"problems\":[]}");

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
                {"problems":[
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
    void ignoresSchemaVersionSuppliedByModelBecauseVersionIsBackendManaged() {
        assertThat(preCheckParser.parse("{\"schemaVersion\":\"2.0\",\"problems\":[]}").problems())
                .isEmpty();
    }

    @Test
    void rejectsMalformedAndNullButIgnoresUnknownPreCheckJsonFields() {
        assertThatThrownBy(() -> preCheckParser.parse("{not-json"))
                .isInstanceOf(AiOutputValidationException.class);
        assertThat(preCheckParser.parse("{\"problems\":[],\"unknown\":true}").problems()).isEmpty();
        assertThatThrownBy(() -> preCheckParser.parse("null"))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void preCheckBeanValidationIsSeparateFromParsing() {
        var incomplete = preCheckParser.parse("""
                {"problems":[{"severity":"ERROR","message":"Fehler","suggestion":""}]}
                """);

        assertThatThrownBy(() -> preCheckValidator.validate(incomplete))
                .isInstanceOf(AiOutputValidationException.class);
        preCheckValidator.validate(preCheckParser.parse("{\"problems\":[]}"));
    }

    @Test
    void parsesGenerationWithTemporaryIdsAndBothOrigins() {
        var result = generationParser.parse(validGenerationJson());

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
    void persistedProviderResultsDoNotTreatSchemaVersionAsGeneratedContent() {
        var result = generationParser.parse(validGenerationJson());

        assertThat(snapshotCodec.writeGeneratedPlan(result)).doesNotContain("schemaVersion");
        assertThat(snapshotCodec.writePreCheckResult(preCheckParser.parse("{\"problems\":[]}")))
                .doesNotContain("schemaVersion");
    }

    @Test
    void workflowPayloadsRoundTripAndLegacyJsonStringsRemainReadable() {
        AiWizardSnapshot snapshot = new AiWizardSnapshot(
                "Testprojekt", "Beschreibung", null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Sonstiges",
                "Ziel", null, null);
        var preCheckResult = preCheckParser.parse(problemJson("WARNING", "Knapp"));

        String snapshotJson = snapshotCodec.writeSnapshot(snapshot);
        String legacyJsonString = objectMapper.writeValueAsString(snapshotJson);

        assertThat(snapshotCodec.readSnapshot(snapshotJson)).isEqualTo(snapshot);
        assertThat(snapshotCodec.readSnapshot(legacyJsonString)).isEqualTo(snapshot);
        assertThat(snapshotCodec.readPreCheckResult(snapshotCodec.writePreCheckResult(preCheckResult)))
                .isEqualTo(preCheckResult);
    }

    @Test
    void workflowPayloadCodecRejectsNullEmptyAndDamagedPayloadsClearly() {
        assertThatThrownBy(() -> snapshotCodec.readSnapshot(null))
                .isInstanceOf(GenerationException.class)
                .hasMessageContaining("Payload ist leer");
        assertThatThrownBy(() -> snapshotCodec.readPreCheckResult("   "))
                .isInstanceOf(GenerationException.class)
                .hasMessageContaining("Payload ist leer");
        assertThatThrownBy(() -> snapshotCodec.readSnapshot("{beschädigt"))
                .isInstanceOf(GenerationException.class)
                .hasMessageContaining("Wizard-Stand");
        assertThatThrownBy(() -> snapshotCodec.writeSnapshot(null))
                .isInstanceOf(GenerationException.class)
                .hasMessageContaining("nicht null");
    }

    @Test
    void parserDoesNotPerformGenerationBeanOrDomainValidation() {
        assertThat(generationParser.parse(validGenerationJson().replace(
                "\"title\":\"Umzugskartons packen\",", ""))).isNotNull();
        assertThatThrownBy(() -> generationParser.parse(validGenerationJson().replace(
                "\"origin\":\"USER_INPUT\"", "\"origin\":\"TEMPLATE\"")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void parsesValidPriorityAndRejectsUnknownPriority() {
        var parsed = generationParser.parse(validGenerationJson().replace(
                "\"origin\":\"USER_INPUT\"",
                "\"priority\":\"HIGH\",\"origin\":\"USER_INPUT\""));
        assertThat(parsed.phases().getFirst().tasks().getFirst().priority())
                .isEqualTo(TaskPriority.HIGH);

        assertThatThrownBy(() -> generationParser.parse(validGenerationJson().replace(
                "\"origin\":\"USER_INPUT\"",
                "\"priority\":\"URGENT\",\"origin\":\"USER_INPUT\"")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsWrongJsonTypesDuringDeserialization() {
        assertThatThrownBy(() -> preCheckParser.parse("{\"problems\":{}}"))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> generationParser.parse(validGenerationJson().replace(
                "\"startDate\":\"2026-08-25\"", "\"startDate\":false")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void trimsHarmlessSurroundingWhitespace() {
        var result = generationParser.parse(validGenerationJson().replace(
                "\"title\":\"Vorbereitung\"", "\"title\":\"  Vorbereitung  \""));

        assertThat(result.phases().getFirst().title()).isEqualTo("Vorbereitung");
    }

    @Test
    void acceptsDuplicateTemporaryIdsForSubsequentDomainValidation() {
        assertThat(generationParser.parse(validGenerationJson().replace(
                "\"tempId\":\"task-2\"", "\"tempId\":\"task-1\""))).isNotNull();
    }

    @Test
    void ignoresUnknownApplicationManagedFields() {
        assertThat(generationParser.parse(validGenerationJson().replace(
                "\"phases\":",
                "\"projectTitle\":\"Nicht übernehmen\",\"phases\":"))).isNotNull();
        assertThat(generationParser.parse(validGenerationJson().replace(
                "\"origin\":\"USER_INPUT\",",
                "\"origin\":\"USER_INPUT\",\"reviewed\":true,"))).isNotNull();
    }

    @Test
    void rejectsMalformedAndNullButIgnoresUnknownGenerationFields() {
        assertThatThrownBy(() -> generationParser.parse("{not-json"))
                .isInstanceOf(AiOutputValidationException.class);
        assertThat(generationParser.parse(validGenerationJson().replace(
                "\"phases\":", "\"unknown\":true,\"phases\":"))).isNotNull();
        assertThatThrownBy(() -> generationParser.parse("null"))
                .isInstanceOf(AiOutputValidationException.class);
    }

    private String problemJson(String severity, String message) {
        return """
                {"problems":[{
                  "severity":"%s","message":"%s","suggestion":"Plane mehr Zeit ein."
                }]}
                """.formatted(severity, message);
    }

    private String validGenerationJson() {
        return """
                {
                  "phases":[{
                    "tempId":"phase-1","title":"Vorbereitung","description":"Alles vorbereiten",
                    "startDate":"2026-08-25","endDate":"2026-08-27","order":1,
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
