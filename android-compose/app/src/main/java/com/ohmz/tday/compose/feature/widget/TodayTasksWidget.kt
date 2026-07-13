package com.ohmz.tday.compose.feature.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import dagger.hilt.android.EntryPointAccessors
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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val cacheManager = entryPoint.offlineCacheManager()
        val secureConfigStore = entryPoint.secureConfigStore()
        val state = cacheManager.loadOfflineState()
        val model = buildTodayTasksWidgetModel(
            state = state,
            title = context.getString(R.string.widget_today_tasks_title),
            workspaceConfigured = secureConfigStore.getAppDataMode() != AppDataMode.UNSET,
        )
        val visuals = todayWidgetVisuals(taskWidgetIsDaytime(LocalTime.now().hour))
        val strings = TodayTasksWidgetStrings(
            emptyMessage = context.getString(R.string.widget_today_tasks_empty),
            setupTitle = context.getString(R.string.widget_today_tasks_setup_title),
            setupMessage = context.getString(R.string.widget_today_tasks_setup_message),
            addTaskLabel = context.getString(R.string.widget_today_tasks_add),
            countLabelFormat = context.getString(R.string.widget_today_tasks_count),
        )

        provideContent {
            GlanceTheme {
                TaskWidgetContent(
                    title = model.title,
                    state = model.status.toContentState(),
                    taskCount = model.taskCount,
                    countLabel = strings.countLabel(model.taskCount),
                    setupTitle = strings.setupTitle,
                    setupMessage = strings.setupMessage,
                    emptyTitle = strings.emptyMessage,
                    emptyMessage = strings.addTaskLabel,
                    rows = model.tasks.map { task ->
                        TaskWidgetRow(
                            key = task.id.hashCode().toLong(),
                            title = task.title,
                            priority = task.priority,
                            trailingText = task.dueEpochMs?.let(::dueTimeText),
                            description = task.description,
                            completeAction = completeTodayTaskAction(task.id),
                        )
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

private fun TodayTasksWidgetStatus.toContentState(): TaskWidgetContentState {
    return when (this) {
        TodayTasksWidgetStatus.SETUP -> TaskWidgetContentState.SETUP
        TodayTasksWidgetStatus.EMPTY -> TaskWidgetContentState.EMPTY
        TodayTasksWidgetStatus.TASKS -> TaskWidgetContentState.TASKS
    }
}

private fun TodayTasksWidgetStrings.countLabel(count: Int): String {
    return String.format(Locale.getDefault(), countLabelFormat, count)
}

private fun dueTimeText(epochMs: Long): String {
    return DateFormat
        .getTimeInstance(DateFormat.SHORT)
        .format(Date.from(Instant.ofEpochMilli(epochMs)))
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
