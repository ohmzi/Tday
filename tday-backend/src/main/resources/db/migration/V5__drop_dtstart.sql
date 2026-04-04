ALTER TABLE todos DROP COLUMN IF EXISTS "dtstart";
ALTER TABLE todos DROP COLUMN IF EXISTS "durationMinutes";
ALTER TABLE todo_instances DROP COLUMN IF EXISTS "overriddenDtstart";
ALTER TABLE todo_instances DROP COLUMN IF EXISTS "overriddenDurationMinutes";
ALTER TABLE "CompletedTodo" DROP COLUMN IF EXISTS "dtstart";
