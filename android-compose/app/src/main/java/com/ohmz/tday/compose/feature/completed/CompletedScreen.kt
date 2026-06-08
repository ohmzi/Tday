package com.ohmz.tday.compose.feature.completed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.ui.EmptyTaskBackgroundMessage
import com.ohmz.tday.compose.core.ui.EmptyTaskWatermark
import com.ohmz.tday.compose.core.ui.TaskSwipeActionButton
import com.ohmz.tday.compose.core.ui.animateTaskSwipeOffsetAsState
import com.ohmz.tday.compose.core.ui.rememberLazyListCollapsingTitleScrollBehavior
import com.ohmz.tday.compose.core.ui.rememberTaskSwipeRevealState
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.theme.TdayCompletedTitleAccent
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdaySwipeDeleteBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeEditBackground
import com.ohmz.tday.compose.ui.theme.TdayTaskCompleteAccent
import com.ohmz.tday.compose.ui.theme.tdayListAccentColor
import com.ohmz.tday.compose.ui.theme.tdayListIconForKey
import com.ohmz.tday.compose.ui.theme.tdayPriorityColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CompletedTimelineSameDateTaskSpacing = 2.dp
private val CompletedTimelineDateGroupSpacing = 6.dp
private val CompletedTimelineSectionTopSpacing = 6.dp
private val CompletedTimelineHeaderBodySpacing = 2.dp
private val CompletedTimelineCollapsedSectionSpacing = 4.dp
private val CompletedSwipeRowHeight = 56.dp
private const val COMPLETED_RESTORE_STEP_MS = 180L
private const val COMPLETED_RESTORE_FADE_MS = 260L

private fun completedTaskBottomSpacing(
    itemIndex: Int,
    lastIndex: Int,
    showDateDivider: Boolean,
) = if (showDateDivider || itemIndex == lastIndex) {
    CompletedTimelineDateGroupSpacing
} else {
    CompletedTimelineSameDateTaskSpacing
}

private enum class CompletedRestorePhase {
    Completed,
    Unchecked,
    Unstruck,
    Fading,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedScreen(
    uiState: CompletedUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUncomplete: (CompletedItem) -> Unit,
    onDelete: (CompletedItem) -> Unit,
    onUpdateTask: (CompletedItem, CreateTaskPayload) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val timelineSections = remember(uiState.items) {
        buildCompletedTimelineSections(uiState.items)
    }
    val titleScrollBehavior = rememberLazyListCollapsingTitleScrollBehavior(
        listState = listState,
        maxCollapseDistance = COMPLETED_TITLE_COLLAPSE_DISTANCE_DP.dp,
        label = "completedTitleCollapseProgress",
    )
    var collapsedSectionKeys by rememberSaveable {
        mutableStateOf(emptySet<String>())
    }
    var editTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var openSwipeTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val editTarget = remember(editTargetId, uiState.items) {
        editTargetId?.let { targetId -> uiState.items.firstOrNull { it.id == targetId } }
    }
    LaunchedEffect(uiState.items, openSwipeTaskId) {
        val openId = openSwipeTaskId ?: return@LaunchedEffect
        if (uiState.items.none { it.id == openId }) {
            openSwipeTaskId = null
        }
    }

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            CompletedTopBar(
                onBack = onBack,
                collapseProgress = titleScrollBehavior.collapseProgress,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(titleScrollBehavior.nestedScrollConnection),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    timelineSections.forEachIndexed { sectionIndex, section ->
                        val isCollapsed = collapsedSectionKeys.contains(section.key)
                        item(key = "completed-header-${section.key}") {
                            CompletedTimelineSectionHeader(
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = null,
                                        placementSpec = tween(
                                            durationMillis = 320,
                                            easing = FastOutSlowInEasing,
                                        ),
                                        fadeOutSpec = null,
                                    )
                                    .padding(
                                        top = if (sectionIndex == 0) 0.dp else CompletedTimelineSectionTopSpacing,
                                        bottom = if (isCollapsed) {
                                            CompletedTimelineCollapsedSectionSpacing
                                        } else {
                                            CompletedTimelineHeaderBodySpacing
                                        },
                                    ),
                                section = section,
                                isCollapsed = isCollapsed,
                                onHeaderClick = {
                                    collapsedSectionKeys =
                                        if (isCollapsed) {
                                            collapsedSectionKeys - section.key
                                        } else {
                                            collapsedSectionKeys + section.key
                                        }
                                },
                            )
                        }
                        if (!isCollapsed) {
                            section.items.forEachIndexed { itemIndex, completed ->
                                val showCompletedDateDivider = shouldShowDateDivider(
                                    afterItemIndex = itemIndex,
                                    inSectionIndex = sectionIndex,
                                    sections = timelineSections,
                                    collapsedSectionKeys = collapsedSectionKeys,
                                )
                                item(key = "completed-row-${section.key}-${completed.id}") {
                                    CompletedSwipeRow(
                                        modifier = Modifier
                                            .animateItem(
                                                fadeInSpec = tween(
                                                    durationMillis = 190,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                                placementSpec = tween(
                                                    durationMillis = 320,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                                fadeOutSpec = tween(
                                                    durationMillis = 150,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                            .padding(
                                                bottom = completedTaskBottomSpacing(
                                                    itemIndex = itemIndex,
                                                    lastIndex = section.items.lastIndex,
                                                    showDateDivider = showCompletedDateDivider,
                                                ),
                                            ),
                                        item = completed,
                                        lists = uiState.lists,
                                        showDateDivider = showCompletedDateDivider,
                                        onInfo = { editTargetId = completed.id },
                                        onDelete = { onDelete(completed) },
                                        onUncomplete = { onUncomplete(completed) },
                                        openSwipeTaskId = openSwipeTaskId,
                                        onOpenSwipeTaskIdChange = { openSwipeTaskId = it },
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.items.isEmpty() && uiState.isLoading) {
                        item {
                            EmptyCompletedState(
                                message = stringResource(R.string.label_loading),
                            )
                        }
                    }

                    uiState.errorMessage?.let { message ->
                        item {
                            com.ohmz.tday.compose.core.ui.ErrorRetryCard(
                                message = message,
                                onRetry = onRefresh,
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }

            EmptyTaskWatermark(
                iconRes = R.drawable.ic_lucide_circle_check_big,
                accentColor = COMPLETED_TITLE_COLOR,
            )
            if (uiState.items.isEmpty() && !uiState.isLoading) {
                EmptyTaskBackgroundMessage(
                    message = stringResource(R.string.completed_empty),
                )
            }
        }
    }

    editTarget?.let { completed ->
        CreateTaskBottomSheet(
            lists = uiState.lists,
            editingTask = completed.toEditableTodo(uiState.lists),
            defaultListId = completed.resolveListId(uiState.lists),
            onDismiss = { editTargetId = null },
            onCreateTask = { _ -> },
            onUpdateTask = { _, payload ->
                onUpdateTask(completed, payload)
                editTargetId = null
            },
        )
    }
}

@Composable
private fun CompletedTopBar(
    onBack: () -> Unit,
    collapseProgress: Float,
) {
    val progress = collapseProgress.coerceIn(0f, 1f)
    val titleHandoffPoint = 0.9f
    val density = LocalDensity.current
    val expandedTitleHeight = lerp(56.dp, 0.dp, progress)
    val expandedTitleAlpha = ((titleHandoffPoint - progress) / titleHandoffPoint).coerceIn(0f, 1f)
    val collapsedTitleAlpha =
        ((progress - titleHandoffPoint) / (1f - titleHandoffPoint)).coerceIn(0f, 1f)
    val collapsedTitleShiftY = with(density) { (12.dp * (1f - collapsedTitleAlpha)).toPx() }
    val expandedTitleShiftY = with(density) { (-10.dp * (1f - expandedTitleAlpha)).toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompletedHeaderButton(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    onClick = onBack,
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_left),
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            if (collapsedTitleAlpha > 0.001f) {
                Text(
                    text = stringResource(R.string.completed_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = COMPLETED_TITLE_COLOR,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = collapsedTitleAlpha
                            translationY = collapsedTitleShiftY
                        },
                )
            }
        }
        Spacer(modifier = Modifier.height(lerp(14.dp, 0.dp, progress)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(expandedTitleHeight),
            contentAlignment = Alignment.BottomStart,
        ) {
            if (expandedTitleAlpha > 0.001f) {
                Text(
                    text = stringResource(R.string.completed_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = COMPLETED_TITLE_COLOR,
                    modifier = Modifier.graphicsLayer {
                        alpha = expandedTitleAlpha
                        translationY = expandedTitleShiftY
                    },
                )
            }
        }
    }
}

@Composable
private fun CompletedHeaderButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val containerColor =
        if (isDarkTheme) colorScheme.surface.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        label = "completedHeaderButtonScale",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        label = "completedHeaderButtonOffsetY",
    )

    Card(
        modifier = Modifier
            .then(modifier)
            .offset(y = offsetY)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
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
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun CompletedTimelineSectionHeader(
    modifier: Modifier = Modifier,
    section: CompletedSection,
    isCollapsed: Boolean,
    onHeaderClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val headerInteractionSource = remember { MutableInteractionSource() }
    val isHeaderPressed by headerInteractionSource.collectIsPressedAsState()
    val collapseChevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "completedSectionChevronRotation",
    )
    val baseHeaderColor = colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    val headerTextColor = if (isHeaderPressed) {
        androidx.compose.ui.graphics.lerp(baseHeaderColor, colorScheme.onSurface, 0.16f)
    } else {
        baseHeaderColor
    }
    val baseChevronColor = colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val chevronColor = if (isHeaderPressed) {
        androidx.compose.ui.graphics.lerp(baseChevronColor, colorScheme.onSurface, 0.16f)
    } else {
        baseChevronColor
    }
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = headerInteractionSource,
                    indication = null,
                    onClick = onHeaderClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.title,
                color = headerTextColor,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_down),
                contentDescription = if (isCollapsed) {
                    stringResource(R.string.action_expand_section)
                } else {
                    stringResource(R.string.action_collapse_section)
                },
                tint = chevronColor,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp)
                    .graphicsLayer { rotationZ = collapseChevronRotation },
            )
        }
    }
}

@Composable
private fun CompletedSwipeRow(
    modifier: Modifier = Modifier,
    item: CompletedItem,
    lists: List<ListSummary>,
    showDateDivider: Boolean,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onUncomplete: () -> Unit,
    openSwipeTaskId: String?,
    onOpenSwipeTaskIdChange: (String?) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val swipeRevealState = rememberTaskSwipeRevealState(item.id)
    var restorePhase by remember(item.id) { mutableStateOf(CompletedRestorePhase.Completed) }
    var titleLayoutResult by remember(item.id) { mutableStateOf<TextLayoutResult?>(null) }
    val latestOpenSwipeTaskId = rememberUpdatedState(openSwipeTaskId)
    fun claimSwipeSlot() {
        if (latestOpenSwipeTaskId.value != item.id) {
            onOpenSwipeTaskIdChange(item.id)
        }
    }

    fun closeSwipeSlot() {
        swipeRevealState.close()
        if (latestOpenSwipeTaskId.value == item.id) {
            onOpenSwipeTaskIdChange(null)
        }
    }
    val animatedOffsetX by animateTaskSwipeOffsetAsState(
        state = swipeRevealState,
        label = "completedSwipeOffset",
    )
    val actionRevealProgress = swipeRevealState.revealProgress(animatedOffsetX)
    val showCompletedCheckmark = restorePhase == CompletedRestorePhase.Completed
    val showStrikethrough =
        restorePhase == CompletedRestorePhase.Completed || restorePhase == CompletedRestorePhase.Unchecked
    val isFading = restorePhase == CompletedRestorePhase.Fading
    val isRestoring = restorePhase != CompletedRestorePhase.Completed
    val rowAlpha by animateFloatAsState(
        targetValue = if (isFading) 0f else 1f,
        animationSpec = tween(
            durationMillis = COMPLETED_RESTORE_FADE_MS.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "completedRestoreRowAlpha",
    )
    val rowScale by animateFloatAsState(
        targetValue = if (isFading) 0.985f else 1f,
        animationSpec = tween(
            durationMillis = COMPLETED_RESTORE_FADE_MS.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "completedRestoreRowScale",
    )
    val rowOffsetY by animateDpAsState(
        targetValue = if (isFading) (-10).dp else 0.dp,
        animationSpec = tween(
            durationMillis = COMPLETED_RESTORE_FADE_MS.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "completedRestoreRowOffsetY",
    )
    val titleColor by animateColorAsState(
        targetValue = if (showStrikethrough) {
            colorScheme.onSurface.copy(alpha = 0.78f)
        } else {
            colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 160),
        label = "completedRestoreTitleColor",
    )
    val titleStrikeProgress by animateFloatAsState(
        targetValue = if (showStrikethrough) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "completedRestoreTitleStrikeProgress",
    )
    val completedAtText = COMPLETED_ROW_TIME_FORMATTER
        .withZone(ZoneId.systemDefault())
        .format(item.completedAt ?: item.due ?: Instant.EPOCH)
    val listMeta = item.resolveListSummary(lists)
    val listIndicatorColor = listMeta?.color?.let(::tdayListAccentColor)
        ?: item.listColor?.let(::tdayListAccentColor)
        ?: colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
    val showListIndicator = !item.listName.isNullOrBlank() || listMeta != null
    val priorityIcon = priorityIconFor(item.priority)
    val showPriorityIcon = priorityIcon != null
    val rowShape = RoundedCornerShape(16.dp)
    val foregroundColor = colorScheme.background
    LaunchedEffect(openSwipeTaskId, item.id) {
        if (openSwipeTaskId != null && openSwipeTaskId != item.id && swipeRevealState.isOpenOrDragging) {
            swipeRevealState.close()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rowAlpha
                scaleX = rowScale
                scaleY = rowScale
                translationY = rowOffsetY.toPx()
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CompletedSwipeRowHeight),
        ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskSwipeActionButton(
                        icon = R.drawable.ic_lucide_square_pen,
                        contentDescription = stringResource(R.string.action_edit_task),
                        label = stringResource(R.string.action_edit),
                        tint = Color.White,
                        background = TdaySwipeEditBackground,
                        revealProgress = actionRevealProgress,
                        revealDelay = 0.62f,
                        onClick = {
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.CLOCK_TICK,
                            )
                            closeSwipeSlot()
                            onInfo()
                        },
                    )
                    TaskSwipeActionButton(
                        icon = R.drawable.ic_lucide_trash,
                        contentDescription = stringResource(R.string.action_delete_task),
                        label = stringResource(R.string.action_delete),
                        tint = Color.White,
                        background = TdaySwipeDeleteBackground,
                        revealProgress = actionRevealProgress,
                        revealDelay = 0.04f,
                        onClick = {
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.CLOCK_TICK,
                            )
                            closeSwipeSlot()
                            onDelete()
                        },
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = animatedOffsetX }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                if (delta < 0f || swipeRevealState.isOpenOrDragging) {
                                    claimSwipeSlot()
                                }
                                swipeRevealState.dragBy(delta)
                                if (!swipeRevealState.isOpenOrDragging && latestOpenSwipeTaskId.value == item.id) {
                                    onOpenSwipeTaskIdChange(null)
                                }
                            },
                            onDragStopped = { velocity ->
                                swipeRevealState.settle(velocity)
                                if (swipeRevealState.isOpenOrDragging) {
                                    claimSwipeSlot()
                                } else if (latestOpenSwipeTaskId.value == item.id) {
                                    onOpenSwipeTaskIdChange(null)
                                }
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (swipeRevealState.isOpenOrDragging) {
                                closeSwipeSlot()
                            } else if (!swipeRevealState.isHinting && !isRestoring) {
                                claimSwipeSlot()
                                coroutineScope.launch {
                                    swipeRevealState.playHint()
                                    if (latestOpenSwipeTaskId.value == item.id && !swipeRevealState.isOpenOrDragging) {
                                        onOpenSwipeTaskIdChange(null)
                                    }
                                }
                            }
                        },
                    shape = rowShape,
                    colors = CardDefaults.cardColors(containerColor = foregroundColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompletedCircularToggleIcon(
                            imageVector = if (showCompletedCheckmark) {
                                ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big)
                            } else {
                                ImageVector.vectorResource(R.drawable.ic_lucide_circle)
                            },
                            contentDescription = stringResource(R.string.label_undo_complete),
                            tint = if (showCompletedCheckmark) {
                                TdayTaskCompleteAccent
                            } else {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            },
                            enabled = !isRestoring,
                            onClick = {
                                ViewCompat.performHapticFeedback(
                                    view,
                                    HapticFeedbackConstantsCompat.CLOCK_TICK,
                                )
                                closeSwipeSlot()
                                coroutineScope.launch {
                                    restorePhase = CompletedRestorePhase.Unchecked
                                    delay(COMPLETED_RESTORE_STEP_MS)
                                    restorePhase = CompletedRestorePhase.Unstruck
                                    delay(COMPLETED_RESTORE_STEP_MS)
                                    restorePhase = CompletedRestorePhase.Fading
                                    delay(COMPLETED_RESTORE_FADE_MS)
                                    onUncomplete()
                                }
                            },
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        ) {
                            Text(
                                text = item.title,
                                modifier = Modifier.drawWithContent {
                                    drawContent()
                                    if (titleStrikeProgress > 0f) {
                                        val lineEnd = (
                                                titleLayoutResult
                                                    ?.takeIf { it.lineCount > 0 }
                                                    ?.getLineRight(0) ?: size.width
                                                ).coerceIn(0f, size.width)
                                        val lineY = size.height * 0.56f
                                        drawLine(
                                            color = colorScheme.onSurface.copy(alpha = 0.65f),
                                            start = Offset(0f, lineY),
                                            end = Offset(lineEnd * titleStrikeProgress, lineY),
                                            strokeWidth = TdayDimens.BorderWidthThick.toPx(),
                                        )
                                    }
                                },
                                color = titleColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                onTextLayout = { titleLayoutResult = it },
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_clock),
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = completedAtText,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                        }

                        if (showPriorityIcon) {
                            Row(
                                modifier = Modifier.padding(end = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (showListIndicator) {
                                    Icon(
                                        imageVector = tdayListIconForKey(listMeta?.iconKey),
                                        contentDescription = stringResource(R.string.label_task_list),
                                        tint = listIndicatorColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Icon(
                                    imageVector = priorityIcon
                                        ?: ImageVector.vectorResource(R.drawable.ic_lucide_flag),
                                    contentDescription = stringResource(R.string.label_priority_task),
                                    tint = tdayPriorityColor(item.priority),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else if (showListIndicator) {
                            Icon(
                                imageVector = tdayListIconForKey(listMeta?.iconKey),
                                contentDescription = stringResource(R.string.label_task_list),
                                tint = listIndicatorColor,
                                modifier = Modifier
                                    .padding(end = 24.dp)
                                    .size(18.dp),
                            )
                        }
                    }
                }
            }
        if (showDateDivider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.58f)),
            )
        }
    }
}

@Composable
private fun CompletedCircularToggleIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = 14.dp,
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun EmptyCompletedState(
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 110.dp, bottom = 180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun priorityIconFor(priority: String): ImageVector? {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "medium" -> ImageVector.vectorResource(R.drawable.ic_lucide_flag_filled)
        "high", "urgent", "important" -> ImageVector.vectorResource(R.drawable.ic_lucide_flag_filled)
        else -> null
    }
}

private fun CompletedItem.resolveListSummary(lists: List<ListSummary>): ListSummary? {
    val name = listName?.trim()?.lowercase(Locale.getDefault()) ?: return null
    return lists.firstOrNull { it.name.trim().lowercase(Locale.getDefault()) == name }
}

private fun CompletedItem.resolveListId(lists: List<ListSummary>): String? {
    return resolveListSummary(lists)?.id
}

private fun CompletedItem.toEditableTodo(lists: List<ListSummary>): TodoItem {
    val resolvedListId = resolveListId(lists)
    val canonical = originalTodoId ?: id
    return TodoItem(
        id = canonical,
        canonicalId = canonical,
        title = title,
        description = description,
        priority = priority,
        due = due,
        rrule = rrule,
        instanceDate = instanceDate,
        pinned = false,
        completed = true,
        listId = resolvedListId,
        updatedAt = completedAt,
    )
}

private data class CompletedSection(
    val key: String,
    val title: String,
    val items: List<CompletedItem>,
)

private fun shouldShowDateDivider(
    afterItemIndex: Int,
    inSectionIndex: Int,
    sections: List<CompletedSection>,
    collapsedSectionKeys: Set<String>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val section = sections.getOrNull(inSectionIndex) ?: return false
    val currentItem = section.items.getOrNull(afterItemIndex) ?: return false
    val nextItemInSection = section.items.getOrNull(afterItemIndex + 1)
    if (nextItemInSection != null) {
        return !currentItem.completedDate()
            .isSameLocalDayAs(nextItemInSection.completedDate(), zoneId)
    }

    val nextVisibleItem = sections
        .asSequence()
        .drop(inSectionIndex + 1)
        .filter { it.key !in collapsedSectionKeys }
        .flatMap { it.items.asSequence() }
        .firstOrNull()
        ?: return false

    return !currentItem.completedDate().isSameLocalDayAs(nextVisibleItem.completedDate(), zoneId)
}

private fun CompletedItem.completedDate() = completedAt ?: due ?: Instant.EPOCH

private fun Instant.isSameLocalDayAs(other: Instant, zoneId: ZoneId): Boolean =
    LocalDate.ofInstant(this, zoneId) == LocalDate.ofInstant(other, zoneId)

private fun buildCompletedTimelineSections(
    items: List<CompletedItem>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<CompletedSection> {
    val groupedByDate = items.groupBy { item ->
        LocalDate.ofInstant(item.completedAt ?: item.due ?: Instant.EPOCH, zoneId)
    }

    return groupedByDate.keys
        .sortedDescending()
        .map { date ->
            val sectionItems = groupedByDate[date].orEmpty().sortedWith(
                compareByDescending<CompletedItem> { it.completedAt ?: it.due ?: Instant.EPOCH }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
                    .thenBy { it.id },
            )
            CompletedSection(
                key = "completed-$date",
                title = date.format(COMPLETED_SECTION_FORMATTER),
                items = sectionItems,
            )
        }
}

private val COMPLETED_TITLE_COLOR = TdayCompletedTitleAccent
private val COMPLETED_SECTION_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private val COMPLETED_ROW_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private const val COMPLETED_TITLE_COLLAPSE_DISTANCE_DP = 180f
