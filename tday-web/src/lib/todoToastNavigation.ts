import { TodoItemType } from "@/types";

export const TODO_FOCUS_TASK_QUERY_PARAM = "focusTask";
export const TODO_FOCUS_DATE_QUERY_PARAM = "focusDate";
export const TODO_FOCUS_MODE_QUERY_PARAM = "focusMode";
export const TODO_FOCUS_MODE_DELETED = "deleted";

const FOCUS_DATE_KEY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

type TodoToastTarget = Pick<TodoItemType, "id" | "due">;
type TodoDateTarget = Pick<TodoItemType, "due">;

function getTimeZoneDate(date: Date, timeZone?: string) {
  return new Date(date.toLocaleString("en-US", { timeZone: timeZone || "UTC" }));
}

export function getTodoDayKey(date: Date, timeZone?: string) {
  const dateInTimezone = getTimeZoneDate(date, timeZone);
  const y = dateInTimezone.getFullYear();
  const m = String(dateInTimezone.getMonth() + 1).padStart(2, "0");
  const d = String(dateInTimezone.getDate()).padStart(2, "0");

  return `${y}-${m}-${d}`;
}

export function buildTodoFocusPath(todo: TodoToastTarget, timeZone?: string) {
  const params = new URLSearchParams({
    [TODO_FOCUS_TASK_QUERY_PARAM]: todo.id,
    [TODO_FOCUS_DATE_QUERY_PARAM]: getTodoDayKey(todo.due, timeZone),
  });

  return `/app/todo?${params.toString()}`;
}

export function buildScheduledFocusPath(todo: TodoDateTarget, timeZone?: string) {
  const params = new URLSearchParams({
    [TODO_FOCUS_DATE_QUERY_PARAM]: getTodoDayKey(todo.due, timeZone),
    [TODO_FOCUS_MODE_QUERY_PARAM]: TODO_FOCUS_MODE_DELETED,
  });

  return `/app/scheduled?${params.toString()}`;
}

export function getTodoFocusElementId(todoId: string) {
  return `todo-focus-${encodeURIComponent(todoId)}`;
}

export function getTodoDateSectionId(dayKey: string) {
  return `todo-date-${dayKey}`;
}

export function isTodoFocusDateKey(value: string | null | undefined): value is string {
  return Boolean(value && FOCUS_DATE_KEY_PATTERN.test(value));
}

export function formatTodoFocusDateLabel(dayKey: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(`${dayKey}T12:00:00`));
}
