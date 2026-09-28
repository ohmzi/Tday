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
import java.time.LocalTime
import java.util.Locale

/**
 * Widgets v3: a widget instance scoped to ONE arbitrary list, chosen per instance via
 * [WidgetListConfigurationActivity] rather than fixed to "today's due tasks" or "the floater
 * list" the way [TodayTasksWidget]/[FloaterTasksWidget] are. Content shape follows whichever list
 * TYPE was configured (see [WidgetListType]'s KDoc) — this object does not decide that itself, it
 * only reads what [WidgetListSelectionStore] already recorded at configuration time.
 *
 * The selection, the lock flag and the snapshot are all read on every render, so a reconfigure
 * (the launcher's widget-edit affordance relaunches the configuration activity for the SAME
 * `appWidgetId`) shows up on the next repaint without any per-instance state to invalidate.
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
        // normal case never reaches this: WidgetListConfigurationActivity writes the first
        // snapshot synchronously right after the user picks a list.
        if (!isAppLocked && selection != null && snapshot == null) WidgetHydrateWorker.runOnce(appContext)
        logWidgetComposition(
            composingAs = WidgetInstanceKind.LIST,
            appWidgetId = appWidgetId,
            providerKind = WidgetInstanceResolver(appContext).kindOf(appWidgetId),
            details = "locked=$isAppLocked listType=${selection?.listType?.name ?: "none"} " +
                "snapshotNull=${snapshot == null}",
        )

        val visuals = listWidgetVisualsFor(selection?.listType)
        val title = listWidgetTitleFor(appContext, selection, isAppLocked)
        return if (selection == null) {
            unconfiguredModel(appContext, appWidgetId, title, visuals, isAppLocked)
        } else {
            configuredModel(appContext, appWidgetId, selection, title, visuals, isAppLocked, snapshot)
        }
    }

    /** Where a tap on this instance (or one of its rows) goes. */
    fun openIntent(context: Context, appWidgetId: Int): Intent {
        val selection = WidgetListSelectionStore(context.applicationContext).selectionFor(appWidgetId)
            ?: return reconfigureIntent(appWidgetId)
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
private fun listWidgetTitleFor(appContext: Context, selection: WidgetListSelection?, isAppLocked: Boolean): String {
    if (isAppLocked) return appContext.getString(R.string.widget_list_tasks_title)
    return selection?.listName?.takeIf { it.isNotBlank() } ?: appContext.getString(R.string.widget_list_tasks_title)
}

/**
 * No selection on disk: an instance whose configuration was somehow lost (store cleared without
 * the widget itself being removed), or a render that landed before
 * `WidgetListConfigurationActivity` finished writing it — reuse the SETUP content state as "tap to
 * finish setting this up" rather than inventing a fourth one, with kind-neutral visuals.
 */
private fun unconfiguredModel(
    appContext: Context,
    appWidgetId: Int,
    title: String,
    visuals: TaskWidgetVisuals,
    isAppLocked: Boolean,
): TaskWidgetModel = TaskWidgetModel(
    title = title,
    state = if (isAppLocked) TaskWidgetContentState.LOCKED else TaskWidgetContentState.SETUP,
    countLabel = "",
    setupTitle = appContext.getString(R.string.widget_list_tasks_setup_title),
    setupMessage = appContext.getString(R.string.widget_list_tasks_setup_message),
    emptyTitle = "",
    lockedTitle = appContext.getString(R.string.widget_locked_title),
    lockedMessage = appContext.getString(R.string.widget_locked_message),
    loadingTitle = appContext.getString(R.string.widget_loading),
    addLabel = appContext.getString(R.string.widget_list_tasks_setup_title),
    rows = emptyList(),
    visuals = visuals,
    openIntent = reconfigureIntent(appWidgetId),
    addIntent = reconfigureIntent(appWidgetId),
)

private fun configuredModel(
    appContext: Context,
    appWidgetId: Int,
    selection: WidgetListSelection,
    title: String,
    visuals: TaskWidgetVisuals,
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
): TaskWidgetModel {
    val (emptyRes, addRes, countRes) = when (selection.listType) {
        WidgetListType.TODO -> Triple(
            R.string.widget_today_tasks_empty,
            R.string.widget_today_tasks_add,
            R.string.widget_today_tasks_count,
        )

        WidgetListType.FLOATER -> Triple(
            R.string.widget_floater_tasks_empty,
            R.string.widget_floater_tasks_add,
            R.string.widget_floater_tasks_count,
        )
    }
    return TaskWidgetModel(
        title = title,
        state = listContentState(isAppLocked, snapshot),
        countLabel = String.format(Locale.getDefault(), appContext.getString(countRes), snapshot?.taskCount ?: 0),
        setupTitle = appContext.getString(R.string.widget_today_tasks_setup_title),
        setupMessage = appContext.getString(R.string.widget_today_tasks_setup_message),
        emptyTitle = appContext.getString(emptyRes),
        lockedTitle = appContext.getString(R.string.widget_locked_title),
        lockedMessage = appContext.getString(R.string.widget_locked_message),
        loadingTitle = appContext.getString(R.string.widget_loading),
        addLabel = appContext.getString(addRes),
        rows = if (isAppLocked || snapshot == null) emptyList() else listRows(snapshot, selection.listType),
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

private fun listRows(snapshot: WidgetSnapshot, listType: WidgetListType): List<TaskWidgetRow> {
    // One formatter for the whole list — see TodayTasksWidget for why this isn't baked at write
    // time (locale + 12/24h setting are read-time concerns).
    val timeFormatter = if (listType == WidgetListType.TODO) DateFormat.getTimeInstance(DateFormat.SHORT) else null
    return snapshot.rows.map { row ->
        TaskWidgetRow(
            key = row.key,
            id = row.id,
            title = row.title,
            priority = row.priorityRing.toPriorityValue(),
            trailingText = timeFormatter?.let { formatter -> row.dueEpochMs?.let { dueTimeText(formatter, it) } },
            overdueTrailing = timeFormatter != null && row.overdue,
            description = row.description,
        )
    }
}

/** Relaunches configuration for THIS instance — the same activity the launcher's own widget-edit
 *  affordance opens, reused here as the recovery path for a selection-less instance. */
private fun reconfigureIntent(appWidgetId: Int): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
    component = ComponentName(BuildConfig.APPLICATION_ID, WidgetListConfigurationActivity::class.java.name)
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
