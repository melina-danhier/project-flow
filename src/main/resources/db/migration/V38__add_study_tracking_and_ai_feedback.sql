CREATE TABLE study_sessions (
    id UUID PRIMARY KEY,
    participant_id VARCHAR(64) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL
);

CREATE INDEX ix_study_sessions_participant ON study_sessions (participant_id);
CREATE INDEX ix_study_sessions_project ON study_sessions (project_id);

CREATE TABLE study_events (
    id UUID PRIMARY KEY,
    study_session_id UUID NOT NULL REFERENCES study_sessions(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_study_events_session_time ON study_events (study_session_id, occurred_at);

CREATE TABLE ai_feedback (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    context VARCHAR(30) NOT NULL,
    action_id UUID NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_ai_feedback_action UNIQUE (user_id, context, action_id)
);

CREATE INDEX ix_ai_feedback_context ON ai_feedback (context);
