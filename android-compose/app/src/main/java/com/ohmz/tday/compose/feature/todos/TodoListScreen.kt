package com.ohmz.tday.compose.feature.todos

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.ohmz.tday.compose.core.ui.animateTaskSwipeOffsetAsState
import com.ohmz.tday.compose.core.ui.rememberLazyListCollapsingTitleScrollBehavior
import com.ohmz.tday.compose.core.ui.rememberTaskSwipeRevealState
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.component.RootFeedDock
import com.ohmz.tday.compose.ui.component.RootFeedTab
import com.ohmz.tday.compose.ui.component.TdayModalBottomSheet
import com.ohmz.tday.compose.ui.component.TdayPullToRefreshBox
import com.ohmz.tday.compose.ui.component.TdaySheetCard
import com.ohmz.tday.compose.ui.component.TdaySheetDefaults
import com.ohmz.tday.compose.ui.component.TdaySheetHeader
import com.ohmz.tday.compose.ui.component.TdaySheetSectionTitle
import com.ohmz.tday.compose.ui.priority.isImportantPriority
import com.ohmz.tday.compose.ui.priority.isUrgentPriority
import com.ohmz.tday.compose.ui.priority.priorityDisplayLabelRes
import com.ohmz.tday.compose.ui.theme.TDAY_DEFAULT_LIST_COLOR_KEY
import com.ohmz.tday.compose.ui.theme.TDAY_DEFAULT_LIST_ICON_KEY
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayFloaterAccent
import com.ohmz.tday.compose.ui.theme.TdayListColorOptions
import com.ohmz.tday.compose.ui.theme.TdayListIconOptions
import com.ohmz.tday.compose.ui.theme.TdaySwipeDeleteBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeEditBackground
import com.ohmz.tday.compose.ui.theme.TdayTaskCompleteAccent
import com.ohmz.tday.compose.ui.theme.TdayTitleIconDayAccent
import com.ohmz.tday.compose.ui.theme.TdayTitleIconNightAccent
import com.ohmz.tday.compose.ui.theme.TdayTodoModeAllAccent
import com.ohmz.tday.compose.ui.theme.TdayTodoModeOverdueAccent
import com.ohmz.tday.compose.ui.theme.TdayTodoModePriorityAccent
import com.ohmz.tday.compose.ui.theme.TdayTodoModeScheduledAccent
import com.ohmz.tday.compose.ui.theme.TdayTodoModeTodayAccent
import com.ohmz.tday.compose.ui.theme.isTdayListIconKeySupported
import com.ohmz.tday.compose.ui.theme.normalizeTdayListColorKey
import com.ohmz.tday.compose.ui.theme.tdayListAccentColor
import com.ohmz.tday.compose.ui.theme.tdayListIconForKey
import com.ohmz.tday.compose.ui.theme.tdayPriorityColor
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
import androidx.compose.ui.graphics.lerp as lerpColor

private val TimelineSameDateTaskSpacing = 2.dp
private val TimelineDateGroupSpacing = 6.dp
private val TimelineSectionTopSpacing = 6.dp
private val TimelineHeaderBodySpacing = 2.dp
private val TimelineCollapsedSectionSpacing = 4.dp
private val RootFeedDockCollapseThreshold = 44.dp

private fun timelineTaskBottomSpacing(
    itemIndex: Int,
    lastIndex: Int,
    showDateDivider: Boolean,
): Dp {
    return if (showDateDivider || itemIndex == lastIndex) {
        TimelineDateGroupSpacing
    } else {
        TimelineSameDateTaskSpacing
    }
}

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
    onDeleteList: (listId: String) -> Unit,
    onOpenFloaterList: (listId: String, listName: String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onCreateList: (name: String, color: String?, iconKey: String?) -> Unit = { _, _, _ -> },
    rootFeedTab: RootFeedTab? = null,
    onRootFeedTabSelected: ((RootFeedTab) -> Unit)? = null,
    showRootFeedDock: Boolean = true,
    showCreateTaskButton: Boolean = true,
    openCreateTaskOnStart: Boolean = false,
    exitToLauncherOnBack: Boolean = false,
    exitOnCreateTaskSheetDismiss: Boolean = false,
    onCreateTaskFlowFinished: () -> Unit = {},
    pullRefreshEnabled: Boolean = true,
    summaryAvailable: Boolean = true,
    usesRootFeedHeader: Boolean = false,
    createTaskRequestKey: Int = 0,
    onCreateTaskRequestHandled: (Int) -> Unit = {},
    scrollToTopRequestKey: Int = 0,
    onRootDockCollapsedChange: (Boolean) -> Unit = {},
    onRootControlsVisibleChange: (Boolean) -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val zoneId = remember { ZoneId.systemDefault() }
    val selectedList = uiState.lists.firstOrNull { it.id == uiState.listId }
    val selectedListColorKey = selectedList?.color
    val isTodayDaytime = rememberTodoRootIsDaytime()
    val todayTimeIcon = if (isTodayDaytime) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    val todayTimeIconTint = if (isTodayDaytime) TdayTitleIconDayAccent else TdayTitleIconNightAccent
    val usesTodayStyle =
        uiState.mode == TodoListMode.TODAY || uiState.mode == TodoListMode.OVERDUE || uiState.mode == TodoListMode.SCHEDULED || uiState.mode == TodoListMode.ALL || uiState.mode == TodoListMode.PRIORITY || uiState.mode == TodoListMode.FLOATER || uiState.mode == TodoListMode.LIST
    val isRootFloaterScreen =
        uiState.mode == TodoListMode.FLOATER && uiState.listId.isNullOrBlank()
    val isListDetailScreen =
        uiState.mode == TodoListMode.LIST ||
                (uiState.mode == TodoListMode.FLOATER && !uiState.listId.isNullOrBlank())
    val usesRootFeedChrome =
        usesRootFeedHeader || isRootFloaterScreen
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
        isTodayDaytime = isTodayDaytime,
    )
    val emptyWatermarkDrawable = emptyStateDrawableForMode(uiState.mode)
    val showSectionedTimeline =
        uiState.mode == TodoListMode.TODAY || uiState.mode == TodoListMode.OVERDUE || uiState.mode == TodoListMode.SCHEDULED || uiState.mode == TodoListMode.ALL || uiState.mode == TodoListMode.PRIORITY || uiState.mode == TodoListMode.FLOATER || uiState.mode == TodoListMode.LIST
    val suppressInitialTodayTimeline =
        uiState.mode == TodoListMode.TODAY &&
                !uiState.hasHydratedSnapshot &&
                uiState.items.isEmpty()
    var draggedScheduledTodoId by rememberSaveable(uiState.mode) { mutableStateOf<String?>(null) }
    val canRescheduleTasks = uiState.mode.supportsTaskReschedule()
    val timelineSections = remember(uiState.mode, uiState.items) {
        buildTimelineSections(
            mode = uiState.mode,
            items = uiState.items,
        )
    }
    val floaterListRows = remember(uiState.mode, uiState.listId, uiState.items, uiState.lists) {
        if (uiState.mode == TodoListMode.FLOATER && uiState.listId.isNullOrBlank()) {
            val floaterCountsByList = uiState.items
                .asSequence()
                .mapNotNull { it.listId }
                .groupingBy { it }
                .eachCount()
            // Show every list, including ones with no tasks yet, so a newly
            // created (still-empty) list is always reachable here.
            uiState.lists.map { list ->
                list to (floaterCountsByList[list.id] ?: 0)
            }
        } else {
            emptyList()
        }
    }
    val floaterListById = remember(uiState.lists) { uiState.lists.associateBy { it.id } }
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
    val screenScope = rememberCoroutineScope()
    val hasScrollableContent =
        listState.canScrollForward || listState.canScrollBackward
    val dockCollapseThresholdPx = with(LocalDensity.current) {
        RootFeedDockCollapseThreshold.roundToPx()
    }
    val hasScrolledPastDockCollapseThreshold =
        listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > dockCollapseThresholdPx
    val dockCollapsed =
        hasScrollableContent && hasScrolledPastDockCollapseThreshold
    LaunchedEffect(dockCollapsed) {
        onRootDockCollapsedChange(dockCollapsed)
    }
    LaunchedEffect(Unit) {
        onRootControlsVisibleChange(true)
    }
    DisposableEffect(Unit) {
        onDispose { onRootControlsVisibleChange(true) }
    }
    val density = LocalDensity.current
    val todayTitleScrollBehavior = rememberLazyListCollapsingTitleScrollBehavior(
        listState = listState,
        maxCollapseDistance = TODAY_TITLE_COLLAPSE_DISTANCE_DP.dp,
        enabled = usesTodayStyle,
        label = "todayTitleCollapseProgress",
    )
    val isCollapsibleTimelineMode =
        uiState.mode == TodoListMode.ALL ||
                uiState.mode == TodoListMode.PRIORITY ||
                uiState.mode == TodoListMode.LIST
    var showCreateTaskSheet by rememberSaveable {
        mutableStateOf(openCreateTaskOnStart)
    }
    var openSwipeTaskId by rememberSaveable(uiState.mode, uiState.listId) {
        mutableStateOf<String?>(null)
    }
    var rootFloaterSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var rootFloaterSearchQuery by rememberSaveable { mutableStateOf("") }
    val normalizedRootFloaterSearchQuery = remember(rootFloaterSearchQuery) {
        rootFloaterSearchQuery.trim().lowercase(Locale.getDefault())
    }
    val rootFloaterSearchResults = remember(
        isRootFloaterScreen,
        normalizedRootFloaterSearchQuery,
        uiState.items,
        floaterListById,
    ) {
        if (!isRootFloaterScreen || normalizedRootFloaterSearchQuery.isBlank()) {
            emptyList()
        } else {
            uiState.items
                .asSequence()
                .filter { todo ->
                    todo.title.lowercase(Locale.getDefault())
                        .contains(normalizedRootFloaterSearchQuery) ||
                            (todo.description?.lowercase(Locale.getDefault())
                                ?.contains(normalizedRootFloaterSearchQuery) == true) ||
                            (todo.listId?.let { floaterListById[it]?.name }
                                ?.lowercase(Locale.getDefault())
                                ?.contains(normalizedRootFloaterSearchQuery) == true)
                }
                .sortedWith(
                    compareByDescending<TodoItem> { it.pinned }
                        .thenBy { floaterPriorityRank(it.priority) }
                        .thenBy { it.title.lowercase(Locale.getDefault()) },
                )
                .take(20)
                .toList()
        }
    }
    val showRootFloaterSearchResults =
        isRootFloaterScreen && rootFloaterSearchExpanded && rootFloaterSearchQuery.isNotBlank()
    val closeRootFloaterSearch = {
        rootFloaterSearchExpanded = false
        rootFloaterSearchQuery = ""
    }
    LaunchedEffect(scrollToTopRequestKey) {
        if (scrollToTopRequestKey <= 0 || !isRootFloaterScreen) return@LaunchedEffect
        closeRootFloaterSearch()
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.animateScrollToItem(index = 0, scrollOffset = 0)
        }
    }
    LaunchedEffect(uiState.items, openSwipeTaskId) {
        val openId = openSwipeTaskId ?: return@LaunchedEffect
        if (uiState.items.none { it.id == openId }) {
            openSwipeTaskId = null
        }
    }
    var lastHandledCreateTaskRequestKey by rememberSaveable { mutableStateOf(0) }
    var collapsedSectionKeys by rememberSaveable(uiState.mode, uiState.listId, highlightedTodoId) {
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
    LaunchedEffect(createTaskRequestKey) {
        if (createTaskRequestKey > 0 && createTaskRequestKey != lastHandledCreateTaskRequestKey) {
            lastHandledCreateTaskRequestKey = createTaskRequestKey
            onCreateTaskRequestHandled(createTaskRequestKey)
            closeRootFloaterSearch()
            quickAddDueEpochMs = null
            showCreateTaskSheet = true
        }
    }
    LaunchedEffect(openCreateTaskOnStart) {
        if (openCreateTaskOnStart) {
            closeRootFloaterSearch()
            quickAddDueEpochMs = null
            showCreateTaskSheet = true
        }
    }
    BackHandler(enabled = rootFloaterSearchExpanded) {
        closeRootFloaterSearch()
    }
    BackHandler(enabled = exitToLauncherOnBack && !showCreateTaskSheet && !rootFloaterSearchExpanded) {
        onBack()
    }
    LaunchedEffect(isRootFloaterScreen, rootFloaterSearchExpanded) {
        if (!isRootFloaterScreen) {
            closeRootFloaterSearch()
            onRootControlsVisibleChange(true)
        } else {
            onRootControlsVisibleChange(!rootFloaterSearchExpanded)
        }
    }
    var showListSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showCreateListSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteListConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSummarySheet by rememberSaveable(uiState.mode) { mutableStateOf(false) }
    var listSettingsTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var listSettingsName by rememberSaveable { mutableStateOf("") }
    var listSettingsColor by rememberSaveable { mutableStateOf(TDAY_DEFAULT_LIST_COLOR_KEY) }
    var listSettingsIconKey by rememberSaveable { mutableStateOf(TDAY_DEFAULT_LIST_ICON_KEY) }
    var listSettingsColorTouched by rememberSaveable { mutableStateOf(false) }
    var listSettingsIconTouched by rememberSaveable { mutableStateOf(false) }
    var createListName by rememberSaveable { mutableStateOf("") }
    var createListColor by rememberSaveable { mutableStateOf(TDAY_DEFAULT_LIST_COLOR_KEY) }
    var createListIconKey by rememberSaveable { mutableStateOf(TDAY_DEFAULT_LIST_ICON_KEY) }
    val fabInteractionSource = remember { MutableInteractionSource() }
    val editTargetTodo = remember(editTargetTodoId, uiState.items) {
        editTargetTodoId?.let { targetId -> uiState.items.firstOrNull { it.id == targetId } }
    }
    val draggedScheduledTodo = remember(draggedScheduledTodoId, uiState.items) {
        draggedScheduledTodoId?.let { targetId ->
            uiState.items.firstOrNull { it.id == targetId || it.canonicalId == targetId }
        }
    }
    val requestTaskReschedule: (TodoItem, LocalDate) -> Unit =
        requestTaskReschedule@{ todo, targetDate ->
        draggedScheduledTodoId = null
        activeDropSectionKey = null
        activeTimelineDrag = null
        timelineDropTargetBounds.clear()
            val currentDue = todo.due ?: return@requestTaskReschedule
            val currentDate = LocalDate.ofInstant(currentDue, zoneId)
        if (currentDate != targetDate) {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
            if (todo.isRecurring) {
                pendingRescheduleDrop = TaskRescheduleDrop(todo = todo, targetDate = targetDate)
            } else {
                onMoveTask(todo, targetDate, TaskRescheduleScope.OCCURRENCE)
            }
        }
    }
    val canSummarizeCurrentMode =
        summaryAvailable &&
                uiState.aiSummaryEnabled &&
                uiState.items.isNotEmpty() &&
                // Hidden on the root floater screen; kept on the per-mode screens
                // (today/all/scheduled/etc.) and list detail.
                !isRootFloaterScreen
    val topBarActions = listOfNotNull(
        if (canSummarizeCurrentMode) {
            TodoTopBarAction(
                icon = Icons.Rounded.AutoAwesome,
                contentDescription = stringResource(R.string.todos_summarize),
                onClick = { showSummarySheet = true },
            )
        } else {
            null
        },
        if (isListDetailScreen && selectedList != null) {
            TodoTopBarAction(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.action_more_options),
                onClick = {
                    listSettingsTargetId = selectedList.id
                    listSettingsName = selectedList.name
                    listSettingsColor = normalizeTdayListColorKey(selectedList.color)
                    listSettingsIconKey = selectedList.iconKey
                        ?.takeIf { isTdayListIconKeySupported(it) }
                        ?: TDAY_DEFAULT_LIST_ICON_KEY
                    listSettingsColorTouched = false
                    listSettingsIconTouched = false
                    showListSettingsSheet = true
                },
            )
        } else {
            null
        },
    )
    val fabPressed by fabInteractionSource.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.93f else 1f,
        label = "todoFabScale",
    )
    val fabOffsetY by animateDpAsState(
        targetValue = if (fabPressed) 2.dp else 0.dp,
        label = "todoFabOffsetY",
    )
    val timelineItemSpacing = TimelineDateGroupSpacing
    val timelineHeaderBodySpacing = TimelineHeaderBodySpacing
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
    fun rootFloaterTodoListTarget(todoId: String): Pair<Int, String>? {
        var itemIndex = 1 // Root Floater header row.
        timelineSections.forEach { section ->
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
            todayTitleScrollBehavior.collapseFully()
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
    fun openRootFloaterSearchResult(todo: TodoItem) {
        closeRootFloaterSearch()
        val target = rootFloaterTodoListTarget(todo.id) ?: return
        screenScope.launch {
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
            flashTodoId = todo.id
            delay(2300)
            if (flashTodoId == todo.id || flashTodoId == todo.canonicalId) {
                flashTodoId = null
            }
        }
    }
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == TodoListMode.PRIORITY || uiState.mode == TodoListMode.LIST) {
            collapsedSectionKeys = collapsedSectionKeys + "earlier"
        }
    }
    LaunchedEffect(draggedScheduledTodoId) {
        if (draggedScheduledTodoId == null) {
            timelineDropTargetBounds.clear()
        }
    }

    fun timelineSectionForKey(key: String): TodoSection? =
        timelineSections.firstOrNull { section -> section.key == key }

    fun originSectionKeyFor(todo: TodoItem): String? {
        timelineSections.firstOrNull { section ->
            section.items.any { item -> item.id == todo.id }
        }?.let { section ->
            return section.key
        }
        return timelineSections.firstOrNull { section ->
            section.items.any { item -> item.canonicalId == todo.canonicalId }
        }?.key
    }

    fun canDropTodoInTimelineSection(todo: TodoItem, section: TodoSection): Boolean {
        val targetDate = section.targetDate ?: return false
        if (originSectionKeyFor(todo) == section.key) return false
        val due = todo.due ?: return false
        return LocalDate.ofInstant(due, zoneId) != targetDate
    }

    fun timelineDropSectionKeyAt(position: Offset, todo: TodoItem): String? {
        return timelineDropTargetBounds.values
            .asSequence()
            .filter { target -> target.bounds.contains(position) }
            .mapNotNull { target ->
                val section = timelineSectionForKey(target.sectionKey) ?: return@mapNotNull null
                if (canDropTodoInTimelineSection(todo, section)) target else null
            }
            .minByOrNull { target -> target.bounds.height }
            ?.sectionKey
    }

    fun updateActiveTimelineDropTarget(position: Offset) {
        val todo = activeTimelineDrag?.todo ?: draggedScheduledTodo
        val nextSectionKey = todo?.let { timelineDropSectionKeyAt(position, it) }
        if (activeDropSectionKey != nextSectionKey) {
            activeDropSectionKey = nextSectionKey
        }
    }

    fun finishTimelineDrag(position: Offset?) {
        val drag = activeTimelineDrag
        val targetKey = position
            ?.let { dropPosition -> drag?.let { timelineDropSectionKeyAt(dropPosition, it.todo) } }
            ?: activeDropSectionKey
        val targetDate = targetKey
            ?.let(::timelineSectionForKey)
            ?.takeIf { section -> drag?.let { canDropTodoInTimelineSection(it.todo, section) } == true }
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
            when {
                usesRootFeedChrome -> Unit
                usesTodayStyle -> {
                    TodayTopBar(
                        onBack = onBack,
                        collapseProgress = todayTitleScrollBehavior.collapseProgress,
                        title = uiState.title,
                        titleColor = titleColor,
                        titleIcon = if (uiState.mode == TodoListMode.TODAY) todayTimeIcon else null,
                        titleIconTint = todayTimeIconTint,
                        actions = topBarActions,
                    )
                }

                else -> {
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
            }
        },
        floatingActionButton = {
            if (showCreateTaskButton) {
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
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    timelineDragContainerOrigin = coordinates.positionInRoot()
                },
        ) {
            TdayPullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = onRefresh,
                enabled = pullRefreshEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (usesTodayStyle) {
                                Modifier.nestedScroll(todayTitleScrollBehavior.nestedScrollConnection)
                            } else {
                                Modifier
                            },
                        ),
                    state = listState,
                    contentPadding = when {
                        usesRootFeedChrome -> PaddingValues(18.dp)
                        usesTodayStyle -> PaddingValues(horizontal = 18.dp, vertical = 2.dp)
                        else -> PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    },
                    verticalArrangement = Arrangement.spacedBy(
                        if (showSectionedTimeline) 0.dp else timelineItemSpacing,
                    ),
                ) {
                    if (usesRootFeedChrome) {
                        item(
                            key = "root-feed-title",
                            contentType = "root-feed-title",
                        ) {
                            if (isRootFloaterScreen) {
                                RootFeedSearchHeaderRow(
                                    title = uiState.title,
                                    searchExpanded = rootFloaterSearchExpanded,
                                    searchQuery = rootFloaterSearchQuery,
                                    onSearchQueryChange = { rootFloaterSearchQuery = it },
                                    onSearchExpandedChange = { rootFloaterSearchExpanded = it },
                                    onSearchClose = closeRootFloaterSearch,
                                    onCreateList = {
                                        closeRootFloaterSearch()
                                        showCreateListSheet = true
                                    },
                                    onOpenSettings = {
                                        closeRootFloaterSearch()
                                        onOpenSettings()
                                    },
                                )
                            } else {
                                RootFeedTitleRow(title = uiState.title)
                            }
                        }
                    }

                    if (showRootFloaterSearchResults) {
                        item(
                            key = "root-floater-search-results",
                            contentType = "root-floater-search-results",
                        ) {
                            RootFloaterSearchResultsCard(
                                results = rootFloaterSearchResults,
                                listsById = floaterListById,
                                onOpenTodo = ::openRootFloaterSearchResult,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }

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
                                TodoListMode.LIST -> section.key == "earlier"
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
                            val isDropEligibleSection = sectionDraggedTodo?.let { todo ->
                                canDropTodoInTimelineSection(todo, section)
                            } == true

                            if (!usesRootFeedChrome) {
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
                                            .heightIn(min = 1.dp)
                                            .timelineInAppDropTarget(
                                                targetId = "header-${section.key}",
                                                section = section,
                                                enabled = isDropEligibleSection,
                                                dropTargets = timelineDropTargetBounds,
                                            )
                                            .padding(top = if (sectionIndex == 0) 0.dp else TimelineSectionTopSpacing),
                                        section = section,
                                        useMinimalStyle = usesTodayStyle,
                                        isCollapsed = isCollapsed,
                                        isDropTarget = isActiveDropSection && isDropEligibleSection,
                                        bottomSpacing = if (isCollapsed) {
                                            TimelineCollapsedSectionSpacing
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
                            }

                            if (canRescheduleTasks && isActiveDropSection && isDropEligibleSection && section.targetDate != null) {
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
                                                enabled = isDropEligibleSection,
                                                dropTargets = timelineDropTargetBounds,
                                            )
                                            .padding(
                                                bottom = TimelineDateGroupSpacing,
                                            ),
                                        active = true,
                                        useMinimalStyle = usesTodayStyle,
                                    )
                                }
                            }

                            if (!isCollapsed && section.items.isNotEmpty()) {
                                val showEarlierDateTimeSubtitle =
                                    section.key == "earlier" &&
                                            (
                                                    uiState.mode == TodoListMode.ALL ||
                                                            uiState.mode == TodoListMode.PRIORITY ||
                                                            uiState.mode == TodoListMode.LIST
                                                    )
                                section.items.forEachIndexed { itemIndex, todo ->
                                    val showTimelineDateDivider = shouldShowDateDivider(
                                        afterItemIndex = itemIndex,
                                        inSectionIndex = sectionIndex,
                                        sections = timelineSections,
                                        collapsedSectionKeys = collapsedSectionKeys,
                                    )
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
                                                    enabled = isDropEligibleSection,
                                                    dropTargets = timelineDropTargetBounds,
                                                )
                                                .padding(
                                                    bottom = timelineTaskBottomSpacing(
                                                        itemIndex = itemIndex,
                                                        lastIndex = section.items.lastIndex,
                                                        showDateDivider = showTimelineDateDivider,
                                                    ),
                                                ),
                                            todo = todo,
                                            mode = uiState.mode,
                                            lists = uiState.lists,
                                            useMinimalStyle = usesTodayStyle,
                                            flashHighlight = flashTodoId == todo.id || flashTodoId == todo.canonicalId,
                                            showEarlierDateTimeSubtitle = showEarlierDateTimeSubtitle,
                                            showDateDivider = showTimelineDateDivider,
                                            onComplete = { onComplete(todo) },
                                            onDelete = { onDelete(todo) },
                                            onInfo = {
                                                editTargetTodoId = todo.id
                                            },
                                            draggedTodo = sectionDraggedTodo,
                                            openSwipeTaskId = openSwipeTaskId,
                                            onOpenSwipeTaskIdChange = { openSwipeTaskId = it },
                                            onDragTodoStart = if (canRescheduleTasks) {
                                                { position ->
                                                    activeDropSectionKey = null
                                                    timelineDropTargetBounds.clear()
                                                    draggedScheduledTodoId = todo.id
                                                    ViewCompat.performHapticFeedback(
                                                        view,
                                                        HapticFeedbackConstantsCompat.LONG_PRESS
                                                    )
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

                    // Root floater empty state: mirror the web layout — a
                    // centered "No floater tasks" message sitting in a gap in
                    // the middle of the screen, with the list names below it
                    // (instead of a full-screen watermark overlay).
                    if (isRootFloaterScreen && uiState.items.isEmpty() && !uiState.isLoading) {
                        item(
                            key = "floater-empty-message",
                            contentType = "floater-empty-message",
                        ) {
                            val gapHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = gapHeight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = emptyStateMessageForMode(uiState.mode),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.66f),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                            }
                        }
                    }

                    if (floaterListRows.isNotEmpty()) {
                        item(
                            key = "floater-my-lists-header",
                            contentType = "floater-list-header",
                        ) {
                            FloaterMyListsHeader(
                                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                            )
                        }
                        items(
                            items = floaterListRows,
                            key = { (list, _) -> "floater-list-${list.id}" },
                            contentType = { "floater-list-row" },
                        ) { (list, count) ->
                            FloaterListRow(
                                modifier = Modifier.padding(bottom = 10.dp),
                                name = list.name,
                                colorKey = list.color,
                                iconKey = list.iconKey,
                                count = count,
                                onClick = {
                                    onOpenFloaterList(
                                        list.id,
                                        capitalizeFirstListLetter(list.name),
                                    )
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

                    item { Spacer(Modifier.height(96.dp)) }
                }
            }

            if (emptyWatermarkDrawable != null) {
                EmptyTaskWatermark(
                    iconRes = emptyWatermarkDrawable,
                    accentColor = titleColor,
                )
            } else {
                EmptyTaskWatermark(
                    imageVector = emptyWatermarkIcon,
                    accentColor = titleColor,
                )
            }
            // Root floater shows its empty message inline (in the list, above
            // the list names) so the overlay version would double up.
            if (uiState.items.isEmpty() && !uiState.isLoading && !suppressInitialTodayTimeline && !isRootFloaterScreen) {
                EmptyTaskBackgroundMessage(
                    message = emptyStateMessageForMode(uiState.mode),
                )
            }

            if (showRootFeedDock && rootFeedTab != null && onRootFeedTabSelected != null) {
                RootFeedDock(
                    activeTab = rootFeedTab,
                    collapsed = dockCollapsed,
                    onTabSelected = { tab ->
                        if (tab == rootFeedTab && isRootFloaterScreen) {
                            screenScope.launch {
                                closeRootFloaterSearch()
                                listState.animateScrollToItem(index = 0, scrollOffset = 0)
                            }
                        } else {
                            onRootFeedTabSelected(tab)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(8f),
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
            defaultListId = if (uiState.mode == TodoListMode.LIST || uiState.mode == TodoListMode.FLOATER) uiState.listId else null,
            defaultPriority = if (uiState.mode == TodoListMode.PRIORITY) "Medium" else null,
            defaultScheduled = uiState.mode != TodoListMode.FLOATER,
            showScheduleControls = uiState.mode != TodoListMode.FLOATER,
            initialDueEpochMs = quickAddDueEpochMs,
            presentImmediately = openCreateTaskOnStart,
            onParseTaskTitleNlp = if (uiState.mode == TodoListMode.FLOATER) null else onParseTaskTitleNlp,
            onDismiss = {
                if (exitOnCreateTaskSheetDismiss) {
                    onBack()
                } else {
                    showCreateTaskSheet = false
                    quickAddDueEpochMs = null
                    onCreateTaskFlowFinished()
                }
            },
            onCreateTask = { payload ->
                onAddTask(payload)
                showCreateTaskSheet = false
                quickAddDueEpochMs = null
                onCreateTaskFlowFinished()
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
            summarySource = uiState.summarySource,
            aiSummaryConfigured = uiState.aiSummaryConfigured,
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
            showScheduleControls = uiState.mode != TodoListMode.FLOATER,
            onParseTaskTitleNlp = if (uiState.mode == TodoListMode.FLOATER) null else onParseTaskTitleNlp,
            onDismiss = { editTargetTodoId = null },
            onCreateTask = { _ -> },
            onUpdateTask = { target, payload ->
                onUpdateTask(target, payload)
                editTargetTodoId = null
            },
        )
    }

    if (showCreateListSheet && isRootFloaterScreen) {
        ListSettingsBottomSheet(
            title = stringResource(R.string.home_new_list),
            listName = createListName,
            onListNameChange = { createListName = capitalizeFirstListLetter(it) },
            listColor = createListColor,
            onListColorChange = { createListColor = it },
            listIconKey = createListIconKey,
            onListIconChange = { createListIconKey = it },
            showDelete = false,
            onDismiss = { showCreateListSheet = false },
            onSave = {
                val normalizedName = capitalizeFirstListLetter(createListName).trim()
                if (normalizedName.isNotBlank()) {
                    onCreateList(normalizedName, createListColor, createListIconKey)
                    createListName = ""
                    createListColor = TDAY_DEFAULT_LIST_COLOR_KEY
                    createListIconKey = TDAY_DEFAULT_LIST_ICON_KEY
                    showCreateListSheet = false
                }
            },
            onDelete = {},
        )
    }

    val selectedListId = listSettingsTargetId ?: uiState.listId
    if (
        showListSettingsSheet &&
        isListDetailScreen &&
        !selectedListId.isNullOrBlank()
    ) {
        ListSettingsBottomSheet(
            title = stringResource(R.string.todos_list_settings),
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
            onDelete = {
                showListSettingsSheet = false
                showDeleteListConfirmation = true
            },
        )
    }

    val deleteConfirmationListId = selectedListId
    if (
        showDeleteListConfirmation &&
        isListDetailScreen &&
        !deleteConfirmationListId.isNullOrBlank()
    ) {
        ListDeleteConfirmationDialog(
            onDismissRequest = { showDeleteListConfirmation = false },
            onConfirm = {
                showDeleteListConfirmation = false
                onDeleteList(deleteConfirmationListId)
                listSettingsTargetId = null
            },
        )
    }
}

@Composable
private fun ListDeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val dialogContainerColor = TdaySheetDefaults.surfaceColor()
    val scrimColor = TdaySheetDefaults.scrimColor()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .padding(horizontal = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(maxWidth = 420.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = TdaySheetDefaults.OverlayShape,
                border = BorderStroke(1.dp, TdaySheetDefaults.cardStrokeColor()),
                colors = CardDefaults.cardColors(containerColor = dialogContainerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = stringResource(R.string.todos_delete_list_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = stringResource(R.string.todos_delete_list_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.16f,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                color = colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                        TextButton(
                            onClick = {
                                ViewCompat.performHapticFeedback(
                                    view,
                                    HapticFeedbackConstantsCompat.CLOCK_TICK,
                                )
                                onConfirm()
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = colorScheme.error,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootFeedTitleRow(
    title: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDaytime = rememberTodoRootIsDaytime()
    val titleIcon = if (isDaytime) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    val titleIconTint = if (isDaytime) TdayTitleIconDayAccent else TdayTitleIconNightAccent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = titleIcon,
                contentDescription = null,
                tint = titleIconTint,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onBackground,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RootFeedSearchHeaderRow(
    title: String,
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchClose: () -> Unit,
    onCreateList: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val isDaytime = rememberTodoRootIsDaytime()
    val titleIcon = if (isDaytime) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    val titleIconTint = if (isDaytime) TdayTitleIconDayAccent else TdayTitleIconNightAccent
    var containerWidth by remember { mutableStateOf(0.dp) }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            delay(300)
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onSizeChanged { size ->
                containerWidth = with(density) { size.width.toDp() }
            },
    ) {
        val buttonSize = 56.dp
        val buttonGap = 8.dp
        val actionCount = 2
        val expandedSearchWidth = containerWidth.coerceAtLeast(buttonSize)
        val collapsedSearchOffset = -((buttonSize * actionCount) + (buttonGap * actionCount))
        val animatedSearchWidth by animateDpAsState(
            targetValue = if (searchExpanded) expandedSearchWidth else buttonSize,
            label = "rootFeedSearchHeaderWidth",
        )
        val animatedSearchOffset by animateDpAsState(
            targetValue = if (searchExpanded) 0.dp else collapsedSearchOffset,
            label = "rootFeedSearchHeaderOffset",
        )
        val actionsAlpha by animateFloatAsState(
            targetValue = if (searchExpanded) 0f else 1f,
            label = "rootFeedSearchHeaderActionsAlpha",
        )
        val searchContentAlpha by animateFloatAsState(
            targetValue = if (searchExpanded) 1f else 0f,
            label = "rootFeedSearchHeaderContentAlpha",
        )

        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .graphicsLayer { alpha = actionsAlpha },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = titleIcon,
                contentDescription = null,
                tint = titleIconTint,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { alpha = actionsAlpha },
            horizontalArrangement = Arrangement.spacedBy(buttonGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TodayHeaderButton(
                onClick = onCreateList,
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = stringResource(R.string.action_create_list),
            )
            TodayHeaderButton(
                onClick = onOpenSettings,
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.action_more),
            )
        }

        Card(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = animatedSearchOffset)
                .width(animatedSearchWidth)
                .height(buttonSize)
                .zIndex(2f),
            shape = CircleShape,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.38f)),
            colors = CardDefaults.cardColors(containerColor = colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                    ) {
                        if (!searchExpanded) onSearchExpandedChange(true)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.action_search),
                    tint = colorScheme.onSurface,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer { alpha = if (searchExpanded) 0f else 1f },
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp)
                        .graphicsLayer { alpha = searchContentAlpha },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        enabled = searchExpanded,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        cursorBrush = SolidColor(colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.home_search_placeholder),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    IconButton(onClick = onSearchClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RootFloaterSearchResultsCard(
    results: List<TodoItem>,
    listsById: Map<String, ListSummary>,
    onOpenTodo: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        if (results.isEmpty()) {
            Text(
                text = stringResource(R.string.home_search_no_results),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(
                    items = results,
                    key = { todo -> todo.id },
                ) { todo ->
                    val listMeta = todo.listId?.let { listsById[it] }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .heightIn(min = 48.dp)
                            .clickable { onOpenTodo(todo) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = tdayListIconForKey(listMeta?.iconKey),
                            contentDescription = null,
                            tint = tdayListAccentColor(listMeta?.color).copy(alpha = 0.92f),
                            modifier = Modifier.size(17.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = todo.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = listMeta?.name
                                    ?: stringResource(priorityDisplayLabelRes(todo.priority)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloaterMyListsHeader(
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.home_my_lists),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier,
    )
}

@Composable
private fun FloaterListRow(
    modifier: Modifier = Modifier,
    name: String,
    colorKey: String?,
    iconKey: String?,
    count: Int,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "floaterListRowScale",
    )
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        label = "floaterListRowOffsetY",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 8.dp,
        label = "floaterListRowElevation",
    )
    val accent = tdayListAccentColor(colorKey)
    val icon = tdayListIconForKey(iconKey)
    val containerColor =
        lerpColor(colorScheme.surfaceVariant, accent, FLOATER_LIST_CONTAINER_COLOR_WEIGHT)
    val displayName = capitalizeFirstListLetter(name)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .semantics(mergeDescendants = true) {}
            .offset(y = animatedOffsetY)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = animatedElevation,
            pressedElevation = animatedElevation,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = lerpColor(containerColor, Color.White, 0.34f).copy(alpha = 0.42f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 14.dp, y = 8.dp)
                    .size(82.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    text = count.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberTodoRootIsDaytime(): Boolean {
    var hour by remember { mutableStateOf(LocalTime.now().hour) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            val millisToNextMinute = ((60 - now.second) * 1000L) - (now.nano / 1_000_000L)
            delay(millisToNextMinute.coerceAtLeast(500L))
            hour = LocalTime.now().hour
        }
    }

    return hour in 6 until 18
}

@Composable
private fun TodayTopBar(
    onBack: () -> Unit,
    collapseProgress: Float,
    title: String,
    titleColor: Color,
    titleIcon: ImageVector? = null,
    titleIconTint: Color = titleColor,
    actions: List<TodoTopBarAction>,
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
    val collapsedTitleHorizontalPadding =
        maxOf(TdayDimens.FabSize, (actions.size.coerceAtLeast(1) * 56).dp) + 12.dp

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
                if (actions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        actions.forEach { action ->
                            TodayHeaderButton(
                                onClick = action.onClick,
                                icon = action.icon,
                                contentDescription = action.contentDescription,
                            )
                        }
                    }
                }
            }
            if (collapsedTitleAlpha > 0.001f) {
                TodayTitleLabel(
                    text = title,
                    color = titleColor,
                    icon = titleIcon,
                    iconTint = titleIconTint,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = collapsedTitleHorizontalPadding)
                        .wrapContentSize(Alignment.Center)
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
                TodayTitleLabel(
                    text = title,
                    color = titleColor,
                    icon = titleIcon,
                    iconTint = titleIconTint,
                    modifier = Modifier.graphicsLayer {
                        alpha = expandedTitleAlpha
                        translationY = expandedTitleShiftY
                    },
                )
            }
        }
    }
}

private data class TodoTopBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

@Composable
private fun TodayTitleLabel(
    text: String,
    color: Color,
    icon: ImageVector?,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    summarySource: String?,
    aiSummaryConfigured: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    TdayModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TdaySheetHeader(
                title = stringResource(R.string.todos_summary_title),
                leftIcon = Icons.Rounded.Close,
                leftContentDescription = stringResource(R.string.todos_summary_close),
                onLeftClick = onDismiss,
                showConfirmAction = false,
            )

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
                TdaySheetCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurface,
                        )
                        if (aiSummaryConfigured) {
                            Text(
                                text = if (summarySource == "ai") {
                                    stringResource(R.string.summary_source_server)
                                } else {
                                    stringResource(R.string.summary_source_local)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurface.copy(alpha = 0.58f),
                            )
                        }
                    }
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
    title: String,
    listName: String,
    onListNameChange: (String) -> Unit,
    listColor: String,
    onListColorChange: (String) -> Unit,
    listIconKey: String,
    onListIconChange: (String) -> Unit,
    showDelete: Boolean = true,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val colorScheme = MaterialTheme.colorScheme
    val selectedAccent = tdayListAccentColor(listColor)
    val selectedIcon = tdayListIconForKey(listIconKey)
    val canSave = listName.isNotBlank()

    TdayModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                TdaySheetHeader(
                    title = title,
                    leftIcon = Icons.Rounded.Close,
                    leftContentDescription = stringResource(R.string.action_close),
                    onLeftClick = {
                        focusManager.clearFocus(force = true)
                        onDismiss()
                    },
                    confirmContentDescription = stringResource(R.string.todos_save_list_settings),
                    onConfirm = {
                        focusManager.clearFocus(force = true)
                        if (canSave) onSave()
                    },
                    confirmEnabled = canSave,
                )

                TdaySheetSectionTitle(
                    text = stringResource(R.string.home_section_list),
                )
                TdaySheetCard {
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
                                            TdaySheetDefaults.controlSurfaceColor(),
                                            RoundedCornerShape(16.dp)
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

                TdaySheetSectionTitle(
                    text = stringResource(R.string.home_section_color),
                )
                TdaySheetCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TdayListColorOptions.forEach { option ->
                            val colorKey = option.key
                            val selected = listColor == colorKey
                            val swatchColor = option.color
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

                TdaySheetSectionTitle(
                    text = stringResource(R.string.home_section_icon),
                )
                TdaySheetCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TdayListIconOptions.forEach { option ->
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
                                            TdaySheetDefaults.controlSurfaceColor()
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
                                    painter = painterResource(option.iconRes),
                                    contentDescription = stringResource(R.string.home_section_icon),
                                    tint = if (selected) selectedAccent else colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                if (showDelete) {
                    ListSettingsDeleteButton(onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun ListSettingsDeleteButton(
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "listSettingsDeleteButtonScale",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, colorScheme.error.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.error.copy(
                alpha = if (TdaySheetDefaults.isDarkTheme()) 0.14f else 0.04f,
            ),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = colorScheme.error,
            )
            Text(
                text = stringResource(R.string.action_delete_list),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.error,
                fontWeight = FontWeight.ExtraBold,
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
    if (section.title.isEmpty()) return

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
        lerpColor(baseHeaderColor, colorScheme.onSurface, 0.16f)
    } else if (isDropTarget) {
        colorScheme.error
    } else {
        baseHeaderColor
    }
    val baseChevronColor =
        colorScheme.onSurfaceVariant.copy(alpha = if (useMinimalStyle) 0.72f else 1f)
    val chevronColor = if (isHeaderPressed) {
        lerpColor(baseChevronColor, colorScheme.onSurface, 0.16f)
    } else {
        baseChevronColor
    }
    val minimumHeaderHeight = if (useMinimalStyle) 32.dp else 44.dp
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
                todo.due?.let(TODO_DUE_TIME_FORMATTER::format)?.let { dueText ->
                    Text(
                        text = dueText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (showListIndicator) {
                Icon(
                    imageVector = tdayListIconForKey(listMeta?.iconKey),
                    contentDescription = null,
                    tint = tdayListAccentColor(listMeta?.color),
                    modifier = Modifier.size(18.dp),
                )
            }
            priorityIconFor(todo.priority)?.let { priorityIcon ->
                Icon(
                    imageVector = priorityIcon,
                    contentDescription = null,
                    tint = tdayPriorityColor(todo.priority),
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
    openSwipeTaskId: String?,
    onOpenSwipeTaskIdChange: (String?) -> Unit,
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
                openSwipeTaskId = openSwipeTaskId,
                onOpenSwipeTaskIdChange = onOpenSwipeTaskIdChange,
            )
        } else if (
            useMinimalStyle &&
            (
                    mode == TodoListMode.TODAY ||
                            mode == TodoListMode.OVERDUE ||
                            mode == TodoListMode.SCHEDULED ||
                            mode == TodoListMode.PRIORITY ||
                            mode == TodoListMode.FLOATER ||
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
                openSwipeTaskId = openSwipeTaskId,
                onOpenSwipeTaskIdChange = onOpenSwipeTaskIdChange,
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
        val currentDue = currentTodo.due ?: return false
        val nextDue = nextTodoInSection.due ?: return false
        return !currentDue.isSameLocalDayAs(nextDue, zoneId)
    }

    val nextVisibleTodo = sections
        .asSequence()
        .drop(inSectionIndex + 1)
        .filter { it.key !in collapsedSectionKeys }
        .flatMap { it.items.asSequence() }
        .firstOrNull()
        ?: return false

    val currentDue = currentTodo.due ?: return false
    val nextDue = nextVisibleTodo.due ?: return false
    return !currentDue.isSameLocalDayAs(nextDue, zoneId)
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

        TodoListMode.FLOATER -> buildFloaterSections(items)

        TodoListMode.LIST -> buildScheduledSections(
            items = items,
            zoneId = zoneId,
            futureOnly = false,
            placesEarlierBeforeToday = true,
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
        .mapNotNull { todo -> todo.due?.let { due -> due to todo } }
        .filter { (due, _) -> due.isBefore(now) }
        .groupBy({ (due, _) -> LocalDate.ofInstant(due, zoneId) }, { (_, todo) -> todo })

    val sections = mutableListOf<TodoSection>()

    overdueByDate[today]
        ?.sortedBy { it.due ?: Instant.MAX }
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
                items = overdueByDate[date].orEmpty().sortedBy { it.due ?: Instant.MAX },
                quickAddDefaults = null,
            )
        }

    return sections
}

private fun buildTodaySections(
    items: List<TodoItem>,
    zoneId: ZoneId,
): List<TodoSection> {
    val sorted = items.filter { it.due != null }.sortedBy { it.due ?: Instant.MAX }
    val noon = LocalTime.NOON
    val eveningStartBoundary = LocalTime.of(18, 0)

    fun sectionOf(todo: TodoItem): TodaySectionSlot {
        val dueTime = todo.due?.atZone(zoneId)?.toLocalTime() ?: LocalTime.NOON
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

private fun buildFloaterSections(items: List<TodoItem>): List<TodoSection> {
    val floaterItems = items
        .sortedWith(
            compareByDescending<TodoItem> { it.pinned }
                .thenBy { floaterPriorityRank(it.priority) }
                .thenByDescending { it.updatedAt }
                .thenBy { it.title.lowercase(Locale.getDefault()) },
        )

    return listOf(
        TodoSection(
            key = "floater-all",
            title = "",
            items = floaterItems,
        ),
    )
}

private fun floaterPriorityRank(priority: String): Int {
    return when {
        isUrgentPriority(priority) -> 0
        isImportantPriority(priority) -> 1
        else -> 2
    }
}

private fun buildScheduledSections(
    items: List<TodoItem>,
    zoneId: ZoneId,
    futureOnly: Boolean,
    placesEarlierBeforeToday: Boolean = true,
    includeEmptyEarlierTarget: Boolean = false,
): List<TodoSection> {
    val now = Instant.now()
    val sorted = items.asSequence().mapNotNull { todo ->
        todo.due?.let { due -> due to todo }
    }.filter { (due, _) ->
        if (futureOnly) !due.isBefore(now) else true
    }.sortedBy { (due, _) -> due }.map { (_, todo) -> todo }.toList()
    val groupedByDate = sorted.groupBy { todo ->
        LocalDate.ofInstant(todo.due ?: Instant.MAX, zoneId)
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
            .flatMap { (_, dayItems) -> dayItems.asSequence() }.sortedBy { it.due ?: Instant.MAX }
            .toList()
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
    }.flatMap { (_, dayItems) -> dayItems.asSequence() }.sortedBy { it.due ?: Instant.MAX }.toList()
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
        }.flatMap { (_, dayItems) -> dayItems.asSequence() }.sortedBy { it.due ?: Instant.MAX }
            .toList()
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
            val monthName = runCatching {
                val ym = YearMonth.parse(ymPart)
                ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            }.getOrNull()

            if (monthName != null) {
                stringResource(R.string.todos_section_rest_of, monthName)
            } else {
                section.title
            }
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
        TodoListMode.FLOATER -> stringResource(R.string.todos_empty_floater)
        TodoListMode.SCHEDULED -> stringResource(R.string.todos_empty_scheduled)
        TodoListMode.ALL -> stringResource(R.string.todos_empty_all)
        TodoListMode.LIST -> stringResource(R.string.todos_empty_list)
    }
}

/**
 * Lucide drawable watermark for the home-category modes, mirroring the web app.
 * Returns null for modes that keep their Material vector watermark (today/floater/list).
 */
@DrawableRes
private fun emptyStateDrawableForMode(mode: TodoListMode): Int? {
    return when (mode) {
        TodoListMode.OVERDUE -> R.drawable.ic_lucide_clock_3
        TodoListMode.PRIORITY -> R.drawable.ic_lucide_flag
        TodoListMode.SCHEDULED -> R.drawable.ic_lucide_calendar_clock
        TodoListMode.ALL -> R.drawable.ic_lucide_layers
        else -> null
    }
}

@Composable
private fun emptyStateIconForMode(
    mode: TodoListMode,
    listIconKey: String?,
    isTodayDaytime: Boolean,
): ImageVector {
    return when (mode) {
        TodoListMode.TODAY -> if (isTodayDaytime) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
        TodoListMode.OVERDUE -> Icons.Rounded.ErrorOutline
        TodoListMode.PRIORITY -> Icons.Rounded.Flag
        TodoListMode.FLOATER ->
            if (listIconKey.isNullOrBlank()) Icons.Rounded.Eco else tdayListIconForKey(listIconKey)
        TodoListMode.SCHEDULED -> Icons.Rounded.Schedule
        TodoListMode.ALL -> Icons.Rounded.Inbox
        TodoListMode.LIST -> tdayListIconForKey(listIconKey)
    }
}

private val SCHEDULED_DAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault())

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
private const val TASK_COMPLETION_CHECK_TO_STRIKE_MS = 160L
private const val TASK_COMPLETION_STRIKE_TO_FADE_MS = 360L
private const val TASK_COMPLETION_FADE_MS = 260L
private val SWIPE_ROW_CONTENT_VERTICAL_PADDING = 2.dp
private val SWIPE_ROW_HEIGHT = 56.dp
private val TASK_CHECKMARK_GREEN = TdayTaskCompleteAccent
private val TODO_DUE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(ZoneId.systemDefault())
private val TODO_DUE_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

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
    openSwipeTaskId: String?,
    onOpenSwipeTaskIdChange: (String?) -> Unit,
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
        openSwipeTaskId = openSwipeTaskId,
        onOpenSwipeTaskIdChange = onOpenSwipeTaskIdChange,
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
    openSwipeTaskId: String?,
    onOpenSwipeTaskIdChange: (String?) -> Unit,
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
        openSwipeTaskId = openSwipeTaskId,
        onOpenSwipeTaskIdChange = onOpenSwipeTaskIdChange,
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
    openSwipeTaskId: String?,
    onOpenSwipeTaskIdChange: (String?) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val swipeRevealState = rememberTaskSwipeRevealState(todo.id)
    var localChecked by remember(todo.id) { mutableStateOf(false) }
    var localStruck by remember(todo.id) { mutableStateOf(false) }
    var pendingCompletion by remember(todo.id) { mutableStateOf(false) }
    var completionFading by remember(todo.id) { mutableStateOf(false) }
    var titleLayoutResult by remember(todo.id) { mutableStateOf<TextLayoutResult?>(null) }
    var rowOriginInRoot by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var dragPointerPosition by remember(todo.id) { mutableStateOf<Offset?>(null) }
    val latestOpenSwipeTaskId = rememberUpdatedState(openSwipeTaskId)
    fun claimSwipeSlot() {
        if (latestOpenSwipeTaskId.value != todo.id) {
            onOpenSwipeTaskIdChange(todo.id)
        }
    }

    fun closeSwipeSlot() {
        swipeRevealState.close()
        if (latestOpenSwipeTaskId.value == todo.id) {
            onOpenSwipeTaskIdChange(null)
        }
    }
    val highlightAnim = remember(todo.id) { Animatable(0f) }
    val visuallyChecked = localChecked || (keepCompletedInline && todo.completed)
    val visuallyStruck = localStruck || (keepCompletedInline && todo.completed)
    val animatedOffsetX by animateTaskSwipeOffsetAsState(
        state = swipeRevealState,
        label = "swipeTaskOffset",
    )
    val actionRevealProgress = swipeRevealState.revealProgress(animatedOffsetX)
    val completionAlpha by animateFloatAsState(
        targetValue = if (completionFading) 0f else 1f,
        animationSpec = tween(
            durationMillis = TASK_COMPLETION_FADE_MS.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "swipeTaskCompletionAlpha",
    )
    val completionOffsetY by animateDpAsState(
        targetValue = if (completionFading) (-10).dp else 0.dp,
        animationSpec = tween(
            durationMillis = TASK_COMPLETION_FADE_MS.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "swipeTaskCompletionOffsetY",
    )
    val titleStrikeProgress by animateFloatAsState(
        targetValue = if (visuallyStruck) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "swipeTaskTitleStrikeProgress",
    )
    val isOverdue = !todo.completed && todo.due?.isBefore(Instant.now()) == true
    val dueBodyText = todo.due?.let {
        if (showDueDateInSubtitle) {
            TODO_DUE_DATE_TIME_FORMATTER.format(it)
        } else {
            TODO_DUE_TIME_FORMATTER.format(it)
        }
    }
    val dueSubtitleText = dueBodyText?.let { text ->
        if (isOverdue) {
            stringResource(R.string.todos_due_overdue_text, text)
        } else if (showDuePrefix) {
            stringResource(R.string.todos_due_text, text)
        } else {
            text
        }
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
        TodoListMode.FLOATER,
        TodoListMode.ALL,
            -> listMeta != null

        TodoListMode.LIST,
            -> false
    }
    val priorityIcon = priorityIconFor(todo.priority)
    val showPriorityIcon = priorityIcon != null
    val listIndicatorColor = tdayListAccentColor(listMeta?.color)
    LaunchedEffect(openSwipeTaskId, todo.id) {
        if (openSwipeTaskId != null && openSwipeTaskId != todo.id && swipeRevealState.isOpenOrDragging) {
            swipeRevealState.close()
        }
    }
    LaunchedEffect(flashHighlight) {
        if (!flashHighlight) return@LaunchedEffect
        closeSwipeSlot()
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
            .graphicsLayer {
                alpha = if (dragging) completionAlpha * 0.55f else completionAlpha
                translationY = completionOffsetY.toPx()
            },
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
                        .onGloballyPositioned { coordinates ->
                            rowOriginInRoot = coordinates.positionInRoot()
                        }
                        .graphicsLayer { translationX = animatedOffsetX }
                        .then(
                            if (dragEnabled) {
                                Modifier.pointerInput(todo.id, dragEnabled) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { localOffset ->
                                            closeSwipeSlot()
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
                                if (delta < 0f || swipeRevealState.isOpenOrDragging) {
                                    claimSwipeSlot()
                                }
                                swipeRevealState.dragBy(delta)
                                if (!swipeRevealState.isOpenOrDragging && latestOpenSwipeTaskId.value == todo.id) {
                                    onOpenSwipeTaskIdChange(null)
                                }
                            },
                            onDragStopped = { velocity ->
                                swipeRevealState.settle(velocity)
                                if (swipeRevealState.isOpenOrDragging) {
                                    claimSwipeSlot()
                                } else if (latestOpenSwipeTaskId.value == todo.id) {
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
                            } else if (!swipeRevealState.isHinting && !pendingCompletion && !dragging) {
                                claimSwipeSlot()
                                coroutineScope.launch {
                                    swipeRevealState.playHint()
                                    if (latestOpenSwipeTaskId.value == todo.id && !swipeRevealState.isOpenOrDragging) {
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
                                imageVector = if (!visuallyChecked) {
                                    Icons.Rounded.RadioButtonUnchecked
                                } else {
                                    Icons.Rounded.CheckCircle
                                },
                                contentDescription = if (visuallyChecked) {
                                    stringResource(R.string.label_completed)
                                } else {
                                    stringResource(R.string.label_mark_complete)
                                },
                                tint = if (!visuallyChecked) {
                                    colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                } else {
                                    TASK_CHECKMARK_GREEN
                                },
                                enabled = !visuallyChecked && !pendingCompletion,
                                onClick = {
                                    ViewCompat.performHapticFeedback(
                                        view,
                                        HapticFeedbackConstantsCompat.CLOCK_TICK,
                                    )
                                    closeSwipeSlot()
                                    localChecked = true
                                    pendingCompletion = true
                                    coroutineScope.launch {
                                        delay(TASK_COMPLETION_CHECK_TO_STRIKE_MS)
                                        localStruck = true
                                        delay(TASK_COMPLETION_STRIKE_TO_FADE_MS)
                                        completionFading = true
                                        delay(TASK_COMPLETION_FADE_MS)
                                        onComplete()
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
                                    color = if (visuallyStruck) {
                                        colorScheme.onSurface.copy(alpha = 0.78f)
                                    } else {
                                        colorScheme.onSurface
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    textDecoration = TextDecoration.None,
                                    maxLines = 2,
                                    onTextLayout = { titleLayoutResult = it },
                                )
                                if (showDueText && dueSubtitleText != null) {
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
                                        imageVector = tdayListIconForKey(listMeta?.iconKey),
                                        contentDescription = stringResource(R.string.label_task_list),
                                        tint = listIndicatorColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                if (priorityIcon != null) {
                                    Icon(
                                        imageVector = priorityIcon,
                                        contentDescription = stringResource(R.string.label_priority_task),
                                        tint = tdayPriorityColor(todo.priority),
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
    val isDetailOverdue = !todo.completed && todo.due?.isBefore(Instant.now()) == true
    val detailDueText = todo.due?.let { due ->
        val dueText = TODO_DUE_TIME_FORMATTER.format(due)
        if (isDetailOverdue) {
            stringResource(R.string.todos_due_overdue_text, dueText)
        } else {
            dueText
        }
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
                    detailDueText?.let { text ->
                        Text(
                            text = text,
                            color = if (isDetailOverdue) colorScheme.error else colorScheme.onSurfaceVariant.copy(
                                alpha = 0.8f
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
    val due = todo.due?.let(TODO_DUE_DATE_TIME_FORMATTER::format)

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
                    due?.let { text ->
                        Text(
                            text = text,
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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

private fun priorityIconFor(priority: String): ImageVector? {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "medium" -> Icons.Rounded.Flag
        "high", "urgent", "important" -> Icons.Rounded.Flag
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
        TodoListMode.TODAY -> TdayTodoModeTodayAccent
        TodoListMode.OVERDUE -> TdayTodoModeOverdueAccent
        TodoListMode.SCHEDULED -> TdayTodoModeScheduledAccent
        TodoListMode.ALL -> TdayTodoModeAllAccent
        TodoListMode.PRIORITY -> TdayTodoModePriorityAccent
        TodoListMode.FLOATER -> listColorKey
            ?.takeIf { it.isNotBlank() }
            ?.let(::tdayListAccentColor)
            ?: TdayFloaterAccent
        TodoListMode.LIST -> tdayListAccentColor(listColorKey)
    }
}

private const val FLOATER_LIST_CONTAINER_COLOR_WEIGHT = 0.66f
