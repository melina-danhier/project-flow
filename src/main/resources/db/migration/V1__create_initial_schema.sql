CREATE TABLE app_users (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT uk_app_users_email UNIQUE (email)
);

CREATE TABLE plan_containers (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000),
    structure_mode VARCHAR(20) NOT NULL DEFAULT 'TEMPORAL',
    sort_mode VARCHAR(20) NOT NULL DEFAULT 'DATE',
    CONSTRAINT pk_plan_containers PRIMARY KEY (id),
    CONSTRAINT ck_plan_containers_structure_mode
        CHECK (structure_mode IN ('TEMPORAL', 'THEMATIC')),
    CONSTRAINT ck_plan_containers_sort_mode
        CHECK (sort_mode IN ('MANUAL', 'DATE'))
);

CREATE TABLE projects (
    id UUID NOT NULL,
    start_date DATE,
    end_date DATE,
    creation_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    location VARCHAR(20) NOT NULL DEFAULT 'OVERVIEW',
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_plan_container
        FOREIGN KEY (id) REFERENCES plan_containers (id),
    CONSTRAINT ck_projects_creation_type
        CHECK (creation_type IN ('EMPTY', 'TEMPLATE', 'AI')),
    CONSTRAINT ck_projects_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED')),
    CONSTRAINT ck_projects_location
        CHECK (location IN ('OVERVIEW', 'TRASH', 'ARCHIVE'))
);

CREATE TABLE plan_templates (
    id UUID NOT NULL,
    category VARCHAR(50) NOT NULL,
    project_type VARCHAR(100) NOT NULL,
    recommended_duration_days INTEGER,
    collaboration_mode VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    template_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_plan_templates PRIMARY KEY (id),
    CONSTRAINT fk_plan_templates_plan_container
        FOREIGN KEY (id) REFERENCES plan_containers (id),
    CONSTRAINT ck_plan_templates_category CHECK (category IN (
        'EDUCATION',
        'SOFTWARE_TECHNOLOGY',
        'EVENT',
        'HOME',
        'CREATIVE',
        'CAREER',
        'HEALTH_PERSONAL_DEVELOPMENT',
        'TRAVEL'
    )),
    CONSTRAINT ck_plan_templates_collaboration_mode
        CHECK (collaboration_mode IN ('INDIVIDUAL', 'GROUP', 'BOTH')),
    CONSTRAINT ck_plan_templates_duration
        CHECK (recommended_duration_days IS NULL OR recommended_duration_days > 0),
    CONSTRAINT ck_plan_templates_version CHECK (template_version >= 1)
);

CREATE TABLE project_members (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (id),
    CONSTRAINT fk_project_members_project
        FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_members_user
        FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT ck_project_members_role CHECK (role IN ('OWNER', 'MEMBER'))
);

CREATE INDEX ix_project_members_user ON project_members (user_id);

CREATE TABLE plan_sections (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    plan_container_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000),
    start_date DATE,
    end_date DATE,
    relative_start_day INTEGER,
    relative_end_day INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 0,
    origin VARCHAR(20) NOT NULL,
    has_critical_assumption BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_plan_sections PRIMARY KEY (id),
    CONSTRAINT fk_plan_sections_container
        FOREIGN KEY (plan_container_id) REFERENCES plan_containers (id),
    CONSTRAINT ck_plan_sections_relative_start
        CHECK (relative_start_day IS NULL OR relative_start_day >= 0),
    CONSTRAINT ck_plan_sections_relative_end
        CHECK (relative_end_day IS NULL OR relative_end_day >= 0),
    CONSTRAINT ck_plan_sections_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_plan_sections_origin CHECK (origin IN ('USER', 'TEMPLATE', 'AI'))
);

CREATE INDEX ix_plan_sections_container ON plan_sections (plan_container_id);

CREATE TABLE plan_elements (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    plan_container_id UUID NOT NULL,
    plan_section_id UUID,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000),
    sort_order INTEGER NOT NULL DEFAULT 0,
    origin VARCHAR(20) NOT NULL,
    has_critical_assumption BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_plan_elements PRIMARY KEY (id),
    CONSTRAINT fk_plan_elements_container
        FOREIGN KEY (plan_container_id) REFERENCES plan_containers (id),
    CONSTRAINT fk_plan_elements_section
        FOREIGN KEY (plan_section_id) REFERENCES plan_sections (id),
    CONSTRAINT ck_plan_elements_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_plan_elements_origin CHECK (origin IN ('USER', 'TEMPLATE', 'AI'))
);

CREATE INDEX ix_plan_elements_container ON plan_elements (plan_container_id);
CREATE INDEX ix_plan_elements_section ON plan_elements (plan_section_id);

CREATE TABLE tasks (
    id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    start_date DATE,
    due_date DATE,
    relative_start_day INTEGER,
    relative_due_day INTEGER,
    assignee_id UUID,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_plan_element
        FOREIGN KEY (id) REFERENCES plan_elements (id),
    CONSTRAINT fk_tasks_assignee
        FOREIGN KEY (assignee_id) REFERENCES project_members (id),
    CONSTRAINT ck_tasks_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_tasks_relative_start
        CHECK (relative_start_day IS NULL OR relative_start_day >= 0),
    CONSTRAINT ck_tasks_relative_due
        CHECK (relative_due_day IS NULL OR relative_due_day >= 0)
);

CREATE INDEX ix_tasks_assignee ON tasks (assignee_id);

CREATE TABLE milestones (
    id UUID NOT NULL,
    due_date DATE,
    relative_due_day INTEGER,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_milestones PRIMARY KEY (id),
    CONSTRAINT fk_milestones_plan_element
        FOREIGN KEY (id) REFERENCES plan_elements (id),
    CONSTRAINT ck_milestones_relative_due
        CHECK (relative_due_day IS NULL OR relative_due_day >= 0)
);

CREATE TABLE task_prerequisites (
    successor_task_id UUID NOT NULL,
    prerequisite_task_id UUID NOT NULL,
    CONSTRAINT uk_task_prerequisites_pair
        UNIQUE (successor_task_id, prerequisite_task_id),
    CONSTRAINT fk_task_prerequisites_successor
        FOREIGN KEY (successor_task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_prerequisites_prerequisite
        FOREIGN KEY (prerequisite_task_id) REFERENCES tasks (id)
);

CREATE INDEX ix_task_prerequisites_prerequisite
    ON task_prerequisites (prerequisite_task_id);

CREATE TABLE plan_drafts (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    project_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'GENERATING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000),
    model_name VARCHAR(100),
    prompt_version VARCHAR(100) NOT NULL,
    schema_version VARCHAR(100) NOT NULL,
    generated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_plan_drafts PRIMARY KEY (id),
    CONSTRAINT uk_plan_drafts_project UNIQUE (project_id),
    CONSTRAINT fk_plan_drafts_project
        FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT ck_plan_drafts_status CHECK (status IN (
        'GENERATING',
        'FAILED',
        'READY_FOR_REVIEW',
        'IN_REVIEW',
        'APPLYING'
    )),
    CONSTRAINT ck_plan_drafts_attempt_count CHECK (attempt_count >= 0)
);

CREATE TABLE draft_sections (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    plan_draft_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000),
    start_date DATE,
    end_date DATE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    user_modified BOOLEAN NOT NULL DEFAULT FALSE,
    has_critical_assumption BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_draft_sections PRIMARY KEY (id),
    CONSTRAINT fk_draft_sections_plan_draft
        FOREIGN KEY (plan_draft_id) REFERENCES plan_drafts (id),
    CONSTRAINT ck_draft_sections_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_draft_sections_review_status
        CHECK (review_status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX ix_draft_sections_plan_draft ON draft_sections (plan_draft_id);

CREATE TABLE draft_plan_elements (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    plan_draft_id UUID NOT NULL,
    draft_section_id UUID,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000),
    sort_order INTEGER NOT NULL DEFAULT 0,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    user_modified BOOLEAN NOT NULL DEFAULT FALSE,
    has_critical_assumption BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_draft_plan_elements PRIMARY KEY (id),
    CONSTRAINT fk_draft_elements_plan_draft
        FOREIGN KEY (plan_draft_id) REFERENCES plan_drafts (id),
    CONSTRAINT fk_draft_elements_section
        FOREIGN KEY (draft_section_id) REFERENCES draft_sections (id),
    CONSTRAINT ck_draft_elements_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_draft_elements_review_status
        CHECK (review_status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX ix_draft_elements_plan_draft ON draft_plan_elements (plan_draft_id);
CREATE INDEX ix_draft_elements_section ON draft_plan_elements (draft_section_id);

CREATE TABLE draft_tasks (
    id UUID NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    start_date DATE,
    due_date DATE,
    CONSTRAINT pk_draft_tasks PRIMARY KEY (id),
    CONSTRAINT fk_draft_tasks_plan_element
        FOREIGN KEY (id) REFERENCES draft_plan_elements (id),
    CONSTRAINT ck_draft_tasks_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE TABLE draft_milestones (
    id UUID NOT NULL,
    due_date DATE,
    CONSTRAINT pk_draft_milestones PRIMARY KEY (id),
    CONSTRAINT fk_draft_milestones_plan_element
        FOREIGN KEY (id) REFERENCES draft_plan_elements (id)
);

CREATE TABLE draft_task_prerequisites (
    successor_draft_task_id UUID NOT NULL,
    prerequisite_draft_task_id UUID NOT NULL,
    CONSTRAINT uk_draft_task_prerequisites_pair
        UNIQUE (successor_draft_task_id, prerequisite_draft_task_id),
    CONSTRAINT fk_draft_prerequisites_successor
        FOREIGN KEY (successor_draft_task_id) REFERENCES draft_tasks (id),
    CONSTRAINT fk_draft_prerequisites_prerequisite
        FOREIGN KEY (prerequisite_draft_task_id) REFERENCES draft_tasks (id)
);

CREATE INDEX ix_draft_prerequisites_prerequisite
    ON draft_task_prerequisites (prerequisite_draft_task_id);
