package com.ohmz.tday.compose.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
import com.ohmz.tday.compose.core.ui.TaskSwipeActionButton
import com.ohmz.tday.compose.core.ui.animateTaskSwipeOffsetAsState
import com.ohmz.tday.compose.core.ui.rememberTaskSwipeRevealState
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.component.RootFeedDock
import com.ohmz.tday.compose.ui.component.RootFeedTab
import com.ohmz.tday.compose.ui.component.TdayCenteredSheetContent
import com.ohmz.tday.compose.ui.component.TdayModalBottomSheet
import com.ohmz.tday.compose.ui.component.TdayPullToRefreshBox
import com.ohmz.tday.compose.ui.component.TdaySheetCard
import com.ohmz.tday.compose.ui.component.TdaySheetDefaults
import com.ohmz.tday.compose.ui.component.TdaySheetHeader
import com.ohmz.tday.compose.ui.component.TdaySheetSectionTitle
import com.ohmz.tday.compose.ui.theme.TDAY_DEFAULT_LIST_COLOR_KEY
import com.ohmz.tday.compose.ui.theme.TDAY_DEFAULT_LIST_ICON_KEY
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayFontFamily
import com.ohmz.tday.compose.ui.theme.TdayListColorOptions
import com.ohmz.tday.compose.ui.theme.TdayListIconOptions
import com.ohmz.tday.compose.ui.theme.TdaySwipeDeleteBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeEditBackground
import com.ohmz.tday.compose.ui.theme.TdayTaskCompleteAccent
import com.ohmz.tday.compose.ui.theme.TdayTitleIconDayAccent
import com.ohmz.tday.compose.ui.theme.TdayTitleIconNightAccent
import com.ohmz.tday.compose.ui.theme.tdayListAccentColor
import com.ohmz.tday.compose.ui.theme.tdayListIconForKey
import com.ohmz.tday.compose.ui.theme.tdayPriorityColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
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
    scrollToTopRequestKey: Int = 0,
    onRootDockCollapsedChange: (Boolean) -> Unit = {},
    onRootControlsVisibleChange: (Boolean) -> Unit = {},
) {
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
    var lastHandledCreateTaskRequestKey by rememberSaveable {
        mutableIntStateOf(createTaskRequestKey)
    }
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
    val openTaskFromSearch: (String) -> Unit = openTask@{ todoId ->
        if (searchResultOpening) return@openTask
        searchResultOpening = true
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onOpenTaskFromSearch(todoId)
        searchResultScope.launch {
            delay(SEARCH_RESULT_SEARCH_CLOSE_DELAY_MS)
            closeSearch()
        }
    }
    BackHandler(enabled = searchExpanded) {
        closeSearch()
    }
    LaunchedEffect(createTaskRequestKey) {
        if (createTaskRequestKey > lastHandledCreateTaskRequestKey) {
            lastHandledCreateTaskRequestKey = createTaskRequestKey
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
    val overdueCount = remember(uiState.searchableTodos) {
        val now = Instant.now()
        uiState.searchableTodos.count { todo -> todo.due?.isBefore(now) == true }
    }
    val dueFormatter = remember {
        DateTimeFormatter.ofPattern("EEE h:mm a")
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
                        (todo.description?.lowercase(Locale.getDefault())?.contains(normalizedSearchQuery) == true) ||
                        (todo.listId?.let { listById[it]?.name }?.lowercase(Locale.getDefault())
                            ?.contains(normalizedSearchQuery) == true)
                }
                .sortedBy { it.due ?: Instant.MAX }
                .take(20)
                .toList()
        }
    }
    val showSearchResultsOverlay = searchExpanded && searchQuery.isNotBlank()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val hasScrollableContent =
        listState.canScrollForward || listState.canScrollBackward
    val dockCollapseThresholdPx = with(density) { RootFeedDockCollapseThreshold.roundToPx() }
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
    LaunchedEffect(listState.isScrollInProgress, searchExpanded) {
        if (searchExpanded || listState.isScrollInProgress) return@LaunchedEffect
        // Snap only when top header row is partially visible.
        // If fully off-screen (index > 0), do not force any anchor behavior.
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 0) {
            listState.animateScrollBy(
                value = -listState.firstVisibleItemScrollOffset.toFloat(),
                animationSpec = tween(
                    durationMillis = 260,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

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
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                TdayPullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    enabled = pullRefreshEnabled,
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
                                                val tappedSearchBar =
                                                    searchBarBounds?.contains(tapInRoot) == true
                                                val tappedSearchResults =
                                                    searchResultsBounds?.contains(tapInRoot) == true
                                                val up =
                                                    waitForUpOrCancellation(pass = PointerEventPass.Final)
                                                if (up != null && !tappedSearchBar && !tappedSearchResults) {
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
                        contentPadding = PaddingValues(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                    item {
                        TopSearchBar(
                            searchExpanded = searchExpanded,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearchExpandedChange = { searchExpanded = it },
                            onSearchClose = closeSearch,
                            onSearchBarBoundsChanged = { bounds ->
                                if (searchExpanded && searchBarBounds != bounds) {
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
                        )
                    }
                        item {
                            HomeTodayCard(
                                count = uiState.summary.todayCount,
                                onClick = {
                                    closeSearch()
                                    onOpenToday()
                                },
                            )
                        }

                        itemsIndexed(
                            items = uiState.todayTodos,
                            key = { _, todo -> "home-today-${todo.id}" },
                            contentType = { _, _ -> "home_today_task" },
                        ) { _, todo ->
                            HomeTodayTaskRow(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(
                                        durationMillis = 180,
                                        easing = FastOutSlowInEasing,
                                    ),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                    fadeOutSpec = tween(
                                        durationMillis = 140,
                                        easing = FastOutSlowInEasing,
                                    ),
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

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        item {
                            MyListsHeader()
                        }
                        itemsIndexed(
                            items = uiState.summary.lists,
                            key = { _, list -> list.id },
                            contentType = { _, _ -> "list_row" },
                        ) { _, list ->
                            ListRow(
                                name = list.name,
                                colorKey = list.color,
                                iconKey = list.iconKey,
                                count = list.todoCount,
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
            if (showRootFeedDock && !searchExpanded) {
                RootFeedDock(
                    activeTab = RootFeedTab.HOME,
                    collapsed = dockCollapsed,
                    onTabSelected = { tab ->
                        when (tab) {
                            RootFeedTab.HOME -> {
                                searchResultScope.launch {
                                    closeSearch()
                                    listState.animateScrollToItem(index = 0, scrollOffset = 0)
                                }
                            }

                            RootFeedTab.FLOATER -> {
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
            onDismiss = { showCreateTask = false },
            onCreateTask = { payload ->
                onCreateTask(payload)
                showCreateTask = false
            },
        )
    }

    if (showSummarySheet) {
        HomeSummaryBottomSheet(
            isLoading = uiState.isSummarizing,
            summaryText = uiState.summaryText,
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
private fun HomeSummaryBottomSheet(
    isLoading: Boolean,
    summaryText: String?,
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
                TdaySheetCard(modifier = Modifier.fillMaxWidth()) {
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    val focusRequester = remember { FocusRequester() }
    var nameFieldFocused by remember { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }
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
        animationSpec = tween(
            durationMillis = CREATE_LIST_SHEET_MOTION_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "createListSheetHeight",
    )
    val sheetContainerColor = TdaySheetDefaults.containerColor()
    val sheetScrimColor = TdaySheetDefaults.scrimColor()
    val sheetTonalElevation = TdaySheetDefaults.tonalElevation()

    LaunchedEffect(Unit) {
        sheetVisible = true
        delay(500)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = {
            dismissKeyboard()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sheetScrimColor)
                    .clickable {
                        dismissKeyboard()
                        onDismiss()
                    },
            )

            AnimatedVisibility(
                visible = sheetVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = slideInVertically(
                    animationSpec = tween(
                        durationMillis = CREATE_LIST_SHEET_MOTION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                    initialOffsetY = { fullHeight -> fullHeight },
                ) + fadeIn(animationSpec = tween(durationMillis = CREATE_LIST_SHEET_MOTION_MS)),
                exit = slideOutVertically(
                    animationSpec = tween(
                        durationMillis = CREATE_LIST_SHEET_MOTION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                    targetOffsetY = { fullHeight -> fullHeight },
                ) + fadeOut(animationSpec = tween(durationMillis = CREATE_LIST_SHEET_MOTION_MS)),
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
                                title = stringResource(R.string.home_new_list),
                                leftIcon = Icons.Rounded.Close,
                                leftContentDescription = stringResource(R.string.action_close),
                                onLeftClick = {
                                dismissKeyboard()
                                onDismiss()
                            },
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
                                        .focusRequester(focusRequester)
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
                                                    text = stringResource(R.string.home_list_name_placeholder),
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

                            TdaySheetSectionTitle(stringResource(R.string.home_section_color))
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
                                            ) { onListColorChange(option.key) },
                                    )
                                }
                            }
                        }

                            TdaySheetSectionTitle(stringResource(R.string.home_section_icon))
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
                                        stringResource(R.string.home_list_icon_option, option.key)
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
                                            ) { onListIconChange(option.key) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = option.icon,
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
            performGentleHaptic(view)
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
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.action_create_task),
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun TopSearchBar(
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchClose: () -> Unit,
    onSearchBarBoundsChanged: (Rect) -> Unit,
    onCreateList: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val isDaytime = rememberIsDaytime()
    val homeTitleIcon = if (isDaytime) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    val homeTitleIconTint = if (isDaytime) TdayTitleIconDayAccent else TdayTitleIconNightAccent

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

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val buttonSize = TdayDimens.FabSize
        val buttonGap = 8.dp
        val actionCount = 2
        val collapsedSearchWidth = buttonSize
        val expandedSearchWidth = maxWidth.coerceAtLeast(buttonSize)
        val collapsedSearchOffset = -((buttonSize * actionCount) + (buttonGap * actionCount))
        val animatedSearchWidth by animateDpAsState(
            targetValue = if (searchExpanded) expandedSearchWidth else collapsedSearchWidth,
            label = "topSearchBarSearchWidth",
        )
        val animatedSearchOffset by animateDpAsState(
            targetValue = if (searchExpanded) 0.dp else collapsedSearchOffset,
            label = "topSearchBarSearchOffset",
        )
        val actionsAlpha by animateFloatAsState(
            targetValue = if (searchExpanded) 0f else 1f,
            label = "topSearchBarActionsAlpha",
        )
        val searchContentAlpha by animateFloatAsState(
            targetValue = if (searchExpanded) 1f else 0f,
            label = "topSearchBarContentAlpha",
        )

        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .graphicsLayer { alpha = actionsAlpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = homeTitleIcon,
                contentDescription = null,
                tint = homeTitleIconTint,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
                color = colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { alpha = actionsAlpha },
            horizontalArrangement = Arrangement.spacedBy(buttonGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressableIconButton(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = stringResource(R.string.action_create_list),
                tint = colorScheme.onSurface,
                onClick = onCreateList,
            )
            PressableIconButton(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.action_more),
                tint = colorScheme.onSurface,
                onClick = onOpenSettings,
            )
        }

        Card(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = animatedSearchOffset)
                .width(animatedSearchWidth)
                .height(buttonSize)
                .zIndex(2f)
                .then(
                    if (searchExpanded) {
                        Modifier.onGloballyPositioned { coordinates ->
                            onSearchBarBoundsChanged(coordinates.boundsInRoot())
                        }
                    } else {
                        Modifier
                    }
                ),
            onClick = {
                if (!searchExpanded) {
                    onSearchExpandedChange(true)
                }
            },
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.26f)),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - searchContentAlpha },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.action_search),
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = searchContentAlpha }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                    BasicTextField(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        enabled = searchExpanded,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        cursorBrush = SolidColor(colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (searchQuery.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.home_search_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    PressableIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close_search),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        compact = true,
                        onClick = onSearchClose,
                    )
                }
            }
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
private fun MyListsHeader() {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
         Text(
            text = stringResource(R.string.home_my_lists),
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun PressableIconButton(
    icon: ImageVector,
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
        label = "homeIconButtonScale",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        label = "homeIconButtonOffsetY",
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
            performGentleHaptic(view)
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
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(if (compact) 24.dp else 22.dp),
            )
        }
    }
}

private val HOME_TODAY_DUE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
private val HOME_TODAY_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d").withZone(ZoneId.systemDefault())

@Composable
private fun HomeTodayCard(
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
    val dateLabel = remember { HOME_TODAY_DATE_FORMATTER.format(Instant.now()) }
    val color = Color(0xFF6EA8E1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .offset(y = animatedOffsetY)
            .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale },
        onClick = {
            performGentleHaptic(view)
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
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.08f),
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
private fun HomeTodayTaskRow(
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
    val coroutineScope = rememberCoroutineScope()
    val swipeRevealState = rememberTaskSwipeRevealState(todo.id)
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
        label = "homeTodaySwipeOffset",
    )
    val completionAlpha by animateFloatAsState(
        targetValue = if (completionFading) 0f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "homeTodayCompletionAlpha",
    )
    val completionOffsetY by animateDpAsState(
        targetValue = if (completionFading) (-10).dp else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "homeTodayCompletionOffsetY",
    )
    val titleStrikeProgress by animateFloatAsState(
        targetValue = if (localStruck) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "homeTodayTitleStrikeProgress",
    )
    val actionRevealProgress = swipeRevealState.revealProgress(animatedOffsetX)
    val dueText = todo.due?.let(HOME_TODAY_DUE_FORMATTER::format)
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
                .height(58.dp),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskSwipeActionButton(
                    icon = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.action_edit_task),
                    label = stringResource(R.string.action_edit),
                    tint = Color.White,
                    background = TdaySwipeEditBackground,
                    revealProgress = actionRevealProgress,
                    revealDelay = 0.62f,
                    onClick = {
                        ViewCompat.performHapticFeedback(
                            view,
                            HapticFeedbackConstantsCompat.CLOCK_TICK
                        )
                        closeSwipeSlot()
                        onEdit()
                    },
                )
                TaskSwipeActionButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete_task),
                    label = stringResource(R.string.action_delete),
                    tint = Color.White,
                    background = TdaySwipeDeleteBackground,
                    revealProgress = actionRevealProgress,
                    revealDelay = 0.04f,
                    onClick = {
                        ViewCompat.performHapticFeedback(
                            view,
                            HapticFeedbackConstantsCompat.CLOCK_TICK
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
                                swipeRevealState.playHint()
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
                                    closeSwipeSlot()
                                    localChecked = true
                                    pendingCompletion = true
                                    coroutineScope.launch {
                                        delay(160)
                                        localStruck = true
                                        delay(360)
                                        completionFading = true
                                        delay(260)
                                        onComplete()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (localChecked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (localChecked) {
                                stringResource(R.string.label_completed)
                            } else {
                                stringResource(R.string.label_mark_complete)
                            },
                            tint = if (localChecked) TdayTaskCompleteAccent else colorScheme.onSurfaceVariant.copy(
                                alpha = 0.78f
                            ),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
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
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = TdayFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 22.sp,
                            color = if (localStruck) {
                                colorScheme.onSurface.copy(alpha = 0.78f)
                            } else {
                                colorScheme.onSurface
                            },
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
                icon = Icons.Rounded.Schedule,
                backgroundWatermark = Icons.Rounded.Schedule,
                title = stringResource(R.string.home_category_scheduled),
                count = scheduledCount,
                onClick = onOpenScheduled,
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFFC97880),
                icon = Icons.Rounded.Flag,
                backgroundWatermark = Icons.Rounded.Flag,
                title = stringResource(R.string.home_category_priority),
                count = priorityCount,
                onClick = onOpenPriority,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFFE06F66),
                icon = Icons.Rounded.ErrorOutline,
                backgroundWatermark = Icons.Rounded.ErrorOutline,
                title = stringResource(R.string.home_category_overdue),
                count = overdueCount,
                onClick = onOpenOverdue,
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFF68717A),
                icon = Icons.Rounded.Inbox,
                backgroundWatermark = Icons.Rounded.Inbox,
                title = stringResource(R.string.home_category_all),
                count = allCount,
                onClick = onOpenAll,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = completedColor,
                icon = Icons.Rounded.Check,
                backgroundWatermark = Icons.Rounded.Check,
                title = stringResource(R.string.home_category_completed),
                count = completedCount,
                onClick = onOpenCompleted,
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = calendarTileColor(colorScheme),
                icon = Icons.Rounded.CalendarToday,
                backgroundGrid = true,
                title = "Calendar",
                count = calendarCount,
                onClick = onOpenCalendar,
            )
        }
    }
}

private fun completedTileColor(colorScheme: ColorScheme): Color {
    return Color(0xFF719F84)
}

private fun calendarTileColor(colorScheme: ColorScheme): Color {
    return Color(0xFF9A89D2)
}

@Composable
private fun CategoryCard(
    modifier: Modifier,
    color: Color,
    icon: ImageVector,
    backgroundWatermark: ImageVector? = null,
    backgroundGrid: Boolean = false,
    title: String,
    count: Int? = null,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "categoryCardScale",
    )
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        label = "categoryCardOffsetY",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 9.dp,
        label = "categoryCardElevation",
    )

    Card(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .offset(y = animatedOffsetY)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        onClick = {
            performGentleHaptic(view)
            onClick()
        },
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(
            defaultElevation = animatedElevation,
            pressedElevation = animatedElevation,
        ),
        shape = RoundedCornerShape(26.dp),
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
                }
        ) {
            if (backgroundGrid) {
                val gridTint = lerp(color, Color.White, 0.32f)
                Box(modifier = Modifier.matchParentSize()) {
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 28.dp, y = 14.dp)
                            .size(width = 172.dp, height = 116.dp)
                            .graphicsLayer { alpha = 0.42f },
                    ) {
                        val cols = 6
                        val rows = 4
                        val stroke = 1.2.dp.toPx()
                        val lineColor = gridTint.copy(alpha = 0.85f)
                        val borderColor = gridTint.copy(alpha = 0.95f)

                        drawRoundRect(
                            color = borderColor,
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                            style = Stroke(width = stroke),
                        )

                        for (i in 1 until cols) {
                            val x = size.width * i / cols
                            drawLine(
                                color = lineColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = stroke,
                            )
                        }
                        for (j in 1 until rows) {
                            val y = size.height * j / rows
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = stroke,
                            )
                        }
                    }
                }
            }

            if (backgroundWatermark != null) {
                Box(modifier = Modifier.matchParentSize()) {
                    Icon(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 22.dp, y = 12.dp)
                            .size(124.dp),
                        imageVector = backgroundWatermark,
                        contentDescription = null,
                        tint = lerp(color, Color.White, 0.28f).copy(alpha = 0.4f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                    if (count != null) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun ListRow(
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
    val containerColor = lerp(colorScheme.surfaceVariant, accent, HOME_LIST_CONTAINER_COLOR_WEIGHT)
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
            performGentleHaptic(view)
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
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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

private const val HOME_LIST_CONTAINER_COLOR_WEIGHT = 0.66f
private const val CREATE_LIST_SHEET_MAX_HEIGHT_FRACTION = 0.80f
private const val CREATE_LIST_SHEET_NORMAL_HEIGHT_FRACTION = 0.70f
private const val CREATE_LIST_SHEET_KEYBOARD_HEIGHT_FRACTION = 0.80f
private const val CREATE_LIST_SHEET_MOTION_MS = 320
private const val SEARCH_RESULT_SEARCH_CLOSE_DELAY_MS = 260L
private val RootFeedDockCollapseThreshold = 44.dp

private fun performGentleHaptic(view: android.view.View) {
    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
}

private fun priorityIconFor(priority: String): ImageVector? {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "medium" -> Icons.Rounded.Flag
        "high", "urgent", "important" -> Icons.Rounded.Flag
        else -> null
    }
}
