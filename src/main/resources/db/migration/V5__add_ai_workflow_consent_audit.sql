ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN consent_confirmed_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN consent_version VARCHAR(20);

UPDATE ai_plan_generation_workflows
SET consent_confirmed_at = created_at,
    consent_version = 'v1'
WHERE consent_confirmed_at IS NULL
   OR consent_version IS NULL;

ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN consent_confirmed_at SET NOT NULL;

ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN consent_version SET NOT NULL;
