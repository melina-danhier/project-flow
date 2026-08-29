ALTER TABLE plan_sections
    DROP CONSTRAINT IF EXISTS ck_plan_sections_relative_start;

ALTER TABLE plan_sections
    DROP CONSTRAINT IF EXISTS ck_plan_sections_relative_end;

ALTER TABLE plan_sections
    DROP COLUMN IF EXISTS start_date;

ALTER TABLE plan_sections
    DROP COLUMN IF EXISTS end_date;

ALTER TABLE plan_sections
    DROP COLUMN IF EXISTS relative_start_day;

ALTER TABLE plan_sections
    DROP COLUMN IF EXISTS relative_end_day;

ALTER TABLE draft_sections
    DROP COLUMN IF EXISTS start_date;

ALTER TABLE draft_sections
    DROP COLUMN IF EXISTS end_date;
