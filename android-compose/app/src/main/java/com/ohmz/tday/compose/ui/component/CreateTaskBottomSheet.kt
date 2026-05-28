package com.ohmz.tday.compose.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import android.graphics.Color as AndroidColor

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

private fun normalizePriorityValue(value: String?): String {
    return when (value?.trim()?.lowercase()) {
        "medium" -> "Medium"
        "high" -> "High"
        else -> "Low"
    }
}

private const val DEFAULT_TASK_DURATION_MS = 60L * 60L * 1000L
private const val CREATE_TASK_SHEET_MAX_HEIGHT_FRACTION = 0.86f
private const val CREATE_TASK_SHEET_NORMAL_HEIGHT_FRACTION = 0.70f
private const val CREATE_TASK_SHEET_KEYBOARD_HEIGHT_FRACTION = 0.85f
private const val CREATE_TASK_SHEET_MOTION_MS = 320

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskBottomSheet(
    lists: List<ListSummary>,
    editingTask: TodoItem? = null,
    defaultListId: String? = null,
    defaultPriority: String? = null,
    defaultScheduled: Boolean = true,
    initialDueEpochMs: Long? = null,
    onParseTaskTitleNlp: (suspend (
        title: String,
        referenceDueEpochMs: Long,
    ) -> TodoTitleNlpResponse?)? = null,
    onDismiss: () -> Unit,
    onCreateTask: (CreateTaskPayload) -> Unit,
    onUpdateTask: ((todo: TodoItem, payload: CreateTaskPayload) -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    val dateOnlyFormatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d").withZone(ZoneId.systemDefault())
    }
    val timeOnlyFormatter = remember {
        DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    }
    val listIdsKey = remember(lists) { lists.joinToString(separator = "|") { it.id } }

    val isEditMode = editingTask != null
    var title by rememberSaveable(editingTask?.id) {
        mutableStateOf(editingTask?.title.orEmpty())
    }
    var notes by rememberSaveable(editingTask?.id) {
        mutableStateOf(editingTask?.description.orEmpty())
    }
    var selectedPriority by rememberSaveable(editingTask?.id, defaultPriority) {
        mutableStateOf(
            normalizePriorityValue(editingTask?.priority ?: defaultPriority),
        )
    }
    var selectedListId by rememberSaveable(editingTask?.id, defaultListId, listIdsKey) {
        mutableStateOf(
            editingTask?.listId?.takeIf { id -> lists.any { it.id == id } }
                ?: defaultListId?.takeIf { id -> lists.any { it.id == id } },
        )
    }
    val nowEpochMs = remember { System.currentTimeMillis() }
    val resolvedDueEpochMs = editingTask?.due?.toEpochMilli()
        ?: (initialDueEpochMs ?: (nowEpochMs + DEFAULT_TASK_DURATION_MS))
    var dueEpochMs by rememberSaveable(editingTask?.id, initialDueEpochMs) {
        mutableStateOf(resolvedDueEpochMs)
    }
    var scheduleEnabled by rememberSaveable(editingTask?.id, defaultScheduled) {
        mutableStateOf(editingTask?.due != null || (editingTask == null && defaultScheduled))
    }
    LaunchedEffect(title, onParseTaskTitleNlp) {
        val nlpParser = onParseTaskTitleNlp ?: return@LaunchedEffect
        val inputTitle = title.trim()
        if (inputTitle.isBlank()) return@LaunchedEffect

        delay(260)
        val parseResult = runCatching {
            nlpParser(
                inputTitle,
                dueEpochMs,
            )
        }.getOrNull() ?: return@LaunchedEffect

        val parsedDueEpochMs = parseResult.dueEpochMs ?: return@LaunchedEffect

        val cleanTitle = parseResult.cleanTitle
        if (cleanTitle != title) {
            title = cleanTitle
        }
        if (!scheduleEnabled) {
            scheduleEnabled = true
        }
        if (parsedDueEpochMs != dueEpochMs) {
            dueEpochMs = parsedDueEpochMs
        }
    }
    var selectedRepeat by rememberSaveable(editingTask?.id) {
        mutableStateOf(repeatPresetFromRrule(editingTask?.rrule).name)
    }
    LaunchedEffect(scheduleEnabled) {
        if (!scheduleEnabled) {
            selectedRepeat = RepeatPreset.NONE.name
        }
    }
    var dueDatePickerOpen by rememberSaveable { mutableStateOf(false) }
    var dueTimePickerOpen by rememberSaveable { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }

    val selectedListName = lists.firstOrNull { it.id == selectedListId }?.name ?: "No list"
    val repeatPreset = if (scheduleEnabled) {
        RepeatPreset.valueOf(selectedRepeat)
    } else {
        RepeatPreset.NONE
    }
    val canSubmit = title.isNotBlank()
    val colorScheme = MaterialTheme.colorScheme
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
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val maxSheetHeight = screenHeight * CREATE_TASK_SHEET_MAX_HEIGHT_FRACTION
    val sheetHeight by animateDpAsState(
        targetValue = (screenHeight * if (keyboardVisible) {
            CREATE_TASK_SHEET_KEYBOARD_HEIGHT_FRACTION
        } else {
            CREATE_TASK_SHEET_NORMAL_HEIGHT_FRACTION
        }).coerceAtMost(maxSheetHeight),
        animationSpec = tween(
            durationMillis = CREATE_TASK_SHEET_MOTION_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "createTaskSheetHeight",
    )

    LaunchedEffect(Unit) {
        sheetVisible = true
    }

    fun submitTask() {
        val due = if (scheduleEnabled) Instant.ofEpochMilli(dueEpochMs) else null

        val payload = CreateTaskPayload(
            title = title.trim(),
            description = notes.trim().ifBlank { null },
            priority = selectedPriority,
            due = due,
            rrule = repeatPreset.rrule?.takeIf { scheduleEnabled },
            listId = selectedListId,
        )
        val editing = editingTask
        if (editing != null && onUpdateTask != null) {
            onUpdateTask(editing, payload)
        } else {
            onCreateTask(payload)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sheetScrimColor)
                    .clickable {
                        dismissKeyboard()
                        onDismiss()
                    },
            )

            AnimatedVisibility(
                visible = sheetVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = slideInVertically(
                    animationSpec = tween(
                        durationMillis = CREATE_TASK_SHEET_MOTION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                    initialOffsetY = { fullHeight -> fullHeight },
                ) + fadeIn(animationSpec = tween(durationMillis = CREATE_TASK_SHEET_MOTION_MS)),
                exit = slideOutVertically(
                    animationSpec = tween(
                        durationMillis = CREATE_TASK_SHEET_MOTION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                    targetOffsetY = { fullHeight -> fullHeight },
                ) + fadeOut(animationSpec = tween(durationMillis = CREATE_TASK_SHEET_MOTION_MS)),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                    shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
                    color = sheetContainerColor,
                    tonalElevation = if (isDarkTheme) 10.dp else 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                    SheetHeader(
                        title = if (isEditMode) "Edit task" else "New task",
                        leftIcon = Icons.Rounded.Close,
                        leftContentDescription = "Close",
                        onLeftClick = {
                            dismissKeyboard()
                            onDismiss()
                        },
                        onConfirm = {
                            dismissKeyboard()
                            if (canSubmit) {
                                submitTask()
                            }
                        },
                        confirmEnabled = canSubmit,
                    )

                    TaskTextCard(
                        title = title,
                        notes = notes,
                        onTitleChange = { title = it },
                        onNotesChange = { notes = it },
                        onKeyboardDone = dismissKeyboard,
                    )

                    SectionHeading("Schedule")
                    GroupCard {
                        ScheduleSwitchRow(
                            enabled = scheduleEnabled,
                            onEnabledChange = { enabled -> scheduleEnabled = enabled },
                        )
                        AnimatedVisibility(visible = scheduleEnabled) {
                            Column {
                                RowDivider()
                                SplitDateTimeRow(
                                    icon = Icons.Rounded.CalendarMonth,
                                    title = "Due",
                                    dateValue = dateOnlyFormatter.format(
                                        Instant.ofEpochMilli(
                                            dueEpochMs
                                        )
                                    ),
                                    timeValue = timeOnlyFormatter.format(
                                        Instant.ofEpochMilli(
                                            dueEpochMs
                                        )
                                    ),
                                    onDateClick = { dueDatePickerOpen = true },
                                    onTimeClick = { dueTimePickerOpen = true },
                                )
                            }
                        }
                    }

                    SectionHeading("Details")
                    GroupCard {
                        SheetDropdownRow(
                            icon = Icons.AutoMirrored.Rounded.List,
                            title = "List",
                            value = selectedListName,
                            options = listOf<ListSummary?>(null) + lists,
                            optionLabel = { option -> option?.name ?: "No list" },
                            optionSwatchColor = { option ->
                                option?.let {
                                    listColorSwatchForSelector(
                                        raw = it.color,
                                        fallback = colorScheme.primary.copy(alpha = 0.75f),
                                    )
                                } ?: colorScheme.outlineVariant.copy(alpha = 0.95f)
                            },
                            isSelected = { option -> option?.id == selectedListId },
                            onOptionSelected = { option -> selectedListId = option?.id },
                        )
                        RowDivider()
                        SheetDropdownRow(
                            icon = Icons.Rounded.LowPriority,
                            title = "Priority",
                            value = selectedPriority,
                            options = listOf("Low", "Medium", "High"),
                            optionLabel = { option -> option },
                            optionSwatchColor = { option -> prioritySwatchColor(option) },
                            isSelected = { option -> selectedPriority == option },
                            onOptionSelected = { option -> selectedPriority = option },
                        )
                        RowDivider()
                        SheetDropdownRow(
                            icon = Icons.Rounded.Repeat,
                            title = "Repeat",
                            value = repeatPreset.label,
                            options = if (scheduleEnabled) RepeatPreset.entries.toList() else listOf(
                                RepeatPreset.NONE
                            ),
                            optionLabel = { option -> option.label },
                            optionSwatchColor = { option -> repeatSwatchColor(option) },
                            isSelected = { option -> selectedRepeat == option.name },
                            onOptionSelected = { option -> selectedRepeat = option.name },
                        )
                    }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    if (dueDatePickerOpen) {
        ThemedDatePickerDialog(
            initialEpochMs = dueEpochMs,
            onDismiss = { dueDatePickerOpen = false },
            onConfirm = { pickedDateEpochMs ->
                dueEpochMs = mergeDateKeepingTime(
                    baseEpochMs = dueEpochMs,
                    selectedDateEpochMs = pickedDateEpochMs,
                )
                dueDatePickerOpen = false
            },
        )
    }

    if (dueTimePickerOpen) {
        ThemedTimePickerDialog(
            initialEpochMs = dueEpochMs,
            onDismiss = { dueTimePickerOpen = false },
            onConfirm = { pickedTimeEpochMs ->
                dueEpochMs = mergeTimeKeepingDate(
                    baseEpochMs = dueEpochMs,
                    selectedTimeEpochMs = pickedTimeEpochMs,
                )
                dueTimePickerOpen = false
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
            fontWeight = FontWeight.ExtraBold,
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
        fontWeight = FontWeight.ExtraBold,
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
    onKeyboardDone: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    GroupCard {
        TaskField(
            value = title,
            placeholder = "Title",
            onValueChange = onTitleChange,
            onKeyboardDone = onKeyboardDone,
        )
        RowDivider()
        TaskField(
            value = notes,
            placeholder = "Notes",
            onValueChange = onNotesChange,
            onKeyboardDone = onKeyboardDone,
        )
    }
}

@Composable
private fun TaskField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onKeyboardDone: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace('\n', ' ').replace('\r', ' ')) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                onKeyboardDone()
                defaultKeyboardAction(ImeAction.Done)
            },
        ),
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
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
                    fontWeight = FontWeight.ExtraBold,
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
            fontWeight = FontWeight.ExtraBold,
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
                    fontWeight = FontWeight.ExtraBold,
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
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ScheduleSwitchRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnabledChange(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Due date",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = if (enabled) "Scheduled" else "Anytime",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorScheme.onPrimary,
                checkedTrackColor = colorScheme.primary,
                uncheckedThumbColor = colorScheme.onSurfaceVariant,
                uncheckedTrackColor = colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

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
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.size(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 8.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.width(2.dp))

            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> SheetDropdownRow(
    icon: ImageVector,
    title: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    optionSwatchColor: (T) -> Color,
    isSelected: (T) -> Boolean,
    onOptionSelected: (T) -> Unit,
) {
    var selectorOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        SheetRow(
            icon = icon,
            title = title,
            value = value,
            onClick = { selectorOpen = true },
        )

        if (selectorOpen) {
            CenteredSelectorDialog(
                title = title,
                options = options,
                optionLabel = optionLabel,
                optionSwatchColor = optionSwatchColor,
                isSelected = isSelected,
                onDismiss = { selectorOpen = false },
                onOptionSelected = { option ->
                    onOptionSelected(option)
                    selectorOpen = false
                },
            )
        }
    }
}

@Composable
private fun <T> CenteredSelectorDialog(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    optionSwatchColor: (T) -> Color,
    isSelected: (T) -> Boolean,
    onDismiss: () -> Unit,
    onOptionSelected: (T) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val containerColor = if (isDark) {
        lerp(colorScheme.surface, colorScheme.surfaceVariant, 0.18f)
    } else {
        colorScheme.surface
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.74f)
                .heightIn(max = 380.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )

                options.forEachIndexed { index, option ->
                    if (index > 0) {
                        RowDivider()
                    }
                    CenteredSelectorRow(
                        title = optionLabel(option),
                        swatchColor = optionSwatchColor(option),
                        selected = isSelected(option),
                        onClick = { onOptionSelected(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredSelectorRow(
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
            .padding(horizontal = 18.dp, vertical = 14.dp),
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

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(modifier = Modifier.size(20.dp))
        }
    }
}

private fun listColorSwatchForSelector(raw: String?, fallback: Color): Color {
    if (raw.isNullOrBlank()) return fallback
    return when (raw.trim().uppercase()) {
        "PINK" -> Color(0xFFC987A5)
        "GOLD" -> Color(0xFFC7AA63)
        "DEEP_BLUE" -> Color(0xFF6F86C6)
        "CORAL" -> Color(0xFFD39A82)
        "TEAL" -> Color(0xFF67AAA7)
        "SLATE", "GRAY" -> Color(0xFF7F8996)
        "BLUE" -> Color(0xFF6F9FCE)
        "PURPLE" -> Color(0xFF9A86CF)
        "ROSE" -> Color(0xFFC98299)
        "LIGHT_RED" -> Color(0xFFD58D8D)
        "BRICK" -> Color(0xFFAD786E)
        "YELLOW" -> Color(0xFFCFB866)
        "LIME", "GREEN" -> Color(0xFF8DBB73)
        "ORANGE" -> Color(0xFFD69B63)
        "RED" -> Color(0xFFD97873)
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

private fun repeatPresetFromRrule(rrule: String?): RepeatPreset {
    if (rrule.isNullOrBlank()) return RepeatPreset.NONE
    return RepeatPreset.entries.firstOrNull { it.rrule == rrule } ?: RepeatPreset.NONE
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
                        fontWeight = FontWeight.ExtraBold,
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
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = "Done",
                            color = primaryText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
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
