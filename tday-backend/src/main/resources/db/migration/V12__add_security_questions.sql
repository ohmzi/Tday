-- Security questions for self-service password reset.
--
-- A user picks 2 of 3 predefined questions at signup and answers them; the
-- hashed answers let them reset their own password later. Repeated failures
-- lock self-service reset and let the user request a reset from an admin.

-- Number of consecutive failed security-question reset attempts. Lockout
-- triggers once this exceeds 3.
ALTER TABLE "User"
    ADD COLUMN IF NOT EXISTS "securityQuestionFailCount" INTEGER NOT NULL DEFAULT 0;

-- Set when a locked-out user asks an admin to reset their password. The admin
-- panel highlights these users; cleared on admin reset.
ALTER TABLE "User"
    ADD COLUMN IF NOT EXISTS "pendingAdminReset" BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE "User"
    ADD COLUMN IF NOT EXISTS "adminResetRequestedAt" TIMESTAMP;

-- TRUE means the user still needs to choose security questions. Existing rows
-- default TRUE (prompted on next login); the signup path inserts FALSE because
-- it provides the questions inline.
ALTER TABLE "User"
    ADD COLUMN IF NOT EXISTS "requireSecurityQuestions" BOOLEAN NOT NULL DEFAULT TRUE;

-- One row per chosen question. Only the question id + hashed answer are stored,
-- never the question text, so wording can change without a migration.
CREATE TABLE IF NOT EXISTS user_security_questions (
    id VARCHAR(30) PRIMARY KEY,
    "userID" VARCHAR(30) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    question_id INTEGER NOT NULL,
    answer_hash TEXT NOT NULL,
    "createdAt" TIMESTAMP NOT NULL,
    "updatedAt" TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS user_security_questions_userid_idx
    ON user_security_questions ("userID");

-- A user can't pick the same question twice; also enables upsert by (user, question).
CREATE UNIQUE INDEX IF NOT EXISTS user_security_questions_user_q_unique
    ON user_security_questions ("userID", question_id);
