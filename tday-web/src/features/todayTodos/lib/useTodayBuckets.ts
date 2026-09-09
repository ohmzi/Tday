import { useMemo } from "react";
import type { TodoItemType } from "@/types";
import { getTimeZoneDate } from "./timelineScopeHelpers";
import type { TimelineItem, TimelineScope } from "../component/AllTasksTimelineContainer";

export type TodayBucketGroup = {
  label: "Morning" | "Afternoon" | "Tonight";
  todos: TodoItemType[];
};

/**
 * Today screen: Morning (<12) / Afternoon (12–18) / Tonight (≥18), matching native.
 * All three buckets stay visible (even empty ones) as long as the day holds at
 * least one task, so they read as live drop targets alongside the others. But
 * when the whole day is empty, `scopeFilteredItems` is already the same
 * dayDiff===0 set `hasScopedTasks` checks in `useTimelineEmptyState` — so this
 * returns no buckets in lockstep with `showEmpty`, letting the empty-state
 * illustration own the screen instead of three headerless buckets sitting
 * above it.
 */
export function useTodayBuckets({
  scope,
  scopeFilteredItems,
  timeZone,
}: {
  scope: TimelineScope;
  scopeFilteredItems: TimelineItem[];
  timeZone?: string;
}): TodayBucketGroup[] {
  return useMemo(() => {
    if (scope !== "today" || scopeFilteredItems.length === 0) return [];
    const groups: Record<"Morning" | "Afternoon" | "Tonight", TodoItemType[]> = {
      Morning: [],
      Afternoon: [],
      Tonight: [],
    };
    for (const item of scopeFilteredItems) {
      const hour = getTimeZoneDate(item.todo.due, timeZone).getHours();
      const label = hour < 12 ? "Morning" : hour < 18 ? "Afternoon" : "Tonight";
      groups[label].push(item.todo);
    }
    return (["Morning", "Afternoon", "Tonight"] as const).map((label) => ({
      label,
      todos: groups[label],
    }));
  }, [scope, scopeFilteredItems, timeZone]);
}
