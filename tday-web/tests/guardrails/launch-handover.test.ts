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
const MONO = path.resolve(__dirname, "..", "..", "..");
const ROOT_VIEW = path.join(MONO, "ios-swiftUI", "Tday", "Feature", "App", "AppRootView.swift");
const ZOOM_NAV = path.join(MONO, "ios-swiftUI", "Tday", "Core", "Navigation", "ZoomNavigation.swift");
const HOME_SCREEN = path.join(
  MONO,
  "ios-swiftUI",
  "Tday",
  "Feature",
  "ScheduledTaskHome",
  "ScheduledTaskHomeScreen.swift",
);

/**
 * Comments and string literals blanked, length preserved.
 *
 * Both matter, for different reasons. The comments argue in prose about the rung
 * and the curves — the call site quotes `--tday-duration-enter` while doing it —
 * so a scan that read them would pass on the argument for the code instead of on
 * the code, which is the mistake `motion-parity`'s own counters strip comments to
 * avoid. The strings are for the brace walk: an interpolation carries braces that
 * would desync the depth counter. `AppRootView`'s body holds exactly one string
 * literal today and it is empty, so that half is insurance there — but the tile
 * board below is built out of asset names and `L("…")` calls, so on that file it is
 * load-bearing from the first line.
 */
function stripCommentsAndStrings(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, (m) => m.replace(/[^\n]/g, " "))
    .replace(/"(?:\\.|[^"\\\n])*"/g, (m) => " ".repeat(m.length));
}

/** The index of the closer that balances the bracket at `open`. */
function balanced(source: string, open: number, close: string, where_ = "AppRootView.swift"): number {
  const opener = source[open];
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === opener) depth++;
    else if (source[i] === close && --depth === 0) return i;
  }
  throw new Error(`${where_}: unbalanced ${opener} at ${open} — the strip above desynced`);
}

describe("the cold launch hands over instead of cutting", () => {
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

/**
 * THE SIX HOME TILES ZOOMING INTO WHAT THEY OPEN, READ AS TEXT
 *
 * In this file rather than one of its own because it is the same claim about the
 * same view: `AppRootView` owns both navigation boundaries a user crosses without
 * asking for one — the launch hand-over above, and every push out of the root feed
 * here — and neither can be compiled, run or looked at on the machine this suite
 * runs on. What differs is which way the shape can go wrong.
 *
 * The zoom is two iOS 18 modifiers on two views two files apart, matched by a
 * string. Nothing reports a mismatch: `.navigationTransition(.zoom(sourceID:in:))`
 * whose source is not on screen falls back to the stock push, and
 * `.matchedTransitionSource` nobody asks for is inert. So every failure this unit
 * can have looks identical from here and from review — the transition simply stops
 * being the feature — and three of them have no other gate at all:
 *
 *   1. The availability guard. The deployment target is iOS 17.0 and both APIs are
 *      iOS 18.0, so an unguarded call compiles nowhere; `ZoomNavigationTests` is a
 *      value test and never touches the modifiers, and there is no Swift toolchain
 *      here. This is the one mistake in the unit that nothing else would catch
 *      before an Xcode build.
 *   2. The six tiles naming the routes their own closures push. `zoomRoute` and
 *      `action` are two independent arguments at each construction, and a tile
 *      wired to the Overdue screen while publishing the All tile's id is six ids,
 *      all distinct, every assertion in `ZoomNavigationTests` green, and a screen
 *      growing out of the wrong rectangle.
 *   3. One destination site. The zoom is applied over every route at a single
 *      `.navigationDestination`, which is what lets a route with no source id fall
 *      through without a list to keep in step; a second site is a second list.
 */
describe("the home tiles zoom into the screens they open", () => {
  const zoom = stripCommentsAndStrings(readFileSync(ZOOM_NAV, "utf-8"));
  const root = stripCommentsAndStrings(readFileSync(ROOT_VIEW, "utf-8"));
  const home = stripCommentsAndStrings(readFileSync(HOME_SCREEN, "utf-8"));

  /** Every `X(` argument list in `source`, sliced by paren rather than by line. */
  function constructions(source: string, name: string, where_: string): string[] {
    const found: string[] = [];
    for (let at = source.indexOf(`${name}(`); at !== -1; at = source.indexOf(`${name}(`, at + 1)) {
      const open = at + name.length;
      found.push(source.slice(open + 1, balanced(source, open, ")", where_)));
    }
    return found;
  }

  /**
   * The value passed for `label:` in one argument list, whitespace collapsed.
   *
   * Depth-aware, because `.allTodos(highlightTodoId: nil)` carries a comma-free
   * paren of its own and a scan that stopped at the first `,` would read half of it.
   */
  function argument(args: string, label: string, where_: string): string | null {
    const at = args.indexOf(`${label}:`);
    if (at === -1) return null;
    let depth = 0;
    for (let i = at + label.length + 1; i < args.length; i++) {
      const c = args[i];
      if (c === "(" || c === "[" || c === "{") depth++;
      else if (c === ")" || c === "]" || c === "}") depth--;
      else if (c === "," && depth === 0) {
        return args.slice(at + label.length + 1, i).trim().replace(/\s+/g, " ");
      }
      if (depth < 0) throw new Error(`${where_}: ${label} ran past its argument list`);
    }
    return args.slice(at + label.length + 1).trim().replace(/\s+/g, " ");
  }

  /** The `{ … }` bodies of every `if #available(iOS 18.0, *)` in `ZoomNavigation.swift`. */
  const guarded: Array<[number, number]> = [];
  const MARKER = "if #available(iOS 18.0, *)";
  for (let at = zoom.indexOf(MARKER); at !== -1; at = zoom.indexOf(MARKER, at + MARKER.length)) {
    const open = zoom.indexOf("{", at + MARKER.length);
    guarded.push([open, balanced(zoom, open, "}", "ZoomNavigation.swift")]);
  }

  it("keeps both iOS 18 APIs behind the availability check the target needs", () => {
    const uses: string[] = [];
    for (const api of [".matchedTransitionSource(", ".navigationTransition("]) {
      for (let at = zoom.indexOf(api); at !== -1; at = zoom.indexOf(api, at + api.length)) {
        if (!guarded.some(([open, close]) => at > open && at < close)) uses.push(`${api} at ${at}`);
      }
    }
    expect(
      uses,
      "the deployment target is iOS 17.0 and both of these are iOS 18.0 — an unguarded " +
        "call compiles nowhere, and nothing else on this machine can say so",
    ).toEqual([]);
    // A floor, not a formality: the rule above passes for free the day the two
    // modifiers stop being in this file at all.
    expect(guarded.length, "`if #available(iOS 18.0, *)` blocks").toBe(2);
  });

  it("leaves reduced motion to the gate rather than reading the setting again", () => {
    // A zoom is the large-amplitude case Apple's own guidance names, and the
    // substitute is the platform's: the stock push, which draws the finished screen
    // and adds no wait (`docs/motion.md`'s fifth idiom rule). Both halves have to
    // refuse together — one alone leaves a source published for a destination that
    // will not ask for it — so both are asserted, not the pair.
    expect(zoom.match(/tdayAnimation\.isEnabled/g) ?? []).toHaveLength(2);
    expect(zoom).not.toMatch(/accessibilityReduceMotion/);
    // Neither API takes a length. A `duration:` here would be a literal bought with
    // nothing, on a counter (`ios.easeDuration`) that has no headroom.
    expect(zoom).not.toMatch(/\bduration\s*:\s*\d/);
  });

  it("publishes one namespace and applies the destination over every route once", () => {
    expect(root).toMatch(/@Namespace private var zoomNamespace/);
    expect(root.match(/\.environment\(\\\.tdayZoomNamespace/g) ?? []).toHaveLength(1);
    expect(
      root.match(/\.tdayZoomDestination\(/g) ?? [],
      "one site covers every push — a second one is a second list of routes to keep in step",
    ).toHaveLength(1);
    expect(root.match(/\.navigationDestination\(for: AppRoute\.self\)/g) ?? []).toHaveLength(1);
  });

  it("makes every tile publish the id of the route its own closure pushes", () => {
    const board = constructions(home, "ScheduledTaskHomeCategoryBoard", "ScheduledTaskHomeScreen.swift");
    expect(board, "the board is built at exactly one site").toHaveLength(1);

    const tiles = constructions(home, "ScheduledTaskHomeCategoryTile", "ScheduledTaskHomeScreen.swift");
    expect(tiles, "the six category tiles").toHaveLength(6);

    // The tile is handed an opaque `() -> Void`, so what it pushes is only knowable
    // one level up: `action:` names the board's parameter, and the board's own
    // argument at that label holds the `onNavigate(…)` the press runs.
    const pushedBy = new Map<string, string>();
    for (const label of ["onOpenOverdue", "onOpenScheduled", "onOpenAll", "onOpenPriority", "onOpenCompleted", "onOpenCalendar"]) {
      const closure = argument(board[0], label, "ScheduledTaskHomeScreen.swift");
      expect(closure, `the board must be handed ${label}`).not.toBeNull();
      const at = closure!.indexOf("onNavigate(");
      expect(at, `${label} must push a route`).toBeGreaterThan(-1);
      const open = at + "onNavigate".length;
      pushedBy.set(label, closure!.slice(open + 1, balanced(closure!, open, ")", "ScheduledTaskHomeScreen.swift")).trim());
    }

    const wiring = tiles.map((tile) => [
      argument(tile, "action", "ScheduledTaskHomeScreen.swift"),
      argument(tile, "zoomRoute", "ScheduledTaskHomeScreen.swift"),
    ]);
    expect(
      wiring.filter(([, route]) => route === null),
      "every tile carries the route it pushes — without it the tile publishes nothing and the push is the stock slide",
    ).toEqual([]);
    expect(
      wiring.map(([action, route]) => `${action} → ${route}`).sort(),
      "each tile must publish the id of the route its OWN closure pushes: a tile whose " +
        "zoomRoute and action disagree grows the wrong screen out of the wrong rectangle, " +
        "and every other rule in this unit stays green while it does",
    ).toEqual(
      wiring.map(([action]) => `${action} → ${pushedBy.get(action!)}`).sort(),
    );

    // And the source half is applied once, on the tile itself rather than on one of
    // the gradients inside it — the Phase-7 shape, where a modifier is written, is
    // read, and is attached to a node the transition never looks at.
    expect(home.match(/\.tdayZoomSource\(/g) ?? []).toHaveLength(1);
    expect(home).toMatch(/\.buttonStyle\(ScheduledTaskHomeTileButtonStyle\(\)\)\s*\.tdayZoomSource\(zoomRoute\)/);
  });
});
