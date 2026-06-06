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
};

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

export function parseTodoTitle(input: ParseTodoTitleInput): ParseTodoTitleOutput {
  const rawText = typeof input.text === "string" ? input.text : "";
  const trimmedText = rawText.trim();
  if (!trimmedText) {
    return {
      cleanTitle: "",
      matchedText: null,
      matchStart: null,
      dueEpochMs: null,
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
    return {
      cleanTitle: trimmedText,
      matchedText: null,
      matchStart: null,
      dueEpochMs: null,
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
  const cleanTitle = `${before}${after}`.replace(/\s{2,}/g, " ").trim();

  return {
    cleanTitle,
    matchedText: matchedText || null,
    matchStart,
    dueEpochMs: dueDate.getTime(),
  };
}
