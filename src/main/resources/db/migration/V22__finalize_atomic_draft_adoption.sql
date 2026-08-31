ALTER TABLE plan_drafts
    ADD COLUMN applied_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE plan_drafts
SET applied_at = updated_at
WHERE status = 'APPLIED';

UPDATE ai_plan_generation_workflows
SET status = 'GENERATION_COMPLETED'
WHERE status = 'DRAFT_APPLIED';

ALTER TABLE ai_plan_generation_workflows DROP CONSTRAINT ck_ai_workflows_status;
ALTER TABLE ai_plan_generation_workflows ADD CONSTRAINT ck_ai_workflows_status CHECK (status IN (
    'PRE_CHECK_PENDING', 'PRE_CHECK_RUNNING', 'PRE_CHECK_RETRY_PENDING',
    'PRE_CHECK_NEEDS_REVIEW', 'GENERATION_PENDING', 'GENERATION_RUNNING',
    'ASSUMPTIONS_REVIEW_PENDING', 'GENERATION_COMPLETED', 'GENERATION_FAILED',
    'TECHNICAL_FAILURE'));

ALTER TABLE plan_drafts DROP CONSTRAINT ck_plan_drafts_status;
ALTER TABLE plan_drafts ADD CONSTRAINT ck_plan_drafts_status CHECK (
    status IN ('READY_FOR_REVIEW', 'IN_REVIEW', 'APPLYING', 'APPLIED'));

ALTER TABLE plan_drafts ADD CONSTRAINT ck_plan_drafts_applied_at CHECK (
    (status = 'APPLIED' AND applied_at IS NOT NULL)
    OR (status <> 'APPLIED' AND applied_at IS NULL));
