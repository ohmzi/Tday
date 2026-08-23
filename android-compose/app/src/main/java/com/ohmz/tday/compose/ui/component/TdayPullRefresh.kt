package com.ohmz.tday.compose.ui.component

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayTodayBlue
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private const val RefreshBarCount = 5
private const val RefreshMinVisibleMillis = 600L

/**
 * How far the feed itself moves on a pull, and when it starts.
 *
 * The pill and the content are deliberately on different curves. The pill leads
 * and travels its full distance early; the content ignores the first third of
 * the pull and then follows by only [ContentPullTravel] — half the 56dp it moved
 * when both were driven by the same fraction.
 */
private val ContentPullTravel = 28.dp
private const val ContentPullStart = 0.35f

/** The pill is deliberately not fully opaque — see the background below. */
private const val RefreshPillOpacity = 0.86f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TdayPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    enabled: Boolean = true,
    /**
     * Set false when the caller draws the pill itself — a screen with a pinned
     * header needs it above that header, which this box cannot reach.
     */
    showsIndicator: Boolean = true,
    /** Reports `(isRefreshing, distanceFraction)` for callers drawing their own pill. */
    onIndicatorStateChange: ((Boolean, Float) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!enabled) {
        Box(
            modifier = modifier,
            contentAlignment = contentAlignment,
            content = content,
        )
        return
    }

    val state = rememberPullToRefreshState()
    // Single source of truth for whether the indicator is shown as refreshing.
    // Raised when a refresh starts (locally triggered or signalled externally)
    // and lowered exactly once, after the external signal ends and the
    // indicator has been visible for a minimum time — never re-raised.
    var displayRefreshing by remember { mutableStateOf(false) }
    var shownAtMs by remember { mutableStateOf(0L) }

    LaunchedEffect(isRefreshing, displayRefreshing) {
        if (isRefreshing) {
            if (!displayRefreshing) {
                displayRefreshing = true
                shownAtMs = SystemClock.uptimeMillis()
            }
        } else if (displayRefreshing) {
            val remaining = RefreshMinVisibleMillis - (SystemClock.uptimeMillis() - shownAtMs)
            if (remaining > 0) {
                delay(remaining)
            }
            displayRefreshing = false
        }
    }

    if (onIndicatorStateChange != null) {
        val fraction = state.distanceFraction
        LaunchedEffect(displayRefreshing, fraction) {
            onIndicatorStateChange(displayRefreshing, fraction)
        }
    }

    PullToRefreshBox(
        isRefreshing = displayRefreshing,
        onRefresh = {
            if (!displayRefreshing) {
                displayRefreshing = true
                shownAtMs = SystemClock.uptimeMillis()
            }
            onRefresh()
        },
        modifier = modifier,
        state = state,
        contentAlignment = contentAlignment,
        indicator = {
            if (showsIndicator) {
                TdayPullToRefreshIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(1f),
                    isRefreshing = displayRefreshing,
                    distanceFraction = state.distanceFraction,
                )
            }
        },
        content = {
            val density = LocalDensity.current
            val contentPull = ((state.distanceFraction - ContentPullStart) /
                    (1f - ContentPullStart)).coerceIn(0f, 1f)
            // Snaps to the finger while pulling; springs when the refresh starts,
            // so the feed bounces back to where it was instead of being held down
            // for the whole request while the pill sits below it.
            val contentTranslation by animateFloatAsState(
                targetValue = if (displayRefreshing) {
                    0f
                } else {
                    with(density) { contentPull * ContentPullTravel.toPx() }
                },
                animationSpec = if (displayRefreshing) {
                    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
                } else {
                    snap()
                },
                label = "pullRefreshContentTranslation",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = contentTranslation },
                contentAlignment = contentAlignment,
                content = content,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TdayPullToRefreshIndicator(
    modifier: Modifier,
    isRefreshing: Boolean,
    distanceFraction: Float,
    /**
     * Whether the pill positions itself from the pull distance. Callers that
     * place it themselves — a pinned header flying it in from the top — turn
     * this off and own the offset.
     */
    applyPullTranslation: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val refreshAccent = TdayTodayBlue
    val visible = isRefreshing || distanceFraction > 0f
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "pullRefreshAlpha",
    )
    val spin = if (isRefreshing) {
        val refreshTransition = rememberInfiniteTransition(label = "pullRefreshSpin")
        val refreshSpin by refreshTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1050, easing = LinearEasing),
            ),
            label = "pullRefreshWavePhase",
        )
        refreshSpin
    } else {
        0f
    }
    val pullProgress = distanceFraction.coerceIn(0f, 1f)
    val sweepTrackWidth =
        TdayDimens.PullRefreshContainerWidth - (TdayDimens.PullRefreshSweepInset * 2)
    val indicatorShape = RoundedCornerShape(TdayDimens.PullRefreshContainerCornerRadius)

    Box(
        modifier = modifier
            .size(
                width = TdayDimens.PullRefreshContainerWidth,
                height = TdayDimens.PullRefreshContainerHeight,
            )
            .drawWithContent {
                clipRect(
                    top = 0f,
                    left = -Float.MAX_VALUE,
                    right = Float.MAX_VALUE,
                    bottom = Float.MAX_VALUE,
                ) {
                    this@drawWithContent.drawContent()
                }
            }
            .graphicsLayer {
                val showElevation = distanceFraction > 0f || isRefreshing
                // Same single fraction-driven animator as the content offset.
                translationY = if (applyPullTranslation) {
                    (distanceFraction * PullToRefreshDefaults.PositionalThreshold.toPx()) - size.height
                } else {
                    0f
                }
                shadowElevation = if (showElevation) TdayDimens.PullRefreshElevation.toPx() else 0f
                shape = indicatorShape
                clip = true
                this.alpha = alpha
            }
            .background(
                // Slightly translucent, so what it flies over stays legible
                // behind it rather than being punched out.
                color = colorScheme.surface.copy(alpha = RefreshPillOpacity),
                shape = indicatorShape,
            )
            .border(
                width = TdayDimens.BorderWidth,
                color = colorScheme.onSurface.copy(alpha = 0.12f),
                shape = indicatorShape,
            )
            .clip(indicatorShape),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Box(
                modifier = Modifier
                    .width(sweepTrackWidth)
                    .height(TdayDimens.PullRefreshSweepHeight)
                    .clip(RoundedCornerShape(TdayDimens.PullRefreshSweepHeight))
                    .background(
                        color = refreshAccent.copy(
                            alpha = if (isRefreshing) {
                                0.18f
                            } else {
                                0.08f + (pullProgress * 0.10f)
                            },
                        ),
                        shape = RoundedCornerShape(TdayDimens.PullRefreshSweepHeight),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TdayDimens.PullRefreshDotSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(RefreshBarCount) { index ->
                        val metrics = refreshBarMetrics(
                            index = index,
                            pullProgress = pullProgress,
                            cycle = spin,
                            isRefreshing = isRefreshing,
                        )
                        Box(
                            modifier = Modifier
                                .width(TdayDimens.PullRefreshDotWidth)
                                .height(metrics.height)
                                .graphicsLayer {
                                    translationY = metrics.verticalOffset.toPx()
                                }
                                .background(
                                    color = refreshAccent.copy(alpha = metrics.alpha),
                                    shape = RoundedCornerShape(TdayDimens.PullRefreshDotWidth),
                                ),
                        )
                    }
                }
            }
        }
    }
}

private data class RefreshBarMetrics(
    val height: Dp,
    val alpha: Float,
    val verticalOffset: Dp,
)

private fun refreshBarMetrics(
    index: Int,
    pullProgress: Float,
    cycle: Float,
    isRefreshing: Boolean,
): RefreshBarMetrics {
    return if (isRefreshing) {
        val phasedCycle = (cycle + (index * 0.11f)) % 1f
        val wave = ((sin(phasedCycle * PI.toFloat() * 2f) + 1f) / 2f)
            .smoothstep()
        val height = TdayDimens.PullRefreshDotMinHeight +
            ((TdayDimens.PullRefreshDotMaxHeight - TdayDimens.PullRefreshDotMinHeight) * wave)
        RefreshBarMetrics(
            height = height,
            alpha = 0.42f + (wave * 0.58f),
            verticalOffset = 0.dp,
        )
    } else {
        val staggerStart = index * 0.11f
        val progress = ((pullProgress - staggerStart) / 0.56f)
            .coerceIn(0f, 1f)
            .smoothstep()
        val height = TdayDimens.PullRefreshDotMinHeight +
            ((TdayDimens.PullRefreshDotMaxHeight - TdayDimens.PullRefreshDotMinHeight) * progress)
        RefreshBarMetrics(
            height = height,
            alpha = 0.32f + (progress * 0.68f),
            verticalOffset = 0.dp,
        )
    }
}

private fun Float.smoothstep(): Float {
    val clamped = coerceIn(0f, 1f)
    return clamped * clamped * (3f - (2f * clamped))
}
