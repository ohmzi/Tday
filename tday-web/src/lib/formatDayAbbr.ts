/**
 * Short weekday name for a date — "Mon", "lun.", "月" — in the caller's locale.
 *
 * The locale is a required argument rather than an optional one. These abbreviations render
 * inside quick-pick rows sitting directly beside fully translated labels, so defaulting to
 * the runtime locale is exactly the bug worth preventing: a German UI printing "Mon".
 */
export function formatDayAbbr(date: Date, locale: string): string {
  return new Intl.DateTimeFormat(locale, { weekday: "short" }).format(date);
}
