package com.ohmz.tday.compose.feature.scheduledtaskhome

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import com.ohmz.tday.compose.core.sound.rememberTaskCompletionSound
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
import com.ohmz.tday.compose.core.ui.LazyListHeroTitleSettle
import com.ohmz.tday.compose.core.ui.CategoryCard
import com.ohmz.tday.compose.core.ui.EmptyTaskWatermark
import com.ohmz.tday.compose.core.ui.LocalSnackbarManager
import com.ohmz.tday.compose.core.ui.TaskSwipeActionButton
import com.ohmz.tday.compose.core.ui.TdayHaptics
import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import com.ohmz.tday.compose.core.ui.TdaySheetMotion
import com.ohmz.tday.compose.core.ui.animateTaskSwipeOffsetAsState
import com.ohmz.tday.compose.core.ui.rememberSystemMotionScale
import com.ohmz.tday.compose.core.ui.rememberTaskStrikeProgress
import com.ohmz.tday.compose.core.ui.rememberTaskSwipeRevealState
import com.ohmz.tday.compose.core.ui.rememberTdayMotionEnabled
import com.ohmz.tday.compose.core.ui.rememberTdayMotionScale
import com.ohmz.tday.compose.core.ui.scaledDelay
import com.ohmz.tday.compose.core.ui.taskCopyText
import com.ohmz.tday.compose.core.ui.taskStrikethrough
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.component.rememberSheetDismissState
import com.ohmz.tday.compose.core.ui.RootFeedHeroHeader
import com.ohmz.tday.compose.core.ui.RootFeedHeroHeaderMetrics
import com.ohmz.tday.compose.core.ui.RootFeedHeroMark
import com.ohmz.tday.compose.ui.component.RootFeedDock
import com.ohmz.tday.compose.ui.component.RootFeedTab
import com.ohmz.tday.compose.ui.component.TdayCenteredSheetContent
import com.ohmz.tday.compose.ui.component.TdayModalBottomSheet
import com.ohmz.tday.compose.ui.component.TdaySheetFullBleedWindow
import com.ohmz.tday.compose.ui.component.TdayPullToRefreshBox
import com.ohmz.tday.compose.ui.component.TdaySheetCard
import com.ohmz.tday.compose.ui.component.TdaySheetDefaults
import com.ohmz.tday.compose.ui.component.TdaySheetHeader
import com.ohmz.tday.compose.ui.component.TdaySheetSectionTitle
import com.ohmz.tday.compose.ui.theme.TDAY_DEFAULT_LIST_COLOR_KEY
import com.ohmz.tday.compose.ui.theme.TDAY_DEFAULT_LIST_ICON_KEY
import com.ohmz.tday.compose.ui.theme.TdayCompletedTileAccent
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayFontFamily
import com.ohmz.tday.compose.ui.theme.TdayListColorOptions
import com.ohmz.tday.compose.ui.theme.TdayListIconOptions
import com.ohmz.tday.compose.ui.theme.TdaySwipeCopyBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeDeleteBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeEditBackground
import com.ohmz.tday.compose.ui.theme.TdayTaskCompleteAccent
import com.ohmz.tday.compose.ui.theme.TdayTitleIconDayAccent
import com.ohmz.tday.compose.ui.theme.TdayTitleIconNightAccent
import com.ohmz.tday.compose.ui.theme.tdayListAccentColor
import com.ohmz.tday.compose.ui.theme.tdayListIconForKey
import com.ohmz.tday.compose.ui.theme.tdayPriorityColor
import com.ohmz.tday.shared.sort.TaskSortEngine
import com.ohmz.tday.shared.sort.TaskSortKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.ohmz.tday.compose.core.text.flattenNotesToPlainText

// Maps a domain TodoItem onto the shared sort key so this feed orders tasks
// through the one TaskSortEngine (identical order to every other T'Day surface).
private fun TodoItem.toTaskSortKey(): TaskSortKey = TaskSortKey(
    id = id,
    pinned = pinned,
    dueEpochMs = due?.toEpochMilli(),
    priorityRank = TaskSortEngine.priorityRank(priority),
    updatedAtEpochMs = updatedAt?.toEpochMilli(),
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduledTaskHomeScreen(
    uiState: ScheduledTaskHomeUiState,
    onRefresh: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenOverdue: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenPriority: () -> Unit,
    onOpenCompleted: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenFloater: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTaskFromSearch: (todoId: String) -> Unit,
    onOpenList: (listId: String, listName: String) -> Unit,
    onCreateTask: (payload: CreateTaskPayload) -> Unit,
    onParseTaskTitleNlp: suspend (title: String, referenceDueEpochMs: Long) -> TodoTitleNlpResponse?,
    onSuggestRepeat: (suspend (title: String) -> String?)? = null,
    onCreateList: (name: String, color: String?, iconKey: String?) -> Unit,
    onCompleteTask: (todo: TodoItem) -> Unit,
    onDeleteTask: (todo: TodoItem) -> Unit,
    onUpdateTask: (todo: TodoItem, payload: CreateTaskPayload) -> Unit,
    onSummarize: () -> Unit = {},
    summaryAvailable: Boolean = true,
    showRootFeedDock: Boolean = true,
    showCreateTaskButton: Boolean = true,
    pullRefreshEnabled: Boolean = true,
    createTaskRequestKey: Int = 0,
    onCreateTaskRequestHandled: (Int) -> Unit = {},
    scrollToTopRequestKey: Int = 0,
    onRootDockCollapsedChange: (Boolean) -> Unit = {},
    onRootControlsVisibleChange: (Boolean) -> Unit = {},
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val fabInteractionSource = remember { MutableInteractionSource() }
    val fabPressed by fabInteractionSource.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.93f else 1f,
        label = "fabScale",
    )
    val fabOffsetY by animateDpAsState(
        targetValue = if (fabPressed) 2.dp else 0.dp,
        label = "fabOffsetY",
    )
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val imeVisible = WindowInsets.isImeVisible
    var searchImeWasVisible by rememberSaveable { mutableStateOf(false) }
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }
    var searchResultsBounds by remember { mutableStateOf<Rect?>(null) }
    var rootInRoot by remember { mutableStateOf(Offset.Zero) }
    var showCreateTask by rememberSaveable { mutableStateOf(false) }
    var showSummarySheet by rememberSaveable { mutableStateOf(false) }
    var openSwipeTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastHandledCreateTaskRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var editTargetTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    val editTargetTodo = remember(editTargetTodoId, uiState.todayTodos) {
        editTargetTodoId?.let { id -> uiState.todayTodos.firstOrNull { it.id == id } }
    }
    var listName by rememberSaveable { mutableStateOf("") }
    var listColor by rememberSaveable { mutableStateOf(TDAY_DEFAULT_LIST_COLOR_KEY) }
    var listIconKey by rememberSaveable { mutableStateOf(TDAY_DEFAULT_LIST_ICON_KEY) }
    var showCreateList by rememberSaveable { mutableStateOf(false) }
    var searchResultOpening by rememberSaveable { mutableStateOf(false) }
    val searchResultScope = rememberCoroutineScope()
    val closeSearch = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        searchExpanded = false
        searchQuery = ""
        searchBarBounds = null
        searchResultsBounds = null
        rootInRoot = Offset.Zero
        searchImeWasVisible = false
        searchResultOpening = false
    }
    // The SYSTEM scale and not the app's: what this is timed against is the route
    // handover in `TdayApp`, a `fadeIn(tween(NAV_FADE_IN_DURATION_MS))` that reads
    // nothing of the preference and so keeps running at the device's scale whatever
    // the in-app switch says. Handed the app's scale, the switch would zero the wait
    // and leave the transition — the motion kept, the wait removed, which is the one
    // way round `docs/motion.md`'s fifth rule nobody looks for. Moves to
    // `rememberTdayMotionScale` on the day that transition is gated.
    val searchCloseMotionScale = rememberSystemMotionScale()
    val openTaskFromSearch: (String) -> Unit = openTask@{ todoId ->
        if (searchResultOpening) return@openTask
        searchResultOpening = true
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onOpenTaskFromSearch(todoId)
        searchResultScope.launch {
            // Scaled, because what it is waiting out is the push onto the task:
            // tearing the search surface down underneath a transition that is
            // still running is the jump this wait exists to hide, and with the
            // device's animations off there is no transition left to hide behind.
            scaledDelay(SEARCH_RESULT_SEARCH_CLOSE_DELAY_MS, searchCloseMotionScale)
            closeSearch()
        }
    }
    BackHandler(enabled = searchExpanded) {
        closeSearch()
    }
    LaunchedEffect(createTaskRequestKey) {
        if (createTaskRequestKey > 0 && createTaskRequestKey != lastHandledCreateTaskRequestKey) {
            lastHandledCreateTaskRequestKey = createTaskRequestKey
            onCreateTaskRequestHandled(createTaskRequestKey)
            closeSearch()
            showCreateTask = true
        }
    }
    LaunchedEffect(searchExpanded) {
        onRootControlsVisibleChange(!searchExpanded)
    }
    DisposableEffect(Unit) {
        onDispose { onRootControlsVisibleChange(true) }
    }
    LaunchedEffect(searchExpanded, imeVisible) {
        if (!searchExpanded) {
            searchImeWasVisible = false
            return@LaunchedEffect
        }
        if (imeVisible) {
            searchImeWasVisible = true
            return@LaunchedEffect
        }
        if (searchImeWasVisible) {
            // Back usually dismisses IME first; once IME goes away, also close search.
            closeSearch()
        }
    }
    val listById = remember(uiState.summary.lists) { uiState.summary.lists.associateBy { it.id } }
    val normalizedSearchQuery = remember(searchQuery) { searchQuery.trim().lowercase(Locale.getDefault()) }
    val overdueCount by remember {
        derivedStateOf {
            val now = Instant.now()
            uiState.searchableTodos.count { todo -> todo.due?.isBefore(now) == true }
        }
    }
    val dueFormatter = remember {
        DateTimeFormatter.ofPattern("EEE h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    val searchResults = remember(normalizedSearchQuery, uiState.searchableTodos, listById) {
        if (normalizedSearchQuery.isBlank()) {
            emptyList()
        } else {
            uiState.searchableTodos
                .asSequence()
                .filter { todo ->
                    todo.title.lowercase(Locale.getDefault()).contains(normalizedSearchQuery) ||
                        flattenNotesToPlainText(todo.description).lowercase(Locale.getDefault())
                            .contains(normalizedSearchQuery) ||
                        (todo.listId?.let { listById[it]?.name }?.lowercase(Locale.getDefault())
                            ?.contains(normalizedSearchQuery) == true)
                }
                .toList()
                .let { filtered -> TaskSortEngine.sortedTodos(filtered) { it.toTaskSortKey() } }
                .take(20)
        }
    }
    val showSearchResultsOverlay = searchExpanded && searchQuery.isNotBlank()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val hasScrollableContent =
        listState.canScrollForward || listState.canScrollBackward
    val dockCollapseThresholdPx = with(density) { RootFeedDockCollapseThreshold.roundToPx() }
    val headerCollapsePx = with(density) { RootFeedHeroHeaderMetrics.CollapseDistance.toPx() }
    // The header draws the refresh pill itself, so it can fly in from the top
    // and hover in front of the title instead of being painted underneath the
    // pinned toolbar.
    var refreshIsRefreshing by remember { mutableStateOf(false) }
    var refreshPullFraction by remember { mutableFloatStateOf(0f) }
    var refreshWaveFrozen by remember { mutableStateOf(false) }
    val headerBarHeightPx = with(density) { RootFeedHeroHeaderMetrics.BarHeight.toPx() }
    // Read lazily inside the header so a scroll frame recomposes the header
    // alone rather than this whole screen.
    val headerCollapseProgress: () -> Float = {
        if (listState.firstVisibleItemIndex > 0) {
            1f
        } else {
            (listState.firstVisibleItemScrollOffset / headerCollapsePx).coerceIn(0f, 1f)
        }
    }
    val hasScrolledPastDockCollapseThreshold =
        listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > dockCollapseThresholdPx
    val dockCollapsed =
        hasScrollableContent && hasScrolledPastDockCollapseThreshold
    LaunchedEffect(dockCollapsed) {
        onRootDockCollapsedChange(dockCollapsed)
    }
    LaunchedEffect(scrollToTopRequestKey) {
        if (scrollToTopRequestKey <= 0) return@LaunchedEffect
        closeSearch()
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.animateScrollToItem(index = 0, scrollOffset = 0)
        }
    }
    LaunchedEffect(uiState.todayTodos, openSwipeTaskId) {
        val openId = openSwipeTaskId ?: return@LaunchedEffect
        if (uiState.todayTodos.none { it.id == openId }) {
            openSwipeTaskId = null
        }
    }
    // The shared settle, not this screen's own copy of it — see
    // [LazyListHeroTitleSettle] for the three faults that copy carried.
    LazyListHeroTitleSettle(
        listState = listState,
        collapsePx = headerCollapsePx,
        enabled = !searchExpanded,
    )

    LaunchedEffect(showSearchResultsOverlay) {
        if (!showSearchResultsOverlay) {
            searchResultsBounds = null
        }
    }

    Scaffold(
        containerColor = colorScheme.background,
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
                    onClick = {
                        showCreateTask = true
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val isDaytime = rememberIsDaytime()
            EmptyTaskWatermark(
                imageVector = if (isDaytime) ImageVector.vectorResource(R.drawable.ic_lucide_sun) else ImageVector.vectorResource(
                    R.drawable.ic_lucide_moon
                ),
                accentColor = TdayTitleIconDayAccent,
            )

            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                TdayPullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    enabled = pullRefreshEnabled,
                    showsIndicator = false,
                    onIndicatorStateChange = { refreshing, fraction, frozen ->
                        refreshIsRefreshing = refreshing
                        refreshPullFraction = fraction
                        refreshWaveFrozen = frozen
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (searchExpanded) {
                                    Modifier
                                        .onGloballyPositioned { coordinates ->
                                            val topLeft = coordinates.boundsInRoot().topLeft
                                            if (rootInRoot != topLeft) {
                                                rootInRoot = topLeft
                                            }
                                        }
                                        .pointerInput(
                                            searchBarBounds,
                                            searchResultsBounds,
                                            rootInRoot
                                        ) {
                                            awaitEachGesture {
                                                val down =
                                                    awaitFirstDown(pass = PointerEventPass.Final)
                                                val tapInRoot = down.position + rootInRoot
                                                // The whole toolbar row belongs to the
                                                // header — the field, its cancel button and
                                                // the tap that opened the field all land
                                                // inside it. Fixed geometry rather than a
                                                // reported bounds rect, so this cannot race
                                                // a layout pass and shut the field on the
                                                // very tap that opened it.
                                                val tappedToolbarRow =
                                                    tapInRoot.y <= headerBarHeightPx
                                                val tappedSearchResults =
                                                    searchResultsBounds?.contains(tapInRoot) == true
                                                val up =
                                                    waitForUpOrCancellation(pass = PointerEventPass.Final)
                                                if (up != null && !tappedToolbarRow && !tappedSearchResults) {
                                                    closeSearch()
                                                }
                                            }
                                        }
                                } else {
                                    Modifier
                                }
                            ),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 18.dp,
                            end = 18.dp,
                            bottom = 18.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                    item(key = "root-feed-header-spacer") {
                        // Reserves the pinned header's space. The feed scrolls
                        // behind the header, folding it down into its
                        // always-visible toolbar strip.
                        Spacer(modifier = Modifier.height(RootFeedHeroHeaderMetrics.ExpandedHeight))
                    }

                        // While a query is live the results take the screen
                        // over: the feed emits nothing, leaving the results
                        // panel and blank space. A tap on that blank space
                        // dismisses the field via the outside-tap gesture.
                        if (!showSearchResultsOverlay) {
                        item {
                            ScheduledTaskHomeTodayCard(
                                count = uiState.summary.todayCount,
                                onClick = {
                                    closeSearch()
                                    onOpenToday()
                                },
                            )
                        }

                        itemsIndexed(
                            items = uiState.todayTodos,
                            key = { _, todo -> "scheduled-task-home-today-${todo.id}" },
                            contentType = { _, _ -> "scheduled_task_home_today_task" },
                        ) { _, todo ->
                            ScheduledTaskHomeTodayTaskRow(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = ScheduledTaskHomeItemFadeIn,
                                    placementSpec = ScheduledTaskHomeItemPlacement,
                                    fadeOutSpec = ScheduledTaskHomeItemFadeOut,
                                ),
                                todo = todo,
                                lists = uiState.summary.lists,
                                onComplete = { onCompleteTask(todo) },
                                onDelete = { onDeleteTask(todo) },
                                onEdit = { editTargetTodoId = todo.id },
                                openSwipeTaskId = openSwipeTaskId,
                                onOpenSwipeTaskIdChange = { openSwipeTaskId = it },
                            )
                        }

                    // Keyed so a completion moves this block rather than
                    // rebuilding it: without a key its slot is its index, which
                    // the departing row changes, and the whole grid was dropped
                    // and re-added a row height higher in one frame.
                    item(key = "scheduled-task-home-category-grid") {
                        Column(
                            modifier = scheduledTaskHomeDisplacedItemMotion(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CategoryGrid(
                                overdueCount = overdueCount,
                                scheduledCount = uiState.summary.scheduledCount,
                                allCount = uiState.summary.allCount,
                                priorityCount = uiState.summary.priorityCount,
                                completedCount = uiState.summary.completedCount,
                                calendarCount = uiState.summary.scheduledCount,
                                onOpenOverdue = {
                                    closeSearch()
                                    onOpenOverdue()
                                },
                                onOpenScheduled = {
                                    closeSearch()
                                    onOpenScheduled()
                                },
                                onOpenAll = {
                                    closeSearch()
                                    onOpenAll()
                                },
                                onOpenPriority = {
                                    closeSearch()
                                    onOpenPriority()
                                },
                                onOpenCompleted = {
                                    closeSearch()
                                    onOpenCompleted()
                                },
                                onOpenCalendar = {
                                    closeSearch()
                                    onOpenCalendar()
                                },
                            )
                        }
                    }

                    if (uiState.summary.lists.isNotEmpty()) {
                        item(key = "scheduled-task-home-my-lists-header") {
                            MyListsHeader(
                                modifier = scheduledTaskHomeDisplacedItemMotion(),
                            )
                        }
                        itemsIndexed(
                            items = uiState.summary.lists,
                            key = { _, list -> list.id },
                            contentType = { _, _ -> "list_row" },
                        ) { _, list ->
                            ListRow(
                                modifier = scheduledTaskHomeDisplacedItemMotion(),
                                name = list.name,
                                colorKey = list.color,
                                iconKey = list.iconKey,
                                count = list.todoCount,
                                isShared = list.isShared,
                                sharedByLabel = list.ownerUsername?.let {
                                    stringResource(R.string.members_shared_by, it)
                                },
                                onClick = {
                                    closeSearch()
                                    onOpenList(list.id, capitalizeFirstListLetter(list.name))
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

                    item { Spacer(Modifier.height(80.dp)) }
                        }
                    }

                    val searchBarRect = searchBarBounds
                    if (showSearchResultsOverlay && searchBarRect != null) {
                        val overlayLeft = with(density) { (searchBarRect.left - rootInRoot.x).toDp() }
                        val overlayTop = with(density) { (searchBarRect.bottom - rootInRoot.y).toDp() } + 8.dp
                        val overlayWidth = with(density) { searchBarRect.width.toDp() }
                        Card(
                            modifier = Modifier
                                .offset(x = overlayLeft, y = overlayTop)
                                .width(overlayWidth)
                                .zIndex(6f)
                                .onGloballyPositioned { coordinates ->
                                    searchResultsBounds = coordinates.boundsInRoot()
                                },
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.2f)),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            if (searchResults.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.scheduled_task_home_search_no_results),
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
                                    itemsIndexed(
                                        items = searchResults,
                                        key = { _, todo -> todo.id },
                                    ) { index, todo ->
                                        val listMeta = todo.listId?.let { listById[it] }
                                        val listTint = tdayListAccentColor(listMeta?.color)
                                        val listIcon = tdayListIconForKey(listMeta?.iconKey)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .semantics(mergeDescendants = true) {}
                                                .heightIn(min = 48.dp)
                                                .clickable {
                                                    TdayHaptics.buttonPress(view)
                                                    openTaskFromSearch(todo.id)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = listIcon,
                                                contentDescription = null,
                                                tint = listTint.copy(alpha = 0.92f),
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
                                                    text = todo.due?.let(dueFormatter::format)
                                                        .orEmpty(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                        if (shouldShowDateDivider(
                                                afterItemIndex = index,
                                                items = searchResults,
                                            )
                                        ) {
                                            Spacer(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .padding(horizontal = 12.dp)
                                                    .background(
                                                        colorScheme.outlineVariant.copy(
                                                            alpha = 0.45f
                                                        )
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    }
                }
            }
            RootFeedHeroHeader(
                title = stringResource(R.string.scheduled_task_home_title),
                mark = RootFeedHeroMark.TimeOfDay,
                collapseProgress = headerCollapseProgress,
                searchExpanded = searchExpanded,
                searchQuery = searchQuery,
                searchPlaceholder = stringResource(R.string.root_feed_search_scheduled),
                searchPlaceholderShort = stringResource(R.string.action_search),
                onSearchQueryChange = { searchQuery = it },
                onSearchExpandedChange = { searchExpanded = it },
                onSearchClose = closeSearch,
                onSearchBarBoundsChanged = { bounds ->
                    if (searchBarBounds != bounds) {
                        searchBarBounds = bounds
                    }
                },
                onCreateList = {
                    closeSearch()
                    showCreateList = true
                },
                onOpenSettings = {
                    closeSearch()
                    onOpenSettings()
                },
                onScrollToTop = {
                    searchResultScope.launch {
                        closeSearch()
                        listState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(padding)
                    .zIndex(6f),
                refreshIsRefreshing = refreshIsRefreshing,
                refreshPullFraction = { refreshPullFraction },
                    refreshWaveFrozen = refreshWaveFrozen,
            )

            if (showRootFeedDock && !searchExpanded) {
                RootFeedDock(
                    activeTab = RootFeedTab.SCHEDULED_TASK_HOME,
                    collapsed = dockCollapsed,
                    onTabSelected = { tab ->
                        when (tab) {
                            RootFeedTab.SCHEDULED_TASK_HOME -> {
                                searchResultScope.launch {
                                    closeSearch()
                                    listState.animateScrollToItem(index = 0, scrollOffset = 0)
                                }
                            }

                            RootFeedTab.FLOATER_TASK_HOME -> {
                                closeSearch()
                                onOpenFloater()
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(8f),
                )
            }
        }
    }

    if (showCreateTask) {
        CreateTaskBottomSheet(
            lists = uiState.summary.lists,
            defaultListId = null,
            onParseTaskTitleNlp = onParseTaskTitleNlp,
            onSuggestRepeat = onSuggestRepeat,
            onDismiss = { showCreateTask = false },
            onCreateTask = { payload ->
                onCreateTask(payload)
                showCreateTask = false
            },
        )
    }

    if (showSummarySheet) {
        ScheduledTaskHomeSummaryBottomSheet(
            isLoading = uiState.isSummarizing,
            summaryText = uiState.summaryText,
            summarySource = uiState.summarySource,
            aiSummaryConfigured = uiState.aiSummaryConfigured,
            errorMessage = if (summaryAvailable) {
                uiState.summaryError
            } else {
                stringResource(R.string.todos_summary_offline_unavailable)
            },
            onDismiss = { showSummarySheet = false },
        )
    }

    editTargetTodo?.let { todo ->
        CreateTaskBottomSheet(
            lists = uiState.summary.lists,
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

    if (showCreateList) {
        CreateListBottomSheet(
            listName = listName,
            onListNameChange = { listName = capitalizeFirstListLetter(it) },
            listColor = listColor,
            onListColorChange = { listColor = it },
            listIconKey = listIconKey,
            onListIconChange = { listIconKey = it },
            onDismiss = { showCreateList = false },
            onCreate = {
                val normalizedName = capitalizeFirstListLetter(listName).trim()
                if (normalizedName.isNotBlank()) {
                    onCreateList(normalizedName, listColor, listIconKey)
                    listName = ""
                    listColor = TDAY_DEFAULT_LIST_COLOR_KEY
                    listIconKey = TDAY_DEFAULT_LIST_ICON_KEY
                    showCreateList = false
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledTaskHomeSummaryBottomSheet(
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
                leftIcon = ImageVector.vectorResource(R.drawable.ic_lucide_x),
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
                TdaySheetCard(modifier = Modifier.fillMaxWidth()) {
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

private fun shouldShowDateDivider(
    afterItemIndex: Int,
    items: List<TodoItem>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val currentTodo = items.getOrNull(afterItemIndex) ?: return false
    val nextTodo = items.getOrNull(afterItemIndex + 1) ?: return false
    val currentDue = currentTodo.due ?: return false
    val nextDue = nextTodo.due ?: return false
    return LocalDate.ofInstant(currentDue, zoneId) != LocalDate.ofInstant(nextDue, zoneId)
}

@Composable
private fun CreateListBottomSheet(
    listName: String,
    onListNameChange: (String) -> Unit,
    listColor: String,
    onListColorChange: (String) -> Unit,
    listIconKey: String,
    onListIconChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    var nameFieldFocused by remember { mutableStateOf(false) }
    // The same two-step dismissal the create-task sheet uses: start the exit, and tell the
    // caller only once it has finished, so TdaySheetMotion's slide out is not cut off by
    // the host Dialog leaving the composition on the frame of the tap.
    //
    // The keyboard goes at the end of that, with the caller's onDismiss, for the same
    // reason it does over there: clearing focus first drops `useTypingHeight` below, which
    // retargets `sheetHeight` from 80 % of the screen down to 70 % in the middle of the
    // slide, and `slideOutVertically` offsets by the height it measured — so the card
    // shrinks while it is leaving instead of just leaving.
    val sheetDismiss = rememberSheetDismissState(
        onDismissed = {
            dismissKeyboard()
            onDismiss()
        },
    )
    val startDismiss = { sheetDismiss.start() }
    val colorScheme = MaterialTheme.colorScheme
    val selectedAccent = tdayListAccentColor(listColor)
    val canCreate = listName.isNotBlank()
    val selectedIcon = tdayListIconForKey(listIconKey)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val useTypingHeight = nameFieldFocused && keyboardVisible
    val maxSheetHeight = screenHeight * CREATE_LIST_SHEET_MAX_HEIGHT_FRACTION
    val sheetHeight by animateDpAsState(
        targetValue = (screenHeight * if (useTypingHeight) {
            CREATE_LIST_SHEET_KEYBOARD_HEIGHT_FRACTION
        } else {
            CREATE_LIST_SHEET_NORMAL_HEIGHT_FRACTION
        }).coerceAtMost(maxSheetHeight),
        // Emphasis rather than TdaySheetMotion.cardIn(): this is the card changing height
        // under a keyboard, not the card arriving. Same rung today — a size change is
        // geometry either way — but keeping it off the card's spec means a retime of the
        // arrival cannot silently retime the keyboard climb as well.
        animationSpec = tween(
            durationMillis = TdayMotionTokens.Durations.Emphasis,
            easing = FastOutSlowInEasing,
        ),
        label = "createListSheetHeight",
    )
    val sheetContainerColor = TdaySheetDefaults.containerColor()
    val sheetScrimColor = TdaySheetDefaults.scrimColor()
    val sheetTonalElevation = TdaySheetDefaults.tonalElevation()

    Dialog(
        onDismissRequest = startDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        TdaySheetFullBleedWindow()

        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // Same chrome as the create-task sheet, and now the same four specs: the scrim
            // fades with the card instead of being drawn and undrawn with the Dialog
            // window. `visible` and not `visibleState` — see SheetDismissState.visible.
            AnimatedVisibility(
                visible = sheetDismiss.visible,
                enter = fadeIn(animationSpec = TdaySheetMotion.scrimIn()),
                exit = fadeOut(animationSpec = TdaySheetMotion.scrimOut()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(sheetScrimColor)
                        // No indication: a dismiss tap on the scrim is a gesture at the
                        // sheet, not a press of a full-screen button, and the default
                        // ripple draws itself across the entire window on the way out.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = startDismiss,
                        ),
                )
            }

            AnimatedVisibility(
                visibleState = sheetDismiss.transition,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = slideInVertically(
                    animationSpec = TdaySheetMotion.cardIn(),
                    initialOffsetY = { fullHeight -> fullHeight },
                ) + fadeIn(animationSpec = TdaySheetMotion.cardIn()),
                exit = slideOutVertically(
                    animationSpec = TdaySheetMotion.cardOut(),
                    targetOffsetY = { fullHeight -> fullHeight },
                ) + fadeOut(animationSpec = TdaySheetMotion.cardOut()),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                    shape = TdaySheetDefaults.TopShape,
                    color = sheetContainerColor,
                    tonalElevation = sheetTonalElevation,
                ) {
                    TdayCenteredSheetContent {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            TdaySheetHeader(
                                title = stringResource(R.string.scheduled_task_home_new_list),
                                leftIcon = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                                leftContentDescription = stringResource(R.string.action_close),
                                onLeftClick = startDismiss,
                                confirmContentDescription = stringResource(R.string.action_create_list),
                            onConfirm = {
                                dismissKeyboard()
                                if (canCreate) onCreate()
                            },
                            confirmEnabled = canCreate,
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
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(selectedAccent),
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
                                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                                        color = selectedAccent,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { nameFieldFocused = it.isFocused },
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(TdaySheetDefaults.controlSurfaceColor())
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (listName.isBlank()) {
                                                Text(
                                                    text = stringResource(R.string.scheduled_task_home_list_name_placeholder),
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                                    fontWeight = FontWeight.ExtraBold,
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                )
                            }
                        }

                            TdaySheetSectionTitle(stringResource(R.string.scheduled_task_home_section_color))
                            TdaySheetCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TdayListColorOptions.forEach { option ->
                                    val selected = listColor == option.key
                                    val interactionSource = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(option.color)
                                            .border(
                                                width = if (selected) 3.dp else 0.dp,
                                                color = if (selected) colorScheme.onBackground.copy(
                                                    alpha = 0.32f
                                                ) else Color.Transparent,
                                                shape = CircleShape,
                                            )
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = ripple(
                                                    bounded = true,
                                                    radius = 24.dp,
                                                ),
                                            ) {
                                                TdayHaptics.selection(view)
                                                onListColorChange(option.key)
                                            },
                                    )
                                }
                            }
                        }

                            TdaySheetSectionTitle(stringResource(R.string.scheduled_task_home_section_icon))
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
                                    val iconOptionDescription =
                                        stringResource(R.string.scheduled_task_home_list_icon_option, option.key)
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) {
                                                    selectedAccent.copy(alpha = 0.2f)
                                                } else {
                                                    TdaySheetDefaults.controlSurfaceColor()
                                                },
                                            )
                                            .border(
                                                width = if (selected) 2.dp else 0.dp,
                                                color = if (selected) selectedAccent.copy(alpha = 0.55f) else Color.Transparent,
                                                shape = CircleShape,
                                            )
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = ripple(
                                                    bounded = true,
                                                    radius = 24.dp,
                                                ),
                                            ) {
                                                TdayHaptics.selection(view)
                                                onListIconChange(option.key)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(option.iconRes),
                                            contentDescription = iconOptionDescription,
                                            tint = if (selected) selectedAccent else colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTaskButton(
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val fabBlue = Color(0xFF6EA8E1)
    val fabBlueBorder = Color(0xFF3D7FEA).copy(alpha = 0.58f)

    Card(
        modifier = modifier,
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        border = BorderStroke(1.dp, fabBlueBorder),
        colors = CardDefaults.cardColors(containerColor = fabBlue),
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
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun rememberIsDaytime(): Boolean {
    val hour = remember { mutableIntStateOf(LocalTime.now().hour) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            val millisToNextMinute = ((60 - now.second) * 1000L) - (now.nano / 1_000_000L)
            delay(millisToNextMinute.coerceAtLeast(500L))
            hour.intValue = LocalTime.now().hour
        }
    }

    return hour.intValue in 6 until 18
}

@Composable
private fun MyListsHeader(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
         Text(
            text = stringResource(R.string.scheduled_task_home_my_lists),
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun PressableIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    tint: Color,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        label = "scheduledTaskHomeIconButtonScale",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        label = "scheduledTaskHomeIconButtonOffsetY",
    )
    val buttonSize = if (compact) 30.dp else TdayDimens.FabSize
    val defaultElevation = if (compact) 0.dp else TdayDimens.FabElevation
    val pressedElevation = if (compact) 0.dp else TdayDimens.FabPressedElevation

    Card(
        modifier = Modifier
            .size(buttonSize)
            .offset(y = offsetY)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = if (compact) RoundedCornerShape(999.dp) else CircleShape,
        border = if (compact) null else BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.34f)),
        colors = CardDefaults.cardColors(containerColor = if (compact) Color.Transparent else colorScheme.background),
        elevation = CardDefaults.cardElevation(
            defaultElevation = defaultElevation,
            pressedElevation = pressedElevation,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(if (compact) 24.dp else 22.dp),
            )
        }
    }
}

/**
 * The check-off's beats, the same four every task row in every client plays:
 * the tick lands, the rule crosses the task, the ink leaves, the row is handed
 * to the list. Named here rather than left as three `delay` literals so that
 * this row can be read against `TASK_COMPLETION_*_MS` in `TodoListScreen.kt`,
 * `CALENDAR_TASK_COMPLETION_*_MS` in `CalendarScreen.kt` and
 * `taskCompletionTiming.ts` on web without anybody counting milliseconds.
 *
 * The first two are gaps and not motions — nobody watches the wait between the
 * tick and the strike — which is why they are plain numbers while the third,
 * which is the length of a fade somebody does watch, reads its rung instead.
 */
private const val SCHEDULED_TASK_COMPLETION_CHECK_TO_STRIKE_MS = 160L
private const val SCHEDULED_TASK_COMPLETION_STRIKE_TO_FADE_MS = 360L
private val SCHEDULED_TASK_COMPLETION_FADE_MS = TdayMotionTokens.Durations.Change.toLong()

private val SCHEDULED_TASK_HOME_TODAY_DUE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(ZoneId.systemDefault())
private val SCHEDULED_TASK_HOME_TODAY_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).withZone(ZoneId.systemDefault())

@Composable
private fun ScheduledTaskHomeTodayCard(
    count: Int,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "todayCardScale"
    )
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        label = "todayCardOffsetY"
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 9.dp,
        label = "todayCardElevation"
    )
    val dateLabel = remember { SCHEDULED_TASK_HOME_TODAY_DATE_FORMATTER.format(Instant.now()) }
    val color = Color(0xFF6EA8E1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .offset(y = animatedOffsetY)
            .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale },
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(
            defaultElevation = animatedElevation,
            pressedElevation = animatedElevation
        ),
        shape = RoundedCornerShape(26.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val glow = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.22f, size.height * 0.2f),
                        radius = size.width * 0.72f,
                    )
                    val pearl = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.75f),
                        radius = size.width * 0.55f,
                    )
                    onDrawWithContent { drawRect(glow); drawRect(pearl); drawContent() }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dateLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontFamily = TdayFontFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 28.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontFamily = TdayFontFamily,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 40.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduledTaskHomeTodayTaskRow(
    modifier: Modifier = Modifier,
    todo: TodoItem,
    lists: List<ListSummary>,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    openSwipeTaskId: String?,
    onOpenSwipeTaskIdChange: (String?) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val taskCompletionSound = rememberTaskCompletionSound()
    val coroutineScope = rememberCoroutineScope()
    // Edit + Copy + Delete: matches the 3-pill width used elsewhere (see
    // SwipeTaskRow.revealWidth).
    val swipeRevealState = rememberTaskSwipeRevealState(todo.id, revealWidth = 256.dp)
    val clipboardManager = LocalClipboardManager.current
    val snackbarManager = LocalSnackbarManager.current
    val copyContext = LocalContext.current
    val copiedMessage = stringResource(R.string.task_copied_toast)
    val copyFailedMessage = stringResource(R.string.task_copy_failed_toast)
    var localChecked by remember(todo.id) { mutableStateOf(false) }
    var localStruck by remember(todo.id) { mutableStateOf(false) }
    var pendingCompletion by remember(todo.id) { mutableStateOf(false) }
    var completionFading by remember(todo.id) { mutableStateOf(false) }
    var titleLayoutResult by remember(todo.id) { mutableStateOf<TextLayoutResult?>(null) }
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
    val animatedOffsetX by animateTaskSwipeOffsetAsState(
        state = swipeRevealState,
        label = "scheduledTaskHomeTodaySwipeOffset",
    )
    // The beats this row cut straight to. The tint answers the finger, so it is
    // Quick; the title colour travels with the rule crossing it, so it is Emphasis
    // and not a rung of its own; the fade below carries the row off.
    val motionEnabled = rememberTdayMotionEnabled()
    // Gated like the beats in front of it. The last leg of the check-off is timed
    // against this fade, so a fade still running while its own wait had been zeroed
    // would pull the row out of the list at full opacity — exactly the pop that leg
    // exists to prevent.
    val completionAlpha by animateFloatAsState(
        targetValue = if (completionFading) 0f else 1f,
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = SCHEDULED_TASK_COMPLETION_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "scheduledTaskHomeTodayCompletionAlpha",
    )
    val completionOffsetY by animateDpAsState(
        targetValue = if (completionFading) (-10).dp else 0.dp,
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = SCHEDULED_TASK_COMPLETION_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "scheduledTaskHomeTodayCompletionOffsetY",
    )
    val titleStrikeProgress =
        rememberTaskStrikeProgress(localStruck, "scheduledTaskHomeTodayTitleStrike")
    // The number behind that switch, for this row's waits rather than its specs:
    // the hint's two holds and the three legs of the check-off are gaps between
    // beats this row gates on [motionEnabled], which is what makes the app's own
    // scale the right clock for them. See [scaledDelay].
    val rowMotionScale = rememberTdayMotionScale()
    val toggleTint by animateColorAsState(
        targetValue = if (localChecked) {
            TdayTaskCompleteAccent
        } else {
            colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        },
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Quick,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "scheduledTaskHomeTodayToggleTint",
    )
    val titleColor by animateColorAsState(
        targetValue = if (localStruck) {
            colorScheme.onSurface.copy(alpha = 0.78f)
        } else {
            colorScheme.onSurface
        },
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Emphasis,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "scheduledTaskHomeTodayTitleColor",
    )
    var noteLayoutResult by remember(todo.id) { mutableStateOf<TextLayoutResult?>(null) }
    val actionRevealProgress = swipeRevealState.revealProgress(animatedOffsetX)
    val dueText = todo.due?.let(SCHEDULED_TASK_HOME_TODAY_DUE_FORMATTER::format)
    val rowShape = RoundedCornerShape(16.dp)
    val listMeta = todo.listId?.let { listId -> lists.firstOrNull { it.id == listId } }
    val listIndicatorColor = tdayListAccentColor(listMeta?.color)
    val priorityIcon = priorityIconFor(todo.priority)
    val isOverdue = !todo.completed && todo.due?.isBefore(Instant.now()) == true
    val subtitleColor =
        if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = 0.8f
        )
    val subtitleText = dueText?.let { text ->
        if (isOverdue) {
            stringResource(R.string.todos_due_overdue_text, text)
        } else {
            stringResource(R.string.todos_due_text, text)
        }
    }
    LaunchedEffect(openSwipeTaskId, todo.id) {
        if (openSwipeTaskId != null && openSwipeTaskId != todo.id && swipeRevealState.isOpenOrDragging) {
            swipeRevealState.close()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = completionAlpha
                translationY = completionOffsetY.toPx()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .height(IntrinsicSize.Min),
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
                        TdayHaptics.buttonPress(view)
                        closeSwipeSlot()
                        onEdit()
                    },
                )
                TaskSwipeActionButton(
                    icon = R.drawable.ic_lucide_copy,
                    contentDescription = stringResource(R.string.action_copy_task),
                    label = stringResource(R.string.action_copy),
                    tint = Color.White,
                    background = TdaySwipeCopyBackground,
                    revealProgress = actionRevealProgress,
                    revealDelay = 0.40f,
                    onClick = {
                        TdayHaptics.buttonPress(view)
                        closeSwipeSlot()
                        runCatching {
                            clipboardManager.setText(AnnotatedString(taskCopyText(copyContext, todo)))
                        }.onSuccess {
                            snackbarManager?.showSuccess(copiedMessage)
                        }.onFailure {
                            snackbarManager?.showError(copyFailedMessage)
                        }
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
                        TdayHaptics.destructive(view)
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
                        } else if (!swipeRevealState.isHinting && !pendingCompletion) {
                            claimSwipeSlot()
                            coroutineScope.launch {
                                swipeRevealState.playHint(rowMotionScale)
                                if (latestOpenSwipeTaskId.value == todo.id && !swipeRevealState.isOpenOrDragging) {
                                    onOpenSwipeTaskIdChange(null)
                                }
                            }
                        }
                    },
                shape = rowShape,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .wrapContentSize(Alignment.Center)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, radius = 24.dp),
                                enabled = !pendingCompletion,
                            ) {
                                if (!pendingCompletion) {
                                    taskCompletionSound.play()
                                    closeSwipeSlot()
                                    localChecked = true
                                    TdayHaptics.completion(view)
                                    pendingCompletion = true
                                    coroutineScope.launch {
                                        scaledDelay(
                                            SCHEDULED_TASK_COMPLETION_CHECK_TO_STRIKE_MS,
                                            rowMotionScale,
                                        )
                                        localStruck = true
                                        scaledDelay(
                                            SCHEDULED_TASK_COMPLETION_STRIKE_TO_FADE_MS,
                                            rowMotionScale,
                                        )
                                        completionFading = true
                                        scaledDelay(
                                            SCHEDULED_TASK_COMPLETION_FADE_MS,
                                            rowMotionScale,
                                        )
                                        onComplete()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val toggleGlyph = if (localChecked) {
                            ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big)
                        } else {
                            ImageVector.vectorResource(R.drawable.ic_lucide_circle)
                        }
                        val toggleLabel = if (localChecked) {
                            stringResource(R.string.label_completed)
                        } else {
                            stringResource(R.string.label_mark_complete)
                        }
                        // Crossed over rather than swapped, the same as the task list's
                        // and the calendar's toggles: the tint above is only half of the
                        // t=0 beat, and a glyph that hard-cuts underneath a tint that
                        // travels is the control answering the finger twice.
                        Crossfade(
                            targetState = toggleGlyph,
                            animationSpec = if (motionEnabled) {
                                tween(
                                    durationMillis = TdayMotionTokens.Durations.Quick,
                                    easing = TdayMotionTokens.Easings.Standard,
                                )
                            } else {
                                snap()
                            },
                            label = "scheduledTaskHomeTodayToggleGlyph",
                        ) { glyph ->
                            Icon(
                                imageVector = glyph,
                                // Only the glyph being crossed TO carries the description:
                                // for the frames both exist, two labels in the tree would
                                // have TalkBack announce the control twice.
                                contentDescription = toggleLabel.takeIf { glyph == toggleGlyph },
                                tint = toggleTint,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = todo.title,
                            // The rule this row already swept, moved into the modifier
                            // every task row now shares: the sweep was right and the
                            // copy of it in each screen was the problem.
                            modifier = Modifier.taskStrikethrough(
                                progress = titleStrikeProgress,
                                layout = titleLayoutResult,
                                color = colorScheme.onSurface.copy(alpha = 0.65f),
                                thickness = TdayDimens.BorderWidthThick,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = TdayFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 22.sp,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { titleLayoutResult = it },
                        )
                        subtitleText?.let { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = TdayFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 18.sp,
                                color = subtitleColor,
                            )
                        }
                        flattenNotesToPlainText(todo.description).takeIf { it.isNotBlank() }?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = TdayFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 18.sp,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                // Struck alongside the title, on the title's own sweep, so
                                // the whole task reads as one edit rather than as a rule
                                // that grows and a rule that appears.
                                modifier = Modifier.taskStrikethrough(
                                    progress = titleStrikeProgress,
                                    layout = noteLayoutResult,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    thickness = TdayDimens.BorderWidthThick,
                                ),
                                onTextLayout = { noteLayoutResult = it },
                            )
                        }
                    }

                    if (listMeta != null || priorityIcon != null) {
                        Row(
                            modifier = Modifier.padding(end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (listMeta != null) {
                                Icon(
                                    imageVector = tdayListIconForKey(listMeta.iconKey),
                                    contentDescription = null,
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
    }
}

@Composable
private fun CategoryGrid(
    overdueCount: Int,
    scheduledCount: Int,
    allCount: Int,
    priorityCount: Int,
    completedCount: Int,
    calendarCount: Int,
    onOpenOverdue: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenPriority: () -> Unit,
    onOpenCompleted: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val completedColor = completedTileColor(colorScheme)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFFD98F4B),
                iconRes = R.drawable.ic_lucide_calendar_clock,
                watermarkRes = R.drawable.ic_lucide_calendar_clock,
                title = stringResource(R.string.scheduled_task_home_category_scheduled),
                count = scheduledCount,
                onClick = onOpenScheduled,
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFFC97880),
                iconRes = R.drawable.ic_lucide_flag,
                watermarkRes = R.drawable.ic_lucide_flag,
                title = stringResource(R.string.scheduled_task_home_category_priority),
                count = priorityCount,
                onClick = onOpenPriority,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFFE06F66),
                iconRes = R.drawable.ic_lucide_clock_3,
                watermarkRes = R.drawable.ic_lucide_clock_3,
                title = stringResource(R.string.scheduled_task_home_category_overdue),
                count = overdueCount,
                onClick = onOpenOverdue,
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFF68717A),
                iconRes = R.drawable.ic_lucide_layers,
                watermarkRes = R.drawable.ic_lucide_layers,
                title = stringResource(R.string.scheduled_task_home_category_all),
                count = allCount,
                onClick = onOpenAll,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = completedColor,
                iconRes = R.drawable.ic_lucide_circle_check_big,
                watermarkRes = R.drawable.ic_lucide_circle_check_big,
                title = stringResource(R.string.scheduled_task_home_category_completed),
                count = completedCount,
                onClick = onOpenCompleted,
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = calendarTileColor(colorScheme),
                iconRes = R.drawable.ic_lucide_calendar_1,
                watermarkRes = R.drawable.ic_lucide_calendar_1,
                title = stringResource(R.string.scheduled_task_home_category_calendar),
                count = calendarCount,
                onClick = onOpenCalendar,
            )
        }
    }
}

private fun completedTileColor(colorScheme: ColorScheme): Color = TdayCompletedTileAccent

private fun calendarTileColor(colorScheme: ColorScheme): Color {
    return Color(0xFF9A89D2)
}

@Composable
private fun ListRow(
    modifier: Modifier = Modifier,
    name: String,
    colorKey: String?,
    iconKey: String?,
    count: Int,
    isShared: Boolean = false,
    sharedByLabel: String? = null,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "listRowScale",
    )
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        label = "listRowOffsetY",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 8.dp,
        label = "listRowElevation",
    )
    val animatedCount by animateIntAsState(
        targetValue = count,
        animationSpec = tween(durationMillis = 220),
        label = "listRowCount",
    )
    val accent = tdayListAccentColor(colorKey)
    val icon = tdayListIconForKey(iconKey)
    val containerColor = lerp(colorScheme.surfaceVariant, accent, SCHEDULED_TASK_HOME_LIST_CONTAINER_COLOR_WEIGHT)
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
            TdayHaptics.buttonPress(view)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val iconSideGlow = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(
                            x = size.width * 0.22f,
                            y = size.height * 0.2f,
                        ),
                        radius = size.maxDimension * 0.9f,
                    )
                    val pearlWash = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color(0xFFE7F3FF).copy(alpha = 0.1f),
                            Color(0xFFFFF2FA).copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        start = Offset(
                            x = size.width * 0.05f,
                            y = size.height * 0.04f,
                        ),
                        end = Offset(
                            x = size.width * 0.9f,
                            y = size.height * 0.75f,
                        ),
                    )
                    onDrawWithContent {
                        drawRect(iconSideGlow)
                        drawRect(pearlWash)
                        drawContent()
                    }
                },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = lerp(containerColor, Color.White, 0.34f).copy(alpha = 0.42f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 14.dp, y = 8.dp)
                    .size(82.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isShared || sharedByLabel != null) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_users_round),
                                    contentDescription = stringResource(R.string.members_title),
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(16.dp),
                                )
                            }
                        }
                        sharedByLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Text(
                    text = animatedCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

/**
 * This feed's item motion, in one place so a row and everything its departure
 * moves travel together.
 *
 * The Today rows already left on this spring; the tiles and list rows under them
 * had no keys, so every completion re-keyed them by index and they jumped a row
 * height in a single frame while the row above was still gliding away. Same
 * defect as the floater home's empty-state snap, an order of magnitude smaller —
 * this screen never draws an empty scene, so there is no gap to open.
 */
private val ScheduledTaskHomeItemFadeIn =
    tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
private val ScheduledTaskHomeItemPlacement = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private val ScheduledTaskHomeItemFadeOut =
    tween<Float>(durationMillis = 140, easing = FastOutSlowInEasing)

/**
 * The same spring, for a block a completion *moves* but never adds or removes.
 *
 * Placement only. The grid and the list rows are on screen before the row above
 * them is ticked and still on screen after, so a fade describes nothing here —
 * and it would fire where it has no business firing: a live search query
 * replaces this whole feed body at once, and a block with a fade spec fades out
 * and back in every time the field opens or closes.
 */
private fun LazyItemScope.scheduledTaskHomeDisplacedItemMotion(): Modifier =
    Modifier.animateItem(
        fadeInSpec = null,
        placementSpec = ScheduledTaskHomeItemPlacement,
        fadeOutSpec = null,
    )

private const val SCHEDULED_TASK_HOME_LIST_CONTAINER_COLOR_WEIGHT = 0.66f
private const val CREATE_LIST_SHEET_MAX_HEIGHT_FRACTION = 0.80f
private const val CREATE_LIST_SHEET_NORMAL_HEIGHT_FRACTION = 0.70f
private const val CREATE_LIST_SHEET_KEYBOARD_HEIGHT_FRACTION = 0.80f

/**
 * How long the search surface is left standing after a result is tapped — not a
 * token — see docs/motion.md. It equals Change by arithmetic and not by argument:
 * what it is timed against is the navigation leaving this screen, not a rung.
 */
private const val SEARCH_RESULT_SEARCH_CLOSE_DELAY_MS = 260L
private val RootFeedDockCollapseThreshold = 44.dp

@Composable
private fun priorityIconFor(priority: String): ImageVector? {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "medium" -> ImageVector.vectorResource(R.drawable.ic_lucide_flag_filled)
        "high", "urgent", "important" -> ImageVector.vectorResource(R.drawable.ic_lucide_flag_filled)
        else -> null
    }
}
