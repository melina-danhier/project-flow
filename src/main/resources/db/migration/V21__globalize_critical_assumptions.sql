ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN generation_assumption_context JSONB,
    ADD COLUMN pending_assumption_review JSONB;

UPDATE ai_plan_generation_workflows
SET generation_prompt_version = 'generation-v3',
    generation_schema_version = '3.0'
WHERE status IN (
    'PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
    'PRE_CHECK_NEEDS_REVIEW', 'GENERATION_PENDING', 'GENERATION_RUNNING',
    'GENERATION_FAILED', 'TECHNICAL_FAILURE');

ALTER TABLE ai_plan_generation_workflows DROP CONSTRAINT ck_ai_workflows_status;
ALTER TABLE ai_plan_generation_workflows ADD CONSTRAINT ck_ai_workflows_status CHECK (status IN (
    'PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
    'PRE_CHECK_NEEDS_REVIEW', 'GENERATION_PENDING', 'GENERATION_RUNNING',
    'ASSUMPTIONS_REVIEW_PENDING', 'GENERATION_COMPLETED', 'GENERATION_FAILED',
    'DRAFT_APPLIED', 'TECHNICAL_FAILURE'));

ALTER TABLE draft_plan_elements DROP COLUMN critical_assumption;
ALTER TABLE draft_plan_elements DROP COLUMN has_critical_assumption;
ALTER TABLE draft_sections DROP COLUMN has_critical_assumption;
ALTER TABLE plan_elements DROP COLUMN critical_assumption;
ALTER TABLE plan_elements DROP COLUMN has_critical_assumption;
ALTER TABLE plan_sections DROP COLUMN has_critical_assumption;
