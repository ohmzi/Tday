import * as Sentry from "@sentry/react";

type SentryInitOptions = NonNullable<Parameters<typeof Sentry.init>[0]>;
type SentryBeforeBreadcrumb = NonNullable<SentryInitOptions["beforeBreadcrumb"]>;
type SentryBreadcrumb = Parameters<SentryBeforeBreadcrumb>[0];
type SentryBeforeSend = NonNullable<SentryInitOptions["beforeSend"]>;
type SentryErrorEvent = Parameters<SentryBeforeSend>[0];
type SentryBeforeSendTransaction = NonNullable<SentryInitOptions["beforeSendTransaction"]>;
type SentryTransactionEvent = Parameters<SentryBeforeSendTransaction>[0];

const STATIC_SEGMENTS = new Set([
  "api",
  "app",
  "auth",
  "callback",
  "credentials",
  "credentials-key",
  "csrf",
  "logout",
  "register",
  "session",
  "todo",
  "todos",
  "today",
  "overdue",
  "scheduled",
  "all",
  "priority",
  "instance",
  "complete",
  "uncomplete",
  "prioritize",
  "reorder",
  "summary",
  "nlp",
  "list",
  "floater",
  "floater-list",
  "floaterList",
  "completedTodo",
  "completedFloater",
  "completed",
  "calendar",
  "settings",
  "admin",
  "version",
  "privacy",
  "terms",
  "blogs",
  "page",
  "tday",
  "app-settings",
  "preferences",
  "user",
  "profile",
  "change-password",
  "timezone",
  "mobile",
  "probe",
]);

const SENSITIVE_HEADERS = new Set([
  "authorization",
  "cookie",
  "set-cookie",
  "x-csrf-token",
]);

const SENSITIVE_LABEL_PATTERN =
  /(https?:\/\/|wss?:\/\/|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|bearer\s+|token=|password=|session=|cookie=|csrf)/i;

const SENSITIVE_DATA_KEY_PATTERN =
  /(authorization|cookie|csrf|token|password|session|secret|email|username|body|payload|header)/i;

function parseTelemetryUrl(raw: string): URL | null {
  try {
    return new URL(raw, window.location.origin);
  } catch {
    try {
      return new URL(raw, "https://tday.local");
    } catch {
      return null;
    }
  }
}

export function sanitizeTelemetryPath(raw: string): string {
  const parsed = parseTelemetryUrl(raw);
  const pathname = parsed?.pathname ?? raw.split(/[?#]/, 1)[0] ?? "/";
  const segments = pathname.split("/").filter(Boolean);
  if (segments.length === 0) return "/";

  return `/${segments.map(sanitizeSegment).join("/")}`;
}

export function sanitizeTelemetryUrl(raw: string): string {
  return sanitizeTelemetryPath(raw);
}

export function routeTemplate(method: string, url: string): string {
  return `${method.toUpperCase()} ${sanitizeTelemetryUrl(url)}`;
}

export function readTraceSampleRate(
  rawValue: string | undefined,
  fallback: number,
): number {
  if (!rawValue) return fallback;
  const parsed = Number(rawValue);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(1, Math.max(0, parsed));
}

export function addApiErrorBreadcrumb({
  method,
  url,
  status,
  code,
}: {
  method: string;
  url: string;
  status: number;
  code?: string;
}) {
  Sentry.addBreadcrumb({
    category: "api",
    message: routeTemplate(method, url),
    level: "error",
    data: {
      route: sanitizeTelemetryUrl(url),
      status,
      code: sanitizeLabel(code),
    },
  });
}

export function addDiagnosticBreadcrumb(
  operation: string,
  data: Record<string, unknown> = {},
) {
  Sentry.addBreadcrumb({
    category: "tday",
    message: sanitizeLabel(operation),
    level: "info",
    data: sanitizeData(data),
  });
}

export function captureUiException(
  error: unknown,
  operation: string,
  data: Record<string, unknown> = {},
) {
  Sentry.captureException(error, {
    tags: {
      "tday.operation": sanitizeLabel(operation),
    },
    extra: sanitizeData(data),
  });
}

export function scrubSentryEvent(event: SentryErrorEvent): SentryErrorEvent {
  if (event.user) {
    delete event.user.ip_address;
    delete event.user.email;
    delete event.user.username;
  }

  if (event.request) {
    if (event.request.url) {
      event.request.url = sanitizeTelemetryUrl(event.request.url);
    }
    delete event.request.query_string;
    if (event.request.headers) {
      for (const headerName of Object.keys(event.request.headers)) {
        if (SENSITIVE_HEADERS.has(headerName.toLowerCase())) {
          delete event.request.headers[headerName];
        }
      }
    }
    delete event.request.cookies;
  }

  if (event.breadcrumbs) {
    event.breadcrumbs = event.breadcrumbs
      .map((breadcrumb) => scrubSentryBreadcrumb(breadcrumb))
      .filter((breadcrumb): breadcrumb is SentryBreadcrumb => breadcrumb != null);
  }

  return event;
}

export function scrubSentryTransaction(
  event: SentryTransactionEvent,
): SentryTransactionEvent {
  if (event.transaction) {
    event.transaction = sanitizeTransactionName(event.transaction);
  }
  return event;
}

export function scrubSentryBreadcrumb(
  breadcrumb: SentryBreadcrumb,
): SentryBreadcrumb | null {
  if (breadcrumb.category === "console" || breadcrumb.category?.startsWith("ui.")) {
    return null;
  }

  const sanitized: SentryBreadcrumb = { ...breadcrumb };
  if (sanitized.message) {
    sanitized.message = sanitizeBreadcrumbMessage(sanitized.message);
  }
  if (sanitized.data) {
    sanitized.data = sanitizeData(sanitized.data as Record<string, unknown>);
  }
  return sanitized;
}

function sanitizeSegment(segment: string): string {
  const decoded = decodeURIComponentSafe(segment).trim();
  if (!decoded) return ":value";
  if (/^:[A-Za-z][A-Za-z0-9_]*$/.test(decoded)) return decoded;
  if (STATIC_SEGMENTS.has(decoded)) return decoded;
  if (/^[a-z]{2}(-[A-Z]{2})?$/.test(decoded)) return ":locale";
  if (decoded.includes("@") || decoded.includes("=")) return ":redacted";
  if (decoded.length > 24) return ":id";
  if (/\d/.test(decoded)) return ":id";
  if (/[-_:]/.test(decoded)) return ":id";
  return ":value";
}

function decodeURIComponentSafe(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

export function sanitizeTelemetryLabel(value: unknown): string {
  if (typeof value !== "string" || value.trim() === "") return "unknown";
  const raw = value.trim();
  if (SENSITIVE_LABEL_PATTERN.test(raw)) return "redacted";
  if (raw.length > 24 && /\d/.test(raw) && /^[A-Za-z0-9_.:-]+$/.test(raw)) {
    return "id";
  }
  const normalized = raw.replace(/[^A-Za-z0-9_.:-]/g, "_");
  return normalized.slice(0, 64) || "unknown";
}

function sanitizeData(data: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(data).map(([key, value]) => {
      if (SENSITIVE_DATA_KEY_PATTERN.test(key)) {
        return [key, "redacted"];
      }
      if (["route", "path", "url", "href", "from", "to"].includes(key.toLowerCase()) && typeof value === "string") {
        return [key, sanitizeTelemetryUrl(value)];
      }
      if (typeof value === "number" || typeof value === "boolean") {
        return [key, value];
      }
      return [key, sanitizeTelemetryLabel(value)];
    }),
  );
}

function sanitizeLabel(value: unknown): string {
  return sanitizeTelemetryLabel(value);
}

function sanitizeBreadcrumbMessage(message: string): string {
  if (message.includes("->")) {
    return message
      .split(/\s*->\s*/)
      .map((part) => sanitizeTelemetryUrl(part))
      .join(" -> ");
  }

  const routeMatch = message.match(/^([A-Z]+)\s+(.+)$/);
  if (routeMatch) {
    return routeTemplate(routeMatch[1], routeMatch[2]);
  }

  if (message.includes("/") || message.includes("?") || message.includes("@")) {
    return sanitizeTelemetryUrl(message);
  }

  return sanitizeTelemetryLabel(message);
}

function sanitizeTransactionName(transaction: string): string {
  const routeMatch = transaction.match(/^([A-Z]+)\s+(.+)$/);
  if (routeMatch) {
    return routeTemplate(routeMatch[1], routeMatch[2]);
  }
  if (transaction.includes("/") || transaction.includes("?")) {
    return sanitizeTelemetryPath(transaction);
  }
  return sanitizeTelemetryLabel(transaction);
}
