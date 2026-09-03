package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
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
 * Re-renders every placed [ListTasksWidget] instance — the per-instance analogue of
 * [TodayTasksWidgetRefresher], copied structurally from it (single-flight render, conflated
 * fire-and-forget requests, `updateAll` as best-effort belt-and-braces alongside enumerating real
 * ids per receiver) for the same reasons documented there. The one difference: this refresher
 * does not need to know WHICH list each instance shows — every instance reads its own selection
 * and snapshot as composable state inside `ListTasksWidget.provideGlance`, keyed by its own
 * `appWidgetId`, so triggering a plain Glance `update()` per id is enough to make each one
 * recompose against whatever is on disk for it right now.
 */
@Singleton
class ListTasksWidgetRefresher @Inject constructor(
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

    /**
     * Render and SUSPEND until it completes. Use from a background worker whose process may be
     * torn down the moment it returns (a fire-and-forget request could be killed before it paints).
     */
    suspend fun refreshNow() {
        renderNow()
    }

    @android.annotation.SuppressLint("RestrictedApi")
    private suspend fun renderNow() {
        renderMutex.withLock {
            val widget = ListTasksWidget()
            val manager = AppWidgetManager.getInstance(context)
            var updated = 0
            for (receiverClass in receiverClasses) {
                val componentName = ComponentName(context, receiverClass)
                val ids = runCatching { manager.getAppWidgetIds(componentName) }.getOrNull() ?: continue
                for (appWidgetId in ids) {
                    runCatching { widget.update(context, AppWidgetId(appWidgetId)) }
                        .onSuccess { updated++ }
                        .onFailure { Log.e(WIDGET_LOG_TAG, "list: update($appWidgetId) failed", it) }
                }
            }
            runCatching { widget.updateAll(context) }
                .onFailure { Log.e(WIDGET_LOG_TAG, "list: updateAll failed", it) }
            Log.i(WIDGET_LOG_TAG, "list: render requested for $updated widget id(s)")
        }
    }

    private companion object {
        val receiverClasses = listOf(
            ListTasksWidgetSmallReceiver::class.java,
            ListTasksWidgetReceiver::class.java,
            ListTasksWidgetLargeReceiver::class.java,
        )
    }
}
