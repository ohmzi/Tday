/**
 * Failure raised by a local-mode handler. `api-client` converts it into the same
 * `ApiError` the network path throws, so callers can't tell the two apart.
 *
 * It lives in its own module to keep `api-client` → `localApi` a one-way import.
 */
export class LocalApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string,
    public field?: string,
  ) {
    super(message);
    this.name = "LocalApiError";
  }
}

export function localBadRequest(message: string, field?: string): LocalApiError {
  return new LocalApiError(message, 400, "bad_request", field);
}

export function localNotFound(message: string): LocalApiError {
  return new LocalApiError(message, 404, "not_found");
}
