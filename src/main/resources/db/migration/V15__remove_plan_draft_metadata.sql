UPDATE ai_plan_generation_workflows
SET generated_plan = generated_plan - 'metadata'
WHERE generated_plan ? 'metadata';

ALTER TABLE plan_drafts DROP COLUMN summary;
ALTER TABLE plan_drafts DROP COLUMN assumptions;
