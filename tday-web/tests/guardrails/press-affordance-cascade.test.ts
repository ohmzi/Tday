import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * THE PRESS AFFORDANCE AND THE LAYER IT HAS TO SIT IN
 *
 * The blanket press rule in `globals.css` is written with `:where()`, which is
 * specificity 0,0,0 on purpose so that a call site can say `active:scale-[0.97]`
 * and be obeyed. That made it look as though specificity was what decided the
 * rule's fate, and it is not: `transition-colors` — which every shadcn Button
 * carries — is a Tailwind utility, and `@layer utilities` beats `@layer base` at
 * EVERY specificity. Measured in Chromium before the fix: on a shadcn Button the
 * press geometry jumped to 0.985/1.5px in one frame and back in one frame, under
 * a ripple that still took its time, because the utility had replaced the press
 * rule's `transition-property` wholesale. A 0,3,4 selector written inside `base`
 * loses to that 0,1,0 utility just the same, so the arms-race fix is not merely
 * ugly here, it does not work.
 *
 * The fix is `@layer tday-press`, opened after `@import "tailwindcss"` so that it
 * sorts after `utilities`. Four things about it are load-bearing and none of
 * them is visible in a rendered className, which is why this is a source read
 * rather than a render assertion — the same reason `toast-action-specificity`
 * gives next door:
 *
 *   1. The layer is opened AFTER the import. Layer order follows first
 *      appearance. Move the block above the import and it sorts first instead,
 *      every defect above returns, and the stylesheet still reads correctly.
 *   2. The declarations a component class must not be able to delete are in it:
 *      which properties transition, what curve they take, and the
 *      reduced-motion floor, which the same utilities were overriding on every
 *      element that carried one.
 *   3. The pressed SCALE is not in it. That one is meant to be beaten — 17 call
 *      sites press to a chip's, a sheet button's or an icon button's own depth
 *      (1 × `[0.97]`, 8 × `[0.99]`, 8 × `scale-95`) — so it stays in `base`
 *      where a utility still outranks it. Promoting it would silence all 17 at
 *      once and look like a tidy-up while doing it.
 *   4. `min-width` is in the property list. Setting `transition-property` at all
 *      displaces `transition-all` as surely as it displaces `transition-colors`,
 *      and `all` is the one a closed list cannot be a superset of. The dock tab
 *      is the single pressable in the app relying on that difference, and a call
 *      site has no way to add a property back from below, so the list carries it.
 */
describe("the press affordance outranks the utilities it has to outrank", () => {
  const css = readFileSync(
    resolve(__dirname, "../../src/globals.css"),
    "utf8",
  );

  /** The layer block, brace-matched from its opener. */
  const pressLayer = (() => {
    const open = css.indexOf("@layer tday-press {");
    if (open < 0) return null;
    const start = css.indexOf("{", open);
    let depth = 0;
    for (let i = start; i < css.length; i++) {
      if (css[i] === "{") depth++;
      else if (css[i] === "}" && --depth === 0) {
        return { at: open, body: css.slice(start + 1, i) };
      }
    }
    throw new Error("globals.css: @layer tday-press is never closed");
  })();

  const outside = pressLayer
    ? css.slice(0, pressLayer.at) + css.slice(pressLayer.at + pressLayer.body.length)
    : css;

  it("declares the press layer at all", () => {
    expect(pressLayer).not.toBeNull();
  });

  it("opens the layer after the Tailwind import, which is what puts it above the utilities", () => {
    const tailwind = css.indexOf('@import "tailwindcss"');
    expect(tailwind).toBeGreaterThanOrEqual(0);
    expect(pressLayer!.at).toBeGreaterThan(tailwind);
  });

  // The property list and the curve are the two a `transition-*` utility replaces.
  it.each([
    ["the property list", /transition-property:\s*[^;]*\bscale\b[^;]*\btranslate\b/],
    ["the press curve", /transition-timing-function:\s*var\(--tday-ease-gesture\)/],
    ["the reduced-motion floor", /prefers-reduced-motion: reduce/],
  ])("keeps %s where a component class cannot delete it", (_label, pattern) => {
    expect(pressLayer!.body).toMatch(pattern);
  });

  /**
   * `transition-all` sits on 19 class strings, several of them on elements this
   * selector matches, and this declaration replaces it wherever it does. On all
   * but one the properties actually in flight — translate, scale, background,
   * colour, opacity, shadow — are in the list already. The exception is the dock
   * tab in `RootDock.tsx`, whose selected state is `sm:min-w-[104px]` against an
   * unselected `sm:min-w-12`: without `min-width` here it collapses in one frame
   * while the indicator pill beside it (a `pointer-events-none` div this selector
   * does not match, so it keeps `transition-property: all`) still glides for
   * 300 ms. Nothing at the call site can restore it — a `transition-[min-width]`
   * there loses to this rule too — so deleting it from this list is a silent
   * regression with no local symptom.
   */
  it("carries the one property `transition-all` was doing that the press does not", () => {
    expect(pressLayer!.body).toMatch(/transition-property:[^;]*\bmin-width\b/);
  });

  it("leaves the pressed scale below the utilities, where a call site can still beat it", () => {
    expect(pressLayer!.body).not.toMatch(/scale:\s*var\(--tday-press-/);
    expect(outside).toMatch(/scale:\s*var\(--tday-press-row\)/);
  });

  /**
   * The layer is what holds the pressed shadow down now. If an `!important`
   * comes back into the affordance it means someone met a collision with force
   * instead of putting the declaration in the layer that already wins.
   */
  it("needs no !important anywhere in the affordance", () => {
    // Comments stripped, because the argument for why the `!important` went away
    // says the word and would otherwise fail the assertion it is explaining.
    const affordance = css
      .slice(
        css.indexOf('[data-pressable="true"]'),
        css.lastIndexOf('[data-pressable="true"]'),
      )
      .replace(/\/\*[\s\S]*?\*\//g, "");
    expect(affordance).not.toContain("!important");
  });
});
