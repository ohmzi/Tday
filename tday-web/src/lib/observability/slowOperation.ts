import * as Sentry from "@sentry/react";
import { isTelemetryGranted } from "@/lib/privacy/instanceTelemetry";

/**
 * A slow operation as a failure report. Crash reporting here is failures-only — no traces, no
 * timings from healthy sessions — so "this took far too long" is sent as an event of its own, once
 * an operation has crossed the line a user would call broken, and never otherwise.
 *
 * Android and iOS send the same event (`SlowOperation`, `reportSlowOperation`), with the same
 * operation ids and thresholds, so one query in Sentry covers all three. Some ids have no caller on
 * the web (`widget_refresh`, `reminder_reschedule`); they are here so that the vocabulary is one
 * table everywhere rather than three that drift.
 *
 * Kept out of `sentry.ts` on purpose: five component tests mock that module with a single export,
 * and anything a component imports from it has to exist in those mocks.
 */

export const SLOW_OPERATION_THRESHOLDS_MS = {
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
} as const;

export type SlowOperation = keyof typeof SLOW_OPERATION_THRESHOLDS_MS;

const MAX_REPORTS_PER_PAGE_LOAD = 5;
const COOLDOWN_MS = 24 * 60 * 60 * 1000;

// One JSON object of operation -> epoch ms of its last report. It is not on the sign-out
// preserve list, so signing out re-arms every cooldown; that costs at most one more report per
// operation, which is not worth a key of its own in that list.
const COOLDOWN_STORAGE_KEY = "tday.slowOperation.cooldowns";

const reportedThisLoad = new Set<SlowOperation>();

export function durationBucket(durationMs: number): string {
  if (durationMs < 5_000) return "<5s";
  if (durationMs < 10_000) return "5-10s";
  if (durationMs < 30_000) return "10-30s";
  if (durationMs < 60_000) return "30-60s";
  return ">60s";
}

function readCooldowns(): Partial<Record<SlowOperation, number>> {
  try {
    const parsed: unknown = JSON.parse(window.localStorage.getItem(COOLDOWN_STORAGE_KEY) ?? "{}");
    return parsed !== null && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function writeCooldowns(cooldowns: Partial<Record<SlowOperation, number>>): void {
  try {
    window.localStorage.setItem(COOLDOWN_STORAGE_KEY, JSON.stringify(cooldowns));
  } catch {
    // Ignore storage write failures; the once-per-load limit still holds without it.
  }
}

// A stored time in the future — a clock that was once set forward — must not silence an
// operation until the real date catches up, so only a past time inside the window counts.
function isCoolingDown(lastReportedAt: number | undefined, now: number): boolean {
  if (typeof lastReportedAt !== "number") return false;
  const elapsed = now - lastReportedAt;
  return elapsed >= 0 && elapsed < COOLDOWN_MS;
}

/**
 * Reports `operation` if it took at least its threshold, at most once per operation per page load,
 * five per page load, and once per operation per day across loads. Does nothing without consent.
 */
export function reportSlowOperation(operation: SlowOperation, durationMs: number): void {
  const thresholdMs = SLOW_OPERATION_THRESHOLDS_MS[operation];
  if (durationMs < thresholdMs || !isTelemetryGranted()) return;
  if (reportedThisLoad.has(operation) || reportedThisLoad.size >= MAX_REPORTS_PER_PAGE_LOAD) return;

  const now = Date.now();
  const cooldowns = readCooldowns();
  if (isCoolingDown(cooldowns[operation], now)) return;

  reportedThisLoad.add(operation);
  writeCooldowns({ ...cooldowns, [operation]: now });
  Sentry.captureEvent({
    message: "slow_operation",
    level: "warning",
    // One issue per operation, however the duration varies.
    fingerprint: ["slow_operation", operation],
    tags: { operation, duration_bucket: durationBucket(durationMs) },
    extra: { duration_ms: Math.round(durationMs), threshold_ms: thresholdMs },
  });
}
