package de.melinadanhier.projectflow.ai.prompt;

import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationPromptBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GenerationPromptBuilder builder = new GenerationPromptBuilder(objectMapper);

    @Test
    void promptUsesCurrentGenerationPromptVersion() {
        AiPrompt prompt = builder.build(createRequest());

        assertThat(prompt.version()).isEqualTo(AiPromptVersions.GENERATION_PROMPT);
        assertThat(prompt.version()).isEqualTo("generation-v11");
    }

    @Test
    void promptContainsFinishToStartDependencySemantics() {
        AiPrompt prompt = builder.build(createRequest());
        String instructions = prompt.systemInstructions();

        assertThat(instructions)
                .contains("Finish-to-Start-Abhängigkeit")
                .contains("B.startDate >= A.dueDate")
                .contains("B.dueDate >= A.dueDate")
                .contains("Ein Beginn am selben Kalendertag wie das Fälligkeitsdatum")
                .contains("Voraussetzung ist erlaubt.")
                .contains("Aufgaben dürfen sich grundsätzlich zeitlich überlappen oder parallel bearbeitet werden")
                .contains("prerequisiteTaskTempIds miteinander verknüpft werden")
                .contains("zwingend notwendig ist, damit die Nachfolgeaufgabe")
                .contains("Nicht jede inhaltliche, thematische oder organisatorische Beziehung");
    }

    private AiGenerationRequest createRequest() {
        AiWizardSnapshot snapshot = new AiWizardSnapshot(
                "Testprojekt", "Beschreibung", null, null,
                CollaborationMode.INDIVIDUAL, ProjectCategory.HOME, null,
                null, null, null, null, null,
                Map.of());
        return new AiGenerationRequest(snapshot, List.of());
    }
}
