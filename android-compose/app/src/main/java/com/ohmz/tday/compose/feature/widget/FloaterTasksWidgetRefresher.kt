package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.updateAll
import com.ohmz.tday.compose.core.data.AppDataMode
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes the Floater widget, but only when its DISPLAYED content actually changed. See
 * [TodayTasksWidgetRefresher] for the rationale — the 30-min background sync must leave the
 * widget untouched when nothing visible changed, while any change to a shown floater reloads.
 */
@Singleton
class FloaterTasksWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val renderMutex = Mutex()

    fun requestRefresh() {
        scope.launch { refreshNow() }
    }

    suspend fun refreshNow() {
        renderMutex.withLock {
            val signature = computeDisplayedSignature()
            if (signature != null && signature == storedSignature()) {
                return@withLock
            }
            updateWidgetInstances()
            requestSystemRefresh()
            if (signature != null) storeSignature(signature)
        }
    }

    private suspend fun computeDisplayedSignature(): String? = withContext(Dispatchers.Default) {
        runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            val state = entryPoint.offlineCacheManager().loadOfflineState()
            val configured =
                entryPoint.secureConfigStore().getAppDataMode() != AppDataMode.UNSET
            val model = buildFloaterTasksWidgetModel(
                state = state,
                title = "",
                workspaceConfigured = configured,
            )
            buildString {
                append(model.status.name).append(UNIT_SEP).append(model.taskCount)
                model.tasks.forEach { task ->
                    append(RECORD_SEP)
                        .append(task.id).append(UNIT_SEP)
                        .append(task.title).append(UNIT_SEP)
                        .append(task.priority).append(UNIT_SEP)
                        .append(task.description.orEmpty())
                }
            }
        }.getOrNull()
    }

    private fun storedSignature(): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PREFS_KEY, null)

    private fun storeSignature(signature: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY, signature)
            .apply()
    }

    // Glance exposes no public per-id update path; AppWidgetId is the
    // documented interop for refreshing specific widget instances.
    @android.annotation.SuppressLint("RestrictedApi")
    private suspend fun updateWidgetInstances() {
        withContext(Dispatchers.Default) {
            val widget = FloaterTasksWidget()
            runCatching { widget.updateAll(context) }
            floaterWidgetReceiverClasses.forEach { receiverClass ->
                runCatching {
                    val manager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, receiverClass)
                    manager.getAppWidgetIds(componentName).forEach { appWidgetId ->
                        widget.update(context, AppWidgetId(appWidgetId))
                    }
                }
            }
        }
    }

    private fun requestSystemRefresh() {
        val manager = AppWidgetManager.getInstance(context)
        floaterWidgetReceiverClasses.forEach { receiverClass ->
            val componentName = ComponentName(context, receiverClass)
            val appWidgetIds = manager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return@forEach
            context.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = componentName
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                },
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "tday_widget_signatures"
        const val PREFS_KEY = "floater_tasks"
        private const val UNIT_SEP = '\u001F'
        private const val RECORD_SEP = '\u001E'
        val floaterWidgetReceiverClasses = listOf(
            FloaterTasksWidgetSmallReceiver::class.java,
            FloaterTasksWidgetReceiver::class.java,
            FloaterTasksWidgetLargeReceiver::class.java,
        )
    }
}
