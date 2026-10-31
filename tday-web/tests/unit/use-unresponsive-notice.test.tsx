// @vitest-environment jsdom

/**
 * The "T'Day stopped responding" notice must only fire for a thread that was actually blocked while
 * the page was on screen. A backgrounded tab's throttled timers look identical to a stall from the
 * inside, so the watchdog must stay quiet for them, and still report a real stall in the foreground.
 *
 * The clocks are driven by hand: `performance.now` and `Date.now` are stubbed, and only the interval
 * itself runs on fake timers. A "block" advances both clocks before one tick fires, which is what a
 * real stall looks like to the hook.
 */

import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { useUnresponsiveNotice } from "@/hooks/useUnresponsiveNotice";

vi.mock("sonner", () => ({ toast: vi.fn() }));
vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

let perfNow = 0;
let wallNow = 0;
let hidden = false;

function setHidden(next: boolean) {
  hidden = next;
}

function setVisibility(next: boolean) {
  setHidden(!next);
  document.dispatchEvent(new Event("visibilitychange"));
}

/** A stall of `ms`: both clocks move, and no tick fires until the caller advances the interval. */
function block(ms: number) {
  perfNow += ms;
  wallNow += ms;
}

/** A background throttle: time passes for the clocks, and the timer fires late. */
function elapse(ms: number) {
  perfNow += ms;
  wallNow += ms;
}

function tick() {
  vi.advanceTimersByTime(1000);
}

describe("useUnresponsiveNotice", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
    perfNow = 0;
    wallNow = 0;
    hidden = false;
    vi.spyOn(performance, "now").mockImplementation(() => perfNow);
    vi.spyOn(Date, "now").mockImplementation(() => wallNow);
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    Object.defineProperty(document, "hidden", { configurable: true, get: () => hidden });
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      get: () => (hidden ? "hidden" : "visible"),
    });
    vi.mocked(toast).mockClear();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    delete (document as unknown as Record<string, unknown>).hidden;
    delete (document as unknown as Record<string, unknown>).visibilityState;
  });

  it("reports a real stall while the page is visible and focused", () => {
    renderHook(() => useUnresponsiveNotice());
    tick();
    block(5000);
    tick();

    expect(toast).toHaveBeenCalledTimes(1);
    expect(vi.mocked(toast).mock.calls[0]?.[0]).toBe("unresponsiveTitle");
    expect(vi.mocked(toast).mock.calls[0]?.[1]).toMatchObject({
      id: "tday-unresponsive",
      description: "unresponsiveBody",
    });
  });

  it("does not report a background gap when the tab returns with its event", () => {
    renderHook(() => useUnresponsiveNotice());
    tick();

    setVisibility(false);
    elapse(60_000);
    tick();
    elapse(60_000);
    tick();

    setVisibility(true);
    tick();

    expect(toast).not.toHaveBeenCalled();
  });

  it("does not report a background gap when the first visible tick lands before the event", () => {
    renderHook(() => useUnresponsiveNotice());
    tick();

    setVisibility(false);
    elapse(60_000);
    tick();

    // The browser flips visibility, and a throttled tick fires before visibilitychange is dispatched.
    setHidden(false);
    elapse(60_000);
    tick();

    expect(toast).not.toHaveBeenCalled();
  });

  it("does not report a gap that spans a window focus change", () => {
    renderHook(() => useUnresponsiveNotice());
    tick();

    window.dispatchEvent(new Event("blur"));
    elapse(30_000);
    tick();
    window.dispatchEvent(new Event("focus"));
    elapse(30_000);
    tick();

    expect(toast).not.toHaveBeenCalled();
  });

  it("does not report a sleep, where the wall clock jumps without the monotonic clock", () => {
    renderHook(() => useUnresponsiveNotice());
    tick();

    wallNow += 60_000;
    perfNow += 3000;
    tick();

    expect(toast).not.toHaveBeenCalled();
  });

  it("keeps reporting real stalls after a background period", () => {
    renderHook(() => useUnresponsiveNotice());
    tick();

    setVisibility(false);
    elapse(60_000);
    tick();
    setVisibility(true);
    tick();
    tick();

    block(5000);
    tick();

    expect(toast).toHaveBeenCalledTimes(1);
  });
});
