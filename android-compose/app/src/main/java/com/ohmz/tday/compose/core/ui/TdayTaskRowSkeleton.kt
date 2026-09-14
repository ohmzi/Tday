package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.ohmz.tday.compose.R

/**
 * The geometry a task row occupies, named once so that something which is *not* a
 * task row can be exactly its size.
 *
 * These were literals inside `TodayTodoRow` and the check control it leads with,
 * and literals were fine while the row was the only thing drawing them. [TdayTaskRowSkeleton] is the
 * second drawer, and a placeholder whose geometry merely *happens* to match the
 * row is a placeholder that silently stops matching the first time someone
 * re-spaces the row: the feed then lands one frame later at a different height
 * than the thing it replaced, which is the jump the skeleton exists to remove.
 * Sharing the constants makes that impossible rather than unlikely.
 *
 * Only the numbers the skeleton has to reproduce live here. The row's colours,
 * its type weights and its trailing delete control stay where they are — a
 * placeholder has no business naming those, because it deliberately draws none
 * of them.
 */
object TdayTaskRowMetrics {

    /** Between the row's body and the hairline that closes it. */
    val RowSpacing: Dp = 6.dp

    /** Above and below the row's body. */
    val RowVerticalPadding: Dp = 4.dp

    /**
     * The leading check control's touch target. This, not the glyph, is what sets
     * the row's height — so a skeleton that drew only the 24 dp glyph would be a
     * short row wearing the right circle.
     */
    val CheckTargetMinSize: Dp = 48.dp

    /** The check glyph centred inside [CheckTargetMinSize]. */
    val CheckGlyphSize: Dp = 24.dp

    /** Between the check control and the title. */
    val TextColumnStartPadding: Dp = 10.dp

    /** The hairline under each row. */
    val DividerThickness: Dp = 1.dp

    /** How far down `outlineVariant` the hairline is drawn. */
    const val DividerAlpha: Float = 0.58f
}

/**
 * What a task feed draws before it has any tasks to draw.
 *
 * Android had no skeleton. It had the word "Loading" twice, in two type scales —
 * a `surfaceVariant` card on the timeline, a centred `displaySmall` ExtraBold
 * word on Completed — and in both places the real feed then appeared underneath
 * it in a single frame. Two vocabularies for one state, and a pop at the end of
 * each.
 *
 * The replacement is the row's own geometry with the ink taken out: the same
 * [TdayTaskRowMetrics.CheckTargetMinSize] circle, the same weighted text column
 * at the same [TdayTaskRowMetrics.TextColumnStartPadding], bars at the real type
 * scale's own line heights, the same closing hairline. That is the whole point —
 * a placeholder that is the right *shape* means the arriving feed changes only
 * colour, and nothing below it has to move.
 *
 * ## The two specs
 *
 * [handoff] is [TdayMotionTokens.Durations.Enter], and that rung owes an argument,
 * because the mechanism is geometry: the placeholder retracts its own height and
 * three rows of feed travel up through the slot it gives back. The second idiom
 * rule reads geometry as `Emphasis`. `docs/motion.md` writes the exception out
 * where it discusses the one other exit in the tree that does this — *an exit that
 * hands a slot to an arrival answers to that ARRIVAL's length, not to the length
 * of the thing it undoes*. Nothing here takes a new slot: the rows are landing in
 * theirs for the first time, and the only element whose size changes is the one
 * leaving. 320 would make the placeholder outlast the feed it is uncovering, which
 * is the first idiom rule seen from the other side. Web reaches the same rung by
 * the same sentence and says so where the constant is
 * (`TODAY_EARLIER_EXIT_MS = DURATION_MS.enter`).
 *
 * The arrival it answers to is the feed's own, which on Android is
 * [TdayFeedItemMotion.FadeInMillis] — and this deliberately does NOT name it. That
 * 190 is a hand-written literal which `docs/motion.md` settled against the ladder
 * in favour of 200, and which both of the other clients' feed-motion mirrors
 * already name in writing as the value that owes the move. A new site reaching for
 * it would be adding a third.
 *
 * The curve is [TdayMotionTokens.Easings.Standard] rather than `Enter` or `Exit`,
 * for the reason iOS's `AppRootView` writes down about its own crossfade: a
 * hand-over runs both halves off one clock, and neither a decelerate nor an
 * accelerate describes that.
 *
 * [pulse] is [TdayMotionTokens.Durations.Change] on `Reverse`. Change is the rung
 * for something replayed in place with nothing changing position, which is
 * exactly what an alpha that goes nowhere is. `Reverse` and not `Restart`,
 * because a restarting pulse snaps from dim back to bright on one frame — a
 * flicker, not a breath.
 *
 * ## Reduced motion
 *
 * The pulse is deliberately not gated on the preference. `docs/motion/LEDGER.md`
 * exempts spinners and skeleton pulses from the reduced-motion floor on the
 * grounds that they are status with no finished state to draw, and one that has
 * stopped says the work is done when it is not; Android says the same thing here
 * rather than diverging from the other clients on it.
 *
 * What IS answered for is the frozen frame, and that is a different question from
 * whether to gate. An `InfiniteTransition` is a Compose animation nobody wrote
 * code for, so it obeys the `MotionDurationScale` in the recomposer's context —
 * the device's scale, the half that [effectiveMotionScale] says an in-app switch
 * cannot reach. At scale 0 Compose does not leave the value where it found it:
 * `InfiniteTransition` calls `skipToEnd()` on every animation it owns, and that
 * assigns the `TargetBasedAnimation`'s TARGET. A tween running [RestingAlpha] down
 * to [DimmedAlpha] is therefore pinned at 0.45 for the whole load on exactly the
 * devices that asked for less motion — the fifth idiom rule broken twice over: a
 * surface that cannot animate and therefore cannot be read.
 *
 * Turning the tween round is not the fix, only the other half of the same bug. The
 * INITIAL value is what a host that never delivers a frame holds — a screenshot
 * test, a suspended composition — so one orientation is legible when frames stop
 * and the other is legible when the scale is 0, and neither is both. The ends stay
 * bright-to-faint for the frame-starved case, and [frozenAlpha] answers the scale-0
 * one by drawing a constant with no transition composed at all.
 *
 * The hand-over itself IS gated on the preference, at the call site, because that
 * one has a finished state to draw and drawing it immediately is the correct
 * answer.
 */
object TdayTaskRowSkeleton {

    /** How long the skeleton takes to give the feed its slot back. */
    const val HandoffMillis: Int = TdayMotionTokens.Durations.Enter

    /** One breath of the pulse, in one direction. */
    const val PulseMillis: Int = TdayMotionTokens.Durations.Change

    /** The alpha the bars sit at, and the one they are frozen at when nothing runs. */
    const val RestingAlpha: Float = 1f

    /** The far end of the breath. Faint, never invisible. */
    const val DimmedAlpha: Float = 0.45f

    /** How many rows a group draws when the caller has no better guess. */
    const val DefaultRowCount: Int = 3

    /**
     * What to draw instead of the pulse, or `null` while the pulse is still the answer.
     *
     * [motionScale] is the DEVICE's — [rememberSystemMotionScale], not
     * [rememberTdayMotionScale]. The question this asks is not whether the user wants
     * less motion; it is whether Compose is still running the transition at all, and
     * only the system half of the scale reaches an `InfiniteTransition`. See the
     * reduced-motion note above for where Compose leaves one it has stopped, which is
     * the whole reason this is not an identity.
     */
    fun frozenAlpha(motionScale: Float): Float? =
        if (motionScale == 0f) RestingAlpha else null

    /**
     * Generic for the reason [TdayMotionTokens.Springs] and [TdaySheetMotion] give:
     * the hand-over fades and un-sizes at once, so the same rung is wanted over
     * `Float` and over `IntSize` in the same expression.
     */
    fun <T> handoff(): TweenSpec<T> = tween(
        durationMillis = HandoffMillis,
        easing = TdayMotionTokens.Easings.Standard,
    )

    fun pulse(): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = PulseMillis,
            easing = TdayMotionTokens.Easings.Standard,
        ),
        repeatMode = RepeatMode.Reverse,
    )
}

/**
 * Whether the skeleton's lazy item should still be in the list at all.
 *
 * An item kept mounted past its own visibility is the only way an exit gets a node
 * to play on — the guard that removes it removes the transition with it. Kept
 * mounted FOREVER is a different bug, and a `LazyColumn` is where it bites:
 * `Arrangement.spacedBy` is applied per item rather than per drawn pixel, so an
 * item that composes to nothing still costs the feed its gap. The flat modes run
 * at `TimelineDateGroupSpacing`, which means a permanently-mounted placeholder
 * goes on charging 6 dp under the header for the whole of the loaded state the
 * user actually lives in — long after the thing that earned it has gone.
 *
 * So the mount is a window: open while [visible], held open one
 * [TdayTaskRowSkeleton.HandoffMillis] past the frame it drops, closed after. The
 * wait is [scaledDelay] on [rememberTdayMotionScale] and not `delay`, because the
 * exit it is holding the node open for is gated on that same preference at the
 * call site — a wait kept after its motion is removed is the fifth idiom rule
 * broken from the side nobody watches.
 */
@Composable
fun rememberTdayTaskRowSkeletonMounted(visible: Boolean): Boolean {
    val motionScale = rememberTdayMotionScale()
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible, motionScale) {
        if (visible) {
            mounted = true
        } else if (mounted) {
            scaledDelay(TdayTaskRowSkeleton.HandoffMillis.toLong(), motionScale)
            mounted = false
        }
    }
    return mounted
}

/**
 * A feed's worth of skeleton rows.
 *
 * Carries the loading announcement that the two "Loading" texts used to carry by
 * being text. Bars say nothing to TalkBack, so replacing words with shapes would
 * have quietly removed the only thing on screen a screen reader could report —
 * the one way this change could have been a regression. One merged node with one
 * description, announced politely rather than interrupting whatever the user was
 * already being read.
 */
@Composable
fun TdayTaskRowSkeletonGroup(
    modifier: Modifier = Modifier,
    count: Int = TdayTaskRowSkeleton.DefaultRowCount,
) {
    val loadingLabel = stringResource(R.string.label_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = loadingLabel
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        repeat(count) { index ->
            val widths = SkeletonRowWidths[index % SkeletonRowWidths.size]
            TdayTaskRowSkeleton(
                titleWidthFraction = widths.first,
                subtitleWidthFraction = widths.second,
            )
        }
    }
}

/**
 * One skeleton row, built against `TodayTodoRow` — the flat row the loading state
 * actually guards, not the carded variant the non-Today modes use.
 */
@Composable
fun TdayTaskRowSkeleton(
    modifier: Modifier = Modifier,
    titleWidthFraction: Float = SkeletonRowWidths.first().first,
    subtitleWidthFraction: Float = SkeletonRowWidths.first().second,
) {
    val colorScheme = MaterialTheme.colorScheme
    val frozen = TdayTaskRowSkeleton.frozenAlpha(rememberSystemMotionScale())
    // Branching over composing-and-ignoring: at scale 0 the transition's frame loop
    // still asks for every frame it is never going to use, on the one device that
    // asked for less. Not composing it leaves nothing to skip to the wrong end of.
    val pulseAlpha = if (frozen != null) {
        frozen
    } else {
        val transition = rememberInfiniteTransition(label = "taskRowSkeleton")
        val animated by transition.animateFloat(
            initialValue = TdayTaskRowSkeleton.RestingAlpha,
            targetValue = TdayTaskRowSkeleton.DimmedAlpha,
            animationSpec = TdayTaskRowSkeleton.pulse(),
            label = "taskRowSkeletonPulse",
        )
        animated
    }
    val fill = colorScheme.surfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TdayTaskRowMetrics.RowSpacing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = TdayTaskRowMetrics.RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(TdayTaskRowMetrics.CheckTargetMinSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(TdayTaskRowMetrics.CheckGlyphSize)
                        .alpha(pulseAlpha)
                        .background(fill, CircleShape),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = TdayTaskRowMetrics.TextColumnStartPadding),
            ) {
                SkeletonTextBar(
                    style = MaterialTheme.typography.titleMedium,
                    widthFraction = titleWidthFraction,
                    fill = fill,
                    alpha = pulseAlpha,
                )
                SkeletonTextBar(
                    style = MaterialTheme.typography.bodySmall,
                    widthFraction = subtitleWidthFraction,
                    fill = fill,
                    alpha = pulseAlpha,
                )
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(TdayTaskRowMetrics.DividerThickness)
                .background(
                    colorScheme.outlineVariant.copy(alpha = TdayTaskRowMetrics.DividerAlpha),
                ),
        )
    }
}

/**
 * One bar standing in for one line of text.
 *
 * The box is the style's LINE height and the bar inside it is the style's FONT
 * size, which is what keeps this honest in both directions at once: the line box
 * is what the real `Text` will occupy, so the row's height is right, and the bar
 * is roughly what the glyphs will ink, so the gap between two bars is the gap the
 * eye will see between two lines. A bar filling its whole line box would be the
 * correct height and would still read as a solid slab rather than as writing.
 */
@Composable
private fun SkeletonTextBar(
    style: TextStyle,
    widthFraction: Float,
    fill: Color,
    alpha: Float,
) {
    val density = LocalDensity.current
    val lineHeight = spDimension(density, style.lineHeight, FallbackLineHeight)
    val barHeight = spDimension(density, style.fontSize, FallbackBarHeight)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(lineHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .height(barHeight)
                .alpha(alpha)
                .background(fill, RoundedCornerShape(percent = 50)),
        )
    }
}

/**
 * A text dimension in Dp, or the fallback when the theme did not give one in sp.
 *
 * Material3's own type scale specifies both line height and font size in sp, so
 * the fallback is not for the shipping theme — it is for the one that overrides
 * the scale in `em`, where `toDp()` throws rather than mis-measuring. A skeleton
 * that crashes the feed it is standing in for would be a poor trade for four
 * exact pixels.
 */
private fun spDimension(density: Density, value: TextUnit, fallback: Dp): Dp {
    if (!value.isSpecified || value.type != TextUnitType.Sp) return fallback
    return with(density) { value.toDp() }
}

/** `titleMedium`'s shipped line height, for a theme that declines to give one. */
private val FallbackLineHeight: Dp = 24.dp

/** `titleMedium`'s shipped font size, for the same. */
private val FallbackBarHeight: Dp = 16.dp

/**
 * How wide each row's two bars are, cycled by index.
 *
 * Three identical rows read as a pattern; three different ones read as text that
 * has not arrived. The numbers are arbitrary and say so — nothing measures them,
 * and no rung is claimed by them.
 */
private val SkeletonRowWidths: List<Pair<Float, Float>> = listOf(
    0.72f to 0.38f,
    0.54f to 0.46f,
    0.63f to 0.33f,
)
