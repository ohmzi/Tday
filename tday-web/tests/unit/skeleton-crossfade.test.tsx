// @vitest-environment jsdom

/**
 * The frame where a skeleton stops being the truth.
 *
 * Every loading state on web ended in one frame. The placeholder was rendered behind a
 * `loading` flag, so the flag going false took the grey bars off the screen and put the
 * real content up in the same paint — the app's most-seen transition, and the only one it
 * never drew. `useSkeletonCrossfade` keeps the placeholder mounted past the flip so
 * `.tday-skeleton-exit` has a beat to play in.
 *
 * jsdom applies no stylesheet and lays nothing out, so nothing here can see the fade
 * itself. What it can pin is everything the fade depends on: that the node is still there
 * to be faded, that it carries the class that fades it, that it is gone once the rung has
 * elapsed, and that a reduced-motion user gets none of that wait. The second suite reads
 * source text, for the claims a render cannot make — that the duration in the stylesheet
 * is the token and not a number; that the class never lands on an element that also
 * carries `animate-pulse`, which is the one spelling that would smuggle the whole fade
 * past the blanket reduced-motion floor via the exemption that keeps busy indicators
 * moving; that the exiting box outranks the rows it fades over and gives back its margin
 * with its height; and that the call sites render this rather than gating it, which is
 * the one way the crossfade can be perfectly correct here and inert on every screen that
 * ships it.
 */

import React from "react";
import { readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import { act, cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import TodoListLoading from "@/components/todo/component/TodoListLoading";
import { DURATION_MS } from "@/lib/motion";
import { installReducedMotion } from "../setup/reduced-motion";

const SRC = path.resolve(__dirname, "..", "..", "src");
const read = (relative: string) => readFileSync(path.join(SRC, relative), "utf-8");
const tsxFilesUnder = (dir: string): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return tsxFilesUnder(full);
    return entry.name.endsWith(".tsx") ? [full] : [];
  });

const skeletonOf = (container: HTMLElement) => container.querySelector('[aria-busy="true"]');
const exitingOf = (container: HTMLElement) => container.querySelector(".tday-skeleton-exit");

describe("the skeleton crossfades out instead of vanishing", () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
    window.matchMedia = originalMatchMedia;
    cleanup();
  });

  it("stays in the document carrying the exit class once loading goes false, and leaves on the Enter rung", async () => {
    const { container, rerender } = render(<TodoListLoading loading />);
    expect(skeletonOf(container), "nothing was drawn while loading").not.toBeNull();
    expect(exitingOf(container), "a placeholder that is still the truth is not leaving").toBeNull();

    rerender(<TodoListLoading loading={false} />);
    // The frame the flag flips: the content has taken the slot and the placeholder is
    // still on screen, fading over it. This is the whole point of the hook.
    expect(skeletonOf(container), "the placeholder vanished in the frame the flag flipped").not.toBeNull();
    expect(exitingOf(container), "it lingered without the class that fades it").not.toBeNull();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(DURATION_MS.enter - 50);
    });
    expect(skeletonOf(container), "it left before its own fade had finished").not.toBeNull();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(50);
    });
    expect(skeletonOf(container), "it outstayed the fade and is now a ghost").toBeNull();
    expect(container.innerHTML).toBe("");
  });

  it("no element ever carries the exit class and animate-pulse at once", () => {
    const { container, rerender } = render(<TodoListLoading loading />);
    rerender(<TodoListLoading loading={false} />);

    const exiting = exitingOf(container);
    expect(exiting).not.toBeNull();
    // The floor exempts `.animate-pulse` per element — a frozen busy indicator lies — so
    // an element carrying both would keep its 200 ms fade for a user who asked for none.
    expect(
      exiting?.classList.contains("animate-pulse"),
      "the crossfade wrapper is also the pulsing bar",
    ).toBe(false);
    expect(
      container.querySelectorAll(".animate-pulse").length,
      "the bars underneath stopped pulsing",
    ).toBeGreaterThan(0);
  });

  it("removes the placeholder on the same flip under prefers-reduced-motion — no lingering node, no wait", () => {
    installReducedMotion(true);

    const { container, rerender } = render(<TodoListLoading loading />);
    expect(skeletonOf(container)).not.toBeNull();

    rerender(<TodoListLoading loading={false} />);
    expect(skeletonOf(container), "a reduced-motion user is waiting out a fade they cannot see").toBeNull();
    expect(exitingOf(container)).toBeNull();
    expect(container.innerHTML).toBe("");
  });
});

describe("what a render in jsdom cannot show — read off the source", () => {
  const globals = read("globals.css");

  /** The `.tday-skeleton-exit` rule body, which is the only text these two claims are about. */
  const exitRule = (() => {
    const at = globals.indexOf(".tday-skeleton-exit {");
    expect(at, "globals.css no longer declares .tday-skeleton-exit").toBeGreaterThan(-1);
    return globals.slice(at, globals.indexOf("}", at));
  })();

  it("spells its duration as the Enter token", () => {
    expect(exitRule).toMatch(/animation-duration:\s*var\(--tday-duration-enter\)/);
    expect(exitRule).toMatch(/animation-timing-function:\s*var\(--tday-ease-standard\)/);
  });

  it("spells no millisecond literal at all — not 200, and above all not 190", () => {
    // The ledger row asked for 190. `docs/motion.md` settled that rung at 200, and a
    // number typed here would be a raw literal against a `cssMsLiteral` ceiling of 2.
    expect(exitRule).not.toMatch(/\d+ms/);
  });

  it("clears the z-index the arriving rows carry, or it fades underneath them", () => {
    const declared = /z-index:\s*(\d+)/.exec(exitRule);
    expect(declared, "the exiting placeholder declares no z-index at all").not.toBeNull();
    // Not a round number picked for looks: a row's content is `relative z-10` over a
    // wrapper that is `relative` with no z-index, so the 10 competes in the same stacking
    // context this rule does and anything below it fades UNDER its own replacement.
    expect(read("components/todo/component/TodoItemContainer.tsx")).toContain("relative z-10");
    expect(read("features/floater/component/FloaterItemContainer.tsx")).toContain("relative z-10");
    expect(
      Number(declared?.[1]),
      "the bars fade under the rows they stood in for",
    ).toBeGreaterThan(10);
  });

  it("gives back its margin as well as its height", () => {
    // `TodoListLoading` carries its callers' `mt-5` / `mt-8`. Without a block formatting
    // context that margin collapses out through a zero-height box and holds 20-32 px open
    // for the length of the fade — the height handed back and the space kept.
    expect(exitRule).toMatch(/display:\s*flow-root/);
    expect(exitRule).toMatch(/height:\s*0/);
  });

  it("is rendered by every caller rather than gated by one", () => {
    // The defect this whole file exists to stop, and the one shape no render of the
    // component can show: `{loading && <TodoListLoading />}` unmounts the placeholder in
    // the frame it is supposed to fade out in, and the crossfade goes silently inert.
    const callers = tsxFilesUnder(SRC).filter(
      (file) => !file.endsWith(path.join("component", "TodoListLoading.tsx")),
    );

    let sites = 0;
    for (const file of callers) {
      const text = readFileSync(file, "utf-8");
      for (const match of text.matchAll(/<TodoListLoading\b[^>]*>/g)) {
        sites += 1;
        const where = path.relative(SRC, file);
        expect(match[0], `${where} renders the placeholder without a loading flag`).toMatch(
          /loading=\{/,
        );
        expect(
          text.slice(Math.max(0, (match.index ?? 0) - 60), match.index),
          `${where} gates the mount, so the placeholder is unmounted before it can fade`,
        ).not.toMatch(/(&&|\?)\s*$/);
      }
    }
    expect(sites, "no call site was read at all").toBeGreaterThan(0);
  });

  it("keeps the pulse and the fade in different files, so they can never be one element", () => {
    // Comments are blanked first, the same rule the motion-reachability suites read by:
    // both files argue about the trap in prose, and prose describing a defect must not
    // read as the defect. `TaskRowSkeleton` owns the pulsing bars and names no fade;
    // `TodoListLoading` owns the fading wrapper and draws no bar of its own.
    const code = (relative: string) =>
      read(relative).replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, "");
    expect(code("components/ui/TaskRowSkeleton.tsx")).not.toContain("tday-skeleton-exit");
    expect(code("components/todo/component/TodoListLoading.tsx")).not.toContain("animate-pulse");
  });
});
