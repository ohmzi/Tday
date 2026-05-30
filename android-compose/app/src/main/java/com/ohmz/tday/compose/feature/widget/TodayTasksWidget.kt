package com.ohmz.tday.compose.feature.widget

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.AppDataMode
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TdayWidgetFontFamily = FontFamily("Nunito")

class TodayTasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(150.dp, 110.dp),
            DpSize(250.dp, 110.dp),
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
    MEDIUM,
    TALL,
}

@Composable
private fun WidgetContent(
    model: TodayTasksWidgetModel,
    strings: TodayTasksWidgetStrings,
) {
    val layout = widgetLayoutFor(LocalSize.current)
    val horizontalPadding = if (layout == TodayTasksWidgetLayout.COMPACT) 12.dp else 14.dp
    val verticalPadding = if (layout == TodayTasksWidgetLayout.COMPACT) 10.dp else 12.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        HeaderRow(
            title = model.title,
            count = model.taskCount,
            addTaskLabel = strings.addTaskLabel,
            showTitle = layout != TodayTasksWidgetLayout.COMPACT,
        )

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
                TodayTasksWidgetLayout.MEDIUM -> TaskList(tasks = model.tasks.take(4))
                TodayTasksWidgetLayout.TALL -> TaskList(tasks = model.tasks)
            }
        }
    }
}

@Composable
private fun HeaderRow(
    title: String,
    count: Int,
    addTaskLabel: String,
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
                .height(48.dp)
                .clickable(openDeepLinkAction(TODAY_DEEP_LINK)),
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
                    color = GlanceTheme.colors.primary,
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
                    color = GlanceTheme.colors.primary,
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
            .clickable(openDeepLinkAction(if (message == title) TODAY_DEEP_LINK else CREATE_TODAY_DEEP_LINK)),
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
            .clickable(openTaskAction(task.id)),
    ) {
        TaskPriorityDot(priority = task.priority, size = 10.dp)
        Spacer(modifier = GlanceModifier.height(6.dp))
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
private fun TaskList(tasks: List<CachedTodoRecord>) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(tasks, itemId = { it.id.hashCode().toLong() }) { task ->
            TaskRow(task = task)
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun TaskRow(task: CachedTodoRecord) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable(openTaskAction(task.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskPriorityDot(priority = task.priority, size = 8.dp)
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            modifier = GlanceModifier.defaultWeight(),
            text = task.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontFamily = TdayWidgetFontFamily,
                fontSize = 14.sp,
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TaskPriorityDot(priority: String, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(priorityColor(priority)),
    ) {}
}

@Composable
private fun priorityColor(priority: String): ColorProvider {
    return when (priority.lowercase()) {
        "high" -> ColorProvider(Color(0xFFE47C7C))
        "medium" -> ColorProvider(Color(0xFFDDB37D))
        else -> GlanceTheme.colors.onSurfaceVariant
    }
}

private fun widgetLayoutFor(size: DpSize): TodayTasksWidgetLayout {
    return when {
        size.height < 150.dp -> TodayTasksWidgetLayout.COMPACT
        size.height >= 210.dp -> TodayTasksWidgetLayout.TALL
        else -> TodayTasksWidgetLayout.MEDIUM
    }
}

private fun dueTimeText(epochMs: Long): String {
    return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs))
}

private fun openTaskAction(taskId: String) = openDeepLinkAction(
    "tday://todos/all?highlightTodoId=${Uri.encode(taskId)}",
)

private fun openDeepLinkAction(deepLink: String) = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>("deepLink") to deepLink,
    ),
)

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")
    .withZone(ZoneId.systemDefault())

private const val TODAY_DEEP_LINK = "tday://todos/today"
private const val CREATE_TODAY_DEEP_LINK = "tday://todos/create?target=today"
