package com.ohmz.tday.compose.feature.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
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
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshot
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotKind
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotSignal
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore
import java.util.Locale

/** Reused by `ListTasksWidget` for a floater-list instance — same undated shape as Floater. */
internal val FloaterWidgetVisuals = TaskWidgetVisuals(
    addButtonBackground = R.drawable.widget_floater_add_button_background,
    addIcon = R.drawable.widget_add_icon_floater,
    emptyWatermark = R.drawable.widget_empty_watermark_floater,
    setupWatermark = R.drawable.widget_empty_watermark_floater,
    priorityRingOverride = R.drawable.widget_priority_ring_floater,
)

class FloaterTasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(TaskWidgetResponsiveSizes)

    // See TodayTasksWidget: this app never reads Glance's own state store.
    override val stateDefinition: GlanceStateDefinition<*>? = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // See TodayTasksWidget for the full rationale — no EntryPointAccessors / Hilt / DB on
        // this path. Both stores below are constructed directly from applicationContext.
        val appContext = context.applicationContext
        // See TodayTasksWidget: fixed for this instance's lifetime, so safe above `provideContent`,
        // and it rides the "+" deep link so the create sheet resolves THIS instance's feed rather
        // than trusting the deep link's own `target` parameter.
        val appWidgetId = GlanceAppWidgetManager(appContext).getAppWidgetId(id)
        val securityPreferenceStore = AppSecurityPreferenceStore(appContext)
        val snapshotStore = WidgetSnapshotStore(appContext)
        val title = appContext.getString(R.string.widget_floater_tasks_title)
        val strings = FloaterTasksWidgetStrings(
            emptyMessage = appContext.getString(R.string.widget_floater_tasks_empty),
            setupTitle = appContext.getString(R.string.widget_today_tasks_setup_title),
            setupMessage = appContext.getString(R.string.widget_today_tasks_setup_message),
            addTaskLabel = appContext.getString(R.string.widget_floater_tasks_add),
            countLabelFormat = appContext.getString(R.string.widget_floater_tasks_count),
        )

        // App lock is checked FIRST, before anything reads a snapshot off disk — a locked
        // device never touches task content at all. Plain SharedPreferences, no Keystore.
        val initialLocked = securityPreferenceStore.appLockEnabled.value
        // No snapshot yet and not locked: a fresh install, an upgrade that rebooted before the
        // app was ever opened, or a file that fails to decrypt/decode (WidgetSnapshotStore.read
        // deletes it in that case, so `exists` stays truthful). Hydrate off the render path.
        if (!initialLocked && !snapshotStore.exists(WidgetSnapshotKind.FLOATER)) {
            WidgetHydrateWorker.runOnce(appContext)
        }

        provideContent {
            // See TodayTasksWidget: provideGlance runs once per Glance session, so reading the
            // snapshot or the lock flag outside this lambda freezes the widget until the session
            // is recreated. Both are collected as composable state so a change recomposes in
            // place.
            val isAppLocked by securityPreferenceStore.appLockEnabled.collectAsState()
            val snapshotVersion by WidgetSnapshotSignal.version.collectAsState()
            // `remember`, not `produceState` — see TodayTasksWidget for why: this widget is what
            // actually caught the LOADING → hydrate → repaint race that left it stuck on
            // "Loading tasks…" indefinitely. `remember` recomputes inline in the same recompose
            // pass, so there's no async gap for that race to happen in.
            val currentSnapshot = remember(snapshotVersion, isAppLocked) {
                if (isAppLocked) null else snapshotStore.readFloater()
            }
            // See TodayTasksWidget: fires on every recompute this key change causes.
            Log.i(
                WIDGET_LOG_TAG,
                "floater: composing, version=$snapshotVersion locked=$isAppLocked " +
                    "snapshotNull=${currentSnapshot == null}",
            )

            GlanceTheme {
                TaskWidgetContent(
                    title = title,
                    state = floaterContentState(isAppLocked, currentSnapshot),
                    countLabel = strings.countLabel(currentSnapshot?.taskCount ?: 0),
                    setupTitle = strings.setupTitle,
                    setupMessage = strings.setupMessage,
                    emptyTitle = strings.emptyMessage,
                    emptyMessage = strings.addTaskLabel,
                    lockedTitle = appContext.getString(R.string.widget_locked_title),
                    lockedMessage = appContext.getString(R.string.widget_locked_message),
                    loadingTitle = appContext.getString(R.string.widget_loading),
                    rows = if (isAppLocked || currentSnapshot == null) {
                        emptyList()
                    } else {
                        currentSnapshot.rows.map { row ->
                            TaskWidgetRow(
                                key = row.key,
                                title = row.title,
                                priority = row.priorityRing.toPriorityValue(),
                                description = row.description,
                                completeAction = completeFloaterTaskAction(row.id),
                            )
                        }
                    },
                    visuals = FloaterWidgetVisuals,
                    openAction = openFloaterAction(),
                    addAction = openCreateFloaterAction(appWidgetId),
                )
            }
        }
    }
}

private data class FloaterTasksWidgetStrings(
    val emptyMessage: String,
    val setupTitle: String,
    val setupMessage: String,
    val addTaskLabel: String,
    val countLabelFormat: String,
)

private fun floaterContentState(
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
): TaskWidgetContentState = when {
    isAppLocked -> TaskWidgetContentState.LOCKED
    snapshot == null -> TaskWidgetContentState.LOADING
    else -> snapshot.status.toContentState()
}

private fun FloaterTasksWidgetStrings.countLabel(count: Int): String {
    return String.format(Locale.getDefault(), countLabelFormat, count)
}

private fun openCreateFloaterAction(appWidgetId: Int) = actionStartActivity(
    Intent(
        Intent.ACTION_VIEW,
        Uri.parse(WidgetCreateRoute.deepLink(WidgetCreateRoute.TARGET_FLOATER, appWidgetId)),
    ).apply {
        component = ComponentName(
            BuildConfig.APPLICATION_ID,
            WidgetCreateTaskActivity::class.java.name,
        )
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    },
)

private fun openFloaterAction() = actionStartActivity(
    Intent(Intent.ACTION_VIEW, Uri.parse(FLOATER_DEEP_LINK)).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    },
)

private const val FLOATER_DEEP_LINK = "tday://floater"
