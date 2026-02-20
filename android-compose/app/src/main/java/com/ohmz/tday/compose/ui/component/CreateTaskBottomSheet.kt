package com.ohmz.tday.compose.ui.component

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private enum class RepeatPreset(
    val label: String,
    val rrule: String?,
) {
    NONE("No repeat", null),
    DAILY("Daily", "RRULE:FREQ=DAILY;INTERVAL=1"),
    WEEKLY("Weekly", "RRULE:FREQ=WEEKLY;INTERVAL=1"),
    WEEKDAYS("Weekdays", "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR"),
    MONTHLY("Monthly", "RRULE:FREQ=MONTHLY;INTERVAL=1"),
    YEARLY("Yearly", "RRULE:FREQ=YEARLY;INTERVAL=1"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskBottomSheet(
    lists: List<ListSummary>,
    defaultListId: String? = null,
    onDismiss: () -> Unit,
    onCreateTask: (CreateTaskPayload) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d • HH:mm").withZone(ZoneId.systemDefault())
    }

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var selectedPriority by rememberSaveable { mutableStateOf("Low") }
    var selectedListId by rememberSaveable(defaultListId, lists.map { it.id }.joinToString("|")) {
        mutableStateOf(defaultListId ?: lists.firstOrNull()?.id)
    }
    var startEpochMs by rememberSaveable {
        mutableStateOf(System.currentTimeMillis())
    }
    var dueEpochMs by rememberSaveable {
        mutableStateOf(System.currentTimeMillis() + 3L * 60L * 60L * 1000L)
    }
    var selectedRepeat by rememberSaveable { mutableStateOf(RepeatPreset.NONE.name) }

    var listMenuExpanded by remember { mutableStateOf(false) }
    var repeatMenuExpanded by remember { mutableStateOf(false) }

    val selectedListName = lists.firstOrNull { it.id == selectedListId }?.name.orEmpty()
    val repeatPreset = RepeatPreset.valueOf(selectedRepeat)
    val canCreate = title.isNotBlank() &&
        (lists.isEmpty() || !selectedListId.isNullOrBlank())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        tonalElevation = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "New task",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                ),
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                ),
                minLines = 2,
                maxLines = 4,
            )

            Text(
                text = "Priority",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Low", "Medium", "High").forEach { priority ->
                    FilterChip(
                        selected = selectedPriority == priority,
                        onClick = { selectedPriority = priority },
                        label = { Text(priority) },
                    )
                }
            }

            if (lists.isNotEmpty()) {
                Column {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { listMenuExpanded = true },
                        value = selectedListName,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("List") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenu(
                        expanded = listMenuExpanded,
                        onDismissRequest = { listMenuExpanded = false },
                    ) {
                        lists.forEach { list ->
                            DropdownMenuItem(
                                text = { Text(list.name) },
                                onClick = {
                                    selectedListId = list.id
                                    listMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showDateTimePicker(
                            context = context,
                            initialEpochMs = startEpochMs,
                        ) { picked ->
                            startEpochMs = picked
                            if (dueEpochMs <= picked) {
                                dueEpochMs = picked + 3L * 60L * 60L * 1000L
                            }
                        }
                    },
                value = formatter.format(Instant.ofEpochMilli(startEpochMs)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Starts") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                    )
                },
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showDateTimePicker(
                            context = context,
                            initialEpochMs = dueEpochMs,
                        ) { picked ->
                            dueEpochMs = if (picked > startEpochMs) {
                                picked
                            } else {
                                startEpochMs + 3L * 60L * 60L * 1000L
                            }
                        }
                    },
                value = formatter.format(Instant.ofEpochMilli(dueEpochMs)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Due") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                    )
                },
            )

            Column {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { repeatMenuExpanded = true },
                    value = repeatPreset.label,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Repeat") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Repeat,
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenu(
                    expanded = repeatMenuExpanded,
                    onDismissRequest = { repeatMenuExpanded = false },
                ) {
                    RepeatPreset.entries.forEach { repeat ->
                        DropdownMenuItem(
                            text = { Text(repeat.label) },
                            onClick = {
                                selectedRepeat = repeat.name
                                repeatMenuExpanded = false
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(
                    enabled = canCreate,
                    onClick = {
                        val start = Instant.ofEpochMilli(startEpochMs)
                        val due = if (dueEpochMs > startEpochMs) {
                            Instant.ofEpochMilli(dueEpochMs)
                        } else {
                            start.plusSeconds(3L * 60L * 60L)
                        }

                        onCreateTask(
                            CreateTaskPayload(
                                title = title,
                                description = description,
                                priority = selectedPriority,
                                dtstart = start,
                                due = due,
                                rrule = repeatPreset.rrule,
                                listId = selectedListId,
                            ),
                        )
                    },
                ) {
                    Text("Create")
                }
            }
        }
    }
}

private fun showDateTimePicker(
    context: android.content.Context,
    initialEpochMs: Long,
    onSelected: (Long) -> Unit,
) {
    val zoneId = ZoneId.systemDefault()
    val initial = ZonedDateTime.ofInstant(Instant.ofEpochMilli(initialEpochMs), zoneId)

    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val selected = ZonedDateTime.of(
                        year,
                        month + 1,
                        dayOfMonth,
                        hourOfDay,
                        minute,
                        0,
                        0,
                        zoneId,
                    )
                    onSelected(selected.toInstant().toEpochMilli())
                },
                initial.hour,
                initial.minute,
                true,
            ).show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}
