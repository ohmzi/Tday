// @vitest-environment jsdom

/**
 * THE VERSION PAGE IS NOT ALLOWED TO LIE OR TO GO BLANK.
 *
 * It used to be admin-only, behind a redirect that bounced everyone else back to
 * Today; the Settings App Version row is tappable now, so this is the first
 * screen a non-admin sees when they ask what build they are on. Two ways that
 * goes wrong are pinned here, because neither is visible in a diff:
 *
 *   1. A FAILED CHECK IS NOT "UP TO DATE". The latest-release lookup is the one
 *      step that can fail on its own, and its failure used to arrive as
 *      `hasUpdate: false` — indistinguishable from a genuine up-to-date build,
 *      so the page printed "You're running the latest version" without having
 *      looked. The query now also reports `latestLookupFailed`, and the page
 *      shows the native error copy with Retry instead of the claim.
 *   2. NOTHING-TO-SAY IS STILL SOMETHING. The fallback release metadata always
 *      carries a version and a GitHub URL, so the page keeps the installed
 *      version, the release card and the link even when every source fails —
 *      only the date and the changelog go missing, and the empty-notes copy
 *      stands in for the changelog.
 *
 * The other two cases are the native update/installed split: an update shows the
 * "What's new in" notes for the NEW version and never the old build's, and an
 * update with an empty changelog hides the notes card outright — the rule the
 * native update card follows by having no empty-message to show.
 *
 * Deliberately absent from every case below: an APK download/install button and
 * an App Store "Open Update" button. A browser tab can honour neither, and faking
 * one would be worse than the gap.
 */
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@/i18n";
import VersionPage from "@/components/release/VersionPage";

const state: { data: unknown } = { data: null };

vi.mock("@/features/release/query/get-release-info", () => ({
  useReleaseInfo: () => ({ data: state.data, refetch: vi.fn() }),
}));

const base = {
  currentVersion: "1.8.20",
  currentRelease: {
    version: "1.8.20",
    publishedAt: "2026-04-02T18:12:08Z",
    notes: ["Older note"],
    releaseUrl: "https://github.com/ohmzi/Tday/releases/tag/v1.8.20",
    compareUrl: null,
  },
  latestRelease: null,
  hasUpdate: false,
  latestUrl: "https://github.com/ohmzi/Tday/releases/tag/v1.8.20",
  latestLookupFailed: false,
};

const renderPage = () =>
  render(
    <MemoryRouter>
      <VersionPage backFallbackHref="/app/settings" />
    </MemoryRouter>,
  );

afterEach(cleanup);

describe("version page", () => {
  it("shows the loading card before data", () => {
    state.data = null;
    renderPage();
    expect(screen.getByText("Loading release information…")).toBeTruthy();
  });

  it("shows the up-to-date status and the changelog", () => {
    state.data = { ...base };
    renderPage();
    expect(screen.getByText("Latest")).toBeTruthy();
    expect(screen.getByText("You're running the latest version")).toBeTruthy();
    expect(screen.getByText("Installed Version")).toBeTruthy();
    expect(screen.getByText("What's new in v1.8.20")).toBeTruthy();
    expect(screen.getByText("Older note")).toBeTruthy();
    expect(screen.getByText("Published April 2, 2026")).toBeTruthy();
    expect(screen.getByText("View on GitHub")).toBeTruthy();
  });

  it("shows the update status, latest version and both cards", () => {
    state.data = {
      ...base,
      hasUpdate: true,
      latestRelease: {
        version: "1.9.0",
        publishedAt: "2026-05-01T00:00:00Z",
        notes: ["Newer note"],
        releaseUrl: "https://github.com/ohmzi/Tday/releases/tag/v1.9.0",
        compareUrl: null,
      },
    };
    renderPage();
    expect(screen.getByText("Update Available")).toBeTruthy();
    expect(screen.getByText("Version v1.9.0 is available.")).toBeTruthy();
    expect(screen.getByText("Installed")).toBeTruthy();
    expect(screen.getByText("v1.8.20")).toBeTruthy();
    expect(screen.getByText("v1.9.0")).toBeTruthy();
    expect(screen.getByText("What's new in v1.9.0")).toBeTruthy();
    expect(screen.getByText("Newer note")).toBeTruthy();
    expect(screen.queryByText("Older note")).toBeNull();
  });

  it("hides the notes card when the update has no changelog", () => {
    state.data = {
      ...base,
      hasUpdate: true,
      latestRelease: {
        version: "1.9.0",
        publishedAt: null,
        notes: [],
        releaseUrl: "https://github.com/ohmzi/Tday/releases/tag/v1.9.0",
        compareUrl: null,
      },
    };
    renderPage();
    expect(screen.getByText("Update Available")).toBeTruthy();
    expect(screen.queryByText("No release notes available for this version")).toBeNull();
  });

  it("says the check failed instead of claiming it is up to date, and still shows the build", () => {
    state.data = {
      ...base,
      latestLookupFailed: true,
      currentRelease: {
        version: "1.8.20",
        publishedAt: null,
        notes: [],
        releaseUrl: "https://github.com/ohmzi/Tday/releases/tag/v1.8.20",
        compareUrl: null,
      },
    };
    renderPage();
    expect(
      screen.getByText(
        "Unable to fetch release information. Please check your connection and try again.",
      ),
    ).toBeTruthy();
    expect(screen.getByText("Retry")).toBeTruthy();
    expect(screen.queryByText("You're running the latest version")).toBeNull();
    expect(screen.getByText("What's new in v1.8.20")).toBeTruthy();
    expect(screen.getByText("No release notes available for this version")).toBeTruthy();
    expect(screen.getByText("View on GitHub")).toBeTruthy();
  });
});
