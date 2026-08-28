-- Generation metadata belongs to the workflow, not to a visible draft.
ALTER TABLE ai_plan_generation_workflows ADD COLUMN model_name VARCHAR(100);
UPDATE ai_plan_generation_workflows
SET model_name = (
    SELECT draft.model_name FROM plan_drafts draft
    WHERE draft.project_id = ai_plan_generation_workflows.project_id
);

-- Only remove legacy empty generation placeholders. A populated invalid draft
-- deliberately fails the new status constraint and requires manual inspection.
DELETE FROM plan_drafts
WHERE status IN ('GENERATING', 'FAILED')
  AND NOT EXISTS (SELECT 1 FROM draft_sections s WHERE s.plan_draft_id = plan_drafts.id)
  AND NOT EXISTS (SELECT 1 FROM draft_plan_elements e WHERE e.plan_draft_id = plan_drafts.id);

ALTER TABLE plan_drafts ALTER COLUMN status SET DEFAULT 'READY_FOR_REVIEW';
ALTER TABLE plan_drafts DROP CONSTRAINT ck_plan_drafts_status;
ALTER TABLE plan_drafts ADD CONSTRAINT ck_plan_drafts_status CHECK (
    status IN ('READY_FOR_REVIEW', 'IN_REVIEW', 'APPLYING', 'APPLIED'));
ALTER TABLE plan_drafts DROP CONSTRAINT ck_plan_drafts_attempt_count;
ALTER TABLE plan_drafts DROP COLUMN attempt_count;
ALTER TABLE plan_drafts DROP COLUMN last_error;
ALTER TABLE plan_drafts DROP COLUMN model_name;
ALTER TABLE plan_drafts DROP COLUMN prompt_version;
ALTER TABLE plan_drafts DROP COLUMN schema_version;
