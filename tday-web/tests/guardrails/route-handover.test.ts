// @vitest-environment jsdom
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { startsRouteHandover } from "@/lib/routeHandover";
import { installReducedMotion } from "../setup/reduced-motion";

/**
 * THE ROUTE HAND-OVER, AND THE THREE UA DEFAULTS IT HAS TO BEAT
 *
 * A route change has two halves on web and only one of them exists everywhere.
 * `.tday-route-fade` fades the arriving screen up on every browser; where
 * `document.startViewTransition` exists, `lib/navigation.tsx` asks for one and the
 * screen being left comes back as a flat snapshot to fade out over the top — the
 * outgoing half at none of the cost of the live tree `RouteFade` refused to hold
 * mounted for it.
 *
 * The two are meant to COMPOSE, and every assertion here is about that seam. None
 * of the three overrides below has a local symptom if it is deleted: the transition
 * still runs, the route still changes, and what a reviewer sees in the diff is three
 * declarations that look like they are restating the default.
 *
 *   1. `::view-transition-new(root)` must not animate. It renders the arriving
 *      screen LIVE, so `.tday-route-fade` is already fading up inside it. Let the
 *      UA's own opacity curve back in and the arrival plays twice, multiplied — the
 *      fade of a fade this pairing exists to avoid.
 *   2. `::view-transition-old(root)` must therefore be the half that moves, which
 *      means it must sit ON TOP. The UA paints `new` last; an opaque `new` over a
 *      fading `old` hides the outgoing half completely, and the result is exactly
 *      today's behaviour with a snapshot nobody can see.
 *   3. The blend must be `normal`. `plus-lighter` is what stops the UA's own
 *      symmetric crossfade dipping through the background halfway; over two layers
 *      that are each opaque at one end it blows the screen out to white instead.
 *
 * The three are read out of the stylesheet as text rather than out of a render, for
 * the reason `press-affordance-cascade` gives next door: jsdom has no view
 * transitions to start, so a rendered assertion could only ever see the path that
 * was already there.
 *
 * That argument covers the CSS and stops there. The navigation half — WHICH
 * navigations ask for a transition at all — is a pure function, and jsdom can call
 * one however little it can play the result, so the last block below asserts what
 * `startsRouteHandover` returns rather than what `navigation.tsx` says. Grepping the
 * source for the guards would pass just as happily on an implementation that had
 * them in the wrong order.
 */
describe("the route hand-over composes its two halves", () => {
  const SRC = resolve(__dirname, "../../src");
  const css = readFileSync(resolve(SRC, "globals.css"), "utf8");

  /**
   * One rule's body, by exact prelude.
   *
   * A match whose line ends in a comma one line up is the TAIL of a selector list,
   * not a rule of its own — `::view-transition-new(root)` appears both ways in this
   * stylesheet, and reading the grouped rule's body as the standalone one's would
   * let the assertion below pass on a declaration that is not there.
   */
  function ruleBody(prelude: string): string | null {
    let at = -1;
    for (;;) {
      at = css.indexOf(`\n${prelude} {`, at + 1);
      if (at < 0) return null;
      if (css[at - 1] !== ",") break;
    }
    const start = css.indexOf("{", at);
    let depth = 0;
    for (let i = start; i < css.length; i++) {
      if (css[i] === "{") depth++;
      else if (css[i] === "}" && --depth === 0) return css.slice(start + 1, i);
    }
    throw new Error(`globals.css: ${prelude} is never closed`);
  }

  it("times the arriving screen on the Enter rung, by name", () => {
    // The 140 this replaced argued against the LONG end and still does, at the call
    // site. What it could not defend was 140 itself: it named no rung, so nothing
    // could tell a decision from a number somebody liked.
    const body = ruleBody(".tday-route-fade");
    expect(body).not.toBeNull();
    expect(body).toContain("var(--tday-duration-enter)");
    expect(body).toContain("var(--tday-ease-enter)");
  });

  it("gives the outgoing snapshot the same rung, so the hand-over has one length", () => {
    const body = ruleBody("::view-transition-old(root)");
    expect(body).not.toBeNull();
    expect(body).toContain("var(--tday-duration-enter)");
  });

  it("leaves the arriving snapshot un-animated, so the arrival is not faded twice", () => {
    expect(ruleBody("::view-transition-new(root)")).toMatch(/animation:\s*none/);
  });

  it("raises the outgoing snapshot above the arriving one, which the UA paints last", () => {
    expect(ruleBody("::view-transition-old(root)")).toMatch(/z-index:\s*[1-9]/);
  });

  it("takes both snapshots off plus-lighter, which two opaque layers blow out to white", () => {
    const body = ruleBody("::view-transition-old(root),\n::view-transition-new(root)");
    expect(body).not.toBeNull();
    expect(body).toMatch(/mix-blend-mode:\s*normal/);
  });

  it("pins the finished state under reduced motion instead of holding the screen just left", () => {
    // An un-animated `old` is opaque and on top: switching the animation off alone
    // would hold the previous screen for the frame the transition takes to end.
    const floor = css.slice(css.indexOf(".tday-route-fade {"));
    const block = floor.slice(floor.indexOf("@media (prefers-reduced-motion: reduce)"));
    expect(block).toMatch(/\.tday-route-fade\s*\{\s*animation:\s*none;?\s*\}/);
    expect(block).toMatch(
      /::view-transition-old\(root\)\s*\{[^}]*animation:\s*none;[^}]*opacity:\s*0/,
    );
  });

  describe("starts a transition only where one is worth playing", () => {
    const REAL_MATCH_MEDIA = window.matchMedia;

    /** jsdom has no view transitions; this is the presence the feature test reads. */
    function withViewTransitions() {
      (document as unknown as { startViewTransition: unknown }).startViewTransition =
        () => ({ finished: Promise.resolve() });
    }

    afterEach(() => {
      delete (document as unknown as { startViewTransition?: unknown })
        .startViewTransition;
      window.matchMedia = REAL_MATCH_MEDIA;
    });

    it("hands over when the pathname changes", () => {
      withViewTransitions();
      expect(startsRouteHandover("/en/app/calendar", "/en/app")).toBe(true);
    });

    it("declines when the destination is the page already on screen", () => {
      withViewTransitions();
      expect(startsRouteHandover("/en/app", "/en/app")).toBe(false);
    });

    it("declines a query-string-only destination, which is the same screen", () => {
      // A view transition snapshots the whole document, so one started for the
      // task-focus params the timeline pages push would crossfade a page with
      // itself — the flash `RouteFade` already declines to draw when it keys its
      // own fade on pathname alone. Asserted through the function because the
      // guard that catches it is a second one: `"?focus=1"` splits to the EMPTY
      // string, not to `/en/app`, so the pathname comparison alone calls it a
      // different page.
      withViewTransitions();
      expect(startsRouteHandover("?focus=1", "/en/app")).toBe(false);
    });

    it("declines a fragment-only destination for the same reason", () => {
      withViewTransitions();
      expect(startsRouteHandover("#section", "/en/app/guide")).toBe(false);
    });

    it("declines where the browser cannot play one", () => {
      // React Router falls back on its own, but warns once per app on every
      // environment that cannot honour the opt-in. jsdom is one of them, and this
      // is the state it is in with nothing stubbed.
      expect(startsRouteHandover("/en/app/calendar", "/en/app")).toBe(false);
    });

    it("declines under reduced motion, where the CSS override lands too late", () => {
      // The stylesheet can pin the finished frame; it cannot stop the opt-in
      // parking the new route in `pendingState` to be applied two effect passes
      // later, inside the transition callback. Leaving this to CSS alone would bill
      // that deferral to the users who asked for less motion, for nothing drawn.
      withViewTransitions();
      installReducedMotion(true);
      expect(startsRouteHandover("/en/app/calendar", "/en/app")).toBe(false);
    });
  });
});

/**
 * THE ANDROID HALF OF THE SAME HAND-OVER
 *
 * The block above is web's. This one is the other client's, and the point of them sitting
 * in one file is that a route change is one decision the two have to keep making the same
 * way: `Enter` in both directions, with the only difference between the two directions
 * being the curve. Web says that with `var(--tday-duration-enter)` on `.tday-route-fade`
 * and on `::view-transition-old(root)`; Android says it with `Durations.Enter` in
 * `navigationEnterTransition` and `navigationExitTransition`. Split these into two files
 * and the next hand to retime one of them has no reason to open the other.
 *
 * Read as text, for the reason the CSS half next door is: there is no Compose runtime in
 * vitest, so nothing here can play a NavHost transition or ask what one resolved to. What a
 * text read CAN see is everything this unit actually landed — a rung named rather than a
 * number written, four wirings rather than three, and two curves rather than one — and each
 * of those is a thing a later edit could undo while leaving something that still animates.
 * That is the failure mode: the wrong version of this change is not a broken screen, it is a
 * screen that fades at a length nobody chose.
 */
describe("the Android half of the same hand-over", () => {
  const APP = resolve(
    __dirname,
    "../../../android-compose/app/src/main/java/com/ohmz/tday/compose/TdayApp.kt",
  );
  const raw = readFileSync(APP, "utf8");

  /**
   * Kotlin with its comments blanked, copied from `motion-parity.test.ts` for the same
   * reason that file needs one: this unit argues its numbers in prose at the call site, and
   * an assertion that a literal is gone must not be satisfied — or defeated — by an argument
   * about it. The `:` guard is what keeps `"tday://settings"` from eating a deep link
   * declaration and everything after it on the line.
   */
  function stripComments(source: string): string {
    let out = "";
    let i = 0;
    let quote: string | null = null;
    while (i < source.length) {
      const c = source[i];
      const next = source[i + 1];
      if (quote) {
        if (c === "\\") { out += "  "; i += 2; continue; }
        if (c === quote) quote = null;
        out += c; i++; continue;
      }
      if (c === '"' || c === "'" || c === "`") { quote = c; out += c; i++; continue; }
      if (c === "/" && next === "*") {
        const end = source.indexOf("*/", i + 2);
        const skipped = source.slice(i, end < 0 ? source.length : end + 2);
        out += skipped.replace(/[^\n]/g, " ");
        i += skipped.length; continue;
      }
      if (c === "/" && next === "/" && source[i - 1] !== ":") {
        const end = source.indexOf("\n", i);
        i = end < 0 ? source.length : end; continue;
      }
      out += c; i++;
    }
    return out;
  }

  const code = stripComments(raw);

  /** One top-level `private fun`'s text, up to the next declaration at column zero. */
  function declaration(signature: string): string {
    const at = code.indexOf(signature);
    expect(at, `${signature} is gone`).toBeGreaterThan(-1);
    const rest = code.slice(at + signature.length);
    const end = rest.search(/\n(?:private |internal |@|fun |\/\*)/);
    return rest.slice(0, end < 0 ? rest.length : end);
  }

  /** The argument list of a call, by balancing its parentheses. */
  function callArguments(callee: string): string {
    const open = code.indexOf(`${callee}(`) + callee.length;
    expect(open, `${callee} is never called`).toBeGreaterThan(callee.length - 1);
    let depth = 0;
    for (let i = open; i < code.length; i++) {
      if (code[i] === "(") depth++;
      else if (code[i] === ")" && --depth === 0) return code.slice(open + 1, i);
    }
    throw new Error(`TdayApp.kt: ${callee}( is never closed`);
  }

  it("has no constant left holding a route fade's own length", () => {
    // Searched in the RAW text on purpose. These two are the numbers the unit retired, and a
    // comment that still names one of them as something the app does would be a doc rotting
    // in place — the stripper is for counting literals, not for hiding evidence.
    expect(raw).not.toContain("NAV_FADE_IN_DURATION_MS");
    expect(raw).not.toContain("NAV_FADE_OUT_DURATION_MS");
  });

  it("leaves no route naming a length of its own over the NavHost's", () => {
    // The splash and the two legacy auth entry points each wrote `tween(300)` across the
    // graph's defaults. A route that restates the hand-over is a route that drifts off it.
    expect(code).not.toContain("tween(300");
  });

  it("times both directions on the Enter rung, by name", () => {
    for (const fn of ["navigationEnterTransition", "navigationExitTransition"]) {
      expect(declaration(`private fun ${fn}(`)).toContain(
        "TdayMotionTokens.Durations.Enter",
      );
    }
  });

  it("wires all four directions through the gate, so the hand-over has no hole in it", () => {
    // Push and pop share one pair because nothing travels any more and there is no
    // direction left to express — which means a missing wiring is not a missing flourish,
    // it is one of the four ways out of a screen cutting while the other three fade.
    //
    // The argument is asserted with the slot and not separately, because the other way to
    // lose the gate is to keep all four wirings and hand one of them a literal `true`. That
    // reads as deliberate in a diff and animates for everybody, including the users who
    // turned the switch on.
    const args = callArguments("NavHost");
    for (const [slot, transition] of [
      ["enterTransition", "navigationEnterTransition"],
      ["exitTransition", "navigationExitTransition"],
      ["popEnterTransition", "navigationEnterTransition"],
      ["popExitTransition", "navigationExitTransition"],
    ]) {
      expect(args, `NavHost does not wire ${slot} through the preference`).toContain(
        `${slot} = { ${transition}(motionEnabled) }`,
      );
    }
  });

  it("keeps the two directions on different curves, which is the only thing separating them", () => {
    // One length, two curves — the same model web runs, where `.tday-route-fade` and
    // `::view-transition-old(root)` share `--tday-duration-enter` and differ only in the
    // easing. Now that both directions read the same duration token, collapsing the curves
    // too would leave a hand-over with nothing to say about which way it is going, and
    // nothing else in this file would notice.
    const enter = declaration("private fun navigationEnterTransition(");
    const exit = declaration("private fun navigationExitTransition(");
    expect(enter).toContain("LinearOutSlowInEasing");
    expect(enter).not.toContain("FastOutLinearInEasing");
    expect(exit).toContain("FastOutLinearInEasing");
    expect(exit).not.toContain("LinearOutSlowInEasing");
  });

  it("draws the destination finished rather than fading it when motion is refused", () => {
    // Compose's animator scale zeroes these on its own; the in-app Reduce Motion switch is
    // the half Compose cannot see, and the NavHost was the last surface deaf to it. `None`
    // is the fifth idiom rule in one word — the arriving screen on its first frame, not a
    // fade held at its start.
    expect(declaration("private fun navigationEnterTransition(")).toContain(
      "EnterTransition.None",
    );
    expect(declaration("private fun navigationExitTransition(")).toContain(
      "ExitTransition.None",
    );
    // Hoisted above the NavHost, because the four lambdas are transition scopes and not
    // composables. Read it inside one and this does not compile; forget it and it silently
    // does not apply — which is why this looks for the hoist in the NavHost's OWN scope
    // rather than anywhere in the file. Two unrelated screens further down hold a line of
    // exactly this text, and a whole-file search would have been satisfied by either of
    // them with the gate at the graph deleted outright.
    const HOIST = "val motionEnabled = rememberTdayMotionEnabled()";
    const navHost = code.indexOf("NavHost(");
    const hoist = code.lastIndexOf(HOIST, navHost);
    expect(hoist, "the NavHost is not handed a Reduce Motion answer").toBeGreaterThan(-1);
    expect(
      code.slice(hoist, navHost),
      "the hoist the NavHost reads is in some other function",
    ).not.toMatch(/\bfun\s/);
  });
});
