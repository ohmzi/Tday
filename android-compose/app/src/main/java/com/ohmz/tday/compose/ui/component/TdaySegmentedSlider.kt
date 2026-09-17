package com.ohmz.tday.compose.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.core.ui.TdayHaptics
import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import com.ohmz.tday.compose.core.ui.rememberTdayMotionEnabled
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayTodayBlue

private val TdaySegmentedSliderAccent = TdayTodayBlue

/**
 * The rungs a caller may put this control's thumb on, by name.
 *
 * The control's own default is a spring, and it stays that: `SettingsScreen`'s two
 * switchers and `CalendarScreen`'s view-mode strip have slid on it since they were built,
 * and re-timing them is a visible change to three screens that this change was not asked
 * to make.
 *
 * [Enter] is here because web's Completed tab strip runs on it as a deliberate, argued
 * choice (`CompletedContainer.tsx`: "Enter, not Emphasis, and that is a choice rather than
 * an oversight… it is the same segmented control `SettingsPage` draws twice"), and web
 * spells that rung `duration-enter ease-out` — 200 ms on `cubic-bezier(0, 0, 0.2, 1)`,
 * which is [TdayMotionTokens.Durations.Enter] on [TdayMotionTokens.Easings.Enter]. Both
 * terms come from the token layer; nothing here is a new literal.
 *
 * What this leaves is a divergence worth naming rather than hiding: after the completion
 * history takes this rung, the app has the same two controls web has — the strip on Enter,
 * the calendar's on the spring — instead of the one control on one clock it had while all
 * three screens drew the default. Bringing `SettingsScreen`'s two switchers onto Enter, so
 * that the strip and the switchers match web's own pairing, is the next move and belongs
 * to whoever makes that argument.
 */
object TdaySegmentedSliderMotion {
    val Enter: AnimationSpec<Dp> = tween(
        durationMillis = TdayMotionTokens.Durations.Enter,
        easing = TdayMotionTokens.Easings.Enter,
    )
}

@Composable
fun <T> TdaySegmentedSlider(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = TdaySegmentedSliderAccent,
    label: (T) -> String,
    /**
     * The count drawn beside an option's label, or null for an option that carries none.
     *
     * Optional rather than a slot every caller fills: the completion history's two tabs
     * are the app's only segmented control that counts anything, and on web they are the
     * only one of the three that draws this badge either (`CompletedContainer.tsx`). A
     * later caller that wants no count writes nothing, and an option that wants none
     * answers null.
     */
    badge: (T) -> String? = { null },
    /**
     * The thumb's travel, or null for the spring this control has always slid on.
     *
     * A parameter rather than a change to the default, because the two are different
     * questions and only one of them was asked here — see [TdaySegmentedSliderMotion].
     */
    selectorAnimationSpec: AnimationSpec<Dp>? = null,
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
                // The app's Reduce Motion switch, and a switch this control was missing:
                // every other motion on a screen answers to it, and a thumb that slides
                // through a device that has asked for less movement is the one thing here
                // that does not. With the switch on the thumb is placed rather than
                // travelled, which is the clean cut the rule asks for.
                animationSpec = if (!rememberTdayMotionEnabled()) {
                    snap()
                } else {
                    selectorAnimationSpec ?: spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                    )
                },
                label = "tdaySegmentedSliderSelectorOffset",
            )
            // Not `Modifier.tdayPressable`, and not a retune: `PressScales.Row` is
            // 0.985 to the byte, and this scale is not a surface reading its own
            // press. It is the floating selector, whose interaction source belongs
            // to an option Box in the Row below — and it shares the spring with
            // the offset that slides it, so the squash and the slide are one
            // movement. The shared modifier takes no spec, by argument.
            val selectorScale by animateFloatAsState(
                targetValue =
                    if (pressedOption == selectedOption) {
                        TdayMotionTokens.PressScales.Row
                    } else {
                        1f
                    },
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
                    // A half-hundredth shallower than the 0.98 typed here, and
                    // on the selector's number on purpose: a segment's label and
                    // the selector sitting under it are pressed by the same
                    // finger, and two depths half a percent apart read as the
                    // label sliding against its own pill. Spring kept for the
                    // same coupling.
                    val contentScale by animateFloatAsState(
                        targetValue = if (isPressed) TdayMotionTokens.PressScales.Row else 1f,
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
                                        TdayHaptics.selection(view)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXs),
                            modifier = Modifier.graphicsLayer {
                                scaleX = contentScale
                                scaleY = contentScale
                            },
                        ) {
                            Text(
                                text = label(option),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.Black else FontWeight.ExtraBold,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                            )
                            badge(option)?.let { count ->
                                Text(
                                    text = count,
                                    // The same weight and the same stepped-back alpha the
                                    // option's own label uses when it is not the selected
                                    // one, so the count reads as an annotation on the label
                                    // rather than as a second, competing one.
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = contentColor.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
