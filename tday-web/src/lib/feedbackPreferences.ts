/**
 * The two cues the app gives back that are not on the screen — the pop a
 * completed task makes, and the buzz under a finger — and the user's say in each.
 *
 * Android and iOS play both of these too, and on both of them the *phone* already
 * holds the switch. `TaskCompletionSound.play()` refuses outright unless the
 * ringer is in `RINGER_MODE_NORMAL`; `SoundManager` runs on an `.ambient` session,
 * which is what makes the silent switch silence it; and every native haptic goes
 * out through `performHapticFeedback` / `UIFeedbackGenerator`, which the OS itself
 * mutes when touch feedback is off. Three system signals, none of which reaches a
 * browser: there is no media query for "this phone is on silent", and
 * `navigator.vibrate()` buzzes whatever the system haptic setting says. Web is the
 * one client where the app has to own the switch, which is why this file exists
 * and its native counterparts do not.
 *
 * That makes it an accessibility control rather than a nicety. A person who finds
 * an unannounced buzz painful, or who cannot have a device chirp in the room they
 * are in, has a way to say so on the other two clients and had none here.
 *
 * Device-local, like `isRestingFloatersEnabled`: the question is about the machine
 * in the room — whether it can buzz at all, whether its owner is somewhere quiet —
 * and not about the account, which may be signed in on a desk and in a pocket at
 * the same time.
 *
 * Both default to on. Turning a cue off is a choice; defaulting to off would make
 * it for everyone who never opens Settings.
 */

/**
 * Exported so `AuthProvider` can name them in `PRESERVED_STORAGE_KEYS`: a session
 * ending — a sign-out, or a token quietly expiring — must not hand someone back a
 * buzz they turned off, and by that list's own rule these belong to the browser
 * rather than to any server account.
 */
export const SOUND_STORAGE_KEY = "tday.sound.enabled";
export const HAPTICS_STORAGE_KEY = "tday.haptics.enabled";

/**
 * Read through to storage every time, with no module-level copy of the answer.
 *
 * A cached flag would be a second place the preference lives, and it would have to
 * be invalidated from every other place that can write the key — this tab's
 * Settings switch, the sign-out sweep, another tab. The reads are on tap-sized
 * events — a checkbox, a drop, a bar button — not on a frame loop, so there is
 * nothing here worth the staleness a cache would buy.
 *
 * Storage can throw rather than return null when a browser blocks site data
 * outright, so the fallback covers both, and it falls the same way round the
 * defaults do: a browser that cannot answer gets the cue.
 */
function isEnabled(key: string): boolean {
  if (typeof window === "undefined") return true;
  try {
    return window.localStorage.getItem(key) !== "0";
  } catch {
    return true;
  }
}

function setEnabled(key: string, enabled: boolean): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(key, enabled ? "1" : "0");
  } catch {
    // Best-effort, as elsewhere: a preference that cannot be stored still applies
    // for this session, because the Settings switch holds it in its own state.
  }
}

/** Whether a completed task is allowed to make a noise. */
export function isSoundEnabled(): boolean {
  return isEnabled(SOUND_STORAGE_KEY);
}

export function setSoundEnabled(enabled: boolean): void {
  setEnabled(SOUND_STORAGE_KEY, enabled);
}

/** Whether the app is allowed to vibrate. Asked at `haptics.ts`'s one chokepoint. */
export function isHapticsEnabled(): boolean {
  return isEnabled(HAPTICS_STORAGE_KEY);
}

export function setHapticsEnabled(enabled: boolean): void {
  setEnabled(HAPTICS_STORAGE_KEY, enabled);
}
