// @vitest-environment jsdom

import * as Sentry from "@sentry/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  initSentryIfConsented,
  reactRootErrorHandlers,
  stopSentry,
} from "@/lib/observability/sentryInit";
import { setTelemetryConsent } from "@/lib/privacy/telemetryConsent";

/**
 * `startSentry` and `stopSentry` run for real here, against the real SDK, with only the network
 * cut out: `makeFetchTransport` is the one thing replaced, by a recorder. That is what the
 * mocked-`init` tests in `web-sentry-init.test.ts` cannot show: what the page looks like before a
 * yes, and that a withdrawal followed by a new yes leaves a working client rather than a dead one.
 */

const wire = vi.hoisted(() => [] as string[]);

vi.mock("@sentry/react", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@sentry/react")>();
  return {
    ...actual,
    makeFetchTransport: () => ({
      send: (envelope: unknown) => {
        wire.push(JSON.stringify(envelope));
        return Promise.resolve({});
      },
      flush: () => Promise.resolve(true),
    }),
  };
});

const DSN = "https://key@o1.ingest.example.invalid/2";

let unsubscribe: (() => void) | undefined;

// Built by hand because a test file's own frames point at the test file, not at a script this
// origin served, and the origin filter would rightly drop them.
function appStack(message: string): string {
  return `Error: ${message}\n    at syncReplay (${window.location.origin}/assets/index-abc123.js:1:1)`;
}

function appError(message: string): Error {
  const error = new Error(message);
  error.stack = appStack(message);
  return error;
}

async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 20));
}

beforeEach(() => {
  wire.length = 0;
  vi.stubEnv("VITE_SENTRY_DSN", DSN);
  window.localStorage.clear();
  window.dispatchEvent(new StorageEvent("storage", { key: null }));
});

afterEach(() => {
  unsubscribe?.();
  unsubscribe = undefined;
  stopSentry();
  vi.unstubAllEnvs();
});

// The page-level proof comes first in this file on purpose: instrumentation, once installed, is
// never removed from the page, and it must be seen not to be there before any test installs it.
describe("a browser that has not said yes", () => {
  it("leaves the page untouched: no client, no patched fetch, no error handler", async () => {
    const fetchBefore = globalThis.fetch;
    const onerrorBefore = window.onerror;

    unsubscribe = initSentryIfConsented();
    Sentry.captureException(appError("before any answer"));
    await settle();

    expect(Sentry.getClient()).toBeUndefined();
    expect(globalThis.fetch).toBe(fetchBefore);
    expect(window.onerror).toBe(onerrorBefore);
    expect(wire).toHaveLength(0);
  });

  it("stays that way for a browser that said no", async () => {
    setTelemetryConsent(false);
    const fetchBefore = globalThis.fetch;

    unsubscribe = initSentryIfConsented();
    await settle();

    expect(Sentry.getClient()).toBeUndefined();
    expect(globalThis.fetch).toBe(fetchBefore);
  });
});

describe("following the answer without a reload", () => {
  it("starts a client on a yes and reports a failure from this page, tagged and scrubbed", async () => {
    unsubscribe = initSentryIfConsented();
    setTelemetryConsent(true);

    Sentry.captureException(appError("Could not load https://tday.my-home.example.net/api/todo"));
    await settle();

    expect(Sentry.getClient()).toBeDefined();
    expect(wire).toHaveLength(1);
    expect(wire[0]).toContain("Could not load <url>");
    expect(wire[0]).toContain('"client":"web"');
    expect(wire[0]).toMatch(/"tz_offset":"UTC(?:[+-]\d{1,2}(?::\d{2})?)?"/);
    expect(wire[0]).toMatch(/"locale_lang":"[a-z]{2,3}"/);
    expect(wire[0]).not.toContain("my-home");
  });

  it("unbinds the client on a no, so nothing captured afterwards is even queued", async () => {
    unsubscribe = initSentryIfConsented();
    setTelemetryConsent(true);

    setTelemetryConsent(false);
    Sentry.captureException(appError("after the withdrawal"));
    await settle();

    expect(Sentry.getClient()).toBeUndefined();
    expect(wire).toHaveLength(0);
  });

  it("works again after a no and a new yes, through the global handlers as well", async () => {
    // Listening for `error` is what tells the test runner this one is expected and not a crash.
    const expected = (event: Event) => event.preventDefault();
    window.addEventListener("error", expected);
    unsubscribe = initSentryIfConsented();
    setTelemetryConsent(true);
    const first = Sentry.getClient();
    setTelemetryConsent(false);

    setTelemetryConsent(true);
    const second = Sentry.getClient();
    Sentry.captureException(appError("after the second yes"));
    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "Uncaught Error: thrown by the page",
        error: appError("thrown by the page"),
      }),
    );
    await settle();
    window.removeEventListener("error", expected);

    expect(second).toBeDefined();
    expect(second).not.toBe(first);
    expect(wire.join("\n")).toContain("after the second yes");
    expect(wire.join("\n")).toContain("thrown by the page");
  });

  it("does not deliver, after a new yes, anything that happened while it was no", async () => {
    unsubscribe = initSentryIfConsented();
    setTelemetryConsent(true);
    setTelemetryConsent(false);
    Sentry.captureException(appError("while it was off"));

    setTelemetryConsent(true);
    await settle();

    expect(wire).toHaveLength(0);
  });
});

describe("errors React reports outside any boundary", () => {
  const componentStack = `\n    at Page (${window.location.origin}/assets/index-abc123.js:9:9)`;

  it.each(["onUncaughtError", "onRecoverableError"] as const)(
    "%s reaches Sentry once consent is granted, and still logs to the console",
    async (handler) => {
      const log = vi.spyOn(console, "error").mockImplementation(() => {});
      unsubscribe = initSentryIfConsented();
      setTelemetryConsent(true);

      reactRootErrorHandlers[handler](appError("render failed"), { componentStack });
      await settle();

      expect(wire.join("\n")).toContain("render failed");
      expect(log).toHaveBeenCalledTimes(1);
      log.mockRestore();
    },
  );

  it.each(["onUncaughtError", "onRecoverableError"] as const)(
    "%s hands nothing to Sentry, and leaves the error as it was, while consent is not granted",
    async (handler) => {
      const log = vi.spyOn(console, "error").mockImplementation(() => {});
      unsubscribe = initSentryIfConsented();
      const error = appError("render failed");

      reactRootErrorHandlers[handler](error, { componentStack });
      await settle();

      expect(wire).toHaveLength(0);
      // The SDK's own handler would have hung a `cause` carrying the component stack on it.
      expect(error.cause).toBeUndefined();
      expect(log).toHaveBeenCalledTimes(1);
      log.mockRestore();
    },
  );
});
