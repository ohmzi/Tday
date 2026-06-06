-- Flag set by an admin password reset to force the user to choose a new
-- password the next time they sign in. Cleared when the user changes it.
ALTER TABLE "User"
    ADD COLUMN IF NOT EXISTS "requirePasswordChange" BOOLEAN NOT NULL DEFAULT FALSE;
tent