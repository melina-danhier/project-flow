package de.melinadanhier.projectflow.ai.exception;

/** Stabiler Code für technische Fehler im KI-Workflow. */
public enum AiTechnicalErrorCode {
    PROVIDER_UNAVAILABLE,
    PROVIDER_CONFIGURATION_ERROR,
    INVALID_AI_RESPONSE,
    AI_REFUSAL,
    INCOMPLETE_AI_RESPONSE,
    PRE_CHECK_INITIALIZATION_FAILED,
    PRE_CHECK_PROCESSING_FAILED,
    RETRY_INTERRUPTED,
    UNKNOWN_AI_ERROR
}
