package com.ohmz.tday.compose.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TaskRescheduleScope
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.core.sound.rememberTaskCompletionSound
import com.ohmz.tday.compose.core.ui.EmptyTaskWatermark
import com.ohmz.tday.compose.core.ui.LocalSnackbarManager
import com.ohmz.tday.compose.core.ui.TdayDragLift
import com.ohmz.tday.compose.core.ui.TdayEmptyState
import com.ohmz.tday.compose.core.ui.TdayFeedItemMotion
import com.ohmz.tday.compose.core.ui.TdayHaptics
import com.ohmz.tday.compose.core.ui.TdayHeroToolbar
import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import com.ohmz.tday.compose.core.ui.TdaySearchCapsule
import com.ohmz.tday.compose.core.ui.animateTaskSwipeOffsetAsState
import com.ohmz.tday.compose.core.ui.rememberLazyListHeroTitleCollapse
import com.ohmz.tday.compose.core.ui.rememberTaskStrikeProgress
import com.ohmz.tday.compose.core.ui.rememberTaskSwipeRevealState
import com.ohmz.tday.compose.core.ui.rememberTdayMotionEnabled
import com.ohmz.tday.compose.core.ui.rememberTdayMotionScale
import com.ohmz.tday.compose.core.ui.scaledDelay
import com.ohmz.tday.compose.core.ui.taskCopyText
import com.ohmz.tday.compose.core.ui.taskStrikethrough
import com.ohmz.tday.compose.core.ui.tdayBarButtonContainerColor
import com.ohmz.tday.compose.core.ui.tdayHeroTitleItem
import com.ohmz.tday.compose.core.ui.TdayHeroTitleMetrics
import com.ohmz.tday.compose.core.ui.tdayClosesSearchOnOutsideTap
import com.ohmz.tday.compose.core.ui.tdayPressable
import com.ohmz.tday.compose.ui.component.CreateTaskBottomSheet
import com.ohmz.tday.compose.ui.component.rememberEditSheetTarget
import com.ohmz.tday.compose.ui.component.TdaySegmentedSlider
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdaySwipeCopyBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeDeleteBackground
import com.ohmz.tday.compose.ui.theme.TdaySwipeEditBackground
import com.ohmz.tday.compose.ui.theme.TdayTaskCompleteAccent
import com.ohmz.tday.compose.ui.theme.tdayListAccentColor
import com.ohmz.tday.compose.ui.theme.tdayListIconForKey
import com.ohmz.tday.compose.ui.theme.tdayPriorityColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt
import com.ohmz.tday.compose.core.text.flattenNotesToPlainText

private val CalendarAccentPurple = Color(0xFF7D67B6)

/**
 * The same accent, lightened for dark, and only for the places it is used as
 * INK on a bar control rather than as a fill that composites.
 *
 * Hue and saturation are held exactly — HSL 256.7 deg / 0.351, the accent's own
 * — and lightness goes 0.559 to 0.70. That is the one axis contrast answers to,
 * so it is the only one moved. What it is read against is
 * `lerp(background, onBackground, 0.12f)`, and `androidx.compose.ui.graphics.lerp`
 * mixes in Oklab rather than per channel — it converts both ends through
 * `ColorSpaces.Oklab` first — so the static dark fill is #1A1A1D where a
 * channel-wise mix predicts #222224, and every ratio taken against the wrong
 * one comes out about 8% flat. On #1A1A1D the accent at 0.559 is 3.71:1; at
 * 0.70 it is 6.63:1. The same lerp over the Material You neutral-variant space
 * runs lightest at #323342 (tone-12 background, Lab chroma 10, hue 285), where
 * 0.559 falls to 2.66:1 and 0.70 still holds 4.75:1 — so the lift clears 4.5:1
 * on every dark scheme rather than by a rounding.
 *
 * `TdayPriorityLowest`/`TdayPriorityLowestDark` is the same move on the same
 * rule, and `TdayLightPrimary`/`TdayDarkPrimary` lift further than this does.
 *
 * It is deliberately NOT swapped into the accent's other call sites on this
 * screen — but not because they are all fills. Four of them are ink.
 * `titleColor` on `tdayHeroTitleItem` and on `TdayHeroToolbar` is the hero and
 * the docked "Calendar" at 32sp ExtraBold: WCAG large text, which owes 3:1 and
 * has it — 4.33:1 light, 4.35:1 on the static dark background, 3.88:1 at the
 * worst dynamic dark. The other two are the day-cell task counts, `stateTint`'s
 * `else ->` branch reaching `Text` in `CalendarWeekDayCell` and
 * `CalendarDayCell` at 11sp ExtraBold, which is NOT large text: it owes 4.5:1
 * and misses, at 4.35:1 static dark and 3.48-3.97:1 across dynamic dark. Worse
 * again is the selected day number, the accent inked on its own 24% accent
 * wash — 3.23:1 light, 2.78-3.50:1 dark. Those sites are real, pre-existing and
 * still failing, and they are deliberately out of scope here rather than fixed
 * in passing: the grid's ink, washes and borders are one colour system and move
 * together or not at all. The cost of leaving them is that dark carries two
 * purples as ink on one screen — this one in the bar, the accent in the grid,
 * 1.79:1 apart in luminance — and that is the follow-up, not a reason to
 * half-do it here. Only the remaining uses — day-cell fills, the create FAB,
 * the hero glyph — are the fills this lift genuinely does not apply to.
 */
private val CalendarAccentPurpleOnDark = Color(0xFFA798CD)
private val CalendarTodayBlue = Color(0xFF509AE6)
private val CalendarCardCornerRadius = 24.dp
private val CalendarCardAmbientShadowElevation = 10.dp
private val CalendarCardKeyShadowElevation = 3.dp
private val CalendarCardHeaderHeight = 36.dp
private val CalendarCardHeaderHorizontalPadding = 6.dp
private val CalendarCardNavButtonWidth = 40.dp
private val CalendarCardNavButtonHeight = 36.dp
private val CalendarCardNavIconSize = 28.dp
private val CalendarCardNavButtonRadius = 12.dp
private val CalendarCardNavButtonRippleRadius = 20.dp
private val CalendarCardHorizontalPadding = 16.dp
private val CalendarMonthCardTopPadding = 16.dp
private val CalendarMonthCardBottomPadding = 20.dp
private val CalendarMonthCardOuterSpacing = 14.dp
private val CalendarMonthGridSpacing = 8.dp
private val CalendarMonthWeekdayHeight = 18.dp
private val CalendarMonthGridHeight = 292.dp
private val CalendarMonthDayCellHeight = 42.dp
private val CalendarMonthDayHighlightWidth = 42.dp
private val CalendarMonthDayHighlightHeight = 40.dp
private val CalendarMonthDayNumberWidth = 34.dp
private val CalendarMonthDayNumberHeight = 24.dp
private val CalendarMonthTaskCountHeight = 13.dp
private val CalendarMonthTaskDotSize = 4.6.dp
private val CalendarMonthDayCellContentSpacing = 1.dp
private val CalendarMonthHeaderTitleSize = 21.sp
private val CalendarPeriodHeaderTitleSize = 21.sp
private val CalendarDaySummaryTitleSize = 25.sp
private val CalendarDaySummaryCountSize = 18.sp
private val CalendarPeriodCardPageHeight = 78.dp
private val CalendarPeriodWeekDayCellHeight = 72.dp
private val CalendarPeriodPageHorizontalGutter = 2.dp
private val CalendarPeriodCardTopPadding = 16.dp
private val CalendarPeriodCardBottomPadding = 18.dp
private val CalendarWeekDayCellContentSpacing = 3.dp
private val CalendarTaskListSameDateSpacing = 2.dp
private val CalendarTaskRowHeight = 56.dp
private val CalendarTaskCompletionRiseOffsetY = (-10).dp
private val CalendarTaskRowTitleStartPadding = 10.dp
private val CalendarRowTrailingIconSize = 18.dp
private val CalendarSwipeRevealWidth = 256.dp
private val CalendarSwipeActionSpacing = 16.dp
private val CalendarSwipeActionMinWidth = 60.dp
private val CalendarSwipeActionButtonWidth = 56.dp
private val CalendarSwipeActionButtonHeight = 34.dp
private val CalendarSwipeActionIconSize = 21.dp
private val CalendarCompletionToggleTouchTarget = 48.dp
private val CalendarCompletionToggleRippleRadius = 24.dp
private val CalendarCompletionToggleIconSize = 24.dp
private val CalendarBarButtonIconSize = 22.dp

// The drag preview rides under the finger, not beside it: the pointer is offset
// into the card so the task being carried is the thing the hand is over.
private val CalendarDragPreviewAnchorX = 130.dp
private val CalendarDragPreviewAnchorY = 34.dp
private val CalendarDragPreviewMinWidth = 220.dp
private val CalendarDragPreviewMaxWidth = 280.dp
private val CalendarDragPreviewContentSpacing = 10.dp
private val CalendarDragPreviewIconSize = 22.dp

// Four states of one day-cell outline, and they are deliberately off any spacing
// scale: 0.2 dp apart is what separates today from selected when both are drawn
// at once, so a rung that rounded them would make two states one.
private val CalendarDayCellBorderDropTarget = 2.dp
private val CalendarDayCellBorderSelected = 1.6.dp
private val CalendarDayCellBorderToday = 1.4.dp
private val CalendarDayCellBorderPressed = 1.2.dp

/**
 * The tick landing, then the strike beginning — the check-off's first gap.
 *
 * Both rows below hand all three legs to [scaledDelay] rather than to `delay`: each
 * gap exists only to let the beat before it land, and every one of those beats is
 * already on the animator's clock. The argument is written out once, against the
 * identically shaped constants in `TodoListScreen.kt`.
 */
private const val CALENDAR_TASK_COMPLETION_CHECK_TO_STRIKE_MS = 160L
private const val CALENDAR_TASK_COMPLETION_STRIKE_TO_FADE_MS = 360L

/**
 * The ink leaving, and the wait before the row is handed to the list — one
 * number because they are one motion, read from the rung rather than typed.
 * Change, because the row's content goes where it stands. See the identically
 * shaped constants in `TodoListScreen.kt` and `ScheduledTaskHomeScreen.kt`.
 */
private val CALENDAR_TASK_COMPLETION_FADE_MS = TdayMotionTokens.Durations.Change.toLong()
private val CalendarTaskDragDueTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(ZoneId.systemDefault())
// Internal rather than private because `CalendarPageSelection.kt` owns the page
// arithmetic these three size, and a page count that lived in only one of the
// two files would be a clamp the decisions could disagree with.
internal const val CalendarMonthPagerPageCount = 240
internal const val CalendarWeekPagerPageCount = 1040
internal const val CalendarDayPagerPageCount = 3650

private fun shouldShowDateDivider(
    afterItemIndex: Int,
    items: List<TodoItem>,
    zoneId: ZoneId,
): Boolean {
    val currentTodo = items.getOrNull(afterItemIndex) ?: return false
    val nextTodo = items.getOrNull(afterItemIndex + 1) ?: return false
    val currentDue = currentTodo.due ?: return false
    val nextDue = nextTodo.due ?: return false
    return LocalDate.ofInstant(currentDue, zoneId) != LocalDate.ofInstant(nextDue, zoneId)
}

private data class CalendarTaskRescheduleDrop(
    val todo: TodoItem,
    val targetDate: LocalDate,
)

private data class CalendarTaskDragState(
    val todo: TodoItem,
    val position: Offset,
)

private data class CalendarDateDropTargetBounds(
    val date: LocalDate,
    val bounds: Rect,
)

private fun calendarTaskAlreadyDueOnDate(
    todo: TodoItem,
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean = todo.due?.let { LocalDate.ofInstant(it, zoneId) == date } == true

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreateTask: (CreateTaskPayload) -> Unit,
    onParseTaskTitleNlp: suspend (title: String, referenceDueEpochMs: Long) -> TodoTitleNlpResponse?,
    onCompleteTask: (TodoItem) -> Unit,
    onUpdateTask: (TodoItem, CreateTaskPayload) -> Unit,
    onMoveTask: (todo: TodoItem, targetDate: LocalDate, scope: TaskRescheduleScope) -> Unit,
    onDelete: (TodoItem) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val view = LocalView.current
    val today = remember { LocalDate.now(zoneId) }
    val minNavigableMonth = remember(zoneId) { YearMonth.now(zoneId) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val heroCollapse = rememberLazyListHeroTitleCollapse(listState = listState)
    val calendarTitle = stringResource(R.string.calendar_title)
    val calendarIcon = ImageVector.vectorResource(R.drawable.ic_lucide_calendar_1)
    var visibleMonthIso by rememberSaveable { mutableStateOf(minNavigableMonth.toString()) }
    var selectedDateIso by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedViewKey by rememberSaveable { mutableStateOf(CalendarViewMode.MONTH.name) }
    var todayJumpRequestId by rememberSaveable { mutableStateOf(0) }
    var todayJumpRequest by remember { mutableStateOf<CalendarTodayJumpRequest?>(null) }

    val visibleMonth = remember(visibleMonthIso) { YearMonth.parse(visibleMonthIso) }
    val selectedDate = remember(selectedDateIso) { LocalDate.parse(selectedDateIso) }
    val selectedViewMode = remember(selectedViewKey) {
        CalendarViewMode.entries.firstOrNull { it.name == selectedViewKey } ?: CalendarViewMode.MONTH
    }
    val calendarTaskRescheduleEnabled = selectedViewMode != CalendarViewMode.DAY
    val tasksByDate = remember(uiState.items, zoneId) {
        uiState.items
            .mapNotNull { todo -> todo.due?.let { due -> due to todo } }
            .groupBy({ (due, _) -> LocalDate.ofInstant(due, zoneId) }, { (_, todo) -> todo })
            .mapValues { (_, tasks) -> tasks.sortedBy { it.due ?: java.time.Instant.MAX } }
    }
    val selectedDatePendingTasks = tasksByDate[selectedDate].orEmpty()
    // Scoped search over what the calendar is SHOWING — the month, week or day
    // currently on the grid — not over every dated task there is. A day list is
    // a one-day slice of a range the screen already draws in full, so searching
    // only the selected day would find nothing you could not already see; but
    // searching all time would answer with tasks months outside the grid, which
    // is a different screen's job. The visible range is the page.
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    // TdayHeroToolbar's row height, for the outside-tap guard: the bar is an
    // overlay on the same box as the content, so "below the bar" has to be
    // measured rather than inferred from the hierarchy.
    val pinnedToolbarHeightPx = with(LocalDensity.current) {
        TdayHeroTitleMetrics.ToolbarHeight.toPx()
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchNeedsFocus by remember { mutableStateOf(false) }
    val normalizedSearchQuery = remember(searchQuery) {
        searchQuery.trim().lowercase(Locale.getDefault())
    }
    val searchActive = searchExpanded && normalizedSearchQuery.isNotBlank()
    val closeSearch = {
        searchExpanded = false
        searchQuery = ""
        searchNeedsFocus = false
    }
    // The span the grid is plotting, which is what a query is allowed to reach.
    val searchRange = remember(selectedViewMode, visibleMonth, selectedDate) {
        when (selectedViewMode) {
            CalendarViewMode.DAY -> selectedDate..selectedDate
            CalendarViewMode.WEEK -> {
                val start = selectedDate.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
                start..start.plusDays(6)
            }
            CalendarViewMode.MONTH -> visibleMonth.atDay(1)..visibleMonth.atEndOfMonth()
        }
    }
    val searchResults = remember(uiState.items, searchActive, normalizedSearchQuery, searchRange, zoneId) {
        if (!searchActive) {
            emptyList()
        } else {
            // The same two fields the list-detail screens match on: the title
            // and the notes flattened out of their rich-text form.
            uiState.items
                .filter { todo ->
                    val due = todo.due ?: return@filter false
                    LocalDate.ofInstant(due, zoneId) in searchRange && (
                        todo.title.lowercase(Locale.getDefault())
                            .contains(normalizedSearchQuery) ||
                            flattenNotesToPlainText(todo.description)
                                .lowercase(Locale.getDefault())
                                .contains(normalizedSearchQuery)
                        )
                }
                .sortedBy { it.due }
        }
    }
    val listedTasks = if (searchActive) searchResults else selectedDatePendingTasks
    // The grid follows the query. Left on the unfiltered map it kept a dot on
    // every day that has any task, so it said "matches here" for days holding
    // none — a legend for a list it was no longer describing.
    val plottedTasksByDate = if (searchActive) {
        remember(searchResults, zoneId) {
            searchResults
                .mapNotNull { todo -> todo.due?.let { due -> due to todo } }
                .groupBy({ (due, _) -> LocalDate.ofInstant(due, zoneId) }, { (_, todo) -> todo })
        }
    } else {
        tasksByDate
    }
    // Whether the day list is showing the illustrated empty scene rather than
    // rows — read by the watermark, which draws the same glyph.
    val showsEmptyScene = listedTasks.isEmpty() && !uiState.isLoading
    fun canNavigateTo(date: LocalDate): Boolean = YearMonth.from(date) >= minNavigableMonth
    fun selectDate(date: LocalDate) {
        if (!canNavigateTo(date)) return
        visibleMonthIso = YearMonth.from(date).toString()
        selectedDateIso = date.toString()
        // Picking a day is a request to see that day, which a live query would
        // otherwise override — the list below would keep showing the results
        // and the tap would look ignored.
        if (searchActive) closeSearch()
    }
    fun clearTodayJumpRequest(requestId: Int) {
        if (todayJumpRequest?.id == requestId) {
            todayJumpRequest = null
        }
    }

    var editTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateTaskSheet by rememberSaveable { mutableStateOf(false) }
    var createDueEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var draggedCalendarTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeCalendarDrag by remember { mutableStateOf<CalendarTaskDragState?>(null) }
    var calendarDragContainerOrigin by remember { mutableStateOf(Offset.Zero) }
    val calendarDropTargetBounds =
        remember { mutableStateMapOf<String, CalendarDateDropTargetBounds>() }
    var activeDropDateIso by remember { mutableStateOf<String?>(null) }
    var pendingRescheduleDrop by remember { mutableStateOf<CalendarTaskRescheduleDrop?>(null) }
    var openSwipeTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedViewMode) {
        if (selectedViewMode == CalendarViewMode.DAY) {
            draggedCalendarTodoId = null
            activeCalendarDrag = null
            activeDropDateIso = null
            calendarDropTargetBounds.clear()
        }
    }
    LaunchedEffect(uiState.items, openSwipeTaskId) {
        val openId = openSwipeTaskId ?: return@LaunchedEffect
        if (uiState.items.none { it.id == openId }) {
            openSwipeTaskId = null
        }
    }
    val editTarget = rememberEditSheetTarget(
        id = editTargetId,
        current = remember(editTargetId, uiState.items) {
            editTargetId?.let { targetId ->
                uiState.items.firstOrNull { it.id == targetId }
            }
        },
    )
    val draggedCalendarTodo = remember(draggedCalendarTodoId, uiState.items) {
        draggedCalendarTodoId?.let { targetId ->
            uiState.items.firstOrNull { it.id == targetId || it.canonicalId == targetId }
        }
    }
    val resolveTodoForDrop: (String) -> TodoItem? = { targetId ->
        uiState.items.firstOrNull { it.id == targetId || it.canonicalId == targetId }
    }
    val activeDropDate = remember(activeDropDateIso) {
        activeDropDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    fun openCreateTaskSheetForSelectedDate() {
        val currentDate = LocalDate.now(zoneId)
        val prefillDue = if (selectedDate == currentDate) {
            val nowTime = java.time.LocalTime.now(zoneId)
            selectedDate.atTime(nowTime).atZone(zoneId).plusHours(1)
        } else {
            selectedDate.atTime(21, 0).atZone(zoneId)
        }
        createDueEpochMs = prefillDue.toInstant().toEpochMilli()
        showCreateTaskSheet = true
    }
    fun requestTaskReschedule(todo: TodoItem, targetDate: LocalDate) {
        draggedCalendarTodoId = null
        activeCalendarDrag = null
        activeDropDateIso = null
        calendarDropTargetBounds.clear()
        if (calendarTaskAlreadyDueOnDate(todo, targetDate, zoneId)) return
        TdayHaptics.dragDrop(view)
        TdayTelemetry.addBreadcrumb(
            "calendar.drag_reschedule",
            data = mapOf(
                "mode" to selectedViewMode.name.lowercase(),
                "recurring" to todo.isRecurring,
            ),
        )
        if (todo.isRecurring) {
            pendingRescheduleDrop = CalendarTaskRescheduleDrop(todo = todo, targetDate = targetDate)
        } else {
            onMoveTask(todo, targetDate, TaskRescheduleScope.OCCURRENCE)
            // Keep the user on the date they're viewing; don't jump to the drop target.
        }
    }

    fun activeCalendarDropDate(position: Offset, todo: TodoItem?): LocalDate? {
        return calendarDropTargetBounds.values
            .asSequence()
            .filter { target -> target.bounds.contains(position) }
            .filter { target ->
                todo == null || !calendarTaskAlreadyDueOnDate(todo, target.date, zoneId)
            }
            .minByOrNull { target -> target.bounds.width * target.bounds.height }
            ?.date
    }

    fun updateActiveCalendarDropTarget(position: Offset) {
        val todo = activeCalendarDrag?.todo ?: draggedCalendarTodo
        activeDropDateIso = activeCalendarDropDate(position, todo)?.toString()
    }

    fun finishCalendarDrag(position: Offset?) {
        val drag = activeCalendarDrag
        val targetDate = position?.let { activeCalendarDropDate(it, drag?.todo) }
            ?: activeDropDate
                ?.takeUnless { target ->
                    drag?.todo?.let { todo -> calendarTaskAlreadyDueOnDate(todo, target, zoneId) } == true
                }
        activeCalendarDrag = null
        draggedCalendarTodoId = null
        activeDropDateIso = null
        calendarDropTargetBounds.clear()
        if (drag != null && targetDate != null) {
            requestTaskReschedule(drag.todo, targetDate)
        }
    }

    fun cancelCalendarDrag() {
        activeCalendarDrag = null
        draggedCalendarTodoId = null
        activeDropDateIso = null
        calendarDropTargetBounds.clear()
    }

    LaunchedEffect(draggedCalendarTodoId) {
        if (draggedCalendarTodoId == null) {
            calendarDropTargetBounds.clear()
        }
    }
    BackHandler(enabled = searchExpanded) {
        closeSearch()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            CalendarCreateTaskFab(
                onClick = { openCreateTaskSheetForSelectedDate() },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    calendarDragContainerOrigin = coordinates.positionInRoot()
                },
        ) {
            // Stood down while the empty scene is up: that scene draws the same
            // calendar glyph, and the watermark behind it put a 212dp copy of it
            // at alpha 0.10 directly under a 52dp solid one.
            if (!showsEmptyScene) {
                EmptyTaskWatermark(
                    iconRes = R.drawable.ic_lucide_calendar_1,
                    accentColor = CalendarAccentPurple,
                )
            }

            run {
                LazyColumn(
                    // The Scaffold's insets, which this screen alone was
                    // dropping — under `enableEdgeToEdge` that put its bar, and
                    // the back button in it, behind the status bar. Applied to
                    // the scroller and to the toolbar overlay together, exactly
                    // as the timeline screen does, so the hero block's own
                    // reserve still lines the two up.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        // Tap the grid and the field goes away, as on the root
                        // feeds. The toolbar is an overlay on this same box, so
                        // the guard is its row height rather than a reported
                        // rect.
                        .tdayClosesSearchOnOutsideTap(
                            isSearchOpen = searchExpanded,
                            barHeightPx = pinnedToolbarHeightPx,
                            close = closeSearch,
                        ),
                    state = listState,
                    // No top padding: the hero item reserves the bar's height
                    // itself, so the scroll offset is a clean count from the top.
                    contentPadding = PaddingValues(
                        start = TdayDimens.ContentPaddingHorizontal,
                        end = TdayDimens.ContentPaddingHorizontal,
                        bottom = TdayDimens.SpacingXxs,
                    ),
                    verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXl),
                ) {
                tdayHeroTitleItem(
                    title = calendarTitle,
                    icon = calendarIcon,
                    accentColor = CalendarAccentPurple,
                    titleColor = CalendarAccentPurple,
                    collapseProgress = heroCollapse.progress,
                )
                item {
                    CalendarViewModeTabs(
                        selectedMode = selectedViewMode,
                        onModeSelected = { mode ->
                            TdayTelemetry.addBreadcrumb(
                                "calendar.mode",
                                data = mapOf("mode" to mode.name.lowercase()),
                            )
                            selectedViewKey = mode.name
                            if (mode == CalendarViewMode.MONTH &&
                                selectedViewMode != CalendarViewMode.MONTH
                            ) {
                                // The grid opens on the selected date's month.
                                // This was written on the way OUT of Month, which
                                // was invisible while the grid vanished in one
                                // frame — but the card is composed for the whole
                                // cross now, so an exit write re-paged the grid to
                                // another month while it was still fully opaque.
                                // Written on entry, the only card that reads it is
                                // the one coming in, and nothing reads it between
                                // the two taps: `searchRange` only consults the
                                // visible month in Month mode, and picking a date
                                // in Week or Day moves it anyway.
                                visibleMonthIso = YearMonth.from(selectedDate).toString()
                            }
                        },
                    )
                }

                item {
                    val viewModeMotionEnabled = rememberTdayMotionEnabled()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                // A month grid collapsing to a week strip is a
                                // card finding its height, which is what Settle
                                // is for. What it replaces was never chosen:
                                // both docs/motion.md and the budget fixture's
                                // `android._spring` note say on the record that
                                // StiffnessMediumLow here was a library default.
                                animationSpec = if (viewModeMotionEnabled) {
                                    TdayMotionTokens.Springs.settle()
                                } else {
                                    snap()
                                },
                            )
                            .calendarCardChrome(),
                    ) {
                        // The height above is animated; the content inside it was
                        // not, so the grid was replaced by the strip in one frame
                        // while the card around it was still travelling. Crossing
                        // the two over fixes that, and the cross is deliberately
                        // shorter than the spring: the eye lands on a resolved
                        // grid inside a still-settling card rather than on two
                        // ghosted grids. Both branches hand back `using null`,
                        // because the height is already owned above: every
                        // SizeTransform, clipping or not, carries a default
                        // 400-stiffness spring on the AnimatedContent's own size,
                        // and a 250 spring chasing that is not a card finding its
                        // height, it is two springs negotiating. Null, the inner
                        // box is simply the tapped mode's height on the frame it
                        // changes, and the card's own clip is the one edge that
                        // moves — the outgoing grid is drawn where it was and
                        // that edge travels down across it.
                        AnimatedContent(
                            targetState = selectedViewMode,
                            transitionSpec = {
                                if (viewModeMotionEnabled) {
                                    fadeIn(
                                        animationSpec = tween(
                                            durationMillis = TdayMotionTokens.Durations.Enter,
                                            easing = TdayMotionTokens.Easings.Enter,
                                        ),
                                    ) togetherWith fadeOut(
                                        animationSpec = tween(
                                            durationMillis = TdayMotionTokens.Durations.Quick,
                                            easing = TdayMotionTokens.Easings.Exit,
                                        ),
                                    ) using null
                                } else {
                                    // Motion off still means the mode the user
                                    // asked for, drawn at its own height, now.
                                    EnterTransition.None togetherWith ExitTransition.None using null
                                }
                            },
                            label = "calendarViewMode",
                        ) { viewMode ->
                            when (viewMode) {
                                CalendarViewMode.MONTH -> CalendarMonthCard(
                                    visibleMonth = visibleMonth,
                                    minNavigableMonth = minNavigableMonth,
                                    canGoPrevMonth = visibleMonth > minNavigableMonth,
                                    selectedDate = selectedDate,
                                    today = today,
                                    tasksByDate = plottedTasksByDate,
                                    draggedTodo = draggedCalendarTodo,
                                    activeDropDate = activeDropDate,
                                    dropTargets = calendarDropTargetBounds,
                                    canSelectDate = ::canNavigateTo,
                                    todayJumpRequest = todayJumpRequest,
                                    onTodayJumpHandled = ::clearTodayJumpRequest,
                                    onVisibleMonthChanged = { targetMonth ->
                                        if (targetMonth >= minNavigableMonth) {
                                            visibleMonthIso = targetMonth.toString()
                                        }
                                    },
                                    onSelectDate = ::selectDate,
                                    onDropDateChanged = { date ->
                                        activeDropDateIso = date?.toString()
                                    },
                                    onMoveTaskToDate = ::requestTaskReschedule,
                                    resolveTodo = resolveTodoForDrop,
                                )

                                CalendarViewMode.WEEK -> CalendarWeekCard(
                                    selectedDate = selectedDate,
                                    minNavigableMonth = minNavigableMonth,
                                    today = today,
                                    tasksByDate = plottedTasksByDate,
                                    draggedTodo = draggedCalendarTodo,
                                    activeDropDate = activeDropDate,
                                    dropTargets = calendarDropTargetBounds,
                                    canGoPrevWeek = canNavigateTo(selectedDate.minusWeeks(1)),
                                    canSelectDate = ::canNavigateTo,
                                    todayJumpRequest = todayJumpRequest,
                                    onTodayJumpHandled = ::clearTodayJumpRequest,
                                    onSelectDate = ::selectDate,
                                    onDropDateChanged = { date ->
                                        activeDropDateIso = date?.toString()
                                    },
                                    onMoveTaskToDate = ::requestTaskReschedule,
                                    resolveTodo = resolveTodoForDrop,
                                )

                                CalendarViewMode.DAY -> CalendarDayCard(
                                    selectedDate = selectedDate,
                                    minNavigableMonth = minNavigableMonth,
                                    today = today,
                                    tasksByDate = plottedTasksByDate,
                                    canGoPrevDay = canNavigateTo(selectedDate.minusDays(1)),
                                    canSelectDate = ::canNavigateTo,
                                    todayJumpRequest = todayJumpRequest,
                                    onTodayJumpHandled = ::clearTodayJumpRequest,
                                    onSelectDate = ::selectDate,
                                )
                            }
                        }
                    }
                }

                // A results list is not due on the selected day, so the day's
                // heading steps aside rather than sitting over dates it does
                // not describe.
                if (!searchActive) {
                    item {
                        val tasksDueDateLabel =
                            selectedDate.format(
                                DateTimeFormatter.ofPattern(
                                    "EEE, MMM d",
                                    Locale.getDefault()
                                )
                            )
                        Text(
                            text = stringResource(R.string.calendar_tasks_due, tasksDueDateLabel),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = TdayDimens.SpacingXs),
                        )
                    }
                }

                    itemsIndexed(
                        items = listedTasks,
                        key = { _, todo -> "calendar-task-${todo.id}" },
                        contentType = { _, _ -> "calendar_task_row" },
                    ) { index, todo ->
                        // Results can span the whole visible range, and the row
                        // list's own divider between two dates is an unlabelled
                        // hairline — fine when every row shares the heading
                        // above, useless when they do not. Each new date in a
                        // result list gets its own label, so a match three weeks
                        // out cannot read as a task on the selected day.
                        if (searchActive) {
                            val rowDate = todo.due?.let { LocalDate.ofInstant(it, zoneId) }
                            val previousDate = listedTasks.getOrNull(index - 1)
                                ?.due?.let { LocalDate.ofInstant(it, zoneId) }
                            if (rowDate != null && rowDate != previousDate) {
                                Text(
                                    text = rowDate.format(
                                        DateTimeFormatter.ofPattern(
                                            "EEE, MMM d",
                                            Locale.getDefault(),
                                        ),
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = TdayDimens.SpacingXs,
                                        top = if (index == 0) {
                                            TdayDimens.SpacingNone
                                        } else {
                                            TdayDimens.SpacingXl
                                        },
                                        bottom = TdayDimens.SpacingSm,
                                    ),
                                )
                            }
                        }
                        CalendarTodoRow(
                            modifier = Modifier
                                // The day list is a task feed, so it takes the feed's
                                // own clock. It used to run 180 in and 140 out — ten
                                // milliseconds under [TdayFeedItemMotion] on each leg,
                                // naming no rung and shared with nothing, which is
                                // exactly the drift that object exists to stop. Rule 1
                                // still holds across the swap: 150 out stays shorter
                                // than 190 in.
                                //
                                // `placementSpec` stays null, and that is the one
                                // thing this list does NOT take from the object. It is
                                // not that nothing is displaced: completing, deleting
                                // or rescheduling a task off the selected date each
                                // remove exactly one keyed row, so today the rows
                                // below a departure take their new slots in a single
                                // frame while the departing one fades over 150 — the
                                // same clock splitting that [TdayFeedItemMotion]'s
                                // header argues against, and that `CompletedScreen`,
                                // on the same object, does not have. Left standing
                                // rather than fixed in passing: this unit is retiring
                                // drifted literals, and starting to animate something
                                // this feed has never animated is a behaviour change
                                // that needs its own argument and its own device pass.
                                .animateItem(
                                    fadeInSpec = TdayFeedItemMotion.FadeIn,
                                    placementSpec = null,
                                    fadeOutSpec = TdayFeedItemMotion.FadeOut,
                                )
                                .padding(
                                    bottom = if (index == listedTasks.lastIndex) {
                                        TdayDimens.SpacingNone
                                    } else {
                                        CalendarTaskListSameDateSpacing
                                    },
                                ),
                            todo = todo,
                            lists = uiState.lists,
                            // Suppressed while searching: the date label above
                            // each group already says where the break is, and the
                            // hairline under the last row of a group then reads
                            // as a second, weaker divider.
                            showDateDivider = !searchActive && shouldShowDateDivider(
                                afterItemIndex = index,
                                items = listedTasks,
                                zoneId = zoneId,
                            ),
                            dragEnabled = calendarTaskRescheduleEnabled,
                            onComplete = { onCompleteTask(todo) },
                            onInfo = { editTargetId = todo.id },
                            onDelete = { onDelete(todo) },
                            dragging = calendarTaskRescheduleEnabled && draggedCalendarTodo?.id == todo.id,
                            openSwipeTaskId = openSwipeTaskId,
                            onOpenSwipeTaskIdChange = { openSwipeTaskId = it },
                            onDragStart = { position ->
                                activeDropDateIso = null
                                draggedCalendarTodoId = todo.id
                                activeCalendarDrag = CalendarTaskDragState(
                                    todo = todo,
                                    position = position,
                                )
                                updateActiveCalendarDropTarget(position)
                            },
                            onDragMove = { position ->
                                activeCalendarDrag = CalendarTaskDragState(
                                    todo = todo,
                                    position = position,
                                )
                                updateActiveCalendarDropTarget(position)
                            },
                            onDragEnd = ::finishCalendarDrag,
                            onDragCancel = ::cancelCalendarDrag,
                        )
                    }

                if (listedTasks.isEmpty() && !uiState.isLoading) {
                    item(key = "calendar-empty", contentType = "calendar-empty") {
                        if (searchActive) {
                            TdayEmptyState(
                                icon = R.drawable.ic_lucide_search,
                                accentColor = CalendarAccentPurple,
                                title = stringResource(R.string.scheduled_task_home_search_no_results),
                                description = stringResource(R.string.search_no_results_body),
                                modifier = Modifier.padding(vertical = TdayDimens.SpacingLg),
                            )
                        } else {
                            TdayEmptyState(
                                icon = R.drawable.ic_lucide_calendar_1,
                                accentColor = CalendarAccentPurple,
                                title = stringResource(R.string.calendar_no_pending),
                                description = stringResource(R.string.calendar_no_pending_body),
                                modifier = Modifier.padding(vertical = TdayDimens.SpacingLg),
                            )
                        }
                    }
                }

                // Keyed, because a load failure genuinely adds and removes a row
                // here and `animateItem` cannot animate either on an item whose
                // identity is its index. It takes [TdayFeedItemMotion] whole —
                // the rows above it take its two fades but pass
                // `placementSpec = null` (argued there, and not because they are
                // never displaced), while this card is the one item on this feed
                // that already glides to whatever slot the rows above leave it
                // in.
                uiState.errorMessage?.let { message ->
                    item(key = "error-retry", contentType = "error_retry") {
                        val errorCardMotionEnabled = rememberTdayMotionEnabled()
                        com.ohmz.tday.compose.core.ui.ErrorRetryCard(
                            message = message,
                            onRetry = onRefresh,
                            modifier = if (errorCardMotionEnabled) {
                                Modifier.animateItem(
                                    fadeInSpec = TdayFeedItemMotion.FadeIn,
                                    placementSpec = TdayFeedItemMotion.Placement,
                                    fadeOutSpec = TdayFeedItemMotion.FadeOut,
                                )
                            } else {
                                Modifier
                            },
                        )
                    }
                }

                    item { Spacer(modifier = Modifier.height(TdayDimens.BottomScrollSpacer)) }
                }
            }

            activeCalendarDrag?.let { drag ->
                CalendarTaskDragPreview(
                    modifier = Modifier
                        .offset {
                            val localPosition = drag.position - calendarDragContainerOrigin
                            val anchorX = with(density) { CalendarDragPreviewAnchorX.toPx() }
                            val anchorY = with(density) { CalendarDragPreviewAnchorY.toPx() }
                            IntOffset(
                                x = (localPosition.x - anchorX).roundToInt(),
                                y = (localPosition.y - anchorY).roundToInt(),
                            )
                        }
                        .zIndex(20f),
                    todo = drag.todo,
                    lists = uiState.lists,
                )
            }

            // Last, so it draws over the content passing behind it.
        TdayHeroToolbar(
            title = calendarTitle,
            titleColor = CalendarAccentPurple,
            collapseProgress = heroCollapse.progress,
            // Gone while the field is up: a back chevron beside an open search
            // is a second way out that leaves the screen rather than the query,
            // and it costs the field the width that makes a placeholder
            // readable.
            onBack = if (searchExpanded) null else onBack,
            backContentDescription = stringResource(R.string.action_back),
            modifier = Modifier.padding(padding),
            titleSuppressed = searchExpanded,
        ) {
            if (searchExpanded) {
                // The field takes the WHOLE bar — back chevron, title and action
                // cluster, the Today pill included, all give way to it, as they
                // do on the root feeds and on iOS's TimelineTopBar.
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(searchNeedsFocus) {
                    if (!searchNeedsFocus) return@LaunchedEffect
                    // Consumed on the way in, so returning to a screen that
                    // still has the field open does not re-open the keyboard
                    // with it.
                    searchNeedsFocus = false
                    focusRequester.requestFocus()
                }
                TdaySearchCapsule(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.action_search_in, calendarTitle),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    // The one control in the row, so its X leaves the search —
                    // and leaving clears the query on the way out.
                    onClose = closeSearch,
                    trailingContentDescription = stringResource(R.string.action_close_search),
                )
            } else {
                CalendarBarButton(
                    // Only opens: the bar hands its row over to the field, so
                    // this button is not on screen to be tapped again.
                    onClick = {
                        searchExpanded = true
                        searchNeedsFocus = true
                    },
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_search),
                    contentDescription = stringResource(R.string.action_search),
                )
                CalendarTodayButton(
                    collapseProgress = heroCollapse.progress,
                    label = stringResource(R.string.calendar_today),
                    contentDescription = stringResource(R.string.calendar_jump_to_today),
                    onClick = {
                        todayJumpRequestId += 1
                        TdayTelemetry.addBreadcrumb(
                            "calendar.today",
                            data = mapOf("mode" to selectedViewMode.name.lowercase()),
                        )
                        todayJumpRequest = CalendarTodayJumpRequest(
                            id = todayJumpRequestId,
                            targetDate = LocalDate.now(zoneId),
                        )
                    },
                )
            }
        }        }
    }

    if (showCreateTaskSheet) {
        CreateTaskBottomSheet(
            lists = uiState.lists,
            initialDueEpochMs = createDueEpochMs,
            onParseTaskTitleNlp = onParseTaskTitleNlp,
            onDismiss = {
                showCreateTaskSheet = false
                createDueEpochMs = null
            },
            onCreateTask = onCreateTask,
        )
    }

    pendingRescheduleDrop?.let { drop ->
        AlertDialog(
            onDismissRequest = { pendingRescheduleDrop = null },
            title = {
                Text(
                    text = stringResource(R.string.todos_reschedule_recurring_title),
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(text = stringResource(R.string.todos_reschedule_recurring_message))
            },
            dismissButton = {
                TextButton(onClick = { pendingRescheduleDrop = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingRescheduleDrop = null
                        onMoveTask(drop.todo, drop.targetDate, TaskRescheduleScope.OCCURRENCE)
                    }) {
                        Text(stringResource(R.string.todos_reschedule_this_occurrence))
                    }
                    TextButton(onClick = {
                        pendingRescheduleDrop = null
                        onMoveTask(drop.todo, drop.targetDate, TaskRescheduleScope.SERIES)
                    }) {
                        Text(stringResource(R.string.todos_reschedule_entire_series))
                    }
                }
            },
        )
    }

    editTarget?.let { todo ->
        CreateTaskBottomSheet(
            lists = uiState.lists,
            editingTask = todo,
            defaultListId = todo.listId,
            onParseTaskTitleNlp = onParseTaskTitleNlp,
            onDismiss = { editTargetId = null },
            onCreateTask = { _ -> },
            onUpdateTask = onUpdateTask,
        )
    }
}

@Composable
private fun CalendarCreateTaskFab(
    onClick: () -> Unit,
) {
    val view = LocalView.current
    FloatingActionButton(
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        modifier = Modifier.size(TdayDimens.FabSize),
        shape = CircleShape,
        containerColor = CalendarAccentPurple,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = TdayDimens.FabElevation,
            pressedElevation = TdayDimens.FabPressedElevation,
            focusedElevation = TdayDimens.FabElevation,
            hoveredElevation = TdayDimens.FabElevation,
        ),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_plus),
            contentDescription = stringResource(R.string.action_create_task),
            modifier = Modifier.size(TdayDimens.FabIconSize),
        )
    }
}

private enum class CalendarViewMode {
    MONTH,
    WEEK,
    DAY,
}

@Composable
private fun Modifier.calendarCardChrome(): Modifier {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(CalendarCardCornerRadius)
    val ambientShadowColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.055f)
    val keyShadowColor = Color.Black.copy(alpha = if (isDark) 0.18f else 0.045f)
    val strokeColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.035f)
    }

    return this
        .shadow(
            elevation = CalendarCardAmbientShadowElevation,
            shape = shape,
            clip = false,
            ambientColor = ambientShadowColor,
            spotColor = ambientShadowColor,
        )
        .shadow(
            elevation = CalendarCardKeyShadowElevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Transparent,
            spotColor = keyShadowColor,
        )
        .clip(shape)
        .background(
            color = colorScheme.surface,
            shape = shape,
        )
        .border(
            width = TdayDimens.BorderWidth,
            color = strokeColor,
            shape = shape,
        )
}

@Composable
private fun CalendarViewModeTabs(
    selectedMode: CalendarViewMode,
    onModeSelected: (CalendarViewMode) -> Unit,
) {
    TdaySegmentedSlider(
        options = CalendarViewMode.entries,
        selectedOption = selectedMode,
        onOptionSelected = onModeSelected,
        accentColor = CalendarAccentPurple,
        label = { mode ->
            mode.name.lowercase(Locale.getDefault())
                .replaceFirstChar { it.uppercase() }
        },
    )
}

@Composable
private fun CalendarWeekCard(
    selectedDate: LocalDate,
    minNavigableMonth: YearMonth,
    today: LocalDate,
    tasksByDate: Map<LocalDate, List<TodoItem>>,
    draggedTodo: TodoItem?,
    activeDropDate: LocalDate?,
    dropTargets: MutableMap<String, CalendarDateDropTargetBounds>,
    canGoPrevWeek: Boolean,
    canSelectDate: (LocalDate) -> Boolean,
    todayJumpRequest: CalendarTodayJumpRequest?,
    onTodayJumpHandled: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onDropDateChanged: (LocalDate?) -> Unit,
    onMoveTaskToDate: (TodoItem, LocalDate) -> Unit,
    resolveTodo: (String) -> TodoItem?,
) {
    val colorScheme = MaterialTheme.colorScheme
    val minWeekStart = remember(minNavigableMonth) { startOfWeek(minNavigableMonth.atDay(1)) }
    val weekStart = remember(selectedDate) { startOfWeek(selectedDate) }
    val selectedDayOffset = remember(selectedDate) {
        (selectedDate.dayOfWeek.value % 7).toLong()
    }
    val currentPage = remember(minWeekStart, weekStart) {
        ChronoUnit.WEEKS.between(minWeekStart, weekStart)
            .toInt()
            .coerceIn(0, CalendarWeekPagerPageCount - 1)
    }
    var scrollRequest by remember { mutableStateOf<CalendarPagerScrollRequest?>(null) }
    val isPagingAtRest = scrollRequest == null

    fun requestPage(offset: Int) {
        val targetIndex = (currentPage + offset).coerceIn(0, CalendarWeekPagerPageCount - 1)
        if (targetIndex == currentPage || !isPagingAtRest) return
        // Written straight through rather than from a `coroutineScope.launch`. The launch bought
        // nothing — this is a plain state write, not suspending work — and it cost the guard two
        // lines above its meaning: the check read `isPagingAtRest` in the click's frame while the
        // write landed on a later dispatch, so two taps inside one frame both passed a guard
        // neither had yet closed. Writing here makes the read and the write the same moment.
        scrollRequest = CalendarPagerScrollRequest(
            id = System.nanoTime().toInt(),
            page = targetIndex,
        )
    }

    // The date a Today jump is carrying while its sweep is in the air. The week
    // pager's own settle arithmetic only knows the weekday the user came in on,
    // so without this the sweep lands on today's week with last week's weekday
    // still selected.
    var pendingTodayJumpDate by remember { mutableStateOf<LocalDate?>(null) }

    fun settlePage(page: Int) {
        // One settle consumes the pending jump whether or not this is the page
        // the jump asked for: a finger that grabs the sweep mid-flight lands
        // somewhere else entirely, and a target left pending would then fire on
        // whatever week the user paged to next.
        val jumpTarget = pendingTodayJumpDate
        pendingTodayJumpDate = null
        val targetDate = weekPageSettleSelection(
            minWeekStart = minWeekStart,
            page = page,
            preferredDayOffset = selectedDayOffset,
            pendingJumpDate = jumpTarget,
            canSelectDate = canSelectDate,
        )
        if (targetDate == null) {
            // No day on this page may be selected, so the screen has nothing it
            // can say about it: the header, the day list and the `+` prefill
            // would all go on describing the week we left. Send the pager back
            // to the page the selection does describe rather than sit in that
            // split state saying nothing, which is what it used to do.
            scrollRequest = CalendarPagerScrollRequest(
                id = System.nanoTime().toInt(),
                page = currentPage,
            )
            return
        }
        TdayTelemetry.addBreadcrumb(
            "calendar.page",
            data = mapOf(
                "mode" to "week",
                "direction" to if (page >= currentPage) "next" else "previous",
            ),
        )
        onSelectDate(targetDate)
    }

    LaunchedEffect(todayJumpRequest) {
        val request = todayJumpRequest ?: return@LaunchedEffect
        when (
            val jump = weekPagerTodayJump(
                minWeekStart = minWeekStart,
                currentPage = currentPage,
                targetDate = request.targetDate,
            )
        ) {
            is CalendarTodayJump.SelectNow -> {
                pendingTodayJumpDate = null
                onSelectDate(jump.date)
            }

            is CalendarTodayJump.PageThenSelect -> {
                pendingTodayJumpDate = jump.date
                scrollRequest = CalendarPagerScrollRequest(request.id, jump.page)
            }
        }
        onTodayJumpHandled(request.id)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalendarCardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CalendarCardHorizontalPadding,
                    top = CalendarPeriodCardTopPadding,
                    end = CalendarCardHorizontalPadding,
                    bottom = CalendarPeriodCardBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXl),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarCardHeaderHeight)
                    .padding(horizontal = CalendarCardHeaderHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniCalendarNavButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_left),
                    contentDescription = stringResource(R.string.calendar_prev_week),
                    enabled = canGoPrevWeek && isPagingAtRest,
                    onClick = { requestPage(-1) },
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatWeekRange(weekStart),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = CalendarPeriodHeaderTitleSize,
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface,
                    )
                }
                MiniCalendarNavButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_right),
                    contentDescription = stringResource(R.string.calendar_next_week),
                    enabled = isPagingAtRest,
                    onClick = { requestPage(1) },
                )
            }

            CalendarPagingContent(
                pageCount = CalendarWeekPagerPageCount,
                currentPage = currentPage,
                onPageSettled = ::settlePage,
                scrollRequest = scrollRequest,
                onScrollRequestHandled = { requestId ->
                    if (scrollRequest?.id == requestId) {
                        scrollRequest = null
                    }
                },
                pageKey = { page -> "week-${minWeekStart.plusWeeks(page.toLong())}" },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarPeriodCardPageHeight),
            ) { page ->
                val displayWeekStart = remember(minWeekStart, page) {
                    minWeekStart.plusWeeks(page.toLong())
                }
                val weekDays = remember(displayWeekStart) {
                    List(7) { offset -> displayWeekStart.plusDays(offset.toLong()) }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CalendarPeriodPageHorizontalGutter),
                    horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingSm),
                ) {
                    weekDays.forEach { day ->
                        val isSelected = day == selectedDate
                        val isToday = day == today
                        val taskCount = tasksByDate[day]?.size ?: 0
                        val isEnabled = canSelectDate(day)
                        val dropEligibleDraggedTodo = draggedTodo?.takeIf { todo ->
                            isEnabled && !calendarTaskAlreadyDueOnDate(todo, day)
                        }
                        CalendarWeekDayCell(
                            pageStart = displayWeekStart,
                            date = day,
                            taskCount = taskCount,
                            isSelected = isSelected,
                            isToday = isToday,
                            isEnabled = isEnabled,
                            isDropTarget = activeDropDate == day &&
                                (draggedTodo == null || dropEligibleDraggedTodo != null),
                            draggedTodo = dropEligibleDraggedTodo,
                            dropTargets = dropTargets,
                            onClick = { onSelectDate(day) },
                            onDropDateChanged = onDropDateChanged,
                            onMoveTaskToDate = onMoveTaskToDate,
                            resolveTodo = resolveTodo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarWeekDayCell(
    pageStart: LocalDate,
    date: LocalDate,
    taskCount: Int,
    isSelected: Boolean,
    isToday: Boolean,
    isEnabled: Boolean,
    isDropTarget: Boolean,
    draggedTodo: TodoItem?,
    dropTargets: MutableMap<String, CalendarDateDropTargetBounds>,
    onClick: () -> Unit,
    onDropDateChanged: (LocalDate?) -> Unit,
    onMoveTaskToDate: (TodoItem, LocalDate) -> Unit,
    resolveTodo: (String) -> TodoItem?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when {
        isDropTarget -> colorScheme.error.copy(alpha = 0.20f)
        isSelected -> CalendarAccentPurple.copy(alpha = 0.24f)
        isToday -> CalendarTodayBlue.copy(alpha = 0.16f)
        else -> colorScheme.background
    }
    val borderColor = when {
        isDropTarget -> colorScheme.error
        isSelected -> CalendarAccentPurple.copy(alpha = 0.95f)
        isToday -> CalendarTodayBlue.copy(alpha = 0.74f)
        else -> Color.Transparent
    }
    val borderWidth = when {
        isDropTarget -> CalendarDayCellBorderDropTarget
        isSelected -> CalendarDayCellBorderSelected
        isToday -> CalendarDayCellBorderToday
        else -> TdayDimens.SpacingNone
    }
    val stateTint = when {
        isDropTarget -> colorScheme.error
        isSelected -> CalendarAccentPurple
        isToday -> CalendarTodayBlue
        else -> CalendarAccentPurple
    }

    Box(
        modifier = modifier
            .height(CalendarPeriodCardPageHeight)
            .minimumInteractiveComponentSize()
            .calendarDateDropTarget(
                date = date,
                draggedTodo = draggedTodo,
                enabled = isEnabled,
                onDropDateChanged = onDropDateChanged,
                onMoveTaskToDate = onMoveTaskToDate,
                resolveTodo = resolveTodo,
            )
            .calendarInAppDateDropTarget(
                // Scope the ID to the pager page: pre-composed adjacent pages must not
                // overwrite the visible page's bounds for the same date.
                targetId = "week-$pageStart-$date",
                date = date,
                enabled = isEnabled && draggedTodo != null,
                dropTargets = dropTargets,
            )
            .graphicsLayer { alpha = if (isEnabled) 1f else 0.48f },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(CalendarPeriodWeekDayCellHeight),
            shape = RoundedCornerShape(TdayDimens.RadiusRow),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(
                width = borderWidth,
                color = borderColor,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
            enabled = isEnabled,
            onClick = onClick,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TdayDimens.SpacingSm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CalendarWeekDayCellContentSpacing),
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant.copy(alpha = if (isEnabled) 0.9f else 0.52f),
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDropTarget || isSelected || isToday) stateTint else colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = if (taskCount > 9) {
                        stringResource(R.string.calendar_task_count_cap)
                    } else {
                        taskCount.toString()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (taskCount > 0) stateTint else colorScheme.onSurfaceVariant.copy(
                        alpha = 0.42f
                    ),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.calendarDateDropTarget(
    date: LocalDate,
    draggedTodo: TodoItem?,
    enabled: Boolean,
    onDropDateChanged: (LocalDate?) -> Unit,
    onMoveTaskToDate: (TodoItem, LocalDate) -> Unit,
    resolveTodo: (String) -> TodoItem?,
): Modifier {
    if (!enabled) return this

    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            event.mimeTypes().any { mimeType -> mimeType.startsWith("text/") } &&
                (draggedTodo?.let { todo -> !calendarTaskAlreadyDueOnDate(todo, date) } != false)
        },
        target = object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                val todo = draggedTodo ?: event.todoIdText()?.let(resolveTodo)
                if (todo == null || !calendarTaskAlreadyDueOnDate(todo, date)) {
                    onDropDateChanged(date)
                } else {
                    onDropDateChanged(null)
                }
            }

            override fun onExited(event: DragAndDropEvent) {
                onDropDateChanged(null)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val todo = draggedTodo ?: event.todoIdText()?.let(resolveTodo) ?: return false
                if (calendarTaskAlreadyDueOnDate(todo, date)) {
                    onDropDateChanged(null)
                    return false
                }
                onDropDateChanged(null)
                onMoveTaskToDate(todo, date)
                return true
            }

            override fun onEnded(event: DragAndDropEvent) {
                onDropDateChanged(null)
            }
        },
    )
}

private fun DragAndDropEvent.todoIdText(): String? {
    val clipData = toAndroidDragEvent().clipData ?: return null
    for (index in 0 until clipData.itemCount) {
        val text = clipData.getItemAt(index).text?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            return text
        }
    }
    return null
}

private fun Modifier.calendarInAppDateDropTarget(
    targetId: String,
    date: LocalDate,
    enabled: Boolean,
    dropTargets: MutableMap<String, CalendarDateDropTargetBounds>,
): Modifier {
    if (!enabled) return this

    return composed {
        DisposableEffect(targetId) {
            onDispose {
                dropTargets.remove(targetId)
            }
        }
        onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            val size = coordinates.size
            dropTargets[targetId] = CalendarDateDropTargetBounds(
                date = date,
                bounds = Rect(
                    left = position.x,
                    top = position.y,
                    right = position.x + size.width,
                    bottom = position.y + size.height,
                ),
            )
        }
    }
}

@Composable
private fun CalendarDayCard(
    selectedDate: LocalDate,
    minNavigableMonth: YearMonth,
    today: LocalDate,
    tasksByDate: Map<LocalDate, List<TodoItem>>,
    canGoPrevDay: Boolean,
    canSelectDate: (LocalDate) -> Boolean,
    todayJumpRequest: CalendarTodayJumpRequest?,
    onTodayJumpHandled: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val minDate = remember(minNavigableMonth) { minNavigableMonth.atDay(1) }
    val currentPage = remember(minDate, selectedDate) {
        ChronoUnit.DAYS.between(minDate, selectedDate)
            .toInt()
            .coerceIn(0, CalendarDayPagerPageCount - 1)
    }
    var scrollRequest by remember { mutableStateOf<CalendarPagerScrollRequest?>(null) }
    val isPagingAtRest = scrollRequest == null

    fun requestPage(offset: Int) {
        val targetIndex = (currentPage + offset).coerceIn(0, CalendarDayPagerPageCount - 1)
        if (targetIndex == currentPage || !isPagingAtRest) return
        // Same synchronous write as the week card above, for the same reason.
        scrollRequest = CalendarPagerScrollRequest(
            id = System.nanoTime().toInt(),
            page = targetIndex,
        )
    }

    fun dateForPage(page: Int): LocalDate {
        return minDate.plusDays(page.toLong())
    }

    fun settlePage(page: Int) {
        TdayTelemetry.addBreadcrumb(
            "calendar.page",
            data = mapOf(
                "mode" to "day",
                "direction" to if (page >= currentPage) "next" else "previous",
            ),
        )
        onSelectDate(dateForPage(page))
    }

    LaunchedEffect(todayJumpRequest) {
        val request = todayJumpRequest ?: return@LaunchedEffect
        when (
            val jump = dayPagerTodayJump(
                minDate = minDate,
                currentPage = currentPage,
                targetDate = request.targetDate,
            )
        ) {
            is CalendarTodayJump.SelectNow -> onSelectDate(jump.date)

            // Alone of the three, this pager's page *is* its date, so the settle
            // below re-derives exactly the day the jump asked for and nothing
            // has to wait in the air for it.
            is CalendarTodayJump.PageThenSelect ->
                scrollRequest = CalendarPagerScrollRequest(request.id, jump.page)
        }
        onTodayJumpHandled(request.id)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalendarCardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CalendarCardHorizontalPadding,
                    top = CalendarPeriodCardTopPadding,
                    end = CalendarCardHorizontalPadding,
                    bottom = CalendarPeriodCardBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXl),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarCardHeaderHeight)
                    .padding(horizontal = CalendarCardHeaderHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniCalendarNavButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_left),
                    contentDescription = stringResource(R.string.calendar_prev_day),
                    enabled = canGoPrevDay && isPagingAtRest,
                    onClick = { requestPage(-1) },
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = CalendarPeriodHeaderTitleSize,
                        ),
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                MiniCalendarNavButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_right),
                    contentDescription = stringResource(R.string.calendar_next_day),
                    enabled = isPagingAtRest,
                    onClick = { requestPage(1) },
                )
            }

            CalendarPagingContent(
                pageCount = CalendarDayPagerPageCount,
                currentPage = currentPage,
                onPageSettled = ::settlePage,
                scrollRequest = scrollRequest,
                onScrollRequestHandled = { requestId ->
                    if (scrollRequest?.id == requestId) {
                        scrollRequest = null
                    }
                },
                pageKey = { page -> "day-${dateForPage(page)}" },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarPeriodCardPageHeight),
            ) { page ->
                val displayDate = remember(minDate, page) { dateForPage(page) }
                val taskCount = tasksByDate[displayDate]?.size ?: 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(TdayDimens.RadiusRow))
                        .background(Color.Transparent)
                        .padding(horizontal = TdayDimens.SpacingSm, vertical = TdayDimens.SpacingXs),
                    verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXl),
                ) {
                    Text(
                        text = displayDate.format(
                            DateTimeFormatter.ofPattern(
                                "MMMM d, yyyy",
                                Locale.getDefault()
                            )
                        ),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = CalendarDaySummaryTitleSize,
                        ),
                        color = when {
                            displayDate == today -> CalendarAccentPurple
                            else -> colorScheme.onSurface
                        },
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = if (taskCount == 1) {
                            stringResource(R.string.calendar_task_count_one)
                        } else {
                            stringResource(R.string.calendar_task_count_many, taskCount)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = CalendarDaySummaryCountSize,
                        ),
                        color = colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

private fun formatWeekRange(weekStart: LocalDate): String {
    val weekEnd = weekStart.plusDays(6)
    val monthShortFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val sameMonth = weekStart.month == weekEnd.month && weekStart.year == weekEnd.year
    return if (sameMonth) {
        "${weekStart.format(monthShortFormatter)} ${weekStart.dayOfMonth}-${weekEnd.dayOfMonth}, ${weekEnd.year}"
    } else {
        "${weekStart.format(monthShortFormatter)} ${weekStart.dayOfMonth} - " +
            "${weekEnd.format(monthShortFormatter)} ${weekEnd.dayOfMonth}, ${weekEnd.year}"
    }
}

/**
 * The plain circle this screen's other toolbar actions sit in.
 *
 * A local copy of the timeline screen's `TodayHeaderButton`, which is private to
 * `TodoListScreen` and has no shared home yet — the fill, the size and the lift
 * come from the same tokens as the back button beside it, so the two match
 * whatever the scheme does with them.
 */
@Composable
private fun CalendarBarButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .tdayPressable(interactionSource, scale = TdayMotionTokens.PressScales.Bar),
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = tdayBarButtonContainerColor()),
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.BarButtonElevation,
            pressedElevation = TdayDimens.CardElevationDefault,
        ),
    ) {
        Box(
            modifier = Modifier.size(TdayDimens.FabSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(CalendarBarButtonIconSize),
            )
        }
    }
}

/**
 * Accent "Today" action shown in the calendar top bar. While the title is down
 * (expanded) it shows the word "Today"; once the title is pulled up (collapsed)
 * it shrinks to an icon-only circle, matching the native iOS behavior.
 */
@Composable
private fun CalendarTodayButton(
    // A lambda, so a scroll frame invalidates only this button rather than the
    // whole calendar.
    collapseProgress: () -> Float,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val showLabel = collapseProgress().coerceIn(0f, 1f) < 0.5f

    val accentColor = if (isDarkTheme) CalendarAccentPurpleOnDark else CalendarAccentPurple
    // A cap, not a page margin. `SpacingXxl` is `ContentPaddingHorizontal` — the
    // rung the toolbar itself is inset by — and spending it again INSIDE a 56dp
    // control made 36 of the pill's 123dp air, so the thing read loose at the
    // same time as it read wide. `SpacingXl` is the rung below it and takes the
    // expanded pill to 109dp: 1.95x a circle rather than 2.20x.
    val horizontalPadding by animateDpAsState(
        targetValue = if (showLabel) {
            TdayDimens.SpacingXl
        } else {
            TdayDimens.SpacingNone
        },
        label = "calendarTodayButtonPadding",
    )

    Card(
        modifier = Modifier
            .tdayPressable(interactionSource, scale = TdayMotionTokens.PressScales.Bar)
            .animateContentSize(),
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        // The bar's own material, and no border. What used to be here — a 12%
        // purple wash under a 48% purple hairline — was defended as the mark of
        // an accented pill rather than a plain circle, and that argument was
        // imported rather than wrong. On iOS it holds, because the search
        // button beside it is `.outlined` and wears the identical wash and ring
        // (CalendarScreen.swift:2481-2488, :2748-2758) and `.outlined` there
        // carries no shadow at all (:2724). Android's neighbour is filled, so
        // the port landed as the only stroked control in any toolbar in the app
        // — 1 of 29 `BorderStroke` call sites, every other one a sheet, a card,
        // a list row, the dock track or a day cell — wearing the lift iOS
        // suppresses on exactly that material.
        //
        // It also measured worse than the bar it sat on. The wash composited to
        // #E6E5F3 in light, 1.15:1 from the bar, which signifies nothing — and
        // it cost the label the contrast it was meant to buy: the same accent
        // reads 4.33:1 on the bare bar and 3.75:1 on the wash. Dark was 3.59:1,
        // and 2.91:1 once `TdayTheme`'s default dynamic scheme picks the
        // background, i.e. below the 3:1 a graphical object owes, on the
        // configuration every Android 12+ device actually runs. The hairline
        // was 1.86:1 light / 2.29:1 dark against the bar — under the 3:1 WCAG
        // 1.4.11 asks of a boundary, so it identified nothing, while at 2.75x
        // density a 1dp stroke that weak rasterises to the smudge the report
        // called muddy. Beside it the plain circles run 15.65:1 and 16.07:1.
        //
        // On the shared fill the ink is 4.67:1 light and — as
        // `CalendarAccentPurpleOnDark`, which is where the dark numbers stop
        // being the same purple — 6.63:1 dark, 4.75:1 on the lightest dynamic
        // dark, clearing 4.5:1 in both themes. Nothing is
        // lost by it, because the wash was never the signal: this is still the
        // only coloured content in a bar of `onSurface` glyphs and still the
        // only control that grows a word, which is what says destination rather
        // than mode. Web reached the same answer already — `CalendarClient.tsx`
        // :235 and :1061 are byte-identical material, `text-accent` and the word
        // the only difference. iOS is internally consistent and owes nothing.
        colors = CardDefaults.cardColors(containerColor = tdayBarButtonContainerColor()),
        // The lift every other circle in a bar carries. This one sat flat and
        // 2dp short of the back button beside it, which is the same
        // some-have-shadows-some-do-not the rest of the bars were fixed for.
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.BarButtonElevation,
            pressedElevation = TdayDimens.CardElevationDefault,
        ),
    ) {
        Row(
            modifier = Modifier
                .height(TdayDimens.FabSize)
                .sizeIn(minWidth = TdayDimens.FabSize)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_calendar),
                contentDescription = contentDescription,
                tint = accentColor,
                // The 22 every other glyph in every bar in the app is drawn at,
                // this screen's `CalendarBarButtonIconSize` included. At
                // `IconLg` a 24-viewport lucide path put its 2-unit stroke on
                // screen at 2.33dp against the chevron's and the magnifier's
                // 1.83dp — 27% heavier, which is the back button's own fix in
                // `TdayHeroTitleHeader` arriving here a size down, and iOS's
                // rule for a resized asset rather than an SF symbol
                // (CalendarScreen.swift:2684-2695). It was the last thing in the
                // bar drawn at a different weight from everything beside it.
                modifier = Modifier.size(CalendarBarButtonIconSize),
            )
            if (showLabel) {
                Text(
                    text = label,
                    color = accentColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = TdayDimens.SpacingMd),
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthCard(
    visibleMonth: YearMonth,
    minNavigableMonth: YearMonth,
    canGoPrevMonth: Boolean,
    selectedDate: LocalDate,
    today: LocalDate,
    tasksByDate: Map<LocalDate, List<TodoItem>>,
    draggedTodo: TodoItem?,
    activeDropDate: LocalDate?,
    dropTargets: MutableMap<String, CalendarDateDropTargetBounds>,
    canSelectDate: (LocalDate) -> Boolean,
    todayJumpRequest: CalendarTodayJumpRequest?,
    onTodayJumpHandled: (Int) -> Unit,
    onVisibleMonthChanged: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onDropDateChanged: (LocalDate?) -> Unit,
    onMoveTaskToDate: (TodoItem, LocalDate) -> Unit,
    resolveTodo: (String) -> TodoItem?,
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentPage = remember(minNavigableMonth, visibleMonth) {
        ChronoUnit.MONTHS.between(minNavigableMonth, visibleMonth)
            .toInt()
            .coerceIn(0, CalendarMonthPagerPageCount - 1)
    }
    var scrollRequest by remember { mutableStateOf<CalendarPagerScrollRequest?>(null) }
    val isPagingAtRest = scrollRequest == null

    fun requestPage(offset: Int) {
        val targetIndex = (currentPage + offset).coerceIn(0, CalendarMonthPagerPageCount - 1)
        if (targetIndex == currentPage || !isPagingAtRest) return
        // Same synchronous write as the week card above, for the same reason.
        scrollRequest = CalendarPagerScrollRequest(
            id = System.nanoTime().toInt(),
            page = targetIndex,
        )
    }

    fun monthForPage(page: Int): YearMonth {
        return minNavigableMonth.plusMonths(page.toLong())
    }

    // The date a Today jump is carrying while its sweep is in the air. A settled
    // month page carries no day of its own — swiping to November means "show me
    // November", not "select a day in November" — so a cross-month jump has to
    // hand its date over here or arrive with the selection left behind.
    var pendingTodayJumpDate by remember { mutableStateOf<LocalDate?>(null) }

    fun settlePage(page: Int) {
        // Consumed on any settle, not only a matching one: see the week card.
        val jumpTarget = pendingTodayJumpDate
        pendingTodayJumpDate = null
        TdayTelemetry.addBreadcrumb(
            "calendar.page",
            data = mapOf(
                "mode" to "month",
                "direction" to if (page >= currentPage) "next" else "previous",
            ),
        )
        val jumpSelection = monthPageSettleSelection(
            minNavigableMonth = minNavigableMonth,
            page = page,
            pendingJumpDate = jumpTarget,
        )
        if (jumpSelection != null) {
            // `onSelectDate` moves the visible month as well as the day, so this
            // is the whole settle — and it is the only thing that moves the
            // "Tasks due …" heading, the day list and the `+` prefill onto the
            // month the sweep just landed on.
            onSelectDate(jumpSelection)
        } else {
            onVisibleMonthChanged(monthForPage(page))
        }
    }

    LaunchedEffect(todayJumpRequest) {
        val request = todayJumpRequest ?: return@LaunchedEffect
        when (
            val jump = monthPagerTodayJump(
                minNavigableMonth = minNavigableMonth,
                currentPage = currentPage,
                targetDate = request.targetDate,
            )
        ) {
            is CalendarTodayJump.SelectNow -> {
                pendingTodayJumpDate = null
                onSelectDate(jump.date)
            }

            is CalendarTodayJump.PageThenSelect -> {
                pendingTodayJumpDate = jump.date
                scrollRequest = CalendarPagerScrollRequest(request.id, jump.page)
            }
        }
        onTodayJumpHandled(request.id)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalendarCardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CalendarCardHorizontalPadding,
                    top = CalendarMonthCardTopPadding,
                    end = CalendarCardHorizontalPadding,
                    bottom = CalendarMonthCardBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(CalendarMonthCardOuterSpacing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarCardHeaderHeight)
                    .padding(horizontal = CalendarCardHeaderHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniCalendarNavButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_left),
                    contentDescription = stringResource(R.string.calendar_prev_month),
                    enabled = canGoPrevMonth && isPagingAtRest,
                    onClick = { requestPage(-1) },
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                            " " + visibleMonth.year,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = CalendarMonthHeaderTitleSize,
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface,
                    )
                }
                MiniCalendarNavButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_right),
                    contentDescription = stringResource(R.string.calendar_next_month),
                    enabled = isPagingAtRest,
                    onClick = { requestPage(1) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarMonthWeekdayHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WEEKDAY_HEADERS.forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            CalendarPagingContent(
                pageCount = CalendarMonthPagerPageCount,
                currentPage = currentPage,
                onPageSettled = ::settlePage,
                scrollRequest = scrollRequest,
                onScrollRequestHandled = { requestId ->
                    if (scrollRequest?.id == requestId) {
                        scrollRequest = null
                    }
                },
                pageKey = { page -> "month-${monthForPage(page)}" },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarMonthGridHeight),
            ) { page ->
                val displayMonth = remember(minNavigableMonth, page) { monthForPage(page) }
                val monthDays = remember(displayMonth) { buildMonthCells(displayMonth) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CalendarMonthGridSpacing),
                ) {
                    monthDays.chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingNone),
                        ) {
                            week.forEach { cell ->
                                val taskCount = tasksByDate[cell.date]?.size ?: 0
                                val isEnabled = canSelectDate(cell.date)
                                val dropEligibleDraggedTodo = draggedTodo?.takeIf { todo ->
                                    isEnabled && !calendarTaskAlreadyDueOnDate(todo, cell.date)
                                }
                                CalendarDayCell(
                                    pageMonth = displayMonth,
                                    cell = cell,
                                    taskCount = taskCount,
                                    isSelected = cell.date == selectedDate,
                                    isToday = cell.date == today,
                                    isEnabled = isEnabled,
                                    isDropTarget = activeDropDate == cell.date &&
                                        (draggedTodo == null || dropEligibleDraggedTodo != null),
                                    draggedTodo = dropEligibleDraggedTodo,
                                    dropTargets = dropTargets,
                                    onClick = { onSelectDate(cell.date) },
                                    onDropDateChanged = onDropDateChanged,
                                    onMoveTaskToDate = onMoveTaskToDate,
                                    resolveTodo = resolveTodo,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun MiniCalendarNavButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val enabledIconTint = iconTint ?: colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .width(CalendarCardNavButtonWidth)
            .height(CalendarCardNavButtonHeight)
            .clip(RoundedCornerShape(CalendarCardNavButtonRadius))
            .background(
                color = if (enabled && isPressed) {
                    colorScheme.surfaceVariant.copy(alpha = 0.6f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = CalendarCardNavButtonRippleRadius,
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                enabledIconTint
            } else {
                colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
            },
            modifier = Modifier.size(CalendarCardNavIconSize),
        )
    }
}

@Composable
private fun CalendarDayCell(
    pageMonth: YearMonth,
    cell: CalendarDayCellModel,
    taskCount: Int,
    isSelected: Boolean,
    isToday: Boolean,
    isEnabled: Boolean,
    isDropTarget: Boolean,
    draggedTodo: TodoItem?,
    dropTargets: MutableMap<String, CalendarDateDropTargetBounds>,
    onClick: () -> Unit,
    onDropDateChanged: (LocalDate?) -> Unit,
    onMoveTaskToDate: (TodoItem, LocalDate) -> Unit,
    resolveTodo: (String) -> TodoItem?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetCellBackground = when {
        isDropTarget -> colorScheme.error.copy(alpha = 0.20f)
        isSelected -> CalendarAccentPurple.copy(alpha = if (isPressed) 0.32f else 0.24f)
        isToday -> CalendarTodayBlue.copy(alpha = if (isPressed) 0.24f else 0.16f)
        isPressed && isEnabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val cellBackground by animateColorAsState(
        targetValue = targetCellBackground,
        label = "calendarMonthDateCellBackground",
    )
    val targetCellBorderColor = when {
        isDropTarget -> colorScheme.error
        isSelected -> CalendarAccentPurple.copy(alpha = 0.95f)
        isToday -> CalendarTodayBlue.copy(alpha = 0.74f)
        isPressed && isEnabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
        else -> Color.Transparent
    }
    val cellBorderColor by animateColorAsState(
        targetValue = targetCellBorderColor,
        label = "calendarMonthDateCellBorder",
    )
    val targetCellBorderWidth = when {
        isDropTarget -> CalendarDayCellBorderDropTarget
        isSelected -> CalendarDayCellBorderSelected
        isToday -> CalendarDayCellBorderToday
        isPressed && isEnabled -> CalendarDayCellBorderPressed
        else -> TdayDimens.SpacingNone
    }
    val cellBorderWidth by animateDpAsState(
        targetValue = targetCellBorderWidth,
        label = "calendarMonthDateCellBorderWidth",
    )
    val stateTint = when {
        isDropTarget -> colorScheme.error
        isSelected -> CalendarAccentPurple
        isToday -> CalendarTodayBlue
        else -> CalendarAccentPurple
    }
    val cellShape = RoundedCornerShape(TdayDimens.RadiusRow)
    val dayTextColor = when {
        isDropTarget || isSelected || isToday -> stateTint
        cell.isCurrentMonth -> colorScheme.onSurface
        else -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CalendarMonthDayCellHeight)
            .graphicsLayer { alpha = if (cell.isCurrentMonth) 1f else 0.45f }
            .calendarDateDropTarget(
                date = cell.date,
                draggedTodo = draggedTodo,
                enabled = isEnabled,
                onDropDateChanged = onDropDateChanged,
                onMoveTaskToDate = onMoveTaskToDate,
                resolveTodo = resolveTodo,
            )
            .calendarInAppDateDropTarget(
                // Scope the ID to the pager page: leading/trailing dates also appear on the
                // pre-composed adjacent page, which must not overwrite this page's bounds.
                targetId = "month-$pageMonth-${cell.date}",
                date = cell.date,
                enabled = isEnabled && draggedTodo != null,
                dropTargets = dropTargets,
            )
            .clickable(
                enabled = isEnabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(CalendarMonthDayHighlightWidth)
                .height(CalendarMonthDayHighlightHeight)
                .clip(cellShape)
                .background(cellBackground, cellShape)
                .border(
                    width = cellBorderWidth,
                    color = cellBorderColor,
                    shape = cellShape,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                CalendarMonthDayCellContentSpacing,
                Alignment.CenterVertically,
            ),
        ) {
            Box(
                modifier = Modifier
                    .width(CalendarMonthDayNumberWidth)
                    .height(CalendarMonthDayNumberHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cell.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 19.sp),
                    color = dayTextColor,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier.height(CalendarMonthTaskCountHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXxs),
            ) {
                if (taskCount > 0 && isEnabled) {
                    Box(
                        modifier = Modifier
                            .size(CalendarMonthTaskDotSize)
                            .background(stateTint, CircleShape),
                    )
                    Text(
                        text = if (taskCount > 9) {
                            stringResource(R.string.calendar_task_count_cap)
                        } else {
                            taskCount.toString()
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                        ),
                        color = stateTint,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarTaskDragPreview(
    modifier: Modifier = Modifier,
    todo: TodoItem,
    lists: List<ListSummary>,
) {
    val colorScheme = MaterialTheme.colorScheme
    val listMeta = todo.listId?.let { listId -> lists.firstOrNull { it.id == listId } }
    val previewShape = RoundedCornerShape(TdayDimens.RadiusLg)
    // The pick-up itself. This card used to be composed straight into its final
    // size and elevation, so the one frame that says "the app has your task"
    // never existed; [TdayDragLift] argues the rise and its two ends.
    val lift by TdayDragLift.rememberProgress(rememberTdayMotionEnabled())
    Card(
        modifier = modifier
            .sizeIn(minWidth = CalendarDragPreviewMinWidth, maxWidth = CalendarDragPreviewMaxWidth)
            .graphicsLayer {
                val scale = TdayDragLift.scaleAt(lift)
                scaleX = scale
                scaleY = scale
            },
        shape = previewShape,
        // Opaque. A card the finger is holding is not a card the user may not
        // have, and partial alpha is what this app says everywhere else.
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(TdayDimens.BorderWidth, colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDragLift.elevationAt(lift)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = TdayDimens.SpacingXl,
                vertical = CalendarDragPreviewContentSpacing,
            ),
            horizontalArrangement = Arrangement.spacedBy(CalendarDragPreviewContentSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_circle),
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                modifier = Modifier.size(CalendarDragPreviewIconSize),
            )
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXxs),
            ) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                )
                todo.due?.let(CalendarTaskDragDueTimeFormatter::format)?.let { dueText ->
                    Text(
                        text = dueText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (listMeta != null) {
                Icon(
                    imageVector = tdayListIconForKey(listMeta.iconKey),
                    contentDescription = null,
                    tint = tdayListAccentColor(listMeta.color),
                    modifier = Modifier.size(CalendarRowTrailingIconSize),
                )
            }
            priorityIconFor(todo.priority)?.let { priorityIcon ->
                Icon(
                    imageVector = priorityIcon,
                    contentDescription = null,
                    tint = tdayPriorityColor(todo.priority),
                    modifier = Modifier.size(CalendarRowTrailingIconSize),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarTodoRow(
    modifier: Modifier = Modifier,
    todo: TodoItem,
    lists: List<ListSummary>,
    showDateDivider: Boolean,
    dragEnabled: Boolean,
    onComplete: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    dragging: Boolean,
    openSwipeTaskId: String?,
    onOpenSwipeTaskIdChange: (String?) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: (Offset?) -> Unit,
    onDragCancel: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val taskCompletionSound = rememberTaskCompletionSound()
    val coroutineScope = rememberCoroutineScope()
    // Edit + Copy + Delete: matches the 3-pill width used elsewhere (see
    // SwipeTaskRow.revealWidth).
    val swipeRevealState = rememberTaskSwipeRevealState(todo.id, revealWidth = CalendarSwipeRevealWidth)
    val clipboardManager = LocalClipboardManager.current
    val snackbarManager = LocalSnackbarManager.current
    val copyContext = LocalContext.current
    val copiedMessage = stringResource(R.string.task_copied_toast)
    val copyFailedMessage = stringResource(R.string.task_copy_failed_toast)
    var localChecked by remember(todo.id) { mutableStateOf(false) }
    var localStruck by remember(todo.id) { mutableStateOf(false) }
    var pendingCompletion by remember(todo.id) { mutableStateOf(false) }
    var completionFading by remember(todo.id) { mutableStateOf(false) }
    var rowOriginInRoot by remember(todo.id) { mutableStateOf(Offset.Zero) }
    var dragPointerPosition by remember(todo.id) { mutableStateOf<Offset?>(null) }
    val latestOpenSwipeTaskId = rememberUpdatedState(openSwipeTaskId)
    fun claimSwipeSlot() {
        if (latestOpenSwipeTaskId.value != todo.id) {
            onOpenSwipeTaskIdChange(todo.id)
        }
    }

    fun closeSwipeSlot() {
        swipeRevealState.close()
        if (latestOpenSwipeTaskId.value == todo.id) {
            onOpenSwipeTaskIdChange(null)
        }
    }
    val animatedOffsetX by animateTaskSwipeOffsetAsState(
        state = swipeRevealState,
        label = "calendarTaskSwipeOffset",
    )
    val motionEnabled = rememberTdayMotionEnabled()
    // Gated like the beats in front of it. The last leg of the check-off is timed
    // against this fade, so a fade still running while its own wait had been zeroed
    // would pull the row out of the list at full opacity — exactly the pop that leg
    // exists to prevent.
    val completionAlpha by animateFloatAsState(
        targetValue = if (completionFading) 0f else 1f,
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = CALENDAR_TASK_COMPLETION_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "calendarTaskCompletionAlpha",
    )
    val completionOffsetY by animateDpAsState(
        targetValue = if (completionFading) {
            CalendarTaskCompletionRiseOffsetY
        } else {
            TdayDimens.SpacingNone
        },
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = CALENDAR_TASK_COMPLETION_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "calendarTaskCompletionOffsetY",
    )
    // This used to be computed and never read — a 320ms animation nothing drew,
    // left behind when the swept rule came out of the row. It is back on the
    // screen now, through the modifier every task row shares.
    val titleStrikeProgress = rememberTaskStrikeProgress(localStruck, "calendarTaskTitleStrike")
    var titleLayoutResult by remember(todo.id) { mutableStateOf<TextLayoutResult?>(null) }
    var noteLayoutResult by remember(todo.id) { mutableStateOf<TextLayoutResult?>(null) }
    // The number behind that switch, for this row's waits rather than its specs:
    // the hint's two holds and the three legs of the check-off are all gaps
    // between beats this row gates on [motionEnabled], which is what makes the
    // app's own scale the right clock for them. See [scaledDelay].
    val rowMotionScale = rememberTdayMotionScale()
    val toggleTint by animateColorAsState(
        targetValue = if (localChecked) {
            TdayTaskCompleteAccent
        } else {
            colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        },
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Quick,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "calendarTaskToggleTint",
    )
    val titleColor by animateColorAsState(
        targetValue = if (localStruck) {
            colorScheme.onSurface.copy(alpha = 0.78f)
        } else {
            colorScheme.onSurface
        },
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Emphasis,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "calendarTaskTitleColor",
    )
    // The other half of the pick-up: the slot this card came out of. It used to
    // cut to 70 % on the frame the long press fired, alongside a preview that
    // cut to full size, which is two events for one gesture. Same rung as the
    // rise, so the row empties exactly as the card leaves it.
    val vacatedAlpha by animateFloatAsState(
        targetValue = if (dragging) TdayDragLift.VacatedAlpha else 1f,
        animationSpec = TdayDragLift.spec(motionEnabled),
        label = "calendarTaskDragVacated",
    )
    val dueText = todo.due
        ?.let {
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                .withZone(ZoneId.systemDefault()).format(it)
        }
    val listMeta = todo.listId?.let { listId -> lists.firstOrNull { it.id == listId } }
    val showListIndicator = listMeta != null
    val priorityIcon = priorityIconFor(todo.priority)
    val showPriorityIcon = priorityIcon != null
    val listIndicatorColor = tdayListAccentColor(listMeta?.color)
    val rowShape = RoundedCornerShape(TdayDimens.RadiusRow)
    val foregroundColor = colorScheme.background
    val actionRevealProgress = swipeRevealState.revealProgress(animatedOffsetX)
    LaunchedEffect(openSwipeTaskId, todo.id) {
        if (openSwipeTaskId != null && openSwipeTaskId != todo.id && swipeRevealState.isOpenOrDragging) {
            swipeRevealState.close()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = completionAlpha * vacatedAlpha
                translationY = completionOffsetY.toPx()
            }
            .semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CalendarTaskRowHeight)
                .height(IntrinsicSize.Min),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = TdayDimens.SpacingXxs),
                horizontalArrangement = Arrangement.spacedBy(CalendarSwipeActionSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarSwipeActionButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_square_pen),
                    contentDescription = stringResource(R.string.action_edit_task),
                    label = stringResource(R.string.action_edit),
                    tint = Color.White,
                    background = TdaySwipeEditBackground,
                    revealProgress = actionRevealProgress,
                    revealDelay = 0.62f,
                    onClick = {
                        TdayHaptics.buttonPress(view)
                        closeSwipeSlot()
                        onInfo()
                    },
                )
                CalendarSwipeActionButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_copy),
                    contentDescription = stringResource(R.string.action_copy_task),
                    label = stringResource(R.string.action_copy),
                    tint = Color.White,
                    background = TdaySwipeCopyBackground,
                    revealProgress = actionRevealProgress,
                    revealDelay = 0.40f,
                    onClick = {
                        TdayHaptics.buttonPress(view)
                        closeSwipeSlot()
                        runCatching {
                            clipboardManager.setText(AnnotatedString(taskCopyText(copyContext, todo)))
                        }.onSuccess {
                            snackbarManager?.showSuccess(copiedMessage)
                        }.onFailure {
                            snackbarManager?.showError(copyFailedMessage)
                        }
                    },
                )
                CalendarSwipeActionButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_trash_2),
                    contentDescription = stringResource(R.string.action_delete_task),
                    label = stringResource(R.string.action_delete),
                    tint = Color.White,
                    background = TdaySwipeDeleteBackground,
                    revealProgress = actionRevealProgress,
                    revealDelay = 0.04f,
                    onClick = {
                        TdayHaptics.destructive(view)
                        closeSwipeSlot()
                        onDelete()
                    },
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        rowOriginInRoot = coordinates.positionInRoot()
                    }
                    .graphicsLayer { translationX = animatedOffsetX }
                    .then(
                        if (dragEnabled) {
                            Modifier.pointerInput(todo.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset ->
                                        closeSwipeSlot()
                                        val startPosition = rowOriginInRoot + localOffset
                                        dragPointerPosition = startPosition
                                        onDragStart(startPosition)
                                        onDragMove(startPosition)
                                        TdayHaptics.dragPickUp(view)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val nextPosition =
                                            (dragPointerPosition ?: rowOriginInRoot) + dragAmount
                                        dragPointerPosition = nextPosition
                                        onDragMove(nextPosition)
                                    },
                                    onDragEnd = {
                                        onDragEnd(dragPointerPosition)
                                        dragPointerPosition = null
                                    },
                                    onDragCancel = {
                                        dragPointerPosition = null
                                        onDragCancel()
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            if (delta < 0f || swipeRevealState.isOpenOrDragging) {
                                claimSwipeSlot()
                            }
                            swipeRevealState.dragBy(delta)
                            if (!swipeRevealState.isOpenOrDragging && latestOpenSwipeTaskId.value == todo.id) {
                                onOpenSwipeTaskIdChange(null)
                            }
                        },
                        onDragStopped = { velocity ->
                            swipeRevealState.settle(velocity)
                            if (swipeRevealState.isOpenOrDragging) {
                                claimSwipeSlot()
                            } else if (latestOpenSwipeTaskId.value == todo.id) {
                                onOpenSwipeTaskIdChange(null)
                            }
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (swipeRevealState.isOpenOrDragging) {
                            closeSwipeSlot()
                        } else if (!swipeRevealState.isHinting && !pendingCompletion) {
                            claimSwipeSlot()
                            coroutineScope.launch {
                                swipeRevealState.playHint(rowMotionScale)
                                if (latestOpenSwipeTaskId.value == todo.id &&
                                    !swipeRevealState.isOpenOrDragging
                                ) {
                                    onOpenSwipeTaskIdChange(null)
                                }
                            }
                        }
                    },
                shape = rowShape,
                colors = CardDefaults.cardColors(containerColor = foregroundColor),
                elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = TdayDimens.SpacingXs, vertical = TdayDimens.SpacingXxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CalendarCompletionToggleIcon(
                        imageVector = if (localChecked) {
                            ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big)
                        } else {
                            ImageVector.vectorResource(R.drawable.ic_lucide_circle)
                        },
                        contentDescription = if (localChecked) {
                            stringResource(R.string.label_completed)
                        } else {
                            stringResource(R.string.label_mark_complete)
                        },
                        tint = toggleTint,
                        enabled = !pendingCompletion,
                        onClick = {
                            TdayHaptics.completion(view)
                            taskCompletionSound.play()
                            closeSwipeSlot()
                            localChecked = true
                            pendingCompletion = true
                            coroutineScope.launch {
                                scaledDelay(
                                    CALENDAR_TASK_COMPLETION_CHECK_TO_STRIKE_MS,
                                    rowMotionScale,
                                )
                                localStruck = true
                                scaledDelay(
                                    CALENDAR_TASK_COMPLETION_STRIKE_TO_FADE_MS,
                                    rowMotionScale,
                                )
                                completionFading = true
                                scaledDelay(
                                    CALENDAR_TASK_COMPLETION_FADE_MS,
                                    rowMotionScale,
                                )
                                onComplete()
                            }
                        },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = CalendarTaskRowTitleStartPadding),
                    ) {
                        Text(
                            text = todo.title,
                            // One rule per line, swept — the same modifier the task list's
                            // own row draws, which is why a two-line title here crosses
                            // out both lines rather than the gap between them.
                            modifier = Modifier.taskStrikethrough(
                                progress = titleStrikeProgress,
                                layout = titleLayoutResult,
                                color = titleColor,
                                thickness = TdayDimens.BorderWidthThick,
                            ),
                            color = titleColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            onTextLayout = { titleLayoutResult = it },
                        )
                        dueText?.let { text ->
                            Text(
                                text = text,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        flattenNotesToPlainText(todo.description).takeIf { it.isNotBlank() }?.let { note ->
                            val noteColor = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            Text(
                                text = note,
                                // Struck alongside the title, on the title's own sweep.
                                modifier = Modifier.taskStrikethrough(
                                    progress = titleStrikeProgress,
                                    layout = noteLayoutResult,
                                    color = noteColor,
                                    thickness = TdayDimens.BorderWidthThick,
                                ),
                                color = noteColor,
                                style = MaterialTheme.typography.bodySmall,
                                onTextLayout = { noteLayoutResult = it },
                            )
                        }
                    }
                    if (showListIndicator || showPriorityIcon) {
                        Row(
                            modifier = Modifier.padding(end = TdayDimens.Spacing3xl),
                            horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingMd),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showListIndicator) {
                                Icon(
                                    imageVector = tdayListIconForKey(listMeta?.iconKey),
                                    contentDescription = stringResource(R.string.label_task_list),
                                    tint = listIndicatorColor,
                                    modifier = Modifier.size(CalendarRowTrailingIconSize),
                                )
                            }
                            if (priorityIcon != null) {
                                Icon(
                                    imageVector = priorityIcon,
                                    contentDescription = stringResource(R.string.label_priority_task),
                                    tint = tdayPriorityColor(todo.priority),
                                    modifier = Modifier.size(CalendarRowTrailingIconSize),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showDateDivider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TdayDimens.BorderWidth)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )
        }
    }
}

@Composable
private fun CalendarCompletedTodoRow(
    item: CompletedItem,
    lists: List<ListSummary>,
    onUndoComplete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var pendingUncomplete by remember(item.id) { mutableStateOf(false) }
    var unstruck by remember(item.id) { mutableStateOf(false) }
    var fading by remember(item.id) { mutableStateOf(false) }
    val showCompletedState = !pendingUncomplete
    val showStrikethrough = !unstruck
    val restoreMotionEnabled = rememberTdayMotionEnabled()
    // Gated like the beats in front of it. The last leg of the restore is timed
    // against this fade, so a fade still running while its own wait had been zeroed
    // would pull the row out of the list at full opacity — exactly the pop that leg
    // exists to prevent.
    val rowAlpha by animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = if (restoreMotionEnabled) {
            tween(
                durationMillis = CALENDAR_TASK_COMPLETION_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "calendarCompletedRestoreAlpha",
    )
    val rowOffsetY by animateDpAsState(
        targetValue = if (fading) {
            CalendarTaskCompletionRiseOffsetY
        } else {
            TdayDimens.SpacingNone
        },
        animationSpec = if (restoreMotionEnabled) {
            tween(
                durationMillis = CALENDAR_TASK_COMPLETION_FADE_MS.toInt(),
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "calendarCompletedRestoreOffsetY",
    )
    // Un-completing is the check-off played backwards, and the rule retracts the
    // way it swept. `animateFloatAsState` starts AT its target, so a row that was
    // already complete when the screen opened is simply drawn struck — the sweep
    // only ever plays for the tap that asked for it.
    val titleStrikeProgress =
        rememberTaskStrikeProgress(showStrikethrough, "calendarCompletedTitleStrike")
    var titleLayoutResult by remember(item.id) { mutableStateOf<TextLayoutResult?>(null) }
    // Same three legs as the check-off, so the same clock. See [scaledDelay].
    val restoreMotionScale = rememberTdayMotionScale()
    val restoreToggleTint by animateColorAsState(
        targetValue = if (showCompletedState) {
            TdayTaskCompleteAccent
        } else {
            colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        },
        animationSpec = if (restoreMotionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Quick,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "calendarCompletedToggleTint",
    )
    val restoreTitleColor by animateColorAsState(
        targetValue = if (showStrikethrough) {
            colorScheme.onSurface.copy(alpha = 0.78f)
        } else {
            colorScheme.onSurface
        },
        animationSpec = if (restoreMotionEnabled) {
            tween(
                durationMillis = TdayMotionTokens.Durations.Emphasis,
                easing = TdayMotionTokens.Easings.Standard,
            )
        } else {
            snap()
        },
        label = "calendarCompletedTitleColor",
    )
    val dueText = item.due
        ?.let {
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                .withZone(ZoneId.systemDefault()).format(it)
        }
    val listMeta = item.resolveListSummary(lists)
    val listIndicatorColor = listMeta?.color?.let(::tdayListAccentColor)
        ?: item.listColor?.let(::tdayListAccentColor)
        ?: colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
    val showListIndicator = !item.listName.isNullOrBlank() || listMeta != null
    val priorityIcon = priorityIconFor(item.priority)
    val showPriorityIcon = priorityIcon != null
    val rowShape = RoundedCornerShape(TdayDimens.RadiusRow)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rowAlpha
                translationY = rowOffsetY.toPx()
            }
            .semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXs),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(CalendarTaskRowHeight),
            shape = rowShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TdayDimens.SpacingXs, vertical = TdayDimens.SpacingXxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarCompletionToggleIcon(
                    imageVector = if (showCompletedState) {
                        ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big)
                    } else {
                        ImageVector.vectorResource(R.drawable.ic_lucide_circle)
                    },
                    contentDescription = stringResource(R.string.label_undo_complete),
                    tint = restoreToggleTint,
                    enabled = !pendingUncomplete,
                    onClick = {
                        TdayHaptics.toggle(view, on = false)
                        pendingUncomplete = true
                        // The check-off's own beats, run backwards. This row used to
                        // keep a third set — 180 / 180 — so undoing a completion took
                        // a different length of time from making one, on the same
                        // screen, through the same control.
                        coroutineScope.launch {
                            scaledDelay(
                                CALENDAR_TASK_COMPLETION_CHECK_TO_STRIKE_MS,
                                restoreMotionScale,
                            )
                            unstruck = true
                            scaledDelay(
                                CALENDAR_TASK_COMPLETION_STRIKE_TO_FADE_MS,
                                restoreMotionScale,
                            )
                            fading = true
                            scaledDelay(
                                CALENDAR_TASK_COMPLETION_FADE_MS,
                                restoreMotionScale,
                            )
                            onUndoComplete()
                        }
                    },
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = CalendarTaskRowTitleStartPadding),
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier.taskStrikethrough(
                            progress = titleStrikeProgress,
                            layout = titleLayoutResult,
                            color = restoreTitleColor,
                            thickness = TdayDimens.BorderWidthThick,
                        ),
                        color = restoreTitleColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        onTextLayout = { titleLayoutResult = it },
                    )
                    dueText?.let { text ->
                        Text(
                            text = text,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (showPriorityIcon) {
                    Row(
                        modifier = Modifier.padding(end = TdayDimens.Spacing3xl),
                        horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingMd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showListIndicator) {
                            Icon(
                                imageVector = tdayListIconForKey(listMeta?.iconKey),
                                contentDescription = stringResource(R.string.label_task_list),
                                tint = listIndicatorColor,
                                modifier = Modifier.size(CalendarRowTrailingIconSize),
                            )
                        }
                        Icon(
                            imageVector = priorityIcon
                                ?: ImageVector.vectorResource(R.drawable.ic_lucide_flag),
                            contentDescription = stringResource(R.string.label_priority_task),
                            tint = tdayPriorityColor(item.priority),
                            modifier = Modifier.size(CalendarRowTrailingIconSize),
                        )
                    }
                } else if (showListIndicator) {
                    Icon(
                        imageVector = tdayListIconForKey(listMeta?.iconKey),
                        contentDescription = stringResource(R.string.label_task_list),
                        tint = listIndicatorColor,
                        modifier = Modifier
                            .padding(end = TdayDimens.Spacing3xl)
                            .size(CalendarRowTrailingIconSize),
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(TdayDimens.BorderWidth)
                .background(colorScheme.outlineVariant.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun CalendarSwipeActionButton(
    icon: ImageVector,
    contentDescription: String,
    label: String,
    tint: Color,
    background: Color,
    revealProgress: Float,
    revealDelay: Float,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // not a token — see docs/motion.md. This is multiplied by the reveal scale
    // below, so it is one factor of a composed transform rather than the press
    // scale a finger actually sees; the press-scale tokens are the whole scale.
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        label = "calendarSwipeActionScale",
    )
    val normalizedReveal = ((revealProgress - revealDelay) / (1f - revealDelay))
        .coerceIn(0f, 1f)
    val easedReveal = FastOutSlowInEasing.transform(normalizedReveal)

    Column(
        modifier = Modifier
            .sizeIn(minWidth = CalendarSwipeActionMinWidth)
            .graphicsLayer {
                alpha = easedReveal
                val revealScale = 0.38f + (0.62f * easedReveal)
                scaleX = pressedScale * revealScale
                scaleY = pressedScale * revealScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingXs),
    ) {
        Card(
            modifier = Modifier.size(
                width = CalendarSwipeActionButtonWidth,
                height = CalendarSwipeActionButtonHeight,
            ),
            onClick = onClick,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(TdayDimens.RadiusLg),
            colors = CardDefaults.cardColors(containerColor = background),
            elevation = CardDefaults.cardElevation(
                defaultElevation = TdayDimens.CardElevationDefault,
                pressedElevation = TdayDimens.CardElevationDefault,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(CalendarSwipeActionIconSize),
                )
            }
        }
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CalendarCompletionToggleIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .sizeIn(
                minWidth = CalendarCompletionToggleTouchTarget,
                minHeight = CalendarCompletionToggleTouchTarget,
            )
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = CalendarCompletionToggleRippleRadius,
                ),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Crossed over rather than swapped, for the reason the task list's own
        // toggle gives: here the glyph is the whole control, so a one-frame swap
        // is the control hard-cutting.
        Crossfade(
            targetState = imageVector,
            animationSpec = if (rememberTdayMotionEnabled()) {
                tween(
                    durationMillis = TdayMotionTokens.Durations.Quick,
                    easing = TdayMotionTokens.Easings.Standard,
                )
            } else {
                snap()
            },
            label = "calendarCompletionToggleGlyph",
        ) { glyph ->
            Icon(
                imageVector = glyph,
                contentDescription = contentDescription.takeIf { glyph == imageVector },
                tint = tint,
                modifier = Modifier.size(CalendarCompletionToggleIconSize),
            )
        }
    }
}

private data class CalendarDayCellModel(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
)

private fun buildMonthCells(month: YearMonth): List<CalendarDayCellModel> {
    val firstDay = month.atDay(1)
    val firstOffset = firstDay.dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val previousMonth = month.minusMonths(1)
    val daysInPreviousMonth = previousMonth.lengthOfMonth()
    val nextMonth = month.plusMonths(1)

    return List(42) { index ->
        val dayNumber = index - firstOffset + 1
        when {
            dayNumber < 1 -> {
                CalendarDayCellModel(
                    date = previousMonth.atDay(daysInPreviousMonth + dayNumber),
                    isCurrentMonth = false,
                )
            }

            dayNumber > daysInMonth -> {
                CalendarDayCellModel(
                    date = nextMonth.atDay(dayNumber - daysInMonth),
                    isCurrentMonth = false,
                )
            }

            else -> {
                CalendarDayCellModel(
                    date = month.atDay(dayNumber),
                    isCurrentMonth = true,
                )
            }
        }
    }
}

@Composable
private fun priorityIconFor(priority: String): ImageVector? {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "medium" -> ImageVector.vectorResource(R.drawable.ic_lucide_flag_filled)
        "high", "urgent", "important" -> ImageVector.vectorResource(R.drawable.ic_lucide_flag_filled)
        else -> null
    }
}

private fun CompletedItem.resolveListSummary(lists: List<ListSummary>): ListSummary? {
    val name = listName?.trim()?.lowercase(Locale.getDefault()) ?: return null
    return lists.firstOrNull { it.name.trim().lowercase(Locale.getDefault()) == name }
}

private val WEEKDAY_HEADERS = listOf("S", "M", "T", "W", "T", "F", "S")
