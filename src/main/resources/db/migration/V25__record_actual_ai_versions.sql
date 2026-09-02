ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN pre_check_prompt_version VARCHAR(100);

ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN generation_prompt_version DROP NOT NULL;
ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN generation_prompt_version DROP DEFAULT;
ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN pre_check_schema_version DROP NOT NULL;
ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN pre_check_schema_version DROP DEFAULT;
ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN pre_check_schema_version TYPE VARCHAR(50);
ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN generation_schema_version DROP NOT NULL;
ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN generation_schema_version DROP DEFAULT;
ALTER TABLE ai_plan_generation_workflows
    ALTER COLUMN generation_schema_version TYPE VARCHAR(50);

UPDATE ai_plan_generation_workflows
SET pre_check_prompt_version = NULL,
    pre_check_schema_version = NULL,
    generation_prompt_version = NULL,
    generation_schema_version = NULL;
