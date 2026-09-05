-- Comments on tasks (comment module, Phase 8).
CREATE TABLE comments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id    UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id  UUID NOT NULL REFERENCES users(id),
    body       TEXT NOT NULL,
    -- Set explicitly by application code the moment a real edit happens (CommentService),
    -- not derived by comparing created_at/updated_at. Two Hibernate-generated timestamps
    -- from the same flush aren't guaranteed to differ by enough (or at all, depending on
    -- clock resolution) to reliably distinguish "just created" from "edited immediately
    -- after" - an explicit column removes that ambiguity entirely.
    edited_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A task's comment thread, ordered by time, is the only query this table needs to serve well.
CREATE INDEX ix_comments_task_id_created_at ON comments (task_id, created_at);
