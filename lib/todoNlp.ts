import * as chrono from "chrono-node";
import { addMinutes } from "date-fns";

const DEFAULT_DURATION_MINUTES = 180;
const MIN_DURATION_MINUTES = 1;
const MAX_DURATION_MINUTES = 24 * 60;

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
  startEpochMs: number | null;
  dueEpochMs: number | null;
};

type ChronoParser = {
  parse: (
    text: string,
    ref?: chrono.ParsingReference | Date,
    option?: chrono.ParsingOption,
  ) => chrono.ParsedResult[];
};

function resolveChronoParser(rawLocale?: string | null): ChronoParser {
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

function sanitizeDurationMinutes(rawDuration?: number | null): number {
  if (typeof rawDuration !== "number" || !Number.isFinite(rawDuration)) {
    return DEFAULT_DURATION_MINUTES;
  }
  return Math.min(
    Math.max(Math.floor(rawDuration), MIN_DURATION_MINUTES),
    MAX_DURATION_MINUTES,
  );
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

export function parseTodoTitle(input: ParseTodoTitleInput): ParseTodoTitleOutput {
  const rawText = typeof input.text === "string" ? input.text : "";
  const trimmedText = rawText.trim();
  if (!trimmedText) {
    return {
      cleanTitle: "",
      matchedText: null,
      matchStart: null,
      startEpochMs: null,
      dueEpochMs: null,
    };
  }

  const parser = resolveChronoParser(input.locale);
  const referenceInstant =
    typeof input.referenceEpochMs === "number" && Number.isFinite(input.referenceEpochMs)
      ? new Date(input.referenceEpochMs)
      : new Date();
  const timezoneOffset = sanitizeTimezoneOffset(input.timezoneOffsetMinutes);
  const reference: chrono.ParsingReference = {
    instant: referenceInstant,
    timezone: timezoneOffset,
  };

  const parsedResults = parser.parse(rawText, reference);
  if (!parsedResults.length) {
    return {
      cleanTitle: trimmedText,
      matchedText: null,
      matchStart: null,
      startEpochMs: null,
      dueEpochMs: null,
    };
  }

  const parsed = parsedResults[0];
  const matchStart = Math.max(0, parsed.index ?? rawText.indexOf(parsed.text));
  const matchedText = parsed.text ?? "";
  const startDate = parsed.start.date();
  const durationMinutes = sanitizeDurationMinutes(input.defaultDurationMinutes);
  const dueDate = parsed.end?.date() ?? addMinutes(startDate, durationMinutes);

  const before = rawText.slice(0, matchStart);
  const after = rawText.slice(matchStart + matchedText.length);
  const cleanTitle = `${before}${after}`.replace(/\s{2,}/g, " ").trim();

  return {
    cleanTitle,
    matchedText: matchedText || null,
    matchStart,
    startEpochMs: startDate.getTime(),
    dueEpochMs: dueDate.getTime(),
  };
}
