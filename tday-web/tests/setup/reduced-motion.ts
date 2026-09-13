/**
 * A `prefers-reduced-motion` stub that can change its mind.
 *
 * jsdom does implement `matchMedia`, but its lists are frozen at
 * `matches: false` for their lifetime — so the half of `src/lib/prefersReducedMotion.ts`
 * that exists to notice a CHANGE cannot be exercised against the real thing, and
 * a per-file stub that only reports a fixed value quietly tests the read alone.
 *
 * Unlike its neighbour `web-storage.ts`, this is NOT a vitest `setupFiles` entry:
 * it is imported by the handful of tests that care, because a global that forces
 * every suite onto a fake media query would be deciding the preference for tests
 * that have no opinion about it.
 *
 * Only the members `prefersReducedMotion.ts` touches are implemented, so how much
 * `MediaQueryList` surface the helper leans on stays visible at a glance.
 */
export type ReducedMotionControl = {
  /** Flip the preference and notify every subscriber, as the OS would. */
  set(reduce: boolean): void;
  /** Attached subscriber count — what a cleanup assertion reads. */
  readonly listenerCount: number;
};

/**
 * Replaces `window.matchMedia` for the rest of the test. Putting it back is the
 * caller's job — an `afterEach` that restores a `window.matchMedia` captured at
 * module scope — because some of these tests delete `matchMedia` outright
 * instead of installing this, and one undo that covers both beats two.
 */
export function installReducedMotion(initial: boolean): ReducedMotionControl {
  const listeners = new Set<() => void>();
  const list = {
    matches: initial,
    media: "(prefers-reduced-motion: reduce)",
    addEventListener: (_type: string, listener: () => void) => {
      listeners.add(listener);
    },
    removeEventListener: (_type: string, listener: () => void) => {
      listeners.delete(listener);
    },
  };

  // Any other query keeps answering false rather than throwing: a component
  // under test may well ask about hover or display-mode on the way past.
  window.matchMedia = ((query: string) =>
    query.includes("prefers-reduced-motion")
      ? list
      : {
          matches: false,
          media: query,
          addEventListener: () => {},
          removeEventListener: () => {},
        }) as unknown as typeof window.matchMedia;

  return {
    set(reduce: boolean) {
      list.matches = reduce;
      listeners.forEach((listener) => listener());
    },
    get listenerCount() {
      return listeners.size;
    },
  };
}
