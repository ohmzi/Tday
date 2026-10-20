package com.ohmz.tday.compose.feature.widget

import android.content.Context
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotStore

/**
 * The three size receivers a [ListTasksWidget] instance can be placed as — the same
 * Small/Medium/Large split as Today/Floater.
 *
 * Named `ListWidget*`, not the `ListTasksWidget*` the first List widget used: that widget was
 * removed rather than migrated, and a provider class that no longer exists is how Android takes
 * its placed instances off the home screen. [WidgetSnapshotWriter] clears whatever selection and
 * snapshot those orphaned ids left behind.
 */
// `open`, not `abstract`: every member below is already concrete — the three subclasses exist
// only because the manifest/AppWidgetManager need a DISTINCT class per size receiver, not because
// this base has anything left for them to implement.
open class BaseListWidgetReceiver : TaskWidgetReceiver() {
    override val kind: WidgetInstanceKind get() = WidgetInstanceKind.LIST

    /**
     * The host removed these instances for good — no picker is coming back to reuse their stored
     * list selection or snapshot, so both are deleted. SharedPreferences + a
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

class ListWidgetSmallReceiver : BaseListWidgetReceiver()

class ListWidgetReceiver : BaseListWidgetReceiver()

class ListWidgetLargeReceiver : BaseListWidgetReceiver()
