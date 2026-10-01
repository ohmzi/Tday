import { TodoItemType } from "@/types";
import { differenceInCalendarDays, format, startOfDay } from "date-fns";
import { toZonedTime } from "date-fns-tz";
import i18n from "@/i18n";
import { getDateFnsLocale } from "@/lib/date/dateFnsLocale";

// Translator for the user-visible summary strings. Defaults to the app's i18n
// singleton (reads the active language) so callers don't have to thread a `t`
// through; the `summary` namespace holds these keys.
function st(key: string, options?: Record<string, unknown>): string {
  return i18n.t(`summary:${key}`, options ?? {}) as string;
}

const DUE_WINDOW_DAY_RANGE = 3;

function normalizePriority(priority: string | null | undefined): string {
  return (priority ?? "Low").trim();
}

function priorityRank(priority: string | null | undefined): number {
  const normalized = normalizePriority(priority).toLowerCase();
  if (normalized === "high" || normalized === "urgent" || normalized === "important") {
    return 3;
  }
  if (normalized === "medium") {
    return 2;
  }
  return 1;
}

function summaryPriorityLabel(priority: string | null | undefined): "high" | "medium" | "low" {
  const normalized = normalizePriority(priority).toLowerCase();
  if (normalized === "high" || normalized === "urgent" || normalized === "important") {
    return "high";
  }
  if (normalized === "medium") {
    return "medium";
  }
  return "low";
}

function dueDayDelta(due: Date, now: Date, timeZone: string): number {
  const zonedNow = toZonedTime(now, timeZone);
  const zonedDue = toZonedTime(due, timeZone);
  return differenceInCalendarDays(startOfDay(zonedDue), startOfDay(zonedNow));
}

function urgencyBand(dayDelta: number): number {
  if (dayDelta < 0) return 0;
  if (dayDelta === 0) return 1;
  if (dayDelta === 1) return 2;
  if (dayDelta <= 7) return 3;
  if (dayDelta <= 30) return 4;
  return 5;
}

function compactTitle(title: string | null | undefined): string {
  const normalized = (title ?? "").replace(/\s+/g, " ").trim();
  if (!normalized) return st("untitledTask");
  if (normalized.length <= 46) return normalized;
  return `${normalized.slice(0, 43).trimEnd()}...`;
}

function joinTaskTitles(titles: string[]): string {
  if (titles.length === 0) return "";
  if (titles.length === 1) return titles[0];
  const and = st("and");
  if (titles.length === 2) return `${titles[0]} ${and} ${titles[1]}`;
  return `${titles.slice(0, -1).join(", ")}, ${and} ${titles[titles.length - 1]}`;
}

function taskPhrase(task: Pick<SummaryTaskCandidate, "title" | "dueLabel" | "dueDayDelta" | "isOverdue">): string {
  // `dueLabel` already carries a localized "due …" phrase; we interpolate the
  // whole thing rather than string-stripping an English "due " prefix.
  const isPast = task.isOverdue || (task.dueDayDelta ?? 0) < 0;
  return st(isPast ? "taskPhrasePast" : "taskPhrasePresent", {
    title: task.title,
    due: task.dueLabel,
  });
}

function buildGroupedThenPhrase(thenTasks: SummaryTaskCandidate[]): string {
  if (thenTasks.length === 0) return "";
  if (thenTasks.length === 1) return taskPhrase(thenTasks[0]);

  const dayGroups = thenTasks.reduce<Array<{ dayKey: string; tasks: SummaryTaskCandidate[] }>>((acc, task) => {
    const last = acc[acc.length - 1];
    const dayKey = task.dueDayKey ?? task.dueLabel;
    if (last && last.dayKey === dayKey) {
      last.tasks.push(task);
      return acc;
    }
    acc.push({ dayKey, tasks: [task] });
    return acc;
  }, []);

  const groupPhrases = dayGroups.map((group) => buildDayGroupedPhrase(group.tasks));

  if (groupPhrases.length === 1) {
    return groupPhrases[0];
  }

  const thenSep = st("thenSeparator");
  return `${groupPhrases.slice(0, -1).join(thenSep)}${thenSep}${groupPhrases[groupPhrases.length - 1]}`;
}

function dueWindow(hour: number): "morning" | "afternoon" | "night" {
  if (hour < 12) return "morning";
  if (hour < 18) return "afternoon";
  return "night";
}

function dueWindowPhrase(window: "morning" | "afternoon" | "night"): string {
  switch (window) {
    case "morning":
      return st("windowMorning");
    case "afternoon":
      return st("windowAfternoon");
    case "night":
      return st("windowNight");
  }
}

function buildDueDescriptor(due: Date, now: Date, timeZone: string): {
  dueLabel: string;
  dueDayKey: string;
  dueDayTarget: string;
  dueWindowPhrase: string;
} {
  const zonedNow = toZonedTime(now, timeZone);
  const zonedDue = toZonedTime(due, timeZone);
  const dayDelta = differenceInCalendarDays(startOfDay(zonedDue), startOfDay(zonedNow));
  const includeWindowPhrase = Math.abs(dayDelta) <= DUE_WINDOW_DAY_RANGE;
  const window = dueWindow(zonedDue.getHours());
  const neutralWindowPhrase = includeWindowPhrase ? dueWindowPhrase(window) : "";
  const dueDayKey = format(zonedDue, "yyyy-MM-dd");

  const windowWord = window === "night" ? st("night") : st(`window_${window}`);

  if (dayDelta === 0) {
    return {
      dueLabel:
        window === "night"
          ? st("dueTonight")
          : st("dueTodayWindow", { window: neutralWindowPhrase }).trim(),
      dueDayKey,
      dueDayTarget: st("targetToday"),
      dueWindowPhrase: neutralWindowPhrase,
    };
  }
  if (dayDelta === 1) {
    return {
      dueLabel: st("dueTomorrowWindow", { window: windowWord }).trim(),
      dueDayKey,
      dueDayTarget: st("targetTomorrow"),
      dueWindowPhrase: neutralWindowPhrase,
    };
  }
  if (dayDelta === -1) {
    return {
      dueLabel: st("dueYesterdayWindow", { window: windowWord }).trim(),
      dueDayKey,
      dueDayTarget: st("targetYesterday"),
      dueWindowPhrase: neutralWindowPhrase,
    };
  }

  const sameYear = zonedDue.getFullYear() === zonedNow.getFullYear();
  const dayLabel = format(zonedDue, sameYear ? "do MMM" : "do MMM yyyy", {
    locale: getDateFnsLocale(i18n.language),
  });
  const dueDayTarget = st("targetOnDate", { date: dayLabel });
  return {
    dueLabel: neutralWindowPhrase
      ? st("dueOnDateWindow", { target: dueDayTarget, window: neutralWindowPhrase }).trim()
      : st("dueOnDate", { target: dueDayTarget }),
    dueDayKey,
    dueDayTarget,
    dueWindowPhrase: neutralWindowPhrase,
  };
}

function buildDayGroupedPhrase(tasks: SummaryTaskCandidate[]): string {
  if (tasks.length === 0) return "";
  if (tasks.length === 1) return taskPhrase(tasks[0]);

  const windowGroups = tasks.reduce<Array<{ windowPhrase: string; titles: string[] }>>((acc, task) => {
    const last = acc[acc.length - 1];
    const phrase = task.dueWindowPhrase ?? "";
    if (last && last.windowPhrase === phrase) {
      last.titles.push(task.title);
      return acc;
    }
    acc.push({ windowPhrase: phrase, titles: [task.title] });
    return acc;
  }, []);

  const windowPhrases = windowGroups.map((group) => {
    const titles = joinTaskTitles(group.titles);
    return group.windowPhrase ? `${titles} ${group.windowPhrase}` : titles;
  });
  const joinedTaskPhrases = joinTaskTitles(windowPhrases);
  const dueTarget = tasks[0].dueDayTarget ?? tasks[0].dueLabel;
  const qualifier = tasks.length === 2 ? st("qualifierBoth") : st("qualifierAll");
  const isPast = tasks[0].isOverdue || (tasks[0].dueDayDelta ?? 0) < 0;
  return st(isPast ? "dayGroupedPast" : "dayGroupedPresent", {
    tasks: joinedTaskPhrases,
    qualifier,
    target: dueTarget,
  });
}

export type SummaryTaskCandidate = {
  id: string;
  title: string;
  priorityLabel: "high" | "medium" | "low";
  dueLabel: string;
  dueEpochMs: number;
  dueDayDelta?: number;
  dueDayKey?: string;
  dueDayTarget?: string;
  dueWindowPhrase?: string;
  isOverdue?: boolean;
};

export function buildReadableTaskSummary({
  startTask,
  thenTasks,
  overdueCount = 0,
}: {
  startTask: SummaryTaskCandidate;
  thenTasks: SummaryTaskCandidate[];
  overdueCount?: number;
}): string {
  const sentences: string[] = [];
  sentences.push(st("startWith", { task: taskPhrase(startTask) }));
  if (thenTasks.length >= 1) {
    sentences.push(st("nextUp", { tasks: buildGroupedThenPhrase(thenTasks) }));
  }
  if (overdueCount > 0) {
    // Single (non-plural-suffixed) key keeps locale key-parity intact; the
    // translation reads naturally with the interpolated count.
    sentences.push(st("overdueCatchUp", { count: overdueCount }));
  }
  return sentences.join(" ");
}

export function buildSummaryTaskCandidates(
  todos: TodoItemType[],
  {
    now = new Date(),
    timeZone = "UTC",
  }: {
    now?: Date;
    timeZone?: string;
  } = {},
): SummaryTaskCandidate[] {
  if (todos.length === 0) return [];
  const ranked = todos
    .map((todo) => {
      const dayDelta = dueDayDelta(todo.due, now, timeZone);
      return {
        todo,
        dueEpochMs: todo.due.getTime(),
        dayDelta,
        band: urgencyBand(dayDelta),
        priority: priorityRank(todo.priority),
      };
    })
    .sort((a, b) => {
      if (a.band !== b.band) return a.band - b.band;
      if (a.dayDelta !== b.dayDelta) return a.dayDelta - b.dayDelta;

      const priorityDelta = b.priority - a.priority;
      if (priorityDelta !== 0) return priorityDelta;

      if (a.dueEpochMs !== b.dueEpochMs) return a.dueEpochMs - b.dueEpochMs;
      return 0;
    });

  const nowMs = now.getTime();
  return ranked.map(({ todo, dueEpochMs, dayDelta }, index) => ({
    ...buildDueDescriptor(todo.due, now, timeZone),
    id: `T${index + 1}`,
    title: compactTitle(todo.title),
    priorityLabel: summaryPriorityLabel(todo.priority),
    dueEpochMs,
    dueDayDelta: dayDelta,
    isOverdue: dueEpochMs < nowMs,
  }));
}
