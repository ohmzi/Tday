/**
 * The confetti burst's physics, with no React and no canvas in it.
 *
 * `docs/confetti-spec.md` is the normative file; this is its web third. The model
 * is one coherent idea — **linear (Stokes) drag** — which is what lets every
 * attribute have a closed form and lets the whole burst be a pure function of the
 * flight clock. A piece is thrown, the throw is spent against drag so the outward
 * reach is finite, and what is left is a fall that settles to that piece's own
 * terminal speed rather than accelerating forever.
 *
 * It lives apart from `Confetti.tsx` because the numbers are the part that can be
 * wrong. A canvas cannot be asked what it drew; this module can be asked what it
 * computed, and `tests/unit/confetti-kinematics.test.ts` asks it the six invariants
 * the spec holds all three clients to. Android and iOS have twins of this file for
 * the same reason.
 */

/** What the draw layer needs per piece per frame, and nothing else. */
export type ConfettiFrame = {
  /** Offset from the origin, in fractions of the box WIDTH. */
  dx: number;
  /** Offset from the origin, in fractions of the box WIDTH — y grows downward. */
  dy: number;
  /** In-plane rotation, radians. */
  rot: number;
  /** How wide the piece draws as it turns edge-on: `MIN_FLIP`..1 of its own width. */
  widthScale: number;
  alpha: number;
};

/**
 * One piece: the fifteen values the sixteen seeded draws produce, plus the six
 * derivations `fan()` does once so that `frame()` is arithmetic and the tests can
 * see what the piece was actually built with.
 */
export type ConfettiPiece = {
  angle: number;
  speed: number;
  /** Total in-plane turn over the flight, radians. Signed; the sign is its own draw. */
  spin: number;
  spinPhase: number;
  /** Edge-on flips per flight, radians — its OWN axis, unrelated to `spin`. */
  flipRate: number;
  flipPhase: number;
  width: number;
  height: number;
  colorIndex: number;
  /** Launch delay as a fraction of the flight. */
  delay: number;
  dragScale: number;
  /** Offset along the throw angle at which this piece leaves the source patch. */
  muzzle: number;
  swayAmp: number;
  swayRate: number;
  swayPhase: number;

  /** Launch velocity, resolved onto the axes. */
  vx0: number;
  vy0: number;
  /** This piece's drag coefficient. */
  k: number;
  /** This piece's terminal fall speed, `GRAVITY / k`. */
  vt: number;
  /** Where on the source patch it leaves from. */
  mx: number;
  my: number;
};

/**
 * The burst's own clock.
 *
 * not a token — see docs/motion.md. It is on none of the five rungs and must not be
 * put on one: the ladder times transitions, and this is not a transition. It is how
 * long a thrown piece is in the air — the denominator every constant below is
 * expressed against (`GRAVITY` is span per flight squared, `DRAG` is per flight) —
 * so changing it does not retime a motion, it rewrites the physics. It is the same
 * 2000 on Android and iOS, and that agreement is `docs/confetti-spec.md`'s to keep
 * rather than `MotionTokens.kt`'s: the three clients share a model, not a duration.
 */
export const FLIGHT_MS = 2000;

/** Where the burst is thrown from, as a fraction of the box: the scene's heart. */
export const ORIGIN_X = 0.5;
export const ORIGIN_Y = 0.28;

const PIECE_COUNT = 46;

/** Up and out: 200°..340°, measured with y growing downward. */
const FAN_START = (200 * Math.PI) / 180;
const FAN_SWEEP = (140 * Math.PI) / 180;

/**
 * Per flight. The time constant is `1/DRAG` = a sixth of the flight, so a third of
 * the throw is spent in the first 150 ms — the burst reads as a snap rather than a
 * bloom, and the fast pieces stop before they reach the sides.
 */
const DRAG = 6.0;
/** Span per flight squared. What the eye actually reads is `vt = GRAVITY / k`. */
const GRAVITY = 5.1;
/** Radians of lean, in phase with the sway position: paper banks into its drift. */
const ROCK = 0.5;
/** A piece exactly edge-on would vanish, which reads as a dropped frame. */
const MIN_FLIP = 0.25;
/** Opaque for the first 60 % of the flight, then a smoothstep to nothing. */
const FADE_START = 0.6;
/** One salvo, staggered: a single simultaneous launch reads as an expanding ring. */
const MAX_LAUNCH_DELAY = 0.16;

/** Launch speeds, not distances: reach is `speed / k`, so 1.10..1.95 → 0.167..0.361 span. */
const MIN_SPEED = 1.1;
const SPEED_RANGE = 0.85;
/** A total turn over the flight, not a rate. */
const MIN_SPIN = 1.5;
const SPIN_RANGE = 3.0;
/** 1.9..3.8 edge-on passes per flight, undamped and decoupled from `spin`. */
const MIN_FLIP_RATE = 6;
const FLIP_RATE_RANGE = 6;
const MIN_WIDTH = 5;
const WIDTH_RANGE = 4;
const MIN_HEIGHT = 8;
const HEIGHT_RANGE = 5;
/** The seven-colour palette plus the screen's own accent — the draw layer's array length. */
const COLOR_COUNT = 8;
const MIN_DRAG_SCALE = 0.9;
const DRAG_SCALE_RANGE = 0.2;
/** A thumb-sized source patch rather than a single pixel. */
const MAX_MUZZLE = 0.03;
/** ±7–15 px of drift on a 340 px box. */
const MIN_SWAY_AMP = 0.018;
const SWAY_AMP_RANGE = 0.022;
/** 0.7–1.2 Hz, per piece, so forty-six of them do not flutter in unison. */
const MIN_SWAY_RATE = 9;
const SWAY_RATE_RANGE = 6;

const TWO_PI = Math.PI * 2;

/**
 * Every constant the model has, in one place.
 *
 * The tests assert against these rather than against transcribed numbers: a bound
 * derived from the parameter ranges stays true when a range moves, and a bound typed
 * out from a spec silently stops being the thing it claims to check.
 */
export const METRICS = {
  pieceCount: PIECE_COUNT,
  flightMs: FLIGHT_MS,
  originX: ORIGIN_X,
  originY: ORIGIN_Y,
  fanStart: FAN_START,
  fanSweep: FAN_SWEEP,
  drag: DRAG,
  gravity: GRAVITY,
  rock: ROCK,
  minFlip: MIN_FLIP,
  fadeStart: FADE_START,
  maxLaunchDelay: MAX_LAUNCH_DELAY,
  minSpeed: MIN_SPEED,
  speedRange: SPEED_RANGE,
  minSpin: MIN_SPIN,
  spinRange: SPIN_RANGE,
  minFlipRate: MIN_FLIP_RATE,
  flipRateRange: FLIP_RATE_RANGE,
  minWidth: MIN_WIDTH,
  widthRange: WIDTH_RANGE,
  minHeight: MIN_HEIGHT,
  heightRange: HEIGHT_RANGE,
  colorCount: COLOR_COUNT,
  minDragScale: MIN_DRAG_SCALE,
  dragScaleRange: DRAG_SCALE_RANGE,
  maxMuzzle: MAX_MUZZLE,
  minSwayAmp: MIN_SWAY_AMP,
  swayAmpRange: SWAY_AMP_RANGE,
  minSwayRate: MIN_SWAY_RATE,
  swayRateRange: SWAY_RATE_RANGE,
} as const;

/**
 * The fan, rolled from a fixed seed: the burst is the same every time, which is what
 * makes it read as a designed celebration rather than a random one.
 *
 * The sixteen draws happen in exactly this order, and the order is the parity
 * contract — Android's `Random(PieceCount * 31L)` and iOS's LCG are different streams
 * and will never produce the same values, so what the three clients can actually
 * agree on is which distribution each draw comes from and when it is taken:
 *
 *   1 angle jitter U[0,1), stratified   9  height U[8,13]
 *   2 speed U[1.10,1.95]               10  colorIndex int U[0,8)
 *   3 spin sign coin                   11  delay U[0,0.16)
 *   4 spin magnitude U[1.5,4.5]        12  dragScale U[0.90,1.10]
 *   5 spinPhase U[0,2π)                13  muzzle U[0,0.03]
 *   6 flipRate U[6,12]                 14  swayAmp U[0.018,0.04]
 *   7 flipPhase U[0,2π)                15  swayRate U[9,15]
 *   8 width U[5,9]                     16  swayPhase U[0,2π)
 *
 * The six derivations at the bottom are cached here rather than recomputed per frame
 * for the reason the split exists at all: they are what a test has to be able to read
 * to check that a piece left from its own muzzle and settled to its own terminal
 * speed.
 */
export function fan(): ConfettiPiece[] {
  const random = seeded(0x7da9102b);
  return Array.from({ length: PIECE_COUNT }, (_, index) => {
    // Stratified rather than uniform: one jittered piece per slot of the sweep, so
    // forty-six independent draws cannot leave a visible gap in the fan.
    const angle = FAN_START + FAN_SWEEP * ((index + random()) / PIECE_COUNT);
    const speed = MIN_SPEED + random() * SPEED_RANGE;
    const spin = (random() < 0.5 ? 1 : -1) * (MIN_SPIN + random() * SPIN_RANGE);
    const spinPhase = random() * TWO_PI;
    const flipRate = MIN_FLIP_RATE + random() * FLIP_RATE_RANGE;
    const flipPhase = random() * TWO_PI;
    const width = MIN_WIDTH + random() * WIDTH_RANGE;
    const height = MIN_HEIGHT + random() * HEIGHT_RANGE;
    const colorIndex = Math.floor(random() * COLOR_COUNT);
    const delay = random() * MAX_LAUNCH_DELAY;
    const dragScale = MIN_DRAG_SCALE + random() * DRAG_SCALE_RANGE;
    const muzzle = random() * MAX_MUZZLE;
    const swayAmp = MIN_SWAY_AMP + random() * SWAY_AMP_RANGE;
    const swayRate = MIN_SWAY_RATE + random() * SWAY_RATE_RANGE;
    const swayPhase = random() * TWO_PI;

    const cos = Math.cos(angle);
    const sin = Math.sin(angle);
    const k = DRAG * dragScale;
    return {
      angle,
      speed,
      spin,
      spinPhase,
      flipRate,
      flipPhase,
      width,
      height,
      colorIndex,
      delay,
      dragScale,
      muzzle,
      swayAmp,
      swayRate,
      swayPhase,
      vx0: cos * speed,
      vy0: sin * speed,
      k,
      vt: GRAVITY / k,
      mx: muzzle * cos,
      my: muzzle * sin,
    };
  });
}

/**
 * Where one piece is at flight time `t` (0..1), or `null` if it is not in the air.
 *
 * Positions are fractions of the box WIDTH, from the origin — not of the longest
 * side, which on a narrow screen is the height and throws every piece clean off the
 * sides before it can be seen.
 *
 * The `tau >= 1` exit lives here rather than only in the draw loop so that "the
 * flight is over" is one fact with one owner; a draw layer keeps its own `t >= 1`
 * exit because it also has a canvas to stop clearing.
 *
 * No value below needs clamping, and that is a property of the constants rather than
 * luck: `k >= 5.4`, `1 - delay >= 0.84`, and `u` is only ever evaluated on
 * `[FADE_START, 1)`.
 */
export function frame(piece: ConfettiPiece, t: number): ConfettiFrame | null {
  const tau = (t - piece.delay) / (1 - piece.delay);
  if (tau <= 0 || tau >= 1) return null;

  // How much of the launch velocity is left, how far a unit launch speed has got,
  // and how much of the throw has been spent. The last doubles as the flutter
  // envelope: sway and rock are nothing at the muzzle and full once the throw is.
  const remaining = Math.exp(-piece.k * tau);
  const travel = (1 - remaining) / piece.k;
  const spent = 1 - remaining;

  const theta = piece.swayRate * tau + piece.swayPhase;
  const sway = Math.sin(theta);

  return {
    dx: piece.mx + piece.vx0 * travel + piece.swayAmp * sway * spent,
    // The fall is `vt * (tau - travel)`: zero at launch, and asymptotically `vt` per
    // unit flight once the drag transient is gone. As `DRAG → 0` it collapses back to
    // `GRAVITY * tau² / 2`, so this is a strict superset of a plain parabola.
    dy: piece.my + piece.vy0 * travel + piece.vt * (tau - travel),
    rot: piece.spinPhase + spent * (piece.spin + ROCK * sway),
    // Its own axis at its own rate. Locking the flip to `rot` — which is what this
    // used to do — makes every piece a propeller and a coin at once, and that lock is
    // what read as a mechanical wobble rather than as paper.
    widthScale:
      MIN_FLIP +
      (1 - MIN_FLIP) * Math.abs(Math.cos(piece.flipPhase + piece.flipRate * tau)),
    alpha: alpha(tau),
  };
}

/**
 * The fade, as a function of a piece's own `tau`.
 *
 * Separate from `frame()` because the one value worth pinning hardest — that the last
 * frame is fully transparent, with zero slope into it — is at `tau = 1`, where there
 * is deliberately no frame to read it off. A linear tail ends on a visible edge; the
 * smoothstep is what makes the burst stop without a moment you could point at.
 *
 * Written out rather than via `Math.pow`: this is three multiplies, and the three
 * clients have to produce the same number to 1e-9.
 */
export function alpha(tau: number): number {
  if (tau < FADE_START) return 1;
  const u = (tau - FADE_START) / (1 - FADE_START);
  return 1 - u * u * (3 - 2 * u);
}

/**
 * What a piece actually draws at: its own fade, taken away by the cancel envelope.
 *
 * Two independent terms, multiplied, and the independence is the whole design.
 * [alpha] is a function of the piece's own flight clock and knows nothing about being
 * interrupted; the envelope is a function of a clock that does not exist until
 * somebody undoes a completion (or adds a task, or a collaborator does) and the burst
 * has to leave before it was finished. An uncancelled burst multiplies by exactly 1
 * for its whole flight, so this reduces to [alpha] and the spec-pinned numbers above —
 * `FLIGHT_MS`, `FADE_START`, the scene lead the caller holds — are untouched. The
 * envelope is a new term, never a retune of the fade.
 *
 * It multiplies rather than replaces for the reason the fade is wanted at all: the
 * pieces keep FLYING while the envelope runs — same positions, same spin, same flip —
 * because freezing the flight and dissolving a still frame is a second, quieter
 * version of the complaint this fixes. Only the paint leaves.
 *
 * No clamp, on the same terms as the model above: [alpha] is a smoothstep on `[0, 1]`
 * by construction, and the envelope is `1 - EASE.exit(progress)` on a cubic Bezier
 * with both control points inside the unit square, which cannot overshoot the way a
 * spring could. A caller who ever reaches for a spring here owes this line a clamp.
 *
 * `docs/confetti-spec.md`'s Interruption section is normative; Android's
 * `envelopedAlpha` in `TdayConfettiKinematics.kt` and iOS's twin in `TdayConfetti.swift`
 * are the same one line for the same reason.
 */
export function envelopedAlpha(pieceAlpha: number, envelope: number): number {
  return pieceAlpha * envelope;
}

/** Mulberry32: three lines, and the same fan on every machine. */
function seeded(seed: number) {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
