import { compareTodosWithinDay } from "@/lib/timeline/buildTimelineSections";
import type { TimelineItem, TimelineScope } from "../component/AllTasksTimelineContainer";

const MS_IN_DAY = 1000 * 60 * 60 * 24;

// Scopes that render the native date-bucketed timeline with drag-and-drop.
export const isTimelineScope = (scope: TimelineScope) =>
  scope === "all" || scope === "priority" || scope === "scheduled";

// `date` re-expressed as a wall-clock Date in `timeZone` (UTC when omitted),
// so calendar-field reads (`getHours`, `getFullYear`, …) reflect the user's
// local day rather than the JS runtime's own timezone.
export const getTimeZoneDate = (date: Date, timeZone?: string) =>
  new Date(date.toLocaleString("en-US", { timeZone: timeZone || "UTC" }));

// Whole-day offset from today (0 = today, 1 = tomorrow, -1 = yesterday),
// computed on timezone-local midnights so it is independent of time-of-day.
export const getDayDiff = (date: Date, timeZone?: string) => {
  const nowInTimezone = getTimeZoneDate(new Date(), timeZone);
  const dateInTimezone = getTimeZoneDate(date, timeZone);

  const todayMidnight = new Date(
    nowInTimezone.getFullYear(),
    nowInTimezone.getMonth(),
    nowInTimezone.getDate(),
  );
  const dateMidnight = new Date(
    dateInTimezone.getFullYear(),
    dateInTimezone.getMonth(),
    dateInTimezone.getDate(),
  );

  return Math.round((dateMidnight.getTime() - todayMidnight.getTime()) / MS_IN_DAY);
};

// Display label for a timeline item's day: "Today"/"Tomorrow" for the two
// near days, otherwise a short weekday/month/day string in `locale`.
export const getDayLabel = ({
  date,
  dayDiff,
  locale,
  timeZone,
  appDict,
}: {
  date: Date;
  dayDiff: number;
  locale: string;
  timeZone?: string;
  appDict: (key: string) => string;
}) => {
  if (dayDiff === 0) return appDict("today");
  if (dayDiff === 1) return appDict("tomorrow");
  // Weekday + month + day, no year — matches the timeline section headers (and
  // Android/iOS) so Overdue dates read the same everywhere. Commas dropped to
  // mirror the native header style ("Mon Jun 8").
  return new Intl.DateTimeFormat(locale, {
    weekday: "short",
    month: "short",
    day: "numeric",
    timeZone: timeZone || "UTC",
  })
    .formatToParts(date)
    .filter((part) => !(part.type === "literal" && /,/.test(part.value)))
    .map((part) => (part.type === "literal" ? part.value.replace(/,/g, "") : part.value))
    .join(" ")
    .replace(/\s+/g, " ")
    .trim();
};

// Sort rank for a day-diff group: Earlier first, then Today, Tomorrow, then
// every later date — the same ordering `compareTimelineItems` sorts within.
const getTimelinePriority = (dayDiff: number) => {
  if (dayDiff < 0) return -1; // Earlier – above everything
  if (dayDiff === 0) return 0; // Today
  if (dayDiff === 1) return 1; // Tomorrow
  return 2; // Future dates
};

// The default timeline ordering: Earlier, then Today, Tomorrow, then future
// dates ascending (and Earlier dates descending, i.e. most recent first),
// falling back to the fixed within-day todo ordering for same-day items.
export const compareTimelineItems = (a: TimelineItem, b: TimelineItem) => {
  const priorityDelta = getTimelinePriority(a.dayDiff) - getTimelinePriority(b.dayDiff);
  if (priorityDelta !== 0) {
    return priorityDelta;
  }

  if (a.dayDiff > 1 || b.dayDiff > 1) {
    const futureDelta = a.dayDiff - b.dayDiff;
    if (futureDelta !== 0) {
      return futureDelta;
    }
  } else if (a.dayDiff < 0 || b.dayDiff < 0) {
    const pastDelta = b.dayDiff - a.dayDiff;
    if (pastDelta !== 0) {
      return pastDelta;
    }
  }

  // Same day group: fall back to the FIXED within-day todo ordering (pinned,
  // due asc, priority, modified desc, id) — see src/lib/taskSort.ts.
  return compareTodosWithinDay(a.todo, b.todo);
};

// The standalone Overdue screen's ordering: today's own still-pending tasks
// lead, then the genuinely overdue days, each group internally ordered by
// `compareTimelineItems`.
export const compareOverdueTimelineItems = (a: TimelineItem, b: TimelineItem) => {
  const aIsToday = a.dayDiff === 0;
  const bIsToday = b.dayDiff === 0;

  if (aIsToday !== bIsToday) {
    return aIsToday ? -1 : 1;
  }

  return compareTimelineItems(a, b);
};

// Whether a todo's priority counts toward the Priority screen/scope.
export const isPriorityTask = (priority: string | null | undefined) => {
  const normalized = (priority || "").trim().toLowerCase();
  return normalized === "medium" ||
    normalized === "high" ||
    normalized === "important" ||
    normalized === "urgent";
};

// The standalone Overdue screen's own definition: due strictly before now
// (a timestamp comparison) — see `useTodayEarlierBucket`'s doc comment for
// how this differs from Today's own day-boundary "Earlier" bucket.
export const isOverdueTask = (due: Date) => due < new Date();
