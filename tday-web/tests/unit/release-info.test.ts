// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";

describe("loadCurrentRelease", () => {
  afterEach(() => {
    window.localStorage.clear();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("falls back to the GitHub release for the installed version when bundled metadata is stale", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({
              version: "1.8.19",
              publishedAt: "2026-04-02T08:21:06-04:00",
              notes: ["Older release note"],
              releaseUrl: "https://github.com/ohmzi/Tday/releases/tag/v1.8.19",
              compareUrl: "https://github.com/ohmzi/Tday/compare/v1.8.18..v1.8.19",
            }),
            {
              status: 200,
              headers: { "Content-Type": "application/json" },
            },
          ),
        )
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({
              tag_name: "v1.8.20",
              published_at: "2026-04-02T18:12:08Z",
              html_url: "https://github.com/ohmzi/Tday/releases/tag/v1.8.20",
              body: [
                "## What's Changed",
                "",
                "- Add overdue task views",
                "- Fix mobile outline button active state",
                "",
                "## Downloads",
              ].join("\n"),
            }),
            {
              status: 200,
              headers: { "Content-Type": "application/json" },
            },
          ),
        ),
    );

    Object.defineProperty(globalThis, "__APP_VERSION__", {
      value: "1.8.20",
      configurable: true,
    });

    const { loadCurrentRelease } = await import("@/features/release/query/get-release-info");

    await expect(loadCurrentRelease()).resolves.toMatchObject({
      version: "1.8.20",
      notes: [
        "Add overdue task views",
        "Fix mobile outline button active state",
      ],
      releaseUrl: "https://github.com/ohmzi/Tday/releases/tag/v1.8.20",
    });
  });
});
