package com.ohmz.tday.compose.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.DeleteSweep
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreateTask: (CreateTaskPayload) -> Unit,
    onCompleteTask: (TodoItem) -> Unit,
    onUncompleteTask: (CompletedItem) -> Unit,
    onUpdateTask: (TodoItem, CreateTaskPayload) -> Unit,
    onDelete: (TodoItem) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zoneId) }
    val minNavigableMonth = remember(zoneId) { YearMonth.now(zoneId) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val monthTitleSnapThresholdPx = remember(density) { with(density) { 58.dp.roundToPx() } }
    var visibleMonthIso by rememberSaveable { mutableStateOf(minNavigableMonth.toString()) }
    var selectedDateIso by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedViewKey by rememberSaveable { mutableStateOf(CalendarViewMode.MONTH.name) }

    val visibleMonth = remember(visibleMonthIso) { YearMonth.parse(visibleMonthIso) }
    val selectedDate = remember(selectedDateIso) { LocalDate.parse(selectedDateIso) }
    val selectedViewMode = remember(selectedViewKey) {
        CalendarViewMode.entries.firstOrNull { it.name == selectedViewKey } ?: CalendarViewMode.MONTH
    }
    val tasksByDate = remember(uiState.items, zoneId) {
        uiState.items
            .groupBy { LocalDate.ofInstant(it.due, zoneId) }
            .mapValues { (_, tasks) -> tasks.sortedBy { it.due } }
    }
    val completedTasksByDate = remember(uiState.completedItems, zoneId) {
        uiState.completedItems
            .groupBy { LocalDate.ofInstant(it.due, zoneId) }
            .mapValues { (_, tasks) -> tasks.sortedBy { it.due } }
    }
    val selectedDatePendingTasks = tasksByDate[selectedDate].orEmpty()
    val selectedDateCompletedTasks = completedTasksByDate[selectedDate].orEmpty()
    fun canNavigateTo(date: LocalDate): Boolean = YearMonth.from(date) >= minNavigableMonth
    fun selectDate(date: LocalDate) {
        if (!canNavigateTo(date)) return
        visibleMonthIso = YearMonth.from(date).toString()
        selectedDateIso = date.toString()
    }

    var editTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateTaskSheet by rememberSaveable { mutableStateOf(false) }
    var createStartEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var createDueEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    val editTarget = remember(editTargetId, uiState.items) {
        editTargetId?.let { targetId ->
            uiState.items.firstOrNull { it.id == targetId }
        }
    }
    fun openCreateTaskSheetForSelectedDate() {
        val currentDate = LocalDate.now(zoneId)
        val (prefillStart, prefillDue) = if (selectedDate == currentDate) {
            val nowTime = java.time.LocalTime.now(zoneId)
            val start = selectedDate.atTime(nowTime).atZone(zoneId)
            start to start.plusHours(1)
        } else {
            val start = selectedDate.atTime(6, 0).atZone(zoneId)
            val due = selectedDate.atTime(21, 0).atZone(zoneId)
            start to due
        }
        createStartEpochMs = prefillStart.toInstant().toEpochMilli()
        createDueEpochMs = prefillDue.toInstant().toEpochMilli()
        showCreateTaskSheet = true
    }
    LaunchedEffect(listState.isScrollInProgress, monthTitleSnapThresholdPx) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        if (listState.firstVisibleItemIndex != 0) return@LaunchedEffect
        val offset = listState.firstVisibleItemScrollOffset
        if (offset in 1 until monthTitleSnapThresholdPx) {
            listState.animateScrollBy(
                value = -offset.toFloat(),
                animationSpec = tween(
                    durationMillis = 240,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CalendarTopBar(
                onBack = onBack,
                onJumpToday = {
                    selectDate(LocalDate.now(zoneId))
                },
            )
        },
        floatingActionButton = {
            CalendarCreateTaskFab(
                onClick = { openCreateTaskSheetForSelectedDate() },
            )
        },
    ) { padding ->
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = listState,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    CalendarViewModeTabs(
                        selectedMode = selectedViewMode,
                        onModeSelected = { mode ->
                            selectedViewKey = mode.name
                            if (mode != CalendarViewMode.MONTH) {
                                visibleMonthIso = YearMonth.from(selectedDate).toString()
                            }
                        },
                    )
                }

                item {
                    AnimatedContent(
                        targetState = selectedViewMode,
                        transitionSpec = {
                            val enteringForward = targetState.ordinal > initialState.ordinal
                            val enter = slideInHorizontally(
                                animationSpec = tween(durationMillis = 200),
                                initialOffsetX = { fullWidth ->
                                    if (enteringForward) fullWidth / 4 else -fullWidth / 4
                                },
                            ) + fadeIn(animationSpec = tween(durationMillis = 200))
                            val exit = slideOutHorizontally(
                                animationSpec = tween(durationMillis = 180),
                                targetOffsetX = { fullWidth ->
                                    if (enteringForward) -fullWidth / 4 else fullWidth / 4
                                },
                            ) + fadeOut(animationSpec = tween(durationMillis = 180))
                            enter togetherWith exit
                        },
                        label = "calendarViewModeAnimatedContent",
                    ) { mode ->
                        when (mode) {
                            CalendarViewMode.MONTH -> CalendarMonthCard(
                                visibleMonth = visibleMonth,
                                canGoPrevMonth = visibleMonth > minNavigableMonth,
                                selectedDate = selectedDate,
                                today = today,
                                tasksByDate = tasksByDate,
                                onPrevMonth = {
                                    if (visibleMonth > minNavigableMonth) {
                                        visibleMonthIso = visibleMonth.minusMonths(1).toString()
                                    }
                                },
                                onNextMonth = { visibleMonthIso = visibleMonth.plusMonths(1).toString() },
                                onSelectDate = ::selectDate,
                            )

                            CalendarViewMode.WEEK -> CalendarWeekCard(
                                selectedDate = selectedDate,
                                today = today,
                                tasksByDate = tasksByDate,
                                canGoPrevWeek = canNavigateTo(selectedDate.minusWeeks(1)),
                                onPrevWeek = { selectDate(selectedDate.minusWeeks(1)) },
                                onNextWeek = { selectDate(selectedDate.plusWeeks(1)) },
                                onSelectDate = ::selectDate,
                            )

                            CalendarViewMode.DAY -> CalendarDayCard(
                                selectedDate = selectedDate,
                                today = today,
                                tasksByDate = tasksByDate,
                                canGoPrevDay = canNavigateTo(selectedDate.minusDays(1)),
                                onPrevDay = { selectDate(selectedDate.minusDays(1)) },
                                onNextDay = { selectDate(selectedDate.plusDays(1)) },
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Tasks due ${selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                if (selectedDatePendingTasks.isEmpty()) {
                    item {
                        Text(
                            text = "No pending task due for this day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                } else {
                    items(selectedDatePendingTasks, key = { it.id }) { todo ->
                        CalendarTodoRow(
                            todo = todo,
                            lists = uiState.lists,
                            onComplete = { onCompleteTask(todo) },
                            onInfo = { editTargetId = todo.id },
                            onDelete = { onDelete(todo) },
                        )
                    }
                }

                if (selectedDateCompletedTasks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Completed:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }

                    items(selectedDateCompletedTasks, key = { it.id }) { completed ->
                        CalendarCompletedTodoRow(
                            item = completed,
                            lists = uiState.lists,
                            onUndoComplete = { onUncompleteTask(completed) },
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

    if (showCreateTaskSheet) {
        CreateTaskBottomSheet(
            lists = uiState.lists,
            initialStartEpochMs = createStartEpochMs,
            initialDueEpochMs = createDueEpochMs,
            onDismiss = {
                showCreateTaskSheet = false
                createStartEpochMs = null
                createDueEpochMs = null
            },
            onCreateTask = { payload ->
                onCreateTask(payload)
                showCreateTaskSheet = false
                createStartEpochMs = null
                createDueEpochMs = null
            },
        )
    }

    editTarget?.let { todo ->
        CreateTaskBottomSheet(
            lists = uiState.lists,
            editingTask = todo,
            defaultListId = todo.listId,
            onDismiss = { editTargetId = null },
            onCreateTask = { _ -> },
            onUpdateTask = { targetTodo, payload ->
                onUpdateTask(targetTodo, payload)
                editTargetId = null
            },
        )
    }
}

@Composable
private fun CalendarCreateTaskFab(
    onClick: () -> Unit,
) {
    val view = LocalView.current
    FloatingActionButton(
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Create task",
            modifier = Modifier.size(26.dp),
        )
    }
}

private enum class CalendarViewMode {
    MONTH,
    WEEK,
    DAY,
}

@Composable
private fun CalendarViewModeTabs(
    selectedMode: CalendarViewMode,
    onModeSelected: (CalendarViewMode) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            CalendarViewMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            color = if (selected) {
                                colorScheme.background
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onModeSelected(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode.name.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarWeekCard(
    selectedDate: LocalDate,
    today: LocalDate,
    tasksByDate: Map<LocalDate, List<TodoItem>>,
    canGoPrevWeek: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val weekStart = remember(selectedDate) { startOfWeek(selectedDate) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 42.dp.toPx() }
    val maxPreviewDragPx = with(density) { 64.dp.toPx() }
    var horizontalDragAccumulated by remember(weekStart) { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember(weekStart) { mutableFloatStateOf(0f) }
    val dragTranslationX by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = tween(durationMillis = 120),
        label = "calendarWeekDragTranslationX",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(weekStart, canGoPrevWeek) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        horizontalDragAccumulated = 0f
                        dragOffsetPx = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        horizontalDragAccumulated += dragAmount
                        val maxRight = if (canGoPrevWeek) maxPreviewDragPx else 0f
                        dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(
                            minimumValue = -maxPreviewDragPx,
                            maximumValue = maxRight,
                        )
                    },
                    onDragCancel = {
                        horizontalDragAccumulated = 0f
                        dragOffsetPx = 0f
                    },
                    onDragEnd = {
                        when {
                            horizontalDragAccumulated > swipeThresholdPx && canGoPrevWeek -> onPrevWeek()
                            horizontalDragAccumulated < -swipeThresholdPx -> onNextWeek()
                        }
                        horizontalDragAccumulated = 0f
                        dragOffsetPx = 0f
                    },
                )
            },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        AnimatedContent(
            targetState = weekStart,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragTranslationX },
            transitionSpec = {
                val movingToFuture = targetState > initialState
                val enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetX = { fullWidth ->
                        if (movingToFuture) fullWidth else -fullWidth
                    },
                ) + fadeIn(animationSpec = tween(durationMillis = 220))
                val exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    targetOffsetX = { fullWidth ->
                        if (movingToFuture) -fullWidth else fullWidth
                    },
                ) + fadeOut(animationSpec = tween(durationMillis = 180))
                (enter togetherWith exit).using(SizeTransform(clip = true))
            },
            label = "calendarWeekSwipeAnimatedContent",
        ) { displayWeekStart ->
            val weekDays = remember(displayWeekStart) {
                List(7) { offset -> displayWeekStart.plusDays(offset.toLong()) }
            }
            val weekLabel = remember(displayWeekStart) { formatWeekRange(displayWeekStart) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniCalendarNavButton(
                        icon = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous week",
                        enabled = canGoPrevWeek,
                        onClick = onPrevWeek,
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = weekLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                    }
                    MiniCalendarNavButton(
                        icon = Icons.Rounded.ChevronRight,
                        contentDescription = "Next week",
                        onClick = onNextWeek,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    weekDays.forEach { day ->
                        val isSelected = day == selectedDate
                        val isToday = day == today
                        val taskCount = tasksByDate[day]?.size ?: 0
                        CalendarWeekDayCell(
                            date = day,
                            taskCount = taskCount,
                            isSelected = isSelected,
                            isToday = isToday,
                            onClick = { onSelectDate(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarWeekDayCell(
    date: LocalDate,
    taskCount: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when {
        isSelected -> colorScheme.primary.copy(alpha = 0.2f)
        isToday -> colorScheme.primary.copy(alpha = 0.12f)
        else -> colorScheme.background
    }
    val borderColor = when {
        isSelected -> colorScheme.primary.copy(alpha = 0.8f)
        isToday -> colorScheme.primary.copy(alpha = 0.42f)
        else -> Color.Transparent
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (borderColor == Color.Transparent) 0.dp else 1.dp,
            color = borderColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Medium,
            )
            Text(
                text = if (taskCount > 9) "9+" else taskCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (taskCount > 0) colorScheme.secondary else colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CalendarDayCard(
    selectedDate: LocalDate,
    today: LocalDate,
    tasksByDate: Map<LocalDate, List<TodoItem>>,
    canGoPrevDay: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 42.dp.toPx() }
    val maxPreviewDragPx = with(density) { 64.dp.toPx() }
    var horizontalDragAccumulated by remember(selectedDate) { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember(selectedDate) { mutableFloatStateOf(0f) }
    val dragTranslationX by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = tween(durationMillis = 120),
        label = "calendarDayDragTranslationX",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(selectedDate, canGoPrevDay) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        horizontalDragAccumulated = 0f
                        dragOffsetPx = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        horizontalDragAccumulated += dragAmount
                        val maxRight = if (canGoPrevDay) maxPreviewDragPx else 0f
                        dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(
                            minimumValue = -maxPreviewDragPx,
                            maximumValue = maxRight,
                        )
                    },
                    onDragCancel = {
                        horizontalDragAccumulated = 0f
                        dragOffsetPx = 0f
                    },
                    onDragEnd = {
                        when {
                            horizontalDragAccumulated > swipeThresholdPx && canGoPrevDay -> onPrevDay()
                            horizontalDragAccumulated < -swipeThresholdPx -> onNextDay()
                        }
                        horizontalDragAccumulated = 0f
                        dragOffsetPx = 0f
                    },
                )
            },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        AnimatedContent(
            targetState = selectedDate,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragTranslationX },
            transitionSpec = {
                val movingToFuture = targetState > initialState
                val enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetX = { fullWidth ->
                        if (movingToFuture) fullWidth else -fullWidth
                    },
                ) + fadeIn(animationSpec = tween(durationMillis = 220))
                val exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    targetOffsetX = { fullWidth ->
                        if (movingToFuture) -fullWidth else fullWidth
                    },
                ) + fadeOut(animationSpec = tween(durationMillis = 180))
                (enter togetherWith exit).using(SizeTransform(clip = true))
            },
            label = "calendarDaySwipeAnimatedContent",
        ) { displayDate ->
            val taskCount = tasksByDate[displayDate]?.size ?: 0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniCalendarNavButton(
                        icon = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous day",
                        enabled = canGoPrevDay,
                        onClick = onPrevDay,
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = displayDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    MiniCalendarNavButton(
                        icon = Icons.Rounded.ChevronRight,
                        contentDescription = "Next day",
                        onClick = onNextDay,
                    )
                }

                Text(
                    text = displayDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (displayDate == today) colorScheme.primary else colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (taskCount == 1) {
                        "1 task due"
                    } else {
                        "$taskCount tasks due"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun startOfWeek(date: LocalDate): LocalDate {
    val sundayOffset = date.dayOfWeek.value % 7
    return date.minusDays(sundayOffset.toLong())
}

private fun formatWeekRange(weekStart: LocalDate): String {
    val weekEnd = weekStart.plusDays(6)
    val monthShortFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val sameMonth = weekStart.month == weekEnd.month && weekStart.year == weekEnd.year
    return if (sameMonth) {
        "${weekStart.format(monthShortFormatter)} ${weekStart.dayOfMonth}-${weekEnd.dayOfMonth}, ${weekEnd.year}"
    } else {
        "${weekStart.format(monthShortFormatter)} ${weekStart.dayOfMonth} - " +
            "${weekEnd.format(monthShortFormatter)} ${weekEnd.dayOfMonth}, ${weekEnd.year}"
    }
}

@Composable
private fun CalendarTopBar(
    onBack: () -> Unit,
    onJumpToday: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalendarCircleButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            CalendarCircleButton(
                icon = Icons.Rounded.CalendarMonth,
                contentDescription = "Jump to today",
                onClick = onJumpToday,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFF7D67B6),
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "Calendar",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7D67B6),
            )
        }
    }
}

@Composable
private fun CalendarCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = androidx.compose.ui.platform.LocalView.current
    Card(
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        shape = androidx.compose.foundation.shape.CircleShape,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.34f)),
        colors = CardDefaults.cardColors(containerColor = colorScheme.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun CalendarMonthCard(
    visibleMonth: YearMonth,
    canGoPrevMonth: Boolean,
    selectedDate: LocalDate,
    today: LocalDate,
    tasksByDate: Map<LocalDate, List<TodoItem>>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 42.dp.toPx() }
    val maxPreviewDragPx = with(density) { 64.dp.toPx() }
    var horizontalDragAccumulated by remember(visibleMonth) { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember(visibleMonth) { mutableFloatStateOf(0f) }
    val dragTranslationX by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = tween(durationMillis = 120),
        label = "calendarMonthDragTranslationX",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(visibleMonth, canGoPrevMonth) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        horizontalDragAccumulated = 0f
                        dragOffsetPx = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        horizontalDragAccumulated += dragAmount
                        val maxRight = if (canGoPrevMonth) maxPreviewDragPx else 0f
                        dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(
                            minimumValue = -maxPreviewDragPx,
                            maximumValue = maxRight,
                        )
                    },
                    onDragCancel = {
                        horizontalDragAccumulated = 0f
                        dragOffsetPx = 0f
                    },
                    onDragEnd = {
                        when {
                            horizontalDragAccumulated > swipeThresholdPx && canGoPrevMonth -> onPrevMonth()
                            horizontalDragAccumulated < -swipeThresholdPx -> onNextMonth()
                        }
                        horizontalDragAccumulated = 0f
                        dragOffsetPx = 0f
                    },
                )
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        AnimatedContent(
            targetState = visibleMonth,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragTranslationX },
            transitionSpec = {
                val movingToFuture = targetState > initialState
                val enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetX = { fullWidth ->
                        if (movingToFuture) fullWidth else -fullWidth
                    },
                ) + fadeIn(animationSpec = tween(durationMillis = 220))
                val exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    targetOffsetX = { fullWidth ->
                        if (movingToFuture) -fullWidth else fullWidth
                    },
                ) + fadeOut(animationSpec = tween(durationMillis = 180))
                (enter togetherWith exit).using(SizeTransform(clip = true))
            },
            label = "calendarMonthSwipeAnimatedContent",
        ) { displayMonth ->
            val monthLabel = remember(displayMonth) {
                displayMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                    " " + displayMonth.year
            }
            val monthDays = remember(displayMonth) { buildMonthCells(displayMonth) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniCalendarNavButton(
                        icon = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous month",
                        enabled = canGoPrevMonth,
                        onClick = onPrevMonth,
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                    }
                    MiniCalendarNavButton(
                        icon = Icons.Rounded.ChevronRight,
                        contentDescription = "Next month",
                        onClick = onNextMonth,
                    )
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    WEEKDAY_HEADERS.forEach { dayLabel ->
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                monthDays.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        week.forEach { cell ->
                            val taskCount = tasksByDate[cell.date]?.size ?: 0
                            CalendarDayCell(
                                cell = cell,
                                taskCount = taskCount,
                                isSelected = cell.date == selectedDate,
                                isToday = cell.date == today,
                                onClick = { onSelectDate(cell.date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCalendarNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = if (enabled && isPressed) {
                    colorScheme.surfaceVariant.copy(alpha = 0.6f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = 17.dp,
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                colorScheme.onSurfaceVariant
            } else {
                colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CalendarDayCell(
    cell: CalendarDayCellModel,
    taskCount: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when {
        isSelected -> colorScheme.primary.copy(alpha = 0.2f)
        isToday -> colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isSelected -> colorScheme.primary.copy(alpha = 0.8f)
        isToday -> colorScheme.primary.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val cellShape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val dayTextColor = when {
        isSelected -> colorScheme.primary
        cell.isCurrentMonth -> colorScheme.onSurface
        else -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                color = containerColor,
                shape = cellShape,
            )
            .border(
                width = if (borderColor == Color.Transparent) 0.dp else 1.2.dp,
                color = borderColor,
                shape = cellShape,
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = dayTextColor,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 6.dp),
        )

        if (taskCount > 0 && cell.isCurrentMonth) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FiberManualRecord,
                    contentDescription = null,
                    tint = if (isSelected) colorScheme.primary else colorScheme.secondary,
                    modifier = Modifier.size(7.dp),
                )
                Text(
                    text = if (taskCount > 9) "9+" else taskCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CalendarTodoRow(
    todo: TodoItem,
    lists: List<ListSummary>,
    onComplete: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val actionRevealPx = with(density) { 130.dp.toPx() }
    var targetOffsetX by remember(todo.id) { mutableFloatStateOf(0f) }
    var pendingCompletion by remember(todo.id) { mutableStateOf(false) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = tween(durationMillis = 150),
        label = "calendarTaskSwipeOffset",
    )
    val showCompletedState = pendingCompletion
    val dueText = DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(todo.due)
    val listMeta = todo.listId?.let { listId -> lists.firstOrNull { it.id == listId } }
    val showListIndicator = listMeta != null
    val showPriorityFlag = isHighPriority(todo.priority)
    val listIndicatorColor = listAccentColor(listMeta?.color)
    val rowShape = RoundedCornerShape(16.dp)
    val actionContainerColor =
        colorScheme.surfaceVariant.copy(alpha = if (colorScheme.background.luminance() < 0.5f) 0.62f else 0.92f)
    val foregroundColor = colorScheme.background

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(rowShape)
                .background(actionContainerColor),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarSwipeActionCircle(
                    icon = Icons.Rounded.Info,
                    contentDescription = "Edit task",
                    tint = colorScheme.onSurface,
                    background = colorScheme.surface,
                    onClick = {
                        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                        onInfo()
                        targetOffsetX = 0f
                    },
                )
                CalendarSwipeActionCircle(
                    icon = Icons.Rounded.DeleteSweep,
                    contentDescription = "Delete task",
                    tint = colorScheme.error,
                    background = colorScheme.surface,
                    onClick = {
                        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                        targetOffsetX = 0f
                        coroutineScope.launch {
                            onDelete()
                        }
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
                            targetOffsetX = (targetOffsetX + delta).coerceIn(-actionRevealPx, 0f)
                        },
                        onDragStopped = { velocity ->
                            val flingOpen = velocity < -1450f
                            val dragOpen = targetOffsetX < -(actionRevealPx * 0.32f)
                            targetOffsetX = if (flingOpen || dragOpen) {
                                -actionRevealPx
                            } else {
                                0f
                            }
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
                    CalendarCompletionToggleIcon(
                        imageVector = if (showCompletedState) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = if (showCompletedState) {
                            "Completed"
                        } else {
                            "Mark complete"
                        },
                        tint = if (showCompletedState) {
                            Color(0xFF6FBF86)
                        } else {
                            colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        },
                        enabled = !pendingCompletion,
                        onClick = {
                            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                            targetOffsetX = 0f
                            pendingCompletion = true
                            coroutineScope.launch {
                                delay(500)
                                onComplete()
                            }
                        },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    ) {
                        Text(
                            text = todo.title,
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
                            maxLines = 2,
                        )
                        Text(
                            text = dueText,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (showListIndicator || showPriorityFlag) {
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
                            if (showPriorityFlag) {
                                Icon(
                                    imageVector = Icons.Rounded.Flag,
                                    contentDescription = "Priority task",
                                    tint = priorityColor(todo.priority),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colorScheme.outlineVariant.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun CalendarCompletedTodoRow(
    item: CompletedItem,
    lists: List<ListSummary>,
    onUndoComplete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var pendingUncomplete by remember(item.id) { mutableStateOf(false) }
    val showCompletedState = !pendingUncomplete
    val dueText = DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(item.due)
    val listMeta = item.resolveListSummary(lists)
    val listIndicatorColor = listMeta?.color?.let(::listAccentColor)
        ?: item.listColor?.let(::listAccentColor)
        ?: colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
    val showListIndicator = !item.listName.isNullOrBlank() || listMeta != null
    val showPriorityFlag = isHighPriority(item.priority)
    val rowShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = rowShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarCompletionToggleIcon(
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
                    enabled = !pendingUncomplete,
                    onClick = {
                        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                        pendingUncomplete = true
                        coroutineScope.launch {
                            delay(500)
                            onUndoComplete()
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
                        maxLines = 2,
                    )
                    Text(
                        text = dueText,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
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
                            contentDescription = "Priority task",
                            tint = priorityColor(item.priority),
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

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colorScheme.outlineVariant.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun CalendarSwipeActionCircle(
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
        label = "calendarSwipeActionScale",
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
        shape = androidx.compose.foundation.shape.CircleShape,
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
private fun CalendarCompletionToggleIcon(
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
private fun EmptyCalendarState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 70.dp, bottom = 90.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private data class CalendarDayCellModel(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
)

private fun buildMonthCells(month: YearMonth): List<CalendarDayCellModel> {
    val firstDay = month.atDay(1)
    val firstOffset = firstDay.dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val previousMonth = month.minusMonths(1)
    val daysInPreviousMonth = previousMonth.lengthOfMonth()
    val nextMonth = month.plusMonths(1)

    return List(42) { index ->
        val dayNumber = index - firstOffset + 1
        when {
            dayNumber < 1 -> {
                CalendarDayCellModel(
                    date = previousMonth.atDay(daysInPreviousMonth + dayNumber),
                    isCurrentMonth = false,
                )
            }

            dayNumber > daysInMonth -> {
                CalendarDayCellModel(
                    date = nextMonth.atDay(dayNumber - daysInMonth),
                    isCurrentMonth = false,
                )
            }

            else -> {
                CalendarDayCellModel(
                    date = month.atDay(dayNumber),
                    isCurrentMonth = true,
                )
            }
        }
    }
}

private fun priorityColor(priority: String): Color {
    return when (priority.lowercase(Locale.getDefault())) {
        "high", "urgent", "important" -> Color(0xFFE56A6A)
        "medium" -> Color(0xFFE3B368)
        else -> Color(0xFF6FBF86)
    }
}

private fun isHighPriority(priority: String): Boolean {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "medium", "high", "urgent", "important" -> true
        else -> false
    }
}

private fun CompletedItem.resolveListSummary(lists: List<ListSummary>): ListSummary? {
    val name = listName?.trim()?.lowercase(Locale.getDefault()) ?: return null
    return lists.firstOrNull { it.name.trim().lowercase(Locale.getDefault()) == name }
}

private fun listAccentColor(colorKey: String?): Color {
    return when (colorKey) {
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
        else -> Color(0xFF5C9FE7)
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
        "home" -> Icons.Rounded.Home
        else -> Icons.AutoMirrored.Rounded.List
    }
}

private val WEEKDAY_HEADERS = listOf("S", "M", "T", "W", "T", "F", "S")
