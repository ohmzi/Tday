import { addDays, addHours, nextMonday, set, startOfDay } from "date-fns";

export type QuickDeferKey = "laterToday" | "tonight" | "tomorrow" | "nextWeek";

export type QuickDeferOption = {
  key: QuickDeferKey;
  due: Date;
};

/**
 * The Quick Defer instants, computed locally (same semantics as the Android
 * and iOS helpers): +3h, today 19:00, tomorrow 09:00, next Monday 09:00.
 * "Tonight" hides once the evening is effectively here (18:30) so it can
 * never produce an instant in the past.
 */
export function quickDeferOptions(now: Date = new Date()): QuickDeferOption[] {
  const options: QuickDeferOption[] = [
    { key: "laterToday", due: addHours(now, 3) },
  ];
  const eveningCutoff = set(startOfDay(now), { hours: 18, minutes: 30 });
  if (now < eveningCutoff) {
    options.push({ key: "tonight", due: set(startOfDay(now), { hours: 19 }) });
  }
  options.push({
    key: "tomorrow",
    due: set(startOfDay(addDays(now, 1)), { hours: 9 }),
  });
  options.push({
    key: "nextWeek",
    due: set(startOfDay(nextMonday(now)), { hours: 9 }),
  });
  return options;
}
