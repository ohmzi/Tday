-- Account identifier moves from email to username.
-- Existing values are carried over as-is (an old email simply becomes that
-- user's username). The unused emailVerified column is dropped.
ALTER TABLE "User" RENAME COLUMN email TO username;
ALTER TABLE "User" RENAME CONSTRAINT user_email_unique TO user_username_unique;
ALTER TABLE "User" DROP COLUMN "emailVerified";
