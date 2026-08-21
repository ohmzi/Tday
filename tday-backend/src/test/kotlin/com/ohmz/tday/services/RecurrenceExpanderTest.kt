package com.ohmz.tday.services

import com.ohmz.tday.models.response.TodoResponse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Expansion has to match what the web client shows, because an external API client
 * cannot do it itself — `TodoDto` carries `rrule` but not `exdates`.
 */
class RecurrenceExpanderTest {

    private val expander = RecurrenceExpanderImpl()
    private val utc = ZoneId.of("UTC")

    private fun todo(
        due: LocalDateTime,
        rrule: String?,
        id: String = "todo_1",
        title: String = "Standup",
    ) = TodoResponse(id = id, title = title, due = due.toString(), rrule = rrule)

    private fun dues(results: List<TodoResponse>) = results.map { it.due }

    @Test
    fun `a daily rule fills the window`() {
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 8, 20, 9, 0), "RRULE:FREQ=DAILY;INTERVAL=1"),
            state = RecurrenceState(),
            zone = utc,
            from = LocalDateTime.of(2026, 8, 20, 0, 0),
            to = LocalDateTime.of(2026, 8, 23, 23, 59),
        )

        assertEquals(
            listOf("2026-08-20T09:00", "2026-08-21T09:00", "2026-08-22T09:00", "2026-08-23T09:00"),
            dues(result),
        )
    }

    @Test
    fun `a weekly rule only lands on its weekdays`() {
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 8, 17, 9, 0), "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE"),
            state = RecurrenceState(),
            zone = utc,
            from = LocalDateTime.of(2026, 8, 17, 0, 0),
            to = LocalDateTime.of(2026, 8, 23, 23, 59),
        )

        // 2026-08-17 is a Monday.
        assertEquals(listOf("2026-08-17T09:00", "2026-08-19T09:00"), dues(result))
    }

    @Test
    fun `an exdate removes that occurrence`() {
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 8, 20, 9, 0), "RRULE:FREQ=DAILY"),
            state = RecurrenceState(exdates = listOf(LocalDateTime.of(2026, 8, 21, 9, 0))),
            zone = utc,
            from = LocalDateTime.of(2026, 8, 20, 0, 0),
            to = LocalDateTime.of(2026, 8, 22, 23, 59),
        )

        assertEquals(listOf("2026-08-20T09:00", "2026-08-22T09:00"), dues(result))
    }

    @Test
    fun `an override renames and completes a single occurrence`() {
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 8, 20, 9, 0), "RRULE:FREQ=DAILY"),
            state = RecurrenceState(
                overrides = listOf(
                    OccurrenceOverride(
                        instanceDate = LocalDateTime.of(2026, 8, 21, 9, 0),
                        overriddenTitle = "Long standup",
                        overriddenPriority = "High",
                        completedAt = LocalDateTime.of(2026, 8, 21, 9, 30),
                    ),
                ),
            ),
            zone = utc,
            from = LocalDateTime.of(2026, 8, 20, 0, 0),
            to = LocalDateTime.of(2026, 8, 22, 23, 59),
        )

        val overridden = result.single { it.due == "2026-08-21T09:00" }
        assertEquals("Long standup", overridden.title)
        assertEquals("High", overridden.priority)
        assertTrue(overridden.completed)
        assertEquals("Standup", result.first { it.due == "2026-08-20T09:00" }.title)
    }

    @Test
    fun `a moved occurrence is reported at its new time`() {
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 8, 20, 9, 0), "RRULE:FREQ=DAILY"),
            state = RecurrenceState(
                overrides = listOf(
                    OccurrenceOverride(
                        instanceDate = LocalDateTime.of(2026, 8, 21, 9, 0),
                        overriddenDue = LocalDateTime.of(2026, 8, 21, 16, 0),
                    ),
                ),
            ),
            zone = utc,
            from = LocalDateTime.of(2026, 8, 20, 0, 0),
            to = LocalDateTime.of(2026, 8, 21, 23, 59),
        )

        assertEquals(listOf("2026-08-20T09:00", "2026-08-21T16:00"), dues(result))
        assertEquals("2026-08-21T09:00", result.last().instanceDate, "occurrence identity stays the original date")
    }

    @Test
    fun `an occurrence moved into the window is included`() {
        // The natural occurrence is outside the window; the user moved it inside.
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 8, 1, 9, 0), "RRULE:FREQ=MONTHLY"),
            state = RecurrenceState(
                overrides = listOf(
                    OccurrenceOverride(
                        instanceDate = LocalDateTime.of(2026, 9, 1, 9, 0),
                        overriddenDue = LocalDateTime.of(2026, 8, 20, 9, 0),
                    ),
                ),
            ),
            zone = utc,
            from = LocalDateTime.of(2026, 8, 15, 0, 0),
            to = LocalDateTime.of(2026, 8, 25, 23, 59),
        )

        assertEquals(listOf("2026-08-20T09:00"), dues(result))
    }

    @Test
    fun `an occurrence moved out of the window is dropped`() {
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 8, 20, 9, 0), "RRULE:FREQ=DAILY"),
            state = RecurrenceState(
                overrides = listOf(
                    OccurrenceOverride(
                        instanceDate = LocalDateTime.of(2026, 8, 21, 9, 0),
                        overriddenDue = LocalDateTime.of(2026, 9, 30, 9, 0),
                    ),
                ),
            ),
            zone = utc,
            from = LocalDateTime.of(2026, 8, 20, 0, 0),
            to = LocalDateTime.of(2026, 8, 21, 23, 59),
        )

        assertEquals(listOf("2026-08-20T09:00"), dues(result))
    }

    @Test
    fun `the series recurs in local time across a DST change`() {
        // 09:00 London: BST (UTC+1) before 25 October 2026, GMT after.
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 10, 23, 8, 0), "RRULE:FREQ=DAILY"),
            state = RecurrenceState(),
            zone = ZoneId.of("Europe/London"),
            from = LocalDateTime.of(2026, 10, 23, 0, 0),
            to = LocalDateTime.of(2026, 10, 26, 23, 59),
        )

        assertEquals(
            listOf("2026-10-23T08:00", "2026-10-24T08:00", "2026-10-25T09:00", "2026-10-26T09:00"),
            dues(result),
        )
    }

    @Test
    fun `the occurrence limit caps a runaway rule`() {
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 8, 20, 0, 0), "RRULE:FREQ=MINUTELY"),
            state = RecurrenceState(),
            zone = utc,
            from = LocalDateTime.of(2026, 8, 20, 0, 0),
            to = LocalDateTime.of(2026, 8, 30, 0, 0),
            limit = 25,
        )

        assertEquals(25, result.size)
    }

    @Test
    fun `an unparseable rule drops the series instead of failing the listing`() {
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 8, 20, 9, 0), "RRULE:FREQ=NONSENSE"),
            state = RecurrenceState(),
            zone = utc,
            from = LocalDateTime.of(2026, 8, 20, 0, 0),
            to = LocalDateTime.of(2026, 8, 22, 23, 59),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a non-recurring todo expands to nothing`() {
        val result = expander.expand(
            todo = todo(LocalDateTime.of(2026, 8, 20, 9, 0), rrule = null),
            state = RecurrenceState(),
            zone = utc,
            from = LocalDateTime.of(2026, 8, 20, 0, 0),
            to = LocalDateTime.of(2026, 8, 22, 23, 59),
        )

        assertTrue(result.isEmpty())
    }
}
