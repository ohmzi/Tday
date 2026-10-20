// @vitest-environment jsdom

/**
 * The squash under the finger on the most-tapped control in the app, and which
 * event it is allowed to be armed from.
 *
 * `TodoCheckbox` armed its pop from `onMouseDown` for most of its life. A desktop
 * browser sends that the instant the button goes down, so on a laptop the control
 * looked right and nobody had a reason to look further. A touch browser does not:
 * it synthesises `mousedown` only after it has decided the touch was not a scroll
 * and not a double-tap — several hundred milliseconds later, after the tap has
 * already been dispatched — or, if the page swallowed the touch, never at all. The
 * phone got the sound, the haptic and the strike, and the one piece of feedback
 * that belongs to the finger itself was the piece it did not get.
 *
 * The fix is one word, which is exactly why it needs a test: the two commits that
 * produced today's code — the pointer move, then a repair for the `mousedown`
 * guard that move had deleted — between them touched no test at all, and the
 * second only exists because nothing was watching after the first.
 *
 * So the four assertions below are the two halves of that history, pinned apart.
 * Three are about WHEN the squash is drawn: it must come from the pointer, it must
 * not come from the mouse, and it must let go on the rung the class transitions
 * over. The fourth is about whether the tap survives at all — `mousedown` is still
 * stopped for dnd-kit's `MouseSensor`, and a future reader who sees a pointer
 * handler doing the interesting work must not conclude the mouse handler beside it
 * is the dead remains of the old mechanism.
 */

import { Check } from "lucide-react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, createEvent, fireEvent, render } from "@testing-library/react";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { DURATION_MS } from "@/lib/motion";

/**
 * The squash. One class, applied and removed by state, transitioned by
 * `duration-quick ease-gesture` on the same element — so the hold this file
 * measures and the transition the browser runs are the same rung, and reading
 * `className` is reading what the control does on screen.
 */
const SQUASH = "scale-125";

/**
 * Renders the `outline-solid` variant with the icon its real call sites pass, and
 * hands back the element the pop lives on.
 *
 * The control is found structurally because it has nothing else to be found by: it
 * is an unlabelled `div` sitting next to a hidden `input`, which is the whole
 * reason the squash has to be armed from a DOM handler rather than driven off the
 * checkbox's own `:active`. A role query here would be the input, and the input is
 * not what moves.
 */
function renderCheckbox() {
  const view = render(
    <TodoCheckbox
      icon={Check}
      complete={false}
      checked={false}
      onChange={() => {}}
      variant="outline-solid"
    />,
  );
  const control = view.container.querySelector("label > div");
  if (!(control instanceof HTMLElement)) {
    throw new Error("outline-solid no longer renders its control as a div under the label");
  }
  return control;
}

beforeEach(() => {
  // Plain fake timers, not `shouldAdvanceTime`: the release below is asserted one
  // millisecond either side of the rung, and a clock that also moves on its own
  // would make that the runner's decision rather than the component's.
  vi.useFakeTimers();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("TodoCheckbox — the pop answers the finger", () => {
  it("squashes on pointerdown, which is the event a touch browser actually sends first", () => {
    const control = renderCheckbox();

    fireEvent.pointerDown(control);

    expect(control.className).toContain(SQUASH);
  });

  it("does not squash on mousedown alone", () => {
    const control = renderCheckbox();

    // The regression this guards is not hypothetical — it is the code that shipped.
    // `mousedown` still reaches this element on a desktop, and on a desktop it
    // arrives beside the pointer event rather than instead of it, so a handler that
    // squashed here too would look perfect on the machine anyone would test on and
    // be exactly as late on a phone as before.
    fireEvent.mouseDown(control);

    expect(control.className).not.toContain(SQUASH);
  });

  it("lets the squash go after Quick, the rung the class transitions over", () => {
    const control = renderCheckbox();

    fireEvent.pointerDown(control);
    expect(control.className).toContain(SQUASH);

    // A frame short of the rung the pop is still out: the hold is the duration, not
    // "some timer eventually fires". Both halves are read from the vocabulary, so a
    // rung that moves moves this test with it and a hold that drifts off the
    // transition beside it fails here.
    act(() => vi.advanceTimersByTime(DURATION_MS.quick - 1));
    expect(control.className).toContain(SQUASH);

    act(() => vi.advanceTimersByTime(1));
    expect(control.className).not.toContain(SQUASH);
  });

  it("still stops the mousedown, which is the drag sensor's guard and not the pop's", () => {
    const control = renderCheckbox();

    // Asserted on the event rather than by reading the source: the rows this
    // checkbox sits in are dnd-kit draggables whose `MouseSensor` activates on
    // `mousedown`, so a tap with a few pixels of travel in it gets read as a
    // drag-start and swallowed unless this element stops the event from reaching
    // the sensor. That is a different job from arming the squash, and the pointer
    // handler above does not do it.
    const mousedown = createEvent.mouseDown(control);
    const stopPropagation = vi.spyOn(mousedown, "stopPropagation");

    fireEvent(control, mousedown);

    expect(stopPropagation).toHaveBeenCalled();
  });
});
