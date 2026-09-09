import { useEffect, useMemo, useRef, useState } from "react";
import type { TimelineItem, TimelineSection } from "../component/AllTasksTimelineContainer";

const PAGE_SIZE = 10;

const toSections = (items: TimelineItem[]): TimelineSection[] => {
  const sections: TimelineSection[] = [];

  for (const item of items) {
    const currentSection = sections[sections.length - 1];
    if (!currentSection || currentSection.key !== item.dayKey) {
      sections.push({
        key: item.dayKey,
        label: item.label,
        dayDiff: item.dayDiff,
        todos: [item.todo],
      });
      continue;
    }

    currentSection.todos.push(item.todo);
  }

  return sections;
};

/**
 * Today/Overdue's own paging (unchanged by this PR): `PAGE_SIZE` rows
 * revealed at a time behind an IntersectionObserver sentinel, plus the
 * day-grouping the standalone Overdue screen renders from
 * (`earlierSections`/`regularSections` — Today ignores these in favor of its
 * own time-of-day buckets). Resets to the first page whenever the underlying
 * scoped set changes size, and jumps the page ahead immediately when a
 * deep-linked/focused item or date lands past what's currently revealed, so
 * it's on screen without an extra scroll-to-load.
 */
export function useTimelinePaging({
  scopeFilteredItems,
  timeline,
  focusedDateKey,
  focusedTaskId,
}: {
  scopeFilteredItems: TimelineItem[];
  timeline: boolean;
  focusedDateKey: string | null;
  focusedTaskId: string | null;
}) {
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);
  const sentinelRef = useRef<HTMLDivElement | null>(null);

  const focusedDateIndex = useMemo(
    () =>
      focusedDateKey
        ? scopeFilteredItems.findIndex((item) => item.dayKey === focusedDateKey)
        : -1,
    [focusedDateKey, scopeFilteredItems],
  );
  const focusedTaskIndex = useMemo(
    () =>
      focusedTaskId
        ? scopeFilteredItems.findIndex((item) => item.todo.id === focusedTaskId)
        : -1,
    [focusedTaskId, scopeFilteredItems],
  );

  const visibleTimelineItems = useMemo(
    () => scopeFilteredItems.slice(0, visibleCount),
    [scopeFilteredItems, visibleCount],
  );
  const sections = useMemo(() => toSections(visibleTimelineItems), [visibleTimelineItems]);
  const earlierSections = useMemo(() => sections.filter((s) => s.dayDiff < 0), [sections]);
  const regularSections = useMemo(() => sections.filter((s) => s.dayDiff >= 0), [sections]);

  const hasMore = !timeline && visibleCount < scopeFilteredItems.length;

  useEffect(() => {
    setVisibleCount(PAGE_SIZE);
  }, [scopeFilteredItems.length]);

  useEffect(() => {
    if (timeline) return;
    const targetIndex = focusedTaskIndex >= 0 ? focusedTaskIndex : focusedDateIndex;
    if (targetIndex < 0 || targetIndex < visibleCount) {
      return;
    }

    setVisibleCount((prev) => {
      const nextCount = Math.ceil((targetIndex + 1) / PAGE_SIZE) * PAGE_SIZE;
      return Math.min(Math.max(prev, nextCount), scopeFilteredItems.length);
    });
  }, [focusedDateIndex, focusedTaskIndex, scopeFilteredItems.length, timeline, visibleCount]);

  useEffect(() => {
    if (!hasMore || !sentinelRef.current) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        const [entry] = entries;
        if (!entry?.isIntersecting) {
          return;
        }
        setVisibleCount((prev) => Math.min(prev + PAGE_SIZE, scopeFilteredItems.length));
      },
      {
        root: null,
        rootMargin: "200px 0px",
      },
    );

    observer.observe(sentinelRef.current);
    return () => {
      observer.disconnect();
    };
  }, [scopeFilteredItems.length, hasMore]);

  return { visibleTimelineItems, earlierSections, regularSections, hasMore, sentinelRef };
}
