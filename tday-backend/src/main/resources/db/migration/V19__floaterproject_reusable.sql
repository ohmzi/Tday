-- Reusable floater lists.
--
-- A "reusable" floater list (e.g. a packing checklist or a weekly-cleaning list) can be
-- reset — all its floaters un-completed in one go — so the same list can be run again.
-- The flag just decides whether the Reset action is offered; existing lists default to
-- non-reusable. The table is the quoted "FloaterProject".
ALTER TABLE "FloaterProject"
    ADD COLUMN IF NOT EXISTS reusable BOOLEAN NOT NULL DEFAULT FALSE;
