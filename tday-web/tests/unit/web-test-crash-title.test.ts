// @vitest-environment jsdom

import type { ErrorEvent } from "@sentry/react";
import { describe, expect, it, vi } from "vitest";
import { applyTestCrashTitle, testCrashMessage, type TestCrashId } from "@/lib/testCrash";
import { buildWebSentryOptions } from "@/lib/observability/sentryInit";

// TEST-CRASH: the harness is temporary and so is this file — it goes out with the same revert.

const DONE_ID: TestCrashId = "TC-BUILTIN-DONE";
const TODAY_ID: TestCrashId = "TC-BUILTIN-TODAY";
const ANY_ID: TestCrashId = "TC-LIST-ANY";
const SCHEDULED_LIST_ID: TestCrashId = "TC-LIST-SCHED";
const NEW_LIST_ID: TestCrashId = "TC-NEW-LIST";
const EDIT_ID: TestCrashId = "TC-TASK-EDIT";

const CRASH_TAG = "test-crash";
const SET_TIMEOUT_MECHANISM = "auto.browser.browserapierrors.setTimeout";
const APP_VERSION = "0.8.1";
const FRAME_FILE = "app.ts";
const CLEAN_MESSAGE = "Cannot read properties of undefined";

const FIXED_TIMESTAMP = 1_760_000_000;
const HTTP_DSN = "https://key@o1.ingest.example.invalid/2";

function messageFor(id: TestCrashId): string {
  return `TEST-CRASH ${id}: somewhere`;
}

/** The shape a `DOMException` trigger reaches Sentry in: one value, no type, and marked synthetic. */
function domExceptionEvent(id: TestCrashId, frames?: { filename: string; lineno: number }[]): ErrorEvent {
  return {
    event_id: "0".repeat(32),
    timestamp: FIXED_TIMESTAMP,
    message: messageFor(id),
    exception: {
      values: [
        {
          type: undefined,
          value: messageFor(id),
          stacktrace: frames ? { frames } : undefined,
          mechanism: { type: SET_TIMEOUT_MECHANISM, handled: false, synthetic: true },
        },
      ],
    },
  } as unknown as ErrorEvent;
}

/** An error raised the ordinary way: typed, and one entry per throw. */
function typedEvent(id: TestCrashId, kind: string, frames?: { filename: string; lineno: number }[]): ErrorEvent {
  const typed = { type: kind, value: messageFor(id) };
  const wrapper = { type: "Error", value: `${kind}: ${messageFor(id)}`, stacktrace: frames ? { frames } : undefined };
  return {
    event_id: "1".repeat(32),
    timestamp: FIXED_TIMESTAMP,
    message: `${kind}: ${messageFor(id)}`,
    exception: { values: [typed, wrapper] },
  } as unknown as ErrorEvent;
}

describe("applyTestCrashTitle", () => {
  it("titles a DOMException trigger with its own type and screen", () => {
    const event = applyTestCrashTitle(domExceptionEvent(DONE_ID));
    expect(event.exception?.values).toHaveLength(1);
    expect(event.exception?.values?.[0]).toMatchObject({
      type: "InvalidStateError",
      value: testCrashMessage(DONE_ID),
    });
  });

  it("keeps the mechanism and the stack of the entry it titles with", () => {
    const titled = applyTestCrashTitle(
      typedEvent(SCHEDULED_LIST_ID, "NotFoundError", [{ filename: FRAME_FILE, lineno: 1 }]),
    );
    expect(titled.exception?.values?.[0]?.stacktrace?.frames?.[0]?.filename).toBe(FRAME_FILE);
  });

  it("clears the synthetic flag, so Sentry does not title the issue after a frame", () => {
    const titled = applyTestCrashTitle(domExceptionEvent(ANY_ID));
    expect(titled.exception?.values?.[0]?.mechanism).toMatchObject({
      type: SET_TIMEOUT_MECHANISM,
      handled: false,
      synthetic: false,
    });
  });

  it("gives every trigger an issue of its own", () => {
    const done = applyTestCrashTitle(domExceptionEvent(DONE_ID));
    const today = applyTestCrashTitle(domExceptionEvent(TODAY_ID));
    expect(done.fingerprint).toEqual([CRASH_TAG, DONE_ID]);
    expect(today.fingerprint).toEqual([CRASH_TAG, TODAY_ID]);
    expect(done.fingerprint).not.toEqual(today.fingerprint);
  });

  it("sets the message Sentry searches on", () => {
    const event = applyTestCrashTitle(domExceptionEvent(NEW_LIST_ID));
    expect(event.message).toBe(testCrashMessage(NEW_LIST_ID));
  });

  it("leaves a real event completely alone", () => {
    const real = {
      event_id: "1".repeat(32),
      timestamp: FIXED_TIMESTAMP,
      message: CLEAN_MESSAGE,
      exception: { values: [{ type: "TypeError", value: CLEAN_MESSAGE }] },
    } as ErrorEvent;
    const sent = applyTestCrashTitle(real);
    expect(sent).toBe(real);
    expect(sent.fingerprint).toBeUndefined();
    expect(sent.exception?.values?.[0]?.type).toBe("TypeError");
  });

  it("ignores a message that only looks like a trigger id", () => {
    const fake = { event_id: "2".repeat(32), timestamp: 1, message: "TEST-CRASH TC-NOT-A-CRASH: nope" } as ErrorEvent;
    expect(applyTestCrashTitle(fake).fingerprint).toBeUndefined();
  });
});

describe("beforeSend with the harness wired in", () => {
  const env = {
    dsn: HTTP_DSN,
    environment: "production",
    appVersion: APP_VERSION,
    origin: "https://tday.example.invalid",
  };

  /** The deps `buildWebSentryOptions` needs, with consent as the one thing the cases vary. */
  function deps(isGranted: boolean) {
    return {
      isGranted: () => isGranted,
      consentAt: () => null,
      eventContext: () => ({ appVersion: APP_VERSION, mode: "server", tzOffset: "UTC", localeLang: "en" }),
      makeTransport: () => ({ send: vi.fn(() => Promise.resolve({})), flush: vi.fn(() => Promise.resolve(true)) }),
    } as never;
  }

  it("sends a trigger event titled and fingerprinted", () => {
    const { beforeSend } = buildWebSentryOptions(env, deps(true));
    const sent = beforeSend?.(domExceptionEvent(EDIT_ID), {}) as ErrorEvent;
    expect(sent.fingerprint).toEqual([CRASH_TAG, EDIT_ID]);
    expect(sent.exception?.values?.[0]).toMatchObject({ type: "RangeError", value: testCrashMessage(EDIT_ID) });
  });

  it("still drops a trigger event when consent is off", () => {
    const { beforeSend } = buildWebSentryOptions(env, deps(false));
    expect(beforeSend?.(domExceptionEvent(EDIT_ID), {})).toBeNull();
  });
});
