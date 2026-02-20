package com.ohmz.tday.compose.feature.todos

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.component.TdayPullToRefreshBox
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
) {
    val colorScheme = MaterialTheme.colorScheme
    var showCreateTaskSheet by rememberSaveable { mutableStateOf(false) }
    val fabInteractionSource = remember { MutableInteractionSource() }
    val fabPressed by fabInteractionSource.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.93f else 1f,
        label = "todoFabScale",
    )
    val fabElevation by animateDpAsState(
        targetValue = if (fabPressed) 4.dp else 14.dp,
        label = "todoFabElevation",
    )
    val fabOffsetY by animateDpAsState(
        targetValue = if (fabPressed) 2.dp else 0.dp,
        label = "todoFabOffsetY",
    )

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
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
                elevation = fabElevation,
                onClick = { showCreateTaskSheet = true },
            )
        },
    ) { padding ->
        TdayPullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.items.isEmpty()) {
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

                items(uiState.items, key = { it.id }) { todo ->
                    TodoRow(
                        todo = todo,
                        onComplete = { onComplete(todo) },
                        onDelete = { onDelete(todo) },
                        onPin = { onTogglePin(todo) },
                    )
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
            defaultListId = uiState.listId ?: uiState.lists.firstOrNull()?.id,
            onDismiss = { showCreateTaskSheet = false },
            onCreateTask = { payload ->
                onAddTask(payload)
                showCreateTaskSheet = false
            },
        )
    }
}

@Composable
private fun CreateTaskButton(
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    elevation: Dp,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier,
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
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
private fun TodoRow(
    todo: TodoItem,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val due = DateTimeFormatter.ofPattern("MMM d, HH:mm")
        .withZone(ZoneId.systemDefault())
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
