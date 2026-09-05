package com.ohmz.tday.compose.feature.widget.snapshot

import android.content.Context
import com.ohmz.tday.compose.core.data.AppDataMode
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.feature.widget.WidgetListSelectionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
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
 * refreshers it triggers via [WidgetSnapshotSignal.bump]. Callers already gate on whether there's
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

    /** Rebuilds and writes every snapshot. Returns true when any file was actually written. */
    fun write(state: OfflineSyncState): Boolean {
        val workspaceConfigured = secureConfigStore.getAppDataMode() != AppDataMode.UNSET
        val today = buildTodayWidgetSnapshot(state, workspaceConfigured)
        val floater = buildFloaterWidgetSnapshot(state, workspaceConfigured)

        val todayWritten = store.write(WidgetSnapshotKind.TODAY, today)
        val floaterWritten = store.write(WidgetSnapshotKind.FLOATER, floater)
        val listWritten = writeListSnapshots(state, workspaceConfigured)
        val changed = todayWritten || floaterWritten || listWritten
        if (changed) WidgetSnapshotSignal.bump()
        return changed
    }

    /**
     * Writes only when no file exists on disk yet. Exists for
     * `OfflineCacheManager.saveOfflineStateBlocking`'s no-op early return (the very first save
     * call this process makes, when nothing actually changed from what was already persisted) —
     * without this, a first run with no changes would never seed the file, and the widget would
     * sit in LOADING until an unrelated write happened to land.
     */
    fun ensureSeeded(state: OfflineSyncState) {
        val needsToday = !store.exists(WidgetSnapshotKind.TODAY)
        val needsFloater = !store.exists(WidgetSnapshotKind.FLOATER)
        val needsAnyList = listSelectionStore.configuredWidgetIds().any { !store.existsList(it) }
        if (needsToday || needsFloater || needsAnyList) write(state)
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
}
