// @vitest-environment jsdom

/**
 * The toast's action ("Undo") must read the same on web as it does natively.
 *
 * iOS renders it as a bare `Button(.plain)` — `.subheadline` heavy, tinted with the snackbar
 * accent (`Color(hex: 0xE06F66)` in AppRootView.AppSnackbar). Web used to render it as a filled
 * `bg-primary` pill with white text, which is what made the two look different side by side.
 *
 * Two paths produce a toast action, and both have to match: plain sonner toasts (task completed)
 * and the custom ClickableToast (task deleted, which is tap-through).
 */

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import ClickableToast from "@/hooks/ClickableToast";

describe("the toast action button", () => {
  // This vitest config has no globals-based auto cleanup, so renders would otherwise stack up
  // and a second lookup would find two matching buttons.
  afterEach(cleanup);

  it("renders as bare accent text, not a filled pill", () => {
    render(
      <ClickableToast title="Task deleted" onClick={vi.fn()} action={{ label: "Undo", onClick: vi.fn() }} />,
    );

    const undo = screen.getByRole("button", { name: "Undo" });

    // Tinted with the shared snackbar accent…
    expect(undo.className).toContain("text-[hsl(var(--toast-action))]");
    // …and none of the filled-pill treatment it used to carry.
    expect(undo.className).not.toContain("bg-primary");
    expect(undo.className).not.toContain("text-primary-foreground");
    expect(undo.className).not.toContain("rounded-full");
  });

  it("keeps the tap-through body and the action as separate targets", () => {
    const onBody = vi.fn();
    const onUndo = vi.fn();
    render(
      <ClickableToast title="Task deleted" onClick={onBody} action={{ label: "Undo", onClick: onUndo }} />,
    );

    screen.getByRole("button", { name: "Undo" }).click();

    expect(onUndo).toHaveBeenCalledTimes(1);
    expect(onBody).not.toHaveBeenCalled();
  });
});
