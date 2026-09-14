import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

/**
 * THE COLD-LAUNCH HAND-OVER, READ AS TEXT
 *
 * `AppRootView`'s outermost `Group` is the boundary between the launch splash and
 * the first real screen, and until `ios-cold-launch-fade` it was a bare `if`: no
 * `.transition` on either arm and no `.animation(_:value:)` to play one in, so the
 * splash was cut out from under the app on every single launch. It is the one
 * motion in this app that every user sees and the only one none of them can miss.
 *
 * This suite reads the Swift as text, which needs the argument
 * `route-handover.test.ts` makes about jsdom and needs it harder: there is no Swift
 * toolchain on a machine that runs vitest, and there would be little to do with one
 * if there were — a SwiftUI hierarchy is not something node can render, so no
 * assertion here can watch the fade play. `xctest` in CI is the compile gate and a
 * TestFlight build is the eye check. What a text read CAN hold is the shape the
 * hand-over is spelled in, and each of the five below is a thing with no local
 * symptom when it goes missing:
 *
 *   1. One `Equatable` value keys it. `.animation(_:value:)` diffs what it is
 *      handed, and the two properties under this boundary can flip separately.
 *   2. Both arms carry a `.transition`, ON THE ARM'S OWN MODIFIER CHAIN. One alone is
 *      half a hand-over — the app fades up over a splash that already blinked out, or
 *      the reverse — and one that has slid down onto a child is the Phase-7 shape
 *      where the modifier is written, is read, and is attached to a node the
 *      boundary never touches. The app arm is three hundred lines of feed screens,
 *      each with `.transition`s of its own for the tab swap, so "somewhere in the
 *      subtree" is not a thing worth asserting here.
 *   3. The `Group` opens a transaction. A `.transition` is inert without one, so
 *      deleting this single modifier leaves both arms in the file, reading
 *      correctly, and never running — the exact failure `motion-reachability-ios`
 *      exists for, on the one boundary that file's rules do not reach.
 *   4. The animation resolves through `tdayAnimation(`, the Phase 8 gate, rather
 *      than a `reduceMotion ?` ternary. That ternary is the seven-site hand copy
 *      `TdayMotionEnvironment.swift` exists to have deleted, and one reappearing
 *      here would be the first of the seven growing back.
 *   5. No numeric duration anywhere in the block. A `duration: 0.2` would look
 *      right, play right, and be invisible to `:shared:verifyMotionTokens` — the
 *      rung would move and this site would stay where somebody typed it. It would
 *      also move `ios.easeDuration`, which has no headroom.
 */
describe("the cold launch hands over instead of cutting", () => {
  const MONO = path.resolve(__dirname, "..", "..", "..");
  const ROOT_VIEW = path.join(
    MONO,
    "ios-swiftUI",
    "Tday",
    "Feature",
    "App",
    "AppRootView.swift",
  );

  /**
   * Comments and string literals blanked, length preserved.
   *
   * Both matter, for different reasons. The comments argue in prose about the rung
   * and the curves — the call site quotes `--tday-duration-enter` while doing it —
   * so a scan that read them would pass on the argument for the code instead of on
   * the code, which is the mistake `motion-parity`'s own counters strip comments to
   * avoid. The strings are for the brace walk: an interpolation carries braces that
   * would desync the depth counter. This view's body holds exactly one string
   * literal today and it is empty, so that half is insurance — and if an
   * interpolated one ever lands, the walk throws rather than silently reading the
   * wrong slice.
   */
  function stripCommentsAndStrings(source: string): string {
    return source
      .replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, (m) => m.replace(/[^\n]/g, " "))
      .replace(/"(?:\\.|[^"\\\n])*"/g, (m) => " ".repeat(m.length));
  }

  /** The index of the closer that balances the bracket at `open`. */
  function balanced(source: string, open: number, close: string): number {
    const opener = source[open];
    let depth = 0;
    for (let i = open; i < source.length; i++) {
      if (source[i] === opener) depth++;
      else if (source[i] === close && --depth === 0) return i;
    }
    throw new Error(
      `AppRootView.swift: unbalanced ${opener} at ${open} — the strip above desynced`,
    );
  }

  /**
   * The `.transition(…)` argument lists on an arm's OWN modifier chain, as text.
   *
   * Depth-filtered, and that filter is the rule rather than an optimisation. The app
   * arm is the whole else branch: a `NavigationStack` over three feed screens that
   * each carry a `.transition(.opacity)` for the tab swap, three hundred lines below
   * the boundary this file is about. A scan that took any `.transition(` in the
   * subtree would go green on the launch hand-over having been moved onto one of
   * them, where it plays for a tab change and never for a launch — written, readable,
   * and on the wrong node, which is the failure Phase 7 kept finding. An arm root's
   * own modifiers are the ones that sit at the arm body's outermost depth, so that is
   * what this counts: every bracket, braces included, because a trailing closure is
   * how a child gets underneath one of these in the first place.
   */
  function chainTransitions(armBody: string): string[] {
    const found: string[] = [];
    let depth = 0;
    for (let i = 0; i < armBody.length; i++) {
      if (depth === 0 && armBody.startsWith(".transition(", i)) {
        const open = i + ".transition".length;
        found.push(armBody.slice(open, balanced(armBody, open, ")") + 1));
      }
      const c = armBody[i];
      if (c === "{" || c === "(" || c === "[") depth++;
      else if (c === "}" || c === ")" || c === "]") depth--;
    }
    return found;
  }

  const source = stripCommentsAndStrings(readFileSync(ROOT_VIEW, "utf-8"));

  /**
   * The `Group` the launch splash lives in, sliced by braces rather than by line.
   *
   * Anchored on `var body` so it cannot wander onto one of the several other
   * `Group`s further down the file, and bounded by the walk so the two arms below
   * are the real arms rather than whatever the next four hundred lines say.
   */
  const groupOpen = source.indexOf("{", source.indexOf("Group {", source.indexOf("var body: some View {")));
  const groupClose = balanced(source, groupOpen, "}");
  const group = source.slice(groupOpen, groupClose);

  /**
   * The two arms, split at the `Group`'s own depth.
   *
   * Each arm is its BODY — the text between the branch's own braces — so the depth a
   * `.transition` has to sit at to be on the arm root is zero, and `chainTransitions`
   * can say "outermost" without a second offset to subtract.
   *
   * Behind a function and behind an `expect` rather than computed alongside `group`,
   * because the branch marker is the thing rule 1 is about: without it the brace walk
   * has nothing to start from and would die with a lexing error at import time,
   * failing every rule below with a message about the scanner instead of about the
   * hand-over. A guardrail that cannot say what is wrong on the state it was written
   * against is not one.
   */
  function arms(): { splash: string; app: string } {
    const at = group.indexOf("if showsLaunchSplash");
    expect(at, "the launch branch must key on showsLaunchSplash").toBeGreaterThan(-1);
    const splashOpen = group.indexOf("{", at);
    const splashClose = balanced(group, splashOpen, "}");
    const elseOpen = group.indexOf("{", group.indexOf("else", splashClose));
    return {
      splash: group.slice(splashOpen + 1, splashClose),
      app: group.slice(elseOpen + 1, balanced(group, elseOpen, "}")),
    };
  }

  /** The modifiers on the `Group` itself — where the transaction has to be. */
  const transaction = source.slice(groupClose, source.indexOf(".overlay", groupClose));

  it("keys the hand-over on one value rather than on the two properties under it", () => {
    // A bootstrap that finishes while a finger is still holding the splash down is
    // one arrival, not an arrival followed by a second one.
    expect(source).toMatch(/private var showsLaunchSplash: Bool/);
    expect(group).toMatch(/if showsLaunchSplash\b/);
  });

  it("fades the departing splash out on Exit, the curve for something leaving", () => {
    const { splash } = arms();
    expect(splash).toContain("AppLaunchSplashView");
    expect(
      chainTransitions(splash).some(
        (spec) =>
          spec.includes("tdayAnimation(") &&
          spec.includes("TdayMotion.exit(duration: TdayMotion.Durations.enter)"),
      ),
      "the splash arm must carry a .transition on its OWN chain, gated on tdayAnimation, on Exit at the Enter rung",
    ).toBe(true);
  });

  it("fades the arriving app in on Enter, the curve for something that settles", () => {
    expect(
      chainTransitions(arms().app).some(
        (spec) =>
          spec.includes("tdayAnimation(") &&
          spec.includes("TdayMotion.enter(duration: TdayMotion.Durations.enter)"),
      ),
      "the app arm must carry a .transition on its OWN chain, not on a feed screen inside it, gated on tdayAnimation, on Enter at the Enter rung",
    ).toBe(true);
  });

  it("opens the transaction both transitions are inert without, on the same rung", () => {
    expect(transaction).toMatch(/\.animation\(/);
    expect(transaction).toContain("tdayAnimation(");
    expect(transaction).toContain("TdayMotion.Durations.enter");
    expect(transaction).toMatch(/value:\s*showsLaunchSplash/);
  });

  it("leaves reduced motion to the gate instead of branching on the setting again", () => {
    // The gate returns nil and the arms still swap: the app is drawn finished in the
    // frame the bootstrap completes, which is `docs/motion.md`'s fifth idiom rule and
    // needs no branch of its own here.
    expect(group + transaction).not.toMatch(/reduceMotion\s*\?/);
  });

  it("names every length instead of typing one, across the whole block", () => {
    // `ios.easeDuration` reads exactly this shape and is at its ceiling: a literal
    // here costs the budget AND goes quiet the next time the rung moves.
    expect(group + transaction).not.toMatch(/\bduration\s*:\s*\d*\.?\d+/);
  });
});
