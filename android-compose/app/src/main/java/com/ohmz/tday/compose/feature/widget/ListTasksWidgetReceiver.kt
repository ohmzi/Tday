package com.ohmz.tday.compose.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore

/**
 * The three size receivers a [ListTasksWidget] instance can be placed as — same
 * Small/default/Large split as Today/Floater, sharing the one [ListTasksWidget] Glance class.
 *
 * Deliberately does NOT call [WidgetFastPaint] the way `BaseTodayTasksWidgetReceiver`/
 * `BaseFloaterTasksWidgetReceiver` do on `onUpdate`. That optimisation is keyed by
 * `WidgetSnapshotKind` — one shared file per KIND, checked once per process — and gates on
 * "does a snapshot exist for this kind at all". A list-widget snapshot is per `appWidgetId`
 * instead, so the same shortcut would need enumerating which of possibly-several ids in this
 * broadcast are configured and have a snapshot before it could publish anything, which is most of
 * the work `WidgetFastPaint` exists to skip. Fast paint only shaves ~2.4-3s off the FIRST
 * `onUpdate` after a reboot; skipping it here just means a list widget repaints on the same
 * timeline every OTHER widget in this app used before that optimisation existed — a deliberate
 * scope cut for this PR, not a correctness gap.
 */
abstract class BaseListTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ListTasksWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TodayTasksWidgetPreviewPublisher.publish(context)
    }

    /**
     * The host removed these instances for good — no configuration screen is coming back to
     * reuse their stored list selection or snapshot, so both are deleted. `onDeleted` is not a
     * broadcast this class needs `goAsync()` for: SharedPreferences + a couple of `File.delete()`
     * calls stay well inside the broadcast's execution budget.
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val appContext = context.applicationContext
        val selectionStore = WidgetListSelectionStore(appContext)
        val snapshotStore = WidgetSnapshotStore(appContext)
        for (appWidgetId in appWidgetIds) {
            selectionStore.clearSelection(appWidgetId)
            snapshotStore.deleteList(appWidgetId)
        }
    }
}

class ListTasksWidgetSmallReceiver : BaseListTasksWidgetReceiver()

class ListTasksWidgetReceiver : BaseListTasksWidgetReceiver()

class ListTasksWidgetLargeReceiver : BaseListTasksWidgetReceiver()
