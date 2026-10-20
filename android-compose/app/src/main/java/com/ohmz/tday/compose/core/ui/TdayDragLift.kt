package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp as lerpFloat

/**
 * Picking a task up: what rises, by how much, and on which rung.
 *
 * Two screens drag a task — the timeline's date sections and the calendar's
 * month grid — and each one draws a preview card that follows the finger while
 * the row it came from stays in the list. Both cards used to appear at their
 * final size with their shadow already cast, which gives a pick-up no moment of
 * picking up: the card is in the row, then it is in the air, in the same frame.
 * Both were also drawn translucent, which is worse than nothing — the app spends
 * partial alpha on secondary and unavailable things everywhere else, so a card
 * the finger is holding at 88 % reads as one the user may not have.
 *
 * **A lift is elevation and scale, never transparency.** Transparency on these
 * screens already has a job and keeps it: [VacatedAlpha] dims the row left
 * behind, because a hole is exactly what that row now is. The card is opaque,
 * sits higher than anything under it, and is slightly larger than the row it
 * came out of.
 *
 * [LiftedScale] is not a new number. [TdayMotionTokens.PressScales.Card] is how
 * far a card sinks under a finger; a lift is the same card travelling the other
 * way under the same finger, so it rises by the same amount — and the tree gets
 * no tenth press literal on top of the nine `docs/motion.md` already counts.
 * Web reads the identical token the identical way, as
 * `calc(2 - var(--tday-press-card))` in `globals.css`.
 *
 * Emphasis by the second idiom rule: the card changes how big it is, and
 * geometry takes the longer rung whatever it is a gesture for. The Enter curve,
 * because the card is arriving under a finger and should settle rather than
 * stop dead.
 */
object TdayDragLift {

    /** Flat, in the row, where the card starts. */
    val RestingElevation: Dp = 0.dp

    /** In the air, where a card the finger is holding lives. */
    val LiftedElevation: Dp = 12.dp

    /**
     * How far a lifted card outgrows the row it came from: the card press scale,
     * read backwards. See the class doc — this is a derivation and not a
     * measurement, so it moves if and only if the token does.
     */
    val LiftedScale: Float = 2f - TdayMotionTokens.PressScales.Card

    /**
     * How far the row left behind dims.
     *
     * Kept here beside the lift rather than at the two rows, because the dim and
     * the rise are one event seen from two places and reading them apart is how
     * they drift. iOS spends the same value on the same row
     * (`CalendarScreen.swift`'s `.opacity(draggedTodo?.id == todo.id ? 0.7 : 1)`),
     * which is why this is 0.7 and not a number chosen here.
     */
    const val VacatedAlpha: Float = 0.7f

    /**
     * The clock both halves run on.
     *
     * `snap()` rather than a shortened tween when the platform has animations
     * off: reduced motion removes the trip and never the destination, and the
     * destination of a pick-up is a card that is already up and a row that is
     * already a hole.
     */
    fun spec(motionEnabled: Boolean): FiniteAnimationSpec<Float> = if (motionEnabled) {
        tween(
            durationMillis = TdayMotionTokens.Durations.Emphasis,
            easing = TdayMotionTokens.Easings.Enter,
        )
    } else {
        snap()
    }

    /**
     * The rise itself, as a 0-to-1 progress a preview card can read twice — once
     * for its scale and once for its elevation — so the two cannot come apart.
     *
     * Seeded at 1 when motion is off, the way `TdayEmptyState` seeds its own
     * appearance, rather than seeded at 0 with the animation skipped: a card
     * held at the first frame of a lift is a card that never lifted.
     *
     * @param motionEnabled Whether decorative motion should play at all.
     * @return The lift's progress, 0 at rest and 1 fully lifted.
     */
    @Composable
    fun rememberProgress(motionEnabled: Boolean): State<Float> {
        val progress = remember { Animatable(if (motionEnabled) 0f else 1f) }
        LaunchedEffect(Unit) {
            if (!motionEnabled) return@LaunchedEffect
            progress.animateTo(targetValue = 1f, animationSpec = spec(motionEnabled = true))
        }
        return progress.asState()
    }

    /** The card's scale at a given point in the rise. */
    fun scaleAt(progress: Float): Float = lerpFloat(1f, LiftedScale, progress)

    /** The card's elevation at a given point in the rise. */
    fun elevationAt(progress: Float): Dp =
        lerpDp(RestingElevation, LiftedElevation, progress)
}
