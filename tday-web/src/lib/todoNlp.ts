import * as chrono from "chrono-node";

export type ParseTodoTitleInput = {
  text: string;
  locale?: string | null;
  referenceEpochMs?: number | null;
  timezoneOffsetMinutes?: number | null;
  defaultDurationMinutes?: number | null;
};

export type ParseTodoTitleOutput = {
  cleanTitle: string;
  matchedText: string | null;
  matchStart: number | null;
  dueEpochMs: number | null;
  rrule: string | null;
  priority: TodoPriority | null;
};

export type TodoPriority = "Low" | "Medium" | "High";

export type RecurrencePriorityResult = {
  cleanTitle: string;
  rrule: string | null;
  priority: TodoPriority | null;
};

// Canonical RRULE strings — byte-identical to the 5 recurrence presets the create
// sheets offer (see TodoFormSelectors / CreateTaskBottomSheet / CreateTaskSheet).
const RECURRENCE_RULES: ReadonlyArray<{ re: RegExp; rrule: string }> = [
  // "weekday(s)" must be tried before "week", which it contains.
  { re: /\b(?:every\s+weekday|weekdays?)\b/i, rrule: "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR" },
  { re: /\b(?:every\s*day|everyday|daily)\b/i, rrule: "RRULE:FREQ=DAILY;INTERVAL=1" },
  { re: /\b(?:every\s+week|weekly)\b/i, rrule: "RRULE:FREQ=WEEKLY;INTERVAL=1" },
  { re: /\b(?:every\s+month|monthly)\b/i, rrule: "RRULE:FREQ=MONTHLY;INTERVAL=1" },
  { re: /\b(?:every\s+year|yearly|annually)\b/i, rrule: "RRULE:FREQ=YEARLY;INTERVAL=1" },
];

function capitalizePriority(word: string): TodoPriority {
  const lower = word.toLowerCase();
  if (lower === "high") return "High";
  if (lower === "low") return "Low";
  return "Medium";
}

/**
 * Deterministic, on-device recurrence + priority capture. Detects an English
 * recurrence phrase ("every day", "weekly", …) → one of the 5 preset RRULEs, and a
 * priority marker ("!" / "!!" / "high|low|medium priority" / a trailing high|low|
 * medium) → the priority field. Matched phrases are stripped from the title. Bare
 * priority words are only honoured when trailing or paired with "priority", to avoid
 * false positives like "buy low-fat milk".
 */
export function parseRecurrencePriority(text: string): RecurrencePriorityResult {
  let working = typeof text === "string" ? text : "";
  let rrule: string | null = null;

  for (const rule of RECURRENCE_RULES) {
    const match = rule.re.exec(working);
    if (match) {
      rrule = rule.rrule;
      working = working.slice(0, match.index) + working.slice(match.index + match[0].length);
      break;
    }
  }

  let priority: TodoPriority | null = null;
  if (working.includes("!!")) {
    priority = "High";
    working = working.replace("!!", "");
  } else if (working.includes("!")) {
    priority = "Medium";
    working = working.replace("!", "");
  } else {
    const phrase = /\b(high|medium|low)\s+priority\b/i.exec(working);
    if (phrase) {
      priority = capitalizePriority(phrase[1]);
      working = working.slice(0, phrase.index) + working.slice(phrase.index + phrase[0].length);
    } else {
      const trailing = /\s+(high|medium|low)\s*$/i.exec(working);
      if (trailing) {
        priority = capitalizePriority(trailing[1]);
        working = working.slice(0, trailing.index);
      }
    }
  }

  const cleanTitle = working.replace(/\s{2,}/g, " ").trim();
  return { cleanTitle, rrule, priority };
}

type ChronoParser = {
  parse: (
    text: string,
    ref?: chrono.ParsingReference | Date,
    option?: chrono.ParsingOption,
  ) => chrono.ParsedResult[];
};

function resolveChronoParser(rawLocale?: string | null): unknown {
  const normalized = normalizeLocale(rawLocale);
  switch (normalized) {
    case "ja":
      return chrono.ja;
    case "fr":
      return chrono.fr;
    case "ru":
      return chrono.ru;
    case "es":
      return chrono.es;
    case "it":
      return chrono.it;
    case "de":
      return chrono.de;
    case "pt":
      return chrono.pt;
    case "zh":
      return chrono.zh;
    default:
      return chrono.en;
  }
}

function normalizeLocale(rawLocale?: string | null): string {
  if (!rawLocale) return "en";
  const firstToken = rawLocale
    .split(",")[0]
    ?.trim()
    .toLowerCase();
  if (!firstToken) return "en";
  return firstToken.split("-")[0] ?? "en";
}

function sanitizeTimezoneOffset(rawOffset?: number | null): number | undefined {
  if (typeof rawOffset !== "number" || !Number.isFinite(rawOffset)) {
    return undefined;
  }
  const rounded = Math.floor(rawOffset);
  if (rounded < -14 * 60 || rounded > 14 * 60) {
    return undefined;
  }
  return rounded;
}

export type RepeatCompletion = { title: string; completedAtEpochMs: number };

const SUGGEST_TARGETS: ReadonlyArray<{ days: number; rrule: string; tol: number }> = [
  { days: 1, rrule: "RRULE:FREQ=DAILY;INTERVAL=1", tol: 0.5 },
  { days: 7, rrule: "RRULE:FREQ=WEEKLY;INTERVAL=1", tol: 2 },
  { days: 30, rrule: "RRULE:FREQ=MONTHLY;INTERVAL=1", tol: 7 },
  { days: 365, rrule: "RRULE:FREQ=YEARLY;INTERVAL=1", tol: 45 },
];
const SUGGEST_MIN_COMPLETIONS = 3;
const MS_PER_DAY = 86_400_000;

/** Lowercased, whitespace-collapsed, recurrence/priority-stripped title — matches the
 * shared Kotlin RepeatSuggestionEngine.normalize so cross-platform behaviour agrees. */
export function normalizeForSuggestion(title: string): string {
  return parseRecurrencePriority(title)
    .cleanTitle.toLowerCase()
    .replace(/\s{2,}/g, " ")
    .trim();
}

function medianOf(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

/**
 * "Make this repeat?" — mirrors the shared Kotlin RepeatSuggestionEngine. Returns a
 * preset RRULE to suggest for `currentTitle` when past completions of the same title
 * show a steady cadence, else null.
 */
export function suggestRepeat(
  currentTitle: string,
  completions: RepeatCompletion[],
): string | null {
  const norm = normalizeForSuggestion(currentTitle);
  if (!norm) return null;

  const times = completions
    .filter((c) => normalizeForSuggestion(c.title) === norm)
    .map((c) => c.completedAtEpochMs)
    .sort((a, b) => a - b);
  if (times.length < SUGGEST_MIN_COMPLETIONS) return null;

  const intervals: number[] = [];
  for (let i = 1; i < times.length; i++) {
    const days = (times[i] - times[i - 1]) / MS_PER_DAY;
    if (days > 0.25) intervals.push(days);
  }
  if (intervals.length < SUGGEST_MIN_COMPLETIONS - 1) return null;

  const median = medianOf(intervals);
  const target = SUGGEST_TARGETS.find((t) => Math.abs(median - t.days) <= t.tol);
  if (!target) return null;

  const consistent = intervals.filter((d) => Math.abs(d - median) <= target.tol).length;
  if (consistent < intervals.length - Math.floor(intervals.length / 3)) return null;

  return target.rrule;
}

export function parseTodoTitle(input: ParseTodoTitleInput): ParseTodoTitleOutput {
  const rawText = typeof input.text === "string" ? input.text : "";
  const trimmedText = rawText.trim();
  if (!trimmedText) {
    return {
      cleanTitle: "",
      matchedText: null,
      matchStart: null,
      dueEpochMs: null,
      rrule: null,
      priority: null,
    };
  }

  const parser = resolveChronoParser(input.locale);
  const parse = (parser as ChronoParser).parse.bind(parser);
  const referenceInstant =
    typeof input.referenceEpochMs === "number" && Number.isFinite(input.referenceEpochMs)
      ? new Date(input.referenceEpochMs)
      : new Date();
  const timezoneOffset = sanitizeTimezoneOffset(input.timezoneOffsetMinutes);
  const reference: chrono.ParsingReference = {
    instant: referenceInstant,
    timezone: timezoneOffset,
  };

  const parsedResults = parse(rawText, reference);
  if (!parsedResults.length) {
    const rp = parseRecurrencePriority(trimmedText);
    return {
      cleanTitle: rp.cleanTitle,
      matchedText: null,
      matchStart: null,
      dueEpochMs: null,
      rrule: rp.rrule,
      priority: rp.priority,
    };
  }

  const parsed = parsedResults[0];
  const matchStart = Math.max(0, parsed.index ?? rawText.indexOf(parsed.text));
  const matchedText = parsed.text ?? "";
  // Due is the time the user named (parsed.start); only use an explicit end when
  // the text gave a range. Matches the backend NLP (Natty), which ignores any
  // default duration: dueDate = dates.size > 1 ? dates[1] : start.
  const startDate = parsed.start.date();
  const dueDate = parsed.end?.date() ?? startDate;

  const before = rawText.slice(0, matchStart);
  const after = rawText.slice(matchStart + matchedText.length);
  const dateCleaned = `${before}${after}`;
  const rp = parseRecurrencePriority(dateCleaned);

  dueDate.setSeconds(0, 0);

  return {
    cleanTitle: rp.cleanTitle,
    matchedText: matchedText || null,
    matchStart,
    dueEpochMs: dueDate.getTime(),
    rrule: rp.rrule,
    priority: rp.priority,
  };
}
