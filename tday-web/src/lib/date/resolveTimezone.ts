/**
 * Whether the runtime recognises `tz` as an IANA zone. `Intl.DateTimeFormat` is the check:
 * it throws a RangeError on anything it cannot resolve, and it is the same resolver every
 * downstream date format goes through.
 */
function isSupportedTimeZone(tz: string): boolean {
  try {
    Intl.DateTimeFormat(undefined, { timeZone: tz });
    return true;
  } catch {
    return false;
  }
}

/**
 * The timezone to render a user's dates in, falling back to the device's own zone.
 *
 * A stored preference can outlive the tz database entry it named (zones get renamed and
 * retired), so an unrecognised value is treated the same as an absent one instead of being
 * passed through to throw at the first format call.
 */
export default function resolveTimezone(timezone?: string): string {
  if (timezone && isSupportedTimeZone(timezone)) return timezone;
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}
