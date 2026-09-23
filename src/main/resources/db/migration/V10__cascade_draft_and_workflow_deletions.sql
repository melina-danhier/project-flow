ALTER TABLE projects
    ADD COLUMN plan_confirmed_at TIMESTAMP(6) WITH TIME ZONE;

-- Draft cascades
ALTER TABLE draft_task_prerequisites
    DROP CONSTRAINT fk_draft_prerequisites_successor,
    ADD CONSTRAINT fk_draft_prerequisites_successor
        FOREIGN KEY (successor_draft_task_id) REFERENCES draft_tasks (id) ON DELETE CASCADE;

ALTER TABLE draft_task_prerequisites
    DROP CONSTRAINT fk_draft_prerequisites_prerequisite,
    ADD CONSTRAINT fk_draft_prerequisites_prerequisite
        FOREIGN KEY (prerequisite_draft_task_id) REFERENCES draft_tasks (id) ON DELETE CASCADE;

ALTER TABLE draft_tasks
    DROP CONSTRAINT fk_draft_tasks_plan_element,
    ADD CONSTRAINT fk_draft_tasks_plan_element
        FOREIGN KEY (id) REFERENCES draft_plan_elements (id) ON DELETE CASCADE;

ALTER TABLE draft_milestones
    DROP CONSTRAINT fk_draft_milestones_plan_element,
    ADD CONSTRAINT fk_draft_milestones_plan_element
        FOREIGN KEY (id) REFERENCES draft_plan_elements (id) ON DELETE CASCADE;

ALTER TABLE draft_plan_elements
    DROP CONSTRAINT fk_draft_elements_section,
    ADD CONSTRAINT fk_draft_elements_section
        FOREIGN KEY (draft_section_id) REFERENCES draft_sections (id) ON DELETE CASCADE;

ALTER TABLE draft_plan_elements
    DROP CONSTRAINT fk_draft_elements_plan_draft,
    ADD CONSTRAINT fk_draft_elements_plan_draft
        FOREIGN KEY (plan_draft_id) REFERENCES plan_drafts (id) ON DELETE CASCADE;

ALTER TABLE draft_sections
    DROP CONSTRAINT fk_draft_sections_plan_draft,
    ADD CONSTRAINT fk_draft_sections_plan_draft
        FOREIGN KEY (plan_draft_id) REFERENCES plan_drafts (id) ON DELETE CASCADE;

ALTER TABLE plan_drafts
    DROP CONSTRAINT fk_plan_drafts_project,
    ADD CONSTRAINT fk_plan_drafts_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

-- Workflow cascades
ALTER TABLE ai_workflow_completion_tokens
    DROP CONSTRAINT fk_ai_workflow_completion_tokens_workflow,
    ADD CONSTRAINT fk_ai_workflow_completion_tokens_workflow
        FOREIGN KEY (workflow_id) REFERENCES ai_plan_generation_workflows (id) ON DELETE CASCADE;

ALTER TABLE ai_workflow_acknowledged_warnings
    DROP CONSTRAINT fk_ai_workflow_acknowledged_warnings_workflow,
    ADD CONSTRAINT fk_ai_workflow_acknowledged_warnings_workflow
        FOREIGN KEY (workflow_id) REFERENCES ai_plan_generation_workflows (id) ON DELETE CASCADE;

ALTER TABLE ai_workflow_open_point_contexts
    DROP CONSTRAINT fk_ai_workflow_open_point_contexts_workflow,
    ADD CONSTRAINT fk_ai_workflow_open_point_contexts_workflow
        FOREIGN KEY (workflow_id) REFERENCES ai_plan_generation_workflows (id) ON DELETE CASCADE;
