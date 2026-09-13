// @vitest-environment jsdom
import { describe, expect, it, afterEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import {
  prefersReducedMotion,
  usePrefersReducedMotion,
} from "@/lib/prefersReducedMotion";

/**
 * A `MediaQueryList` that can actually change its mind, which is the half of
 * this module jsdom's own `matchMedia` cannot exercise: its lists are frozen at
 * `matches: false` for their lifetime, so a stub that only reports a fixed value
 * would test the read and silently skip the subscription.
 *
 * Only the members the module touches are implemented. Everything else on
 * `MediaQueryList` is cast away rather than stubbed, so a future reader can see
 * at a glance exactly how much surface the helper depends on.
 */
function installMatchMedia(initial: boolean) {
  const listeners = new Set<() => void>();
  const list = {
    matches: initial,
    media: "(prefers-reduced-motion: reduce)",
    addEventListener: (_: string, listener: () => void) => {
      listeners.add(listener);
    },
    removeEventListener: (_: string, listener: () => void) => {
      listeners.delete(listener);
    },
  };

  window.matchMedia = ((query: string) =>
    query.includes("prefers-reduced-motion")
      ? list
      : { matches: false, media: query, addEventListener: () => {}, removeEventListener: () => {} }) as unknown as typeof window.matchMedia;

  return {
    set(next: boolean) {
      list.matches = next;
      listeners.forEach((listener) => listener());
    },
    /** How many subscribers are attached — the cleanup assertion reads this. */
    get listenerCount() {
      return listeners.size;
    },
  };
}

const originalMatchMedia = window.matchMedia;

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

describe("prefersReducedMotion", () => {
  it("reports the query's current answer", () => {
    installMatchMedia(true);
    expect(prefersReducedMotion()).toBe(true);
  });

  it("reports false when the user has not asked for reduced motion", () => {
    installMatchMedia(false);
    expect(prefersReducedMotion()).toBe(false);
  });

  it("falls back to 'motion is fine' where the question cannot be asked", () => {
    // Stands in for SSR and for any environment without `matchMedia`: a browser
    // that cannot report the preference must get the animated build, not a
    // permanently still one.
    (window as unknown as { matchMedia?: unknown }).matchMedia = undefined;
    expect(prefersReducedMotion()).toBe(false);
  });
});

describe("usePrefersReducedMotion", () => {
  it("has the real answer on the FIRST render, not one frame later", () => {
    installMatchMedia(true);
    const { result } = renderHook(() => usePrefersReducedMotion());
    expect(result.current).toBe(true);
  });

  it("re-renders when the preference flips mid-session", () => {
    const media = installMatchMedia(false);
    const { result } = renderHook(() => usePrefersReducedMotion());
    expect(result.current).toBe(false);

    // The whole reason this is a subscription: a user turning reduce-motion on
    // (or an OS battery-saver doing it for them) must not be stuck with the
    // decision the app made at mount.
    act(() => {
      media.set(true);
    });
    expect(result.current).toBe(true);

    act(() => {
      media.set(false);
    });
    expect(result.current).toBe(false);
  });

  it("detaches its listener on unmount", () => {
    const media = installMatchMedia(false);
    const { unmount } = renderHook(() => usePrefersReducedMotion());
    expect(media.listenerCount).toBe(1);

    unmount();
    expect(media.listenerCount).toBe(0);
  });

  it("renders without a matchMedia to subscribe to", () => {
    (window as unknown as { matchMedia?: unknown }).matchMedia = undefined;
    const { result, unmount } = renderHook(() => usePrefersReducedMotion());
    expect(result.current).toBe(false);
    // The no-op unsubscribe has to be callable — returning `undefined` from
    // `subscribe` is the shape that throws here rather than at subscribe time.
    unmount();
  });
});
