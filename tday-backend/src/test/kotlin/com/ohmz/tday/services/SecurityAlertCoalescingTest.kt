package com.ohmz.tday.services

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The alert-storm defence, exercised directly.
 *
 * The property that matters is the last test: however many events an attack produces, the number
 * of pushes is bounded by elapsed time divided by the cooldown.
 */
class SecurityAlertCoalescingTest {
    private val start: LocalDateTime = LocalDateTime.of(2026, 8, 5, 12, 0, 0)
    private val cooldown = 900

    @Test
    fun `the first event of a type alerts immediately`() {
        val decision = alertDecision(
            lastSentAt = null,
            pendingCount = 0,
            now = start,
            cooldownSec = cooldown,
            minOccurrences = 1,
        )
        assertEquals(AlertDecision.Dispatch(suppressedCount = 0), decision)
    }

    @Test
    fun `events inside the cooldown are folded in instead of pushed`() {
        val decision = alertDecision(
            lastSentAt = start,
            pendingCount = 4,
            now = start.plusMinutes(3),
            cooldownSec = cooldown,
            minOccurrences = 1,
        )
        assertEquals(AlertDecision.Coalesce, decision)
    }

    @Test
    fun `the next alert after a cooldown reports how many were suppressed`() {
        val decision = alertDecision(
            lastSentAt = start,
            pendingCount = 12,
            now = start.plusMinutes(16),
            cooldownSec = cooldown,
            minOccurrences = 1,
        )
        assertEquals(AlertDecision.Dispatch(suppressedCount = 12), decision)
    }

    @Test
    fun `the cooldown boundary is inclusive`() {
        assertEquals(
            AlertDecision.Coalesce,
            alertDecision(start, 0, start.plusSeconds(899), cooldown, 1),
        )
        assertEquals(
            AlertDecision.Dispatch(suppressedCount = 0),
            alertDecision(start, 0, start.plusSeconds(900), cooldown, 1),
        )
    }

    @Test
    fun `a signal that only matters when repeated stays quiet until it repeats`() {
        // Anomalies: a new device on its own is routine, three is worth a look.
        assertEquals(AlertDecision.Coalesce, alertDecision(null, 0, start, cooldown, minOccurrences = 3))
        assertEquals(AlertDecision.Coalesce, alertDecision(null, 1, start, cooldown, minOccurrences = 3))
        assertEquals(
            AlertDecision.Dispatch(suppressedCount = 2),
            alertDecision(null, 2, start, cooldown, minOccurrences = 3),
        )
    }

    @Test
    fun `suppressed counts are named in the body only when there are any`() {
        assertEquals("Blocked one source.", alertBody("Blocked one source.", 0))
        assertEquals("Blocked one source.", alertBody("Blocked one source.", -1))
        assertEquals(
            "Blocked one source. — 12 further attempts suppressed",
            alertBody("Blocked one source.", 12),
        )
    }

    @Test
    fun `ten thousand events in an hour produce at most one push per cooldown`() {
        // Replays the real state machine: dispatch resets pending and stamps lastSentAt.
        var lastSentAt: LocalDateTime? = null
        var pending = 0
        var dispatches = 0

        val events = 10_000
        // One event every 360ms, i.e. an hour of sustained attack traffic.
        repeat(events) { index ->
            val now = start.plusNanos(index * 360_000_000L)
            when (val decision = alertDecision(lastSentAt, pending, now, cooldown, minOccurrences = 1)) {
                is AlertDecision.Dispatch -> {
                    dispatches++
                    assertEquals(pending, decision.suppressedCount)
                    pending = 0
                    lastSentAt = now
                }
                AlertDecision.Coalesce -> pending++
            }
        }

        // ~3600s of traffic at a 900s cooldown: the opening alert plus one per elapsed window.
        assertEquals(4, dispatches)
        assertTrue(dispatches < events / 1000)
    }
}
