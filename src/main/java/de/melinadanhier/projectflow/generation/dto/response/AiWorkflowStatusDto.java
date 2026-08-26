package de.melinadanhier.projectflow.generation.dto.response;

import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;

import java.util.UUID;

public record AiWorkflowStatusDto(
        UUID workflowId,
        UUID projectId,
        AiPlanGenerationWorkflowStatus status,
        int preCheckRetryCount,
        int generationRoundAttemptCount,
        int generationTotalAttemptCount,
        AiTechnicalErrorCode errorCode,
        Boolean errorRetryable,
        String errorDiagnosis
) {
    public boolean isProcessing() {
        return status == AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING
                || status == AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING
                || status == AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING
                || status == AiPlanGenerationWorkflowStatus.GENERATION_PENDING
                || status == AiPlanGenerationWorkflowStatus.GENERATION_RUNNING;
    }

    public boolean canRetry() {
        return Boolean.TRUE.equals(errorRetryable)
                && (status == AiPlanGenerationWorkflowStatus.GENERATION_FAILED
                || status == AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
    }
}
