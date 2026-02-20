package com.ohmz.tday.compose.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    var listName by rememberSaveable { mutableStateOf("") }
    var showCreateList by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF050507),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateList = true },
                containerColor = Color(0xFF4B9AF4),
                contentColor = Color.White,
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
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
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
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171A21)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCalendar() },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Calendar",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Month/week/day views coming natively",
                                color = Color(0xFFA0A6B4),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = Color(0xFFBFD9FF),
                        )
                    }
                }
            }

            item {
                Text(
                    text = "My Lists",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF121316))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {}) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Color.White)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenNotes) {
                Icon(Icons.Rounded.List, contentDescription = "Notes", tint = Color.White)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = Color.White)
            }
        }
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
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(26.dp),
    ) {
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

@Composable
private fun ProjectRow(
    name: String,
    count: Int,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A21)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                    color = Color.White,
                )
            }

            Text(
                text = count.toString(),
                color = Color(0xFFADB3C1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EmptyProjectCard(onCreate: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A21)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "No lists yet",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Create project lists to organize tasks like iOS Reminders.",
                color = Color(0xFF9EA6B8),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("Create first list")
            }
        }
    }
}
