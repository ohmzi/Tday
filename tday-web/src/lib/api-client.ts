import { addApiErrorBreadcrumb } from "@/lib/observability/sentry";
import { notifySessionExpired } from "@/lib/auth/sessionExpiry";
import { isLocalMode } from "@/lib/local/appMode";
import { handleLocalRequest } from "@/lib/local/localApi";
import { LocalApiError } from "@/lib/local/localError";
import { LocalVaultError } from "@/lib/local/localCrypto";

type fetchOptions = {
  method: string;
  headers?: object;
  body?: string | FormData;
  signal?: AbortSignal;
};

type ApiErrorPayload = {
  code?: string | number;
  field?: string;
  message?: string;
  reason?: string;
  retryAfterSeconds?: number;
};

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string,
    public field?: string,
    public retryAfterSeconds?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * Local Mode answers `/api/*` from browser storage instead of the network, so
 * every feature hook keeps using this client unchanged. Failures are rethrown as
 * `ApiError` to keep both paths indistinguishable to callers.
 */
const localApi = (url: string, options: fetchOptions) => {
  try {
    const data = handleLocalRequest({
      method: options.method,
      url,
      body: options.body,
    });
    return data ?? null;
  } catch (error) {
    if (error instanceof LocalApiError) {
      addApiErrorBreadcrumb({
        method: options.method,
        url,
        status: error.status,
        code: error.code,
      });
      throw new ApiError(error.message, error.status, error.code, error.field);
    }
    if (error instanceof LocalVaultError) {
      // A read that raced the passphrase gate (or a relock). Surfaced as a plain
      // 423 so callers show an error instead of a blank screen.
      throw new ApiError(error.message, 423, `local_vault_${error.code}`);
    }
    throw error;
  }
};

const fetchApi = async (url: string, options: fetchOptions) => {
  if (isLocalMode() && url.startsWith("/api/")) {
    return localApi(url, options);
  }

  const res = await fetch(url, {
    method: options.method,
    headers: options.headers as HeadersInit | undefined,
    body: options.body,
    signal: options.signal,
    cache: "no-store",
    credentials: "same-origin",
  });

  const isJson = (res.headers.get("content-type") ?? "").includes(
    "application/json",
  );

  if (!res.ok) {
    const payload = isJson ? ((await res.json()) as ApiErrorPayload) : null;
    const errorCode =
      payload?.reason ??
      (typeof payload?.code === "string" ? payload.code : undefined);
    const message =
      payload?.message || `a ${res.statusText || "request"} error occurred`;
    addApiErrorBreadcrumb({
      method: options.method,
      url,
      status: res.status,
      code: errorCode,
    });
    // A confirmed 401 on any non-auth request means the session is no longer
    // valid. Signal the AuthProvider to expire the session and redirect to
    // login. Auth endpoints (session probe, login, logout) handle their own
    // 401s and must not self-trigger a global expiry.
    if (res.status === 401 && !url.startsWith("/api/auth/")) {
      notifySessionExpired();
    }
    throw new ApiError(
      message,
      res.status,
      errorCode,
      payload?.field,
      payload?.retryAfterSeconds,
    );
  }

  if (!isJson || res.status === 204) {
    return null;
  }

  return await res.json();
};

export const api = {
  GET({ url, signal }: { url: string; signal?: AbortSignal }) {
    return fetchApi(url, { method: "GET", signal });
  },
  PATCH({
    url,
    headers,
    body,
    signal,
  }: {
    url: string;
    headers?: fetchOptions["headers"];
    body?: fetchOptions["body"];
    signal?: fetchOptions["signal"];
  }) {
    return fetchApi(url, { method: "PATCH", headers, body, signal });
  },
  DELETE({
    url,
    headers,
    body,
    signal,
  }: {
    url: string;
    headers?: fetchOptions["headers"];
    body?: fetchOptions["body"];
    signal?: fetchOptions["signal"];
  }) {
    return fetchApi(url, { method: "DELETE", headers, body, signal });
  },
  POST({
    url,
    headers,
    body,
    signal,
  }: {
    url: string;
    headers?: fetchOptions["headers"];
    body: fetchOptions["body"];
    signal?: fetchOptions["signal"];
  }) {
    return fetchApi(url, { method: "POST", headers, body, signal });
  },
};
