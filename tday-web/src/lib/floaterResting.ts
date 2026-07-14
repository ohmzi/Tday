// Client-only "resting floaters" aging — mirrors the shared Kotlin FloaterResting so
// web/Android/iOS fade and group identically. Input (updatedAtEpochMs) is READ-ONLY;
// never write it back from aging logic (it's the LWW sync clock).

export type FloaterRestingTier = "active" | "fading" | "resting";

export const FLOATER_FADING_DAYS = 30;
export const FLOATER_RESTING_DAYS = 90;
const MS_PER_DAY = 86_400_000;

export function floaterRestingTier(
  updatedAtEpochMs: number | null | undefined,
  nowEpochMs: number,
): FloaterRestingTier {
  if (!updatedAtEpochMs || updatedAtEpochMs <= 0) return "active";
  const ageDays = (nowEpochMs - updatedAtEpochMs) / MS_PER_DAY;
  if (ageDays >= FLOATER_RESTING_DAYS) return "resting";
  if (ageDays >= FLOATER_FADING_DAYS) return "fading";
  return "active";
}

/** The floater's last-touched instant, falling back to creation when absent. */
export function floaterUpdatedEpochMs(floater: {
  updatedAt?: Date | null;
  createdAt?: Date | null;
}): number | null {
  return (floater.updatedAt ?? floater.createdAt)?.getTime() ?? null;
}

// The "resting floaters" display cue is on by default; users can turn it off.
const RESTING_PREF_KEY = "tday.restingFloaters.enabled";

export function isRestingFloatersEnabled(): boolean {
  if (typeof window === "undefined") return true;
  return localStorage.getItem(RESTING_PREF_KEY) !== "0";
}

export function setRestingFloatersEnabled(enabled: boolean): void {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(RESTING_PREF_KEY, enabled ? "1" : "0");
  } catch {
    // best-effort
  }
}
