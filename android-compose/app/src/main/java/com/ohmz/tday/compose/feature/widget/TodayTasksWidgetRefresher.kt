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
 * Re-renders the Today widget from the offline cache. This is UNCONDITIONAL on purpose:
 * `provideGlance` always reads the current cache, so a re-render with unchanged data is an
 * invisible no-op — but skipping renders (a content-signature guard) risks a stuck, stale
 * widget if a single render is dropped, which is worse. Reliability over micro-optimization.
 *
 * Renders are SINGLE-FLIGHT. One sync writes the cache several times, and several callers
 * ([OfflineCacheManager], [SyncManager], [TodoRepository]) each ask for a refresh — so a naive
 * "launch a coroutine that calls updateAll" fires a burst of OVERLAPPING Glance sessions on the
 * same widget. The launcher coalesces/cancels those, and the session that would paint the newest
 * cache is sometimes the one dropped → the widget stays stale even though the in-app screen (which
 * just reads a StateFlow) already updated. Here every render goes through one [renderMutex], and
 * fire-and-forget requests collapse through a CONFLATED channel: at most one render runs at a time,
 * with exactly one trailing render afterward that reads the latest cache. That converges the widget
 * to the current cache the same way the screen's observable does, with no racing sessions to drop.
 */
@Singleton
class TodayTasksWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val renderMutex = Mutex()

    // CONFLATED: a burst of requests arriving while a render is in flight collapses to a single
    // queued render, so we do the in-flight render + exactly one trailing render (latest cache).
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

    // The three size receivers (Small / default / Large) all share ONE [TodayTasksWidget] class.
    // `updateAll` resolves a GlanceAppWidget class to a SINGLE receiver via Glance's internal
    // mapping, so it only ever repaints one receiver's instances and silently misses the others —
    // provideGlance never even runs for them. That is the "widget updates randomly" bug: whichever
    // receiver Glance last recorded wins, and an on-screen widget of a different size is skipped.
    // So the reliable path is to enumerate each receiver's real appWidgetIds via AppWidgetManager
    // and update them by id. `updateAll` is kept only as a cheap best-effort belt-and-braces.
    // Serialized through [renderMutex] so concurrent callers can't spawn overlapping Glance
    // sessions that cancel each other.
    @android.annotation.SuppressLint("RestrictedApi")
    private suspend fun renderNow() {
        renderMutex.withLock {
            val widget = TodayTasksWidget()
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
            TodayTasksWidgetSmallReceiver::class.java,
            TodayTasksWidgetReceiver::class.java,
            TodayTasksWidgetLargeReceiver::class.java,
        )
    }
}
