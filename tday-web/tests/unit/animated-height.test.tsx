// @vitest-environment jsdom

/**
 * `AnimatedHeight` is the app's only height transition, and the thing that
 * makes it one is not the class string — it is the measurement. jsdom lays
 * nothing out and applies no stylesheet, so what these tests pin is the half a
 * refactor actually breaks: that the box is handed a NUMBER to transition
 * towards, that the number follows the content rather than being taken once,
 * and that the element being measured is not the element being animated.
 *
 * A box whose height is never written still renders its children perfectly,
 * which is exactly why the regression is invisible in a screenshot: the
 * content is all there, it just arrives in one frame.
 */

import { act, cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AnimatedHeight from "@/components/ui/AnimatedHeight";

/**
 * What the content reports as its own height. jsdom answers 0 for every
 * `scrollHeight`, so the property is replaced for the length of the file —
 * without it every assertion below would be comparing "0px" to "0px" and
 * would pass on a component that had stopped measuring anything.
 */
let contentHeight = 0;

class StubResizeObserver {
  static live: StubResizeObserver[] = [];
  readonly observed: Element[] = [];
  disconnected = false;

  constructor(readonly callback: () => void) {
    StubResizeObserver.live.push(this);
  }

  observe(element: Element) {
    this.observed.push(element);
  }

  disconnect() {
    this.disconnected = true;
  }
}

/** The box in the layout — the element that carries the transition. */
const boxOf = (container: HTMLElement) => container.firstElementChild as HTMLElement;
/** Its single child, which is what gets measured. */
const contentOf = (container: HTMLElement) => boxOf(container).firstElementChild as HTMLElement;

const originalScrollHeight = Object.getOwnPropertyDescriptor(
  HTMLElement.prototype,
  "scrollHeight",
);

beforeEach(() => {
  contentHeight = 0;
  StubResizeObserver.live = [];
  vi.stubGlobal("ResizeObserver", StubResizeObserver);
  Object.defineProperty(HTMLElement.prototype, "scrollHeight", {
    configurable: true,
    get: () => contentHeight,
  });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  if (originalScrollHeight) {
    Object.defineProperty(HTMLElement.prototype, "scrollHeight", originalScrollHeight);
  }
});

describe("AnimatedHeight", () => {
  it("writes the content's measured height onto the box, because `auto` cannot be transitioned", () => {
    contentHeight = 240;
    const { container } = render(
      <AnimatedHeight>
        <p>Sign in</p>
      </AnimatedHeight>,
    );

    expect(boxOf(container).style.height).toBe("240px");
  });

  it("follows the content when it changes size, rather than measuring once at mount", () => {
    contentHeight = 240;
    const { container } = render(
      <AnimatedHeight>
        <p>Sign in</p>
      </AnimatedHeight>,
    );

    contentHeight = 388;
    act(() => StubResizeObserver.live[0].callback());

    // The whole point of the component: the new number is what the running
    // transition aims at, so the dialog grows instead of jumping.
    expect(boxOf(container).style.height).toBe("388px");
  });

  it("measures the content, not the box it animates", () => {
    contentHeight = 120;
    const { container } = render(
      <AnimatedHeight>
        <p>Sign in</p>
      </AnimatedHeight>,
    );

    // Observing the box would feed the animation its own intermediate frames
    // back as fresh measurements, and the height would chase itself.
    expect(StubResizeObserver.live[0].observed).toEqual([contentOf(container)]);
    expect(StubResizeObserver.live[0].observed).not.toContain(boxOf(container));
  });

  it("renders its children inside the measured element", () => {
    contentHeight = 120;
    const { container } = render(
      <AnimatedHeight>
        <p>Change setup</p>
      </AnimatedHeight>,
    );

    expect(contentOf(container).textContent).toBe("Change setup");
  });

  it("stops observing when it unmounts", () => {
    contentHeight = 120;
    const { unmount } = render(
      <AnimatedHeight>
        <p>Sign in</p>
      </AnimatedHeight>,
    );

    unmount();

    expect(StubResizeObserver.live[0].disconnected).toBe(true);
  });

  it("stays `auto` where there is no ResizeObserver, rather than holding one stale measurement", () => {
    vi.stubGlobal("ResizeObserver", undefined);
    contentHeight = 240;
    const { container } = render(
      <AnimatedHeight>
        <p>Sign in</p>
      </AnimatedHeight>,
    );

    // A single measurement that nothing ever updates is worse than no
    // animation: the box is `overflow-hidden`, so the next thing the content
    // does would be clipped out of sight.
    expect(boxOf(container).style.height).toBe("");
  });
});
