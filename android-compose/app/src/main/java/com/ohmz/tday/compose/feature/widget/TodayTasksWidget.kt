package com.ohmz.tday.compose.feature.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshot
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore
import com.ohmz.tday.compose.feature.widget.snapshot.todayAt
import java.text.DateFormat
import java.time.Instant
import java.time.LocalTime
import java.util.Date
import java.util.Locale

/** Reused by `ListTasksWidget` for a todo-list instance — same due-date shape as Today. */
internal fun todayWidgetVisuals(isDaytime: Boolean): TaskWidgetVisuals {
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

/**
 * What a Today widget instance shows right now. Called on every render (see [WidgetRenderer]), so
 * the lock flag, the snapshot and the clock are always read fresh — there is no long-lived session
 * holding an earlier answer.
 *
 * No Hilt, no encrypted cache on this path: both stores below are constructed directly from the
 * application context and read a small snapshot file (see `WidgetSnapshotStore`).
 */
internal object TodayTasksWidget {

    fun model(context: Context, appWidgetId: Int, nowEpochMs: Long = System.currentTimeMillis()): TaskWidgetModel {
        val appContext = context.applicationContext
        // App lock is checked FIRST, before anything reads a snapshot off disk — a locked device
        // never touches task content at all. Plain SharedPreferences, no Keystore.
        val isAppLocked = AppSecurityPreferenceStore(appContext).appLockEnabled.value
        val snapshot = if (isAppLocked) null else WidgetSnapshotStore(appContext).readToday()
        // The snapshot's day containing now — usually its own, or after midnight one of the
        // upcoming days it carries (see todayAt), so turning over needs no rebuild.
        val day = snapshot?.todayAt(nowEpochMs)
        val snapshotStale = snapshot != null && day == null
        // No snapshot (a fresh install, an upgrade that rebooted before the app was ever opened,
        // or a file that failed to decrypt), or one that has run out of days (a week offline with
        // the app never opened): rebuild it from the cache off the render path. The rewrite
        // repaints; until then this renders LOADING.
        if (!isAppLocked && (snapshot == null || snapshotStale)) {
            WidgetHydrateWorker.runOnce(appContext)
        }
        // Absence after a reboot means the widget was never rendered at all; presence with
        // snapshotNull=true past the first frame means something is stuck.
        logWidgetComposition(
            composingAs = WidgetInstanceKind.TODAY,
            appWidgetId = appWidgetId,
            providerKind = WidgetInstanceResolver(appContext).kindOf(appWidgetId),
            details = "locked=$isAppLocked snapshotNull=${snapshot == null} stale=$snapshotStale",
        )

        return TaskWidgetModel(
            title = appContext.getString(R.string.widget_today_tasks_title),
            state = todayContentState(isAppLocked, snapshot, nowEpochMs),
            countLabel = String.format(
                Locale.getDefault(),
                appContext.getString(R.string.widget_today_tasks_count),
                day?.taskCount ?: 0,
            ),
            setupTitle = appContext.getString(R.string.widget_today_tasks_setup_title),
            setupMessage = appContext.getString(R.string.widget_today_tasks_setup_message),
            emptyTitle = appContext.getString(R.string.widget_today_tasks_empty),
            lockedTitle = appContext.getString(R.string.widget_locked_title),
            lockedMessage = appContext.getString(R.string.widget_locked_message),
            loadingTitle = appContext.getString(R.string.widget_loading),
            addLabel = appContext.getString(R.string.widget_today_tasks_add),
            rows = if (isAppLocked || day == null) {
                emptyList()
            } else {
                // One formatter for the whole list, not one per row: dueEpochMs is deliberately
                // NOT preformatted at write time (see WidgetSnapshot's KDoc — it depends on the
                // read-time locale and 12/24h setting), but constructing
                // DateFormat.getTimeInstance is not free and every row needs the same instance.
                val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
                day.rows.map { row ->
                    TaskWidgetRow(
                        key = row.key,
                        id = row.id,
                        title = row.title,
                        priority = row.priorityRing.toPriorityValue(),
                        trailingText = row.dueEpochMs?.let { dueTimeText(timeFormatter, it) },
                        description = row.description,
                    )
                }
            },
            // Follows the clock, so the day/night artwork turns over with it.
            visuals = todayWidgetVisuals(taskWidgetIsDaytime(LocalTime.now().hour)),
            openIntent = openIntent(),
            addIntent = createIntent(appWidgetId),
        )
    }

    fun openIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    /** Carries this instance's own id so the create sheet resolves THIS instance's feed. */
    private fun createIntent(appWidgetId: Int): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(WidgetCreateRoute.deepLink(WidgetCreateRoute.targetFor(WidgetFeed.SCHEDULED), appWidgetId)),
    ).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, WidgetCreateTaskActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}

/**
 * Renders the snapshot's day containing [nowEpochMs] (see [todayAt]). LOADING only when there is
 * no snapshot yet, or it has run out of days — never for one merely written on an earlier day,
 * which is the normal state of a widget after midnight whenever nothing has changed.
 */
internal fun todayContentState(
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
    nowEpochMs: Long,
): TaskWidgetContentState {
    if (isAppLocked) return TaskWidgetContentState.LOCKED
    val day = snapshot?.todayAt(nowEpochMs) ?: return TaskWidgetContentState.LOADING
    return day.status.toContentState()
}

internal fun dueTimeText(formatter: DateFormat, epochMs: Long): String =
    formatter.format(Date.from(Instant.ofEpochMilli(epochMs)))
