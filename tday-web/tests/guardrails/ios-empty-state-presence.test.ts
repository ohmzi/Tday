import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * The iOS empty scenes: when they are allowed on screen, and how they leave it.
 *
 * `motion-reachability-ios.test.ts` cannot speak to either. Its rules key off a view's own
 * `@State`, and every gate here is a computed `private var` over the view model instead — so the
 * scan reaches these `.transition` sites, finds no observable state in the condition above them,
 * and correctly declines to have an opinion. That is the right call for a general rule and it
 * leaves two specific defects uncovered, which is what this file is for.
 *
 * Neither is provable by anything else this repository runs. There is no local iOS compile, and
 * both are about what a running screen does across a frame boundary — a scene held while a sync is
 * in flight, a scene fading rather than cutting when a keystroke lands. CI's `xctest` cannot see
 * the second at all without a simulator and a finger. Reading the source is enough here because
 * both defects were the *absence* of a construct — a missing removal leg, a stray guard term — and
 * an absence is exactly what a static read can see.
 */

const MONO = resolve(__dirname, "..", "..", "..");

const CALENDAR_SCREEN = resolve(MONO, "ios-swiftUI/Tday/Feature/Calendar/CalendarScreen.swift");
const COMPLETED_SCREEN = resolve(MONO, "ios-swiftUI/Tday/Feature/Completed/CompletedScreen.swift");
const EMPTY_STATE = resolve(MONO, "ios-swiftUI/Tday/Core/UI/TdayEmptyState.swift");
const APP_ROOT = resolve(MONO, "ios-swiftUI/Tday/Feature/App/AppRootView.swift");
const TODO_LIST_SCREEN = resolve(MONO, "ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift");
const SCHEDULED_TASK_HOME_SCREEN = resolve(
  MONO,
  "ios-swiftUI/Tday/Feature/ScheduledTaskHome/ScheduledTaskHomeScreen.swift",
);

/**
 * Reads a source file with its comments removed.
 *
 * These files explain themselves at length, and the explanations quote the very constructs being
 * asserted about — the comment saying why the calendar's scene is *not* gated on `isLoading` names
 * `isLoading` twice. Stripping first means every assertion below is about code.
 */
function readCode(file: string): string {
  return readFileSync(file, "utf8")
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((line) => !line.trimStart().startsWith("//"))
    .join("\n");
}

/** The other half: the `//` prose only, for the assertions that are about what a comment claims. */
function readComments(file: string): string {
  return readFileSync(file, "utf8")
    .split("\n")
    .filter((line) => line.trimStart().startsWith("//"))
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

/**
 * The argument list of the call that starts at `anchor`, parentheses included.
 *
 * A call site is not a block — `CompletedScreen(container: container)` has no braces at all — so
 * `blockAfter` would run past it and return the next unrelated body. Same naive counting, same
 * failure mode: a miscount returns a short string and the assertion using it fails.
 */
function callArgsAfter(source: string, anchor: string): string {
  const anchorAt = source.indexOf(anchor);
  if (anchorAt < 0) return "";
  const open = source.indexOf("(", anchorAt);
  if (open < 0) return "";

  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === "(") depth += 1;
    else if (source[i] === ")") {
      depth -= 1;
      if (depth === 0) return source.slice(open, i + 1);
    }
  }
  return "";
}

describe("the iOS calendar keeps the day's empty scene up while a sync runs", () => {
  const code = readCode(CALENDAR_SCREEN);
  const rows = blockAfter(code, "private var pendingTaskRows: some View {");

  it("still has the property the day's list and its empty scene are chosen in", () => {
    expect(rows).not.toBe("");
    expect(rows).toContain("calendarDayEmptyState");
  });

  it("shows the scene on a plain else, not behind a refresh flag", () => {
    // `CalendarViewModel.isLoading` is raised by one thing, `refresh()`, which is a user-initiated
    // force sync over a cache the view model hydrated from synchronously in `init`. Nothing on this
    // screen loads on appear, so `items` is the truth from the first frame and a sync in flight
    // never makes the day's emptiness less true. `else if !viewModel.isLoading` was therefore not
    // withholding a premature answer — it blanked a correct one for the length of the round trip,
    // leaving the card sitting over nothing but the watermark.
    expect(rows).not.toContain("isLoading");
    expect(rows).toMatch(/\}\s*else\s*\{/);
  });

  it("keeps both scenes the empty branch chooses between", () => {
    // A scene that survives the gate and then has nothing to draw is the same blank screen by
    // another route, so the branch this row is about is only fixed while both arms still exist.
    const scene = blockAfter(code, "private var calendarDayEmptyState: some View {");
    expect(scene.match(/TdayEmptyState\(/g) ?? []).toHaveLength(2);
    expect(scene).toContain("isSearching");
  });
});

describe("the iOS completed history's empty scene fades out instead of cutting", () => {
  const code = readCode(COMPLETED_SCREEN);

  it("gives both empty scenes a transition", () => {
    // Deleting a character out of a query that matched nothing turns the no-match scene straight
    // back into rows. With no removal leg SwiftUI just stops drawing the scene on that frame.
    expect(code.match(/\.transition\(completedEmptyStateTransition\)/g) ?? []).toHaveLength(2);
  });

  it("leaves the insertion leg inert", () => {
    // `.identity` on insertion is the whole point rather than an oversight: `TdayEmptyState` gives
    // itself a 0.52s rise from its own `onAppear`, so an opacity leg here would stack a second,
    // independent fade on one arrival. Anyone "completing" this asymmetry into `.opacity` both ways
    // is introducing the double animation, not removing an omission.
    const transition = blockAfter(code, "private var completedEmptyStateTransition: AnyTransition");
    expect(transition).toContain(".asymmetric(insertion: .identity, removal: .opacity)");
  });

  it("still has the self-animating arrival that inert insertion leg relies on", () => {
    // The claim above is only true while `TdayEmptyState` really does animate itself in. If that
    // ever goes, `.identity` stops being a considered choice and becomes a hard cut on insertion —
    // so the two are asserted together, here, rather than left to agree by memory.
    const emptyState = readCode(EMPTY_STATE);
    const onAppear = blockAfter(emptyState, ".onAppear {");
    expect(onAppear).toContain("withAnimation(");
    expect(onAppear).toContain("entered = true");
  });

  it("keys an .animation(_:value:) to the same gate the scenes sit behind", () => {
    // The transition is the shape of the exit; this is the transaction that lets it run at all. The
    // scene leaves on a keystroke written straight into `searchQuery` by the top bar's field, with
    // no `withAnimation` anywhere near it, so without this modifier the removal has no animation to
    // take and the spec above is decoration.
    expect(code).toContain("if showsCompletedEmptyState {");
    expect(code).toMatch(
      /\.animation\(\s*\.easeIn\(duration: CompletedEmptyStateExit\.duration\),\s*value: showsCompletedEmptyState,?\s*\)/,
    );
  });

  it("gates the scenes and the animation on one property", () => {
    // Written out twice, the gate and the key drift, and a removal transition goes quietly inert
    // the day someone edits one of them — the exact failure the motion programme keeps finding.
    //
    // The count moved one property along when both scenes were put behind `feedAnswer`, and the
    // assertion follows it rather than being relaxed: `showsCompletedEmptyState` still has to be
    // the single name the scenes and the `.animation(_:value:)` key share, and `completedAnswer`
    // still has to be the single place history's emptiness is counted. Two hops, one definition
    // each, is the same guarantee — what it forbids is a second count written out by hand.
    const gate = blockAfter(code, "private var showsCompletedEmptyState: Bool {");
    expect(gate).toContain("completedAnswer");
    const answer = blockAfter(code, "private var completedAnswer: FeedAnswer {");
    expect(answer).toContain("searchedItems.isEmpty");
  });
});

/**
 * The refresh flag is chrome. It may drive the pull pill; it may never decide what the scene is.
 *
 * An empty state is an ANSWER, not an absence of one, and `isLoading` is raised on these three
 * screens by exactly one thing — `refresh()`, reachable only from the pull gesture and the error
 * card's Retry. Every feed view model here hydrates from the local cache synchronously in `init`,
 * so the flag has never meant "no answer yet"; it has always meant "a refresh over an answer
 * already on screen", which is the one condition an empty scene must be held THROUGH. Spelled
 * `!viewModel.isLoading`, it made the gesture that asks the app to re-check its answer the very
 * term that withdrew it: the user pulls the Anytime home down with nothing in it, the
 * illustration and its copy vanish, the page collapses upward, three grey placeholder bars grow
 * in the gap, and the whole block comes back when the refresh returns with nothing new.
 *
 * `feedAnswer(storeRead:rowsEmpty:firstAnswerLanded:)` has no loading parameter, so the ban below
 * is not a style rule — it is the only way the absence stays structural once these gates are
 * ordinary Swift properties again. The calendar's own day list is the in-tree precedent and is
 * already pinned this way a few describes above; the rest of the same copy-pasted gate is below.
 */
describe("no iOS feed scene is decided by the refresh flag", () => {
  const todoList = readCode(TODO_LIST_SCREEN);
  const completed = readCode(COMPLETED_SCREEN);
  const scheduledHome = readCode(SCHEDULED_TASK_HOME_SCREEN);

  const gates: ReadonlyArray<{ file: string; source: string; anchor: string; counts: string }> = [
    // The reported screenshot, in one line of Swift.
    {
      file: "TodoListScreen.swift",
      source: todoList,
      anchor: "private var showInlineFloaterTaskHomeEmpty: Bool {",
      counts: "floaterTaskHomeAnswer",
    },
    // Its other half on the same pull: the skeleton that used to GROW during a refresh over an
    // already-answered feed, and drew nothing on the one load that really has no answer.
    {
      file: "TodoListScreen.swift",
      source: todoList,
      anchor: "private var showsTimelineSkeleton: Bool {",
      counts: "timelineAnswer",
    },
    // The term here was dropped rather than swapped, and this is what keeps it dropped. A search
    // is answered locally out of rows the screen already holds, so there is no first load to
    // withhold the no-match scene for; the flag only ever left a refresh under a non-matching
    // query showing neither this scene nor the skeleton, which excludes search on purpose.
    {
      file: "TodoListScreen.swift",
      source: todoList,
      anchor: "private var showsListSearchEmptyState: Bool {",
      counts: "timelineItems.isEmpty",
    },
    {
      file: "CompletedScreen.swift",
      source: completed,
      anchor: "private var showsCompletedEmptyState: Bool {",
      counts: "completedAnswer",
    },
    {
      file: "CompletedScreen.swift",
      source: completed,
      anchor: "private var completedAnswer: FeedAnswer {",
      counts: "searchedItems.isEmpty",
    },
    {
      file: "CompletedScreen.swift",
      source: completed,
      anchor: "private var showsCompletedFeedSkeleton: Bool {",
      counts: "completedAnswer",
    },
    // The mirror image of the report: this root has a live pull and no empty scene, so a pull on
    // a Today feed the user had just cleared grew three grey bars in a gap that was correctly
    // empty.
    {
      file: "ScheduledTaskHomeScreen.swift",
      source: scheduledHome,
      anchor: "private var showsTodayFeedSkeleton: Bool {",
      counts: "viewModel.todayTodos.isEmpty",
    },
  ];

  for (const gate of gates) {
    const anchorName = gate.anchor.slice("private var ".length, gate.anchor.indexOf(":"));

    it(`${gate.file} decides ${anchorName} without asking whether a refresh is running`, () => {
      const rows = blockAfter(gate.source, gate.anchor);
      expect(rows, `${gate.file} no longer declares ${anchorName}`).not.toBe("");
      expect(rows).toContain(gate.counts);
      expect(rows).not.toContain("isLoading");
    });
  }

  it("keeps the full-screen illustration's own condition off the flag too", () => {
    // The one gate in this set that is not a `private var` — it is a bare `if` inside the
    // watermark overlay, so `blockAfter` has no declaration to anchor on and the condition is
    // matched as written. The two named terms are asserted together on purpose: this is the
    // Earlier/celebration predicate that two recent commits rewrote, extended by a sibling term
    // rather than edited, and a future reader deleting either half is deleting a different fix.
    expect(todoList).toContain(
      "if showsEmptyStateIllustration, pendingScopeAnswer == .empty, !isFloaterTaskHomeScreen, !isSearchingList {",
    );
  });

  it("keeps the three answer states a decision rather than three hand-written conditions", () => {
    // The gates above are allowed to name `feedAnswer` indirectly (`floaterTaskHomeAnswer`,
    // `completedAnswer`) because the row count differs per scope and the properties exist to
    // carry that difference. What may not come back is a second copy of the RULE, so each screen
    // has to reach the one function — a screen that stopped calling it entirely would satisfy
    // every `not.toContain("isLoading")` above by inventing its own flag instead.
    expect(todoList).toContain("feedAnswer(");
    expect(completed).toContain("feedAnswer(");
    expect(scheduledHome).toContain("feedAnswer(");
  });

  it("leaves the refresh indicator and the error card exactly where they were", () => {
    // The user asked for the picture to stay, not for the refresh to become invisible. Pinned as
    // an agreement rather than left implied, because "remove the isLoading term" is one careless
    // reading away from removing the spinner the gesture needs in order to feel answered at all.
    expect(todoList).toContain("isRefreshing: viewModel.isLoading");
    expect(completed).toContain("isRefreshing: viewModel.isLoading");
    expect(scheduledHome).toContain("isRefreshing: viewModel.isLoading");
  });
});

describe("CompletedScreen does not claim a pull-to-refresh it has not got", () => {
  const comments = readComments(COMPLETED_SCREEN);
  const rootView = readCode(APP_ROOT);

  it("agrees with its own call site about whether the gesture exists", () => {
    // `pullRefreshEnabled` defaults to false and `AppRootView` builds this screen without it, so
    // the `tdayPullToRefresh` in the body is wired to nothing. A comment justifying an
    // `.allowsHitTesting(false)` with "pull-to-refresh still has to work here" therefore described
    // a gesture the screen does not have, and cost one verifier pass a wrong conclusion before it
    // was caught.
    //
    // Asserted as an agreement rather than a ban, so it latches in both directions: bring the
    // parameter in at the call site and the prose is allowed back; write the prose while the
    // parameter is still absent and this fails again.
    //
    // What is matched is the English phrase, not the API names. `pullRefreshEnabled` and
    // `tdayPullToRefresh` stay sayable, which is how the screen's own comment explains that the
    // gesture is absent without tripping the rule that keeps it absent.
    const callSite = callArgsAfter(rootView, "CompletedScreen(");
    expect(callSite).not.toBe("");
    const constructedWithFlag = callSite.includes("pullRefreshEnabled");
    const claimsPullToRefresh = /pull[- ]to[- ]refresh/i.test(comments);

    expect(
      claimsPullToRefresh,
      constructedWithFlag
        ? "AppRootView now passes pullRefreshEnabled, so CompletedScreen's prose may describe the gesture again"
        : "AppRootView builds CompletedScreen without pullRefreshEnabled, so no comment in it may justify anything with a pull-to-refresh",
    ).toBe(constructedWithFlag);
  });
});
