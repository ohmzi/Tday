import { describe, expect, it } from "vitest";
import {
  routeTemplate,
  sanitizeTelemetryLabel,
  sanitizeTelemetryPath,
  sanitizeTelemetryUrl,
  scrubSentryBreadcrumb,
} from "@/lib/observability/sentry";

describe("observability sanitizers", () => {
  it("removes query strings and replaces ids in API paths", () => {
    expect(
      sanitizeTelemetryUrl("/api/list/list-123?token=secret&email=a@example.com"),
    ).toBe("/api/list/:id");
    expect(routeTemplate("patch", "/api/todo/cjld2cjxh0000qzrmn831i7rn")).toBe(
      "PATCH /api/todo/:id",
    );
  });

  it("keeps structural app routes without list names or ids", () => {
    expect(sanitizeTelemetryPath("/en/app/list/list-123")).toBe(
      "/:locale/app/list/:id",
    );
    expect(sanitizeTelemetryPath("/:locale/app/list/:id")).toBe(
      "/:locale/app/list/:id",
    );
    expect(sanitizeTelemetryPath("/en/app/calendar")).toBe(
      "/:locale/app/calendar",
    );
  });

  it("redacts accidental labels containing PII or token-shaped ids", () => {
    expect(sanitizeTelemetryLabel("alex@example.com")).toBe("redacted");
    expect(sanitizeTelemetryLabel("https://tday.local/api/todo/123")).toBe(
      "redacted",
    );
    expect(sanitizeTelemetryLabel("cjld2cjxh0000qzrmn831i7rn")).toBe("id");
  });

  it("drops console and DOM breadcrumbs and sanitizes automatic URL crumbs", () => {
    expect(scrubSentryBreadcrumb({ category: "console", message: "secret" })).toBeNull();
    expect(scrubSentryBreadcrumb({ category: "ui.click", message: "Task title" })).toBeNull();
    expect(
      scrubSentryBreadcrumb({
        category: "fetch",
        message: "GET /api/list/list-123?token=secret",
        data: {
          url: "/api/list/list-123?token=secret",
          status_code: 500,
        },
      }),
    ).toMatchObject({
      message: "GET /api/list/:id",
      data: {
        url: "/api/list/:id",
        status_code: 500,
      },
    });
  });
});
