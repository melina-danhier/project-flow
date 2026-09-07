ALTER TABLE plan_sections
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';

UPDATE plan_sections
SET review_status = 'UNREVIEWED'
WHERE origin IN ('TEMPLATE', 'AI');

ALTER TABLE plan_sections
    ADD CONSTRAINT ck_plan_sections_review_status
        CHECK (review_status IN ('CONFIRMED', 'UNREVIEWED'));

ALTER TABLE plan_elements
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';

UPDATE plan_elements
SET review_status = 'UNREVIEWED'
WHERE origin IN ('TEMPLATE', 'AI');

ALTER TABLE plan_elements
    ADD CONSTRAINT ck_plan_elements_review_status
        CHECK (review_status IN ('CONFIRMED', 'UNREVIEWED'));
