import { TodoItemType } from "@/types";
import { endOfDay, startOfDay } from "date-fns";
import { fromZonedTime, toZonedTime } from "date-fns-tz";

export type TodoSummaryMode = "today" | "scheduled" | "all" | "priority";

type SummaryContext = {
  mode: TodoSummaryMode;
  todos: TodoItemType[];
  timeZone: string;
  now?: Date;
};

const PRIORITY_ALIASES = new Set(["medium", "high", "important", "urgent"]);

function normalizePriority(priority: string | null | undefined): string {
  return (priority ?? "Low").trim();
}

function isPriorityTodo(priority: string | null | undefined): boolean {
  const normalized = (priority ?? "").trim().toLowerCase();
  return PRIORITY_ALIASES.has(normalized);
}

function getTodayBounds(now: Date, timeZone: string): { start: Date; end: Date } {
  const zonedNow = toZonedTime(now, timeZone);
  const start = fromZonedTime(startOfDay(zonedNow), timeZone);
  const end = fromZonedTime(endOfDay(zonedNow), timeZone);
  return { start, end };
}

export function filterTodosForSummaryMode({
  mode,
  todos,
  timeZone,
  now = new Date(),
}: SummaryContext): TodoItemType[] {
  const activeTodos = todos.filter((todo) => !todo.completed);

  const filtered = (() => {
    if (mode === "all") {
      return activeTodos;
    }

    if (mode === "scheduled") {
      return activeTodos.filter((todo) => !todo.due || todo.due >= now);
    }

    if (mode === "priority") {
      return activeTodos.filter((todo) => isPriorityTodo(todo.priority));
    }

    const { start, end } = getTodayBounds(now, timeZone);
    return activeTodos.filter((todo) => todo.due >= start && todo.dtstart <= end);
  })();

  return filtered.sort((a, b) => a.due.getTime() - b.due.getTime());
}

function modeLabel(mode: TodoSummaryMode): string {
  switch (mode) {
    case "today":
      return "Today";
    case "scheduled":
      return "Scheduled";
    case "priority":
      return "Priority";
    case "all":
      return "All Tasks";
  }
}

function priorityRank(priority: string | null | undefined): number {
  const normalized = normalizePriority(priority).toLowerCase();
  if (normalized === "high" || normalized === "urgent" || normalized === "important") {
    return 3;
  }
  if (normalized === "medium") {
    return 2;
  }
  return 1;
}

function compactTitle(title: string | null | undefined): string {
  const normalized = (title ?? "").replace(/\s+/g, " ").trim();
  if (!normalized) return "Untitled task";
  if (normalized.length <= 46) return normalized;
  return `${normalized.slice(0, 43).trimEnd()}...`;
}

function pickFocusTodo(todos: TodoItemType[]): TodoItemType {
  return [...todos].sort((a, b) => {
    const rankDelta = priorityRank(b.priority) - priorityRank(a.priority);
    if (rankDelta !== 0) return rankDelta;
    return a.due.getTime() - b.due.getTime();
  })[0];
}

function sortedRemainingTodos(
  todos: TodoItemType[],
  focusTodo: TodoItemType,
): TodoItemType[] {
  return todos
    .filter((todo) => todo !== focusTodo)
    .sort((a, b) => a.due.getTime() - b.due.getTime());
}

function joinTaskTitles(titles: string[]): string {
  if (titles.length === 0) return "";
  if (titles.length === 1) return titles[0];
  if (titles.length === 2) return `${titles[0]} and ${titles[1]}`;
  return `${titles[0]}, ${titles[1]}, and ${titles[2]}`;
}

export type SummaryTaskCandidate = {
  id: string;
  title: string;
};

export function buildSummaryTaskCandidates(
  todos: TodoItemType[],
): SummaryTaskCandidate[] {
  if (todos.length === 0) return [];
  const focusTodo = pickFocusTodo(todos);
  const ordered = [focusTodo, ...sortedRemainingTodos(todos, focusTodo)].slice(0, 5);
  return ordered.map((todo, index) => ({
    id: `T${index + 1}`,
    title: compactTitle(todo.title),
  }));
}

export function buildSummaryPrompt({
  mode,
  todos,
  timeZone,
  now = new Date(),
}: SummaryContext): string {
  const candidates = buildSummaryTaskCandidates(todos);
  if (candidates.length === 0) {
    return [
      "You are writing a short planning note for a todo app.",
      `Screen: ${modeLabel(mode)}`,
      `Timezone: ${timeZone}`,
      `Current time: ${now.toISOString()}`,
      "",
      "Return ONLY valid JSON with no extra text:",
      '{"startId":null,"thenIds":[]}',
    ].join("\n");
  }

  return [
    "You are writing a short planning note for a todo app.",
    `Screen: ${modeLabel(mode)}`,
    `Timezone: ${timeZone}`,
    `Current time: ${now.toISOString()}`,
    "",
    "Return ONLY valid JSON.",
    "Schema:",
    '{"startId":"T1","thenIds":["T2","T3"]}',
    "Rules:",
    "- Use only IDs from the task list.",
    "- thenIds must contain 1 to 3 IDs.",
    "- Do not include times, dates, priorities, labels, or any extra keys.",
    "- No prose, no markdown, no code fences.",
    "",
    "Tasks:",
    ...candidates.map((task) => `- ${task.id}: ${task.title}`),
  ].join("\n");
}

export function buildFallbackSummary({
  todos,
  now = new Date(),
}: SummaryContext): string {
  if (todos.length === 0) {
    return "- You're clear for now. No tasks need attention in this view.";
  }

  const candidates = buildSummaryTaskCandidates(todos);
  const focusTitle = candidates[0]?.title ?? "your next task";
  const nextTitles = candidates.slice(1, 4).map((candidate) => candidate.title);
  const overdueCount = todos.filter((todo) => todo.due < now).length;

  const lines = [`- Start with ${focusTitle}.`];
  if (nextTitles.length > 0) {
    lines.push(`- Then move through ${joinTaskTitles(nextTitles)}.`);
  }
  if (overdueCount > 0) {
    lines.push(
      `- You also have ${overdueCount} overdue task${overdueCount === 1 ? "" : "s"} to catch up on.`,
    );
  }

  return lines.join("\n");
}
