package com.ohmz.tday.compose.feature.widget

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TdayWidgetFontFamily = FontFamily("Nunito")

class TodayTasksWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val cacheManager = entryPoint.offlineCacheManager()
        val state = cacheManager.loadOfflineState()

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val todayTasks = state.todos
            .filter { task -> !task.completed && task.dueEpochMs?.let { it in dayStart until dayEnd } == true }
            .sortedBy { it.dueEpochMs ?: Long.MAX_VALUE }
            .take(8)

        provideContent {
            GlanceTheme {
                WidgetContent(tasks = todayTasks)
            }
        }
    }
}

@Composable
private fun WidgetContent(tasks: List<CachedTodoRecord>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>(
                    actionParametersOf(
                        ActionParameters.Key<String>("deepLink") to "tday://todos/today",
                    ),
                ))
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Today's Tasks",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${tasks.size}",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No tasks due today",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontFamily = TdayWidgetFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(tasks, itemId = { it.id.hashCode().toLong() }) { task ->
                    TaskRow(task = task)
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun TaskRow(task: CachedTodoRecord) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
    val dueText = task.dueEpochMs
        ?.let { timeFormatter.format(Instant.ofEpochMilli(it)) }
    val priorityColor = when (task.priority.lowercase()) {
        "high" -> ColorProvider(androidx.compose.ui.graphics.Color(0xFFFF3B30))
        "medium" -> ColorProvider(androidx.compose.ui.graphics.Color(0xFFFF9500))
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity<MainActivity>(
                actionParametersOf(
                    ActionParameters.Key<String>("deepLink") to
                        "tday://todos/all?highlightTodoId=${Uri.encode(task.id)}",
                ),
            ))
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .background(priorityColor),
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = task.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        dueText?.let { text ->
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = text,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontFamily = TdayWidgetFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}
