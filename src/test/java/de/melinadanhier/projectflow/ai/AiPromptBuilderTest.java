package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.prompt.AiPromptVersions;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.RejectedCriticalAssumption;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptBuilderTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final PreCheckPromptBuilder preCheckPromptBuilder = new PreCheckPromptBuilder(objectMapper);
    private final GenerationPromptBuilder generationPromptBuilder = new GenerationPromptBuilder(objectMapper);

    @Test
    void preCheckPromptSeparatesRulesAndConfirmedDataAndPinsVersions() {
        var prompt = preCheckPromptBuilder.build(snapshot());

        assertThat(prompt.version()).isEqualTo(AiPromptVersions.PRE_CHECK_PROMPT);
        assertThat(prompt.systemInstructions())
                .isNotBlank()
                .doesNotContain("Umzug planen");
        assertThat(objectMapper.readTree(prompt.confirmedUserData()).get("confirmedWizardData"))
                .isEqualTo(objectMapper.valueToTree(snapshot()));
        assertThat(objectMapper.readTree(prompt.confirmedUserData())
                .at("/confirmedWizardData/subcategory").asText()).isEqualTo("MOVING");
        assertThat(prompt.confirmedUserData()).doesNotContain("\"projectType\"");
    }

    @Test
    void generationPromptSeparatesRulesFromConfirmedDataAndPreservesAcknowledgedWarnings() {
        var warning = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING, "Zeitraum knapp", "Mehr Zeit einplanen");
        var otherWarning = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING, "Budget knapp", "Umfang reduzieren");
        var warnings = List.of(warning, otherWarning);

        var prompt = generationPromptBuilder.build(snapshot(), warnings);

        assertThat(prompt.version()).isEqualTo(AiPromptVersions.GENERATION_PROMPT);
        assertThat(prompt.systemInstructions())
                .isNotBlank()
                .doesNotContain("Umzug planen", "Zeitraum knapp");
        assertThat(objectMapper.readTree(prompt.confirmedUserData()).get("confirmedWizardData"))
                .isEqualTo(objectMapper.valueToTree(snapshot()));
        assertThat(objectMapper.readTree(prompt.confirmedUserData()).get("acknowledgedPreCheckWarnings"))
                .isEqualTo(objectMapper.valueToTree(warnings));
    }

    @Test
    void regenerationPromptContainsBindingAssumptionReviewContext() {
        var request = new AiGenerationRequest(snapshot(), List.of(), List.of(),
                List.of("Cloud-Dienste dürfen eingesetzt werden."),
                List.of(new RejectedCriticalAssumption(
                        "Zehn Stunden pro Woche stehen bereit.", "Es sind vier Stunden.")),
                AiPromptVersions.GENERATION_PROMPT);

        var prompt = generationPromptBuilder.build(request);

        assertThat(prompt.systemInstructions())
                .contains("ausschließlich global", "nicht erneut als Annahmen", "bloße Verneinung");
        var data = objectMapper.readTree(prompt.confirmedUserData());
        assertThat(data.at("/confirmedAssumptions/0").asText())
                .isEqualTo("Cloud-Dienste dürfen eingesetzt werden.");
        assertThat(data.at("/rejectedAssumptions/0/statement").asText())
                .isEqualTo("Zehn Stunden pro Woche stehen bereit.");
        assertThat(data.at("/rejectedAssumptions/0/correction").asText())
                .isEqualTo("Es sind vier Stunden.");
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Umzug planen", "Wohnungswechsel organisieren",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
                CollaborationMode.GROUP, TemplateCategory.HOME, ProjectSubCategory.MOVING, null,
                "Bis Monatsende umziehen", "Budget 2.000 Euro", "Kartons sind vorhanden");
    }
}
