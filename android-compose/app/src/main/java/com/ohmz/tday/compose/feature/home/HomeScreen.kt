package com.ohmz.tday.compose.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.component.TdayPullToRefreshBox
import kotlinx.coroutines.delay
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenPriority: () -> Unit,
    onOpenCompleted: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenList: (listId: String, listName: String) -> Unit,
    onCreateTask: (payload: CreateTaskPayload) -> Unit,
    onCreateList: (name: String, color: String?, iconKey: String?) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
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
    val imeVisible = WindowInsets.isImeVisible
    var searchImeWasVisible by rememberSaveable { mutableStateOf(false) }
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }
    var rootInRoot by remember { mutableStateOf(Offset.Zero) }
    var showCreateTask by rememberSaveable { mutableStateOf(false) }
    var listName by rememberSaveable { mutableStateOf("") }
    var listColor by rememberSaveable { mutableStateOf(DEFAULT_LIST_COLOR) }
    var listIconKey by rememberSaveable { mutableStateOf(DEFAULT_LIST_ICON_KEY) }
    var showCreateList by rememberSaveable { mutableStateOf(false) }
    var hasCapturedInitialListSnapshot by rememberSaveable { mutableStateOf(false) }
    var hasShownListDataOnce by rememberSaveable { mutableStateOf(false) }
    var lastListStructureSignature by rememberSaveable { mutableStateOf("") }
    var visibleListStage by rememberSaveable { mutableIntStateOf(0) }
    var animateListCascade by rememberSaveable { mutableStateOf(false) }
    val closeSearch = {
        searchExpanded = false
        searchBarBounds = null
        rootInRoot = Offset.Zero
        searchImeWasVisible = false
    }
    BackHandler(enabled = searchExpanded) {
        closeSearch()
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
    val listStructureSignature = remember(uiState.summary.lists) {
        uiState.summary.lists.joinToString(separator = "|") { list ->
            buildString {
                append(list.id)
                append(':')
                append(list.name)
                append(':')
                append(list.color.orEmpty())
                append(':')
                append(list.iconKey.orEmpty())
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress, searchExpanded) {
        if (searchExpanded || listState.isScrollInProgress) return@LaunchedEffect
        // Snap only when top header row is partially visible.
        // If fully off-screen (index > 0), do not force any anchor behavior.
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 0) {
            listState.animateScrollToItem(index = 0, scrollOffset = 0)
        }
    }

    LaunchedEffect(listStructureSignature) {
        val lists = uiState.summary.lists
        val targetFinalStage = if (lists.isEmpty()) 0 else lists.size + 1
        if (!hasCapturedInitialListSnapshot) {
            visibleListStage = targetFinalStage
            animateListCascade = false
            hasCapturedInitialListSnapshot = true
            hasShownListDataOnce = lists.isNotEmpty()
            lastListStructureSignature = listStructureSignature
            return@LaunchedEffect
        }

        if (listStructureSignature == lastListStructureSignature) {
            visibleListStage = targetFinalStage
            animateListCascade = false
            return@LaunchedEffect
        }

        lastListStructureSignature = listStructureSignature
        if (lists.isEmpty()) {
            visibleListStage = 0
            animateListCascade = false
            return@LaunchedEffect
        }

        if (!hasShownListDataOnce) {
            visibleListStage = targetFinalStage
            animateListCascade = false
            hasShownListDataOnce = true
            return@LaunchedEffect
        }

        animateListCascade = true
        visibleListStage = 0
        delay(70)
        visibleListStage = 1
        delay(75)
        lists.forEachIndexed { index, _ ->
            visibleListStage = index + 2
            delay(60)
        }
        // Stop wrapping rows with entry animation once the cascade has completed.
        // This prevents rows from re-animating when they are recomposed during scroll.
        visibleListStage = targetFinalStage
        animateListCascade = false
    }

    Scaffold(
        containerColor = colorScheme.background,
        floatingActionButton = {
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
        },
    ) { padding ->
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            TdayPullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = onRefresh,
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
                                    .pointerInput(searchBarBounds, rootInRoot) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(pass = PointerEventPass.Final)
                                            val tapInRoot = down.position + rootInRoot
                                            val tappedSearchBar = searchBarBounds?.contains(tapInRoot) == true
                                            val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                                            if (up != null && !tappedSearchBar) {
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
                            onSearchExpandedChange = { searchExpanded = it },
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
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CategoryGrid(
                                todayCount = uiState.summary.todayCount,
                                scheduledCount = uiState.summary.scheduledCount,
                                allCount = uiState.summary.allCount,
                                priorityCount = uiState.summary.priorityCount,
                                completedCount = uiState.summary.completedCount,
                                onOpenToday = {
                                    closeSearch()
                                    onOpenToday()
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
                            )

                            CategoryCard(
                                modifier = Modifier.fillMaxWidth(),
                                color = calendarTileColor(colorScheme),
                                icon = Icons.Rounded.CalendarToday,
                                backgroundGrid = true,
                                title = "Calendar",
                                count = uiState.summary.scheduledCount,
                                onClick = {
                                    closeSearch()
                                    onOpenCalendar()
                                },
                            )
                        }
                    }

                    if (uiState.summary.lists.isNotEmpty()) {
                        item {
                            if (visibleListStage >= 1) {
                                if (animateListCascade) {
                                    TopDownCascadeReveal {
                                        MyListsHeader()
                                    }
                                } else {
                                    MyListsHeader()
                                }
                            }
                        }
                        itemsIndexed(
                            items = uiState.summary.lists,
                            key = { _, list -> list.id },
                            contentType = { _, _ -> "list_row" },
                        ) { index, list ->
                            if (visibleListStage >= index + 2) {
                                if (animateListCascade) {
                                    TopDownCascadeReveal {
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
                                } else {
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

                    item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    if (showCreateTask) {
        CreateTaskBottomSheet(
            lists = uiState.summary.lists,
            defaultListId = null,
            onDismiss = { showCreateTask = false },
            onCreateTask = { payload ->
                onCreateTask(payload)
                showCreateTask = false
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
                    listColor = DEFAULT_LIST_COLOR
                    listIconKey = DEFAULT_LIST_ICON_KEY
                    showCreateList = false
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val selectedAccent = listColorAccent(listColor)
    val canCreate = listName.isNotBlank()
    val selectedIcon = listIconForKey(listIconKey)
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val sheetContainerColor = if (isDarkTheme) {
        lerp(colorScheme.background, colorScheme.surfaceVariant, 0.34f)
    } else {
        colorScheme.background
    }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ListSheetHeader(
                    onClose = {
                        focusManager.clearFocus(force = true)
                        onDismiss()
                    },
                    onConfirm = {
                        focusManager.clearFocus(force = true)
                        if (canCreate) onCreate()
                    },
                    confirmEnabled = canCreate,
                )

                ListSheetSectionTitle("List")
                ListSheetCard {
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
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colorScheme.surfaceVariant)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (listName.isBlank()) {
                                        Text(
                                            text = "List name",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }
                }

                ListSheetSectionTitle("Color")
                ListSheetCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LIST_COLOR_OPTIONS.forEach { option ->
                            val selected = listColor == option.key
                            val interactionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(option.color)
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) colorScheme.onBackground.copy(alpha = 0.32f) else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = ripple(
                                            bounded = true,
                                            radius = 21.dp,
                                        ),
                                    ) { onListColorChange(option.key) },
                            )
                        }
                    }
                }

                ListSheetSectionTitle("Icon")
                ListSheetCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LIST_ICON_OPTIONS.forEach { option ->
                            val selected = listIconKey == option.key
                            val interactionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) {
                                            selectedAccent.copy(alpha = 0.2f)
                                        } else {
                                            colorScheme.surfaceVariant
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
                                            radius = 23.dp,
                                        ),
                                    ) { onListIconChange(option.key) },
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

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ListSheetHeader(
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListSheetActionButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Close",
            enabled = true,
            accentColor = Color(0xFFE35A5A),
            onClick = onClose,
        )

        Text(
            text = "New list",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        ListSheetActionButton(
            icon = Icons.Rounded.Check,
            contentDescription = "Create list",
            enabled = confirmEnabled,
            accentColor = Color(0xFF2FA35B),
            onClick = onConfirm,
        )
    }
}

@Composable
private fun ListSheetActionButton(
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
        label = "listSheetHeaderButtonScale",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            pressed && enabled -> 2.dp
            enabled -> 8.dp
            else -> 5.dp
        },
        label = "listSheetHeaderButtonElevation",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed && enabled) 1.dp else 0.dp,
        label = "listSheetHeaderButtonOffsetY",
    )
    val containerColor = colorScheme.surfaceVariant
    val iconTint = colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.55f)
    val borderColor = if (enabled) {
        accentColor.copy(alpha = 0.55f)
    } else {
        accentColor.copy(alpha = 0.3f)
    }

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
                color = borderColor,
                shape = RoundedCornerShape(999.dp),
            ),
        onClick = {
            if (enabled) performGentleHaptic(view)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
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
private fun ListSheetSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ListSheetCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
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

@Composable
private fun TopSearchBar(
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
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
    val homeTitleIconTint = if (isDaytime) Color(0xFFF4C542) else Color(0xFFA8B8E8)

    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val buttonSize = 54.dp
        val buttonGap = 8.dp
        val fixedActionWidth = (buttonSize * 2) + buttonGap
        val collapsedSearchWidth = buttonSize
        val expandedSearchWidth = (maxWidth - fixedActionWidth - buttonGap).coerceAtLeast(buttonSize)
        val animatedSearchWidth by animateDpAsState(
            targetValue = if (searchExpanded) expandedSearchWidth else collapsedSearchWidth,
            label = "topSearchBarSearchWidth",
        )

        if (!searchExpanded) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp),
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
                    text = "T'Day",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .then(
                    if (searchExpanded) {
                        Modifier.onGloballyPositioned { coordinates ->
                            onSearchBarBoundsChanged(coordinates.boundsInRoot())
                        }
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(buttonGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Card(
                modifier = Modifier
                    .width(animatedSearchWidth)
                    .height(buttonSize),
                onClick = {
                    if (!searchExpanded) {
                        onSearchExpandedChange(true)
                    }
                },
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.26f)),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PressableIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = if (searchExpanded) "Close search" else "Search",
                        tint = colorScheme.onSurface,
                        compact = true,
                        onClick = {
                            if (searchExpanded) {
                                onSearchExpandedChange(false)
                            } else {
                                onSearchExpandedChange(true)
                            }
                        },
                    )

                    if (searchExpanded) {
                        BasicTextField(
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (searchQuery.isBlank()) {
                                        Text(
                                            text = "Search",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }
                }
            }

            PressableIconButton(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = "Create list",
                tint = colorScheme.onSurface,
                onClick = onCreateList,
            )
            PressableIconButton(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = "More",
                tint = colorScheme.onSurface,
                onClick = onOpenSettings,
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
private fun MyListsHeader() {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
         Text(
            text = "My Lists",
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
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

    Card(
        modifier = Modifier
            .size(if (compact) 30.dp else 54.dp)
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
                modifier = Modifier.size(if (compact) 24.dp else 22.dp),
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    todayCount: Int,
    scheduledCount: Int,
    allCount: Int,
    priorityCount: Int,
    completedCount: Int,
    onOpenToday: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenPriority: () -> Unit,
    onOpenCompleted: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val completedColor = completedTileColor(colorScheme)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFF6EA8E1),
                icon = Icons.Rounded.WbSunny,
                backgroundWatermark = Icons.Rounded.WbSunny,
                title = "Today",
                count = todayCount,
                onClick = onOpenToday,
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFFDDB37D),
                icon = Icons.Rounded.Schedule,
                backgroundWatermark = Icons.Rounded.Schedule,
                title = "Scheduled",
                count = scheduledCount,
                onClick = onOpenScheduled,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFF4E4E50),
                icon = Icons.Rounded.Inbox,
                backgroundWatermark = Icons.Rounded.Inbox,
                title = "All",
                count = allCount,
                onClick = onOpenAll,
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFFD48A8C),
                icon = Icons.Rounded.Flag,
                backgroundWatermark = Icons.Rounded.Flag,
                title = "Priority",
                count = priorityCount,
                onClick = onOpenPriority,
            )
        }
        CategoryCard(
            modifier = Modifier.fillMaxWidth(),
            color = completedColor,
            icon = Icons.Rounded.Check,
            backgroundWatermark = Icons.Rounded.Check,
            title = "Completed",
            count = completedCount,
            onClick = onOpenCompleted,
        )
    }
}

private fun completedTileColor(colorScheme: ColorScheme): Color {
    return Color(0xFFA8C8B2)
}

private fun calendarTileColor(colorScheme: ColorScheme): Color {
    return Color(0xFFC3B4DF)
}

@Composable
private fun TopDownCascadeReveal(
    content: @Composable () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "listCascadeAlpha",
    )
    val offsetY by animateDpAsState(
        targetValue = if (revealed) 0.dp else (-14).dp,
        animationSpec = tween(durationMillis = 320),
        label = "listCascadeOffsetY",
    )

    LaunchedEffect(Unit) {
        revealed = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                translationY = offsetY.toPx()
            },
    ) {
        content()
    }
}

@Composable
private fun CategoryCard(
    modifier: Modifier,
    color: Color,
    icon: ImageVector,
    backgroundWatermark: ImageVector? = null,
    backgroundGrid: Boolean = false,
    title: String,
    count: Int,
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White)
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ListRow(
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
    val accent = listColorAccent(colorKey)
    val icon = listIconForKey(iconKey)
    val containerColor = lerp(colorScheme.surfaceVariant, accent, 0.38f)
    val displayName = capitalizeFirstListLetter(name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
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
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = animatedCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun performGentleHaptic(view: android.view.View) {
    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
}

private data class ListColorOption(
    val key: String,
    val color: Color,
)

private data class ListIconOption(
    val key: String,
    val icon: ImageVector,
)

private const val DEFAULT_LIST_COLOR = "BLUE"
private const val DEFAULT_LIST_ICON_KEY = "inbox"

private val LIST_COLOR_OPTIONS = listOf(
    ListColorOption("RED", Color(0xFFE65E52)),
    ListColorOption("ORANGE", Color(0xFFF29F38)),
    ListColorOption("YELLOW", Color(0xFFF3D04A)),
    ListColorOption("LIME", Color(0xFF8ACF56)),
    ListColorOption("BLUE", Color(0xFF5C9FE7)),
    ListColorOption("PURPLE", Color(0xFF8D6CE2)),
    ListColorOption("PINK", Color(0xFFDF6DAA)),
    ListColorOption("TEAL", Color(0xFF4EB5B0)),
    ListColorOption("CORAL", Color(0xFFE3876D)),
    ListColorOption("GOLD", Color(0xFFCFAB57)),
    ListColorOption("DEEP_BLUE", Color(0xFF4B73D6)),
    ListColorOption("ROSE", Color(0xFFD9799A)),
    ListColorOption("LIGHT_RED", Color(0xFFE48888)),
    ListColorOption("BRICK", Color(0xFFB86A5C)),
    ListColorOption("SLATE", Color(0xFF7B8593)),
)

private val LIST_ICON_OPTIONS = listOf(
    ListIconOption("inbox", Icons.Rounded.Inbox),
    ListIconOption("sun", Icons.Rounded.WbSunny),
    ListIconOption("calendar", Icons.Rounded.CalendarToday),
    ListIconOption("schedule", Icons.Rounded.Schedule),
    ListIconOption("flag", Icons.Rounded.Flag),
    ListIconOption("check", Icons.Rounded.Check),
    ListIconOption("smile", Icons.Rounded.Mood),
    ListIconOption("list", Icons.AutoMirrored.Rounded.List),
    ListIconOption("bookmark", Icons.Rounded.Bookmark),
    ListIconOption("key", Icons.Rounded.Key),
    ListIconOption("gift", Icons.Rounded.CardGiftcard),
    ListIconOption("cake", Icons.Rounded.Cake),
    ListIconOption("school", Icons.Rounded.School),
    ListIconOption("bag", Icons.Rounded.Backpack),
    ListIconOption("edit", Icons.Rounded.Edit),
    ListIconOption("document", Icons.Rounded.Description),
    ListIconOption("book", Icons.AutoMirrored.Rounded.MenuBook),
    ListIconOption("work", Icons.Rounded.Work),
    ListIconOption("wallet", Icons.Rounded.AccountBalanceWallet),
    ListIconOption("money", Icons.Rounded.Payments),
    ListIconOption("fitness", Icons.Rounded.FitnessCenter),
    ListIconOption("run", Icons.AutoMirrored.Rounded.DirectionsRun),
    ListIconOption("food", Icons.Rounded.Restaurant),
    ListIconOption("drink", Icons.Rounded.LocalBar),
    ListIconOption("health", Icons.Rounded.Medication),
    ListIconOption("monitor", Icons.Rounded.DesktopWindows),
    ListIconOption("music", Icons.Rounded.MusicNote),
    ListIconOption("computer", Icons.Rounded.Computer),
    ListIconOption("game", Icons.Rounded.SportsEsports),
    ListIconOption("headphones", Icons.Rounded.Headphones),
    ListIconOption("eco", Icons.Rounded.Eco),
    ListIconOption("pets", Icons.Rounded.Pets),
    ListIconOption("child", Icons.Rounded.ChildCare),
    ListIconOption("family", Icons.Rounded.FamilyRestroom),
    ListIconOption("basket", Icons.Rounded.ShoppingBasket),
    ListIconOption("cart", Icons.Rounded.ShoppingCart),
    ListIconOption("mall", Icons.Rounded.LocalMall),
    ListIconOption("inventory", Icons.Rounded.Inventory),
    ListIconOption("soccer", Icons.Rounded.SportsSoccer),
    ListIconOption("baseball", Icons.Rounded.SportsBaseball),
    ListIconOption("basketball", Icons.Rounded.SportsBasketball),
    ListIconOption("football", Icons.Rounded.SportsFootball),
    ListIconOption("tennis", Icons.Rounded.SportsTennis),
    ListIconOption("train", Icons.Rounded.Train),
    ListIconOption("flight", Icons.Rounded.Flight),
    ListIconOption("boat", Icons.Rounded.DirectionsBoat),
    ListIconOption("car", Icons.Rounded.DirectionsCar),
    ListIconOption("umbrella", Icons.Rounded.BeachAccess),
    ListIconOption("drop", Icons.Rounded.WaterDrop),
    ListIconOption("snow", Icons.Rounded.AcUnit),
    ListIconOption("fire", Icons.Rounded.Whatshot),
    ListIconOption("tools", Icons.Rounded.Build),
    ListIconOption("scissors", Icons.Rounded.ContentCut),
    ListIconOption("architecture", Icons.Rounded.Architecture),
    ListIconOption("code", Icons.Rounded.Code),
    ListIconOption("idea", Icons.Rounded.Lightbulb),
    ListIconOption("chat", Icons.Rounded.ChatBubbleOutline),
    ListIconOption("alert", Icons.Rounded.PriorityHigh),
    ListIconOption("star", Icons.Rounded.Star),
    ListIconOption("heart", Icons.Rounded.Favorite),
    ListIconOption("circle", Icons.Rounded.Circle),
    ListIconOption("square", Icons.Rounded.Square),
    ListIconOption("triangle", Icons.Rounded.ChangeHistory),
    ListIconOption("home", Icons.Rounded.Home),
    ListIconOption("city", Icons.Rounded.LocationCity),
    ListIconOption("bank", Icons.Rounded.AccountBalance),
    ListIconOption("camera", Icons.Rounded.CameraAlt),
    ListIconOption("palette", Icons.Rounded.Palette),
)

private fun listColorAccent(colorKey: String?): Color {
    return LIST_COLOR_OPTIONS.firstOrNull { it.key == colorKey }?.color
        ?: Color(0xFFE9A03B)
}

private fun listIconForKey(iconKey: String?): ImageVector {
    return LIST_ICON_OPTIONS.firstOrNull { it.key == iconKey }?.icon
        ?: Icons.Rounded.Inbox
}
