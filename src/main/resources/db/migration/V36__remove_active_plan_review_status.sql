ALTER TABLE plan_sections
    DROP CONSTRAINT ck_plan_sections_review_status;

ALTER TABLE plan_sections
    DROP COLUMN review_status;

ALTER TABLE plan_elements
    DROP CONSTRAINT ck_plan_elements_review_status;

ALTER TABLE plan_elements
    DROP COLUMN review_status;
