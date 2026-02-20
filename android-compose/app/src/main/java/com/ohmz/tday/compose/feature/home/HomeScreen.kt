package com.ohmz.tday.compose.feature.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenFlagged: () -> Unit,
    onOpenCompleted: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProject: (projectId: String, projectName: String) -> Unit,
    onCreateTask: (title: String) -> Unit,
    onCreateProject: (name: String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val fabInteractionSource = remember { MutableInteractionSource() }
    val fabPressed by fabInteractionSource.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.93f else 1f,
        label = "fabScale",
    )
    val fabElevation by animateDpAsState(
        targetValue = if (fabPressed) 4.dp else 14.dp,
        label = "fabElevation",
    )
    val fabOffsetY by animateDpAsState(
        targetValue = if (fabPressed) 2.dp else 0.dp,
        label = "fabOffsetY",
    )
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }
    var rootInRoot by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var taskTitle by rememberSaveable { mutableStateOf("") }
    var showCreateTask by rememberSaveable { mutableStateOf(false) }
    var listName by rememberSaveable { mutableStateOf("") }
    var showCreateList by rememberSaveable { mutableStateOf(false) }
    val closeSearch = { searchExpanded = false }

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
                elevation = fabElevation,
                onClick = { showCreateTask = true },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { coordinates ->
                    rootInRoot = coordinates.boundsInRoot().topLeft
                }
                .pointerInput(searchExpanded, searchBarBounds, rootInRoot) {
                    if (!searchExpanded) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Final)
                        val tapInRoot = down.position + rootInRoot
                        val tappedSearchBar = searchBarBounds?.contains(tapInRoot) == true
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                        if (up != null && !tappedSearchBar) {
                            searchExpanded = false
                        }
                    }
                },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    TopSearchBar(
                        searchExpanded = searchExpanded,
                        onSearchExpandedChange = { searchExpanded = it },
                        onSearchBarBoundsChanged = { bounds -> searchBarBounds = bounds },
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
                            flaggedCount = uiState.summary.flaggedCount,
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
                            onOpenFlagged = {
                                closeSearch()
                                onOpenFlagged()
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

                if (uiState.summary.projects.isNotEmpty()) {
                    item {
                        Text(
                            text = "My Lists",
                            style = MaterialTheme.typography.headlineMedium,
                            color = colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(uiState.summary.projects, key = { it.id }) { project ->
                        ProjectRow(
                            name = project.name,
                            count = project.todoCount,
                            onClick = {
                                closeSearch()
                                onOpenProject(project.id, project.name)
                            },
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

                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (showCreateTask) {
        AlertDialog(
            onDismissRequest = { showCreateTask = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (taskTitle.isNotBlank()) {
                            onCreateTask(taskTitle)
                            taskTitle = ""
                            showCreateTask = false
                        }
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTask = false }) { Text("Cancel") }
            },
            title = { Text("New task") },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    singleLine = true,
                    label = { Text("Task title") },
                )
            },
        )
    }

    if (showCreateList) {
        AlertDialog(
            onDismissRequest = { showCreateList = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (listName.isNotBlank()) {
                            onCreateProject(listName)
                            listName = ""
                            showCreateList = false
                        }
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateList = false }) { Text("Cancel") }
            },
            title = { Text("New list") },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = listName,
                    onValueChange = { listName = it },
                    singleLine = true,
                    label = { Text("List name") },
                )
            },
        )
    }
}

@Composable
private fun CreateTaskButton(
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    elevation: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier,
        onClick = {
            performGentleHaptic(view)
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
        val collapsedWidth = 168.dp.coerceAtMost(maxWidth)
        val expandedWidth = maxWidth
        val animatedWidth by animateDpAsState(
            targetValue = if (searchExpanded) expandedWidth else collapsedWidth,
            label = "topSearchBarWidth",
        )

        if (!searchExpanded) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp),
                text = "Tday",
                style = MaterialTheme.typography.headlineLarge,
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(animatedWidth)
                .onGloballyPositioned { coordinates ->
                    onSearchBarBoundsChanged(coordinates.boundsInRoot())
                }
                .clip(RoundedCornerShape(32.dp))
                .background(colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressableIconButton(
                icon = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = colorScheme.onSurface,
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
                        .height(40.dp)
                        .focusRequester(focusRequester),
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (searchQuery.isBlank()) {
                                Text(
                                    text = "Search",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            PressableIconButton(
                icon = Icons.Rounded.Add,
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
private fun PressableIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        label = "homeIconButtonScale",
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ) {
                performGentleHaptic(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Composable
private fun CategoryGrid(
    todayCount: Int,
    scheduledCount: Int,
    allCount: Int,
    flaggedCount: Int,
    completedCount: Int,
    onOpenToday: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenFlagged: () -> Unit,
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
                color = Color(0xFFD48A8C),
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
                color = Color(0xFFDDB37D),
                icon = Icons.Rounded.Flag,
                backgroundWatermark = Icons.Rounded.Flag,
                title = "Flagged",
                count = flaggedCount,
                onClick = onOpenFlagged,
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
    return lerp(colorScheme.surfaceVariant, colorScheme.onSurface, 0.32f)
}

private fun calendarTileColor(colorScheme: ColorScheme): Color {
    return lerp(completedTileColor(colorScheme), colorScheme.primary, 0.18f)
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
        targetValue = if (isPressed) 2.dp else 14.dp,
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
                    val pearlBand = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.16f),
                            Color(0xFFDDF0FF).copy(alpha = 0.1f),
                            Color.Transparent,
                        ),
                        start = Offset(
                            x = size.width * 0.08f,
                            y = 0f,
                        ),
                        end = Offset(
                            x = size.width * 0.68f,
                            y = size.height,
                        ),
                    )
                    val depthShade = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.1f),
                        ),
                        start = Offset(
                            x = size.width * 0.35f,
                            y = size.height * 0.35f,
                        ),
                        end = Offset(
                            x = size.width,
                            y = size.height,
                        ),
                    )
                    onDrawWithContent {
                        drawRect(iconSideGlow)
                        drawRect(pearlWash)
                        drawRect(pearlBand)
                        drawRect(depthShade)
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
                            .offset(x = 24.dp, y = 14.dp)
                            .size(132.dp),
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
private fun ProjectRow(
    name: String,
    count: Int,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "projectRowScale",
    )
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 0.dp,
        label = "projectRowOffsetY",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 10.dp,
        label = "projectRowElevation",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(
            defaultElevation = animatedElevation,
            pressedElevation = animatedElevation,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFE9A03B)),
                )
                Text(
                    modifier = Modifier.padding(start = 12.dp),
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                )
            }

            Text(
                text = count.toString(),
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun performGentleHaptic(view: android.view.View) {
    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
}
