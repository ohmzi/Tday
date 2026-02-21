package com.ohmz.tday.compose.feature.todos

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
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
    onUpdateListSettings: (listId: String, name: String, color: String?, iconKey: String?) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val selectedList = uiState.lists.firstOrNull { it.id == uiState.listId }
    val selectedListColorKey = selectedList?.color
    val usesTodayStyle =
        uiState.mode == TodoListMode.TODAY || uiState.mode == TodoListMode.SCHEDULED || uiState.mode == TodoListMode.ALL || uiState.mode == TodoListMode.PRIORITY || uiState.mode == TodoListMode.LIST
    val titleColor = when (uiState.mode) {
        TodoListMode.TODAY -> Color(0xFF6EA8E1)
        TodoListMode.SCHEDULED -> Color(0xFFDDB37D)
        TodoListMode.ALL -> Color(0xFF4E4E50)
        TodoListMode.PRIORITY -> Color(0xFFD48A8C)
        TodoListMode.LIST -> listAccentColor(selectedListColorKey)
    }
    val titleIcon = when (uiState.mode) {
        TodoListMode.TODAY -> Icons.Rounded.WbSunny
        TodoListMode.SCHEDULED -> Icons.Rounded.Schedule
        TodoListMode.ALL -> Icons.Rounded.Inbox
        TodoListMode.PRIORITY -> Icons.Rounded.Flag
        TodoListMode.LIST -> listIconForKey(selectedList?.iconKey)
    }
    val fabColor = todoFabColorForMode(
        mode = uiState.mode,
        listColorKey = selectedListColorKey,
    )
    val showSectionedTimeline =
        uiState.mode == TodoListMode.TODAY || uiState.mode == TodoListMode.SCHEDULED || uiState.mode == TodoListMode.ALL || uiState.mode == TodoListMode.PRIORITY || uiState.mode == TodoListMode.LIST
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
    var showListSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var listSettingsTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var listSettingsName by rememberSaveable { mutableStateOf("") }
    var listSettingsColor by rememberSaveable { mutableStateOf(DEFAULT_LIST_COLOR_KEY) }
    var listSettingsIconKey by rememberSaveable { mutableStateOf(DEFAULT_LIST_ICON_KEY) }
    var listSettingsColorTouched by rememberSaveable { mutableStateOf(false) }
    var listSettingsIconTouched by rememberSaveable { mutableStateOf(false) }
    val fabInteractionSource = remember { MutableInteractionSource() }
    val fabPressed by fabInteractionSource.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.93f else 1f,
        label = "todoFabScale",
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
                    titleIcon = titleIcon,
                    titleColor = titleColor,
                    onMore = {
                        if (uiState.mode == TodoListMode.LIST && selectedList != null) {
                            listSettingsTargetId = selectedList.id
                            listSettingsName = selectedList.name
                            listSettingsColor = selectedList.color
                                ?.takeIf { isSupportedListColor(it) }
                                ?: DEFAULT_LIST_COLOR_KEY
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = titleIcon,
                                contentDescription = null,
                                tint = titleColor,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = uiState.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                            )
                        }
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
                backgroundColor = fabColor,
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
    titleIcon: ImageVector,
    titleColor: Color,
    onMore: () -> Unit,
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
                    onClick = onMore,
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
                        imageVector = titleIcon,
                        contentDescription = null,
                        tint = titleColor,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
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
                        imageVector = titleIcon,
                        contentDescription = null,
                        tint = titleColor,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                    )
                }
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
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Create task",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        containerColor = colorScheme.background,
        tonalElevation = 0.dp,
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
                        contentDescription = "Close",
                        enabled = true,
                        accentColor = Color(0xFFE35A5A),
                        onClick = {
                            focusManager.clearFocus(force = true)
                            onDismiss()
                        },
                    )

                    Text(
                        text = "List settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )

                    ListSettingsActionButton(
                        icon = Icons.Rounded.Check,
                        contentDescription = "Save list settings",
                        enabled = canSave,
                        accentColor = Color(0xFF2FA35B),
                        onClick = {
                            focusManager.clearFocus(force = true)
                            if (canSave) onSave()
                        },
                    )
                }

                Text(
                    text = "List",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
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
                                fontWeight = FontWeight.Bold,
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
                                            text = "List name",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                            fontWeight = FontWeight.Bold,
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
                    text = "Color",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
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
                    text = "Icon",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
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
                                    contentDescription = null,
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
    MORNING, AFTERNOON, TONIGHT,
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
        )
    }

    if (!futureOnly) {
        val earlierItems = groupedByDate.asSequence().filter { (date, _) -> date < today }
            .flatMap { (_, dayItems) -> dayItems.asSequence() }.sortedBy { it.due }.toList()
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

private val SCHEDULED_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d")

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
    val dueText =
        DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(todo.due)

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
    val due = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())
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

private fun todoFabColorForMode(
    mode: TodoListMode,
    listColorKey: String?,
): Color {
    return when (mode) {
        TodoListMode.TODAY -> Color(0xFF6EA8E1)
        TodoListMode.SCHEDULED -> Color(0xFFDDB37D)
        TodoListMode.ALL -> Color(0xFF4E4E50)
        TodoListMode.PRIORITY -> Color(0xFFD48A8C)
        TodoListMode.LIST -> listAccentColor(listColorKey)
    }
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
    return LIST_SETTINGS_ICON_OPTIONS.firstOrNull { it.key == iconKey }?.icon ?: Icons.Rounded.Inbox
}

private fun isSupportedListColor(colorKey: String): Boolean {
    return LIST_SETTINGS_COLOR_KEYS.contains(colorKey)
}

private fun isSupportedListIconKey(iconKey: String): Boolean {
    return LIST_SETTINGS_ICON_OPTIONS.any { it.key == iconKey }
}

private data class ListSettingsIconOption(
    val key: String,
    val icon: ImageVector,
)

private const val DEFAULT_LIST_COLOR_KEY = "BLUE"
private const val DEFAULT_LIST_ICON_KEY = "inbox"

private val LIST_SETTINGS_COLOR_KEYS = listOf(
    "RED",
    "ORANGE",
    "YELLOW",
    "LIME",
    "BLUE",
    "PURPLE",
    "PINK",
    "TEAL",
    "CORAL",
    "GOLD",
    "DEEP_BLUE",
    "ROSE",
    "LIGHT_RED",
    "BRICK",
    "SLATE",
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
