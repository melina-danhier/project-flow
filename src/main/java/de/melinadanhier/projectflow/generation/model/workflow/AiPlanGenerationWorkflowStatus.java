package de.melinadanhier.projectflow.generation.model.workflow;

public enum AiPlanGenerationWorkflowStatus {
    PRE_CHECK_PENDING,
    PRE_CHECK_RUNNING,
    PRE_CHECK_RETRY_PENDING,
    PRE_CHECK_NEEDS_REVIEW,
    GENERATION_PENDING,
    GENERATION_RUNNING,
    ASSUMPTIONS_REVIEW_PENDING,
    GENERATION_COMPLETED,
    GENERATION_FAILED,
    TECHNICAL_FAILURE
}
