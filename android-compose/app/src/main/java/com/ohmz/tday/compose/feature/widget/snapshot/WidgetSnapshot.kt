package com.ohmz.tday.compose.feature.widget.snapshot

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

internal const val TODAY_TASKS_WIDGET_TASK_LIMIT = 50
internal const val FLOATER_TASKS_WIDGET_TASK_LIMIT = 50
internal const val LIST_TASKS_WIDGET_TASK_LIMIT = 50

/** Local days after the Today snapshot's own that it pre-computes: a week of cover in all. */
internal const val UPCOMING_DAY_COUNT = 6

/** Display cap for each upcoming day. Smaller than the Today cap, matching iOS. */
internal const val UPCOMING_DAY_TASK_LIMIT = 20

@Serializable
internal enum class WidgetSnapshotStatus { SETUP, EMPTY, TASKS }

/** The four priority buckets a widget row can render (see `taskWidgetPriorityRingResource`). */
@Serializable
internal enum class WidgetPriorityRing { HIGH, MEDIUM, LOW, LOWEST }

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
    /**
     * Today only: the same "due that day" selection for each of the next [UPCOMING_DAY_COUNT]
     * local days, so the widget turns over at midnight without a write (see [todayAt]). Defaulted
     * so a snapshot written before this existed still decodes, as covering its own day only.
     */
    val upcomingDays: List<WidgetSnapshotDay> = emptyList(),
)

/** One upcoming local day of [WidgetSnapshot.upcomingDays], `[dayStartEpochMs, dayEndEpochMs)`. */
@Serializable
internal data class WidgetSnapshotDay(
    val dayStartEpochMs: Long,
    val dayEndEpochMs: Long,
    val taskCount: Int,
    val rows: List<WidgetSnapshotRow> = emptyList(),
)

/** What the Today widget renders for one local day. */
internal data class TodayWidgetDay(
    val status: WidgetSnapshotStatus,
    val taskCount: Int,
    val rows: List<WidgetSnapshotRow>,
)

/**
 * The day of this Today snapshot that contains [nowEpochMs]: its own, or one of its
 * [WidgetSnapshot.upcomingDays] — or null when it covers none (it has run out of days, or the
 * clock moved back before it), which must not be rendered as today.
 *
 * Only a cache write with UI changes rebuilds the snapshot, and offline there are none, so it is
 * routinely read on a later day than it was written. Carrying the days ahead lets the widget turn
 * over at midnight from disk alone, instead of waiting on a rebuild in the background that OEM
 * battery managers can defer for hours. A snapshot with no window (SETUP, Floater, per-list)
 * describes no particular day and is always returned as-is. Mirrors iOS's `TodayWidgetDayWindow`.
 */
internal fun WidgetSnapshot.todayAt(nowEpochMs: Long): TodayWidgetDay? {
    val start = dayStartEpochMs
    val end = dayEndEpochMs
    if (start == null || end == null || nowEpochMs in start until end) {
        return TodayWidgetDay(status, taskCount, rows)
    }
    val day = upcomingDays.firstOrNull { nowEpochMs in it.dayStartEpochMs until it.dayEndEpochMs }
        ?: return null
    val dayStatus = if (day.taskCount == 0) WidgetSnapshotStatus.EMPTY else WidgetSnapshotStatus.TASKS
    return TodayWidgetDay(dayStatus, day.taskCount, day.rows)
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
    /**
     * Today/Floater never set this (they pass no `nowEpochMs` to their row mapper, so it stays
     * false — pixel-identical to before this field existed). Only the per-list todo snapshot
     * computes it, at build time against the same [WidgetSnapshot.generatedAtEpochMs] instant.
     */
    val overdue: Boolean = false,
)


/**
 * Whether a snapshot file was last written before today's local midnight: a file stat, not a
 * decrypt, cheap enough for every save and every `onUpdate`. The Today snapshot is built from the
 * day it is written on, so an earlier write means today is (at best) one of its upcoming days.
 */
internal fun wasWrittenBeforeLocalDay(
    lastModifiedEpochMs: Long,
    nowEpochMs: Long,
    zoneId: ZoneId,
): Boolean {
    val todayStart = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        .atStartOfDay(zoneId).toInstant().toEpochMilli()
    return lastModifiedEpochMs < todayStart
}
