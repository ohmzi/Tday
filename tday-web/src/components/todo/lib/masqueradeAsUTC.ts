import { datetime } from "rrule";

/**
 * Restates a local wall-clock time as the same wall-clock time in UTC.
 *
 * RRULE has no timezone: "every day at 09:00" means 09:00 wherever the rule is read. So a
 * local 09:00 has to enter the rule as 09:00 UTC — shifting by the real offset would move
 * the user's morning task by hours. This is a relabelling, not a conversion.
 *
 * @param localDate a Date whose local fields hold the time the user picked
 * @returns a Date whose UTC fields hold those same numbers
 */
export function masqueradeAsUTC(localDate: Date) {
  return datetime(
    localDate.getFullYear(),
    localDate.getMonth() + 1,
    localDate.getDate(),
    localDate.getHours(),
    localDate.getMinutes(),
    localDate.getSeconds(),
  );
}
