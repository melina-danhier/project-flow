CREATE TABLE ai_plan_generation_workflows (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    project_id UUID NOT NULL,
    confirmed_snapshot JSONB NOT NULL,
    snapshot_version VARCHAR(50) NOT NULL,
    completion_token UUID NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PRE_CHECK_PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_technical_error VARCHAR(2000),
    pre_check_result JSONB,
    CONSTRAINT pk_ai_plan_generation_workflows PRIMARY KEY (id),
    CONSTRAINT uk_ai_workflows_project UNIQUE (project_id),
    CONSTRAINT uk_ai_workflows_completion_token UNIQUE (completion_token),
    CONSTRAINT fk_ai_workflows_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_workflows_status CHECK (status IN (
        'PRE_CHECK_PENDING',
        'PRE_CHECK_RUNNING',
        'PRE_CHECK_RETRY_PENDING',
        'PRE_CHECK_PASSED',
        'PRE_CHECK_NEEDS_REVIEW',
        'TECHNICAL_FAILURE'
    )),
    CONSTRAINT ck_ai_workflows_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX ix_ai_workflows_status ON ai_plan_generation_workflows (status);
