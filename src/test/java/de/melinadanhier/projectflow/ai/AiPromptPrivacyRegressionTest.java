package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementContent;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementProjectContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementRequest;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.ImprovementPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PlanChangePromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressionstests: Stellt sicher, dass keine StudySession-ID, interne User-ID,
 * SoSci-ID oder Account-E-Mail in den an den KI-Anbieter gesendeten Prompt-Daten
 * enthalten ist.
 */
class AiPromptPrivacyRegressionTest {

    // Bekannte sensitive Werte, die unter keinen Umständen im Prompt auftauchen dürfen.
    private static final UUID STUDY_SESSION_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID INTERNAL_USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String ACCOUNT_EMAIL = "studyteilnehmer@example.com";
    private static final String SOSCI_ID = "SOSCI-REF-42XYZ";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generationPromptContainsNoSensitiveIdentifiers() {
        AiWizardSnapshot snapshot = createSnapshot();
        GenerationPromptBuilder builder = new GenerationPromptBuilder(objectMapper);

        AiPrompt prompt = builder.build(new AiGenerationRequest(snapshot, List.of()));

        assertNoSensitiveData(prompt);
    }

    @Test
    void preCheckPromptContainsNoSensitiveIdentifiers() {
        AiWizardSnapshot snapshot = createSnapshot();
        PreCheckPromptBuilder builder = new PreCheckPromptBuilder(objectMapper);

        AiPrompt prompt = builder.build(new AiPreCheckRequest(snapshot));

        assertNoSensitiveData(prompt);
    }

    @Test
    void improvementPromptContainsNoSensitiveIdentifiers() {
        ImprovementPromptBuilder builder = new ImprovementPromptBuilder(objectMapper);
        AiImprovementRequest request = new AiImprovementRequest(
                AiFeedbackType.IMPROVE,
                "Bitte kürzer formulieren",
                new AiImprovementProjectContext("Umzug", "Umzug nach Berlin", null, null),
                new AiImprovementContent(AiImprovementElementType.TASK,
                        "Kartons packen", "Alle Zimmer durchgehen", null, null, null, null)
        );

        AiPrompt prompt = builder.build(request);

        assertNoSensitiveData(prompt);
    }

    @Test
    void planChangePromptContainsNoSensitiveIdentifiers() {
        PlanChangePromptBuilder builder = new PlanChangePromptBuilder(objectMapper);
        AiPlanChangeRequest request = new AiPlanChangeRequest(
                "Ergänze eine Aufgabe für die Wohnungsübergabe.",
                new AiImprovementProjectContext("Umzug", null, null, null),
                new AiImprovementPlanContext(List.of(
                        new AiImprovementPlanContext.Section("s1", "Vorbereitung", null, 1, List.of())
                ))
        );

        AiPrompt prompt = builder.build(request);

        assertNoSensitiveData(prompt);
    }

    /**
     * Prüft, dass weder systemInstructions noch confirmedUserData einen der
     * sensitiven Identifikatoren enthalten.
     */
    private void assertNoSensitiveData(AiPrompt prompt) {
        String fullPayload = prompt.systemInstructions() + " " + prompt.confirmedUserData();

        assertThat(fullPayload)
                .as("Prompt darf keine StudySession-ID enthalten")
                .doesNotContain(STUDY_SESSION_ID.toString());

        assertThat(fullPayload)
                .as("Prompt darf keine interne User-ID enthalten")
                .doesNotContain(INTERNAL_USER_ID.toString());

        assertThat(fullPayload)
                .as("Prompt darf keine Account-E-Mail enthalten")
                .doesNotContain(ACCOUNT_EMAIL);

        assertThat(fullPayload)
                .as("Prompt darf keine SoSci-ID enthalten")
                .doesNotContain(SOSCI_ID);
    }

    private AiWizardSnapshot createSnapshot() {
        return new AiWizardSnapshot(
                "Umzug nach Berlin",
                "Umzug in sechs Wochen",
                null, null,
                CollaborationMode.INDIVIDUAL,
                ProjectCategory.OTHER,
                null,
                "Privatumzug",
                "Umzug organisieren",
                null,
                null
        );
    }
}
