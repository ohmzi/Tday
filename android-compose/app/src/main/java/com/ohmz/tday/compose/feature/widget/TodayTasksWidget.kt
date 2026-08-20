package com.ohmz.tday.compose.feature.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
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
import java.text.DateFormat
import java.time.Instant
import java.time.LocalTime
import java.util.Date
import java.util.Locale

private fun todayWidgetVisuals(isDaytime: Boolean): TaskWidgetVisuals {
    val watermark = if (isDaytime) {
        R.drawable.widget_empty_watermark_today
    } else {
        R.drawable.widget_empty_watermark_today_night
    }

    return TaskWidgetVisuals(
        addButtonBackground = R.drawable.widget_add_button_background,
        addIcon = R.drawable.widget_add_icon_today,
        emptyWatermark = watermark,
        setupWatermark = watermark,
    )
}

class TodayTasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(TaskWidgetResponsiveSizes)

    // This app never reads Glance's own state store — the widget renders from
    // WidgetSnapshotStore instead — so skip the DataStore create+read AppWidgetSession otherwise
    // does before every composition.
    override val stateDefinition: GlanceStateDefinition<*>? = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // No EntryPointAccessors / Hilt / DB on this path — see WidgetEntryPoint's KDoc. Both
        // stores below are constructed directly from applicationContext, exactly the way
        // AppSecurityPreferenceStore already was before this change.
        val appContext = context.applicationContext
        val securityPreferenceStore = AppSecurityPreferenceStore(appContext)
        val snapshotStore = WidgetSnapshotStore(appContext)
        val title = appContext.getString(R.string.widget_today_tasks_title)
        val strings = TodayTasksWidgetStrings(
            emptyMessage = appContext.getString(R.string.widget_today_tasks_empty),
            setupTitle = appContext.getString(R.string.widget_today_tasks_setup_title),
            setupMessage = appContext.getString(R.string.widget_today_tasks_setup_message),
            addTaskLabel = appContext.getString(R.string.widget_today_tasks_add),
            countLabelFormat = appContext.getString(R.string.widget_today_tasks_count),
        )

        // App lock is checked FIRST, before anything reads a snapshot off disk — a locked
        // device never touches task content at all. Plain SharedPreferences, no Keystore.
        val initialLocked = securityPreferenceStore.appLockEnabled.value
        // No snapshot yet and not locked: a fresh install, an upgrade that rebooted before the
        // app was ever opened, or a file that fails to decrypt/decode (WidgetSnapshotStore.read
        // deletes it in that case, so `exists` stays truthful). Hydrate off the render path.
        // A cheap file-exists check, not a decrypt — the composition below does the one real read.
        if (!initialLocked && !snapshotStore.exists(WidgetSnapshotKind.TODAY)) {
            WidgetHydrateWorker.runOnce(appContext)
        }

        provideContent {
            // provideGlance runs ONCE per Glance session; a live session's own update() only
            // re-composes, it never re-runs provideGlance. Reading both signals as composable
            // state here means a lock toggle or a new snapshot recomposes the widget in place.
            val isAppLocked by securityPreferenceStore.appLockEnabled.collectAsState()
            val snapshotVersion by WidgetSnapshotSignal.version.collectAsState()
            // `remember`, not `produceState`: an explicit refresher update() publishes RemoteViews
            // from whatever the composition holds right now, and produceState's read would land
            // slightly later as its own follow-up recomposition — which can lose Glance's publish
            // race (confirmed on-device: intermittently left a widget stuck on "Loading tasks…"
            // after its snapshot had already resolved). `remember` recomputes inline in the same
            // pass that observes the key change, so there's no async gap to race.
            val currentSnapshot = remember(snapshotVersion, isAppLocked) {
                if (isAppLocked) null else snapshotStore.readToday()
            }
            // Inside the composition so the day/night artwork follows the clock too.
            val visuals = todayWidgetVisuals(taskWidgetIsDaytime(LocalTime.now().hour))

            GlanceTheme {
                TaskWidgetContent(
                    title = title,
                    state = todayContentState(isAppLocked, currentSnapshot),
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
                        // One formatter for the whole list, not one per row: dueEpochMs is
                        // deliberately NOT preformatted at write time (see WidgetSnapshot's
                        // KDoc — it depends on the read-time locale and 12/24h setting), but
                        // constructing DateFormat.getTimeInstance is not free and every row
                        // needs the same instance.
                        val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
                        currentSnapshot.rows.map { row ->
                            TaskWidgetRow(
                                key = row.key,
                                title = row.title,
                                priority = row.priorityRing.toPriorityValue(),
                                trailingText = row.dueEpochMs?.let { dueTimeText(timeFormatter, it) },
                                description = row.description,
                                completeAction = completeTodayTaskAction(row.id),
                            )
                        }
                    },
                    visuals = visuals,
                    openAction = openAppAction(),
                    addAction = openCreateTodayAction(),
                )
            }
        }
    }
}

private data class TodayTasksWidgetStrings(
    val emptyMessage: String,
    val setupTitle: String,
    val setupMessage: String,
    val addTaskLabel: String,
    val countLabelFormat: String,
)

private fun todayContentState(
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
): TaskWidgetContentState = when {
    isAppLocked -> TaskWidgetContentState.LOCKED
    snapshot == null -> TaskWidgetContentState.LOADING
    else -> snapshot.status.toContentState()
}

private fun TodayTasksWidgetStrings.countLabel(count: Int): String {
    return String.format(Locale.getDefault(), countLabelFormat, count)
}

private fun dueTimeText(formatter: DateFormat, epochMs: Long): String {
    return formatter.format(Date.from(Instant.ofEpochMilli(epochMs)))
}

private fun openCreateTodayAction() = actionStartActivity(
    Intent(Intent.ACTION_VIEW, Uri.parse(CREATE_TODAY_DEEP_LINK)).apply {
        component = ComponentName(
            BuildConfig.APPLICATION_ID,
            WidgetCreateTaskActivity::class.java.name,
        )
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    },
)

private fun openAppAction() = actionStartActivity(
    Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        addCategory(Intent.CATEGORY_LAUNCHER)
    },
)

private const val CREATE_TODAY_DEEP_LINK = "tday://todos/create?target=today"
