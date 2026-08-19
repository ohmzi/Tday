package com.ohmz.tday.compose.feature.widget.snapshot

import kotlinx.serialization.Serializable

/**
 * Bump ONLY for a genuinely incompatible change (a field removed or retyped). New fields must be
 * nullable or defaulted so an older writer's file still decodes. [WidgetSnapshotStore] rejects a
 * file whose [WidgetSnapshot.schemaVersion] is greater than this — an app downgrade after a newer
 * build wrote the file — treating it as missing rather than risking a half-decoded row.
 */
internal const val WIDGET_SNAPSHOT_SCHEMA_VERSION = 1

internal const val TODAY_TASKS_WIDGET_TASK_LIMIT = 50
internal const val FLOATER_TASKS_WIDGET_TASK_LIMIT = 50

@Serializable
internal enum class WidgetSnapshotStatus { SETUP, EMPTY, TASKS }

/** The three priority buckets a widget row can render (see `taskWidgetPriorityRingResource`). */
@Serializable
internal enum class WidgetPriorityRing { HIGH, MEDIUM, LOW }

/**
 * The exact render payload a widget needs — nothing more. Written by the app process (which has
 * Hilt and the encrypted Room cache) and read directly by a widget's `provideGlance` (which has
 * neither): opening the SQLCipher cache costs ~9.5s cold on a Pixel 7, dominated by SQLCipher's
 * default PBKDF2 KDF, and the renderer only ever needs a status, a count, and up to
 * [TODAY_TASKS_WIDGET_TASK_LIMIT] rows.
 *
 * Two fields are deliberately NOT baked in, unlike the iOS twin ([WidgetSnapshotStore]'s KDoc
 * has the full rationale):
 * - No title. This app ships a per-app language override, so a title baked at write time would
 *   freeze the widget header at whatever locale was active on the last cache write.
 * - No preformatted trailing time. `DateFormat.getTimeInstance(SHORT)` depends on locale AND the
 *   system 12/24-hour setting; baking it would leave stale formatting until the next cache write.
 *   [WidgetSnapshotRow.dueEpochMs] is formatted on read instead.
 *
 * [WidgetSnapshotRow.priorityRing] IS baked, because the bucketing is locale-independent.
 */
@Serializable
internal data class WidgetSnapshot(
    val schemaVersion: Int = WIDGET_SNAPSHOT_SCHEMA_VERSION,
    val generatedAtEpochMs: Long,
    val status: WidgetSnapshotStatus,
    val taskCount: Int,
    /** Today only: the local-day window this snapshot was built for. Null for Floater. */
    val dayStartEpochMs: Long? = null,
    val dayEndEpochMs: Long? = null,
    val rows: List<WidgetSnapshotRow> = emptyList(),
) {
    /** How many tasks past the builder's row cap were dropped from [rows]. Not currently rendered. */
    val overflowCount: Int
        get() = (taskCount - rows.size).coerceAtLeast(0)

    /**
     * Displayed-content equality, ignoring [generatedAtEpochMs] — used by [WidgetSnapshotWriter]
     * to skip an encrypt-and-write when nothing the widget shows actually changed. Never used to
     * skip a repaint: `TodayTasksWidgetRefresher`'s renders stay unconditional on purpose.
     */
    fun hasSameContent(other: WidgetSnapshot): Boolean {
        return status == other.status &&
            taskCount == other.taskCount &&
            dayStartEpochMs == other.dayStartEpochMs &&
            dayEndEpochMs == other.dayEndEpochMs &&
            rows == other.rows
    }

    /**
     * False for a file written by a NEWER build than this one understands (an app downgrade).
     * Pure and standalone so the rejection rule is unit-testable without Android/Keystore —
     * [WidgetSnapshotStore.read] treats an unsupported snapshot identically to a missing file.
     */
    fun isSupported(): Boolean = schemaVersion <= WIDGET_SNAPSHOT_SCHEMA_VERSION
}

@Serializable
internal data class WidgetSnapshotRow(
    /** The cached-record id — what a tap's complete action resolves back against. */
    val id: String,
    /** Precomputed `id.hashCode().toLong()`: the LazyColumn item key. */
    val key: Long,
    val title: String,
    val priorityRing: WidgetPriorityRing,
    val dueEpochMs: Long? = null,
    val description: String? = null,
)
