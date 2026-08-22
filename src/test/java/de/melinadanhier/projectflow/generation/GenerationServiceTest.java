package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.client.AiClient;
import de.melinadanhier.projectflow.generation.client.AiExecutionProperties;
import de.melinadanhier.projectflow.generation.client.AiProviderConfigurationException;
import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedElementOrigin;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPhase;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanMetadata;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedTask;
import de.melinadanhier.projectflow.generation.service.AiPreCheckBackoff;
import de.melinadanhier.projectflow.generation.service.GenerationService;
import de.melinadanhier.projectflow.generation.validation.GenerationResponseValidator;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationServiceTest {

    @Test
    void invalidOutputIsRetriedWithAllValidationIssues() throws Exception {
        AiClient client = mock(AiClient.class);
        AiPreCheckBackoff backoff = mock(AiPreCheckBackoff.class);
        when(client.generatePlan(any())).thenReturn(emptyPlan(), validPlan());
        GenerationService service = service(client, backoff, 2);

        assertThat(service.generatePlan(snapshot(), List.of()).phases()).hasSize(1);

        ArgumentCaptor<AiGenerationRequest> requests = ArgumentCaptor.forClass(AiGenerationRequest.class);
        verify(client, times(2)).generatePlan(requests.capture());
        assertThat(requests.getAllValues().get(0).previousValidationIssues()).isEmpty();
        assertThat(requests.getAllValues().get(1).previousValidationIssues())
                .contains("Es wurde keine Phase erzeugt.", "Es wurde keine Aufgabe erzeugt.");
        verify(backoff).waitBeforeRetry(1);
    }

    @Test
    void outputRetryLimitIsEnforced() {
        AiClient client = mock(AiClient.class);
        when(client.generatePlan(any())).thenReturn(emptyPlan());

        assertThatThrownBy(() -> service(client, mock(AiPreCheckBackoff.class), 2)
                .generatePlan(snapshot(), List.of()))
                .isInstanceOf(de.melinadanhier.projectflow.generation.client.AiOutputValidationException.class);
        verify(client, times(3)).generatePlan(any());
    }

    @Test
    void permanentConfigurationFailureIsNotRetried() throws Exception {
        AiClient client = mock(AiClient.class);
        AiPreCheckBackoff backoff = mock(AiPreCheckBackoff.class);
        when(client.generatePlan(any())).thenThrow(new AiProviderConfigurationException("API-Key falsch"));

        assertThatThrownBy(() -> service(client, backoff, 2).generatePlan(snapshot(), List.of()))
                .isInstanceOf(AiProviderConfigurationException.class);
        verify(client).generatePlan(any());
        verify(backoff, never()).waitBeforeRetry(any(Integer.class));
    }

    private GenerationService service(AiClient client, AiPreCheckBackoff backoff, int retries) {
        AiExecutionProperties properties = new AiExecutionProperties();
        properties.setMaxAutomaticRetries(retries);
        return new GenerationService(client, new GenerationResponseValidator(
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()), properties, backoff);
    }

    private GeneratedPlanResponse emptyPlan() {
        return new GeneratedPlanResponse(new GeneratedPlanMetadata("Plan", List.of()), List.of());
    }

    private GeneratedPlanResponse validPlan() {
        return new GeneratedPlanResponse(
                new GeneratedPlanMetadata("Plan", List.of()),
                List.of(new GeneratedPhase(
                        "phase-1", "Phase", null, null, null, 1,
                        List.of(new GeneratedTask(
                                "task-1", "Aufgabe", null, 1, null, null, null,
                                GeneratedElementOrigin.AI_INFERRED, 1)), List.of())));
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Projekt", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null);
    }
}
