package com.ohmz.tday.compose.feature.completed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.theme.TdayDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val density = LocalDensity.current
    val maxCollapsePx = with(density) { COMPLETED_TITLE_COLLAPSE_DISTANCE_DP.dp.toPx() }
    var headerCollapsePx by rememberSaveable { mutableFloatStateOf(0f) }
    val collapseProgressTarget = if (maxCollapsePx > 0f) {
        (headerCollapsePx / maxCollapsePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val nestedScrollConnection = remember(listState, maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val deltaY = available.y
                if (deltaY < 0f) {
                    val previous = headerCollapsePx
                    val next = (previous - deltaY).coerceIn(0f, maxCollapsePx)
                    val consumed = next - previous
                    if (consumed > 0f) {
                        headerCollapsePx = next
                        return Offset(0f, -consumed)
                    }
                    return Offset.Zero
                }

                if (deltaY > 0f) {
                    val isListAtTop =
                        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    if (!isListAtTop) return Offset.Zero
                    val previous = headerCollapsePx
                    val next = (previous - deltaY).coerceIn(0f, maxCollapsePx)
                    val consumed = previous - next
                    if (consumed > 0f) {
                        headerCollapsePx = next
                        return Offset(0f, consumed)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y < 0f && headerCollapsePx < maxCollapsePx) {
                    headerCollapsePx = maxCollapsePx
                    return available
                }
                val isListAtTop =
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && isListAtTop && headerCollapsePx > 0f) {
                    headerCollapsePx = 0f
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val collapseProgress by animateFloatAsState(
        targetValue = collapseProgressTarget,
        label = "completedTitleCollapseProgress",
    )
    var collapsedSectionKeys by rememberSaveable {
        mutableStateOf(emptySet<String>())
    }
    var editTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val editTarget = remember(editTargetId, uiState.items) {
        editTargetId?.let { targetId -> uiState.items.firstOrNull { it.id == targetId } }
    }

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            CompletedTopBar(
                onBack = onBack,
                collapseProgress = collapseProgress,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(nestedScrollConnection),
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
                            .padding(top = if (sectionIndex == 0) 0.dp else 8.dp),
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
                                    .padding(top = 4.dp),
                                item = completed,
                                lists = uiState.lists,
                                onInfo = { editTargetId = completed.id },
                                onDelete = { onDelete(completed) },
                                onUncomplete = { onUncomplete(completed) },
                            )
                        }
                    }
                }
            }

            if (uiState.items.isEmpty()) {
                item {
                    EmptyCompletedState(
                        message = if (uiState.isLoading) {
                            stringResource(R.string.label_loading)
                        } else {
                            stringResource(R.string.completed_empty)
                        },
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
                    icon = Icons.Rounded.ChevronLeft,
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
                imageVector = Icons.Rounded.ExpandMore,
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
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onUncomplete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val actionRevealPx = with(density) { 130.dp.toPx() }
    val swipeHintOffsetPx = with(density) { 36.dp.toPx() }.coerceAtMost(actionRevealPx * 0.28f)
    val maxElasticDragPx = actionRevealPx * 1.22f
    var targetOffsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var swipeHinting by remember(item.id) { mutableStateOf(false) }
    var restorePhase by remember(item.id) { mutableStateOf(CompletedRestorePhase.Completed) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "completedSwipeOffset",
    )
    val showCompletedCheckmark = restorePhase == CompletedRestorePhase.Completed
    val showStrikethrough =
        restorePhase == CompletedRestorePhase.Completed || restorePhase == CompletedRestorePhase.Unchecked
    val isFading = restorePhase == CompletedRestorePhase.Fading
    val isRestoring = restorePhase != CompletedRestorePhase.Completed
    val rowAlpha by animateFloatAsState(
        targetValue = if (isFading) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "completedRestoreRowAlpha",
    )
    val rowScale by animateFloatAsState(
        targetValue = if (isFading) 0.985f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "completedRestoreRowScale",
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
    val completedAtText = COMPLETED_ROW_TIME_FORMATTER
        .withZone(ZoneId.systemDefault())
        .format(item.completedAt ?: item.due)
    val listMeta = item.resolveListSummary(lists)
    val listIndicatorColor = listMeta?.color?.let(::listAccentColor)
        ?: item.listColor?.let(::listAccentColor)
        ?: colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
    val showListIndicator = !item.listName.isNullOrBlank() || listMeta != null
    val showPriorityFlag = isHighPriority(item.priority)
    val rowShape = RoundedCornerShape(16.dp)
    val actionContainerColor =
        colorScheme.surfaceVariant.copy(alpha = if (colorScheme.background.luminance() < 0.5f) 0.62f else 0.92f)
    val foregroundColor = colorScheme.background

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rowAlpha
                scaleX = rowScale
                scaleY = rowScale
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .background(actionContainerColor, rowShape),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwipeActionCircle(
                        icon = Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.action_edit_task),
                        tint = colorScheme.onSurface,
                        background = colorScheme.surface,
                        onClick = {
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.CLOCK_TICK,
                            )
                            onInfo()
                            targetOffsetX = 0f
                        },
                    )
                    SwipeActionCircle(
                        icon = Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.action_delete_task),
                        tint = colorScheme.error,
                        background = colorScheme.surface,
                        onClick = {
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.CLOCK_TICK,
                            )
                            onDelete()
                            targetOffsetX = 0f
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
                                targetOffsetX = (targetOffsetX + delta).coerceIn(
                                    -maxElasticDragPx,
                                    0f,
                                )
                            },
                            onDragStopped = { velocity ->
                                val flingOpen = velocity < -1450f
                                val dragOpen = targetOffsetX < -(actionRevealPx * 0.32f)
                                targetOffsetX = if (flingOpen || dragOpen) -actionRevealPx else 0f
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (targetOffsetX != 0f) {
                                targetOffsetX = 0f
                            } else if (!swipeHinting && !isRestoring) {
                                swipeHinting = true
                                coroutineScope.launch {
                                    targetOffsetX = -swipeHintOffsetPx
                                    delay(150)
                                    targetOffsetX = 0f
                                    delay(360)
                                    swipeHinting = false
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
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = stringResource(R.string.label_undo_complete),
                            tint = if (showCompletedCheckmark) {
                                Color(0xFF6FBF86)
                            } else {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            },
                            enabled = !isRestoring,
                            onClick = {
                                ViewCompat.performHapticFeedback(
                                    view,
                                    HapticFeedbackConstantsCompat.CLOCK_TICK,
                                )
                                targetOffsetX = 0f
                                coroutineScope.launch {
                                    restorePhase = CompletedRestorePhase.Unchecked
                                    delay(180)
                                    restorePhase = CompletedRestorePhase.Unstruck
                                    delay(180)
                                    restorePhase = CompletedRestorePhase.Fading
                                    delay(220)
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
                                color = titleColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textDecoration = if (showStrikethrough) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                },
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
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

                        if (showPriorityFlag) {
                            Row(
                                modifier = Modifier.padding(end = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (showListIndicator) {
                                    Icon(
                                        imageVector = listIconForKey(listMeta?.iconKey),
                                        contentDescription = stringResource(R.string.label_task_list),
                                        tint = listIndicatorColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.Flag,
                                    contentDescription = stringResource(R.string.label_priority_task),
                                    tint = priorityColor(item.priority),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else if (showListIndicator) {
                            Icon(
                                imageVector = listIconForKey(listMeta?.iconKey),
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
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.58f)),
            )
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
private fun SwipeActionCircle(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        label = "completedSwipeActionScale",
    )
    Card(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
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
private fun priorityColor(priority: String): Color {
    return when (priority.lowercase()) {
        "high", "urgent", "important" -> Color(0xFFE56A6A)
        "medium" -> Color(0xFFE3B368)
        else -> Color(0xFF6FBF86)
    }
}

private fun isHighPriority(priority: String): Boolean {
    return when (priority.trim().lowercase()) {
        "medium", "high", "urgent", "important" -> true
        else -> false
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

private fun listAccentColor(colorKey: String?): Color {
    return when (colorKey?.trim()?.uppercase(Locale.getDefault())) {
        "RED" -> Color(0xFFE65E52)
        "ORANGE" -> Color(0xFFF29F38)
        "YELLOW" -> Color(0xFFF3D04A)
        "LIME" -> Color(0xFF8ACF56)
        "BLUE" -> Color(0xFF5C9FE7)
        "PURPLE" -> Color(0xFF8D6CE2)
        "PINK" -> Color(0xFFDF6DAA)
        "TEAL" -> Color(0xFF4EB5B0)
        "CORAL" -> Color(0xFFE3876D)
        "GOLD" -> Color(0xFFCFAB57)
        "DEEP_BLUE" -> Color(0xFF4B73D6)
        "ROSE" -> Color(0xFFD9799A)
        "LIGHT_RED" -> Color(0xFFE48888)
        "BRICK" -> Color(0xFFB86A5C)
        "SLATE" -> Color(0xFF7B8593)
        else -> Color(0xFF6EA8E1)
    }
}

private fun listIconForKey(iconKey: String?): ImageVector {
    return when (iconKey?.trim()?.lowercase(Locale.getDefault())) {
        "sun" -> Icons.Rounded.WbSunny
        "calendar" -> Icons.Rounded.CalendarMonth
        "schedule" -> Icons.Rounded.Schedule
        "flag" -> Icons.Rounded.Flag
        "check" -> Icons.Rounded.Check
        "inbox" -> Icons.Rounded.Inbox
        "book" -> Icons.AutoMirrored.Rounded.MenuBook
        "briefcase" -> Icons.Rounded.Work
        "health" -> Icons.Rounded.LocalHospital
        "fitness" -> Icons.Rounded.FitnessCenter
        "food" -> Icons.Rounded.Restaurant
        "cocktail" -> Icons.Rounded.LocalBar
        "music" -> Icons.Rounded.MusicNote
        "travel" -> Icons.Rounded.Flight
        "car" -> Icons.Rounded.DirectionsCar
        "gift" -> Icons.Rounded.CardGiftcard
        "home" -> Icons.Rounded.Home
        else -> Icons.AutoMirrored.Rounded.List
    }
}

private data class CompletedSection(
    val key: String,
    val title: String,
    val items: List<CompletedItem>,
)

private fun buildCompletedTimelineSections(
    items: List<CompletedItem>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<CompletedSection> {
    val groupedByDate = items.groupBy { item ->
        LocalDate.ofInstant(item.completedAt ?: item.due, zoneId)
    }

    return groupedByDate.keys
        .sortedDescending()
        .map { date ->
            val sectionItems = groupedByDate[date].orEmpty().sortedWith(
                compareByDescending<CompletedItem> { it.completedAt ?: it.due }
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

private val COMPLETED_TITLE_COLOR = Color(0xFF5E6878)
private val COMPLETED_SECTION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
private val COMPLETED_ROW_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private const val COMPLETED_TITLE_COLLAPSE_DISTANCE_DP = 180f
