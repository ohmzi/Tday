package com.ohmz.tday.services

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.db.tables.AbuseBlocks
import com.ohmz.tday.db.tables.AuthSignals
import com.ohmz.tday.db.tables.AuthThrottles
import com.ohmz.tday.db.tables.CronLogs
import com.ohmz.tday.db.tables.EventLogs
import com.ohmz.tday.db.tables.SecurityAlerts
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.db.util.CuidGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Ages out the security bookkeeping tables.
 *
 * These grow on every request an attacker makes and nothing ever deleted from them, so a slow
 * unauthenticated drip against any throttled endpoint filled the disk indefinitely — refusing
 * traffic cost the server more than serving it.
 *
 * Two rules here are load-bearing rather than cosmetic:
 *
 * 1. `auththrottle` rows carry live lockouts. Deleting one that is still locked would hand an
 *    attacker a lockout reset, so an active `lockUntil` is always preserved regardless of age.
 * 2. `cronlog` holds the reminder scheduler's "last successful run" bookmark. Trimming it too
 *    aggressively makes that scheduler fall back to a one-tick lookback and silently drop
 *    reminders, so its retention is floored in [AppConfig].
 */
class RetentionScheduler(
    private val config: AppConfig,
) {
    private val logger = LoggerFactory.getLogger(RetentionScheduler::class.java)

    suspend fun run() {
        logger.info(
            "Retention scheduler started (interval={}h, dryRun={}, eventLog={}d, authThrottle={}d, authSignal={}d, cronLog={}d)",
            TICK_INTERVAL.toHours(),
            config.retentionDryRun,
            config.retentionEventLogDays,
            config.retentionAuthThrottleDays,
            config.retentionAuthSignalDays,
            config.retentionCronLogDays,
        )
        while (currentCoroutineContext().isActive) {
            try {
                tick()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn("Retention tick failed: {}", e.message, e)
                runCatching { writeRunLog(success = false, log = "error=${e.message}") }
            }
            delay(TICK_INTERVAL.toMillis())
        }
    }

    private suspend fun tick() {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val summary = mutableListOf<String>()

        purge("eventLog", config.retentionEventLogDays, summary) { cutoff ->
            EventLogs.deleteWhere { EventLogs.capturedTime less cutoff }
        }
        purge("authThrottle", config.retentionAuthThrottleDays, summary) { cutoff ->
            // Never drop a bucket that is still serving a lockout.
            AuthThrottles.deleteWhere {
                (AuthThrottles.updatedAt less cutoff) and
                    (AuthThrottles.lockUntil.isNull() or (AuthThrottles.lockUntil less now))
            }
        }
        purge("authSignal", config.retentionAuthSignalDays, summary) { cutoff ->
            AuthSignals.deleteWhere { AuthSignals.lastSeenAt less cutoff }
        }
        purge("cronLog", config.retentionCronLogDays, summary) { cutoff ->
            CronLogs.deleteWhere { CronLogs.runAt less cutoff }
        }
        purge("securityAlert", config.retentionEventLogDays, summary) { cutoff ->
            // Dispatch history only. security_alert_state is never swept: it is one row per alert
            // type, and dropping it would reset a cooldown and let an alert storm restart.
            SecurityAlerts.deleteWhere { SecurityAlerts.createdAt less cutoff }
        }
        purge("abuseBlock", config.retentionAuthThrottleDays, summary) { cutoff ->
            // Same rule as authThrottle above: a row still serving a block is never dropped,
            // because deleting it would hand the abuser an early release.
            AbuseBlocks.deleteWhere {
                (AbuseBlocks.updatedAt less cutoff) and
                    (AbuseBlocks.blockedUntil.isNull() or (AbuseBlocks.blockedUntil less now))
            }
        }

        // Anyone can raise pendingAdminReset for any username without signing in, so a request
        // nobody acted on stops flagging the account once it has clearly gone stale.
        val resetCutoff = now.minusDays(ADMIN_RESET_REQUEST_TTL_DAYS)
        val staleResets = if (config.retentionDryRun) {
            newSuspendedTransaction(Dispatchers.IO) {
                Users.selectAll().where {
                    (Users.pendingAdminReset eq true) and (Users.adminResetRequestedAt less resetCutoff)
                }.count().toInt()
            }
        } else {
            newSuspendedTransaction(Dispatchers.IO) {
                Users.update({
                    (Users.pendingAdminReset eq true) and (Users.adminResetRequestedAt less resetCutoff)
                }) {
                    it[Users.pendingAdminReset] = false
                    it[Users.adminResetRequestedAt] = null
                }
            }
        }
        if (staleResets > 0) summary += "staleAdminResets=$staleResets"

        if (summary.isNotEmpty()) {
            writeRunLog(success = true, log = summary.joinToString(" "))
        }
    }

    /** Runs one table's purge, or counts what it would delete when [AppConfig.retentionDryRun]. */
    private suspend fun purge(
        label: String,
        retentionDays: Int,
        summary: MutableList<String>,
        delete: (LocalDateTime) -> Int,
    ) {
        if (retentionDays <= 0) return // 0 disables retention for this table.
        val cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(retentionDays.toLong())

        if (config.retentionDryRun) {
            // Deliberately not counted separately: a dry run should not double the query cost.
            logger.info("[retention:dry-run] {} would purge rows older than {}", label, cutoff)
            summary += "$label=dry-run"
            return
        }

        var removed = 0
        // Batched so a large backlog never holds one long transaction.
        while (true) {
            val batch = newSuspendedTransaction(Dispatchers.IO) { delete(cutoff) }
            removed += batch
            if (batch < BATCH_LIMIT) break
        }
        if (removed > 0) {
            summary += "$label=$removed"
            logger.info("[retention] purged {} rows from {}", removed, label)
        }
    }

    private suspend fun writeRunLog(success: Boolean, log: String) {
        newSuspendedTransaction(Dispatchers.IO) {
            CronLogs.insert {
                it[CronLogs.id] = CuidGenerator.newCuid()
                it[CronLogs.runAt] = LocalDateTime.now(ZoneOffset.UTC)
                it[CronLogs.success] = success
                it[CronLogs.log] = "$JOB_LABEL $log"
            }
        }
    }

    private companion object {
        val TICK_INTERVAL: Duration = Duration.ofHours(6)
        const val BATCH_LIMIT = 5000
        const val JOB_LABEL = "retention"
    }
}
