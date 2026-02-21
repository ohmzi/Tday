package com.ohmz.tday.compose.ui.component

import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    LIST,
    PRIORITY,
    REPEAT,
}

private enum class DatePickerTarget {
    START,
    DUE,
}

private enum class TimePickerTarget {
    START,
    DUE,
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

private const val DEFAULT_TASK_DURATION_MS = 60L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskBottomSheet(
    lists: List<ListSummary>,
    defaultListId: String? = null,
    initialStartEpochMs: Long? = null,
    initialDueEpochMs: Long? = null,
    onDismiss: () -> Unit,
    onCreateTask: (CreateTaskPayload) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateOnlyFormatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d").withZone(ZoneId.systemDefault())
    }
    val timeOnlyFormatter = remember {
        DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    }
    val listIdsKey = remember(lists) { lists.joinToString(separator = "|") { it.id } }

    var page by rememberSaveable { mutableStateOf(TaskSheetPage.MAIN) }
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var selectedPriority by rememberSaveable { mutableStateOf("Low") }
    var selectedListId by rememberSaveable(defaultListId, listIdsKey) {
        mutableStateOf(defaultListId?.takeIf { id -> lists.any { it.id == id } })
    }
    val nowEpochMs = remember { System.currentTimeMillis() }
    val resolvedStartEpochMs = initialStartEpochMs ?: nowEpochMs
    val resolvedDueEpochMs = initialDueEpochMs ?: (resolvedStartEpochMs + DEFAULT_TASK_DURATION_MS)
    var startEpochMs by rememberSaveable(initialStartEpochMs, initialDueEpochMs) {
        mutableStateOf(resolvedStartEpochMs)
    }
    var dueEpochMs by rememberSaveable(initialStartEpochMs, initialDueEpochMs) {
        mutableStateOf(
            if (resolvedDueEpochMs > resolvedStartEpochMs) {
                resolvedDueEpochMs
            } else {
                resolvedStartEpochMs + DEFAULT_TASK_DURATION_MS
            },
        )
    }
    var selectedRepeat by rememberSaveable { mutableStateOf(RepeatPreset.NONE.name) }
    var listReturnPage by rememberSaveable { mutableStateOf(TaskSheetPage.MAIN.name) }
    var priorityReturnPage by rememberSaveable { mutableStateOf(TaskSheetPage.DETAILS.name) }
    var repeatReturnPage by rememberSaveable { mutableStateOf(TaskSheetPage.DETAILS.name) }
    var datePickerTarget by rememberSaveable { mutableStateOf<DatePickerTarget?>(null) }
    var timePickerTarget by rememberSaveable { mutableStateOf<TimePickerTarget?>(null) }

    val selectedListName = lists.firstOrNull { it.id == selectedListId }?.name ?: "No list"
    val repeatPreset = RepeatPreset.valueOf(selectedRepeat)
    val canCreate = title.isNotBlank()

    fun submitTask() {
        val start = Instant.ofEpochMilli(startEpochMs)
        val due = if (dueEpochMs > startEpochMs) {
            Instant.ofEpochMilli(dueEpochMs)
        } else {
            start.plusSeconds(DEFAULT_TASK_DURATION_MS / 1000L)
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
                    title = when (page) {
                        TaskSheetPage.MAIN -> "New task"
                        TaskSheetPage.DETAILS -> "Details"
                        TaskSheetPage.LIST -> "List"
                        TaskSheetPage.PRIORITY -> "Priority"
                        TaskSheetPage.REPEAT -> "Repeat"
                    },
                    leftIcon = if (page == TaskSheetPage.MAIN) {
                        Icons.Rounded.Close
                    } else {
                        Icons.AutoMirrored.Rounded.ArrowBack
                    },
                    leftContentDescription = if (page == TaskSheetPage.MAIN) "Close" else "Back",
                    onLeftClick = {
                        focusManager.clearFocus(force = true)
                        when (page) {
                            TaskSheetPage.MAIN -> onDismiss()
                            TaskSheetPage.DETAILS -> page = TaskSheetPage.MAIN
                            TaskSheetPage.LIST -> page = TaskSheetPage.valueOf(listReturnPage)
                            TaskSheetPage.PRIORITY -> page = TaskSheetPage.valueOf(priorityReturnPage)
                            TaskSheetPage.REPEAT -> page = TaskSheetPage.valueOf(repeatReturnPage)
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
                            SplitDateTimeRow(
                                icon = Icons.Rounded.Schedule,
                                title = "Start",
                                dateValue = dateOnlyFormatter.format(Instant.ofEpochMilli(startEpochMs)),
                                timeValue = timeOnlyFormatter.format(Instant.ofEpochMilli(startEpochMs)),
                                onDateClick = { datePickerTarget = DatePickerTarget.START },
                                onTimeClick = { timePickerTarget = TimePickerTarget.START },
                            )
                            RowDivider()
                            SplitDateTimeRow(
                                icon = Icons.Rounded.CalendarMonth,
                                title = "Due",
                                dateValue = dateOnlyFormatter.format(Instant.ofEpochMilli(dueEpochMs)),
                                timeValue = timeOnlyFormatter.format(Instant.ofEpochMilli(dueEpochMs)),
                                onDateClick = { datePickerTarget = DatePickerTarget.DUE },
                                onTimeClick = { timePickerTarget = TimePickerTarget.DUE },
                            )
                        }

                        SectionHeading("More Options")
                        GroupCard {
                            if (lists.isNotEmpty()) {
                                SheetRow(
                                    icon = Icons.AutoMirrored.Rounded.List,
                                    title = "List",
                                    value = selectedListName,
                                    onClick = {
                                        listReturnPage = TaskSheetPage.MAIN.name
                                        page = TaskSheetPage.LIST
                                    },
                                )
                                RowDivider()
                            }
                            SheetRow(
                                icon = Icons.Rounded.LowPriority,
                                title = "Priority",
                                value = selectedPriority,
                                onClick = {
                                    priorityReturnPage = TaskSheetPage.MAIN.name
                                    page = TaskSheetPage.PRIORITY
                                },
                            )
                            RowDivider()
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
                            SplitDateTimeRow(
                                icon = Icons.Rounded.Schedule,
                                title = "Start",
                                dateValue = dateOnlyFormatter.format(Instant.ofEpochMilli(startEpochMs)),
                                timeValue = timeOnlyFormatter.format(Instant.ofEpochMilli(startEpochMs)),
                                onDateClick = { datePickerTarget = DatePickerTarget.START },
                                onTimeClick = { timePickerTarget = TimePickerTarget.START },
                            )
                            RowDivider()
                            SplitDateTimeRow(
                                icon = Icons.Rounded.CalendarMonth,
                                title = "Due",
                                dateValue = dateOnlyFormatter.format(Instant.ofEpochMilli(dueEpochMs)),
                                timeValue = timeOnlyFormatter.format(Instant.ofEpochMilli(dueEpochMs)),
                                onDateClick = { datePickerTarget = DatePickerTarget.DUE },
                                onTimeClick = { timePickerTarget = TimePickerTarget.DUE },
                            )
                            RowDivider()
                            SheetRow(
                                icon = Icons.Rounded.Repeat,
                                title = "Repeat",
                                value = repeatPreset.label,
                                onClick = {
                                    repeatReturnPage = TaskSheetPage.DETAILS.name
                                    page = TaskSheetPage.REPEAT
                                },
                            )
                        }

                        SectionHeading("Organization")
                        GroupCard {
                            SheetRow(
                                icon = Icons.Rounded.LowPriority,
                                title = "Priority",
                                value = selectedPriority,
                                onClick = {
                                    priorityReturnPage = TaskSheetPage.DETAILS.name
                                    page = TaskSheetPage.PRIORITY
                                },
                            )

                            if (lists.isNotEmpty()) {
                                RowDivider()
                                SheetRow(
                                    icon = Icons.AutoMirrored.Rounded.List,
                                    title = "List",
                                    value = selectedListName,
                                    onClick = {
                                        listReturnPage = TaskSheetPage.DETAILS.name
                                        page = TaskSheetPage.LIST
                                    },
                                )
                            }
                        }
                    }

                    TaskSheetPage.LIST -> {
                        SectionHeading("Choose List")
                        GroupCard {
                            ListSelectionRow(
                                title = "No list",
                                swatchColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.95f),
                                selected = selectedListId == null,
                                onClick = {
                                    selectedListId = null
                                    page = TaskSheetPage.valueOf(listReturnPage)
                                },
                            )
                            lists.forEach { list ->
                                RowDivider()
                                ListSelectionRow(
                                    title = list.name,
                                    swatchColor = listColorSwatchForSelector(
                                        raw = list.color,
                                        fallback = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                    ),
                                    selected = selectedListId == list.id,
                                    onClick = {
                                        selectedListId = list.id
                                        page = TaskSheetPage.valueOf(listReturnPage)
                                    },
                                )
                            }
                        }
                    }

                    TaskSheetPage.PRIORITY -> {
                        SectionHeading("Choose Priority")
                        GroupCard {
                            listOf("Low", "Medium", "High").forEachIndexed { index, option ->
                                if (index > 0) {
                                    RowDivider()
                                }
                                ListSelectionRow(
                                    title = option,
                                    swatchColor = prioritySwatchColor(option),
                                    selected = selectedPriority == option,
                                    onClick = {
                                        selectedPriority = option
                                        page = TaskSheetPage.valueOf(priorityReturnPage)
                                    },
                                )
                            }
                        }
                    }

                    TaskSheetPage.REPEAT -> {
                        SectionHeading("Choose Repeat")
                        GroupCard {
                            RepeatPreset.entries.forEachIndexed { index, option ->
                                if (index > 0) {
                                    RowDivider()
                                }
                                ListSelectionRow(
                                    title = option.label,
                                    swatchColor = repeatSwatchColor(option),
                                    selected = selectedRepeat == option.name,
                                    onClick = {
                                        selectedRepeat = option.name
                                        page = TaskSheetPage.valueOf(repeatReturnPage)
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    datePickerTarget?.let { target ->
        ThemedDatePickerDialog(
            initialEpochMs = if (target == DatePickerTarget.START) startEpochMs else dueEpochMs,
            onDismiss = { datePickerTarget = null },
            onConfirm = { pickedDateEpochMs ->
                when (target) {
                    DatePickerTarget.START -> {
                        startEpochMs = mergeDateKeepingTime(
                            baseEpochMs = startEpochMs,
                            selectedDateEpochMs = pickedDateEpochMs,
                        )
                        dueEpochMs = startEpochMs + DEFAULT_TASK_DURATION_MS
                    }

                    DatePickerTarget.DUE -> {
                        dueEpochMs = mergeDateKeepingTime(
                            baseEpochMs = dueEpochMs,
                            selectedDateEpochMs = pickedDateEpochMs,
                        )
                        startEpochMs = dueEpochMs - DEFAULT_TASK_DURATION_MS
                    }
                }
                datePickerTarget = null
            },
        )
    }

    timePickerTarget?.let { target ->
        ThemedTimePickerDialog(
            initialEpochMs = if (target == TimePickerTarget.START) startEpochMs else dueEpochMs,
            onDismiss = { timePickerTarget = null },
            onConfirm = { pickedTimeEpochMs ->
                when (target) {
                    TimePickerTarget.START -> {
                        startEpochMs = mergeTimeKeepingDate(
                            baseEpochMs = startEpochMs,
                            selectedTimeEpochMs = pickedTimeEpochMs,
                        )
                        dueEpochMs = startEpochMs + DEFAULT_TASK_DURATION_MS
                    }

                    TimePickerTarget.DUE -> {
                        dueEpochMs = mergeTimeKeepingDate(
                            baseEpochMs = dueEpochMs,
                            selectedTimeEpochMs = pickedTimeEpochMs,
                        )
                        startEpochMs = dueEpochMs - DEFAULT_TASK_DURATION_MS
                    }
                }
                timePickerTarget = null
            },
        )
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
            style = MaterialTheme.typography.titleLarge,
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
        style = MaterialTheme.typography.titleLarge,
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
        textStyle = MaterialTheme.typography.titleMedium.copy(
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
                    style = MaterialTheme.typography.titleMedium,
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
private fun SplitDateTimeRow(
    icon: ImageVector,
    title: String,
    dateValue: String,
    timeValue: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val splitShape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.size(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        Row(
            modifier = Modifier
                .weight(1.45f)
                .clip(splitShape)
                .border(
                    width = 1.dp,
                    color = colorScheme.outlineVariant.copy(alpha = 0.55f),
                    shape = splitShape,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onDateClick)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dateValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onTimeClick)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = timeValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        if (value.isNotBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
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

@Composable
private fun ListSelectionRow(
    title: String,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = swatchColor,
                    shape = RoundedCornerShape(999.dp),
                ),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun listColorSwatchForSelector(raw: String?, fallback: Color): Color {
    if (raw.isNullOrBlank()) return fallback
    return when (raw.trim().uppercase()) {
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
        else -> runCatching { Color(AndroidColor.parseColor(raw)) }
            .getOrDefault(fallback)
    }
}

private fun prioritySwatchColor(priority: String): Color {
    return when (priority.lowercase()) {
        "high" -> Color(0xFFE56A6A)
        "medium" -> Color(0xFFE3B368)
        else -> Color(0xFF6FBF86)
    }
}

private fun repeatSwatchColor(preset: RepeatPreset): Color {
    return when (preset) {
        RepeatPreset.NONE -> Color(0xFFB7BCC8)
        RepeatPreset.DAILY -> Color(0xFF6FBF86)
        RepeatPreset.WEEKLY -> Color(0xFF6FA6E8)
        RepeatPreset.WEEKDAYS -> Color(0xFF8C7AE6)
        RepeatPreset.MONTHLY -> Color(0xFFE3B368)
        RepeatPreset.YEARLY -> Color(0xFFE56A6A)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemedDatePickerDialog(
    initialEpochMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val now = remember(zoneId) { ZonedDateTime.now(zoneId) }
    val initialDateEpochMs = remember(initialEpochMs, zoneId) {
        ZonedDateTime
            .ofInstant(Instant.ofEpochMilli(initialEpochMs), zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }
    val currentYear = now.year
    val minMonthStartEpochMs = remember(zoneId, now.year, now.monthValue) {
        ZonedDateTime
            .of(now.year, now.monthValue, 1, 0, 0, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()
    }
    val boundedInitialDateEpochMs = maxOf(initialDateEpochMs, minMonthStartEpochMs)
    val selectableDates = remember(minMonthStartEpochMs, currentYear) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis >= minMonthStartEpochMs
            }

            override fun isSelectableYear(year: Int): Boolean {
                return year >= currentYear
            }
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = boundedInitialDateEpochMs,
        yearRange = currentYear..(currentYear + 100),
        selectableDates = selectableDates,
    )
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val pickerAccent = lerp(
        colorScheme.onSurfaceVariant,
        colorScheme.onSurface,
        if (isDark) 0.14f else 0.28f,
    )
    val dialogContainer = colorScheme.background
    val calendarSurface = if (isDark) colorScheme.surface else Color.White
    val primaryText = colorScheme.onSurface
    val mutedText = colorScheme.onSurfaceVariant
    val selectedContentColor = if (pickerAccent.luminance() > 0.45f) colorScheme.surface else Color.White
    val pickerColors = DatePickerDefaults.colors(
        containerColor = calendarSurface,
        titleContentColor = mutedText,
        headlineContentColor = primaryText,
        weekdayContentColor = mutedText,
        subheadContentColor = mutedText,
        navigationContentColor = primaryText,
        yearContentColor = primaryText,
        currentYearContentColor = pickerAccent,
        selectedYearContentColor = selectedContentColor,
        selectedYearContainerColor = pickerAccent,
        dayContentColor = primaryText,
        selectedDayContentColor = selectedContentColor,
        selectedDayContainerColor = pickerAccent,
        todayContentColor = pickerAccent,
        todayDateBorderColor = Color.Transparent,
        dayInSelectionRangeContentColor = primaryText,
        dayInSelectionRangeContainerColor = pickerAccent.copy(alpha = if (isDark) 0.24f else 0.12f),
        dividerColor = Color.Transparent,
    )

    SpectrumPickerDialog(
        onDismiss = onDismiss,
        title = "Select date",
        titleIcon = Icons.Rounded.CalendarMonth,
        accent = pickerAccent,
        primaryText = primaryText,
        mutedText = mutedText,
        containerColor = dialogContainer,
        panelColor = calendarSurface,
        dialogWidthFraction = 0.97f,
        onConfirm = { onConfirm(pickerState.selectedDateMillis ?: boundedInitialDateEpochMs) },
    ) {
        DatePicker(
            modifier = Modifier.fillMaxWidth(),
            state = pickerState,
            showModeToggle = false,
            title = null,
            headline = null,
            colors = pickerColors,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemedTimePickerDialog(
    initialEpochMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val initial = remember(initialEpochMs, zoneId) {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(initialEpochMs), zoneId)
    }
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val pickerAccent = lerp(
        colorScheme.onSurfaceVariant,
        colorScheme.onSurface,
        if (isDark) 0.14f else 0.28f,
    )
    val dialogContainer = colorScheme.background
    val pickerSurface = colorScheme.surface
    val primaryText = colorScheme.onSurface
    val mutedText = colorScheme.onSurfaceVariant
    val selectedContentColor = if (pickerAccent.luminance() > 0.45f) colorScheme.surface else Color.White
    val unselectedChipText = if (isDark) {
        colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    } else {
        colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    val unselectedChipContainer = if (isDark) {
        colorScheme.surfaceVariant.copy(alpha = 0.32f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.44f)
    }
    val pickerColors = TimePickerDefaults.colors(
        clockDialColor = pickerSurface,
        clockDialSelectedContentColor = selectedContentColor,
        clockDialUnselectedContentColor = primaryText.copy(alpha = 0.9f),
        selectorColor = pickerAccent,
        containerColor = Color.Transparent,
        periodSelectorBorderColor = Color.Transparent,
        periodSelectorSelectedContainerColor = pickerAccent,
        periodSelectorUnselectedContainerColor = unselectedChipContainer,
        periodSelectorSelectedContentColor = selectedContentColor,
        periodSelectorUnselectedContentColor = unselectedChipText,
        timeSelectorSelectedContainerColor = pickerAccent,
        timeSelectorUnselectedContainerColor = unselectedChipContainer,
        timeSelectorSelectedContentColor = selectedContentColor,
        timeSelectorUnselectedContentColor = unselectedChipText,
    )

    SpectrumPickerDialog(
        onDismiss = onDismiss,
        title = "Select time",
        titleIcon = Icons.Rounded.Schedule,
        accent = pickerAccent,
        primaryText = primaryText,
        mutedText = mutedText,
        containerColor = dialogContainer,
        panelColor = pickerSurface,
        onConfirm = {
            val selected = initial
                .withHour(pickerState.hour)
                .withMinute(pickerState.minute)
                .withSecond(0)
                .withNano(0)
            onConfirm(selected.toInstant().toEpochMilli())
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            TimePicker(
                state = pickerState,
                colors = pickerColors,
            )
        }
    }
}

@Composable
private fun SpectrumPickerDialog(
    onDismiss: () -> Unit,
    title: String,
    titleIcon: ImageVector,
    accent: Color,
    primaryText: Color,
    mutedText: Color,
    containerColor: Color,
    panelColor: Color,
    dialogWidthFraction: Float = 0.92f,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val outerShape = RoundedCornerShape(34.dp)
    val innerShape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(dialogWidthFraction),
            shape = outerShape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = titleIcon,
                        contentDescription = null,
                        tint = mutedText,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = innerShape,
                    colors = CardDefaults.cardColors(containerColor = panelColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    content()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = mutedText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = "Done",
                            color = primaryText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun mergeDateKeepingTime(
    baseEpochMs: Long,
    selectedDateEpochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(baseEpochMs), zoneId)
    val selectedDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(selectedDateEpochMs), zoneId).toLocalDate()
    return ZonedDateTime.of(selectedDate, base.toLocalTime(), zoneId)
        .toInstant()
        .toEpochMilli()
}

private fun mergeTimeKeepingDate(
    baseEpochMs: Long,
    selectedTimeEpochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(baseEpochMs), zoneId)
    val selectedTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(selectedTimeEpochMs), zoneId).toLocalTime()
    return ZonedDateTime.of(base.toLocalDate(), selectedTime, zoneId)
        .toInstant()
        .toEpochMilli()
}
