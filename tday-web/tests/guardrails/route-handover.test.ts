import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

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
 * Read out of the stylesheet as text rather than out of a render, for the reason
 * `press-affordance-cascade` gives next door: jsdom has no view transitions to
 * start, so a rendered assertion can only ever see the path that was already there.
 */
describe("the route hand-over composes its two halves", () => {
  const SRC = resolve(__dirname, "../../src");
  const css = readFileSync(resolve(SRC, "globals.css"), "utf8");
  const navigation = readFileSync(resolve(SRC, "lib/navigation.tsx"), "utf8");

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

  it("starts a transition only for a change of pathname, the same question RouteFade asks", () => {
    // A view transition snapshots the whole document, so one started for a
    // query-string change would crossfade a page with itself — the flash `RouteFade`
    // already declines to draw when it keys its own fade on pathname alone.
    expect(navigation).toMatch(/function startsRouteHandover\(/);
    expect(navigation).toMatch(/targetPath !== current/);
    // And a `to` that is only a query string or only a fragment splits to the empty
    // string, which is the one way that comparison can call the current page a
    // different one.
    expect(navigation).toMatch(/targetPath === ""/);
    expect(navigation).toMatch(/typeof document\.startViewTransition !== "function"/);
  });
});
