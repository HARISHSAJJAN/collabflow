-- Tasks (task module, Phase 7). This is the table with the project's central concurrency
-- requirement: `version` backs JPA optimistic locking (@Version on the Task entity).
--
-- The scenario this defends against: two users load the same task, both see status=TODO, both
-- decide (based on that now-stale read) to change something, and both submit an update. Without
-- a version check, whichever write lands second silently overwrites the first ("lost update") -
-- the first user's change vanishes with no error to anyone. With `version`, every UPDATE
-- includes `AND version = <the value that was read>` in its WHERE clause; the write that loses
-- the race matches zero rows, and Hibernate raises OptimisticLockException, which the app
-- translates to 409 CONFLICT (see GlobalExceptionHandler) instead of silently losing data. See
-- docs/database.md's concurrency section and the test in TaskConcurrencyTest for this proven,
-- not just asserted, end to end.
CREATE TABLE tasks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'TODO' CHECK (status IN ('TODO', 'IN_PROGRESS', 'REVIEW', 'DONE', 'BLOCKED')),
    priority    VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    assignee_id UUID REFERENCES users(id),
    reporter_id UUID NOT NULL REFERENCES users(id),
    due_date    DATE,
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The task board / task list is almost always scoped to one project and then filtered by
-- status (a Kanban column) - this composite index serves both "all of a project's tasks" and
-- "a project's TODO tasks" without a separate single-column index on status alone, which would
-- rarely be useful on its own (status values repeat heavily across the whole table).
CREATE INDEX ix_tasks_project_id_status ON tasks (project_id, status);

-- "My assigned tasks" (the dashboard's central query, per the brief) filters by assignee
-- directly, often across many projects - a separate index, since it doesn't share a useful
-- prefix with the project-scoped index above.
CREATE INDEX ix_tasks_assignee_id ON tasks (assignee_id) WHERE assignee_id IS NOT NULL;

-- Backs "upcoming deadlines" (dashboard) and future due-date-range filtering (Phase 13).
CREATE INDEX ix_tasks_due_date ON tasks (due_date) WHERE due_date IS NOT NULL;

-- Free-text labels per task, not a shared per-project label catalog: see docs/decisions.md for
-- why a full label-catalog subsystem (with its own CRUD, color-coding, reuse across tasks) was
-- deliberately not built for what the brief asks for ("add labels").
CREATE TABLE task_labels (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id  UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    label    VARCHAR(50) NOT NULL
);

CREATE UNIQUE INDEX ux_task_labels_task_label ON task_labels (task_id, label);
