package com.ohmz.tday.compose.feature.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotKind
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Paints a widget from inside the `APPWIDGET_UPDATE` broadcast itself, before Glance's managed
 * update path gets a chance to run.
 *
 * **Why this exists.** After a reboot the launcher holds no RemoteViews for our widget ids, so it
 * inflates `android:initialLayout` ("Loading tasks…") until *we* call
 * [AppWidgetManager.updateAppWidget]. `GlanceAppWidgetReceiver.onUpdate` never does that from the
 * broadcast — it only does `goAsync { glanceAppWidget.update(...) }`, and the one `updateAppWidget`
 * call site that matters in glance-appwidget 1.1.1 sits at the far end of
 * `SessionManagerImpl.startSession` -> `WorkManager.enqueueUniqueWork(...).result.await()` ->
 * JobScheduler -> `SessionWorker.doWork` -> `AppWidgetSession.processEmittableTree`. Measured on a
 * Pixel 7 post-reboot, that WorkManager round trip alone cost ~2.4-3.0s, for data that was already
 * on disk and decrypts in well under 100ms. This class removes that wait: same data, same
 * composable, published straight from the broadcast via the public, non-experimental
 * [GlanceAppWidget.compose] — one composition with no WorkManager, pixel-identical to what the
 * managed session publishes moments later, so the handoff is invisible. Both tap mechanisms
 * survive it unchanged (`actionRunCallback` goes through Glance's own manifest-declared
 * `ActionCallbackBroadcastReceiver`; `actionStartActivity` is a plain `PendingIntent`).
 *
 * This is an OPTIMISATION ONLY: every failure path must leave the widget exactly as it was and let
 * the normal Glance update proceed, hence the blanket `runCatching`.
 */
internal object WidgetFastPaint {
    private const val TAG = "WidgetFastPaint"

    /**
     * Hard ceiling on how long we'll block the broadcast thread — a manifest BroadcastReceiver
     * gets ~10s before the system considers it stuck, and this needs to stay far inside that.
     */
    private const val TIMEOUT_MS = 300L

    /**
     * Only worth doing on the very FIRST `onUpdate` a cold process receives for a given [kind]:
     * that's the one case with no live Glance session yet, so a managed `update()` would otherwise
     * pay the full WorkManager round trip. Every `onUpdate` after that already has a live session,
     * where the managed path is already fast — and `updatePeriodMillis` fires `onUpdate` every 30
     * minutes for as long as the process lives, so without this gate the up-to-[TIMEOUT_MS]
     * main-thread block below would recur forever instead of running once at cold start.
     */
    private val alreadyPublished: MutableSet<WidgetSnapshotKind> = ConcurrentHashMap.newKeySet()

    /**
     * Composes and publishes [widget] for each id in [appWidgetIds].
     *
     * MUST be called synchronously from `onUpdate`, BEFORE `super.onUpdate(...)`: do NOT call
     * `goAsync()` here — `BroadcastReceiver.goAsync()` nulls the pending result, so a second call
     * in the same dispatch returns null, and Glance's `CoroutineBroadcastReceiver` calls
     * `pendingResult.finish()` in a `finally` with no exception handler, turning that into an
     * uncaught NPE. Finishing before `super.onUpdate` also stops this composition and the managed
     * session from running concurrently, which would otherwise race allocating layout ids for the
     * same widget id in Glance's shared `LayoutConfiguration`.
     */
    @SuppressLint("RestrictedApi") // AppWidgetId, exactly as WidgetRefresher already does
    fun publish(
        context: Context,
        widget: GlanceAppWidget,
        kind: WidgetSnapshotKind,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        if (!alreadyPublished.add(kind)) return

        runCatching {
            val appContext = context.applicationContext

            // App lock is read FIRST, before anything touches a snapshot — the same ordering
            // TodayTasksWidget.provideGlance uses. This is a security invariant, not a
            // micro-optimisation: a locked device must never read task text off disk.
            val isLocked = AppSecurityPreferenceStore(appContext).appLockEnabled.value

            // Nothing worth showing yet: no snapshot on disk (fresh install, or an upgrade that
            // rebooted before the app was ever opened). Leave the launcher on initialLayout rather
            // than replacing it with an empty frame — publishing a blank widget FASTER than before
            // would look exactly like the bug this whole effort removed. Glance's managed session
            // still runs right after us and will render LOADING + kick off WidgetHydrateWorker.
            // Deliberately `exists` (a stat) and not a decrypt: the composition below does the one
            // real read, so this stays free.
            if (!isLocked && !WidgetSnapshotStore(appContext).exists(kind)) return

            val manager = AppWidgetManager.getInstance(appContext)
            runBlocking(Dispatchers.Default) {
                withTimeout(TIMEOUT_MS) {
                    for (appWidgetId in appWidgetIds) {
                        // Real options, not the default empty Bundle `compose()` falls back to:
                        // on API < 31 Glance's SizeMode.Responsive derives the size buckets from
                        // this bundle's OPTION_APPWIDGET_MIN/MAX_WIDTH/HEIGHT, and an empty one
                        // silently collapses every size to the smallest bucket.
                        val options = manager.getAppWidgetOptions(appWidgetId)
                        val views = widget.compose(appContext, AppWidgetId(appWidgetId), options = options)
                        manager.updateAppWidget(appWidgetId, views)
                    }
                }
            }
        }.onFailure {
            // Expected and harmless in at least one real case: a before-first-unlock reboot, where
            // filesDir is still credential-encrypted and the snapshot cannot be read at all.
            Log.w(TAG, "Fast paint skipped for ${kind.name}; leaving the managed update to paint", it)
        }
    }
}
