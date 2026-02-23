import { TodoItemType } from "@/types";
import { differenceInCalendarDays, endOfDay, format, startOfDay } from "date-fns";
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
  const { start, end } = getTodayBounds(now, timeZone);

  const filtered = (() => {
    if (mode === "all") {
      return activeTodos.filter((todo) => todo.due >= start);
    }

    if (mode === "scheduled") {
      return activeTodos.filter((todo) => !todo.due || todo.due >= now);
    }

    if (mode === "priority") {
      return activeTodos.filter((todo) => isPriorityTodo(todo.priority));
    }

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

function dueDayDelta(due: Date, now: Date, timeZone: string): number {
  const zonedNow = toZonedTime(now, timeZone);
  const zonedDue = toZonedTime(due, timeZone);
  return differenceInCalendarDays(startOfDay(zonedDue), startOfDay(zonedNow));
}

function urgencyBand(dayDelta: number): number {
  if (dayDelta < 0) return 0;
  if (dayDelta === 0) return 1;
  if (dayDelta === 1) return 2;
  if (dayDelta <= 7) return 3;
  if (dayDelta <= 30) return 4;
  return 5;
}

function compactTitle(title: string | null | undefined): string {
  const normalized = (title ?? "").replace(/\s+/g, " ").trim();
  if (!normalized) return "Untitled task";
  if (normalized.length <= 46) return normalized;
  return `${normalized.slice(0, 43).trimEnd()}...`;
}

function joinTaskTitles(titles: string[]): string {
  if (titles.length === 0) return "";
  if (titles.length === 1) return titles[0];
  if (titles.length === 2) return `${titles[0]} and ${titles[1]}`;
  return `${titles[0]}, ${titles[1]}, and ${titles[2]}`;
}

function taskPhrase(task: Pick<SummaryTaskCandidate, "title" | "dueLabel">): string {
  return `${task.title} (${task.dueLabel})`;
}

function formatDueLabel(due: Date, now: Date, timeZone: string): string {
  const zonedNow = toZonedTime(now, timeZone);
  const zonedDue = toZonedTime(due, timeZone);
  const dayDelta = differenceInCalendarDays(startOfDay(zonedDue), startOfDay(zonedNow));

  if (dayDelta === 0) {
    return "due today";
  }
  if (dayDelta === 1) {
    return "due tomorrow";
  }
  if (dayDelta === -1) {
    return "due yesterday";
  }

  const sameYear = zonedDue.getFullYear() === zonedNow.getFullYear();
  return `due on ${format(zonedDue, sameYear ? "do MMM" : "do MMM yyyy")}`;
}

export type SummaryTaskCandidate = {
  id: string;
  title: string;
  dueLabel: string;
  dueEpochMs: number;
};

export function buildReadableTaskSummary({
  startTask,
  thenTasks,
  overdueCount = 0,
}: {
  startTask: SummaryTaskCandidate;
  thenTasks: SummaryTaskCandidate[];
  overdueCount?: number;
}): string {
  const sentences = [`Start with ${taskPhrase(startTask)}.`];
  if (thenTasks.length === 1) {
    sentences.push(`Next up is ${taskPhrase(thenTasks[0])}.`);
  } else if (thenTasks.length > 1) {
    sentences.push(`Next up are ${joinTaskTitles(thenTasks.map(taskPhrase))}.`);
  }
  if (overdueCount > 0) {
    sentences.push(
      `You also have ${overdueCount} overdue task${overdueCount === 1 ? "" : "s"} to catch up on.`,
    );
  }
  return sentences.join(" ");
}

export function buildSummaryTaskCandidates(
  todos: TodoItemType[],
  {
    mode = "all",
    now = new Date(),
    timeZone = "UTC",
  }: {
    mode?: TodoSummaryMode;
    now?: Date;
    timeZone?: string;
  } = {},
): SummaryTaskCandidate[] {
  if (todos.length === 0) return [];
  const ranked = todos
    .map((todo) => {
      const dayDelta = dueDayDelta(todo.due, now, timeZone);
      return {
        todo,
        dueEpochMs: todo.due.getTime(),
        dayDelta,
        band: urgencyBand(dayDelta),
        priority: priorityRank(todo.priority),
      };
    })
    .sort((a, b) => {
      if (a.band !== b.band) return a.band - b.band;

      if (mode === "priority") {
        const priorityDelta = b.priority - a.priority;
        if (priorityDelta !== 0) return priorityDelta;
      }

      if (a.dueEpochMs !== b.dueEpochMs) return a.dueEpochMs - b.dueEpochMs;
      return b.priority - a.priority;
    })
    .slice(0, 5);

  return ranked.map(({ todo, dueEpochMs }, index) => ({
    id: `T${index + 1}`,
    title: compactTitle(todo.title),
    dueLabel: formatDueLabel(todo.due, now, timeZone),
    dueEpochMs,
  }));
}

export function buildSummaryPrompt({
  mode,
  todos,
  timeZone,
  now = new Date(),
}: SummaryContext): string {
  const candidates = buildSummaryTaskCandidates(todos, { mode, now, timeZone });
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
    "- Start with the earliest-due task.",
    "- Keep thenIds ordered from sooner due to later due.",
    "- Use only IDs from the task list.",
    "- thenIds must contain 1 to 3 IDs.",
    "- Do not include times, dates, priorities, labels, or any extra keys.",
    "- No prose, no markdown, no code fences.",
    "",
    "Tasks:",
    ...candidates.map((task) => `- ${task.id}: ${task.title} (${task.dueLabel})`),
  ].join("\n");
}

export function buildFallbackSummary({
  mode,
  todos,
  timeZone,
  now = new Date(),
}: SummaryContext): string {
  if (todos.length === 0) {
    return "You're clear for now. No tasks need attention in this view.";
  }

  const candidates = buildSummaryTaskCandidates(todos, { mode, now, timeZone });
  const focusCandidate = candidates[0];
  if (!focusCandidate) {
    return "You're clear for now. No tasks need attention in this view.";
  }
  const nextTasks = candidates.slice(1, 4);
  const overdueCount = todos.filter((todo) => todo.due < now).length;
  return buildReadableTaskSummary({
    startTask: focusCandidate,
    thenTasks: nextTasks,
    overdueCount,
  });
}
