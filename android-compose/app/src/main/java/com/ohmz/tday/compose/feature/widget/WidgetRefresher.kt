package com.ohmz.tday.compose.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.AppWidgetId
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
 * raced each other, and every caller had to decide up front which of the three to call. Two things
 * went wrong with that decision, both fixed here:
 *
 *  - `MainActivity`, `TodoRepository`, `SyncManager`, `BulkTaskRepository` and
 *    `BootRescheduleReceiver` only ever called the Today and Floater refreshers, so per-list
 *    widgets went stale on every one of those paths. The boot one is the worst of them: after a
 *    reboot a per-list instance sat on its static `android:initialLayout` until some unrelated
 *    cache write happened to repaint it.
 *  - The widget's own "+" chose which refresher to AWAIT from a guessed create target rather than
 *    from the instance that was tapped. A misroute could not paint the wrong content — each
 *    refresher only ever enumerated its own receivers' ids — but it aimed the one SYNCHRONOUS
 *    repaint at the wrong kind, leaving the widget that was actually tapped to rely on the
 *    fire-and-forget request from the cache write, which a short-lived widget process can be torn
 *    down before it paints.
 *
 * With one refresher there is no routing decision left to get wrong: [renderNow] walks
 * [WidgetInstanceCatalog.bindings] and pairs every id with the kind of the receiver it was
 * enumerated from, so an id can never be handed to a foreign widget class, and one call covers all
 * three kinds.
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

    // THIS RENDERS THROUGH THE PLAN AND NOTHING ELSE. There used to be a trailing
    // `WidgetInstanceKind.entries.forEach { newWidget(it).updateAll(context) }` sweep here as
    // "cheap belt-and-braces". It was removed because it could not do the job it was kept for and
    // could do real harm:
    //
    //  - It cannot reach an id the plan missed. `updateAll` resolves a class through
    //    `GlanceAppWidgetManager.getGlanceIds`, which looks the class up in Glance's own DataStore
    //    and then calls `AppWidgetManager.getAppWidgetIds` on each recorded receiver — the same
    //    platform call `idsForReceiver` below makes, over a subset of the receivers (only those
    //    Glance has seen this process). It is strictly weaker than the plan, never wider. The one
    //    case it was justified by — our own `getAppWidgetIds` threw, so the kind is absent from the
    //    plan — is exactly the case where Glance's identical call throws too.
    //  - It routes by class, and a class is not an instance identity in a release build. R8's
    //    horizontal merger collapsed all three widget classes into one (see the Glance section of
    //    `proguard-rules.pro`), which made `TodayTasksWidget().updateAll` enumerate FLOATER and
    //    LIST ids and take ownership of their Glance sessions — painting Today's Tasks onto a
    //    Floater widget until the process died. The keep rules fix that; deleting the sweep means
    //    a future regression of them cannot reach a foreign id through this class at all.
    //
    // So an enumeration failure is now LOGGED rather than silently compensated for. Painting
    // nothing and saying so beats painting the wrong kind.
    private suspend fun renderNow(firstAppWidgetId: Int?) {
        renderMutex.withLock {
            val manager = AppWidgetManager.getInstance(context)
            // All the routing lives in this one pure call (see WidgetInstanceCatalog.renderPlan);
            // everything below just executes what it returned.
            val plan = WidgetInstanceCatalog.renderPlan(
                firstAppWidgetId = firstAppWidgetId,
                kindOf = resolver::kindOf,
                idsForReceiver = { receiverClass ->
                    runCatching { manager.getAppWidgetIds(ComponentName(context, receiverClass)) }
                        .onFailure {
                            Log.e(
                                WIDGET_LOG_TAG,
                                "widgets: could not enumerate ids for ${receiverClass.simpleName}; " +
                                    "its instances are not in this render plan",
                                it,
                            )
                        }
                        .getOrNull()
                },
            )

            var updated = 0
            for ((appWidgetId, kind) in plan) {
                if (update(kind, appWidgetId)) updated++
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
