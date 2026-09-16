CREATE TABLE study_sessions (
    id UUID NOT NULL,
    participant_id VARCHAR(64) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    project_id UUID,
    current_phase VARCHAR(20),
    CONSTRAINT pk_study_sessions PRIMARY KEY (id),
    CONSTRAINT fk_study_sessions_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL,
    CONSTRAINT ck_study_sessions_current_phase CHECK (current_phase IN ('TASK_1', 'TASK_2'))
);

CREATE TABLE study_events (
    id UUID NOT NULL,
    study_session_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    study_phase VARCHAR(20) NOT NULL,
    CONSTRAINT pk_study_events PRIMARY KEY (id),
    CONSTRAINT fk_study_events_session FOREIGN KEY (study_session_id) REFERENCES study_sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_study_events_type CHECK (event_type IN (
        'PLAN_GENERATED', 'DRAFT_ITEM_ACCEPTED', 'DRAFT_ITEM_REJECTED', 'DRAFT_ITEM_EDITED',
        'PLAN_REGENERATED', 'PLAN_ADOPTED', 'LOCAL_AI_CHANGE_STARTED',
        'LOCAL_AI_CHANGE_ADOPTED', 'LOCAL_AI_CHANGE_REJECTED', 'PLAN_AI_CHANGE_STARTED',
        'PLAN_AI_CHANGE_ADOPTED', 'PLAN_AI_CHANGE_REJECTED', 'AI_EDIT_STARTED',
        'AI_EDIT_ADOPTED', 'AI_EDIT_REJECTED'
    )),
    CONSTRAINT ck_study_events_phase CHECK (study_phase IN ('TASK_1', 'TASK_2'))
);

CREATE TABLE ai_feedback (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    context VARCHAR(30) NOT NULL,
    action_id UUID NOT NULL,
    rating SMALLINT NOT NULL,
    comment VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ai_feedback PRIMARY KEY (id),
    CONSTRAINT fk_ai_feedback_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT uk_ai_feedback_action UNIQUE (user_id, context, action_id),
    CONSTRAINT ck_ai_feedback_context CHECK (
        context IN ('PLAN_ADOPTED', 'DRAFT_DELETED', 'AI_EDIT_ADOPTED', 'AI_EDIT_REJECTED')
    ),
    CONSTRAINT ck_ai_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);
