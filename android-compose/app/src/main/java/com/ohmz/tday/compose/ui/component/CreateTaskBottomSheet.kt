package com.ohmz.tday.compose.ui.component

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.ui.TdayHaptics
import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import com.ohmz.tday.compose.core.ui.TdaySheetMotion
import com.ohmz.tday.compose.feature.guide.GuideHelpLink
import com.ohmz.tday.compose.ui.priority.PRIORITY_OPTIONS_HIGH_TO_LOW
import com.ohmz.tday.compose.ui.priority.canonicalPriorityValue
import com.ohmz.tday.compose.ui.priority.priorityDisplayLabelRes
import com.ohmz.tday.compose.ui.theme.TdayTaskCompleteAccent
import com.ohmz.tday.compose.ui.theme.tdayListAccentColorOrNull
import com.ohmz.tday.compose.ui.theme.tdayListIconForList
import com.ohmz.tday.compose.ui.theme.tdayPriorityColor
import com.ohmz.tday.compose.core.data.RepeatSuggestionDismissalStore
import com.ohmz.tday.shared.guide.GuideTopicIds
import com.ohmz.tday.shared.nlp.RecurrencePriorityGrammar
import com.ohmz.tday.shared.nlp.RepeatSuggestionEngine
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import android.graphics.Color as AndroidColor

private enum class RepeatPreset(
    @StringRes val labelRes: Int,
    val rrule: String?,
) {
    NONE(R.string.create_task_repeat_none, null),
    DAILY(R.string.create_task_repeat_daily, "RRULE:FREQ=DAILY;INTERVAL=1"),
    WEEKLY(R.string.create_task_repeat_weekly, "RRULE:FREQ=WEEKLY;INTERVAL=1"),
    WEEKDAYS(R.string.create_task_repeat_weekdays, "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR"),
    MONTHLY(R.string.create_task_repeat_monthly, "RRULE:FREQ=MONTHLY;INTERVAL=1"),
    YEARLY(R.string.create_task_repeat_yearly, "RRULE:FREQ=YEARLY;INTERVAL=1"),
}

private const val DEFAULT_TASK_DURATION_MS = 60L * 60L * 1000L
private const val CREATE_TASK_SHEET_FLOATER_CREATE_HEIGHT_FRACTION = 0.54f
private const val CREATE_TASK_SHEET_EDIT_HEIGHT_FRACTION = 0.76f
private const val CREATE_TASK_SHEET_FLOATER_EDIT_HEIGHT_FRACTION = 0.54f
private const val CREATE_TASK_SHEET_MAX_HEIGHT_FRACTION = 0.86f
private const val CREATE_TASK_SHEET_KEYBOARD_HEIGHT_FRACTION = 0.85f

/**
 * The dismissal half of a sheet that is an `AnimatedVisibility` inside a [Dialog].
 *
 * Every dismiss affordance used to call the caller's `onDismiss` directly: the scrim tap,
 * the close button, and the host `Dialog`'s own back/outside-tap request. Each caller's
 * `onDismiss` flips the state that composes the sheet at all — `showCreateTaskSheet = false`,
 * `editTargetTodoId = null`, `finish()` for the widget — so the whole `Dialog` left the
 * composition on the frame of the tap and the exit spec never animated a single frame. The
 * sheet was cut, not slid, from all seven call sites, and the flag driving `visible` was set
 * true once and never set false, which is exactly what Rule B of the Android
 * motion-reachability guardrail reports.
 *
 * So dismissal is two steps and every path takes both. [start] flips the transition's target
 * to false and the exit plays; the caller is told only once the transition has settled on
 * "gone", which is the first moment it is safe to tear the composition down. Because the
 * sheet's own state outlives the tap, its content is still composed — and still readable —
 * for the whole slide out.
 *
 * Waiting on the transition rather than on a `delay(TdaySheetMotion.CardOutMillis)` also keeps
 * the handoff honest when the system animation scale is 0: the transition settles on the next
 * frame and the sheet closes immediately, instead of stranding a sheet-less scrim on screen
 * for the length of an exit nobody is playing.
 */
@Stable
internal class SheetDismissState(internal val transition: MutableTransitionState<Boolean>) {
    /** True once a dismissal has been asked for. The exit may still be playing. */
    var dismissing: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether the sheet is meant to be on screen — the target, not where the card has got
     * to.
     *
     * This is what a *second* surface animating alongside the card reads, and it exists so
     * that surface does not share [transition] instead. A `MutableTransitionState` handed
     * to two `AnimatedVisibility` calls is driven by two `Transition`s, and each of them
     * writes `currentState` and clears `isRunning` when *it* finishes — so the shorter of
     * the two would report the whole dismissal settled while the longer was still playing,
     * and [gone] would tear the composition down over a card that is still moving. That is
     * the exact defect this class was written to remove, arriving through the back door.
     */
    val visible: Boolean
        get() = transition.targetState

    /** The exit has finished and the sheet is off screen; the host can go away now. */
    val gone: Boolean
        get() = dismissing && transition.isIdle && !transition.currentState

    /**
     * Start the exit, and answer whether this call is the one that started it.
     *
     * Repeat taps during the slide out are ignored rather than queued, and the answer is
     * what a *confirm* reads to know it is a repeat. A dismiss affordance can discard it:
     * the second tap on the X wants what the first tap already asked for. A confirm cannot,
     * because it hands a payload over on its way out. The card stays composed and
     * hit-testable for the whole exit — that is the point of this class — and the exit
     * accelerates, so a second tap a few frames later still lands on a Create button that
     * has barely moved. Let it through and the caller mints a second task from a second
     * payload, which is the duplicate the old shape was immune to only because the host
     * tore the composition down on the frame of the tap.
     */
    fun start(): Boolean {
        if (dismissing) return false
        dismissing = true
        transition.targetState = false
        return true
    }
}

/**
 * Remembers a [SheetDismissState], runs the sheet's enter, and calls [onDismissed] exactly
 * once — after the exit has finished.
 *
 * [presentImmediately] is for a host that is itself the sheet (the widget create activity):
 * it opens already showing it, so there is nothing to slide in.
 */
@Composable
internal fun rememberSheetDismissState(
    presentImmediately: Boolean = false,
    onDismissed: () -> Unit,
): SheetDismissState {
    val state = remember { SheetDismissState(MutableTransitionState(presentImmediately)) }
    LaunchedEffect(Unit) { state.transition.targetState = true }
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    LaunchedEffect(state.gone) {
        if (state.gone) currentOnDismissed()
    }
    return state
}

/**
 * The row an edit sheet is editing, held for as long as the sheet is on screen.
 *
 * Every host of the edit sheet keeps the *id* — it survives process death, the row does not
 * — looks it up in the feed it is currently showing, and composes the sheet inside
 * `target?.let { … }`. That was sound while confirming tore the sheet down on the frame of
 * the tap. It stops being sound now that the sheet outlives the confirm by its exit: saving
 * an edit that moves the task out of the feed underneath it — a due date pushed off today
 * on the Today feed, a list changed on a list feed — drops the row from the state while the
 * card is still sliding, the `let` stops composing, and the sheet is cut by its host
 * instead of by its own callback. The same defect one level up, and reachable only by the
 * change that fixed the first one.
 *
 * So the lookup is answered once and retained: the card keeps drawing the row it was opened
 * on until [id] itself goes null, which is the host's teardown at the end of the exit.
 * Retained in a plain holder rather than in snapshot state, because nothing should
 * recompose when it is written — the recomposition doing the writing is the one the feed
 * change already caused.
 */
@Composable
internal fun <T : Any> rememberEditSheetTarget(id: Any?, current: T?): T? {
    val holder = remember(id) { EditSheetTargetHolder<T>() }
    if (id == null) return null
    if (current != null) holder.value = current
    return holder.value
}

private class EditSheetTargetHolder<T : Any> {
    var value: T? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskBottomSheet(
    lists: List<ListSummary>,
    editingTask: TodoItem? = null,
    defaultListId: String? = null,
    defaultPriority: String? = null,
    defaultScheduled: Boolean = true,
    showScheduleControls: Boolean = true,
    initialDueEpochMs: Long? = null,
    initialTitle: String? = null,
    initialNotes: String? = null,
    presentImmediately: Boolean = false,
    onParseTaskTitleNlp: (suspend (
        title: String,
        referenceDueEpochMs: Long,
    ) -> TodoTitleNlpResponse?)? = null,
    onSuggestRepeat: (suspend (title: String) -> String?)? = null,
    onDismiss: () -> Unit,
    onCreateTask: (CreateTaskPayload) -> Unit,
    onUpdateTask: ((todo: TodoItem, payload: CreateTaskPayload) -> Unit)? = null,
) {
    // Viewers can't create or move tasks into a shared list, so those lists are
    // not offered as targets (the task being edited keeps its current list).
    @Suppress("NAME_SHADOWING")
    val lists = lists.filter { !it.isViewer || it.id == editingTask?.listId }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
    val dateOnlyFormatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    val timeOnlyFormatter = remember {
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    val listIdsKey = remember(lists) { lists.joinToString(separator = "|") { it.id } }

    val isEditMode = editingTask != null
    // Turning Schedule off on an existing task converts it into a Floater (demote), and a
    // recurring task cannot be demoted — the backend refuses, because its series would be
    // silently destroyed. So the switch is not offered for one. The task still shows as
    // scheduled; the repeat row below says why it cannot become unscheduled.
    val canToggleSchedule = showScheduleControls && !(editingTask?.isRecurring == true)
    var title by rememberSaveable(editingTask?.id) {
        mutableStateOf(editingTask?.title ?: initialTitle.orEmpty())
    }
    // The natural-language date phrase detected in the title (e.g. "July 29 at
    // 8pm"). Kept visible & highlighted in the field as you type, and stripped
    // from the saved title on submit — mirroring the web.
    var nlpMatchedText by rememberSaveable(editingTask?.id) {
        mutableStateOf<String?>(null)
    }
    // Authoritative start offset of the matched phrase (from the parser), so highlight
    // and submit-strip target the exact span the parser consumed instead of the first
    // indexOf() occurrence (which is wrong when the phrase repeats in the title).
    var nlpMatchStart by rememberSaveable(editingTask?.id) {
        mutableStateOf(-1)
    }
    var notes by rememberSaveable(editingTask?.id) {
        mutableStateOf(editingTask?.description ?: initialNotes.orEmpty())
    }
    var selectedListId by rememberSaveable(editingTask?.id, defaultListId, listIdsKey) {
        mutableStateOf(
            editingTask?.listId?.takeIf { id -> lists.any { it.id == id } }
                ?: defaultListId?.takeIf { id -> lists.any { it.id == id } },
        )
    }
    var selectedPriority by rememberSaveable(editingTask?.id, defaultPriority) {
        mutableStateOf(
            canonicalPriorityValue(
                editingTask?.priority
                    ?: defaultPriority
                    ?: lists.firstOrNull { it.id == selectedListId }?.defaultPriority,
            ),
        )
    }
    // Only a NEW task's priority tracks its list's default; an edit always starts
    // "touched" so opening the sheet on an existing task never rewrites its priority.
    var priorityTouchedByUser by rememberSaveable(editingTask?.id) {
        mutableStateOf(editingTask != null)
    }
    LaunchedEffect(selectedListId) {
        if (editingTask == null && !priorityTouchedByUser) {
            selectedPriority = canonicalPriorityValue(
                defaultPriority ?: lists.firstOrNull { it.id == selectedListId }?.defaultPriority,
            )
        }
    }
    val nowEpochMs = remember { val ms = System.currentTimeMillis(); ms - (ms % 60_000L) }
    val resolvedDueEpochMs = editingTask?.due?.toEpochMilli()
        ?: (initialDueEpochMs ?: (nowEpochMs + DEFAULT_TASK_DURATION_MS))
    var dueEpochMs by rememberSaveable(editingTask?.id, initialDueEpochMs) {
        mutableStateOf(resolvedDueEpochMs)
    }
    var scheduleEnabled by rememberSaveable(editingTask?.id, defaultScheduled) {
        mutableStateOf(showScheduleControls && (editingTask?.due != null || (editingTask == null && defaultScheduled)))
    }
    var selectedRepeat by rememberSaveable(editingTask?.id) {
        mutableStateOf(repeatPresetFromRrule(editingTask?.rrule).name)
    }
    LaunchedEffect(scheduleEnabled) {
        if (!scheduleEnabled) {
            selectedRepeat = RepeatPreset.NONE.name
        }
    }
    LaunchedEffect(title, onParseTaskTitleNlp) {
        val nlpParser = onParseTaskTitleNlp ?: return@LaunchedEffect
        if (title.isBlank()) {
            nlpMatchedText = null
            nlpMatchStart = -1
            return@LaunchedEffect
        }

        delay(260)
        // Pass the raw title so the matched date phrase aligns with what's shown in the
        // field (used to highlight it). Matched phrases stay in the field; they're only
        // removed from the saved title on submit.
        val parseResult = runCatching { nlpParser(title, dueEpochMs) }.getOrNull()
        if (parseResult == null) {
            nlpMatchedText = null
            nlpMatchStart = -1
            return@LaunchedEffect
        }

        // Date span highlight — only when a date phrase was actually matched.
        val matched = parseResult.matchedText
        if (matched.isNullOrEmpty()) {
            nlpMatchedText = null
            nlpMatchStart = -1
        } else {
            nlpMatchedText = matched
            nlpMatchStart = parseResult.matchStart ?: -1
        }

        val parsedDueEpochMs = parseResult.dueEpochMs
        if (parsedDueEpochMs != null) {
            if (showScheduleControls && !scheduleEnabled) scheduleEnabled = true
            if (parsedDueEpochMs != dueEpochMs) dueEpochMs = parsedDueEpochMs
        }
        // A captured recurrence needs a schedule to start from; enable it so the preset sticks.
        parseResult.rrule?.let { rrule ->
            if (showScheduleControls) {
                if (!scheduleEnabled) scheduleEnabled = true
                selectedRepeat = repeatPresetFromRrule(rrule).name
            }
        }
        parseResult.priority?.let { selectedPriority = canonicalPriorityValue(it) }
    }

    // "Make this repeat?" suggestion from the completed-history cadence.
    val repeatSuggestionContext = LocalContext.current
    val repeatDismissalStore = remember { RepeatSuggestionDismissalStore(repeatSuggestionContext) }
    var suggestedRepeatRrule by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(title, onSuggestRepeat, selectedRepeat) {
        val lookup = onSuggestRepeat
        if (lookup == null || editingTask != null || title.isBlank() ||
            selectedRepeat != RepeatPreset.NONE.name
        ) {
            suggestedRepeatRrule = null
            return@LaunchedEffect
        }
        delay(400)
        val rrule = runCatching { lookup(title) }.getOrNull()
        suggestedRepeatRrule = rrule?.takeUnless {
            repeatDismissalStore.isDismissed(RepeatSuggestionEngine.normalize(title))
        }
    }
    var dueDatePickerOpen by rememberSaveable { mutableStateOf(false) }
    var dueTimePickerOpen by rememberSaveable { mutableStateOf(false) }
    // Every way out of this sheet goes through `startDismiss`: the scrim, the close
    // button, the host Dialog's back press and outside tap, the Create/Save confirm, and
    // so — one step later — each caller's own onDismiss. See [SheetDismissState] for why
    // it is two steps.
    //
    // The keyboard leaves with the caller's onDismiss, at the END of the exit, and not at
    // the start of it. Hiding it first collapses `WindowInsets.ime` while the slide is
    // still playing: `reserveKeyboardLayout` below follows the insets down, the Surface's
    // modifier chain hard-swaps from a fixed 85 % of the screen to the wrap-content branch
    // mid-slide, and because `slideOutVertically` offsets by the height it measured, the
    // card's top edge collapses about a third of a screen on one frame while it is still
    // on its way out. That is `and-create-sheet-ime-height-snap` (PR 15b) escaping the
    // opening and getting into the exit; leaving the IME alone until the Dialog goes keeps
    // it in the one place PR 15b will fix it.
    val sheetDismiss = rememberSheetDismissState(
        presentImmediately = presentImmediately,
        onDismissed = {
            dismissKeyboard()
            onDismiss()
        },
    )
    // No host gets a veto over this. The widget create surface used to hold one, because
    // its submit finished the Activity out from under a dismissal the sheet had already
    // accepted — and `start()` latches, so a dropped dismissal can never be retried and
    // leaves a bare scrim with no sheet in it. That host gates its teardown on the exit
    // now, so there is nothing left to refuse. A veto would not have reached the confirm
    // in any case: `submitTask` gets to `start()` inside the same click that sets the
    // host's flag, a frame before the refusal composes. And a confirm is not a gesture a
    // host is entitled to refuse — the user has committed, so the sheet has to leave.
    //
    // Typed `() -> Unit` so the three dismiss affordances can go on discarding the answer.
    // Only the confirm has anything to do with it; see `submitTask`.
    val startDismiss: () -> Unit = { sheetDismiss.start() }

    val noListLabel = stringResource(R.string.create_task_no_list)
    val priorityOptions = remember { PRIORITY_OPTIONS_HIGH_TO_LOW }
    val priorityLabels = priorityOptions.associateWith { stringResource(priorityDisplayLabelRes(it)) }
    val repeatLabels = mapOf(
        RepeatPreset.NONE to stringResource(RepeatPreset.NONE.labelRes),
        RepeatPreset.DAILY to stringResource(RepeatPreset.DAILY.labelRes),
        RepeatPreset.WEEKLY to stringResource(RepeatPreset.WEEKLY.labelRes),
        RepeatPreset.WEEKDAYS to stringResource(RepeatPreset.WEEKDAYS.labelRes),
        RepeatPreset.MONTHLY to stringResource(RepeatPreset.MONTHLY.labelRes),
        RepeatPreset.YEARLY to stringResource(RepeatPreset.YEARLY.labelRes),
    )
    val selectedListName = lists.firstOrNull { it.id == selectedListId }?.name ?: noListLabel
    val repeatPreset = if (scheduleEnabled && showScheduleControls) {
        RepeatPreset.valueOf(selectedRepeat)
    } else {
        RepeatPreset.NONE
    }
    val canSubmit = title.isNotBlank()
    val colorScheme = MaterialTheme.colorScheme
    val sheetContainerColor = TdaySheetDefaults.containerColor()
    val sheetScrimColor = TdaySheetDefaults.scrimColor()
    val sheetTonalElevation = TdaySheetDefaults.tonalElevation()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val density = LocalDensity.current
    // The live inset, not a crossing. The platform animates this up and down over roughly
    // 250 ms and republishes it every frame, so reading the dp here is what puts the sheet
    // on the keyboard's own clock instead of on a threshold.
    val imeHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val keyboardVisible = imeHeight > 0.dp
    val maxSheetHeight = screenHeight * CREATE_TASK_SHEET_MAX_HEIGHT_FRACTION
    val usesTallCreateModal = !isEditMode && showScheduleControls
    val usesFloaterCreateModal = !isEditMode && !showScheduleControls
    val usesScheduledEditModal = isEditMode && showScheduleControls
    val usesFloaterEditModal = isEditMode && !showScheduleControls
    val usesScrollSizedModal = usesTallCreateModal ||
            usesFloaterCreateModal ||
            usesScheduledEditModal ||
            usesFloaterEditModal
    val floaterCreateSheetHeight = (screenHeight * CREATE_TASK_SHEET_FLOATER_CREATE_HEIGHT_FRACTION)
        .coerceAtMost(maxSheetHeight)
    val editSheetHeight = (screenHeight * CREATE_TASK_SHEET_EDIT_HEIGHT_FRACTION)
        .coerceAtMost(maxSheetHeight)
    val floaterEditSheetHeight = (screenHeight * CREATE_TASK_SHEET_FLOATER_EDIT_HEIGHT_FRACTION)
        .coerceAtMost(maxSheetHeight)
    val sheetFormScrollState = rememberScrollState()
    // A plain value, not an animation. Both inputs are fixed for the life of the
    // composition — the screen height and a `const val` fraction — so the 320 ms tween
    // that used to wrap this had a target it could never move away from and never ran a
    // single frame. It read as motion in review for exactly as long as it was dead.
    //
    // It is no longer a destination either. The chain below used to swap whole branches on
    // "is the IME visible", which sent the sheet here in the one frame the keyboard's first
    // pixel appeared; this is now the ceiling that growth stops at, and the growth itself
    // comes from the live inset. See [CreateSheetImeHeight].
    val keyboardSheetHeight = (screenHeight * CREATE_TASK_SHEET_KEYBOARD_HEIGHT_FRACTION)
        .coerceAtMost(maxSheetHeight)
    // The least its own branch below will accept — a floor under the resting height, not
    // the resting height itself. Three of the four branches wrap their content, so they
    // stand taller than this whenever the form is taller than the fraction.
    val restingSheetMinHeight = when {
        usesTallCreateModal || usesFloaterCreateModal -> floaterCreateSheetHeight
        usesScheduledEditModal -> editSheetHeight
        usesFloaterEditModal -> floaterEditSheetHeight
        else -> 0.dp
    }
    // Where the sheet actually stood the last time the keyboard was down. Climbing from
    // the branch minimum instead would spend the first (measured − minimum) dp of keyboard
    // travel below a sheet that is already taller than that, so the sheet would sit still
    // for that part of the rise and only then start tracking — the late start the device
    // row is hunting for.
    var restingSheetHeight by remember { mutableStateOf(0.dp) }
    val keyboardFloorHeight = CreateSheetImeHeight.sheetHeightFor(
        restingHeight = maxOf(restingSheetHeight, restingSheetMinHeight),
        imeHeight = imeHeight,
        keyboardHeight = keyboardSheetHeight,
    )
    // A content tween belongs in the height chain only while the keyboard is still. With
    // the inset moving, the sheet is held to it exactly (below), and `animateContentSize`
    // left in the chain would keep chasing a height it is never allowed to report —
    // drifting hundreds of dp behind it, because a tween restarts from zero velocity every
    // time its target moves and so covers under 1 % of the gap per frame. On the frame the
    // keyboard finally reaches zero and the pin comes off, that stale value is what the
    // sheet would snap to. Taking the node out for the duration means it is rebuilt at the
    // size the sheet is actually at, and content changes still animate with the keyboard
    // down, which is the only time they are visible anyway.
    val sheetContentSizeAnimation = if (keyboardVisible) {
        Modifier
    } else {
        // Emphasis rather than TdaySheetMotion.cardIn(): what this resizes is the sheet's
        // content, not the sheet arriving. It happens to be the same rung — a size change
        // is geometry either way — but tying it to the card's spec would carry it along
        // the next time the arrival is retimed, which is a different question.
        Modifier.animateContentSize(
            animationSpec = tween(
                durationMillis = TdayMotionTokens.Durations.Emphasis,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    fun submitTask() {
        // Claim the exit first, and drop the confirm if it was already claimed.
        //
        // Confirming leaves the sheet the same way the X does. It was the one way out that
        // still cut: every host cleared the flag that composes the `Dialog` inside its own
        // `onCreateTask`/`onUpdateTask`, on the frame of the tap, so the exit spec played
        // over a composition that had already gone. The hosts hand that teardown to
        // `onDismiss` instead, which is the end of the exit.
        //
        // Which puts the payload inside the slide rather than before it, and that is why
        // this is the first line of the function and not the last. The card is still under
        // the finger for the whole 260 ms — that is what makes it readable on the way out —
        // so Create is still lit and still hit-testable, and a second tap would hand over a
        // second payload: a duplicated task, queued as its own create and synced to every
        // device. `start()` latches, so asking it first is what makes the second tap
        // answerable. Handing the payload over first and dismissing afterwards, which is
        // how this landed, reads as the more careful order but defends nothing: a host that
        // tears the composition down inside its own callback cuts the exit from either side
        // of the call.
        if (!sheetDismiss.start()) return

        val due =
            if (scheduleEnabled && showScheduleControls) {
                Instant.ofEpochMilli(dueEpochMs).truncatedTo(ChronoUnit.MINUTES)
            } else {
                null
            }

        // Strip the highlighted date phrase from the saved title (it stays visible
        // in the field but isn't part of the task name) — same as the web.
        val matched = nlpMatchedText
        val effectiveTitle = if (!matched.isNullOrEmpty()) {
            // Prefer the parser's authoritative offset; only fall back to indexOf if the
            // title changed since the parse so the offset no longer lines up.
            val start = nlpMatchStart
            val idx = if (start in 0..(title.length - matched.length) &&
                title.regionMatches(start, matched, 0, matched.length)
            ) {
                start
            } else {
                title.indexOf(matched)
            }
            if (idx >= 0) {
                (title.substring(0, idx) + title.substring(idx + matched.length))
                    .replace(Regex("\\s{2,}"), " ").trim()
            } else {
                title.trim()
            }
        } else {
            title.trim()
        }
        // Also strip any recurrence/priority phrase we captured into the fields.
        val cleanedTitle = RecurrencePriorityGrammar.parse(effectiveTitle).cleanTitle
            .ifBlank { effectiveTitle }

        val payload = CreateTaskPayload(
            title = cleanedTitle,
            description = notes.trim().ifBlank { null },
            priority = selectedPriority,
            due = due,
            rrule = repeatPreset.rrule?.takeIf { scheduleEnabled && showScheduleControls },
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
        onDismissRequest = startDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        TdaySheetFullBleedWindow()

        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // The scrim fades on the sheet's timing, not the Dialog window's. Left bare it
            // had no timing of its own: it arrived with the window — roughly 150 ms — and
            // was at full dim long before the card finished its Emphasis-long slide up, and
            // it could not leave until the Dialog did, which is after `gone`, which is after
            // the card has landed. The dim led the card in and outlasted it going out; one
            // transition target answers both ends.
            //
            // `visible` and not `visibleState`: see SheetDismissState.visible for why the
            // two surfaces must not share one transition.
            AnimatedVisibility(
                visible = sheetDismiss.visible,
                enter = fadeIn(animationSpec = TdaySheetMotion.scrimIn()),
                exit = fadeOut(animationSpec = TdaySheetMotion.scrimOut()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(sheetScrimColor)
                        // No indication: a dismiss tap on the scrim is a gesture at the
                        // sheet, not a press of a full-screen button, and the default
                        // ripple draws itself across the entire window on the way out.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = startDismiss,
                        ),
                )
            }

            AnimatedVisibility(
                visibleState = sheetDismiss.transition,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = slideInVertically(
                    animationSpec = TdaySheetMotion.cardIn(),
                    initialOffsetY = { fullHeight -> fullHeight },
                ) + fadeIn(animationSpec = TdaySheetMotion.cardIn()),
                exit = slideOutVertically(
                    animationSpec = TdaySheetMotion.cardOut(),
                    targetOffsetY = { fullHeight -> fullHeight },
                ) + fadeOut(animationSpec = TdaySheetMotion.cardOut()),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Read with the keyboard down only, so what it records is the
                        // height the climb has to start from and never a height the climb
                        // itself produced.
                        .onSizeChanged { size ->
                            if (!keyboardVisible) {
                                restingSheetHeight = with(density) { size.height.toDp() }
                            }
                        }
                        // While the inset is anywhere but zero, the keyboard owns the
                        // height and the sheet is held to it EXACTLY, outside the branch
                        // below rather than instead of it. An exact height is clamped in
                        // both directions, so the sheet follows the inset down as
                        // faithfully as it follows it up. A `heightIn(min = ...)` floor
                        // here would only clamp upward — on the way down the branch's own
                        // animated size sits above the falling floor, inside the range,
                        // and is reported verbatim, which hands the whole retraction to a
                        // 320 ms tween racing the keyboard's ~250 ms.
                        .then(
                            if (keyboardVisible) {
                                Modifier.height(keyboardFloorHeight)
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (usesTallCreateModal) {
                                // Wrap content (like the floater create sheet) so the
                                // bottom padding under the last row matches; a fixed
                                // height left extra space below Repeat.
                                sheetContentSizeAnimation
                                    .heightIn(min = floaterCreateSheetHeight, max = maxSheetHeight)
                            } else if (usesScheduledEditModal) {
                                Modifier.height(editSheetHeight)
                            } else if (usesFloaterCreateModal) {
                                sheetContentSizeAnimation
                                    .heightIn(min = floaterCreateSheetHeight, max = maxSheetHeight)
                            } else if (usesFloaterEditModal) {
                                sheetContentSizeAnimation
                                    .heightIn(min = floaterEditSheetHeight, max = maxSheetHeight)
                            } else {
                                sheetContentSizeAnimation
                                    .heightIn(max = maxSheetHeight)
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                    shape = TdaySheetDefaults.TopShape,
                    color = sheetContainerColor,
                    tonalElevation = sheetTonalElevation,
                ) {
                    TdayCenteredSheetContent {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            TdaySheetHeader(
                                title = stringResource(
                                    if (isEditMode) {
                                        R.string.create_task_title_edit
                                    } else {
                                        R.string.create_task_title_new
                                    },
                                ),
                                leftIcon = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                                leftContentDescription = stringResource(R.string.action_close),
                                onLeftClick = startDismiss,
                                confirmContentDescription = stringResource(
                                    if (isEditMode) {
                                        R.string.action_save_task
                                    } else {
                                        R.string.action_create_task
                                    },
                                ),
                                // No `dismissKeyboard()` here: the keyboard goes at the
                                // end of the exit with every other dismissal, for the
                                // reason spelled out where `sheetDismiss` is built. The
                                // confirm button is disabled unless `canSubmit`, so this
                                // ran only on a tap that was already going to submit.
                                //
                                // A repeat tap during the exit is refused inside
                                // `submitTask`, and not by taking `confirmEnabled` down
                                // with it: the control is still on screen for the whole
                                // slide, so greying it would be the user watching it go
                                // dead under their own finger. It stays lit and does
                                // nothing, which is what a control on a sheet that is
                                // already leaving should look like.
                                onConfirm = {
                                    if (canSubmit) {
                                        submitTask()
                                    }
                                },
                                confirmEnabled = canSubmit,
                            )

                            val formModifier = if (keyboardVisible || usesScrollSizedModal) {
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(sheetFormScrollState)
                            } else {
                                Modifier.fillMaxWidth()
                            }

                            Column(
                                modifier = formModifier,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                TaskTextCard(
                                    title = title,
                                    notes = notes,
                                    titleHighlight = nlpMatchedText,
                                    titleHighlightStart = nlpMatchStart,
                                    onTitleChange = { title = it },
                                    onNotesChange = { notes = it },
                                    onKeyboardDone = dismissKeyboard,
                                )

                                suggestedRepeatRrule?.let { rrule ->
                                    RepeatSuggestionChip(
                                        rrule = rrule,
                                        onAccept = {
                                            if (showScheduleControls) scheduleEnabled = true
                                            selectedRepeat = repeatPresetFromRrule(rrule).name
                                            suggestedRepeatRrule = null
                                        },
                                        onDismiss = {
                                            repeatDismissalStore.markDismissed(
                                                RepeatSuggestionEngine.normalize(title),
                                            )
                                            suggestedRepeatRrule = null
                                        },
                                    )
                                }

                                if (showScheduleControls) {
                                    SectionHeading(stringResource(R.string.create_task_section_schedule))
                                    GroupCard {
                                        if (canToggleSchedule) {
                                            ScheduleSwitchRow(
                                                enabled = scheduleEnabled,
                                                onEnabledChange = { enabled ->
                                                    scheduleEnabled = enabled
                                                },
                                            )
                                        }
                                        AnimatedVisibility(visible = scheduleEnabled) {
                                            Column {
                                                if (canToggleSchedule) RowDivider()
                                                SplitDateTimeRow(
                                                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_calendar_clock),
                                                    title = stringResource(R.string.create_task_due),
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
                                }

                                SectionHeading(stringResource(R.string.create_task_section_details))
                                GroupCard {
                                    SheetDropdownRow(
                                        icon = ImageVector.vectorResource(R.drawable.ic_lucide_list),
                                        title = stringResource(R.string.create_task_list),
                                        value = selectedListName,
                                        options = listOf<ListSummary?>(null) + lists,
                                        optionLabel = { option -> option?.name ?: noListLabel },
                                        optionSwatchColor = { option ->
                                            option?.let {
                                                listColorSwatchForSelector(
                                                    raw = it.color,
                                                    fallback = colorScheme.primary.copy(alpha = 0.75f),
                                                )
                                            } ?: colorScheme.outlineVariant.copy(alpha = 0.95f)
                                        },
                                        isSelected = { option -> option?.id == selectedListId },
                                        onOptionSelected = { option ->
                                            selectedListId = option?.id
                                        },
                                        valueLeading = {
                                            val selected =
                                                lists.firstOrNull { it.id == selectedListId }
                                            if (selected != null) {
                                                Icon(
                                                    imageVector = tdayListIconForList(selected.iconKey, selected.name),
                                                    contentDescription = null,
                                                    tint = listColorSwatchForSelector(
                                                        raw = selected.color,
                                                        fallback = colorScheme.primary.copy(alpha = 0.75f),
                                                    ),
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        },
                                    )
                                    RowDivider()
                                    SheetDropdownRow(
                                        icon = ImageVector.vectorResource(R.drawable.ic_lucide_flag_filled),
                                        title = stringResource(R.string.create_task_priority),
                                        value = priorityLabels.getValue(selectedPriority),
                                        options = priorityOptions,
                                        optionLabel = { option -> priorityLabels.getValue(option) },
                                        optionSwatchColor = { option -> tdayPriorityColor(option) },
                                        isSelected = { option -> selectedPriority == option },
                                        onOptionSelected = { option ->
                                            selectedPriority = option
                                            priorityTouchedByUser = true
                                        },
                                        valueLeading = {
                                            Icon(
                                                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_flag_filled),
                                                contentDescription = null,
                                                tint = tdayPriorityColor(selectedPriority),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                    )
                                    if (showScheduleControls) {
                                        RowDivider()
                                        SheetDropdownRow(
                                            icon = ImageVector.vectorResource(R.drawable.ic_lucide_repeat),
                                            title = stringResource(R.string.create_task_repeat),
                                            value = repeatLabels.getValue(repeatPreset),
                                            options = if (scheduleEnabled) {
                                                RepeatPreset.entries.toList()
                                            } else {
                                                listOf(RepeatPreset.NONE)
                                            },
                                            optionLabel = { option -> repeatLabels.getValue(option) },
                                            optionSwatchColor = { option -> repeatSwatchColor(option) },
                                            isSelected = { option -> selectedRepeat == option.name },
                                            onOptionSelected = { option ->
                                                selectedRepeat = option.name
                                            },
                                            titleTrailing = {
                                                GuideHelpLink(GuideTopicIds.RECURRENCE_PRESETS)
                                            },
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
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
private fun SectionHeading(text: String) {
    TdaySheetSectionTitle(
        text = text,
    )
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    TdaySheetCard(content = content)
}

@Composable
private fun TaskTextCard(
    title: String,
    notes: String,
    titleHighlight: String?,
    titleHighlightStart: Int,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onKeyboardDone: () -> Unit,
) {
    GroupCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskField(
                value = title,
                placeholder = stringResource(R.string.create_task_title_placeholder),
                onValueChange = onTitleChange,
                onKeyboardDone = onKeyboardDone,
                highlightText = titleHighlight,
                highlightStart = titleHighlightStart,
                modifier = Modifier.weight(1f),
            )
            GuideHelpLink(
                GuideTopicIds.NLP_DATE_SYNTAX,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        RowDivider()
        NotesField(
            value = notes,
            onValueChange = onNotesChange,
            placeholder = stringResource(R.string.create_task_notes_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TaskField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onKeyboardDone: () -> Unit,
    highlightText: String? = null,
    highlightStart: Int = -1,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    // Warm highlight behind the detected date phrase, matching the web's --nlp
    // tint. Translucent so it reads on both light and dark surfaces.
    val nlpHighlightColor = Color(0xFFE0732A).copy(alpha = 0.38f)
    val highlightTransformation = remember(highlightText, highlightStart, nlpHighlightColor) {
        val phrase = highlightText
        if (phrase.isNullOrEmpty()) {
            VisualTransformation.None
        } else {
            VisualTransformation { input ->
                val plain = input.text
                // Use the parser's offset when it still aligns with the current text;
                // otherwise fall back to the first occurrence.
                val idx = if (highlightStart in 0..(plain.length - phrase.length) &&
                    plain.regionMatches(highlightStart, phrase, 0, phrase.length)
                ) {
                    highlightStart
                } else {
                    plain.indexOf(phrase)
                }
                if (idx < 0) {
                    TransformedText(input, OffsetMapping.Identity)
                } else {
                    val styled = buildAnnotatedString {
                        append(plain.substring(0, idx))
                        pushStyle(SpanStyle(background = nlpHighlightColor))
                        append(plain.substring(idx, idx + phrase.length))
                        pop()
                        append(plain.substring(idx + phrase.length))
                    }
                    // Text length is unchanged (styling only), so offsets map 1:1.
                    TransformedText(styled, OffsetMapping.Identity)
                }
            }
        }
    }

    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace('\n', ' ').replace('\r', ' ')) },
        singleLine = true,
        visualTransformation = highlightTransformation,
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
        modifier = modifier
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
    val view = LocalView.current
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
                    .clickable(onClick = {
                        TdayHaptics.buttonPress(view)
                        onDateClick()
                    })
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
                    .clickable(onClick = {
                        TdayHaptics.buttonPress(view)
                        onTimeClick()
                    })
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
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                TdayHaptics.toggle(view, on = !enabled)
                onEnabledChange(!enabled)
            }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(
                if (enabled) R.drawable.ic_lucide_calendar_clock else R.drawable.ic_lucide_leaf,
            ),
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.create_task_section_schedule),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(
                    if (enabled) {
                        R.string.create_task_schedule_enabled
                    } else {
                        R.string.create_task_schedule_disabled
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                fontWeight = FontWeight.Bold,
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
    valueLeading: (@Composable () -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                TdayHaptics.buttonPress(view)
                onClick()
            })
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            if (titleTrailing != null) {
                Spacer(modifier = Modifier.width(2.dp))
                titleTrailing()
            }
        }

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 8.dp),
        ) {
            if (valueLeading != null) {
                valueLeading()
                Spacer(modifier = Modifier.width(6.dp))
            }

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
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_down),
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
    optionSwatchColor: @Composable (T) -> Color,
    isSelected: (T) -> Boolean,
    onOptionSelected: (T) -> Unit,
    valueLeading: (@Composable () -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
) {
    var selectorOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        SheetRow(
            icon = icon,
            title = title,
            value = value,
            onClick = { selectorOpen = true },
            valueLeading = valueLeading,
            titleTrailing = titleTrailing,
        )

        if (selectorOpen) {
            TdayCenteredSelectorDialog(
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

private fun listColorSwatchForSelector(raw: String?, fallback: Color): Color {
    if (raw.isNullOrBlank()) return fallback
    return tdayListAccentColorOrNull(raw)
        ?: runCatching { Color(AndroidColor.parseColor(raw)) }
            .getOrDefault(fallback)
}

private fun repeatSwatchColor(preset: RepeatPreset): Color {
    return when (preset) {
        RepeatPreset.NONE -> Color(0xFFB7BCC8)
        RepeatPreset.DAILY -> TdayTaskCompleteAccent
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

/** "Make this repeat?" pill — accept sets the recurrence, ✕ dismisses it (persisted). */
@Composable
private fun RepeatSuggestionChip(
    rrule: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val cadence = stringResource(repeatPresetFromRrule(rrule).labelRes)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(999.dp))
                .background(colorScheme.secondary.copy(alpha = 0.15f))
                .clickable(onClick = onAccept)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_repeat),
                contentDescription = null,
                tint = colorScheme.secondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.create_task_repeat_suggestion, cadence),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.secondary,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onDismiss)
                .padding(6.dp),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                contentDescription = stringResource(R.string.create_task_repeat_suggestion_dismiss),
                tint = colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemedDatePickerDialog(
    initialEpochMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val now = remember(zoneId) { ZonedDateTime.now(zoneId) }
    // The Material3 DatePicker represents each calendar day as its UTC-midnight epoch
    // (both selectedDateMillis and isSelectableDate's argument are UTC). All boundary
    // math here must therefore use the UTC frame, taking the *local* calendar date but
    // expressing it as UTC midnight — otherwise users west/east of UTC see an
    // off-by-one in which days are selectable and which day gets saved.
    val initialDateEpochMs = remember(initialEpochMs, zoneId) {
        ZonedDateTime
            .ofInstant(Instant.ofEpochMilli(initialEpochMs), zoneId)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }
    val currentYear = now.year
    val minMonthStartEpochMs = remember(now.year, now.monthValue) {
        LocalDate
            .of(now.year, now.monthValue, 1)
            .atStartOfDay(ZoneOffset.UTC)
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
    val dialogContainer = TdaySheetDefaults.containerColor()
    val calendarSurface = TdaySheetDefaults.surfaceColor()
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
        title = stringResource(R.string.create_task_select_date),
        titleIcon = ImageVector.vectorResource(R.drawable.ic_lucide_calendar),
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
    val dialogContainer = TdaySheetDefaults.containerColor()
    val pickerSurface = TdaySheetDefaults.surfaceColor()
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
        title = stringResource(R.string.create_task_select_time),
        titleIcon = ImageVector.vectorResource(R.drawable.ic_lucide_clock),
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        TdaySheetFullBleedWindow()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TdaySheetDefaults.scrimColor())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(dialogWidthFraction)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = TdaySheetDefaults.DialogShape,
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
                        shape = TdaySheetDefaults.CardShape,
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
                                text = stringResource(R.string.action_cancel),
                                color = mutedText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        TextButton(onClick = onConfirm) {
                            Text(
                                text = stringResource(R.string.action_done),
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
}

private fun mergeDateKeepingTime(
    baseEpochMs: Long,
    selectedDateEpochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(baseEpochMs), zoneId)
    // selectedDateEpochMs comes from the Material3 DatePicker as UTC midnight, so the
    // picked calendar date must be read in UTC (reading it in a negative-offset zone
    // would roll back to the previous day). The time-of-day stays in the local zone.
    val selectedDate =
        Instant.ofEpochMilli(selectedDateEpochMs).atZone(ZoneOffset.UTC).toLocalDate()
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
