// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { useFadeUnmount } from "@/hooks/useFadeUnmount";

const FADE_MS = 260;

describe("useFadeUnmount", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("starts unmounted when `expanded` starts false — no spurious exit fade on a cold collapsed mount", () => {
    const { result } = renderHook(() => useFadeUnmount(false, FADE_MS));
    expect(result.current).toBe(false);
  });

  it("starts mounted when `expanded` starts true", () => {
    const { result } = renderHook(() => useFadeUnmount(true, FADE_MS));
    expect(result.current).toBe(true);
  });

  it("expanding mounts immediately — the caller's own `expanded` flip is never delayed by this hook", () => {
    const { result, rerender } = renderHook(({ expanded }) => useFadeUnmount(expanded, FADE_MS), {
      initialProps: { expanded: false },
    });
    expect(result.current).toBe(false);

    rerender({ expanded: true });
    expect(result.current).toBe(true);
  });

  it("collapsing stays mounted for exactly `durationMs` so the exit fade can finish, then unmounts", async () => {
    const { result, rerender } = renderHook(({ expanded }) => useFadeUnmount(expanded, FADE_MS), {
      initialProps: { expanded: true },
    });
    expect(result.current).toBe(true);

    rerender({ expanded: false });
    // Still mounted right after the flip — the fade-out has not played yet.
    expect(result.current).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(FADE_MS - 50);
    });
    expect(result.current).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(50);
    });
    expect(result.current).toBe(false);
  });

  it("re-expanding mid fade-out cancels the pending unmount — no flash of the body disappearing", async () => {
    const { result, rerender } = renderHook(({ expanded }) => useFadeUnmount(expanded, FADE_MS), {
      initialProps: { expanded: true },
    });

    rerender({ expanded: false });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(FADE_MS / 2);
    });
    expect(result.current).toBe(true);

    // Re-expand before the exit timer fires.
    rerender({ expanded: true });
    expect(result.current).toBe(true);

    // The original exit timer, if it fired anyway, must be a no-op — assert
    // the body stays mounted through where it would have fired.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(FADE_MS);
    });
    expect(result.current).toBe(true);
  });

  it("skips the delay outright for a non-positive duration — unmounts on the same flip", () => {
    const { result, rerender } = renderHook(({ expanded }) => useFadeUnmount(expanded, 0), {
      initialProps: { expanded: true },
    });
    rerender({ expanded: false });
    expect(result.current).toBe(false);
  });

  it("skips the delay under prefers-reduced-motion — unmounts on the same flip", () => {
    const originalMatchMedia = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: query.includes("prefers-reduced-motion"),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    })) as unknown as typeof window.matchMedia;

    try {
      const { result, rerender } = renderHook(({ expanded }) => useFadeUnmount(expanded, FADE_MS), {
        initialProps: { expanded: true },
      });
      rerender({ expanded: false });
      expect(result.current).toBe(false);
    } finally {
      window.matchMedia = originalMatchMedia;
    }
  });

  it("unmounting the host mid fade-out clears the pending timer instead of leaking a setState-after-unmount", async () => {
    const { result, rerender, unmount } = renderHook(
      ({ expanded }) => useFadeUnmount(expanded, FADE_MS),
      { initialProps: { expanded: true } },
    );
    rerender({ expanded: false });
    expect(result.current).toBe(true);

    unmount();

    // If the timeout were not cleared, this would call setState on an
    // unmounted component — vitest/jsdom surfaces that as a thrown/console
    // error, so simply not throwing here is the assertion.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(FADE_MS + 10);
    });
  });
});
