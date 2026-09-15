package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The swipe row has two clocks in it and they must never both be running.
 *
 * The defect these pin is the one where they were the same clock: a single
 * `StiffnessLow` spring drove the drag *and* the release, so every frame of the
 * gesture was the spring chasing a target the finger had already left. There is
 * no Robolectric and no Compose harness on this source set, so what is checked
 * here is the state machine either side of the lift-off — that a drag writes the
 * finger's translation through untouched, that no animation is in flight while a
 * pointer is down, and that a release hands off from exactly where the finger
 * was with exactly the velocity it had. The spring itself is then sampled
 * directly, because `SpringSpec` vectorizes to plain arithmetic that needs no
 * frame clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSwipeRevealStateTest {

    private val revealWidthPx = 256f
    private val hintOffsetPx = 42f

    /** The animator scale on a device nobody has touched the slider on. */
    private val unscaled = 1f

    private fun state() = TaskSwipeRevealState(
        revealWidthPx = revealWidthPx,
        hintOffsetPx = hintOffsetPx,
        maxElasticDragPx = revealWidthPx * 1.14f,
    )

    // ---- the drag is the finger, 1:1 ------------------------------------------------

    @Test
    fun `a drag of N px moves the row N px, not a fraction of N`() {
        val state = state()

        state.dragBy(-40f)
        // Exact, with a zero tolerance: the whole defect was that this arrived
        // as some spring-damped share of -40 and caught up a few frames later.
        assertEquals(-40f, state.offsetX, 0f)

        state.dragBy(-30f)
        assertEquals(-70f, state.offsetX, 0f)

        state.dragBy(12.5f)
        assertEquals(-57.5f, state.offsetX, 0f)
    }

    @Test
    fun `nothing is animating while a pointer is down`() {
        val state = state()

        state.dragBy(-40f)

        assertTrue(state.isDragging)
        // A non-null release is the only thing that starts the spring. While the
        // finger is on the row there must not be one, or the two clocks are both
        // running again and the row trails the thumb.
        assertNull(state.release)
        assertTrue(state.isOpenOrDragging)
    }

    @Test
    fun `only the elastic limit ever clamps a drag`() {
        val state = state()

        state.dragBy(-1000f)
        assertEquals(-revealWidthPx * 1.14f, state.offsetX, 0.001f)

        // Coming back out of the overdrag still tracks 1:1 from the clamped edge.
        state.dragBy(50f)
        assertEquals(-revealWidthPx * 1.14f + 50f, state.offsetX, 0.001f)

        state.dragBy(1000f)
        assertEquals(0f, state.offsetX, 0f)
    }

    // ---- the release, and only the release, is sprung -------------------------------

    @Test
    fun `a release past the threshold springs open from where the finger let go`() {
        val state = state()
        state.dragBy(-100f) // past 256 * 0.32 = 81.92

        state.settle(velocityPxPerSecond = 0f)

        assertNotNull(state.release)
        val release = state.release!!
        assertEquals(-100f, release.fromPx, 0f)
        assertEquals(-revealWidthPx, release.toPx, 0f)
        assertFalse(state.isDragging)
        assertTrue(state.isOpenOrDragging)
    }

    @Test
    fun `a release short of the threshold springs back to closed`() {
        val state = state()
        state.dragBy(-60f) // short of 81.92

        state.settle(velocityPxPerSecond = 0f)

        val release = state.release!!
        assertEquals(-60f, release.fromPx, 0f)
        assertEquals(0f, release.toPx, 0f)
        // The swipe slot is given up at the moment of the decision, not when the
        // spring finishes, which is what the row's own tap handler reads.
        assertFalse(state.isOpenOrDragging)
    }

    @Test
    fun `a fling opens from under the threshold and carries its velocity into the spring`() {
        val state = state()
        state.dragBy(-20f) // nowhere near the distance threshold

        state.settle(velocityPxPerSecond = -1500f) // past -1450 px/s

        val release = state.release!!
        assertEquals(-revealWidthPx, release.toPx, 0f)
        // A throw and a settle have to be one movement. Drop the velocity here
        // and the row stops dead at lift-off before starting again.
        assertEquals(-1500f, release.initialVelocityPxPerSecond, 0f)
    }

    @Test
    fun `closing a row springs it shut from wherever it stands`() {
        val state = state()
        state.dragBy(-200f)
        state.settle(velocityPxPerSecond = 0f)

        state.close()

        val release = state.release!!
        assertEquals(-200f, release.fromPx, 0f)
        assertEquals(0f, release.toPx, 0f)
        assertFalse(state.isOpenOrDragging)
    }

    @Test
    fun `a finger landing mid-settle takes the row off the spring`() {
        val state = state()
        state.dragBy(-100f)
        state.settle(velocityPxPerSecond = 0f)
        assertNotNull(state.release)

        state.dragBy(-10f)

        assertNull(state.release)
        assertTrue(state.isDragging)
        assertEquals(-110f, state.offsetX, 0f)
    }

    // ---- the reveal detent fires once per open --------------------------------------
    //
    // No gate in this repository can feel a haptic, and there is no device here.
    // What can be proven is the decision, which is why the decision is a pure
    // function of state this class already holds and the buzz is the composable's
    // job: every case below is the whole rule, minus the vibration.

    /** 256 * 0.32. A drag past this is a drag that would open the row if it stopped. */
    private val detentPx = -81.92f

    @Test
    fun `crossing the detent fires exactly once`() {
        val state = state()

        assertFalse(state.dragBy(-40f))
        assertFalse(state.dragBy(-30f)) // -70, still short of -81.92
        assertTrue(state.dragBy(-20f)) // -90, past it

        // The row is committed from here on, and the hand has already been told.
        assertFalse(state.dragBy(-20f))
        assertFalse(state.dragBy(-100f))
    }

    @Test
    fun `a finger parked on the detent does not rattle`() {
        val state = state()
        assertTrue(state.dragBy(-90f))

        // Ten frames of a finger holding almost still either side of -81.92.
        // Sampling "is the row past the threshold?" per frame answers yes to
        // most of these; remembering the event answers no to all of them.
        repeat(5) {
            assertFalse(state.dragBy(-0.5f))
            assertFalse(state.dragBy(0.5f))
        }
    }

    @Test
    fun `past, back under and past again inside one gesture fires only the first time`() {
        val state = state()

        assertTrue(state.dragBy(-100f))
        assertFalse(state.dragBy(50f)) // -50, back short of the detent
        // Without a finger lift there has been no close, so the row has not
        // re-armed: this is one continuous gesture and it is worth one buzz.
        assertFalse(state.dragBy(-80f)) // -130, past again
    }

    @Test
    fun `a release that opens after the detent already fired fires nothing`() {
        val state = state()
        assertTrue(state.dragBy(-100f))

        assertFalse(state.settle(velocityPxPerSecond = 0f))
        assertEquals(-revealWidthPx, state.restOffsetX, 0f)
    }

    @Test
    fun `a release that lands closed fires nothing`() {
        val state = state()
        assertFalse(state.dragBy(-60f)) // short of the detent

        assertFalse(state.settle(velocityPxPerSecond = 0f))
        assertEquals(0f, state.restOffsetX, 0f)
    }

    @Test
    fun `a fling that never crossed the detent fires at the release`() {
        val state = state()
        assertFalse(state.dragBy(-20f)) // nowhere near the distance threshold

        // Mirrors `a fling opens from under the threshold...` above: the most
        // deliberate swipe in the app opens the row without ever crossing -81.92,
        // and would be the only silent one if the detent were the only arm.
        assertTrue(state.settle(velocityPxPerSecond = -1500f))
        assertEquals(-revealWidthPx, state.restOffsetX, 0f)
        // And it has spent this open-cycle's buzz like any other arm would.
        assertFalse(state.dragBy(-200f))
    }

    /** Runs the release spring's frames to their target, the way the row draws it. */
    private fun TaskSwipeRevealState.land() {
        onReleaseFrame(restOffsetX)
    }

    @Test
    fun `a row that has come to rest closed fires again on the next swipe`() {
        val state = state()
        assertTrue(state.dragBy(-100f))
        assertFalse(state.settle(velocityPxPerSecond = 0f))
        state.land()

        // Closing itself answers nothing to fire — `close()` returns Unit and
        // always will. What it does is re-arm: opening uncovers something that
        // was not there, closing puts back what was.
        state.close()
        state.land()

        assertFalse(state.dragBy(-60f)) // short of -81.92 again
        assertTrue(state.dragBy(-40f)) // -100, past
    }

    @Test
    fun `a row closed from under the user re-arms on the same funnel`() {
        val state = state()
        assertTrue(state.dragBy(-100f))
        assertFalse(state.settle(velocityPxPerSecond = 0f))
        state.land()

        // One row is open at a time, so most closes are another row claiming the
        // slot rather than anything this finger did. They all reach the state
        // through `close()`, which is the point of re-arming in the one funnel
        // rather than at each of the four screens' close call sites.
        state.close()
        state.land()

        assertTrue(state.dragBy(-100f))
    }

    @Test
    fun `an already-open row dragged further open fires nothing`() {
        val state = state()
        assertTrue(state.dragBy(-100f))
        assertFalse(state.settle(velocityPxPerSecond = 0f))
        state.land()
        assertEquals(-revealWidthPx, state.offsetX, 0f)

        // A finger landing on an open row and pulling it further out is not a
        // reveal; there is nothing left to reveal.
        assertFalse(state.dragBy(-50f))
        // Nor is coming part of the way back and going out again without the row
        // ever coming to rest closed in between.
        assertFalse(state.dragBy(150f))
        assertFalse(state.dragBy(-150f))
    }

    @Test
    fun `the settle animation cannot fire the detent on its own frames`() {
        val state = state()
        // Opened on velocity alone, so the row is still at -20 px when the
        // spring takes over and every frame of the settle crosses -81.92.
        assertFalse(state.dragBy(-20f))
        assertTrue(state.settle(velocityPxPerSecond = -1500f))

        // `onReleaseFrame` is the app's clock, not the finger's. A detent test
        // derived from `offsetX` in general rather than from the drag path in
        // particular would phantom-fire here one frame after the real event, and
        // again on every programmatic open. It returns Unit precisely so that it
        // cannot; what this pins is that driving the row through the threshold
        // this way leaves the state's own arming untouched.
        for (px in -20 downTo -256 step 8) {
            state.onReleaseFrame(px.toFloat())
        }
        state.onReleaseFrame(-revealWidthPx)
        assertEquals(-revealWidthPx, state.offsetX, 0f)

        // Still spent from the fling, not re-armed and not double-fired by the
        // 30 frames just pushed through the threshold.
        assertFalse(state.dragBy(-10f))
    }

    @Test
    fun `the hint never reaches the detent`() {
        // `playHint` moves the row through `settleTo`, so it cannot trip a test
        // that lives in the drag path — but the reason a tap-to-hint is silent
        // *and would stay silent under any offset-derived rule* is this
        // inequality, and it holds by coincidence of two independent constants:
        // `rememberTaskSwipeRevealState` coerces the hint to at most 0.24 of the
        // reveal width, and the detent sits at 0.32 of it. Raising the hint
        // offset turns this red instead of making a tap buzz.
        assertTrue(revealWidthPx * 0.24f < revealWidthPx * 0.32f)
        assertTrue(hintOffsetPx < revealWidthPx * 0.32f)
        assertTrue(-hintOffsetPx > detentPx)
    }

    // ---- the release spring is iOS's, converted ------------------------------------

    @Test
    fun `the release spring is the converted iOS release spec`() {
        // SwipeActions.swift:353 — .interactiveSpring(response: 0.34, dampingFraction: 0.82).
        // response is the undamped period, so stiffness is (2 * PI / 0.34)^2 ~= 341.
        assertEquals(0.82f, TaskSwipeMotion.Release.dampingRatio, 0f)
        assertEquals(340f, TaskSwipeMotion.Release.stiffness, 0f)
        // Not Spring.StiffnessMedium (1500f), which is 4.4x this and is a Compose
        // default rather than a decision.
        assertTrue(TaskSwipeMotion.Release.stiffness < 1500f)
    }

    @Test
    fun `the release spring travels rather than cutting`() {
        val spec = TaskSwipeMotion.Release.vectorize(Float.VectorConverter)
        val from = AnimationVector1D(-100f)
        val to = AnimationVector1D(-revealWidthPx)
        val atRest = AnimationVector1D(0f)

        assertEquals(-100f, spec.getValueFromNanos(0L, from, to, atRest).value, 0.01f)

        // One frame in it has moved, and it is still much nearer where the finger
        // left it than where it is going — a cut would already be at the target.
        val oneFrame = spec.getValueFromNanos(16_000_000L, from, to, atRest).value
        assertTrue(oneFrame < -100f)
        assertTrue(oneFrame > -160f)

        // Halfway through its travel it is between the two, not at either.
        val mid = spec.getValueFromNanos(60_000_000L, from, to, atRest).value
        assertTrue(mid < oneFrame)
        assertTrue(mid > -revealWidthPx)

        val durationNanos = spec.getDurationNanos(from, to, atRest)
        assertTrue(durationNanos > 16_000_000L)
        assertEquals(
            -revealWidthPx,
            spec.getValueFromNanos(durationNanos, from, to, atRest).value,
            1f,
        )
    }

    // ---- the hint never overrules a finger ------------------------------------------

    @Test
    fun `a hint refuses to start under a live finger`() = runTest {
        val state = state()
        state.dragBy(-30f)

        state.playHint(unscaled)

        assertEquals(-30f, state.offsetX, 0f)
        assertFalse(state.isHinting)
        assertNull(state.release)
        assertTrue(state.isDragging)
    }

    @Test
    fun `a hint in flight abandons the row to a finger that arrives`() = runTest {
        val state = state()
        val hint = launch { state.playHint(unscaled) }
        advanceTimeBy(50)
        runCurrent()
        assertTrue(state.isHinting)
        assertEquals(-hintOffsetPx, state.restOffsetX, 0f)

        state.dragBy(-30f)
        // Past the 150 ms hold, which is where the hint used to write a zero
        // straight over the finger's offset.
        advanceTimeBy(200)
        runCurrent()

        assertEquals(-30f, state.offsetX, 0f)
        assertNull(state.release)
        assertTrue(state.isDragging)

        hint.join()
        assertFalse(state.isHinting)
        assertEquals(-30f, state.offsetX, 0f)
    }

    @Test
    fun `a hint left alone holds the row out and then returns it`() = runTest {
        val state = state()
        val hint = launch { state.playHint(unscaled) }
        advanceTimeBy(50)
        runCurrent()

        assertEquals(-hintOffsetPx, state.restOffsetX, 0f)
        val out = state.release!!
        assertEquals(0f, out.fromPx, 0f)
        assertEquals(-hintOffsetPx, out.toPx, 0f)

        advanceTimeBy(150)
        runCurrent()
        assertEquals(0f, state.restOffsetX, 0f)

        hint.join()
        assertFalse(state.isHinting)
        assertFalse(state.isOpenOrDragging)
    }

    @Test
    fun `a flick that lands and lifts inside the hold leaves the row it opened open`() = runTest {
        val state = state()
        val hint = launch { state.playHint(unscaled) }
        advanceTimeBy(50)
        runCurrent()
        assertTrue(state.isHinting)

        // A flick is 60-100 ms of contact: down, across, gone — all of it inside
        // the hint's 150 ms hold. By the time the hint wakes up there is no
        // finger left to see, so asking `isDragging` then answers "no" and the
        // return leg used to slam the just-opened row shut.
        state.dragBy(-30f)
        state.settle(velocityPxPerSecond = -1600f)
        assertFalse(state.isDragging)
        assertEquals(-revealWidthPx, state.restOffsetX, 0f)

        advanceTimeBy(200)
        runCurrent()

        assertEquals(-revealWidthPx, state.restOffsetX, 0f)
        assertTrue(state.isOpenOrDragging)

        hint.join()
        assertFalse(state.isHinting)
        assertEquals(-revealWidthPx, state.restOffsetX, 0f)
        assertTrue(state.isOpenOrDragging)
    }

    // ---- the hint is on the animator's clock -----------------------------------------

    @Test
    fun `a hint does not play at all with animations off`() = runTest {
        val state = state()

        state.playHint(0f)

        // Not "plays instantly": the row is never written at all. The hint is made
        // entirely of movement, so with the springs collapsed to a frame the only
        // thing left to show is a 42 px flick out and back inside that frame.
        assertEquals(0f, state.offsetX, 0f)
        assertEquals(0f, state.restOffsetX, 0f)
        assertNull(state.release)
        assertFalse(state.isHinting)
    }

    @Test
    fun `a scale the setting should never hold still cannot flick the row`() = runTest {
        val state = state()

        // `Settings.Global` is a float anything holding WRITE_SECURE_SETTINGS can put
        // a number in, and NaN fails every comparison it is put through — including
        // the one that would otherwise have let it through as "not zero".
        state.playHint(Float.NaN)

        assertNull(state.release)
        assertFalse(state.isHinting)
    }

    @Test
    fun `a hint at double speed holds twice as long`() = runTest {
        val state = state()
        val hint = launch { state.playHint(2f) }
        runCurrent()

        assertEquals(-hintOffsetPx, state.restOffsetX, 0f)

        // At 1x the row would be on its way back by now; at 2x the springs either
        // side of this hold take twice as long too, so the hold has to stretch with
        // them or the return leg fires over an outbound spring still travelling.
        advanceTimeBy(200)
        runCurrent()
        assertEquals(-hintOffsetPx, state.restOffsetX, 0f)

        advanceTimeBy(150)
        runCurrent()
        assertEquals(0f, state.restOffsetX, 0f)

        hint.join()
        assertFalse(state.isHinting)
    }

    @Test
    fun `a second hint cannot start while the first is still running`() = runTest {
        val state = state()
        val first = launch { state.playHint(unscaled) }
        advanceTimeBy(50)
        runCurrent()

        state.playHint(unscaled)
        assertEquals(-hintOffsetPx, state.restOffsetX, 0f)

        first.join()
        assertFalse(state.isHinting)
    }
}
