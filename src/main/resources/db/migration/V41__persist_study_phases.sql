ALTER TABLE study_sessions ADD COLUMN current_phase VARCHAR(20);
ALTER TABLE study_sessions ADD CONSTRAINT chk_study_sessions_current_phase
    CHECK (current_phase IN ('TASK_1', 'TASK_2'));

ALTER TABLE study_events ADD COLUMN study_phase VARCHAR(20);
UPDATE study_events SET study_phase = 'TASK_1';
ALTER TABLE study_events ALTER COLUMN study_phase SET NOT NULL;
ALTER TABLE study_events ADD CONSTRAINT chk_study_events_phase
    CHECK (study_phase IN ('TASK_1', 'TASK_2'));
