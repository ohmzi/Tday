// @vitest-environment jsdom

import type { ErrorEvent } from "@sentry/react";
import { describe, expect, it, vi } from "vitest";
import { applyTestCrashTitle, testCrashMessage } from "@/lib/testCrash";
import { buildWebSentryOptions } from "@/lib/observability/sentryInit";

// TEST-CRASH: the harness is temporary and so is this file — it goes out with the same revert.

/** The shape a `DOMException` trigger reaches Sentry in: one value, no type, and marked synthetic. */
function domExceptionEvent(id: string): ErrorEvent {
  const message = `TEST-CRASH ${id}: somewhere`;
  return {
    event_id: "0".repeat(32),
    timestamp: 1_760_000_000,
    message,
    exception: {
      values: [
        {
          type: undefined,
          value: message,
          mechanism: { type: "auto.browser.browserapierrors.setTimeout", handled: false, synthetic: true },
        },
      ],
    },
  } as unknown as ErrorEvent;
}

/** An error raised the ordinary way: typed, and one entry per throw. */
function typedEvent(id: string, kind: string, extra = true): ErrorEvent {
  const message = `TEST-CRASH ${id}: somewhere`;
  const first = { type: kind, value: message, mechanism: { type: "auto.browser.browserapierrors.setTimeout", handled: false } };
  const wrapper = { type: "Error", value: `${kind}: ${message}` };
  return {
    event_id: "1".repeat(32),
    timestamp: 1_760_000_000,
    message: `${kind}: ${message}`,
    exception: { values: extra ? [first, wrapper] : [first] },
  } as unknown as ErrorEvent;
}

describe("applyTestCrashTitle", () => {
  it("titles a DOMException trigger with its own type and screen", () => {
    const event = applyTestCrashTitle(domExceptionEvent("TC-BUILTIN-DONE"));
    expect(event.exception?.values).toHaveLength(1);
    expect(event.exception?.values?.[0]).toMatchObject({
      type: "InvalidStateError",
      value: testCrashMessage("TC-BUILTIN-DONE"),
    });
  });

  it("keeps the mechanism and the stack of the entry it titles with", () => {
    const event = typedEvent("TC-LIST-SCHED", "NotFoundError");
    event.exception!.values![1]!.stacktrace = { frames: [{ filename: "app.ts", lineno: 1 }] } as never;
    const titled = applyTestCrashTitle(event);
    expect(titled.exception?.values?.[0]?.stacktrace?.frames?.[0]?.filename).toBe("app.ts");
  });

  it("clears the synthetic flag, so Sentry does not title the issue after a frame", () => {
    const titled = applyTestCrashTitle(domExceptionEvent("TC-LIST-ANY"));
    expect(titled.exception?.values?.[0]?.mechanism).toMatchObject({
      type: "auto.browser.browserapierrors.setTimeout",
      handled: false,
      synthetic: false,
    });
  });

  it("gives every trigger an issue of its own", () => {
    const done = applyTestCrashTitle(domExceptionEvent("TC-BUILTIN-DONE"));
    const today = applyTestCrashTitle(domExceptionEvent("TC-BUILTIN-TODAY"));
    expect(done.fingerprint).toEqual(["test-crash", "TC-BUILTIN-DONE"]);
    expect(today.fingerprint).toEqual(["test-crash", "TC-BUILTIN-TODAY"]);
    expect(done.fingerprint).not.toEqual(today.fingerprint);
  });

  it("sets the message Sentry searches on", () => {
    const event = applyTestCrashTitle(domExceptionEvent("TC-NEW-LIST"));
    expect(event.message).toBe("TEST-CRASH TC-NEW-LIST: create list sheet");
  });

  it("leaves a real event completely alone", () => {
    const real = {
      event_id: "1".repeat(32),
      timestamp: 1_760_000_000,
      message: "Cannot read properties of undefined",
      exception: { values: [{ type: "TypeError", value: "Cannot read properties of undefined" }] },
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
  const event = () => domExceptionEvent("TC-TASK-EDIT");
  const env = {
    dsn: "https://key@o1.ingest.example.invalid/2",
    environment: "production",
    appVersion: "0.8.1",
    origin: "https://tday.example.invalid",
  };

  it("sends a trigger event titled and fingerprinted", () => {
    const { beforeSend } = buildWebSentryOptions(env, {
      isGranted: () => true,
      consentAt: () => null,
      eventContext: () => ({ appVersion: "0.8.1", mode: "server", tzOffset: "UTC", localeLang: "en" }),
      makeTransport: () => ({ send: vi.fn(() => Promise.resolve({})), flush: vi.fn(() => Promise.resolve(true)) }),
    } as never);
    const sent = beforeSend?.(event(), {}) as ErrorEvent;
    expect(sent.fingerprint).toEqual(["test-crash", "TC-TASK-EDIT"]);
    expect(sent.exception?.values?.[0]).toMatchObject({ type: "RangeError", value: testCrashMessage("TC-TASK-EDIT") });
  });

  it("still drops a trigger event when consent is off", () => {
    const { beforeSend } = buildWebSentryOptions(env, {
      isGranted: () => false,
      consentAt: () => null,
      eventContext: () => ({ appVersion: "0.8.1", mode: "server", tzOffset: "UTC", localeLang: "en" }),
      makeTransport: () => ({ send: vi.fn(() => Promise.resolve({})), flush: vi.fn(() => Promise.resolve(true)) }),
    } as never);
    expect(beforeSend?.(event(), {})).toBeNull();
  });
});
