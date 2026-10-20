package com.ohmz.tday.compose.core.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The confetti burst's physics, with no Compose in it at all.
 *
 * `docs/confetti-spec.md` is the normative file; this is its Android third. The
 * model is one idea — **linear (Stokes) drag** — which is what gives every
 * attribute a closed form and lets the whole burst be a pure function of the
 * flight clock. A piece is thrown, the throw is spent against drag so its outward
 * reach is finite, and what is left is a fall that settles to that piece's own
 * terminal speed instead of accelerating forever.
 *
 * It lives apart from [TdayConfetti] because the numbers are the part that can be
 * wrong, and a `Canvas` cannot be asked what it drew. Not one import here is a
 * Compose import — that is the whole reason the file exists, because it is what
 * lets `TdayConfettiKinematicsTest` run as plain JUnit with no Robolectric and no
 * device, and ask this module the six invariants the spec holds all three clients
 * to. Web and iOS have twins for the same reason.
 */

/** What the draw layer needs per piece per frame, and nothing else. */
data class ConfettiFrame(
    /** Offset from the origin, in fractions of the box WIDTH. */
    val dx: Float,
    /** Offset from the origin, in fractions of the box WIDTH — y grows downward. */
    val dy: Float,
    /** In-plane rotation, radians. Degrees exist only at the draw site. */
    val rot: Float,
    /** How wide the piece draws as it turns edge-on: [MinFlip]..1 of its own width. */
    val widthScale: Float,
    val alpha: Float,
)

/**
 * One piece: the values the sixteen seeded draws produce, and the six derivations
 * cached from them.
 *
 * The derivations are computed here rather than at the call site so that a piece
 * cannot exist in a state where its cache disagrees with its draws — a test that
 * hand-builds a piece gets the same arithmetic the fan does, which is the only way
 * the invariants below are about [frame] rather than about how carefully the test
 * filled in a constructor.
 *
 * A `data class` because `confettiFan` rolling the same burst twice is one of those
 * invariants, and the sixteen draws are exactly the identity worth comparing: the
 * six below are a pure function of them, so equality over the constructor is
 * equality over the piece.
 */
data class ConfettiPiece(
    val angle: Float,
    val speed: Float,
    /** Total in-plane turn over the flight, radians. Signed; the sign is its own draw. */
    val spin: Float,
    val spinPhase: Float,
    /** Edge-on flips per flight, radians — its OWN axis, unrelated to [spin]. */
    val flipRate: Float,
    val flipPhase: Float,
    val width: Float,
    val height: Float,
    val colorIndex: Int,
    /** Launch delay as a fraction of the flight. */
    val delay: Float,
    /**
     * This piece's share of the drag coefficient: heavier and lighter paper.
     *
     * Named a factor and not a scale deliberately. `motion-budget.json`'s
     * `android.pressScale` counter reads any `0.9…f` on a line that also says
     * "scale" as a press scale, and the ratchet has no headroom — so calling an
     * aerodynamic constant a scale would spend two counts of a budget about how
     * hard a control shrinks under a finger. The spec spells it `dragScale`; the
     * number, the draw order and the distribution are what parity is in.
     */
    val dragFactor: Float,
    /** How far along its own throw angle this piece leaves the source patch. */
    val muzzle: Float,
    val swayAmp: Float,
    val swayRate: Float,
    val swayPhase: Float,
) {
    private val cosAngle = cos(angle)
    private val sinAngle = sin(angle)

    /** Launch velocity, resolved onto the axes. */
    val vx0: Float = cosAngle * speed
    val vy0: Float = sinAngle * speed

    /** This piece's drag coefficient, per flight. */
    val k: Float = Drag * dragFactor

    /** This piece's terminal fall speed, [Gravity] over [k]. */
    val vt: Float = Gravity / k

    /** Where on the source patch it leaves from, offset along its own throw angle. */
    val mx: Float = muzzle * cosAngle
    val my: Float = muzzle * sinAngle
}

/**
 * How long a piece is in the air.
 *
 * not a token — see docs/motion.md. It is on none of the five rungs and must not be
 * put on one: the ladder times transitions, and a flight is not a transition. It is
 * the burst's own clock, and the denominator every constant below is expressed
 * against — [Drag] is per flight, [Gravity] is span per flight squared — so moving
 * it does not retime a motion, it rewrites the physics. The same 2000 runs on web
 * and iOS, and that agreement is `docs/confetti-spec.md`'s to keep rather than
 * `MotionTokens.kt`'s: the three clients share a model, not a duration.
 */
internal const val FlightMillis = 2000

internal const val PieceCount = 46

/** Where the burst is thrown from, as a fraction of the box: the scene's heart. */
internal const val OriginX = 0.5f
internal const val OriginY = 0.28f

internal const val TwoPi = (PI * 2).toFloat()

/** Up and out: 200°..340°, measured with y growing downward. */
internal const val FanStartRadians = (PI * 200.0 / 180.0).toFloat()
internal const val FanSweepRadians = (PI * 140.0 / 180.0).toFloat()

/**
 * Per flight. The time constant is `1/Drag`, a sixth of the flight, so a third of
 * the throw is spent in the first 150 ms: the burst reads as a snap rather than a
 * bloom, and the fast pieces stop before they reach the sides instead of leaving
 * them.
 */
internal const val Drag = 6.0f

/**
 * Span per flight squared.
 *
 * Not comparable with the 0.95 this replaces, which was `g/2` in a model with no
 * drag at all. What the eye actually reads is `vt = Gravity / k`, a piece's own
 * terminal speed — 0.77 to 0.94 span per flight across the fan.
 */
internal const val Gravity = 5.1f

/** Radians of lean, in phase with the sway position: paper banks into its drift. */
internal const val Rock = 0.5f

/** A piece exactly edge-on would vanish, which reads as a dropped frame. */
internal const val MinFlip = 0.25f

/** Opaque for the first 60 % of the flight, then a smoothstep to nothing. */
internal const val FadeStart = 0.60f

/** One salvo, staggered: a simultaneous launch reads as an expanding ring. */
internal const val MaxLaunchDelay = 0.16f

/**
 * Launch SPEEDS, not distances.
 *
 * The old 0.30..0.78 were the whole outward travel; under drag a piece covers
 * `speed / k`, so these reach 0.167..0.361 of the span. The floor is what keeps the
 * slow half from clumping on top of the illustration, and the ceiling is what caps
 * the apex at 0.195 span so the web canvas has something to bleed into.
 */
internal const val MinSpeed = 1.10f
internal const val SpeedRange = 0.85f

/**
 * A TOTAL in-plane turn over the flight, radians — not a rate, and not a maximum.
 *
 * This is the rename the row exists for. The old pair was `MinSpin = 3.5f` and
 * `MaxSpin = 9f`, used as `MinSpin + random.nextFloat() * MaxSpin`: `MaxSpin` was
 * the width of the range and never a maximum of anything, so the real spread was
 * 3.5..12.5 and the code read as an off-by-a-variable bug to everyone who met it.
 *
 * **There is no iOS/Android spin parity bug.** Every proposal that claimed one was
 * reading `MaxSpin` as a ceiling and comparing it against iOS's and web's honest
 * 3.5..12.5; all three clients spin identically today. Nothing here is a fix for
 * that, and nobody should "restore" it.
 */
internal const val MinSpinRadians = 1.5f
internal const val SpinRadiansRange = 3.0f

/**
 * 1.9..3.8 edge-on passes per flight, undamped and decoupled from [ConfettiPiece.spin].
 *
 * Locking the flip to the rotation — which is what this used to do — makes every
 * piece a propeller and a coin at once, and that lock is what read as a mechanical
 * wobble rather than as paper.
 */
internal const val MinFlipRate = 6f
internal const val FlipRateRange = 6f

internal const val MinPieceWidthDp = 5f
internal const val PieceWidthRangeDp = 4f
internal const val MinPieceHeightDp = 8f
internal const val PieceHeightRangeDp = 5f

/**
 * The seven-colour palette plus the screen's own accent — the draw layer's array
 * length, held here because the draw layer is the half this module cannot see.
 */
internal const val ColorCount = 8

/** Heavier and lighter paper: a tenth either side of [Drag]. */
internal const val MinDragFactor = 0.90f
internal const val DragFactorRange = 0.20f

/** A thumb-sized source patch rather than a single pixel. */
internal const val MaxMuzzle = 0.03f

/** ±7–15 px of drift on a 340 dp box. */
internal const val MinSwayAmp = 0.018f
internal const val SwayAmpRange = 0.022f

/** 0.7–1.2 Hz, per piece, so forty-six of them do not flutter in unison. */
internal const val MinSwayRate = 9f
internal const val SwayRateRange = 6f

/**
 * The fan, rolled from the seed the caller passes: the burst is the same every
 * time, which is what makes it read as a designed celebration rather than a random
 * one, and what lets a screenshot test see the same frame twice.
 *
 * The sixteen draws happen in exactly this order, and the order is the parity
 * contract. Android's `Random(PieceCount * 31L)`, iOS's LCG and web's mulberry32
 * are three different streams and will never produce the same values, so what the
 * clients can actually agree on is which distribution each draw comes from and when
 * it is taken:
 *
 *   1 angle jitter U[0,1), stratified   9  height U[8,13]
 *   2 speed U[1.10,1.95]               10  colorIndex int U[0,8)
 *   3 spin sign coin                   11  delay U[0,0.16)
 *   4 spin magnitude U[1.5,4.5]        12  dragFactor U[0.90,1.10]
 *   5 spinPhase U[0,2π)                13  muzzle U[0,0.03]
 *   6 flipRate U[6,12]                 14  swayAmp U[0.018,0.04]
 *   7 flipPhase U[0,2π)                15  swayRate U[9,15]
 *   8 width U[5,9]                     16  swayPhase U[0,2π)
 *
 * The sign and the magnitude of the spin are two draws and are written as two
 * statements: folding them into one expression leaves the order of the stream to
 * Kotlin's evaluation rules, and the order of the stream is the thing being
 * promised.
 */
internal fun confettiFan(random: Random): List<ConfettiPiece> = List(PieceCount) { index ->
    // Stratified rather than uniform: one jittered piece per slot of the sweep, so
    // forty-six independent draws cannot leave a visible gap in the fan.
    val angle = FanStartRadians + FanSweepRadians * ((index + random.nextFloat()) / PieceCount)
    val speed = MinSpeed + random.nextFloat() * SpeedRange
    val spinSign = if (random.nextBoolean()) 1f else -1f
    val spin = spinSign * (MinSpinRadians + random.nextFloat() * SpinRadiansRange)
    ConfettiPiece(
        angle = angle,
        speed = speed,
        spin = spin,
        spinPhase = random.nextFloat() * TwoPi,
        flipRate = MinFlipRate + random.nextFloat() * FlipRateRange,
        flipPhase = random.nextFloat() * TwoPi,
        width = MinPieceWidthDp + random.nextFloat() * PieceWidthRangeDp,
        height = MinPieceHeightDp + random.nextFloat() * PieceHeightRangeDp,
        colorIndex = random.nextInt(ColorCount),
        delay = random.nextFloat() * MaxLaunchDelay,
        dragFactor = MinDragFactor + random.nextFloat() * DragFactorRange,
        muzzle = random.nextFloat() * MaxMuzzle,
        swayAmp = MinSwayAmp + random.nextFloat() * SwayAmpRange,
        swayRate = MinSwayRate + random.nextFloat() * SwayRateRange,
        swayPhase = random.nextFloat() * TwoPi,
    )
}

/**
 * Where one piece is at flight time [t] (0..1), or `null` if it is not in the air.
 *
 * Positions are fractions of the box WIDTH, from the origin — not of the longest
 * side, which on a phone is the height and throws every piece clean off the sides
 * before it can be seen.
 *
 * The `tau >= 1` exit lives here rather than only in the draw loop, so that "the
 * flight is over" is one fact with one owner; the draw layer keeps its own `t >= 1`
 * exit because it has a whole canvas to stop walking, not one piece to skip.
 *
 * Nothing below needs clamping, and that is a property of the constants rather than
 * luck: `k >= 5.4`, `1 - delay >= 0.84`, and `u` inside [alpha] is only ever
 * evaluated on `[FadeStart, 1)`.
 */
internal fun frame(piece: ConfettiPiece, t: Float): ConfettiFrame? {
    val tau = (t - piece.delay) / (1f - piece.delay)
    if (tau <= 0f || tau >= 1f) return null

    // How much of the launch velocity is left, how far a unit launch speed has got,
    // and how much of the throw has been spent. The last doubles as the flutter
    // envelope: sway and rock are nothing at the muzzle and full once the throw is.
    val remaining = exp(-piece.k * tau)
    val travel = (1f - remaining) / piece.k
    val spent = 1f - remaining

    val sway = sin(piece.swayRate * tau + piece.swayPhase)

    return ConfettiFrame(
        dx = piece.mx + piece.vx0 * travel + piece.swayAmp * sway * spent,
        // The fall is `vt * (tau - travel)`: zero at launch, and asymptotically `vt`
        // per unit flight once the drag transient is gone. As Drag goes to zero it
        // collapses back to `Gravity * tau² / 2`, so this is a strict superset of
        // the plain parabola it replaces rather than a different effect.
        dy = piece.my + piece.vy0 * travel + piece.vt * (tau - travel),
        rot = piece.spinPhase + spent * (piece.spin + Rock * sway),
        widthScale = MinFlip +
            (1f - MinFlip) * abs(cos(piece.flipPhase + piece.flipRate * tau)),
        alpha = alpha(tau),
    )
}

/**
 * The fade, as a function of a piece's own `tau`.
 *
 * Separate from [frame] because the one value worth pinning hardest — that the last
 * frame is fully transparent, with zero slope into it — is at `tau = 1`, where there
 * is deliberately no frame to read it off. A linear tail ends on a visible edge; the
 * smoothstep is what lets the burst stop without a moment anyone could point at.
 *
 * Written out rather than through `pow`: this is three multiplies, and the three
 * clients have to agree on the number.
 */
internal fun alpha(tau: Float): Float {
    if (tau < FadeStart) return 1f
    val u = (tau - FadeStart) / (1f - FadeStart)
    return 1f - u * u * (3f - 2f * u)
}

/**
 * What a piece actually draws at: its own fade, taken away by the cancel
 * envelope.
 *
 * Two independent terms, multiplied, and the independence is the whole design.
 * [pieceAlpha] is a function of the piece's own flight clock and knows nothing
 * about being interrupted; [envelope] is a function of a clock that does not
 * exist until somebody undoes a completion (or adds a task, or a collaborator
 * does) and the burst has to leave before it was finished. A burst that is not
 * cancelled multiplies by exactly 1 for its whole flight and this reduces to
 * [alpha], which is why the envelope is a new term rather than a retune: every
 * spec-pinned number above -- [FlightMillis], [FadeStart], the scene lead the
 * caller holds -- is untouched, and the three clients' kinematics tests still
 * assert the same curve.
 *
 * It multiplies rather than replaces for the reason the fade is wanted at all.
 * The pieces keep FLYING while the envelope runs: same positions, same spin,
 * same flip, because freezing the flight and dissolving a still frame is a
 * second, quieter version of the complaint this fixes. Only the paint leaves.
 *
 * No clamp, on the same terms as [alpha] above: [pieceAlpha] is a smoothstep on
 * `[0, 1]` by construction and the envelope is driven by a token tween on the
 * `Exit` curve, which is a cubic Bezier with both control points inside the unit
 * square and therefore cannot overshoot the way a spring could. A caller who
 * ever reaches for a spring here owes this line a clamp.
 */
internal fun envelopedAlpha(pieceAlpha: Float, envelope: Float): Float = pieceAlpha * envelope
