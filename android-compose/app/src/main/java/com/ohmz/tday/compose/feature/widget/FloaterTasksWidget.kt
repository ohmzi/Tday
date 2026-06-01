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
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import dagger.hilt.android.EntryPointAccessors
import java.util.Locale

private val FloaterWidgetFontFamily = FontFamily("Nunito")
private val FloaterWidgetAccentColor = ColorProvider(R.color.tday_widget_floater_accent)
private val FloaterWidgetAccentWash = ColorProvider(R.color.tday_widget_floater_accent_wash)

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
            countLabelFormat = context.getString(R.string.widget_floater_tasks_count),
            moreLabelFormat = context.getString(R.string.widget_today_tasks_more),
        )

        provideContent {
            GlanceTheme {
                FloaterWidgetContent(model = model, strings = strings)
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
    val moreLabelFormat: String,
)

private data class FloaterTasksWidgetListItem(
    val key: Long,
    val task: CachedFloaterRecord? = null,
    val overflowLabel: String? = null,
)

private enum class FloaterTasksWidgetLayout {
    COMPACT,
    WIDE,
    MEDIUM,
    TALL,
}

@Composable
private fun FloaterWidgetContent(
    model: FloaterTasksWidgetModel,
    strings: FloaterTasksWidgetStrings,
) {
    val layout = floaterWidgetLayoutFor(LocalSize.current)
    val horizontalPadding = if (layout == FloaterTasksWidgetLayout.COMPACT) 13.dp else 14.dp
    val topPadding = if (layout == FloaterTasksWidgetLayout.COMPACT) 7.dp else 8.dp
    val bottomPadding = if (layout == FloaterTasksWidgetLayout.COMPACT) 10.dp else 12.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(openFloaterAction()),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(3.dp)
                .background(FloaterWidgetAccentWash),
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
            FloaterHeaderRow(
                title = model.title,
                count = model.taskCount,
                countLabel = strings.countLabel(model.taskCount),
                showTitle = layout != FloaterTasksWidgetLayout.COMPACT,
            )

            Spacer(modifier = GlanceModifier.height(if (layout == FloaterTasksWidgetLayout.COMPACT) 4.dp else 5.dp))

            when (model.status) {
                FloaterTasksWidgetStatus.SETUP -> FloaterWidgetMessage(
                    title = strings.setupTitle,
                    message = strings.setupMessage,
                    compact = layout == FloaterTasksWidgetLayout.COMPACT,
                )

                FloaterTasksWidgetStatus.EMPTY -> FloaterWidgetMessage(
                    title = strings.emptyMessage,
                    message = strings.addTaskLabel,
                    compact = layout == FloaterTasksWidgetLayout.COMPACT,
                )

                FloaterTasksWidgetStatus.TASKS -> FloaterTaskList(
                    tasks = model.tasks,
                    overflowCount = model.overflowCount,
                    overflowLabel = strings.moreLabel(model.overflowCount),
                    layout = layout,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                )
            }
        }
    }
}

@Composable
private fun FloaterHeaderRow(
    title: String,
    count: Int,
    countLabel: String,
    showTitle: Boolean,
) {
    val headerHeight = 48.dp

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(headerHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .height(headerHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTitle) {
                Text(
                    modifier = GlanceModifier.defaultWeight(),
                    text = title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontFamily = FloaterWidgetFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.width(7.dp))
                Text(
                    text = countLabel,
                    style = TextStyle(
                        color = FloaterWidgetAccentColor,
                        fontFamily = FloaterWidgetFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
            } else {
                Text(
                    text = count.toString(),
                    style = TextStyle(
                        color = FloaterWidgetAccentColor,
                        fontFamily = FloaterWidgetFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                    ),
                    maxLines = 1,
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(if (showTitle) 8.dp else 6.dp))
        FloaterAddTaskButton()
    }
}

@Composable
private fun FloaterAddTaskButton() {
    Box(
        modifier = GlanceModifier
            .size(48.dp)
            .background(ImageProvider(R.drawable.widget_floater_add_button_background))
            .clickable(openDeepLinkAction(CREATE_FLOATER_DEEP_LINK)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = TextStyle(
                color = FloaterWidgetAccentColor,
                fontFamily = FloaterWidgetFontFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun FloaterWidgetMessage(
    title: String,
    message: String,
    compact: Boolean,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(openFloaterAction()),
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
                    fontFamily = FloaterWidgetFontFamily,
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
                        fontFamily = FloaterWidgetFontFamily,
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
private fun FloaterTaskList(
    tasks: List<CachedFloaterRecord>,
    overflowCount: Int,
    overflowLabel: String,
    layout: FloaterTasksWidgetLayout,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize(),
) {
    val rows = tasks.map { task ->
        FloaterTasksWidgetListItem(
            key = task.id.hashCode().toLong(),
            task = task,
        )
    } + if (overflowCount > 0) {
        listOf(
            FloaterTasksWidgetListItem(
                key = Long.MIN_VALUE,
                overflowLabel = overflowLabel,
            ),
        )
    } else {
        emptyList()
    }

    LazyColumn(modifier = modifier) {
        items(rows, itemId = { it.key }) { row ->
            row.task?.let { task ->
                FloaterTaskRow(task = task, layout = layout)
            } ?: row.overflowLabel?.let { label ->
                FloaterOverflowRow(label = label, layout = layout)
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun FloaterTaskRow(
    task: CachedFloaterRecord,
    layout: FloaterTasksWidgetLayout,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(floaterTaskRowHeight(layout))
            .clickable(openFloaterAction()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FloaterTaskPriorityDot(priority = task.priority, size = 7.dp)
        Spacer(modifier = GlanceModifier.width(7.dp))
        Text(
            modifier = GlanceModifier.defaultWeight(),
            text = task.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontFamily = FloaterWidgetFontFamily,
                fontSize = if (layout == FloaterTasksWidgetLayout.TALL) 13.sp else 12.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun FloaterOverflowRow(
    label: String,
    layout: FloaterTasksWidgetLayout,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(floaterTaskRowHeight(layout))
            .clickable(openFloaterAction()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = GlanceModifier.width(14.dp))
        Text(
            text = label,
            style = TextStyle(
                color = FloaterWidgetAccentColor,
                fontFamily = FloaterWidgetFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

private fun FloaterTasksWidgetStrings.countLabel(count: Int): String {
    return String.format(Locale.getDefault(), countLabelFormat, count)
}

private fun FloaterTasksWidgetStrings.moreLabel(count: Int): String {
    return String.format(Locale.getDefault(), moreLabelFormat, count)
}

@Composable
private fun FloaterTaskPriorityDot(priority: String, size: Dp) {
    Image(
        provider = ImageProvider(floaterPriorityDotResource(priority)),
        contentDescription = null,
        modifier = GlanceModifier
            .size(size),
    )
}

private fun floaterPriorityDotResource(priority: String): Int {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "high", "urgent", "important" -> R.drawable.widget_priority_dot_high
        "medium" -> R.drawable.widget_priority_dot_medium
        else -> R.drawable.widget_priority_dot_low
    }
}

private fun floaterWidgetLayoutFor(size: DpSize): FloaterTasksWidgetLayout {
    return when {
        size.height >= 210.dp -> FloaterTasksWidgetLayout.TALL
        size.height >= 150.dp -> FloaterTasksWidgetLayout.MEDIUM
        size.width >= 220.dp -> FloaterTasksWidgetLayout.WIDE
        size.height < 150.dp -> FloaterTasksWidgetLayout.COMPACT
        else -> FloaterTasksWidgetLayout.MEDIUM
    }
}

private fun floaterTaskRowHeight(layout: FloaterTasksWidgetLayout): Dp {
    return when (layout) {
        FloaterTasksWidgetLayout.WIDE -> 22.dp
        FloaterTasksWidgetLayout.MEDIUM -> 24.dp
        FloaterTasksWidgetLayout.TALL -> 25.dp
        FloaterTasksWidgetLayout.COMPACT -> 24.dp
    }
}

private fun openDeepLinkAction(deepLink: String) = actionStartActivity(
    if (deepLink == CREATE_FLOATER_DEEP_LINK) {
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

private fun openFloaterAction() = actionStartActivity(
    Intent(Intent.ACTION_VIEW, Uri.parse(FLOATER_DEEP_LINK)).apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    },
)

private const val FLOATER_DEEP_LINK = "tday://floater"
private const val CREATE_FLOATER_DEEP_LINK = "tday://todos/create?target=floater"
