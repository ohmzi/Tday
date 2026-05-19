package com.ohmz.tday.compose.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
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
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.theme.TdayDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val CalendarAccentPurple = Color(0xFF7D67B6)

private fun calendarPageAnimationSpec() = tween<IntOffset>(
    durationMillis = 260,
    easing = FastOutSlowInEasing,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreateTask: (CreateTaskPayload) -> Unit,
    onParseTaskTitleNlp: suspend (title: String, referenceDueEpochMs: Long) -> TodoTitleNlpResponse?,
    onCompleteTask: (TodoItem) -> Unit,
    onUpdateTask: (TodoItem, CreateTaskPayload) -> Unit,
    onDelete: (TodoItem) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zoneId) }
    val minNavigableMonth = remember(zoneId) { YearMonth.now(zoneId) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val maxCollapsePx = with(density) { CALENDAR_TITLE_COLLAPSE_DISTANCE_DP.dp.toPx() }
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
        label = "calendarTitleCollapseProgress",
    )
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
    val selectedDatePendingTasks = tasksByDate[selectedDate].orEmpty()
    fun canNavigateTo(date: LocalDate): Boolean = YearMonth.from(date) >= minNavigableMonth
    fun selectDate(date: LocalDate) {
        if (!canNavigateTo(date)) return
        visibleMonthIso = YearMonth.from(date).toString()
        selectedDateIso = date.toString()
    }

    var editTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateTaskSheet by rememberSaveable { mutableStateOf(false) }
    var createDueEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    val editTarget = remember(editTargetId, uiState.items) {
        editTargetId?.let { targetId ->
            uiState.items.firstOrNull { it.id == targetId }
        }
    }
    fun openCreateTaskSheetForSelectedDate() {
        val currentDate = LocalDate.now(zoneId)
        val prefillDue = if (selectedDate == currentDate) {
            val nowTime = java.time.LocalTime.now(zoneId)
            selectedDate.atTime(nowTime).atZone(zoneId).plusHours(1)
        } else {
            selectedDate.atTime(21, 0).atZone(zoneId)
        }
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
                collapseProgress = collapseProgress,
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
                    .padding(padding)
                    .nestedScroll(nestedScrollConnection),
                state = listState,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
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
                    val tasksDueDateLabel =
                        selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                    Text(
                        text = stringResource(R.string.calendar_tasks_due, tasksDueDateLabel),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                if (selectedDatePendingTasks.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.calendar_no_pending),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                } else {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            selectedDatePendingTasks.forEach { todo ->
                                key(todo.id) {
                                    CalendarTodoRow(
                                        todo = todo,
                                        lists = uiState.lists,
                                        onComplete = { onCompleteTask(todo) },
                                        onInfo = { editTargetId = todo.id },
                                        onDelete = { onDelete(todo) },
                                    )
                                }
                            }
                        }
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
    }

    if (showCreateTaskSheet) {
        CreateTaskBottomSheet(
            lists = uiState.lists,
            initialDueEpochMs = createDueEpochMs,
            onParseTaskTitleNlp = onParseTaskTitleNlp,
            onDismiss = {
                showCreateTaskSheet = false
                createDueEpochMs = null
            },
            onCreateTask = { payload ->
                onCreateTask(payload)
                showCreateTaskSheet = false
                createDueEpochMs = null
            },
        )
    }

    editTarget?.let { todo ->
        CreateTaskBottomSheet(
            lists = uiState.lists,
            editingTask = todo,
            defaultListId = todo.listId,
            onParseTaskTitleNlp = onParseTaskTitleNlp,
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
        modifier = Modifier.size(TdayDimens.FabSize),
        shape = CircleShape,
        containerColor = CalendarAccentPurple,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = TdayDimens.FabElevation,
            pressedElevation = TdayDimens.FabPressedElevation,
            focusedElevation = TdayDimens.FabElevation,
            hoveredElevation = TdayDimens.FabElevation,
        ),
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.action_create_task),
            modifier = Modifier.size(40.dp),
        )
    }
}

private enum class CalendarViewMode {
    MONTH,
    WEEK,
    DAY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarViewModeTabs(
    selectedMode: CalendarViewMode,
    onModeSelected: (CalendarViewMode) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val segmentColors = SegmentedButtonDefaults.colors(
        activeContainerColor = CalendarAccentPurple.copy(alpha = 0.18f),
        activeContentColor = CalendarAccentPurple,
        activeBorderColor = CalendarAccentPurple.copy(alpha = 0.62f),
        inactiveContainerColor = colorScheme.surface,
        inactiveContentColor = colorScheme.onSurfaceVariant,
        inactiveBorderColor = colorScheme.outline.copy(alpha = 0.42f),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        CalendarViewMode.entries.forEachIndexed { index, mode ->
            val selected = mode == selectedMode
            SegmentedButton(
                selected = selected,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = CalendarViewMode.entries.size,
                ),
                colors = segmentColors,
                icon = {},
                label = {
                    Text(
                        text = mode.name.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
            )
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
                    contentDescription = stringResource(R.string.calendar_prev_week),
                    enabled = canGoPrevWeek,
                    onClick = onPrevWeek,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatWeekRange(weekStart),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface,
                    )
                }
                MiniCalendarNavButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_week),
                    onClick = onNextWeek,
                )
            }

            AnimatedContent(
                targetState = weekStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = dragTranslationX },
                transitionSpec = {
                    val movingToFuture = targetState > initialState
                    val enter = slideInHorizontally(
                        animationSpec = calendarPageAnimationSpec(),
                        initialOffsetX = { fullWidth ->
                            if (movingToFuture) fullWidth else -fullWidth
                        },
                    )
                    val exit = slideOutHorizontally(
                        animationSpec = calendarPageAnimationSpec(),
                        targetOffsetX = { fullWidth ->
                            if (movingToFuture) -fullWidth else fullWidth
                        },
                    )
                    (enter togetherWith exit).using(SizeTransform(clip = true))
                },
                label = "calendarWeekSwipeAnimatedContent",
            ) { displayWeekStart ->
                val weekDays = remember(displayWeekStart) {
                    List(7) { offset -> displayWeekStart.plusDays(offset.toLong()) }
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
        isSelected -> CalendarAccentPurple.copy(alpha = 0.2f)
        isToday -> CalendarAccentPurple.copy(alpha = 0.12f)
        else -> colorScheme.background
    }
    val borderColor = when {
        isSelected -> CalendarAccentPurple.copy(alpha = 0.8f)
        isToday -> CalendarAccentPurple.copy(alpha = 0.42f)
        else -> Color.Transparent
    }

    Card(
        modifier = modifier.minimumInteractiveComponentSize(),
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
                color = if (isSelected || isToday) CalendarAccentPurple else colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = if (taskCount > 9) {
                    stringResource(R.string.calendar_task_count_cap)
                } else {
                    taskCount.toString()
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (taskCount > 0) CalendarAccentPurple else colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                fontWeight = FontWeight.ExtraBold,
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
                    contentDescription = stringResource(R.string.calendar_prev_day),
                    enabled = canGoPrevDay,
                    onClick = onPrevDay,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                MiniCalendarNavButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_day),
                    onClick = onNextDay,
                )
            }

            AnimatedContent(
                targetState = selectedDate,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = dragTranslationX },
                transitionSpec = {
                    val movingToFuture = targetState > initialState
                    val enter = slideInHorizontally(
                        animationSpec = calendarPageAnimationSpec(),
                        initialOffsetX = { fullWidth ->
                            if (movingToFuture) fullWidth else -fullWidth
                        },
                    )
                    val exit = slideOutHorizontally(
                        animationSpec = calendarPageAnimationSpec(),
                        targetOffsetX = { fullWidth ->
                            if (movingToFuture) -fullWidth else fullWidth
                        },
                    )
                    (enter togetherWith exit).using(SizeTransform(clip = true))
                },
                label = "calendarDaySwipeAnimatedContent",
            ) { displayDate ->
                val taskCount = tasksByDate[displayDate]?.size ?: 0
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = displayDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (displayDate == today) CalendarAccentPurple else colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = if (taskCount == 1) {
                            stringResource(R.string.calendar_task_count_one)
                        } else {
                            stringResource(R.string.calendar_task_count_many, taskCount)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
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
    collapseProgress: Float,
    onJumpToday: () -> Unit,
) {
    val progress = collapseProgress.coerceIn(0f, 1f)
    val expandedFadeStart = 0.62f
    val expandedFadeEnd = 0.86f
    val collapsedFadeStart = 0.72f
    val collapsedFadeEnd = 0.96f
    val density = LocalDensity.current
    val expandedTitleHeight = lerp(56.dp, 0.dp, progress)
    val expandedTitleAlpha = 1f - (
        (progress - expandedFadeStart) / (expandedFadeEnd - expandedFadeStart)
        ).coerceIn(0f, 1f)
    val collapsedTitleAlpha = (
        (progress - collapsedFadeStart) / (collapsedFadeEnd - collapsedFadeStart)
        ).coerceIn(0f, 1f)
    val collapsedTitleShiftY = with(density) { (10.dp * (1f - collapsedTitleAlpha)).toPx() }
    val expandedTitleShiftY = with(density) { lerp(0.dp, (-18).dp, progress).toPx() }

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
                CalendarCircleButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                    isBackButton = true,
                )
                CalendarCircleButton(
                    icon = Icons.Rounded.CalendarMonth,
                    contentDescription = stringResource(R.string.calendar_jump_to_today),
                    onClick = onJumpToday,
                )
            }
            if (collapsedTitleAlpha > 0.001f) {
                Text(
                    text = stringResource(R.string.calendar_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = CalendarAccentPurple,
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
                    text = stringResource(R.string.calendar_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = CalendarAccentPurple,
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
private fun CalendarCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isBackButton: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = androidx.compose.ui.platform.LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val containerColor = if (isBackButton) {
        if (isDarkTheme) colorScheme.surface.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)
    } else {
        colorScheme.background
    }
    val buttonBorder = if (isBackButton) null else BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.34f))
    val buttonSize = if (isBackButton) TdayDimens.FabSize else 54.dp
    val iconSize = if (isBackButton) 36.dp else 28.dp
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        label = "calendarHeaderButtonScale",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        label = "calendarHeaderButtonOffsetY",
    )

    Card(
        modifier = Modifier
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
        shape = androidx.compose.foundation.shape.CircleShape,
        border = buttonBorder,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isBackButton) TdayDimens.FabElevation else 0.dp,
            pressedElevation = if (isBackButton) TdayDimens.FabPressedElevation else 0.dp,
        ),
    ) {
        Box(
            modifier = Modifier.size(buttonSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(iconSize),
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
                    contentDescription = stringResource(R.string.calendar_prev_month),
                    enabled = canGoPrevMonth,
                    onClick = onPrevMonth,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                            " " + visibleMonth.year,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface,
                    )
                }
                MiniCalendarNavButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_month),
                    onClick = onNextMonth,
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                WEEKDAY_HEADERS.forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.64f),
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AnimatedContent(
                targetState = visibleMonth,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = dragTranslationX },
                transitionSpec = {
                    val movingToFuture = targetState > initialState
                    val enter = slideInHorizontally(
                        animationSpec = calendarPageAnimationSpec(),
                        initialOffsetX = { fullWidth ->
                            if (movingToFuture) fullWidth else -fullWidth
                        },
                    )
                    val exit = slideOutHorizontally(
                        animationSpec = calendarPageAnimationSpec(),
                        targetOffsetX = { fullWidth ->
                            if (movingToFuture) -fullWidth else fullWidth
                        },
                    )
                    (enter togetherWith exit).using(SizeTransform(clip = true))
                },
                label = "calendarMonthSwipeAnimatedContent",
            ) { displayMonth ->
                val monthDays = remember(displayMonth) { buildMonthCells(displayMonth) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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
}

private const val CALENDAR_TITLE_COLLAPSE_DISTANCE_DP = 180f

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
        isSelected -> CalendarAccentPurple.copy(alpha = 0.2f)
        isToday -> CalendarAccentPurple.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isSelected -> CalendarAccentPurple.copy(alpha = 0.8f)
        isToday -> CalendarAccentPurple.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val cellShape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val dayTextColor = when {
        isSelected || isToday -> CalendarAccentPurple
        cell.isCurrentMonth -> colorScheme.onSurface
        else -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .aspectRatio(1f)
            .clip(cellShape)
            .background(
                color = containerColor,
                shape = cellShape,
            )
            .border(
                width = if (borderColor == Color.Transparent) 0.dp else 1.2.dp,
                color = borderColor,
                shape = cellShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = 28.dp,
                ),
                onClick = onClick,
            ),
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = dayTextColor,
            fontWeight = FontWeight.ExtraBold,
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
                    tint = CalendarAccentPurple,
                    modifier = Modifier.size(7.dp),
                )
                Text(
                    text = if (taskCount > 9) {
                        stringResource(R.string.calendar_task_count_cap)
                    } else {
                        taskCount.toString()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CalendarAccentPurple,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun CalendarTodoRow(
    modifier: Modifier = Modifier,
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
    val actionRevealPx = with(density) { 176.dp.toPx() }
    val swipeHintOffsetPx = with(density) { 42.dp.toPx() }.coerceAtMost(actionRevealPx * 0.24f)
    val maxElasticDragPx = actionRevealPx * 1.14f
    var targetOffsetX by remember(todo.id) { mutableFloatStateOf(0f) }
    var swipeHinting by remember(todo.id) { mutableStateOf(false) }
    var pendingCompletion by remember(todo.id) { mutableStateOf(false) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
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
    val foregroundColor = colorScheme.background
    val actionRevealProgress = (-animatedOffsetX / actionRevealPx).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarSwipeActionButton(
                    icon = Icons.Rounded.BorderColor,
                    contentDescription = stringResource(R.string.action_edit_task),
                    label = stringResource(R.string.action_edit),
                    tint = Color.White,
                    background = Color(0xFF4C7DDE),
                    revealProgress = actionRevealProgress,
                    revealDelay = 0.62f,
                    onClick = {
                        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                        onInfo()
                        targetOffsetX = 0f
                    },
                )
                CalendarSwipeActionButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete_task),
                    label = stringResource(R.string.action_delete),
                    tint = Color.White,
                    background = Color(0xFFFF453A),
                    revealProgress = actionRevealProgress,
                    revealDelay = 0.04f,
                    onClick = {
                        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                        targetOffsetX = 0f
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
                            targetOffsetX = (targetOffsetX + delta).coerceIn(
                                -maxElasticDragPx,
                                0f,
                            )
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
                        if (targetOffsetX != 0f) {
                            targetOffsetX = 0f
                        } else if (!swipeHinting && !pendingCompletion) {
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
                    CalendarCompletionToggleIcon(
                        imageVector = if (showCompletedState) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = if (showCompletedState) {
                            stringResource(R.string.label_completed)
                        } else {
                            stringResource(R.string.label_mark_complete)
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
                            fontWeight = FontWeight.ExtraBold,
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
                            if (showPriorityFlag) {
                                Icon(
                                    imageVector = Icons.Rounded.Flag,
                                    contentDescription = stringResource(R.string.label_priority_task),
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
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
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
                    contentDescription = stringResource(R.string.label_undo_complete),
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
                        fontWeight = FontWeight.ExtraBold,
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

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colorScheme.outlineVariant.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun CalendarSwipeActionButton(
    icon: ImageVector,
    contentDescription: String,
    label: String,
    tint: Color,
    background: Color,
    revealProgress: Float,
    revealDelay: Float,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        label = "calendarSwipeActionScale",
    )
    val normalizedReveal = ((revealProgress - revealDelay) / (1f - revealDelay))
        .coerceIn(0f, 1f)
    val easedReveal = FastOutSlowInEasing.transform(normalizedReveal)

    Column(
        modifier = Modifier
            .sizeIn(minWidth = 60.dp)
            .graphicsLayer {
                alpha = easedReveal
                val revealScale = 0.38f + (0.62f * easedReveal)
                scaleX = pressedScale * revealScale
                scaleY = pressedScale * revealScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Card(
            modifier = Modifier.size(width = 56.dp, height = 34.dp),
            onClick = onClick,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = background),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
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
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = 24.dp,
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
            fontWeight = FontWeight.ExtraBold,
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
