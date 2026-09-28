package com.ohmz.tday.compose.feature.widget

import android.content.Context
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore

/**
 * The three size receivers a [ListTasksWidget] instance can be placed as — same
 * Small/default/Large split as Today/Floater.
 */
// `open`, not `abstract`: every member below is already concrete — the three subclasses exist
// only because the manifest/AppWidgetManager need a DISTINCT class per size receiver, not because
// this base has anything left for them to implement.
open class BaseListTasksWidgetReceiver : TaskWidgetReceiver() {
    override val kind: WidgetInstanceKind get() = WidgetInstanceKind.LIST

    /**
     * The host removed these instances for good — no configuration screen is coming back to
     * reuse their stored list selection or snapshot, so both are deleted. SharedPreferences + a
     * couple of `File.delete()` calls stay well inside the broadcast's execution budget.
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
