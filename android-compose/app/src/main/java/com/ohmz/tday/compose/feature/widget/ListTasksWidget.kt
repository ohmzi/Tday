package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetListType
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshot
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotSignal
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore
import java.text.DateFormat
import java.time.Instant
import java.time.LocalTime
import java.util.Date
import java.util.Locale

/**
 * Widgets v3: a widget instance scoped to ONE arbitrary list, chosen per instance via
 * [WidgetListConfigurationActivity] rather than fixed to "today's due tasks" or "the floater
 * list" the way [TodayTasksWidget]/[FloaterTasksWidget] are. Content shape follows whichever list
 * TYPE was configured (see [WidgetListType]'s KDoc) — this class does not decide that itself, it
 * only reads what [WidgetListSelectionStore] already recorded at configuration time.
 *
 * CRITICAL (see the `glance-widget-provideglance-once` project note): `provideGlance` runs
 * EXACTLY ONCE per Glance session — a live session's `update()` only recomposes, it never re-runs
 * this function. [appWidgetId] itself is safe to resolve once (an instance's id never changes for
 * its lifetime), but the SELECTION that id maps to, the app-lock flag, and the snapshot on disk
 * can all change after this session started — they are read as composable state INSIDE
 * `provideContent`, exactly like [TodayTasksWidget], never captured above it.
 */
class ListTasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(TaskWidgetResponsiveSizes)

    // See TodayTasksWidget: this app never reads Glance's own state store.
    override val stateDefinition: GlanceStateDefinition<*>? = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        // Synchronous — GlanceAppWidgetManager.getAppWidgetId is a plain int lookup, not a
        // DataStore read. Safe to resolve once: unlike the SELECTION it maps to, an instance's
        // own id is fixed for its lifetime, so this is the one piece of per-instance state that
        // is fine to read above `provideContent`.
        val appWidgetId = GlanceAppWidgetManager(appContext).getAppWidgetId(id)

        val securityPreferenceStore = AppSecurityPreferenceStore(appContext)
        val snapshotStore = WidgetSnapshotStore(appContext)
        val selectionStore = WidgetListSelectionStore(appContext)

        val initialLocked = securityPreferenceStore.appLockEnabled.value
        val initialSelection = selectionStore.selectionFor(appWidgetId)
        // Same fallback Today/Floater use for "no snapshot yet" (fresh install, or a reboot
        // before the app was ever opened) — a configured-but-unseeded instance shouldn't sit in
        // LOADING forever waiting for an unrelated cache write. The normal case never reaches
        // this: WidgetListConfigurationActivity writes the first snapshot synchronously right
        // after the user picks a list, before this provideGlance call ever runs.
        if (!initialLocked && initialSelection != null && !snapshotStore.existsList(appWidgetId)) {
            WidgetHydrateWorker.runOnce(appContext)
        }

        provideContent {
            ListTasksWidgetContent(appContext, appWidgetId, securityPreferenceStore, snapshotStore, selectionStore)
        }
    }
}

/**
 * Everything `provideGlance` runs ONCE per Glance session for (see the class KDoc): every value
 * that can change after cold start is collected as composable state HERE, not above
 * `provideContent`, exactly like TodayTasksWidget/FloaterTasksWidget. Pulled out to its own
 * composable so `provideGlance`'s own body stays a flat, linear setup sequence.
 */
@Composable
private fun ListTasksWidgetContent(
    appContext: Context,
    appWidgetId: Int,
    securityPreferenceStore: AppSecurityPreferenceStore,
    snapshotStore: WidgetSnapshotStore,
    selectionStore: WidgetListSelectionStore,
) {
    val isAppLocked by securityPreferenceStore.appLockEnabled.collectAsState()
    val snapshotVersion by WidgetSnapshotSignal.version.collectAsState()
    // The configured list itself can change too (the user reconfigures via the launcher's
    // widget-edit affordance, which relaunches WidgetListConfigurationActivity for this SAME
    // appWidgetId) — re-read on every signal bump, not just once at cold start.
    val selection = remember(snapshotVersion) { selectionStore.selectionFor(appWidgetId) }
    // `remember`, not `produceState` — see TodayTasksWidget for the on-device race this avoids:
    // an async follow-up recomposition can lose Glance's RemoteViews publish race.
    val currentSnapshot = remember(snapshotVersion, isAppLocked, selection) {
        if (isAppLocked || selection == null) null else snapshotStore.readList(appWidgetId)
    }
    Log.i(
        WIDGET_LOG_TAG,
        "list[$appWidgetId]: composing, version=$snapshotVersion locked=$isAppLocked " +
            "listType=${selection?.listType?.name ?: "none"} snapshotNull=${currentSnapshot == null}",
    )

    GlanceTheme {
        ListTasksWidgetBody(
            appContext = appContext,
            appWidgetId = appWidgetId,
            selection = selection,
            isAppLocked = isAppLocked,
            currentSnapshot = currentSnapshot,
        )
    }
}

@Composable
private fun ListTasksWidgetBody(
    appContext: Context,
    appWidgetId: Int,
    selection: WidgetListSelection?,
    isAppLocked: Boolean,
    currentSnapshot: WidgetSnapshot?,
) {
    val visuals = listWidgetVisualsFor(selection?.listType)
    val title = listWidgetTitleFor(appContext, selection, isAppLocked)

    if (selection == null) {
        // No selection on disk: an instance whose configuration was somehow lost (store cleared
        // without the widget itself being removed), or the launcher called `provideGlance`
        // before `WidgetListConfigurationActivity` finished writing it — reuse the SETUP visual
        // as "tap to finish setting this up" rather than inventing a fourth content state.
        UnconfiguredListWidgetContent(
            appContext = appContext,
            appWidgetId = appWidgetId,
            title = title,
            visuals = visuals,
            isAppLocked = isAppLocked,
        )
    } else {
        ConfiguredListWidgetContent(
            appContext = appContext,
            appWidgetId = appWidgetId,
            selection = selection,
            title = title,
            visuals = visuals,
            isAppLocked = isAppLocked,
            currentSnapshot = currentSnapshot,
        )
    }
}

private fun listWidgetVisualsFor(listType: WidgetListType?): TaskWidgetVisuals = when (listType) {
    WidgetListType.FLOATER -> FloaterWidgetVisuals
    WidgetListType.TODO, null -> todayWidgetVisuals(taskWidgetIsDaytime(LocalTime.now().hour))
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

@Composable
private fun UnconfiguredListWidgetContent(
    appContext: Context,
    appWidgetId: Int,
    title: String,
    visuals: TaskWidgetVisuals,
    isAppLocked: Boolean,
) {
    TaskWidgetContent(
        title = title,
        state = if (isAppLocked) TaskWidgetContentState.LOCKED else TaskWidgetContentState.SETUP,
        countLabel = "",
        setupTitle = appContext.getString(R.string.widget_list_tasks_setup_title),
        setupMessage = appContext.getString(R.string.widget_list_tasks_setup_message),
        emptyTitle = "",
        emptyMessage = "",
        lockedTitle = appContext.getString(R.string.widget_locked_title),
        lockedMessage = appContext.getString(R.string.widget_locked_message),
        loadingTitle = appContext.getString(R.string.widget_loading),
        rows = emptyList(),
        visuals = visuals,
        openAction = reconfigureAction(appWidgetId),
        addAction = reconfigureAction(appWidgetId),
    )
}

@Composable
private fun ConfiguredListWidgetContent(
    appContext: Context,
    appWidgetId: Int,
    selection: WidgetListSelection,
    title: String,
    visuals: TaskWidgetVisuals,
    isAppLocked: Boolean,
    currentSnapshot: WidgetSnapshot?,
) {
    val strings = listTasksWidgetStringsFor(appContext, selection.listType)
    TaskWidgetContent(
        title = title,
        state = listContentState(isAppLocked, currentSnapshot),
        countLabel = strings.countLabel(currentSnapshot?.taskCount ?: 0),
        setupTitle = appContext.getString(R.string.widget_today_tasks_setup_title),
        setupMessage = appContext.getString(R.string.widget_today_tasks_setup_message),
        emptyTitle = strings.emptyMessage,
        emptyMessage = strings.addTaskLabel,
        lockedTitle = appContext.getString(R.string.widget_locked_title),
        lockedMessage = appContext.getString(R.string.widget_locked_message),
        loadingTitle = appContext.getString(R.string.widget_loading),
        rows = if (isAppLocked || currentSnapshot == null) {
            emptyList()
        } else {
            listRows(currentSnapshot, selection.listType)
        },
        visuals = visuals,
        openAction = openListAction(selection.listId, selection.listName, selection.listType),
        addAction = openCreateListTaskAction(appWidgetId, selection.listId, selection.listType),
    )
}

private data class ListTasksWidgetStrings(
    val emptyMessage: String,
    val addTaskLabel: String,
    val countLabelFormat: String,
)

private fun listTasksWidgetStringsFor(appContext: Context, listType: WidgetListType): ListTasksWidgetStrings {
    val (emptyRes, addRes, countRes) = when (listType) {
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
    return ListTasksWidgetStrings(
        emptyMessage = appContext.getString(emptyRes),
        addTaskLabel = appContext.getString(addRes),
        countLabelFormat = appContext.getString(countRes),
    )
}

private fun ListTasksWidgetStrings.countLabel(count: Int): String =
    String.format(Locale.getDefault(), countLabelFormat, count)

private fun listContentState(
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
): TaskWidgetContentState = when {
    isAppLocked -> TaskWidgetContentState.LOCKED
    snapshot == null -> TaskWidgetContentState.LOADING
    else -> snapshot.status.toContentState()
}

private fun listRows(snapshot: WidgetSnapshot, listType: WidgetListType): List<TaskWidgetRow> {
    val completeAction: (String) -> Action = when (listType) {
        WidgetListType.TODO -> ::completeTodayTaskAction
        WidgetListType.FLOATER -> ::completeFloaterTaskAction
    }
    return if (listType == WidgetListType.TODO) {
        // One formatter for the whole list — see TodayTasksWidget for why this isn't baked at
        // write time (locale + 12/24h setting are read-time concerns).
        val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
        snapshot.rows.map { row ->
            TaskWidgetRow(
                key = row.key,
                title = row.title,
                priority = row.priorityRing.toPriorityValue(),
                trailingText = row.dueEpochMs?.let { dueTimeText(timeFormatter, it) },
                overdueTrailing = row.overdue,
                description = row.description,
                completeAction = completeAction(row.id),
            )
        }
    } else {
        snapshot.rows.map { row ->
            TaskWidgetRow(
                key = row.key,
                title = row.title,
                priority = row.priorityRing.toPriorityValue(),
                description = row.description,
                completeAction = completeAction(row.id),
            )
        }
    }
}

private fun dueTimeText(formatter: DateFormat, epochMs: Long): String =
    formatter.format(Date.from(Instant.ofEpochMilli(epochMs)))

/** Relaunches configuration for THIS instance — the same activity the launcher's own widget-edit
 *  affordance opens, reused here as the recovery path for a selection-less instance. */
private fun reconfigureAction(appWidgetId: Int) = actionStartActivity(
    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
        component = ComponentName(
            BuildConfig.APPLICATION_ID,
            WidgetListConfigurationActivity::class.java.name,
        )
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    },
)

/**
 * Carries this instance's own `appWidgetId`, so the create sheet resolves the feed from the
 * placement itself. The `target` parameter stays for the entry points that have no widget, but it
 * is no longer what decides this widget's behavior — a list instance whose stored selection cannot
 * be read now fails closed instead of quietly creating a scheduled task.
 */
private fun openCreateListTaskAction(
    appWidgetId: Int,
    listId: String,
    listType: WidgetListType,
) = actionStartActivity(
    Intent(
        Intent.ACTION_VIEW,
        Uri.parse(
            WidgetCreateRoute.deepLink(
                target = if (listType == WidgetListType.TODO) {
                    WidgetCreateRoute.TARGET_TODAY
                } else {
                    WidgetCreateRoute.TARGET_FLOATER
                },
                appWidgetId = appWidgetId,
                listId = listId,
            ),
        ),
    ).apply {
        component = ComponentName(
            BuildConfig.APPLICATION_ID,
            WidgetCreateTaskActivity::class.java.name,
        )
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    },
)

private fun openListAction(listId: String, listName: String, listType: WidgetListType) = actionStartActivity(
    Intent(Intent.ACTION_VIEW, Uri.parse(listDeepLink(listId, listName, listType))).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        addCategory(Intent.CATEGORY_LAUNCHER)
    },
)

private fun listDeepLink(listId: String, listName: String, listType: WidgetListType): String {
    val prefix = if (listType == WidgetListType.TODO) "tday://todos/list" else "tday://floater/list"
    return "$prefix/${Uri.encode(listId)}/${Uri.encode(listName)}"
}
