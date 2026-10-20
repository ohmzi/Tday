package com.ohmz.tday.compose.core.observability

import io.sentry.SentryEvent
import io.sentry.SentryLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SlowOperationTest {
    private var now = 100L * HOUR
    private val ledger = FakeLedger()
    private val captured = mutableListOf<SentryEvent>()
    private var reporting = true
    private val slow = SlowOperation(ledger, clock = { now }, isReporting = { reporting }, capture = { captured += it })

    @Test
    fun `the operations and thresholds are the ones the contract names`() {
        assertEquals(
            mapOf(
                "cold_start" to 5_000L,
                "first_data_ready" to 8_000L,
                "db_open_migrate" to 3_000L,
                "cache_hydrate" to 2_000L,
                "sync_replay" to 15_000L,
                "api_call" to 10_000L,
                "widget_refresh" to 10_000L,
                "reminder_reschedule" to 10_000L,
            ),
            SlowOperationKind.entries.associate { it.id to it.thresholdMs },
        )
    }

    @Test
    fun `an operation that stays within its threshold is not reported`() {
        slow.report(SlowOperationKind.COLD_START, durationMs = 5_000L)

        assertEquals(0, captured.size)
        assertNull(ledger.lastReportedMs("cold_start"))
    }

    @Test
    fun `nothing is reported, claimed or stored while the reporter is not running`() {
        reporting = false

        slow.report(SlowOperationKind.COLD_START, durationMs = 30_000L)
        reporting = true
        slow.report(SlowOperationKind.COLD_START, durationMs = 30_000L)

        // The first slow start, made with consent off, must not use up the day's report or leave a
        // timestamp behind: it is the second one, with the reporter running, that is sent.
        assertEquals(1, captured.size)
        assertEquals(now, ledger.lastReportedMs("cold_start"))
    }

    @Test
    fun `a slow operation becomes one warning with a fixed message and a stable fingerprint`() {
        slow.report(SlowOperationKind.COLD_START, durationMs = 7_250L)

        val event = captured.single()
        assertEquals("slow_operation", event.message!!.message)
        assertEquals(SentryLevel.WARNING, event.level)
        assertEquals(listOf("slow_operation", "cold_start"), event.fingerprints)
        assertEquals("cold_start", event.getTag("operation"))
        assertEquals("5-10s", event.getTag("duration_bucket"))
        assertEquals(7_250L, event.getExtra("duration_ms"))
        assertEquals(5_000L, event.getExtra("threshold_ms"))
    }

    @Test
    fun `buckets the duration the way the dashboard groups it`() {
        assertEquals("<5s", SlowOperation.durationBucket(4_999L))
        assertEquals("5-10s", SlowOperation.durationBucket(5_000L))
        assertEquals("5-10s", SlowOperation.durationBucket(9_999L))
        assertEquals("10-30s", SlowOperation.durationBucket(10_000L))
        assertEquals("10-30s", SlowOperation.durationBucket(29_999L))
        assertEquals("30-60s", SlowOperation.durationBucket(30_000L))
        assertEquals("30-60s", SlowOperation.durationBucket(59_999L))
        assertEquals(">60s", SlowOperation.durationBucket(60_000L))
    }

    @Test
    fun `reports an operation at most once per process`() {
        slow.report(SlowOperationKind.SYNC_REPLAY, durationMs = 20_000L)
        slow.report(SlowOperationKind.SYNC_REPLAY, durationMs = 40_000L)

        assertEquals(1, captured.size)
    }

    @Test
    fun `reports at most five operations per process`() {
        SlowOperationKind.entries.forEach { slow.report(it, durationMs = 100_000L) }

        assertEquals(5, captured.size)
    }

    @Test
    fun `stays quiet for a day after a report, across processes`() {
        ledger.markReported("cold_start", now - 23 * HOUR)

        slow.report(SlowOperationKind.COLD_START, durationMs = 9_000L)

        assertEquals(0, captured.size)
    }

    @Test
    fun `speaks again once the day is up and records when it did`() {
        ledger.markReported("cold_start", now - 25 * HOUR)

        slow.report(SlowOperationKind.COLD_START, durationMs = 9_000L)

        assertEquals(1, captured.size)
        assertEquals(now, ledger.lastReportedMs("cold_start"))
    }

    @Test
    fun `puts the extra attributes through the same redaction as every other extra`() {
        slow.report(
            SlowOperationKind.API_CALL,
            durationMs = 12_000L,
            attrs = mapOf("route" to "https://tasks.example.com/api/list/123?token=1", "email" to "a@b.example"),
        )

        val event = captured.single()
        assertEquals("/api/list/:id", event.getExtra("route"))
        assertEquals("redacted", event.getExtra("email"))
    }

    @Test
    fun `measure times the block with the injected clock and returns its value`() {
        val result = slow.measure(SlowOperationKind.CACHE_HYDRATE) {
            now += 2_600L
            "hydrated"
        }

        assertEquals("hydrated", result)
        assertEquals(2_600L, captured.single().getExtra("duration_ms"))
    }

    @Test
    fun `measure reports a slow block that throws and still throws`() {
        assertThrows(IllegalStateException::class.java) {
            slow.measure(SlowOperationKind.DB_OPEN_MIGRATE) {
                now += 4_000L
                error("migration failed")
            }
        }

        assertEquals(1, captured.size)
    }

    @Test
    fun `a failing sink never breaks the operation being measured`() {
        val broken = SlowOperation(ledger, clock = { now }, isReporting = { true }, capture = { error("sentry is closed") })

        broken.report(SlowOperationKind.COLD_START, durationMs = 9_000L)
    }

    @Test
    fun `the shared preferences ledger round trips per operation`() {
        val persisted = SharedPreferencesSlowOperationLedger(FakeSharedPreferences())

        assertNull(persisted.lastReportedMs("cold_start"))
        persisted.markReported("cold_start", 1234L)

        assertEquals(1234L, persisted.lastReportedMs("cold_start"))
        assertNull(persisted.lastReportedMs("sync_replay"))
    }

    private class FakeLedger : SlowOperationLedger {
        private val reported = mutableMapOf<String, Long>()

        override fun lastReportedMs(operation: String): Long? = reported[operation]

        override fun markReported(operation: String, nowMs: Long) {
            reported[operation] = nowMs
        }
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
