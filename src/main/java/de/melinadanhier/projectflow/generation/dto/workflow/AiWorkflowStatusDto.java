package de.melinadanhier.projectflow.generation.dto.workflow;

import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;

import java.util.UUID;

public record AiWorkflowStatusDto(
        UUID workflowId,
        UUID projectId,
        AiPlanGenerationWorkflowStatus status,
        int preCheckRetryCount,
        int generationRoundAttemptCount,
        int generationTotalAttemptCount,
        AiTechnicalErrorCode errorCode,
        AiOperation errorOperation,
        Boolean errorRetryable
) {
    public String errorMessage() {
        return errorCode == null ? null : errorCode.getUserMessage();
    }

    public boolean isProcessing() {
        return status.isActiveExecution();
    }

    public boolean canRetry() {
        return (Boolean.TRUE.equals(errorRetryable)
                || status == AiPlanGenerationWorkflowStatus.GENERATION_FAILED
                || (status == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE
                && errorCode != AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR))
                && errorOperation == AiOperation.PLAN_GENERATION;
    }

    public boolean canReturnToSummary() {
        return !isProcessing() && status != AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED;
    }

    public boolean canContinueManually() {
        return (status == AiPlanGenerationWorkflowStatus.GENERATION_FAILED
                || status == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE)
                && errorOperation == AiOperation.PLAN_GENERATION;
    }

    public boolean canCancel() {
        return isProcessing();
    }

    public boolean isPreCheckRun() {
        return status.isPreCheckPhase()
                || (status == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE
                && errorOperation == AiOperation.PRE_CHECK);
    }
}
