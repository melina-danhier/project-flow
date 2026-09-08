package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.generation.dto.workflow.AiWorkflowStatusDto;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiWorkflowStatusDtoTest {

    @Test
    void onlyRetryablePlanGenerationFailureCanBeRestartedFromStatusPage() {
        assertThat(status(AiOperation.PLAN_GENERATION).canRetry()).isTrue();
        assertThat(status(AiOperation.PRE_CHECK).canRetry()).isFalse();
        assertThat(new AiWorkflowStatusDto(
                UUID.randomUUID(), UUID.randomUUID(),
                AiPlanGenerationWorkflowStatus.GENERATION_FAILED,
                0, 1, 1,
                AiTechnicalErrorCode.INVALID_AI_RESPONSE,
                AiOperation.PLAN_GENERATION, false).canRetry()).isTrue();
    }

    @Test
    void canReturnToSummaryWhenNotProcessingAndNotCompleted() {
        assertThat(status(AiPlanGenerationWorkflowStatus.GENERATION_FAILED).canReturnToSummary()).isTrue();
        assertThat(status(AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE).canReturnToSummary()).isTrue();
        assertThat(status(AiPlanGenerationWorkflowStatus.PRE_CHECK_CANCELLED).canReturnToSummary()).isTrue();
        assertThat(status(AiPlanGenerationWorkflowStatus.GENERATION_CANCELLED).canReturnToSummary()).isTrue();
        assertThat(status(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING).canReturnToSummary()).isFalse();
        assertThat(status(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED).canReturnToSummary()).isFalse();
    }

    @Test
    void exposesTheUserMessageDefinedByTheStableErrorCode() {
        assertThat(status(AiOperation.PLAN_GENERATION).errorMessage())
                .isEqualTo(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE.getUserMessage());
    }

    @Test
    void pendingAndRunningWorkCanBeCancelled() {
        for (var cancellable : new AiPlanGenerationWorkflowStatus[] {
                AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING,
                AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING,
                AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING,
                AiPlanGenerationWorkflowStatus.GENERATION_PENDING,
                AiPlanGenerationWorkflowStatus.GENERATION_RUNNING}) {
            assertThat(status(cancellable).isProcessing()).isTrue();
            assertThat(status(cancellable).canCancel()).isTrue();
        }
        assertThat(status(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED).canCancel()).isFalse();
    }

    @Test
    void retryBackoffAndCancelledPreCheckRemainAssignedToThePreCheckPhase() {
        assertThat(status(AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING).isPreCheckRun()).isTrue();
        assertThat(status(AiPlanGenerationWorkflowStatus.PRE_CHECK_CANCELLED).isPreCheckRun()).isTrue();
        assertThat(status(AiOperation.PRE_CHECK).isPreCheckRun()).isTrue();
        assertThat(status(AiOperation.PLAN_GENERATION).isPreCheckRun()).isFalse();
    }

    private AiWorkflowStatusDto status(AiOperation operation) {
        return new AiWorkflowStatusDto(
                UUID.randomUUID(), UUID.randomUUID(),
                AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE,
                0, 1, 1,
                AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                operation, true);
    }

    private AiWorkflowStatusDto status(AiPlanGenerationWorkflowStatus status) {
        return new AiWorkflowStatusDto(
                UUID.randomUUID(), UUID.randomUUID(), status,
                0, 0, 0, null, null, null);
    }
}
