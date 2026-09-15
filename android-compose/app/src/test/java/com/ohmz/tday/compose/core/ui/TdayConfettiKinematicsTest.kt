package com.ohmz.tday.compose.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The six invariants of `docs/confetti-spec.md`, on the Android third of the model.
 *
 * Plain JUnit and no Robolectric, which is the whole reason `TdayConfettiKinematics`
 * has no Compose import in it: the physics can be asked what they computed, while a
 * `Canvas` can only be asked to draw.
 *
 * Why invariants and not golden values: this model has never been seen moving on a
 * device, and its numbers were derived on paper. What is worth pinning is the shape —
 * travel that decelerates to a finite reach, a fall that approaches a terminal speed
 * from either side without crossing it, a fade that arrives at exactly zero with no
 * slope into it, a flip that is not the rotation — because that is what a
 * plausible-looking edit would quietly break.
 *
 * Everything here is relative to one flight: `t` runs 0..1 over [FlightMillis], and
 * every distance is a fraction of the burst box's WIDTH. No assertion below is in
 * pixels, milliseconds or degrees, and none should be.
 */
class TdayConfettiKinematicsTest {

    /**
     * Float tolerances, per the spec: sweep at 0.05 rather than the 0.01 the two
     * Double clients use, and compare at 1e-4.
     *
     * Monotonicity and concavity below use a strict comparison with NO epsilon. At
     * this step the tail differences are around 1e-4 themselves, so subtracting an
     * absolute 1e-4 from either side of those checks would not loosen them, it would
     * delete them.
     */
    private val step = 0.05f
    private val steps = 20
    private val tol = 1e-4f

    private val fan = confettiFan(Random(PieceCount * 31L))

    /** `i / count` rather than a running sum: an accumulated step drifts off the grid. */
    private fun grid(count: Int): List<Float> = List(count + 1) { it.toFloat() / count }

    private fun forwardDifferences(values: List<Float>): List<Float> =
        values.drop(1).mapIndexed { i, value -> value - values[i] }

    /** [frame] with the "it is in the air" precondition asserted rather than assumed. */
    private fun frameAt(piece: ConfettiPiece, t: Float): ConfettiFrame =
        frame(piece, t) ?: throw AssertionError("expected a frame at t=$t")

    /**
     * A piece with one attribute switched on and the rest switched off.
     *
     * Unlike the web twin this does not re-derive `vx0`/`k`/`vt`/`mx`/`my`: on this
     * client they are computed by [ConfettiPiece] itself, so there is no second
     * expression that could disagree with the fan's — which is the point of putting
     * them there.
     */
    private fun buildPiece(
        angle: Float,
        speed: Float,
        spin: Float = 0f,
        spinPhase: Float = 0f,
        flipRate: Float = 0f,
        flipPhase: Float = 0f,
        delay: Float = 0f,
        dragFactor: Float = 1f,
        muzzle: Float = 0f,
        swayAmp: Float = 0f,
        swayRate: Float = 0f,
        swayPhase: Float = 0f,
    ): ConfettiPiece = ConfettiPiece(
        angle = angle,
        speed = speed,
        spin = spin,
        spinPhase = spinPhase,
        flipRate = flipRate,
        flipPhase = flipPhase,
        width = 7f,
        height = 10f,
        colorIndex = 0,
        delay = delay,
        dragFactor = dragFactor,
        muzzle = muzzle,
        swayAmp = swayAmp,
        swayRate = swayRate,
        swayPhase = swayPhase,
    )

    // I1 — nothing before the throw, and it leaves from the muzzle patch.

    @Test
    fun `nothing is drawn at, before, or halfway to a piece's launch`() {
        for (piece in fan) {
            assertNull(frame(piece, 0f))
            assertNull(frame(piece, piece.delay / 2f))
            assertNull(frame(piece, piece.delay))
        }
    }

    @Test
    fun `nothing is drawn on the last frame of the flight`() {
        // The `tau >= 1` exit is inside `frame()` so that a draw layer cannot forget
        // it and leave forty-six pieces parked on the screen.
        for (piece in fan) assertNull(frame(piece, 1f))
    }

    @Test
    fun `the burst leaves a patch rather than a single pixel`() {
        // Checked against the range the muzzle is drawn from, not against each
        // piece's own cached `mx, my`. A fan that stopped offsetting anything would
        // satisfy the frame check below with zero on both sides — and a single
        // launch point is precisely the defect this model was written to fix.
        val launchPoints = fan.map { "${it.mx},${it.my}" }.toSet()
        assertEquals(fan.size, launchPoints.size)
        for (piece in fan) {
            assertTrue(hypot(piece.mx, piece.my) <= MaxMuzzle + tol)
        }
        // Spread across the patch rather than clustered at its centre, argued from
        // the range and not from this seed: forty-six draws on [0, MaxMuzzle] all
        // landing in the inner half is a 2^-46 event, so this survives a re-roll.
        val furthest = fan.maxOf { hypot(it.mx, it.my) }
        assertTrue(furthest > MaxMuzzle / 2f)
    }

    @Test
    fun `a piece leaves from its own point on the patch`() {
        for (piece in fan) {
            val state = frameAt(piece, piece.delay + 1e-4f * (1f - piece.delay))
            // Still inside the thumb-sized source patch: 0.03 span plus the ~2e-4 of
            // travel a ten-thousandth of the flight buys at these launch speeds.
            assertTrue(hypot(state.dx, state.dy) <= 0.031f)
            assertTrue(hypot(state.dx - piece.mx, state.dy - piece.my) <= 1e-3f)
        }
    }

    // I2 — drag: outward travel concave, reach finite.

    /** Straight out along +x at the top speed, so `dx` is the drag term alone. */
    private val thrownFlat = buildPiece(angle = 0f, speed = MinSpeed + SpeedRange)

    private val flatDx: List<Float> =
        grid(steps).filter { it > 0f && it < 1f }.map { frameAt(thrownFlat, it).dx }

    @Test
    fun `a piece travels outward without ever turning back`() {
        for (difference in forwardDifferences(flatDx)) assertTrue(difference > 0f)
    }

    @Test
    fun `a piece decelerates the whole way out`() {
        for (difference in forwardDifferences(forwardDifferences(flatDx))) {
            assertTrue(difference < 0f)
        }
    }

    @Test
    fun `one time constant of the throw is spent at a sixth of the flight`() {
        // `speed * (1 - e^-1) / k`. Pinned against the closed form rather than
        // against the spec's 0.2054392, which is that form rounded to seven places;
        // the rounded figure is checked beside it so a transcription error in either
        // direction still fails.
        val expected = thrownFlat.speed * (1f - exp(-1f)) / thrownFlat.k
        assertEquals(expected, frameAt(thrownFlat, 1f / 6f).dx, tol)
        assertEquals(0.2054392f, frameAt(thrownFlat, 1f / 6f).dx, tol)
    }

    @Test
    fun `a piece stops short of a bound instead of running off the box`() {
        // The whole point of the drag term: reach is `speed / k` = 0.325 and the
        // piece never gets there. Undragged, this same launch would be at 1.95 span.
        val dx = frameAt(thrownFlat, 0.999f).dx
        assertTrue(dx >= 0.32175f)
        assertTrue(dx <= 0.325f)
    }

    // I3 — terminal velocity approached from both sides, never crossed.

    private val rising = buildPiece(angle = (-PI / 2.0).toFloat(), speed = MinSpeed + SpeedRange)
    private val falling = buildPiece(angle = (PI / 2.0).toFloat(), speed = MinSpeed + SpeedRange)

    /** Average vertical speed over each step — the thing `dy` actually does. */
    private fun verticalSpeeds(piece: ConfettiPiece): List<Float> =
        forwardDifferences(grid(steps).filter { it > 0f && it < 1f }.map { frameAt(piece, it).dy })
            .map { it / step }

    @Test
    fun `a piece has its own terminal speed`() {
        assertEquals(0.85f, rising.vt, tol)
    }

    @Test
    fun `a piece thrown upward falls back and settles without overshooting`() {
        val speeds = verticalSpeeds(rising)
        assertTrue(speeds.first() < 0f)
        for (difference in forwardDifferences(speeds)) assertTrue(difference > 0f)
        for (v in speeds) assertTrue(v <= rising.vt + tol)
        // Analytically 0.9871 of terminal over the last step of this sweep. The
        // Double clients reach 0.9912 because they sample at 0.01 and this one stops
        // averaging at 0.95 — the fraction is a property of the step, not of the
        // model, which is why it is not the same number on all three.
        assertTrue(speeds.last() >= 0.985f * rising.vt)
    }

    @Test
    fun `a piece thrown downward slows to the same speed rather than running away`() {
        // This is the half a parabola cannot do: under the old model this piece
        // accelerated past everything else and off the bottom of the box.
        val speeds = verticalSpeeds(falling)
        assertTrue(speeds.first() > falling.vt)
        for (difference in forwardDifferences(speeds)) assertTrue(difference < 0f)
        for (v in speeds) assertTrue(v >= falling.vt - tol)
        assertTrue(speeds.last() <= 1.015f * falling.vt)
    }

    // I4 — fade schedule.

    @Test
    fun `a piece is fully opaque right up to the fade and no longer`() {
        // Asserted at FadeStart ± 1e-3 and never at FadeStart itself: a piece's own
        // `(t - delay) / (1 - delay)` rounds either side of 0.60, and in Float it
        // rounds further. The claim is about the schedule, not about a rounding mode.
        assertTrue(alpha(FadeStart - 1e-3f) >= 1f - 1e-6f)
        assertTrue(alpha(FadeStart + 1e-3f) < 1f)
    }

    @Test
    fun `a piece is exactly half gone at four fifths of the flight`() {
        assertEquals(0.5f, alpha(0.8f), tol)
    }

    @Test
    fun `the fade is the smoothstep and not the straight line it is mistakable for`() {
        // The half-way pin above cannot tell them apart: at tau 0.80 `u` is 0.5,
        // where smoothstep and a plain `1 - u` are both 0.5. These two are where the
        // curves part — the spec's own verified numbers, which a linear tail answers
        // 0.75 and 0.25.
        assertEquals(0.844f, alpha(0.7f), 5e-4f)
        assertEquals(0.156f, alpha(0.9f), 5e-4f)
    }

    @Test
    fun `the fade arrives at zero with no slope into it`() {
        // The whole argument for the smoothstep: a linear tail is still shedding 2.5
        // alpha per flight as it hits zero, and that last step is a visible edge the
        // burst ends on. A hundredth of a flight out the smoothstep has 3d² left,
        // with d = 0.025 of the fade window.
        assertTrue(alpha(1f - 0.01f) <= 3e-3f)
        // And it has not simply quit early: at the half-way point the same measure
        // over the same step is forty times larger.
        assertTrue(alpha(0.8f - 0.01f) - alpha(0.8f + 0.01f) > 0.07f)
    }

    @Test
    fun `the fade reaches zero exactly, which is why it is a separate function`() {
        // There is no frame at tau = 1 to read this off, and a fade that stops at
        // 0.02 is a burst that ends on an edge the eye can find.
        assertEquals(0f, alpha(1f), 0f)
    }

    @Test
    fun `the fade never brightens`() {
        for (difference in forwardDifferences(grid(200).map { alpha(it) })) {
            assertTrue(difference <= 0f)
        }
    }

    // The interruption envelope. A burst that is never cancelled multiplies by 1
    // for its whole flight, so the three assertions worth making are that an
    // uncancelled burst is byte-for-byte the flight above, that a finished cancel
    // leaves nothing painted, and that the way between the two only ever goes one
    // way — the envelope is paint leaving, and paint that brightened on its way
    // out would read as the burst flinching rather than bowing.

    @Test
    fun `an uncancelled burst draws exactly the fade it always did`() {
        for (t in grid(50)) {
            assertEquals(alpha(t), envelopedAlpha(alpha(t), 1f), 0f)
        }
    }

    @Test
    fun `a spent envelope paints nothing, whatever the piece's own fade says`() {
        // Including at the start of the flight, where `alpha` is a flat 1: this is
        // what lets the view leave composition when the envelope lands, instead of
        // being torn out from over pieces that are still fully opaque.
        assertEquals(0f, envelopedAlpha(alpha(0.1f), 0f), 0f)
        assertEquals(0f, envelopedAlpha(1f, 0f), 0f)
    }

    @Test
    fun `the envelope only ever takes paint away`() {
        // Monotonic in the envelope at a fixed point in the flight, and never
        // brighter than the flight's own fade — the envelope is a second term over
        // the first, not a replacement for it.
        val pieceAlpha = alpha(0.7f)
        val painted = grid(50).map { envelope -> envelopedAlpha(pieceAlpha, envelope) }
        for (difference in forwardDifferences(painted)) {
            assertTrue(difference >= 0f)
        }
        for (value in painted) {
            assertTrue(value <= pieceAlpha)
        }
    }

    // I5 — stays in the box, and the fan is choreography.

    @Test
    fun `every piece stays inside the box for the whole flight`() {
        // The bounds come from the parameter ranges and never from this seed. That
        // distinction is the trap the spec records: the proposal this model came
        // from quoted a dy ceiling that was simply wrong for its own wider ranges,
        // and a bound read off one roll of the fan would have agreed with it.
        //
        // |dx| <= muzzle + speed/k + swayAmp, each at its worst, all with |cos| at
        //   its largest over the 200°..340° sweep (0.9397, at either end):
        //   0.03*0.9397 + 1.95*0.9397/5.4 + 0.04 = 0.4075, and the true worst is
        //   0.4058 because the sway is not at its peak when the travel is.
        // dy is lowest at the apex of the hardest straight-up throw (270°, speed
        //   1.95, dragFactor 0.90): -0.1952 at tau 0.207.
        // dy is highest at the end of the flattest, slowest throw (200°, speed 1.10,
        //   dragFactor 0.90, so the largest vt): +0.7005 as tau approaches 1.
        // Rounded outward, so the fan can be re-rolled without re-deriving these.
        val dxLimit = 0.44f
        val dyFloor = -0.22f
        val dyCeiling = 0.72f

        for (piece in fan) {
            for (t in grid(200)) {
                val state = frame(piece, t) ?: continue
                assertTrue(state.dx >= -dxLimit)
                assertTrue(state.dx <= dxLimit)
                assertTrue(state.dy >= dyFloor)
                assertTrue(state.dy <= dyCeiling)
                assertTrue(state.widthScale >= MinFlip)
                assertTrue(state.widthScale <= 1f)
                assertTrue(state.alpha >= 0f)
                assertTrue(state.alpha <= 1f)
            }
        }
    }

    @Test
    fun `the same seed rolls the same burst`() {
        // A celebration that is different on every list is a random effect; this one
        // is designed, and the seed is what says so. The comparison is over the
        // sixteen draws, which is the whole identity of a piece: the six cached
        // derivations are a pure function of them.
        assertEquals(fan, confettiFan(Random(PieceCount * 31L)))
    }

    @Test
    fun `the fan sweeps up and out, in order, within every drawn range`() {
        assertEquals(PieceCount, fan.size)
        val sweepEnd = FanStartRadians + FanSweepRadians
        var previousAngle = Float.NEGATIVE_INFINITY
        for (piece in fan) {
            // Stratified, so the fan sweeps rather than scattering: a piece never
            // comes out behind the one before it.
            assertTrue(piece.angle >= previousAngle)
            previousAngle = piece.angle
            assertTrue(piece.angle >= FanStartRadians)
            assertTrue(piece.angle < sweepEnd)
            assertTrue(piece.speed >= MinSpeed)
            assertTrue(piece.speed <= MinSpeed + SpeedRange)
            assertTrue(abs(piece.spin) >= MinSpinRadians)
            assertTrue(abs(piece.spin) <= MinSpinRadians + SpinRadiansRange)
            assertTrue(piece.dragFactor >= MinDragFactor)
            assertTrue(piece.dragFactor <= MinDragFactor + DragFactorRange)
            assertTrue(piece.delay >= 0f)
            assertTrue(piece.delay < MaxLaunchDelay)
            // The rest of the sixteen. An unbounded draw is a draw the fan is free to
            // stop making: muzzle, swayAmp and swayRate could each be zeroed in
            // `confettiFan` with every other assertion in this file still green,
            // because nothing downstream of them has a floor of its own.
            assertTrue(piece.muzzle >= 0f)
            assertTrue(piece.muzzle <= MaxMuzzle)
            assertTrue(piece.swayAmp >= MinSwayAmp)
            assertTrue(piece.swayAmp <= MinSwayAmp + SwayAmpRange)
            assertTrue(piece.swayRate >= MinSwayRate)
            assertTrue(piece.swayRate <= MinSwayRate + SwayRateRange)
            assertTrue(piece.flipRate >= MinFlipRate)
            assertTrue(piece.flipRate <= MinFlipRate + FlipRateRange)
            assertTrue(piece.width >= MinPieceWidthDp)
            assertTrue(piece.width <= MinPieceWidthDp + PieceWidthRangeDp)
            assertTrue(piece.height >= MinPieceHeightDp)
            assertTrue(piece.height <= MinPieceHeightDp + PieceHeightRangeDp)
            // An index into the draw layer's palette array, so in range or it reads
            // off the end of it.
            assertTrue(piece.colorIndex >= 0)
            assertTrue(piece.colorIndex < ColorCount)
            for (phase in listOf(piece.spinPhase, piece.flipPhase, piece.swayPhase)) {
                assertTrue(phase >= 0f)
                assertTrue(phase < TwoPi)
            }
        }
    }

    // I6 — the flip really is its own axis.

    /** Flip held still and edge-on, while the piece makes its largest in-plane turn. */
    private val frozenFlip = buildPiece(
        angle = 0f,
        speed = MinSpeed,
        spin = MinSpinRadians + SpinRadiansRange,
        flipRate = 0f,
        flipPhase = (PI / 2.0).toFloat(),
    )

    @Test
    fun `a rotating piece does not turn flat just because it is rotating`() {
        for (t in grid(steps)) {
            val state = frame(frozenFlip, t) ?: continue
            // Not an exact equality: cos(π/2) is a few times 1e-8 rather than 0 in
            // binary32. The claim is that the flip is frozen, not that the library's
            // cosine is exact.
            assertEquals(MinFlip, state.widthScale, tol)
        }
    }

    @Test
    fun `a piece still rotates while its flip is frozen`() {
        assertTrue(frameAt(frozenFlip, 0.5f).rot - frameAt(frozenFlip, 0.1f).rot > 1.5f)
    }

    // Past the six: the flutter is wired, and the lean is the drift.
    //
    // The spec's list leaves the sway and the rock with no assertion at all, and they
    // are half of what makes this paper rather than a diagram — without them
    // forty-six pieces fall down forty-six straight lines, and every invariant above
    // stays green.

    /**
     * Straight up, so `cos(angle)` is zero and the throw contributes no sideways
     * travel: `dx` is the sway term alone. Spin and spinPhase zero, so every radian
     * of `rot` is the rock.
     */
    private val fluttering = buildPiece(
        angle = (-PI / 2.0).toFloat(),
        speed = MinSpeed,
        swayAmp = MinSwayAmp + SwayAmpRange,
        swayRate = MinSwayRate,
    )

    @Test
    fun `a falling piece drifts to both sides instead of dropping down a line`() {
        val states = grid(steps).mapNotNull { frame(fluttering, it) }
        // Nine radians of sway over a flight is a pass and a half, so a piece that is
        // really fluttering has to reach both sides of its own launch line.
        assertTrue(states.maxOf { it.dx } > 0.01f)
        assertTrue(states.minOf { it.dx } < -0.01f)
    }

    @Test
    fun `a falling piece banks into its own drift`() {
        // Both terms are `sin(theta) * spent`, so the lean is the drift scaled by
        // Rock — exactly, at every tau. Drop `Rock * sway` from `rot` and the piece
        // slides sideways bolt upright, which reads as a sprite on a path.
        for (state in grid(steps).mapNotNull { frame(fluttering, it) }) {
            assertEquals(state.dx * Rock, state.rot * fluttering.swayAmp, tol)
        }
    }
}
