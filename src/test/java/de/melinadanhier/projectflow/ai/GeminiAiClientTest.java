package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;

import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.model.precheck.*;
import de.melinadanhier.projectflow.ai.prompt.*;
import de.melinadanhier.projectflow.ai.provider.gemini.*;
import de.melinadanhier.projectflow.ai.provider.stub.*;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GeminiAiClientTest {
    @Test
    void forwardsBothPromptsModelsAndSharedResponseTypesWithoutReparsing() {
        var gateway = mock(AiResponsesGateway.class);
        var properties = new GeminiProperties();
        properties.setPreCheckModel("pre-model");
        properties.setGenerationModel("plan-model");
        var prePrompts = mock(PreCheckPromptBuilder.class);
        var planPrompts = mock(GenerationPromptBuilder.class);
        var snapshot = new AiWizardSnapshot("Projekt", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test", null, null, null);
        var preRequest = new AiPreCheckRequest(snapshot);
        var planRequest = new AiGenerationRequest(snapshot, List.of());
        var prePrompt = new AiPrompt("pre-v1", "pre instructions", "pre data");
        var planPrompt = new AiPrompt("plan-v1", "plan instructions", "plan data");
        when(prePrompts.build(preRequest)).thenReturn(prePrompt);
        when(planPrompts.build(planRequest)).thenReturn(planPrompt);
        var preResult = AiPreCheckResult.withoutIssues();
        var planResult = new StubAiClient(new StubAiProperties()).generatePlan(planRequest);
        when(gateway.execute("pre-model", prePrompt, AiPreCheckResult.class)).thenReturn(preResult);
        when(gateway.execute("plan-model", planPrompt, GeneratedPlanResponse.class)).thenReturn(planResult);
        var client = new GeminiAiClient(gateway, properties, prePrompts, planPrompts);

        assertThat(client.preCheck(preRequest)).isSameAs(preResult);
        assertThat(client.generatePlan(planRequest)).isSameAs(planResult);
        verify(gateway).execute("pre-model", prePrompt, AiPreCheckResult.class);
        verify(gateway).execute("plan-model", planPrompt, GeneratedPlanResponse.class);
        verifyNoMoreInteractions(gateway);
    }
}
