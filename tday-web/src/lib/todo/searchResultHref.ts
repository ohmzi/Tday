import { canonicalTodoId, todoInstanceTimestampFromId } from "@/lib/todo/todo-id";
import {
  TODO_FOCUS_DATE_QUERY_PARAM,
  TODO_FOCUS_INSTANCE_QUERY_PARAM,
  TODO_FOCUS_TASK_QUERY_PARAM,
  getTodoDayKey,
} from "@/lib/todoToastNavigation";

type ScheduledSearchTarget = {
  /** A timeline row id: the bare task id, or `${id}:${ms}` for an occurrence. */
  id: string;
  due: Date;
  completed: boolean;
  listID?: string | null;
};

type FloaterSearchTarget = {
  id: string;
  listID?: string | null;
};

type SearchResultOptions = {
  timeZone?: string;
  /** Injected so the day classification is testable; defaults to the current time. */
  now?: Date;
};

type FocusParams = {
  dayKey?: string;
  /** The occurrence ms of a recurring task, so only that occurrence is focused. */
  instance?: number | null;
};

function withFocus(path: string, taskId: string, { dayKey, instance }: FocusParams = {}) {
  const params = new URLSearchParams({ [TODO_FOCUS_TASK_QUERY_PARAM]: taskId });
  if (dayKey) params.set(TODO_FOCUS_DATE_QUERY_PARAM, dayKey);
  if (instance != null) params.set(TODO_FOCUS_INSTANCE_QUERY_PARAM, String(instance));
  return `${path}?${params.toString()}`;
}

/**
 * Where a scheduled task search result opens, so the row is highlighted where it
 * actually lives:
 * - in a custom list: that list's screen;
 * - unlisted and due today: Today;
 * - unlisted and overdue: Overdue, on its day;
 * - unlisted and upcoming: Scheduled, on its day;
 * - completed and unlisted: All, which keeps finished scheduled rows on their day.
 *   The timeline does not filter completed rows, so a finished task can match a
 *   search, and Overdue and Scheduled are the scopes that would hide it.
 *
 * The occurrence ms travels with the bare task id, so a recurring series
 * highlights the occurrence that matched rather than every occurrence.
 */
export function buildScheduledSearchResultHref(
  todo: ScheduledSearchTarget,
  { timeZone, now = new Date() }: SearchResultOptions = {},
): string {
  const taskId = canonicalTodoId(todo.id);
  const instance = todoInstanceTimestampFromId(todo.id);
  if (todo.listID) {
    return withFocus(`/app/list/${encodeURIComponent(todo.listID)}`, taskId, { instance });
  }

  const dayKey = getTodoDayKey(todo.due, timeZone);
  if (todo.completed) {
    return withFocus("/app/todo", taskId, { dayKey, instance });
  }

  const todayKey = getTodoDayKey(now, timeZone);
  if (dayKey === todayKey) return withFocus("/app/today", taskId, { instance });
  if (dayKey < todayKey) return withFocus("/app/overdue", taskId, { dayKey, instance });
  return withFocus("/app/scheduled", taskId, { dayKey, instance });
}

/** Floater search results open their Anytime list, or the Anytime feed when unlisted. */
export function buildFloaterSearchResultHref(floater: FloaterSearchTarget): string {
  const taskId = canonicalTodoId(floater.id);
  if (floater.listID) {
    return withFocus(`/app/floater-list/${encodeURIComponent(floater.listID)}`, taskId);
  }
  return withFocus("/app/floater", taskId);
}
