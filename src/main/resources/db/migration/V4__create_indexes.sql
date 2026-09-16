CREATE INDEX ix_project_members_user ON project_members (user_id);
CREATE INDEX ix_project_members_project_active ON project_members (project_id, active);

CREATE INDEX ix_plan_sections_container ON plan_sections (plan_container_id);
CREATE INDEX ix_plan_sections_manual_order ON plan_sections (plan_container_id, sort_order, id);
CREATE INDEX ix_plan_elements_container ON plan_elements (plan_container_id);
CREATE INDEX ix_plan_elements_section ON plan_elements (plan_section_id);
CREATE INDEX ix_plan_elements_manual_order
    ON plan_elements (plan_container_id, plan_section_id, sort_order, id);

CREATE INDEX ix_task_prerequisites_prerequisite ON task_prerequisites (prerequisite_task_id);
CREATE INDEX ix_task_assignees_member ON task_assignees (project_member_id);
CREATE INDEX ix_task_comments_task_created ON task_comments (task_id, created_at);
CREATE INDEX ix_task_comments_author ON task_comments (author_id);
CREATE INDEX ix_milestone_comments_milestone_created ON milestone_comments (milestone_id, created_at);
CREATE INDEX ix_milestone_comments_author ON milestone_comments (author_id);

CREATE INDEX ix_draft_sections_plan_draft ON draft_sections (plan_draft_id);
CREATE INDEX ix_draft_sections_manual_order ON draft_sections (plan_draft_id, sort_order, id);
CREATE INDEX ix_draft_elements_plan_draft ON draft_plan_elements (plan_draft_id);
CREATE INDEX ix_draft_elements_section ON draft_plan_elements (draft_section_id);
CREATE INDEX ix_draft_elements_manual_order
    ON draft_plan_elements (plan_draft_id, draft_section_id, sort_order, id);
CREATE INDEX ix_draft_prerequisites_prerequisite
    ON draft_task_prerequisites (prerequisite_draft_task_id);

CREATE INDEX ix_ai_workflows_status ON ai_plan_generation_workflows (status);
CREATE INDEX ix_ai_workflows_run_expiry ON ai_plan_generation_workflows (run_expires_at)
    WHERE status IN ('PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
                     'GENERATION_PENDING', 'GENERATION_RUNNING');
CREATE INDEX ix_ai_workflow_completion_tokens_workflow
    ON ai_workflow_completion_tokens (workflow_id);

CREATE INDEX ix_study_sessions_participant ON study_sessions (participant_id);
CREATE INDEX ix_study_sessions_project ON study_sessions (project_id);
CREATE INDEX ix_study_events_session_time ON study_events (study_session_id, occurred_at);
CREATE INDEX ix_ai_feedback_context ON ai_feedback (context);
