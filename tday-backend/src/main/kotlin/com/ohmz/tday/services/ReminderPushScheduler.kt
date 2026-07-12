package com.ohmz.tday.services

import com.ohmz.tday.db.tables.CronLogs
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.util.CuidGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The backend's first background job. Once a minute it looks for scheduled todos
 * whose due time has just arrived and sends the owning user a web-push notification
 * via [PushNotificationService.sendToUser] — which, until now, nothing ever called,
 * so web users who enabled push never actually received a reminder.
 *
 * Design notes:
 * - Reminders are otherwise a client-only concept: the mobile apps schedule local
 *   notifications with a per-task lead time, but that offset is never persisted on
 *   the server. The only time the backend knows about is [Todos.due], so a server
 *   push fires at the due instant. This is exactly the reminder mechanism for web,
 *   which has no local scheduler and relies on VAPID push.
 * - The scan window is overlap-safe. Each tick processes todos whose due fell in
 *   `(lastRun, now]`, where `lastRun` is the timestamp of the previous successful
 *   run recorded in [CronLogs]. Consecutive, non-overlapping windows mean every due
 *   todo is notified in exactly one tick, so no per-notification dedup table is
 *   needed and a slow or missed tick can't skip a reminder. After a restart the gap
 *   is caught up, bounded by [MAX_LOOKBACK] so a long outage can't fire an avalanche.
 * - Recurring todos (`rrule != null`) are excluded: their [Todos.due] is the series
 *   anchor, not the next occurrence, so a naive due-window match would misfire.
 *   Server push for recurring reminders needs an RFC 5545 occurrence expander and is
 *   left as a follow-up.
 */
class ReminderPushScheduler(
    private val pushService: PushNotificationService,
) {
    private val logger = LoggerFactory.getLogger(ReminderPushScheduler::class.java)

    suspend fun run() {
        if (!pushService.isConfigured()) {
            logger.info("Reminder push scheduler idle: VAPID keys are not configured")
            return
        }
        logger.info("Reminder push scheduler started (interval={}s)", TICK_INTERVAL.seconds)
        while (currentCoroutineContext().isActive) {
            try {
                tick()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn("Reminder push tick failed: {}", e.message, e)
                runCatching { writeRunLog(LocalDateTime.now(ZoneOffset.UTC), success = false, log = "error=${e.message}") }
            }
            delay(TICK_INTERVAL.toMillis())
        }
    }

    private suspend fun tick() {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val previous = lastSuccessfulRun() ?: now.minus(TICK_INTERVAL)
        val windowStart = maxOf(previous, now.minus(MAX_LOOKBACK))

        // Non-recurring, incomplete todos whose due entered (windowStart, now].
        val dueTodos = newSuspendedTransaction(Dispatchers.IO) {
            Todos.selectAll()
                .where {
                    (Todos.completed eq false) and
                        Todos.rrule.isNull() and
                        (Todos.due greater windowStart) and
                        (Todos.due lessEq now)
                }
                .map { Triple(it[Todos.id], it[Todos.userID], it[Todos.title]) }
        }

        var sent = 0
        for ((todoId, userId, title) in dueTodos) {
            pushService.sendToUser(
                userId = userId,
                title = "Task due",
                body = title.ifBlank { "You have a task due" },
                url = "/",
                todoId = todoId,
            ).onRight { sent++ }
        }

        writeRunLog(now, success = true, log = "due=${dueTodos.size} sent=$sent")
    }

    private suspend fun lastSuccessfulRun(): LocalDateTime? =
        newSuspendedTransaction(Dispatchers.IO) {
            CronLogs.selectAll()
                .where { (CronLogs.log like "$JOB_LABEL%") and (CronLogs.success eq true) }
                .orderBy(CronLogs.runAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.get(CronLogs.runAt)
        }

    private suspend fun writeRunLog(runAt: LocalDateTime, success: Boolean, log: String) {
        newSuspendedTransaction(Dispatchers.IO) {
            CronLogs.insert {
                it[CronLogs.id] = CuidGenerator.newCuid()
                it[CronLogs.runAt] = runAt
                it[CronLogs.success] = success
                it[CronLogs.log] = "$JOB_LABEL $log"
            }
        }
    }

    private companion object {
        val TICK_INTERVAL: Duration = Duration.ofSeconds(60)
        val MAX_LOOKBACK: Duration = Duration.ofHours(24)
        const val JOB_LABEL = "reminder-push"
    }
}
