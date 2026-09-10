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
 * single frame.
 *
 * Two rules come out of that, and both matter more than the numbers:
 *
 * 1. **A displaced item takes [Placement] and nothing else.** The Completed
 *    tile, the "My Lists" header and the list rows are never added or removed by
 *    a completion — they are only moved by one. Giving them fades as well buys
 *    nothing here and costs elsewhere: search swaps the whole feed body out in
 *    one go, and an item that fades would fade the entire screen in and out on
 *    every open and close of the field.
 *
 *    That "only moved by one [jump]" is load-bearing, not incidental: [Placement]
 *    is a tween that chases wherever an item's target offset lands *this frame*,
 *    which reads as smooth precisely because a plain add/remove moves that
 *    target exactly once and then holds it still. An item whose neighbor is
 *    instead resizing itself frame-by-frame — an `AnimatedVisibility` running
 *    `expandVertically`/`shrinkVertically`, say — keeps moving that target for
 *    the whole of the resize, and [PlacementMillis] outlasting that resize (see
 *    [TdayFeedItemMotionTest]'s "an item leaves faster than it arrives") means
 *    the displaced neighbor never catches up before the target has already
 *    stopped: it lags behind the resizing item's real, already-smooth bounds
 *    for the whole transition, long enough to visibly overlap whatever sits on
 *    the far side of it. `TodoListScreen`'s Earlier header is the one caller
 *    that hits this (`earlierHeaderSkipsPlacementSpec`, next to the inline
 *    "today-earlier-empty-scene" item its own doc points at) — it drops
 *    [Placement] entirely rather than re-time it, because nothing else ever
 *    legitimately moves that header while that item exists.
 * 2. **The celebration waits for the move, it does not race it.** The scene's
 *    host passes [PlacementMillis] as `TdayEmptyState`'s
 *    `celebrationStartDelayMillis`, so the burst begins on the frame the feed
 *    finishes settling. The list-detail screens get that ordering for free —
 *    there the empty state is a full-screen overlay and nothing on the page
 *    moves at all — and the floater home, which keeps the inline layout iOS and
 *    web share, buys it with this delay instead.
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

    /**
     * How long a feed that hosts `TdayEmptyState` inline holds its celebration
     * back — exactly as long as the items that scene displaces take to reach
     * their new slots.
     */
    const val CelebrationStartDelayMillis: Long = PlacementMillis.toLong()
}
