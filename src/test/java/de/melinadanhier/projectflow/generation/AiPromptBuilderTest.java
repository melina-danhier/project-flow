package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.prompt.AiPromptVersions;
import de.melinadanhier.projectflow.generation.prompt.AiSchemaVersions;
import de.melinadanhier.projectflow.generation.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.generation.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AiPromptBuilderTest {

    @Autowired
    private PreCheckPromptBuilder preCheckPromptBuilder;

    @Autowired
    private GenerationPromptBuilder generationPromptBuilder;

    @Test
    void preCheckPromptSeparatesRulesAndConfirmedDataAndPinsVersions() {
        var prompt = preCheckPromptBuilder.build(snapshot());

        assertThat(prompt.version()).isEqualTo(AiPromptVersions.PRE_CHECK_PROMPT);
        assertThat(prompt.systemInstructions())
                .contains(
                        "noch keinen Projektplan",
                        "WARNING",
                        "ERROR",
                        "Schemas " + AiSchemaVersions.PRE_CHECK,
                        "\"schemaVersion\":\"" + AiSchemaVersions.PRE_CHECK + "\"")
                .doesNotContain("Umzug planen");
        assertThat(prompt.confirmedUserData()).contains("Umzug planen", "Kartons sind vorhanden");
    }

    @Test
    void generationPromptExcludesGeneralProjectFieldsFromOutputAndIncludesOnlyIgnoredWarnings() {
        var warning = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING, "Zeitraum knapp", "Mehr Zeit einplanen");
        var error = new AiPreCheckProblem(
                AiPreCheckSeverity.ERROR, "Unmöglich", "Ziel reduzieren");

        var prompt = generationPromptBuilder.build(snapshot(), List.of(warning, error));

        assertThat(prompt.version()).isEqualTo(AiPromptVersions.GENERATION_PROMPT);
        assertThat(prompt.systemInstructions())
                .contains(
                        "keinen\n  Projekttitel",
                        "metadata",
                        "tempId",
                        "USER_INPUT",
                        "AI_INFERRED",
                        "ungeprüft",
                        "Schemas " + AiSchemaVersions.GENERATION,
                        "\"schemaVersion\":\"" + AiSchemaVersions.GENERATION + "\"");
        assertThat(prompt.confirmedUserData())
                .contains("confirmedWizardData", "explicitlyIgnoredWarnings", "Zeitraum knapp")
                .doesNotContain("Unmöglich");
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Umzug planen", "Wohnungswechsel organisieren",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
                CollaborationMode.GROUP, TemplateCategory.HOME, "Umzug",
                "Bis Monatsende umziehen", "Budget 2.000 Euro", "Kartons sind vorhanden");
    }
}
