import { canonicalTodoId } from "@/lib/todo/todo-id";
import {
  TODO_FOCUS_DATE_QUERY_PARAM,
  TODO_FOCUS_TASK_QUERY_PARAM,
  getTodoDayKey,
} from "@/lib/todoToastNavigation";

type ScheduledSearchTarget = {
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

function withFocus(path: string, taskId: string, dayKey?: string) {
  const params = new URLSearchParams({ [TODO_FOCUS_TASK_QUERY_PARAM]: taskId });
  if (dayKey) params.set(TODO_FOCUS_DATE_QUERY_PARAM, dayKey);
  return `${path}?${params.toString()}`;
}

/**
 * Where a scheduled task search result opens, so the row is highlighted where it
 * actually lives:
 * - in a custom list: that list's screen;
 * - unlisted and due today: Today;
 * - unlisted and overdue: Overdue, on its day;
 * - unlisted and upcoming: Scheduled, on its day;
 * - completed and unlisted: All, the screen that keeps finished scheduled rows
 *   on their day.
 */
export function buildScheduledSearchResultHref(
  todo: ScheduledSearchTarget,
  { timeZone, now = new Date() }: SearchResultOptions = {},
): string {
  const taskId = canonicalTodoId(todo.id);
  if (todo.listID) {
    return withFocus(`/app/list/${encodeURIComponent(todo.listID)}`, taskId);
  }

  const dayKey = getTodoDayKey(todo.due, timeZone);
  if (todo.completed) {
    return withFocus("/app/todo", taskId, dayKey);
  }

  const todayKey = getTodoDayKey(now, timeZone);
  if (dayKey === todayKey) return withFocus("/app/today", taskId);
  if (dayKey < todayKey) return withFocus("/app/overdue", taskId, dayKey);
  return withFocus("/app/scheduled", taskId, dayKey);
}

/** Floater search results open their Anytime list, or the Anytime feed when unlisted. */
export function buildFloaterSearchResultHref(floater: FloaterSearchTarget): string {
  const taskId = canonicalTodoId(floater.id);
  if (floater.listID) {
    return withFocus(`/app/floater-list/${encodeURIComponent(floater.listID)}`, taskId);
  }
  return withFocus("/app/floater", taskId);
}
