package com.ohmz.tday.compose.ui.component

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LowPriority
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private enum class TaskSheetPage {
    MAIN,
    DETAILS,
}

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
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d • HH:mm").withZone(ZoneId.systemDefault())
    }
    val listIdsKey = remember(lists) { lists.joinToString(separator = "|") { it.id } }

    var page by rememberSaveable { mutableStateOf(TaskSheetPage.MAIN) }
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var selectedPriority by rememberSaveable { mutableStateOf("Low") }
    var selectedListId by rememberSaveable(defaultListId, listIdsKey) {
        mutableStateOf(defaultListId ?: lists.firstOrNull()?.id)
    }
    var startEpochMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var dueEpochMs by rememberSaveable { mutableStateOf(System.currentTimeMillis() + 3L * 60L * 60L * 1000L) }
    var selectedRepeat by rememberSaveable { mutableStateOf(RepeatPreset.NONE.name) }

    var listMenuExpanded by remember { mutableStateOf(false) }
    var priorityMenuExpanded by remember { mutableStateOf(false) }
    var repeatMenuExpanded by remember { mutableStateOf(false) }

    val selectedListName = lists.firstOrNull { it.id == selectedListId }?.name.orEmpty()
    val repeatPreset = RepeatPreset.valueOf(selectedRepeat)
    val canCreate = title.isNotBlank() && (lists.isEmpty() || !selectedListId.isNullOrBlank())

    fun submitTask() {
        val start = Instant.ofEpochMilli(startEpochMs)
        val due = if (dueEpochMs > startEpochMs) {
            Instant.ofEpochMilli(dueEpochMs)
        } else {
            start.plusSeconds(3L * 60L * 60L)
        }

        onCreateTask(
            CreateTaskPayload(
                title = title.trim(),
                description = notes.trim().ifBlank { null },
                priority = selectedPriority,
                dtstart = start,
                due = due,
                rrule = repeatPreset.rrule,
                listId = selectedListId,
            ),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
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
                SheetHeader(
                    title = if (page == TaskSheetPage.MAIN) "New task" else "Details",
                    leftIcon = if (page == TaskSheetPage.MAIN) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                    leftContentDescription = if (page == TaskSheetPage.MAIN) "Close" else "Back",
                    onLeftClick = {
                        focusManager.clearFocus(force = true)
                        if (page == TaskSheetPage.MAIN) {
                            onDismiss()
                        } else {
                            page = TaskSheetPage.MAIN
                        }
                    },
                    onConfirm = {
                        focusManager.clearFocus(force = true)
                        if (canCreate) {
                            submitTask()
                        }
                    },
                    confirmEnabled = canCreate,
                )

                when (page) {
                    TaskSheetPage.MAIN -> {
                        TaskTextCard(
                            title = title,
                            notes = notes,
                            onTitleChange = { title = it },
                            onNotesChange = { notes = it },
                        )

                        SectionHeading("Date & Time")
                        GroupCard {
                            SheetRow(
                                icon = Icons.Rounded.Schedule,
                                title = "Starts",
                                value = dateFormatter.format(Instant.ofEpochMilli(startEpochMs)),
                                onClick = {
                                    showDateTimePicker(context, startEpochMs) { picked ->
                                        startEpochMs = picked
                                        if (dueEpochMs <= picked) {
                                            dueEpochMs = picked + 3L * 60L * 60L * 1000L
                                        }
                                    }
                                },
                            )
                            RowDivider()
                            SheetRow(
                                icon = Icons.Rounded.CalendarMonth,
                                title = "Due",
                                value = dateFormatter.format(Instant.ofEpochMilli(dueEpochMs)),
                                onClick = {
                                    showDateTimePicker(context, dueEpochMs) { picked ->
                                        dueEpochMs = if (picked > startEpochMs) {
                                            picked
                                        } else {
                                            startEpochMs + 3L * 60L * 60L * 1000L
                                        }
                                    }
                                },
                            )
                        }

                        SectionHeading("More Options")
                        GroupCard {
                            if (lists.isNotEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SheetRow(
                                        icon = Icons.AutoMirrored.Rounded.List,
                                        title = "List",
                                        value = selectedListName,
                                        onClick = { listMenuExpanded = true },
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
                                RowDivider()
                            }
                            SheetRow(
                                icon = Icons.Rounded.Info,
                                title = "Details",
                                value = "",
                                forceChevron = true,
                                onClick = { page = TaskSheetPage.DETAILS },
                            )
                        }
                    }

                    TaskSheetPage.DETAILS -> {
                        SectionHeading("Scheduling")
                        GroupCard {
                            SheetRow(
                                icon = Icons.Rounded.Schedule,
                                title = "Starts",
                                value = dateFormatter.format(Instant.ofEpochMilli(startEpochMs)),
                                onClick = {
                                    showDateTimePicker(context, startEpochMs) { picked ->
                                        startEpochMs = picked
                                        if (dueEpochMs <= picked) {
                                            dueEpochMs = picked + 3L * 60L * 60L * 1000L
                                        }
                                    }
                                },
                            )
                            RowDivider()
                            SheetRow(
                                icon = Icons.Rounded.CalendarMonth,
                                title = "Due",
                                value = dateFormatter.format(Instant.ofEpochMilli(dueEpochMs)),
                                onClick = {
                                    showDateTimePicker(context, dueEpochMs) { picked ->
                                        dueEpochMs = if (picked > startEpochMs) {
                                            picked
                                        } else {
                                            startEpochMs + 3L * 60L * 60L * 1000L
                                        }
                                    }
                                },
                            )
                            RowDivider()
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SheetRow(
                                    icon = Icons.Rounded.Repeat,
                                    title = "Repeat",
                                    value = repeatPreset.label,
                                    onClick = { repeatMenuExpanded = true },
                                )
                                DropdownMenu(
                                    expanded = repeatMenuExpanded,
                                    onDismissRequest = { repeatMenuExpanded = false },
                                ) {
                                    RepeatPreset.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                selectedRepeat = option.name
                                                repeatMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        SectionHeading("Organization")
                        GroupCard {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SheetRow(
                                    icon = Icons.Rounded.LowPriority,
                                    title = "Priority",
                                    value = selectedPriority,
                                    onClick = { priorityMenuExpanded = true },
                                )
                                DropdownMenu(
                                    expanded = priorityMenuExpanded,
                                    onDismissRequest = { priorityMenuExpanded = false },
                                ) {
                                    listOf("Low", "Medium", "High").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                selectedPriority = option
                                                priorityMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }

                            if (lists.isNotEmpty()) {
                                RowDivider()
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    SheetRow(
                                        icon = Icons.AutoMirrored.Rounded.List,
                                        title = "List",
                                        value = selectedListName,
                                        onClick = { listMenuExpanded = true },
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
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    leftIcon: ImageVector,
    leftContentDescription: String,
    onLeftClick: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleActionButton(
            icon = leftIcon,
            contentDescription = leftContentDescription,
            onClick = onLeftClick,
            enabled = true,
            accentColor = Color(0xFFE35A5A),
        )

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        CircleActionButton(
            icon = Icons.Rounded.Check,
            contentDescription = "Create task",
            onClick = onConfirm,
            enabled = confirmEnabled,
            accentColor = Color(0xFF2FA35B),
        )
    }
}

@Composable
private fun CircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    accentColor: Color,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.93f else 1f,
        label = "sheetHeaderButtonScale",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            pressed && enabled -> 2.dp
            enabled -> 8.dp
            else -> 5.dp
        },
        label = "sheetHeaderButtonElevation",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed && enabled) 1.dp else 0.dp,
        label = "sheetHeaderButtonOffsetY",
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
        shape = RoundedCornerShape(999.dp),
        enabled = enabled,
        onClick = {
            if (enabled) {
                performGentleHaptic(view)
            }
            onClick()
        },
        interactionSource = interactionSource,
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

private fun performGentleHaptic(view: android.view.View) {
    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun TaskTextCard(
    title: String,
    notes: String,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    GroupCard {
        TaskField(
            value = title,
            placeholder = "Title",
            onValueChange = onTitleChange,
            singleLine = true,
        )
        RowDivider()
        TaskField(
            value = notes,
            placeholder = "Notes",
            onValueChange = onNotesChange,
            singleLine = false,
        )
    }
}

@Composable
private fun TaskField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            innerTextField()
        },
    )
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    )
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    forceChevron: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.size(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        if (value.isNotBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (forceChevron || value.isNotBlank()) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
