ALTER TABLE ai_plan_generation_workflows
    DROP CONSTRAINT ck_ai_workflows_technical_error;

UPDATE ai_plan_generation_workflows
SET last_technical_error = 'CLIENT_CONFIGURATION_ERROR'
WHERE last_technical_error = 'PROVIDER_CONFIGURATION_ERROR';

ALTER TABLE ai_plan_generation_workflows
    ADD CONSTRAINT ck_ai_workflows_technical_error CHECK (
        last_technical_error IS NULL OR last_technical_error IN (
            'PROVIDER_UNAVAILABLE', 'CLIENT_CONFIGURATION_ERROR', 'INVALID_AI_RESPONSE',
            'AI_REFUSAL', 'INCOMPLETE_AI_RESPONSE', 'PRE_CHECK_INITIALIZATION_FAILED',
            'PRE_CHECK_PROCESSING_FAILED', 'RETRY_INTERRUPTED', 'UNKNOWN_AI_ERROR'));
