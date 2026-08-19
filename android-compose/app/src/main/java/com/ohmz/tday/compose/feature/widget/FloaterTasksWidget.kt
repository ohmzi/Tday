package com.ohmz.tday.compose.feature.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.AppDataMode
import com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore
import dagger.hilt.android.EntryPointAccessors
import java.util.Locale

private val FloaterWidgetVisuals = TaskWidgetVisuals(
    addButtonBackground = R.drawable.widget_floater_add_button_background,
    addIcon = R.drawable.widget_add_icon_floater,
    emptyWatermark = R.drawable.widget_empty_watermark_floater,
    setupWatermark = R.drawable.widget_empty_watermark_floater,
    priorityRingOverride = R.drawable.widget_priority_ring_floater,
)

class FloaterTasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(TaskWidgetResponsiveSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val cacheManager = entryPoint.offlineCacheManager()
        val secureConfigStore = entryPoint.secureConfigStore()
        val securityPreferenceStore = AppSecurityPreferenceStore(context.applicationContext)
        val appContext = context.applicationContext
        val title = appContext.getString(R.string.widget_floater_tasks_title)
        val loadModel: suspend () -> FloaterTasksWidgetModel = {
            buildFloaterTasksWidgetModel(
                state = cacheManager.loadOfflineState(),
                title = title,
                workspaceConfigured = secureConfigStore.getAppDataMode() != AppDataMode.UNSET,
            )
        }
        // Seeded once so the first frame paints real content instead of flashing empty; the
        // composition below is what keeps it current from then on.
        val initialVersion = cacheManager.cacheDataVersion.value
        val initialModel = loadModel()
        // See TodayTasksWidget: absent = never composed, EMPTY = composed against an empty cache.
        android.util.Log.i(
            WIDGET_LOG_TAG,
            "floater: provideGlance session start, status=${initialModel.status} " +
                "count=${initialModel.taskCount} cacheVersion=$initialVersion",
        )
        val strings = FloaterTasksWidgetStrings(
            emptyMessage = appContext.getString(R.string.widget_floater_tasks_empty),
            setupTitle = appContext.getString(R.string.widget_today_tasks_setup_title),
            setupMessage = appContext.getString(R.string.widget_today_tasks_setup_message),
            addTaskLabel = appContext.getString(R.string.widget_floater_tasks_add),
            countLabelFormat = appContext.getString(R.string.widget_floater_tasks_count),
        )

        provideContent {
            // See TodayTasksWidget: provideGlance runs once per Glance session, so reading
            // the cache or the lock flag outside this lambda freezes the widget until the
            // session is recreated. Both are collected as composable state so a change
            // recomposes in place.
            val cacheVersion by cacheManager.cacheDataVersion.collectAsState()
            val isAppLocked by securityPreferenceStore.appLockEnabled.collectAsState()
            val model by produceState(initialModel, cacheVersion) {
                if (cacheVersion == initialVersion) return@produceState
                val reloaded = loadModel()
                android.util.Log.i(
                    WIDGET_LOG_TAG,
                    "floater: recomposed on cacheVersion=$cacheVersion, " +
                        "status=${reloaded.status} count=${reloaded.taskCount}",
                )
                value = reloaded
            }

            GlanceTheme {
                TaskWidgetContent(
                    title = model.title,
                    // Checked BEFORE any task content is touched below, so a locked device
                    // never even builds rows carrying real titles into the Glance tree.
                    state = if (isAppLocked) TaskWidgetContentState.LOCKED else model.status.toContentState(),
                    taskCount = model.taskCount,
                    countLabel = strings.countLabel(model.taskCount),
                    setupTitle = strings.setupTitle,
                    setupMessage = strings.setupMessage,
                    emptyTitle = strings.emptyMessage,
                    emptyMessage = strings.addTaskLabel,
                    lockedTitle = appContext.getString(R.string.widget_locked_title),
                    lockedMessage = appContext.getString(R.string.widget_locked_message),
                    rows = if (isAppLocked) {
                        emptyList()
                    } else {
                        model.tasks.map { task ->
                            TaskWidgetRow(
                                key = task.id.hashCode().toLong(),
                                title = task.title,
                                priority = task.priority,
                                description = task.description,
                                completeAction = completeFloaterTaskAction(task.id),
                            )
                        }
                    },
                    visuals = FloaterWidgetVisuals,
                    openAction = openFloaterAction(),
                    addAction = openCreateFloaterAction(),
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

private fun FloaterTasksWidgetStatus.toContentState(): TaskWidgetContentState {
    return when (this) {
        FloaterTasksWidgetStatus.SETUP -> TaskWidgetContentState.SETUP
        FloaterTasksWidgetStatus.EMPTY -> TaskWidgetContentState.EMPTY
        FloaterTasksWidgetStatus.TASKS -> TaskWidgetContentState.TASKS
    }
}

private fun FloaterTasksWidgetStrings.countLabel(count: Int): String {
    return String.format(Locale.getDefault(), countLabelFormat, count)
}

private fun openCreateFloaterAction() = actionStartActivity(
    Intent(Intent.ACTION_VIEW, Uri.parse(CREATE_FLOATER_DEEP_LINK)).apply {
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
private const val CREATE_FLOATER_DEEP_LINK = "tday://todos/create?target=floater"
