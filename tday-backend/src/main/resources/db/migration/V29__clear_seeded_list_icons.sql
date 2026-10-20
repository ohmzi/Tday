-- Let the name-derived list icon reach the lists that already exist.
--
-- v0.7.28 gives a list with no chosen icon one that matches its name -- "Groceries" a
-- cart, "Gym" a dumbbell. The rule it rests on is that "iconKey IS NULL" means "the
-- owner never chose" and anything else means "the owner chose, leave it alone".
--
-- Until v0.7.28 nothing wrote NULL. Every create sheet on all three clients seeded its
-- picker with the default and posted whatever the picker held, so a list made by a user
-- who never looked at the icon row stored the string 'inbox' -- and so did a list made
-- by a user who deliberately tapped Inbox. The two are byte-identical in this column;
-- the distinction the new rule depends on was never recorded, because before v0.7.28
-- nothing depended on it.
--
-- So without this migration the feature ships switched off for everyone who already uses
-- the app: every list they own says 'inbox', every client's resolver reads that as a
-- deliberate choice and stops, and the user who asked for the detective system opens
-- their "Groceries" list after updating and sees exactly the inbox they saw before. Only
-- lists created AFTER the update could ever be guessed for.
--
-- The trade, stated plainly rather than buried: clearing 'inbox' wholesale also clears
-- the minority who really did choose Inbox on purpose, and some of their lists will now
-- take a name-derived glyph. That is the wrong answer for them. It is the cheaper wrong
-- answer, for three reasons:
--   1. It is unknowable which rows they are. Not "expensive to work out" -- the
--      information was never written down. Any rule that tried to guess (list renamed
--      since? icon differs from the name's inference?) would be a second detective
--      system, applied to the one decision the feature promises never to override.
--   2. It is recoverable in two taps, and this time it sticks: a post-cutover tap on
--      Inbox stores 'inbox' against a client that no longer posts the default, so it
--      now means what it says and is never inferred over again.
--   3. Not doing it is unrecoverable by the user at all. There is no "unset my icon"
--      control on any client -- ListService.update and FloaterListService.update both
--      write `iconKey?.let { ... }`, so a NULL in a PATCH means "leave it alone" and no
--      UI path can ever return a list to unset. A list frozen on the seeded default
--      stays frozen for the life of the account.
--
-- NO createdAt CUTOFF, deliberately. Flyway runs inside DatabaseConfig.init() before the
-- server binds a port, so every row that exists when this statement runs is by definition
-- a row written by a pre-v0.7.28 client. A timestamp comparison could not identify a
-- single additional row, and would add a clock-skew question to a migration that has
-- none. What it cannot cover either way is an OLD client still installed on someone's
-- phone after the server updates: it keeps posting 'inbox' on create, and those lists
-- stay frozen until that client updates. Fixing that server-side would mean rewriting an
-- incoming 'inbox' to NULL, which would destroy a NEW client's deliberate Inbox -- the
-- exact promise this feature is built on -- so it is left alone.
--
-- Android carries a second copy of this same seeded default, device-side, that no SQL can
-- reach: SecureConfigStore's list_icon_map, which SyncManager feeds to mapListDto as
-- `iconFallback` and which therefore resurrects 'inbox' over a NULL column. It is pruned
-- once on that client by SecureConfigStore.pruneSeededListIconShadow(), on the same
-- argument as this file. iOS has the same store but has never wired it as a fallback
-- (`mapListDTO`'s iconFallback is nil at every call site) and web has no such shadow, so
-- this migration is the whole story on those two.

-- MATCHED ON lower(btrim(...)), not on the exact bytes, and the same way in the Android
-- prune. Every client trims and lowercases a key before looking it up -- Android's
-- normalizeTdayListIconKeyOrNull, web's normalizeListIconKey, iOS's
-- normalizedTodoListIconKey -- so ' Inbox ' and 'INBOX' paint the inbox glyph exactly as
-- 'inbox' does. They are the same seeded default wearing different bytes, and an exact
-- match would leave those lists frozen on the very value this migration exists to clear,
-- invisibly, because all three spellings render identically.

-- Scheduled lists. `project` is created by V2__full_schema.sql, so it exists on every
-- database that reaches this migration and needs no guard.
UPDATE project
SET "iconKey" = NULL
WHERE lower(btrim("iconKey")) = 'inbox';

-- Anytime lists. `floaterproject` is entirely Exposed-owned -- see V19's note and V27's --
-- so on a database whose first-ever boot is still in progress it does not exist yet at
-- Flyway time; Exposed creates it moments later. UPDATE has no IF EXISTS, so the guard
-- has to be a to_regclass test. On that path there are no rows to fix and the block is a
-- no-op, which is correct: a table that does not exist yet has no pre-cutover lists in it.
DO $$
BEGIN
    IF to_regclass('public.floaterproject') IS NOT NULL THEN
        UPDATE floaterproject
        SET "iconKey" = NULL
        WHERE lower(btrim("iconKey")) = 'inbox';
    END IF;
END $$;
