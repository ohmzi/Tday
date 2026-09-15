import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * The Android empty scenes: what decides them, and what may never be allowed back into that
 * decision.
 *
 * This is the client the bug was REPORTED on — the Anytime home, pulled down with no tasks in it,
 * withdrawing its illustration, its heading and its body and putting them back when the refresh
 * returned with nothing new — and until this file existed it was the only one of the three with no
 * guardrail. `FeedAnswerTest` proves the decision, and `feedAnswer` has no loading parameter, so
 * the FUNCTION cannot be handed a refresh flag; neither fact reaches the three ARGUMENTS each
 * screen builds for it. Editing one line of `TodoListScreen.kt`
 *
 *     storeRead = uiState.hasHydratedSnapshot && !uiState.isLoading,
 *
 * restores the reported screenshot exactly, and the whole Android suite plus every web guardrail
 * stayed green with it in the tree. That is what is closed here.
 *
 * Why in `tday-web/tests/`: it is where `ios-empty-state-presence.test.ts` and the motion
 * reachability scans already live, because this repository's only cross-client test runner is
 * vitest. The Kotlin suite cannot see a Compose gate without an emulator; a static read can, and
 * every defect below is the PRESENCE of a term rather than a behaviour, which is exactly what a
 * static read is good for.
 */

const MONO = resolve(__dirname, "..", "..", "..");
const ANDROID = resolve(MONO, "android-compose/app/src/main/java/com/ohmz/tday/compose");

const TODO_LIST_SCREEN = resolve(ANDROID, "feature/todos/TodoListScreen.kt");
const COMPLETED_SCREEN = resolve(ANDROID, "feature/completed/CompletedScreen.kt");
const CALENDAR_SCREEN = resolve(ANDROID, "feature/calendar/CalendarScreen.kt");
const SCHEDULED_TASK_HOME_SCREEN = resolve(
  ANDROID,
  "feature/scheduledtaskhome/ScheduledTaskHomeScreen.kt",
);
const COMPLETED_VIEW_MODEL = resolve(ANDROID, "feature/completed/CompletedViewModel.kt");
const CALENDAR_VIEW_MODEL = resolve(ANDROID, "feature/calendar/CalendarViewModel.kt");

/**
 * Reads a Kotlin source with its comments removed.
 *
 * Not optional here, for the reason the iOS file gives: these files argue in prose, and the prose
 * quotes the very term being banned. `TodoListScreen.kt` says "Everything below that used to ask
 * `!uiState.isLoading`" directly above the gate that no longer does. Stripping first means every
 * assertion below is about code.
 */
function readCode(file: string): string {
  return readFileSync(file, "utf8")
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((line) => !line.trimStart().startsWith("//"))
    .join("\n");
}

const CONTINUES = /(&&|\|\||\+|=|,|\?:|->)$/;

/**
 * The whole declaration that starts at `anchor` — a `val` whose expression wraps over five lines,
 * or a `fun` with either an expression body or a block one.
 *
 * Kotlin gives no single terminator to look for, so the rule is "still open": parentheses or
 * braces unbalanced, or the line ends in a token that cannot end a statement. A miscount runs long
 * and swallows the next declaration, which makes the `not.toContain("isLoading")` assertions
 * STRICTER rather than laxer; running short makes them fail outright. Both failure modes are red,
 * which is the property a scanner like this has to have.
 */
function declarationAt(source: string, anchor: string): string {
  const lines = source.split("\n");
  const start = lines.findIndex((line) => line.includes(anchor));
  if (start < 0) return "";

  let parens = 0;
  let braces = 0;
  const collected: string[] = [];
  for (let i = start; i < lines.length; i++) {
    const line = lines[i];
    collected.push(line);
    for (const char of line) {
      if (char === "(") parens += 1;
      else if (char === ")") parens -= 1;
      else if (char === "{") braces += 1;
      else if (char === "}") braces -= 1;
    }
    const trimmed = line.trim();
    if (parens <= 0 && braces <= 0 && !CONTINUES.test(trimmed)) break;
  }
  return collected.join("\n");
}

/**
 * The brace-delimited block that opens after `anchor`, anchor included.
 *
 * `declarationAt` counts braces from a zero it assumes the anchor starts at, which is exactly
 * wrong for a lambda anchor like `}.getOrElse {` — the line closes the previous block before it
 * opens this one. This counts from the opening brace itself instead. Naive about braces inside
 * strings, which none of the blocks pointed at here contain; a miscount truncates and the
 * assertion using it fails, so the failure mode stays red.
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

/** Every `feedAnswer(...)` call in a source, arguments included. */
function feedAnswerCalls(source: string): string[] {
  const calls: string[] = [];
  let from = 0;
  for (;;) {
    const at = source.indexOf("feedAnswer(", from);
    if (at < 0) break;
    const open = source.indexOf("(", at);
    let depth = 0;
    let end = -1;
    for (let i = open; i < source.length; i++) {
      if (source[i] === "(") depth += 1;
      else if (source[i] === ")") {
        depth -= 1;
        if (depth === 0) {
          end = i + 1;
          break;
        }
      }
    }
    if (end < 0) break;
    calls.push(source.slice(at, end));
    from = end;
  }
  return calls;
}

const todoList = readCode(TODO_LIST_SCREEN);
const completed = readCode(COMPLETED_SCREEN);
const calendar = readCode(CALENDAR_SCREEN);
const scheduledHome = readCode(SCHEDULED_TASK_HOME_SCREEN);

/**
 * The refresh flag is chrome. It may drive the pull pill and the error card; it may never decide
 * what the scene is.
 *
 * `isLoading` on this client is written by exactly one thing — `refreshInternal(showLoading =
 * true)`, reachable only from `refresh()`, whose callers are `TdayApp`'s pull lambda and the error
 * card's Retry. Every feed hydrates from its local cache synchronously, so the flag has never meant
 * "no answer yet"; it has always meant "a refresh over an answer already on screen", which is the
 * one condition an empty scene must be held THROUGH.
 */
describe("no Android feed scene is decided by the refresh flag", () => {
  const sources: ReadonlyArray<{ file: string; source: string }> = [
    { file: "TodoListScreen.kt", source: todoList },
    { file: "CompletedScreen.kt", source: completed },
    { file: "CalendarScreen.kt", source: calendar },
  ];

  for (const { file, source } of sources) {
    it(`${file} builds every feedAnswer argument out of the cache read and the first answer`, () => {
      // THE ARGUMENTS, which is where the reported bug can come back. The two terms named here are
      // the two that a refresh cannot move: `hasHydratedSnapshot` is written on both the success
      // and the failure path of a hydrate and never unwritten, and `firstAnswerLanded` only ever
      // goes false -> true. `isLoading` is the one that flips twice per pull, and the whole fix is
      // that it is not in this call.
      const calls = feedAnswerCalls(source);
      expect(calls.length, `${file} no longer asks feedAnswer at all`).toBeGreaterThan(0);
      for (const call of calls) {
        expect(call).toContain("hasHydratedSnapshot");
        expect(call).toContain("firstAnswerLanded");
        expect(call).not.toContain("isLoading");
      }
    });
  }

  const gates: ReadonlyArray<{
    file: string;
    source: string;
    anchor: string;
    name: string;
    names: string;
  }> = [
    // The reported screenshot, in one line of Kotlin: the Anytime home's inline scene.
    {
      file: "TodoListScreen.kt",
      source: todoList,
      anchor: "internal fun shouldShowFloaterEmptyScene(",
      name: "shouldShowFloaterEmptyScene",
      names: "FeedAnswer.Empty",
    },
    // Its call site, which is where a refresh term would be re-added without touching the
    // predicate — the gate can stay pure while the screen hands it a poisoned answer.
    {
      file: "TodoListScreen.kt",
      source: todoList,
      anchor: "val floaterEmptySceneVisible = shouldShowFloaterEmptyScene(",
      name: "floaterEmptySceneVisible",
      names: "answer = feedItemsAnswer",
    },
    // The other half of the same pull: the placeholder that used to GROW during a refresh over an
    // already-answered feed, and drew nothing on the one load that really has no answer yet.
    {
      file: "TodoListScreen.kt",
      source: todoList,
      anchor: "val taskFeedSkeletonVisible =",
      name: "taskFeedSkeletonVisible",
      names: "FeedAnswer.AwaitingFirst",
    },
    // "All done today" withdrawn mid-refresh is the reported flash with a worse message on it,
    // and it takes the completion haptic with it: `LaunchedEffect(isDayDone)` re-fires when the
    // refresh returns with nothing new.
    {
      file: "TodoListScreen.kt",
      source: todoList,
      anchor: "val isDayDone =",
      name: "isDayDone",
      names: "FeedAnswer.Empty",
    },
    // The v0.7.25 presentation: the scene anchored below Earlier's rows. Its mount guard is
    // deliberately wider than its visibility, so the exit has somewhere to play from.
    {
      file: "TodoListScreen.kt",
      source: todoList,
      anchor: "val earlierScenePresent =",
      name: "earlierScenePresent",
      names: "FeedAnswer.Empty",
    },
    {
      file: "TodoListScreen.kt",
      source: todoList,
      anchor: "internal fun shouldShowEarlierScene(",
      name: "shouldShowEarlierScene",
      names: "FeedAnswer.Empty",
    },
    // The celebration gate two recent commits rewrote. It is EXTENDED by the answer term rather
    // than replaced, and `celebrationCancelledAt` is read by the predicate beside it — a future
    // reader deleting either half is deleting a different fix.
    {
      file: "TodoListScreen.kt",
      source: todoList,
      anchor: "internal fun shouldShowTodayEarlierExpandedCelebration(",
      name: "shouldShowTodayEarlierExpandedCelebration",
      names: "FeedAnswer.Empty",
    },
    {
      file: "CompletedScreen.kt",
      source: completed,
      anchor: "val showEmptyState =",
      name: "showEmptyState",
      names: "FeedAnswer.Empty",
    },
    {
      file: "CompletedScreen.kt",
      source: completed,
      anchor: "val completedFeedSkeletonVisible =",
      name: "completedFeedSkeletonVisible",
      names: "FeedAnswer.AwaitingFirst",
    },
    // The calendar's day list, which was the in-tree precedent for all of the above: an empty day
    // inside a full month is an answer, and a sync in flight never makes it less true.
    {
      file: "CalendarScreen.kt",
      source: calendar,
      anchor: "val showsEmptyScene =",
      name: "showsEmptyScene",
      names: "FeedAnswer.Empty",
    },
  ];

  for (const gate of gates) {
    it(`${gate.file} decides ${gate.name} without asking whether a refresh is running`, () => {
      const declaration = declarationAt(gate.source, gate.anchor);
      expect(declaration, `${gate.file} no longer declares ${gate.name}`).not.toBe("");
      expect(declaration).toContain(gate.names);
      expect(declaration).not.toContain("isLoading");
    });
  }

  it("keeps the full-screen illustration's own condition off the flag too", () => {
    // The one gate in this set that is not a declaration — it is the `visible` argument of an
    // `AnimatedVisibility` inside a `LazyListScope`, so there is nothing to anchor on and the
    // condition is matched as written. This is the overdue-less path; the overdue one is
    // `earlierScenePresent` above, and they must not be merged: a restored overdue row leaves the
    // scene under Earlier's header exactly where v0.7.25 put it.
    expect(todoList).toContain(
      "visible = scopeItemsEmpty && scopeAnswer == FeedAnswer.Empty &&",
    );
  });

  it("keeps the three answer states a decision rather than a condition per screen", () => {
    // Each screen has to reach the one function. A screen that stopped calling it would satisfy
    // every `not.toContain("isLoading")` above by inventing its own flag instead — which is how
    // this rule was spelled `!isLoading` in seven places to begin with.
    expect(todoList).toContain("feedAnswer(");
    expect(completed).toContain("feedAnswer(");
    expect(calendar).toContain("feedAnswer(");
  });

  it("leaves the refresh indicator exactly where it was on the two screens that have one", () => {
    // The user asked for the picture to stay, not for the refresh to become invisible. Two and not
    // three: `CompletedScreen` and `CalendarScreen` have no pull-to-refresh on this client at all
    // (no `TdayPullRefresh`, no `isRefreshing` — checked below so this stays an agreement rather
    // than an excuse), and the Anytime home's pull lives on `ScheduledTaskHomeScreen`, which hosts
    // the feed `TodoListScreen` draws. That is the pull from the report.
    expect(todoList).toContain("isRefreshing = uiState.isLoading");
    expect(scheduledHome).toContain("isRefreshing = uiState.isLoading");
    expect(completed).not.toContain("isRefreshing");
    expect(calendar).not.toContain("isRefreshing");
  });
});

/**
 * A cache read that THREW still answered, and a screen may not wait on it twice.
 *
 * `hasHydratedSnapshot` is half of every `feedAnswer` argument list above, and it is the half that
 * can be left false forever. `TodoListViewModel` writes it on both legs of its hydrate and says
 * why; the other two hydrate in a field initializer, where the failure leg is a `getOrElse` and is
 * one default constructor away from never answering at all. With `CompletedUiState()` there, a
 * failed read pins the row placeholder up for the life of the process; with `CalendarUiState()`,
 * the day list draws neither its rows nor its scene. Both used to show the empty state in that
 * state, so the fix would have taken something away on exactly the screens it was meant to protect.
 */
describe("an Android cache read that failed still ends the wait", () => {
  const viewModels: ReadonlyArray<{ file: string; source: string; state: string }> = [
    { file: "CompletedViewModel.kt", source: readCode(COMPLETED_VIEW_MODEL), state: "CompletedUiState" },
    { file: "CalendarViewModel.kt", source: readCode(CALENDAR_VIEW_MODEL), state: "CalendarUiState" },
  ];

  for (const { file, source, state } of viewModels) {
    it(`${file} answers on the initializer's failure path`, () => {
      const fallback = blockAfter(source, "}.getOrElse {");
      expect(fallback, `${file} no longer has a getOrElse fallback`).not.toBe("");
      expect(fallback).toContain("hasHydratedSnapshot = true");
      expect(fallback).toContain("firstAnswerLanded = firstAnswerSignal.hasLanded()");
      // The bare default is the whole defect, in one expression.
      expect(fallback).not.toContain(`${state}()`);
    });

    it(`${file} answers on the hydrate's failure path too`, () => {
      // The initializer runs once; `load()` and every cache-version bump come back through
      // `hydrateFromCache`. Without an `onFailure` leg there, a device whose read fails twice gets
      // a second chance that changes nothing.
      const hydrate = blockAfter(source, "private fun hydrateFromCache() {");
      expect(hydrate, `${file} no longer declares hydrateFromCache`).not.toBe("");
      const failure = blockAfter(hydrate, ".onFailure {");
      expect(failure, `${file}'s hydrate has no failure leg`).not.toBe("");
      expect(failure).toContain("hasHydratedSnapshot = true");
      expect(failure).toContain("firstAnswerLanded = firstAnswerSignal.hasLanded()");
    });
  }
});
