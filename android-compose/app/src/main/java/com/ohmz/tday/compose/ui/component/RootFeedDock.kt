package com.ohmz.tday.compose.ui.component

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.ui.TdayHaptics
import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import com.ohmz.tday.compose.core.ui.TdayPress
import com.ohmz.tday.compose.core.ui.interactiveTimeoutMillis
import com.ohmz.tday.compose.core.ui.rememberTdayMotionEnabled
import com.ohmz.tday.compose.core.ui.tdayPressable
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayFloaterAccent
import com.ohmz.tday.compose.ui.theme.TdayRootFeedAccent
import com.ohmz.tday.compose.ui.theme.TdayTodayBlue
import kotlinx.coroutines.delay

enum class RootFeedTab {
    SCHEDULED_TASK_HOME,
    FLOATER_TASK_HOME,
}

/** The API/cache-persisted value for the "Default home screen" preference. */
fun RootFeedTab.toDefaultHomeScreenApiValue(): String = when (this) {
    RootFeedTab.SCHEDULED_TASK_HOME -> "scheduled"
    RootFeedTab.FLOATER_TASK_HOME -> "floater"
}

/** Inverse of [toDefaultHomeScreenApiValue]; unrecognized/absent values fall back to Scheduled. */
fun rootFeedTabFromDefaultHomeScreenApiValue(value: String?): RootFeedTab = when (value) {
    "floater" -> RootFeedTab.FLOATER_TASK_HOME
    else -> RootFeedTab.SCHEDULED_TASK_HOME
}

private val RootFeedTabs = listOf(RootFeedTab.SCHEDULED_TASK_HOME, RootFeedTab.FLOATER_TASK_HOME)
private val RootFeedDockHeight = TdayDimens.RootFeedDockHeight
private val RootFeedDockCollapsedWidth = RootFeedDockHeight
private val RootFeedDockInnerPadding = TdayDimens.RootFeedDockInnerPadding
private val RootFeedDockTabWidth = TdayDimens.RootFeedDockTabWidth
private val RootFeedDockExpandedWidth =
    (RootFeedDockTabWidth * RootFeedTabs.size) + (RootFeedDockInnerPadding * 2)

/**
 * Where a root feed's dock folds down to its pill, and where it opens back up again.
 *
 * Two thresholds and not one. A single comparison flips on its own boundary pixel, so a
 * finger parked exactly at [CollapseThreshold] — which is where a finger parked anywhere
 * near the top of a feed ends up, a list settling a pixel either way under its own
 * fling — strobes the dock between [RootFeedDockCollapsedWidth] and
 * [RootFeedDockExpandedWidth] for as long as it rests there. The 20 dp between the two
 * numbers below is the dead band that swallows that hover. It costs a deliberate scroll
 * back to the top nothing: such a scroll passes both edges inside one gesture.
 *
 * [CollapseThreshold] is not ours alone. iOS declares the same 44 at
 * `RootFeedDockCollapse.collapseThreshold` in `ios-swiftUI/Tday/Core/UI/RootFeedDock.swift:222`
 * — it spelled it twice, once per root feed, until that enum took it over — and web's root
 * dock fold carries the third copy in `tday-web/src/lib/rootDockCollapse.ts`. Moving it here
 * moves one client of three, and a dock that folds at three different distances is three
 * docks.
 */
object RootFeedDockCollapse {

    /** How far a feed has to travel before its dock gives up its labels. */
    val CollapseThreshold: Dp = 44.dp

    /** How far back up it has to come before the dock gets them back. */
    val ExpandThreshold: Dp = 24.dp

    /**
     * The dock's next folded state, given the one it is already in.
     *
     * [previous] is what makes the dead band a dead band rather than a second threshold
     * nobody reaches: it picks which edge is being tested. Any feed scrolled off its
     * first item is past both edges by definition and collapses regardless — a lazy list
     * reports the offset within the first visible item, not the distance travelled, so
     * without that clause a long scroll reads as a small one.
     */
    fun next(
        previous: Boolean,
        firstVisibleItemIndex: Int,
        scrollOffsetPx: Int,
        collapsePx: Int,
        expandPx: Int,
    ): Boolean {
        if (firstVisibleItemIndex > 0) return true
        // Distance travelled, never a position: an offset above the top of the first
        // item is zero travel. With both edges positive this changes no answer on its
        // own — it is here so the comparisons below read as "how far has this feed
        // come", which is the question, rather than as "where is its first item",
        // which is not.
        val offset = scrollOffsetPx.coerceAtLeast(0)
        return if (previous) offset > expandPx else offset > collapsePx
    }
}

/**
 * How long the dock stays open after a tap has opened it, for a user who has asked
 * Android for nothing. A base rather than the window itself — see
 * [interactiveTimeoutMillis].
 */
private const val RootFeedDockTapExpansionMs = 2_400L
private val RootFeedDockShape = RoundedCornerShape(TdayDimens.RootFeedDockRadius)
private val RootFeedDockSelectorShape = RoundedCornerShape(TdayDimens.RootFeedDockSelectorRadius)

@StringRes
internal fun RootFeedTab.labelRes(): Int {
    return when (this) {
        RootFeedTab.SCHEDULED_TASK_HOME -> R.string.root_feed_tab_scheduled_task_home
        RootFeedTab.FLOATER_TASK_HOME -> R.string.root_feed_tab_floater
    }
}

@Composable
private fun RootFeedTab.icon(): ImageVector {
    return when (this) {
        RootFeedTab.SCHEDULED_TASK_HOME -> ImageVector.vectorResource(R.drawable.ic_lucide_calendar_check)
        RootFeedTab.FLOATER_TASK_HOME -> ImageVector.vectorResource(R.drawable.ic_lucide_leaf)
    }
}

private fun RootFeedTab.accentColor(): Color {
    return when (this) {
        RootFeedTab.SCHEDULED_TASK_HOME -> TdayTodayBlue
        RootFeedTab.FLOATER_TASK_HOME -> TdayFloaterAccent
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
        // The press goes on the END of the incoming modifier, not in front of
        // it. Both callers put the button where it lives from the outside —
        // `.align()`, `.navigationBarsPadding()`, `.padding()` here and
        // `.align().size()` on the car surface — and those have to stay
        // outermost, so that the offset and the squash move the drawn circle
        // inside a layout slot that does not budge. Put them first and a press
        // shifts the slot instead, which drags the navigation-bar inset with it.
        modifier = modifier.tdayPressable(interactionSource, scale = TdayPress.FabScale),
        onClick = {
            TdayHaptics.buttonPress(view)
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
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_plus),
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
    val context = LocalContext.current
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
    val labelTextStyle = MaterialTheme.typography.titleSmall.copy(
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )
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
            // Same question the Undo toast asks, on the same screen: something opened
            // itself for the user and will take itself away whether or not they got to
            // it. 2.4s is the shortest such window in the app, so a user who told
            // Settings they need thirty seconds to act was watching the dock close
            // while they were still travelling to the tab — and unlike the toast there
            // is no second chance on screen, only the same tap again. Read when the
            // dock opens rather than at composition, because that is when the window
            // starts and the user can have changed the setting since. Not scaled with
            // motion: this is dwell time, not a duration anyone watches. See
            // AccessibilityTimeout.kt.
            delay(interactiveTimeoutMillis(context, RootFeedDockTapExpansionMs))
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
                targetValue = if (activePressed) TdayMotionTokens.PressScales.Row else 1f,
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

            // The tab's tint, the create button's accent and the feed body underneath
            // both of them are one handover: a finger lands on a tab and three surfaces
            // answer it. This was the surface answering on its own clock — a written 180
            // that is not a rung and sits between two that are — while the other two run
            // on Quick. Quick is right on its own terms too: a control answering a finger
            // that is on it. Nothing here travels, so this is not Emphasis; and it is not
            // Change either, which is the user's own edit replayed for them to watch, not
            // a control repainting itself under the press that asked for it. Standard is
            // the curve because a colour crossing between two accents has no arriving or
            // leaving half to favour, which is the same call TdayApp's crossfades make.
            //
            // One spec, hoisted out of the loop: the tab going grey and the tab taking the
            // accent are one event seen from both ends, and a spec each is an invitation
            // to retime half of it.
            //
            // With motion off the tint snaps, in the same frame the create button's does,
            // so a tab is drawn already wearing its finished colour rather than parked
            // between the two — docs/motion.md's fifth idiom rule.
            val motionEnabled = rememberTdayMotionEnabled()
            val contentColorSpec: AnimationSpec<Color> = if (motionEnabled) {
                tween(
                    durationMillis = TdayMotionTokens.Durations.Quick,
                    easing = TdayMotionTokens.Easings.Standard,
                )
            } else {
                snap()
            }

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
                // On `Row` rather than on the 0.98 this used to write, and it is
                // allowed on a token despite being multiplied into the two
                // `graphicsLayer` blocks below. `docs/motion.md` excludes a press
                // factor that is one term of a composed transform, because there
                // the token would name half of what a finger sees. The narrow
                // reason it does not apply here: the dock settles at
                // `expansionProgress` 0 or 1 and nowhere else, and at both of those
                // the factor on whichever of the two is visible is exactly 1 — the
                // icon's `1 - 0.08 * expansionProgress` at 0, where the icon is the
                // opaque one, and the label's `0.94 + 0.06 * textAlpha` at 1, where
                // the label is. In between, the other factor does leave 1, but that
                // is the expansion running, and the expansion is a transient that
                // fades the thing it is scaling. A press is measured against a
                // settled tab, and a settled tab squashes by this number and
                // nothing else.
                val contentScale by animateFloatAsState(
                    targetValue = if (tabPressed) TdayMotionTokens.PressScales.Row else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "rootFeedDockContentPressScale",
                )
                val contentColor = if (selected) {
                    tab.accentColor()
                } else {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                }
                val animatedContentColor by animateColorAsState(
                    targetValue = contentColor,
                    animationSpec = contentColorSpec,
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
                                    TdayHaptics.reveal(view)
                                    expandedByTap = true
                                } else {
                                    if (!selected) {
                                        TdayHaptics.selection(view)
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
                        painter = if (tab == RootFeedTab.SCHEDULED_TASK_HOME) {
                            painterResource(R.drawable.ic_lucide_calendar_check)
                        } else {
                            rememberVectorPainter(tab.icon())
                        },
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
                        style = labelTextStyle,
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
                            TdayHaptics.reveal(view)
                            expandedByTap = true
                        },
                )
            }
        }
    }
}
