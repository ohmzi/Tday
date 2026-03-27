type fetchOptions = {
  method: string;
  headers?: object;
  body?: string | FormData;
};

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
    const payload = isJson ? ((await res.json()) as { message?: string }) : null;
    const message = payload?.message || `a ${res.statusText} error ocurred`;
    throw new Error(message);
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
