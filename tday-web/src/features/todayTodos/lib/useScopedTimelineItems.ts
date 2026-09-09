import { useMemo } from "react";
import { TodoItemType } from "@/types";
import { flattenNotesToPlainText } from "@/lib/richNotes";
import { getTodoDayKey } from "@/lib/todoToastNavigation";
import {
  compareOverdueTimelineItems,
  compareTimelineItems,
  getDayDiff,
  getDayLabel,
  isOverdueTask,
  isPriorityTask,
} from "./timelineScopeHelpers";
import type { TimelineItem, TimelineScope } from "../component/AllTasksTimelineContainer";

/**
 * The scope-filtering pipeline every screen shares: search narrows the
 * scope's own todos (priority-filtered first, for the Priority screen) into
 * dated/labeled `TimelineItem`s, which each scope then reduces to the actual
 * set it renders — Today's own day (`dayDiff === 0`), Overdue's `due < now`
 * (re-sorted so today's own still-pending tasks lead), Scheduled's
 * future-only, or everything for All/Priority (their own date bucketing
 * happens downstream, in `useTimelineSections`).
 *
 * Search is scoped to this screen: it narrows the tasks this scope already
 * shows and reaches nothing outside them, so the priority screen searches
 * priority tasks and the overdue screen searches overdue ones.
 */
export function useScopedTimelineItems({
  scope,
  todos,
  searchQuery,
  locale,
  timeZone,
  appDict,
}: {
  scope: TimelineScope;
  todos: TodoItemType[];
  searchQuery: string;
  locale: string;
  timeZone?: string;
  appDict: (key: string) => string;
}) {
  const scopedTodos = useMemo(() => {
    const inScope =
      scope === "priority" ? todos.filter((todo) => isPriorityTask(todo.priority)) : todos;
    const query = searchQuery.trim().toLowerCase();
    if (!query) return inScope;
    return inScope.filter((todo) => {
      const title = todo.title.toLowerCase();
      const description = flattenNotesToPlainText(todo.description).toLowerCase();
      return title.includes(query) || description.includes(query);
    });
  }, [scope, searchQuery, todos]);

  const timelineItems = useMemo((): TimelineItem[] => {
    return scopedTodos
      .map((todo) => {
        const dayDiff = getDayDiff(todo.due, timeZone);
        return {
          todo,
          dayDiff,
          dayKey: getTodoDayKey(todo.due, timeZone),
          label: getDayLabel({ date: todo.due, dayDiff, locale, timeZone, appDict }),
        };
      })
      .sort(compareTimelineItems);
  }, [appDict, locale, scopedTodos, timeZone]);

  const scopeFilteredItems = useMemo((): TimelineItem[] => {
    if (scope === "today") {
      return timelineItems.filter((item) => item.dayDiff === 0);
    }
    if (scope === "overdue") {
      return timelineItems
        .filter((item) => isOverdueTask(item.todo.due))
        .sort(compareOverdueTimelineItems);
    }
    if (scope === "scheduled") {
      const now = new Date();
      return timelineItems.filter((item) => item.todo.due >= now);
    }
    return timelineItems;
  }, [timelineItems, scope]);

  return { timelineItems, scopeFilteredItems };
}
