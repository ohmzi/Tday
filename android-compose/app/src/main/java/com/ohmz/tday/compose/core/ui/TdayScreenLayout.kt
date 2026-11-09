package com.ohmz.tday.compose.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ohmz.tday.shared.layout.LayoutTokens

/**
 * Where a titled screen's content starts, and the slot an empty scene sits in, read
 * off the shared [LayoutTokens] — the same ratios iOS and the web read, so the first
 * row (or the scene standing in for it) lands at the same height on every client.
 *
 * Both are ratios of the FULL screen height. The rule that turns the content ratio
 * into a gap lives here rather than in the shared object because only this client
 * knows its own status-bar inset and its own header height.
 */

/** The full window height, status bar included — the denominator both ratios use. */
@Composable
internal fun rememberTdayScreenHeight(): Dp {
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    // Zero before the first layout pass and in a preview. The configuration height
    // leaves out the system bars on some API levels, which makes it a poor first
    // choice but a fine stand-in for the frames that have nothing better.
    return if (windowHeightPx > 0) {
        with(density) { windowHeightPx.toDp() }
    } else {
        LocalConfiguration.current.screenHeightDp.dp
    }
}

/** The minimum height of the slot an empty scene is centred in. */
@Composable
internal fun tdayEmptySlotHeight(): Dp =
    LayoutTokens.emptySlotHeight(rememberTdayScreenHeight().value.toDouble()).dp

/**
 * The gap to leave under a hero title so the content starts on the shared line.
 *
 * Pure, so a JVM test can hold it against iOS's numbers: the title ends at the
 * status bar plus the pinned toolbar plus the hero block, and the gap is whatever
 * [LayoutTokens.contentGap] says reaches the line from there.
 */
internal fun tdayContentGapFor(screenHeight: Dp, statusBarTop: Dp): Dp {
    val m = TdayHeroTitleMetrics
    val titleBottom = statusBarTop + m.ToolbarHeight + m.HeroHeight
    return LayoutTokens.contentGap(
        screenHeight = screenHeight.value.toDouble(),
        titleBottom = titleBottom.value.toDouble(),
    ).dp
}

/** [tdayContentGapFor] for the window this composable is in. */
@Composable
internal fun tdayContentGap(): Dp {
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    return tdayContentGapFor(rememberTdayScreenHeight(), statusBarTop)
}

/**
 * How far below the top of the Scaffold's content area the content line sits — the
 * top padding for an empty scene drawn as an overlay, so it starts where the first
 * row would have. The Scaffold's own top padding already carries the status bar, so
 * the inset is measured from there.
 */
@Composable
internal fun tdayContentStartInset(): Dp {
    val m = TdayHeroTitleMetrics
    return m.ToolbarHeight + m.HeroHeight + tdayContentGap()
}
