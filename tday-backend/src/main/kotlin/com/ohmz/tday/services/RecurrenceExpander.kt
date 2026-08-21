package com.ohmz.tday.services

import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.routes.parseTodoDateTime
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/** One per-occurrence override row (`todo_instances`), already decrypted. */
data class OccurrenceOverride(
    /** Identity of the occurrence being overridden — its *original* date. */
    val instanceDate: LocalDateTime,
    val overriddenTitle: String? = null,
    val overriddenDescription: String? = null,
    val overriddenPriority: String? = null,
    /** Set when this single occurrence was moved to another time. */
    val overriddenDue: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
)

/** The recurrence state a flat [TodoResponse] does not carry. */
data class RecurrenceState(
    /** Occurrences the user cancelled. Identified by their original date. */
    val exdates: List<LocalDateTime> = emptyList(),
    val overrides: List<OccurrenceOverride> = emptyList(),
)

/**
 * Expands a recurring todo template into the concrete occurrences that fall in a window.
 *
 * External API clients cannot do this themselves: `TodoDto` carries `rrule` but not
 * `exdates`, so a cancelled occurrence is invisible outside `/api/export`. Anything
 * that has to answer "what is actually due today" — the MCP tools — has to expand
 * server-side.
 *
 * Behavior mirrors the web client, which is the reference implementation:
 * `tday-web/src/lib/generateTodosFromRRule.ts` (expansion),
 * `getMovedInstances.ts` and `mergeInstanceAndTodo.ts` (overrides). In particular the
 * series recurs in the *user's* timezone, not the todo's stored one, so a 09:00 daily
 * task stays at 09:00 local across a DST boundary.
 */
interface RecurrenceExpander {
    /**
     * @param todo the recurring template (its `due` is the series anchor, a UTC wall clock)
     * @param zone the user's timezone; the series recurs in local time here
     * @param from inclusive window start, as a UTC wall clock
     * @param to inclusive window end, as a UTC wall clock
     * @param limit hard cap on emitted occurrences, so a per-minute rule can't fan out unbounded
     */
    fun expand(
        todo: TodoResponse,
        state: RecurrenceState,
        zone: ZoneId,
        from: LocalDateTime,
        to: LocalDateTime,
        limit: Int = DEFAULT_OCCURRENCE_LIMIT,
    ): List<TodoResponse>

    companion object {
        const val DEFAULT_OCCURRENCE_LIMIT = 500
    }
}

class RecurrenceExpanderImpl : RecurrenceExpander {

    private val logger = LoggerFactory.getLogger(RecurrenceExpanderImpl::class.java)

    override fun expand(
        todo: TodoResponse,
        state: RecurrenceState,
        zone: ZoneId,
        from: LocalDateTime,
        to: LocalDateTime,
        limit: Int,
    ): List<TodoResponse> {
        val rrule = todo.rrule?.trim()?.ifEmpty { null } ?: return emptyList()
        val anchor = parseTodoDateTime(todo.due) ?: return emptyList()
        if (from.isAfter(to)) return emptyList()

        val excluded = state.exdates.mapTo(HashSet()) { it.truncatedToMinute() }
        val overridesByDate = state.overrides.associateBy { it.instanceDate.truncatedToMinute() }

        val occurrences = try {
            generateOccurrences(rrule, anchor, zone, from, to, limit)
        } catch (e: Exception) {
            // A malformed rule is the user's data problem, not a request failure:
            // drop the series rather than failing the whole listing.
            logger.warn("Skipping todo {} — unparseable RRULE: {}", todo.id, e.message)
            return emptyList()
        }

        val emitted = LinkedHashMap<LocalDateTime, TodoResponse>()
        for (occurrence in occurrences) {
            if (occurrence in excluded) continue
            val override = overridesByDate[occurrence]
            val due = override?.overriddenDue?.truncatedToMinute() ?: occurrence
            // A moved occurrence is emitted at its new time, and only if that time
            // still lands in the window.
            if (due < from || due > to) continue
            emitted[occurrence] = todo.applyOccurrence(occurrence, due, override)
        }

        // Occurrences moved *into* the window from outside it: their natural date is
        // out of range so the iterator never produced them, but the user did schedule
        // them here. Mirrors getMovedInstances.ts.
        for (override in state.overrides) {
            val instanceDate = override.instanceDate.truncatedToMinute()
            if (instanceDate in excluded || emitted.containsKey(instanceDate)) continue
            val movedTo = override.overriddenDue?.truncatedToMinute() ?: continue
            if (movedTo < from || movedTo > to) continue
            emitted[instanceDate] = todo.applyOccurrence(instanceDate, movedTo, override)
        }

        return emitted.values.sortedBy { parseTodoDateTime(it.due) }
    }

    private fun generateOccurrences(
        rrule: String,
        anchor: LocalDateTime,
        zone: ZoneId,
        from: LocalDateTime,
        to: LocalDateTime,
        limit: Int,
    ): List<LocalDateTime> {
        val rule = RecurrenceRule(rrule.removePrefix(RRULE_PREFIX), RecurrenceRule.RfcMode.RFC5545_LAX)
        val timeZone = TimeZone.getTimeZone(zone)
        val anchorLocal = anchor.toZone(zone)
        val start = DateTime(
            timeZone,
            anchorLocal.year,
            anchorLocal.monthValue - 1,
            anchorLocal.dayOfMonth,
            anchorLocal.hour,
            anchorLocal.minute,
            0,
        )

        val iterator = rule.iterator(start)
        val fromMillis = from.toEpochMillis()
        val toMillis = to.toEpochMillis()
        iterator.fastForward(DateTime(timeZone, fromMillis))

        val results = ArrayList<LocalDateTime>()
        while (iterator.hasNext() && results.size < limit) {
            val millis = iterator.nextDateTime().timestamp
            if (millis > toMillis) break
            if (millis >= fromMillis) results.add(millis.toUtcWallClock())
        }
        return results
    }

    private fun TodoResponse.applyOccurrence(
        instanceDate: LocalDateTime,
        due: LocalDateTime,
        override: OccurrenceOverride?,
    ): TodoResponse = copy(
        title = override?.overriddenTitle ?: title,
        description = override?.overriddenDescription ?: description,
        priority = override?.overriddenPriority ?: priority,
        due = due.toString(),
        instanceDate = instanceDate.toString(),
        completed = override?.completedAt != null,
    )

    private companion object {
        const val RRULE_PREFIX = "RRULE:"

        fun LocalDateTime.truncatedToMinute(): LocalDateTime = withSecond(0).withNano(0)

        fun LocalDateTime.toEpochMillis(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

        /** Read a UTC wall clock as an instant and re-render it in [zone]'s local time. */
        fun LocalDateTime.toZone(zone: ZoneId): LocalDateTime =
            atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()

        fun Long.toUtcWallClock(): LocalDateTime =
            LocalDateTime.ofEpochSecond(this / 1000, 0, ZoneOffset.UTC)
    }
}
