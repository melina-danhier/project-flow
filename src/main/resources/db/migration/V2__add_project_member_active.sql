ALTER TABLE project_members
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX ix_project_members_project_active
    ON project_members (project_id, active);

CREATE UNIQUE INDEX uk_project_members_single_owner
    ON project_members (project_id)
    WHERE role = 'OWNER';
