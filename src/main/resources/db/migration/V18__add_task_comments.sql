CREATE TABLE task_comments (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    task_id UUID NOT NULL,
    author_id UUID NOT NULL,
    content VARCHAR(2000) NOT NULL,
    CONSTRAINT pk_task_comments PRIMARY KEY (id),
    CONSTRAINT fk_task_comments_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_comments_author
        FOREIGN KEY (author_id) REFERENCES project_members (id) ON DELETE CASCADE,
    CONSTRAINT ck_task_comments_content
        CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 2000)
);

CREATE INDEX ix_task_comments_task_created
    ON task_comments (task_id, created_at);

CREATE INDEX ix_task_comments_author
    ON task_comments (author_id);
