import * as Sentry from "@sentry/react";
import i18n from "@/i18n";
import { isLocalMode } from "@/lib/local/appMode";
import {
  clientSentryDsn,
  getTelemetryConsentAt,
  isCrashReportingConfigured,
  isTelemetryGranted,
  subscribeToTelemetryConsent,
} from "@/lib/privacy/telemetryConsent";
import { SENTRY_PII_HEADER_SNIPPETS } from "./sentry";
import { applyTestCrashTitle } from "@/lib/testCrash"; // TEST-CRASH
import {
  formatUtcOffset,
  localeLangTag,
  scrubWebBreadcrumb,
  scrubWebEvent,
  type WebEventContext,
} from "./webScrub";

/**
 * The one place the browser's Sentry SDK is configured and started, and the only place it is
 * allowed to be: nothing initialises it at module load any more. It runs when — and only while —
 * this browser has said yes (`telemetryConsent`), and only in a build that carries a DSN.
 *
 * "Off" has to mean off, so it is enforced in layers rather than in one:
 *
 * - Before a yes the SDK does not exist: no patched globals, no handlers, nothing buffered. An
 *   error during first run is lost, by design, because buffering it would be collecting it.
 * - A no closes the client, empties the scopes and unbinds it, so the helpers in `sentry.ts` go
 *   back to being no-ops.
 * - `beforeSend`, `beforeBreadcrumb` and the transport each ask the consent store again, because
 *   the SDK sends some things around `beforeSend` (its own internal-error events) and a closed
 *   client can still be holding an envelope.
 * - `predatesConsent` drops an event stamped before the grant, so nothing from the time the
 *   switch was off can ride in on a later yes.
 */

type BrowserOptions = NonNullable<Parameters<typeof Sentry.init>[0]>;
type SentryTransport = ReturnType<typeof Sentry.makeFetchTransport>;
type TransportFactory = (options: Parameters<typeof Sentry.makeFetchTransport>[0]) => SentryTransport;
type RootErrorInfo = Parameters<ReturnType<typeof Sentry.reactErrorHandler>>[1];

export type WebSentryEnvironment = {
  dsn: string;
  environment: string;
  appVersion: string;
  /** This page's origin. Only errors thrown by scripts served from it are reported. */
  origin: string;
};

export type WebSentryDeps = {
  isGranted: () => boolean;
  consentAt: () => number | null;
  eventContext: () => WebEventContext;
  makeTransport: TransportFactory;
};

// How long `close()` may wait for events already in flight. Short on purpose: by the time it is
// called the answer is already "no", so the gated transport drops whatever is left and there is
// nothing worth waiting for.
const CLOSE_TIMEOUT_MS = 100;

// Browser and network noise rather than bugs in T'Day: a fetch cut off by a navigation or a
// dropped connection, and the layout-loop notice browsers print for a slow resize handler.
const IGNORED_ERRORS = [
  "ResizeObserver loop limit exceeded",
  "Failed to fetch",
  "Load failed",
  "NetworkError when attempting to fetch resource",
  "AbortError",
];

// Scripts injected by a browser extension are not ours to fix.
const DENIED_URLS = [/^chrome-extension:\/\//i, /^moz-extension:\/\//i, /^safari-extension:\/\//i];

/** Wraps a transport so that it sends nothing while `isOpen()` says no. */
export function makeGatedTransport(
  makeTransport: TransportFactory,
  isOpen: () => boolean,
): TransportFactory {
  return (options) => {
    const transport = makeTransport(options);
    return {
      // Asked per envelope, not once at start, so a revoke takes effect on the very next send.
      // A dropped envelope answers like a delivered one, so the SDK has nothing to retry.
      send: (envelope) => (isOpen() ? transport.send(envelope) : Promise.resolve({})),
      flush: (timeout) => transport.flush(timeout),
    };
  };
}

function predatesConsent(event: Sentry.ErrorEvent, consentAt: number | null): boolean {
  return (
    consentAt !== null &&
    typeof event.timestamp === "number" &&
    event.timestamp * 1000 < consentAt
  );
}

function readWebEventContext(): WebEventContext {
  return {
    appVersion: __APP_VERSION__,
    mode: isLocalMode() ? "local" : "server",
    tzOffset: formatUtcOffset(-new Date().getTimezoneOffset()),
    localeLang: localeLangTag(i18n.language || navigator.language || ""),
  };
}

const DEFAULT_DEPS: WebSentryDeps = {
  isGranted: isTelemetryGranted,
  consentAt: getTelemetryConsentAt,
  eventContext: readWebEventContext,
  makeTransport: Sentry.makeFetchTransport,
};

export function buildWebSentryOptions(
  env: WebSentryEnvironment,
  deps: WebSentryDeps = DEFAULT_DEPS,
): BrowserOptions {
  return {
    dsn: env.dsn,
    environment: env.environment,
    release: `tday-web@${env.appVersion}`,
    // Sentry 11 dropped `sendDefaultPii` and now collects user info, cookies, headers,
    // query strings and bodies unless told otherwise. This is the privacy-first posture
    // `sendDefaultPii: false` used to give, with cookies and query strings off outright.
    dataCollection: {
      userInfo: false,
      cookies: false,
      httpHeaders: {
        request: { deny: SENTRY_PII_HEADER_SNIPPETS },
        response: { deny: SENTRY_PII_HEADER_SNIPPETS },
      },
      httpBodies: [],
      urlQueryParams: false,
      genAI: { inputs: false, outputs: false },
      databaseQueryData: false,
    },
    // Failures only. The default list also installs session pings, a console hook and the
    // locale/time-zone context, and the old config added sampled page-load traces on top; none
    // of that is a failure report. Every integration below is named, so a new SDK default can
    // never join them silently. (`breadcrumbsIntegration` has no `console` switch in 11: console
    // capture became its own `Console` integration, which is simply not listed.)
    defaultIntegrations: false,
    integrations: [
      Sentry.eventFiltersIntegration(),
      Sentry.functionToStringIntegration(),
      Sentry.browserApiErrorsIntegration(),
      // `sentry: false` stops the SDK echoing each sent error's message back as a breadcrumb.
      Sentry.breadcrumbsIntegration({ dom: false, sentry: false }),
      Sentry.globalHandlersIntegration(),
      Sentry.linkedErrorsIntegration(),
      Sentry.dedupeIntegration(),
      // Reads the browser and OS from the User-Agent; `scrubWebEvent` drops its Referer.
      Sentry.httpContextIntegration(),
    ],
    // Zero, not unset: explicit, so it reads as a decision. No tracing integration is installed,
    // so there is nothing to sample, and `static` pins the lifecycle the 11.x docs disagree about.
    tracesSampleRate: 0,
    traceLifecycle: "static",
    tracePropagationTargets: [],
    sendClientReports: false,
    replaysSessionSampleRate: 0,
    replaysOnErrorSampleRate: 0,
    allowUrls: [env.origin],
    denyUrls: DENIED_URLS,
    ignoreErrors: IGNORED_ERRORS,
    transport: makeGatedTransport(deps.makeTransport, deps.isGranted),
    // `makeFetchTransport` sends `referrerPolicy: "strict-origin"`, which puts the page's origin, the
    // self-hoster's own address, in `Referer`; its `fetchOptions` are spread last, so this wins and
    // the gated transport above passes it on untouched. `Origin` is a different matter: a browser
    // adds it to every cross-origin POST and nothing here can remove it, so Sentry still sees which
    // site a web report came from.
    transportOptions: { fetchOptions: { referrerPolicy: "no-referrer" } },
    beforeBreadcrumb: (breadcrumb) => (deps.isGranted() ? scrubWebBreadcrumb(breadcrumb) : null),
    beforeSend: (event) => {
      if (!deps.isGranted() || predatesConsent(event, deps.consentAt())) return null;
      try {
        // TEST-CRASH: the harness names and fingerprints its own events. This import and this call
        // go with the rest of the harness.
        return applyTestCrashTitle(scrubWebEvent(event, deps.eventContext()));
      } catch {
        // An event that could not be scrubbed is not sent. The SDK would otherwise report the
        // failure as an event of its own, and those skip `beforeSend`.
        return null;
      }
    },
  };
}

let started = false;

export function startSentry(): void {
  if (started || !isTelemetryGranted() || !isCrashReportingConfigured()) return;
  Sentry.init(
    buildWebSentryOptions({
      dsn: clientSentryDsn(),
      environment: import.meta.env.MODE,
      appVersion: __APP_VERSION__,
      origin: window.location.origin,
    }),
  );
  started = true;
}

export function stopSentry(): void {
  if (!started) return;
  started = false;
  void Sentry.close(CLOSE_TIMEOUT_MS);
  // `Scope.clear()` is gone in 11; breadcrumbs and the user are the only things that could have
  // built up. Breadcrumbs sit on the isolation scope and would otherwise attach to the first
  // event after a later yes.
  for (const scope of [Sentry.getGlobalScope(), Sentry.getIsolationScope(), Sentry.getCurrentScope()]) {
    scope.clearBreadcrumbs();
    scope.setUser(null);
  }
  // With no client bound, the helpers in `sentry.ts` and every instrumentation hook the SDK left
  // behind go back to doing nothing.
  Sentry.getCurrentScope().setClient(undefined);
}

function syncSentryWithConsent(): void {
  if (isTelemetryGranted()) startSentry();
  else stopSentry();
}

/**
 * Called once from `main.tsx`, before React renders. Starts the SDK if this browser already said
 * yes, then follows every later change: a grant starts it, a withdrawal — here or in another tab —
 * stops it, with no reload either way. Returns the function that unsubscribes.
 */
export function initSentryIfConsented(): () => void {
  syncSentryWithConsent();
  return subscribeToTelemetryConsent(syncSentryWithConsent);
}

const captureReactError = Sentry.reactErrorHandler();

function reportRootError(error: unknown, errorInfo: RootErrorInfo): void {
  // Asked here as well as in `beforeSend`: the SDK's handler attaches the component stack to the
  // error object itself before it looks for a client, and an answer of "no" means untouched.
  if (isTelemetryGranted()) captureReactError(error, errorInfo);
  // Giving React a handler replaces its default report, which is what put the error in the
  // console. Keep that, so a render that fails above the app's own ErrorBoundary is not silent.
  console.error(error);
}

/**
 * `createRoot` options for errors React reports outside any error boundary — and for the ones it
 * recovers from — so they reach Sentry with their component stack. Errors the app's own
 * `ErrorBoundary` catches are reported there, so no `onCaughtError` here.
 */
export const reactRootErrorHandlers = {
  onUncaughtError: reportRootError,
  onRecoverableError: reportRootError,
};
