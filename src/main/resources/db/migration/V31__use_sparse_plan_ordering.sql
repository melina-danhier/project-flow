-- Preserve each container's deterministic relative order while creating room for cheap moves.
WITH section_order AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY plan_container_id ORDER BY sort_order, created_at, id
    ) * 100 AS new_order
    FROM plan_sections
)
UPDATE plan_sections
SET sort_order = section_order.new_order
FROM section_order
WHERE plan_sections.id = section_order.id;

WITH element_order AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY plan_container_id, plan_section_id ORDER BY sort_order, created_at, id
    ) * 100 AS new_order
    FROM plan_elements
)
UPDATE plan_elements
SET sort_order = element_order.new_order
FROM element_order
WHERE plan_elements.id = element_order.id;

WITH draft_section_order AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY plan_draft_id ORDER BY sort_order, created_at, id
    ) * 100 AS new_order
    FROM draft_sections
)
UPDATE draft_sections
SET sort_order = draft_section_order.new_order
FROM draft_section_order
WHERE draft_sections.id = draft_section_order.id;

WITH draft_element_order AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY plan_draft_id, draft_section_id ORDER BY sort_order, created_at, id
    ) * 100 AS new_order
    FROM draft_plan_elements
)
UPDATE draft_plan_elements
SET sort_order = draft_element_order.new_order
FROM draft_element_order
WHERE draft_plan_elements.id = draft_element_order.id;

-- Drafts deliberately have no user-selectable manual display mode.
UPDATE plan_drafts SET sort_mode = 'DATE';

CREATE INDEX ix_plan_sections_manual_order
    ON plan_sections (plan_container_id, sort_order, id);
CREATE INDEX ix_plan_elements_manual_order
    ON plan_elements (plan_container_id, plan_section_id, sort_order, id);
CREATE INDEX ix_draft_sections_manual_order
    ON draft_sections (plan_draft_id, sort_order, id);
CREATE INDEX ix_draft_elements_manual_order
    ON draft_plan_elements (plan_draft_id, draft_section_id, sort_order, id);
