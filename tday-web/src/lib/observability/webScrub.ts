import type { Breadcrumb, ErrorEvent } from "@sentry/react";
import { scrubSentryBreadcrumb, scrubSentryEvent } from "./sentry";

/**
 * The privacy pass every browser event goes through on its way out, kept pure so a golden test can
 * feed it an event full of identifiers and grep the result.
 *
 * `scrubSentryEvent` in `sentry.ts` already strips user fields, query strings, cookies and secret
 * headers and reduces the URL to a route template. This composes with it and covers what it never
 * did, found by reading what the 11.x SDK actually attaches: the `Referer` header, the self-hoster's
 * host inside every stack frame and debug-id image, free text in exception messages, and the
 * locale and time-zone context. What stays is the allow-list: release, browser and OS (from the
 * User-Agent), the error type and frames, structural breadcrumbs, and five tags.
 */

/** What the event is tagged with. Read at send time, so a switch to Local Mode shows up at once. */
export type WebEventContext = {
  appVersion: string;
  mode: "local" | "server";
  /** `UTC+2`, `UTC-5`, `UTC+5:30`, or `UTC`. Never the IANA zone, which names a region. */
  tzOffset: string;
  /** Two lower-case letters, never the full locale. */
  localeLang: string;
};

const MAX_MESSAGE_LENGTH = 300;
// What the patterns below are ever run over. Several are quadratic on a long unbroken run, and this
// runs on the thread that is reporting a failure, so a message holding a large blob is cut first.
// Far more than the 300 characters that are kept, so the cut still lands after the redaction.
const MAX_SCANNED_LENGTH = 2000;

// Categories a browser breadcrumb may carry. `fetch`/`xhr` and `navigation` come from the SDK's
// breadcrumbs integration (data scrubbed to route templates by `scrubSentryBreadcrumb`); `api` and
// `tday` are this app's own structural helpers. Everything else — console, `ui.*`, the SDK's own
// `sentry.event` echo of an earlier error message — is dropped.
const BREADCRUMB_CATEGORIES = new Set(["api", "fetch", "xhr", "navigation", "tday"]);

// A real top-level domain, because that is what separates `tday.my-home.net` from `window.location`
// or `t.map`: the second is an expression in an error message and the first is a host. Common
// self-hosting suffixes (`.lan`, `.local`, `.internal`, `.localdomain`, `.home.arpa`; `.ts.net` is
// a `.net`) are in on purpose.
const TOP_LEVEL_DOMAINS =
  "com|net|org|io|dev|app|co|xyz|info|cloud|site|online|us|uk|de|fr|jp|cn|ru|eu|ca|au|br|page|it|ai|me|es|nl|se|ch|at|nz|tech|link|lan|local|internal|home|localdomain|arpa";

const JDBC_PATTERN = /\bjdbc:[^\s"'<>)\]]+/gi;
const URL_PATTERN = /\b[a-z][a-z0-9+.-]*:\/\/[^\s"'<>)\]]+/gi;
const EMAIL_PATTERN = /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/gi;
const DATABASE_KEY_PATTERN = /Key \([^)]*\)=\([^)]*\)/g;
// Chrome quotes the start of the body it failed to parse, and a body that is JSON has quotes of
// its own, so the snippet runs up to the last quote before the closing words, not the next one.
const JSON_SNIPPET_PATTERN = /(,\s*)"[^\n]*"(?:\.\.\.)?(\s+is not valid JSON)/g;
// Safari names the offending token instead: `JSON Parse error: Unrecognized token 'Buy'`.
const JSON_TOKEN_PATTERN = /(Unrecognized token )'[^'\n]*'/g;
const IPV4_PATTERN = /\b(?:\d{1,3}\.){3}\d{1,3}\b/g;
const IPV6_PATTERN =
  /(?<![\w:])(?:(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}|(?:[0-9a-f]{1,4}:){1,7}:(?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4}){0,6})?|::[0-9a-f]{1,4}(?::[0-9a-f]{1,4}){0,6})(?![\w:])/gi;
// A host written without a scheme can still be followed by a path and a query, and those can hold
// anything, so a match takes them with it.
const ADDRESS_TAIL = "(?:[/?#][^\\s\"'<>)\\]]*)?";
const HOST_PORT_PATTERN = new RegExp(
  `\\b((?:localhost|(?:[a-z0-9-]+\\.)+[a-z][a-z0-9-]*)):\\d{2,5}\\b${ADDRESS_TAIL}`,
  "gi",
);
const SOURCE_FILE_PATTERN = /\.(?:[cm]?[jt]sx?|css|html|json|map)$/i;
const HOST_PATTERN = new RegExp(
  `\\b(?:[a-z0-9-]+\\.)+(?:${TOP_LEVEL_DOMAINS})\\b${ADDRESS_TAIL}`,
  "gi",
);
const UUID_PATTERN = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;
const LONG_ID_PATTERN = /\b(?=[a-z0-9]*\d)[a-z0-9]{20,}\b/gi;
const LONG_DIGITS_PATTERN = /\b\d{6,}\b/g;

/**
 * Error text is the one free-form string in an event, so it gets the strictest treatment: URLs,
 * hosts, addresses, emails, database fragments and identifiers are replaced with a placeholder,
 * and the rest is cut to a few hundred characters. The exception type is kept by the caller, which
 * is what groups the issue; the message is only there to help read it.
 */
export function redactDiagnosticText(text: string): string {
  const redacted = text
    .slice(0, MAX_SCANNED_LENGTH)
    .replace(JSON_SNIPPET_PATTERN, '$1"<redacted>"$2')
    .replace(JSON_TOKEN_PATTERN, "$1'<redacted>'")
    .replace(JDBC_PATTERN, "<jdbc>")
    .replace(URL_PATTERN, "<url>")
    .replace(EMAIL_PATTERN, "<email>")
    .replace(DATABASE_KEY_PATTERN, "Key (<redacted>)=(<redacted>)")
    .replace(IPV4_PATTERN, "<ip>")
    .replace(IPV6_PATTERN, "<ip>")
    .replace(HOST_PORT_PATTERN, (match, host: string) =>
      SOURCE_FILE_PATTERN.test(host) ? match : "<host>",
    )
    .replace(HOST_PATTERN, "<host>")
    .replace(UUID_PATTERN, "<id>")
    .replace(LONG_ID_PATTERN, "<id>")
    .replace(LONG_DIGITS_PATTERN, "<n>");
  return redacted.length > MAX_MESSAGE_LENGTH
    ? `${redacted.slice(0, MAX_MESSAGE_LENGTH - 1)}…`
    : redacted;
}

/** `UTC+2`, `UTC-5`, `UTC+5:30`, or plain `UTC`, from minutes east of UTC. */
export function formatUtcOffset(offsetMinutes: number): string {
  if (offsetMinutes === 0) return "UTC";
  const sign = offsetMinutes > 0 ? "+" : "-";
  const absolute = Math.abs(offsetMinutes);
  const hours = Math.floor(absolute / 60);
  const minutes = absolute % 60;
  return minutes === 0
    ? `UTC${sign}${hours}`
    : `UTC${sign}${hours}:${String(minutes).padStart(2, "0")}`;
}

/** The language half of a locale — `de` from `de-DE` — or `und` when there is no such thing. */
export function localeLangTag(locale: string): string {
  const language = locale.slice(0, 2).toLowerCase();
  return /^[a-z]{2}$/.test(language) ? language : "und";
}

export function scrubWebBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb | null {
  if (!BREADCRUMB_CATEGORIES.has(breadcrumb.category ?? "")) return null;
  return scrubSentryBreadcrumb(breadcrumb);
}

// A script's address names the server it came from, which for a self-hosted app is the operator's
// own domain. The path is all a source map needs: frames and debug-id images are rewritten the same
// way, so they still match each other. Anything that is not a web address (`<anonymous>`, a bare
// path) is left as it was, and `blob:`/`data:` collapse to their scheme because their payload is
// an address or a body.
function toLocationPath(location: string): string {
  let parsed: URL;
  try {
    parsed = new URL(location);
  } catch {
    return location;
  }
  return /^(?:https?|wss?|chrome-extension|moz-extension|safari-extension):$/.test(parsed.protocol)
    ? parsed.pathname
    : parsed.protocol;
}

function scrubRequest(event: ErrorEvent): void {
  if (!event.request) return;
  const headers = event.request.headers ?? {};
  const userAgent = Object.entries(headers).find(([name]) => name.toLowerCase() === "user-agent")?.[1];
  // Rebuilt rather than pruned: the only things a request may still carry are the route template
  // and the User-Agent, which is where Sentry reads the browser and OS from. Everything else
  // `HttpContext` attaches — `Referer` above all, which can hold any page the visitor came from —
  // goes.
  event.request = {
    ...(event.request.url ? { url: event.request.url } : {}),
    ...(userAgent ? { headers: { "User-Agent": userAgent } } : {}),
  };
}

function scrubExceptions(event: ErrorEvent): void {
  for (const exception of event.exception?.values ?? []) {
    if (exception.value) exception.value = redactDiagnosticText(exception.value);
    for (const frame of exception.stacktrace?.frames ?? []) {
      if (frame.filename) frame.filename = toLocationPath(frame.filename);
      if (frame.abs_path) frame.abs_path = toLocationPath(frame.abs_path);
    }
  }
}

function scrubDebugMeta(event: ErrorEvent): void {
  for (const image of event.debug_meta?.images ?? []) {
    if ("code_file" in image && image.code_file) image.code_file = toLocationPath(image.code_file);
  }
}

function scrubContexts(event: ErrorEvent): void {
  if (!event.contexts) return;
  // `culture` is the SDK's locale and IANA time zone, which the offset and language tags replace.
  // It is not installed, and stays out if a later SDK default brings it back.
  delete event.contexts.culture;
  const device = event.contexts.device;
  if (device) {
    delete device.name;
    delete device.timezone;
    delete device.locale;
  }
}

export function scrubWebEvent(event: ErrorEvent, context: WebEventContext): ErrorEvent {
  scrubSentryEvent(event);
  delete event.user;
  scrubRequest(event);
  scrubExceptions(event);
  scrubDebugMeta(event);
  scrubContexts(event);

  if (event.message) event.message = redactDiagnosticText(event.message);
  if (event.logentry?.message) event.logentry.message = redactDiagnosticText(event.logentry.message);
  if (event.breadcrumbs) {
    event.breadcrumbs = event.breadcrumbs
      .map(scrubWebBreadcrumb)
      .filter((breadcrumb): breadcrumb is Breadcrumb => breadcrumb != null);
  }

  event.tags = {
    ...event.tags,
    client: "web",
    app_version: context.appVersion,
    mode: context.mode,
    tz_offset: context.tzOffset,
    locale_lang: context.localeLang,
  };
  return event;
}
