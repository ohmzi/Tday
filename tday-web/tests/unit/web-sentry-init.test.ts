// @vitest-environment jsdom

import * as Sentry from "@sentry/react";
import type { ErrorEvent } from "@sentry/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  buildWebSentryOptions,
  initSentryIfConsented,
  makeGatedTransport,
  startSentry,
  stopSentry,
  type WebSentryDeps,
} from "@/lib/observability/sentryInit";
import { setInstanceAnswerForTests, stubInstanceTelemetryFetch } from "./support/instanceTelemetry";

// Everything the lifecycle tests assert about "was the SDK started" is read off these two spies.
// The real functions stay behind them for the scope helpers, so the teardown checks run against
// the SDK's actual scopes.
vi.mock("@sentry/react", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@sentry/react")>();
  return {
    ...actual,
    init: vi.fn(),
    close: vi.fn(() => Promise.resolve(true)),
  };
});

const DSN = "https://key@o1.ingest.example.invalid/2";

const ENV = {
  dsn: DSN,
  environment: "production",
  appVersion: "0.8.0",
  origin: "https://tday.example.invalid",
};

type Transport = ReturnType<typeof Sentry.makeFetchTransport>;

function fakeDeps(overrides: Partial<WebSentryDeps> = {}): WebSentryDeps {
  return {
    isGranted: () => true,
    consentAt: () => null,
    eventContext: () => ({
      appVersion: "0.8.0",
      mode: "server",
      tzOffset: "UTC",
      localeLang: "en",
    }),
    makeTransport: () => ({ send: vi.fn(() => Promise.resolve({})), flush: vi.fn(() => Promise.resolve(true)) }) as Transport,
    ...overrides,
  };
}

describe("buildWebSentryOptions", () => {
  it("names the release and environment, and carries the DSN it was given", () => {
    const options = buildWebSentryOptions(ENV, fakeDeps());
    expect(options.dsn).toBe(DSN);
    expect(options.environment).toBe("production");
    expect(options.release).toBe("tday-web@0.8.0");
  });

  it("sends reports with no Referer, whatever page the visitor is on", () => {
    const options = buildWebSentryOptions(ENV, fakeDeps());
    expect(options.transportOptions?.fetchOptions?.referrerPolicy).toBe("no-referrer");
  });

  it("hands the transport factory those fetch options through the gate", () => {
    const makeTransport = vi.fn(fakeDeps().makeTransport);
    const options = buildWebSentryOptions(ENV, fakeDeps({ makeTransport }));
    const transportOptions = { url: DSN, ...options.transportOptions } as Parameters<
      NonNullable<typeof options.transport>
    >[0];
    options.transport!(transportOptions);
    expect(makeTransport).toHaveBeenCalledWith(
      expect.objectContaining({ fetchOptions: { referrerPolicy: "no-referrer" } }),
    );
  });

  it("installs exactly the allow-list of integrations, and none of the defaults", () => {
    const options = buildWebSentryOptions(ENV, fakeDeps());
    expect(options.defaultIntegrations).toBe(false);

    const names = (options.integrations as { name: string }[]).map((integration) => integration.name);
    expect(names.sort()).toEqual(
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
    // Named, because each of these is what the old config shipped: session pings, sampled
    // page-load traces, the locale and time zone, and console output.
    for (const banned of ["BrowserSession", "BrowserTracing", "CultureContext", "Console", "Replay"]) {
      expect(names).not.toContain(banned);
    }
  });

  it("turns tracing, sessions, client reports and trace propagation off", () => {
    const options = buildWebSentryOptions(ENV, fakeDeps());
    expect(options.tracesSampleRate).toBe(0);
    expect(options.tracesSampler).toBeUndefined();
    expect(options.traceLifecycle).toBe("static");
    expect(options.sendClientReports).toBe(false);
    expect(options.tracePropagationTargets).toEqual([]);
    expect(options.replaysSessionSampleRate).toBe(0);
    expect(options.replaysOnErrorSampleRate).toBe(0);
  });

  it("collects no user info, cookies, query strings or bodies", () => {
    const { dataCollection } = buildWebSentryOptions(ENV, fakeDeps());
    expect(dataCollection).toMatchObject({
      userInfo: false,
      cookies: false,
      httpBodies: [],
      urlQueryParams: false,
      databaseQueryData: false,
    });
  });

  it("only accepts errors from this page's own scripts, never an extension's", () => {
    const options = buildWebSentryOptions(ENV, fakeDeps());
    expect(options.allowUrls).toEqual(["https://tday.example.invalid"]);
    for (const extension of [
      "chrome-extension://abc/content.js",
      "moz-extension://abc/content.js",
      "safari-extension://abc/content.js",
    ]) {
      expect((options.denyUrls ?? []).some((pattern) => new RegExp(pattern).test(extension))).toBe(true);
    }
  });

  it("ignores the browser's own network noise", () => {
    const { ignoreErrors } = buildWebSentryOptions(ENV, fakeDeps());
    for (const noise of [
      "TypeError: Failed to fetch",
      "TypeError: Load failed",
      "NetworkError when attempting to fetch resource.",
      "ResizeObserver loop limit exceeded",
    ]) {
      expect((ignoreErrors ?? []).some((pattern) => noise.includes(String(pattern)))).toBe(true);
    }
  });

  describe("beforeSend", () => {
    const event = () =>
      ({
        event_id: "0".repeat(32),
        timestamp: 1_760_000_000,
        message: "boom for taylor@example.com",
        user: { id: "u1", email: "taylor@example.com" },
      }) as ErrorEvent;

    it("drops every event while consent is not granted", () => {
      const { beforeSend } = buildWebSentryOptions(ENV, fakeDeps({ isGranted: () => false }));
      expect(beforeSend?.(event(), {})).toBeNull();
    });

    it("scrubs and tags an event while consent is granted", () => {
      const { beforeSend } = buildWebSentryOptions(ENV, fakeDeps());
      const sent = beforeSend?.(event(), {}) as ErrorEvent;
      expect(sent.user).toBeUndefined();
      expect(sent.message).toBe("boom for <email>");
      expect(sent.tags).toMatchObject({ client: "web", app_version: "0.8.0", mode: "server" });
    });

    it("drops an event that happened before consent was granted", () => {
      const consentAt = 1_760_000_100_000;
      const { beforeSend } = buildWebSentryOptions(ENV, fakeDeps({ consentAt: () => consentAt }));
      const before = { ...event(), timestamp: consentAt / 1000 - 1 } as ErrorEvent;
      const after = { ...event(), timestamp: consentAt / 1000 + 1 } as ErrorEvent;
      expect(beforeSend?.(before, {})).toBeNull();
      expect(beforeSend?.(after, {})).not.toBeNull();
    });

    it("drops the event, rather than sending it unscrubbed, if scrubbing fails", () => {
      const { beforeSend } = buildWebSentryOptions(
        ENV,
        fakeDeps({
          eventContext: () => {
            throw new Error("context unavailable");
          },
        }),
      );
      expect(beforeSend?.(event(), {})).toBeNull();
    });
  });

  describe("beforeBreadcrumb", () => {
    it("drops every breadcrumb while consent is not granted", () => {
      const { beforeBreadcrumb } = buildWebSentryOptions(ENV, fakeDeps({ isGranted: () => false }));
      expect(beforeBreadcrumb?.({ category: "tday", message: "sync.replay" })).toBeNull();
    });

    it("keeps a structural breadcrumb, and drops the rest, while consent is granted", () => {
      const { beforeBreadcrumb } = buildWebSentryOptions(ENV, fakeDeps());
      expect(beforeBreadcrumb?.({ category: "tday", message: "sync.replay" })).not.toBeNull();
      expect(beforeBreadcrumb?.({ category: "ui.click", message: "Buy milk" })).toBeNull();
    });
  });
});

describe("makeGatedTransport", () => {
  const envelope = [{}, []] as unknown as Parameters<Transport["send"]>[0];

  it("hands envelopes to the real transport while open", async () => {
    const inner = { send: vi.fn(() => Promise.resolve({ statusCode: 200 })), flush: vi.fn() };
    const transport = makeGatedTransport(() => inner as unknown as Transport, () => true)({} as never);

    await transport.send(envelope);

    expect(inner.send).toHaveBeenCalledTimes(1);
  });

  it("drops envelopes while closed, and answers as if they were sent", async () => {
    const inner = { send: vi.fn(), flush: vi.fn() };
    const transport = makeGatedTransport(() => inner as unknown as Transport, () => false)({} as never);

    await expect(transport.send(envelope)).resolves.toEqual({});

    expect(inner.send).not.toHaveBeenCalled();
  });

  it("asks at send time, so a grant or a revoke between envelopes takes effect", async () => {
    const inner = { send: vi.fn(() => Promise.resolve({})), flush: vi.fn() };
    let open = false;
    const transport = makeGatedTransport(() => inner as unknown as Transport, () => open)({} as never);

    await transport.send(envelope);
    open = true;
    await transport.send(envelope);
    open = false;
    await transport.send(envelope);

    expect(inner.send).toHaveBeenCalledTimes(1);
  });

  it("passes flush straight through", async () => {
    const inner = { send: vi.fn(), flush: vi.fn(() => Promise.resolve(true)) };
    const transport = makeGatedTransport(() => inner as unknown as Transport, () => false)({} as never);

    await transport.flush(500);

    expect(inner.flush).toHaveBeenCalledWith(500);
  });
});

describe("initSentryIfConsented", () => {
  let unsubscribe: (() => void) | undefined;

  beforeEach(() => {
    vi.stubEnv("VITE_SENTRY_DSN", DSN);
    window.localStorage.clear();
    setInstanceAnswerForTests(false);
    vi.mocked(Sentry.init).mockClear();
    vi.mocked(Sentry.close).mockClear();
  });

  afterEach(() => {
    unsubscribe?.();
    unsubscribe = undefined;
    stopSentry();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it("asks the server and starts nothing at all until the answer arrives", async () => {
    let answer: (value: unknown) => void = () => {};
    const pending = new Promise((resolve) => {
      answer = resolve;
    });
    const fetchMock = vi.fn(() => pending);
    vi.stubGlobal("fetch", fetchMock);

    unsubscribe = initSentryIfConsented();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/instance/telemetry",
      expect.objectContaining({ credentials: "same-origin", cache: "no-store" }),
    );
    expect(Sentry.init).not.toHaveBeenCalled();

    answer({ ok: true, json: () => Promise.resolve({ enabled: true, updatedAt: null }) });
    await vi.waitFor(() => expect(Sentry.init).toHaveBeenCalledTimes(1));
    expect(vi.mocked(Sentry.init).mock.calls[0][0]).toMatchObject({ dsn: DSN });
  });

  it("starts nothing when the server answers that reports are off", async () => {
    stubInstanceTelemetryFetch({ enabled: false, updatedAt: "2026-09-01T00:00:00.000Z" });

    unsubscribe = initSentryIfConsented();
    await vi.waitFor(() => expect(globalThis.fetch).toHaveBeenCalled());

    expect(Sentry.init).not.toHaveBeenCalled();
  });

  it.each([
    ["a failed request", () => Promise.reject(new Error("offline"))],
    ["an error status", () => Promise.resolve({ ok: false, json: () => Promise.resolve({}) })],
    ["a body that is not an answer", () => Promise.resolve({ ok: true, json: () => Promise.resolve({}) })],
  ])("keeps reports off when the read ends in %s", async (_label, response) => {
    const fetchMock = vi.fn(response);
    vi.stubGlobal("fetch", fetchMock);

    unsubscribe = initSentryIfConsented();
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled());
    await Promise.resolve();

    expect(Sentry.init).not.toHaveBeenCalled();
  });

  it("ignores a stale per-browser answer from an older build", async () => {
    window.localStorage.setItem("tday.telemetry.consent", "granted");
    window.localStorage.setItem("tday.telemetry.consentAt", "1759320000000");
    stubInstanceTelemetryFetch({ enabled: false, updatedAt: null });

    unsubscribe = initSentryIfConsented();
    await vi.waitFor(() => expect(globalThis.fetch).toHaveBeenCalled());

    expect(Sentry.init).not.toHaveBeenCalled();
  });

  it("starts nothing when the build carries no DSN, even after a yes", () => {
    vi.stubEnv("VITE_SENTRY_DSN", "");
    setInstanceAnswerForTests(true);

    unsubscribe = initSentryIfConsented();
    startSentry();

    expect(Sentry.init).not.toHaveBeenCalled();
  });

  it("starts when the admin turns reports on after launch", () => {
    unsubscribe = initSentryIfConsented();
    expect(Sentry.init).not.toHaveBeenCalled();

    setInstanceAnswerForTests(true);

    expect(Sentry.init).toHaveBeenCalledTimes(1);
  });

  it("does not initialise a second time while it is already running", () => {
    setInstanceAnswerForTests(true);
    unsubscribe = initSentryIfConsented();

    startSentry();
    startSentry();
    setInstanceAnswerForTests(true);

    expect(Sentry.init).toHaveBeenCalledTimes(1);
  });

  it("refuses to start without the server's yes even when asked directly", () => {
    startSentry();

    expect(Sentry.init).not.toHaveBeenCalled();
  });

  it("closes the SDK and empties the scopes the moment the admin turns reports off", () => {
    setInstanceAnswerForTests(true);
    unsubscribe = initSentryIfConsented();
    Sentry.getIsolationScope().addBreadcrumb({ category: "tday", message: "sync.replay" });
    Sentry.getCurrentScope().addBreadcrumb({ category: "tday", message: "sync.replay" });
    Sentry.getGlobalScope().setUser({ id: "u1" });

    setInstanceAnswerForTests(false);

    expect(Sentry.close).toHaveBeenCalledTimes(1);
    expect(Sentry.getIsolationScope().getLastBreadcrumb()).toBeUndefined();
    expect(Sentry.getCurrentScope().getLastBreadcrumb()).toBeUndefined();
    expect(Sentry.getGlobalScope().getUser()?.id).toBeUndefined();
  });

  it("does not flush on the way out, so nothing queued is delivered after a no", () => {
    setInstanceAnswerForTests(true);
    unsubscribe = initSentryIfConsented();

    setInstanceAnswerForTests(false);

    // A flush would be asked to wait for queued events. Closing with a short fuse leaves the
    // transport gate, already closed, to drop whatever is left.
    const [timeout] = vi.mocked(Sentry.close).mock.calls[0];
    expect(timeout).toBeLessThanOrEqual(250);
  });

  it("starts a fresh client when reports are turned back on", () => {
    setInstanceAnswerForTests(true);
    unsubscribe = initSentryIfConsented();
    setInstanceAnswerForTests(false);

    setInstanceAnswerForTests(true);

    expect(Sentry.init).toHaveBeenCalledTimes(2);
    expect(Sentry.close).toHaveBeenCalledTimes(1);
  });

  it("does nothing extra when told no and was never running", () => {
    unsubscribe = initSentryIfConsented();

    setInstanceAnswerForTests(false);

    expect(Sentry.close).not.toHaveBeenCalled();
  });
});
