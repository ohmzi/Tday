-- Sharing/collaboration for scheduled lists and floater lists. A share row
-- grants a non-owner member EDITOR or VIEWER access; the owner stays implicit
-- on "Project"/"FloaterProject"."userID" and never gets a share row.

CREATE TABLE IF NOT EXISTS list_shares (
    id          VARCHAR(30) PRIMARY KEY,
    "listID"    VARCHAR(30) NOT NULL REFERENCES "Project"(id) ON DELETE CASCADE,
    "userID"    VARCHAR(30) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    role        VARCHAR(16) NOT NULL CHECK (role IN ('EDITOR', 'VIEWER')),
    "createdAt" TIMESTAMP NOT NULL,
    "updatedAt" TIMESTAMP NOT NULL,
    CONSTRAINT list_shares_list_user_unique UNIQUE ("listID", "userID")
);

CREATE INDEX IF NOT EXISTS list_shares_userid_idx ON list_shares ("userID");

CREATE TABLE IF NOT EXISTS floater_list_shares (
    id          VARCHAR(30) PRIMARY KEY,
    "listID"    VARCHAR(30) NOT NULL REFERENCES "FloaterProject"(id) ON DELETE CASCADE,
    "userID"    VARCHAR(30) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    role        VARCHAR(16) NOT NULL CHECK (role IN ('EDITOR', 'VIEWER')),
    "createdAt" TIMESTAMP NOT NULL,
    "updatedAt" TIMESTAMP NOT NULL,
    CONSTRAINT floater_list_shares_list_user_unique UNIQUE ("listID", "userID")
);

CREATE INDEX IF NOT EXISTS floater_list_shares_userid_idx ON floater_list_shares ("userID");
