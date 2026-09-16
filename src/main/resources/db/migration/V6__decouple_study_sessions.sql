DROP INDEX IF EXISTS ix_study_sessions_participant;

ALTER TABLE study_sessions
    ADD COLUMN consent_given_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE study_sessions
    ADD COLUMN status VARCHAR(20);
UPDATE study_sessions
SET status = CASE WHEN completed_at IS NULL THEN 'ABORTED' ELSE 'COMPLETED' END
WHERE status IS NULL;
ALTER TABLE study_sessions ALTER COLUMN status SET NOT NULL;
ALTER TABLE study_sessions
    ADD CONSTRAINT ck_study_sessions_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABORTED'));

ALTER TABLE study_sessions DROP COLUMN participant_id;

ALTER TABLE study_events DROP CONSTRAINT ck_study_events_type;
ALTER TABLE study_events
    ADD CONSTRAINT ck_study_events_type CHECK (event_type IN (
        'STUDY_STARTED', 'STUDY_TASK_COMPLETED', 'STUDY_COMPLETED', 'STUDY_ABORTED',
        'PLAN_GENERATED', 'DRAFT_ITEM_ACCEPTED', 'DRAFT_ITEM_REJECTED', 'DRAFT_ITEM_EDITED',
        'PLAN_REGENERATED', 'PLAN_ADOPTED', 'LOCAL_AI_CHANGE_STARTED',
        'LOCAL_AI_CHANGE_ADOPTED', 'LOCAL_AI_CHANGE_REJECTED', 'PLAN_AI_CHANGE_STARTED',
        'PLAN_AI_CHANGE_ADOPTED', 'PLAN_AI_CHANGE_REJECTED', 'AI_EDIT_STARTED',
        'AI_EDIT_ADOPTED', 'AI_EDIT_REJECTED'
    ));

ALTER TABLE app_users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE app_users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE app_users ADD COLUMN study_account BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_account_credentials CHECK (
        (study_account = TRUE AND email IS NULL AND password_hash IS NULL)
        OR
        (study_account = FALSE AND email IS NOT NULL AND password_hash IS NOT NULL)
    );
