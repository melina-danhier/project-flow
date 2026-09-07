ALTER TABLE projects DROP CONSTRAINT IF EXISTS ck_projects_draft_state;
ALTER TABLE projects DROP CONSTRAINT IF EXISTS ck_projects_status;
ALTER TABLE projects DROP COLUMN status;
