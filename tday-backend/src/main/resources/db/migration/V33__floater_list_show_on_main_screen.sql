-- Floater list main-screen visibility.
--
-- Off keeps a list's tasks out of the Floater feed, widget and summary; they stay in the list
-- itself and in search. Every existing list keeps showing, hence DEFAULT TRUE.
--
-- floaterproject is Exposed-owned, so on a fresh install it does not exist yet at Flyway-migrate
-- time: IF EXISTS makes this a no-op there and Exposed creates the column from FloaterLists (see V19).
ALTER TABLE IF EXISTS floaterproject
    ADD COLUMN IF NOT EXISTS "showOnMainScreen" BOOLEAN NOT NULL DEFAULT TRUE;
