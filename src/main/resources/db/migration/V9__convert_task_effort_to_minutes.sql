-- 1. Drop existing constraints before multiplication to prevent check constraint violations
ALTER TABLE tasks
    DROP CONSTRAINT IF EXISTS ck_tasks_estimated_hours;

-- 2. Rename column in tasks
ALTER TABLE tasks
    RENAME COLUMN estimated_hours TO estimated_minutes;

-- 3. Multiply existing values by 60
UPDATE tasks
SET estimated_minutes = estimated_minutes * 60
WHERE estimated_minutes IS NOT NULL;

-- 4. Add new constraint with range 1 to 600000 minutes
ALTER TABLE tasks
    ADD CONSTRAINT ck_tasks_estimated_minutes
    CHECK (
        estimated_minutes IS NULL
        OR (estimated_minutes BETWEEN 1 AND 600000)
    );

-- 5. Drop existing constraint on draft_tasks
ALTER TABLE draft_tasks
    DROP CONSTRAINT IF EXISTS ck_draft_tasks_estimated_hours;

-- 6. Rename column in draft_tasks
ALTER TABLE draft_tasks
    RENAME COLUMN estimated_hours TO estimated_minutes;

-- 7. Multiply existing values in draft_tasks by 60
UPDATE draft_tasks
SET estimated_minutes = estimated_minutes * 60
WHERE estimated_minutes IS NOT NULL;

-- 8. Add new constraint with range 1 to 600000 minutes
ALTER TABLE draft_tasks
    ADD CONSTRAINT ck_draft_tasks_estimated_minutes
    CHECK (
        estimated_minutes IS NULL
        OR (estimated_minutes BETWEEN 1 AND 600000)
    );
