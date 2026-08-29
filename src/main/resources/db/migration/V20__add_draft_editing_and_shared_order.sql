ALTER TABLE plan_drafts
    ADD COLUMN sort_mode VARCHAR(20) NOT NULL DEFAULT 'DATE';

UPDATE plan_drafts
SET sort_mode = plan_containers.sort_mode
FROM plan_containers
WHERE plan_drafts.project_id = plan_containers.id;

ALTER TABLE plan_drafts
    ADD CONSTRAINT ck_plan_drafts_sort_mode CHECK (sort_mode IN ('MANUAL', 'DATE'));

ALTER TABLE draft_sections
    ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'AI';

UPDATE draft_sections SET origin = CASE WHEN user_modified THEN 'AI_MODIFIED' ELSE 'AI' END;

ALTER TABLE draft_sections
    ADD CONSTRAINT ck_draft_sections_origin
        CHECK (origin IN ('USER', 'TEMPLATE', 'TEMPLATE_MODIFIED', 'AI', 'AI_MODIFIED'));

ALTER TABLE draft_plan_elements DROP CONSTRAINT ck_draft_elements_ai_origin;
UPDATE draft_plan_elements SET ai_origin = CASE
    WHEN user_modified AND ai_origin = 'USER_INPUT' THEN 'USER'
    WHEN user_modified THEN 'AI_MODIFIED'
    WHEN ai_origin = 'USER_INPUT' THEN 'USER'
    ELSE 'AI'
END;
ALTER TABLE draft_plan_elements
    ADD CONSTRAINT ck_draft_elements_ai_origin
        CHECK (ai_origin IN ('USER', 'TEMPLATE', 'TEMPLATE_MODIFIED', 'AI', 'AI_MODIFIED'));

ALTER TABLE plan_sections DROP CONSTRAINT ck_plan_sections_origin;
ALTER TABLE plan_sections
    ADD CONSTRAINT ck_plan_sections_origin
        CHECK (origin IN ('USER', 'TEMPLATE', 'TEMPLATE_MODIFIED', 'AI', 'AI_MODIFIED'));

ALTER TABLE plan_elements DROP CONSTRAINT ck_plan_elements_origin;
ALTER TABLE plan_elements
    ADD CONSTRAINT ck_plan_elements_origin
        CHECK (origin IN ('USER', 'TEMPLATE', 'TEMPLATE_MODIFIED', 'AI', 'AI_MODIFIED'));

WITH positions AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY draft_section_id ORDER BY sort_order, created_at, id
    ) AS new_position
    FROM draft_plan_elements
    WHERE draft_section_id IS NOT NULL
)
UPDATE draft_plan_elements
SET sort_order = positions.new_position
FROM positions
WHERE draft_plan_elements.id = positions.id;

WITH positions AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY plan_draft_id ORDER BY sort_order, created_at, id
    ) AS new_position
    FROM draft_sections
)
UPDATE draft_sections
SET sort_order = positions.new_position
FROM positions
WHERE draft_sections.id = positions.id;

ALTER TABLE draft_sections DROP COLUMN user_modified;
ALTER TABLE draft_plan_elements DROP COLUMN user_modified;
