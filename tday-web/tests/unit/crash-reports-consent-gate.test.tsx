// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@/i18n";
import { TINT } from "@/components/onboarding/OnboardingPrimitives";
import CrashReportsConsentGate from "@/components/privacy/CrashReportsConsentGate";
import {
  getTelemetryConsent,
  isTelemetryGranted,
  setTelemetryConsent,
} from "@/lib/privacy/telemetryConsent";

let authUser: Record<string, unknown> | null = { id: "u1" };

vi.mock("@/providers/AuthProvider", () => ({
  useAuth: () => ({ user: authUser }),
}));

const DSN = "https://key@o1.ingest.example.invalid/2";
const DEFERRED_KEY = "tday.telemetry.consentDeferred";

function LocationProbe() {
  return <output data-testid="location">{useLocation().pathname}</output>;
}

// Mounted where the app mounts it — under `/:locale/app` — because `useRouter` reads the locale
// from the route, and that is what the guide link has to be built from.
function renderGate() {
  return render(
    <MemoryRouter initialEntries={["/en/app/tday"]}>
      <Routes>
        <Route
          path="/:locale/app/*"
          element={
            <>
              <CrashReportsConsentGate />
              <LocationProbe />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

function resetConsentFromStorage() {
  window.dispatchEvent(new StorageEvent("storage", { key: null }));
}

beforeEach(() => {
  vi.stubEnv("VITE_SENTRY_DSN", DSN);
  authUser = { id: "u1" };
  window.localStorage.clear();
  window.sessionStorage.clear();
  resetConsentFromStorage();
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
});

describe("CrashReportsConsentGate", () => {
  it("asks once the question is unanswered and this build can send reports", () => {
    renderGate();

    expect(screen.getByRole("dialog", { name: "Help fix crashes?" })).toBeTruthy();
    expect(screen.getByText(/T'Day can send a short technical report/)).toBeTruthy();
    expect(screen.getByText("What's included")).toBeTruthy();
    expect(screen.getByText("Never included")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Share reports" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Not now" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Read the full FAQ" })).toBeTruthy();
  });

  it("tells the web reader which website a report is attributed to", () => {
    renderGate();

    expect(
      screen.getByText(
        "In the web app, only unexpected errors are reported, and your browser also tells Sentry which website a report came from.",
      ),
    ).toBeTruthy();
  });

  it("keeps the white title readable on the hero tile (WCAG 4.5:1)", () => {
    const luminance = (rgb: string) => {
      const [r, g, b] = (rgb.match(/\d+/g) ?? []).map((v) => {
        const c = Number(v) / 255;
        return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
      });
      return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    };

    expect(1.05 / (luminance(TINT.consentBlue) + 0.05)).toBeGreaterThanOrEqual(4.5);
    expect(1.05 / (luminance(TINT.serverBlue) + 0.05)).toBeLessThan(4.5);
  });

  it("does not take focus onto the consenting button, so a stray Enter cannot grant", () => {
    renderGate();

    const share = screen.getByRole("button", { name: "Share reports" });
    expect(document.activeElement).not.toBe(share);
    expect(document.activeElement).toBe(screen.getByRole("dialog"));
  });

  it("stays away when the build carries no DSN, so there is nothing to ask about", () => {
    vi.stubEnv("VITE_SENTRY_DSN", "");

    renderGate();

    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it.each([["granted", true], ["denied", false]] as const)(
    "stays away once the answer is %s",
    (_label, granted) => {
      setTelemetryConsent(granted);

      renderGate();

      expect(screen.queryByRole("dialog")).toBeNull();
    },
  );

  it("stays away for the rest of the session after being deferred", () => {
    window.sessionStorage.setItem(DEFERRED_KEY, "1");

    renderGate();

    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it.each([["requirePasswordChange"], ["requireSecurityQuestions"]])(
    "waits behind the blocking %s prompt",
    (flag) => {
      authUser = { id: "u1", [flag]: true };

      renderGate();

      expect(screen.queryByRole("dialog")).toBeNull();
    },
  );

  it("grants on Share reports", () => {
    renderGate();

    fireEvent.click(screen.getByRole("button", { name: "Share reports" }));

    expect(getTelemetryConsent()).toBe("granted");
    expect(isTelemetryGranted()).toBe(true);
    expect(window.localStorage.getItem("tday.telemetry.consent")).toBe("granted");
    expect(window.sessionStorage.getItem(DEFERRED_KEY)).toBeNull();
  });

  it("denies on Not now, and does not ask again", () => {
    const { unmount } = renderGate();

    fireEvent.click(screen.getByRole("button", { name: "Not now" }));

    expect(getTelemetryConsent()).toBe("denied");
    unmount();
    renderGate();
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("defers on Escape without answering, and asks again next session", () => {
    const { unmount } = renderGate();

    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });

    expect(getTelemetryConsent()).toBe("unanswered");
    expect(window.sessionStorage.getItem(DEFERRED_KEY)).toBe("1");

    unmount();
    renderGate();
    expect(screen.queryByRole("dialog")).toBeNull();

    window.sessionStorage.clear();
    cleanup();
    renderGate();
    expect(screen.getByRole("dialog")).toBeTruthy();
  });

  it("defers, rather than denies, when the backdrop is pressed", async () => {
    renderGate();
    const overlay = document.body.querySelector("[data-state='open'].fixed.inset-0");
    expect(overlay).not.toBeNull();
    // Radix arms its outside-press listener on the next tick.
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 1));
    });

    // A press is a pointerdown and the click that follows it; Radix waits for the click.
    fireEvent.pointerDown(overlay as Element);
    fireEvent.click(overlay as Element);

    expect(getTelemetryConsent()).toBe("unanswered");
    expect(window.sessionStorage.getItem(DEFERRED_KEY)).toBe("1");
  });

  it("defers and opens the guide topic on Read the full FAQ", () => {
    renderGate();

    fireEvent.click(screen.getByRole("button", { name: "Read the full FAQ" }));

    expect(getTelemetryConsent()).toBe("unanswered");
    expect(window.sessionStorage.getItem(DEFERRED_KEY)).toBe("1");
    expect(screen.getByTestId("location").textContent).toBe("/en/app/guide/crash-reports");
  });

  it("gives Share reports and Not now the same weight", () => {
    renderGate();

    const share = screen.getByRole("button", { name: "Share reports" });
    const notNow = screen.getByRole("button", { name: "Not now" });

    expect(share.className).toBe(notNow.className);
  });
});
