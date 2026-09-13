// @vitest-environment jsdom
import { readFileSync } from "fs";
import path from "path";
import React from "react";
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import {
  Drawer,
  DrawerContent,
  DrawerTitle,
  DRAWER_EXIT_MS,
} from "@/components/ui/drawer";
import { DURATION_MS } from "@/lib/motion";

/**
 * THE CONFIRM SHEET OVER A SHEET
 *
 * The calendar's confirm drawers are siblings of the form drawer they cover — see
 * `EditDrawer`, which renders `ConfirmCancelEditDrawer` beside its own `Drawer`
 * rather than inside it. Two roots, two portals, two scrims over the same pixels,
 * and `black/80` twice over resolves to 96% black: the confirm sheet arrived on a
 * page several shades darker than any other surface in the app.
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

describe("a drawer scrim over a drawer scrim", () => {
  afterEach(() => {
    cleanup();
  });

  it("dims the page when it is the only scrim up", () => {
    render(<StackedDrawers formOpen confirmOpen={false} />);

    expect(scrims()).toHaveLength(1);
    expect(scrims()[0].className).toContain("bg-black/80");
    expect(scrims()[0].getAttribute("data-nested-scrim")).toBeNull();
  });

  it("adds no dim of its own when it opens over a page that is already dim", () => {
    const { rerender } = render(<StackedDrawers formOpen confirmOpen={false} />);
    rerender(<StackedDrawers formOpen confirmOpen />);

    const [beneath, above] = scrims();
    expect(scrims()).toHaveLength(2);
    // The one doing the dimming keeps doing it: this is not a swap of which
    // layer is dark, it is the second layer declining to darken again.
    expect(beneath.className).toContain("bg-black/80");
    expect(above.getAttribute("data-nested-scrim")).toBe("true");
    expect(above.className).toContain("bg-transparent");
    expect(above.className).not.toContain("bg-black/80");
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
    // The count is a registry, not a latch: a drawer opened later, on its own,
    // is the only scrim up and has to be the one that dims.
    const { rerender, unmount } = render(<StackedDrawers formOpen confirmOpen />);
    rerender(<StackedDrawers formOpen={false} confirmOpen={false} />);
    unmount();

    render(<StackedDrawers formOpen confirmOpen={false} />);

    expect(scrims()).toHaveLength(1);
    expect(scrims()[0].className).toContain("bg-black/80");
    expect(scrims()[0].getAttribute("data-nested-scrim")).toBeNull();
  });
});

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
