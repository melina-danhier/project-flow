CREATE TABLE ai_plan_generation_workflows (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    project_id UUID NOT NULL,
    confirmed_snapshot JSONB NOT NULL,
    snapshot_version VARCHAR(50) NOT NULL,
    completion_token UUID NOT NULL,
    consent_confirmed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PRE_CHECK_PENDING',
    active_run_id UUID,
    run_expires_at TIMESTAMP(6) WITH TIME ZONE,
    pre_check_retry_count INTEGER NOT NULL DEFAULT 0,
    last_technical_error VARCHAR(50),
    last_ai_operation VARCHAR(30),
    pre_check_result JSONB,
    generated_plan JSONB,
    pre_check_prompt_version VARCHAR(100),
    generation_prompt_version VARCHAR(100),
    model_name VARCHAR(100),
    pre_check_schema_version VARCHAR(50),
    generation_schema_version VARCHAR(50),
    generation_round_attempt_count INTEGER NOT NULL DEFAULT 0,
    generation_total_attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error_retryable BOOLEAN,
    CONSTRAINT pk_ai_plan_generation_workflows PRIMARY KEY (id),
    CONSTRAINT uk_ai_workflows_project UNIQUE (project_id),
    CONSTRAINT uk_ai_workflows_completion_token UNIQUE (completion_token),
    CONSTRAINT fk_ai_workflows_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_workflows_status CHECK (status IN (
        'PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
        'PRE_CHECK_COMPLETED', 'PRE_CHECK_NEEDS_REVIEW', 'PRE_CHECK_CANCELLED',
        'GENERATION_PENDING', 'GENERATION_RUNNING', 'GENERATION_CANCELLED',
        'GENERATION_COMPLETED', 'GENERATION_FAILED', 'TECHNICAL_FAILURE'
    )),
    CONSTRAINT ck_ai_workflows_pre_check_retry_count CHECK (pre_check_retry_count >= 0),
    CONSTRAINT ck_ai_workflows_generation_round_attempt_count CHECK (generation_round_attempt_count >= 0),
    CONSTRAINT ck_ai_workflows_generation_total_attempt_count CHECK (generation_total_attempt_count >= 0),
    CONSTRAINT ck_ai_workflows_generation_attempt_counts CHECK (
        generation_total_attempt_count >= generation_round_attempt_count
    ),
    CONSTRAINT ck_ai_workflows_technical_error CHECK (
        last_technical_error IS NULL OR last_technical_error IN (
            'PROVIDER_UNAVAILABLE', 'PROVIDER_TIMEOUT', 'RATE_LIMIT_EXCEEDED',
            'CLIENT_CONFIGURATION_ERROR', 'INVALID_AI_RESPONSE', 'AI_REFUSAL',
            'RETRY_INTERRUPTED', 'UNKNOWN_AI_ERROR'
        )
    ),
    CONSTRAINT ck_ai_workflows_error_operation CHECK (
        (last_technical_error IS NULL AND last_ai_operation IS NULL)
        OR (last_technical_error IS NOT NULL AND last_ai_operation IN ('PRE_CHECK', 'PLAN_GENERATION'))
    ),
    CONSTRAINT ck_ai_workflows_active_run CHECK (
        (status IN ('PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
                    'GENERATION_PENDING', 'GENERATION_RUNNING')
            AND active_run_id IS NOT NULL AND run_expires_at IS NOT NULL)
        OR
        (status NOT IN ('PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
                        'GENERATION_PENDING', 'GENERATION_RUNNING')
            AND active_run_id IS NULL AND run_expires_at IS NULL)
    )
);

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

CREATE TABLE ai_workflow_acknowledged_warnings (
    workflow_id UUID NOT NULL,
    problem_index INTEGER NOT NULL,
    CONSTRAINT pk_ai_workflow_acknowledged_warnings PRIMARY KEY (workflow_id, problem_index),
    CONSTRAINT fk_ai_workflow_acknowledged_warnings_workflow
        FOREIGN KEY (workflow_id) REFERENCES ai_plan_generation_workflows (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_workflow_acknowledged_warning_index CHECK (problem_index >= 0)
);

CREATE TABLE ai_workflow_open_point_contexts (
    workflow_id UUID NOT NULL,
    problem_index INTEGER NOT NULL,
    confirmed_context VARCHAR(1000) NOT NULL,
    CONSTRAINT pk_ai_workflow_open_point_contexts PRIMARY KEY (workflow_id, problem_index),
    CONSTRAINT fk_ai_workflow_open_point_contexts_workflow
        FOREIGN KEY (workflow_id) REFERENCES ai_plan_generation_workflows (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_workflow_open_point_context_index CHECK (problem_index >= 0),
    CONSTRAINT ck_ai_workflow_open_point_context_not_blank CHECK (btrim(confirmed_context) <> '')
);
