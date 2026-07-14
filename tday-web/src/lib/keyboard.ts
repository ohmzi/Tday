/** Shared guards for global key handlers (previously copy-pasted per component). */

/** True when the key event lands in a place that accepts typing. */
export function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return (
    target.isContentEditable ||
    ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName)
  );
}

export function isMacPlatform(): boolean {
  if (typeof navigator === "undefined") return false;
  return navigator.userAgent.toLowerCase().includes("mac");
}

/** Display segment for the command modifier: ⌘ on Mac, Ctrl elsewhere. */
export function commandKeyLabel(): string {
  return isMacPlatform() ? "⌘" : "Ctrl";
}
