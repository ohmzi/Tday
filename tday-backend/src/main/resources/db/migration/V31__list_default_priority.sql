-- Per-list default priority: pre-fills a new task's priority when it's created in
-- that list, on either the scheduled or Anytime side. The tables are unquoted
-- project/floaterproject (Postgres folds them to lowercase; see V19's note), but the
-- column is camelCase like iconKey/userID, so it stays quoted to keep its case, and
-- the enum type itself is always quoted ("Priority", per todos.priority's own column).
-- floaterproject is Exposed-owned and absent on a database that has never booted the app
-- (see V19), hence IF EXISTS; Exposed creates it afterwards with the column declared.
ALTER TABLE project ADD COLUMN IF NOT EXISTS "defaultPriority" "Priority";
ALTER TABLE IF EXISTS floaterproject ADD COLUMN IF NOT EXISTS "defaultPriority" "Priority";
