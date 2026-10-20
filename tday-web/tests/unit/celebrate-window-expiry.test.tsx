// @vitest-environment jsdom

/**
 * The celebration window's ENDING, which until now was not an event.
 *
 * Both halves of `celebrate` are a timestamp compared against `Date.now()` in a
 * render body: true while the window is open, false once it is not. Read like
 * that, the window has no end of its own — it ends whenever something unrelated
 * next re-renders the component and the comparison happens to come out false. On
 * the two screens where the window is holding the empty scene on a slot Earlier's
 * own rows otherwise own, that meant the scene sat there past its welcome and
 * then vanished on a keystroke, in the frame of a render that was about nothing
 * to do with it.
 *
 * So these tests never re-render the hook themselves. Every assertion below is
 * "advance the clock and nothing else" — which is exactly the thing the old code
 * could not do, and the only way to tell a window that closes from one that is
 * merely observed closed.
 */

import { act, cleanup, render, renderHook } from "@testing-library/react";
import { useEffect } from "react";
import { Leaf } from "lucide-react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useCelebrateEmptyTransition } from "@/hooks/use-celebrate-empty-transition";
import {
  CELEBRATION_WINDOW_MS,
  markTaskCompleted,
  useTaskJustCompleted,
} from "@/lib/task-completion-signal";
import TimelineEmptyState from "@/features/todayTodos/component/TimelineEmptyState";
import {
  OVERDUE_ROWS_FADE_MS,
  TODAY_EARLIER_EXIT_MS,
  shouldShowTodayEmptyIllustration,
} from "@/features/todayTodos/lib/todayEarlierIllustration";
import {
  useCelebrationSceneExit,
  useEarlierExpandHandoff,
} from "@/features/todayTodos/lib/useEarlierExpandHandoff";

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("useTaskJustCompleted", () => {
  it("closes on its own clock, with nothing else re-rendering", async () => {
    markTaskCompleted();
    const { result } = renderHook(() => useTaskJustCompleted());
    expect(result.current).toBe(true);

    // Short of the deadline by half a second rather than by a millisecond:
    // `shouldAdvanceTime` also moves the mocked clock by however long the test
    // itself really takes, and an assertion that would flip on a slow CI box is
    // worse than one with slack in it. Nothing here is measuring the boundary
    // to the frame — what is being measured is that the window shuts on a timer
    // at all, with no other render in the picture.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(CELEBRATION_WINDOW_MS - 500);
    });
    expect(result.current).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });
    expect(result.current).toBe(false);
  });

  it("arms nothing for a window that is already shut", async () => {
    markTaskCompleted();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(CELEBRATION_WINDOW_MS);
    });

    // Mounting into a closed window must not schedule a timer for a deadline in
    // the past — a `setTimeout(…, negative)` fires on the next tick, every
    // render, which is how a hook like this turns into a re-render loop.
    const { result } = renderHook(() => useTaskJustCompleted());
    expect(result.current).toBe(false);
    expect(vi.getTimerCount()).toBe(0);
  });
});

describe("useCelebrateEmptyTransition", () => {
  it("closes its own window too — the remote half is the same window", () => {
    // Both halves feed one `celebrate`, so an end only one of them observes is
    // not an end: the OR stays true on the other's stale read.
    const { result, rerender } = renderHook(({ isEmpty }) => useCelebrateEmptyTransition(isEmpty), {
      initialProps: { isEmpty: false },
    });
    expect(result.current).toBe(false);

    rerender({ isEmpty: true });
    expect(result.current).toBe(true);

    act(() => {
      vi.advanceTimersByTime(CELEBRATION_WINDOW_MS);
    });
    expect(result.current).toBe(false);
  });
});

describe("useCelebrationSceneExit", () => {
  type Props = { celebrate: boolean; sceneLeavesWithTheWindow: boolean };

  function mount(initialProps: Props) {
    const beginSceneExit = vi.fn();
    const view = renderHook(
      (props: Props) => useCelebrationSceneExit({ ...props, beginSceneExit }),
      { initialProps },
    );
    return { ...view, beginSceneExit };
  }

  it("plays the scene out when the window shuts under it", () => {
    const { rerender, beginSceneExit } = mount({
      celebrate: true,
      sceneLeavesWithTheWindow: true,
    });
    expect(beginSceneExit).not.toHaveBeenCalled();

    rerender({ celebrate: false, sceneLeavesWithTheWindow: true });
    expect(beginSceneExit).toHaveBeenCalledTimes(1);
  });

  it("plays nothing on the edge that opens the window", () => {
    const { rerender, beginSceneExit } = mount({
      celebrate: false,
      sceneLeavesWithTheWindow: true,
    });

    rerender({ celebrate: true, sceneLeavesWithTheWindow: true });
    expect(beginSceneExit).not.toHaveBeenCalled();
  });

  it("plays nothing where the window shutting takes nothing off the screen", () => {
    // Earlier collapsed, or the scope no longer empty: the scene is either
    // staying or already being replaced by something else. Playing it out of a
    // slot it is about to be handed back would end the beat by putting it
    // straight back on.
    const { rerender, beginSceneExit } = mount({
      celebrate: true,
      sceneLeavesWithTheWindow: false,
    });

    rerender({ celebrate: false, sceneLeavesWithTheWindow: false });
    expect(beginSceneExit).not.toHaveBeenCalled();
  });

  it("plays once, not once per render, for one closing", () => {
    const { rerender, beginSceneExit } = mount({
      celebrate: true,
      sceneLeavesWithTheWindow: true,
    });

    rerender({ celebrate: false, sceneLeavesWithTheWindow: true });
    rerender({ celebrate: false, sceneLeavesWithTheWindow: true });
    expect(beginSceneExit).toHaveBeenCalledTimes(1);
  });
});

/**
 * The departure the window's ending is supposed to buy, asserted on the DOM
 * rather than on the call that starts it.
 *
 * `useCelebrationSceneExit`'s own tests above say `beginSceneExit` is called,
 * which is true of a version that plays no departure at all: the window closing
 * is the same render that makes `celebrate` false, so a beat armed one commit
 * later arrives to find the scene already unmounted and hangs its exit classes
 * on a brand new element. A fresh node is the whole defect — its track's FIRST
 * computed style is the closed one, so the 42vh goes in a frame rather than
 * over a transition, and `.tday-empty-enter` restarts on the thing that is
 * leaving. None of that is visible from the mock; all of it is visible from
 * asking whether the element is the same element.
 */
describe("the scene the window was holding", () => {
  /**
   * The one screen shape where the window closing takes something off the
   * screen: empty scope, Earlier open under the scene, the scene on the slot
   * for the length of the celebration and nothing else. Composed from the real
   * pieces rather than the containers, which would need a scope's worth of
   * query mocking to reach the same four lines.
   */
  function EarlierSceneScreen() {
    const { expanded, handoff, beginSceneExit, setExpandedImmediately } = useEarlierExpandHandoff(
      TODAY_EARLIER_EXIT_MS,
      OVERDUE_ROWS_FADE_MS,
    );
    const celebrate = useTaskJustCompleted();
    const sceneHeldForExit = useCelebrationSceneExit({
      celebrate,
      sceneLeavesWithTheWindow: expanded,
      beginSceneExit,
    });
    const showScene = shouldShowTodayEmptyIllustration({
      showEmpty: true,
      hasEarlierItems: true,
      earlierExpanded: expanded,
      earlierHandoff: handoff,
      celebrate,
      sceneHeldForExit,
    });

    useEffect(() => {
      setExpandedImmediately(true);
    }, [setExpandedImmediately]);

    if (!showScene) return null;
    return (
      <TimelineEmptyState
        icon={Leaf}
        accentColor="#22c55e"
        isDayDone={false}
        celebrate={celebrate}
        earlierHandoff={handoff}
        locale="en-US"
        emptyTitle="allDone"
        emptyBody="allDoneBody"
        appDict={(key) => key}
      />
    );
  }

  async function mountUnderAnOpenWindow() {
    markTaskCompleted();
    const view = render(<EarlierSceneScreen />);
    // One turn for the expand effect, which is what makes this the shape where
    // the window is the only thing holding the scene.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    return view;
  }

  it("hangs the departure on the node that was already there", async () => {
    const { container } = await mountUnderAnOpenWindow();
    const slot = container.querySelector(".tday-empty-slot");
    const ink = container.querySelector(".tday-empty-enter");
    expect(slot).not.toBeNull();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(CELEBRATION_WINDOW_MS + 10);
    });

    // Same element, not an equivalent one. A replacement would satisfy every
    // class assertion below and still cut.
    expect(container.querySelector(".tday-empty-slot")).toBe(slot);
    expect(container.querySelector(".tday-empty-enter")).toBe(ink);
    expect(slot?.classList.contains("tday-empty-exit")).toBe(true);
    expect(slot?.classList.contains("tday-empty-slot-closing")).toBe(true);
  });

  it("gives the slot up once the beat it waited for has played", async () => {
    const { container } = await mountUnderAnOpenWindow();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(CELEBRATION_WINDOW_MS + 10);
    });
    expect(container.querySelector(".tday-empty-slot")).not.toBeNull();

    // The hold is one render, not a second window: the scene leaves at the end
    // of the exit it is now playing, and Earlier's rows have the slot.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TODAY_EARLIER_EXIT_MS + 10);
    });
    expect(container.querySelector(".tday-empty-slot")).toBeNull();
  });
});
