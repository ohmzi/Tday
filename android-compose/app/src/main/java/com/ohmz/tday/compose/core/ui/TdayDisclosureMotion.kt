package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment

/**
 * A box that opens in place: the app's one disclosure spec.
 *
 * Settings' three inline account forms — name, password, security questions —
 * each wrapped themselves in a bare
 * `AnimatedVisibility(enter = expandVertically(), exit = shrinkVertically())`.
 * That is two defects in one line. It names no spec at all, so the forms ran on
 * whatever Compose's default spring happens to be rather than on a rung; and,
 * worse, `expandVertically` **clips**. With nothing fading, the travelling clip
 * edge is the only thing describing the growth, so it saws down the page through
 * the `OutlinedTextField` labels and the password dots — a field whose label is
 * cut in half reads as a rendering bug, not as a form opening.
 *
 * **The fade is not decoration.** Pairing it with the expand gives the content
 * somewhere to come from other than out of a moving cut. That pairing is not new
 * here: `TodoListScreen`'s `today-earlier-empty-scene` item argues it in place
 * for the Earlier illustration — fade AND expand, so the arrival reads as one
 * motion rather than an alpha ramp over a height that has already snapped — and
 * the task-feed skeleton right above it hands over to the real rows the same
 * way. This object is that shape with a name, so a fourth disclosure does not
 * have to rediscover it.
 *
 * **[Alignment.Top], and this is the half the fade cannot do.** `expandVertically`
 * defaults to `expandFrom = Alignment.Bottom`, which aligns the full-height
 * content with the BOTTOM of the growing box: the content is laid out at
 * `y = animatedHeight - fullHeight` and slides down through a clip edge that
 * stands still under the header. The user is shown the form's last row first and
 * its first field's floating label last, sliced, at near-full alpha — a drawer
 * pulled out from behind the row above, and the glyph it cuts is the one being
 * read. Anchored to the top, the content never moves: it is pinned under the
 * header it belongs to and the cut travels away along the arriving bottom edge,
 * which is where a disclosure's cut belongs. The `today-earlier-empty-scene`
 * precedent above takes the defaults and is right to — a full-bleed illustration
 * has no glyph at its top edge to lose. Three stacked text fields are the
 * opposite case.
 *
 * The clip itself stays. `clip = false` would let the form paint over the section
 * beneath it for the length of the open, which trades a cut for an overlap; the
 * cut is fine once it is travelling along an edge nothing is being read at, and
 * the fade is what keeps even that from reading as a hard line.
 *
 * **Emphasis, because the box changes size.** `docs/motion.md`'s Durations table
 * puts position and size on this rung and says where the boundary is: geometry,
 * not importance. A form growing by three text fields is the plainest geometry
 * in the app, and 320 ms is long enough that the growth can be followed with the
 * eye instead of merely noticed afterwards.
 *
 * **[Exit] is `Quick` by the first idiom rule** — an exit is never longer than
 * the enter it undoes. Nobody is meant to watch a form they have just cancelled
 * fold itself away; the rung for something leaving is the one the app answers a
 * finger on.
 *
 * These are plain values and not a `spec(motionEnabled)` the way [TdayDragLift]
 * needs one, because an `EnterTransition` runs under the recomposer's
 * `MotionDurationScale`: a device at 0x lands the form at full height on the next
 * frame without anything here asking. What that does *not* reach is the in-app
 * "Reduce motion" switch, which cannot substitute a coroutine context for a
 * subtree — see `effectiveMotionScale`. These three sites are therefore part of
 * `docs/motion/LEDGER.md`'s `reduced-motion-coverage`, and whoever migrates them
 * migrates this object rather than the call sites.
 */
object TdayDisclosureMotion {

    /** The box opening: fade and grow together downwards, on the geometry rung. */
    val Enter: EnterTransition = fadeIn(
        animationSpec = tween(
            durationMillis = TdayMotionTokens.Durations.Emphasis,
            easing = TdayMotionTokens.Easings.Enter,
        ),
    ) + expandVertically(
        animationSpec = tween(
            durationMillis = TdayMotionTokens.Durations.Emphasis,
            easing = TdayMotionTokens.Easings.Enter,
        ),
        expandFrom = Alignment.Top,
    )

    /** The box folding away: the same motion backwards, up into its header, and shorter. */
    val Exit: ExitTransition = fadeOut(
        animationSpec = tween(
            durationMillis = TdayMotionTokens.Durations.Quick,
            easing = TdayMotionTokens.Easings.Exit,
        ),
    ) + shrinkVertically(
        animationSpec = tween(
            durationMillis = TdayMotionTokens.Durations.Quick,
            easing = TdayMotionTokens.Easings.Exit,
        ),
        shrinkTowards = Alignment.Top,
    )
}
