// @vitest-environment jsdom

/**
 * THE DISSOLVE BAND PAINTS BENEATH THE BAR'S OWN CONTROLS.
 *
 * The band under the pinned bar — the gradient that dissolves content as it
 * slides behind the bar — is a positioned sibling of the back button and the
 * trailing actions, and the bar itself is a stacking context (`sticky z-40`).
 * Every control in the bar is positioned too (`position: relative`, written
 * onto `button` by the press-affordance rule in `globals.css`), and none of
 * them carries a `z-index`, so tree order alone decides who paints last.
 *
 * That made the band the winner while it sat after the buttons, and the back
 * button's shadow — `rootFeedHeaderButtonClass`'s, whose ink reaches about 7px
 * past the bar's 6px bottom padding and so lands inside the band — was cut off
 * in a straight line along the bar's bottom edge. Nothing about the band moves
 * to fix that: same rectangle, same offset, same opacity ramp, still off at
 * rest. It only has to be painted first, which is what this pins, together with
 * the two things a careless reorder would break: the docked title staying above
 * the band, and the band itself staying where it is.
 *
 * `RootFeedHeroHeader` and `MobileSearchHeader` already draw it first for the
 * same reason — which is why the pages that hand their bar in through
 * `barSlots` never showed the cut.
 */
import { cleanup, render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import { Info } from "lucide-react";
import NativePageHeader from "@/components/app/NativePageHeader";

/** True when `later` is painted after `earlier` in tree order. */
const paintsAfter = (later: Element, earlier: Element) =>
  Boolean(
    earlier.compareDocumentPosition(later) & Node.DOCUMENT_POSITION_FOLLOWING,
  );

afterEach(cleanup);

const renderHeader = () =>
  render(
    <MemoryRouter>
      <NativePageHeader title="App Version" accentColor="#68717A" icon={Info} />
    </MemoryRouter>,
  );

describe("NativePageHeader dissolve band", () => {
  it("paints the band before the bar's buttons, so their shadows land on it", () => {
    const { container } = renderHeader();
    const bar = container.querySelector("header");
    expect(bar).not.toBeNull();

    const band = bar!.querySelector('[class*="bg-gradient-to-b"]');
    const backButton = bar!.querySelector("button");
    const dockedTitle = bar!.querySelector("span");

    expect(band).not.toBeNull();
    expect(backButton).not.toBeNull();
    expect(dockedTitle).not.toBeNull();

    // Both of these must come AFTER the band. The button is the fix; the title
    // is the arrangement that must survive it.
    expect(paintsAfter(backButton!, band!)).toBe(true);
    expect(paintsAfter(dockedTitle!, band!)).toBe(true);
  });

  it("leaves the band's own rectangle, offset and rest state alone", () => {
    const { container } = renderHeader();
    const band = container
      .querySelector("header")!
      .querySelector('[class*="bg-gradient-to-b"]');

    // `top-full`: still parked below the bar's box, not moved into it.
    expect(band!.className).toContain("absolute");
    expect(band!.className).toContain("inset-x-0");
    expect(band!.className).toContain("top-full");
    // Still inert, and still invisible until the page moves: a band painted at
    // rest would veil whatever sits under the bar.
    expect(band!.className).toContain("pointer-events-none");
    expect(band!.getAttribute("style")).toContain("opacity: 0");
    expect(band!.getAttribute("style")).toContain("height: 30px");
  });
});
