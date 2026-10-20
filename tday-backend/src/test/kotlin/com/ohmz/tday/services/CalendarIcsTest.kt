package com.ohmz.tday.services

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarIcsTest {
    private val stamp = LocalDateTime.of(2026, 1, 2, 3, 4, 5)

    @Test
    fun `single timed event renders a well-formed VEVENT`() {
        val ics = CalendarIcs.document(
            listOf(
                IcsEvent(
                    uid = "t1@tday",
                    timeZone = "America/New_York",
                    start = LocalDateTime.of(2026, 3, 1, 9, 0, 0),
                    rrule = null,
                    exdates = emptyList(),
                    summary = "Call the bank",
                    description = "Ask about the fee",
                ),
            ),
            stamp,
        )

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
        assertContains(ics, "VERSION:2.0\r\n")
        assertContains(ics, "UID:t1@tday\r\n")
        assertContains(ics, "DTSTAMP:20260102T030405Z\r\n")
        assertContains(ics, "DTSTART;TZID=America/New_York:20260301T090000\r\n")
        assertContains(ics, "SUMMARY:Call the bank\r\n")
        assertContains(ics, "DESCRIPTION:Ask about the fee\r\n")
        // Non-recurring events carry no RRULE / EXDATE / RECURRENCE-ID.
        assertFalse(ics.contains("RRULE"))
        assertFalse(ics.contains("EXDATE"))
        assertFalse(ics.contains("RECURRENCE-ID"))
        // Every line is CRLF-terminated.
        assertFalse(ics.replace("\r\n", "").contains('\n'))
    }

    @Test
    fun `recurring event emits RRULE and EXDATE`() {
        val ics = CalendarIcs.document(
            listOf(
                IcsEvent(
                    uid = "t2@tday",
                    timeZone = "UTC",
                    start = LocalDateTime.of(2026, 3, 1, 8, 0, 0),
                    rrule = "FREQ=WEEKLY;BYDAY=MO",
                    exdates = listOf(
                        LocalDateTime.of(2026, 3, 8, 8, 0, 0),
                        LocalDateTime.of(2026, 3, 15, 8, 0, 0),
                    ),
                    summary = "Standup",
                    description = null,
                ),
            ),
            stamp,
        )

        assertContains(ics, "RRULE:FREQ=WEEKLY;BYDAY=MO\r\n")
        assertContains(ics, "EXDATE;TZID=UTC:20260308T080000,20260315T080000\r\n")
        // A null description is omitted entirely.
        assertFalse(ics.contains("DESCRIPTION"))
    }

    @Test
    fun `override occurrence renders a RECURRENCE-ID VEVENT`() {
        val ics = CalendarIcs.document(
            listOf(
                IcsEvent(
                    uid = "t3@tday",
                    timeZone = "UTC",
                    start = LocalDateTime.of(2026, 3, 1, 8, 0, 0),
                    rrule = "FREQ=DAILY",
                    exdates = emptyList(),
                    summary = "Series",
                    description = null,
                ),
                IcsEvent(
                    uid = "t3@tday",
                    timeZone = "UTC",
                    start = LocalDateTime.of(2026, 3, 3, 15, 0, 0),
                    rrule = null,
                    exdates = emptyList(),
                    summary = "Moved occurrence",
                    description = null,
                    recurrenceId = LocalDateTime.of(2026, 3, 3, 8, 0, 0),
                ),
            ),
            stamp,
        )

        assertContains(ics, "RECURRENCE-ID;TZID=UTC:20260303T080000\r\n")
        assertContains(ics, "DTSTART;TZID=UTC:20260303T150000\r\n")
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
    }

    @Test
    fun `text values are escaped per RFC 5545`() {
        val ics = CalendarIcs.document(
            listOf(
                IcsEvent(
                    uid = "t4@tday",
                    timeZone = "UTC",
                    start = LocalDateTime.of(2026, 3, 1, 8, 0, 0),
                    rrule = null,
                    exdates = emptyList(),
                    summary = "Pay; rent, now\nurgent",
                    description = null,
                ),
            ),
            stamp,
        )

        assertContains(ics, "SUMMARY:Pay\\; rent\\, now\\nurgent\r\n")
    }

    @Test
    fun `long lines are folded to 75 chars with a leading space`() {
        val longSummary = "x".repeat(200)
        val ics = CalendarIcs.document(
            listOf(
                IcsEvent(
                    uid = "t5@tday",
                    timeZone = "UTC",
                    start = LocalDateTime.of(2026, 3, 1, 8, 0, 0),
                    rrule = null,
                    exdates = emptyList(),
                    summary = longSummary,
                    description = null,
                ),
            ),
            stamp,
        )

        for (line in ics.split("\r\n")) {
            assertTrue(line.length <= 75, "line exceeds 75 chars: '$line'")
        }
        // Folded continuation lines begin with a single space.
        assertTrue(ics.contains("\r\n x"))
    }
}
