UPDATE ai_plan_generation_workflows
SET active_run_id = NULL,
    run_expires_at = NULL
WHERE status NOT IN ('PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
                     'GENERATION_PENDING', 'GENERATION_RUNNING');

ALTER TABLE ai_plan_generation_workflows DROP CONSTRAINT ck_ai_workflows_active_run;
ALTER TABLE ai_plan_generation_workflows ADD CONSTRAINT ck_ai_workflows_active_run CHECK (
    (status IN ('PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
                'GENERATION_PENDING', 'GENERATION_RUNNING')
        AND active_run_id IS NOT NULL AND run_expires_at IS NOT NULL)
    OR
    (status NOT IN ('PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
                    'GENERATION_PENDING', 'GENERATION_RUNNING')
        AND active_run_id IS NULL AND run_expires_at IS NULL)
);
