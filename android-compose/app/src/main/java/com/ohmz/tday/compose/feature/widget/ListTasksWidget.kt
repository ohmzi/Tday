package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetListType
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshot
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * The List widget: one placed instance scoped to ONE list the user picks for it, rather than fixed
 * to "today's due tasks" or "the floater list" the way [TodayTasksWidget]/[FloaterTasksWidget] are.
 *
 * It is placed UNCONFIGURED: a picture and "Choose a list", and a tap (anywhere, "+" included)
 * opens [WidgetListPickerActivity] for this `appWidgetId`. The same activity is the launcher's
 * reconfigure target, so the list can be changed later from the widget's long-press menu. Once a
 * list is picked, content shape follows that list's TYPE (see [WidgetListType]'s KDoc) and "+"
 * creates straight into it — that list, that task type, and the list's own default priority.
 *
 * The selection, the lock flag and the snapshot are all read on every render, so a pick or a
 * reconfigure shows up on the next repaint without any per-instance state to invalidate.
 */
internal object ListTasksWidget {

    fun model(context: Context, appWidgetId: Int): TaskWidgetModel {
        val appContext = context.applicationContext
        val isAppLocked = AppSecurityPreferenceStore(appContext).appLockEnabled.value
        val selection = WidgetListSelectionStore(appContext).selectionFor(appWidgetId)
        val snapshot = if (isAppLocked || selection == null) {
            null
        } else {
            WidgetSnapshotStore(appContext).readList(appWidgetId)
        }
        // Same fallback Today/Floater use for "no snapshot yet" — a configured-but-unseeded
        // instance shouldn't sit in LOADING forever waiting for an unrelated cache write. The
        // normal case never reaches this: WidgetListPickerActivity writes the first snapshot
        // right after the user picks a list.
        if (!isAppLocked && selection != null && snapshot == null) WidgetHydrateWorker.runOnce(appContext)
        logWidgetComposition(
            composingAs = WidgetInstanceKind.LIST,
            appWidgetId = appWidgetId,
            providerKind = WidgetInstanceResolver(appContext).kindOf(appWidgetId),
            details = "locked=$isAppLocked listType=${selection?.listType?.name ?: "none"} " +
                "snapshotNull=${snapshot == null}",
        )

        // A list deleted since it was picked is the same state as no list at all — the widget asks
        // for another — with a message that says why.
        val listMissing = snapshot?.listMissing == true
        val visuals = listWidgetVisualsFor(selection?.listType?.takeUnless { listMissing })
        val title = listWidgetTitleFor(appContext, selection?.takeUnless { listMissing }, snapshot, isAppLocked)
        return if (selection == null || listMissing) {
            unconfiguredModel(appContext, appWidgetId, title, visuals, isAppLocked, listMissing)
        } else {
            configuredModel(appContext, appWidgetId, selection, title, visuals, isAppLocked, snapshot)
        }
    }

    /** Where a tap on this instance (or one of its rows) goes. */
    fun openIntent(context: Context, appWidgetId: Int): Intent {
        val selection = WidgetListSelectionStore(context.applicationContext).selectionFor(appWidgetId)
            ?: return pickListIntent(context, appWidgetId)
        return openListIntent(selection.listId, selection.listName, selection.listType)
    }
}

/**
 * The look of an instance whose selection could not be read — no watermark and a neutral "+",
 * rather than any kind's accent.
 *
 * This is the render half of the same rule [WidgetInstanceCatalog.feedFor] holds for routing: an
 * unresolved instance must not be presented as the scheduled widget. It used to fall in with
 * [WidgetListType.TODO] on the `null` branch below, which meant a per-list instance whose
 * selection was momentarily unreadable painted the Today sun watermark and the Today accent — the
 * scheduled widget's whole visual identity — on a widget that may well be on a floater list, then
 * changed back the moment the selection re-read. The state it is really in is "not configured
 * yet", and it now looks like that and nothing else.
 */
internal val UnconfiguredListWidgetVisuals = TaskWidgetVisuals(
    addButtonBackground = R.drawable.widget_add_button_background_neutral,
    addIcon = R.drawable.widget_add_icon_neutral,
    emptyWatermark = null,
    setupWatermark = null,
)

internal fun listWidgetVisualsFor(listType: WidgetListType?): TaskWidgetVisuals = when (listType) {
    WidgetListType.FLOATER -> FloaterWidgetVisuals
    WidgetListType.TODO -> todayWidgetVisuals(taskWidgetIsDaytime(LocalTime.now().hour))
    null -> UnconfiguredListWidgetVisuals
}

/**
 * A chosen list's NAME is more revealing than Today/Floater's fixed app-supplied title ("Job
 * search", "Therapy", …), so — unlike Today/Floater's title, which is shown regardless of state —
 * this falls back to the generic title while locked, consistent with the app-lock policy of never
 * surfacing user content on a locked device (see AppSecurityPreferenceStore's KDoc).
 */
private fun listWidgetTitleFor(
    appContext: Context,
    selection: WidgetListSelection?,
    snapshot: WidgetSnapshot?,
    isAppLocked: Boolean,
): String {
    if (isAppLocked || selection == null) return appContext.getString(R.string.widget_list_tasks_title)
    // The cache's current name first, so a rename shows without picking the list again.
    return snapshot?.listName?.takeIf { it.isNotBlank() }
        ?: selection.listName.takeIf { it.isNotBlank() }
        ?: appContext.getString(R.string.widget_list_tasks_title)
}

/**
 * No list yet — how every List widget is placed — or a list deleted since it was picked: the SETUP
 * content state, with the picture and kind-neutral visuals, and every tap (the "+" too, which has
 * no list to add to) opening the picker for this instance. Locked, it shows the lock and a tap
 * opens the app, which asks for the unlock, rather than listing list names.
 */
private fun unconfiguredModel(
    appContext: Context,
    appWidgetId: Int,
    title: String,
    visuals: TaskWidgetVisuals,
    isAppLocked: Boolean,
    listMissing: Boolean,
): TaskWidgetModel {
    val tapIntent = if (isAppLocked) TodayTasksWidget.openIntent() else pickListIntent(appContext, appWidgetId)
    return TaskWidgetModel(
        title = title,
        state = if (isAppLocked) TaskWidgetContentState.LOCKED else TaskWidgetContentState.SETUP,
        countLabel = null,
        setupTitle = appContext.getString(R.string.widget_list_tasks_setup_title),
        setupMessage = appContext.getString(
            if (listMissing) R.string.widget_list_tasks_missing_message else R.string.widget_list_tasks_setup_message,
        ),
        emptyTitle = "",
        lockedTitle = appContext.getString(R.string.widget_locked_title),
        lockedMessage = appContext.getString(R.string.widget_locked_message),
        loadingTitle = appContext.getString(R.string.widget_loading),
        addLabel = appContext.getString(R.string.widget_list_tasks_setup_title),
        items = emptyList(),
        setupArt = R.drawable.widget_list_setup_art,
        visuals = visuals,
        openIntent = tapIntent,
        addIntent = tapIntent,
    )
}

private fun configuredModel(
    appContext: Context,
    appWidgetId: Int,
    selection: WidgetListSelection,
    title: String,
    visuals: TaskWidgetVisuals,
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
): TaskWidgetModel {
    // A list holds every open task in it, whatever day each is due, so it counts "open" and
    // empties to "nothing left in this list" for either type — not Today's "due" and "due today".
    val addRes = when (selection.listType) {
        WidgetListType.TODO -> R.string.widget_today_tasks_add
        WidgetListType.FLOATER -> R.string.widget_floater_tasks_add
    }
    val state = listContentState(isAppLocked, snapshot)
    return TaskWidgetModel(
        title = title,
        state = state,
        countLabel = String.format(
            Locale.getDefault(),
            appContext.getString(R.string.widget_floater_tasks_count),
            snapshot?.taskCount ?: 0,
        ).takeIf { state == TaskWidgetContentState.TASKS },
        setupTitle = appContext.getString(R.string.widget_today_tasks_setup_title),
        setupMessage = appContext.getString(R.string.widget_today_tasks_setup_message),
        emptyTitle = appContext.getString(R.string.widget_list_tasks_empty),
        lockedTitle = appContext.getString(R.string.widget_locked_title),
        lockedMessage = appContext.getString(R.string.widget_locked_message),
        loadingTitle = appContext.getString(R.string.widget_loading),
        addLabel = appContext.getString(addRes),
        items = if (isAppLocked || snapshot == null) emptyList() else listRows(snapshot, selection.listType),
        visuals = visuals,
        openIntent = openListIntent(selection.listId, selection.listName, selection.listType),
        addIntent = createListTaskIntent(appWidgetId, selection.listId, selection.listType),
    )
}

private fun listContentState(
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
): TaskWidgetContentState = when {
    isAppLocked -> TaskWidgetContentState.LOCKED
    snapshot == null -> TaskWidgetContentState.LOADING
    else -> snapshot.status.toContentState()
}

private fun listRows(snapshot: WidgetSnapshot, listType: WidgetListType): List<TaskWidgetListItem> {
    val dueText = if (listType == WidgetListType.TODO) listDueText() else null
    val checkingIds = WidgetCheckOff.ids()
    return snapshot.rows.map { row ->
        TaskWidgetListItem.Task(
            TaskWidgetRow(
                key = row.key,
                id = row.id,
                title = row.title,
                priority = row.priorityRing.toPriorityValue(),
                trailingText = dueText?.let { format -> row.dueEpochMs?.let(format) },
                overdueTrailing = dueText != null && row.overdue,
                description = row.description,
                checking = row.id in checkingIds,
            ),
        )
    }
}

/**
 * A scheduled list runs across days, so a row due today shows its time and any other row its day
 * ("Sep 30") — a bare time on next week's task would read as today's. The formatters are built
 * once for the whole list and at read time, not baked into the snapshot: locale and the 12/24h
 * setting are read-time concerns (see TodayTasksWidget).
 */
private fun listDueText(): (Long) -> String {
    val locale = Locale.getDefault()
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
    val dayFormatter = SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"),
        locale,
    )
    return { epochMs ->
        val dueToday = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate() == today
        dueTimeText(if (dueToday) timeFormatter else dayFormatter, epochMs)
    }
}

/**
 * Opens [WidgetListPickerActivity] for THIS instance — the same activity the launcher's own
 * reconfigure affordance opens. The data URI carries the id as well as the extra, so two placed
 * List widgets never share one `PendingIntent` (their intents would otherwise compare equal).
 */
internal fun pickListIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, WidgetListPickerActivity::class.java).apply {
        action = WidgetListPickerActivity.ACTION_PICK_LIST
        data = Uri.parse("tday-widget://pick-list/$appWidgetId")
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/**
 * Carries this instance's own `appWidgetId`, so the create sheet resolves the feed from the
 * placement itself. The `target` parameter stays for the entry points that have no widget, but it
 * is no longer what decides this widget's behavior — a list instance whose stored selection cannot
 * be read now fails closed instead of quietly creating a scheduled task.
 */
private fun createListTaskIntent(
    appWidgetId: Int,
    listId: String,
    listType: WidgetListType,
): Intent = Intent(
    Intent.ACTION_VIEW,
    Uri.parse(
        WidgetCreateRoute.deepLink(
            target = WidgetCreateRoute.targetFor(WidgetInstanceCatalog.feedForListType(listType)),
            appWidgetId = appWidgetId,
            listId = listId,
        ),
    ),
).apply {
    component = ComponentName(BuildConfig.APPLICATION_ID, WidgetCreateTaskActivity::class.java.name)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
}

private fun openListIntent(listId: String, listName: String, listType: WidgetListType): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(listDeepLink(listId, listName, listType))).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

private fun listDeepLink(listId: String, listName: String, listType: WidgetListType): String {
    val prefix = if (listType == WidgetListType.TODO) "tday://todos/list" else "tday://floater/list"
    return "$prefix/${Uri.encode(listId)}/${Uri.encode(listName)}"
}
