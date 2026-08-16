ALTER TABLE projects DROP CONSTRAINT IF EXISTS ck_projects_creation_type;
ALTER TABLE projects DROP CONSTRAINT IF EXISTS ck_projects_location;
ALTER TABLE projects DROP CONSTRAINT IF EXISTS ck_projects_draft_state;

UPDATE projects
SET creation_type = 'EMPTY'
WHERE creation_type = 'MANUAL';

UPDATE projects
SET location = 'DRAFT'
WHERE status = 'DRAFT';

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS category VARCHAR(50);

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS project_type VARCHAR(100);

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS collaboration_mode VARCHAR(20);

ALTER TABLE projects
    ADD CONSTRAINT ck_projects_creation_type
        CHECK (creation_type IN ('EMPTY', 'TEMPLATE', 'AI'));

ALTER TABLE projects
    ADD CONSTRAINT ck_projects_location
        CHECK (location IN ('OVERVIEW', 'DRAFT', 'TRASH', 'ARCHIVE'));

ALTER TABLE projects
    ADD CONSTRAINT ck_projects_category CHECK (category IS NULL OR category IN (
        'EDUCATION',
        'SOFTWARE_TECHNOLOGY',
        'EVENT',
        'HOME',
        'CREATIVE',
        'CAREER',
        'HEALTH_PERSONAL_DEVELOPMENT',
        'TRAVEL',
        'OTHER'
    ));

ALTER TABLE projects
    ADD CONSTRAINT ck_projects_collaboration_mode
        CHECK (collaboration_mode IS NULL OR collaboration_mode IN ('INDIVIDUAL', 'GROUP'));

ALTER TABLE projects
    ADD CONSTRAINT ck_projects_draft_state CHECK (
        (status = 'DRAFT' AND location = 'DRAFT')
        OR (status <> 'DRAFT' AND location <> 'DRAFT')
    );

ALTER TABLE plan_templates DROP CONSTRAINT IF EXISTS ck_plan_templates_category;

ALTER TABLE plan_templates
    ADD CONSTRAINT ck_plan_templates_category CHECK (category IN (
        'EDUCATION',
        'SOFTWARE_TECHNOLOGY',
        'EVENT',
        'HOME',
        'CREATIVE',
        'CAREER',
        'HEALTH_PERSONAL_DEVELOPMENT',
        'TRAVEL',
        'OTHER'
    ));
