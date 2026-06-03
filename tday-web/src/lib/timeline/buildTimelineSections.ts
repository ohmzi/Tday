import { TodoItemType } from "@/types";
import { getTodoDayKey } from "@/lib/todoToastNavigation";

/**
 * Builds the vertically ordered, date-bucketed task timeline that mirrors the
 * native iOS/Android apps. The bucket scheme (see `buildFutureTimelineSections`
 * in the iOS `TodoListScreen.swift` and `timelineRescheduleTargetDate` in
 * `DomainModels.swift`) is:
 *
 *   Earlier (collapsible) → Today → Tomorrow → next 5 days (+2..+6) →
 *   "Rest of [current month]" → future month sections (through at least the end
 *   of the current year, plus any later month that actually holds a task).
 *
 * Every bucket renders even when empty so it can act as a drop target. Each
 * section carries a `targetDayKey` (YYYY-MM-DD) compatible with
 * `moveTodoToDay`, or `null` when it is not a valid drop target (e.g. a past
 * day, or "Rest of month" once the +7 horizon has rolled into next month).
 *
 * All day math is calendar arithmetic on the user's timezone-local day keys, so
 * it is independent of the JS runtime timezone.
 */

export type TimelineSectionKind = "earlier" | "day" | "rest" | "month";

export type TimelineSection = {
  /**
   * Stable key. Day buckets use their `YYYY-MM-DD` day key (so existing
   * focus/scroll + `getTodoDateSectionId` keep working); aggregate buckets use
   * `earlier` / `rest-<monthIndex>` / `month-<monthIndex>`.
   */
  key: string;
  label: string;
  kind: TimelineSectionKind;
  /** YYYY-MM-DD drop target for `moveTodoToDay`, or null when not droppable. */
  targetDayKey: string | null;
  /** Only the (non-empty) Earlier bucket is collapsible. */
  collapsible: boolean;
  /** Offset in days from today for day buckets (0 = today); null otherwise. */
  dayDiff: number | null;
  todos: TodoItemType[];
};

const pad = (value: number) => String(value).padStart(2, "0");

const formatUtcDayKey = (date: Date) =>
  `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;

const monthIndexFromDayKey = (dayKey: string) => {
  const [year, month] = dayKey.split("-").map(Number);
  return year * 12 + month;
};

// "Fri Jun 5" — weekday/month/day with the separating commas dropped to match
// the native header style.
const dayLabel = (dayKey: string, locale: string) =>
  new Intl.DateTimeFormat(locale, {
    weekday: "short",
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  })
    .formatToParts(new Date(`${dayKey}T12:00:00Z`))
    .filter((part) => !(part.type === "literal" && /,/.test(part.value)))
    .map((part) => (part.type === "literal" ? part.value.replace(/,/g, "") : part.value))
    .join("")
    .replace(/\s+/g, " ")
    .trim();

const monthLabel = (year: number, month: number, currentYear: number, locale: string) => {
  const date = new Date(`${year}-${pad(month)}-01T12:00:00Z`);
  return new Intl.DateTimeFormat(locale, {
    month: "long",
    ...(year === currentYear ? {} : { year: "numeric" }),
    timeZone: "UTC",
  }).format(date);
};

// Within a single calendar day: manual order, then due time.
const compareWithinDay = (a: TodoItemType, b: TodoItemType) => {
  const orderDelta = a.order - b.order;
  if (orderDelta !== 0) return orderDelta;
  return a.due.getTime() - b.due.getTime();
};

const hasValidDue = (todo: TodoItemType) =>
  todo.due instanceof Date && !Number.isNaN(todo.due.getTime());

export type BuildTimelineSectionsArgs = {
  todos: TodoItemType[];
  locale: string;
  timeZone?: string;
  /** When true (Scheduled), omit the Earlier bucket and never look at the past. */
  futureOnly: boolean;
  /** Render Earlier above Today (true for All/Priority/List). */
  placesEarlierBeforeToday: boolean;
  todayLabel: string;
  tomorrowLabel: string;
};

export function buildTimelineSections({
  todos,
  locale,
  timeZone,
  futureOnly,
  placesEarlierBeforeToday,
  todayLabel,
  tomorrowLabel,
}: BuildTimelineSectionsArgs): TimelineSection[] {
  const dated = todos.filter(hasValidDue);

  const todayKey = getTodoDayKey(new Date(), timeZone);
  const [ty, tm, td] = todayKey.split("-").map(Number); // tm is 1-based

  // Calendar arithmetic via UTC so it never depends on the JS runtime timezone.
  const offsetDayKey = (offset: number) =>
    formatUtcDayKey(new Date(Date.UTC(ty, tm - 1, td + offset, 12)));

  const horizonKey = offsetDayKey(7);
  const currentMonthIndex = ty * 12 + tm;
  const horizonInCurrentMonth = monthIndexFromDayKey(horizonKey) === currentMonthIndex;

  // Bucket every dated todo by its timezone-local day key.
  const byDayKey = new Map<string, TodoItemType[]>();
  for (const todo of dated) {
    const key = getTodoDayKey(todo.due, timeZone);
    const bucket = byDayKey.get(key);
    if (bucket) bucket.push(todo);
    else byDayKey.set(key, [todo]);
  }

  const dayBucket = (key: string) => (byDayKey.get(key) ?? []).slice().sort(compareWithinDay);

  // Items across multiple days, ordered oldest → newest, then within-day order.
  const sortAcrossDays = (items: TodoItemType[]) =>
    items.slice().sort((a, b) => {
      const ka = getTodoDayKey(a.due, timeZone);
      const kb = getTodoDayKey(b.due, timeZone);
      if (ka !== kb) return ka < kb ? -1 : 1;
      return compareWithinDay(a, b);
    });

  const sections: TimelineSection[] = [];

  // 1. Earlier — only when not future-only and there are past tasks.
  const earlierTodos = futureOnly
    ? []
    : sortAcrossDays(dated.filter((t) => getTodoDayKey(t.due, timeZone) < todayKey));
  const earlierSection: TimelineSection | null =
    earlierTodos.length > 0
      ? {
          key: "earlier",
          label: "Earlier",
          kind: "earlier",
          targetDayKey: offsetDayKey(-1),
          collapsible: true,
          dayDiff: null,
          todos: earlierTodos,
        }
      : null;

  if (earlierSection && placesEarlierBeforeToday) sections.push(earlierSection);

  // 2. Today
  sections.push({
    key: todayKey,
    label: todayLabel,
    kind: "day",
    targetDayKey: todayKey,
    collapsible: false,
    dayDiff: 0,
    todos: dayBucket(todayKey),
  });

  if (earlierSection && !placesEarlierBeforeToday) sections.push(earlierSection);

  // 3. Tomorrow + 4. next 5 days (+2..+6)
  for (let offset = 1; offset <= 6; offset += 1) {
    const key = offsetDayKey(offset);
    sections.push({
      key,
      label: offset === 1 ? tomorrowLabel : dayLabel(key, locale),
      kind: "day",
      targetDayKey: key,
      collapsible: false,
      dayDiff: offset,
      todos: dayBucket(key),
    });
  }

  // 5. Rest of [current month] — days from the +7 horizon through month end.
  const restTodos = sortAcrossDays(
    dated.filter((t) => {
      const key = getTodoDayKey(t.due, timeZone);
      return key >= horizonKey && monthIndexFromDayKey(key) === currentMonthIndex;
    }),
  );
  // Render only when it is a live drop target or actually holds tasks.
  if (horizonInCurrentMonth || restTodos.length > 0) {
    sections.push({
      key: `rest-${currentMonthIndex}`,
      label: `Rest of ${monthLabel(ty, tm, ty, locale)}`,
      kind: "rest",
      targetDayKey: horizonInCurrentMonth ? horizonKey : null,
      collapsible: false,
      dayDiff: null,
      todos: restTodos,
    });
  }

  // 6. Future months — next month through at least December of the current
  //    year, plus any later month that has a task beyond the horizon.
  const futureMonthIndexes = dated
    .map((t) => getTodoDayKey(t.due, timeZone))
    .filter((key) => key >= horizonKey)
    .map(monthIndexFromDayKey);
  const minimumFinalMonthIndex = ty * 12 + 12; // December of the current year
  const finalMonthIndex = Math.max(minimumFinalMonthIndex, ...futureMonthIndexes, currentMonthIndex);

  for (let idx = currentMonthIndex + 1; idx <= finalMonthIndex; idx += 1) {
    const month = ((idx - 1) % 12) + 1;
    const year = (idx - month) / 12;
    const monthItems = sortAcrossDays(
      dated.filter((t) => {
        const key = getTodoDayKey(t.due, timeZone);
        return key >= horizonKey && monthIndexFromDayKey(key) === idx;
      }),
    );
    sections.push({
      key: `month-${idx}`,
      label: monthLabel(year, month, ty, locale),
      kind: "month",
      targetDayKey: `${year}-${pad(month)}-01`,
      collapsible: false,
      dayDiff: null,
      todos: monthItems,
    });
  }

  return sections;
}

/**
 * Maps a focus day key (e.g. from the `focusDate`/`focusTask` query params) to
 * the key of the section that actually contains that day, so we can scroll to
 * the right bucket even when the day lives inside an aggregate (Earlier / Rest /
 * month) section.
 */
export function findSectionKeyForDayKey(
  sections: TimelineSection[],
  dayKey: string,
  timeZone?: string,
): string | null {
  // Day buckets share their day key, so a direct match wins immediately.
  const direct = sections.find((section) => section.key === dayKey);
  if (direct) return direct.key;

  // Otherwise the day lives inside an aggregate bucket (Earlier / Rest / month);
  // find the one whose todos include that calendar day.
  const containing = sections.find((section) =>
    section.todos.some((todo) => getTodoDayKey(todo.due, timeZone) === dayKey),
  );
  return containing?.key ?? null;
}
