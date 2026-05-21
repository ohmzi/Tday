package com.ohmz.tday.compose.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.ui.theme.TdayDimens
import kotlin.math.cos
import kotlin.math.sin

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
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        contentAlignment = contentAlignment,
        indicator = {
            TdayPullToRefreshIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                state = state,
            )
        },
        content = content,
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
    val spin by rememberInfiniteTransition(label = "pullRefreshSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "pullRefreshOrbitRotation",
    )
    val pullProgress = state.distanceFraction.coerceIn(0f, 1f)
    val rotation = if (isRefreshing) spin else pullProgress * 210f
    val sweep = if (isRefreshing) 280f else 65f + (pullProgress * 215f)
    val pulse = if (isRefreshing) {
        0.9f + (sin(Math.toRadians((spin * 2f).toDouble())).toFloat() * 0.1f)
    } else {
        0.86f + (pullProgress * 0.14f)
    }

    Box(
        modifier = modifier
            .pullToRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                shape = RoundedCornerShape(19.dp),
                containerColor = colorScheme.surface,
                elevation = TdayDimens.PullRefreshElevation,
            )
            .size(52.dp)
            .border(
                width = TdayDimens.BorderWidth,
                color = colorScheme.onSurface.copy(alpha = 0.12f),
                shape = RoundedCornerShape(19.dp),
            )
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationY = if (visible) 0f else -12.dp.toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val strokeWidth = 3.2.dp.toPx()
            val orbitDiameter = size.minDimension
            val orbitRadius = orbitDiameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcTopLeft = Offset(center.x - orbitRadius, center.y - orbitRadius)

            drawCircle(
                color = colorScheme.primary.copy(alpha = 0.13f),
                radius = orbitRadius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
            drawArc(
                color = colorScheme.primary,
                startAngle = rotation - 90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = Size(orbitDiameter, orbitDiameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            val dotAngle = Math.toRadians((rotation - 90f + sweep).toDouble())
            val dotCenter = Offset(
                x = center.x + (cos(dotAngle).toFloat() * orbitRadius),
                y = center.y + (sin(dotAngle).toFloat() * orbitRadius),
            )
            drawCircle(
                color = colorScheme.primary,
                radius = 3.4.dp.toPx() * pulse,
                center = dotCenter,
            )
        }
    }
}
