import { useEffect, useMemo } from "react";
import {
  buildTimelineSections,
  findSectionKeyForDayKey,
} from "@/lib/timeline/buildTimelineSections";
import { getTodoDateSectionId } from "@/lib/todoToastNavigation";
import type { TimelineItem, TimelineScope } from "../component/AllTasksTimelineContainer";

/**
 * The native date-bucketed timeline (All / Priority / Scheduled) — its
 * sections, plus the two effects that react to a deep-linked/focused target:
 * expanding a collapsed Earlier bucket the target lives in, and scrolling its
 * date into view once the section exists. Paired together the same way
 * `useTodayEarlierBucket` pairs Today's own Earlier bucket with its own
 * deep-link effect — the data and the effects that read it live in one place.
 */
export function useTimelineSections({
  timeline,
  timelineItems,
  scope,
  locale,
  timeZone,
  dragActive,
  appDict,
  focusedTaskId,
  focusedDateKey,
  setEarlierExpandedImmediately,
}: {
  timeline: boolean;
  timelineItems: TimelineItem[];
  scope: TimelineScope;
  locale: string;
  timeZone?: string;
  dragActive: boolean;
  appDict: (key: string) => string;
  focusedTaskId: string | null;
  focusedDateKey: string | null;
  setEarlierExpandedImmediately: (value: boolean) => void;
}) {
  // Every scope now shows only the dates that hold tasks; the empty buckets
  // come back for the length of a drag so there is somewhere to drop.
  const timelineSections = useMemo(() => {
    if (!timeline) return [];
    return buildTimelineSections({
      todos: timelineItems.map((item) => item.todo),
      locale,
      timeZone,
      futureOnly: scope === "scheduled",
      placesEarlierBeforeToday: scope !== "scheduled",
      includeEmptyDropTargets: dragActive,
      todayLabel: appDict("today"),
      tomorrowLabel: appDict("tomorrow"),
    });
  }, [appDict, dragActive, locale, scope, timeline, timelineItems, timeZone]);

  // Expand Earlier when the focused task lives in the past (timeline scopes).
  useEffect(() => {
    if (!timeline || !focusedTaskId) return;
    const earlier = timelineSections.find((section) => section.kind === "earlier");
    if (earlier?.todos.some((todo) => todo.id === focusedTaskId)) {
      setEarlierExpandedImmediately(true);
    }
  }, [focusedTaskId, setEarlierExpandedImmediately, timeline, timelineSections]);

  // Scroll a focused date into view within the timeline (the bucket may be an
  // aggregate Earlier / Rest / month section).
  useEffect(() => {
    // `dragActive` is in the guard, not just the deps: `timelineSections` now
    // changes identity at drag start and again at drag end, and this effect
    // would smooth-scroll a bucket to the top of the viewport with the user's
    // finger down. It is reachable — deleting a task pushes
    // /app/scheduled?focusDate=…&focusMode=deleted and never clears the param,
    // so focusDate can sit in the URL with no focusTask, which is exactly this
    // effect's condition. Worse, the day it targets is usually empty at rest
    // and only becomes findable when the drag conjures its bucket.
    if (!timeline || !focusedDateKey || focusedTaskId || dragActive) {
      return undefined;
    }
    const sectionKey = findSectionKeyForDayKey(timelineSections, focusedDateKey, timeZone);
    if (!sectionKey) return undefined;

    const frame = window.requestAnimationFrame(() => {
      document
        .getElementById(getTodoDateSectionId(sectionKey))
        ?.scrollIntoView({ behavior: "smooth", block: "start" });
    });

    return () => window.cancelAnimationFrame(frame);
  }, [dragActive, focusedDateKey, focusedTaskId, timeline, timelineSections, timeZone]);

  return timelineSections;
}
