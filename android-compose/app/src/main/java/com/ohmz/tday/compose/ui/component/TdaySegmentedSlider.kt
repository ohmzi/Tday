package com.ohmz.tday.compose.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.ui.theme.TdayTodayBlue

private val TdaySegmentedSliderAccent = TdayTodayBlue

@Composable
fun <T> TdaySegmentedSlider(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = TdaySegmentedSliderAccent,
    label: (T) -> String,
) {
    if (options.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val containerShape = RoundedCornerShape(22.dp)
    val selectorShape = RoundedCornerShape(18.dp)
    val trackColor = colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.76f else 0.68f)
    val trackBorderColor = if (isDarkTheme) {
        colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    } else {
        colorScheme.surface.copy(alpha = 0.72f)
    }
    val selectorContainerColor = if (isDarkTheme) {
        colorScheme.background.copy(alpha = 0.9f)
    } else {
        colorScheme.surface.copy(alpha = 0.98f)
    }
    val selectorBorderColor = if (isDarkTheme) {
        colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
    } else {
        colorScheme.onSurface.copy(alpha = 0.1f)
    }
    val interactionSources = remember(options) {
        List(options.size) { MutableInteractionSource() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(containerShape)
            .background(trackColor, containerShape)
            .border(
                width = 1.dp,
                color = trackBorderColor,
                shape = containerShape,
            )
            .padding(5.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val segmentWidth = maxWidth / options.size
            val pressedStates = interactionSources.map { source ->
                source.collectIsPressedAsState()
            }
            val pressedIndex =
                pressedStates.indexOfFirst { state -> state.value }.takeIf { it >= 0 }
            val pressedOption = pressedIndex?.let { options[it] }
            val selectedOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "tdaySegmentedSliderSelectorOffset",
            )
            val selectorScale by animateFloatAsState(
                targetValue = if (pressedOption == selectedOption) 0.985f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "tdaySegmentedSliderSelectorPressScale",
            )
            val selectorPressOverlayAlpha by animateFloatAsState(
                targetValue = if (pressedOption == selectedOption) 0.06f else 0f,
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                label = "tdaySegmentedSliderSelectorPressOverlayAlpha",
            )

            Box(
                modifier = Modifier
                    .offset(x = selectedOffset)
                    .width(segmentWidth)
                    .fillMaxSize()
                    .padding(2.dp)
                    .graphicsLayer {
                        scaleX = selectorScale
                        scaleY = selectorScale
                    }
                    .shadow(
                        elevation = 12.dp,
                        shape = selectorShape,
                        ambientColor = accentColor.copy(alpha = 0.16f),
                        spotColor = Color.Black.copy(alpha = 0.14f),
                    )
                    .clip(selectorShape)
                    .background(selectorContainerColor, selectorShape)
                    .background(
                        accentColor.copy(alpha = if (isDarkTheme) 0.04f else 0.06f),
                        selectorShape
                    )
                    .background(
                        colorScheme.onSurface.copy(alpha = selectorPressOverlayAlpha),
                        selectorShape
                    )
                    .border(
                        width = 1.dp,
                        color = selectorBorderColor,
                        shape = selectorShape,
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .selectableGroup(),
            ) {
                options.forEachIndexed { index, option ->
                    val selected = option == selectedOption
                    val interactionSource = interactionSources[index]
                    val isPressed = pressedStates[index].value
                    val contentScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.98f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "tdaySegmentedSliderContentPressScale",
                    )
                    val pressHaloAlpha by animateFloatAsState(
                        targetValue = if (isPressed && !selected) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = if (isPressed && !selected) 90 else 190,
                            easing = FastOutSlowInEasing,
                        ),
                        label = "tdaySegmentedSliderPressHaloAlpha",
                    )
                    val pressHaloScale by animateFloatAsState(
                        targetValue = if (isPressed && !selected) 1f else 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "tdaySegmentedSliderPressHaloScale",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) {
                            colorScheme.onSurface
                        } else {
                            colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                        },
                        label = "tdaySegmentedSliderContentColor",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(selectorShape)
                            .selectable(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        ViewCompat.performHapticFeedback(
                                            view,
                                            HapticFeedbackConstantsCompat.CLOCK_TICK,
                                        )
                                    }
                                    onOptionSelected(option)
                                },
                                role = Role.RadioButton,
                                interactionSource = interactionSource,
                                indication = null,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .graphicsLayer {
                                    alpha = pressHaloAlpha
                                    scaleX = pressHaloScale
                                    scaleY = pressHaloScale
                                }
                                .clip(selectorShape)
                                .background(colorScheme.surface.copy(alpha = 0.62f), selectorShape)
                                .background(accentColor.copy(alpha = 0.10f), selectorShape)
                                .border(
                                    width = 1.dp,
                                    color = colorScheme.surface.copy(alpha = 0.76f),
                                    shape = selectorShape,
                                )
                        )
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.ExtraBold,
                            color = contentColor,
                            modifier = Modifier.graphicsLayer {
                                scaleX = contentScale
                                scaleY = contentScale
                            },
                        )
                    }
                }
            }
        }
    }
}
