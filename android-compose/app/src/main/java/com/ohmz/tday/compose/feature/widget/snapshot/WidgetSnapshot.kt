package com.ohmz.tday.compose.feature.widget.snapshot

import kotlinx.serialization.Serializable

internal const val TODAY_TASKS_WIDGET_TASK_LIMIT = 50
internal const val FLOATER_TASKS_WIDGET_TASK_LIMIT = 50
internal const val LIST_TASKS_WIDGET_TASK_LIMIT = 50

@Serializable
internal enum class WidgetSnapshotStatus { SETUP, EMPTY, TASKS }

/** The three priority buckets a widget row can render (see `taskWidgetPriorityRingResource`). */
@Serializable
internal enum class WidgetPriorityRing { HIGH, MEDIUM, LOW }

/**
 * Which shape a per-list widget instance renders in — chosen once, at configuration time, by
 * which kind of list the user picked (a todo-list vs. a floater-list). Deliberately only two
 * values: the content-shape decision for this feature is that a widget always matches whichever
 * list TYPE was picked (due-date-shaped for a todo-list, undated-shaped for a floater-list), the
 * same two shapes the fixed Today/Floater widgets already use — never a third shape.
 */
@Serializable
internal enum class WidgetListType { TODO, FLOATER }

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
    val generatedAtEpochMs: Long,
    val status: WidgetSnapshotStatus,
    val taskCount: Int,
    /** Today only: the local-day window this snapshot was built for. Null for Floater. */
    val dayStartEpochMs: Long? = null,
    val dayEndEpochMs: Long? = null,
    val rows: List<WidgetSnapshotRow> = emptyList(),
)

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
    /**
     * Today/Floater never set this (they pass no `nowEpochMs` to their row mapper, so it stays
     * false — pixel-identical to before this field existed). Only the per-list todo snapshot
     * computes it, at build time against the same [WidgetSnapshot.generatedAtEpochMs] instant.
     */
    val overdue: Boolean = false,
)
