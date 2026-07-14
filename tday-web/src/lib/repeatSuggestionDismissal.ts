// Per-title dismissal for the "Make this repeat?" suggestion chip, so a title the
// user declined once doesn't keep nagging. Keyed by the normalized title; capped to
// the most recent entries. Mirrors the small localStorage helpers elsewhere (e.g.
// pendingApproval.ts).

const KEY = "tday.repeatSuggestion.dismissed";
const MAX_ENTRIES = 200;

function read(): string[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = localStorage.getItem(KEY);
    const parsed = raw ? (JSON.parse(raw) as unknown) : [];
    return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === "string") : [];
  } catch {
    return [];
  }
}

export function isRepeatSuggestionDismissed(normalizedTitle: string): boolean {
  if (!normalizedTitle) return false;
  return read().includes(normalizedTitle);
}

export function dismissRepeatSuggestion(normalizedTitle: string): void {
  if (typeof window === "undefined" || !normalizedTitle) return;
  const next = read().filter((t) => t !== normalizedTitle);
  next.push(normalizedTitle);
  try {
    localStorage.setItem(KEY, JSON.stringify(next.slice(-MAX_ENTRIES)));
  } catch {
    // Ignore quota/serialization failures — dismissal is best-effort.
  }
}
