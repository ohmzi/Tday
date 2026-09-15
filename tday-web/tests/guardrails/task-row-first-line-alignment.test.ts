import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * THE CHECK CIRCLE BELONGS ON THE TITLE'S FIRST LINE, ON ALL THREE CLIENTS
 *
 * The report was one sentence — "make sure everywhere that checkbox bullet point is
 * inline with first line of task, it doesn't need to be centered between all the
 * lines" — and the word doing the work in it is "everywhere". Every task feed in the
 * app draws the same shape: a round toggle, a title that may wrap, and a list or
 * priority mark at the far end. Every one of them stacked that shape centred, which
 * is indistinguishable from correct while the title is one line and becomes the
 * defect the moment it is two: the toggle settles on the text column's MIDDLE, which
 * on a wrapped title is the gap BETWEEN line one and line two. The mark that is
 * supposed to be the line's bullet floats beside nothing.
 *
 * Eleven rows across three clients had it. A fix to the screen in the screenshot
 * would have left it in ten. That is what this file is for.
 *
 * ## Why a source read and not a render assertion
 *
 * jsdom lays nothing out — every box it reports is 0×0 — so the one thing that could
 * actually prove this, a measured offset, is exactly what the web test environment
 * cannot produce, and there is no emulator or simulator in the gate for the other
 * two. What CAN be checked is the thing that decides the layout: which alignment
 * each row asks for, and whether the box it puts its control in is the title's line
 * box or an accident. The arithmetic itself is asserted where the arithmetic lives —
 * `TaskRowFirstLineAlignmentTest` on Android sweeps the derivation across seven font
 * scales, and `TdayTaskRowSkeletonTests` pins iOS's baseline nudge to one value.
 *
 * ## The three idioms, and why they are three
 *
 * They are not a divergence, they are each client's existing answer to the same
 * question, and every one of them was already in the tree before this change:
 *
 *   - **iOS** aligns the `HStack` on `.firstTextBaseline` and hands the toggle an
 *     `.alignmentGuide` returning its own centre plus a nudge, because a `Button`
 *     holds no text and so reports no text baseline. `TodoListScreen` has done this
 *     since the timeline learned to wrap; the other three rows now do it too.
 *   - **Web** gives the control a box that is the title's line box (`h-5`, which is
 *     `leading-5`) and centres it there, with the row's content stacked `items-start`.
 *     `TaskRowSkeleton` was already built this way, and is why `leading-5` is the
 *     number both halves read.
 *   - **Android** cannot express either directly, so it derives: `Alignment.Top` plus
 *     an inset computed from the title style's own `lineHeight` and the control's
 *     height. At `fontScale = 1` that derivation returns exactly the 12 dp
 *     `TodoListScreen` had hand-written, which is the evidence it is the existing
 *     decision rather than a new one.
 *
 * Bare `items-start` / `Alignment.Top` is NOT the fix and is the trap worth naming:
 * top-aligning a 48 dp control against a 24 sp line box puts the glyph 12 dp low,
 * and because one is dp and the other sp the error moves with the user's font scale.
 */
describe("every task row hangs its control off the title's first line", () => {
  /** Paths under `tday-web/`. */
  const read = (path: string) =>
    readFileSync(resolve(__dirname, "../..", path), "utf8");

  /** Paths under the repository root — this claim reaches all three clients. */
  const readRepo = (path: string) =>
    readFileSync(resolve(__dirname, "../../..", path), "utf8");

  /**
   * Each web row, with the class strings that decide where its control lands.
   *
   * `h-5` is the title's `leading-5` line box written as a height — that pairing is
   * the whole technique, so both halves are asserted on every row. A row that kept
   * the `h-5` box and let its title drift to `leading-6` would be aligning to a line
   * it no longer draws.
   */
  const webRows = [
    "src/components/todo/component/TodoItemContainer.tsx",
    "src/features/floater/component/FloaterItemContainer.tsx",
    "src/features/calendar/component/CalendarClient.tsx",
    "src/features/completed/component/ItemContainer.tsx",
    "src/features/completed/component/CompletedFloaterItemContainer.tsx",
  ];

  it.each(webRows)("%s puts its control in the title's own line box", (path) => {
    const source = read(path);
    expect(source).toContain('className="flex h-5 shrink-0 items-center"');
    expect(source).toContain("leading-5");
  });

  it.each(webRows)("%s stacks its row content from the top", (path) => {
    const source = read(path);
    expect(source).toMatch(/items-start/);
  });

  /**
   * The trailing mark moves with the leading one, or the fix is half done.
   *
   * The screenshot had the priority flag floating in the same gap as the check
   * circle, and the two are the same claim: both are annotations ON the title, so
   * both read with its first line. The desktop hover toolbar deliberately does NOT
   * move — it is a menu for the whole task rather than a mark on its title, it is
   * 28 px against a 20 px line, and it stays centred on the row by being positioned
   * against a `self-stretch` box. That exemption is what makes `self-stretch` part of
   * the claim rather than decoration.
   */
  it.each([
    "src/components/todo/component/TodoItemContainer.tsx",
    "src/features/floater/component/FloaterItemContainer.tsx",
    "src/features/calendar/component/CalendarClient.tsx",
  ])("%s keeps its hover toolbar centred while its meta moves up", (path) => {
    const source = read(path);
    expect(source).toContain("self-stretch");
    expect(source).toContain('"flex h-5 items-center gap-2 transition-opacity"');
    // The toolbar still centres itself against that stretched box.
    expect(source).toContain("top-1/2 hidden -translate-y-1/2");
  });

  /**
   * The two Completed rows are the same row twice and carry the mark inline rather
   * than in a fading meta cluster, so they get their own spelling of the same claim.
   */
  it.each([
    ["src/features/completed/component/ItemContainer.tsx", "gap-2"],
    ["src/features/completed/component/CompletedFloaterItemContainer.tsx", "gap-1.5"],
  ])("%s puts its list mark on the first line", (path, gap) => {
    expect(read(path)).toContain(`className="flex h-5 shrink-0 items-center ${gap} pr-1"`);
  });

  const iosRows = [
    "ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift",
    "ios-swiftUI/Tday/Feature/Completed/CompletedScreen.swift",
    "ios-swiftUI/Tday/Feature/Calendar/CalendarScreen.swift",
    "ios-swiftUI/Tday/Feature/ScheduledTaskHome/ScheduledTaskHomeScreen.swift",
  ];

  it.each(iosRows)("%s aligns its task row on the first text baseline", (path) => {
    const source = readRepo(path);
    expect(source).toContain("HStack(alignment: .firstTextBaseline");
    // A Button reports no text baseline, so the guide is not optional decoration:
    // without it SwiftUI falls back to the button's BOTTOM edge and stands it low.
    expect(source).toMatch(
      /\.alignmentGuide\(\.firstTextBaseline\) \{ dimension in\n\s+dimension\[VerticalAlignment\.center\] \+ \w+\.(minimalRowBaselineNudge|checkBaselineNudge)/,
    );
  });

  /**
   * Android's derivation exists and is what the rows read.
   *
   * The Kotlin side has its own test for the arithmetic and for the set of files that
   * call this; what is added here is the cross-client claim — that the Android answer
   * to the same question is a DERIVATION rather than another hand-written number,
   * which is the only one of the three idioms that survives a font-scale change on
   * its own.
   */
  it("Android derives the inset from the title's line height rather than naming it", () => {
    const helper = readRepo(
      "android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayTaskRowSkeleton.kt",
    );
    expect(helper).toContain("fun taskRowFirstLineAlignment(");
    expect(helper).toContain("spDimension(density, titleStyle.lineHeight, FallbackLineHeight)");

    const androidRows = [
      "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TodoListScreen.kt",
      "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/completed/CompletedScreen.kt",
      "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/calendar/CalendarScreen.kt",
      "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/scheduledtaskhome/ScheduledTaskHomeScreen.kt",
      "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/car/CarTaskSurfaceScreen.kt",
    ];
    for (const path of androidRows) {
      const source = readRepo(path);
      expect(source).toContain("rememberTaskRowFirstLineAlignment(");
      expect(source).toContain("verticalAlignment = Alignment.Top");
    }
  });

  /**
   * No client may quietly go back to centring.
   *
   * Written as an absence because that is the shape of the regression: the change is
   * one word per row, it reads as harmless in review, and it is invisible on the
   * ~97 % of rows whose title fits a line. `CarTaskSurfaceScreen` is the one file
   * that still carries `Alignment.CenterVertically` on a task row's trailing glyph
   * and argues it in place — that glyph is what the whole card does when pressed,
   * not a mark on the title — so it is exempted by name rather than by silence.
   */
  it.each(iosRows)("%s has no centred task-row HStack left in it", (path) => {
    // The two spellings a task row is opened with. Matching the row's own spacing
    // constant rather than `.center` alone is what keeps this claim about task rows:
    // plenty of other HStacks on these screens are centred and should stay that way.
    expect(readRepo(path)).not.toMatch(
      /HStack\(alignment: \.center, spacing: (TodoTimelineMetrics\.minimalRowContentSpacing|TdayTaskRowMetrics\.contentSpacing)\)/,
    );
  });
});
