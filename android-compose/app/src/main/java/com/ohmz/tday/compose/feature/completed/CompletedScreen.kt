package com.ohmz.tday.compose.feature.completed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.component.TdayPullToRefreshBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.YearMonth
import java.util.Locale

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
        mutableStateOf(setOf("earlier"))
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
        TdayPullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(nestedScrollConnection),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(timelineSections, key = { it.key }) { section ->
                    val isCollapsed = collapsedSectionKeys.contains(section.key)
                    CompletedTimelineSection(
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
                        lists = uiState.lists,
                        onInfo = { item -> editTargetId = item.id },
                        onDelete = onDelete,
                        onUncomplete = onUncomplete,
                    )
                }

                if (uiState.items.isEmpty()) {
                    item {
                        EmptyCompletedState(
                            message = if (uiState.isLoading) {
                                "Loading..."
                            } else {
                                "No completed tasks yet"
                            },
                        )
                    }
                }

                uiState.errorMessage?.let { message ->
                    item {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(96.dp)) }
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompletedHeaderButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
                CompletedHeaderButton(
                    onClick = { },
                    icon = Icons.Rounded.MoreHoriz,
                    contentDescription = "More options",
                )
            }
            if (collapsedTitleAlpha > 0.001f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = collapsedTitleAlpha
                            translationY = collapsedTitleShiftY
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFFB4CDBA),
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "Completed",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB4CDBA),
                    )
                }
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
                Row(
                    modifier = Modifier.graphicsLayer {
                        alpha = expandedTitleAlpha
                        translationY = expandedTitleShiftY
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFFB4CDBA),
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "Completed",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB4CDBA),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedHeaderButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current

    androidx.compose.material3.Card(
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        shape = CircleShape,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.38f)),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = colorScheme.background),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun CompletedTimelineSection(
    section: CompletedSection,
    isCollapsed: Boolean,
    onHeaderClick: () -> Unit,
    lists: List<ListSummary>,
    onInfo: (CompletedItem) -> Unit,
    onDelete: (CompletedItem) -> Unit,
    onUncomplete: (CompletedItem) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val headerInteractionSource = remember { MutableInteractionSource() }
    val collapseChevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 180f,
        label = "completedSectionChevronRotation",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (isCollapsed) "Expand section" else "Collapse section",
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp)
                    .graphicsLayer { rotationZ = collapseChevronRotation },
            )
        }

        if (isCollapsed) {
            return@Column
        }

        if (section.items.isEmpty()) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.58f)),
            )
        } else {
            section.items.forEach { item ->
                CompletedSwipeRow(
                    item = item,
                    lists = lists,
                    onInfo = { onInfo(item) },
                    onDelete = { onDelete(item) },
                    onUncomplete = { onUncomplete(item) },
                )
            }
        }
    }
}

@Composable
private fun CompletedSwipeRow(
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
    val maxElasticDragPx = actionRevealPx * 1.22f
    var targetOffsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var pendingUncomplete by remember(item.id) { mutableStateOf(false) }
    var rowVisible by remember(item.id) { mutableStateOf(true) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(),
        label = "completedSwipeOffset",
    )
    val showCompletedState = !pendingUncomplete
    val completedAtText = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(item.completedAt ?: item.due)
    val createdAtText = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(item.dtstart)
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

    AnimatedVisibility(
        visible = rowVisible,
        exit = fadeOut(animationSpec = tween(durationMillis = 220)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
                        contentDescription = "Edit task",
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
                        contentDescription = "Delete task",
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
                            if (targetOffsetX != 0f) targetOffsetX = 0f
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
                        Icon(
                            imageVector = if (showCompletedState) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = "Undo complete",
                            tint = if (showCompletedState) {
                                Color(0xFF6FBF86)
                            } else {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    if (pendingUncomplete) return@clickable
                                    ViewCompat.performHapticFeedback(
                                        view,
                                        HapticFeedbackConstantsCompat.CLOCK_TICK,
                                    )
                                    targetOffsetX = 0f
                                    pendingUncomplete = true
                                    coroutineScope.launch {
                                        delay(500)
                                        rowVisible = false
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
                                color = if (showCompletedState) {
                                    colorScheme.onSurface.copy(alpha = 0.78f)
                                } else {
                                    colorScheme.onSurface
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (showCompletedState) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                },
                            )
                            Text(
                                text = "Created: $createdAtText",
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.84f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                            Text(
                                text = "Completed: $completedAtText",
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.84f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }

                        if (showPriorityFlag) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (showListIndicator) {
                                    Icon(
                                        imageVector = listIconForKey(listMeta?.iconKey),
                                        contentDescription = "Task list",
                                        tint = listIndicatorColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.Flag,
                                    contentDescription = "High priority",
                                    tint = priorityColor("high"),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else if (showListIndicator) {
                            Icon(
                                imageVector = listIconForKey(listMeta?.iconKey),
                                contentDescription = "Task list",
                                tint = listIndicatorColor,
                                modifier = Modifier.size(18.dp),
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
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun priorityColor(priority: String): Color {
    return when (priority.lowercase()) {
        "high" -> Color(0xFFE56A6A)
        "medium" -> Color(0xFFE3B368)
        else -> Color(0xFF6FBF86)
    }
}

private fun isHighPriority(priority: String): Boolean {
    return when (priority.trim().lowercase()) {
        "high", "urgent", "important" -> true
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
        dtstart = dtstart,
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
    val sorted = items.sortedBy { it.due }
    val groupedByDate = sorted.groupBy { item ->
        LocalDate.ofInstant(item.due, zoneId)
    }
    val today = LocalDate.now(zoneId)
    val horizonStart = today.plusDays(7)
    val currentMonth = YearMonth.from(today)

    val sections = mutableListOf<CompletedSection>()
    fun daySection(date: LocalDate, title: String): CompletedSection {
        return CompletedSection(
            key = "day-$date",
            title = title,
            items = groupedByDate[date].orEmpty(),
        )
    }

    val earlierItems = groupedByDate.asSequence().filter { (date, _) -> date < today }
        .flatMap { (_, dayItems) -> dayItems.asSequence() }
        .sortedBy { it.due }
        .toList()
    if (earlierItems.isNotEmpty()) {
        sections += CompletedSection(
            key = "earlier",
            title = "Earlier",
            items = earlierItems,
        )
    }

    sections += daySection(today, "Today")
    sections += daySection(today.plusDays(1), "Tomorrow")
    for (offset in 2..6) {
        val date = today.plusDays(offset.toLong())
        sections += daySection(
            date = date,
            title = date.format(COMPLETED_DAY_FORMATTER),
        )
    }

    val restOfCurrentMonthItems = groupedByDate.asSequence().filter { (date, _) ->
        date >= horizonStart && YearMonth.from(date) == currentMonth
    }.flatMap { (_, dayItems) -> dayItems.asSequence() }.sortedBy { it.due }.toList()
    val monthName = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    sections += CompletedSection(
        key = "rest-$currentMonth",
        title = "Rest of $monthName",
        items = restOfCurrentMonthItems,
    )

    val futureMonthsWithData =
        groupedByDate.keys.asSequence().filter { it >= horizonStart }.map { YearMonth.from(it) }
            .toSet()
    val minimumFinalMonth = YearMonth.of(currentMonth.year, 12)
    val finalMonth = maxOf(
        minimumFinalMonth,
        futureMonthsWithData.maxOrNull() ?: minimumFinalMonth,
    )

    var targetMonth = currentMonth.plusMonths(1)
    while (targetMonth <= finalMonth) {
        val monthItems = groupedByDate.asSequence().filter { (date, _) ->
            date >= horizonStart && YearMonth.from(date) == targetMonth
        }.flatMap { (_, dayItems) -> dayItems.asSequence() }.sortedBy { it.due }.toList()
        sections += CompletedSection(
            key = "month-$targetMonth",
            title = completedMonthTitle(targetMonth, currentMonth.year),
            items = monthItems,
        )
        targetMonth = targetMonth.plusMonths(1)
    }

    return sections
}

private fun completedMonthTitle(
    month: YearMonth,
    currentYear: Int,
): String {
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return if (month.year == currentYear) {
        monthName
    } else {
        "$monthName ${month.year}"
    }
}

private val COMPLETED_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d")
private const val COMPLETED_TITLE_COLLAPSE_DISTANCE_DP = 180f
