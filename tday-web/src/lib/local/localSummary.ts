import { toZonedTime } from "date-fns-tz";
import i18n from "@/i18n";
import {
  loadWorkspace,
  type LocalTodoRow,
  LOCAL_USER_ID,
} from "@/lib/local/localDb";
import { localBadRequest } from "@/lib/local/localError";
import { epochMs } from "@/lib/local/localTime";
import { parseRecurrencePriority, parseTodoTitle } from "@/lib/todoNlp";
import {
  buildReadableTaskSummary,
  buildSummaryTaskCandidates,
  type SummaryTaskCandidate,
} from "@/lib/todoSummary";
import type { TodoItemType } from "@/types";

/**
 * Local-mode task summary and brain dump.
 *
 * Server Mode may reach an AI model; a browser workspace never can, so these
 * always answer with `source: "logic"` — the same deterministic engine the
 * backend falls back to (`SummaryEngine`), driven here by the web original in
 * `lib/todoSummary.ts`.
 */

const SUMMARY_MODES = new Set([
  "today",
  "overdue",
  "scheduled",
  "all",
  "priority",
  "list",
  "floater",
  "anytime",
  "week",
]);

const PRIORITY_ALIASES = new Set(["medium", "high", "important", "urgent"]);
const WEEK_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_FRAGMENTS = 50;

function st(key: string, options?: Record<string, unknown>): string {
  return i18n.t(`summary:${key}`, options ?? {}) as string;
}

function compactTitle(title: string): string {
  const normalized = title.replace(/\s+/g, " ").trim();
  if (!normalized) return st("untitledTask");
  if (normalized.length <= 46) return normalized;
  return `${normalized.slice(0, 43).trimEnd()}...`;
}

function priorityLabel(priority: string): SummaryTaskCandidate["priorityLabel"] {
  const normalized = priority.trim().toLowerCase();
  if (normalized === "high" || normalized === "urgent" || normalized === "important") {
    return "high";
  }
  return normalized === "medium" ? "medium" : "low";
}

function todoPriority(priority: string): TodoItemType["priority"] {
  const label = priorityLabel(priority);
  if (label === "high") return "High";
  return label === "medium" ? "Medium" : "Low";
}

/** A workspace row shaped as the `TodoItemType` the summary helpers consume. */
function toSummaryTodo(row: LocalTodoRow): TodoItemType | null {
  const due = epochMs(row.due);
  if (due === null) return null;
  return {
    id: row.id,
    title: row.title,
    description: row.description,
    pinned: row.pinned,
    createdAt: new Date(epochMs(row.createdAt) ?? Date.now()),
    order: row.order,
    priority: todoPriority(row.priority),
    due: new Date(due),
    rrule: row.rrule,
    timeZone: row.timeZone ?? "UTC",
    userID: LOCAL_USER_ID,
    completed: row.completed,
    exdates: [],
    listID: row.listID,
  };
}

function matchesScope(
  todo: TodoItemType,
  mode: string,
  listId: string | null,
  now: Date,
  timeZone: string,
): boolean {
  switch (mode) {
    case "overdue":
      return todo.due.getTime() < now.getTime();
    case "scheduled":
      return todo.due.getTime() >= now.getTime();
    case "all":
      return true;
    case "priority":
      return PRIORITY_ALIASES.has(todo.priority.trim().toLowerCase());
    case "list":
      return listId !== null && todo.listID === listId;
    default: {
      const zonedDue = toZonedTime(todo.due, timeZone);
      const zonedNow = toZonedTime(now, timeZone);
      return (
        zonedDue.getFullYear() === zonedNow.getFullYear() &&
        zonedDue.getMonth() === zonedNow.getMonth() &&
        zonedDue.getDate() === zonedNow.getDate()
      );
    }
  }
}

function anytimeCandidates(
  titles: Array<{ title: string; priority: string }>,
): SummaryTaskCandidate[] {
  const anytime = st("dueAnytime");
  return titles.map((entry, index) => ({
    id: `T${index + 1}`,
    title: compactTitle(entry.title),
    priorityLabel: priorityLabel(entry.priority),
    dueLabel: anytime,
    dueEpochMs: Number.MAX_SAFE_INTEGER,
    dueDayKey: "anytime",
    dueDayTarget: anytime,
    dueWindowPhrase: "",
    isOverdue: false,
  }));
}

/** Retrospective over the last 7 days of cleared work (mirrors `buildWeekReview`). */
function weekInReview(now: Date, timeZone: string): string {
  const workspace = loadWorkspace();
  const windowStart = now.getTime() - WEEK_WINDOW_MS;
  const cleared = workspace.completedTodos.filter((entry) => {
    const completedAt = epochMs(entry.completedAt);
    return completedAt !== null && completedAt >= windowStart && completedAt <= now.getTime();
  });
  if (cleared.length === 0) return st("weekNone");

  // 0 = Monday … 6 = Sunday, matching the order of `summary.weekdaysShort`.
  const byWeekday = new Map<number, number>();
  for (const entry of cleared) {
    const completedAt = epochMs(entry.completedAt);
    if (completedAt === null) continue;
    const zoned = toZonedTime(new Date(completedAt), timeZone);
    const weekday = (zoned.getDay() + 6) % 7;
    byWeekday.set(weekday, (byWeekday.get(weekday) ?? 0) + 1);
  }
  const busiest = [...byWeekday.entries()].reduce<[number, number] | null>(
    (best, entry) => (best === null || entry[1] > best[1] ? entry : best),
    null,
  );

  // Oldest cleared: the one that was due earliest, else completed earliest.
  const withDue = cleared.filter((entry) => epochMs(entry.due) !== null);
  const rankBy = withDue.length > 0 ? withDue : cleared;
  const rankValue = (entry: (typeof cleared)[number]) =>
    (withDue.length > 0 ? epochMs(entry.due) : epochMs(entry.completedAt)) ?? 0;
  const oldest = rankBy.reduce((best, entry) =>
    rankValue(entry) < rankValue(best) ? entry : best,
  );

  const weekdays = i18n.t("summary:weekdaysShort", { returnObjects: true });
  const sentences = [st("weekCleared", { count: cleared.length })];
  if (busiest && Array.isArray(weekdays) && weekdays.length === 7) {
    sentences.push(
      st("weekBusiest", { day: String(weekdays[busiest[0]]), count: busiest[1] }),
    );
  }
  sentences.push(st("weekOldest", { title: compactTitle(oldest.title) }));
  return sentences.join(" ");
}

export function summarizeLocal(body: Record<string, unknown>) {
  const mode = typeof body.mode === "string" && body.mode.trim().length > 0
    ? body.mode.trim().toLowerCase()
    : "today";
  if (!SUMMARY_MODES.has(mode)) {
    throw localBadRequest("summary mode is invalid", "mode");
  }
  const timeZone =
    typeof body.timeZone === "string" && body.timeZone.length > 0
      ? body.timeZone
      : "UTC";
  const listId =
    typeof body.listId === "string" && body.listId.trim().length > 0
      ? body.listId.trim()
      : null;
  const now = new Date();

  const respond = (summary: string, taskCount: number, reason: string | null) => ({
    summary,
    source: "logic",
    mode: mode === "anytime" ? "floater" : mode,
    taskCount,
    generatedAt: now.toISOString(),
    fallbackReason: reason,
    reason,
  });

  if (mode === "week") {
    const workspace = loadWorkspace();
    return respond(weekInReview(now, timeZone), workspace.completedTodos.length, null);
  }

  const workspace = loadWorkspace();

  if (mode === "floater" || mode === "anytime") {
    const floaters = workspace.floaters
      .filter((floater) => !floater.completed)
      .filter((floater) => listId === null || floater.listID === listId);
    if (floaters.length === 0) return respond(st("clearForNow"), 0, "empty");
    const candidates = anytimeCandidates(floaters);
    return respond(
      buildReadableTaskSummary({
        startTask: candidates[0],
        thenTasks: candidates.slice(1),
      }),
      floaters.length,
      null,
    );
  }

  const scoped = workspace.todos
    .filter((row) => !row.completed)
    .map(toSummaryTodo)
    .filter((todo): todo is TodoItemType => todo !== null)
    .filter((todo) => matchesScope(todo, mode, listId, now, timeZone));

  if (scoped.length === 0) return respond(st("clearForNow"), 0, "empty");

  const candidates = buildSummaryTaskCandidates(scoped, { now, timeZone });
  const overdueCount = scoped.filter((todo) => todo.due.getTime() < now.getTime()).length;
  return respond(
    buildReadableTaskSummary({
      startTask: candidates[0],
      thenTasks: candidates.slice(1),
      overdueCount,
    }),
    scoped.length,
    null,
  );
}

/**
 * Splits a free-text blob into candidate tasks. Mirrors the shared
 * `BrainDumpSplitter` (newlines and bullets, then "and then"/";" separators),
 * then runs each fragment through the web's on-device date + grammar parsers.
 */
export function brainDumpLocal(body: Record<string, unknown>) {
  const text = typeof body.text === "string" ? body.text : "";
  const locale = typeof body.locale === "string" ? body.locale : null;
  const bulletPrefix = /^\s*(?:[-*•·▪◦]|\d+[.)])\s+/;
  const separators = /\s+and then\s+|;|\s+·\s+/i;

  const fragments: string[] = [];
  const seen = new Set<string>();
  for (const rawLine of text.split("\n")) {
    const line = rawLine.replace(/\r/g, " ").trim();
    if (!line) continue;
    for (const piece of line.replace(bulletPrefix, "").split(separators)) {
      const fragment = piece
        .replace(bulletPrefix, "")
        .replace(/\s{2,}/g, " ")
        .trim()
        .replace(/^[,;\-•·]+|[,;\-•·]+$/g, "")
        .trim();
      if (fragment.length < 2) continue;
      const key = fragment.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      fragments.push(fragment);
      if (fragments.length >= MAX_FRAGMENTS) break;
    }
    if (fragments.length >= MAX_FRAGMENTS) break;
  }

  const candidates = fragments.map((fragment) => {
    const dateParse = parseTodoTitle({
      text: fragment,
      locale,
      referenceEpochMs: Date.now(),
    });
    const grammar = parseRecurrencePriority(dateParse.cleanTitle);
    return {
      title: grammar.cleanTitle.trim() || fragment,
      dueEpochMs: dateParse.dueEpochMs,
      rrule: grammar.rrule ?? dateParse.rrule,
      priority: grammar.priority ?? dateParse.priority,
    };
  });

  return { candidates };
}
