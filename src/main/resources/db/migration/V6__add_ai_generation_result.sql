ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN generated_plan JSONB;

ALTER TABLE ai_plan_generation_workflows
    DROP CONSTRAINT ck_ai_workflows_status;

ALTER TABLE ai_plan_generation_workflows
    ADD CONSTRAINT ck_ai_workflows_status CHECK (status IN (
        'PRE_CHECK_PENDING',
        'PRE_CHECK_RUNNING',
        'PRE_CHECK_RETRY_PENDING',
        'PRE_CHECK_PASSED',
        'PRE_CHECK_NEEDS_REVIEW',
        'GENERATION_RUNNING',
        'GENERATION_COMPLETED',
        'GENERATION_FAILED',
        'TECHNICAL_FAILURE'
    ));
