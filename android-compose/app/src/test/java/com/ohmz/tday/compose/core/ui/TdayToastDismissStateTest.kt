package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A toast is dismissed on purpose or not at all.
 *
 * The defect these pin is that it used to be dismissed by accident: the commit
 * was `if (dragOffsetY > 0f)`, so one pixel of downward travel — a twitch on the
 * way to the Undo button, a parent claiming the pointer — threw the card off the
 * bottom of the screen and reported it gone. The Undo it was carrying went with
 * it, and the only way back was to do the delete again.
 *
 * There is no Robolectric and no Compose harness on this source set, which is
 * why the decision is a state holder rather than four lines inside a gesture
 * lambda. What is checked here is the decision either side of the lift-off: how
 * far is far enough, how fast is fast enough, and which of the two wins when
 * they disagree. The spring the refusal goes home on is then sampled directly,
 * because a `SpringSpec` vectorizes to plain arithmetic that needs no frame
 * clock — the same trick `TaskSwipeRevealStateTest` explains.
 */
class TdayToastDismissStateTest {

    /** 96 dp of fade distance on a 3x phone, which is what the host hands it. */
    private val fadeDistancePx = 288f

    /** 288 x 0.32. Written out rather than re-derived: comparing a threshold back
     * against its own definition would pass whatever either one changed to. */
    private val thresholdPx = 92.16f

    private fun state() = TdayToastDismissState(fadeDistancePx = fadeDistancePx)

    // ---- the drag is the finger, plus the gain --------------------------------------

    @Test
    fun `a drag carries the card slightly further than the thumb`() {
        val state = state()

        state.dragBy(50f)

        // The 1.18 gain, applied where the threshold can see it: the distance
        // being judged has to be the distance the user is looking at.
        assertEquals(59f, state.offsetY, 0.001f)
        assertTrue(state.isDragging)
    }

    @Test
    fun `there is nothing above a toast to drag it into`() {
        val state = state()

        state.dragBy(-200f)

        assertEquals(0f, state.offsetY, 0f)
    }

    // ---- how far is far enough ------------------------------------------------------

    @Test
    fun `a twitch on the way to a tap does not throw the toast away`() {
        val state = state()
        state.dragBy(1f)

        val release = state.settle(velocityPxPerSecond = 0f)

        // The whole defect, in one assertion: this used to be true.
        assertFalse(release.dismisses)
        assertEquals(1.18f, release.fromPx, 0.001f)
        // And it goes home from exactly where it was let go, rather than
        // reappearing at rest a frame later.
        assertFalse(state.isDragging)
        assertEquals(0f, state.offsetY, 0f)
    }

    @Test
    fun `a drag that stops short of the threshold goes home`() {
        val state = state()
        state.dragBy(70f) // 82.6 px, under 92.16

        val release = state.settle(velocityPxPerSecond = 0f)

        assertFalse(release.dismisses)
        assertEquals(82.6f, release.fromPx, 0.001f)
    }

    @Test
    fun `a drag past the threshold dismisses on its own`() {
        val state = state()
        state.dragBy(80f) // 94.4 px, past 92.16

        val release = state.settle(velocityPxPerSecond = 0f)

        assertTrue(release.dismisses)
        assertTrue(release.fromPx > thresholdPx)
    }

    // ---- how fast is fast enough ----------------------------------------------------

    @Test
    fun `a flick dismisses from nowhere near the threshold`() {
        val state = state()
        state.dragBy(20f) // 23.6 px, a quarter of the distance

        val release = state.settle(velocityPxPerSecond = 2000f) // past 1450 px/s

        assertTrue(release.dismisses)
        // The speed is reported on every release, this one included — but on a
        // dismissal nothing downstream reads it yet. The card leaves on the
        // fixed-length TOAST_DISMISS_DURATION_MS tween it has always left on,
        // and a tween has no use for an initial velocity even when handed one.
        // So this pins the number as available, not as a continuity that
        // exists: giving a flick a shorter flight than a slow drag reshapes the
        // exit spec, which is `toast-drag-two-stage-exit`'s job and not this
        // fix's.
        assertEquals(2000f, release.initialVelocityPxPerSecond, 0f)
    }

    @Test
    fun `a slow release from nowhere near the threshold does not`() {
        val state = state()
        state.dragBy(20f)

        val release = state.settle(velocityPxPerSecond = 1400f) // short of 1450

        assertFalse(release.dismisses)
    }

    @Test
    fun `a flick back up beats the distance it was flicked from`() {
        val state = state()
        state.dragBy(120f) // 141.6 px, well past the threshold

        val release = state.settle(velocityPxPerSecond = -2000f)

        // Distance says dismiss and the finger says otherwise; the finger is the
        // more recent of the two, and it is the one that was still moving.
        assertFalse(release.dismisses)
        // This is the branch where the velocity reaches an animation: a refusal
        // goes home on `Return` carrying it, so a throw and the return after it
        // are one movement. Drop it and the card stops dead at lift-off before
        // starting back.
        assertEquals(-2000f, release.initialVelocityPxPerSecond, 0f)
    }

    // ---- a cancelled gesture --------------------------------------------------------

    @Test
    fun `a gesture the system takes away leaves the toast where it found it`() {
        val state = state()
        // A cancel arrives as a release with no velocity: `DragGestureNode`
        // answers one by calling `onDragStopped(Velocity.Zero)`. The cancel that
        // actually happens is a parent claiming the pointer a few pixels in, and
        // distance refuses that on its own.
        state.dragBy(4f)

        val release = state.settle(velocityPxPerSecond = 0f)

        assertFalse(release.dismisses)
    }

    @Test
    fun `a cancel is judged on distance, and past the threshold that commits`() {
        val state = state()
        state.dragBy(120f)

        val release = state.settle(velocityPxPerSecond = 0f)

        // Pinned as the known limit rather than left to be read as an oversight:
        // by the time a cancel reaches the holder it is identical to a finger
        // lifting at the same place, and a finger lifting there means to dismiss.
        assertTrue(release.dismisses)
    }

    // ---- the refusal goes home on the row's own spring -------------------------------

    @Test
    fun `a refused toast and a refused row settle on the same spring`() {
        // Not "is a spring": the claim is that these two are one gesture. 0.82 /
        // 340 is iOS's interactiveSpring(response: 0.34, dampingFraction: 0.82),
        // which TaskSwipeRevealState converts and TdayMotionTokens names Gesture.
        assertEquals(
            TaskSwipeMotion.Release.dampingRatio,
            TdayToastDismissState.Return.dampingRatio,
            0f,
        )
        assertEquals(
            TaskSwipeMotion.Release.stiffness,
            TdayToastDismissState.Return.stiffness,
            0f,
        )
    }

    @Test
    fun `the refusal travels home rather than cutting to it`() {
        val spec = TdayToastDismissState.Return.vectorize(Float.VectorConverter)
        val from = AnimationVector1D(141.6f)
        val to = AnimationVector1D(0f)
        val atRest = AnimationVector1D(0f)

        assertEquals(141.6f, spec.getValueFromNanos(0L, from, to, atRest).value, 0.01f)

        // One frame in it has moved and is still much nearer where the finger
        // left it than where it is going. A snapTo would already be home, which
        // is what a refused toast used to do.
        val oneFrame = spec.getValueFromNanos(16_000_000L, from, to, atRest).value
        assertTrue(oneFrame < 141.6f)
        assertTrue(oneFrame > 100f)

        val durationNanos = spec.getDurationNanos(from, to, atRest)
        assertTrue(durationNanos > 16_000_000L)
        assertEquals(0f, spec.getValueFromNanos(durationNanos, from, to, atRest).value, 1f)
    }
}
