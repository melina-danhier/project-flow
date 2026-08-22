UPDATE ai_plan_generation_workflows
SET status = 'GENERATION_PENDING'
WHERE status = 'PRE_CHECK_PASSED';

ALTER TABLE ai_plan_generation_workflows DROP CONSTRAINT ck_ai_workflows_status;
ALTER TABLE ai_plan_generation_workflows ADD CONSTRAINT ck_ai_workflows_status CHECK (status IN (
    'PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
    'PRE_CHECK_NEEDS_REVIEW', 'GENERATION_PENDING', 'GENERATION_RUNNING',
    'GENERATION_COMPLETED', 'GENERATION_FAILED', 'DRAFT_APPLIED', 'TECHNICAL_FAILURE'));

ALTER TABLE ai_plan_generation_workflows
    RENAME COLUMN retry_count TO pre_check_retry_count;

ALTER TABLE ai_plan_generation_workflows
    RENAME CONSTRAINT ck_ai_workflows_retry_count TO ck_ai_workflows_pre_check_retry_count;
