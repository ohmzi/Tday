package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity

private const val COLLAPSE_SNAP_THRESHOLD = 0.5f
private const val COLLAPSE_FLING_VELOCITY_THRESHOLD = 120f

@Stable
class CollapsingTitleScrollBehavior internal constructor(
    val collapseProgress: Float,
    val collapsePx: Float,
    val nestedScrollConnection: NestedScrollConnection,
    private val maxCollapsePx: Float,
    private val setCollapsePx: (Float) -> Unit,
) {
    fun collapseFully() {
        setCollapsePx(maxCollapsePx)
    }

    fun expandFully() {
        setCollapsePx(0f)
    }
}

@Composable
fun rememberLazyListCollapsingTitleScrollBehavior(
    listState: LazyListState,
    maxCollapseDistance: Dp,
    enabled: Boolean = true,
    label: String = "titleCollapseProgress",
): CollapsingTitleScrollBehavior {
    return rememberCollapsingTitleScrollBehavior(
        maxCollapseDistance = maxCollapseDistance,
        enabled = enabled,
        isScrollInProgress = listState.isScrollInProgress,
        isContentAtTop = {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        },
        label = label,
    )
}

@Composable
fun rememberScrollCollapsingTitleScrollBehavior(
    scrollState: ScrollState,
    maxCollapseDistance: Dp,
    enabled: Boolean = true,
    label: String = "titleCollapseProgress",
): CollapsingTitleScrollBehavior {
    return rememberCollapsingTitleScrollBehavior(
        maxCollapseDistance = maxCollapseDistance,
        enabled = enabled,
        isScrollInProgress = scrollState.isScrollInProgress,
        isContentAtTop = { scrollState.value == 0 },
        label = label,
    )
}

@Composable
private fun rememberCollapsingTitleScrollBehavior(
    maxCollapseDistance: Dp,
    enabled: Boolean,
    isScrollInProgress: Boolean,
    isContentAtTop: () -> Boolean,
    label: String,
): CollapsingTitleScrollBehavior {
    val density = LocalDensity.current
    val maxCollapsePx = with(density) { maxCollapseDistance.toPx() }
    var collapsePx by rememberSaveable { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember(enabled, maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || maxCollapsePx <= 0f) return Offset.Zero

                val deltaY = available.y
                if (deltaY < 0f) {
                    val previous = collapsePx
                    val next = (previous - deltaY).coerceIn(0f, maxCollapsePx)
                    val consumed = next - previous
                    if (consumed > 0f) {
                        collapsePx = next
                        return Offset(0f, -consumed)
                    }
                    return Offset.Zero
                }

                if (deltaY > 0f) {
                    if (!isContentAtTop()) return Offset.Zero
                    val previous = collapsePx
                    val next = (previous - deltaY).coerceIn(0f, maxCollapsePx)
                    val consumed = previous - next
                    if (consumed > 0f) {
                        collapsePx = next
                        return Offset(0f, consumed)
                    }
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!enabled || maxCollapsePx <= 0f) return Velocity.Zero
                if (available.y > 0f && !isContentAtTop()) return Velocity.Zero

                val snapped = smoothTitleCollapseSnapPx(
                    currentPx = collapsePx,
                    maxPx = maxCollapsePx,
                    velocityY = available.y,
                )
                if (snapped == collapsePx) return Velocity.Zero

                collapsePx = snapped
                return if (available.y == 0f) Velocity.Zero else available
            }
        }
    }

    LaunchedEffect(enabled, isScrollInProgress, collapsePx, maxCollapsePx) {
        if (!enabled ||
            isScrollInProgress ||
            collapsePx <= 0f ||
            collapsePx >= maxCollapsePx
        ) {
            return@LaunchedEffect
        }

        collapsePx = if (isContentAtTop()) {
            smoothTitleCollapseSnapPx(collapsePx, maxCollapsePx)
        } else {
            maxCollapsePx
        }
    }

    val collapseProgressTarget = if (enabled && maxCollapsePx > 0f) {
        (collapsePx / maxCollapsePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val collapseProgress by animateFloatAsState(
        targetValue = collapseProgressTarget,
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = label,
    )

    return CollapsingTitleScrollBehavior(
        collapseProgress = collapseProgress,
        collapsePx = collapsePx,
        nestedScrollConnection = nestedScrollConnection,
        maxCollapsePx = maxCollapsePx,
        setCollapsePx = { nextCollapsePx ->
            collapsePx = nextCollapsePx.coerceIn(0f, maxCollapsePx)
        },
    )
}

private fun smoothTitleCollapseSnapPx(
    currentPx: Float,
    maxPx: Float,
    velocityY: Float = 0f,
): Float {
    if (maxPx <= 0f) return 0f
    val bounded = currentPx.coerceIn(0f, maxPx)
    if (bounded <= 0f || bounded >= maxPx) return bounded
    if (velocityY < -COLLAPSE_FLING_VELOCITY_THRESHOLD) return maxPx
    if (velocityY > COLLAPSE_FLING_VELOCITY_THRESHOLD) return 0f
    return if (bounded / maxPx >= COLLAPSE_SNAP_THRESHOLD) maxPx else 0f
}
