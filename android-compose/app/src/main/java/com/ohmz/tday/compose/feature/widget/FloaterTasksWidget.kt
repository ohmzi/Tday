package com.ohmz.tday.compose.feature.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.unit.ColorProvider
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.AppDataMode
import dagger.hilt.android.EntryPointAccessors
import java.util.Locale

private val FloaterWidgetVisuals = TaskWidgetVisuals(
    accentColor = ColorProvider(R.color.tday_widget_floater_accent),
    accentWash = ColorProvider(R.color.tday_widget_floater_accent_wash),
    countPillBackground = R.drawable.widget_floater_count_pill_background,
    addButtonBackground = R.drawable.widget_floater_add_button_background,
    addIcon = R.drawable.widget_add_icon_floater,
    featuredRowBackground = R.drawable.widget_floater_feature_row_background,
)

class FloaterTasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(150.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 160.dp),
            DpSize(250.dp, 220.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val cacheManager = entryPoint.offlineCacheManager()
        val secureConfigStore = entryPoint.secureConfigStore()
        val state = cacheManager.loadOfflineState()
        val model = buildFloaterTasksWidgetModel(
            state = state,
            title = context.getString(R.string.widget_floater_tasks_title),
            workspaceConfigured = secureConfigStore.getAppDataMode() != AppDataMode.UNSET,
        )
        val strings = FloaterTasksWidgetStrings(
            emptyMessage = context.getString(R.string.widget_floater_tasks_empty),
            setupTitle = context.getString(R.string.widget_today_tasks_setup_title),
            setupMessage = context.getString(R.string.widget_today_tasks_setup_message),
            addTaskLabel = context.getString(R.string.widget_floater_tasks_add),
            countUnit = context.getString(R.string.widget_floater_tasks_count_unit),
            countLabelFormat = context.getString(R.string.widget_floater_tasks_count),
            moreLabelFormat = context.getString(R.string.widget_today_tasks_more),
        )

        provideContent {
            GlanceTheme {
                TaskWidgetContent(
                    title = model.title,
                    state = model.status.toContentState(),
                    taskCount = model.taskCount,
                    countLabel = strings.countLabel(model.taskCount),
                    countUnit = strings.countUnit,
                    setupTitle = strings.setupTitle,
                    setupMessage = strings.setupMessage,
                    emptyTitle = strings.emptyMessage,
                    emptyMessage = strings.addTaskLabel,
                    rows = model.tasks.map { task ->
                        TaskWidgetRow(
                            key = task.id.hashCode().toLong(),
                            title = task.title,
                            priority = task.priority,
                        )
                    },
                    overflowCount = model.overflowCount,
                    overflowLabel = strings.moreLabelFormat,
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
    val countUnit: String,
    val countLabelFormat: String,
    val moreLabelFormat: String,
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
