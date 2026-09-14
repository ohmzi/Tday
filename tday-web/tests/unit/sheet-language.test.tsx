// @vitest-environment jsdom

/**
 * ONE SCRIM, ONE LADDER, ACROSS FOUR OVERLAY SYSTEMS
 *
 * Web grew four ways to put something over the page — vaul's drawer, Radix's
 * dialog, Radix's sheet, and the hand-rolled `Modal` — and each of them arrived
 * with its own dim and its own timings. Five scrims carried four different
 * alphas (0.80, 0.80, 0.65, 0.50, 0.45), so how dark the page went depended on
 * which library happened to own the surface in front of it, and the enters and
 * exits ran on 500/300, 200, 200/200 and 300/200 with nothing choosing between
 * them. Both native clients had settled this years earlier and agreed with each
 * other: 0.40 light, 0.68 dark, one value.
 *
 * What is asserted here is the vocabulary, not the pixels. A scrim names the
 * token; nobody writes a shade. A duration names a rung; nobody writes a number.
 * The two exported exit constants — read by JavaScript on one side and by CSS on
 * the other, in files that cannot see each other — name the same rungs their
 * stylesheets play on.
 *
 * The shades themselves belong to `globals.css` and are checked there, in the
 * one place both the light and the dark value are visible at once. A class-list
 * test cannot see a custom property, and pretending otherwise would be a test
 * that passes on a token pointing at nothing.
 */

import { readFileSync } from "fs";
import path from "path";
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { Drawer, DrawerContent, DrawerTitle, DRAWER_EXIT_MS } from "@/components/ui/drawer";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { Modal, ModalContent, ModalOverlay, MODAL_EXIT_MS } from "@/components/ui/Modal";
import { CenteredSelectorOverlay } from "@/components/ui/sheet-chrome/CenteredSelectorOverlay";
import { DURATION_MS } from "@/lib/motion";

/**
 * Every element the five systems portal into the document, open.
 *
 * Collected by walking `document.body` rather than by querying each library's
 * private data attribute: the point of this file is that none of these systems
 * gets to be special, so the assertions should not have to know which one drew
 * which node.
 */
function renderedClassStrings(): string[] {
  return Array.from(document.body.querySelectorAll<HTMLElement>("*"))
    .map((el) => el.className)
    .filter((c): c is string => typeof c === "string" && c.length > 0);
}

function scrims(): HTMLElement[] {
  // Every one of the five is a full-bleed fixed layer, and that is the only
  // thing they have in common by construction.
  return Array.from(document.body.querySelectorAll<HTMLElement>(".fixed.inset-0"));
}

const OVERLAYS: Array<[name: string, node: React.ReactElement]> = [
  [
    "drawer",
    <Drawer open onOpenChange={() => {}}>
      <DrawerContent>
        <DrawerTitle>drawer</DrawerTitle>
      </DrawerContent>
    </Drawer>,
  ],
  [
    "dialog",
    <Dialog open onOpenChange={() => {}}>
      <DialogContent>
        <DialogTitle>dialog</DialogTitle>
      </DialogContent>
    </Dialog>,
  ],
  [
    "sheet",
    <Sheet open onOpenChange={() => {}}>
      <SheetContent>
        <SheetTitle>sheet</SheetTitle>
      </SheetContent>
    </Sheet>,
  ],
  [
    "modal",
    <Modal open onOpenChange={() => {}}>
      <ModalOverlay>
        <ModalContent>modal</ModalContent>
      </ModalOverlay>
    </Modal>,
  ],
  [
    "centred selector",
    <CenteredSelectorOverlay open onOpenChange={() => {}} title="selector">
      <p>selector</p>
    </CenteredSelectorOverlay>,
  ],
];

describe("what an overlay draws over the page", () => {
  afterEach(cleanup);

  it.each(OVERLAYS)("the %s scrim names the token instead of a shade", (_name, node) => {
    render(node);

    const dimming = scrims().filter((s) => !s.className.includes("bg-transparent"));
    expect(dimming.length).toBeGreaterThan(0);
    for (const scrim of dimming) {
      expect(scrim.className).toContain("bg-sheet-scrim");
      // The failure this catches is a merge, not a typo: `bg-black/*` beside the
      // token wins or loses by class order, and either way the page goes a shade
      // nobody chose.
      expect(scrim.className).not.toMatch(/\bbg-black\//);
    }
  });

  it.each(OVERLAYS)("the %s names rungs, not numbers", (name, node) => {
    render(node);

    const classes = renderedClassStrings();
    for (const className of classes) {
      // `duration-enter` and friends carry no digits; `duration-200` does. That
      // is the whole distinction the budget ratchet counts, checked here on the
      // rendered output rather than on the source, so a class assembled by `cn`
      // out of two halves is still caught.
      expect(className, `a rendered overlay class names a number: ${className}`).not.toMatch(
        /\bduration-\d/,
      );
    }

    // The other half, or the absence above proves nothing: a component that
    // declares no duration at all would pass it for free. The drawer is the one
    // that legitimately does — vaul injects its own stylesheet and `globals.css`
    // answers it there, which is why `DRAWER_EXIT_MS` is checked below against
    // the rung that file plays rather than against a class.
    if (name !== "drawer") {
      expect(classes.join(" ")).toMatch(/\bduration-(quick|enter|change|emphasis|scene)\b/);
    }
  });
});

describe("the shades the token resolves to", () => {
  const css = readFileSync(path.resolve(__dirname, "..", "..", "src", "globals.css"), "utf-8");

  it("agrees with both native clients, in both themes", () => {
    // Not a re-assertion of a number this repo invented — these are the two
    // alphas iOS and Android had already independently agreed on, and the reason
    // it was web that had to move. If one of the three ever changes, all three
    // change together or this is the first thing that says so.
    expect(css).toMatch(/--sheet-scrim:\s*0 0% 0% \/ 0\.40;/);
    expect(css).toMatch(/--sheet-scrim:\s*0 0% 0% \/ 0\.68;/);
    expect(css).toContain("--color-sheet-scrim: hsl(var(--sheet-scrim));");
  });
});

describe("the two exits that are written twice", () => {
  it("names the rung on the JavaScript side of each one", () => {
    // Both constants exist because a timer in TypeScript has to outlive an
    // animation declared in CSS, and neither half can read the other. Quick for
    // the modal, which is a card fading out where it stands; Emphasis for the
    // drawer, which is a sheet travelling off the bottom of the screen.
    expect(MODAL_EXIT_MS).toBe(DURATION_MS.quick);
    expect(DRAWER_EXIT_MS).toBe(DURATION_MS.emphasis);
  });
});
