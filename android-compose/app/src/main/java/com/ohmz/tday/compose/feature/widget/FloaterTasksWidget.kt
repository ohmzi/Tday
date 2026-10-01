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
import java.util.Locale

/**
 * The leaf is the ROOT Anytime feed's mark, the same way the in-app empty state draws it only when
 * no floater list is selected (`TodoListScreen.emptyStateSceneIconForMode`). A floater LIST
 * instance of the List widget takes its own list's glyph instead — see `listWidgetVisualsFor`.
 */
private val FloaterWidgetWatermark = TaskWidgetWatermark(
    drawable = R.drawable.widget_empty_watermark_floater,
    // The accent the drawable already bakes; see TaskWidgetWatermark for why it is restated here.
    tint = R.color.tday_widget_floater_accent,
)

/** Reused by `ListTasksWidget` for a floater-list instance — same undated shape as Floater. */
internal val FloaterWidgetVisuals = TaskWidgetVisuals(
    addButtonBackground = R.drawable.widget_floater_add_button_background,
    addIcon = R.drawable.widget_add_icon_floater,
    emptyWatermark = FloaterWidgetWatermark,
    setupWatermark = FloaterWidgetWatermark,
)

/** What a Floater widget instance shows right now. See [TodayTasksWidget] for the read rules. */
internal object FloaterTasksWidget {

    fun model(context: Context, appWidgetId: Int): TaskWidgetModel {
        val appContext = context.applicationContext
        // App lock is checked FIRST, before anything reads a snapshot off disk.
        val isAppLocked = AppSecurityPreferenceStore(appContext).appLockEnabled.value
        val snapshot = if (isAppLocked) null else WidgetSnapshotStore(appContext).readFloater()
        // No snapshot yet and not locked: rebuild it off the render path; the rewrite repaints.
        if (!isAppLocked && snapshot == null) WidgetHydrateWorker.runOnce(appContext)
        logWidgetComposition(
            composingAs = WidgetInstanceKind.FLOATER,
            appWidgetId = appWidgetId,
            providerKind = WidgetInstanceResolver(appContext).kindOf(appWidgetId),
            details = "locked=$isAppLocked snapshotNull=${snapshot == null}",
        )

        val state = floaterContentState(isAppLocked, snapshot)
        return TaskWidgetModel(
            title = appContext.getString(R.string.widget_floater_tasks_title),
            state = state,
            countLabel = String.format(
                Locale.getDefault(),
                appContext.getString(R.string.widget_floater_tasks_count),
                snapshot?.taskCount ?: 0,
            ).takeIf { state == TaskWidgetContentState.TASKS },
            setupTitle = appContext.getString(R.string.widget_today_tasks_setup_title),
            setupMessage = appContext.getString(R.string.widget_today_tasks_setup_message),
            emptyTitle = appContext.getString(R.string.widget_floater_tasks_empty),
            lockedTitle = appContext.getString(R.string.widget_locked_title),
            lockedMessage = appContext.getString(R.string.widget_locked_message),
            loadingTitle = appContext.getString(R.string.widget_loading),
            addLabel = appContext.getString(R.string.widget_floater_tasks_add),
            items = if (isAppLocked || snapshot == null) {
                emptyList()
            } else {
                val checkingIds = WidgetCheckOff.ids()
                snapshot.rows.map { row ->
                    TaskWidgetListItem.Task(
                        TaskWidgetRow(
                            key = row.key,
                            id = row.id,
                            title = row.title,
                            priority = row.priorityRing.toPriorityValue(),
                            description = row.description,
                            checking = row.id in checkingIds,
                        ),
                    )
                }
            },
            visuals = FloaterWidgetVisuals,
            openIntent = openIntent(),
            addIntent = createIntent(appWidgetId),
        )
    }

    fun openIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(FLOATER_DEEP_LINK)).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    /** Carries this instance's own id so the create sheet resolves THIS instance's feed. */
    private fun createIntent(appWidgetId: Int): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(WidgetCreateRoute.deepLink(WidgetCreateRoute.targetFor(WidgetFeed.FLOATER), appWidgetId)),
    ).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, WidgetCreateTaskActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}

private fun floaterContentState(
    isAppLocked: Boolean,
    snapshot: WidgetSnapshot?,
): TaskWidgetContentState = when {
    isAppLocked -> TaskWidgetContentState.LOCKED
    snapshot == null -> TaskWidgetContentState.LOADING
    else -> snapshot.status.toContentState()
}

private const val FLOATER_DEEP_LINK = "tday://floater"
