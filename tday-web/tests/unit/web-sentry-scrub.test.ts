// @vitest-environment jsdom

import type { ErrorEvent } from "@sentry/react";
import { describe, expect, it } from "vitest";
import {
  formatUtcOffset,
  localeLangTag,
  redactDiagnosticText,
  scrubWebBreadcrumb,
  scrubWebEvent,
  type WebEventContext,
} from "@/lib/observability/webScrub";

const CONTEXT: WebEventContext = {
  appVersion: "0.8.0",
  mode: "server",
  tzOffset: "UTC+2",
  localeLang: "de",
};

const LIST_ID = "9f1c2e84-1b7a-4a43-9d51-0c1a77d2b6a1";

describe("formatUtcOffset", () => {
  it.each([
    [0, "UTC"],
    [120, "UTC+2"],
    [-300, "UTC-5"],
    [330, "UTC+5:30"],
    [345, "UTC+5:45"],
    [-210, "UTC-3:30"],
    [-570, "UTC-9:30"],
    [840, "UTC+14"],
  ])("renders %i minutes east of UTC as %s", (minutes, expected) => {
    expect(formatUtcOffset(minutes)).toBe(expected);
  });
});

describe("localeLangTag", () => {
  it("keeps only the lower-case language", () => {
    expect(localeLangTag("de-DE")).toBe("de");
    expect(localeLangTag("ZH")).toBe("zh");
    expect(localeLangTag("pt_BR")).toBe("pt");
  });

  it("says undetermined rather than inventing one", () => {
    expect(localeLangTag("")).toBe("und");
    expect(localeLangTag("1")).toBe("und");
  });
});

describe("redactDiagnosticText", () => {
  it("redacts URLs, including the host a self-hoster picked", () => {
    const text = redactDiagnosticText(
      "Failed to fetch https://tday.my-home.example.net/api/todo/123?token=abc and wss://tday.my-home.example.net/ws",
    );
    expect(text).not.toContain("my-home");
    expect(text).not.toContain("token=abc");
    expect(text).toContain("<url>");
  });

  it("redacts bare hosts with a port, and dotted hosts with a real top-level domain", () => {
    expect(redactDiagnosticText("connect ECONNREFUSED localhost:8080")).toBe(
      "connect ECONNREFUSED <host>",
    );
    expect(redactDiagnosticText("could not reach tday.my-home.example.net")).toBe(
      "could not reach <host>",
    );
    expect(redactDiagnosticText("nas.lan refused")).toBe("<host> refused");
  });

  it("takes the path and query of a scheme-less host with it", () => {
    expect(redactDiagnosticText("GET tday.my-home.example.net/api/list?title=Buy+milk failed")).toBe(
      "GET <host> failed",
    );
    expect(redactDiagnosticText("GET localhost:5173/en/app?token=x failed")).toBe("GET <host> failed");
  });

  it("leaves member chains and file names alone", () => {
    const message = "window.location.href is not a function in index-abc123.js:12";
    expect(redactDiagnosticText(message)).toBe(message);
    expect(redactDiagnosticText("t.map is not a function")).toBe("t.map is not a function");
  });

  it("redacts emails", () => {
    expect(redactDiagnosticText("no account for taylor@example.com")).toBe(
      "no account for <email>",
    );
  });

  it("redacts IPv4 and IPv6 addresses", () => {
    expect(redactDiagnosticText("peer 203.0.113.9 refused")).toBe("peer <ip> refused");
    expect(redactDiagnosticText("peer 2001:db8::1 refused")).toBe("peer <ip> refused");
    expect(redactDiagnosticText("peer fe80::1ff:fe23:4567:890a refused")).toBe(
      "peer <ip> refused",
    );
    expect(redactDiagnosticText("peer ::1 refused")).toBe("peer <ip> refused");
    expect(
      redactDiagnosticText("peer 2001:0db8:85a3:0000:0000:8a2e:0370:7334 refused"),
    ).toBe("peer <ip> refused");
  });

  it("does not take a clock time for an address", () => {
    expect(redactDiagnosticText("started at 12:30:45")).toBe("started at 12:30:45");
  });

  it("redacts UUIDs, cuids and long identifiers", () => {
    expect(redactDiagnosticText(`list ${LIST_ID} missing`)).toBe("list <id> missing");
    expect(redactDiagnosticText("todo cjld2cjxh0000qzrmn831i7rn missing")).toBe(
      "todo <id> missing",
    );
  });

  it("redacts long digit runs but not short numbers", () => {
    expect(redactDiagnosticText("at 1759320000000 got 404 after 3 tries")).toBe(
      "at <n> got 404 after 3 tries",
    );
  });

  it("redacts database fragments", () => {
    const text = redactDiagnosticText(
      `duplicate key: Key (list_id)=(${LIST_ID}) already exists, see jdbc:postgresql://db.internal:5432/tday`,
    );
    expect(text).not.toContain(LIST_ID);
    expect(text).not.toContain("db.internal");
    expect(text).toContain("Key (<redacted>)=(<redacted>)");
  });

  it("redacts the JSON snippet a parse error quotes from the response", () => {
    const text = redactDiagnosticText(
      `Unexpected token 'B', "Buy milk a"... is not valid JSON`,
    );
    expect(text).not.toContain("Buy milk");
    expect(text).toContain("is not valid JSON");
  });

  it("truncates long messages", () => {
    const long = "word ".repeat(200);
    expect(redactDiagnosticText(long).length).toBeLessThanOrEqual(300);
  });
});

describe("scrubWebBreadcrumb", () => {
  it("keeps the structural categories, with their URLs reduced to route templates", () => {
    const crumb = scrubWebBreadcrumb({
      category: "fetch",
      type: "http",
      data: {
        method: "PATCH",
        url: `https://tday.my-home.example.net/api/todo/${LIST_ID}?x=1`,
        status_code: 500,
      },
    });
    expect(crumb?.data).toMatchObject({ method: "PATCH", url: "/api/todo/:id", status_code: 500 });
  });

  it.each(["tday", "api", "navigation", "xhr"])("allows %s breadcrumbs", (category) => {
    expect(scrubWebBreadcrumb({ category, message: "sync.replay" })).not.toBeNull();
  });

  it.each(["console", "ui.click", "ui.input", "sentry.event", "sentry.transaction", "auth", undefined])(
    "drops %s breadcrumbs",
    (category) => {
      expect(scrubWebBreadcrumb({ category, message: "Buy milk" })).toBeNull();
    },
  );
});

function leakyEvent(): ErrorEvent {
  return {
    type: undefined,
    event_id: "0".repeat(32),
    message: "Sync failed for taylor@example.com via 203.0.113.9",
    user: {
      id: LIST_ID,
      email: "taylor@example.com",
      username: "taylor",
      ip_address: "203.0.113.9",
    },
    request: {
      url: `https://tday.my-home.example.net/en/app/list/${LIST_ID}?highlightTodoId=abc&token=zzz`,
      query_string: "highlightTodoId=abc",
      cookies: { session: "secret" },
      headers: {
        Referer: "https://other.example.org/private/path?x=1",
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) Firefox/130.0",
        Cookie: "session=secret",
        "X-Forwarded-For": "203.0.113.9",
        "Accept-Language": "de-DE,de;q=0.9",
      },
    },
    exception: {
      values: [
        {
          type: "TypeError",
          value: `Failed to fetch https://tday.my-home.example.net/api/list/${LIST_ID} (203.0.113.9:8080) Key (list_id)=(${LIST_ID}) jdbc:postgresql://db.internal:5432/tday`,
          stacktrace: {
            frames: [
              {
                filename: "https://tday.my-home.example.net/assets/index-abc123.js",
                abs_path: "https://tday.my-home.example.net/assets/index-abc123.js",
                function: "syncReplay",
                lineno: 12,
                colno: 34,
              },
              { filename: "<anonymous>", function: "run" },
            ],
          },
        },
      ],
    },
    debug_meta: {
      images: [
        {
          type: "sourcemap",
          code_file: "https://tday.my-home.example.net/assets/index-abc123.js",
          debug_id: "1b2c3d4e-0000-4000-8000-000000000001",
        },
      ],
    },
    contexts: {
      culture: { timezone: "Europe/Berlin", locale: "de-DE", calendar: "gregory" },
      device: { name: "Taylor's laptop", timezone: "Europe/Berlin", locale: "de-DE", family: "Desktop" },
    },
    breadcrumbs: [
      { category: "ui.click", message: "button.delete-task Buy milk" },
      { category: "console", message: `listing ${LIST_ID}` },
      { category: "sentry.event", message: "TypeError: Failed to fetch" },
      {
        category: "fetch",
        type: "http",
        data: {
          method: "GET",
          url: `https://tday.my-home.example.net/api/list/${LIST_ID}`,
          status_code: 200,
        },
      },
      {
        category: "navigation",
        data: {
          from: "https://tday.my-home.example.net/en/app/tday",
          to: `https://tday.my-home.example.net/en/app/list/${LIST_ID}`,
        },
      },
      { category: "tday", message: "sync.replay", data: { pending: 3 } },
    ],
    tags: { existing: "kept" },
  } as ErrorEvent;
}

describe("scrubWebEvent", () => {
  it("leaves no identifier, host, address, email, zone or referrer in the serialised event", () => {
    const scrubbed = scrubWebEvent(leakyEvent(), CONTEXT);
    const wire = JSON.stringify(scrubbed);

    for (const leak of [
      LIST_ID,
      "my-home",
      "example.net",
      "other.example.org",
      "private/path",
      "203.0.113.9",
      "taylor@example.com",
      "taylor",
      "Europe/Berlin",
      "de-DE",
      "Taylor's laptop",
      "db.internal",
      "highlightTodoId",
      "Referer",
      "Cookie",
      "X-Forwarded-For",
      "Accept-Language",
      "Buy milk",
    ]) {
      expect(wire, `event still contains ${leak}`).not.toContain(leak);
    }
  });

  it("drops the user outright", () => {
    expect(scrubWebEvent(leakyEvent(), CONTEXT).user).toBeUndefined();
  });

  it("keeps the route template and the browser's own User-Agent, and nothing else of the request", () => {
    const { request } = scrubWebEvent(leakyEvent(), CONTEXT);
    expect(request).toEqual({
      url: "/:locale/app/list/:id",
      headers: { "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) Firefox/130.0" },
    });
  });

  it("keeps the error type, the frames and their line numbers, with file locations as paths", () => {
    const [exception] = scrubWebEvent(leakyEvent(), CONTEXT).exception?.values ?? [];
    expect(exception.type).toBe("TypeError");
    expect(exception.value).toContain("Failed to fetch");
    const [frame, anonymous] = exception.stacktrace?.frames ?? [];
    expect(frame).toMatchObject({
      filename: "/assets/index-abc123.js",
      abs_path: "/assets/index-abc123.js",
      function: "syncReplay",
      lineno: 12,
      colno: 34,
    });
    expect(anonymous.filename).toBe("<anonymous>");
  });

  it("rewrites debug-id image locations the same way as the frames they belong to", () => {
    const { debug_meta } = scrubWebEvent(leakyEvent(), CONTEXT);
    expect(debug_meta?.images).toEqual([
      {
        type: "sourcemap",
        code_file: "/assets/index-abc123.js",
        debug_id: "1b2c3d4e-0000-4000-8000-000000000001",
      },
    ]);
  });

  it("keeps only the structural breadcrumbs", () => {
    const { breadcrumbs } = scrubWebEvent(leakyEvent(), CONTEXT);
    expect(breadcrumbs?.map((crumb) => crumb.category)).toEqual(["fetch", "navigation", "tday"]);
    expect(breadcrumbs?.[0].data).toMatchObject({ url: "/api/list/:id", status_code: 200 });
  });

  it("drops the locale and zone contexts", () => {
    const { contexts } = scrubWebEvent(leakyEvent(), CONTEXT);
    expect(contexts?.culture).toBeUndefined();
    expect(contexts?.device).toEqual({ family: "Desktop" });
  });

  it("tags every event with the client, version, mode, offset and language", () => {
    const { tags } = scrubWebEvent(leakyEvent(), CONTEXT);
    expect(tags).toEqual({
      existing: "kept",
      client: "web",
      app_version: "0.8.0",
      mode: "server",
      tz_offset: "UTC+2",
      locale_lang: "de",
    });
  });

  it("reports the mode it was handed, so a switch to Local Mode shows on the next event", () => {
    expect(scrubWebEvent(leakyEvent(), { ...CONTEXT, mode: "local" }).tags?.mode).toBe("local");
  });

  it("leaves a slow_operation event as it was, apart from the tags every event gets", () => {
    const slow = {
      event_id: "2".repeat(32),
      message: "slow_operation",
      level: "warning",
      fingerprint: ["slow_operation", "local_vault_unlock"],
      tags: { operation: "local_vault_unlock", duration_bucket: "10-30s" },
      extra: { duration_ms: 12_346, threshold_ms: 8_000 },
    } as ErrorEvent;

    const scrubbed = scrubWebEvent(slow, CONTEXT);

    expect(scrubbed).toMatchObject({
      message: "slow_operation",
      level: "warning",
      fingerprint: ["slow_operation", "local_vault_unlock"],
      extra: { duration_ms: 12_346, threshold_ms: 8_000 },
    });
    expect(scrubbed.tags).toMatchObject({ operation: "local_vault_unlock", duration_bucket: "10-30s", client: "web" });
  });

  it("copes with an event that has none of the optional parts", () => {
    const scrubbed = scrubWebEvent({ event_id: "1".repeat(32) } as ErrorEvent, CONTEXT);
    expect(scrubbed.tags).toMatchObject({ client: "web" });
    expect(scrubbed.request).toBeUndefined();
    expect(scrubbed.breadcrumbs).toBeUndefined();
  });
});
