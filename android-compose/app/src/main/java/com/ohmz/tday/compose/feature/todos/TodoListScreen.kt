package com.ohmz.tday.compose.feature.todos

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.component.TdayPullToRefreshBox
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.YearMonth
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    uiState: TodoListUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddTask: (payload: CreateTaskPayload) -> Unit,
    onComplete: (todo: TodoItem) -> Unit,
    onDelete: (todo: TodoItem) -> Unit,
    onTogglePin: (todo: TodoItem) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val usesTodayStyle =
        uiState.mode == TodoListMode.TODAY ||
        uiState.mode == TodoListMode.SCHEDULED ||
        uiState.mode == TodoListMode.ALL ||
        uiState.mode == TodoListMode.PRIORITY ||
        uiState.mode == TodoListMode.LIST
    val titleColor = when (uiState.mode) {
        TodoListMode.TODAY -> Color(0xFF3D7FEA)
        TodoListMode.SCHEDULED -> Color(0xFFDDB37D)
        TodoListMode.ALL -> colorScheme.onSurface
        TodoListMode.PRIORITY -> Color(0xFFC8798B)
        else -> colorScheme.onSurface
    }
    val showSectionedTimeline =
        uiState.mode == TodoListMode.TODAY ||
        uiState.mode == TodoListMode.SCHEDULED ||
        uiState.mode == TodoListMode.ALL ||
        uiState.mode == TodoListMode.PRIORITY ||
        uiState.mode == TodoListMode.LIST
    val timelineSections = remember(uiState.mode, uiState.items) {
        buildTimelineSections(
            mode = uiState.mode,
            items = uiState.items,
        )
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val maxTodayCollapsePx = with(density) { TODAY_TITLE_COLLAPSE_DISTANCE_DP.dp.toPx() }
    var todayHeaderCollapsePx by rememberSaveable { mutableFloatStateOf(0f) }
    val todayCollapseProgressTarget = if (usesTodayStyle && maxTodayCollapsePx > 0f) {
        (todayHeaderCollapsePx / maxTodayCollapsePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val todayNestedScrollConnection = remember(usesTodayStyle, listState, maxTodayCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!usesTodayStyle) return Offset.Zero
                val deltaY = available.y
                if (deltaY < 0f) {
                    val previous = todayHeaderCollapsePx
                    val next = (previous - deltaY).coerceIn(0f, maxTodayCollapsePx)
                    val consumed = next - previous
                    if (consumed > 0f) {
                        todayHeaderCollapsePx = next
                        return Offset(0f, -consumed)
                    }
                    return Offset.Zero
                }

                if (deltaY > 0f) {
                    val isListAtTop =
                        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    if (!isListAtTop) return Offset.Zero
                    val previous = todayHeaderCollapsePx
                    val next = (previous - deltaY).coerceIn(0f, maxTodayCollapsePx)
                    val consumed = previous - next
                    if (consumed > 0f) {
                        todayHeaderCollapsePx = next
                        return Offset(0f, consumed)
                    }
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!usesTodayStyle) return Velocity.Zero
                if (available.y < 0f && todayHeaderCollapsePx < maxTodayCollapsePx) {
                    todayHeaderCollapsePx = maxTodayCollapsePx
                    return available
                }
                val isListAtTop =
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && isListAtTop && todayHeaderCollapsePx > 0f) {
                    todayHeaderCollapsePx = 0f
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val todayCollapseProgress by animateFloatAsState(
        targetValue = todayCollapseProgressTarget,
        label = "todayTitleCollapseProgress",
    )
    var showCreateTaskSheet by rememberSaveable { mutableStateOf(false) }
    var quickAddStartEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var quickAddDueEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    val fabInteractionSource = remember { MutableInteractionSource() }
    val fabPressed by fabInteractionSource.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.93f else 1f,
        label = "todoFabScale",
    )
    val fabElevation by animateDpAsState(
        targetValue = if (fabPressed) 4.dp else 14.dp,
        label = "todoFabElevation",
    )
    val fabOffsetY by animateDpAsState(
        targetValue = if (fabPressed) 2.dp else 0.dp,
        label = "todoFabOffsetY",
    )

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            if (usesTodayStyle) {
                TodayTopBar(
                    onBack = onBack,
                    collapseProgress = todayCollapseProgress,
                    title = uiState.title,
                    titleColor = titleColor,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            CreateTaskButton(
                modifier = Modifier
                    .offset(y = fabOffsetY)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    },
                interactionSource = fabInteractionSource,
                elevation = fabElevation,
                onClick = {
                    quickAddStartEpochMs = null
                    quickAddDueEpochMs = null
                    showCreateTaskSheet = true
                },
            )
        },
    ) { padding ->
        TdayPullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(
                    if (usesTodayStyle) {
                        Modifier.nestedScroll(todayNestedScrollConnection)
                    } else {
                        Modifier
                    },
                ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = if (usesTodayStyle) {
                    PaddingValues(horizontal = 18.dp, vertical = 2.dp)
                } else {
                    PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                },
                verticalArrangement = Arrangement.spacedBy(if (usesTodayStyle) 18.dp else 8.dp),
            ) {
                if (!showSectionedTimeline && uiState.items.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                modifier = Modifier.padding(18.dp),
                                text = if (uiState.isLoading) "Loading..." else "No tasks yet.",
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (showSectionedTimeline) {
                    items(timelineSections, key = { it.key }) { section ->
                        TimelineSection(
                            section = section,
                            useMinimalStyle = usesTodayStyle,
                            onTapForQuickAdd = section.quickAddDefaults?.let { quickAdd ->
                                {
                                    quickAddStartEpochMs = quickAdd.first
                                    quickAddDueEpochMs = quickAdd.second
                                    showCreateTaskSheet = true
                                }
                            },
                            onComplete = onComplete,
                            onDelete = onDelete,
                            onTogglePin = onTogglePin,
                        )
                    }
                    if (uiState.items.isEmpty()) {
                        item {
                            EmptyTimelineState(
                                message = "No Reminders",
                                useMinimalStyle = usesTodayStyle,
                            )
                        }
                    }
                } else {
                    items(uiState.items, key = { it.id }) { todo ->
                        if (usesTodayStyle) {
                            TodayTodoRow(
                                todo = todo,
                                onComplete = { onComplete(todo) },
                                onDelete = { onDelete(todo) },
                                onPin = { onTogglePin(todo) },
                            )
                        } else {
                            TodoRow(
                                todo = todo,
                                onComplete = { onComplete(todo) },
                                onDelete = { onDelete(todo) },
                                onPin = { onTogglePin(todo) },
                            )
                        }
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

                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (showCreateTaskSheet) {
        CreateTaskBottomSheet(
            lists = uiState.lists,
            defaultListId = if (uiState.mode == TodoListMode.LIST) uiState.listId else null,
            initialStartEpochMs = quickAddStartEpochMs,
            initialDueEpochMs = quickAddDueEpochMs,
            onDismiss = {
                showCreateTaskSheet = false
                quickAddStartEpochMs = null
                quickAddDueEpochMs = null
            },
            onCreateTask = { payload ->
                onAddTask(payload)
                showCreateTaskSheet = false
                quickAddStartEpochMs = null
                quickAddDueEpochMs = null
            },
        )
    }
}

@Composable
private fun TodayTopBar(
    onBack: () -> Unit,
    collapseProgress: Float,
    title: String,
    titleColor: Color,
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
                TodayHeaderButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
                TodayHeaderButton(
                    onClick = { },
                    icon = Icons.Rounded.MoreHoriz,
                    contentDescription = "More options",
                )
            }
            if (collapsedTitleAlpha > 0.001f) {
                Text(
                    text = title,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = collapsedTitleAlpha
                            translationY = collapsedTitleShiftY
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
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
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
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
private fun TodayHeaderButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    Card(
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        shape = CircleShape,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.38f)),
        colors = CardDefaults.cardColors(containerColor = colorScheme.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
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
private fun CreateTaskButton(
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    elevation: Dp,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier,
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.primary),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation,
            pressedElevation = elevation,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .drawWithCache {
                    val pearlWash = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color(0xFFE7F3FF).copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        start = Offset(size.width * 0.08f, size.height * 0.05f),
                        end = Offset(size.width * 0.9f, size.height * 0.88f),
                    )
                    val depthShade = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f),
                        ),
                        start = Offset(size.width * 0.4f, size.height * 0.3f),
                        end = Offset(size.width, size.height),
                    )
                    onDrawWithContent {
                        drawRect(pearlWash)
                        drawRect(depthShade)
                        drawContent()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Create task",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun TimelineSection(
    section: TodoSection,
    useMinimalStyle: Boolean,
    onTapForQuickAdd: (() -> Unit)?,
    onComplete: (TodoItem) -> Unit,
    onDelete: (TodoItem) -> Unit,
    onTogglePin: (TodoItem) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val headerInteractionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onTapForQuickAdd != null) {
                        Modifier.clickable(
                            interactionSource = headerInteractionSource,
                            indication = null,
                            onClick = onTapForQuickAdd,
                        )
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.title,
                color = if (useMinimalStyle) {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                } else {
                    colorScheme.onSurfaceVariant
                },
                style = if (useMinimalStyle) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (section.items.isEmpty()) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.58f)),
            )
        } else {
            section.items.forEach { todo ->
                if (useMinimalStyle) {
                    TodayTodoRow(
                        todo = todo,
                        onComplete = { onComplete(todo) },
                        onDelete = { onDelete(todo) },
                        onPin = { onTogglePin(todo) },
                    )
                } else {
                    TodoRow(
                        todo = todo,
                        onComplete = { onComplete(todo) },
                        onDelete = { onDelete(todo) },
                        onPin = { onTogglePin(todo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTimelineState(
    message: String,
    useMinimalStyle: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = if (useMinimalStyle) 110.dp else 88.dp,
                bottom = if (useMinimalStyle) 180.dp else 140.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = colorScheme.onSurfaceVariant.copy(alpha = if (useMinimalStyle) 0.52f else 0.85f),
            style = if (useMinimalStyle) {
                MaterialTheme.typography.displaySmall
            } else {
                MaterialTheme.typography.headlineSmall
            },
            fontWeight = FontWeight.Medium,
        )
    }
}

private data class TodoSection(
    val key: String,
    val title: String,
    val items: List<TodoItem>,
    val quickAddDefaults: Pair<Long, Long>? = null,
)

private enum class TodaySectionSlot {
    MORNING,
    AFTERNOON,
    TONIGHT,
}

private fun buildTimelineSections(
    mode: TodoListMode,
    items: List<TodoItem>,
): List<TodoSection> {
    val zoneId = ZoneId.systemDefault()
    return when (mode) {
        TodoListMode.TODAY -> buildTodaySections(items, zoneId)
        TodoListMode.SCHEDULED -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = true,
        )
        TodoListMode.ALL, TodoListMode.PRIORITY, TodoListMode.LIST -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = false,
        )
        else -> emptyList()
    }
}

private fun buildTodaySections(
    items: List<TodoItem>,
    zoneId: ZoneId,
): List<TodoSection> {
    val sorted = items.sortedBy { it.due }
    val noon = LocalTime.NOON
    val eveningStartBoundary = LocalTime.of(18, 0)

    fun sectionOf(todo: TodoItem): TodaySectionSlot {
        val dueTime = todo.due.atZone(zoneId).toLocalTime()
        return when {
            // Requested boundaries:
            // Morning: 12:01 AM -> 12:00 PM (inclusive of 12:00 PM)
            // Afternoon: 12:01 PM -> 6:00 PM
            // Tonight: 6:01 PM -> end of day
            dueTime <= noon -> TodaySectionSlot.MORNING
            dueTime <= eveningStartBoundary -> TodaySectionSlot.AFTERNOON
            else -> TodaySectionSlot.TONIGHT
        }
    }

    return listOf(
        TodoSection(
            key = "today-morning",
            title = "Morning",
            items = sorted.filter { sectionOf(it) == TodaySectionSlot.MORNING },
            quickAddDefaults = quickAddDefaultsForTodaySection(
                slot = TodaySectionSlot.MORNING,
                zoneId = zoneId,
            ),
        ),
        TodoSection(
            key = "today-afternoon",
            title = "Afternoon",
            items = sorted.filter { sectionOf(it) == TodaySectionSlot.AFTERNOON },
            quickAddDefaults = quickAddDefaultsForTodaySection(
                slot = TodaySectionSlot.AFTERNOON,
                zoneId = zoneId,
            ),
        ),
        TodoSection(
            key = "today-tonight",
            title = "Tonight",
            items = sorted.filter { sectionOf(it) == TodaySectionSlot.TONIGHT },
            quickAddDefaults = quickAddDefaultsForTodaySection(
                slot = TodaySectionSlot.TONIGHT,
                zoneId = zoneId,
            ),
        ),
    )
}

private fun buildScheduledSections(
    items: List<TodoItem>,
    zoneId: ZoneId,
    futureOnly: Boolean,
): List<TodoSection> {
    val now = Instant.now()
    val sorted = items
        .asSequence()
        .filter { todo ->
            if (futureOnly) !todo.due.isBefore(now) else true
        }
        .sortedBy { it.due }
        .toList()
    val groupedByDate = sorted.groupBy { todo ->
        LocalDate.ofInstant(todo.due, zoneId)
    }
    val today = LocalDate.now(zoneId)
    val horizonStart = today.plusDays(7)
    val currentMonth = YearMonth.from(today)

    val sections = mutableListOf<TodoSection>()
    fun daySection(date: LocalDate, title: String): TodoSection {
        return TodoSection(
            key = "day-$date",
            title = title,
            items = groupedByDate[date].orEmpty(),
            quickAddDefaults = quickAddDefaultsForDate(
                date = date,
                zoneId = zoneId,
            ),
        )
    }

    if (!futureOnly) {
        val earlierItems = groupedByDate
            .asSequence()
            .filter { (date, _) -> date < today }
            .flatMap { (_, dayItems) -> dayItems.asSequence() }
            .sortedBy { it.due }
            .toList()
        if (earlierItems.isNotEmpty()) {
            sections += TodoSection(
                key = "earlier",
                title = "Earlier",
                items = earlierItems,
                quickAddDefaults = quickAddDefaultsForDate(
                    date = today,
                    zoneId = zoneId,
                ),
            )
        }
    }

    sections += daySection(today, "Today")
    sections += daySection(today.plusDays(1), "Tomorrow")
    for (offset in 2..6) {
        val date = today.plusDays(offset.toLong())
        sections += daySection(
            date = date,
            title = date.format(SCHEDULED_DAY_FORMATTER),
        )
    }

    val restOfCurrentMonthItems = groupedByDate
        .asSequence()
        .filter { (date, _) ->
            date >= horizonStart && YearMonth.from(date) == currentMonth
        }
        .flatMap { (_, dayItems) -> dayItems.asSequence() }
        .sortedBy { it.due }
        .toList()
    val monthName = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    sections += TodoSection(
        key = "rest-$currentMonth",
        title = "Rest of $monthName",
        items = restOfCurrentMonthItems,
        quickAddDefaults = quickAddDefaultsForDate(
            date = currentMonth.atEndOfMonth(),
            zoneId = zoneId,
        ),
    )

    val futureMonthsWithData = groupedByDate.keys
        .asSequence()
        .filter { it >= horizonStart }
        .map { YearMonth.from(it) }
        .toSet()
    val minimumFinalMonth = YearMonth.of(currentMonth.year, 12)
    val finalMonth = maxOf(
        minimumFinalMonth,
        futureMonthsWithData.maxOrNull() ?: minimumFinalMonth,
    )

    var targetMonth = currentMonth.plusMonths(1)
    while (targetMonth <= finalMonth) {
        val monthItems = groupedByDate
            .asSequence()
            .filter { (date, _) ->
                date >= horizonStart && YearMonth.from(date) == targetMonth
            }
            .flatMap { (_, dayItems) -> dayItems.asSequence() }
            .sortedBy { it.due }
            .toList()
        sections += TodoSection(
            key = "month-$targetMonth",
            title = monthTitle(targetMonth, currentMonth.year),
            items = monthItems,
            quickAddDefaults = quickAddDefaultsForDate(
                date = targetMonth.atDay(1),
                zoneId = zoneId,
            ),
        )
        targetMonth = targetMonth.plusMonths(1)
    }

    return sections
}

private fun monthTitle(
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

private val SCHEDULED_DAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM d")

private fun quickAddDefaultsForDate(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Pair<Long, Long> {
    val startTime = LocalTime.of(6, 0)
    val dueTime = LocalTime.of(23, 59)
    val due = ZonedDateTime.of(date, dueTime, zoneId)
    val start = ZonedDateTime.of(date, startTime, zoneId)
    return start.toInstant().toEpochMilli() to due.toInstant().toEpochMilli()
}

private fun quickAddDefaultsForTodaySection(
    slot: TodaySectionSlot,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Pair<Long, Long> {
    when (slot) {
        TodaySectionSlot.MORNING,
        TodaySectionSlot.AFTERNOON,
        TodaySectionSlot.TONIGHT,
        -> Unit
    }
    val today = LocalDate.now(zoneId)
    return quickAddDefaultsForDate(
        date = today,
        zoneId = zoneId,
    )
}

private const val TODAY_TITLE_COLLAPSE_DISTANCE_DP = 180f

@Composable
private fun TodayTodoRow(
    todo: TodoItem,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dueText = DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(todo.due)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Complete",
                tint = priorityColor(todo.priority),
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onComplete() },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = todo.title,
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = dueText,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            IconButton(onClick = onPin) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = "Pin",
                    tint = if (todo.pinned) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = colorScheme.error,
                )
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
private fun TodoRow(
    todo: TodoItem,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val due = DateTimeFormatter.ofPattern("MMM d, h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(todo.due)

    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Complete",
                    tint = priorityColor(todo.priority),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onComplete() },
                )

                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = todo.title,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = due,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPin) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pin",
                        tint = if (todo.pinned) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        tint = colorScheme.error,
                    )
                }
            }
        }
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
