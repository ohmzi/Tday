package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val SWIPE_OPEN_VELOCITY_PX_PER_SECOND = -1450f
private const val SWIPE_OPEN_THRESHOLD_FRACTION = 0.32f
private const val SWIPE_MAX_ELASTIC_FRACTION = 1.14f
private const val SWIPE_HINT_MS = 150L
private const val SWIPE_HINT_SETTLE_MS = 360L

@Stable
class TaskSwipeRevealState internal constructor(
    private val revealWidthPx: Float,
    private val hintOffsetPx: Float,
    private val maxElasticDragPx: Float,
) {
    var targetOffsetX by mutableFloatStateOf(0f)
        private set

    var isHinting by mutableStateOf(false)
        private set

    val isOpenOrDragging: Boolean
        get() = targetOffsetX != 0f

    fun dragBy(deltaPx: Float) {
        targetOffsetX = (targetOffsetX + deltaPx).coerceIn(-maxElasticDragPx, 0f)
    }

    fun settle(velocityPxPerSecond: Float) {
        val flingOpen = velocityPxPerSecond < SWIPE_OPEN_VELOCITY_PX_PER_SECOND
        val dragOpen = targetOffsetX < -(revealWidthPx * SWIPE_OPEN_THRESHOLD_FRACTION)
        targetOffsetX = if (flingOpen || dragOpen) -revealWidthPx else 0f
    }

    fun close() {
        targetOffsetX = 0f
    }

    suspend fun playHint() {
        if (isHinting) return
        isHinting = true
        targetOffsetX = -hintOffsetPx
        delay(SWIPE_HINT_MS)
        targetOffsetX = 0f
        delay(SWIPE_HINT_SETTLE_MS)
        isHinting = false
    }

    fun revealProgress(offsetX: Float): Float {
        return (-offsetX / revealWidthPx).coerceIn(0f, 1f)
    }
}

@Composable
fun rememberTaskSwipeRevealState(
    key: Any?,
    revealWidth: Dp = 176.dp,
    hintOffset: Dp = 42.dp,
): TaskSwipeRevealState {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidth.toPx() }
    val hintOffsetPx = with(density) {
        hintOffset.toPx().coerceAtMost(revealWidthPx * 0.24f)
    }
    val maxElasticDragPx = revealWidthPx * SWIPE_MAX_ELASTIC_FRACTION

    return remember(key, revealWidthPx, hintOffsetPx, maxElasticDragPx) {
        TaskSwipeRevealState(
            revealWidthPx = revealWidthPx,
            hintOffsetPx = hintOffsetPx,
            maxElasticDragPx = maxElasticDragPx,
        )
    }
}

@Composable
fun animateTaskSwipeOffsetAsState(
    state: TaskSwipeRevealState,
    label: String,
): State<Float> {
    return animateFloatAsState(
        targetValue = state.targetOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = label,
    )
}
