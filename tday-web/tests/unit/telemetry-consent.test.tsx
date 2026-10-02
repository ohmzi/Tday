// @vitest-environment jsdom

import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  TELEMETRY_CONSENT_AT_STORAGE_KEY,
  TELEMETRY_CONSENT_STORAGE_KEY,
  getTelemetryConsent,
  getTelemetryConsentAt,
  isCrashReportingConfigured,
  isTelemetryGranted,
  setTelemetryConsent,
  subscribeToTelemetryConsent,
} from "@/lib/privacy/telemetryConsent";
import { useTelemetryConsent } from "@/hooks/useTelemetryConsent";

/**
 * Another tab writing the key is the only way the stored value changes under a running page, and
 * the browser reports it as a `storage` event in every OTHER document. Dispatching that event is
 * also how these tests put the module's in-memory copy back to what the cleared storage says.
 */
function syncFromStorage(key: string | null = null) {
  window.dispatchEvent(new StorageEvent("storage", { key }));
}

beforeEach(() => {
  window.localStorage.clear();
  syncFromStorage();
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.useRealTimers();
});

describe("telemetry consent", () => {
  it("is unanswered, and so off, until somebody answers", () => {
    expect(getTelemetryConsent()).toBe("unanswered");
    expect(isTelemetryGranted()).toBe(false);
    expect(getTelemetryConsentAt()).toBeNull();
  });

  it("grants with the moment it was granted, so older events can be told apart", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-01T12:00:00.000Z"));

    setTelemetryConsent(true);

    expect(getTelemetryConsent()).toBe("granted");
    expect(isTelemetryGranted()).toBe(true);
    expect(getTelemetryConsentAt()).toBe(Date.parse("2026-10-01T12:00:00.000Z"));
    expect(window.localStorage.getItem(TELEMETRY_CONSENT_STORAGE_KEY)).toBe("granted");
    expect(window.localStorage.getItem(TELEMETRY_CONSENT_AT_STORAGE_KEY)).toBe(
      String(Date.parse("2026-10-01T12:00:00.000Z")),
    );
  });

  it("denies without keeping a grant time", () => {
    setTelemetryConsent(true);
    setTelemetryConsent(false);

    expect(getTelemetryConsent()).toBe("denied");
    expect(isTelemetryGranted()).toBe(false);
    expect(getTelemetryConsentAt()).toBeNull();
    expect(window.localStorage.getItem(TELEMETRY_CONSENT_STORAGE_KEY)).toBe("denied");
    expect(window.localStorage.getItem(TELEMETRY_CONSENT_AT_STORAGE_KEY)).toBeNull();
  });

  it("takes a fresh grant time every time it is turned back on", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-01T12:00:00.000Z"));
    setTelemetryConsent(true);
    setTelemetryConsent(false);

    vi.setSystemTime(new Date("2026-10-02T08:30:00.000Z"));
    setTelemetryConsent(true);

    expect(getTelemetryConsentAt()).toBe(Date.parse("2026-10-02T08:30:00.000Z"));
  });

  it("treats an unknown stored value as unanswered rather than as a yes", () => {
    window.localStorage.setItem(TELEMETRY_CONSENT_STORAGE_KEY, "1");
    syncFromStorage(TELEMETRY_CONSENT_STORAGE_KEY);

    expect(getTelemetryConsent()).toBe("unanswered");
    expect(isTelemetryGranted()).toBe(false);
  });

  it("does not notify when the answer did not change", () => {
    setTelemetryConsent(true);
    const listener = vi.fn();
    const unsubscribe = subscribeToTelemetryConsent(listener);

    setTelemetryConsent(true);

    expect(listener).not.toHaveBeenCalled();
    unsubscribe();
  });

  it("tells subscribers about each change, and stops once they unsubscribe", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToTelemetryConsent(listener);

    setTelemetryConsent(true);
    setTelemetryConsent(false);
    expect(listener).toHaveBeenCalledTimes(2);

    unsubscribe();
    setTelemetryConsent(true);
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it("follows another tab that turns reports off", () => {
    setTelemetryConsent(true);
    const listener = vi.fn();
    const unsubscribe = subscribeToTelemetryConsent(listener);

    window.localStorage.setItem(TELEMETRY_CONSENT_STORAGE_KEY, "denied");
    window.localStorage.removeItem(TELEMETRY_CONSENT_AT_STORAGE_KEY);
    syncFromStorage(TELEMETRY_CONSENT_STORAGE_KEY);

    expect(isTelemetryGranted()).toBe(false);
    expect(listener).toHaveBeenCalledTimes(1);
    unsubscribe();
  });

  it("follows the browser clearing site data under it", () => {
    setTelemetryConsent(true);

    window.localStorage.clear();
    syncFromStorage(null);

    expect(getTelemetryConsent()).toBe("unanswered");
  });

  it("ignores storage events for other keys", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToTelemetryConsent(listener);

    window.localStorage.setItem("tday.sound.enabled", "0");
    syncFromStorage("tday.sound.enabled");

    expect(listener).not.toHaveBeenCalled();
    unsubscribe();
  });

  it("keeps the answer for the session when storage refuses the write", () => {
    const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("blocked", "SecurityError");
    });

    setTelemetryConsent(true);

    expect(setItem).toHaveBeenCalled();
    expect(isTelemetryGranted()).toBe(true);
    setItem.mockRestore();
  });
});

describe("useTelemetryConsent", () => {
  it("re-renders when the answer changes", () => {
    const { result } = renderHook(() => useTelemetryConsent());
    expect(result.current).toBe("unanswered");

    act(() => setTelemetryConsent(true));
    expect(result.current).toBe("granted");

    act(() => setTelemetryConsent(false));
    expect(result.current).toBe("denied");
  });
});

describe("crash reporting availability", () => {
  it("is off when this build carries no DSN, whatever has been answered", () => {
    vi.stubEnv("VITE_SENTRY_DSN", "");
    expect(isCrashReportingConfigured()).toBe(false);

    vi.stubEnv("VITE_SENTRY_DSN", "   ");
    expect(isCrashReportingConfigured()).toBe(false);
  });

  it("is on when the build carries a DSN", () => {
    vi.stubEnv("VITE_SENTRY_DSN", "https://key@o1.ingest.example.invalid/2");
    expect(isCrashReportingConfigured()).toBe(true);
  });
});
