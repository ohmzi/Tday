package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * The one set of `Modifier.animateItem` specs a task feed moves by.
 *
 * A feed's rows and everything a row's departure displaces have to travel on the
 * same clock, or the screen comes apart at the moment it should feel finished.
 * The floater home is where that shows worst: ticking off the last task drops a
 * near-half-screen empty scene into the slot the row just left, and anything
 * below it that is not animating is teleported down that whole distance in a
 * single frame — under the confetti, which is going off over the top of it.
 *
 * [PlacementMillis] is deliberately the same number as
 * [TdayEmptyStateCelebrationLeadMillis]: whatever the scene pushes down finishes
 * sliding on the frame the scene itself starts rising through the burst. That is
 * the order the list-detail screens get for free — there the empty state is a
 * full-screen overlay, so the burst leads and the scene follows with nothing
 * else on screen moving at all. The floater home keeps its inline layout (the
 * one iOS and web share) and buys the same sequencing with motion instead.
 */
object TdayFeedItemMotion {

    /** An item arriving. */
    const val FadeInMillis = 190

    /** An item moving to a new slot — and everything moved by that. */
    const val PlacementMillis = 320

    /** An item leaving. Shorter than the arrival: an absence should not linger. */
    const val FadeOutMillis = 150

    val FadeIn: FiniteAnimationSpec<Float> =
        tween(durationMillis = FadeInMillis, easing = FastOutSlowInEasing)

    val Placement: FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = PlacementMillis, easing = FastOutSlowInEasing)

    val FadeOut: FiniteAnimationSpec<Float> =
        tween(durationMillis = FadeOutMillis, easing = FastOutSlowInEasing)
}
