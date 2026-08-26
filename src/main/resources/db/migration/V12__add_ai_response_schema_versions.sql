ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN pre_check_schema_version VARCHAR(20) NOT NULL DEFAULT '1.0';

ALTER TABLE ai_plan_generation_workflows
    ADD COLUMN generation_schema_version VARCHAR(20) NOT NULL DEFAULT '1.0';
