export const RETURNING_BROWSER_STORAGE_KEY = "tday.returning-browser";

export function hasReturningBrowser(): boolean {
  if (typeof window === "undefined") return false;

  try {
    return window.localStorage.getItem(RETURNING_BROWSER_STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

export function markReturningBrowser(): void {
  if (typeof window === "undefined") return;

  try {
    window.localStorage.setItem(RETURNING_BROWSER_STORAGE_KEY, "1");
  } catch {
    // Ignore localStorage write failures in restricted browser contexts.
  }
}
