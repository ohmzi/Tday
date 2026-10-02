// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * The helper keeps its rate-limit state at module level, because "once per operation per page
 * load" is a property of the page load. Each test therefore loads a fresh copy, which is also how
 * a relaunch is simulated for the persisted cooldown.
 */

const captureEvent = vi.fn();
vi.mock("@sentry/react", () => ({ captureEvent: (event: unknown) => captureEvent(event) }));

const CONSENT_KEY = "tday.telemetry.consent";
const COOLDOWN_KEY = "tday.slowOperation.cooldowns";

async function loadHelper(consent: "granted" | "denied" | null = "granted") {
  vi.resetModules();
  if (consent) window.localStorage.setItem(CONSENT_KEY, consent);
  else window.localStorage.removeItem(CONSENT_KEY);
  return import("@/lib/observability/slowOperation");
}

beforeEach(() => {
  captureEvent.mockClear();
  window.localStorage.clear();
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-10-01T12:00:00.000Z"));
});

afterEach(() => {
  vi.useRealTimers();
});

describe("durationBucket", () => {
  it.each([
    [0, "<5s"],
    [4_999, "<5s"],
    [5_000, "5-10s"],
    [9_999, "5-10s"],
    [10_000, "10-30s"],
    [29_999, "10-30s"],
    [30_000, "30-60s"],
    [59_999, "30-60s"],
    [60_000, ">60s"],
    [600_000, ">60s"],
  ])("puts %i ms in %s", async (durationMs, bucket) => {
    const { durationBucket } = await loadHelper();
    expect(durationBucket(durationMs)).toBe(bucket);
  });
});

describe("reportSlowOperation", () => {
  it("shares the operation vocabulary and thresholds every client uses", async () => {
    const { SLOW_OPERATION_THRESHOLDS_MS } = await loadHelper();
    expect(SLOW_OPERATION_THRESHOLDS_MS).toEqual({
      cold_start: 5_000,
      first_data_ready: 8_000,
      db_open_migrate: 3_000,
      cache_hydrate: 2_000,
      sync_replay: 15_000,
      api_call: 10_000,
      widget_refresh: 10_000,
      reminder_reschedule: 10_000,
      local_vault_unlock: 8_000,
      route_chunk_load: 8_000,
      app_bootstrap: 6_000,
    });
  });

  it("reports nothing for an operation under its threshold", async () => {
    const { reportSlowOperation } = await loadHelper();

    reportSlowOperation("app_bootstrap", 5_999);

    expect(captureEvent).not.toHaveBeenCalled();
  });

  it("reports an operation at its threshold as a fixed warning grouped by operation", async () => {
    const { reportSlowOperation } = await loadHelper();

    reportSlowOperation("app_bootstrap", 6_000);

    expect(captureEvent).toHaveBeenCalledTimes(1);
    expect(captureEvent).toHaveBeenCalledWith({
      message: "slow_operation",
      level: "warning",
      fingerprint: ["slow_operation", "app_bootstrap"],
      tags: { operation: "app_bootstrap", duration_bucket: "5-10s" },
      extra: { duration_ms: 6_000, threshold_ms: 6_000 },
    });
  });

  it("rounds the duration it reports", async () => {
    const { reportSlowOperation } = await loadHelper();

    reportSlowOperation("local_vault_unlock", 12_345.678);

    expect(captureEvent.mock.calls[0][0]).toMatchObject({
      tags: { duration_bucket: "10-30s" },
      extra: { duration_ms: 12_346 },
    });
  });

  it.each([["unanswered", null], ["denied", "denied"]] as const)(
    "reports nothing while consent is %s, and does not start a cooldown",
    async (_label, consent) => {
      const { reportSlowOperation } = await loadHelper(consent);

      reportSlowOperation("app_bootstrap", 20_000);

      expect(captureEvent).not.toHaveBeenCalled();
      expect(window.localStorage.getItem(COOLDOWN_KEY)).toBeNull();
    },
  );

  it("reports an operation once per page load", async () => {
    const { reportSlowOperation } = await loadHelper();

    reportSlowOperation("app_bootstrap", 20_000);
    reportSlowOperation("app_bootstrap", 30_000);

    expect(captureEvent).toHaveBeenCalledTimes(1);
  });

  it("reports at most five operations per page load", async () => {
    const { reportSlowOperation } = await loadHelper();

    for (const operation of [
      "cold_start",
      "first_data_ready",
      "db_open_migrate",
      "cache_hydrate",
      "sync_replay",
      "api_call",
    ] as const) {
      reportSlowOperation(operation, 120_000);
    }

    expect(captureEvent).toHaveBeenCalledTimes(5);
    expect(captureEvent.mock.calls.map(([event]) => event.tags.operation)).not.toContain("api_call");
  });

  it("keeps an operation quiet for a day after it reported, across a relaunch", async () => {
    let helper = await loadHelper();
    helper.reportSlowOperation("app_bootstrap", 20_000);
    expect(captureEvent).toHaveBeenCalledTimes(1);

    vi.setSystemTime(new Date("2026-10-02T11:59:59.000Z"));
    helper = await loadHelper();
    helper.reportSlowOperation("app_bootstrap", 20_000);
    expect(captureEvent).toHaveBeenCalledTimes(1);

    vi.setSystemTime(new Date("2026-10-02T12:00:00.000Z"));
    helper = await loadHelper();
    helper.reportSlowOperation("app_bootstrap", 20_000);
    expect(captureEvent).toHaveBeenCalledTimes(2);
  });

  it("keeps one operation's cooldown from silencing another", async () => {
    let helper = await loadHelper();
    helper.reportSlowOperation("app_bootstrap", 20_000);

    helper = await loadHelper();
    helper.reportSlowOperation("route_chunk_load", 20_000);

    expect(captureEvent).toHaveBeenCalledTimes(2);
  });

  it("does not stay silent for good if the clock was once set forward", async () => {
    window.localStorage.setItem(COOLDOWN_KEY, JSON.stringify({ app_bootstrap: Date.now() + 90 * 86_400_000 }));
    const { reportSlowOperation } = await loadHelper();

    reportSlowOperation("app_bootstrap", 20_000);

    expect(captureEvent).toHaveBeenCalledTimes(1);
  });

  it("treats unreadable stored cooldowns as none", async () => {
    window.localStorage.setItem(COOLDOWN_KEY, "{not json");
    const { reportSlowOperation } = await loadHelper();

    reportSlowOperation("app_bootstrap", 20_000);

    expect(captureEvent).toHaveBeenCalledTimes(1);
  });

  it("still reports when storage refuses the cooldown write", async () => {
    const { reportSlowOperation } = await loadHelper();
    const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("quota", "QuotaExceededError");
    });

    reportSlowOperation("app_bootstrap", 20_000);

    expect(captureEvent).toHaveBeenCalledTimes(1);
    setItem.mockRestore();
  });
});
