CREATE TABLE ai_workflow_open_point_contexts (
    workflow_id UUID NOT NULL,
    problem_index INTEGER NOT NULL,
    confirmed_context VARCHAR(1000) NOT NULL,
    CONSTRAINT pk_ai_workflow_open_point_contexts PRIMARY KEY (workflow_id, problem_index),
    CONSTRAINT fk_ai_workflow_open_point_contexts_workflow
        FOREIGN KEY (workflow_id) REFERENCES ai_plan_generation_workflows(id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_workflow_open_point_context_index CHECK (problem_index >= 0),
    CONSTRAINT ck_ai_workflow_open_point_context_not_blank CHECK (btrim(confirmed_context) <> '')
);
