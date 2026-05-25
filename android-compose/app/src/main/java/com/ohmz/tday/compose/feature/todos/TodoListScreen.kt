package com.ohmz.tday.compose.feature.todos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TaskRescheduleScope
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
import com.ohmz.tday.compose.core.model.supportsTaskReschedule
import com.ohmz.tday.compose.core.model.timelineRescheduleTargetDate
import com.ohmz.tday.compose.core.ui.EmptyTaskBackgroundMessage
import com.ohmz.tday.compose.core.ui.EmptyTaskWatermark
import com.ohmz.tday.compose.core.ui.TaskSwipeActionButton
import com.ohmz.tday.compose.core.ui.snapTitleCollapsePx
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.theme.TdayDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoListScreen(
    uiState: TodoListUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    highlightedTodoId: String? = null,
    onSummarize: () -> Unit,
    onDismissSummaryConnectivityError: () -> Unit,
    onAddTask: (payload: CreateTaskPayload) -> Unit,
    onParseTaskTitleNlp: suspend (title: String, referenceDueEpochMs: Long) -> TodoTitleNlpResponse?,
    onUpdateTask: (todo: TodoItem, payload: CreateTaskPayload) -> Unit,
    onMoveTask: (todo: TodoItem, targetDate: LocalDate, scope: TaskRescheduleScope) -> Unit,
    onComplete: (todo: TodoItem) -> Unit,
    onDelete: (todo: TodoItem) -> Unit,
    onUpdateListSettings: (listId: String, name: String, color: String?, iconKey: String?) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val zoneId = remember { ZoneId.systemDefault() }
    val selectedList = uiState.lists.firstOrNull { it.id == uiState.listId }
    val selectedListColorKey = selectedList?.color
    val usesTodayStyle =
        uiState.mode == TodoListMode.TODAY || uiState.mode == TodoListMode.OVERDUE || uiState.mode == TodoListMode.SCHEDULED || uiState.mode == TodoListMode.ALL || uiState.mode == TodoListMode.PRIORITY || uiState.mode == TodoListMode.LIST
    val titleColor = modeAccentColor(
        mode = uiState.mode,
        listColorKey = selectedListColorKey,
    )
    val fabColor = todoFabColorForMode(
        mode = uiState.mode,
        listColorKey = selectedListColorKey,
    )
    val emptyWatermarkIcon = emptyStateIconForMode(
        mode = uiState.mode,
        listIconKey = selectedList?.iconKey,
    )
    val showSectionedTimeline =
        uiState.mode == TodoListMode.TODAY || uiState.mode == TodoListMode.OVERDUE || uiState.mode == TodoListMode.SCHEDULED || uiState.mode == TodoListMode.ALL || uiState.mode == TodoListMode.PRIORITY || uiState.mode == TodoListMode.LIST
    val suppressInitialTodayTimeline =
        uiState.mode == TodoListMode.TODAY &&
                !uiState.hasHydratedSnapshot &&
                uiState.items.isEmpty()
    var draggedScheduledTodoId by rememberSaveable(uiState.mode) { mutableStateOf<String?>(null) }
    val canRescheduleTasks = uiState.mode.supportsTaskReschedule()
    val timelineSections = remember(uiState.mode, uiState.items, draggedScheduledTodoId) {
        buildTimelineSections(
            mode = uiState.mode,
            items = uiState.items,
            includeEmptyEarlierTarget = canRescheduleTasks && draggedScheduledTodoId != null,
        )
    }
    var timelineAnimationsReady by remember(uiState.mode, uiState.listId) {
        mutableStateOf(uiState.mode != TodoListMode.TODAY)
    }
    LaunchedEffect(uiState.mode, uiState.listId, uiState.hasHydratedSnapshot) {
        if (uiState.mode != TodoListMode.TODAY) {
            timelineAnimationsReady = true
            return@LaunchedEffect
        }
        if (!uiState.hasHydratedSnapshot) {
            timelineAnimationsReady = false
            return@LaunchedEffect
        }
        if (!timelineAnimationsReady) {
            withFrameNanos { }
            timelineAnimationsReady = true
        }
    }
    val timelineAnimationsEnabled =
        uiState.mode != TodoListMode.TODAY || timelineAnimationsReady
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
                val isListAtTop =
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && !isListAtTop) return Velocity.Zero
                val snapped = snapTitleCollapsePx(
                    currentPx = todayHeaderCollapsePx,
                    maxPx = maxTodayCollapsePx,
                    velocityY = available.y,
                )
                if (snapped == todayHeaderCollapsePx) return Velocity.Zero
                todayHeaderCollapsePx = snapped
                return if (available.y == 0f) Velocity.Zero else available
            }
        }
    }
    val todayCollapseProgress by animateFloatAsState(
        targetValue = todayCollapseProgressTarget,
        label = "todayTitleCollapseProgress",
    )
    LaunchedEffect(
        usesTodayStyle,
        listState.isScrollInProgress,
        todayHeaderCollapsePx,
        maxTodayCollapsePx,
    ) {
        if (!usesTodayStyle ||
            listState.isScrollInProgress ||
            todayHeaderCollapsePx <= 0f ||
            todayHeaderCollapsePx >= maxTodayCollapsePx
        ) {
            return@LaunchedEffect
        }
        val isListAtTop =
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        todayHeaderCollapsePx = if (isListAtTop) {
            snapTitleCollapsePx(todayHeaderCollapsePx, maxTodayCollapsePx)
        } else {
            maxTodayCollapsePx
        }
    }
    val isCollapsibleTimelineMode =
        uiState.mode == TodoListMode.ALL || uiState.mode == TodoListMode.PRIORITY
    var showCreateTaskSheet by rememberSaveable { mutableStateOf(false) }
    var collapsedSectionKeys by rememberSaveable(uiState.mode, highlightedTodoId) {
        mutableStateOf(
            if (isCollapsibleTimelineMode && highlightedTodoId.isNullOrBlank()) {
                setOf("earlier")
            } else {
                emptySet()
            },
        )
    }
    var flashTodoId by remember(uiState.mode) { mutableStateOf<String?>(null) }
    var quickAddDueEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var editTargetTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDropSectionKey by remember(uiState.mode) { mutableStateOf<String?>(null) }
    var activeTimelineDrag by remember(uiState.mode) { mutableStateOf<TimelineInAppDrag?>(null) }
    var timelineDragContainerOrigin by remember(uiState.mode) { mutableStateOf(Offset.Zero) }
    val timelineDropTargetBounds =
        remember(uiState.mode) { mutableStateMapOf<String, TimelineDropTargetBounds>() }
    var pendingRescheduleDrop by remember(uiState.mode) { mutableStateOf<TaskRescheduleDrop?>(null) }
    var showListSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showSummarySheet by rememberSaveable(uiState.mode) { mutableStateOf(false) }
    var listSettingsTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var listSettingsName by rememberSaveable { mutableStateOf("") }
    var listSettingsColor by rememberSaveable { mutableStateOf(DEFAULT_LIST_COLOR_KEY) }
    var listSettingsIconKey by rememberSaveable { mutableStateOf(DEFAULT_LIST_ICON_KEY) }
    var listSettingsColorTouched by rememberSaveable { mutableStateOf(false) }
    var listSettingsIconTouched by rememberSaveable { mutableStateOf(false) }
    val fabInteractionSource = remember { MutableInteractionSource() }
    val editTargetTodo = remember(editTargetTodoId, uiState.items) {
        editTargetTodoId?.let { targetId -> uiState.items.firstOrNull { it.id == targetId } }
    }
    val draggedScheduledTodo = remember(draggedScheduledTodoId, uiState.items) {
        draggedScheduledTodoId?.let { targetId ->
            uiState.items.firstOrNull { it.id == targetId || it.canonicalId == targetId }
        }
    }
    val requestTaskReschedule: (TodoItem, LocalDate) -> Unit = { todo, targetDate ->
        draggedScheduledTodoId = null
        activeDropSectionKey = null
        activeTimelineDrag = null
        timelineDropTargetBounds.clear()
        val currentDate = LocalDate.ofInstant(todo.due, zoneId)
        if (currentDate != targetDate) {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            if (todo.isRecurring) {
                pendingRescheduleDrop = TaskRescheduleDrop(todo = todo, targetDate = targetDate)
            } else {
                onMoveTask(todo, targetDate, TaskRescheduleScope.OCCURRENCE)
            }
        }
    }
    val canSummarizeCurrentMode =
        uiState.mode != TodoListMode.LIST &&
            uiState.mode != TodoListMode.OVERDUE &&
            uiState.aiSummaryEnabled
    val showTopBarActionButton = canSummarizeCurrentMode || uiState.mode == TodoListMode.LIST
    val fabPressed by fabInteractionSource.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.93f else 1f,
        label = "todoFabScale",
    )
    val fabOffsetY by animateDpAsState(
        targetValue = if (fabPressed) 2.dp else 0.dp,
        label = "todoFabOffsetY",
    )
    val timelineItemSpacing = if (usesTodayStyle) 4.dp else 8.dp
    val timelineHeaderBodySpacing = if (usesTodayStyle) 4.dp else 8.dp
    fun highlightedTodoListTarget(todoId: String): Pair<Int, String>? {
        var itemIndex = 0
        timelineSections.forEach { section ->
            itemIndex += 1
            val todoIndex = section.items.indexOfFirst { item ->
                item.id == todoId || item.canonicalId == todoId
            }
            if (todoIndex >= 0) {
                val todo = section.items[todoIndex]
                return itemIndex + todoIndex to "timeline-todo-${section.key}-${todo.id}"
            }
            itemIndex += section.items.size
        }
        return null
    }
    LaunchedEffect(showSummarySheet, canSummarizeCurrentMode) {
        if (showSummarySheet && canSummarizeCurrentMode) {
            onSummarize()
        }
    }
    LaunchedEffect(highlightedTodoId, uiState.mode, timelineSections) {
        if (uiState.mode != TodoListMode.ALL || highlightedTodoId.isNullOrBlank()) return@LaunchedEffect
        val target = highlightedTodoListTarget(highlightedTodoId)
        if (target != null) {
            todayHeaderCollapsePx = maxTodayCollapsePx
            delay(SEARCH_RESULT_NAV_SETTLE_DELAY_MS)
            val viewportHeight =
                listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
            val estimatedRowHeight =
                with(density) { SEARCH_RESULT_ESTIMATED_ROW_HEIGHT_DP.dp.toPx().toInt() }
            val centeredScrollOffset =
                -((viewportHeight - estimatedRowHeight).coerceAtLeast(0) / 2)
            listState.animateSearchResultScrollToItem(
                targetIndex = target.first,
                targetKey = target.second,
                centeredScrollOffset = centeredScrollOffset,
                estimatedItemSizePx = estimatedRowHeight,
            )
            flashTodoId = highlightedTodoId
            delay(2300)
            if (flashTodoId == highlightedTodoId) {
                flashTodoId = null
            }
        }
    }
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == TodoListMode.PRIORITY) {
            collapsedSectionKeys = collapsedSectionKeys + "earlier"
        }
    }
    LaunchedEffect(draggedScheduledTodoId) {
        if (draggedScheduledTodoId == null) {
            timelineDropTargetBounds.clear()
        }
    }

    fun updateActiveTimelineDropTarget(position: Offset) {
        activeDropSectionKey = timelineDropTargetBounds.values
            .asSequence()
            .filter { target -> target.bounds.contains(position) }
            .minByOrNull { target -> target.bounds.height }
            ?.sectionKey
    }

    fun finishTimelineDrag(position: Offset?) {
        val drag = activeTimelineDrag
        val targetKey = position
            ?.let { dropPosition ->
                timelineDropTargetBounds.values
                    .asSequence()
                    .filter { target -> target.bounds.contains(dropPosition) }
                    .minByOrNull { target -> target.bounds.height }
                    ?.sectionKey
            }
            ?: activeDropSectionKey
        val targetDate = targetKey
            ?.let { key -> timelineSections.firstOrNull { section -> section.key == key } }
            ?.targetDate
        activeTimelineDrag = null
        draggedScheduledTodoId = null
        activeDropSectionKey = null
        timelineDropTargetBounds.clear()
        if (drag != null && targetDate != null) {
            requestTaskReschedule(drag.todo, targetDate)
        }
    }

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            if (usesTodayStyle) {
                TodayTopBar(
                    onBack = onBack,
                    collapseProgress = todayCollapseProgress,
                    title = uiState.title,
                    titleColor = titleColor,
                    showActionButton = showTopBarActionButton,
                    actionIcon = if (canSummarizeCurrentMode) {
                        Icons.Rounded.AutoAwesome
                    } else {
                        Icons.Rounded.MoreHoriz
                    },
                    actionContentDescription = if (canSummarizeCurrentMode) {
                        stringResource(R.string.todos_summarize)
                    } else {
                        stringResource(R.string.action_more_options)
                    },
                    onAction = {
                        if (canSummarizeCurrentMode) {
                            showSummarySheet = true
                        } else if (selectedList != null) {
                            listSettingsTargetId = selectedList.id
                            listSettingsName = selectedList.name
                            listSettingsColor = normalizedListColorKey(selectedList.color)
                            listSettingsIconKey = selectedList.iconKey
                                ?.takeIf { isSupportedListIconKey(it) }
                                ?: DEFAULT_LIST_ICON_KEY
                            listSettingsColorTouched = false
                            listSettingsIconTouched = false
                            showListSettingsSheet = true
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = titleColor,
                        )
                    },
                    navigationIcon = {
                        TodayHeaderButton(
                            onClick = onBack,
                            icon = Icons.Rounded.ChevronLeft,
                            contentDescription = stringResource(R.string.action_back),
                            isBackButton = true,
                        )
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
                backgroundColor = fabColor,
                onClick = {
                    quickAddDueEpochMs = null
                    showCreateTaskSheet = true
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    timelineDragContainerOrigin = coordinates.positionInRoot()
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (usesTodayStyle) {
                                Modifier.nestedScroll(todayNestedScrollConnection)
                            } else {
                                Modifier
                            },
                        ),
                    state = listState,
                    contentPadding = if (usesTodayStyle) {
                        PaddingValues(horizontal = 18.dp, vertical = 2.dp)
                    } else {
                        PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    },
                    verticalArrangement = Arrangement.spacedBy(
                        if (showSectionedTimeline) 0.dp else timelineItemSpacing,
                    ),
                ) {
                    if (!showSectionedTimeline && uiState.items.isEmpty() && uiState.isLoading) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text(
                                    modifier = Modifier.padding(18.dp),
                                    text = stringResource(R.string.label_loading),
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (showSectionedTimeline && !suppressInitialTodayTimeline) {
                        timelineSections.forEachIndexed { sectionIndex, section ->
                            val sectionHasTasks = section.items.isNotEmpty()
                            val sectionModeCanCollapse = when (uiState.mode) {
                                TodoListMode.ALL -> true
                                TodoListMode.OVERDUE -> true
                                TodoListMode.SCHEDULED -> true
                                TodoListMode.PRIORITY -> section.key == "earlier"
                                else -> false
                            }
                            val sectionCanCollapse = sectionModeCanCollapse && sectionHasTasks
                            val isCollapsed =
                                sectionCanCollapse && collapsedSectionKeys.contains(section.key)
                            val isActiveDropSection = activeDropSectionKey == section.key
                            val sectionDraggedTodo = if (canRescheduleTasks) {
                                draggedScheduledTodo
                            } else {
                                null
                            }

                            item(
                                key = "timeline-header-${section.key}",
                                contentType = "timeline-header",
                            ) {
                                var headerModifier: Modifier = Modifier
                                if (timelineAnimationsEnabled) {
                                    headerModifier = headerModifier.animateItem(
                                        fadeInSpec = null,
                                        placementSpec = tween(
                                            durationMillis = 320,
                                            easing = FastOutSlowInEasing,
                                        ),
                                        fadeOutSpec = null,
                                    )
                                }
                                TimelineSectionHeader(
                                    modifier = headerModifier
                                        .fillMaxWidth()
                                        .heightIn(
                                            min = if (canRescheduleTasks && draggedScheduledTodoId != null) {
                                                if (usesTodayStyle) 44.dp else 56.dp
                                            } else {
                                                1.dp
                                            },
                                        )
                                        .timelineInAppDropTarget(
                                            targetId = "header-${section.key}",
                                            section = section,
                                            enabled = canRescheduleTasks && draggedScheduledTodoId != null,
                                            dropTargets = timelineDropTargetBounds,
                                        )
                                        .padding(top = if (sectionIndex == 0) 0.dp else 8.dp),
                                    section = section,
                                    useMinimalStyle = usesTodayStyle,
                                    isCollapsed = isCollapsed,
                                    isDropTarget = isActiveDropSection,
                                    bottomSpacing = if (isCollapsed) {
                                        timelineItemSpacing
                                    } else {
                                        timelineHeaderBodySpacing
                                    },
                                    onHeaderClick = if (sectionCanCollapse) {
                                        {
                                            collapsedSectionKeys =
                                                if (isCollapsed) {
                                                    collapsedSectionKeys - section.key
                                                } else {
                                                    collapsedSectionKeys + section.key
                                                }
                                        }
                                    } else {
                                        null
                                    },
                                    onTapForQuickAdd = section.quickAddDefaults
                                        ?.takeUnless { sectionModeCanCollapse }
                                        ?.let { dueEpochMs ->
                                            {
                                                quickAddDueEpochMs = dueEpochMs
                                                showCreateTaskSheet = true
                                            }
                                        },
                                )
                            }

                            if (canRescheduleTasks && isActiveDropSection && section.targetDate != null) {
                                item(
                                    key = "timeline-drop-placeholder-${section.key}",
                                    contentType = "timeline-drop-placeholder",
                                ) {
                                    var placeholderModifier: Modifier = Modifier
                                    if (timelineAnimationsEnabled) {
                                        placeholderModifier = placeholderModifier.animateItem(
                                            fadeInSpec = tween(
                                                durationMillis = 150,
                                                easing = FastOutSlowInEasing,
                                            ),
                                            placementSpec = tween(
                                                durationMillis = 260,
                                                easing = FastOutSlowInEasing,
                                            ),
                                            fadeOutSpec = tween(
                                                durationMillis = 120,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                    TimelineDropPlaceholder(
                                        modifier = placeholderModifier
                                            .timelineInAppDropTarget(
                                                targetId = "placeholder-${section.key}",
                                                section = section,
                                                enabled = true,
                                                dropTargets = timelineDropTargetBounds,
                                            )
                                            .padding(
                                                bottom = if (isCollapsed || section.items.isEmpty()) {
                                                    timelineItemSpacing
                                                } else {
                                                    8.dp
                                                },
                                            ),
                                        active = true,
                                        useMinimalStyle = usesTodayStyle,
                                    )
                                }
                            }

                            if (!isCollapsed && section.items.isNotEmpty()) {
                                val showEarlierDateTimeSubtitle =
                                    section.key == "earlier" &&
                                            (uiState.mode == TodoListMode.ALL || uiState.mode == TodoListMode.PRIORITY)
                                section.items.forEachIndexed { itemIndex, todo ->
                                    item(
                                        key = "timeline-todo-${section.key}-${todo.id}",
                                        contentType = "timeline-todo",
                                    ) {
                                        var rowModifier: Modifier = Modifier
                                        if (timelineAnimationsEnabled) {
                                            rowModifier = rowModifier.animateItem(
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
                                        }
                                        TimelineTaskRow(
                                            modifier = rowModifier
                                                .timelineInAppDropTarget(
                                                    targetId = "row-${section.key}-${todo.id}",
                                                    section = section,
                                                    enabled = canRescheduleTasks && draggedScheduledTodoId != null,
                                                    dropTargets = timelineDropTargetBounds,
                                                )
                                                .padding(
                                                    bottom = if (itemIndex == section.items.lastIndex) {
                                                        timelineItemSpacing
                                                    } else {
                                                        8.dp
                                                    },
                                                ),
                                            todo = todo,
                                            mode = uiState.mode,
                                            lists = uiState.lists,
                                            useMinimalStyle = usesTodayStyle,
                                            flashHighlight = flashTodoId == todo.id || flashTodoId == todo.canonicalId,
                                            showEarlierDateTimeSubtitle = showEarlierDateTimeSubtitle,
                                            showDateDivider = shouldShowDateDivider(
                                                afterItemIndex = itemIndex,
                                                inSectionIndex = sectionIndex,
                                                sections = timelineSections,
                                                collapsedSectionKeys = collapsedSectionKeys,
                                            ),
                                            onComplete = { onComplete(todo) },
                                            onDelete = { onDelete(todo) },
                                            onInfo = {
                                                editTargetTodoId = todo.id
                                            },
                                            draggedTodo = sectionDraggedTodo,
                                            onDragTodoStart = if (canRescheduleTasks) {
                                                { position ->
                                                    activeDropSectionKey = null
                                                    timelineDropTargetBounds.clear()
                                                    draggedScheduledTodoId = todo.id
                                                    activeTimelineDrag =
                                                        TimelineInAppDrag(todo, position)
                                                }
                                            } else {
                                                null
                                            },
                                            onDragTodoMove = { position ->
                                                activeTimelineDrag =
                                                    activeTimelineDrag?.copy(position = position)
                                                        ?: TimelineInAppDrag(todo, position)
                                                updateActiveTimelineDropTarget(position)
                                            },
                                            onDragTodoEnd = { position ->
                                                finishTimelineDrag(position)
                                            },
                                            onDragTodoCancel = {
                                                activeTimelineDrag = null
                                                draggedScheduledTodoId = null
                                                activeDropSectionKey = null
                                                timelineDropTargetBounds.clear()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!showSectionedTimeline) {
                        items(
                            items = uiState.items,
                            key = { it.id },
                            contentType = { "todo-row" },
                        ) { todo ->
                            if (usesTodayStyle) {
                                TodayTodoRow(
                                    todo = todo,
                                    onComplete = { onComplete(todo) },
                                    onDelete = { onDelete(todo) },
                                )
                            } else {
                                TodoRow(
                                    todo = todo,
                                    onComplete = { onComplete(todo) },
                                    onDelete = { onDelete(todo) },
                                )
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

                    item { Spacer(Modifier.height(96.dp)) }
                }
            }

            if (uiState.items.isEmpty() && !uiState.isLoading && !suppressInitialTodayTimeline) {
                EmptyTaskWatermark(
                    imageVector = emptyWatermarkIcon,
                    accentColor = titleColor,
                )
                EmptyTaskBackgroundMessage(
                    message = emptyStateMessageForMode(uiState.mode),
                )
            }

            activeTimelineDrag?.let { drag ->
                TimelineTaskDragPreview(
                    modifier = Modifier
                        .offset {
                            val localPosition = drag.position - timelineDragContainerOrigin
                            IntOffset(
                                x = (localPosition.x - with(density) { 130.dp.toPx() }).roundToInt(),
                                y = (localPosition.y - with(density) { 34.dp.toPx() }).roundToInt(),
                            )
                        }
                        .zIndex(20f),
                    todo = drag.todo,
                    lists = uiState.lists,
                    mode = uiState.mode,
                )
            }
        }
    }

    if (showCreateTaskSheet) {
        CreateTaskBottomSheet(
            lists = uiState.lists,
            defaultListId = if (uiState.mode == TodoListMode.LIST) uiState.listId else null,
            defaultPriority = if (uiState.mode == TodoListMode.PRIORITY) "Medium" else null,
            initialDueEpochMs = quickAddDueEpochMs,
            onParseTaskTitleNlp = onParseTaskTitleNlp,
            onDismiss = {
                showCreateTaskSheet = false
                quickAddDueEpochMs = null
            },
            onCreateTask = { payload ->
                onAddTask(payload)
                showCreateTaskSheet = false
                quickAddDueEpochMs = null
            },
        )
    }

    LaunchedEffect(uiState.summaryConnectivityError) {
        if (uiState.summaryConnectivityError) showSummarySheet = false
    }

    if (showSummarySheet) {
        SummaryBottomSheet(
            isLoading = uiState.isSummarizing,
            summaryText = uiState.summaryText,
            errorMessage = uiState.summaryError,
            onDismiss = { showSummarySheet = false },
        )
    }

    if (uiState.summaryConnectivityError) {
        AlertDialog(
            onDismissRequest = onDismissSummaryConnectivityError,
            title = {
                Text(
                    text = stringResource(R.string.error_connectivity_title),
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(text = stringResource(R.string.error_connectivity))
            },
            dismissButton = {
                TextButton(onClick = onDismissSummaryConnectivityError) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissSummaryConnectivityError()
                    showSummarySheet = true
                }) {
                    Text(stringResource(R.string.action_retry))
                }
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
                    }) {
                        Text(stringResource(R.string.todos_reschedule_this_occurrence))
                    }
                    TextButton(onClick = {
                        pendingRescheduleDrop = null
                        onMoveTask(drop.todo, drop.targetDate, TaskRescheduleScope.SERIES)
                    }) {
                        Text(stringResource(R.string.todos_reschedule_entire_series))
                    }
                }
            },
        )
    }

    editTargetTodo?.let { todo ->
        CreateTaskBottomSheet(
            lists = uiState.lists,
            editingTask = todo,
            onParseTaskTitleNlp = onParseTaskTitleNlp,
            onDismiss = { editTargetTodoId = null },
            onCreateTask = { _ -> },
            onUpdateTask = { target, payload ->
                onUpdateTask(target, payload)
                editTargetTodoId = null
            },
        )
    }

    val selectedListId = listSettingsTargetId ?: uiState.listId
    if (
        showListSettingsSheet &&
        uiState.mode == TodoListMode.LIST &&
        !selectedListId.isNullOrBlank()
    ) {
        ListSettingsBottomSheet(
            listName = listSettingsName,
            onListNameChange = { listSettingsName = capitalizeFirstListLetter(it) },
            listColor = listSettingsColor,
            onListColorChange = {
                listSettingsColor = it
                listSettingsColorTouched = true
            },
            listIconKey = listSettingsIconKey,
            onListIconChange = {
                listSettingsIconKey = it
                listSettingsIconTouched = true
            },
            onDismiss = {
                showListSettingsSheet = false
                listSettingsTargetId = null
            },
            onSave = {
                onUpdateListSettings(
                    selectedListId,
                    listSettingsName,
                    if (listSettingsColorTouched) listSettingsColor else null,
                    if (listSettingsIconTouched) listSettingsIconKey else null,
                )
                showListSettingsSheet = false
                listSettingsTargetId = null
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
    showActionButton: Boolean,
    actionIcon: ImageVector,
    actionContentDescription: String,
    onAction: () -> Unit,
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
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.action_back),
                    isBackButton = true,
                )
                if (showActionButton) {
                    TodayHeaderButton(
                        onClick = onAction,
                        icon = actionIcon,
                        contentDescription = actionContentDescription,
                    )
                }
            }
            if (collapsedTitleAlpha > 0.001f) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
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
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
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
    icon: ImageVector,
    contentDescription: String,
    isBackButton: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val containerColor = if (isBackButton) {
        if (isDarkTheme) colorScheme.surface.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)
    } else {
        colorScheme.background
    }
    val buttonBorder = if (isBackButton) null else BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.38f))
    val buttonSize = if (isBackButton) TdayDimens.FabSize else 56.dp
    val iconSize = if (isBackButton) 36.dp else 30.dp
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        label = "todayHeaderButtonScale",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        label = "todayHeaderButtonOffsetY",
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
                tint = colorScheme.onSurface,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryBottomSheet(
    isLoading: Boolean,
    summaryText: String?,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val sheetContainerColor = if (isDarkTheme) colorScheme.surface else colorScheme.background
    val sheetScrimColor = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.68f)
    } else {
        Color.Black.copy(alpha = 0.40f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        containerColor = sheetContainerColor,
        tonalElevation = if (isDarkTheme) 10.dp else 0.dp,
        scrimColor = sheetScrimColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.todos_summary_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onBackground,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.todos_summary_close),
                        tint = colorScheme.onBackground,
                    )
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.todos_summary_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!summaryText.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        text = summaryText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurface,
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CreateTaskButton(
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    backgroundColor: Color,
    onClick: () -> Unit,
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
        border = BorderStroke(1.dp, backgroundColor.copy(alpha = 0.72f)),
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
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListSettingsBottomSheet(
    listName: String,
    onListNameChange: (String) -> Unit,
    listColor: String,
    onListColorChange: (String) -> Unit,
    listIconKey: String,
    onListIconChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val colorScheme = MaterialTheme.colorScheme
    val selectedAccent = listAccentColor(listColor)
    val selectedIcon = listIconForKey(listIconKey)
    val canSave = listName.isNotBlank()
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val sheetContainerColor = if (isDarkTheme) colorScheme.surface else colorScheme.background
    val sheetScrimColor = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.68f)
    } else {
        Color.Black.copy(alpha = 0.40f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        containerColor = sheetContainerColor,
        tonalElevation = if (isDarkTheme) 10.dp else 0.dp,
        scrimColor = sheetScrimColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListSettingsActionButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        enabled = true,
                        accentColor = Color(0xFFE35A5A),
                        onClick = {
                            focusManager.clearFocus(force = true)
                            onDismiss()
                        },
                    )

                    Text(
                        text = stringResource(R.string.todos_list_settings),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onBackground,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    ListSettingsActionButton(
                        icon = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.todos_save_list_settings),
                        enabled = canSave,
                        accentColor = Color(0xFF2FA35B),
                        onClick = {
                            focusManager.clearFocus(force = true)
                            if (canSave) onSave()
                        },
                    )
                }

                Text(
                    text = stringResource(R.string.home_section_list),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .background(selectedAccent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = selectedIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp),
                            )
                        }

                        BasicTextField(
                            value = listName,
                            onValueChange = onListNameChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                    if (canSave) onSave()
                                },
                            ),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                color = selectedAccent,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onPreviewKeyEvent { event ->
                                    if (
                                        event.type == KeyEventType.KeyUp &&
                                        (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                    ) {
                                        keyboardController?.hide()
                                        focusManager.clearFocus(force = true)
                                        if (canSave) onSave()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            colorScheme.surfaceVariant, RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (listName.isBlank()) {
                                        Text(
                                            text = stringResource(R.string.home_list_name_placeholder),
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        innerTextField()
                                    }
                                }
                            },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.home_section_color),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LIST_SETTINGS_COLOR_KEYS.forEach { colorKey ->
                            val selected = listColor == colorKey
                            val swatchColor = listAccentColor(colorKey)
                            val interactionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .wrapContentSize(Alignment.Center)
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor, CircleShape)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = ripple(
                                            bounded = true,
                                            radius = 21.dp,
                                        ),
                                    ) { onListColorChange(colorKey) }
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = colorScheme.onBackground.copy(alpha = 0.32f),
                                                shape = CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        }
                                    ),
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.home_section_icon),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LIST_SETTINGS_ICON_OPTIONS.forEach { option ->
                            val selected = listIconKey == option.key
                            val interactionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        color = if (selected) {
                                            selectedAccent.copy(alpha = 0.2f)
                                        } else {
                                            colorScheme.surfaceVariant
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = ripple(
                                            bounded = true,
                                            radius = 23.dp,
                                        ),
                                    ) { onListIconChange(option.key) }
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = selectedAccent.copy(alpha = 0.55f),
                                                shape = CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = stringResource(R.string.home_section_icon),
                                    tint = if (selected) selectedAccent else colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListSettingsActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.93f else 1f,
        label = "listSettingsHeaderButtonScale",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            pressed && enabled -> 2.dp
            enabled -> 8.dp
            else -> 5.dp
        },
        label = "listSettingsHeaderButtonElevation",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed && enabled) 1.dp else 0.dp,
        label = "listSettingsHeaderButtonOffsetY",
    )
    val iconTint = colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.55f)

    Card(
        modifier = Modifier
            .size(54.dp)
            .offset(y = offsetY)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 1.5.dp,
                color = accentColor.copy(alpha = if (enabled) 0.55f else 0.3f),
                shape = CircleShape,
            ),
        onClick = {
            if (enabled) {
                ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            }
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation,
            pressedElevation = elevation,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TimelineSectionHeader(
    modifier: Modifier = Modifier,
    section: TodoSection,
    useMinimalStyle: Boolean,
    isCollapsed: Boolean = false,
    isDropTarget: Boolean,
    bottomSpacing: Dp,
    onHeaderClick: (() -> Unit)? = null,
    onTapForQuickAdd: (() -> Unit)?,
) {
    val colorScheme = MaterialTheme.colorScheme
    val headerInteractionSource = remember { MutableInteractionSource() }
    val isHeaderPressed by headerInteractionSource.collectIsPressedAsState()
    val collapseChevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "sectionChevronRotation",
    )
    val animatedBottomSpacing by animateDpAsState(
        targetValue = bottomSpacing,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "sectionBottomSpacing",
    )
    val baseHeaderColor = if (useMinimalStyle) {
        colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    } else {
        colorScheme.onSurfaceVariant
    }
    val headerTextColor = if (isHeaderPressed) {
        androidx.compose.ui.graphics.lerp(baseHeaderColor, colorScheme.onSurface, 0.16f)
    } else if (isDropTarget) {
        colorScheme.error
    } else {
        baseHeaderColor
    }
    val baseChevronColor =
        colorScheme.onSurfaceVariant.copy(alpha = if (useMinimalStyle) 0.72f else 1f)
    val chevronColor = if (isHeaderPressed) {
        androidx.compose.ui.graphics.lerp(baseChevronColor, colorScheme.onSurface, 0.16f)
    } else {
        baseChevronColor
    }
    val minimumHeaderHeight = if (useMinimalStyle) 34.dp else 48.dp
    val headerClickModifier = when {
        onHeaderClick != null -> Modifier.clickable(
            interactionSource = headerInteractionSource,
            indication = null,
            onClick = onHeaderClick,
        )

        onTapForQuickAdd != null -> Modifier.clickable(
            interactionSource = headerInteractionSource,
            indication = null,
            onClick = onTapForQuickAdd,
        )

        else -> Modifier
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colorScheme.background)
            .padding(bottom = animatedBottomSpacing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Transparent)
                .padding(horizontal = 4.dp)
                .heightIn(min = minimumHeaderHeight)
                .then(headerClickModifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localizedSectionTitle(section),
                color = headerTextColor,
                style = if (useMinimalStyle) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.ExtraBold,
            )
            if (onHeaderClick != null) {
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
                        .graphicsLayer {
                            rotationZ = collapseChevronRotation
                        },
                )
            }
        }
    }
}

@Composable
private fun TimelineDropPlaceholder(
    modifier: Modifier = Modifier,
    active: Boolean,
    useMinimalStyle: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val placeholderHeight by animateDpAsState(
        targetValue = if (active) {
            if (useMinimalStyle) 66.dp else 72.dp
        } else {
            if (useMinimalStyle) 46.dp else 52.dp
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "timelineDropPlaceholderHeight",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(placeholderHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (active) {
                    colorScheme.error.copy(alpha = 0.10f)
                } else {
                    colorScheme.surfaceVariant.copy(alpha = 0.16f)
                },
            )
            .border(
                BorderStroke(
                    width = if (active) 1.5.dp else 1.dp,
                    color = if (active) {
                        colorScheme.error.copy(alpha = 0.64f)
                    } else {
                        colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
                    },
                ),
                RoundedCornerShape(18.dp),
            ),
    )
}

@Composable
private fun TimelineTaskDragPreview(
    modifier: Modifier = Modifier,
    todo: TodoItem,
    lists: List<ListSummary>,
    mode: TodoListMode,
) {
    val colorScheme = MaterialTheme.colorScheme
    val listMeta = todo.listId?.let { listId -> lists.firstOrNull { it.id == listId } }
    val showListIndicator = listMeta != null && mode != TodoListMode.LIST
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
                    text = TODO_DUE_TIME_FORMATTER.format(todo.due),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (showListIndicator) {
                Icon(
                    imageVector = listIconForKey(listMeta?.iconKey),
                    contentDescription = null,
                    tint = listAccentColor(listMeta?.color),
                    modifier = Modifier.size(18.dp),
                )
            }
            priorityIconFor(todo.priority)?.let { priorityIcon ->
                Icon(
                    imageVector = priorityIcon,
                    contentDescription = null,
                    tint = priorityColor(todo.priority),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineTaskRow(
    modifier: Modifier = Modifier,
    todo: TodoItem,
    mode: TodoListMode,
    lists: List<ListSummary>,
    useMinimalStyle: Boolean,
    flashHighlight: Boolean,
    showEarlierDateTimeSubtitle: Boolean,
    showDateDivider: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    draggedTodo: TodoItem? = null,
    onDragTodoStart: ((Offset) -> Unit)? = null,
    onDragTodoMove: (Offset) -> Unit = {},
    onDragTodoEnd: (Offset?) -> Unit = {},
    onDragTodoCancel: () -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (mode == TodoListMode.ALL) {
            AllTaskSwipeRow(
                todo = todo,
                lists = lists,
                flashHighlight = flashHighlight,
                onComplete = onComplete,
                onDelete = onDelete,
                onInfo = onInfo,
                showDuePrefix = true,
                showDueDateInSubtitle = showEarlierDateTimeSubtitle,
                showDateDivider = showDateDivider,
                dragEnabled = onDragTodoStart != null,
                dragging = draggedTodo?.id == todo.id,
                onDragStart = { position -> onDragTodoStart?.invoke(position) },
                onDragMove = onDragTodoMove,
                onDragEnd = onDragTodoEnd,
                onDragCancel = onDragTodoCancel,
            )
        } else if (
            useMinimalStyle &&
            (
                    mode == TodoListMode.TODAY ||
                            mode == TodoListMode.OVERDUE ||
                            mode == TodoListMode.SCHEDULED ||
                            mode == TodoListMode.PRIORITY ||
                            mode == TodoListMode.LIST
                    )
        ) {
            TodayTaskSwipeRow(
                todo = todo,
                mode = mode,
                lists = lists,
                flashHighlight = flashHighlight,
                onComplete = onComplete,
                onDelete = onDelete,
                onInfo = onInfo,
                showDuePrefix = true,
                showDueDateInSubtitle = showEarlierDateTimeSubtitle,
                showDateDivider = showDateDivider,
                dragEnabled = onDragTodoStart != null,
                dragging = draggedTodo?.id == todo.id,
                onDragStart = { position -> onDragTodoStart?.invoke(position) },
                onDragMove = onDragTodoMove,
                onDragEnd = onDragTodoEnd,
                onDragCancel = onDragTodoCancel,
            )
        } else if (useMinimalStyle) {
            TodayTodoRow(
                todo = todo,
                onComplete = onComplete,
                onDelete = onDelete,
            )
        } else {
            TodoRow(
                todo = todo,
                onComplete = onComplete,
                onDelete = onDelete,
            )
        }
    }
}

private fun Modifier.timelineInAppDropTarget(
    targetId: String,
    section: TodoSection,
    enabled: Boolean,
    dropTargets: MutableMap<String, TimelineDropTargetBounds>,
): Modifier {
    if (!enabled || section.targetDate == null) {
        return this
    }

    return composed {
        DisposableEffect(targetId) {
            onDispose {
                dropTargets.remove(targetId)
            }
        }
        onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            val size = coordinates.size
            dropTargets[targetId] = TimelineDropTargetBounds(
                sectionKey = section.key,
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

private data class TimelineDropTargetBounds(
    val sectionKey: String,
    val bounds: Rect,
)

private data class TimelineInAppDrag(
    val todo: TodoItem,
    val position: Offset,
)

private data class TodoSection(
    val key: String,
    val title: String,
    val items: List<TodoItem>,
    val quickAddDefaults: Long? = null,
    val targetDate: LocalDate? = null,
)

private data class TaskRescheduleDrop(
    val todo: TodoItem,
    val targetDate: LocalDate,
)

private fun shouldShowDateDivider(
    afterItemIndex: Int,
    inSectionIndex: Int,
    sections: List<TodoSection>,
    collapsedSectionKeys: Set<String>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val section = sections.getOrNull(inSectionIndex) ?: return false
    val currentTodo = section.items.getOrNull(afterItemIndex) ?: return false
    val nextTodoInSection = section.items.getOrNull(afterItemIndex + 1)
    if (nextTodoInSection != null) {
        return !currentTodo.due.isSameLocalDayAs(nextTodoInSection.due, zoneId)
    }

    val nextVisibleTodo = sections
        .asSequence()
        .drop(inSectionIndex + 1)
        .filter { it.key !in collapsedSectionKeys }
        .flatMap { it.items.asSequence() }
        .firstOrNull()
        ?: return false

    return !currentTodo.due.isSameLocalDayAs(nextVisibleTodo.due, zoneId)
}

private fun Instant.isSameLocalDayAs(other: Instant, zoneId: ZoneId): Boolean =
    LocalDate.ofInstant(this, zoneId) == LocalDate.ofInstant(other, zoneId)

private enum class TodaySectionSlot {
    MORNING, AFTERNOON, TONIGHT,
}

private fun buildTimelineSections(
    mode: TodoListMode,
    items: List<TodoItem>,
    includeEmptyEarlierTarget: Boolean = false,
): List<TodoSection> {
    val zoneId = ZoneId.systemDefault()
    return when (mode) {
        TodoListMode.TODAY -> buildTodaySections(items, zoneId)
        TodoListMode.OVERDUE -> buildOverdueSections(items, zoneId)
        TodoListMode.SCHEDULED -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = true,
        )

        TodoListMode.ALL -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = false,
            placesEarlierBeforeToday = true,
            includeEmptyEarlierTarget = includeEmptyEarlierTarget,
        )

        TodoListMode.PRIORITY -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = false,
            placesEarlierBeforeToday = true,
            includeEmptyEarlierTarget = includeEmptyEarlierTarget,
        )

        TodoListMode.LIST -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = false,
            placesEarlierBeforeToday = false,
            includeEmptyEarlierTarget = includeEmptyEarlierTarget,
        )
    }
}

private fun buildOverdueSections(
    items: List<TodoItem>,
    zoneId: ZoneId,
): List<TodoSection> {
    val now = Instant.now()
    val today = LocalDate.now(zoneId)
    val overdueByDate = items.asSequence()
        .filter { todo -> todo.due.isBefore(now) }
        .groupBy { todo -> LocalDate.ofInstant(todo.due, zoneId) }

    val sections = mutableListOf<TodoSection>()

    overdueByDate[today]
        ?.sortedBy { it.due }
        ?.takeIf { it.isNotEmpty() }
        ?.let { todaysItems ->
            sections += TodoSection(
                key = "day-$today",
                title = "Today",
                items = todaysItems,
                quickAddDefaults = quickAddDefaultsForDate(
                    date = today,
                    zoneId = zoneId,
                ),
            )
        }

    overdueByDate.keys
        .asSequence()
        .filter { date -> date < today }
        .sortedDescending()
        .forEach { date ->
            sections += TodoSection(
                key = "day-$date",
                title = date.format(SCHEDULED_DAY_FORMATTER),
                items = overdueByDate[date].orEmpty().sortedBy { it.due },
                quickAddDefaults = null,
            )
        }

    return sections
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
    placesEarlierBeforeToday: Boolean = true,
    includeEmptyEarlierTarget: Boolean = false,
): List<TodoSection> {
    val now = Instant.now()
    val sorted = items.asSequence().filter { todo ->
        if (futureOnly) !todo.due.isBefore(now) else true
    }.sortedBy { it.due }.toList()
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
            targetDate = timelineRescheduleTargetDate("day-$date", today),
        )
    }

    if (futureOnly) {
        return groupedByDate.keys
            .asSequence()
            .filter { date -> date >= today }
            .sorted()
            .map { date ->
                daySection(
                    date = date,
                    title = date.format(SCHEDULED_DAY_FORMATTER),
                )
            }
            .toList()
    }

    val earlierSection = if (!futureOnly) {
        val earlierItems = groupedByDate.asSequence().filter { (date, _) -> date < today }
            .flatMap { (_, dayItems) -> dayItems.asSequence() }.sortedBy { it.due }.toList()
        if (earlierItems.isNotEmpty() || includeEmptyEarlierTarget) {
            TodoSection(
                key = "earlier",
                title = "Earlier",
                items = earlierItems,
                quickAddDefaults = quickAddDefaultsForDate(
                    date = today.minusDays(1),
                    zoneId = zoneId,
                ),
                targetDate = timelineRescheduleTargetDate("earlier", today),
            )
        } else {
            null
        }
    } else {
        null
    }

    if (placesEarlierBeforeToday) {
        earlierSection?.let { sections += it }
    }

    sections += daySection(today, "Today")
    if (!placesEarlierBeforeToday) {
        earlierSection?.let { sections += it }
    }
    sections += daySection(today.plusDays(1), "Tomorrow")
    for (offset in 2..6) {
        val date = today.plusDays(offset.toLong())
        sections += daySection(
            date = date,
            title = date.format(SCHEDULED_DAY_FORMATTER),
        )
    }

    val restOfCurrentMonthItems = groupedByDate.asSequence().filter { (date, _) ->
        date >= horizonStart && YearMonth.from(date) == currentMonth
    }.flatMap { (_, dayItems) -> dayItems.asSequence() }.sortedBy { it.due }.toList()
    val monthName = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    sections += TodoSection(
        key = "rest-$currentMonth",
        title = "Rest of $monthName",
        items = restOfCurrentMonthItems,
        quickAddDefaults = quickAddDefaultsForDate(
            date = currentMonth.atEndOfMonth(),
            zoneId = zoneId,
        ),
        targetDate = timelineRescheduleTargetDate("rest-$currentMonth", today),
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
        sections += TodoSection(
            key = "month-$targetMonth",
            title = monthTitle(targetMonth, currentMonth.year),
            items = monthItems,
            quickAddDefaults = quickAddDefaultsForDate(
                date = targetMonth.atDay(1),
                zoneId = zoneId,
            ),
            targetDate = timelineRescheduleTargetDate("month-$targetMonth", today),
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

@Composable
private fun localizedSectionTitle(section: TodoSection): String {
    return when {
        section.key == "today-morning" -> stringResource(R.string.todos_section_morning)
        section.key == "today-afternoon" -> stringResource(R.string.todos_section_afternoon)
        section.key == "today-tonight" -> stringResource(R.string.todos_section_tonight)
        section.key == "earlier" -> stringResource(R.string.todos_section_earlier)
        section.key.startsWith("day-") -> {
            val zoneId = ZoneId.systemDefault()
            val date = runCatching { LocalDate.parse(section.key.removePrefix("day-")) }.getOrNull()
            val today = LocalDate.now(zoneId)
            when (date) {
                today -> stringResource(R.string.todos_section_today)
                today.plusDays(1) -> stringResource(R.string.todos_section_tomorrow)
                else -> section.title
            }
        }
        section.key.startsWith("rest-") -> {
            val ymPart = section.key.removePrefix("rest-")
            runCatching {
                val ym = YearMonth.parse(ymPart)
                val monthName = ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                stringResource(R.string.todos_section_rest_of, monthName)
            }.getOrElse { section.title }
        }
        else -> section.title
    }
}

@Composable
private fun emptyStateMessageForMode(mode: TodoListMode): String {
    return when (mode) {
        TodoListMode.TODAY -> stringResource(R.string.todos_empty_today)
        TodoListMode.OVERDUE -> stringResource(R.string.todos_empty_overdue)
        TodoListMode.PRIORITY -> stringResource(R.string.todos_empty_priority)
        TodoListMode.SCHEDULED -> stringResource(R.string.todos_empty_scheduled)
        TodoListMode.ALL -> stringResource(R.string.todos_empty_all)
        TodoListMode.LIST -> stringResource(R.string.todos_empty_list)
    }
}

private fun emptyStateIconForMode(
    mode: TodoListMode,
    listIconKey: String?,
): ImageVector {
    return when (mode) {
        TodoListMode.TODAY -> Icons.Rounded.WbSunny
        TodoListMode.OVERDUE -> Icons.Rounded.ErrorOutline
        TodoListMode.PRIORITY -> Icons.Rounded.Flag
        TodoListMode.SCHEDULED -> Icons.Rounded.Schedule
        TodoListMode.ALL -> Icons.Rounded.Inbox
        TodoListMode.LIST -> listIconForKey(listIconKey)
    }
}

private val SCHEDULED_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d")

private fun quickAddDefaultsForDate(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val dueTime = LocalTime.of(23, 59)
    return ZonedDateTime.of(date, dueTime, zoneId).toInstant().toEpochMilli()
}

private fun quickAddDefaultsForTodaySection(
    slot: TodaySectionSlot,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val today = LocalDate.now(zoneId)
    val time = when (slot) {
        TodaySectionSlot.MORNING -> LocalTime.NOON
        TodaySectionSlot.AFTERNOON -> LocalTime.of(18, 0)
        TodaySectionSlot.TONIGHT -> LocalTime.of(22, 0)
    }
    return ZonedDateTime.of(today, time, zoneId).toInstant().toEpochMilli()
}

private suspend fun LazyListState.animateSearchResultScrollToItem(
    targetIndex: Int,
    targetKey: String,
    centeredScrollOffset: Int,
    estimatedItemSizePx: Int,
) {
    repeat(SEARCH_RESULT_SCROLL_CORRECTION_PASSES) {
        val visibleTarget =
            layoutInfo.visibleItemsInfo.firstOrNull { item -> item.key == targetKey }
        if (visibleTarget != null) {
            animateVisibleSearchResultToCenter(
                itemOffset = visibleTarget.offset,
                itemSize = visibleTarget.size,
            )
            return
        }

        val visibleItems = layoutInfo.visibleItemsInfo
        val averageItemSizePx = visibleItems
            .takeIf { it.isNotEmpty() }
            ?.map { item -> item.size }
            ?.average()
            ?.toFloat()
            ?.takeIf { it > 0f }
            ?: estimatedItemSizePx.toFloat()
        val estimatedDistance =
            ((targetIndex - firstVisibleItemIndex) * averageItemSizePx) +
                    centeredScrollOffset -
                    firstVisibleItemScrollOffset
        if (abs(estimatedDistance) < SEARCH_RESULT_SCROLL_MIN_DISTANCE_PX) return
        animateScrollBy(
            value = estimatedDistance,
            animationSpec = tween(
                durationMillis = searchResultScrollDurationMillis(estimatedDistance),
                easing = LinearOutSlowInEasing,
            ),
        )
    }

    val visibleTarget = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.key == targetKey }
    if (visibleTarget != null) {
        animateVisibleSearchResultToCenter(
            itemOffset = visibleTarget.offset,
            itemSize = visibleTarget.size,
        )
    } else {
        scrollToItem(targetIndex, centeredScrollOffset)
    }
}

private suspend fun LazyListState.animateVisibleSearchResultToCenter(
    itemOffset: Int,
    itemSize: Int,
) {
    val viewportCenter =
        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = itemOffset + (itemSize / 2)
    val centerDelta = (itemCenter - viewportCenter).toFloat()
    if (abs(centerDelta) < SEARCH_RESULT_SCROLL_MIN_DISTANCE_PX) return
    animateScrollBy(
        value = centerDelta,
        animationSpec = tween(
            durationMillis = SEARCH_RESULT_CENTER_SCROLL_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
    )
}

private fun searchResultScrollDurationMillis(distancePx: Float): Int =
    (abs(distancePx) / SEARCH_RESULT_SCROLL_PX_PER_MS)
        .roundToInt()
        .coerceIn(
            SEARCH_RESULT_SCROLL_MIN_DURATION_MS,
            SEARCH_RESULT_SCROLL_MAX_DURATION_MS,
        )

private const val TODAY_TITLE_COLLAPSE_DISTANCE_DP = 180f
private const val SEARCH_RESULT_NAV_SETTLE_DELAY_MS = 380L
private const val SEARCH_RESULT_SCROLL_CORRECTION_PASSES = 2
private const val SEARCH_RESULT_SCROLL_MIN_DISTANCE_PX = 2f
private const val SEARCH_RESULT_SCROLL_PX_PER_MS = 1.15f
private const val SEARCH_RESULT_SCROLL_MIN_DURATION_MS = 720
private const val SEARCH_RESULT_SCROLL_MAX_DURATION_MS = 2400
private const val SEARCH_RESULT_CENTER_SCROLL_DURATION_MS = 520
private const val SEARCH_RESULT_ESTIMATED_ROW_HEIGHT_DP = 72f
private val SWIPE_ROW_CONTENT_VERTICAL_PADDING = 2.dp
private val SWIPE_ROW_HEIGHT = 58.dp
private val TASK_CHECKMARK_GREEN = Color(0xFF6FBF86)
private val TODO_DUE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
private val TODO_DUE_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())

@Composable
private fun AllTaskSwipeRow(
    todo: TodoItem,
    lists: List<ListSummary>,
    flashHighlight: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    showDuePrefix: Boolean,
    showDueDateInSubtitle: Boolean = false,
    showDateDivider: Boolean,
    dragEnabled: Boolean = false,
    dragging: Boolean = false,
    onDragStart: ((Offset) -> Unit)? = null,
    onDragMove: (Offset) -> Unit = {},
    onDragEnd: (Offset?) -> Unit = {},
    onDragCancel: () -> Unit = {},
) {
    SwipeTaskRow(
        todo = todo,
        onComplete = onComplete,
        onDelete = onDelete,
        onInfo = onInfo,
        keepCompletedInline = false,
        mode = TodoListMode.ALL,
        lists = lists,
        flashHighlight = flashHighlight,
        showDueText = true,
        showDuePrefix = showDuePrefix,
        showDueDateInSubtitle = showDueDateInSubtitle,
        showDateDivider = showDateDivider,
        useDelayedFadeCompletion = false,
        dragEnabled = dragEnabled,
        dragging = dragging,
        onDragStart = onDragStart,
        onDragMove = onDragMove,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
    )
}

@Composable
private fun TodayTaskSwipeRow(
    todo: TodoItem,
    mode: TodoListMode,
    lists: List<ListSummary>,
    flashHighlight: Boolean = false,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    showDuePrefix: Boolean,
    showDueDateInSubtitle: Boolean = false,
    showDateDivider: Boolean,
    dragEnabled: Boolean = false,
    dragging: Boolean = false,
    onDragStart: ((Offset) -> Unit)? = null,
    onDragMove: (Offset) -> Unit = {},
    onDragEnd: (Offset?) -> Unit = {},
    onDragCancel: () -> Unit = {},
) {
    SwipeTaskRow(
        todo = todo,
        onComplete = onComplete,
        onDelete = onDelete,
        onInfo = onInfo,
        keepCompletedInline = false,
        mode = mode,
        lists = lists,
        flashHighlight = flashHighlight,
        showDueText = true,
        showDuePrefix = showDuePrefix,
        showDueDateInSubtitle = showDueDateInSubtitle,
        showDateDivider = showDateDivider,
        useDelayedFadeCompletion = mode != TodoListMode.TODAY,
        dragEnabled = dragEnabled,
        dragging = dragging,
        onDragStart = onDragStart,
        onDragMove = onDragMove,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeTaskRow(
    todo: TodoItem,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    keepCompletedInline: Boolean,
    mode: TodoListMode = TodoListMode.ALL,
    lists: List<ListSummary> = emptyList(),
    flashHighlight: Boolean = false,
    showDueText: Boolean,
    showDuePrefix: Boolean,
    showDueDateInSubtitle: Boolean = false,
    showDateDivider: Boolean = false,
    useDelayedFadeCompletion: Boolean = false,
    useFadeOnCompletion: Boolean = false,
    dragEnabled: Boolean = false,
    dragging: Boolean = false,
    onDragStart: ((Offset) -> Unit)? = null,
    onDragMove: (Offset) -> Unit = {},
    onDragEnd: (Offset?) -> Unit = {},
    onDragCancel: () -> Unit = {},
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
    var localCompleted by remember(todo.id) { mutableStateOf(false) }
    var pendingCompletion by remember(todo.id) { mutableStateOf(false) }
    var completionFading by remember(todo.id) { mutableStateOf(false) }
    var rowOriginInRoot by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var dragPointerPosition by remember(todo.id) { mutableStateOf<Offset?>(null) }
    val highlightAnim = remember(todo.id) { Animatable(0f) }
    val visuallyCompleted = localCompleted || (keepCompletedInline && todo.completed)
    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "swipeTaskOffset",
    )
    val actionRevealProgress = (-animatedOffsetX / actionRevealPx).coerceIn(0f, 1f)
    val completionAlpha by animateFloatAsState(
        targetValue = if (completionFading) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "swipeTaskCompletionAlpha",
    )
    val dueTimeText = TODO_DUE_TIME_FORMATTER.format(todo.due)
    val dueDateTimeText = TODO_DUE_DATE_TIME_FORMATTER.format(todo.due)
    val isOverdue = !todo.completed && todo.due.isBefore(Instant.now())
    val dueBodyText = if (showDueDateInSubtitle) dueDateTimeText else dueTimeText
    val dueSubtitleText = if (isOverdue) {
        stringResource(R.string.todos_due_overdue_text, dueBodyText)
    } else if (showDuePrefix) {
        stringResource(R.string.todos_due_text, dueBodyText)
    } else {
        dueBodyText
    }
    val rowShape = RoundedCornerShape(16.dp)
    val foregroundColor = colorScheme.background
    val highlightStrength = highlightAnim.value.coerceIn(0f, 1f)
    val contentGlowBrush = Brush.horizontalGradient(
        colors = listOf(
            colorScheme.primary.copy(
                alpha = if (colorScheme.background.luminance() < 0.5f) {
                    0.50f * highlightStrength
                } else {
                    0.40f * highlightStrength
                },
            ),
            colorScheme.primary.copy(
                alpha = if (colorScheme.background.luminance() < 0.5f) {
                    0.30f * highlightStrength
                } else {
                    0.20f * highlightStrength
                },
            ),
            Color.Transparent,
        ),
    )
    val listMeta = todo.listId?.let { listId -> lists.firstOrNull { it.id == listId } }
    val showListIndicator = when (mode) {
        TodoListMode.TODAY,
        TodoListMode.OVERDUE,
        TodoListMode.SCHEDULED,
        TodoListMode.PRIORITY,
        TodoListMode.ALL,
            -> listMeta != null

        TodoListMode.LIST,
            -> false
    }
    val priorityIcon = priorityIconFor(todo.priority)
    val showPriorityIcon = priorityIcon != null
    val listIndicatorColor = listAccentColor(listMeta?.color)
    LaunchedEffect(flashHighlight) {
        if (!flashHighlight) return@LaunchedEffect
        targetOffsetX = 0f
        highlightAnim.stop()
        highlightAnim.snapTo(0f)
        repeat(2) { pulseIndex ->
            highlightAnim.animateTo(
                targetValue = 0.46f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            )
            highlightAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            )
            if (pulseIndex < 1) {
                delay(150)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (dragging) completionAlpha * 0.55f else completionAlpha },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SWIPE_ROW_HEIGHT),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskSwipeActionButton(
                        icon = Icons.Rounded.BorderColor,
                        contentDescription = stringResource(R.string.action_edit_task),
                        label = stringResource(R.string.action_edit),
                        tint = Color.White,
                        background = Color(0xFF4C7DDE),
                        revealProgress = actionRevealProgress,
                        revealDelay = 0.62f,
                        onClick = {
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.CLOCK_TICK,
                            )
                            onInfo()
                            targetOffsetX = 0f
                        },
                    )
                    TaskSwipeActionButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete_task),
                        label = stringResource(R.string.action_delete),
                        tint = Color.White,
                        background = Color(0xFFFF453A),
                        revealProgress = actionRevealProgress,
                        revealDelay = 0.04f,
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
                        .onGloballyPositioned { coordinates ->
                            rowOriginInRoot = coordinates.positionInRoot()
                        }
                        .graphicsLayer { translationX = animatedOffsetX }
                        .then(
                            if (dragEnabled) {
                                Modifier.pointerInput(todo.id, dragEnabled) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { localOffset ->
                                            targetOffsetX = 0f
                                            val startPosition = rowOriginInRoot + localOffset
                                            dragPointerPosition = startPosition
                                            onDragStart?.invoke(startPosition)
                                            onDragMove(startPosition)
                                            ViewCompat.performHapticFeedback(
                                                view,
                                                HapticFeedbackConstantsCompat.CLOCK_TICK,
                                            )
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val nextPosition = (dragPointerPosition
                                                ?: rowOriginInRoot) + dragAmount
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
                            } else if (!swipeHinting && !pendingCompletion && !dragging) {
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
                            .padding(
                                horizontal = 4.dp,
                                vertical = SWIPE_ROW_CONTENT_VERTICAL_PADDING
                            )
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(foregroundColor, RoundedCornerShape(18.dp))
                                .background(contentGlowBrush, RoundedCornerShape(18.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularCheckToggleIcon(
                                imageVector = if (!visuallyCompleted) {
                                    Icons.Rounded.RadioButtonUnchecked
                                } else {
                                    Icons.Rounded.CheckCircle
                                },
                                contentDescription = if (visuallyCompleted) {
                                    stringResource(R.string.label_completed)
                                } else {
                                    stringResource(R.string.label_mark_complete)
                                },
                                tint = if (!visuallyCompleted) {
                                    colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                } else {
                                    TASK_CHECKMARK_GREEN
                                },
                                enabled = !visuallyCompleted && !pendingCompletion,
                                onClick = {
                                    ViewCompat.performHapticFeedback(
                                        view,
                                        HapticFeedbackConstantsCompat.CLOCK_TICK,
                                    )
                                    targetOffsetX = 0f
                                    localCompleted = true
                                    pendingCompletion = true
                                    coroutineScope.launch {
                                        if (useDelayedFadeCompletion) {
                                            delay(500)
                                            if (useFadeOnCompletion) {
                                                completionFading = true
                                                delay(220)
                                            }
                                            onComplete()
                                        } else {
                                            delay(if (keepCompletedInline) 120 else 180)
                                            onComplete()
                                        }
                                    }
                                },
                            )

                            Column(
                                modifier = Modifier
                                    .padding(start = 10.dp, end = 8.dp)
                                    .weight(1f),
                            ) {
                                Text(
                                    text = todo.title,
                                    color = if (visuallyCompleted) {
                                        colorScheme.onSurface.copy(alpha = 0.78f)
                                    } else {
                                        colorScheme.onSurface
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    textDecoration = if (visuallyCompleted) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                    maxLines = 2,
                                )
                                if (showDueText) {
                                    Text(
                                        text = dueSubtitleText,
                                        color = if (isOverdue) colorScheme.error else colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        if (showListIndicator || showPriorityIcon) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, end = 24.dp),
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
                                if (priorityIcon != null) {
                                    Icon(
                                        imageVector = priorityIcon,
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
                    .background(colorScheme.outlineVariant.copy(alpha = 0.58f)),
            )
        }
    }
}

@Composable
private fun TodayTodoRow(
    todo: TodoItem,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dueText = TODO_DUE_TIME_FORMATTER.format(todo.due)
    val isDetailOverdue = !todo.completed && todo.due.isBefore(Instant.now())
    val detailDueText = if (isDetailOverdue) {
        stringResource(R.string.todos_due_overdue_text, dueText)
    } else {
        dueText
    }

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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularCheckToggleIcon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.action_complete),
                    tint = TASK_CHECKMARK_GREEN,
                    onClick = onComplete,
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
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = detailDueText,
                        color = if (isDetailOverdue) colorScheme.error else colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
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
) {
    val colorScheme = MaterialTheme.colorScheme
    val due = TODO_DUE_DATE_TIME_FORMATTER.format(todo.due)

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
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularCheckToggleIcon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.action_complete),
                    tint = TASK_CHECKMARK_GREEN,
                    onClick = onComplete,
                )

                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = todo.title,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = due,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CircularCheckToggleIcon(
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
            .wrapContentSize(Alignment.Center)
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
private fun priorityColor(priority: String): Color {
    return when (priority.lowercase()) {
        "high", "urgent", "important" -> Color(0xFFE56A6A)
        "medium" -> Color(0xFFE3B368)
        else -> Color(0xFF6FBF86)
    }
}

private fun priorityIconFor(priority: String): ImageVector? {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "medium" -> Icons.Rounded.Flag
        "high", "urgent", "important" -> Icons.Rounded.PriorityHigh
        else -> null
    }
}

private fun todoFabColorForMode(
    mode: TodoListMode,
    listColorKey: String?,
): Color {
    return modeAccentColor(
        mode = mode,
        listColorKey = listColorKey,
    )
}

private fun modeAccentColor(
    mode: TodoListMode,
    listColorKey: String?,
): Color {
    return when (mode) {
        TodoListMode.TODAY -> Color(0xFF5C9FE7)
        TodoListMode.OVERDUE -> Color(0xFFDA7661)
        TodoListMode.SCHEDULED -> Color(0xFFF29F38)
        TodoListMode.ALL -> Color(0xFF5E6878)
        TodoListMode.PRIORITY -> Color(0xFFE65E52)
        TodoListMode.LIST -> listAccentColor(listColorKey)
    }
}

private fun listAccentColor(colorKey: String?): Color {
    return when (colorKey) {
        "PINK" -> Color(0xFFC987A5)
        "GOLD" -> Color(0xFFC7AA63)
        "DEEP_BLUE" -> Color(0xFF6F86C6)
        "CORAL" -> Color(0xFFD39A82)
        "TEAL" -> Color(0xFF67AAA7)
        "SLATE", "GRAY" -> Color(0xFF7F8996)
        "BLUE" -> Color(0xFF6F9FCE)
        "PURPLE" -> Color(0xFF9A86CF)
        "ROSE" -> Color(0xFFC98299)
        "LIGHT_RED" -> Color(0xFFD58D8D)
        "BRICK" -> Color(0xFFAD786E)
        "YELLOW" -> Color(0xFFCFB866)
        "LIME", "GREEN" -> Color(0xFF8DBB73)
        "ORANGE" -> Color(0xFFD69B63)
        "RED" -> Color(0xFFD97873)
        else -> Color(0xFFC987A5)
    }
}

private fun listIconForKey(iconKey: String?): ImageVector {
    return LIST_SETTINGS_ICON_OPTIONS.firstOrNull { it.key == iconKey }?.icon ?: Icons.Rounded.Inbox
}

private fun isSupportedListColor(colorKey: String): Boolean {
    return LIST_SETTINGS_COLOR_KEYS.contains(colorKey)
}

private fun normalizedListColorKey(colorKey: String?): String {
    return when (colorKey) {
        "GREEN" -> "LIME"
        "GRAY" -> "SLATE"
        else -> colorKey?.takeIf { isSupportedListColor(it) } ?: DEFAULT_LIST_COLOR_KEY
    }
}

private fun isSupportedListIconKey(iconKey: String): Boolean {
    return LIST_SETTINGS_ICON_OPTIONS.any { it.key == iconKey }
}

private data class ListSettingsIconOption(
    val key: String,
    val icon: ImageVector,
)

private const val DEFAULT_LIST_COLOR_KEY = "PINK"
private const val DEFAULT_LIST_ICON_KEY = "inbox"

private val LIST_SETTINGS_COLOR_KEYS = listOf(
    "PINK",
    "GOLD",
    "DEEP_BLUE",
    "CORAL",
    "TEAL",
    "SLATE",
    "BLUE",
    "PURPLE",
    "ROSE",
    "LIGHT_RED",
    "BRICK",
    "YELLOW",
    "LIME",
    "ORANGE",
    "RED",
)

private val LIST_SETTINGS_ICON_OPTIONS = listOf(
    ListSettingsIconOption("inbox", Icons.Rounded.Inbox),
    ListSettingsIconOption("sun", Icons.Rounded.WbSunny),
    ListSettingsIconOption("calendar", Icons.Rounded.CalendarToday),
    ListSettingsIconOption("schedule", Icons.Rounded.Schedule),
    ListSettingsIconOption("flag", Icons.Rounded.Flag),
    ListSettingsIconOption("check", Icons.Rounded.Check),
    ListSettingsIconOption("smile", Icons.Rounded.Mood),
    ListSettingsIconOption("list", Icons.AutoMirrored.Rounded.List),
    ListSettingsIconOption("bookmark", Icons.Rounded.Bookmark),
    ListSettingsIconOption("key", Icons.Rounded.Key),
    ListSettingsIconOption("gift", Icons.Rounded.CardGiftcard),
    ListSettingsIconOption("cake", Icons.Rounded.Cake),
    ListSettingsIconOption("school", Icons.Rounded.School),
    ListSettingsIconOption("bag", Icons.Rounded.Backpack),
    ListSettingsIconOption("edit", Icons.Rounded.Edit),
    ListSettingsIconOption("document", Icons.Rounded.Description),
    ListSettingsIconOption("book", Icons.AutoMirrored.Rounded.MenuBook),
    ListSettingsIconOption("work", Icons.Rounded.Work),
    ListSettingsIconOption("wallet", Icons.Rounded.AccountBalanceWallet),
    ListSettingsIconOption("money", Icons.Rounded.Payments),
    ListSettingsIconOption("fitness", Icons.Rounded.FitnessCenter),
    ListSettingsIconOption("run", Icons.AutoMirrored.Rounded.DirectionsRun),
    ListSettingsIconOption("food", Icons.Rounded.Restaurant),
    ListSettingsIconOption("drink", Icons.Rounded.LocalBar),
    ListSettingsIconOption("health", Icons.Rounded.Medication),
    ListSettingsIconOption("monitor", Icons.Rounded.DesktopWindows),
    ListSettingsIconOption("music", Icons.Rounded.MusicNote),
    ListSettingsIconOption("computer", Icons.Rounded.Computer),
    ListSettingsIconOption("game", Icons.Rounded.SportsEsports),
    ListSettingsIconOption("headphones", Icons.Rounded.Headphones),
    ListSettingsIconOption("eco", Icons.Rounded.Eco),
    ListSettingsIconOption("pets", Icons.Rounded.Pets),
    ListSettingsIconOption("child", Icons.Rounded.ChildCare),
    ListSettingsIconOption("family", Icons.Rounded.FamilyRestroom),
    ListSettingsIconOption("basket", Icons.Rounded.ShoppingBasket),
    ListSettingsIconOption("cart", Icons.Rounded.ShoppingCart),
    ListSettingsIconOption("mall", Icons.Rounded.LocalMall),
    ListSettingsIconOption("inventory", Icons.Rounded.Inventory),
    ListSettingsIconOption("soccer", Icons.Rounded.SportsSoccer),
    ListSettingsIconOption("baseball", Icons.Rounded.SportsBaseball),
    ListSettingsIconOption("basketball", Icons.Rounded.SportsBasketball),
    ListSettingsIconOption("football", Icons.Rounded.SportsFootball),
    ListSettingsIconOption("tennis", Icons.Rounded.SportsTennis),
    ListSettingsIconOption("train", Icons.Rounded.Train),
    ListSettingsIconOption("flight", Icons.Rounded.Flight),
    ListSettingsIconOption("boat", Icons.Rounded.DirectionsBoat),
    ListSettingsIconOption("car", Icons.Rounded.DirectionsCar),
    ListSettingsIconOption("umbrella", Icons.Rounded.BeachAccess),
    ListSettingsIconOption("drop", Icons.Rounded.WaterDrop),
    ListSettingsIconOption("snow", Icons.Rounded.AcUnit),
    ListSettingsIconOption("fire", Icons.Rounded.Whatshot),
    ListSettingsIconOption("tools", Icons.Rounded.Build),
    ListSettingsIconOption("scissors", Icons.Rounded.ContentCut),
    ListSettingsIconOption("architecture", Icons.Rounded.Architecture),
    ListSettingsIconOption("code", Icons.Rounded.Code),
    ListSettingsIconOption("idea", Icons.Rounded.Lightbulb),
    ListSettingsIconOption("chat", Icons.Rounded.ChatBubbleOutline),
    ListSettingsIconOption("alert", Icons.Rounded.PriorityHigh),
    ListSettingsIconOption("star", Icons.Rounded.Star),
    ListSettingsIconOption("heart", Icons.Rounded.Favorite),
    ListSettingsIconOption("circle", Icons.Rounded.Circle),
    ListSettingsIconOption("square", Icons.Rounded.Square),
    ListSettingsIconOption("triangle", Icons.Rounded.ChangeHistory),
    ListSettingsIconOption("home", Icons.Rounded.Home),
    ListSettingsIconOption("city", Icons.Rounded.LocationCity),
    ListSettingsIconOption("bank", Icons.Rounded.AccountBalance),
    ListSettingsIconOption("camera", Icons.Rounded.CameraAlt),
    ListSettingsIconOption("palette", Icons.Rounded.Palette),
)
