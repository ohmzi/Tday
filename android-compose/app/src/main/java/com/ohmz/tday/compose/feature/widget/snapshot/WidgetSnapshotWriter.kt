package com.ohmz.tday.compose.feature.widget.snapshot

import android.content.Context
import com.ohmz.tday.compose.core.data.AppDataMode
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.SecureConfigStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The write side of the widget snapshot: builds both widgets' render payloads from the offline
 * cache and encrypts them to disk. Called from every chokepoint that can change what a widget
 * shows — see the call sites inside `OfflineCacheManager` — so this is the single place that
 * keeps `feature/widget/snapshot/widget-{today,floater}-snapshot.json` current.
 *
 * Every call re-encrypts and rewrites both files unconditionally, matching
 * `TodayTasksWidgetRefresher`'s documented "reliability over micro-optimization" stance for the
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

    /** Rebuilds and writes both snapshots. Returns true when either file was actually written. */
    fun write(state: OfflineSyncState): Boolean {
        val workspaceConfigured = secureConfigStore.getAppDataMode() != AppDataMode.UNSET
        val today = buildTodayWidgetSnapshot(state, workspaceConfigured)
        val floater = buildFloaterWidgetSnapshot(state, workspaceConfigured)

        val todayWritten = store.write(WidgetSnapshotKind.TODAY, today)
        val floaterWritten = store.write(WidgetSnapshotKind.FLOATER, floater)
        val changed = todayWritten || floaterWritten
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
        if (needsToday || needsFloater) write(state)
    }
}
