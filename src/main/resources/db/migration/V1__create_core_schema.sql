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
    CONSTRAINT ck_plan_containers_structure_mode CHECK (structure_mode IN ('TEMPORAL', 'THEMATIC')),
    CONSTRAINT ck_plan_containers_sort_mode CHECK (sort_mode IN ('MANUAL', 'DATE'))
);

CREATE TABLE projects (
    id UUID NOT NULL,
    start_date DATE,
    end_date DATE,
    category VARCHAR(50),
    other_project_type_description VARCHAR(100),
    subcategory VARCHAR(100),
    collaboration_mode VARCHAR(20),
    creation_type VARCHAR(20) NOT NULL,
    location VARCHAR(20) NOT NULL DEFAULT 'OVERVIEW',
    task_progress_display VARCHAR(20) NOT NULL DEFAULT 'CHECKBOX',
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_plan_container FOREIGN KEY (id) REFERENCES plan_containers (id),
    CONSTRAINT ck_projects_creation_type CHECK (creation_type IN ('EMPTY', 'TEMPLATE', 'AI')),
    CONSTRAINT ck_projects_location CHECK (location IN ('OVERVIEW', 'DRAFT', 'TRASH', 'ARCHIVE')),
    CONSTRAINT ck_projects_category CHECK (category IS NULL OR category IN (
        'EDUCATION', 'SOFTWARE_TECHNOLOGY', 'EVENT', 'HOME', 'CREATIVE', 'CAREER',
        'HEALTH_PERSONAL_DEVELOPMENT', 'TRAVEL', 'OTHER'
    )),
    CONSTRAINT ck_projects_collaboration_mode CHECK (
        collaboration_mode IS NULL OR collaboration_mode IN ('INDIVIDUAL', 'GROUP')
    ),
    CONSTRAINT ck_projects_subcategory CHECK (
        subcategory IS NULL OR (category IS NOT NULL AND (
            (category = 'EDUCATION' AND subcategory IN ('PRESENTATION_OR_REPORT', 'EXAM_PREPARATION', 'LEARNING_PLAN', 'TERM_PAPER', 'THESIS', 'OTHER_EDUCATION'))
            OR (category = 'SOFTWARE_TECHNOLOGY' AND subcategory IN ('SOFTWARE_PROJECT', 'WEB_OR_MOBILE_APP', 'EXTEND_EXISTING_APPLICATION', 'WEBSITE', 'DATABASE_PROJECT', 'HARDWARE_OR_RASPBERRY_PI_PROJECT', 'OTHER_SOFTWARE_AND_TECHNOLOGY'))
            OR (category = 'EVENT' AND subcategory IN ('PRIVATE_CELEBRATION', 'WORKSHOP_TRAINING_OR_INFORMATION_EVENT', 'CLUB_OR_COMMUNITY_EVENT', 'CONCERT_OR_PERFORMANCE', 'FLEA_MARKET_OR_SALES_EVENT', 'FUNDRAISING_EVENT', 'TOURNAMENT_OR_COMPETITION', 'STUDY_EVENT', 'OTHER_EVENT'))
            OR (category = 'HOME' AND subcategory IN ('MOVING', 'RENOVATION_OR_HOME_PROJECT', 'DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION', 'GARDEN_PROJECT', 'OTHER_HOME'))
            OR (category = 'CREATIVE' AND subcategory IN ('WRITING_PROJECT', 'PODCAST', 'VIDEO_OR_SHORT_FILM_PROJECT', 'PHOTO_OR_GRAPHIC_PROJECT', 'MUSIC_PROJECT', 'EXHIBITION', 'BLOG_OR_SOCIAL_MEDIA_CAMPAIGN', 'BOARD_GAME_OR_CREATIVE_PROTOTYPE', 'OTHER_CREATIVE_PROJECT'))
            OR (category = 'CAREER' AND subcategory IN ('JOB_SEARCH_AND_APPLICATION', 'CREATE_PORTFOLIO', 'TRAINING_OR_CERTIFICATION', 'ONBOARDING_PLAN', 'PROFESSIONAL_PRESENTATION', 'PROCESS_IMPROVEMENT', 'PRODUCT_OR_BUSINESS_IDEA', 'OTHER_CAREER'))
            OR (category = 'HEALTH_PERSONAL_DEVELOPMENT' AND subcategory IN ('FITNESS_OR_RUNNING_GOAL', 'COMPETITION_PREPARATION', 'NUTRITION_PROJECT', 'HABIT_OR_PERSONAL_CHALLENGE', 'DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE', 'OTHER_HEALTH_AND_PERSONAL_DEVELOPMENT'))
            OR (category = 'TRAVEL' AND subcategory IN ('TRIP_OR_VACATION', 'ROAD_TRIP', 'FESTIVAL_OR_CONCERT_TRIP', 'CAMPING_TRIP', 'BICYCLE_TOUR', 'OTHER_TRAVEL'))
        ))
    ),
    CONSTRAINT ck_projects_task_progress_display CHECK (task_progress_display IN ('CHECKBOX', 'STATUS'))
);

CREATE TABLE plan_templates (
    id UUID NOT NULL,
    category VARCHAR(50),
    other_project_type_description VARCHAR(100),
    subcategory VARCHAR(100),
    recommended_duration_days INTEGER,
    collaboration_mode VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    template_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_plan_templates PRIMARY KEY (id),
    CONSTRAINT fk_plan_templates_plan_container FOREIGN KEY (id) REFERENCES plan_containers (id),
    CONSTRAINT ck_plan_templates_category CHECK (category IS NULL OR category IN (
        'EDUCATION', 'SOFTWARE_TECHNOLOGY', 'EVENT', 'HOME', 'CREATIVE', 'CAREER',
        'HEALTH_PERSONAL_DEVELOPMENT', 'TRAVEL', 'OTHER'
    )),
    CONSTRAINT ck_plan_templates_subcategory CHECK (
        subcategory IS NULL OR (category IS NOT NULL AND (
            (category = 'EDUCATION' AND subcategory IN ('PRESENTATION_OR_REPORT', 'EXAM_PREPARATION', 'LEARNING_PLAN', 'TERM_PAPER', 'THESIS', 'OTHER_EDUCATION'))
            OR (category = 'SOFTWARE_TECHNOLOGY' AND subcategory IN ('SOFTWARE_PROJECT', 'WEB_OR_MOBILE_APP', 'EXTEND_EXISTING_APPLICATION', 'WEBSITE', 'DATABASE_PROJECT', 'HARDWARE_OR_RASPBERRY_PI_PROJECT', 'OTHER_SOFTWARE_AND_TECHNOLOGY'))
            OR (category = 'EVENT' AND subcategory IN ('PRIVATE_CELEBRATION', 'WORKSHOP_TRAINING_OR_INFORMATION_EVENT', 'CLUB_OR_COMMUNITY_EVENT', 'CONCERT_OR_PERFORMANCE', 'FLEA_MARKET_OR_SALES_EVENT', 'FUNDRAISING_EVENT', 'TOURNAMENT_OR_COMPETITION', 'STUDY_EVENT', 'OTHER_EVENT'))
            OR (category = 'HOME' AND subcategory IN ('MOVING', 'RENOVATION_OR_HOME_PROJECT', 'DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION', 'GARDEN_PROJECT', 'OTHER_HOME'))
            OR (category = 'CREATIVE' AND subcategory IN ('WRITING_PROJECT', 'PODCAST', 'VIDEO_OR_SHORT_FILM_PROJECT', 'PHOTO_OR_GRAPHIC_PROJECT', 'MUSIC_PROJECT', 'EXHIBITION', 'BLOG_OR_SOCIAL_MEDIA_CAMPAIGN', 'BOARD_GAME_OR_CREATIVE_PROTOTYPE', 'OTHER_CREATIVE_PROJECT'))
            OR (category = 'CAREER' AND subcategory IN ('JOB_SEARCH_AND_APPLICATION', 'CREATE_PORTFOLIO', 'TRAINING_OR_CERTIFICATION', 'ONBOARDING_PLAN', 'PROFESSIONAL_PRESENTATION', 'PROCESS_IMPROVEMENT', 'PRODUCT_OR_BUSINESS_IDEA', 'OTHER_CAREER'))
            OR (category = 'HEALTH_PERSONAL_DEVELOPMENT' AND subcategory IN ('FITNESS_OR_RUNNING_GOAL', 'COMPETITION_PREPARATION', 'NUTRITION_PROJECT', 'HABIT_OR_PERSONAL_CHALLENGE', 'DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE', 'OTHER_HEALTH_AND_PERSONAL_DEVELOPMENT'))
            OR (category = 'TRAVEL' AND subcategory IN ('TRIP_OR_VACATION', 'ROAD_TRIP', 'FESTIVAL_OR_CONCERT_TRIP', 'CAMPING_TRIP', 'BICYCLE_TOUR', 'OTHER_TRAVEL'))
        ))
    ),
    CONSTRAINT ck_plan_templates_collaboration_mode CHECK (collaboration_mode IN ('INDIVIDUAL', 'GROUP', 'BOTH')),
    CONSTRAINT ck_plan_templates_duration CHECK (recommended_duration_days IS NULL OR recommended_duration_days > 0),
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
    active BOOLEAN NOT NULL DEFAULT TRUE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_project_members PRIMARY KEY (id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT ck_project_members_role CHECK (role IN ('OWNER', 'MEMBER'))
);

-- PostgreSQL has no table-level partial UNIQUE constraint syntax.
CREATE UNIQUE INDEX uk_project_members_single_owner
    ON project_members (project_id) WHERE role = 'OWNER';

CREATE TABLE plan_sections (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    plan_container_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000),
    sort_order INTEGER NOT NULL DEFAULT 0,
    origin VARCHAR(20) NOT NULL,
    CONSTRAINT pk_plan_sections PRIMARY KEY (id),
    CONSTRAINT fk_plan_sections_container FOREIGN KEY (plan_container_id) REFERENCES plan_containers (id),
    CONSTRAINT ck_plan_sections_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_plan_sections_origin CHECK (origin IN ('USER', 'TEMPLATE', 'TEMPLATE_MODIFIED', 'AI', 'AI_MODIFIED'))
);

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
    CONSTRAINT pk_plan_elements PRIMARY KEY (id),
    CONSTRAINT fk_plan_elements_container FOREIGN KEY (plan_container_id) REFERENCES plan_containers (id),
    CONSTRAINT fk_plan_elements_section FOREIGN KEY (plan_section_id) REFERENCES plan_sections (id),
    CONSTRAINT ck_plan_elements_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_plan_elements_origin CHECK (origin IN ('USER', 'TEMPLATE', 'TEMPLATE_MODIFIED', 'AI', 'AI_MODIFIED'))
);

CREATE TABLE tasks (
    id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    start_date DATE,
    due_date DATE,
    estimated_hours INTEGER,
    relative_start_day INTEGER,
    relative_due_day INTEGER,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_plan_element FOREIGN KEY (id) REFERENCES plan_elements (id),
    CONSTRAINT ck_tasks_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_tasks_estimated_hours CHECK (estimated_hours IS NULL OR estimated_hours > 0),
    CONSTRAINT ck_tasks_relative_start CHECK (relative_start_day IS NULL OR relative_start_day >= 0),
    CONSTRAINT ck_tasks_relative_due CHECK (relative_due_day IS NULL OR relative_due_day >= 0)
);

CREATE TABLE milestones (
    id UUID NOT NULL,
    due_date DATE,
    relative_due_day INTEGER,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_milestones PRIMARY KEY (id),
    CONSTRAINT fk_milestones_plan_element FOREIGN KEY (id) REFERENCES plan_elements (id),
    CONSTRAINT ck_milestones_relative_due CHECK (relative_due_day IS NULL OR relative_due_day >= 0)
);

CREATE TABLE task_prerequisites (
    successor_task_id UUID NOT NULL,
    prerequisite_task_id UUID NOT NULL,
    CONSTRAINT pk_task_prerequisites PRIMARY KEY (successor_task_id, prerequisite_task_id),
    CONSTRAINT fk_task_prerequisites_successor FOREIGN KEY (successor_task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_prerequisites_prerequisite FOREIGN KEY (prerequisite_task_id) REFERENCES tasks (id),
    CONSTRAINT ck_task_prerequisites_not_self CHECK (successor_task_id <> prerequisite_task_id)
);

CREATE TABLE task_assignees (
    task_id UUID NOT NULL,
    project_member_id UUID NOT NULL,
    CONSTRAINT pk_task_assignees PRIMARY KEY (task_id, project_member_id),
    CONSTRAINT fk_task_assignees_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_assignees_member FOREIGN KEY (project_member_id) REFERENCES project_members (id) ON DELETE CASCADE
);

CREATE TABLE task_comments (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    task_id UUID NOT NULL,
    author_id UUID NOT NULL,
    content VARCHAR(2000) NOT NULL,
    CONSTRAINT pk_task_comments PRIMARY KEY (id),
    CONSTRAINT fk_task_comments_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_comments_author FOREIGN KEY (author_id) REFERENCES project_members (id) ON DELETE CASCADE,
    CONSTRAINT ck_task_comments_content CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 2000)
);

CREATE TABLE milestone_comments (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    milestone_id UUID NOT NULL,
    author_id UUID NOT NULL,
    content VARCHAR(2000) NOT NULL,
    CONSTRAINT pk_milestone_comments PRIMARY KEY (id),
    CONSTRAINT fk_milestone_comments_milestone FOREIGN KEY (milestone_id) REFERENCES milestones (id) ON DELETE CASCADE,
    CONSTRAINT fk_milestone_comments_author FOREIGN KEY (author_id) REFERENCES project_members (id) ON DELETE CASCADE,
    CONSTRAINT ck_milestone_comments_content CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 2000)
);

CREATE TABLE plan_drafts (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    project_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'READY_FOR_REVIEW',
    generated_at TIMESTAMP(6) WITH TIME ZONE,
    applied_at TIMESTAMP(6) WITH TIME ZONE,
    sort_mode VARCHAR(20) NOT NULL DEFAULT 'DATE',
    CONSTRAINT pk_plan_drafts PRIMARY KEY (id),
    CONSTRAINT uk_plan_drafts_project UNIQUE (project_id),
    CONSTRAINT fk_plan_drafts_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT ck_plan_drafts_status CHECK (status IN ('READY_FOR_REVIEW', 'IN_REVIEW', 'APPLYING', 'APPLIED')),
    CONSTRAINT ck_plan_drafts_sort_mode CHECK (sort_mode IN ('MANUAL', 'DATE')),
    CONSTRAINT ck_plan_drafts_applied_at CHECK (
        (status = 'APPLIED' AND applied_at IS NOT NULL)
        OR (status <> 'APPLIED' AND applied_at IS NULL)
    )
);

CREATE TABLE draft_sections (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    plan_draft_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000),
    sort_order INTEGER NOT NULL DEFAULT 0,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    origin VARCHAR(20) NOT NULL DEFAULT 'AI',
    CONSTRAINT pk_draft_sections PRIMARY KEY (id),
    CONSTRAINT fk_draft_sections_plan_draft FOREIGN KEY (plan_draft_id) REFERENCES plan_drafts (id),
    CONSTRAINT ck_draft_sections_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_draft_sections_review_status CHECK (review_status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_draft_sections_origin CHECK (origin IN ('USER', 'TEMPLATE', 'TEMPLATE_MODIFIED', 'AI', 'AI_MODIFIED'))
);

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
    ai_origin VARCHAR(20) NOT NULL DEFAULT 'AI',
    CONSTRAINT pk_draft_plan_elements PRIMARY KEY (id),
    CONSTRAINT fk_draft_elements_plan_draft FOREIGN KEY (plan_draft_id) REFERENCES plan_drafts (id),
    CONSTRAINT fk_draft_elements_section FOREIGN KEY (draft_section_id) REFERENCES draft_sections (id),
    CONSTRAINT ck_draft_elements_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_draft_elements_review_status CHECK (review_status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_draft_elements_ai_origin CHECK (ai_origin IN ('USER', 'TEMPLATE', 'TEMPLATE_MODIFIED', 'AI', 'AI_MODIFIED'))
);

CREATE TABLE draft_tasks (
    id UUID NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    start_date DATE,
    due_date DATE,
    estimated_hours INTEGER,
    CONSTRAINT pk_draft_tasks PRIMARY KEY (id),
    CONSTRAINT fk_draft_tasks_plan_element FOREIGN KEY (id) REFERENCES draft_plan_elements (id),
    CONSTRAINT ck_draft_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_draft_tasks_estimated_hours CHECK (estimated_hours IS NULL OR estimated_hours > 0)
);

CREATE TABLE draft_milestones (
    id UUID NOT NULL,
    due_date DATE,
    CONSTRAINT pk_draft_milestones PRIMARY KEY (id),
    CONSTRAINT fk_draft_milestones_plan_element FOREIGN KEY (id) REFERENCES draft_plan_elements (id)
);

CREATE TABLE draft_task_prerequisites (
    successor_draft_task_id UUID NOT NULL,
    prerequisite_draft_task_id UUID NOT NULL,
    CONSTRAINT pk_draft_task_prerequisites PRIMARY KEY (successor_draft_task_id, prerequisite_draft_task_id),
    CONSTRAINT fk_draft_prerequisites_successor FOREIGN KEY (successor_draft_task_id) REFERENCES draft_tasks (id),
    CONSTRAINT fk_draft_prerequisites_prerequisite FOREIGN KEY (prerequisite_draft_task_id) REFERENCES draft_tasks (id),
    CONSTRAINT ck_draft_task_prerequisites_not_self CHECK (successor_draft_task_id <> prerequisite_draft_task_id)
);
