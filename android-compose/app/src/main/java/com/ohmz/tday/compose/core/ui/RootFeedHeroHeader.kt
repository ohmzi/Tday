package com.ohmz.tday.compose.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.component.TdayPullToRefreshIndicator
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayFloaterAccent
import com.ohmz.tday.compose.ui.theme.TdayTitleIconDayAccent
import com.ohmz.tday.compose.ui.theme.TdayTitleIconNightAccent
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Geometry for the root-feed hero header shared by the Scheduled and Floater
 * home screens.
 *
 * The header is pinned above the feed: the toolbar strip ([BarHeight]) stays put
 * while the feed scrolls out of sight behind it. As the feed scrolls the mark
 * shrinks into the toolbar glyph, the title slides up from its centred hero
 * position to sit beside it, and the search field folds down into a round button
 * to make room for the title.
 *
 * These are the iOS numbers (`RootFeedHeroHeaderMetrics` in
 * `ios-swiftUI/Tday/Core/UI/RootFeedHeroHeader.swift`), solved by search rather
 * than by eye: across every supported width crossed with the longest localised
 * titles, the title never crosses the mark, the search field or the buttons, and
 * the rising feed never clips it. Keep the two platforms in step.
 */
object RootFeedHeroHeaderMetrics {
    val HorizontalPadding = 18.dp
    /**
     * Held above the button row so the bar's content does not start flush
     * against the status bar, which is how it read at zero on every screen.
     *
     * It is web's number (`topInset: 8` in
     * `tday-web/src/components/app/RootFeedHeroHeader.tsx`), not iOS's 2 — the
     * one place the three platforms are deliberately not in step, so do not
     * "correct" it back. What it MUST stay level with is
     * [TdayHeroTitleMetrics.TopInset]: both headers are placed with the same
     * Scaffold padding, so any difference between the two is a root feed's
     * toolbar and a back button sitting at visibly different heights.
     */
    val TopInset = 8.dp
    val BarButtonSize = TdayDimens.FabSize
    val BarButtonSpacing = 8.dp

    /** Always-visible toolbar strip. The feed scrolls out of sight behind it. */
    val BarHeight = TopInset + BarButtonSize

    /** Extra height the hero title block claims while the feed sits at the top. */
    val HeroTitleHeight = 78.dp

    /** Total header height at rest — also the feed's top spacer height. */
    val ExpandedHeight = BarHeight + HeroTitleHeight

    /** Scroll distance over which the hero folds into the toolbar. */
    val CollapseDistance = HeroTitleHeight

    /** Gradient below the strip that dissolves rows as they pass under it. */
    val ContentFadeHeight = 24.dp

    val CompactRowCenterY = TopInset + (BarButtonSize / 2)

    val HeroMarkBox = 72.dp
    val CompactMarkBox = 30.dp
    val MarkLeading = HorizontalPadding + 2.dp
    val HeroMarkCenterY = CompactRowCenterY + 10.dp

    val HeroTitleSize = 40.sp

    /** Nominal line height of the hero title, for centring it vertically. */
    val HeroTitleLineHeight = 48.dp
    const val MaxCompactTitleScale = 0.8f
    const val MinTitleScale = 0.5f
    val HeroTitleCenterY = BarHeight + (HeroTitleHeight / 2)
    val TitleGap = 8.dp

    /**
     * The field's trailing edge is fixed just inside the two round buttons, so
     * only its leading edge travels — it folds down into a button in place
     * rather than sliding across the toolbar.
     */
    val SearchTrailingInset = HorizontalPadding + (BarButtonSize * 2) + (BarButtonSpacing * 2)
    val HeroSearchLeading = MarkLeading + HeroMarkBox + BarButtonSpacing
    val SearchIconSlot = 30.dp
    val SearchLeadingPadding = 13.dp

    /** Capsule widths between which the placeholder fades in. */
    val SearchLabelFadeStart = 100.dp
    val SearchLabelFadeEnd = 124.dp

    /** Right-hand breathing room so an ellipsis is not clipped by the capsule cap. */
    val SearchLabelTrailingPadding = 14.dp

    // Pull-to-refresh pill. It flies in from above the top of the content and
    // settles hovering over the title — in front of it, not in place of it.
    val RefreshPillHeight = 58.dp
    val RefreshPillHiddenTop = -(RefreshPillHeight + 28.dp)
    val RefreshPillRestingTop = HeroTitleCenterY - (RefreshPillHeight / 2)

    /**
     * Fraction of the pull over which the pill completes its travel. It leads
     * deliberately: by the time the feed has begun to move at all the pill is
     * most of the way down, and it eases to a stop rather than arriving hard.
     */
    const val PillLeadFraction = 0.45f

    // Staggered curve endpoints, as a fraction of [CollapseDistance]. Flatter
    // easing widens the collision-free set, which is why each leg runs this
    // long: cubic admitted 98 endpoint combinations, quintic 187, septic 255.
    const val MarkCollapseEnd = 0.65f
    const val SearchCollapseEnd = 0.45f
    const val TitleTravelEnd = 0.50f

    /**
     * Septic (7th-order) smootherstep over `[0, end]`: `35t^4-84t^5+70t^6-20t^7`.
     *
     * Its derivative is `140t^3(1-t)^3`, so the first three derivatives are all
     * zero at both ends — one order flatter than quintic, two flatter than the
     * usual cubic smoothstep. That is what takes the sting out of the start and
     * the stop; the peak is correspondingly quicker so the middle of the morph
     * does not turn sluggish in exchange.
     */
    fun stagger(progress: Float, end: Float): Float {
        if (end <= 0f) return if (progress > 0f) 1f else 0f
        val t = (progress / end).coerceIn(0f, 1f)
        return t * t * t * t * (35f + (t * (-84f + (t * (70f - (20f * t))))))
    }

    fun lerp(from: Dp, to: Dp, fraction: Float): Dp = from + ((to - from) * fraction)

    fun lerp(from: Float, to: Float, fraction: Float): Float = from + ((to - from) * fraction)

    /**
     * Fit-to-space caps for both ends of the title morph. A long localised title
     * would otherwise sit under the mark while centred, and under the search
     * button once docked beside it.
     *
     * Returns `hero to compact`.
     */
    fun titleScales(titleWidth: Dp, availableWidth: Dp): Pair<Float, Float> {
        if (titleWidth <= 0.dp) return 1f to MaxCompactTitleScale

        val heroRoom = availableWidth - (HeroSearchLeading * 2)
        val hero = (heroRoom / titleWidth).coerceIn(MinTitleScale, 1f)

        val compactRoom = (availableWidth - SearchTrailingInset - BarButtonSize) -
            (MarkLeading + CompactMarkBox + TitleGap) - TitleGap
        // coerceIn throws when max < min, and MaxCompactTitleScale * hero can dip
        // below MinTitleScale on a narrow screen.
        val compactCeiling = maxOf(MinTitleScale, MaxCompactTitleScale * hero)
        val compact = (compactRoom / titleWidth).coerceIn(MinTitleScale, compactCeiling)

        return hero to minOf(compact, hero)
    }
}

/** Which glyph the header leads with. */
enum class RootFeedHeroMark {
    /** Sun by day, moon by night — the Scheduled feed. */
    TimeOfDay,

    /**
     * The Floater feed's leaf, drawn exactly as RootFeedDock draws its floater
     * tab so the two read as one mark.
     */
    FloaterLeaf,
}

/**
 * How long the time-of-day mark may be stale for. Not a motion value and not on the
 * ladder: nothing moves when it fires. It is a plain minute because that is the
 * period iOS gives the `TimelineView` it reads the same glyph off.
 *
 * `internal` so a JVM test can hold that minute against the other two clients'. A
 * cadence only agrees with anything from outside the file that writes it.
 */
internal const val MARK_CLOCK_TICK_MS = 60_000L

/**
 * How long the caret and the keyboard hold off while the capsule grows — not a token
 * — see docs/motion.md.
 *
 * One client, one site, and no second caller to agree with. It is read against
 * [SearchField]'s own [TdayMotionTokens.Durations.Emphasis] morph rather than against
 * the ladder, and it sits just inside that morph deliberately: the caret and the
 * keyboard arrive as the field finishes arriving, where waiting the full rung would
 * leave a beat of grown, empty, unfocused field with nothing happening in it.
 *
 * [scaledDelay] and not `delay`, because that morph is the whole of what it is
 * waiting for: with animations off the field is already full width on the first
 * frame, and a wait left standing in front of it is a keyboard that takes a third of
 * a second to arrive for no reason the user can see.
 */
private const val SEARCH_FOCUS_SETTLE_MS = 300L

/**
 * Whether an hour of the day falls in the band the sun glyph covers.
 *
 * Split off from [isDaytimeNow] rather than inlined into it because the band is the only
 * part of this that can be wrong, and `Calendar.getInstance()` leaves no seam to push a 5
 * or an 18 through: a boundary this shape is worth a test and was not reachable by one.
 *
 * @param hour An hour of the day, as [Calendar.HOUR_OF_DAY] reports it.
 * @return Whether that hour is a daytime one.
 */
internal fun isDaytimeHour(hour: Int): Boolean = hour in 6..17

/**
 * Whether the wall clock says it is daytime right now. Read at each tick rather than
 * once, which is the whole of the fix below.
 *
 * @return Whether the current hour falls in the daytime band the sun glyph covers.
 */
private fun isDaytimeNow(): Boolean =
    isDaytimeHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

/**
 * Whether the time-of-day mark should be drawing a sun or a moon, re-read as the
 * clock moves.
 *
 * The hour used to be sampled inside a keyless `remember`, which on a header that is
 * never torn down means once per process: a session opened in the afternoon kept the
 * sun up all evening. iOS reads the same glyph off
 * `TimelineView(.periodic(from: .now, by: 60))`, so this polls on the same cadence
 * from the same unaligned start and lands on the same minute.
 *
 * Nothing here animates, and nothing should. The glyph turns over once a day while
 * nobody is watching the header, and a crossfade would be a motion whose only effect
 * is to point at a change that carries nothing.
 *
 * @param active Whether this header's mark is the time-of-day one at all.
 * @return Whether the current hour is a daytime one.
 */
@Composable
private fun rememberRootFeedIsDaytime(active: Boolean): Boolean {
    var isDaytime by remember { mutableStateOf(isDaytimeNow()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            delay(MARK_CLOCK_TICK_MS)
            isDaytime = isDaytimeNow()
        }
    }
    return isDaytime
}

/**
 * A tween on [durationMillis] and the unmarked curve, or no tween at all where the
 * platform has animation switched off.
 *
 * Every motion in this header is a state change rather than a gesture, so they share
 * one curve and differ only in rung. The reduced-motion branch snaps to the target
 * rather than skipping the write: a control left half-faded, or a capsule left at a
 * width between its two, reads as a broken render and not as deliberate stillness
 * (`docs/motion.md`'s fifth idiom rule).
 *
 * @param durationMillis The rung this motion runs on.
 * @return The spec to hand an `animate*AsState`.
 */
@Composable
private fun headerMotionSpec(durationMillis: Int): AnimationSpec<Float> =
    if (rememberTdayMotionEnabled()) {
        tween(durationMillis = durationMillis, easing = TdayMotionTokens.Easings.Standard)
    } else {
        snap()
    }

/**
 * How visible a toolbar control is while the search field has the row.
 *
 * [TdayMotionTokens.Durations.Quick]: these controls are not the motion — the capsule
 * growing past them is — and something leaving that nobody is meant to watch go is
 * what that rung is for. iOS gets the same fades for free from the
 * `withAnimation(searchMorph)` its tap is wrapped in, so they ride that spring
 * instead; Compose has to name a spec per site, and naming the rung here says what
 * the fade is for rather than restating the capsule's timing.
 *
 * @param visible Whether the control should currently be on screen.
 * @return Its alpha this frame.
 */
@Composable
private fun searchClearAlpha(visible: Boolean): Float {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = headerMotionSpec(TdayMotionTokens.Durations.Quick),
        label = "searchClearAlpha",
    )
    return alpha
}

/**
 * @param collapseProgress raw 0..1 scroll progress, read lazily. Passing a lambda
 *   rather than a Float keeps the snapshot read inside this composable's scope,
 *   so a scroll frame recomposes the header alone and not the whole feed behind
 *   it. Do not pre-read it at the call site. The easing lives here, so pass raw
 *   progress — an animation on top of it would lag the finger.
 */
@Composable
fun RootFeedHeroHeader(
    title: String,
    mark: RootFeedHeroMark,
    collapseProgress: () -> Float,
    searchExpanded: Boolean,
    searchQuery: String,
    searchPlaceholder: String,
    /** Shown in place of [searchPlaceholder] when the folded capsule is too narrow. */
    searchPlaceholderShort: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchClose: () -> Unit,
    onCreateList: () -> Unit,
    onOpenSettings: () -> Unit,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchBarBoundsChanged: (Rect) -> Unit = {},
    refreshIsRefreshing: Boolean = false,
    /** 0..1 pull distance, read lazily for the same reason as [collapseProgress]. */
    refreshPullFraction: () -> Float = { 0f },
    /** Holds the pill's bars where they were when the refresh ended. */
    refreshWaveFrozen: Boolean = false,
) {
    val metrics = RootFeedHeroHeaderMetrics
    val colorScheme = MaterialTheme.colorScheme
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val motionScale = rememberTdayMotionScale()
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            scaledDelay(SEARCH_FOCUS_SETTLE_MS, motionScale)
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.ExpandedHeight),
    ) {
        val width = maxWidth
        val progress = collapseProgress().coerceIn(0f, 1f)

        // Opaque toolbar strip. It deliberately swallows touches so rows hidden
        // behind it can't be tapped through.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(metrics.BarHeight)
                .background(colorScheme.background)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        )

        // Rows dissolve into the strip instead of being cut by its edge. Not
        // touch-consuming: rows still visible in the faded band stay tappable.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(metrics.ContentFadeHeight)
                .offset(y = metrics.BarHeight)
                .background(Brush.verticalGradient(listOf(colorScheme.background, Color.Transparent))),
        )

        HeroMark(mark = mark, progress = progress, visible = !searchExpanded)

        HeroTitle(
            title = title,
            progress = progress,
            availableWidth = width,
            searchExpanded = searchExpanded,
            searchHasQuery = searchQuery.isNotBlank(),
            onClick = onScrollToTop,
        )

        val actionsAlpha = searchClearAlpha(visible = !searchExpanded)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = -metrics.HorizontalPadding,
                    y = metrics.CompactRowCenterY - (metrics.BarButtonSize / 2),
                )
                .graphicsLayer { alpha = actionsAlpha },
            horizontalArrangement = Arrangement.spacedBy(metrics.BarButtonSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RootFeedHeaderCircleButton(
                icon = R.drawable.ic_lucide_list_plus,
                contentDescription = stringResource(R.string.action_create_list),
                enabled = !searchExpanded,
                onClick = onCreateList,
            )
            RootFeedHeaderCircleButton(
                icon = R.drawable.ic_lucide_ellipsis,
                contentDescription = stringResource(R.string.action_more),
                enabled = !searchExpanded,
                onClick = onOpenSettings,
            )
        }

        // Drawn last so it hovers in front of the title, and positioned from the
        // header's own origin so it flies down from the top rather than
        // appearing from behind the toolbar. The pull-to-refresh box's built-in
        // indicator is switched off on these screens for exactly that reason —
        // it lives inside the feed, which is painted underneath the header.
        RefreshPill(
            isRefreshing = refreshIsRefreshing,
            pullFraction = refreshPullFraction,
            waveFrozen = refreshWaveFrozen,
        )

        SearchField(
            width = width,
            progress = progress,
            searchExpanded = searchExpanded,
            searchQuery = searchQuery,
            placeholder = searchPlaceholder,
            placeholderShort = searchPlaceholderShort,
            focusRequester = focusRequester,
            onSearchQueryChange = onSearchQueryChange,
            onSearchExpandedChange = onSearchExpandedChange,
            onSearchClose = onSearchClose,
            onBoundsChanged = onSearchBarBoundsChanged,
        )
    }
}

@Composable
private fun BoxScope.RefreshPill(
    isRefreshing: Boolean,
    pullFraction: () -> Float,
    waveFrozen: Boolean,
) {
    val metrics = RootFeedHeroHeaderMetrics
    val fraction = pullFraction().coerceIn(0f, 1f)
    val target = if (isRefreshing) 1f else metrics.stagger(fraction, metrics.PillLeadFraction)
    // Springs rather than tracking the raw fraction. The septic curve already
    // shapes *where* the pill is for a given pull, but the pull itself starts
    // and stops abruptly with the finger; damping it here is what smooths both
    // the descent and the ride back up when the refresh finishes.
    val reveal by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow),
        label = "refreshPillReveal",
    )
    if (reveal <= 0.001f && !waveFrozen) return

    val top = metrics.lerp(metrics.RefreshPillHiddenTop, metrics.RefreshPillRestingTop, reveal)

    TdayPullToRefreshIndicator(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = top)
            .zIndex(4f),
        isRefreshing = isRefreshing,
        distanceFraction = fraction,
        waveFrozen = waveFrozen,
        applyPullTranslation = false,
    )
}

@Composable
private fun BoxScope.HeroMark(
    mark: RootFeedHeroMark,
    progress: Float,
    visible: Boolean,
) {
    val metrics = RootFeedHeroHeaderMetrics
    val collapse = metrics.stagger(progress, metrics.MarkCollapseEnd)
    val box = metrics.lerp(metrics.HeroMarkBox, metrics.CompactMarkBox, collapse)
    val centerY = metrics.lerp(metrics.HeroMarkCenterY, metrics.CompactRowCenterY, collapse)
    val isDaytime = rememberRootFeedIsDaytime(active = mark == RootFeedHeroMark.TimeOfDay)
    val markAlpha = searchClearAlpha(visible)

    val icon: ImageVector = when (mark) {
        RootFeedHeroMark.TimeOfDay -> if (isDaytime) {
            ImageVector.vectorResource(R.drawable.ic_lucide_sun)
        } else {
            ImageVector.vectorResource(R.drawable.ic_lucide_moon)
        }

        RootFeedHeroMark.FloaterLeaf -> ImageVector.vectorResource(R.drawable.ic_lucide_leaf)
    }
    val tint: Color = when (mark) {
        RootFeedHeroMark.TimeOfDay ->
            if (isDaytime) TdayTitleIconDayAccent else TdayTitleIconNightAccent

        RootFeedHeroMark.FloaterLeaf -> TdayFloaterAccent
    }

    // Deliberately not clickable. The header sits above the feed rather than
    // inside it, so anything here that takes a touch is a dead zone for the
    // scroll and pull-to-refresh gesture. The title alone carries the
    // scroll-to-top tap; the mark's box would double that dead zone, over the
    // corner people naturally drag from.
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = metrics.MarkLeading, y = centerY - (box / 2))
            .size(box)
            .graphicsLayer { alpha = markAlpha },
    )
}

@Composable
private fun BoxScope.HeroTitle(
    title: String,
    progress: Float,
    availableWidth: Dp,
    searchExpanded: Boolean,
    searchHasQuery: Boolean,
    onClick: () -> Unit,
) {
    val metrics = RootFeedHeroHeaderMetrics
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    val travel = metrics.stagger(progress, metrics.TitleTravelEnd)
    // The title's vertical travel is deliberately NOT staggered: the feed rises
    // 78dp while the title only rises 67dp, so any delay there lets the first
    // card cut into the title's descenders.
    val drop = metrics.stagger(progress, 1f)

    // Measured, not observed after layout. The offset below is left-edge based,
    // so a width that starts at zero puts the title's left edge on the screen's
    // centre for the first frames of a fresh screen — it then slides left into
    // place, which reads as the title flying in from the right on a tab switch.
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = LocalTextStyle.current.merge(
        TextStyle(fontSize = metrics.HeroTitleSize, fontWeight = FontWeight.ExtraBold),
    )
    val titleWidth = remember(title, titleStyle, density) {
        with(density) { textMeasurer.measure(title, titleStyle).size.width.toDp() }
    }
    val (heroScale, compactScale) = metrics.titleScales(titleWidth, availableWidth)
    val scale = metrics.lerp(heroScale, compactScale, travel)

    val compactCenterX = metrics.MarkLeading + metrics.CompactMarkBox + metrics.TitleGap +
        ((titleWidth * compactScale) / 2)
    val centerX = metrics.lerp(availableWidth / 2, compactCenterX, travel)
    val centerY = metrics.lerp(metrics.HeroTitleCenterY, metrics.CompactRowCenterY, drop)

    // An open field does not by itself hide the title: down in its hero position
    // it sits clear of the toolbar row, so there is nothing to hide it for. It
    // fades as it docks — where it WOULD collide with the expanded field — and
    // goes entirely once a query starts and the results take the screen over.
    val openAlpha = if (searchHasQuery) 0f else 1f - drop
    // Only the open/close STEP is played back, on the same rung as the mark and the
    // two round buttons the field clears out alongside it. `1f - drop` is
    // scroll-derived and has to stay on the finger: a tween over a value the fold
    // rewrites every frame never arrives at the value it was handed, it only trails
    // it by its own length. So the gate is what animates and the fold is multiplied
    // through it — the same split web makes, and the one iOS gets for free, since
    // its `withAnimation(searchMorph)` wraps the `searchExpanded` mutation alone and
    // the scroll that drives `drop` happens outside that transaction.
    val searchGate = searchClearAlpha(visible = !searchExpanded)
    val titleAlpha = openAlpha + ((1f - openAlpha) * searchGate)
    // Hit testing follows the settled state rather than this frame's alpha: a title
    // that is on its way out should not still be taking the tap that scrolls the
    // feed to the top for the length of the fade.
    val visible = !searchExpanded || openAlpha > 0.01f

    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(
                x = centerX - (titleWidth / 2),
                y = centerY - (metrics.HeroTitleLineHeight / 2),
            )
            .graphicsLayer {
                alpha = titleAlpha
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (visible) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        Text(
            text = title,
            fontSize = metrics.HeroTitleSize,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun BoxScope.SearchField(
    width: Dp,
    progress: Float,
    searchExpanded: Boolean,
    searchQuery: String,
    placeholder: String,
    placeholderShort: String,
    focusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchClose: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    val m = RootFeedHeroHeaderMetrics
    val metrics = m
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // The open is a morph and not a swap: the capsule travels out of the folded
    // button to the full-width field instead of arriving there in one frame.
    // [TdayMotionTokens.Durations.Emphasis], because what changes is where it is and
    // how big it is — geometry rather than importance decides that rung
    // (`docs/motion.md`'s second idiom rule).
    //
    // The FRACTION is what animates; the geometry is lerped from it. Animating
    // `fieldWidth` and `leadingX` themselves would look like the same thing and is
    // not: both are also scroll-derived, since `restingWidth` is recomputed on every
    // frame of the fold, and an `animateDpAsState` on them would put the whole fold a
    // tween behind the finger. This way the scroll path stays instant and only the
    // open moves.
    val openFraction by animateFloatAsState(
        targetValue = if (searchExpanded) 1f else 0f,
        animationSpec = headerMotionSpec(TdayMotionTokens.Durations.Emphasis),
        label = "searchOpenFraction",
    )

    val collapse = metrics.stagger(progress, metrics.SearchCollapseEnd)
    val trailingX = width - metrics.SearchTrailingInset
    val heroWidth = maxOf(metrics.BarButtonSize, trailingX - metrics.HeroSearchLeading)
    val restingWidth = metrics.lerp(heroWidth, metrics.BarButtonSize, collapse)
    val expandedWidth = maxOf(metrics.BarButtonSize, width - (metrics.HorizontalPadding * 2))
    val fieldWidth = metrics.lerp(restingWidth, expandedWidth, openFraction)
    val leadingX = metrics.lerp(
        trailingX - restingWidth,
        metrics.HorizontalPadding,
        openFraction,
    )
    // The label answers to the folded width alone. Fading it out again on the way
    // open would be a second departure over the top of the one the resting overlay
    // is already playing.
    val labelAlpha = ((restingWidth - metrics.SearchLabelFadeStart) /
        (metrics.SearchLabelFadeEnd - metrics.SearchLabelFadeStart)).coerceIn(0f, 1f)
    // One value rather than two animations: the two overlays have to sum to 1 through
    // the whole crossfade, or the card's own fill shows through the middle of it.
    val restingAlpha = searchClearAlpha(visible = !searchExpanded)
    val capsuleShape = RoundedCornerShape(metrics.BarButtonSize / 2)

    Card(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = leadingX, y = metrics.CompactRowCenterY - (metrics.BarButtonSize / 2))
            .width(fieldWidth)
            .height(metrics.BarButtonSize)
            .zIndex(2f)
            .onGloballyPositioned { coordinates -> onBoundsChanged(coordinates.boundsInRoot()) },
        onClick = { if (!searchExpanded) onSearchExpandedChange(true) },
        shape = capsuleShape,
        // Same fill and lift as the two circles it sits beside — it folds down
        // into one of them, so anything else makes the fold change material
        // half way through.
        colors = CardDefaults.cardColors(containerColor = tdayBarButtonContainerColor()),
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.BarButtonElevation,
            pressedElevation = 0.dp,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(capsuleShape)) {
            // Resting state. The glyph stays pinned at SearchLeadingPadding,
            // which centres it once the capsule is a round button, while the
            // placeholder simply runs off the end and is clipped.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = metrics.SearchLeadingPadding)
                    .graphicsLayer { alpha = restingAlpha },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(metrics.SearchIconSlot), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_search),
                        contentDescription = stringResource(R.string.action_search),
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                // The long placeholder gives way to the short word rather than
                // being chopped mid-word; the short one still ellipsises if even
                // it cannot fit.
                val labelRoom = restingWidth - m.SearchLeadingPadding - m.SearchIconSlot -
                    m.SearchLabelTrailingPadding
                // Measured, not laid out: writing state from a layout block to
                // size a sibling is the kind of thing that bites later.
                val labelStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                )
                val longLabelWidth = remember(placeholder, labelStyle, density) {
                    with(density) {
                        textMeasurer.measure(placeholder, labelStyle).size.width.toDp()
                    }
                }
                val showLong = labelRoom >= longLabelWidth
                Text(
                    text = if (showLong) placeholder else placeholderShort,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .graphicsLayer { alpha = labelAlpha },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
                    .graphicsLayer { alpha = 1f - restingAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_search),
                    contentDescription = null,
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
                BasicTextField(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    enabled = searchExpanded,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    cursorBrush = SolidColor(colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (searchQuery.isBlank()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                RootFeedHeaderCircleButton(
                    icon = R.drawable.ic_lucide_x,
                    contentDescription = stringResource(R.string.action_close_search),
                    compact = true,
                    enabled = searchExpanded,
                    onClick = onSearchClose,
                )
            }
        }
    }
}

@Composable
private fun RootFeedHeaderCircleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val size = if (compact) 30.dp else RootFeedHeroHeaderMetrics.BarButtonSize

    Card(
        modifier = Modifier
            .size(size)
            .tdayPressable(interactionSource, scale = TdayMotionTokens.PressScales.Bar),
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            // The compact one is the close button INSIDE the expanded search
            // capsule, so it stays transparent: filling and lifting it would
            // put a second raised surface on top of the one it sits in.
            containerColor = if (compact) Color.Transparent else tdayBarButtonContainerColor(),
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (compact) 0.dp else TdayDimens.BarButtonElevation,
            pressedElevation = 0.dp,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = if (compact) {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                } else {
                    colorScheme.onSurface
                },
                modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            )
        }
    }
}
