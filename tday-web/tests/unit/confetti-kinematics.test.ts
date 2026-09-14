import { describe, expect, it } from "vitest";
import {
  alpha,
  fan,
  frame,
  METRICS,
  type ConfettiPiece,
} from "@/components/app/confetti-kinematics";

/**
 * The six invariants of `docs/confetti-spec.md`, on the web third of the model.
 *
 * **This file has to live under `tests/`, not beside the source.**
 * `vitest.config.ts` collects `tests/` and only `tests/`, so a file at
 * `src/components/app/confetti-kinematics.test.ts` is never collected: it compiles,
 * it type-checks, it lints, and it is never run. The spec calls that out as one of
 * the burst's two traps — its twin is the iOS test that has to be registered in
 * `project.pbxproj` — and they are the same trap, a test in the wrong place that
 * leaves CI green by never asking anything.
 *
 * Why invariants and not golden values: the model has never been seen on a device,
 * and its numbers were derived on paper. What is worth pinning is the shape — travel
 * that decelerates to a finite reach, a fall that approaches a terminal speed without
 * crossing it, a fade that reaches exactly zero with no slope, a flip that is not the
 * rotation — because that is what a plausible-looking edit would quietly break.
 *
 * The last block is not one of the six. The spec's list leaves the sway and the rock
 * with no assertion at all, and they are half of what makes this paper rather than a
 * diagram: without them forty-six pieces fall down forty-six straight lines. A
 * describe that the spec does not ask for is cheaper than an invariant nobody can
 * see fail.
 */

/** Double tolerances, per the spec. Android's Float twin uses step 0.05 and 1e-4. */
const STEP = 0.01;
const STEPS = Math.round(1 / STEP);
const TOL = 1e-9;

/**
 * A piece built by hand, with the same six derivations `fan()` caches.
 *
 * Written out a second time on purpose. An invariant checked against a piece whose
 * cache came from `fan()` cannot tell a broken cache from a broken `frame()`; this
 * way the two expressions have to agree, and I5 then checks the real fan against the
 * same bounds.
 */
function buildPiece(overrides: Partial<ConfettiPiece> & { angle: number; speed: number }): ConfettiPiece {
  const base = {
    spin: 0,
    spinPhase: 0,
    flipRate: 0,
    flipPhase: 0,
    width: 7,
    height: 10,
    colorIndex: 0,
    delay: 0,
    dragScale: 1,
    muzzle: 0,
    swayAmp: 0,
    swayRate: 0,
    swayPhase: 0,
    ...overrides,
  };
  const cos = Math.cos(base.angle);
  const sin = Math.sin(base.angle);
  const k = METRICS.drag * base.dragScale;
  return {
    ...base,
    vx0: cos * base.speed,
    vy0: sin * base.speed,
    k,
    vt: METRICS.gravity / k,
    mx: base.muzzle * cos,
    my: base.muzzle * sin,
  };
}

/** `frame()` with the "it is in the air" precondition asserted rather than assumed. */
function frameAt(piece: ConfettiPiece, t: number) {
  const state = frame(piece, t);
  if (!state) throw new Error(`expected a frame at t=${t}`);
  return state;
}

/** `i / count` rather than a running sum: an accumulated step drifts off the grid. */
function grid(count: number): number[] {
  return Array.from({ length: count + 1 }, (_, i) => i / count);
}

function forwardDifferences(values: number[]): number[] {
  return values.slice(1).map((value, i) => value - values[i]);
}

describe("I1 — nothing before the throw, and it leaves from the muzzle patch", () => {
  const pieces = fan();

  it("draws nothing at, before, or halfway to a piece's launch", () => {
    for (const piece of pieces) {
      expect(frame(piece, 0)).toBeNull();
      expect(frame(piece, piece.delay / 2)).toBeNull();
      expect(frame(piece, piece.delay)).toBeNull();
    }
  });

  it("draws nothing on the last frame of the flight", () => {
    // The `tau >= 1` exit is inside `frame()` so that a draw layer cannot forget it.
    for (const piece of pieces) expect(frame(piece, 1.0)).toBeNull();
  });

  it("has forty-six launch points on the patch rather than one pixel", () => {
    // The frame check below compares `dx, dy` against the piece's OWN `mx, my`, so a
    // fan that stopped offsetting anything would satisfy it with zero on both sides —
    // which is precisely the defect this model was written to fix. The draw has to be
    // checked against the range it came from instead.
    const launchPoints = new Set(pieces.map((piece) => `${piece.mx},${piece.my}`));
    expect(launchPoints.size).toBe(pieces.length);
    for (const piece of pieces) {
      expect(Math.hypot(piece.mx, piece.my)).toBeLessThanOrEqual(METRICS.maxMuzzle);
    }
    // Spread across the patch, not clustered at its centre. From the range and not
    // from this seed: forty-six draws on [0, MaxMuzzle] all landing in the inner half
    // is a 2⁻⁴⁶ event, so this stays true through a re-roll.
    const furthest = Math.max(...pieces.map((piece) => Math.hypot(piece.mx, piece.my)));
    expect(furthest).toBeGreaterThan(METRICS.maxMuzzle / 2);
  });

  it("leaves from its own point on the patch rather than from the origin", () => {
    for (const piece of pieces) {
      const state = frameAt(piece, piece.delay + 1e-4 * (1 - piece.delay));
      // Still inside the thumb-sized source patch: 0.03 span plus the ~2e-4 of
      // travel that a ten-thousandth of the flight buys at these launch speeds.
      expect(Math.hypot(state.dx, state.dy)).toBeLessThanOrEqual(0.031);
      expect(Math.hypot(state.dx - piece.mx, state.dy - piece.my)).toBeLessThanOrEqual(1e-3);
    }
  });
});

describe("I2 — drag: outward travel concave, reach finite", () => {
  // Straight out along +x at the top speed, with nothing else switched on, so `dx`
  // is the drag term alone.
  const piece = buildPiece({ angle: 0, speed: METRICS.minSpeed + METRICS.speedRange });
  const samples = grid(STEPS).filter((t) => t > 0 && t < 1);
  const dx = samples.map((t) => frameAt(piece, t).dx);

  it("travels outward without ever turning back", () => {
    for (const difference of forwardDifferences(dx)) expect(difference).toBeGreaterThan(0);
  });

  it("decelerates the whole way — no epsilon, because the tail differences are tiny", () => {
    const second = forwardDifferences(forwardDifferences(dx));
    for (const difference of second) expect(difference).toBeLessThan(0);
  });

  it("has spent one time constant of the throw at a sixth of the flight", () => {
    // `speed * (1 - e^-1) / k`. The spec quotes this as 0.2054392, which is the
    // closed form rounded to seven places; pinned against the form itself because
    // a 1e-9 assertion on the rounded figure would be pinning the rounding.
    const expected = (piece.speed * (1 - Math.exp(-1))) / piece.k;
    expect(Math.abs(frameAt(piece, 1 / 6).dx - expected)).toBeLessThanOrEqual(TOL);
    expect(Math.abs(frameAt(piece, 1 / 6).dx - 0.2054392)).toBeLessThanOrEqual(5e-8);
  });

  it("stops short of a bound instead of running off the box", () => {
    // The whole point of the drag term: reach is `speed / k` = 0.325 and the piece
    // never gets there. Undragged, this same launch speed would be at 1.95 span.
    expect(frameAt(piece, 0.999).dx).toBeGreaterThanOrEqual(0.32175);
    expect(frameAt(piece, 0.999).dx).toBeLessThanOrEqual(0.325);
  });
});

describe("I3 — terminal velocity approached from both sides, never crossed", () => {
  const speed = METRICS.minSpeed + METRICS.speedRange;
  // Straight up and straight down, y growing downward.
  const rising = buildPiece({ angle: -Math.PI / 2, speed });
  const falling = buildPiece({ angle: Math.PI / 2, speed });
  const samples = grid(STEPS).filter((t) => t > 0 && t < 1);

  /** Average vertical speed over each step — the thing `dy` actually does. */
  const verticalSpeeds = (piece: ConfettiPiece) =>
    forwardDifferences(samples.map((t) => frameAt(piece, t).dy)).map((d) => d / STEP);

  it("gives a piece its own terminal speed", () => {
    expect(Math.abs(rising.vt - 0.85)).toBeLessThanOrEqual(TOL);
  });

  it("lets a piece thrown upward fall back and settle, without overshooting", () => {
    const speeds = verticalSpeeds(rising);
    expect(speeds[0]).toBeLessThan(0);
    for (const difference of forwardDifferences(speeds)) expect(difference).toBeGreaterThan(0);
    for (const v of speeds) expect(v).toBeLessThanOrEqual(rising.vt + TOL);
    expect(speeds[speeds.length - 1]).toBeGreaterThanOrEqual(0.985 * rising.vt);
  });

  it("slows a piece thrown downward to the same speed rather than letting it run away", () => {
    // This is the half a parabola cannot do: today's model would have this piece
    // accelerating past everything else and off the bottom of the box.
    const speeds = verticalSpeeds(falling);
    expect(speeds[0]).toBeGreaterThan(falling.vt);
    for (const difference of forwardDifferences(speeds)) expect(difference).toBeLessThan(0);
    for (const v of speeds) expect(v).toBeGreaterThanOrEqual(falling.vt - TOL);
    expect(speeds[speeds.length - 1]).toBeLessThanOrEqual(1.015 * falling.vt);
  });
});

describe("I4 — fade schedule", () => {
  it("is fully opaque right up to the fade, and no longer than that", () => {
    // Asserted at FadeStart ± 1e-3 and never at FadeStart itself: `(t - delay) /
    // (1 - delay)` rounds either side of 0.60, and on Android's Float it rounds
    // further. The assertion is about the schedule, not about a rounding mode.
    expect(alpha(METRICS.fadeStart - 1e-3)).toBeGreaterThanOrEqual(1 - 1e-6);
    expect(alpha(METRICS.fadeStart + 1e-3)).toBeLessThan(1);
  });

  it("is exactly half gone at four fifths of the flight", () => {
    expect(Math.abs(alpha(0.8) - 0.5)).toBeLessThanOrEqual(TOL);
  });

  it("is the smoothstep and not the straight line it is half-mistakable for", () => {
    // The half-way pin above cannot see the difference: at tau 0.80 `u` is 0.5, where
    // smoothstep and a plain `1 - u` are the same 0.5. These two are where the curves
    // part — the spec's own verified numbers, which a linear tail answers 0.75 and
    // 0.25. Tolerance 5e-4 because 0.844 and 0.156 are the exact 0.84375 and 0.15625
    // rounded to three places, and the pin is on the curve, not on the rounding.
    expect(Math.abs(alpha(0.7) - 0.844)).toBeLessThanOrEqual(5e-4);
    expect(Math.abs(alpha(0.9) - 0.156)).toBeLessThanOrEqual(5e-4);
  });

  it("arrives at zero with no slope into it", () => {
    // The whole argument for the smoothstep: a linear tail is still shedding 2.5
    // alpha per flight as it hits zero, and that last step is the visible edge the
    // burst ends on. A hundredth of the flight out, the smoothstep has 3d² left with
    // d = 0.025 of the fade window — 0.0018, against the 0.025 a straight line would
    // still be carrying.
    const step = 0.01;
    expect(alpha(1 - step)).toBeLessThanOrEqual(3e-3);
    // And it is not a fade that simply quit early: it is still moving at the half-way
    // point, where the same measure over the same step is forty times larger.
    expect(alpha(0.8 - step) - alpha(0.8 + step)).toBeGreaterThan(0.07);
  });

  it("reaches zero exactly, which is why it is a separate export", () => {
    // There is no frame at tau = 1 to read this off, and a fade that ends at 0.02
    // is a burst that ends on a visible edge.
    expect(alpha(1.0)).toBe(0);
  });

  it("never brightens", () => {
    const values = grid(200).map(alpha);
    for (const difference of forwardDifferences(values)) expect(difference).toBeLessThanOrEqual(0);
  });
});

describe("I5 — stays in the box, and the fan is choreography", () => {
  const pieces = fan();

  /**
   * The bounds, derived from the parameter ranges rather than from this seed.
   *
   * That distinction is the trap the spec records: the proposal this model came from
   * quoted a `dy` ceiling that was simply wrong for its own wider ranges, and a
   * bound taken off one seed would have agreed with it.
   *
   * `|dx| <= muzzle + speed/k + swayAmp`, each at its worst and all with `|cos|` at
   * its largest over the 200°..340° sweep (0.9397, at either end):
   *   0.03*0.9397 + 1.95*0.9397/5.4 + 0.04 = 0.4075, and the true worst is 0.4058
   *   because the sway is not at its peak when the travel is.
   * `dy` is lowest at the apex of the hardest straight-up throw (angle 270°, speed
   *   1.95, dragScale 0.90): -0.1952 at tau 0.2074.
   * `dy` is highest at the end of the flattest, slowest throw (200°, speed 1.10,
   *   dragScale 0.90, so the largest vt): +0.7005 as tau approaches 1.
   * Rounded outward to leave the fan room to be re-rolled without re-deriving these.
   */
  const DX_LIMIT = 0.44;
  const DY_FLOOR = -0.22;
  const DY_CEILING = 0.72;

  it("keeps every piece inside the box for the whole flight", () => {
    for (const piece of pieces) {
      for (const t of grid(200)) {
        const state = frame(piece, t);
        if (!state) continue;
        expect(state.dx).toBeGreaterThanOrEqual(-DX_LIMIT);
        expect(state.dx).toBeLessThanOrEqual(DX_LIMIT);
        expect(state.dy).toBeGreaterThanOrEqual(DY_FLOOR);
        expect(state.dy).toBeLessThanOrEqual(DY_CEILING);
        expect(state.widthScale).toBeGreaterThanOrEqual(METRICS.minFlip);
        expect(state.widthScale).toBeLessThanOrEqual(1);
        expect(state.alpha).toBeGreaterThanOrEqual(0);
        expect(state.alpha).toBeLessThanOrEqual(1);
      }
    }
  });

  it("rolls the same burst every time", () => {
    // A celebration that is different on every list is a random effect; this one is
    // designed, and the seed is what says so.
    expect(fan()).toEqual(pieces);
  });

  it("fans up and out, in order, within every drawn range", () => {
    expect(pieces).toHaveLength(METRICS.pieceCount);
    const sweepEnd = METRICS.fanStart + METRICS.fanSweep;
    let previousAngle = -Infinity;
    for (const piece of pieces) {
      // Stratified, so the fan sweeps rather than scattering: a piece never comes
      // out behind the one before it.
      expect(piece.angle).toBeGreaterThanOrEqual(previousAngle);
      previousAngle = piece.angle;
      expect(piece.angle).toBeGreaterThanOrEqual(METRICS.fanStart);
      expect(piece.angle).toBeLessThan(sweepEnd);
      expect(piece.speed).toBeGreaterThanOrEqual(METRICS.minSpeed);
      expect(piece.speed).toBeLessThanOrEqual(METRICS.minSpeed + METRICS.speedRange);
      expect(Math.abs(piece.spin)).toBeGreaterThanOrEqual(METRICS.minSpin);
      expect(Math.abs(piece.spin)).toBeLessThanOrEqual(METRICS.minSpin + METRICS.spinRange);
      expect(piece.dragScale).toBeGreaterThanOrEqual(METRICS.minDragScale);
      expect(piece.dragScale).toBeLessThanOrEqual(METRICS.minDragScale + METRICS.dragScaleRange);
      expect(piece.delay).toBeGreaterThanOrEqual(0);
      expect(piece.delay).toBeLessThan(METRICS.maxLaunchDelay);
      // The rest of the sixteen. They were left unbounded here first time round, and
      // an unbounded draw is a draw the fan is free to stop making: muzzle, swayAmp
      // and swayRate could each be zeroed in `fan()` with every assertion in this
      // file still green, because nothing downstream of them has a floor.
      expect(piece.muzzle).toBeGreaterThanOrEqual(0);
      expect(piece.muzzle).toBeLessThanOrEqual(METRICS.maxMuzzle);
      expect(piece.swayAmp).toBeGreaterThanOrEqual(METRICS.minSwayAmp);
      expect(piece.swayAmp).toBeLessThanOrEqual(METRICS.minSwayAmp + METRICS.swayAmpRange);
      expect(piece.swayRate).toBeGreaterThanOrEqual(METRICS.minSwayRate);
      expect(piece.swayRate).toBeLessThanOrEqual(METRICS.minSwayRate + METRICS.swayRateRange);
      expect(piece.flipRate).toBeGreaterThanOrEqual(METRICS.minFlipRate);
      expect(piece.flipRate).toBeLessThanOrEqual(METRICS.minFlipRate + METRICS.flipRateRange);
      expect(piece.width).toBeGreaterThanOrEqual(METRICS.minWidth);
      expect(piece.width).toBeLessThanOrEqual(METRICS.minWidth + METRICS.widthRange);
      expect(piece.height).toBeGreaterThanOrEqual(METRICS.minHeight);
      expect(piece.height).toBeLessThanOrEqual(METRICS.minHeight + METRICS.heightRange);
      // An index into the draw layer's palette array, so integral or it reads off it.
      expect(Number.isInteger(piece.colorIndex)).toBe(true);
      expect(piece.colorIndex).toBeGreaterThanOrEqual(0);
      expect(piece.colorIndex).toBeLessThan(METRICS.colorCount);
      for (const phase of [piece.spinPhase, piece.flipPhase, piece.swayPhase]) {
        expect(phase).toBeGreaterThanOrEqual(0);
        expect(phase).toBeLessThan(2 * Math.PI);
      }
    }
  });
});

describe("I6 — the flip really is its own axis", () => {
  // Flip held still, edge-on, while the piece makes its largest in-plane turn.
  const piece = buildPiece({
    angle: 0,
    speed: METRICS.minSpeed,
    flipRate: 0,
    flipPhase: Math.PI / 2,
    spin: METRICS.minSpin + METRICS.spinRange,
  });

  it("does not turn the piece flat just because the piece is rotating", () => {
    for (const t of grid(STEPS)) {
      const state = frame(piece, t);
      if (!state) continue;
      // Not `toBe`: cos(π/2) is 6.1e-17 rather than 0 in binary64, and 0.75 of that
      // lands one ulp above MinFlip. The claim is the flip is frozen, not that the
      // library's cosine is exact.
      expect(Math.abs(state.widthScale - METRICS.minFlip)).toBeLessThanOrEqual(TOL);
    }
  });

  it("still rotates while the flip is frozen", () => {
    expect(frameAt(piece, 0.5).rot - frameAt(piece, 0.1).rot).toBeGreaterThan(1.5);
  });
});

describe("beyond the six — the flutter is wired, and the lean is the drift", () => {
  // Straight up, so `cos(angle)` is zero and the throw contributes no sideways travel
  // at all: `dx` is the sway term alone. Spin and spinPhase zero, so every radian of
  // `rot` is the rock.
  const piece = buildPiece({
    angle: -Math.PI / 2,
    speed: METRICS.minSpeed,
    swayAmp: METRICS.minSwayAmp + METRICS.swayAmpRange,
    swayRate: METRICS.minSwayRate,
  });
  const states = grid(STEPS)
    .map((t) => frame(piece, t))
    .filter((state): state is NonNullable<typeof state> => state !== null);

  it("drifts the piece to both sides instead of dropping it down a line", () => {
    // Nine radians of sway over the flight is a pass and a half, so a piece that is
    // actually fluttering has to reach both sides of its own launch line.
    expect(Math.max(...states.map((state) => state.dx))).toBeGreaterThan(0.01);
    expect(Math.min(...states.map((state) => state.dx))).toBeLessThan(-0.01);
  });

  it("banks the piece into the drift, which is the paper half of the model", () => {
    // Both terms are `sin(theta) * spent`, so the lean is the drift scaled by Rock —
    // exactly, at every tau. Dropping `Rock * sway` from `rot` leaves the piece
    // sliding sideways while staying bolt upright, and that reads as a sprite on a
    // path rather than as paper.
    for (const state of states) {
      expect(Math.abs(state.rot * piece.swayAmp - state.dx * METRICS.rock))
        .toBeLessThanOrEqual(TOL);
    }
  });
});
