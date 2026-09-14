// @vitest-environment jsdom

/**
 * What the feed draws while its tasks are in flight.
 *
 * The defect this file stands guard over is not a missing skeleton — there was
 * always one — it is a skeleton that stood in for a row the app does not have.
 * It drew a `rounded-2xl` bordered card at `px-3 py-3` inside a `space-y-3`
 * stack; the real row is transparent, `px-1 py-2.5`, and sits flush against its
 * neighbours. Every load therefore ended with the page moving.
 *
 * jsdom lays nothing out and applies no stylesheet, so nothing here can measure
 * that. What it CAN pin is the claim the geometry rests on: that the skeleton
 * spells the row's own classes, and that it spells none of the card's. Both
 * halves are asserted, because the drift can come from either side — a class
 * quietly dropped, or a card class quietly added back.
 *
 * The last test reads `TodoItemContainer` as text, and it is the half that
 * makes the rest mean anything. A skeleton matching a row is a claim about two
 * files; asserting only this one would let the row's padding move and leave
 * this suite green while the skeleton it pins started lying.
 */

import React from "react";
import { cleanup, render } from "@testing-library/react";
import { readFileSync } from "node:fs";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { TaskRowSkeletonGroup } from "@/components/ui/TaskRowSkeleton";

afterEach(cleanup);

function renderGroup(count?: number) {
  const { container } = render(<TaskRowSkeletonGroup count={count} />);
  const group = container.querySelector('[aria-busy="true"]');
  if (!group) throw new Error("the skeleton group did not render");
  return group;
}

describe("TaskRowSkeletonGroup", () => {
  it("renders one row per requested count", () => {
    expect(renderGroup(5).children).toHaveLength(5);
    cleanup();
    expect(renderGroup().children).toHaveLength(3);
  });

  it("draws the flat row's box and none of the card's", () => {
    const row = renderGroup(1).children[0];

    expect(row.className).toContain("px-1");
    expect(row.className).toContain("py-2.5");
    expect(row.className).toContain("sm:rounded-lg");

    // The shape it used to be. A skeleton that grows a fill, a border or a
    // 16 px radius has stopped standing in for the row and started standing in
    // for a card the feed does not draw. The card's 16 px corner is spelled
    // `rounded-lg` now that the radius ladder has been renamed onto its rungs,
    // and the row's own `sm:rounded-lg` above contains that string — so the
    // class list is read as tokens rather than as text, or this asserts nothing.
    expect(row.className.split(/\s+/)).not.toContain("rounded-lg");
    expect(row.className).not.toContain("border-border/65");
    expect(row.className).not.toContain("bg-card");
  });

  it("stretches the title column, because a bar has no text to size it", () => {
    const row = renderGroup(1).children[0];
    const group = row.children[0];
    const column = group.children[1];

    // The row's column is shrink-to-fit and gets its width from the title text
    // in it. A percentage-width bar contributes nothing to intrinsic sizing, so
    // without these the column stands on its widest real child — the 96 px meta
    // bar — and `w-1/2` renders 48 px of it. The three cycled widths land a few
    // pixels apart and the placeholder is narrower than the row that replaces
    // it. jsdom cannot measure that; it can keep the classes that prevent it.
    expect(group.className).toContain("flex-1");
    expect(column.className).toContain("flex-1");
    expect(column.className).toContain("min-w-0");
  });

  it("draws its three title widths, so the stack does not read as a table", () => {
    const rows = [...renderGroup(3).children];
    const widths = rows.map((row) => {
      const title = row.children[0].children[1].children[0];
      return [...title.classList].find((name) => name.startsWith("w-"));
    });

    expect(new Set(widths).size).toBe(3);
  });

  it("stacks flush, the way TodoGroup does", () => {
    const group = renderGroup(3);

    expect(group.className).toContain("space-y-0");
    // The gap is the reflow: three rows' worth of it vanishes in the frame the
    // real feed lands.
    expect(group.className).not.toContain("space-y-3");
  });

  it("announces itself as busy and says nothing else", () => {
    const group = renderGroup(3);

    expect(group.getAttribute("aria-busy")).toBe("true");
    expect(group.textContent).toBe("");
  });
});

describe("the row it stands in for", () => {
  it("still has the padding the skeleton copies", () => {
    const source = readFileSync(
      path.resolve(
        __dirname,
        "../../src/components/todo/component/TodoItemContainer.tsx",
      ),
      "utf8",
    );

    expect(source).toContain("px-1 py-2.5");
  });
});
