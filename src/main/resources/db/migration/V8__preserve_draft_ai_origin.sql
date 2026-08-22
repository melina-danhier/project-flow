ALTER TABLE draft_plan_elements
    ADD COLUMN ai_origin VARCHAR(20) NOT NULL DEFAULT 'AI_INFERRED';

ALTER TABLE draft_plan_elements
    ADD CONSTRAINT ck_draft_elements_ai_origin
        CHECK (ai_origin IN ('USER_INPUT', 'AI_INFERRED'));
