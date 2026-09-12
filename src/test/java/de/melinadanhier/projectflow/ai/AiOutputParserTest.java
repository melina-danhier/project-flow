package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.parser.AiResponseParser;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.validation.precheck.PreCheckResultValidator;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.common.exception.GenerationException;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiOutputParserTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final AiResponseParser parser = new AiResponseParser(objectMapper);
    private final PreCheckResultValidator preCheckValidator = new PreCheckResultValidator(
            Validation.buildDefaultValidatorFactory().getValidator());
    private final AiWorkflowPayloadCodec snapshotCodec = new AiWorkflowPayloadCodec(objectMapper);

    @Test
    void parsesPreCheckWithoutProblems() {
        var result = parsePreCheck("{\"problems\":[]}");

        assertThat(result.problems()).isEmpty();
        assertThat(result.hasPlausibilityIssues()).isFalse();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void parsesWarningWithActionableSuggestion() {
        var result = parsePreCheck(problemJson("WARNING", "Zeitraum ist knapp."));

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(AiPreCheckSeverity.WARNING);
            assertThat(problem.suggestedUserAction()).isEqualTo("Plane mehr Zeit ein.");
        });
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void parsesErrorAsBusinessProblemRatherThanTechnicalFailure() {
        var result = parsePreCheck(problemJson("ERROR", "Das Ziel ist in einem Tag nicht erreichbar."));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.problems()).singleElement()
                .extracting("severity").isEqualTo(AiPreCheckSeverity.ERROR);
    }

    @Test
    void errorsSuppressSimultaneousNonBlockingProblems() {
        var result = parsePreCheck("""
                {"problems":[
                  {"severity":"WARNING","type":"RISK","message":"Knapp.","suggestedUserAction":"Mehr Zeit einplanen.","acceptedInterpretation":"Im Zeitraum priorisieren."},
                  {"severity":"ERROR","type":"CONFLICT","message":"Ziel widerspricht der Frist.","suggestedUserAction":"Ziel reduzieren.","acceptedInterpretation":""}
                ]}
                """);

        assertThat(result.problems()).singleElement()
                .extracting("severity").isEqualTo(AiPreCheckSeverity.ERROR);
        assertThat(result.hasWarnings()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsUnknownSeverityAsTechnicalOutputFailure() {
        assertThatThrownBy(() -> parsePreCheck(problemJson("INFO", "Hinweis")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsModelSuppliedSchemaVersionBecauseVersionIsBackendManaged() {
        assertThatThrownBy(() -> parsePreCheck("{\"schemaVersion\":\"2.0\",\"problems\":[]}"))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsMalformedNullAndUnknownPreCheckJsonFields() {
        assertThatThrownBy(() -> parsePreCheck("{not-json"))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> parsePreCheck("{\"problems\":[],\"unknown\":true}"))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> parsePreCheck("null"))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void preCheckBeanValidationIsSeparateFromParsing() {
        var incomplete = parsePreCheck("""
                {"problems":[{"severity":"ERROR","type":"CONFLICT","message":"Fehler","suggestedUserAction":"","acceptedInterpretation":""}]}
                """);

        assertThatThrownBy(() -> preCheckValidator.validate(incomplete))
                .isInstanceOf(AiOutputValidationException.class);
        preCheckValidator.validate(parsePreCheck("{\"problems\":[]}"));
    }

    @Test
    void parsesGenerationWithTemporaryIdsWithoutModelControlledOrigins() {
        var result = parseGeneration(validGenerationJson());

        assertThat(result.sections()).singleElement().satisfies(section -> {
            assertThat(section.tempId()).isEqualTo("section-1");
            assertThat(section.tasks()).extracting("tempId")
                    .containsExactly("task-1", "task-2");
            assertThat(section.milestones()).singleElement()
                    .extracting("tempId").isEqualTo("milestone-1");
        });
    }

    @Test
    void persistedProviderResultsDoNotTreatSchemaVersionAsGeneratedContent() {
        var result = parseGeneration(validGenerationJson());

        assertThat(snapshotCodec.writeGeneratedPlan(result)).doesNotContain("schemaVersion");
        assertThat(snapshotCodec.writePreCheckResult(parsePreCheck("{\"problems\":[]}")))
                .doesNotContain("schemaVersion");
    }

    @Test
    void workflowPayloadsRoundTripAndLegacyJsonStringsRemainReadable() {
        AiWizardSnapshot snapshot = new AiWizardSnapshot(
                "Testprojekt", "Beschreibung", null, null,
                CollaborationMode.INDIVIDUAL, ProjectCategory.OTHER, null, "Sonstiges",
                "Ziel", null, null);
        var preCheckResult = parsePreCheck(problemJson("WARNING", "Knapp"));

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
                .isInstanceOf(GenerationException.class);
        assertThatThrownBy(() -> snapshotCodec.readPreCheckResult("   "))
                .isInstanceOf(GenerationException.class);
        assertThatThrownBy(() -> snapshotCodec.readSnapshot("{beschädigt"))
                .isInstanceOf(GenerationException.class);
        assertThatThrownBy(() -> snapshotCodec.writeSnapshot(null))
                .isInstanceOf(GenerationException.class);
    }

    @Test
    void parserDoesNotPerformGenerationBeanOrDomainValidation() {
        assertThat(parseGeneration(validGenerationJson().replace(
                "\"title\":\"Umzugskartons packen\",", ""))).isNotNull();
        assertThatThrownBy(() -> parseGeneration(validGenerationJson().replace(
                "\"estimatedHours\":4,", "\"estimatedHours\":4,\"origin\":\"USER_INPUT\",")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void parsesValidPriorityAndRejectsUnknownPriority() {
        var parsed = parseGeneration(validGenerationJson().replace(
                "\"estimatedHours\":4,", "\"estimatedHours\":4,\"priority\":\"HIGH\","));
        assertThat(parsed.sections().getFirst().tasks().getFirst().priority())
                .isEqualTo(TaskPriority.HIGH);

        assertThatThrownBy(() -> parseGeneration(validGenerationJson().replace(
                "\"estimatedHours\":4,", "\"estimatedHours\":4,\"priority\":\"URGENT\",")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsWrongJsonTypesDuringDeserialization() {
        assertThatThrownBy(() -> parsePreCheck("{\"problems\":{}}"))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> parseGeneration(validGenerationJson().replace(
                "\"startDate\":\"2026-08-25\"", "\"startDate\":false")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void trimsHarmlessSurroundingWhitespace() {
        var result = parseGeneration(validGenerationJson().replace(
                "\"title\":\"Vorbereitung\"", "\"title\":\"  Vorbereitung  \""));

        assertThat(result.sections().getFirst().title()).isEqualTo("Vorbereitung");
    }

    @Test
    void acceptsDuplicateTemporaryIdsForSubsequentDomainValidation() {
        assertThat(parseGeneration(validGenerationJson().replace(
                "\"tempId\":\"task-2\"", "\"tempId\":\"task-1\""))).isNotNull();
    }

    @Test
    void rejectsUnknownApplicationManagedFields() {
        assertThatThrownBy(() -> parseGeneration(validGenerationJson().replace(
                "\"sections\":",
                "\"projectTitle\":\"Nicht übernehmen\",\"sections\":"))).isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> parseGeneration(validGenerationJson().replace(
                "\"estimatedHours\":4,",
                "\"estimatedHours\":4,\"reviewed\":true,"))).isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsMalformedNullAndUnknownGenerationFields() {
        assertThatThrownBy(() -> parseGeneration("{not-json"))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> parseGeneration(validGenerationJson().replace(
                "\"sections\":", "\"unknown\":true,\"sections\":"))).isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> parseGeneration("null"))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsExplanatoryTextOutsideStructuredOutput() {
        for (String json : List.of(
                "Hier ist das Ergebnis:\n{\"problems\":[]}",
                "{\"problems\":[]}\nDie Planung ist plausibel.",
                "```json\n{\"problems\":[]}\n```")) {
            assertThatThrownBy(() -> parsePreCheck(json))
                    .isInstanceOf(AiOutputValidationException.class);
        }
    }

    @Test
    void rejectsBlankOversizedTrailingAndNullElementResponses() {
        for (String json : java.util.Arrays.asList(null, "", " \n\t", "{\"problems\":[]} {}",
                "{\"problems\":[null]}", " ".repeat(1048576) + "{}",
                "{\"problems\":[],\"extra\":\"" + "ü".repeat(524288) + "\"}")) {
            assertThatThrownBy(() -> parsePreCheck(json)).isInstanceOf(AiOutputValidationException.class);
        }
        assertThatThrownBy(() -> parseGeneration("{\"sections\":[null]}"))
                .isInstanceOf(AiOutputValidationException.class);
    }

    private AiPreCheckResult parsePreCheck(String json) {
        return parser.parse(json, AiPreCheckResult.class);
    }

    @Test
    void byteLimitCountsUtf8BytesBeforeDeserialization() {
        String json = problemJson("WARNING", "ü".repeat(524288));
        assertThat(json.length()).isLessThan(1048576);
        assertThatThrownBy(() -> parsePreCheck(json)).isInstanceOf(AiOutputValidationException.class);
        // Der reine Parser prüft keine fachliche Textlänge.
        assertThat(parsePreCheck(problemJson("WARNING", "ü".repeat(1000)))).isNotNull();
    }

    private GeneratedPlanResponse parseGeneration(String json) {
        return parser.parse(json, GeneratedPlanResponse.class);
    }

    private String problemJson(String severity, String message) {
        return """
                {"problems":[{
                  "severity":"%s","type":"RISK","message":"%s","suggestedUserAction":"Plane mehr Zeit ein.","acceptedInterpretation":"Im Zeitraum priorisieren."
                }]}
                """.formatted(severity, message);
    }

    private String validGenerationJson() {
        return """
                {
                  "sections":[{
                    "tempId":"section-1","title":"Vorbereitung","description":"Alles vorbereiten",
                    "order":1,
                    "tasks":[
                      {"tempId":"task-1","title":"Umzugskartons packen","description":"Zimmerweise packen",
                       "estimatedHours":4,"startDate":"2026-08-25","dueDate":"2026-08-26",
                       "order":1},
                      {"tempId":"task-2","title":"Transport organisieren","description":"Fahrzeug reservieren",
                       "estimatedHours":2,"startDate":"2026-08-25","dueDate":"2026-08-26",
                       "order":2}
                    ],
                    "milestones":[{"tempId":"milestone-1","title":"Vorbereitung abgeschlossen",
                                   "date":"2026-08-27","order":1}]
                  }]
                }
                """;
    }
}
