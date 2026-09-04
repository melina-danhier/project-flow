WITH section_positions AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY plan_draft_id ORDER BY sort_order, created_at, id
    ) - 1 AS new_position
    FROM draft_sections
)
UPDATE draft_sections
SET sort_order = section_positions.new_position
FROM section_positions
WHERE draft_sections.id = section_positions.id;

WITH element_positions AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY plan_draft_id, draft_section_id ORDER BY sort_order, created_at, id
    ) - 1 AS new_position
    FROM draft_plan_elements
)
UPDATE draft_plan_elements
SET sort_order = element_positions.new_position
FROM element_positions
WHERE draft_plan_elements.id = element_positions.id;
