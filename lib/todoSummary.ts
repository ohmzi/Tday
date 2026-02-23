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

function dueFormatter(timeZone: string): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat("en-US", {
    timeZone,
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function buildSummaryPrompt({
  mode,
  todos,
  timeZone,
  now = new Date(),
}: SummaryContext): string {
  const formatter = dueFormatter(timeZone);
  const tasks = todos.slice(0, 120).map((todo, index) => {
    const lineIndex = index + 1;
    const dueText = formatter.format(todo.due);
    const priority = normalizePriority(todo.priority);
    const title = todo.title.trim() || "(Untitled task)";
    return `${lineIndex}. [${priority}] ${title} | due ${dueText}`;
  });

  return [
    "You are an assistant summarizing a task list for a todo app.",
    `Screen: ${modeLabel(mode)}`,
    `User timezone: ${timeZone}`,
    `Current time: ${now.toISOString()}`,
    "",
    "Write a concise and practical summary in plain English:",
    "- 1 short overview sentence",
    "- 2 to 4 bullet points with what needs attention soon",
    "- mention urgent or high-priority risk first",
    "- keep under 120 words",
    "",
    `Tasks (${todos.length} total for this screen):`,
    ...tasks,
  ].join("\n");
}

export function buildFallbackSummary({
  mode,
  todos,
  timeZone,
  now = new Date(),
}: SummaryContext): string {
  if (todos.length === 0) {
    return `${modeLabel(mode)} is clear right now. No tasks match this view.`;
  }

  const formatter = dueFormatter(timeZone);
  const { start, end } = getTodayBounds(now, timeZone);
  const highCount = todos.filter((todo) => {
    const normalized = normalizePriority(todo.priority).toLowerCase();
    return normalized === "high" || normalized === "urgent" || normalized === "important";
  }).length;
  const mediumCount = todos.filter((todo) => normalizePriority(todo.priority).toLowerCase() === "medium").length;
  const overdueCount = todos.filter((todo) => todo.due < now).length;
  const dueTodayCount = todos.filter((todo) => todo.due >= start && todo.due <= end).length;
  const nextTasks = todos
    .filter((todo) => todo.due >= now)
    .slice(0, 3)
    .map((todo) => `- ${todo.title} (${formatter.format(todo.due)})`);

  const lines = [
    `${modeLabel(mode)} has ${todos.length} active task${todos.length == 1 ? "" : "s"}.`,
    `Priority mix: ${highCount} high/urgent, ${mediumCount} medium, ${Math.max(todos.length - highCount - mediumCount, 0)} low.`,
  ];

  if (overdueCount > 0) {
    lines.push(`There are ${overdueCount} overdue task${overdueCount === 1 ? "" : "s"} needing attention.`);
  }
  if (dueTodayCount > 0) {
    lines.push(`${dueTodayCount} task${dueTodayCount === 1 ? "" : "s"} are due today.`);
  }
  if (nextTasks.length > 0) {
    lines.push("Next up:");
    lines.push(...nextTasks);
  }

  return lines.join("\n");
}
