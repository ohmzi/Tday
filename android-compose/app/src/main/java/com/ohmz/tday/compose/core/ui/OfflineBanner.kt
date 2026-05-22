package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.theme.TdayDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OFFLINE_NOTICE_AUTO_DISMISS_MS = 2_000L
private const val OFFLINE_NOTICE_ENTER_DURATION_MS = 220
private const val OFFLINE_NOTICE_EXIT_DURATION_MS = 170
private const val OFFLINE_NOTICE_DRAG_GAIN = 1.16f
private const val OFFLINE_NOTICE_DISMISS_DURATION_MS = 150
private const val OFFLINE_NOTICE_FADE_DISTANCE_DP = 88

@Composable
fun OfflineBanner(
    visible: Boolean,
    pendingMutationCount: Int,
    noticeKey: Long,
    modifier: Modifier = Modifier,
) {
    var isPresented by remember { mutableStateOf(false) }
    var lastPresentedNoticeKey by remember { mutableStateOf(0L) }

    LaunchedEffect(visible, noticeKey) {
        if (!visible) {
            isPresented = false
            return@LaunchedEffect
        }
        if (noticeKey <= lastPresentedNoticeKey) return@LaunchedEffect

        lastPresentedNoticeKey = noticeKey
        isPresented = true
        delay(OFFLINE_NOTICE_AUTO_DISMISS_MS)
        isPresented = false
    }

    AnimatedVisibility(
        visible = isPresented,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = OFFLINE_NOTICE_ENTER_DURATION_MS,
                easing = LinearOutSlowInEasing,
            ),
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = OFFLINE_NOTICE_ENTER_DURATION_MS,
                easing = LinearOutSlowInEasing,
            ),
            initialOffsetY = { -it / 2 },
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = OFFLINE_NOTICE_EXIT_DURATION_MS,
                easing = FastOutLinearInEasing,
            ),
        ) + slideOutVertically(
            animationSpec = tween(
                durationMillis = OFFLINE_NOTICE_EXIT_DURATION_MS,
                easing = FastOutLinearInEasing,
            ),
            targetOffsetY = { -it / 2 },
        ),
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = TdayDimens.ContentPaddingHorizontal, vertical = 10.dp),
    ) {
        OfflineNoticeCard(
            pendingMutationCount = pendingMutationCount,
            onDismiss = { isPresented = false },
        )
    }
}

@Composable
private fun OfflineNoticeCard(
    pendingMutationCount: Int,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val fadeDistancePx = with(LocalDensity.current) { OFFLINE_NOTICE_FADE_DISTANCE_DP.dp.toPx() }
    val containerColor = if (isDark) {
        lerp(colorScheme.surfaceVariant, colorScheme.primary, 0.12f)
    } else {
        lerp(colorScheme.surfaceVariant, colorScheme.surface, 0.46f)
    }
    val iconContainerColor = if (isDark) {
        lerp(colorScheme.primaryContainer, colorScheme.primary, 0.18f)
    } else {
        lerp(colorScheme.primaryContainer, colorScheme.surface, 0.18f)
    }
    val subtitle = when {
        pendingMutationCount == 1 -> stringResource(R.string.offline_banner_pending_one)
        pendingMutationCount > 1 -> stringResource(
            R.string.offline_banner_pending_many,
            pendingMutationCount,
        )

        else -> stringResource(R.string.offline_banner)
    }
    val scope = rememberCoroutineScope()
    val onDismissState by rememberUpdatedState(onDismiss)
    val settleOffsetY = remember { Animatable(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val displayedOffsetY = if (isDragging) dragOffsetY else settleOffsetY.value
    val dragProgress = (-displayedOffsetY / fadeDistancePx).coerceIn(0f, 1f)
    val noticeAlpha = 1f - (dragProgress * 0.5f)
    val noticeScale = 1f - (dragProgress * 0.025f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = displayedOffsetY
                alpha = noticeAlpha
                scaleX = noticeScale
                scaleY = noticeScale
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragOffsetY = settleOffsetY.value
                        isDragging = true
                        scope.launch { settleOffsetY.stop() }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY = (dragOffsetY + (dragAmount.y * OFFLINE_NOTICE_DRAG_GAIN))
                            .coerceAtMost(0f)
                    },
                    onDragCancel = {
                        scope.launch {
                            isDragging = false
                            settleOffsetY.snapTo(dragOffsetY)
                            settleOffsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = OFFLINE_NOTICE_DISMISS_DURATION_MS,
                                    easing = LinearOutSlowInEasing,
                                ),
                            )
                            dragOffsetY = 0f
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            isDragging = false
                            settleOffsetY.snapTo(dragOffsetY)
                            if (dragOffsetY < 0f) {
                                settleOffsetY.animateTo(
                                    targetValue = -size.height.toFloat() * 1.15f,
                                    animationSpec = tween(
                                        durationMillis = OFFLINE_NOTICE_DISMISS_DURATION_MS,
                                        easing = FastOutLinearInEasing,
                                    ),
                                )
                                onDismissState()
                            } else {
                                settleOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = OFFLINE_NOTICE_DISMISS_DURATION_MS,
                                        easing = LinearOutSlowInEasing,
                                    ),
                                )
                            }
                            dragOffsetY = 0f
                        }
                    },
                )
            }
            .clickable { onDismissState() },
        shape = RoundedCornerShape(TdayDimens.RadiusXl),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = TdayDimens.BorderWidth,
            color = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.5f else 0.7f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 8.dp else 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = stringResource(R.string.offline_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
