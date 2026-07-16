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
 * Refreshes the Today widget, but only when its DISPLAYED content actually changed.
 *
 * Every trigger (foreground mutation, realtime-driven sync, the 30-min background
 * [WidgetSyncWorker]) routes through here. We compute a signature of exactly what the widget
 * shows (status + count + each visible row) and compare it to the last-rendered signature; if
 * nothing visible changed we skip the reload. That's what lets a background sync which touched
 * only non-today data leave the widget untouched while the app still holds the latest state —
 * and a change to a displayed task still reloads immediately.
 */
@Singleton
class TodayTasksWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Serializes the compute -> compare -> render -> store sequence so two concurrent triggers
    // (e.g. a sync and a mutation) can't race on the stored signature.
    private val renderMutex = Mutex()

    fun requestRefresh() {
        scope.launch { refreshNow() }
    }

    suspend fun refreshNow() {
        renderMutex.withLock {
            val signature = computeDisplayedSignature()
            // Skip when the displayed content is unchanged. On a compute failure (null) we
            // fall through and render, so we never risk showing stale content.
            if (signature != null && signature == storedSignature()) {
                return@withLock
            }
            updateWidgetInstances()
            requestSystemRefresh()
            if (signature != null) storeSignature(signature)
        }
    }

    /**
     * A stable string of everything the widget renders. Uses the entry point (not constructor
     * injection) to reach the cache at call time — the cache manager depends on this refresher,
     * so a constructor dependency would be a Hilt cycle. Fields are joined with control chars
     * that can't appear in a title/description, so the signature is unambiguous.
     */
    private suspend fun computeDisplayedSignature(): String? = withContext(Dispatchers.Default) {
        runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            val state = entryPoint.offlineCacheManager().loadOfflineState()
            val configured =
                entryPoint.secureConfigStore().getAppDataMode() != AppDataMode.UNSET
            val model = buildTodayTasksWidgetModel(
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
                        .append(task.dueEpochMs).append(UNIT_SEP)
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
            val widget = TodayTasksWidget()
            runCatching { widget.updateAll(context) }
            todayWidgetReceiverClasses.forEach { receiverClass ->
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
        todayWidgetReceiverClasses.forEach { receiverClass ->
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
        const val PREFS_KEY = "today_tasks"
        private const val UNIT_SEP = '\u001F'
        private const val RECORD_SEP = '\u001E'
        val todayWidgetReceiverClasses = listOf(
            TodayTasksWidgetSmallReceiver::class.java,
            TodayTasksWidgetReceiver::class.java,
            TodayTasksWidgetLargeReceiver::class.java,
        )
    }
}
