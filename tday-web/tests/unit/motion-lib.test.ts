/**
 * `src/lib/motion.ts` is the only place on the web where a generated motion token is reshaped by
 * hand, which makes it the only place a token can be mangled without the drift gate noticing.
 * `verifyMotionTokens` compares the generated artifacts against `MotionTokens.kt`, and
 * `motion-parity.test.ts` compares those artifacts against each other — both stop at the generated
 * file. Everything downstream of the import is this module's own work.
 *
 * So these tests cover the two things that work can get wrong: the string `cubicBezier` builds, and
 * whether `EASE` still offers every curve the vocabulary names. Neither is checked anywhere else,
 * and the second one fails silently — a curve that stops being re-exported is not a broken build,
 * it is a token the web quietly stops having.
 */

import { describe, expect, it } from "vitest";

import { EASINGS } from "@/generated/motion-tokens";
import { cubicBezier, DELAY_MS, DURATION_MS, EASE, PRESS_SCALE } from "@/lib/motion";

describe("cubicBezier", () => {
  it("writes the four control points in CSS order, comma-separated", () => {
    // Order is the whole risk here: (0.05, 0.7, 0.1, 1) and (0.7, 0.05, 1, 0.1) are both
    // syntactically valid curves, and a transposition would render as motion nobody flagged.
    expect(cubicBezier(EASINGS.scene)).toBe("cubic-bezier(0.05, 0.7, 0.1, 1)");
  });

  it("keeps an integral control point integral", () => {
    // `1` must not become `1.0`: the string is compared against hand-written CSS in review, and a
    // second spelling of the same curve is how one curve becomes two.
    expect(cubicBezier(EASINGS.standard)).toBe("cubic-bezier(0.4, 0, 0.2, 1)");
  });
});

describe("EASE", () => {
  it("offers every curve the generated vocabulary names", () => {
    // The literal-by-literal spelling in motion.ts is deliberate — it makes a sixth curve a
    // compile error rather than a silent omission. This asserts the runtime half of that: the
    // keys are compared to the generated module, not to a list restated here.
    expect(Object.keys(EASE).sort()).toEqual(Object.keys(EASINGS).sort());
  });

  it("gives each curve the same points the generated module holds", () => {
    for (const name of Object.keys(EASINGS) as (keyof typeof EASINGS)[]) {
      expect(EASE[name]).toBe(cubicBezier(EASINGS[name]));
    }
  });
});

describe("the millisecond re-exports", () => {
  it("keeps durations and delays in separate namespaces", () => {
    // `emphasis` and `celebrationLead` are both 320 and are not the same decision. If a refactor
    // ever merges these two objects, the two names become interchangeable to every later reader —
    // which is the exact failure the source of truth splits them to prevent.
    expect(DURATION_MS).not.toHaveProperty("celebrationLead");
    expect(DELAY_MS).not.toHaveProperty("emphasis");
  });

  it("hands out whole milliseconds, not seconds", () => {
    // iOS carries these as fractional seconds. A unit slip would leave the web animating 0.32ms.
    for (const ms of Object.values({ ...DURATION_MS, ...DELAY_MS })) {
      expect(Number.isInteger(ms)).toBe(true);
      expect(ms).toBeGreaterThan(1);
    }
  });
});

describe("PRESS_SCALE", () => {
  it("stays a bare ratio a scale property can take", () => {
    for (const scale of Object.values(PRESS_SCALE)) {
      expect(scale).toBeGreaterThan(0);
      expect(scale).toBeLessThanOrEqual(1);
    }
  });
});
