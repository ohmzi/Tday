import { RRule } from "rrule";

/**
 * Brings a stored RRULE string to the same spelling `new RRule(options).toString()` produces,
 * so a form's unchanged rule compares equal to the one the server handed back. Returns the
 * input untouched when it does not parse.
 */
export function normalizeRrule(rrule: string | null | undefined): string | null {
  if (!rrule) return null;
  try {
    return new RRule(RRule.parseString(rrule)).toString();
  } catch {
    return rrule;
  }
}
