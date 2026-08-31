ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN active_run_id UUID,
    ADD COLUMN run_expires_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE ai_plan_generation_workflows
SET active_run_id = gen_random_uuid(),
    run_expires_at = updated_at + INTERVAL '5 minutes'
WHERE status IN ('PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
                 'GENERATION_PENDING', 'GENERATION_RUNNING');

ALTER TABLE ai_plan_generation_workflows DROP CONSTRAINT ck_ai_workflows_status;
ALTER TABLE ai_plan_generation_workflows ADD CONSTRAINT ck_ai_workflows_status CHECK (status IN (
    'PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
    'PRE_CHECK_SUCCEEDED', 'PRE_CHECK_NEEDS_REVIEW', 'PRE_CHECK_CANCELLED',
    'GENERATION_PENDING', 'GENERATION_RUNNING', 'GENERATION_CANCELLED',
    'ASSUMPTIONS_REVIEW_PENDING', 'GENERATION_COMPLETED', 'GENERATION_FAILED',
    'TECHNICAL_FAILURE'));

ALTER TABLE ai_plan_generation_workflows ADD CONSTRAINT ck_ai_workflows_active_run CHECK (
    status NOT IN ('PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
                   'GENERATION_PENDING', 'GENERATION_RUNNING')
    OR (active_run_id IS NOT NULL AND run_expires_at IS NOT NULL));

CREATE INDEX ix_ai_workflows_run_expiry
    ON ai_plan_generation_workflows (run_expires_at)
    WHERE status IN ('PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
                     'GENERATION_PENDING', 'GENERATION_RUNNING');

-- Together with uk_ai_workflows_project this documents and enforces the narrower
-- invariant explicitly, even if the workflow history is split into several rows later.
CREATE UNIQUE INDEX uk_ai_workflows_active_project
    ON ai_plan_generation_workflows (project_id)
    WHERE status IN ('PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
                     'GENERATION_PENDING', 'GENERATION_RUNNING');
