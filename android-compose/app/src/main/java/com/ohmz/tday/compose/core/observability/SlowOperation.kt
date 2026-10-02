package com.ohmz.tday.compose.core.observability

import android.content.SharedPreferences
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message

/** The operations whose slowness is worth a report, and how long each may take before it is one. */
enum class SlowOperationKind(val id: String, val thresholdMs: Long) {
    COLD_START("cold_start", 5_000L),
    FIRST_DATA_READY("first_data_ready", 8_000L),
    DB_OPEN_MIGRATE("db_open_migrate", 3_000L),
    CACHE_HYDRATE("cache_hydrate", 2_000L),
    SYNC_REPLAY("sync_replay", 15_000L),
    API_CALL("api_call", 10_000L),
    WIDGET_REFRESH("widget_refresh", 10_000L),
    REMINDER_RESCHEDULE("reminder_reschedule", 10_000L),
}

/** When each operation was last reported, kept across processes so a slow start is one report a day. */
interface SlowOperationLedger {
    fun lastReportedMs(operation: String): Long?

    fun markReported(operation: String, nowMs: Long)
}

class SharedPreferencesSlowOperationLedger(private val preferences: SharedPreferences) : SlowOperationLedger {
    override fun lastReportedMs(operation: String): Long? =
        if (preferences.contains(operation)) preferences.getLong(operation, 0L) else null

    override fun markReported(operation: String, nowMs: Long) {
        preferences.edit().putLong(operation, nowMs).apply()
    }
}

/**
 * "This took too long" as a failure-class event rather than a performance trace, so it still
 * reaches Sentry with tracing off and counts as the failure the consent card promised: one
 * warning per slow operation, fixed message, grouped by operation.
 *
 * Quiet by construction: one report per operation per process, five per process, and a day's
 * cooldown per operation that survives a restart. A phone that is slow once should produce one
 * data point, not one per launch.
 *
 * Silent until the reporter runs: with no consent Sentry is not started, and then nothing is
 * measured into a report, claimed against a limit or written to the ledger either.
 *
 * The injected pieces are the clock, the ledger and the sink, so the limits are unit tests.
 */
class SlowOperation(
    private val ledger: SlowOperationLedger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val isReporting: () -> Boolean = { Sentry.isEnabled() },
    private val capture: (SentryEvent) -> Unit = { Sentry.captureEvent(it) },
) {
    private val reportedThisProcess = mutableSetOf<SlowOperationKind>()

    /** Reports [operation] if [durationMs] is over its threshold and no limit applies. Never throws. */
    fun report(operation: SlowOperationKind, durationMs: Long, attrs: Map<String, Any?> = emptyMap()) {
        if (durationMs <= operation.thresholdMs || !isReporting()) return
        val now = clock()
        if (!claim(operation, now)) return

        val event = SentryEvent().apply {
            message = Message().apply { message = MESSAGE }
            level = SentryLevel.WARNING
            fingerprints = listOf(MESSAGE, operation.id)
            setTag("operation", operation.id)
            setTag("duration_bucket", durationBucket(durationMs))
            setExtra("duration_ms", durationMs)
            setExtra("threshold_ms", operation.thresholdMs)
            attrs.forEach { (key, value) -> setExtra(key, TdayTelemetry.safeDataValue(key, value)) }
        }
        runCatching { capture(event) }
    }

    /** Runs [block] and reports it if it was slow, including when it throws. */
    inline fun <T> measure(operation: SlowOperationKind, block: () -> T): T {
        val startedAt = now()
        try {
            return block()
        } finally {
            report(operation, now() - startedAt)
        }
    }

    @PublishedApi
    internal fun now(): Long = clock()

    // Under one lock so two threads finishing the same slow operation cannot both report it.
    @Synchronized
    private fun claim(operation: SlowOperationKind, now: Long): Boolean {
        if (operation in reportedThisProcess) return false
        if (reportedThisProcess.size >= MAX_PER_PROCESS) return false
        val last = ledger.lastReportedMs(operation.id)
        if (last != null && now - last < COOLDOWN_MS) return false
        reportedThisProcess += operation
        ledger.markReported(operation.id, now)
        return true
    }

    companion object {
        private const val MESSAGE = "slow_operation"
        private const val MAX_PER_PROCESS = 5
        private const val COOLDOWN_MS = 24L * 60 * 60 * 1000

        fun durationBucket(durationMs: Long): String = when {
            durationMs < 5_000L -> "<5s"
            durationMs < 10_000L -> "5-10s"
            durationMs < 30_000L -> "10-30s"
            durationMs < 60_000L -> "30-60s"
            else -> ">60s"
        }
    }
}
