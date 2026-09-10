CREATE TABLE task_assignees (
    task_id UUID NOT NULL,
    project_member_id UUID NOT NULL,
    CONSTRAINT pk_task_assignees PRIMARY KEY (task_id, project_member_id),
    CONSTRAINT fk_task_assignees_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_assignees_member
        FOREIGN KEY (project_member_id) REFERENCES project_members (id) ON DELETE CASCADE
);

INSERT INTO task_assignees (task_id, project_member_id)
SELECT id, assignee_id FROM tasks WHERE assignee_id IS NOT NULL;

ALTER TABLE tasks DROP CONSTRAINT fk_tasks_assignee;
DROP INDEX ix_tasks_assignee;
ALTER TABLE tasks DROP COLUMN assignee_id;

CREATE INDEX ix_task_assignees_member ON task_assignees (project_member_id);
