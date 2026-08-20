package de.melinadanhier.projectflow.generation.model;

public enum AiPlanGenerationWorkflowStatus {
    PRE_CHECK_PENDING,
    PRE_CHECK_RUNNING,
    PRE_CHECK_RETRY_PENDING,
    PRE_CHECK_PASSED,
    PRE_CHECK_NEEDS_REVIEW,
    TECHNICAL_FAILURE
}
