// @vitest-environment jsdom

/**
 * The "T'Day stopped responding" notice is driven by the browser's `longtask` entries. A long task
 * is only evidence of a stall when the page was on screen and focused, and it started after the
 * last interruption. Timers are not used: a background tab or a sleeping machine looks like a stall
 * to a timer, and a missed notice is preferred over a false one.
 */

import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { useUnresponsiveNotice } from "@/hooks/useUnresponsiveNotice";

vi.mock("sonner", () => ({ toast: vi.fn() }));
vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

type Callback = (list: { getEntries: () => Array<{ startTime: number; duration: number }> }) => void;

let observers: FakeObserver[] = [];
let perfNow = 0;
let wallNow = 0;
let hidden = false;
let focused = true;

class FakeObserver {
  static supportedEntryTypes: string[] = ["longtask"];
  readonly observe = vi.fn();
  readonly disconnect = vi.fn();
  constructor(readonly callback: Callback) {
    observers.push(this);
  }
  deliver(entries: Array<{ startTime: number; duration: number }>) {
    this.callback({ getEntries: () => entries });
  }
}

function setVisibility(next: boolean) {
  hidden = !next;
  document.dispatchEvent(new Event("visibilitychange"));
}

function longTask(startTime: number, duration: number) {
  return { startTime, duration };
}

function latestObserver(): FakeObserver {
  const observer = observers.at(-1);
  if (!observer) throw new Error("no PerformanceObserver created");
  return observer;
}

describe("useUnresponsiveNotice", () => {
  beforeEach(() => {
    observers = [];
    perfNow = 0;
    wallNow = 0;
    hidden = false;
    focused = true;
    FakeObserver.supportedEntryTypes = ["longtask"];
    vi.stubGlobal("PerformanceObserver", FakeObserver);
    vi.spyOn(performance, "now").mockImplementation(() => perfNow);
    vi.spyOn(Date, "now").mockImplementation(() => wallNow);
    vi.spyOn(document, "hasFocus").mockImplementation(() => focused);
    Object.defineProperty(document, "hidden", { configurable: true, get: () => hidden });
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      get: () => (hidden ? "hidden" : "visible"),
    });
    vi.mocked(toast).mockClear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    delete (document as unknown as Record<string, unknown>).hidden;
    delete (document as unknown as Record<string, unknown>).visibilityState;
  });

  it("observes longtask entries that are not buffered", () => {
    renderHook(() => useUnresponsiveNotice());
    expect(latestObserver().observe).toHaveBeenCalledWith({ type: "longtask", buffered: false });
  });

  it("shows the notice for a long task while the page is visible and focused", () => {
    renderHook(() => useUnresponsiveNotice());
    perfNow = 1000;
    latestObserver().deliver([longTask(1000, 5000)]);

    expect(toast).toHaveBeenCalledTimes(1);
    expect(vi.mocked(toast).mock.calls[0]?.[0]).toBe("unresponsiveTitle");
    expect(vi.mocked(toast).mock.calls[0]?.[1]).toMatchObject({
      id: "tday-unresponsive",
      description: "unresponsiveBody",
    });
  });

  it("ignores a long task that started before an interruption", () => {
    renderHook(() => useUnresponsiveNotice());
    perfNow = 1000;
    window.dispatchEvent(new Event("blur"));
    latestObserver().deliver([longTask(500, 5000)]);

    expect(toast).not.toHaveBeenCalled();
  });

  it("shows a long task that started after an interruption", () => {
    renderHook(() => useUnresponsiveNotice());
    perfNow = 1000;
    window.dispatchEvent(new Event("blur"));
    latestObserver().deliver([longTask(1500, 5000)]);

    expect(toast).toHaveBeenCalledTimes(1);
  });

  it("ignores a long task delivered while the page is hidden", () => {
    renderHook(() => useUnresponsiveNotice());
    setVisibility(false);
    latestObserver().deliver([longTask(1000, 5000)]);

    expect(toast).not.toHaveBeenCalled();
  });

  it("ignores a long task delivered while the window is not focused", () => {
    renderHook(() => useUnresponsiveNotice());
    focused = false;
    latestObserver().deliver([longTask(1000, 5000)]);

    expect(toast).not.toHaveBeenCalled();
  });

  it("ignores a task shorter than the threshold", () => {
    renderHook(() => useUnresponsiveNotice());
    latestObserver().deliver([longTask(1000, 2000)]);

    expect(toast).not.toHaveBeenCalled();
  });

  it("never shows anything where longtask is unsupported", () => {
    FakeObserver.supportedEntryTypes = ["navigation", "resource"];
    renderHook(() => useUnresponsiveNotice());

    expect(observers).toHaveLength(0);
    expect(toast).not.toHaveBeenCalled();
  });

  it("shows no notice for a 60 s gap on both clocks with no event", () => {
    renderHook(() => useUnresponsiveNotice());
    perfNow += 60_000;
    wallNow += 60_000;

    expect(toast).not.toHaveBeenCalled();
  });

  it("disconnects the observer on unmount", () => {
    const { unmount } = renderHook(() => useUnresponsiveNotice());
    const observer = latestObserver();
    unmount();

    expect(observer.disconnect).toHaveBeenCalledTimes(1);
  });
});
