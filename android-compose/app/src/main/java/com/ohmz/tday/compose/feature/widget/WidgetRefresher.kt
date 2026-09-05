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
 * Re-renders every placed widget instance — Today, Floater and per-list alike — with the Glance
 * class that instance's own receiver declares.
 *
 * Replaces the three per-kind refreshers this app used to have. They were structurally identical
 * but each owned a SEPARATE mutex and coroutine, so a single cache write fired three renders that
 * raced each other, and every caller had to decide up front which of the three to call. That
 * decision is what went wrong: the widget's own "+" routed its post-save repaint by a guessed
 * create target rather than by the instance that was tapped, and half the call sites in the app
 * (`MainActivity`, `TodoRepository`, `SyncManager`, `BulkTaskRepository`, `BootRescheduleReceiver`)
 * never called the per-list one at all, so list widgets simply went stale. With one refresher there
 * is no routing decision left to get wrong: [renderNow] walks [WidgetInstanceCatalog.bindings] and
 * pairs every id with the kind of the receiver it was enumerated from, so an id can never be handed
 * to a foreign widget class.
 *
 * Renders stay UNCONDITIONAL and SINGLE-FLIGHT for the reasons the old Today refresher documented:
 * `provideGlance` always reads the current snapshot, so a re-render with unchanged data is an
 * invisible no-op, whereas skipping one risks a stuck widget. Every render goes through one
 * [renderMutex] and fire-and-forget requests collapse through one CONFLATED channel — at most one
 * render at a time, plus exactly one trailing render that reads the latest snapshot.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val renderMutex = Mutex()
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val resolver = WidgetInstanceResolver(context)

    init {
        scope.launch {
            for (unused in refreshRequests) {
                runCatching { renderNow(firstAppWidgetId = null) }
            }
        }
    }

    /** Fire-and-forget refresh of every placed instance. Safe from any thread; collapses under load. */
    fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    /**
     * Render and SUSPEND until it completes. Use from a widget action or a background worker whose
     * process may be torn down the moment it returns — a fire-and-forget request could be killed
     * before it paints.
     *
     * [firstAppWidgetId], when given, is the instance the user actually interacted with: it is
     * painted first, with its OWN kind, inside the same lock hold, so the widget that was tapped
     * updates with the least possible latency in a short-lived process. It is an ordering hint
     * only — every other placed instance is still repainted in the same pass.
     */
    suspend fun refreshNow(firstAppWidgetId: Int? = null) {
        renderNow(firstAppWidgetId)
    }

    /**
     * Repaint exactly one instance, with the kind its own receiver declares. Returns false when the
     * id could not be resolved (already removed from the host, or never one of ours) — callers
     * should fall back to [refreshNow] rather than assuming a kind.
     */
    suspend fun refreshInstance(appWidgetId: Int): Boolean {
        val kind = resolver.kindOf(appWidgetId) ?: return false
        renderMutex.withLock { update(kind, appWidgetId) }
        return true
    }

    // `updateAll` alone is not enough and never was: it resolves a GlanceAppWidget class to the
    // receivers Glance itself recorded, so an on-screen widget of a size whose receiver Glance has
    // not seen this process is silently skipped. Enumerating each declared receiver's real ids
    // covers all nine; `updateAll` is kept per kind only as cheap belt-and-braces, exactly as the
    // per-kind refreshers had it.
    private suspend fun renderNow(firstAppWidgetId: Int?) {
        renderMutex.withLock {
            val manager = AppWidgetManager.getInstance(context)
            // All the routing lives in this one pure call (see WidgetInstanceCatalog.renderPlan);
            // everything below just executes what it returned.
            val plan = WidgetInstanceCatalog.renderPlan(
                firstAppWidgetId = firstAppWidgetId,
                kindOf = resolver::kindOf,
                idsForReceiver = { receiverClass ->
                    runCatching { manager.getAppWidgetIds(ComponentName(context, receiverClass)) }.getOrNull()
                },
            )

            var updated = 0
            for ((appWidgetId, kind) in plan) {
                if (update(kind, appWidgetId)) updated++
            }
            for (kind in plan.map { it.kind }.toSet()) {
                runCatching { WidgetInstanceCatalog.newWidget(kind).updateAll(context) }
                    .onFailure { Log.e(WIDGET_LOG_TAG, "${kind.name.lowercase()}: updateAll failed", it) }
            }
            // 0 distinguishes "no widget placed" from "painted nothing" when diagnosing a blank
            // widget after a reboot.
            Log.i(WIDGET_LOG_TAG, "widgets: render requested for $updated widget id(s)")
        }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    private suspend fun update(kind: WidgetInstanceKind, appWidgetId: Int): Boolean =
        runCatching { WidgetInstanceCatalog.newWidget(kind).update(context, AppWidgetId(appWidgetId)) }
            .onFailure { Log.e(WIDGET_LOG_TAG, "${kind.name.lowercase()}: update($appWidgetId) failed", it) }
            .isSuccess
}
