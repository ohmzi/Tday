package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.theme.TdayDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * Geometry for the screen header used by every titled page that is not a root
 * feed: the page's own glyph in a tinted circle with the title beneath it,
 * scrolling up behind a pinned toolbar as the page moves.
 *
 * The block does NOT collapse its own height. It used to, which is why the body
 * content rode up the instant you scrolled and the mark appeared to shrink away
 * rather than travel. It is ordinary scrolling content now — [tdayHeroTitleItem]
 * for a `LazyColumn`, [TdayHeroTitleBlock] for a `verticalScroll` column — and
 * only [TdayHeroToolbar] is pinned, exactly as iOS lays it out.
 *
 * The numbers are iOS's (`TodoTimelineMetrics` in
 * `Feature/Todos/TodoListScreen.swift`); keep the three platforms in step.
 */
object TdayHeroTitleMetrics {
    val HorizontalPadding = 18.dp
    val ToolbarHeight = 56.dp

    val MarkBox = 96.dp
    val MarkGlyph = 44.dp
    /** Gap between the toolbar and the circle. */
    val MarkTopGap = 8.dp
    /** Gap between the circle and the title beneath it. */
    val TitleTopGap = 18.dp
    val TitleSize = 32.sp
    val TitleLineHeight = 40.dp
    val HeroBottomGap = 0.dp

    /** Height of the block that scrolls away, and so the distance it travels. */
    val HeroHeight = MarkTopGap + MarkBox + TitleTopGap + TitleLineHeight + HeroBottomGap

    /**
     * The mark goes first and is gone well before the title reaches the toolbar,
     * so the two never occupy the same space on the way past each other.
     */
    const val MarkFadeEnd = 0.45f
    const val MarkScaleFrom = 0.85f

    /**
     * The handoff. The block's title holds its ground for most of the travel,
     * fades out as it reaches the bar while lifting, and the bar's copy — the
     * same size, so the name never changes shape — fades in from just below,
     * rising and scaling the last 1.5% up. One is at zero exactly where the
     * other starts, so there is never a moment with two titles nor one with
     * none.
     */
    const val HeroTitleFadeStart = 0.60f
    const val HeroTitleFadeEnd = 0.82f
    val HeroTitleLift = 14.dp
    const val DockedTitleRevealStart = 0.82f
    val DockedTitleRise = 10.dp
    const val DockedTitleScaleFrom = 0.985f

    /** Band below the toolbar that dissolves content as it passes under it. */
    val ContentFadeHeight = 24.dp

    /**
     * Clear space kept under the block, so that when the title has finished
     * docking the first row comes to rest BELOW the fade band rather than with
     * its top edge dissolved into it. Deliberately not part of [HeroHeight]: it
     * moves the content down without changing when the title arrives.
     */
    val SettledContentGap = ContentFadeHeight

    /**
     * How quickly the fade band comes in. Off at rest, because there is nothing
     * passing under the toolbar for it to dissolve and it would only veil the
     * top of the mark.
     */
    const val ContentFadeGain = 8f

    fun lerp(from: Dp, to: Dp, fraction: Float): Dp = from + ((to - from) * fraction)
}

/** Septic smootherstep over an arbitrary window, for the legs that do not start at 0. */
private fun staggerRange(progress: Float, start: Float, end: Float): Float =
    if (end <= start) {
        if (progress >= end) 1f else 0f
    } else {
        RootFeedHeroHeaderMetrics.stagger((progress - start).coerceAtLeast(0f), end - start)
    }

/**
 * How far through the collapse a screen is, read from where it has actually been
 * scrolled rather than from scroll this header has eaten.
 *
 * `progress` is a lambda so a scroll frame invalidates only the nodes that read
 * it, never the screen that owns it — the same reason [RootFeedHeroHeader] takes
 * one.
 */
class TdayHeroTitleCollapse internal constructor(
    val progress: () -> Float,
)

/**
 * The settle spec.
 *
 * A spring, not an ease. `FastOutSlowInEasing` starts slow, so the block stood
 * still for a beat after the finger left and then drifted into place — the
 * opposite of elastic. iOS snaps with a UIView spring at 0.92 damping
 * (`VerticalScrollSnapObserver` in `UI/Component/PullToRefresh.swift`); this is
 * the same shape. The visibility threshold is in pixels so it stops when it has
 * visibly stopped rather than chasing hundredths of one.
 */
private val SettleSpring = spring<Float>(
    dampingRatio = 0.9f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = 0.5f,
)

/**
 * Frames the container must hold still before the settle is allowed to run.
 *
 * A fling is a SECOND scroll session: `scrollable` closes the drag's session the
 * moment the finger lifts and only then launches the fling's, so
 * `isScrollInProgress` dips false in between. iOS has the same gap and polls
 * until the scroll view is neither dragging nor decelerating; this is that poll.
 */
private const val SettleQuietFrames = 3

/** Below this the container has not moved enough to have a direction. */
private const val SettleDirectionEpsilonPx = 0.2f

/** Only the sign is read; this clears [snapTitleCollapsePx]'s own deadband. */
private const val SettleDirectionVelocity = 2f

/**
 * Watches one scroll container and settles the collapse every time it comes to
 * rest part-way, so the block is never left half collapsed.
 *
 * @param offsetPx how far the container has been scrolled, in pixels.
 * @param canCollapseFully false when the screen is too short to finish, so the
 *   block goes back rather than stranding.
 * @param settleBy scrolls the container by a delta, returning when it arrives.
 */
private suspend fun runHeroTitleSettle(
    isScrolling: () -> Boolean,
    offsetPx: () -> Float,
    collapsePx: Float,
    canCollapseFully: () -> Boolean,
    settleBy: suspend (delta: Float) -> Unit,
) {
    while (true) {
        snapshotFlow(isScrolling).first { it }

        val startedAt = offsetPx()
        var previous = startedAt
        var lastStep = 0f
        var quietFrames = 0
        while (quietFrames < SettleQuietFrames) {
            withFrameNanos { }
            val now = offsetPx()
            if (now != previous) {
                lastStep = now - previous
                previous = now
            }
            quietFrames = if (isScrolling()) 0 else quietFrames + 1
        }

        val from = offsetPx()
        if (from <= 0f || from >= collapsePx) continue

        // Direction beats the midpoint, which is what carries a short flick
        // upwards all the way into the bar instead of dropping the title back
        // where it started. The last step the content actually took decides;
        // the whole gesture's travel breaks the tie when it ended dead still.
        // `snapTitleCollapsePx` reads a downward-positive velocity while the
        // scroll offset grows as content rises, hence the flipped sign.
        val travelled = if (abs(lastStep) > SettleDirectionEpsilonPx) lastStep else from - startedAt
        val direction = when {
            travelled > SettleDirectionEpsilonPx -> -SettleDirectionVelocity
            travelled < -SettleDirectionEpsilonPx -> SettleDirectionVelocity
            else -> 0f
        }
        val target = if (canCollapseFully()) {
            snapTitleCollapsePx(from, collapsePx, velocityY = direction)
        } else {
            0f
        }

        try {
            settleBy(target - from)
        } catch (interrupted: CancellationException) {
            // Someone outbid us for the scroll mutex — the user grabbing the
            // list again, or the list's own fling. Compose reports that by
            // cancelling the scope the scroll ran in, and letting that through
            // used to unwind this loop for good: one flick and the screen never
            // settled again. Our own cancellation still has to win, so the
            // context is re-checked rather than the exception swallowed whole.
            currentCoroutineContext().ensureActive()
        }
    }
}

@Composable
fun rememberLazyListHeroTitleCollapse(
    listState: LazyListState,
    enabled: Boolean = true,
): TdayHeroTitleCollapse {
    val collapsePx = with(LocalDensity.current) { TdayHeroTitleMetrics.HeroHeight.toPx() }

    // Settle on release: the block goes all the way up or all the way back
    // down, never resting half-collapsed. It is the list that moves now, so it
    // is the list this scrolls — which is also what makes it feel elastic.
    LaunchedEffect(listState, collapsePx, enabled) {
        if (!enabled) return@LaunchedEffect
        runHeroTitleSettle(
            isScrolling = { listState.isScrollInProgress },
            // Past the hero item there is no block left to collapse, so it
            // reads as finished rather than as the next item's own offset.
            offsetPx = {
                if (listState.firstVisibleItemIndex > 0) {
                    collapsePx
                } else {
                    listState.firstVisibleItemScrollOffset.toFloat()
                }
            },
            collapsePx = collapsePx,
            canCollapseFully = { listState.canScrollForward },
            settleBy = { delta -> listState.animateScrollBy(delta, SettleSpring) },
        )
    }

    return remember(listState, collapsePx, enabled) {
        TdayHeroTitleCollapse(
            progress = {
                when {
                    !enabled -> 0f
                    // `firstVisibleItemScrollOffset` is relative to whichever item
                    // is first, so it resets to 0 the moment the hero recycles.
                    // Without this the header would spring open mid-list.
                    listState.firstVisibleItemIndex > 0 -> 1f
                    else -> (listState.firstVisibleItemScrollOffset / collapsePx).coerceIn(0f, 1f)
                }
            },
        )
    }
}

@Composable
fun rememberScrollHeroTitleCollapse(
    scrollState: androidx.compose.foundation.ScrollState,
    enabled: Boolean = true,
): TdayHeroTitleCollapse {
    val collapsePx = with(LocalDensity.current) { TdayHeroTitleMetrics.HeroHeight.toPx() }

    LaunchedEffect(scrollState, collapsePx, enabled) {
        if (!enabled) return@LaunchedEffect
        runHeroTitleSettle(
            isScrolling = { scrollState.isScrollInProgress },
            offsetPx = { scrollState.value.toFloat() },
            collapsePx = collapsePx,
            canCollapseFully = { scrollState.maxValue >= collapsePx },
            settleBy = { delta -> scrollState.animateScrollBy(delta, SettleSpring) },
        )
    }

    return remember(scrollState, collapsePx, enabled) {
        TdayHeroTitleCollapse(
            progress = {
                if (!enabled) 0f else (scrollState.value / collapsePx).coerceIn(0f, 1f)
            },
        )
    }
}

/**
 * The pinned toolbar. Drawn as an overlay over the scroll container rather than
 * in the Scaffold's `topBar` slot, so the content passes behind it instead of
 * starting below it.
 *
 * @param collapseProgress raw 0..1 scroll progress. Pass the unanimated value —
 *   the easing lives here, matching the root feeds' header.
 */
@Composable
fun TdayHeroToolbar(
    title: String,
    collapseProgress: () -> Float,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val m = TdayHeroTitleMetrics
    val colorScheme = MaterialTheme.colorScheme
    val resolvedTitleColor = titleColor ?: colorScheme.onBackground
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxWidth()) {
        // Drawn FIRST, and offset below the bar rather than stacked after it:
        // as a Column sibling it painted over the back button's shadow and cut
        // it off in a straight line at the bar's edge. Dissolves content as it
        // passes under the bar, instead of it being clipped there.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(m.ContentFadeHeight)
                .offset(y = m.ToolbarHeight)
                .graphicsLayer {
                    alpha = (collapseProgress() * m.ContentFadeGain).coerceIn(0f, 1f)
                }
                .background(
                    Brush.verticalGradient(
                        listOf(colorScheme.background, colorScheme.background.copy(alpha = 0f)),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Opaque, so the block genuinely disappears behind it rather
                // than showing through.
                .background(colorScheme.background)
                .height(m.ToolbarHeight)
                .padding(horizontal = m.HorizontalPadding),
        ) {
            if (onBack != null) {
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    TdayHeroBackButton(
                        contentDescription = backContentDescription.orEmpty(),
                        onClick = onBack,
                    )
                }
            }

            // The bar's copy of the title. Only ever visible once the block's
            // own copy has gone, so the two never read as two titles at once.
            //
            // The line box is trimmed to the glyphs and centred inside it, so
            // what gets centred against the back button is the text you can see
            // rather than the font's ascent and descent — a 32sp face carries
            // enough of both to sit visibly high otherwise.
            Text(
                text = title,
                fontSize = m.TitleSize,
                lineHeight = m.TitleSize,
                style = LocalTextStyle.current.merge(
                    TextStyle(
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                ),
                fontWeight = FontWeight.ExtraBold,
                color = resolvedTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        val reveal = staggerRange(
                            collapseProgress().coerceIn(0f, 1f),
                            m.DockedTitleRevealStart,
                            1f,
                        )
                        alpha = reveal
                        translationY = with(density) { m.DockedTitleRise.toPx() } * (1f - reveal)
                        val s = m.DockedTitleScaleFrom + (1f - m.DockedTitleScaleFrom) * reveal
                        scaleX = s
                        scaleY = s
                    },
            )

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * The block that scrolls away: the page's glyph in a tinted circle, the title
 * beneath it, and the toolbar's own height reserved above so the block starts
 * below the bar rather than behind it.
 */
@Composable
fun TdayHeroTitleBlock(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    collapseProgress: () -> Float,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
) {
    val m = TdayHeroTitleMetrics
    val colorScheme = MaterialTheme.colorScheme
    val resolvedTitleColor = titleColor ?: colorScheme.onBackground
    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxWidth()) {
        // The bar's own height, so the block begins where the bar ends. It is
        // inside the scrolling content, which is what lets the block travel
        // behind the bar. No status-bar inset — the caller's Scaffold padding
        // already carries it, and counting it twice starts the block low.
        Spacer(modifier = Modifier.height(m.ToolbarHeight))

        Spacer(modifier = Modifier.height(m.MarkTopGap))
        // A flat glyph on a flat disc read as a utility icon rather than as a
        // page mark. The wash is a gradient, with an oversized echo of the same
        // glyph bleeding out of the bottom-right — the motif the category tiles
        // already use for their watermarks.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(m.MarkBox)
                .graphicsLayer {
                    val fade = 1f - RootFeedHeroHeaderMetrics.stagger(
                        collapseProgress().coerceIn(0f, 1f),
                        m.MarkFadeEnd,
                    )
                    alpha = fade
                    val s = m.MarkScaleFrom + ((1f - m.MarkScaleFrom) * fade)
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.24f),
                            accentColor.copy(alpha = 0.07f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.17f),
                modifier = Modifier.size(108.dp).offset(x = 22.dp, y = 26.dp),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(m.MarkGlyph),
            )
        }

        Spacer(modifier = Modifier.height(m.TitleTopGap))
        Text(
            text = title,
            fontSize = m.TitleSize,
            fontWeight = FontWeight.ExtraBold,
            color = resolvedTitleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val gone = staggerRange(
                        collapseProgress().coerceIn(0f, 1f),
                        m.HeroTitleFadeStart,
                        m.HeroTitleFadeEnd,
                    )
                    alpha = 1f - gone
                    // Leaves a little faster than the finger, so the handoff
                    // reads as one title moving rather than two cross-fading.
                    translationY = -with(density) { m.HeroTitleLift.toPx() } * gone
                },
        )
        Spacer(modifier = Modifier.height(m.SettledContentGap))
    }
}

/** [TdayHeroTitleBlock] as a `LazyColumn`'s first item. */
fun LazyListScope.tdayHeroTitleItem(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    collapseProgress: () -> Float,
    titleColor: Color? = null,
) {
    item(key = "tday-hero-title", contentType = "tday-hero-title") {
        TdayHeroTitleBlock(
            title = title,
            icon = icon,
            accentColor = accentColor,
            collapseProgress = collapseProgress,
            titleColor = titleColor,
        )
    }
}

/**
 * The fill every circle and capsule that sits in a pinned bar shares — the back
 * button here, and the root feeds' buttons and search capsule.
 *
 * Deliberately not `colorScheme.surface`. Under Material You a dynamic light
 * scheme gives `surface` and `background` the same value, so a bar button
 * painted with the token dissolved into the page and only its hairline border
 * showed. Being a different value from the token also keeps Material's tonal
 * elevation tint off a card carrying [TdayDimens.BarButtonElevation], which was
 * washing the same buttons a shade of the primary on top of that.
 */
@Composable
internal fun tdayBarButtonContainerColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    return if (colorScheme.background.luminance() < 0.5f) {
        colorScheme.surface.copy(alpha = 0.94f)
    } else {
        Color.White.copy(alpha = 0.96f)
    }
}

/** The back affordance these headers lead with. */
@Composable
private fun TdayHeroBackButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Card(
        modifier = Modifier
            .size(TdayDimens.FabSize)
            .graphicsLayer {
                val scale = if (pressed) 0.93f else 1f
                scaleX = scale
                scaleY = scale
            },
        onClick = onClick,
        shape = CircleShape,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = tdayBarButtonContainerColor()),
        // The shared bar-button lift, soft enough that it lands on the bar's
        // own opaque strip rather than smearing across the content sliding
        // behind it — which is what a floating-action-button shadow did here.
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.BarButtonElevation),
    ) {
        Box(modifier = Modifier.size(TdayDimens.FabSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_left),
                contentDescription = contentDescription,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
