import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Both native calendars disable their chevrons while a page change is in flight, and on both the
 * only thing that ever re-enables them is a flag being put back. That makes these flags controls
 * rather than bookkeeping: a path that sets one and never clears it does not drop a frame, it takes
 * the calendar's header navigation away for the rest of the session, with no gesture anywhere in
 * the app that gives it back.
 *
 * Android's half is provable by `:app:testDebugUnitTest` and is — see
 * `CalendarPagerScrollRequestTest`. Neither iOS path is provable by anything this repository can
 * run: they are UIKit delegate callbacks, so reaching them needs a scroll view, a run loop and a
 * finger, and `xctest` on CI has the first two at best. This file stands in for that. Reading the
 * source is enough here because both iOS defects were the *absence* of a clearing path rather than
 * a wrong value inside one, and an absence is exactly what a static read can see.
 *
 * The Android assertions below cover the hygiene half of the same PR — the `coroutineScope.launch`
 * that used to wrap the `scrollRequest` write and put the guard's read and the guarded write on
 * different dispatches.
 */

const MONO = resolve(__dirname, "..", "..", "..");

const ANDROID_PAGER = resolve(
  MONO,
  "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/calendar/CalendarPager.kt",
);
const ANDROID_SCREEN = resolve(
  MONO,
  "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/calendar/CalendarScreen.kt",
);
const IOS_PAGER = resolve(
  MONO,
  "ios-swiftUI/Tday/Feature/Calendar/CalendarPagingScrollView.swift",
);

/**
 * Reads a source file with its comments removed.
 *
 * These files explain themselves at length, and the explanations quote the very constructs being
 * asserted about — a comment saying why there is no `launch` here would otherwise read as a
 * `launch`. Stripping first means every assertion below is about code.
 */
function readCode(file: string): string {
  return readFileSync(file, "utf8")
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((line) => !line.trimStart().startsWith("//"))
    .join("\n");
}

/**
 * Returns the brace-delimited block that opens after `anchor`, anchor included.
 *
 * Brace counting is naive — it does not know about braces inside strings — which is safe for the
 * blocks this file points at, none of which contain one. A miscount truncates the block and the
 * assertion using it fails, so the failure mode is a red test rather than a quiet pass.
 */
function blockAfter(source: string, anchor: string): string {
  const anchorAt = source.indexOf(anchor);
  if (anchorAt < 0) return "";
  const open = source.indexOf("{", anchorAt);
  if (open < 0) return "";

  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === "{") depth += 1;
    else if (source[i] === "}") {
      depth -= 1;
      if (depth === 0) return source.slice(anchorAt, i + 1);
    }
  }
  return "";
}

describe("the Android pager clears a scroll request it could not finish", () => {
  const code = readCode(ANDROID_PAGER);
  const runner = blockAfter(code, "internal suspend fun runCalendarPagerScrollRequest(");

  it("keeps the request runner the pager's effect delegates to", () => {
    expect(runner).not.toBe("");
    expect(code).toContain("runCalendarPagerScrollRequest(\n            requestId");
  });

  it("reports the request handled from a finally, not after the animation", () => {
    // `animateScrollToPage` holds the pager's scroll mutex at MutatePriority.Default; a finger
    // takes the same mutex at UserInput and cancels it. A report written after that call is simply
    // skipped when the CancellationException unwinds — the exact path that left both chevrons
    // dead — so the report has to sit where cancellation still runs it.
    expect(runner).toContain("finally {");
    expect(runner.indexOf("onHandled(requestId)")).toBeGreaterThan(runner.indexOf("finally {"));
  });

  it("reports it from nowhere else", () => {
    expect(runner.match(/onHandled\(/g) ?? []).toHaveLength(1);
  });
});

describe("the Android chevrons write their scroll request synchronously", () => {
  const code = readCode(ANDROID_SCREEN);

  it("never defers the write behind a coroutine launch", () => {
    // `requestPage` reads `isPagingAtRest` and then writes the request that makes it false. A
    // `coroutineScope.launch` between the two put them on different dispatches, so two taps inside
    // one frame both passed a guard neither had yet closed.
    expect(/coroutineScope\.launch\s*\{[^}]*scrollRequest\s*=/s.test(code)).toBe(false);
  });

  it("writes the request directly in every requestPage", () => {
    const bodies = code
      .split("fun requestPage(offset: Int) {")
      .slice(1)
      .map((rest) => rest.slice(0, rest.indexOf("\n    }")));

    // Week, day and month cards own one each. A fourth pager is covered the moment it appears, and
    // a missing one is noticed here too.
    expect(bodies).toHaveLength(3);
    for (const body of bodies) {
      expect(body).toContain("scrollRequest = CalendarPagerScrollRequest(");
      expect(body).not.toContain("launch");
    }
  });
});

describe("the iOS pager can always let go of a programmatic scroll", () => {
  const code = readCode(IOS_PAGER);

  it("clears the flags when a touch takes the scroll view", () => {
    // UIKit stops an animated `setContentOffset` the moment a finger lands and sends no
    // `scrollViewDidEndScrollingAnimation` for the animation it discarded. Before this callback
    // existed that was the only clearing path, so `isProgrammaticScroll` stayed true forever and
    // `updateSelection` declined every drag that followed.
    const dragging = blockAfter(code, "func scrollViewWillBeginDragging(");
    expect(dragging).not.toBe("");
    expect(dragging).toContain("endProgrammaticScroll()");
  });

  it("clears the flags on the un-animated path that returns early", () => {
    // "Already parked on the target" means nothing will scroll, so no delegate callback is coming
    // to clear anything. Returning silently is how a flag set by an earlier animated request
    // outlived the animation that was meant to clear it.
    const alreadyThere = blockAfter(
      code,
      "guard abs(scrollView.contentOffset.x - targetX) > 0.5 else",
    );
    expect(alreadyThere).not.toBe("");
    expect(alreadyThere).toContain("endProgrammaticScroll()");
  });

  it("clears the flags when the pages the scroll was aimed at are rebuilt", () => {
    const rebuild = blockAfter(code, "func rebuildPagesIfNeeded(");
    expect(rebuild).toContain("endProgrammaticScroll()");
  });

  it("routes every clear through one method", () => {
    // Keeping the exits in one place is what makes "how many ways can this latch open" a question
    // with a readable answer. Clearing inline is how that list got short enough to be wrong.
    // Anchored to the start of a line so the `private var … = false` declaration, which is a
    // starting value rather than a clear, is not counted as one of the exits.
    expect(code.match(/^\s*isProgrammaticScroll\s*=\s*false/gm) ?? []).toHaveLength(1);
    const reset = blockAfter(code, "private func endProgrammaticScroll()");
    expect(reset).toContain("isProgrammaticScroll = false");
    expect(reset).toContain("programmaticSelection = nil");
  });
});
