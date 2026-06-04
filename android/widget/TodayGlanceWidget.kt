package com.ohmz.tday.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ohmz.tday.MainActivity              // adjust to your actual main activity
import com.ohmz.tday.widget.WidgetStateKeys
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// Entry point so the widget can reach Hilt-managed repos without a ViewModel
// ---------------------------------------------------------------------------
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun todoRepository(): com.ohmz.tday.data.TodoRepository      // adjust import
    fun floaterRepository(): com.ohmz.tday.data.FloaterRepository // adjust import
}

// ---------------------------------------------------------------------------
// Receiver — registered in AndroidManifest.xml
// ---------------------------------------------------------------------------
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayGlanceWidget()
}

// ---------------------------------------------------------------------------
// Widget
// ---------------------------------------------------------------------------
class TodayGlanceWidget : GlanceAppWidget() {

    // Use DataStore Preferences so state survives process restarts
    override val stateDefinition: GlanceStateDefinition<*> =
        PreferencesGlanceStateDefinition

    // Support multiple sizes; Glance picks the best fit automatically
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 110.dp),
            DpSize(270.dp, 110.dp),
            DpSize(180.dp, 220.dp),
            DpSize(270.dp, 220.dp),
        )
    )

    // Called by the system and by WidgetUpdateManager.scheduleImmediateUpdate()
    override suspend fun onUpdate(
        context: Context,
        manager: androidx.glance.appwidget.GlanceAppWidgetManager,
        id: GlanceId,
    ) {
        super.onUpdate(context, manager, id)
        loadAndPersistState(context, id)
    }

    // Provide the Glance UI
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { TodayWidgetContent() }
    }

    // -----------------------------------------------------------------------
    // Data loading — fetch from Room, serialise to Prefs so the UI just reads
    // -----------------------------------------------------------------------
    private suspend fun loadAndPersistState(context: Context, id: GlanceId) =
        withContext(Dispatchers.IO) {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )

            val tasksJson = runCatching {
                val tasks = ep.todoRepository().getTodayTasksForWidget()
                Json.encodeToString(tasks)
            }.getOrDefault("[]")

            androidx.glance.appwidget.state.updateAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                id,
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetStateKeys.TASKS_JSON] = tasksJson
                    this[WidgetStateKeys.LAST_UPDATED_MS] = System.currentTimeMillis()
                }
            }
            update(context, id)   // trigger recompose with new state
        }
}

// ---------------------------------------------------------------------------
// UI — reads from Glance state; no async work here
// ---------------------------------------------------------------------------
@Composable
private fun TodayWidgetContent() {
    val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
    val tasksJson = prefs[WidgetStateKeys.TASKS_JSON] ?: "[]"
    val lastUpdated = prefs[WidgetStateKeys.LAST_UPDATED_MS] ?: 0L

    val tasks = runCatching {
        Json.decodeFromString<List<WidgetTaskItem>>(tasksJson)
    }.getOrDefault(emptyList())

    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp),
        ) {
            // Header row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = GlanceTheme.colors.onSurface,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                // Tap anywhere to open the app
                Box(
                    modifier = GlanceModifier
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    Text(
                        text = "Open",
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.primary),
                    )
                }
            }

            Spacer(GlanceModifier.height(6.dp))

            if (tasks.isEmpty()) {
                Text(
                    text = "No tasks today",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
            } else {
                tasks.take(5).forEach { task ->
                    TaskRow(task)
                }
                if (tasks.size > 5) {
                    Text(
                        text = "+${tasks.size - 5} more",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant,
                        ),
                        modifier = GlanceModifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: WidgetTaskItem) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Priority dot
        Box(
            modifier = GlanceModifier
                .size(6.dp)
                .background(
                    when (task.priority) {
                        "HIGH"   -> androidx.glance.unit.ColorProvider(android.graphics.Color.RED)
                        "MEDIUM" -> androidx.glance.unit.ColorProvider(android.graphics.Color.rgb(255, 165, 0))
                        else     -> androidx.glance.unit.ColorProvider(android.graphics.Color.GRAY)
                    }
                )
        ) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = task.title,
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

// ---------------------------------------------------------------------------
// Lightweight DTO for widget rendering (serialise only what the widget needs)
// ---------------------------------------------------------------------------
@kotlinx.serialization.Serializable
data class WidgetTaskItem(
    val id: String,
    val title: String,
    val priority: String,   // "HIGH" | "MEDIUM" | "LOW"
    val done: Boolean,
)
