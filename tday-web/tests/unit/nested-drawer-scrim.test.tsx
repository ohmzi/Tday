// @vitest-environment jsdom
import { readFileSync } from "fs";
import path from "path";
import React from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  Drawer,
  DrawerContent,
  DrawerTitle,
  DrawerTrigger,
  DRAWER_EXIT_MS,
} from "@/components/ui/drawer";
import { DURATION_MS } from "@/lib/motion";

/**
 * THE CONFIRM SHEET OVER A SHEET
 *
 * The calendar's confirm drawers are siblings of the form drawer they cover — see
 * `EditDrawer`, which renders `ConfirmCancelEditDrawer` beside its own `Drawer`
 * rather than inside it. Two roots, two portals, two scrims over the same pixels,
 * and one scrim over another compounds: at the `black/80` this file was written
 * against, twice over resolved to 96% black, and the confirm sheet arrived on a
 * page several shades darker than any other surface in the app. The shade is
 * `bg-sheet-scrim` now and the arithmetic is gentler, but doubling is doubling.
 *
 * What is asserted here is the rule, not the shade — a scrim that finds one
 * already up adds no dim of its own — plus the thing that made the rule awkward
 * to write: the answer has to be stable for the life of the scrim, because the
 * two sheets are normally dismissed together and a nested scrim that recomputed
 * when the one beneath it left would spend the last frames of its exit turning
 * black.
 */

/**
 * The pair as the calendar builds it: the confirm sheet is a sibling, not a child.
 *
 * `formMounted` exists for one test. Radix holds a closed node in the document
 * until an `animationend` that jsdom never fires, so the only way to put a scrim
 * through the moment the scrim beneath it actually leaves the DOM is to take it
 * out of the tree.
 */
function StackedDrawers({
  formMounted = true,
  formOpen,
  confirmOpen,
}: {
  formMounted?: boolean;
  formOpen: boolean;
  confirmOpen: boolean;
}) {
  return (
    <>
      {formMounted && (
        <Drawer open={formOpen} onOpenChange={() => {}}>
          <DrawerContent>
            <DrawerTitle>form</DrawerTitle>
          </DrawerContent>
        </Drawer>
      )}
      <Drawer open={confirmOpen} onOpenChange={() => {}}>
        <DrawerContent>
          <DrawerTitle>confirm</DrawerTitle>
        </DrawerContent>
      </Drawer>
    </>
  );
}

function scrims(): HTMLElement[] {
  return Array.from(document.querySelectorAll<HTMLElement>("[data-vaul-overlay]"));
}

/**
 * Makes jsdom hold a closed overlay in the document the way a browser does.
 *
 * Radix's `Presence` keeps a closing node until `animationend`, but it only ever
 * waits if `getComputedStyle` reports an animation on it — and jsdom computes
 * none, so it drops every closed overlay on the spot. That difference is not a
 * detail here: the whole hazard this file guards is what a *second* sheet sees
 * while the first one's exit is still playing, and untouched jsdom cannot put a
 * scrim in that state at all. The names are vaul's own, from the stylesheet it
 * injects at import.
 */
function holdClosedNodesLikeABrowser(): void {
  const real = window.getComputedStyle.bind(window);
  vi.spyOn(window, "getComputedStyle").mockImplementation(
    (element: Element, pseudo?: string | null) => {
      const computed = real(element, pseudo ?? undefined);
      const state = element.getAttribute?.("data-state");
      if (state !== "open" && state !== "closed") return computed;
      return new Proxy(computed, {
        get: (target, key) => {
          if (key === "animationName") return state === "open" ? "fadeIn" : "fadeOut";
          const value = Reflect.get(target, key, target);
          return typeof value === "function" ? value.bind(target) : value;
        },
      });
    },
  );
}

const REAL_MATCH_MEDIA = window.matchMedia;

describe("a drawer scrim over a drawer scrim", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    window.matchMedia = REAL_MATCH_MEDIA;
  });

  it("dims the page when it is the only scrim up", () => {
    render(<StackedDrawers formOpen confirmOpen={false} />);

    expect(scrims()).toHaveLength(1);
    expect(scrims()[0].className).toContain("bg-sheet-scrim");
    expect(scrims()[0].getAttribute("data-nested-scrim")).toBeNull();
  });

  it("adds no dim of its own when it opens over a page that is already dim", () => {
    const { rerender } = render(<StackedDrawers formOpen confirmOpen={false} />);
    rerender(<StackedDrawers formOpen confirmOpen />);

    const [beneath, above] = scrims();
    expect(scrims()).toHaveLength(2);
    // The one doing the dimming keeps doing it: this is not a swap of which
    // layer is dark, it is the second layer declining to darken again.
    expect(beneath.className).toContain("bg-sheet-scrim");
    expect(above.getAttribute("data-nested-scrim")).toBe("true");
    expect(above.className).toContain("bg-transparent");
    expect(above.className).not.toContain("bg-sheet-scrim");
  });

  it("stays undimmed when the scrim underneath it goes away", () => {
    // The confirm button closes both sheets in one call, so the scrim beneath is
    // routinely on its way out while the one on top is still there. Being left
    // alone is not a promotion: a scrim that recomputed here would turn black for
    // the last frames of its own exit, which is a flash where there used to be a
    // fade.
    const { rerender } = render(<StackedDrawers formOpen confirmOpen={false} />);
    rerender(<StackedDrawers formOpen confirmOpen />);

    rerender(<StackedDrawers formMounted={false} formOpen={false} confirmOpen />);

    expect(scrims()).toHaveLength(1);
    expect(scrims()[0].getAttribute("data-nested-scrim")).toBe("true");
    expect(scrims()[0].className).toContain("bg-transparent");
  });

  it("dims again for the next sheet once the stack has emptied", () => {
    // The registry is a registry, not a latch: a drawer opened later, on its own,
    // is the only scrim up and has to be the one that dims.
    const { rerender, unmount } = render(<StackedDrawers formOpen confirmOpen />);
    rerender(<StackedDrawers formOpen={false} confirmOpen={false} />);
    unmount();

    render(<StackedDrawers formOpen confirmOpen={false} />);

    expect(scrims()).toHaveLength(1);
    expect(scrims()[0].className).toContain("bg-sheet-scrim");
    expect(scrims()[0].getAttribute("data-nested-scrim")).toBeNull();
  });

  it("dims for a sheet opened while the last one is still sliding out", () => {
    // The window this file's own exit rung opens, and the reason the registry
    // counts drawers that are OPEN rather than scrims that are mounted: for the
    // whole of DRAWER_EXIT_MS the leaving scrim is still in the document, and a
    // sheet that took it for company would spend its entire life over an
    // undimmed page. Two calendar rows tapped in the same third of a second is
    // the ordinary way in — each row owns its own EditDrawer.
    holdClosedNodesLikeABrowser();

    const { rerender } = render(<StackedDrawers formOpen confirmOpen={false} />);
    rerender(<StackedDrawers formOpen={false} confirmOpen={false} />);
    // The premise: the old scrim really is still there, mid-exit.
    expect(scrims()).toHaveLength(1);
    expect(scrims()[0].getAttribute("data-state")).toBe("closed");

    rerender(<StackedDrawers formOpen={false} confirmOpen />);

    const arriving = scrims().find((s) => s.getAttribute("data-state") === "open");
    expect(arriving?.getAttribute("data-nested-scrim")).toBeNull();
    expect(arriving?.className).toContain("bg-sheet-scrim");
  });

  it("counts a drawer that opens from its own trigger", () => {
    // `CustomRepeatDrawer` passes no open flag at all — it opens from a
    // DrawerTrigger and lets vaul hold the state. The registry has to hear about
    // those too, or a confirm sheet over one would dim a page that is already dim.
    //
    // Opening for real is what the rest of this file skips, and vaul's own
    // open path asks for a matchMedia this environment does not have.
    window.matchMedia = ((query: string) => ({
      matches: false,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    })) as unknown as typeof window.matchMedia;

    render(
      <>
        <Drawer>
          <DrawerTrigger>open</DrawerTrigger>
          <DrawerContent>
            <DrawerTitle>uncontrolled</DrawerTitle>
          </DrawerContent>
        </Drawer>
        <UncontrolledNeighbour />
      </>,
    );

    fireEvent.click(screen.getByText("open"));
    expect(scrims()).toHaveLength(1);

    fireEvent.click(screen.getByText("cover it"));

    const [beneath, above] = scrims();
    expect(scrims()).toHaveLength(2);
    expect(beneath.className).toContain("bg-sheet-scrim");
    expect(above.getAttribute("data-nested-scrim")).toBe("true");
    expect(above.className).toContain("bg-transparent");
  });
});

/** A second uncontrolled sheet, opened over the first the way a confirm is. */
function UncontrolledNeighbour() {
  return (
    <Drawer>
      <DrawerTrigger>cover it</DrawerTrigger>
      <DrawerContent>
        <DrawerTitle>neighbour</DrawerTitle>
      </DrawerContent>
    </Drawer>
  );
}

describe("how long a drawer is given to leave", () => {
  it("holds a caller's subtree for exactly the rung the stylesheet plays the exit on", () => {
    // The two halves live in different languages and neither can see the other,
    // which is the whole reason to check them here: `globals.css` plays vaul's
    // closed-state animation on a rung, and `DRAWER_EXIT_MS` is what keeps the
    // node in the document while it plays. Compared to each other and to the
    // vocabulary, never to itself.
    const css = readFileSync(
      path.resolve(__dirname, "..", "..", "src", "globals.css"),
      "utf-8",
    );
    const rule =
      /\[data-vaul-drawer\][^{]*\[data-state="closed"\][^{]*\{([^}]*)\}/.exec(css);

    expect(rule, "globals.css declares no closed-state duration for a vaul drawer").toBeTruthy();
    expect(rule![1]).toContain("animation-duration: var(--tday-duration-emphasis)");
    expect(DRAWER_EXIT_MS).toBe(DURATION_MS.emphasis);
    // Rule 1, in the one place both numbers are visible: vaul's own enter is the
    // half-second it injects, and the exit that undoes it is shorter.
    expect(DRAWER_EXIT_MS).toBeLessThan(500);
  });
});
