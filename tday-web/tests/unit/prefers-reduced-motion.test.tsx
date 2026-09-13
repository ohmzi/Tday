// @vitest-environment jsdom
import { describe, expect, it, afterEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import {
  prefersReducedMotion,
  usePrefersReducedMotion,
} from "@/lib/prefersReducedMotion";
import { installReducedMotion } from "../setup/reduced-motion";

const REAL_MATCH_MEDIA = window.matchMedia;

afterEach(() => {
  window.matchMedia = REAL_MATCH_MEDIA;
});

describe("prefersReducedMotion", () => {
  it("reports the query's current answer", () => {
    installReducedMotion(true);
    expect(prefersReducedMotion()).toBe(true);
  });

  it("reports false when the user has not asked for reduced motion", () => {
    installReducedMotion(false);
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
    installReducedMotion(true);
    const { result } = renderHook(() => usePrefersReducedMotion());
    expect(result.current).toBe(true);
  });

  it("re-renders when the preference flips mid-session", () => {
    const media = installReducedMotion(false);
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
    const media = installReducedMotion(false);
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
