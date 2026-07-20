package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-renders the Floater widget from the offline cache. Unconditional + SINGLE-FLIGHT — see
 * [TodayTasksWidgetRefresher] for the full rationale: renders are serialized through a mutex +
 * conflated channel (racing Glance sessions drop the newest render otherwise), and instances are
 * updated by their real appWidgetId per receiver because `updateAll` resolves the shared
 * GlanceAppWidget class to a single receiver and silently skips the others.
 */
@Singleton
class FloaterTasksWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val renderMutex = Mutex()
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (unused in refreshRequests) {
                runCatching { renderNow() }
            }
        }
    }

    /** Fire-and-forget refresh. Safe to call from any thread; collapses under load. */
    fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    /** Render and SUSPEND until it completes — for a background worker that may die on return. */
    suspend fun refreshNow() {
        renderNow()
    }

    @android.annotation.SuppressLint("RestrictedApi")
    private suspend fun renderNow() {
        renderMutex.withLock {
            val widget = FloaterTasksWidget()
            val manager = AppWidgetManager.getInstance(context)
            for (receiverClass in receiverClasses) {
                val componentName = ComponentName(context, receiverClass)
                val ids = runCatching { manager.getAppWidgetIds(componentName) }.getOrNull() ?: continue
                for (appWidgetId in ids) {
                    runCatching { widget.update(context, AppWidgetId(appWidgetId)) }
                }
            }
            runCatching { widget.updateAll(context) }
        }
    }

    private companion object {
        val receiverClasses = listOf(
            FloaterTasksWidgetSmallReceiver::class.java,
            FloaterTasksWidgetReceiver::class.java,
            FloaterTasksWidgetLargeReceiver::class.java,
        )
    }
}
