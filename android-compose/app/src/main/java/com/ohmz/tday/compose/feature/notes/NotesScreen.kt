package com.ohmz.tday.compose.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.core.model.NoteItem
import com.ohmz.tday.compose.ui.component.TdayPullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    uiState: NotesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var noteName by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                        )
                        Text("Notes")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add note")
            }
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.notes.isEmpty()) {
                    item {
                        Text(
                            text = if (uiState.isLoading) "Loading..." else "No notes yet",
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(uiState.notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onDelete = { onDelete(note.id) },
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
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New note") },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = noteName,
                    onValueChange = { noteName = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (noteName.isNotBlank()) {
                            onCreate(noteName)
                            noteName = ""
                            showCreateDialog = false
                        }
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun NoteRow(
    note: NoteItem,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = note.name,
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            note.content?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Text("Delete")
            }
        }
    }
}
