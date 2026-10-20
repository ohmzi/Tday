// @vitest-environment jsdom

/**
 * The empty scene's wrapper is its SLOT — the one-row grid track the Earlier
 * hand-off closes — and not just a box around it. jsdom applies no stylesheet,
 * so nothing here can measure a track; what it can pin is that the classes the
 * stylesheet hangs all of that off reach the DOM at all, and that the two of
 * them go on TOGETHER. Both are the part a refactor of this file drops
 * silently: the scene still renders, the tests still pass, and the 42vh goes
 * back to arriving and leaving in one frame.
 *
 * The departure itself is argued in `emptySceneIsLeaving` and asserted against
 * its own inputs in `today-earlier-illustration.test.ts`; this is the wiring
 * between the two.
 */

import { cleanup, render } from "@testing-library/react";
import { Leaf } from "lucide-react";
import { afterEach, describe, expect, it } from "vitest";

import TimelineEmptyState from "@/features/todayTodos/component/TimelineEmptyState";
import type { EarlierHandoff } from "@/features/todayTodos/lib/useEarlierExpandHandoff";

afterEach(cleanup);

function renderSlot({
  celebrate = false,
  earlierHandoff = "idle",
}: {
  celebrate?: boolean;
  earlierHandoff?: EarlierHandoff;
} = {}) {
  const { container } = render(
    <TimelineEmptyState
      icon={Leaf}
      accentColor="#22c55e"
      isDayDone={false}
      celebrate={celebrate}
      earlierHandoff={earlierHandoff}
      locale="en-US"
      emptyTitle="allDone"
      emptyBody="allDoneBody"
      appDict={(key) => key}
    />,
  );
  return container.firstElementChild as HTMLElement;
}

describe("the empty scene's slot", () => {
  it("is a slot at rest, with nothing for the transition to run on", () => {
    const slot = renderSlot();
    expect(slot.classList.contains("tday-empty-slot")).toBe(true);
    expect(slot.classList.contains("tday-empty-exit")).toBe(false);
    expect(slot.classList.contains("tday-empty-slot-closing")).toBe(false);
  });

  it("fades its ink and closes its track together on an ordinary hand-off", () => {
    const slot = renderSlot({ earlierHandoff: "scene-leaving" });
    expect(slot.classList.contains("tday-empty-slot")).toBe(true);
    expect(slot.classList.contains("tday-empty-exit")).toBe(true);
    expect(slot.classList.contains("tday-empty-slot-closing")).toBe(true);
  });

  it("draws no departure while the ROWS are the ones leaving", () => {
    // A collapse: the scene is not on the slot yet and has nothing to exit
    // from. In production it is not even mounted here — the beat is exactly
    // the window in which it waits — so a class on this path would be a scene
    // sinking on its way IN.
    const slot = renderSlot({ earlierHandoff: "rows-leaving" });
    expect(slot.classList.contains("tday-empty-exit")).toBe(false);
    expect(slot.classList.contains("tday-empty-slot-closing")).toBe(false);
  });

  it("holds no exit class on a celebrating scene, which is not going anywhere", () => {
    const slot = renderSlot({ celebrate: true });
    expect(slot.classList.contains("tday-empty-exit")).toBe(false);
    expect(slot.classList.contains("tday-empty-slot-closing")).toBe(false);
  });

  it("never carries one half of the departure without the other", () => {
    // The fade-then-snap defect was exactly this asymmetry: the ink left, the
    // track stayed, and then the class came off a scene that had not gone
    // anywhere. Asserted across every combination rather than only at the one
    // that used to disagree, so a condition added to either name has to be
    // added to both.
    for (const earlierHandoff of ["idle", "scene-leaving", "rows-leaving"] as EarlierHandoff[]) {
      for (const celebrate of [false, true]) {
        const slot = renderSlot({ earlierHandoff, celebrate });
        const state = { earlierHandoff, celebrate };
        expect({ ...state, ink: slot.classList.contains("tday-empty-exit") }).toEqual({
          ...state,
          ink: slot.classList.contains("tday-empty-slot-closing"),
        });
        cleanup();
      }
    }
  });
});
