package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.dto.*;
import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.model.workflow.*;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.service.assumption.CriticalAssumptionReviewService;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CriticalAssumptionReviewServiceTest {

    @Mock AiPlanGenerationWorkflowRepository workflows;
    @Mock AiWorkflowPayloadCodec codec;
    @Mock ApplicationEventPublisher events;
    @Mock AiPlanGenerationWorkflow workflow;
    @Mock Project project;

    private CriticalAssumptionReviewService service;
    private final UUID workflowId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CriticalAssumptionReviewService(workflows, codec, events,
                new AiExecutionProperties(), Clock.systemUTC());
        when(workflows.findOwnedByIdForUpdate(workflowId, userId)).thenReturn(Optional.of(workflow));
        lenient().when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.ASSUMPTIONS_REVIEW_PENDING);
        lenient().when(workflow.getGeneratedPlan()).thenReturn("plan");
        lenient().when(codec.readGeneratedPlan("plan")).thenReturn(plan());
    }

    @Test
    void fullyConfirmedReviewReleasesExistingDraftWithoutAiCall() {
        var request = request(confirmed(0), confirmed(1));

        assertThat(service.submit(workflowId, userId, request)).isFalse();

        verify(workflow).confirmAssumptions();
        verifyNoInteractions(events);
        verify(codec, never()).writeAssumptionContext(any());
    }

    @Test
    void confirmationAfterFailedRegenerationUsesPreservedReviewPath() {
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.GENERATION_FAILED);
        when(workflow.hasFailedAssumptionRegeneration()).thenReturn(true);

        assertThat(service.submit(workflowId, userId, request(confirmed(0), confirmed(1)))).isFalse();

        verify(workflow).confirmAssumptionsAfterFailedRegeneration();
        verify(workflow, never()).confirmAssumptions();
        verifyNoInteractions(events);
    }

    @Test
    void rejectionStartsRegenerationWithConfirmedFactsAndOnlyRealCorrection() {
        var request = request(confirmed(0), rejected(1, "Vier Stunden pro Woche."));
        when(codec.readAssumptionContext(null)).thenReturn(GenerationAssumptionContext.empty());
        when(codec.writeAssumptionContext(any())).thenReturn("context");
        when(codec.writeAssumptionReview(request)).thenReturn("review");

        assertThat(service.submit(workflowId, userId, request)).isTrue();

        var context = org.mockito.ArgumentCaptor.forClass(GenerationAssumptionContext.class);
        verify(codec).writeAssumptionContext(context.capture());
        assertThat(context.getValue().confirmedAssumptions()).containsExactly("Cloud-Dienste sind erlaubt.");
        assertThat(context.getValue().rejectedAssumptions()).containsExactly(
                new RejectedCriticalAssumption("Zehn Stunden stehen bereit.", "Vier Stunden pro Woche."));
        verify(workflow).prepareAssumptionRegeneration(
                eq("context"), eq("review"), any(UUID.class), any(java.time.Instant.class));
        verify(events).publishEvent(any(AiGenerationRequestedEvent.class));
    }

    @Test
    void rejectsMissingRequiredCorrectionAndUnexpectedCorrection() {
        assertThatThrownBy(() -> service.submit(workflowId, userId,
                request(confirmed(0), rejected(1, "  "))))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Korrektur");

        assertThatThrownBy(() -> service.submit(workflowId, userId,
                request(rejected(0, "Kommentar"), confirmed(1))))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("keine Korrektur");
        verify(workflow, never()).prepareAssumptionRegeneration(
                anyString(), anyString(), any(UUID.class), any(java.time.Instant.class));
    }

    @Test
    void rejectsIncompleteAndContradictoryReviews() {
        assertThatThrownBy(() -> service.submit(workflowId, userId, request(confirmed(0))))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.submit(workflowId, userId,
                request(confirmed(0), rejected(0, null))))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void enforcesWorkflowOwnership() {
        when(workflows.findOwnedByIdForUpdate(workflowId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(workflowId, userId,
                request(confirmed(0), confirmed(1))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private GeneratedPlanResponse plan() {
        return new GeneratedPlanResponse(List.of(), List.of(
                new GeneratedCriticalAssumption("Cloud-Dienste sind erlaubt.", false),
                new GeneratedCriticalAssumption("Zehn Stunden stehen bereit.", true)));
    }

    private AssumptionReviewRequest request(AssumptionDecisionRequest... decisions) {
        return new AssumptionReviewRequest(List.of(decisions));
    }

    private AssumptionDecisionRequest confirmed(int index) {
        return new AssumptionDecisionRequest(index, AssumptionDecision.CONFIRMED, null);
    }

    private AssumptionDecisionRequest rejected(int index, String correction) {
        return new AssumptionDecisionRequest(index, AssumptionDecision.REJECTED, correction);
    }
}
