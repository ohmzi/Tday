package com.ohmz.tday.compose.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.ui.theme.TdayDimens
import kotlin.math.PI
import kotlin.math.sin

private const val RefreshBarCount = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TdayPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val pullProgress = state.distanceFraction.coerceIn(0f, 1f)
    val contentPullProgress = state.distanceFraction.coerceIn(0f, 1.25f)
    val pullContentOffset = TdayDimens.PullRefreshContentOffset * contentPullProgress
    var isPointerDown by remember { mutableStateOf(false) }
    val isUserPulling = isPointerDown && !isRefreshing && !state.isAnimating && pullProgress > 0f
    val contentOffset by animateDpAsState(
        targetValue = if (isUserPulling) pullContentOffset else 0.dp,
        animationSpec = tween(durationMillis = if (isUserPulling) 0 else 220),
        label = "pullRefreshContentOffset",
    )
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    isPointerDown = event.changes.any { it.pressed }
                }
            }
        },
        state = state,
        contentAlignment = contentAlignment,
        indicator = {
            TdayPullToRefreshIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                state = state,
            )
        },
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = contentOffset.toPx()
                    },
                contentAlignment = contentAlignment,
                content = content,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TdayPullToRefreshIndicator(
    modifier: Modifier,
    isRefreshing: Boolean,
    state: PullToRefreshState,
) {
    val colorScheme = MaterialTheme.colorScheme
    val visible = isRefreshing || state.distanceFraction > 0f
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "pullRefreshAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.78f,
        animationSpec = tween(durationMillis = 220),
        label = "pullRefreshScale",
    )
    val refreshingBottomOffset by animateDpAsState(
        targetValue = if (isRefreshing) TdayDimens.PullRefreshRefreshingOffset else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "pullRefreshRefreshingOffset",
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
    val pullProgress = state.distanceFraction.coerceIn(0f, 1f)
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
                val showElevation = state.distanceFraction > 0f || isRefreshing
                val pullBottomOffset =
                    state.distanceFraction * PullToRefreshDefaults.PositionalThreshold.roundToPx()
                translationY = maxOf(
                    pullBottomOffset,
                    refreshingBottomOffset.toPx(),
                ) - size.height
                shadowElevation = if (showElevation) TdayDimens.PullRefreshElevation.toPx() else 0f
                shape = indicatorShape
                clip = true
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = colorScheme.surface,
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
                        color = colorScheme.primary.copy(
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
                                    color = colorScheme.primary.copy(alpha = metrics.alpha),
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
