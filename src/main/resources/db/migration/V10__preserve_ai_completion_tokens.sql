CREATE TABLE ai_workflow_completion_tokens (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    completion_token UUID NOT NULL,
    workflow_id UUID NOT NULL,
    CONSTRAINT pk_ai_workflow_completion_tokens PRIMARY KEY (id),
    CONSTRAINT uk_ai_workflow_completion_tokens_token UNIQUE (completion_token),
    CONSTRAINT fk_ai_workflow_completion_tokens_workflow
        FOREIGN KEY (workflow_id) REFERENCES ai_plan_generation_workflows (id) ON DELETE CASCADE
);

CREATE INDEX ix_ai_workflow_completion_tokens_workflow
    ON ai_workflow_completion_tokens (workflow_id);

INSERT INTO ai_workflow_completion_tokens (
    id, created_at, updated_at, lock_version, completion_token, workflow_id
)
SELECT id, created_at, updated_at, 0, completion_token, id
FROM ai_plan_generation_workflows;
