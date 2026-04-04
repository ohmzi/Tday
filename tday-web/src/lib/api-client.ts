import * as Sentry from "@sentry/react";

type fetchOptions = {
  method: string;
  headers?: object;
  body?: string | FormData;
};

type ApiErrorPayload = {
  code?: string;
  message?: string;
  reason?: string;
};

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const fetchApi = async (url: string, options: fetchOptions) => {
  const res = await fetch(url, {
    method: options.method,
    headers: options.headers as HeadersInit | undefined,
    body: options.body,
    cache: "no-store",
    credentials: "same-origin",
  });

  const isJson = (res.headers.get("content-type") ?? "").includes(
    "application/json",
  );

  if (!res.ok) {
    const payload = isJson ? ((await res.json()) as ApiErrorPayload) : null;
    const message =
      payload?.message || `a ${res.statusText || "request"} error occurred`;
    Sentry.addBreadcrumb({
      category: "api",
      message: `${options.method} ${url} — ${res.status}`,
      level: "error",
      data: { status: res.status, code: payload?.code },
    });
    throw new ApiError(message, res.status, payload?.code ?? payload?.reason);
  }

  if (!isJson || res.status === 204) {
    return null;
  }

  return await res.json();
};

export const api = {
  GET({ url }: { url: string }) {
    return fetchApi(url, { method: "GET" });
  },
  PATCH({
    url,
    headers,
    body,
  }: {
    url: string;
    headers?: fetchOptions["headers"];
    body?: fetchOptions["body"];
  }) {
    return fetchApi(url, { method: "PATCH", headers, body });
  },
  DELETE({
    url,
    headers,
    body,
  }: {
    url: string;
    headers?: fetchOptions["headers"];
    body?: fetchOptions["body"];
  }) {
    return fetchApi(url, { method: "DELETE", headers, body });
  },
  POST({
    url,
    headers,
    body,
  }: {
    url: string;
    headers?: fetchOptions["headers"];
    body: fetchOptions["body"];
  }) {
    return fetchApi(url, { method: "POST", headers, body });
  },
};
