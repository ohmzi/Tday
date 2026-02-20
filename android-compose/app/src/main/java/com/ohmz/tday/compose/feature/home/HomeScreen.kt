package com.ohmz.tday.compose.feature.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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
    onOpenNotes: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProject: (projectId: String, projectName: String) -> Unit,
    onCreateProject: (name: String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
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
    var listName by rememberSaveable { mutableStateOf("") }
    var showCreateList by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    performGentleHaptic(view)
                    showCreateList = true
                },
                modifier = Modifier
                    .offset(y = fabOffsetY)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    },
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = fabElevation,
                    pressedElevation = fabElevation,
                ),
                interactionSource = fabInteractionSource,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create list")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                TopSearchBar(
                    onOpenNotes = onOpenNotes,
                    onOpenSettings = onOpenSettings,
                )
            }

            item {
                Text(
                    text = "Tday",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
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
                        onOpenToday = onOpenToday,
                        onOpenScheduled = onOpenScheduled,
                        onOpenAll = onOpenAll,
                        onOpenFlagged = onOpenFlagged,
                        onOpenCompleted = onOpenCompleted,
                    )

                    CategoryCard(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFA8B0B7),
                        icon = Icons.Rounded.CalendarToday,
                        title = "Calendar",
                        count = uiState.summary.scheduledCount,
                        onClick = onOpenCalendar,
                    )
                }
            }

            item {
                Text(
                    text = "My Lists",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (uiState.summary.projects.isEmpty()) {
                item {
                    EmptyProjectCard(onCreate = { showCreateList = true })
                }
            } else {
                items(uiState.summary.projects, key = { it.id }) { project ->
                    ProjectRow(
                        name = project.name,
                        count = project.todoCount,
                        onClick = { onOpenProject(project.id, project.name) },
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
private fun TopSearchBar(
    onOpenNotes: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val widthFraction by animateFloatAsState(
        targetValue = if (searchExpanded) 1f else 0.5f,
        label = "topSearchBarWidthFraction",
    )

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
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
                onClick = { searchExpanded = true },
            )

            if (searchExpanded) {
                TextField(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = { Text("Search") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                PressableIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close search",
                    tint = colorScheme.onSurface,
                    onClick = {
                        searchExpanded = false
                        searchQuery = ""
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                    },
                )
            } else {
                PressableIconButton(
                    icon = Icons.Rounded.List,
                    contentDescription = "Notes",
                    tint = colorScheme.onSurface,
                    onClick = onOpenNotes,
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFF6EA8E1),
                icon = Icons.Rounded.CalendarToday,
                title = "Today",
                count = todayCount,
                onClick = onOpenToday,
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFFD48A8C),
                icon = Icons.Rounded.CalendarToday,
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
                title = "All",
                count = allCount,
                onClick = onOpenAll,
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                color = Color(0xFFDDB37D),
                icon = Icons.Rounded.Flag,
                title = "Flagged",
                count = flaggedCount,
                onClick = onOpenFlagged,
            )
        }
        CategoryCard(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFA8B0B7),
            icon = Icons.Rounded.Check,
            title = "Completed",
            count = completedCount,
            onClick = onOpenCompleted,
        )
    }
}

@Composable
private fun CategoryCard(
    modifier: Modifier,
    color: Color,
    icon: ImageVector,
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
        Column(
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
                        drawRect(depthShade)
                        drawContent()
                    }
                }
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

@Composable
private fun EmptyProjectCard(onCreate: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "No lists yet",
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Create project lists to organize tasks like iOS Reminders.",
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = {
                    performGentleHaptic(view)
                    onCreate()
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("Create first list")
            }
        }
    }
}

private fun performGentleHaptic(view: android.view.View) {
    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
}
