import { Options, RRule } from "rrule";

export type RepeatType =
  | "Daily"
  | "Weekly"
  | "Weekday"
  | "Monthly"
  | "Yearly"
  | "Custom"
  | null;

/** The `by*` fields that make a rule more specific than a plain "every N frequency". */
function hasByRule(o: Partial<Options>): boolean {
  return Boolean(
    o.bymonth || o.bymonthday || o.bysetpos || o.byweekday || o.byweekno || o.byyearday,
  );
}

/**
 * Names the repeat preset an RRULE corresponds to, so the form can show "Weekly" instead of
 * the raw rule — or fall back to "Custom" when no preset describes it.
 *
 * Order matters here. "Weekday" is checked first because it is the one preset that carries a
 * `byweekday` list, and the generic `by*` test right after it would otherwise claim it.
 */
export default function deriveRepeatType({
  rruleOptions,
}: {
  rruleOptions: Partial<Options> | null;
}): RepeatType {
  if (!rruleOptions) return null;

  const isWeekdayPreset =
    rruleOptions.freq === RRule.WEEKLY &&
    Array.isArray(rruleOptions.byweekday) &&
    rruleOptions.byweekday.length === 5 &&
    !rruleOptions.bymonth &&
    !rruleOptions.bymonthday &&
    !rruleOptions.bysetpos &&
    !rruleOptions.byweekno &&
    !rruleOptions.byyearday &&
    !rruleOptions.interval;
  if (isWeekdayPreset) return "Weekday";

  // Anything narrowed by a by-rule, or repeating every 2nd/3rd/... period, has no preset.
  if (hasByRule(rruleOptions)) return "Custom";
  if (rruleOptions.interval && rruleOptions.interval > 1) return "Custom";

  switch (rruleOptions.freq) {
    case RRule.DAILY:
      return "Daily";
    case RRule.WEEKLY:
      return "Weekly";
    case RRule.MONTHLY:
      return "Monthly";
    case RRule.YEARLY:
      return "Yearly";
    default:
      return null;
  }
}
