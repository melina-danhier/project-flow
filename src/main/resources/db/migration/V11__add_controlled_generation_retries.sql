ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN generation_prompt_version VARCHAR(100) NOT NULL DEFAULT 'generation-v1';
ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN generation_round_attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN generation_total_attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN last_error_retryable BOOLEAN;
ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN last_error_diagnosis VARCHAR(500);

ALTER TABLE ai_plan_generation_workflows
    ADD CONSTRAINT ck_ai_workflows_generation_round_attempt_count
        CHECK (generation_round_attempt_count >= 0);
ALTER TABLE ai_plan_generation_workflows
    ADD CONSTRAINT ck_ai_workflows_generation_total_attempt_count
        CHECK (generation_total_attempt_count >= 0);
ALTER TABLE ai_plan_generation_workflows
    ADD CONSTRAINT ck_ai_workflows_generation_attempt_counts
        CHECK (generation_total_attempt_count >= generation_round_attempt_count);

CREATE TABLE ai_workflow_acknowledged_warnings (
    workflow_id UUID NOT NULL,
    problem_index INTEGER NOT NULL,
    CONSTRAINT pk_ai_workflow_acknowledged_warnings PRIMARY KEY (workflow_id, problem_index),
    CONSTRAINT fk_ai_workflow_acknowledged_warnings_workflow
        FOREIGN KEY (workflow_id) REFERENCES ai_plan_generation_workflows (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_workflow_acknowledged_warning_index CHECK (problem_index >= 0)
);
