import { useEffect, useMemo } from "react";
import { TodoItemType } from "@/types";
import { buildTimelineSections } from "@/lib/timeline/buildTimelineSections";
import type { TimelineItem, TimelineScope } from "../component/AllTasksTimelineContainer";

// Stable identity so the memo below doesn't hand back a fresh empty array on
// every render for scopes other than Today.
const NO_EARLIER_TODOS: TodoItemType[] = [];

/**
 * Today's own "Earlier" bucket (requirement 2): reuses the exact same
 * section-building code path All/Priority/Scheduled already use for their
 * own Earlier bucket (`buildTimelineSections`'s `kind: "earlier"`, built
 * from `dayKey < todayKey`) rather than a fresh definition of "overdue" —
 * which also keeps it disjoint from Today's own `dayDiff === 0` set in
 * `AllTasksTimelineContainer`, so a task due earlier today (already past its
 * time, but still *today*) is never duplicated between the two. This
 * day-boundary rule is NOT the same set the standalone Overdue screen shows:
 * that screen's `scope === "overdue"` branch filters on `isOverdueTask` (`due
 * < now`), a timestamp comparison, so a task due earlier today but still
 * pending is Overdue there while staying out of this Earlier bucket on
 * purpose. The only thing shared with every other scope (Overdue included)
 * is the raw `timelineItems` array itself, sourced from the same
 * `useTodoTimeline()` query — no separate fetch, just a different filter
 * applied downstream.
 *
 * Also owns requirement 2's deep-link behavior: a focused overdue task
 * expands Earlier immediately (no hand-off beat — there is no illustration
 * to sequence against on a direct navigation).
 */
export function useTodayEarlierBucket({
  scope,
  timelineItems,
  locale,
  timeZone,
  appDict,
  focusedTaskId,
  setEarlierExpandedImmediately,
}: {
  scope: TimelineScope;
  timelineItems: TimelineItem[];
  locale: string;
  timeZone?: string;
  appDict: (key: string) => string;
  focusedTaskId: string | null;
  setEarlierExpandedImmediately: (value: boolean) => void;
}) {
  const todayEarlierSection = useMemo(() => {
    if (scope !== "today") return null;
    const sections = buildTimelineSections({
      todos: timelineItems.map((item) => item.todo),
      locale,
      timeZone,
      futureOnly: false,
      placesEarlierBeforeToday: true,
      includeEmptyDropTargets: false,
      todayLabel: appDict("today"),
      tomorrowLabel: appDict("tomorrow"),
      earlierLabel: appDict("overdue"),
    });
    return sections.find((section) => section.kind === "earlier") ?? null;
  }, [appDict, locale, scope, timelineItems, timeZone]);

  const earlierItems = todayEarlierSection?.todos ?? NO_EARLIER_TODOS;
  // NOT folded into `scopeFilteredItems`/`hasScopedTasks` in
  // `AllTasksTimelineContainer` — see `shouldShowTodayEmptyIllustration`'s
  // own doc comment for why that separation is exactly what keeps
  // requirement 1 intact.
  const todayHasEarlierItems = scope === "today" && earlierItems.length > 0;

  // A deep-linked/focused overdue task should not sit hidden behind a
  // collapsed header. Immediate (no hand-off beat) — there is no
  // illustration to sequence against on a direct navigation.
  useEffect(() => {
    if (scope !== "today" || !focusedTaskId) return;
    if (earlierItems.some((todo) => todo.id === focusedTaskId)) {
      setEarlierExpandedImmediately(true);
    }
  }, [earlierItems, focusedTaskId, scope, setEarlierExpandedImmediately]);

  return { earlierItems, todayHasEarlierItems };
}
