// @vitest-environment jsdom

import * as Sentry from "@sentry/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { buildWebSentryOptions } from "@/lib/observability/sentryInit";
import {
  getTelemetryConsentAt,
  isTelemetryGranted,
  setTelemetryConsent,
} from "@/lib/privacy/telemetryConsent";

/**
 * The real SDK, a recording transport underneath it, and the real consent store on top: the whole
 * path an event takes from `captureException` to the wire, with only the network cut out. This is
 * what the mocked-SDK tests in `web-sentry-init.test.ts` cannot show — that an event which makes it
 * out has been scrubbed, and that one which should not has not.
 */

type Transport = ReturnType<typeof Sentry.makeFetchTransport>;

let wire: string[] = [];

function recordingTransport(): Transport {
  return {
    send: (envelope) => {
      wire.push(JSON.stringify(envelope));
      return Promise.resolve({});
    },
    flush: () => Promise.resolve(true),
  } as Transport;
}

function startRealSdk() {
  Sentry.init(
    buildWebSentryOptions(
      {
        dsn: "https://key@o1.ingest.example.invalid/2",
        environment: "test",
        appVersion: "0.8.0",
        origin: window.location.origin,
      },
      {
        isGranted: isTelemetryGranted,
        consentAt: getTelemetryConsentAt,
        eventContext: () => ({
          appVersion: "0.8.0",
          mode: "server",
          tzOffset: "UTC+2",
          localeLang: "en",
        }),
        makeTransport: recordingTransport,
      },
    ),
  );
}

// An event shaped like the SDK's own for an error thrown in the app's bundle. Built by hand
// because a test file's stack frames point at the test file, not at a script this origin served,
// and the origin filter would rightly drop it.
function appError(value: string, scriptOrigin = window.location.origin): Sentry.Event {
  return {
    exception: {
      values: [
        {
          type: "Error",
          value,
          stacktrace: {
            frames: [
              { filename: `${scriptOrigin}/assets/index-abc123.js`, function: "syncReplay", lineno: 1, colno: 1 },
            ],
          },
        },
      ],
    },
  };
}

async function capture(value: string, scriptOrigin?: string) {
  Sentry.captureEvent(appError(value, scriptOrigin));
  await Sentry.flush(1000);
}

beforeEach(() => {
  wire = [];
  window.localStorage.clear();
  window.dispatchEvent(new StorageEvent("storage", { key: null }));
});

afterEach(async () => {
  delete (document as { referrer?: string }).referrer;
  await Sentry.close(0);
  Sentry.getCurrentScope().setClient(undefined);
  Sentry.getIsolationScope().clearBreadcrumbs();
});

describe("the browser's event pipeline", () => {
  it("sends a scrubbed, tagged report once consent is granted", async () => {
    setTelemetryConsent(true);
    startRealSdk();
    // `HttpContext` puts the page you came from into the request headers.
    Object.defineProperty(document, "referrer", {
      value: "https://other.example.org/private/page?x=1",
      configurable: true,
    });
    Sentry.addBreadcrumb({ category: "ui.click", message: "button.delete-task Buy milk" });
    Sentry.addBreadcrumb({ category: "tday", message: "sync.replay", data: { pending: 3 } });

    await capture("Could not load https://tday.my-home.example.net/api/todo for taylor@example.com");

    expect(wire).toHaveLength(1);
    const payload = wire[0];
    expect(payload).toContain("Could not load <url> for <email>");
    expect(payload).toContain('"client":"web"');
    expect(payload).toContain('"tz_offset":"UTC+2"');
    expect(payload).toContain("sync.replay");
    expect(payload).toContain('"filename":"/assets/index-abc123.js"');
    for (const leak of [
      "my-home",
      "taylor",
      "Buy milk",
      "Referer",
      "other.example.org",
      "ip_address",
      window.location.host,
    ]) {
      expect(payload, `payload still contains ${leak}`).not.toContain(leak);
    }
  });

  it("sends nothing while the answer is unanswered or no", async () => {
    startRealSdk();

    await capture("before any answer");
    setTelemetryConsent(false);
    await capture("after a no");

    expect(wire).toHaveLength(0);
  });

  it("stops sending at once when consent is withdrawn, even before the SDK is shut down", async () => {
    setTelemetryConsent(true);
    startRealSdk();
    await capture("while granted");
    expect(wire).toHaveLength(1);

    setTelemetryConsent(false);
    await capture("after the withdrawal");

    expect(wire).toHaveLength(1);
  });

  it("ignores errors thrown by scripts this page did not serve", async () => {
    setTelemetryConsent(true);
    startRealSdk();

    await capture("from somewhere else", "https://cdn.other.example.invalid");
    await capture("from this page");

    expect(wire).toHaveLength(1);
    expect(wire[0]).toContain("from this page");
  });

  it("installs the allow-listed integrations and nothing the SDK might default to", () => {
    setTelemetryConsent(true);
    startRealSdk();

    expect(Sentry.getClient()?.getIntegrationNames().sort()).toEqual(
      [
        "BrowserApiErrors",
        "Breadcrumbs",
        "Dedupe",
        "EventFilters",
        "FunctionToString",
        "GlobalHandlers",
        "HttpContext",
        "LinkedErrors",
      ].sort(),
    );
  });

  it("sends no client report about the events it dropped", async () => {
    setTelemetryConsent(true);
    startRealSdk();
    await capture("Failed to fetch");
    expect(wire).toHaveLength(0);

    // Client reports go out when the page is hidden.
    Object.defineProperty(document, "visibilityState", { value: "hidden", configurable: true });
    document.dispatchEvent(new Event("visibilitychange"));
    await Sentry.flush(1000);
    Object.defineProperty(document, "visibilityState", { value: "visible", configurable: true });

    expect(wire).toHaveLength(0);
  });

  it("does not deliver an event stamped before consent was granted", async () => {
    setTelemetryConsent(true);
    startRealSdk();
    const grantedAt = getTelemetryConsentAt() ?? 0;

    Sentry.captureEvent({ message: "from before the grant", timestamp: grantedAt / 1000 - 60 });
    await Sentry.flush(1000);
    Sentry.captureEvent({ message: "from after the grant", timestamp: grantedAt / 1000 + 60 });
    await Sentry.flush(1000);

    expect(wire).toHaveLength(1);
    expect(wire[0]).toContain("from after the grant");
  });
});
