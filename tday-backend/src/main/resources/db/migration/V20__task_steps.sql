-- Steps: a flat checklist inside a task (no nesting, no per-step dates).
--
-- Each step belongs to one todo and is deleted with it. Ordering is an integer
-- position. Completed todos keep a JSON snapshot of their steps so history survives
-- the parent's deletion.
CREATE TABLE IF NOT EXISTS task_steps (
    id VARCHAR(30) PRIMARY KEY,
    "todoID" VARCHAR(30) NOT NULL REFERENCES todos(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS task_steps_todoid_idx ON task_steps ("todoID");

-- Completed-history snapshot of steps at completion time (JSON array). The table is
-- unquoted lowercase completedtodo (Exposed maps Table("CompletedTodo") unquoted).
ALTER TABLE completedtodo
    ADD COLUMN IF NOT EXISTS steps TEXT;
