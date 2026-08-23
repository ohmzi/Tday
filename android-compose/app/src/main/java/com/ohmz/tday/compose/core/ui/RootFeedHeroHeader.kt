package com.ohmz.tday.compose.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.text.font.FontWeight
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
    val TopInset = 18.dp
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

    // Pull-to-refresh pill. It flies in from above the top of the content and
    // settles hovering over the title — in front of it, not in place of it.
    val RefreshPillHeight = 58.dp
    val RefreshPillHiddenTop = -(RefreshPillHeight + 28.dp)
    val RefreshPillRestingTop = HeroTitleCenterY - (RefreshPillHeight / 2)

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

@Composable
private fun rememberRootFeedIsDaytime(): Boolean {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    return hour in 6..17
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
) {
    val metrics = RootFeedHeroHeaderMetrics
    val colorScheme = MaterialTheme.colorScheme
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            delay(300)
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
            visible = !searchExpanded,
            onClick = onScrollToTop,
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = -metrics.HorizontalPadding,
                    y = metrics.CompactRowCenterY - (metrics.BarButtonSize / 2),
                )
                .graphicsLayer { alpha = if (searchExpanded) 0f else 1f },
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
        )

        SearchField(
            width = width,
            progress = progress,
            searchExpanded = searchExpanded,
            searchQuery = searchQuery,
            placeholder = searchPlaceholder,
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
) {
    val metrics = RootFeedHeroHeaderMetrics
    val fraction = pullFraction().coerceIn(0f, 1f)
    val reveal = if (isRefreshing) 1f else fraction
    if (reveal <= 0f) return

    val top = metrics.lerp(metrics.RefreshPillHiddenTop, metrics.RefreshPillRestingTop, reveal)

    TdayPullToRefreshIndicator(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = top)
            .zIndex(4f),
        isRefreshing = isRefreshing,
        distanceFraction = fraction,
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
    val isDaytime = rememberRootFeedIsDaytime()

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
            .graphicsLayer { alpha = if (visible) 1f else 0f },
    )
}

@Composable
private fun BoxScope.HeroTitle(
    title: String,
    progress: Float,
    availableWidth: Dp,
    visible: Boolean,
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

    var titleWidth by remember { mutableStateOf(0.dp) }
    val (heroScale, compactScale) = metrics.titleScales(titleWidth, availableWidth)
    val scale = metrics.lerp(heroScale, compactScale, travel)

    val compactCenterX = metrics.MarkLeading + metrics.CompactMarkBox + metrics.TitleGap +
        ((titleWidth * compactScale) / 2)
    val centerX = metrics.lerp(availableWidth / 2, compactCenterX, travel)
    val centerY = metrics.lerp(metrics.HeroTitleCenterY, metrics.CompactRowCenterY, drop)

    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(
                x = centerX - (titleWidth / 2),
                y = centerY - (metrics.HeroTitleLineHeight / 2),
            )
            .graphicsLayer {
                alpha = if (visible) 1f else 0f
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
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val measured = with(density) { coordinates.size.width.toDp() }
                if (measured > 0.dp && measured != titleWidth) {
                    titleWidth = measured
                }
            },
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
    focusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchClose: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    val metrics = RootFeedHeroHeaderMetrics
    val colorScheme = MaterialTheme.colorScheme

    val collapse = metrics.stagger(progress, metrics.SearchCollapseEnd)
    val trailingX = width - metrics.SearchTrailingInset
    val heroWidth = maxOf(metrics.BarButtonSize, trailingX - metrics.HeroSearchLeading)
    val restingWidth = metrics.lerp(heroWidth, metrics.BarButtonSize, collapse)
    val fieldWidth = if (searchExpanded) {
        maxOf(metrics.BarButtonSize, width - (metrics.HorizontalPadding * 2))
    } else {
        restingWidth
    }
    val leadingX = if (searchExpanded) metrics.HorizontalPadding else trailingX - restingWidth
    val labelAlpha = ((restingWidth - metrics.SearchLabelFadeStart) /
        (metrics.SearchLabelFadeEnd - metrics.SearchLabelFadeStart)).coerceIn(0f, 1f)
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
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.26f)),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(capsuleShape)) {
            // Resting state. The glyph stays pinned at SearchLeadingPadding,
            // which centres it once the capsule is a round button, while the
            // placeholder simply runs off the end and is clipped.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = metrics.SearchLeadingPadding)
                    .graphicsLayer { alpha = if (searchExpanded) 0f else 1f },
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
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .graphicsLayer { alpha = labelAlpha },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
                    .graphicsLayer { alpha = if (searchExpanded) 1f else 0f },
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
    val pressed by interactionSource.collectIsPressedAsState()
    val size = if (compact) 30.dp else RootFeedHeroHeaderMetrics.BarButtonSize

    Card(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val pressScale = if (pressed) 0.93f else 1f
                scaleX = pressScale
                scaleY = pressScale
            },
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        interactionSource = interactionSource,
        border = if (compact) null else BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.34f)),
        colors = CardDefaults.cardColors(
            containerColor = if (compact) Color.Transparent else colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (compact) 0.dp else TdayDimens.FabElevation,
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
