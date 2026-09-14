// @vitest-environment jsdom

/**
 * What every cold start draws before anything else does.
 *
 * `AppShellSkeleton` is the Suspense fallback for all of `router.tsx`'s lazy routes and the
 * holding screen `AppHomeRedirectPage` shows while it decides where to send you, so it is
 * the most-seen surface in the app and was the last one still spelling a skeleton by hand.
 * Its three task-row bars were 62 px `rounded-2xl` cards; the row that lands on top of them
 * is flat, transparent and 5 px shorter, so the bars resized under the finger already
 * reaching for them. (The column around them still steps: see the file's own comment.)
 *
 * jsdom applies no stylesheet and measures nothing, so none of that is checked by pixels.
 * What is checked is the claim underneath it: that the rows here come from the same
 * component the feed's own placeholder uses, so the two can never drift apart again, and
 * that nothing in this file draws its own pulse any more.
 *
 * The one exception is asserted rather than waved past. The dock placeholder is still a
 * hand-rolled `animate-pulse`, because it belongs to another unit — ledger PR 176, which
 * removes the double chrome it is half of — and two units opening the same block in
 * parallel is how a rebase eats one of them. Pinning it to exactly one occurrence, on the
 * dock's own line, is what keeps "this file stopped hand-rolling pulses" a real assertion
 * while that block waits its turn.
 */

import React from "react";
import { cleanup, render } from "@testing-library/react";
import { readFileSync } from "node:fs";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import AppShellSkeleton from "@/components/app/AppShellSkeleton";

afterEach(cleanup);

const SOURCE = readFileSync(
  path.resolve(__dirname, "../../src/components/app/AppShellSkeleton.tsx"),
  "utf8",
);

function renderShell() {
  return render(<AppShellSkeleton />).container;
}

describe("AppShellSkeleton", () => {
  it("draws its task rows with the feed's own skeleton", () => {
    const container = renderShell();
    const group = container.querySelector('[aria-busy="true"]');

    expect(group, "the shell no longer renders TaskRowSkeletonGroup").not.toBeNull();
    expect(group!.children).toHaveLength(3);
    // The row's own box, copied from `TodoItemContainer` by `TaskRowSkeleton` and not by
    // this file — which is the whole point of routing through it.
    expect(group!.children[0].className).toContain("px-1");
    expect(group!.children[0].className).toContain("py-2.5");
  });

  it("no longer draws the card the feed does not have", () => {
    const container = renderShell();

    // Both halves, because the drift can come from either side: the group could be
    // swapped back out, or a second hand-written stack could grow beside it.
    expect(container.innerHTML).not.toContain("h-[62px]");
    // The card's 16 px corner answers to `rounded-lg` since the radius ladder was
    // renamed onto its rungs, and the rows this shell renders legitimately carry
    // `sm:rounded-lg` — so the match is anchored to a bare token, not a substring.
    expect(container.innerHTML).not.toMatch(/(?<![\w:[-])rounded-lg/);
  });

  it("keeps the header, hero and tile placeholders at their own sizes", () => {
    const container = renderShell();
    const classes = [...container.querySelectorAll("div")].map((el) => el.className);

    // Routing them through the `Skeleton` primitive was meant to stop the file
    // hand-rolling a pulse, not to redesign it: every size and radius that was here is
    // still here, and each one pulses because the primitive does it.
    for (const box of [
      "h-7 w-24",
      "h-10 w-10",
      "h-[70px]",
      "h-[94px]",
    ]) {
      const match = classes.find((name) => name.includes(box));
      expect(match, `${box} placeholder is gone`).toBeDefined();
      expect(match).toContain("animate-pulse");
    }
  });

  it("hand-rolls exactly one pulse, and it is the dock's", () => {
    const occurrences = SOURCE.split("\n").filter((line) => line.includes("animate-pulse"));

    expect(occurrences).toHaveLength(1);
    // Unchanged in shape, so this unit is visibly not standing on PR 176's row.
    expect(occurrences[0]).toContain("h-16 w-44");
    expect(occurrences[0]).toContain("rounded-[25px]");
    expect(occurrences[0]).toContain("bg-muted/60");
  });

  it("says why it is the one loading surface that does not crossfade", () => {
    // A reader who has seen `useSkeletonCrossfade` on every other placeholder will ask,
    // and the answer — React swaps a Suspense fallback without rendering this component
    // again — is not one anybody should have to re-derive.
    expect(SOURCE).toContain("useSkeletonCrossfade");
    expect(SOURCE).toContain("Suspense");
  });
});
