# Design note: durable, browsable completed floaters

Status: backend foundation merged on `feat/completed-floaters-foundation`. Client work (web,
Android, iOS) branches from here.
Scope: backend (Ktor/Exposed/Flyway) + shared Kotlin DTOs. **Floaters only** — the identical bug
in `ListService.kt`/`CompletedTodos` for scheduled Todos is deliberately untouched (explicit
product decision; do not "fix it while you're in there").

This document is the contract. It is what the three platform builders implement against —
anything not written here, or not verifiable in the live code paths cited below, should be
double-checked against the running contract rather than assumed.

---

## 1. The problem

Two things were true before this change:

1. **Completing** a floater already wrote a durable `CompletedFloaters` row
   (`FloaterService.completeFloater`, `tday-backend/src/main/kotlin/com/ohmz/tday/services/FloaterService.kt`).
   Nothing on any client ever *read* that history for floaters, but the backend already had it.
2. **Deleting the floater's list** destroyed that history anyway:
   `FloaterListService.deleteMany()` explicitly purged every `CompletedFloaters` row referencing
   the list, in the same transaction that deleted the list's `Floaters` rows (completed and
   pending alike) and the `FloaterLists` row itself. So the moment a user tidied up an old list,
   every "completed" record it had ever produced vanished — even though nothing about *completion*
   should depend on the list surviving.

The ask: keep completion history durable across list deletion, expose it as a browsable screen,
and let Undo from that screen work even when the list is long gone — by **recreating** the list
(same name/color) rather than failing or dropping the item.

## 2. The correlation-key decision

**The problem in one sentence:** to converge two different undone items that both trace back to
the *same* deleted list onto *one* recreated list (not two), you need a stable way to know two
`CompletedFloaters` rows shared a list — after the row that would normally answer that question
(`FloaterLists`) is gone. Matching by name is not safe (two lists can share a name; a user could
also have renamed one).

**Decision: keep the id, drop the enforced FK, add an unconstrained twin.**

`CompletedFloaters.listID` (SQL column `"projectID"`) stays a foreign key to `FloaterLists.id`,
but its `onDelete` changes from Exposed's implicit `RESTRICT`/`NO ACTION` default to
`SET NULL`. A new column, `CompletedFloaters.originalListID`, is a **plain `varchar(30)` with no
foreign key at all** — populated once, at completion time, as a copy of `listID`, and never
touched again.

Why this shape and not the alternative (drop the FK entirely, keep only the unconstrained id):

- `listID` staying a real (if softer) FK means every *other* existing reader of that column — the
  `CompletedFloaterDto.listID` field clients already consume, any query that joins on it — keeps
  meaning exactly what it always meant: "the list this is in *right now*, or null." A completed
  floater whose list is still alive is unaffected by any of this; nothing about its `listID`
  changes.
- `originalListID` exists purely so that when `listID` gets nulled by the `SET NULL` action, the
  correlation isn't lost with it. It is never displayed, never joined against by clients — it's
  the only thing `uncompleteFloater()` reads to decide whether "the list this came from" is
  findable, and if so, which one.
- This is the same shape the task write-up called out as the safe option, and it keeps the two
  concerns (referential integrity vs. historical correlation) in two columns instead of trying to
  make one column serve both, which is what forced the purge in the first place.

**The mirror image on `FloaterLists`:** a new column, `recreatedFromListID` (also a plain
`varchar(30)`, also no FK — the row it would reference is, by construction, always already gone
by the time it's set). `uncompleteFloater()`'s find-or-create step is:

```
given originalListId (from the CompletedFloaters row):
  1. is there a live FloaterLists row with id == originalListId?         (defensive; see §5)
  2. else, is there a FloaterLists row with recreatedFromListID == originalListId?
     -> yes: land there (this is the convergence case)
     -> no:  insert a new FloaterLists row, recreatedFromListID = originalListId
```

A second undo of a different item from the same deleted list re-runs step 2, finds the row the
first undo just inserted, and lands there — no duplicate. This is plain application-level
find-or-create, not a database-enforced invariant on its own; see §7 for the one thing about it
that isn't bulletproof under true concurrency.

## 3. The migration

`V27__completed_floaters_survive_list_deletion.sql` — the first free migration number; `V26` was
the highest on `develop` at branch time (`c149c5e4`, `align_cascade_delete_constraints.sql` — the
push_subscriptions/todo_instances CASCADE realignment this migration explicitly follows the
discipline of).

**Both `completedfloaters` and `floaterproject` (the `FloaterLists` table) are 100%
Exposed-managed** — neither has ever had a Flyway `CREATE TABLE` (confirmed: `grep` over
`db/migration/*.sql` for either table name returns nothing; both are created by
`SchemaUtils.createMissingTablesAndColumns` in `DatabaseConfig.init()`, which runs *after* Flyway
on every boot). `V19__floaterproject_reusable.sql` is the precedent for the discipline this still
requires: even for an Exposed-only table, a real column/constraint change gets an explicit,
guarded Flyway migration, not a bet on the next boot's auto-reconciliation. The reason restated
from `V26`'s own comment (still accurate, now for a third table pair): change only the SQL and
Exposed's `createMissingTablesAndColumns` reverts it on the next boot (it diffs the live FK
against what the Kotlin column now declares, and rewrites the live one to match); change only the
Kotlin and the rewrite happens as an unmigrated `ALTER TABLE` against a live database instead of a
tracked migration.

Every statement in V27 is `IF EXISTS`/`IF NOT EXISTS`-guarded, because on a database that has
*never* booted the app, neither table exists yet at Flyway-migrate time — every statement is then
a no-op, and Exposed creates both tables fresh afterward, straight from the new Kotlin
declarations. This is the same reason `V6`/`V19` are guarded the same way.

```sql
-- 1. completedfloaters."projectID" -> floaterproject.id: RESTRICT (Exposed's default) to SET NULL.
ALTER TABLE IF EXISTS completedfloaters
    ADD COLUMN IF NOT EXISTS "originalListID" character varying(30);

UPDATE completedfloaters
SET "originalListID" = "projectID"
WHERE "originalListID" IS NULL AND "projectID" IS NOT NULL;

ALTER TABLE IF EXISTS completedfloaters
    DROP CONSTRAINT IF EXISTS fk_completedfloaters_projectid__id;

ALTER TABLE IF EXISTS completedfloaters
    ADD CONSTRAINT fk_completedfloaters_projectid__id
    FOREIGN KEY ("projectID") REFERENCES floaterproject(id) ON DELETE SET NULL ON UPDATE RESTRICT;

-- 2. floaterproject."recreatedFromListID": the find-or-create marker.
ALTER TABLE IF EXISTS floaterproject
    ADD COLUMN IF NOT EXISTS "recreatedFromListID" character varying(30);
```

(Full file, with rationale comments per statement, at
`tday-backend/src/main/resources/db/migration/V27__completed_floaters_survive_list_deletion.sql`.)

The constraint name `fk_completedfloaters_projectid__id` follows Exposed's documented naming —
`fk_<table>_<column>__<targetColumn>`, lowercased — the same convention `V26` verified against the
two constraints it realigned; this is unverified against a *live* database (no Postgres instance
available in this environment) but is the only name Exposed will ever have generated for this FK,
since it has never been explicitly named otherwise. **A client/ops builder deploying this for the
first time should confirm the `DROP CONSTRAINT IF EXISTS` actually found and dropped something**
(Postgres logs, or `\d completedfloaters` before/after) rather than trust this note blindly — the
`IF EXISTS` makes a name mismatch fail silently instead of loudly.

**The partial unique index is deliberately *not* in the migration.** `FloaterLists.kt` declares it
directly in Kotlin:

```kotlin
init {
    index("floaterproject_userid_recreatedfromlistid", true, userID, recreatedFromListID) {
        recreatedFromListID.isNotNull()
    }
}
```

Unlike a foreign key's `onDelete` rule, Exposed's schema reconciliation only ever *adds* an index
it finds declared-but-missing — it does not drop or rewrite one out from under a differently
worded migration (`checkExcessiveIndices` only *logs* extras at INFO, never drops them). There is
therefore no drift to guard against, and writing the raw `CREATE UNIQUE INDEX` in the migration
would have hit the same "table doesn't exist yet on a truly fresh install" ordering problem the
`IF EXISTS` guards solve for the column/constraint statements above — but `CREATE INDEX` has no
`IF EXISTS`-on-the-table equivalent to guard it the same way, and second-guessing that with a
`DO $$ ... to_regclass(...) ...` block for one plain index was worse than just declaring it once,
in the one place Exposed already handles both the fresh-table and existing-table cases uniformly.

One caveat for anyone extending the backend tests here: **H2 (the in-memory database
`TestDatabase` uses) does not support filtered/partial indexes** — Exposed logs
`Index creation with a filter condition is not supported in H2` and silently creates a full,
non-partial index instead when tests boot. The uniqueness guarantee is therefore only really
enforced on Postgres in this codebase's test suite; the backend tests in this PR verify the
find-or-create *logic* (sequential calls converge correctly), not the database-level uniqueness
backstop. See §7.

## 4. Schema after this change

```
CompletedFloaters ("completedfloaters")
  ...
  listID          varchar(30) NULL   -- FK -> FloaterLists.id, ON DELETE SET NULL, ON UPDATE RESTRICT
  listName        varchar(255) NULL  -- unchanged: denormalized snapshot, still populated at completion time
  listColor       varchar(32) NULL   -- unchanged: denormalized snapshot ("TEAL", "BLUE", ... — ListColor.name)
  originalListID  varchar(30) NULL   -- NEW, no FK. Copy of listID at completion time. Never mutated after insert.

FloaterLists ("FloaterProject" / floaterproject)
  ...
  recreatedFromListID  varchar(30) NULL   -- NEW, no FK. Set only on a list created by uncompleteFloater()'s
                                           -- recreate path; names the original (deleted) list's id.
  UNIQUE (userID, recreatedFromListID) WHERE recreatedFromListID IS NOT NULL   -- declared in Kotlin, see §3
```

`listDeleted` (see §5.1) is computable from these two columns alone:
`originalListID IS NOT NULL AND listID IS NULL`.

## 5. Endpoint contracts

### 5.1 `GET /api/completedFloater` — list completed floaters (extended, not replaced)

Route: `tday-backend/src/main/kotlin/com/ohmz/tday/routes/CompletedFloaterRoutes.kt` (unchanged).
Service: `CompletedFloaterServiceImpl.getAll()` (unchanged query — only the row-mapping function
gained one field). Response envelope unchanged: `{"completedFloaters": [CompletedFloaterDto, ...]}`.

`CompletedFloaterDto` (`shared/src/commonMain/kotlin/com/ohmz/tday/shared/model/FloaterModels.kt`),
verbatim, with the one new field:

```kotlin
@Serializable
data class CompletedFloaterDto(
    val id: String,
    val originalFloaterID: String? = null,
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
    val completedAt: String? = null,
    val daysToComplete: Double? = null,
    val userID: String? = null,
    val listID: String? = null,
    val listName: String? = null,
    val listColor: String? = null,
    val listDeleted: Boolean = false,   // NEW
)
```

`listDeleted` is `true` only when the item had a list at completion time (so `listName`/`listColor`
are populated) and that list has since been deleted (`listID` is null *because of that*, not
because the floater never had a list — a floater with no list also has `listID == null` but
`listDeleted == false`). This is what a client can use to show "list deleted" state in the browsable
Completed screen, and to warn before Undo ("this will recreate **<listName>**") instead of only
finding out after the fact from the uncomplete response.

Everything else about this endpoint — `DELETE /api/completedFloater` (all-or-one),
`PATCH /api/completedFloater` (update or remove-if-no-fields) — is unchanged in shape.
`deleteById()`'s *behavior* changed (see §6), but its request/response contract did not.

### 5.2 `PATCH /api/floater/uncomplete` — reworked

Route: `tday-backend/src/main/kotlin/com/ohmz/tday/routes/FloaterRoutes.kt`, unchanged path and
request body:

```kotlin
@Serializable
data class FloaterUncompleteRequest(
    val id: String,   // the ORIGINAL floater id (CompletedFloaterDto.originalFloaterID), not the
                       // CompletedFloaters row's own id — same as before this change.
)
```

Response shape is new — was a bare `{"message": "floater uncompleted"}` `Map<String,String>`;
now a typed object with the same `message` key still present (so nothing that only ever read
`.message` breaks), plus everything a client needs to render the right outcome without a follow-up
call:

```kotlin
@Serializable
data class FloaterUncompleteResponse(
    val message: String? = null,
    val floater: FloaterDto? = null,     // the restored/recreated floater, in its final resting list
    val listRecreated: Boolean = false,  // see semantics below — read this, not "was a list literally inserted this call"
    val listID: String? = null,          // == floater?.listID; null if the floater has no list at all
    val listName: String? = null,
    val listColor: String? = null,
)
```

**`listRecreated` semantics — read this carefully, it is not "did this exact call INSERT a new
FloaterLists row."** It means *"the floater's list, if any, is not the same list it was completed
from."* It is `true` for **every** item that goes through the recreate path (§6, case b) and had a
list at all — including the *second* undo of a different item from the same deleted list, which
finds the list the *first* undo already created rather than inserting a new one, but still did not
land back in its original list. It is `false` only when either the floater had no list, or the
Floaters row was never actually gone (case a — the common case, nothing deleted).

If a client only needs one bit of UI signal, it's this one: `listRecreated == true` means "tell
the user this landed in a recreated list, and the list id may not be one you've seen before" —
**don't assume the `listID` you get back is the one this floater was originally completed into,
even on a second-or-later undo from the same original list.**

`AppError.NotFound` is returned only when *neither* a live `Floaters` row nor a `CompletedFloaters`
row exists for `id` — i.e. genuinely nothing to undo. A live `Floaters` row that's already
`completed = false` (a harmless double-fire) still succeeds, matching this endpoint's pre-existing
idempotent behavior; it is not treated as an error.

### 5.3 What did *not* change

- `POST/GET/PATCH/DELETE /api/floater` — unchanged.
- `PATCH /api/floater/complete` — unchanged behavior; the only new thing is it now also populates
  `CompletedFloaters.originalListID` on insert (§6).
- `GET/POST/PATCH/DELETE /api/floaterList` and friends — unchanged request/response shapes.
  `FloaterListService.deleteMany()`'s *internal* behavior changed (§6) but its contract (which
  lists actually got deleted, returned as `deletedIds`) did not.

## 6. Server-side behavior changes

**`FloaterService.completeFloater()`** (`FloaterService.kt`): the `CompletedFloaters.insert` block
gained one line, `it[CompletedFloaters.originalListID] = floater[Floaters.listID]` — the snapshot
this entire feature depends on. Nothing else about completion changed.

**`FloaterService.uncompleteFloater()`** — reworked. Two cases inside one transaction:

- **(a) the `Floaters` row still exists** (the list was never deleted, or was deleted+recreated by
  a *previous* undo and this is some other floater still resolvable directly): flip
  `completed = false`, consume the `CompletedFloaters` row if there is one, same as the old
  behavior — this is the regression-tested "unchanged" path.
- **(b) the `Floaters` row is gone**: this can only happen via `FloaterListService.deleteMany()`,
  which deletes every `Floaters` row for a deleted list (completed or not) in the same transaction
  it deletes the list — so reaching this branch means the list is provably gone. Find-or-create the
  list per §2, insert a fresh `Floaters` row (new id) from the `CompletedFloaters` snapshot
  (`title`/`description`/`priority`; ciphertext copied across as-is, same pattern
  `completeFloater`/`promoteToTodo` already use), point it at the (re)created list, delete the
  `CompletedFloaters` row.
- If **neither** exists: `AppError.NotFound`.

**`FloaterListService.deleteMany()`** — the explicit
`CompletedFloaters.deleteWhere { listID inList existingIds ... }` calls are gone. `Floaters` rows
(pending and completed) for the deleted lists are still deleted exactly as before — that part of
the cascade is unchanged and intentional; only the *pending/live* rows disappear, not the
completion history. `CompletedFloaters` rows are no longer touched by this method at all; deleting
the `FloaterLists` row is what detaches them, via the `ON DELETE SET NULL` FK action from §3 —
Postgres does this automatically, there is no explicit "null it out" application code to find.

**`CompletedFloaterService.deleteById()`** — the adjacent bug fix. Previously this (the *permanent*
delete of one completed-floater entry, from the browsable Completed screen's "remove forever"
action) deleted only the `CompletedFloaters` row, leaving the `Floaters` row it pointed at
(`completed = true`) behind forever — invisible to `getAll()` (which filters `completed = false`)
but never cleaned up. It now also deletes that `Floaters` row first, matching what
`FloaterService.delete()` (the direct single-floater permanent delete) already did correctly.
`deleteAll()` (clear-everything) was **not** touched — it has the same latent orphan bug, but
fixing it wasn't in scope for this change; flagging it here rather than fixing it silently.

## 7. Known limitations / things a client builder should verify, not assume

- **The find-or-create in §2 is not race-proof under true concurrency.** Two `uncompleteFloater`
  calls for two different items from the same deleted list, arriving concurrently (not just
  "close together" — genuinely overlapping transactions under Postgres `READ COMMITTED`), could
  both fail to see each other's not-yet-committed insert and each create a `FloaterLists` row.
  The partial unique index (§3) will make the *second* commit fail outright with a constraint
  violation, rather than silently duplicating — but nothing in this PR retries or recovers from
  that failure gracefully (it surfaces as an `AppError.Internal`-shaped 500 from whatever wraps the
  transaction, not a friendly error). This was judged out of scope: the tested and realistic case —
  sequential undos from a Completed screen, which is how a human actually clicks through a list of
  items — always converges correctly (regression-tested, §8). If a client's design makes concurrent
  double-undo plausible (e.g. an auto-retry on timeout that could double-send), that's worth a
  second look before shipping.
- **Shared floater lists**: `deleteMany()` (list deletion) is owner-only and detaches *every*
  member's `CompletedFloaters` rows, unfiltered by `userID` — that part is unchanged from before
  this PR. But the **recreate** path in `uncompleteFloater()` creates the new list owned by
  *whoever calls uncomplete* (`userID` on the `CompletedFloaters` row being undone, which is
  whoever completed that particular item — not necessarily the original list's owner). If a second
  member of the *same* original shared list later undoes their own item, they will get their *own*
  separate recreated list (the find-or-create lookup is scoped to `userID`), not the first member's
  — there is no sharing/re-invite step. This was not in the explicit product requirements for this
  feature and is not tested; flagging it as a real gap rather than a tested behavior, in case a
  client surfaces floater-list sharing prominently enough that this matters.
- **`listRecreated` is a UX signal, not a "was a row inserted" signal** — see the callout in §5.2.
  Get this wrong and a client will show "restored" (implying same list) on a second undo that
  actually landed in a different list than the item's original one.
- **The constraint name in the migration is unverified against a live Postgres instance** — no
  Postgres was available in the environment this was built in. It follows Exposed's documented,
  previously-verified (`V26`) naming convention exactly, but confirm the `DROP CONSTRAINT IF EXISTS`
  actually matched something on first deploy (see §3).
- **This is backend-only.** The icon-based Undo UI change (reversing AppSnackbar's "icons removed
  app-wide" decision, with sign-off, for the shared Undo toast component used by both todo and
  floater delete/complete on every platform) is a separate, already-decided client change with no
  new backend contract behind it — it is not part of this document because it needs none of the
  API surface described here. Do not conflate it with the "restored into a recreated list" signal
  above; they are different UI moments (the 8.5s toast-Undo vs. the browsable-Completed-screen
  Undo — see next bullet).
- **Two different Undo flows exist and both call different things.** The toast Undo (8.5s
  delayed-commit — `docs/PLAN_UNDO_TOAST_DELETE.md`) stages the completion client-side and only
  sends `PATCH /api/floater/complete` to the server if the window closes *without* Undo being
  pressed — pressing Undo within the window cancels the staged send and never calls
  `/api/floater/uncomplete` at all. The **browsable Completed screen's** Undo is a real, immediate
  round-trip to `/api/floater/uncomplete` against an item that is *already* committed server-side,
  is exactly the flow this document's contract is for, and is the only one of the two that can ever
  hit the "list was deleted" recreate path (the toast window is far too short for a list deletion to
  plausibly land in between).
- **Local Mode (web)**: `tday-web/src/lib/local/localFloaters.ts` has a hand-written
  `uncompleteFloater()` mock for `PATCH /api/floater/uncomplete` under Local Mode (no server). It
  was not touched by this backend PR and does not implement any of the recreate-list logic in §6 —
  a web client builder wiring the new Completed screen needs to either extend it to match this
  contract (at least well enough not to break Local Mode's Completed screen) or explicitly scope
  Local Mode out, but should not assume it already matches.

## 8. Backend verification

`./gradlew :tday-backend:test` — 361 tests, 0 failures (357 before this change + 4 new). New
tests in `tday-backend/src/test/kotlin/com/ohmz/tday/services/CompletedFloaterDurabilityTest.kt`,
run against the real service implementations and a database built from the actual Exposed table
declarations (`TestDatabase.fresh()` — the same approach `CascadeDeleteTest`/
`ListShareNotificationTest` use, not hand-written fakes), covering:

- Regression: uncomplete still restores in place, unchanged, when the list was never deleted
  (including `listDeleted == false` on the GET listing).
- Complete → delete the list → uncomplete recreates it with the original name/color, lands the
  floater there, and `listRecreated == true` (including `listDeleted == true` on the GET listing
  before the undo, and the FK-nulled-but-`originalListID`-survives assertion directly against the
  row).
- A second item completed from the same list, undone after the first, converges onto the *same*
  recreated list — asserted both by comparing `listID` across the two responses and by counting
  `FloaterLists` rows.
- `CompletedFloaterService.deleteById()` no longer orphans a `Floaters` row.

`FloaterRoutesTest`, `RateLimitingTest`, `TodoRoutesTest`, `McpTestWorld` (MCP tool test double)
were updated for the new `uncompleteFloater()` return type; no behavioral change to those tests.
