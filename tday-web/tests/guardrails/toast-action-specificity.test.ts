import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Sonner injects `[data-sonner-toast][data-styled=true] [data-button]` at runtime — specificity
 * (0,3,0), which outranks any utility class. So every declaration the app makes on the toast's
 * action button has to be `!important` or it is silently dropped.
 *
 * This bit once, visibly: sonner paints the action `color: var(--normal-bg)` — the toast's OWN
 * surface colour — because it normally sits on a filled chip. The chip was removed and the colour
 * left un-forced, so "Undo" was painted in the exact colour of the pill behind it and became
 * invisible in both themes. The class was present and correct the whole time, which is why a
 * rendered-className assertion cannot catch this. Reading the source is the only cheap guard.
 */
describe("sonner action button beats sonner's own specificity", () => {
  const source = readFileSync(
    resolve(__dirname, "../../src/components/ui/sonner.tsx"),
    "utf8",
  );

  const actionButton = /actionButton:\s*\n?\s*"([^"]+)"/.exec(source)?.[1];

  it("declares an actionButton class list at all", () => {
    expect(actionButton).toBeTruthy();
  });

  // Colour is the one that made "Undo" vanish; size and weight would silently fall back to
  // sonner's 12px/500 and stop matching the native snackbar.
  it.each([
    ["colour", "!text-[hsl(var(--toast-action))]"],
    ["font size", "!text-[15px]"],
    ["font weight", "!font-extrabold"],
    ["background", "!bg-transparent"],
  ])("forces %s with !important", (_label, needle) => {
    expect(actionButton).toContain(needle);
  });

  it("does not reintroduce the filled-pill treatment", () => {
    expect(actionButton).not.toContain("bg-primary");
    expect(actionButton).not.toContain("text-primary-foreground");
  });
});
