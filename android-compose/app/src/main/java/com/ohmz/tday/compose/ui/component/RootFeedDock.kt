package com.ohmz.tday.compose.ui.component

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.lerp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayRootFeedAccent
import com.ohmz.tday.compose.ui.theme.TdayTodayBlue
import kotlinx.coroutines.delay

enum class RootFeedTab {
    HOME,
    FLOATER,
}

private val RootFeedTabs = listOf(RootFeedTab.HOME, RootFeedTab.FLOATER)
private val RootFeedDockHeight = TdayDimens.RootFeedDockHeight
private val RootFeedDockCollapsedWidth = RootFeedDockHeight
private val RootFeedDockInnerPadding = TdayDimens.RootFeedDockInnerPadding
private val RootFeedDockTabWidth = TdayDimens.RootFeedDockTabWidth
private val RootFeedDockExpandedWidth =
    (RootFeedDockTabWidth * RootFeedTabs.size) + (RootFeedDockInnerPadding * 2)
private val RootFeedDockShape = RoundedCornerShape(TdayDimens.RootFeedDockRadius)
private val RootFeedDockSelectorShape = RoundedCornerShape(TdayDimens.RootFeedDockSelectorRadius)

@StringRes
private fun RootFeedTab.labelRes(): Int {
    return when (this) {
        RootFeedTab.HOME -> R.string.root_feed_tab_home
        RootFeedTab.FLOATER -> R.string.root_feed_tab_floater
    }
}

private fun RootFeedTab.icon(): ImageVector {
    return when (this) {
        RootFeedTab.HOME -> Icons.Rounded.Home
        RootFeedTab.FLOATER -> Icons.Rounded.BubbleChart
    }
}

@Composable
fun RootCreateTaskButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = TdayTodayBlue,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val view = LocalView.current

    Card(
        modifier = modifier,
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        border = BorderStroke(TdayDimens.BorderWidth, backgroundColor.copy(alpha = 0.72f)),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.FabElevation,
            pressedElevation = TdayDimens.FabPressedElevation,
        ),
    ) {
        Box(
            modifier = Modifier.size(TdayDimens.FabSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.action_create_task),
                tint = Color.White,
                modifier = Modifier.size(TdayDimens.FabIconSize),
            )
        }
    }
}

@Composable
fun RootFeedDock(
    activeTab: RootFeedTab,
    collapsed: Boolean,
    onTabSelected: (RootFeedTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedByTap by remember { mutableStateOf(false) }
    val expanded = !collapsed || expandedByTap
    val view = LocalView.current
    val expansionProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "rootFeedDockExpansion",
    )
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
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
    val activeIndex = RootFeedTabs.indexOf(activeTab).coerceAtLeast(0)
    val interactionSources = remember {
        List(RootFeedTabs.size) { MutableInteractionSource() }
    }
    val pressedStates = interactionSources.map { source ->
        source.collectIsPressedAsState()
    }
    val dockWidth = lerp(
        RootFeedDockCollapsedWidth,
        RootFeedDockExpandedWidth,
        expansionProgress,
    )

    LaunchedEffect(collapsed) {
        if (!collapsed) {
            expandedByTap = false
        }
    }
    LaunchedEffect(expandedByTap) {
        if (expandedByTap) {
            delay(2400)
            expandedByTap = false
        }
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(
                start = TdayDimens.RootFeedDockOuterPaddingStart,
                bottom = TdayDimens.RootFeedDockOuterPaddingBottom,
            )
            .width(dockWidth)
            .height(RootFeedDockHeight)
            .clip(RootFeedDockShape)
            .background(trackColor, RootFeedDockShape)
            .border(
                width = TdayDimens.BorderWidth,
                color = trackBorderColor,
                shape = RootFeedDockShape,
            )
            .padding(RootFeedDockInnerPadding)
            .selectableGroup(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tabWidthTarget = if (maxWidth < RootFeedDockTabWidth) {
                maxWidth
            } else {
                RootFeedDockTabWidth
            }
            val selectorWidthTarget = tabWidthTarget
            val selectorOffsetTarget = if (activeIndex == 0) {
                TdayDimens.SpacingNone
            } else {
                maxWidth - selectorWidthTarget
            }
            val selectorWidth by animateDpAsState(
                targetValue = selectorWidthTarget,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "rootFeedDockSelectorWidth",
            )
            val selectorOffset by animateDpAsState(
                targetValue = selectorOffsetTarget,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "rootFeedDockSelectorOffset",
            )
            val activePressed = pressedStates.getOrNull(activeIndex)?.value == true
            val selectorScale by animateFloatAsState(
                targetValue = if (activePressed) 0.985f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "rootFeedDockSelectorPressScale",
            )

            Box(
                modifier = Modifier
                    .offset(x = selectorOffset)
                    .width(selectorWidth)
                    .fillMaxHeight()
                    .padding(TdayDimens.RootFeedDockSelectorInset)
                    .graphicsLayer {
                        scaleX = selectorScale
                        scaleY = selectorScale
                    }
                    .shadow(
                        elevation = TdayDimens.RootFeedDockSelectorElevation,
                        shape = RootFeedDockSelectorShape,
                        ambientColor = TdayRootFeedAccent.copy(alpha = 0.16f),
                        spotColor = Color.Black.copy(alpha = 0.14f),
                    )
                    .clip(RootFeedDockSelectorShape)
                    .background(selectorContainerColor, RootFeedDockSelectorShape)
                    .background(
                        TdayRootFeedAccent.copy(alpha = if (isDarkTheme) 0.04f else 0.06f),
                        RootFeedDockSelectorShape,
                    )
                    .border(
                        width = TdayDimens.BorderWidth,
                        color = selectorBorderColor,
                        shape = RootFeedDockSelectorShape,
                    )
            )

            RootFeedTabs.forEachIndexed { index, tab ->
                val selected = tab == activeTab
                val interactionSource = interactionSources[index]
                val tabPressed = pressedStates[index].value
                val tabOffsetTarget = if (selected) {
                    selectorOffsetTarget
                } else {
                    val expandedOffset = tabWidthTarget * index
                    val hiddenOffset = if (index < activeIndex) {
                        -tabWidthTarget
                    } else {
                        maxWidth
                    }
                    lerp(hiddenOffset, expandedOffset, expansionProgress)
                }
                val tabAlphaTarget = if (selected) {
                    1f
                } else {
                    expansionProgress
                }
                val tabOffset by animateDpAsState(
                    targetValue = tabOffsetTarget,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "rootFeedDockTabOffset",
                )
                val tabWidth by animateDpAsState(
                    targetValue = tabWidthTarget,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "rootFeedDockTabWidth",
                )
                val tabAlpha by animateFloatAsState(
                    targetValue = tabAlphaTarget,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "rootFeedDockTabAlpha",
                )
                val contentScale by animateFloatAsState(
                    targetValue = if (tabPressed) 0.98f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "rootFeedDockContentPressScale",
                )
                val contentColor = if (selected) {
                    colorScheme.onSurface
                } else {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                }
                val animatedContentColor by animateColorAsState(
                    targetValue = contentColor,
                    animationSpec = tween(durationMillis = 180),
                    label = "rootFeedDockContentColor",
                )

                Box(
                    modifier = Modifier
                        .offset(x = tabOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = tabAlpha }
                        .clip(RootFeedDockSelectorShape)
                        .selectable(
                            selected = selected,
                            onClick = {
                                if (!expanded && selected) {
                                    ViewCompat.performHapticFeedback(
                                        view,
                                        HapticFeedbackConstantsCompat.CLOCK_TICK,
                                    )
                                    expandedByTap = true
                                } else {
                                    if (!selected) {
                                        ViewCompat.performHapticFeedback(
                                            view,
                                            HapticFeedbackConstantsCompat.CLOCK_TICK,
                                        )
                                    }
                                    onTabSelected(tab)
                                }
                            },
                            role = Role.RadioButton,
                            interactionSource = interactionSource,
                            indication = null,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val textAlpha = if (selected) expansionProgress else 1f
                    val iconAlpha = if (selected) 1f - expansionProgress else 0f

                    Icon(
                        imageVector = tab.icon(),
                        contentDescription = null,
                        tint = animatedContentColor,
                        modifier = Modifier
                            .size(TdayDimens.RootFeedDockIconSize)
                            .graphicsLayer {
                                alpha = iconAlpha * tabAlpha
                                scaleX = contentScale * (1f - (0.08f * expansionProgress))
                                scaleY = contentScale * (1f - (0.08f * expansionProgress))
                            },
                    )
                    Text(
                        text = stringResource(tab.labelRes()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.ExtraBold,
                        color = animatedContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            alpha = textAlpha
                            scaleX = contentScale * (0.94f + (0.06f * textAlpha))
                            scaleY = contentScale * (0.94f + (0.06f * textAlpha))
                        },
                    )
                }
            }

            if (!expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.CLOCK_TICK,
                            )
                            expandedByTap = true
                        },
                )
            }
        }
    }
}
