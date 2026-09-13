// @vitest-environment jsdom

/**
 * The empty scene's wrapper is its SLOT — the one-row grid track the Earlier
 * hand-off closes — and not just a box around it. jsdom applies no stylesheet,
 * so nothing here can measure a track; what it can pin is that the classes the
 * stylesheet hangs all of that off reach the DOM at all, and that the two of
 * them go on independently. Both are the part a refactor of this file drops
 * silently: the scene still renders, the tests still pass, and the 42vh goes
 * back to arriving and leaving in one frame.
 *
 * The geometry itself is argued in `earlierHandoffVacatesSlot` and asserted
 * against its own inputs in `today-earlier-illustration.test.ts`; this is the
 * wiring between the two.
 */

import { cleanup, render } from "@testing-library/react";
import { Leaf } from "lucide-react";
import { afterEach, describe, expect, it } from "vitest";

import TimelineEmptyState from "@/features/todayTodos/component/TimelineEmptyState";

afterEach(cleanup);

function renderSlot({
  celebrate = false,
  earlierHandoffPending = false,
}: {
  celebrate?: boolean;
  earlierHandoffPending?: boolean;
} = {}) {
  const { container } = render(
    <TimelineEmptyState
      icon={Leaf}
      accentColor="#22c55e"
      isDayDone={false}
      celebrate={celebrate}
      earlierHandoffPending={earlierHandoffPending}
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
    const slot = renderSlot({ earlierHandoffPending: true });
    expect(slot.classList.contains("tday-empty-slot")).toBe(true);
    expect(slot.classList.contains("tday-empty-exit")).toBe(true);
    expect(slot.classList.contains("tday-empty-slot-closing")).toBe(true);
  });

  it("fades its ink but holds its track when the scene is going to stay", () => {
    // Mid hand-off inside the celebration window: the scene is still on screen
    // when the beat ends, so a track closed here would reopen a frame later and
    // shove Earlier's newly arrived rows down the height it had just taken.
    const slot = renderSlot({ earlierHandoffPending: true, celebrate: true });
    expect(slot.classList.contains("tday-empty-exit")).toBe(true);
    expect(slot.classList.contains("tday-empty-slot-closing")).toBe(false);
  });

  it("holds no exit class on a celebrating scene that is not handing over", () => {
    const slot = renderSlot({ celebrate: true });
    expect(slot.classList.contains("tday-empty-exit")).toBe(false);
    expect(slot.classList.contains("tday-empty-slot-closing")).toBe(false);
  });
});
