import { floaterRestingTier } from "@/lib/floaterResting";

/**
 * The undated ("Anytime") summary, as a TypeScript twin of the shared Kotlin
 * `FloaterSummaryPlanner` + `SummaryEngine.renderFloaterSummary`.
 *
 * Web local mode never reaches the backend, so nothing in `shared/` runs for it: a browser
 * workspace is summarized entirely here. That is why the Kotlin rewrite alone left local-mode web
 * reciting "Start with X, which is anytime. Next up, Y and Z, both are due anytime." — and in
 * Italian claiming they were "in scadenza", expiring. This file is the other half of that fix.
 *
 * It is a mirror, so it is written to be diffable against the Kotlin rather than to be idiomatic:
 * same band boundaries, same note precedence, same "name at most one task" rule. The guardrail in
 * `tests/guardrails/floater-summary-parity.test.ts` fails if the shared vocabulary grows a key
 * this file never references.
 */

export type FloaterSummaryTask = {
  title: string;
  priority: string;
  pinned: boolean;
  /** Last write (epoch ms), or null when the row carries no usable stamp. */
  updatedAtEpochMs: number | null;
};

/** How big the pile is, as a shape rather than a number — the count is already on screen. */
export type FloaterPileBand = "one" | "few" | "some" | "many";

/** The single extra thing worth saying about the pile. At most one is ever chosen. */
export type FloaterNote =
  | "none"
  | "pinnedOne"
  | "pinnedMany"
  | "restingOne"
  | "restingMany"
  | "restingAll"
  | "priorityOne"
  | "priorityMany"
  | "mediumOne"
  | "mediumMany";

export type FloaterSummaryPlan = {
  band: FloaterPileBand;
  note: FloaterNote;
  /** The one task the note names, raw and uncompacted, or null when the note names none. */
  noteTitle: string | null;
};

/** Upper bound of "few": still countable at a glance. */
export const FLOATER_FEW_MAX = 4;

/** Upper bound of "some": more than a screenful starts to feel like a backlog. */
export const FLOATER_SOME_MAX = 11;

/** Mirrors the shared `priorityRankOf`: only these three count as high. */
export function isHighPriorityFloater(priority: string): boolean {
  const normalized = priority.trim().toLowerCase();
  return normalized === "high" || normalized === "urgent" || normalized === "important";
}

/** Mirrors the shared `priorityRankOf`: exactly the Medium tier, not High. */
export function isMediumPriorityFloater(priority: string): boolean {
  return priority.trim().toLowerCase() === "medium";
}

export function floaterPileBand(count: number): FloaterPileBand {
  if (count <= 1) return "one";
  if (count <= FLOATER_FEW_MAX) return "few";
  if (count <= FLOATER_SOME_MAX) return "some";
  return "many";
}

export function planFloaterSummary(
  tasks: FloaterSummaryTask[],
  nowEpochMs: number,
): FloaterSummaryPlan {
  if (tasks.length === 0) {
    throw new Error("a floater plan needs at least one task");
  }

  // Mirrors TaskSortEngine.compareFloaters (pinned, then priority, then most recently modified)
  // so the task the summary names is the task sitting at the top of the list beneath it.
  const rankOf = (task: FloaterSummaryTask) =>
    isHighPriorityFloater(task.priority) ? 3 : isMediumPriorityFloater(task.priority) ? 2 : 1;
  const ranked = [...tasks].sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
    if (rankOf(a) !== rankOf(b)) return rankOf(b) - rankOf(a);
    return (b.updatedAtEpochMs ?? 0) - (a.updatedAtEpochMs ?? 0);
  });

  const pinned = tasks.filter((task) => task.pinned).length;
  const resting = tasks.filter(
    (task) => floaterRestingTier(task.updatedAtEpochMs, nowEpochMs) === "resting",
  ).length;
  const high = tasks.filter((task) => isHighPriorityFloater(task.priority)).length;
  const medium = tasks.filter((task) => isMediumPriorityFloater(task.priority)).length;

  // A wholly dormant pile outranks a pin: when nothing has been touched in months, "you pinned
  // one of these" is not the story. Below that the pin wins — it is the one mark the person made.
  let note: FloaterNote = "none";
  if (resting === tasks.length) note = "restingAll";
  else if (pinned === 1) note = "pinnedOne";
  else if (pinned > 1) note = "pinnedMany";
  else if (resting === 1) note = "restingOne";
  else if (resting > 1) note = "restingMany";
  else if (high === 1) note = "priorityOne";
  else if (high > 1) note = "priorityMany";
  // Below High: Medium is still worth naming over saying nothing about priority at all, but
  // it never outranks High — a pile with both gets the High note only.
  else if (medium === 1) note = "mediumOne";
  else if (medium > 1) note = "mediumMany";

  // The resting notes name nobody: updatedAt is a last-write clock, so "this one has waited
  // longest" is a claim about creation time that a rename silently falsifies.
  let noteTitle: string | null = null;
  if (note === "pinnedOne" || note === "pinnedMany") {
    noteTitle = ranked.find((task) => task.pinned)?.title ?? null;
  } else if (note === "priorityOne" || note === "priorityMany") {
    noteTitle = ranked.find((task) => isHighPriorityFloater(task.priority))?.title ?? null;
  } else if (note === "mediumOne" || note === "mediumMany") {
    noteTitle = ranked.find((task) => isMediumPriorityFloater(task.priority))?.title ?? null;
  }

  // Naming the only row on screen is an echo, not a summary. A single-task pile keeps the notes
  // that COUNT something and drops the ones that point at a task.
  const single = tasks.length === 1;
  const naming = noteTitle !== null;
  return {
    band: floaterPileBand(tasks.length),
    note: single && naming ? "none" : note,
    noteTitle: single && naming ? null : noteTitle,
  };
}

const PILE_KEYS: Record<FloaterPileBand, string> = {
  one: "floaterPileOne",
  few: "floaterPileFew",
  some: "floaterPileSome",
  many: "floaterPileMany",
};

const NOTE_KEYS: Record<Exclude<FloaterNote, "none">, string> = {
  pinnedOne: "floaterPinnedOne",
  pinnedMany: "floaterPinnedMany",
  restingOne: "floaterRestingOne",
  restingMany: "floaterRestingMany",
  restingAll: "floaterRestingAll",
  priorityOne: "floaterPriorityOne",
  priorityMany: "floaterPriorityMany",
  mediumOne: "floaterMediumOne",
  mediumMany: "floaterMediumMany",
};

export type SummaryTranslate = (key: string, params?: Record<string, unknown>) => string;

/** Compaction shared with the dated paths — mirrors the engine's `compactTitle` exactly. */
export function compactSummaryTitle(title: string, t: SummaryTranslate): string {
  const normalized = title.replace(/\s+/g, " ").trim();
  if (!normalized) return t("untitledTask");
  if (normalized.length <= 46) return normalized;
  return `${normalized.slice(0, 43).trimEnd()}...`;
}

/**
 * One sentence about the pile plus, at most, one about the single thing worth noticing. It never
 * enumerates titles: the rows are already on screen, and re-printing them was ~72% of the old
 * twelve-floater summary.
 */
export function renderFloaterSummary(plan: FloaterSummaryPlan, t: SummaryTranslate): string {
  const pile = t(PILE_KEYS[plan.band]);
  if (plan.note === "none") return pile;
  const note = t(NOTE_KEYS[plan.note], {
    title: compactSummaryTitle(plan.noteTitle ?? "", t),
  });
  // Chinese and Japanese set their own full stop with the space built in; an ASCII one leaves a
  // visible gap mid-summary. Keyed off the punctuation the locale actually used, so a new bundle
  // needs no change here.
  const gap = pile.endsWith("。") || pile.endsWith("！") ? "" : " ";
  return pile + gap + note;
}
