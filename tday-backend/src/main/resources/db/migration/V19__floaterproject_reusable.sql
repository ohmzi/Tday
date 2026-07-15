-- Reusable floater lists.
--
-- A "reusable" floater list (e.g. a packing checklist or a weekly-cleaning list) can be
-- reset — all its floaters un-completed in one go — so the same list can be run again.
-- The flag just decides whether the Reset action is offered; existing lists default to
-- non-reusable. The table is unquoted floaterproject (Postgres folds it to lowercase;
-- the Exposed mapping Table("FloaterProject") is likewise unquoted at query time).
ALTER TABLE floaterproject
    ADD COLUMN IF NOT EXISTS reusable BOOLEAN NOT NULL DEFAULT FALSE;
