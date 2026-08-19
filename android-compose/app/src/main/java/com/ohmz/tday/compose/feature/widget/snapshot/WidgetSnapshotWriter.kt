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
 * Skips the encrypt-and-write when the displayed content is unchanged from the last write this
 * process made (`WidgetSnapshot.hasSameContent`, via an in-memory memo — never a re-read-and-
 * decode of the file, which would defeat the point). This gates only the disk write:
 * `WidgetSnapshotSignal.bump()` still fires whenever [write] is called with a change, and the
 * refreshers `OfflineCacheManager` calls alongside this stay unconditional, matching
 * `TodayTasksWidgetRefresher`'s documented "reliability over micro-optimization" stance.
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

    @Volatile
    private var lastToday: WidgetSnapshot? = null

    @Volatile
    private var lastFloater: WidgetSnapshot? = null

    /** Rebuilds and writes both snapshots. Returns true when either file was actually written. */
    fun write(state: OfflineSyncState): Boolean {
        val workspaceConfigured = secureConfigStore.getAppDataMode() != AppDataMode.UNSET
        val today = buildTodayWidgetSnapshot(state, workspaceConfigured)
        val floater = buildFloaterWidgetSnapshot(state, workspaceConfigured)

        var changed = false
        if (lastToday?.hasSameContent(today) != true &&
            store.write(WidgetSnapshotKind.TODAY, today)
        ) {
            lastToday = today
            changed = true
        }
        if (lastFloater?.hasSameContent(floater) != true &&
            store.write(WidgetSnapshotKind.FLOATER, floater)
        ) {
            lastFloater = floater
            changed = true
        }
        if (changed) WidgetSnapshotSignal.bump()
        return changed
    }

    /**
     * Writes only when a snapshot has never been written this process AND no file exists on
     * disk. Exists for `OfflineCacheManager.saveOfflineStateBlocking`'s no-op early return (the
     * very first save call this process makes, when nothing actually changed from what was
     * already persisted) — without this, a first run with no changes would never seed the file,
     * and the widget would sit in LOADING until an unrelated write happened to land.
     */
    fun ensureSeeded(state: OfflineSyncState) {
        val needsToday = lastToday == null && !store.exists(WidgetSnapshotKind.TODAY)
        val needsFloater = lastFloater == null && !store.exists(WidgetSnapshotKind.FLOATER)
        if (needsToday || needsFloater) write(state)
    }
}
