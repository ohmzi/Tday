import { readFileSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

/**
 * THE CHECK-OFF, ON ALL THREE CLIENTS
 *
 * Ticking a task off is the one animation this app plays on eight different rows —
 * three on Android, four on iOS, five on web — and the only thing holding those
 * eight together is that somebody typed the same three numbers into each of them.
 * `motion-parity.test.ts` cannot see that: it counts hand-written literals, so a row
 * moved to 280 / 620 / 960 through a named constant costs it nothing at all. Which
 * is exactly how the disagreement this file was written after arrived — every client
 * had a screen quietly running the sequence a third slower than its own siblings,
 * and every guardrail in the tree was green.
 *
 * So this one reads the choreography rather than the vocabulary. It takes web's
 * shared clock as the statement of the beats and re-derives the same beats from the
 * Kotlin and the Swift, off disk, as text.
 *
 * Why text and not imports, for the native halves: there is no Kotlin or Swift
 * toolchain in this repo's CI for the web suite, and there does not need to be — a
 * `delay(160L)` is a string like any other. `motion-parity.test.ts` opens with the
 * same argument at more length, and this file inherits its one hard rule: never
 * compare a file to itself. Web's numbers come from web, Android's from Android,
 * iOS's from iOS, and each is only ever asserted against the other two.
 *
 * What is NOT pinned here, deliberately: the last leg. Android and iOS hand a
 * removal to the list and it closes the gap itself; web has no list-removal
 * animation, so its row's own collapse IS the removal and the prune has to outlast
 * it. `taskCompletionTiming.ts` argues that difference where it lives, and
 * `todo-row-complete-interaction.test.tsx` pins web's side of it.
 */

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");

const WEB_CLOCK = path.join(ROOT, "src", "lib", "taskCompletionTiming.ts");
const IOS_GENERATED = path.join(
  MONO, "ios-swiftUI", "Tday", "UI", "Theme", "TdayMotionGenerated.swift",
);

const ANDROID_FEATURE = path.join(
  MONO, "android-compose", "app", "src", "main", "java", "com", "ohmz", "tday",
  "compose", "feature",
);
const IOS_FEATURE = path.join(MONO, "ios-swiftUI", "Tday", "Feature");

/** Where a burst of server events becomes a refresh, on each client. */
const WEB_REALTIME = path.join(ROOT, "src", "lib", "realtime.tsx");
const ANDROID_APP_VM = path.join(
  MONO, "android-compose", "app", "src", "main", "java", "com", "ohmz", "tday",
  "compose", "feature", "app", "AppViewModel.kt",
);
const IOS_APP_VM = path.join(IOS_FEATURE, "App", "AppViewModel.swift");

/** The delayed-commit site whose window the batch rides, and the claim it rides it on. */
const WEB_BULK_ACTIONS = path.join(ROOT, "src", "hooks", "use-bulk-todo-actions.ts");
const WEB_STAGED_ROWS = path.join(ROOT, "src", "lib", "todo", "staged-todo-rows.ts");

/**
 * Every row that stages a check-off, by the file that draws it.
 *
 * Listed out rather than discovered by a glob because the assertion this file makes
 * is "each of these screens agrees", and a glob would quietly stop asserting
 * anything about a screen that was renamed — the failure mode being guarded against
 * is a screen drifting away unnoticed, which is the same shape as a screen
 * disappearing from a pattern unnoticed. A new screen that stages a completion has
 * to be added here by hand, and that is the point.
 */
const ANDROID_SCREENS = [
  path.join(ANDROID_FEATURE, "todos", "TodoListScreen.kt"),
  path.join(ANDROID_FEATURE, "scheduledtaskhome", "ScheduledTaskHomeScreen.kt"),
  path.join(ANDROID_FEATURE, "calendar", "CalendarScreen.kt"),
  path.join(ANDROID_FEATURE, "completed", "CompletedScreen.kt"),
];

const IOS_SCREENS = [
  path.join(IOS_FEATURE, "Todos", "TodoListScreen.swift"),
  path.join(IOS_FEATURE, "ScheduledTaskHome", "ScheduledTaskHomeScreen.swift"),
  path.join(IOS_FEATURE, "Calendar", "CalendarScreen.swift"),
  path.join(IOS_FEATURE, "Completed", "CompletedScreen.swift"),
];

function read(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

function name(filePath: string): string {
  return path.basename(filePath);
}

/**
 * The beats, in the order they are played.
 *
 * `fadeWait` is the wait AFTER the fade is started rather than the fade itself, which
 * is why it is not part of the equality the three clients are compared on: every
 * client spells the fade as the Change rung and then has to wait it out in whatever
 * units its scheduler takes.
 */
type Beats = { checkToStrike: number; strikeToFade: number };
type IosBeats = Beats & { fadeWait: number };

// ─── web ─────────────────────────────────────────────────────────────

/**
 * Web's two offsets, read out of the module every web row imports.
 *
 * A `const X = 160;` and not the imported symbol, for the reason the header gives —
 * importing it and asserting it equals itself is the check that cannot fail. This
 * is the same read any other client's developer would do: open the file, take the
 * number.
 */
function parseWebBeats(): Beats {
  const source = read(WEB_CLOCK);
  const offset = (constant: string): number => {
    const match = source.match(
      new RegExp(`export const ${constant} = (\\d+);`),
    );
    if (!match) throw new Error(`taskCompletionTiming.ts no longer exports ${constant}`);
    return Number(match[1]);
  };
  return {
    checkToStrike: offset("TASK_COMPLETION_CHECK_TO_STRIKE_MS"),
    strikeToFade: offset("TASK_COMPLETION_STRIKE_TO_FADE_MS"),
  };
}

// ─── Android ─────────────────────────────────────────────────────────

/**
 * Android spells the beats as `private const val <SCREEN>_..._MS = 160L`, with each
 * screen prefixing the names its own way. Matched on the suffix, which is the part
 * that says what the beat IS — and matched in both directions, because the Completed
 * screen plays the sequence backwards and names its constants for what it does
 * (`UNCHECK_TO_UNSTRIKE`) rather than for what the forward row does.
 */
function parseAndroidBeats(filePath: string): Beats {
  const source = read(filePath);
  const offset = (suffix: RegExp): number => {
    const match = source.match(
      new RegExp(`private const val [A-Z_]*${suffix.source} = (\\d+)L`),
    );
    if (!match) {
      throw new Error(`${name(filePath)} has no ${suffix.source} beat`);
    }
    return Number(match[1]);
  };
  return {
    checkToStrike: offset(/(?:CHECK_TO_STRIKE|UNCHECK_TO_UNSTRIKE)_MS/),
    strikeToFade: offset(/(?:STRIKE_TO_FADE|UNSTRIKE_TO_FADE)_MS/),
  };
}

/** Whether the screen's fade reads the Change rung instead of carrying a number. */
function androidFadeNamesChange(filePath: string): boolean {
  return /private val [A-Z_]*FADE_MS = TdayMotionTokens\.Durations\.Change\.toLong\(\)/
    .test(read(filePath));
}

// ─── iOS ─────────────────────────────────────────────────────────────

/**
 * iOS stages the sequence inside a `Task { @MainActor in }` and spells its waits in
 * NANOSECONDS, which is the reason this parser exists rather than a grep: 160_000_000
 * shares no digits with anything the other two clients write down, and the underscores
 * are Swift's, not the file's.
 *
 * Only blocks that move a completion phase are read. `TodoListScreen.swift` also runs
 * an unrelated attention pulse on two sleeps of its own a few thousand lines up, and a
 * parser that took every `Task.sleep` in the file would be comparing the check-off to
 * a pulse.
 */
function parseIosBeats(filePath: string): IosBeats[] {
  const source = read(filePath);
  const blocks: IosBeats[] = [];
  const opener = /Task \{ @MainActor in/g;
  let match: RegExpExecArray | null;

  while ((match = opener.exec(source)) !== null) {
    // Brace-matched from the `{` the opener ends on, so a `withAnimation { }` nested
    // inside the block does not close it early.
    const start = source.indexOf("{", match.index);
    let depth = 0;
    let end = start;
    for (; end < source.length; end += 1) {
      if (source[end] === "{") depth += 1;
      else if (source[end] === "}") {
        depth -= 1;
        if (depth === 0) break;
      }
    }
    const body = source.slice(start, end);
    if (!/Phase/.test(body)) continue;

    const sleeps = Array.from(
      body.matchAll(/Task\.sleep\(nanoseconds: ([\d_]+)\)/g),
      (sleep) => Number(sleep[1].replace(/_/g, "")) / 1_000_000,
    );
    if (sleeps.length === 3) {
      blocks.push({
        checkToStrike: sleeps[0],
        strikeToFade: sleeps[1],
        fadeWait: sleeps[2],
      });
    }
  }
  return blocks;
}

/** The Change rung in milliseconds, read off iOS's own generated token file. */
function iosChangeMs(): number {
  const match = read(IOS_GENERATED).match(
    /static let change: TimeInterval = ([\d.]+)/,
  );
  if (!match) throw new Error("TdayMotionGenerated.swift no longer carries Durations.change");
  return Math.round(Number(match[1]) * 1000);
}

// ─── the assertions ──────────────────────────────────────────────────

describe("the check-off plays the same beats on every client", () => {
  const web = parseWebBeats();

  /**
   * The offsets themselves, written out rather than re-derived.
   *
   * `EarlierIllustrationMotionTest.kt` sets the precedent and says why: comparing a
   * constant back against its own definition passes no matter what either one
   * changed to. These two numbers are the choreography — 160ms for the tick to land
   * before the rule starts, 360ms for the rule to be read before the ink goes — and
   * a test that took them from the module would have nothing to say when somebody
   * retimed the module.
   */
  it("web's clock is the 160 / 360 the sequence was designed on", () => {
    expect(web.checkToStrike).toBe(160);
    expect(web.strikeToFade).toBe(360);
  });

  it("web's fade is the Change rung and not a number of its own", () => {
    expect(read(WEB_CLOCK)).toMatch(
      /export const TASK_COMPLETION_FADE_MS = DURATION_MS\.change;/,
    );
  });

  it.each(ANDROID_SCREENS.map((file) => [name(file), file] as const))(
    "%s stages it on web's offsets",
    (_label, file) => {
      expect(parseAndroidBeats(file)).toEqual(web);
    },
  );

  it.each(ANDROID_SCREENS.map((file) => [name(file), file] as const))(
    "%s fades on the Change rung",
    (_label, file) => {
      expect(androidFadeNamesChange(file)).toBe(true);
    },
  );

  it.each(IOS_SCREENS.map((file) => [name(file), file] as const))(
    "%s stages it on web's offsets",
    (_label, file) => {
      const blocks = parseIosBeats(file);
      // At least one, because a screen that stages no completion at all would
      // otherwise pass this by having nothing to disagree with.
      expect(blocks.length).toBeGreaterThan(0);
      for (const block of blocks) {
        expect({
          checkToStrike: block.checkToStrike,
          strikeToFade: block.strikeToFade,
        }).toEqual(web);
      }
    },
  );

  /**
   * iOS waits out its own fade with a nanosecond literal rather than with
   * `TdayMotion.Durations.change`, because `Task.sleep` takes a count and not an
   * animation. That literal is the one place the sequence can drift off the rung
   * without any counter noticing — it is not a `duration:`, so `motion-parity`'s
   * iOS grep cannot see it either — so it is pinned to the rung it is standing in
   * for, read off the generated file.
   */
  it.each(IOS_SCREENS.map((file) => [name(file), file] as const))(
    "%s waits exactly the Change rung for the ink to leave",
    (_label, file) => {
      const blocks = parseIosBeats(file);
      expect(blocks.length).toBeGreaterThan(0);
      for (const block of blocks) expect(block.fadeWait).toBe(iosChangeMs());
    },
  );
});

// ─── once, not once per row ───────────────────────────────────────────

/**
 * The other way the same sequence plays twice: not one row playing it twice, but
 * a row leaving, coming back and leaving again — which the user reads as the
 * sequence playing a second time, and which is what a *batch* provokes.
 *
 * The beats above cannot see it. They pin how long each leg runs, and this is a
 * count. Nothing in the tree counted before this section, which is why a batch
 * that re-inserted every staged row a second before the commit could ship with
 * every guardrail green.
 *
 * Two counts are pinned, and they are the two halves of "the row leaves once":
 *
 * 1. **A burst is one refresh, on every client.** Every completion emits a realtime
 *    event back to the actor who caused it, so N completions are N events. A client
 *    that refreshes per event gives N rows N independent chances to be re-inserted
 *    mid-window — the amplifier the fix commit names and leaves standing, and the
 *    reason "too many together" is what surfaces it. iOS already answers a burst
 *    with one trailing refresh (`scheduleRealtimeSync`); web and Android did not,
 *    so the three are now read as one contract.
 * 2. **The claim is taken before the row is written away, and released only once
 *    the read path is quiet.** A prune that runs before the claim leaves a gap a
 *    fetch already in flight can write back through; a release that runs before
 *    the fetch it authorises lets a request that started inside the window — when
 *    the server still listed the row as pending — answer for it after the commit.
 *    Both are read as text, because both are ordering.
 *
 * These are the assertions that would have caught the reported bug; the beats
 * section above is untouched by them.
 */

/** Milliseconds a client collects a burst of server events before it refreshes. */
function parseWebCoalesceMs(): number {
  const match = read(WEB_REALTIME).match(
    /export const REALTIME_COALESCE_MS = (\d+);/,
  );
  if (!match) throw new Error("src/lib/realtime.tsx no longer names a coalescing window");
  return Number(match[1]);
}

function parseAndroidRealtimeDebounceMs(): number {
  const match = read(ANDROID_APP_VM).match(
    /(?:private )?const val REALTIME_SYNC_DEBOUNCE_MS = (\d+)L/,
  );
  if (!match) {
    throw new Error("AppViewModel.kt no longer names a realtime sync debounce window");
  }
  return Number(match[1]);
}

function parseIosRealtimeDebounceMs(): number {
  const match = read(IOS_APP_VM).match(
    /realtimeSyncDebounceDelay: Duration = \.milliseconds\((\d+)\)/,
  );
  if (!match) {
    throw new Error("AppViewModel.swift no longer names a realtime sync debounce window");
  }
  return Number(match[1]);
}

/**
 * The body of the first `{ … }` opened after `opener`, brace-matched so a nested
 * block does not close it early — the same walk `parseIosBeats` uses.
 */
function blockAfter(source: string, opener: RegExp): string | null {
  const match = opener.exec(source);
  if (!match) return null;
  const start = source.indexOf("{", match.index + match[0].length - 1);
  if (start === -1) return null;
  let depth = 0;
  for (let index = start; index < source.length; index += 1) {
    if (source[index] === "{") depth += 1;
    else if (source[index] === "}") {
      depth -= 1;
      if (depth === 0) return source.slice(start, index);
    }
  }
  return null;
}

describe("a batch of completions refreshes once, not once per row", () => {
  const windows = [
    ["web", parseWebCoalesceMs()],
    ["Android", parseAndroidRealtimeDebounceMs()],
    ["iOS", parseIosRealtimeDebounceMs()],
  ] as const;

  it("names a window on every client, and the three agree on the order of magnitude", () => {
    for (const [, millis] of windows) {
      expect(millis).toBeGreaterThan(0);
      expect(millis).toBeLessThanOrEqual(1000);
    }
    const values = windows.map(([, millis]) => millis);
    expect(Math.max(...values) / Math.min(...values)).toBeLessThanOrEqual(4);
  });

  it("web collects a burst before it invalidates, instead of invalidating in the handler", () => {
    const handler = blockAfter(read(WEB_REALTIME), /socket\.onmessage = \(message\) => \{/);
    expect(handler).not.toBeNull();
    // The socket callback must hand its keys to the coalescer; if it invalidates
    // itself, every event in a batch is a refresh round.
    expect(handler).toContain("scheduleCoalescedInvalidation(");
    expect(handler).not.toContain("invalidateQueries(");
  });

  it("Android debounces the sync its realtime events start", () => {
    const source = read(ANDROID_APP_VM);
    expect(source).toContain("scheduleRealtimeSync()");
    const handler = blockAfter(source, /realtimeClient\.events\.collect \{ event ->/);
    expect(handler).not.toBeNull();
    // The listener must schedule, not sync: `syncAndUpdateOfflineState` awaited
    // per event is one full sync per completion.
    expect(handler).toContain("scheduleRealtimeSync()");
  });
});

describe("a staged row leaves once: claimed before the write, released after the quiet", () => {
  it("the batch hooks its rows before it prunes them", () => {
    const source = read(WEB_BULK_ACTIONS);
    const complete = blockAfter(source, /const completeSelected = useCallback\(/);
    expect(complete).not.toBeNull();
    const claim = complete!.indexOf("stageTodoRows(");
    const prune = complete!.indexOf("pruneStagedRows(");
    expect(claim).toBeGreaterThanOrEqual(0);
    expect(prune).toBeGreaterThanOrEqual(0);
    // A prune that runs before the claim is a write the guard is not yet watching.
    expect(claim).toBeLessThan(prune);
  });

  it("the batch prunes through the module that owns the roots, not a list of its own", () => {
    const prune = blockAfter(read(WEB_BULK_ACTIONS), /const pruneStagedRows = useCallback\(/);
    expect(prune).not.toBeNull();
    // The guard claims `ROW_LIST_KEY_ROOTS` — seven roots. A caller that prunes
    // three of them leaves the rest holding the row until something writes them,
    // which is a departure the toast cannot put back. The roots live in one
    // module; a hand-written `["todo"]` / `["todoTimeline"]` / `["list"]` triple
    // here is the drift this asserts against.
    expect(prune).toContain("pruneTodoRowCaches(");
    expect(prune).not.toMatch(/setQueryData<TodoItemType\[\]>\(\["todo/);
    expect(prune).not.toMatch(/setQueriesData<TodoItemType\[\]>\(\{ queryKey: \["list"\] \}/);
  });

  it("the release is settled after the reads it authorises are cancelled", () => {
    const source = read(WEB_BULK_ACTIONS);
    const commit = blockAfter(source, /commit: \(\) => \{/);
    expect(commit).not.toBeNull();
    const settle = commit!.indexOf("settleTodoRows(");
    const refresh = commit!.indexOf("refreshTodoViews");
    expect(settle).toBeGreaterThanOrEqual(0);
    expect(refresh).toBeGreaterThanOrEqual(0);
    // `releaseTodoRows` then `refreshTodoViews` is the reported bug: a read the
    // window started is still in flight, no longer filtered, and writes every row
    // back at once — and the refresh that follows takes them away again.
    expect(commit).not.toContain("releaseTodoRows(");
    expect(settle).toBeLessThan(refresh);
  });

  it("the claim owns the roots, and settles by cancelling them", () => {
    const source = read(WEB_STAGED_ROWS);
    expect(source).toMatch(/export async function settleTodoRows\(/);
    expect(source).toContain("cancelQueries(");
    // The prune has to be the same shape as the claim, including the
    // `{ list, floaters }` container, or a root the claim covers cannot be
    // cleared at the tap.
    expect(source).toMatch(/export function pruneTodoRowCaches\(/);
    expect(source).toContain("withoutStagedRows(");
  });
});
