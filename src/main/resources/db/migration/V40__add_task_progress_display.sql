ALTER TABLE projects
    ADD COLUMN task_progress_display VARCHAR(20) NOT NULL DEFAULT 'CHECKBOX';

ALTER TABLE projects
    ADD CONSTRAINT chk_projects_task_progress_display
        CHECK (task_progress_display IN ('CHECKBOX', 'STATUS'));
