import parseApiDateTime, {
  parseOptionalApiDateTime,
} from "@/lib/date/parseApiDateTime";

/**
 * Time formatting for the local workspace.
 *
 * The backend stores every timestamp as a UTC wall clock and serializes it with
 * `LocalDateTime.toString()` — no `Z`, no offset. `parseApiDateTime` reads that
 * shape back as UTC, so the local API must emit exactly the same thing or every
 * due date would shift by the browser's offset.
 */

function pad(value: number, length = 2): string {
  return String(value).padStart(length, "0");
}

/** `2026-08-04T09:30:00.000` — a UTC wall clock, matching the API wire format. */
export function toApiDateTime(date: Date): string {
  return (
    `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}` +
    `T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}` +
    `.${pad(date.getUTCMilliseconds(), 3)}`
  );
}

export function nowApiDateTime(): string {
  return toApiDateTime(new Date());
}

export const parseApiInstant = parseOptionalApiDateTime;

/**
 * Parses a due / instanceDate / overriddenDue value and floors it to the minute.
 * Deadlines are minute-precision app-wide (backend `parseDueMinute`), so every
 * write of those three fields goes through here. Returns null when unparseable.
 */
export function parseDueMinute(value: string | null | undefined): string | null {
  if (value == null || value === "") return null;
  const parsed = parseApiDateTime(value);
  if (Number.isNaN(parsed.getTime())) return null;
  parsed.setUTCSeconds(0, 0);
  return toApiDateTime(parsed);
}

export function epochMs(value: string | null | undefined): number | null {
  const parsed = parseApiInstant(value);
  return parsed ? parsed.getTime() : null;
}

/** True when two API timestamps point at the same instant (string form varies). */
export function sameInstant(
  a: string | null | undefined,
  b: string | null | undefined,
): boolean {
  const left = epochMs(a);
  const right = epochMs(b);
  if (left === null || right === null) return left === right;
  return left === right;
}

/** Whole days between two API timestamps, truncated — mirrors `Duration.toDays()`. */
export function wholeDaysBetween(from: string, to: string): number {
  const start = epochMs(from);
  const end = epochMs(to);
  if (start === null || end === null) return 0;
  return Math.trunc((end - start) / 86_400_000);
}
