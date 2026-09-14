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
