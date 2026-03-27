/**
 * The inverse of `masqueradeAsUTC`: reads an RRULE occurrence's UTC fields back as local
 * wall-clock time, so 09:00 in the rule renders as 09:00 on screen rather than being
 * shifted by the viewer's offset.
 */
export function rruleDateToLocal(date: Date) {
  return new Date(
    date.getUTCFullYear(),
    date.getUTCMonth(),
    date.getUTCDate(),
    date.getUTCHours(),
    date.getUTCMinutes(),
    date.getUTCSeconds(),
  );
}
