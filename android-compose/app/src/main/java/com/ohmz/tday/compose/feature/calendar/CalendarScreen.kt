package com.ohmz.tday.compose.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Inventory
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsFootball
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Square
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TaskRescheduleScope
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.ui.snapTitleCollapsePx
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.component.TdaySegmentedSlider
import com.ohmz.tday.compose.ui.theme.TdayDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val CalendarAccentPurple = Color(0xFF7D67B6)
private val CalendarTodayBlue = Color(0xFF509AE6)
private val CalendarCardCornerRadius = 24.dp
private val CalendarCardHeaderHeight = 36.dp
private val CalendarCardHeaderHorizontalPadding = 6.dp
private val CalendarCardNavButtonWidth = 40.dp
private val CalendarCardNavButtonHeight = 36.dp
private val CalendarCardNavIconSize = 28.dp
private val CalendarCardHorizontalPadding = 16.dp
private val CalendarMonthCardTopPadding = 16.dp
private val CalendarMonthCardBottomPadding = 20.dp
private val CalendarMonthCardOuterSpacing = 14.dp
private val CalendarMonthGridSpacing = 8.dp
private val CalendarMonthWeekdayHeight = 18.dp
private val CalendarMonthGridHeight = 292.dp
private val CalendarMonthDayCellHeight = 42.dp
private val CalendarMonthDayHighlightWidth = 42.dp
private val CalendarMonthDayHighlightHeight = 40.dp
private val CalendarMonthDayNumberWidth = 34.dp
private val CalendarMonthDayNumberHeight = 24.dp
private val CalendarMonthTaskCountHeight = 13.dp
private val CalendarMonthTaskDotSize = 4.6.dp
private val CalendarMonthHeaderTitleSize = 21.sp
private val CalendarPeriodHeaderTitleSize = 21.sp
private val CalendarDaySummaryTitleSize = 25.sp
private val CalendarDaySummaryCountSize = 18.sp
private val CalendarPeriodCardPageHeight = 78.dp
private val CalendarPeriodWeekDayCellHeight = 72.dp
private val CalendarPeriodPageHorizontalGutter = 2.dp
private val CalendarPeriodCardBottomPadding = 18.dp
private val CalendarTaskDragDueTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())

private fun shouldShowDateDivider(
    afterItemIndex: Int,
    items: List<TodoItem>,
    zoneId: ZoneId,
): Boolean {
    val currentTodo = items.getOrNull(afterItemIndex) ?: return false
    val nextTodo = items.getOrNull(afterItemIndex + 1) ?: return false
    return LocalDate.ofInstant(currentTodo.due, zoneId) != LocalDate.ofInstant(nextTodo.due, zoneId)
}

private data class CalendarTaskRescheduleDrop(
    val todo: TodoItem,
    val targetDate: LocalDate,
)

private data class CalendarTaskDragState(
    val todo: TodoItem,
    val position: Offset,
)

private data class CalendarDateDropTargetBounds(
    val date: LocalDate,
    val bounds: Rect,
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
    onMoveTask: (todo: TodoItem, targetDate: LocalDate, scope: TaskRescheduleScope) -> Unit,
    onDelete: (TodoItem) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val view = LocalView.current
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
                val isListAtTop =
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && !isListAtTop) return Velocity.Zero
                val snapped = snapTitleCollapsePx(
                    currentPx = headerCollapsePx,
                    maxPx = maxCollapsePx,
                    velocityY = available.y,
                )
                if (snapped == headerCollapsePx) return Velocity.Zero
                headerCollapsePx = snapped
                return if (available.y == 0f) Velocity.Zero else available
            }
        }
    }
    val collapseProgress by animateFloatAsState(
        targetValue = collapseProgressTarget,
        label = "calendarTitleCollapseProgress",
    )
    LaunchedEffect(
        listState.isScrollInProgress,
        headerCollapsePx,
        maxCollapsePx,
    ) {
        if (listState.isScrollInProgress || headerCollapsePx <= 0f || headerCollapsePx >= maxCollapsePx) {
            return@LaunchedEffect
        }
        val isListAtTop =
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        headerCollapsePx = if (isListAtTop) {
            snapTitleCollapsePx(headerCollapsePx, maxCollapsePx)
        } else {
            maxCollapsePx
        }
    }
    val monthTitleSnapThresholdPx = remember(density) { with(density) { 58.dp.roundToPx() } }
    var visibleMonthIso by rememberSaveable { mutableStateOf(minNavigableMonth.toString()) }
    var selectedDateIso by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedViewKey by rememberSaveable { mutableStateOf(CalendarViewMode.MONTH.name) }
    var todayJumpRequestId by rememberSaveable { mutableStateOf(0) }
    var todayJumpRequest by remember { mutableStateOf<CalendarTodayJumpRequest?>(null) }

    val visibleMonth = remember(visibleMonthIso) { YearMonth.parse(visibleMonthIso) }
    val selectedDate = remember(selectedDateIso) { LocalDate.parse(selectedDateIso) }
    val selectedViewMode = remember(selectedViewKey) {
        CalendarViewMode.entries.firstOrNull { it.name == selectedViewKey } ?: CalendarViewMode.MONTH
    }
    val calendarTaskRescheduleEnabled = selectedViewMode != CalendarViewMode.DAY
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
    fun clearTodayJumpRequest(requestId: Int) {
        if (todayJumpRequest?.id == requestId) {
            todayJumpRequest = null
        }
    }

    var editTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateTaskSheet by rememberSaveable { mutableStateOf(false) }
    var createDueEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var draggedCalendarTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeCalendarDrag by remember { mutableStateOf<CalendarTaskDragState?>(null) }
    var calendarDragContainerOrigin by remember { mutableStateOf(Offset.Zero) }
    val calendarDropTargetBounds =
        remember { mutableStateMapOf<String, CalendarDateDropTargetBounds>() }
    var activeDropDateIso by remember { mutableStateOf<String?>(null) }
    var pendingRescheduleDrop by remember { mutableStateOf<CalendarTaskRescheduleDrop?>(null) }
    LaunchedEffect(selectedViewMode) {
        if (selectedViewMode == CalendarViewMode.DAY) {
            draggedCalendarTodoId = null
            activeCalendarDrag = null
            activeDropDateIso = null
            calendarDropTargetBounds.clear()
        }
    }
    val editTarget = remember(editTargetId, uiState.items) {
        editTargetId?.let { targetId ->
            uiState.items.firstOrNull { it.id == targetId }
        }
    }
    val draggedCalendarTodo = remember(draggedCalendarTodoId, uiState.items) {
        draggedCalendarTodoId?.let { targetId ->
            uiState.items.firstOrNull { it.id == targetId || it.canonicalId == targetId }
        }
    }
    val resolveTodoForDrop: (String) -> TodoItem? = { targetId ->
        uiState.items.firstOrNull { it.id == targetId || it.canonicalId == targetId }
    }
    val activeDropDate = remember(activeDropDateIso) {
        activeDropDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
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
    fun requestTaskReschedule(todo: TodoItem, targetDate: LocalDate) {
        draggedCalendarTodoId = null
        activeCalendarDrag = null
        activeDropDateIso = null
        calendarDropTargetBounds.clear()
        val currentDate = LocalDate.ofInstant(todo.due, zoneId)
        if (currentDate == targetDate) return
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
        if (todo.isRecurring) {
            pendingRescheduleDrop = CalendarTaskRescheduleDrop(todo = todo, targetDate = targetDate)
        } else {
            onMoveTask(todo, targetDate, TaskRescheduleScope.OCCURRENCE)
            selectDate(targetDate)
        }
    }

    fun activeCalendarDropDate(position: Offset): LocalDate? {
        return calendarDropTargetBounds.values
            .asSequence()
            .filter { target -> target.bounds.contains(position) }
            .minByOrNull { target -> target.bounds.width * target.bounds.height }
            ?.date
    }

    fun updateActiveCalendarDropTarget(position: Offset) {
        activeDropDateIso = activeCalendarDropDate(position)?.toString()
    }

    fun finishCalendarDrag(position: Offset?) {
        val drag = activeCalendarDrag
        val targetDate = position?.let(::activeCalendarDropDate)
            ?: activeDropDate
        activeCalendarDrag = null
        draggedCalendarTodoId = null
        activeDropDateIso = null
        calendarDropTargetBounds.clear()
        if (drag != null && targetDate != null) {
            requestTaskReschedule(drag.todo, targetDate)
        }
    }

    fun cancelCalendarDrag() {
        activeCalendarDrag = null
        draggedCalendarTodoId = null
        activeDropDateIso = null
        calendarDropTargetBounds.clear()
    }

    LaunchedEffect(draggedCalendarTodoId) {
        if (draggedCalendarTodoId == null) {
            calendarDropTargetBounds.clear()
        }
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
                    todayJumpRequestId += 1
                    todayJumpRequest = CalendarTodayJumpRequest(
                        id = todayJumpRequestId,
                        targetDate = LocalDate.now(zoneId),
                    )
                },
            )
        },
        floatingActionButton = {
            CalendarCreateTaskFab(
                onClick = { openCreateTaskSheetForSelectedDate() },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { coordinates ->
                    calendarDragContainerOrigin = coordinates.positionInRoot()
                },
        ) {
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 2.dp,
                                shape = RoundedCornerShape(CalendarCardCornerRadius),
                                clip = false,
                            )
                            .clip(RoundedCornerShape(CalendarCardCornerRadius))
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(CalendarCardCornerRadius),
                            ),
                    ) {
                        AnimatedContent(
                            targetState = selectedViewMode,
                            transitionSpec = {
                                val enteringForward = targetState.ordinal > initialState.ordinal
                                val enter = slideInHorizontally(
                                    animationSpec = tween(durationMillis = 200),
                                    initialOffsetX = { fullWidth ->
                                        if (enteringForward) fullWidth / 4 else -fullWidth / 4
                                    },
                                )
                                val exit = slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 180),
                                    targetOffsetX = { fullWidth ->
                                        if (enteringForward) -fullWidth / 4 else fullWidth / 4
                                    },
                                )
                                (enter togetherWith exit).using(SizeTransform(clip = true))
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
                                    draggedTodo = draggedCalendarTodo,
                                    activeDropDate = activeDropDate,
                                    dropTargets = calendarDropTargetBounds,
                                    canSelectDate = ::canNavigateTo,
                                    todayJumpRequest = todayJumpRequest,
                                    onTodayJumpHandled = ::clearTodayJumpRequest,
                                    onPrevMonth = {
                                        if (visibleMonth > minNavigableMonth) {
                                            visibleMonthIso = visibleMonth.minusMonths(1).toString()
                                        }
                                    },
                                    onNextMonth = {
                                        visibleMonthIso = visibleMonth.plusMonths(1).toString()
                                    },
                                    onSelectDate = ::selectDate,
                                    onDropDateChanged = { date ->
                                        activeDropDateIso = date?.toString()
                                    },
                                    onMoveTaskToDate = ::requestTaskReschedule,
                                    resolveTodo = resolveTodoForDrop,
                                )

                                CalendarViewMode.WEEK -> CalendarWeekCard(
                                    selectedDate = selectedDate,
                                    today = today,
                                    tasksByDate = tasksByDate,
                                    draggedTodo = draggedCalendarTodo,
                                    activeDropDate = activeDropDate,
                                    dropTargets = calendarDropTargetBounds,
                                    canGoPrevWeek = canNavigateTo(selectedDate.minusWeeks(1)),
                                    canSelectDate = ::canNavigateTo,
                                    todayJumpRequest = todayJumpRequest,
                                    onTodayJumpHandled = ::clearTodayJumpRequest,
                                    onPrevWeek = { selectDate(selectedDate.minusWeeks(1)) },
                                    onNextWeek = { selectDate(selectedDate.plusWeeks(1)) },
                                    onSelectDate = ::selectDate,
                                    onDropDateChanged = { date ->
                                        activeDropDateIso = date?.toString()
                                    },
                                    onMoveTaskToDate = ::requestTaskReschedule,
                                    resolveTodo = resolveTodoForDrop,
                                )

                                CalendarViewMode.DAY -> CalendarDayCard(
                                    selectedDate = selectedDate,
                                    today = today,
                                    tasksByDate = tasksByDate,
                                    canGoPrevDay = canNavigateTo(selectedDate.minusDays(1)),
                                    canSelectDate = ::canNavigateTo,
                                    todayJumpRequest = todayJumpRequest,
                                    onTodayJumpHandled = ::clearTodayJumpRequest,
                                    onPrevDay = { selectDate(selectedDate.minusDays(1)) },
                                    onNextDay = { selectDate(selectedDate.plusDays(1)) },
                                    onSelectDate = ::selectDate,
                                )
                            }
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

                    if (selectedDatePendingTasks.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            selectedDatePendingTasks.forEachIndexed { index, todo ->
                                key(todo.id) {
                                    CalendarTodoRow(
                                        todo = todo,
                                        lists = uiState.lists,
                                        showDateDivider = shouldShowDateDivider(
                                            afterItemIndex = index,
                                            items = selectedDatePendingTasks,
                                            zoneId = zoneId,
                                        ),
                                        dragEnabled = calendarTaskRescheduleEnabled,
                                        onComplete = { onCompleteTask(todo) },
                                        onInfo = { editTargetId = todo.id },
                                        onDelete = { onDelete(todo) },
                                        dragging = calendarTaskRescheduleEnabled && draggedCalendarTodo?.id == todo.id,
                                        onDragStart = { position ->
                                            activeDropDateIso = null
                                            draggedCalendarTodoId = todo.id
                                            activeCalendarDrag = CalendarTaskDragState(
                                                todo = todo,
                                                position = position,
                                            )
                                            updateActiveCalendarDropTarget(position)
                                        },
                                        onDragMove = { position ->
                                            activeCalendarDrag = CalendarTaskDragState(
                                                todo = todo,
                                                position = position,
                                            )
                                            updateActiveCalendarDropTarget(position)
                                        },
                                        onDragEnd = ::finishCalendarDrag,
                                        onDragCancel = ::cancelCalendarDrag,
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

            activeCalendarDrag?.let { drag ->
                CalendarTaskDragPreview(
                    modifier = Modifier
                        .offset {
                            val localPosition = drag.position - calendarDragContainerOrigin
                            IntOffset(
                                x = (localPosition.x - with(density) { 130.dp.toPx() }).roundToInt(),
                                y = (localPosition.y - with(density) { 34.dp.toPx() }).roundToInt(),
                            )
                        }
                        .zIndex(20f),
                    todo = drag.todo,
                    lists = uiState.lists,
                )
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

    pendingRescheduleDrop?.let { drop ->
        AlertDialog(
            onDismissRequest = { pendingRescheduleDrop = null },
            title = {
                Text(
                    text = stringResource(R.string.todos_reschedule_recurring_title),
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(text = stringResource(R.string.todos_reschedule_recurring_message))
            },
            dismissButton = {
                TextButton(onClick = { pendingRescheduleDrop = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingRescheduleDrop = null
                        onMoveTask(drop.todo, drop.targetDate, TaskRescheduleScope.OCCURRENCE)
                        selectDate(drop.targetDate)
                    }) {
                        Text(stringResource(R.string.todos_reschedule_this_occurrence))
                    }
                    TextButton(onClick = {
                        pendingRescheduleDrop = null
                        onMoveTask(drop.todo, drop.targetDate, TaskRescheduleScope.SERIES)
                        selectDate(drop.targetDate)
                    }) {
                        Text(stringResource(R.string.todos_reschedule_entire_series))
                    }
                }
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

@Composable
private fun CalendarViewModeTabs(
    selectedMode: CalendarViewMode,
    onModeSelected: (CalendarViewMode) -> Unit,
) {
    TdaySegmentedSlider(
        options = CalendarViewMode.entries,
        selectedOption = selectedMode,
        onOptionSelected = onModeSelected,
        accentColor = CalendarAccentPurple,
        label = { mode ->
            mode.name.lowercase(Locale.getDefault())
                .replaceFirstChar { it.uppercase() }
        },
    )
}

@Composable
private fun CalendarWeekCard(
    selectedDate: LocalDate,
    today: LocalDate,
    tasksByDate: Map<LocalDate, List<TodoItem>>,
    draggedTodo: TodoItem?,
    activeDropDate: LocalDate?,
    dropTargets: MutableMap<String, CalendarDateDropTargetBounds>,
    canGoPrevWeek: Boolean,
    canSelectDate: (LocalDate) -> Boolean,
    todayJumpRequest: CalendarTodayJumpRequest?,
    onTodayJumpHandled: (Int) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onDropDateChanged: (LocalDate?) -> Unit,
    onMoveTaskToDate: (TodoItem, LocalDate) -> Unit,
    resolveTodo: (String) -> TodoItem?,
) {
    val colorScheme = MaterialTheme.colorScheme
    val weekStart = remember(selectedDate) { startOfWeek(selectedDate) }
    val coroutineScope = rememberCoroutineScope()
    var pendingTodayJump by remember { mutableStateOf<CalendarTodayJumpRequest?>(null) }
    val todayJumpDirection = pendingTodayJump?.let { request ->
        val targetWeek = startOfWeek(request.targetDate)
        when {
            targetWeek < weekStart -> CalendarPagerSlot.PREVIOUS
            targetWeek > weekStart -> CalendarPagerSlot.NEXT
            else -> null
        }
    }
    val previousPageWeek = if (todayJumpDirection == CalendarPagerSlot.PREVIOUS) {
        pendingTodayJump?.targetDate?.let(::startOfWeek)
    } else if (canGoPrevWeek) {
        weekStart.minusWeeks(1)
    } else {
        null
    }
    val nextPageWeek = if (todayJumpDirection == CalendarPagerSlot.NEXT) {
        pendingTodayJump?.targetDate?.let(::startOfWeek) ?: weekStart.plusWeeks(1)
    } else {
        weekStart.plusWeeks(1)
    }
    val pages = remember(previousPageWeek, weekStart, nextPageWeek) {
        buildList {
            previousPageWeek?.let { add(CalendarPagerPage(CalendarPagerSlot.PREVIOUS, it)) }
            add(CalendarPagerPage(CalendarPagerSlot.CURRENT, weekStart))
            add(CalendarPagerPage(CalendarPagerSlot.NEXT, nextPageWeek))
        }
    }
    val centerPageIndex = pages.indexOfSlot(CalendarPagerSlot.CURRENT).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = centerPageIndex) { pages.size }
    val isPagingAtRest = pagerState.settledPage == centerPageIndex && !pagerState.isScrollInProgress

    fun requestPage(slot: CalendarPagerSlot) {
        val targetIndex = pages.indexOfSlot(slot)
        if (targetIndex < 0 || !isPagingAtRest) return
        coroutineScope.launch {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    fun settlePage(slot: CalendarPagerSlot) {
        pendingTodayJump?.let { request ->
            pendingTodayJump = null
            onSelectDate(request.targetDate)
            onTodayJumpHandled(request.id)
            return
        }

        when (slot) {
            CalendarPagerSlot.PREVIOUS -> onPrevWeek()
            CalendarPagerSlot.NEXT -> onNextWeek()
            CalendarPagerSlot.CURRENT -> Unit
        }
    }

    LaunchedEffect(todayJumpRequest) {
        val request = todayJumpRequest ?: return@LaunchedEffect
        if (!isPagingAtRest) {
            onTodayJumpHandled(request.id)
            return@LaunchedEffect
        }
        val targetWeek = startOfWeek(request.targetDate)
        if (targetWeek == weekStart) {
            onSelectDate(request.targetDate)
            onTodayJumpHandled(request.id)
        } else {
            pendingTodayJump = request
            onTodayJumpHandled(request.id)
        }
    }

    LaunchedEffect(pendingTodayJump?.id, pages) {
        val request = pendingTodayJump ?: return@LaunchedEffect
        val targetWeek = startOfWeek(request.targetDate)
        val targetSlot = if (targetWeek < weekStart) CalendarPagerSlot.PREVIOUS else CalendarPagerSlot.NEXT
        val targetIndex = pages.indexOfSlot(targetSlot)
        if (targetIndex >= 0 && pagerState.currentPage == centerPageIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalendarCardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CalendarCardHorizontalPadding,
                    top = 16.dp,
                    end = CalendarCardHorizontalPadding,
                    bottom = CalendarPeriodCardBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarCardHeaderHeight)
                    .padding(horizontal = CalendarCardHeaderHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniCalendarNavButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.calendar_prev_week),
                    enabled = canGoPrevWeek && isPagingAtRest,
                    onClick = { requestPage(CalendarPagerSlot.PREVIOUS) },
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatWeekRange(weekStart),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = CalendarPeriodHeaderTitleSize,
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (
                            activeDropDate != null &&
                            activeDropDate >= weekStart &&
                            activeDropDate <= weekStart.plusDays(6)
                        ) {
                            colorScheme.error
                        } else {
                            colorScheme.onSurface
                        },
                    )
                }
                MiniCalendarNavButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_week),
                    enabled = isPagingAtRest,
                    onClick = { requestPage(CalendarPagerSlot.NEXT) },
                )
            }

            CalendarPagingContent(
                pages = pages,
                pagerState = pagerState,
                centerPageIndex = centerPageIndex,
                onSettledAwayFromCenter = ::settlePage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarPeriodCardPageHeight),
            ) { displayWeekStart ->
                val weekDays = remember(displayWeekStart) {
                    List(7) { offset -> displayWeekStart.plusDays(offset.toLong()) }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CalendarPeriodPageHorizontalGutter),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    weekDays.forEach { day ->
                        val isSelected = day == selectedDate
                        val isToday = day == today
                        val taskCount = tasksByDate[day]?.size ?: 0
                        val isEnabled = canSelectDate(day)
                        CalendarWeekDayCell(
                            date = day,
                            taskCount = taskCount,
                            isSelected = isSelected,
                            isToday = isToday,
                            isEnabled = isEnabled,
                            isDropTarget = activeDropDate == day,
                            draggedTodo = draggedTodo.takeIf { isEnabled },
                            dropTargets = dropTargets,
                            onClick = { onSelectDate(day) },
                            onDropDateChanged = onDropDateChanged,
                            onMoveTaskToDate = onMoveTaskToDate,
                            resolveTodo = resolveTodo,
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
    isEnabled: Boolean,
    isDropTarget: Boolean,
    draggedTodo: TodoItem?,
    dropTargets: MutableMap<String, CalendarDateDropTargetBounds>,
    onClick: () -> Unit,
    onDropDateChanged: (LocalDate?) -> Unit,
    onMoveTaskToDate: (TodoItem, LocalDate) -> Unit,
    resolveTodo: (String) -> TodoItem?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when {
        isDropTarget -> colorScheme.error.copy(alpha = 0.20f)
        isSelected -> CalendarAccentPurple.copy(alpha = 0.24f)
        isToday -> CalendarTodayBlue.copy(alpha = 0.16f)
        else -> colorScheme.background
    }
    val borderColor = when {
        isDropTarget -> colorScheme.error
        isSelected -> CalendarAccentPurple.copy(alpha = 0.95f)
        isToday -> CalendarTodayBlue.copy(alpha = 0.74f)
        else -> Color.Transparent
    }
    val borderWidth = when {
        isDropTarget -> 2.dp
        isSelected -> 1.6.dp
        isToday -> 1.4.dp
        else -> 0.dp
    }
    val stateTint = when {
        isDropTarget -> colorScheme.error
        isSelected -> CalendarAccentPurple
        isToday -> CalendarTodayBlue
        else -> CalendarAccentPurple
    }

    Box(
        modifier = modifier
            .height(CalendarPeriodCardPageHeight)
            .minimumInteractiveComponentSize()
            .calendarDateDropTarget(
                date = date,
                draggedTodo = draggedTodo,
                enabled = isEnabled,
                onDropDateChanged = onDropDateChanged,
                onMoveTaskToDate = onMoveTaskToDate,
                resolveTodo = resolveTodo,
            )
            .calendarInAppDateDropTarget(
                targetId = "week-$date",
                date = date,
                enabled = isEnabled && draggedTodo != null,
                dropTargets = dropTargets,
            )
            .graphicsLayer { alpha = if (isEnabled) 1f else 0.48f },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(CalendarPeriodWeekDayCellHeight),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(
                width = borderWidth,
                color = borderColor,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            enabled = isEnabled,
            onClick = onClick,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant.copy(alpha = if (isEnabled) 0.9f else 0.52f),
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDropTarget || isSelected || isToday) stateTint else colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = if (taskCount > 9) {
                        stringResource(R.string.calendar_task_count_cap)
                    } else {
                        taskCount.toString()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (taskCount > 0) stateTint else colorScheme.onSurfaceVariant.copy(
                        alpha = 0.42f
                    ),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.calendarDateDropTarget(
    date: LocalDate,
    draggedTodo: TodoItem?,
    enabled: Boolean,
    onDropDateChanged: (LocalDate?) -> Unit,
    onMoveTaskToDate: (TodoItem, LocalDate) -> Unit,
    resolveTodo: (String) -> TodoItem?,
): Modifier {
    if (!enabled) return this

    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            event.mimeTypes().any { mimeType -> mimeType.startsWith("text/") }
        },
        target = object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                onDropDateChanged(date)
            }

            override fun onExited(event: DragAndDropEvent) {
                onDropDateChanged(null)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val todo = draggedTodo ?: event.todoIdText()?.let(resolveTodo) ?: return false
                onDropDateChanged(null)
                onMoveTaskToDate(todo, date)
                return true
            }

            override fun onEnded(event: DragAndDropEvent) {
                onDropDateChanged(null)
            }
        },
    )
}

private fun DragAndDropEvent.todoIdText(): String? {
    val clipData = toAndroidDragEvent().clipData ?: return null
    for (index in 0 until clipData.itemCount) {
        val text = clipData.getItemAt(index).text?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            return text
        }
    }
    return null
}

private fun Modifier.calendarInAppDateDropTarget(
    targetId: String,
    date: LocalDate,
    enabled: Boolean,
    dropTargets: MutableMap<String, CalendarDateDropTargetBounds>,
): Modifier {
    if (!enabled) return this

    return composed {
        DisposableEffect(targetId) {
            onDispose {
                dropTargets.remove(targetId)
            }
        }
        onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            val size = coordinates.size
            dropTargets[targetId] = CalendarDateDropTargetBounds(
                date = date,
                bounds = Rect(
                    left = position.x,
                    top = position.y,
                    right = position.x + size.width,
                    bottom = position.y + size.height,
                ),
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
    canSelectDate: (LocalDate) -> Boolean,
    todayJumpRequest: CalendarTodayJumpRequest?,
    onTodayJumpHandled: (Int) -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()
    var pendingTodayJump by remember { mutableStateOf<CalendarTodayJumpRequest?>(null) }
    val todayJumpDirection = pendingTodayJump?.let { request ->
        when {
            request.targetDate < selectedDate -> CalendarPagerSlot.PREVIOUS
            request.targetDate > selectedDate -> CalendarPagerSlot.NEXT
            else -> null
        }
    }
    val previousPageDay = if (todayJumpDirection == CalendarPagerSlot.PREVIOUS) {
        pendingTodayJump?.targetDate
    } else if (canGoPrevDay) {
        selectedDate.minusDays(1)
    } else {
        null
    }
    val nextPageDay = if (todayJumpDirection == CalendarPagerSlot.NEXT) {
        pendingTodayJump?.targetDate ?: selectedDate.plusDays(1)
    } else {
        selectedDate.plusDays(1)
    }
    val pages = remember(previousPageDay, selectedDate, nextPageDay) {
        buildList {
            previousPageDay?.let { add(CalendarPagerPage(CalendarPagerSlot.PREVIOUS, it)) }
            add(CalendarPagerPage(CalendarPagerSlot.CURRENT, selectedDate))
            add(CalendarPagerPage(CalendarPagerSlot.NEXT, nextPageDay))
        }
    }
    val centerPageIndex = pages.indexOfSlot(CalendarPagerSlot.CURRENT).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = centerPageIndex) { pages.size }
    val isPagingAtRest = pagerState.settledPage == centerPageIndex && !pagerState.isScrollInProgress

    fun requestPage(slot: CalendarPagerSlot) {
        val targetIndex = pages.indexOfSlot(slot)
        if (targetIndex < 0 || !isPagingAtRest) return
        coroutineScope.launch {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    fun settlePage(slot: CalendarPagerSlot) {
        pendingTodayJump?.let { request ->
            pendingTodayJump = null
            onSelectDate(request.targetDate)
            onTodayJumpHandled(request.id)
            return
        }

        when (slot) {
            CalendarPagerSlot.PREVIOUS -> onPrevDay()
            CalendarPagerSlot.NEXT -> onNextDay()
            CalendarPagerSlot.CURRENT -> Unit
        }
    }

    LaunchedEffect(todayJumpRequest) {
        val request = todayJumpRequest ?: return@LaunchedEffect
        if (!isPagingAtRest) {
            onTodayJumpHandled(request.id)
            return@LaunchedEffect
        }
        if (request.targetDate == selectedDate) {
            onSelectDate(request.targetDate)
            onTodayJumpHandled(request.id)
        } else {
            pendingTodayJump = request
            onTodayJumpHandled(request.id)
        }
    }

    LaunchedEffect(pendingTodayJump?.id, pages) {
        val request = pendingTodayJump ?: return@LaunchedEffect
        val targetSlot = if (request.targetDate < selectedDate) {
            CalendarPagerSlot.PREVIOUS
        } else {
            CalendarPagerSlot.NEXT
        }
        val targetIndex = pages.indexOfSlot(targetSlot)
        if (targetIndex >= 0 && pagerState.currentPage == centerPageIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalendarCardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CalendarCardHorizontalPadding,
                    top = 16.dp,
                    end = CalendarCardHorizontalPadding,
                    bottom = CalendarPeriodCardBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarCardHeaderHeight)
                    .padding(horizontal = CalendarCardHeaderHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniCalendarNavButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.calendar_prev_day),
                    enabled = canGoPrevDay && isPagingAtRest,
                    onClick = { requestPage(CalendarPagerSlot.PREVIOUS) },
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = CalendarPeriodHeaderTitleSize,
                        ),
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                MiniCalendarNavButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_day),
                    enabled = isPagingAtRest,
                    onClick = { requestPage(CalendarPagerSlot.NEXT) },
                )
            }

            CalendarPagingContent(
                pages = pages,
                pagerState = pagerState,
                centerPageIndex = centerPageIndex,
                onSettledAwayFromCenter = ::settlePage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarPeriodCardPageHeight),
            ) { displayDate ->
                val taskCount = tasksByDate[displayDate]?.size ?: 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = displayDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = CalendarDaySummaryTitleSize,
                        ),
                        color = when {
                            displayDate == today -> CalendarAccentPurple
                            else -> colorScheme.onSurface
                        },
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = if (taskCount == 1) {
                            stringResource(R.string.calendar_task_count_one)
                        } else {
                            stringResource(R.string.calendar_task_count_many, taskCount)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = CalendarDaySummaryCountSize,
                        ),
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
                    isAccentButton = true,
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
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isBackButton: Boolean = false,
    isAccentButton: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val containerColor = when {
        isBackButton -> if (isDarkTheme) colorScheme.surface.copy(alpha = 0.94f) else Color.White.copy(
            alpha = 0.96f
        )

        isAccentButton -> CalendarAccentPurple.copy(alpha = if (isDarkTheme) 0.22f else 0.12f)
        else -> colorScheme.background
    }
    val buttonBorder = when {
        isBackButton -> null
        isAccentButton -> BorderStroke(
            1.dp,
            CalendarAccentPurple.copy(alpha = if (isDarkTheme) 0.62f else 0.48f)
        )

        else -> BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.34f))
    }
    val iconTint = if (isAccentButton) CalendarAccentPurple else colorScheme.onSurface
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
        shape = CircleShape,
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
                tint = iconTint,
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
    draggedTodo: TodoItem?,
    activeDropDate: LocalDate?,
    dropTargets: MutableMap<String, CalendarDateDropTargetBounds>,
    canSelectDate: (LocalDate) -> Boolean,
    todayJumpRequest: CalendarTodayJumpRequest?,
    onTodayJumpHandled: (Int) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onDropDateChanged: (LocalDate?) -> Unit,
    onMoveTaskToDate: (TodoItem, LocalDate) -> Unit,
    resolveTodo: (String) -> TodoItem?,
) {
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()
    var pendingTodayJump by remember { mutableStateOf<CalendarTodayJumpRequest?>(null) }
    val todayJumpDirection = pendingTodayJump?.let { request ->
        val targetMonth = YearMonth.from(request.targetDate)
        when {
            targetMonth < visibleMonth -> CalendarPagerSlot.PREVIOUS
            targetMonth > visibleMonth -> CalendarPagerSlot.NEXT
            else -> null
        }
    }
    val previousPageMonth = if (todayJumpDirection == CalendarPagerSlot.PREVIOUS) {
        pendingTodayJump?.targetDate?.let(YearMonth::from)
    } else if (canGoPrevMonth) {
        visibleMonth.minusMonths(1)
    } else {
        null
    }
    val nextPageMonth = if (todayJumpDirection == CalendarPagerSlot.NEXT) {
        pendingTodayJump?.targetDate?.let(YearMonth::from) ?: visibleMonth.plusMonths(1)
    } else {
        visibleMonth.plusMonths(1)
    }
    val pages = remember(previousPageMonth, visibleMonth, nextPageMonth) {
        buildList {
            previousPageMonth?.let { add(CalendarPagerPage(CalendarPagerSlot.PREVIOUS, it)) }
            add(CalendarPagerPage(CalendarPagerSlot.CURRENT, visibleMonth))
            add(CalendarPagerPage(CalendarPagerSlot.NEXT, nextPageMonth))
        }
    }
    val centerPageIndex = pages.indexOfSlot(CalendarPagerSlot.CURRENT).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = centerPageIndex) { pages.size }
    val isPagingAtRest = pagerState.settledPage == centerPageIndex && !pagerState.isScrollInProgress

    fun requestPage(slot: CalendarPagerSlot) {
        val targetIndex = pages.indexOfSlot(slot)
        if (targetIndex < 0 || !isPagingAtRest) return
        coroutineScope.launch {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    fun settlePage(slot: CalendarPagerSlot) {
        pendingTodayJump?.let { request ->
            pendingTodayJump = null
            onSelectDate(request.targetDate)
            onTodayJumpHandled(request.id)
            return
        }

        when (slot) {
            CalendarPagerSlot.PREVIOUS -> onPrevMonth()
            CalendarPagerSlot.NEXT -> onNextMonth()
            CalendarPagerSlot.CURRENT -> Unit
        }
    }

    LaunchedEffect(todayJumpRequest) {
        val request = todayJumpRequest ?: return@LaunchedEffect
        if (!isPagingAtRest) {
            onTodayJumpHandled(request.id)
            return@LaunchedEffect
        }
        val targetMonth = YearMonth.from(request.targetDate)
        if (targetMonth == visibleMonth) {
            onSelectDate(request.targetDate)
            onTodayJumpHandled(request.id)
        } else {
            pendingTodayJump = request
            onTodayJumpHandled(request.id)
        }
    }

    LaunchedEffect(pendingTodayJump?.id, pages) {
        val request = pendingTodayJump ?: return@LaunchedEffect
        val targetMonth = YearMonth.from(request.targetDate)
        val targetSlot = if (targetMonth < visibleMonth) {
            CalendarPagerSlot.PREVIOUS
        } else {
            CalendarPagerSlot.NEXT
        }
        val targetIndex = pages.indexOfSlot(targetSlot)
        if (targetIndex >= 0 && pagerState.currentPage == centerPageIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalendarCardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CalendarCardHorizontalPadding,
                    top = CalendarMonthCardTopPadding,
                    end = CalendarCardHorizontalPadding,
                    bottom = CalendarMonthCardBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(CalendarMonthCardOuterSpacing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarCardHeaderHeight)
                    .padding(horizontal = CalendarCardHeaderHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniCalendarNavButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.calendar_prev_month),
                    enabled = canGoPrevMonth && isPagingAtRest,
                    onClick = { requestPage(CalendarPagerSlot.PREVIOUS) },
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                            " " + visibleMonth.year,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = CalendarMonthHeaderTitleSize,
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (activeDropDate?.let { YearMonth.from(it) } == visibleMonth) {
                            colorScheme.error
                        } else {
                            colorScheme.onSurface
                        },
                    )
                }
                MiniCalendarNavButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_month),
                    enabled = isPagingAtRest,
                    onClick = { requestPage(CalendarPagerSlot.NEXT) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarMonthWeekdayHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WEEKDAY_HEADERS.forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            CalendarPagingContent(
                pages = pages,
                pagerState = pagerState,
                centerPageIndex = centerPageIndex,
                onSettledAwayFromCenter = ::settlePage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarMonthGridHeight),
            ) { displayMonth ->
                val monthDays = remember(displayMonth) { buildMonthCells(displayMonth) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CalendarMonthGridSpacing),
                ) {
                    monthDays.chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            week.forEach { cell ->
                                val taskCount = tasksByDate[cell.date]?.size ?: 0
                                val isEnabled = canSelectDate(cell.date)
                                CalendarDayCell(
                                    cell = cell,
                                    taskCount = taskCount,
                                    isSelected = cell.date == selectedDate,
                                    isToday = cell.date == today,
                                    isEnabled = isEnabled,
                                    isDropTarget = activeDropDate == cell.date,
                                    draggedTodo = draggedTodo.takeIf { isEnabled },
                                    dropTargets = dropTargets,
                                    onClick = { onSelectDate(cell.date) },
                                    onDropDateChanged = onDropDateChanged,
                                    onMoveTaskToDate = onMoveTaskToDate,
                                    resolveTodo = resolveTodo,
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
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val enabledIconTint = iconTint ?: colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .width(CalendarCardNavButtonWidth)
            .height(CalendarCardNavButtonHeight)
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
                    radius = 20.dp,
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                enabledIconTint
            } else {
                colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
            },
            modifier = Modifier.size(CalendarCardNavIconSize),
        )
    }
}

@Composable
private fun CalendarDayCell(
    cell: CalendarDayCellModel,
    taskCount: Int,
    isSelected: Boolean,
    isToday: Boolean,
    isEnabled: Boolean,
    isDropTarget: Boolean,
    draggedTodo: TodoItem?,
    dropTargets: MutableMap<String, CalendarDateDropTargetBounds>,
    onClick: () -> Unit,
    onDropDateChanged: (LocalDate?) -> Unit,
    onMoveTaskToDate: (TodoItem, LocalDate) -> Unit,
    resolveTodo: (String) -> TodoItem?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetCellBackground = when {
        isDropTarget -> colorScheme.error.copy(alpha = 0.20f)
        isSelected -> CalendarAccentPurple.copy(alpha = if (isPressed) 0.32f else 0.24f)
        isToday -> CalendarTodayBlue.copy(alpha = if (isPressed) 0.24f else 0.16f)
        isPressed && isEnabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val cellBackground by animateColorAsState(
        targetValue = targetCellBackground,
        label = "calendarMonthDateCellBackground",
    )
    val targetCellBorderColor = when {
        isDropTarget -> colorScheme.error
        isSelected -> CalendarAccentPurple.copy(alpha = 0.95f)
        isToday -> CalendarTodayBlue.copy(alpha = 0.74f)
        isPressed && isEnabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
        else -> Color.Transparent
    }
    val cellBorderColor by animateColorAsState(
        targetValue = targetCellBorderColor,
        label = "calendarMonthDateCellBorder",
    )
    val targetCellBorderWidth = when {
        isDropTarget -> 2.dp
        isSelected -> 1.6.dp
        isToday -> 1.4.dp
        isPressed && isEnabled -> 1.2.dp
        else -> 0.dp
    }
    val cellBorderWidth by animateDpAsState(
        targetValue = targetCellBorderWidth,
        label = "calendarMonthDateCellBorderWidth",
    )
    val stateTint = when {
        isDropTarget -> colorScheme.error
        isSelected -> CalendarAccentPurple
        isToday -> CalendarTodayBlue
        else -> CalendarAccentPurple
    }
    val cellShape = RoundedCornerShape(16.dp)
    val dayTextColor = when {
        isDropTarget || isSelected || isToday -> stateTint
        cell.isCurrentMonth -> colorScheme.onSurface
        else -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CalendarMonthDayCellHeight)
            .graphicsLayer { alpha = if (cell.isCurrentMonth) 1f else 0.45f }
            .calendarDateDropTarget(
                date = cell.date,
                draggedTodo = draggedTodo,
                enabled = isEnabled,
                onDropDateChanged = onDropDateChanged,
                onMoveTaskToDate = onMoveTaskToDate,
                resolveTodo = resolveTodo,
            )
            .calendarInAppDateDropTarget(
                targetId = "month-${cell.date}",
                date = cell.date,
                enabled = isEnabled && draggedTodo != null,
                dropTargets = dropTargets,
            )
            .clickable(
                enabled = isEnabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(CalendarMonthDayHighlightWidth)
                .height(CalendarMonthDayHighlightHeight)
                .clip(cellShape)
                .background(cellBackground, cellShape)
                .border(
                    width = cellBorderWidth,
                    color = cellBorderColor,
                    shape = cellShape,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .width(CalendarMonthDayNumberWidth)
                    .height(CalendarMonthDayNumberHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cell.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 19.sp),
                    color = dayTextColor,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier.height(CalendarMonthTaskCountHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (taskCount > 0 && isEnabled) {
                    Box(
                        modifier = Modifier
                            .size(CalendarMonthTaskDotSize)
                            .background(stateTint, CircleShape),
                    )
                    Text(
                        text = if (taskCount > 9) {
                            stringResource(R.string.calendar_task_count_cap)
                        } else {
                            taskCount.toString()
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                        ),
                        color = stateTint,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarTaskDragPreview(
    modifier: Modifier = Modifier,
    todo: TodoItem,
    lists: List<ListSummary>,
) {
    val colorScheme = MaterialTheme.colorScheme
    val listMeta = todo.listId?.let { listId -> lists.firstOrNull { it.id == listId } }
    val previewShape = RoundedCornerShape(18.dp)
    Card(
        modifier = modifier
            .sizeIn(minWidth = 220.dp, maxWidth = 280.dp),
        shape = previewShape,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = CalendarTaskDragDueTimeFormatter.format(todo.due),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (listMeta != null) {
                Icon(
                    imageVector = listIconForKey(listMeta.iconKey),
                    contentDescription = null,
                    tint = listAccentColor(listMeta.color),
                    modifier = Modifier.size(18.dp),
                )
            }
            if (isHighPriority(todo.priority)) {
                Icon(
                    imageVector = Icons.Rounded.Flag,
                    contentDescription = null,
                    tint = priorityColor(todo.priority),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarTodoRow(
    modifier: Modifier = Modifier,
    todo: TodoItem,
    lists: List<ListSummary>,
    showDateDivider: Boolean,
    dragEnabled: Boolean,
    onComplete: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    dragging: Boolean,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: (Offset?) -> Unit,
    onDragCancel: () -> Unit,
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
    var rowOriginInRoot by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var dragPointerPosition by remember(todo.id) { mutableStateOf<Offset?>(null) }
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
            .graphicsLayer { alpha = if (dragging) 0.55f else 1f }
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
                    .onGloballyPositioned { coordinates ->
                        rowOriginInRoot = coordinates.positionInRoot()
                    }
                    .graphicsLayer { translationX = animatedOffsetX }
                    .then(
                        if (dragEnabled) {
                            Modifier.pointerInput(todo.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset ->
                                        targetOffsetX = 0f
                                        val startPosition = rowOriginInRoot + localOffset
                                        dragPointerPosition = startPosition
                                        onDragStart(startPosition)
                                        onDragMove(startPosition)
                                        ViewCompat.performHapticFeedback(
                                            view,
                                            HapticFeedbackConstantsCompat.CLOCK_TICK,
                                        )
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val nextPosition =
                                            (dragPointerPosition ?: rowOriginInRoot) + dragAmount
                                        dragPointerPosition = nextPosition
                                        onDragMove(nextPosition)
                                    },
                                    onDragEnd = {
                                        onDragEnd(dragPointerPosition)
                                        dragPointerPosition = null
                                    },
                                    onDragCancel = {
                                        dragPointerPosition = null
                                        onDragCancel()
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
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
        if (showDateDivider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )
        }
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
        "calendar" -> Icons.Rounded.CalendarToday
        "schedule" -> Icons.Rounded.Schedule
        "flag" -> Icons.Rounded.Flag
        "check" -> Icons.Rounded.Check
        "smile" -> Icons.Rounded.Mood
        "list" -> Icons.AutoMirrored.Rounded.List
        "bookmark" -> Icons.Rounded.Bookmark
        "key" -> Icons.Rounded.Key
        "gift" -> Icons.Rounded.CardGiftcard
        "cake" -> Icons.Rounded.Cake
        "school" -> Icons.Rounded.School
        "bag" -> Icons.Rounded.Backpack
        "edit" -> Icons.Rounded.Edit
        "document" -> Icons.Rounded.Description
        "inbox" -> Icons.Rounded.Inbox
        "book" -> Icons.AutoMirrored.Rounded.MenuBook
        "work", "briefcase" -> Icons.Rounded.Work
        "wallet" -> Icons.Rounded.AccountBalanceWallet
        "money" -> Icons.Rounded.Payments
        "health" -> Icons.Rounded.Medication
        "fitness" -> Icons.Rounded.FitnessCenter
        "run" -> Icons.AutoMirrored.Rounded.DirectionsRun
        "food" -> Icons.Rounded.Restaurant
        "drink", "cocktail" -> Icons.Rounded.LocalBar
        "monitor" -> Icons.Rounded.DesktopWindows
        "music" -> Icons.Rounded.MusicNote
        "computer" -> Icons.Rounded.Computer
        "game" -> Icons.Rounded.SportsEsports
        "headphones" -> Icons.Rounded.Headphones
        "eco" -> Icons.Rounded.Eco
        "pets" -> Icons.Rounded.Pets
        "child" -> Icons.Rounded.ChildCare
        "family" -> Icons.Rounded.FamilyRestroom
        "basket" -> Icons.Rounded.ShoppingBasket
        "cart" -> Icons.Rounded.ShoppingCart
        "mall" -> Icons.Rounded.LocalMall
        "inventory" -> Icons.Rounded.Inventory
        "soccer" -> Icons.Rounded.SportsSoccer
        "baseball" -> Icons.Rounded.SportsBaseball
        "basketball" -> Icons.Rounded.SportsBasketball
        "football" -> Icons.Rounded.SportsFootball
        "tennis" -> Icons.Rounded.SportsTennis
        "train" -> Icons.Rounded.Train
        "flight", "travel" -> Icons.Rounded.Flight
        "boat" -> Icons.Rounded.DirectionsBoat
        "car" -> Icons.Rounded.DirectionsCar
        "umbrella" -> Icons.Rounded.BeachAccess
        "drop" -> Icons.Rounded.WaterDrop
        "snow" -> Icons.Rounded.AcUnit
        "fire" -> Icons.Rounded.Whatshot
        "tools" -> Icons.Rounded.Build
        "scissors" -> Icons.Rounded.ContentCut
        "architecture" -> Icons.Rounded.Architecture
        "bank" -> Icons.Rounded.AccountBalance
        "code" -> Icons.Rounded.Code
        "idea" -> Icons.Rounded.Lightbulb
        "chat" -> Icons.Rounded.ChatBubbleOutline
        "alert" -> Icons.Rounded.PriorityHigh
        "star" -> Icons.Rounded.Star
        "heart" -> Icons.Rounded.Favorite
        "circle" -> Icons.Rounded.Circle
        "square" -> Icons.Rounded.Square
        "triangle" -> Icons.Rounded.ChangeHistory
        "home" -> Icons.Rounded.Home
        "city" -> Icons.Rounded.LocationCity
        "camera" -> Icons.Rounded.CameraAlt
        "palette" -> Icons.Rounded.Palette
        else -> Icons.Rounded.Inbox
    }
}

private val WEEKDAY_HEADERS = listOf("S", "M", "T", "W", "T", "F", "S")
