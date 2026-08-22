UPDATE ai_plan_generation_workflows
SET last_technical_error = CASE last_technical_error
    WHEN 'AI_PROVIDER_UNAVAILABLE' THEN 'PROVIDER_UNAVAILABLE'
    WHEN 'AI_OUTPUT_INVALID' THEN 'INVALID_AI_RESPONSE'
    WHEN 'AiProviderUnavailableException' THEN 'PROVIDER_UNAVAILABLE'
    WHEN 'AiProviderConfigurationException' THEN 'PROVIDER_CONFIGURATION_ERROR'
    WHEN 'AiOutputValidationException' THEN 'INVALID_AI_RESPONSE'
    WHEN 'AiRequestRefusedException' THEN 'AI_REFUSAL'
    WHEN 'AiIncompleteResponseException' THEN 'INCOMPLETE_AI_RESPONSE'
    WHEN 'PRE_CHECK_INITIALIZATION_FAILED' THEN 'PRE_CHECK_INITIALIZATION_FAILED'
    WHEN 'PRE_CHECK_PROCESSING_FAILED' THEN 'PRE_CHECK_PROCESSING_FAILED'
    WHEN 'RETRY_INTERRUPTED' THEN 'RETRY_INTERRUPTED'
    ELSE 'UNKNOWN_AI_ERROR'
END
WHERE last_technical_error IS NOT NULL;

ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN last_technical_error TYPE VARCHAR(50);

ALTER TABLE ai_plan_generation_workflows
    ADD CONSTRAINT ck_ai_workflows_technical_error CHECK (
        last_technical_error IS NULL OR last_technical_error IN (
            'PROVIDER_UNAVAILABLE', 'PROVIDER_CONFIGURATION_ERROR', 'INVALID_AI_RESPONSE',
            'AI_REFUSAL', 'INCOMPLETE_AI_RESPONSE', 'PRE_CHECK_INITIALIZATION_FAILED',
            'PRE_CHECK_PROCESSING_FAILED', 'RETRY_INTERRUPTED', 'UNKNOWN_AI_ERROR'));

ALTER TABLE ai_plan_generation_workflows DROP CONSTRAINT ck_ai_workflows_status;
ALTER TABLE ai_plan_generation_workflows ADD CONSTRAINT ck_ai_workflows_status CHECK (status IN (
    'PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING', 'PRE_CHECK_PASSED',
    'PRE_CHECK_NEEDS_REVIEW', 'GENERATION_PENDING', 'GENERATION_RUNNING',
    'GENERATION_COMPLETED', 'GENERATION_FAILED', 'DRAFT_APPLIED', 'TECHNICAL_FAILURE'));

ALTER TABLE plan_drafts ADD COLUMN summary VARCHAR(1000);
ALTER TABLE plan_drafts ADD COLUMN assumptions JSONB;
ALTER TABLE plan_drafts DROP CONSTRAINT ck_plan_drafts_status;
ALTER TABLE plan_drafts ADD CONSTRAINT ck_plan_drafts_status CHECK (status IN (
    'GENERATING', 'FAILED', 'READY_FOR_REVIEW', 'IN_REVIEW', 'APPLYING', 'APPLIED'));

ALTER TABLE draft_plan_elements ADD COLUMN critical_assumption VARCHAR(2000);
ALTER TABLE draft_tasks ADD COLUMN estimated_hours INTEGER;
ALTER TABLE draft_tasks ADD CONSTRAINT ck_draft_tasks_estimated_hours
    CHECK (estimated_hours IS NULL OR estimated_hours > 0);

ALTER TABLE plan_elements ADD COLUMN critical_assumption VARCHAR(2000);
ALTER TABLE tasks ADD COLUMN estimated_hours INTEGER;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_estimated_hours
    CHECK (estimated_hours IS NULL OR estimated_hours > 0);
