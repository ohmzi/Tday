package com.ohmz.tday.compose.feature.widget.snapshot

import android.content.Context
import com.ohmz.tday.compose.core.data.AppDataMode
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.feature.widget.WidgetListSelectionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The write side of the widget snapshot: builds every widget's render payload from the offline
 * cache and encrypts it to disk. Called from every chokepoint that can change what a widget
 * shows — see the call sites inside `OfflineCacheManager` — so this is the single place that
 * keeps `feature/widget/snapshot/widget-{today,floater}-snapshot.json` AND every configured
 * `widget-list-snapshot-<appWidgetId>.json` current.
 *
 * Every call re-encrypts and rewrites every file unconditionally, matching
 * `WidgetRefresher`'s documented "reliability over micro-optimization" stance for the
 * repaint every caller requests after it. Callers already gate on whether there's
 * anything to write (`OfflineCacheManager`'s `hasUiChanges` check) before reaching this class.
 *
 * Public, not internal: it is a constructor parameter of `OfflineCacheManager` and
 * `WidgetHydrateWorker`, both public Hilt-injected classes, and Kotlin does not allow a public
 * member to expose an internal type.
 */
@Singleton
class WidgetSnapshotWriter @Inject constructor(
    @ApplicationContext context: Context,
    private val secureConfigStore: SecureConfigStore,
    json: Json,
) {
    private val store = WidgetSnapshotStore(context, json)
    private val listSelectionStore = WidgetListSelectionStore(context)

    // Plain prefs: one boolean describing the snapshots, nothing a user could read content from.
    private val meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)

    /** Rebuilds and writes every snapshot. Returns true when any file was actually written. */
    fun write(state: OfflineSyncState): Boolean {
        val workspaceConfigured = isWorkspaceConfigured()
        val today = buildTodayWidgetSnapshot(state, workspaceConfigured)
        val floater = buildFloaterWidgetSnapshot(state, workspaceConfigured)

        val todayWritten = store.write(WidgetSnapshotKind.TODAY, today)
        val floaterWritten = store.write(WidgetSnapshotKind.FLOATER, floater)
        val listWritten = writeListSnapshots(state, workspaceConfigured)
        // Recorded only once both fixed snapshots carry it, so a half-failed write is retried by
        // the next ensureCurrent instead of being mistaken for current.
        if (todayWritten && floaterWritten) {
            meta.edit().putBoolean(KEY_WRITTEN_WORKSPACE_CONFIGURED, workspaceConfigured).apply()
        }
        return todayWritten || floaterWritten || listWritten
    }

    /**
     * Writes only when a file is missing, or the Today snapshot was written on an earlier local
     * day. For `OfflineCacheManager.saveOfflineStateBlocking`'s saves that change nothing the
     * widget shows — the normal case for a sync on a quiet day. Without the missing check a first
     * run with no changes never seeds the file; without the day check nothing rewrites the Today
     * snapshot until the task data happens to change, so the days it carries ahead (see
     * `WidgetSnapshot.todayAt`) would run down to nothing instead of being topped up every day the
     * app runs. Returns true when it wrote.
     *
     * It also writes when the workspace mode changed since the last write. The SETUP state is
     * baked into the snapshot from `AppDataMode`, not from task data, so setting up a workspace
     * that has no tasks yet changes nothing [write]'s callers compare — without this check the
     * widget kept saying "Set up your workspace" after the user had done exactly that.
     */
    fun ensureCurrent(state: OfflineSyncState): Boolean {
        val needsToday = store.lastWrittenEpochMs(WidgetSnapshotKind.TODAY)?.let {
            wasWrittenBeforeLocalDay(it, System.currentTimeMillis(), ZoneId.systemDefault())
        } ?: true
        val needsFloater = !store.exists(WidgetSnapshotKind.FLOATER)
        val needsAnyList = listSelectionStore.configuredWidgetIds().any { !store.existsList(it) }
        val needsWorkspace = writtenForWorkspaceConfigured() != isWorkspaceConfigured()
        return (needsToday || needsFloater || needsAnyList || needsWorkspace) && write(state)
    }

    private fun isWorkspaceConfigured(): Boolean = secureConfigStore.getAppDataMode() != AppDataMode.UNSET

    /** The workspace flag the fixed snapshots were last written with; null before the first. */
    private fun writtenForWorkspaceConfigured(): Boolean? =
        if (meta.contains(KEY_WRITTEN_WORKSPACE_CONFIGURED)) {
            meta.getBoolean(KEY_WRITTEN_WORKSPACE_CONFIGURED, false)
        } else {
            null
        }

    /**
     * One snapshot per configured `appWidgetId`, each scoped to whatever list THAT instance was
     * pointed at — the per-instance analogue of the two lines above. An instance whose list was
     * since deleted still gets a snapshot (an empty one: `state.todos`/`state.floaters` simply has
     * no rows left for that id), rather than being skipped, so the widget shows "no tasks" instead
     * of freezing on its last content.
     */
    private fun writeListSnapshots(state: OfflineSyncState, workspaceConfigured: Boolean): Boolean {
        var changed = false
        for (appWidgetId in listSelectionStore.configuredWidgetIds()) {
            val selection = listSelectionStore.selectionFor(appWidgetId) ?: continue
            val snapshot = buildListWidgetSnapshot(
                state = state,
                listId = selection.listId,
                listType = selection.listType,
                workspaceConfigured = workspaceConfigured,
            )
            if (store.writeList(appWidgetId, snapshot)) changed = true
        }
        return changed
    }

    private companion object {
        const val META_PREFS = "tday_widget_snapshot_meta"
        const val KEY_WRITTEN_WORKSPACE_CONFIGURED = "written_workspace_configured"
    }
}
