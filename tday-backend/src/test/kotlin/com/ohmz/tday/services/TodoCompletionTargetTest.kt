package com.ohmz.tday.services

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `completeTodo`/`uncompleteTodo` are Exposed-on-Postgres and `:tday-backend:test` has no
 * database (a `pgEnum` column and a `timestamp[]` one rule out an in-memory stand-in), so
 * the part that decided wrongly — *what* a request completes — is a pure function and is
 * covered here directly. Both services route through it, so a completion and its undo can
 * only ever address the same row and the same history key.
 *
 * The regression it pins: the service branched `if (rrule == null) { mark the row } else
 * if (instanceDate != null) { write the occurrence }`. A recurring todo completed without
 * an `instanceDate` matched neither arm, so it got a `CompletedTodos` history row and no
 * state change at all. That is not an edge case — `toTodoResponse()` never sets
 * `instanceDate` and both listing queries emit recurring *templates*, so it is the shape
 * every client sends for every recurring task (`docs/design/bulk-selection.md` §1).
 */
class TodoCompletionTargetTest {

    private val occurrence = LocalDateTime.of(2026, 5, 4, 9, 0)

    @Test
    fun `a one-off todo completes the row itself`() {
        assertEquals(CompletionTarget.Series, completionTargetFor(rrule = null, instanceDate = null))
    }

    @Test
    fun `a recurring todo with an occurrence completes that occurrence`() {
        assertEquals(
            CompletionTarget.Occurrence(occurrence),
            completionTargetFor(rrule = "FREQ=DAILY", instanceDate = occurrence),
        )
    }

    @Test
    fun `a recurring todo without an occurrence completes the series`() {
        // The bug: this resolved to nothing, so the row stayed visible in every client
        // while Completed history gained an entry for a completion that never happened.
        // Completing the series is the exact inverse of what uncomplete already does for
        // a null instanceDate, so the two round-trip.
        assertEquals(CompletionTarget.Series, completionTargetFor(rrule = "FREQ=DAILY", instanceDate = null))
    }

    @Test
    fun `a one-off todo ignores an instanceDate the caller sent anyway`() {
        // A one-off has no occurrences. Honouring the date would key the history row by it
        // while `todos.completed` said otherwise, and uncomplete could then only undo one
        // of the two. MCP's completeTask forwards occurrenceDate for any todo handle, so
        // this pair is reachable.
        assertEquals(CompletionTarget.Series, completionTargetFor(rrule = null, instanceDate = occurrence))
    }

    @Test
    fun `the history row is keyed by the target, never by the raw request`() {
        assertEquals(null, completionTargetFor(rrule = null, instanceDate = occurrence).historyInstanceDate)
        assertEquals(null, completionTargetFor(rrule = "FREQ=DAILY", instanceDate = null).historyInstanceDate)
        assertEquals(
            occurrence,
            completionTargetFor(rrule = "FREQ=DAILY", instanceDate = occurrence).historyInstanceDate,
        )
    }

    @Test
    fun `every request resolves to a target that is actually marked complete`() {
        // The `CompletedTodos` insert is gated on this: history is only written for a
        // target the same transaction also marks complete, so the two cannot diverge.
        val everyRequest = listOf(null, "FREQ=DAILY").flatMap { rrule ->
            listOf(null, occurrence).map { date -> rrule to date }
        }
        val resolved = everyRequest.map { (rrule, date) -> completionTargetFor(rrule, date) }

        assertEquals(
            listOf(
                CompletionTarget.Series, // one-off, no occurrence
                CompletionTarget.Series, // one-off, occurrence ignored
                CompletionTarget.Series, // recurring series, no occurrence — was a no-op
                CompletionTarget.Occurrence(occurrence),
            ),
            resolved,
        )
    }
}
