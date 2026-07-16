// T'Day's FIXED, non-configurable task ordering — the TypeScript twin of the
// shared Kotlin engine at
//   shared/src/commonMain/kotlin/com/ohmz/tday/shared/sort/TaskSortEngine.kt
//
// There is NO user setting: this is simply how tasks are presented everywhere
// (web, desktop, both native apps, and their widgets). Keep this file in lockstep
// with the Kotlin source (and the iOS Swift twin) by hand — mirror it exactly:
//
//   - Todos (scheduled screen + custom lists, applied WITHIN each day group):
//       pinned first, then due date+time ascending (soonest first, a null/absent
//       due sorts LAST), then priority (High → Medium → Low), then modified date
//       DESCENDING (most recently modified first, null last), then id ascending.
//   - Floaters:
//       pinned first, then priority (High → Medium → Low), then modified date
//       DESCENDING, then id ascending.

/**
 * Minimal, platform-neutral view of a task. Each surface maps its own model onto
 * this so presentation order is identical no matter where the user opens their
 * account. `dueEpochMs`/`updatedAtEpochMs` are UTC millis; a null due sorts LAST.
 * `priorityRank` is 0-highest (see {@link priorityRank}). `pinned` tasks lead.
 */
export interface TaskSortKey {
  id: string;
  pinned: boolean;
  dueEpochMs: number | null;
  priorityRank: number;
  updatedAtEpochMs: number | null;
}

/** Unknown/absent priority sorts as Low. Mirrors Priority.fromApiOrDefault. */
const LOWEST_PRIORITY_RANK = 2;

const pinRank = (pinned: boolean): number => (pinned ? 1 : 0);

/** Pinned tasks always lead. */
const comparePinned = (a: TaskSortKey, b: TaskSortKey): number =>
  pinRank(b.pinned) - pinRank(a.pinned);

/** Floor a UTC-epoch-millis instant to its minute (drop seconds/millis). */
const floorToMinute = (epochMs: number): number => epochMs - (epochMs % 60_000);

/**
 * Due ascending (soonest first); a null/absent due sorts LAST. Compared at MINUTE precision:
 * times are shown to the minute ("9:41 PM"), so two tasks in the same clock minute differing
 * only by seconds are the "same time" and fall through to the priority tiebreak.
 */
const compareDueAscNullsLast = (a: TaskSortKey, b: TaskSortKey): number => {
  const x = a.dueEpochMs === null ? null : floorToMinute(a.dueEpochMs);
  const y = b.dueEpochMs === null ? null : floorToMinute(b.dueEpochMs);
  if (x === null && y === null) return 0;
  if (x === null) return 1;
  if (y === null) return -1;
  if (x < y) return -1;
  if (x > y) return 1;
  return 0;
};

/** Priority High(0) → Medium(1) → Low(2). */
const comparePriority = (a: TaskSortKey, b: TaskSortKey): number =>
  a.priorityRank - b.priorityRank;

/** Modified DESCENDING (most recently modified first); a null timestamp sorts last. */
const compareModifiedDesc = (a: TaskSortKey, b: TaskSortKey): number => {
  const x = a.updatedAtEpochMs;
  const y = b.updatedAtEpochMs;
  if (x === null && y === null) return 0;
  if (x === null) return 1; // no timestamp sorts last
  if (y === null) return -1;
  if (y < x) return -1; // DESC: most recently modified first
  if (y > x) return 1;
  return 0;
};

/** id ascending — the final, stable tiebreak (lexicographic by code unit). */
const compareId = (a: TaskSortKey, b: TaskSortKey): number => {
  if (a.id < b.id) return -1;
  if (a.id > b.id) return 1;
  return 0;
};

export function compareTodos(a: TaskSortKey, b: TaskSortKey): number {
  const pinned = comparePinned(a, b);
  if (pinned !== 0) return pinned;
  const due = compareDueAscNullsLast(a, b);
  if (due !== 0) return due;
  const priority = comparePriority(a, b);
  if (priority !== 0) return priority;
  const modified = compareModifiedDesc(a, b);
  if (modified !== 0) return modified;
  return compareId(a, b);
}

export function compareFloaters(a: TaskSortKey, b: TaskSortKey): number {
  const pinned = comparePinned(a, b);
  if (pinned !== 0) return pinned;
  const priority = comparePriority(a, b);
  if (priority !== 0) return priority;
  const modified = compareModifiedDesc(a, b);
  if (modified !== 0) return modified;
  return compareId(a, b);
}

/** Non-mutating sort of todos by the fixed todo ordering. */
export function sortTodos<T>(items: T[], key: (item: T) => TaskSortKey): T[] {
  return items.slice().sort((a, b) => compareTodos(key(a), key(b)));
}

/** Non-mutating sort of floaters by the fixed floater ordering. */
export function sortFloaters<T>(items: T[], key: (item: T) => TaskSortKey): T[] {
  return items.slice().sort((a, b) => compareFloaters(key(a), key(b)));
}

/**
 * 0 = highest priority (sorts first). Tolerant of every priority spelling the app
 * stores: canonical Low/Medium/High, the server/legacy vocabulary normal/important/
 * urgent, and any case. Realtime-synced rows arrive un-normalized, so a strict match
 * would collapse them to Low and the sort would ignore priority. Unknown/absent → Low.
 * Mirrors the shared Kotlin `TaskSortEngine.priorityRank(String?)`.
 */
export function priorityRank(priority: string | null | undefined): number {
  switch (priority?.trim().toLowerCase()) {
    case "high":
    case "urgent":
      return 0;
    case "medium":
    case "important":
      return 1;
    default:
      return LOWEST_PRIORITY_RANK;
  }
}
