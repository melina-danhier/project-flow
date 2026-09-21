ALTER TABLE projects
    ADD COLUMN planned_duration_days INTEGER;

ALTER TABLE projects
    ADD CONSTRAINT ck_projects_planned_duration
        CHECK (planned_duration_days IS NULL OR planned_duration_days > 0);
