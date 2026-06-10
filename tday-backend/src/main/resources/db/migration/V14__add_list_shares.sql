-- Sharing/collaboration for scheduled lists and floater lists. A share row
-- grants a non-owner member EDITOR or VIEWER access; the owner stays implicit
-- on "Project"/"FloaterProject"."userID" and never gets a share row.
--
-- Deliberately NO foreign keys to "Project"/"FloaterProject"/"User" here:
-- on databases baselined from the Prisma era those tables can live outside
-- the schema Flyway migrates (Flyway pins its session search_path to the
-- default schema, so the references fail to resolve even though the app sees
-- the tables fine). Referential cleanup is owned by the services
-- (ListService/FloaterListService.deleteMany, ListShareService,
-- AdminService.purgeUser), and Exposed's schema bootstrap adds the
-- constraints on connections where the parents resolve.

CREATE TABLE IF NOT EXISTS list_shares (
    id          VARCHAR(30) PRIMARY KEY,
    "listID"    VARCHAR(30) NOT NULL,
    "userID"    VARCHAR(30) NOT NULL,
    role        VARCHAR(16) NOT NULL CHECK (role IN ('EDITOR', 'VIEWER')),
    "createdAt" TIMESTAMP NOT NULL,
    "updatedAt" TIMESTAMP NOT NULL,
    CONSTRAINT list_shares_list_user_unique UNIQUE ("listID", "userID")
);

CREATE INDEX IF NOT EXISTS list_shares_userid_idx ON list_shares ("userID");

CREATE TABLE IF NOT EXISTS floater_list_shares (
    id          VARCHAR(30) PRIMARY KEY,
    "listID"    VARCHAR(30) NOT NULL,
    "userID"    VARCHAR(30) NOT NULL,
    role        VARCHAR(16) NOT NULL CHECK (role IN ('EDITOR', 'VIEWER')),
    "createdAt" TIMESTAMP NOT NULL,
    "updatedAt" TIMESTAMP NOT NULL,
    CONSTRAINT floater_list_shares_list_user_unique UNIQUE ("listID", "userID")
);

CREATE INDEX IF NOT EXISTS floater_list_shares_userid_idx ON floater_list_shares ("userID");
