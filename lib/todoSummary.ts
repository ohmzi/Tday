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
const DUE_WINDOW_DAY_RANGE = 3;

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
      return activeTodos;
    }

    if (mode === "scheduled") {
      return activeTodos.filter((todo) => todo.due >= start);
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

function summaryPriorityLabel(priority: string | null | undefined): "high" | "medium" | "low" {
  const normalized = normalizePriority(priority).toLowerCase();
  if (normalized === "high" || normalized === "urgent" || normalized === "important") {
    return "high";
  }
  if (normalized === "medium") {
    return "medium";
  }
  return "low";
}

function urgencyStyle(priorityLabel: SummaryTaskCandidate["priorityLabel"]): string {
  if (priorityLabel === "high") return "urgent";
  if (priorityLabel === "medium") return "important";
  return "routine";
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
  return `${titles.slice(0, -1).join(", ")}, and ${titles[titles.length - 1]}`;
}

function taskPhrase(task: Pick<SummaryTaskCandidate, "title" | "dueLabel">): string {
  return `${task.title} (${task.dueLabel})`;
}

function buildUrgencyLead(tasks: SummaryTaskCandidate[]): string | null {
  const urgentCount = tasks.filter((task) => task.priorityLabel === "high").length;
  const importantCount = tasks.filter((task) => task.priorityLabel === "medium").length;

  if (urgentCount > 0 && importantCount > 0) {
    return "Handle the most urgent work first, then move to the important items.";
  }
  if (urgentCount > 0) {
    return "Handle the most urgent work first.";
  }
  if (importantCount > 0) {
    return "Start with the most important work first.";
  }
  return null;
}

function buildGroupedThenPhrase(thenTasks: SummaryTaskCandidate[]): string {
  if (thenTasks.length === 0) return "";
  if (thenTasks.length === 1) return taskPhrase(thenTasks[0]);

  const dayGroups = thenTasks.reduce<Array<{ dayKey: string; tasks: SummaryTaskCandidate[] }>>((acc, task) => {
    const last = acc[acc.length - 1];
    const dayKey = task.dueDayKey ?? task.dueLabel;
    if (last && last.dayKey === dayKey) {
      last.tasks.push(task);
      return acc;
    }
    acc.push({ dayKey, tasks: [task] });
    return acc;
  }, []);

  const groupPhrases = dayGroups.map((group) => buildDayGroupedPhrase(group.tasks));

  if (groupPhrases.length === 1) {
    return groupPhrases[0];
  }

  return `${groupPhrases.slice(0, -1).join(", then ")}, then ${groupPhrases[groupPhrases.length - 1]}`;
}

function dueWindow(hour: number): "morning" | "afternoon" | "night" {
  if (hour < 12) return "morning";
  if (hour < 18) return "afternoon";
  return "night";
}

function dueWindowPhrase(window: "morning" | "afternoon" | "night"): string {
  switch (window) {
    case "morning":
      return "in the morning";
    case "afternoon":
      return "in the afternoon";
    case "night":
      return "at night";
  }
}

function buildDueDescriptor(due: Date, now: Date, timeZone: string): {
  dueLabel: string;
  dueDayKey: string;
  dueDayTarget: string;
  dueWindowPhrase: string;
} {
  const zonedNow = toZonedTime(now, timeZone);
  const zonedDue = toZonedTime(due, timeZone);
  const dayDelta = differenceInCalendarDays(startOfDay(zonedDue), startOfDay(zonedNow));
  const includeWindowPhrase = Math.abs(dayDelta) <= DUE_WINDOW_DAY_RANGE;
  const window = dueWindow(zonedDue.getHours());
  const neutralWindowPhrase = includeWindowPhrase ? dueWindowPhrase(window) : "";
  const dueDayKey = format(zonedDue, "yyyy-MM-dd");

  if (dayDelta === 0) {
    return {
      dueLabel: window === "night" ? "due tonight" : `due today ${neutralWindowPhrase}`,
      dueDayKey,
      dueDayTarget: "today",
      dueWindowPhrase: neutralWindowPhrase,
    };
  }
  if (dayDelta === 1) {
    return {
      dueLabel: `due tomorrow ${window === "night" ? "night" : window}`,
      dueDayKey,
      dueDayTarget: "tomorrow",
      dueWindowPhrase: neutralWindowPhrase,
    };
  }
  if (dayDelta === -1) {
    return {
      dueLabel: `due yesterday ${window === "night" ? "night" : window}`,
      dueDayKey,
      dueDayTarget: "yesterday",
      dueWindowPhrase: neutralWindowPhrase,
    };
  }

  const sameYear = zonedDue.getFullYear() === zonedNow.getFullYear();
  const dayLabel = format(zonedDue, sameYear ? "do MMM" : "do MMM yyyy");
  const dueDayTarget = `on ${dayLabel}`;
  return {
    dueLabel: neutralWindowPhrase
      ? `due ${dueDayTarget} ${neutralWindowPhrase}`
      : `due ${dueDayTarget}`,
    dueDayKey,
    dueDayTarget,
    dueWindowPhrase: neutralWindowPhrase,
  };
}

function buildDayGroupedPhrase(tasks: SummaryTaskCandidate[]): string {
  if (tasks.length === 0) return "";
  if (tasks.length === 1) return taskPhrase(tasks[0]);

  const windowGroups = tasks.reduce<Array<{ windowPhrase: string; titles: string[] }>>((acc, task) => {
    const last = acc[acc.length - 1];
    const phrase = task.dueWindowPhrase ?? "";
    if (last && last.windowPhrase === phrase) {
      last.titles.push(task.title);
      return acc;
    }
    acc.push({ windowPhrase: phrase, titles: [task.title] });
    return acc;
  }, []);

  const windowPhrases = windowGroups.map((group) => {
    const titles = joinTaskTitles(group.titles);
    return group.windowPhrase ? `${titles} ${group.windowPhrase}` : titles;
  });
  const joinedTaskPhrases = joinTaskTitles(windowPhrases);
  const dueTarget = tasks[0].dueDayTarget ?? tasks[0].dueLabel.replace(/^due\s+/, "");
  const qualifier = tasks.length === 2 ? "both" : "all";
  return `${joinedTaskPhrases} (${qualifier} due ${dueTarget})`;
}

export type SummaryTaskCandidate = {
  id: string;
  title: string;
  priorityLabel: "high" | "medium" | "low";
  dueLabel: string;
  dueEpochMs: number;
  dueDayDelta?: number;
  dueDayKey?: string;
  dueDayTarget?: string;
  dueWindowPhrase?: string;
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
  const sentences: string[] = [];
  const urgencyLead = buildUrgencyLead([startTask, ...thenTasks]);
  if (urgencyLead) {
    sentences.push(urgencyLead);
  }
  sentences.push(`Start with ${taskPhrase(startTask)}.`);
  if (thenTasks.length === 1) {
    sentences.push(`Next up is ${buildGroupedThenPhrase(thenTasks)}.`);
  } else if (thenTasks.length > 1) {
    sentences.push(`Next up are ${buildGroupedThenPhrase(thenTasks)}.`);
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
    });

  return ranked.map(({ todo, dueEpochMs, dayDelta }, index) => ({
    ...buildDueDescriptor(todo.due, now, timeZone),
    id: `T${index + 1}`,
    title: compactTitle(todo.title),
    priorityLabel: summaryPriorityLabel(todo.priority),
    dueEpochMs,
    dueDayDelta: dayDelta,
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
    '{"startId":"T1","thenIds":["T2","T3"],"summary":"Start with ..."}',
    "Rules:",
    "- Start with the earliest-due task.",
    "- Keep thenIds ordered from sooner due to later due.",
    "- Use only IDs from the task list.",
    "- thenIds may include any number of IDs.",
    "- summary should be natural-sounding English, easy to read aloud.",
    "- summary must consider all tasks shown in this view, not just the first few.",
    "- summary should mention due timing in plain language (today/tomorrow/date).",
    "- Use morning/afternoon/night only for tasks due within 3 days of now; for tasks farther away, treat them as all-day.",
    "- Convey urgency naturally in plain English (for example: urgent first, then important, then later tasks).",
    "- Avoid explicit labels like 'high priority' or 'medium priority'.",
    "- If multiple tasks share the same day, avoid repeating that date phrase for each task.",
    "- Do not include markdown or extra keys.",
    "- No prose, no markdown, no code fences.",
    "",
    "Tasks:",
    ...candidates.map((task) => `- ${task.id}: ${task.title} (${urgencyStyle(task.priorityLabel)}, ${task.dueLabel})`),
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
  const nextTasks = candidates.slice(1);
  const overdueCount = todos.filter((todo) => todo.due < now).length;
  return buildReadableTaskSummary({
    startTask: focusCandidate,
    thenTasks: nextTasks,
    overdueCount,
  });
}
