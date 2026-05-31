package com.ohmz.tday.compose.feature.widget

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.AppDataMode
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import dagger.hilt.android.EntryPointAccessors
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

private val TdayWidgetFontFamily = FontFamily("Nunito")
private val TdayWidgetAccentColor = ColorProvider(R.color.tday_widget_today_accent)
private val TdayWidgetAccentWash = ColorProvider(R.color.tday_widget_accent_wash)

class TodayTasksWidget : GlanceAppWidget() {
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
        val model = buildTodayTasksWidgetModel(
            state = state,
            title = context.getString(R.string.widget_today_tasks_title),
            workspaceConfigured = secureConfigStore.getAppDataMode() != AppDataMode.UNSET,
        )
        val strings = TodayTasksWidgetStrings(
            emptyMessage = context.getString(R.string.widget_today_tasks_empty),
            setupTitle = context.getString(R.string.widget_today_tasks_setup_title),
            setupMessage = context.getString(R.string.widget_today_tasks_setup_message),
            addTaskLabel = context.getString(R.string.widget_today_tasks_add),
        )

        provideContent {
            GlanceTheme {
                WidgetContent(model = model, strings = strings)
            }
        }
    }
}

private data class TodayTasksWidgetStrings(
    val emptyMessage: String,
    val setupTitle: String,
    val setupMessage: String,
    val addTaskLabel: String,
)

private enum class TodayTasksWidgetLayout {
    COMPACT,
    WIDE,
    MEDIUM,
    TALL,
}

@Composable
private fun WidgetContent(
    model: TodayTasksWidgetModel,
    strings: TodayTasksWidgetStrings,
) {
    val layout = widgetLayoutFor(LocalSize.current)
    val horizontalPadding = if (layout == TodayTasksWidgetLayout.COMPACT) 13.dp else 14.dp
    val topPadding = if (layout == TodayTasksWidgetLayout.COMPACT) 7.dp else 8.dp
    val bottomPadding = if (layout == TodayTasksWidgetLayout.COMPACT) 10.dp else 12.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(openAppAction()),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(3.dp)
                .background(TdayWidgetAccentWash),
        ) {}

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    start = horizontalPadding,
                    top = topPadding,
                    end = horizontalPadding,
                    bottom = bottomPadding,
                ),
        ) {
            HeaderRow(
                title = model.title,
                count = model.taskCount,
                showTitle = layout != TodayTasksWidgetLayout.COMPACT,
            )

            Spacer(modifier = GlanceModifier.height(if (layout == TodayTasksWidgetLayout.COMPACT) 4.dp else 5.dp))

            when (model.status) {
                TodayTasksWidgetStatus.SETUP -> WidgetMessage(
                    title = strings.setupTitle,
                    message = strings.setupMessage,
                    compact = layout == TodayTasksWidgetLayout.COMPACT,
                )

                TodayTasksWidgetStatus.EMPTY -> WidgetMessage(
                    title = strings.emptyMessage,
                    message = strings.addTaskLabel,
                    compact = layout == TodayTasksWidgetLayout.COMPACT,
                )

                TodayTasksWidgetStatus.TASKS -> when (layout) {
                    TodayTasksWidgetLayout.COMPACT -> CompactTaskSummary(model.tasks.first())
                    TodayTasksWidgetLayout.WIDE,
                    TodayTasksWidgetLayout.MEDIUM,
                    TodayTasksWidgetLayout.TALL -> TaskList(
                        tasks = model.tasks,
                        layout = layout,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    title: String,
    count: Int,
    showTitle: Boolean,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTitle) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontFamily = TdayWidgetFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
            }
            Text(
                text = count.toString(),
                style = TextStyle(
                    color = TdayWidgetAccentColor,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = if (showTitle) 14.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }

        Box(
            modifier = GlanceModifier
                .size(48.dp)
                .clickable(openDeepLinkAction(CREATE_TODAY_DEEP_LINK)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = TextStyle(
                    color = TdayWidgetAccentColor,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WidgetMessage(
    title: String,
    message: String,
    compact: Boolean,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(openAppAction()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = if (compact) 14.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = if (compact) 1 else 2,
            )
            if (!compact) {
                Text(
                    text = message,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontFamily = TdayWidgetFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun CompactTaskSummary(task: CachedTodoRecord) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(openAppAction()),
    ) {
        TaskPriorityDot(priority = task.priority, size = 9.dp)
        Spacer(modifier = GlanceModifier.height(5.dp))
        Text(
            text = task.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontFamily = TdayWidgetFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )
        task.dueEpochMs?.let { due ->
            Text(
                text = dueTimeText(due),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TaskList(
    tasks: List<CachedTodoRecord>,
    layout: TodayTasksWidgetLayout,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize(),
) {
    LazyColumn(modifier = modifier) {
        items(tasks, itemId = { it.id.hashCode().toLong() }) { task ->
            TaskRow(task = task, layout = layout)
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun TaskRow(
    task: CachedTodoRecord,
    layout: TodayTasksWidgetLayout,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(taskRowHeight(layout))
            .clickable(openAppAction()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskPriorityDot(priority = task.priority, size = 7.dp)
        Spacer(modifier = GlanceModifier.width(7.dp))
        Text(
            modifier = GlanceModifier.defaultWeight(),
            text = task.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontFamily = TdayWidgetFontFamily,
                fontSize = if (layout == TodayTasksWidgetLayout.TALL) 13.sp else 12.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        task.dueEpochMs?.let { due ->
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = dueTimeText(due),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TaskPriorityDot(priority: String, size: Dp) {
    Image(
        provider = ImageProvider(priorityDotResource(priority)),
        contentDescription = null,
        modifier = GlanceModifier
            .size(size),
    )
}

private fun priorityDotResource(priority: String): Int {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "high", "urgent", "important" -> R.drawable.widget_priority_dot_high
        "medium" -> R.drawable.widget_priority_dot_medium
        else -> R.drawable.widget_priority_dot_low
    }
}

private fun widgetLayoutFor(size: DpSize): TodayTasksWidgetLayout {
    return when {
        size.height >= 210.dp -> TodayTasksWidgetLayout.TALL
        size.height >= 150.dp -> TodayTasksWidgetLayout.MEDIUM
        size.width >= 220.dp -> TodayTasksWidgetLayout.WIDE
        size.height < 150.dp -> TodayTasksWidgetLayout.COMPACT
        else -> TodayTasksWidgetLayout.MEDIUM
    }
}

private fun taskRowHeight(layout: TodayTasksWidgetLayout): Dp {
    return when (layout) {
        TodayTasksWidgetLayout.WIDE -> 22.dp
        TodayTasksWidgetLayout.MEDIUM -> 24.dp
        TodayTasksWidgetLayout.TALL -> 25.dp
        TodayTasksWidgetLayout.COMPACT -> 24.dp
    }
}

private fun dueTimeText(epochMs: Long): String {
    return DateFormat
        .getTimeInstance(DateFormat.SHORT)
        .format(Date.from(Instant.ofEpochMilli(epochMs)))
}

private fun openDeepLinkAction(deepLink: String) = actionStartActivity(
    if (deepLink == CREATE_TODAY_DEEP_LINK) {
        Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            component = ComponentName(
                BuildConfig.APPLICATION_ID,
                WidgetCreateTaskActivity::class.java.name,
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
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
