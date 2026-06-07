package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val TOAST_ENTER_FADE_DURATION_MS = 180
private const val TOAST_EXIT_FADE_DURATION_MS = 140
private const val TOAST_EXIT_SLIDE_DURATION_MS = 180
private const val TOAST_DRAG_GAIN = 1.18f
private const val TOAST_DISMISS_DURATION_MS = 160
private const val TOAST_FADE_DISTANCE_DP = 96
private val TOAST_CORNER_RADIUS = 24.dp
private val TOAST_BOTTOM_PADDING = 88.dp

// Brand accent — the coral used by the overdue tile on the home screen. Non-error
// toasts share it so the toast accent matches the rest of the app; errors keep the
// Material error red so the danger cue survives.
private val TOAST_BRAND_ACCENT = Color(0xFFE06F66)

/** Visual variant of a toast — drives the accent colour and the default icon. */
enum class TdayToastKind { ERROR, SUCCESS, INFO }

data class TdayToastData(
    val id: Long,
    val message: String,
    val kind: TdayToastKind = TdayToastKind.INFO,
    val icon: ImageVector? = null,
    val autoDismissMillis: Long? = null,
    val onTap: (() -> Unit)? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Composable
fun TdayToastHost(
    toast: TdayToastData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(toast?.id, toast?.autoDismissMillis) {
        if (toast == null) return@LaunchedEffect
        val autoDismissMillis = toast.autoDismissMillis ?: return@LaunchedEffect
        kotlinx.coroutines.delay(autoDismissMillis)
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = TOAST_BOTTOM_PADDING),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = TOAST_ENTER_FADE_DURATION_MS,
                    easing = LinearOutSlowInEasing,
                ),
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = TOAST_EXIT_FADE_DURATION_MS,
                    easing = FastOutLinearInEasing,
                ),
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = TOAST_EXIT_SLIDE_DURATION_MS,
                    easing = FastOutLinearInEasing,
                ),
                targetOffsetY = { it / 4 },
            ),
        ) {
            val visibleToast = toast ?: return@AnimatedVisibility
            TdayToastCard(
                toast = visibleToast,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun TdayToastCard(
    toast: TdayToastData,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val fadeDistancePx = with(LocalDensity.current) { TOAST_FADE_DISTANCE_DP.dp.toPx() }
    val containerColor = if (isDark) {
        lerp(colorScheme.surfaceVariant, colorScheme.primary, 0.16f)
    } else {
        lerp(colorScheme.surfaceVariant, colorScheme.primary, 0.10f)
    }
    // The variant colour cue lives in the icon chip + action label; the card surface
    // itself stays neutral, matching the app's card design language.
    val accentColor = when (toast.kind) {
        TdayToastKind.ERROR -> colorScheme.error
        TdayToastKind.SUCCESS -> TOAST_BRAND_ACCENT
        TdayToastKind.INFO -> TOAST_BRAND_ACCENT
    }
    val iconContainerColor = accentColor.copy(alpha = if (isDark) 0.22f else 0.14f)
    val toastIcon = toast.icon ?: when (toast.kind) {
        TdayToastKind.ERROR -> Icons.Rounded.ErrorOutline
        TdayToastKind.SUCCESS -> Icons.Rounded.CheckCircle
        TdayToastKind.INFO -> Icons.Rounded.Info
    }
    val scope = rememberCoroutineScope()
    val onDismissState by rememberUpdatedState(onDismiss)
    val settleOffsetY = remember(toast.id) { Animatable(0f) }
    var dragOffsetY by remember(toast.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(toast.id) { mutableStateOf(false) }
    val tapModifier = toast.onTap?.let { onTap ->
        Modifier.clickable { onTap() }
    } ?: Modifier
    val displayedOffsetY = if (isDragging) dragOffsetY else settleOffsetY.value
    val dragProgress = (displayedOffsetY / fadeDistancePx).coerceIn(0f, 1f)
    val toastAlpha = 1f - (dragProgress * 0.55f)
    val toastScale = 1f - (dragProgress * 0.03f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .graphicsLayer {
                translationY = displayedOffsetY
                alpha = toastAlpha
                scaleX = toastScale
                scaleY = toastScale
            }
            .pointerInput(toast.id) {
                detectDragGestures(
                    onDragStart = {
                        dragOffsetY = settleOffsetY.value
                        isDragging = true
                        scope.launch { settleOffsetY.stop() }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY =
                            (dragOffsetY + (dragAmount.y * TOAST_DRAG_GAIN)).coerceAtLeast(0f)
                    },
                    onDragCancel = {
                        scope.launch {
                            isDragging = false
                            settleOffsetY.snapTo(dragOffsetY)
                            if (dragOffsetY > 0f) {
                                settleOffsetY.animateTo(
                                    targetValue = size.height.toFloat() * 1.15f,
                                    animationSpec = tween(
                                        durationMillis = TOAST_DISMISS_DURATION_MS,
                                        easing = FastOutLinearInEasing,
                                    ),
                                )
                                onDismissState()
                            } else {
                                settleOffsetY.snapTo(0f)
                            }
                            dragOffsetY = 0f
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            isDragging = false
                            settleOffsetY.snapTo(dragOffsetY)
                            if (dragOffsetY > 0f) {
                                settleOffsetY.animateTo(
                                    targetValue = size.height.toFloat() * 1.15f,
                                    animationSpec = tween(
                                        durationMillis = TOAST_DISMISS_DURATION_MS,
                                        easing = FastOutLinearInEasing,
                                    ),
                                )
                                onDismissState()
                            } else {
                                settleOffsetY.snapTo(0f)
                            }
                            dragOffsetY = 0f
                        }
                    },
                )
            }
            .then(tapModifier),
        shape = RoundedCornerShape(TOAST_CORNER_RADIUS),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = 1.dp,
            color = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.52f else 0.72f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 8.dp else 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
                    imageVector = toastIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = accentColor,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                Text(
                    text = toast.message,
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val actionLabel = toast.actionLabel
            val onAction = toast.onAction
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = {
                        onAction()
                        onDismiss()
                    },
                ) {
                    Text(
                        text = actionLabel,
                        color = accentColor,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
