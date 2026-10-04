// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  answerFrom,
  applyInstanceTelemetry,
  clientSentryDsn,
  getInstanceTelemetry,
  getTelemetryConsentAt,
  INSTANCE_TELEMETRY_URL,
  isCrashReportingConfigured,
  isTelemetryGranted,
  parseInstanceTelemetry,
  refreshInstanceTelemetry,
  subscribeToTelemetryConsent,
  watchInstanceTelemetry,
} from "@/lib/privacy/instanceTelemetry";
import { setAppMode } from "@/lib/local/appMode";

/**
 * The browser's crash-report answer is the server's, so this store is one in-memory copy of it:
 * off until a read says otherwise, and off again if a read fails. There is no storage path left to
 * test, which is itself the point — nothing a person chose for themselves in an older build can
 * turn reporting on.
 */

const ANSWERED_AT = "2026-10-01T12:00:00.000Z";

/** The browser globals and events these tests fake more than once. */
const FETCH = "fetch";
const VISIBLE = "visible";
const VISIBILITY_CHANGE = "visibilitychange";

function stubFetch(response: unknown) {
  const fetchMock = vi.fn(() => Promise.resolve(response));
  vi.stubGlobal(FETCH, fetchMock);
  return fetchMock;
}

beforeEach(() => {
  window.localStorage.clear();
  applyInstanceTelemetry(answerFrom(false, null));
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

describe("the instance answer", () => {
  it("is off, with no consent moment, until the server says otherwise", () => {
    expect(getInstanceTelemetry()).toEqual({ enabled: false, updatedAtMs: null });
    expect(isTelemetryGranted()).toBe(false);
    expect(getTelemetryConsentAt()).toBeNull();
  });

  it("reports the moment the admin answered as the time consent began", () => {
    applyInstanceTelemetry(answerFrom(true, ANSWERED_AT));

    expect(isTelemetryGranted()).toBe(true);
    expect(getTelemetryConsentAt()).toBe(Date.parse(ANSWERED_AT));
  });

  it("has no consent moment to guard with when the admin turned reports off", () => {
    applyInstanceTelemetry(answerFrom(true, ANSWERED_AT));
    applyInstanceTelemetry(answerFrom(false, "2026-10-02T09:00:00.000Z"));

    expect(isTelemetryGranted()).toBe(false);
    expect(getTelemetryConsentAt()).toBeNull();
  });

  it("keeps a yes that arrives without a usable timestamp, with no guard", () => {
    applyInstanceTelemetry(answerFrom(true, null));

    expect(isTelemetryGranted()).toBe(true);
    expect(getTelemetryConsentAt()).toBeNull();
  });

  it("tells subscribers about a change, and only about a change", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToTelemetryConsent(listener);

    applyInstanceTelemetry(answerFrom(false, null));
    expect(listener).not.toHaveBeenCalled();

    applyInstanceTelemetry(answerFrom(true, ANSWERED_AT));
    expect(listener).toHaveBeenCalledTimes(1);

    applyInstanceTelemetry(answerFrom(true, ANSWERED_AT));
    expect(listener).toHaveBeenCalledTimes(1);

    unsubscribe();
    applyInstanceTelemetry(answerFrom(false, null));
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it("ignores an older build's per-browser keys outright", async () => {
    window.localStorage.setItem("tday.telemetry.consent", "granted");
    window.localStorage.setItem("tday.telemetry.consentAt", "1759320000000");
    stubFetch({ ok: true, json: () => Promise.resolve({ enabled: false, updatedAt: null }) });

    await refreshInstanceTelemetry();

    expect(isTelemetryGranted()).toBe(false);
  });
});

describe("parseInstanceTelemetry", () => {
  it("accepts the endpoint's own shape", () => {
    expect(parseInstanceTelemetry({ enabled: true, updatedAt: ANSWERED_AT })).toEqual({
      enabled: true,
      updatedAtMs: Date.parse(ANSWERED_AT),
    });
    expect(parseInstanceTelemetry({ enabled: false, updatedAt: null })).toEqual({
      enabled: false,
      updatedAtMs: null,
    });
  });

  it.each([
    ["a missing flag", { updatedAt: ANSWERED_AT }],
    ["a flag that is not a boolean", { enabled: "true" }],
    ["a body that is not an object", "enabled"],
    ["null", null],
  ])("rejects %s", (_label, payload) => {
    expect(parseInstanceTelemetry(payload)).toBeNull();
  });

  it("keeps a yes whose timestamp is unusable, without a guard", () => {
    expect(parseInstanceTelemetry({ enabled: true, updatedAt: "not a time" })).toEqual({
      enabled: true,
      updatedAtMs: null,
    });
  });
});

describe("refreshInstanceTelemetry", () => {
  it("reads the public endpoint and holds the answer it gets", async () => {
    const fetchMock = stubFetch({
      ok: true,
      json: () => Promise.resolve({ enabled: true, updatedAt: ANSWERED_AT }),
    });

    await refreshInstanceTelemetry();

    expect(fetchMock).toHaveBeenCalledWith(
      INSTANCE_TELEMETRY_URL,
      expect.objectContaining({ method: "GET", credentials: "same-origin", cache: "no-store" }),
    );
    expect(isTelemetryGranted()).toBe(true);
    expect(getTelemetryConsentAt()).toBe(Date.parse(ANSWERED_AT));
  });

  it("shares one request between concurrent callers", async () => {
    const fetchMock = stubFetch({
      ok: true,
      json: () => Promise.resolve({ enabled: true, updatedAt: ANSWERED_AT }),
    });

    await Promise.all([refreshInstanceTelemetry(), refreshInstanceTelemetry()]);

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["the request fails", () => Promise.reject(new Error("offline"))],
    ["the server answers with an error status", () => Promise.resolve({ ok: false, json: () => Promise.resolve({}) })],
    ["the body is not an answer", () => Promise.resolve({ ok: true, json: () => Promise.resolve({}) })],
  ])("leaves the answer off when %s", async (_label, response) => {
    window.localStorage.setItem("tday.telemetry.consent", "granted");
    vi.stubGlobal(FETCH, vi.fn(response));

    await refreshInstanceTelemetry();

    expect(isTelemetryGranted()).toBe(false);
    expect(getTelemetryConsentAt()).toBeNull();
  });

  it("does not take an answer away when a later read fails", async () => {
    applyInstanceTelemetry(answerFrom(true, ANSWERED_AT));
    vi.stubGlobal(FETCH, vi.fn(() => Promise.reject(new Error("offline"))));

    await refreshInstanceTelemetry();

    expect(isTelemetryGranted()).toBe(true);
  });
});

describe("watchInstanceTelemetry", () => {
  let unwatch: (() => void) | undefined;

  const setVisibility = (state: "visible" | "hidden") => {
    Object.defineProperty(document, "visibilityState", { value: state, configurable: true });
  };
  const answer = (enabled: boolean, updatedAt: string | null) => ({
    ok: true,
    json: () => Promise.resolve({ enabled, updatedAt }),
  });

  afterEach(() => {
    unwatch?.();
    unwatch = undefined;
    setVisibility(VISIBLE);
    setAppMode(null);
  });

  it("re-reads on the return to view, and applies a change both ways", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(answer(true, ANSWERED_AT))
      .mockResolvedValueOnce(answer(false, "2026-10-02T09:00:00.000Z"));
    vi.stubGlobal(FETCH, fetchMock);
    unwatch = watchInstanceTelemetry();

    // A hidden page never asks.
    setVisibility("hidden");
    document.dispatchEvent(new Event(VISIBILITY_CHANGE));
    await Promise.resolve();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(isTelemetryGranted()).toBe(false);

    // Back in view: the admin turned reports on while this page was away.
    setVisibility(VISIBLE);
    document.dispatchEvent(new Event(VISIBILITY_CHANGE));
    await vi.waitFor(() => expect(isTelemetryGranted()).toBe(true));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getTelemetryConsentAt()).toBe(Date.parse(ANSWERED_AT));

    // Away and back again, with the answer now a no: the page stops reporting.
    setVisibility("hidden");
    document.dispatchEvent(new Event(VISIBILITY_CHANGE));
    setVisibility(VISIBLE);
    document.dispatchEvent(new Event(VISIBILITY_CHANGE));
    await vi.waitFor(() => expect(isTelemetryGranted()).toBe(false));
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(getTelemetryConsentAt()).toBeNull();
  });

  it("also re-reads when the window is focused", async () => {
    const fetchMock = vi.fn().mockResolvedValue(answer(true, ANSWERED_AT));
    vi.stubGlobal(FETCH, fetchMock);
    unwatch = watchInstanceTelemetry();
    setVisibility(VISIBLE);

    window.dispatchEvent(new Event("focus"));

    await vi.waitFor(() => expect(isTelemetryGranted()).toBe(true));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("never asks in Local Mode, where there is no server to obey", async () => {
    const fetchMock = vi.fn().mockResolvedValue(answer(true, ANSWERED_AT));
    vi.stubGlobal(FETCH, fetchMock);
    unwatch = watchInstanceTelemetry();
    setAppMode("local");
    setVisibility(VISIBLE);

    window.dispatchEvent(new Event("focus"));
    document.dispatchEvent(new Event(VISIBILITY_CHANGE));
    await Promise.resolve();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(isTelemetryGranted()).toBe(false);
  });

  it("cannot start anything from a read that never answers", async () => {
    vi.stubGlobal(FETCH, vi.fn(() => new Promise(() => {})));
    unwatch = watchInstanceTelemetry();
    setVisibility(VISIBLE);

    window.dispatchEvent(new Event("focus"));

    expect(isTelemetryGranted()).toBe(false);
    expect(getTelemetryConsentAt()).toBeNull();
  });
});

describe("the browser build's DSN", () => {
  it("is read from the build environment", () => {
    vi.stubEnv("VITE_SENTRY_DSN", " https://key@o1.ingest.example.invalid/2 ");

    expect(clientSentryDsn()).toBe("https://key@o1.ingest.example.invalid/2");
    expect(isCrashReportingConfigured()).toBe(true);
  });

  it("is empty in a build without one, so there is nothing to ask about", () => {
    vi.stubEnv("VITE_SENTRY_DSN", "");

    expect(clientSentryDsn()).toBe("");
    expect(isCrashReportingConfigured()).toBe(false);
  });
});
