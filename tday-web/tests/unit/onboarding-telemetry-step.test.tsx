// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@/i18n";
import OnboardingTelemetryStep from "@/components/onboarding/OnboardingTelemetryStep";
import { ApiError } from "@/lib/api-client";
import { answerFrom, applyInstanceTelemetry } from "@/lib/privacy/instanceTelemetry";

/**
 * The admin's last onboarding step: the instance-wide error-report question, asked only of an
 * admin whose server has never answered it. Everyone else leaves the step for the app without
 * seeing it, which is what keeps a non-admin and a Local Mode setup unblocked.
 */

const api = vi.hoisted(() => ({ GET: vi.fn(), PATCH: vi.fn() }));
vi.mock("@/lib/api-client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api-client")>()),
  api,
}));

let authState = "authenticated";
vi.mock("@/providers/AuthProvider", () => ({
  useAuth: () => ({ authState, user: { role: "ADMIN" } }),
}));

const NEVER_ANSWERED = { dsnConfigured: true, enabled: false, updatedAt: null };
const ALREADY_ANSWERED = { dsnConfigured: true, enabled: true, updatedAt: "2026-09-01T00:00:00.000Z" };

const onFinish = vi.fn();

function renderStep() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <OnboardingTelemetryStep onFinish={onFinish} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  api.GET.mockReset();
  api.PATCH.mockReset();
  onFinish.mockReset();
  authState = "authenticated";
  applyInstanceTelemetry(answerFrom(false, null));
});

afterEach(cleanup);

describe("the admin's onboarding error-report step", () => {
  it("asks the question on an instance that has never answered it", async () => {
    api.GET.mockResolvedValue(NEVER_ANSWERED);

    renderStep();

    expect(await screen.findByRole("button", { name: "Send reports" })).toBeTruthy();
    expect(screen.getByText("Send error reports?")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Don't send" })).toBeTruthy();
    expect(screen.getByText(/Never sent: names, accounts/)).toBeTruthy();
    expect(api.GET).toHaveBeenCalledWith({ url: "/api/admin/telemetry" });
    expect(onFinish).not.toHaveBeenCalled();
  });

  it("writes a yes and leaves for the app", async () => {
    api.GET.mockResolvedValue(NEVER_ANSWERED);
    api.PATCH.mockResolvedValue({ ...NEVER_ANSWERED, enabled: true, updatedAt: "2026-10-01T12:00:00.000Z" });

    renderStep();
    fireEvent.click(await screen.findByRole("button", { name: "Send reports" }));

    await waitFor(() => expect(onFinish).toHaveBeenCalledWith("/app"));
    expect(api.PATCH).toHaveBeenCalledWith({
      url: "/api/admin/telemetry",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled: true }),
    });
  });

  it("writes a no, which is an answer too, and leaves for the app", async () => {
    api.GET.mockResolvedValue(NEVER_ANSWERED);
    api.PATCH.mockResolvedValue({ ...NEVER_ANSWERED, enabled: false, updatedAt: "2026-10-01T12:00:00.000Z" });

    renderStep();
    fireEvent.click(await screen.findByRole("button", { name: "Don't send" }));

    await waitFor(() => expect(onFinish).toHaveBeenCalledWith("/app"));
    expect(api.PATCH).toHaveBeenCalledWith(
      expect.objectContaining({ body: JSON.stringify({ enabled: false }) }),
    );
  });

  it("does not ask a server that already has an answer", async () => {
    api.GET.mockResolvedValue(ALREADY_ANSWERED);

    renderStep();

    await waitFor(() => expect(onFinish).toHaveBeenCalledWith("/app"));
    expect(screen.queryByRole("button", { name: "Send reports" })).toBeNull();
  });

  it("does not ask a viewer who is not an admin, and does not block them", async () => {
    api.GET.mockRejectedValue(new ApiError("Forbidden", 403));

    renderStep();

    await waitFor(() => expect(onFinish).toHaveBeenCalledWith("/app"));
    expect(screen.queryByRole("button", { name: "Don't send" })).toBeNull();
  });

  it("does not ask a server that has no reporting endpoint of its own", async () => {
    api.GET.mockResolvedValue({ ...NEVER_ANSWERED, dsnConfigured: false });

    renderStep();

    await waitFor(() => expect(onFinish).toHaveBeenCalledWith("/app"));
  });

  it("waits for the session rather than asking an endpoint that would refuse it", async () => {
    authState = "unauthenticated";
    api.GET.mockResolvedValue(NEVER_ANSWERED);

    renderStep();

    expect(api.GET).not.toHaveBeenCalled();
    expect(onFinish).not.toHaveBeenCalled();
  });

  it("offers the FAQ, which answers nothing and simply leaves", async () => {
    api.GET.mockResolvedValue(NEVER_ANSWERED);

    renderStep();
    fireEvent.click(await screen.findByRole("button", { name: "Read the full FAQ" }));

    expect(onFinish).toHaveBeenCalledWith("/app/guide/crash-reports");
    expect(api.PATCH).not.toHaveBeenCalled();
  });

  it("says so, and stays on the question, when the write fails", async () => {
    api.GET.mockResolvedValue(NEVER_ANSWERED);
    api.PATCH.mockRejectedValue(new ApiError("Forbidden", 403));

    renderStep();
    fireEvent.click(await screen.findByRole("button", { name: "Send reports" }));

    expect(await screen.findByText("Couldn't save that. Try again.")).toBeTruthy();
    expect(onFinish).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Don't send" })).toBeTruthy();
  });
});
