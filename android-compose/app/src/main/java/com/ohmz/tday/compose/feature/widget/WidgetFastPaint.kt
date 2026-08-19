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
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Paints a widget from inside the `APPWIDGET_UPDATE` broadcast itself, before Glance's managed
 * update path gets a chance to run.
 *
 * **Why this exists.** After a reboot the launcher holds no RemoteViews for our widget ids, so it
 * inflates `android:initialLayout` ("Loading tasks…") and keeps showing it until *we* call
 * [AppWidgetManager.updateAppWidget]. Glance's own receiver never does that from the broadcast:
 * `GlanceAppWidgetReceiver.onUpdate` only does `goAsync { glanceAppWidget.update(...) }`, and the
 * one `updateAppWidget` call site that matters in glance-appwidget 1.1.1 sits at the far end of
 * `SessionManagerImpl.startSession` -> `WorkManager.enqueueUniqueWork(...).result.await()` ->
 * JobScheduler dispatch -> `SessionWorker.doWork` -> `AppWidgetSession.processEmittableTree`.
 * Measured on a Pixel 7 after a reboot, that WorkManager round trip alone cost ~2.4-3.0s — while
 * the data it was waiting on had been sitting on disk the whole time and decrypts in 40-90ms.
 * This class removes that wait: same data, same composable, published from the broadcast.
 *
 * **Why [GlanceAppWidget.compose] and not hand-written RemoteViews.** `compose` is public API that
 * runs the composition once with `shouldPublish = false` and hands back the [android.widget.RemoteViews],
 * with no WorkManager anywhere in it. Because it composes the SAME `GlanceAppWidget`, the frame is
 * pixel-identical to the one Glance's managed session publishes seconds later — so the handoff is
 * invisible, and there is no second renderer to keep in sync with `TaskWidgetDesign.kt`. Both tap
 * mechanisms survive it: `actionRunCallback` is delivered by Glance's manifest-declared
 * `ActionCallbackBroadcastReceiver`, and `actionStartActivity` is a plain PendingIntent. Neither
 * needs a live session.
 *
 * This is an OPTIMISATION ONLY. Every failure path here must leave the widget exactly as it was
 * and let the normal Glance update proceed — hence the blanket `runCatching`.
 */
internal object WidgetFastPaint {
    private const val TAG = "WidgetFastPaint"

    /**
     * Hard ceiling on how long we are willing to block the broadcast thread. A manifest
     * BroadcastReceiver gets ~10s before the system considers it stuck, so this is deliberately far
     * inside the limit: if the composition is slower than this, the fast path is not buying enough
     * to justify holding the thread, and Glance's managed session will paint anyway.
     */
    private const val TIMEOUT_MS = 800L

    /**
     * Composes and publishes [widget] for each id in [appWidgetIds].
     *
     * MUST be called synchronously from `onUpdate`, BEFORE `super.onUpdate(...)`:
     *  - Do NOT call `goAsync()` here. `BroadcastReceiver.goAsync()` nulls out the pending result,
     *    so a second call in the same dispatch returns null — and Glance's `CoroutineBroadcastReceiver`
     *    calls `pendingResult.finish()` in a `finally` with no exception handler, which would turn
     *    that into an uncaught NPE on a background thread.
     *  - Finishing before `super.onUpdate` also keeps this composition and the managed session from
     *    running concurrently, so they cannot race each other allocating layout ids for the same
     *    widget id in Glance's shared `LayoutConfiguration`.
     */
    @SuppressLint("RestrictedApi") // AppWidgetId, exactly as TodayTasksWidgetRefresher already does
    fun publish(
        context: Context,
        widget: GlanceAppWidget,
        kind: WidgetSnapshotKind,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        runCatching {
            val appContext = context.applicationContext
            WidgetTiming.mark("WidgetFastPaint entry (${kind.name}, ${appWidgetIds.size} ids)")

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
            if (!isLocked && !WidgetSnapshotStore(appContext).exists(kind)) {
                WidgetTiming.mark("WidgetFastPaint skipped (${kind.name}: no snapshot yet)")
                return
            }

            val manager = AppWidgetManager.getInstance(appContext)
            runBlocking(Dispatchers.Default) {
                withTimeout(TIMEOUT_MS) {
                    for (appWidgetId in appWidgetIds) {
                        val views = widget.compose(appContext, AppWidgetId(appWidgetId))
                        manager.updateAppWidget(appWidgetId, views)
                    }
                }
            }
            WidgetTiming.mark("WidgetFastPaint published (${kind.name})")
        }.onFailure {
            // Expected and harmless in at least one real case: a before-first-unlock reboot, where
            // filesDir is still credential-encrypted and the snapshot cannot be read at all.
            Log.w(TAG, "Fast paint skipped for ${kind.name}; leaving the managed update to paint", it)
        }
    }
}
