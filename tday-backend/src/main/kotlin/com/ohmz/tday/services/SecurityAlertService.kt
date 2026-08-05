package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.db.enums.ApprovalStatus
import com.ohmz.tday.db.enums.UserRole
import com.ohmz.tday.db.tables.SecurityAlertStates
import com.ohmz.tday.db.tables.SecurityAlerts
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.domain.requireAdminAccess
import com.ohmz.tday.models.response.SecurityAlertResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The high-signal events worth waking the owner for. Deliberately NOT every 429. */
enum class SecurityAlertType(val wire: String, val title: String) {
    ABUSE_BLOCK("abuse_block_applied", "Abuse block applied"),
    LOCKOUT_BURST("auth_alert_lockout_burst", "Repeated sign-in lockouts"),
    IP_CONCENTRATION("auth_alert_ip_concentration", "Failed sign-ins concentrated on one source"),
    ADMIN_RESET_REQUESTED("admin_reset_requested", "Password reset requested"),
    SIGNAL_ANOMALY("auth_signal_anomaly", "Sign-in from an unfamiliar origin"),
}

/** What to do with one incoming event, given the type's current coalescing state. */
internal sealed interface AlertDecision {
    /** Send now. [suppressedCount] events were folded in while the cooldown was running. */
    data class Dispatch(val suppressedCount: Int) : AlertDecision

    /** Stay quiet; just increment the pending counter. */
    data object Coalesce : AlertDecision
}

/**
 * Decides whether an event alerts now or is folded into the next one. Pure — no DB, no clock —
 * because this is the whole of the alert-storm defence and this repo has no test database.
 *
 * Two independent gates, both of which must pass:
 * - [minOccurrences]: some signals (a single sign-in from a new IP) are only interesting when
 *   they repeat, so the first N-1 occurrences never page anyone.
 * - [cooldownSec] since [lastSentAt]: at most one push per type per cooldown, no matter how many
 *   events arrive. This is the hard bound — an attacker generating a million events still cannot
 *   produce more than one notification per type per window.
 */
internal fun alertDecision(
    lastSentAt: LocalDateTime?,
    pendingCount: Int,
    now: LocalDateTime,
    cooldownSec: Int,
    minOccurrences: Int,
): AlertDecision {
    // pendingCount counts events already folded in; this call is one more.
    val occurrences = pendingCount + 1
    if (occurrences < minOccurrences) return AlertDecision.Coalesce
    if (lastSentAt != null && Duration.between(lastSentAt, now).seconds < cooldownSec) {
        return AlertDecision.Coalesce
    }
    return AlertDecision.Dispatch(suppressedCount = pendingCount)
}

/** Push body for a dispatched alert, naming how many events it stands for. */
internal fun alertBody(detail: String, suppressedCount: Int): String =
    if (suppressedCount <= 0) detail else "$detail — $suppressedCount further attempts suppressed"

interface SecurityAlertService {
    /**
     * Report a qualifying security event.
     *
     * Fire-and-forget by contract: never suspends the caller, never throws. Alerting sits on
     * auth paths, and a failure to notify must never turn into a failure to authenticate.
     */
    fun raise(type: SecurityAlertType, detail: String)

    suspend fun listRecent(admin: AuthenticatedUser): Either<AppError, List<SecurityAlertResponse>>
}

class SecurityAlertServiceImpl(
    private val config: AppConfig,
    private val pushService: PushNotificationService,
) : SecurityAlertService {
    private val logger = LoggerFactory.getLogger(SecurityAlertServiceImpl::class.java)

    // Detached, like PushNotificationService.notifyDataChanged: a request path hands the event
    // over and returns immediately.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun raise(type: SecurityAlertType, detail: String) {
        scope.launch {
            runCatching { dispatch(type, detail) }
                .onFailure { logger.warn("Security alert {} failed: {}", type.wire, it.message) }
        }
    }

    /** Visible for the alerting path itself; [raise] is what request handlers call. */
    internal suspend fun dispatch(type: SecurityAlertType, detail: String) {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val minOccurrences = if (type == SecurityAlertType.SIGNAL_ANOMALY) {
            config.securityAlertAnomalyMinCount
        } else {
            1
        }

        // Read-modify-write of the cooldown row under a row lock: concurrent attack traffic must
        // not race two dispatches through the same window.
        val decision = newSuspendedTransaction(Dispatchers.IO) {
            val row = SecurityAlertStates.selectAll()
                .where { SecurityAlertStates.alertType eq type.wire }
                .forUpdate()
                .firstOrNull()

            val lastSentAt = row?.get(SecurityAlertStates.lastSentAt)
            val pendingCount = row?.get(SecurityAlertStates.pendingCount) ?: 0
            val verdict = alertDecision(lastSentAt, pendingCount, now, config.securityAlertCooldownSec, minOccurrences)

            val nextPending = if (verdict is AlertDecision.Dispatch) 0 else pendingCount + 1
            val nextSentAt = if (verdict is AlertDecision.Dispatch) now else lastSentAt

            if (row == null) {
                SecurityAlertStates.insert {
                    it[alertType] = type.wire
                    it[SecurityAlertStates.lastSentAt] = nextSentAt
                    it[SecurityAlertStates.pendingCount] = nextPending
                    it[updatedAt] = now
                }
            } else {
                SecurityAlertStates.update({ SecurityAlertStates.alertType eq type.wire }) {
                    it[SecurityAlertStates.lastSentAt] = nextSentAt
                    it[SecurityAlertStates.pendingCount] = nextPending
                    it[updatedAt] = now
                }
            }
            verdict
        }

        val dispatchDecision = decision as? AlertDecision.Dispatch ?: return
        val body = alertBody(detail, dispatchDecision.suppressedCount)
        val delivered = deliver(type, body)

        newSuspendedTransaction(Dispatchers.IO) {
            SecurityAlerts.insert {
                it[id] = CuidGenerator.newCuid()
                it[alertType] = type.wire
                it[SecurityAlerts.detail] = body.take(MAX_DETAIL_CHARS)
                it[suppressedCount] = dispatchDecision.suppressedCount
                it[pushed] = delivered
                it[createdAt] = now
            }
        }
    }

    /**
     * Pushes to every approved admin. Returns whether at least one send reported success.
     *
     * Note the ceiling on what this can honestly report: [PushNotificationService.sendToUser]
     * returns Right for an admin with no push subscription at all, so `true` means "handed to the
     * push service without error", not "a device buzzed". False is precise — no approved admin
     * exists, or every send failed.
     *
     * A missing VAPID key is not an error: UnifiedPush targets need none, and either way the
     * history row above is written so the owner can still see what happened.
     */
    private suspend fun deliver(type: SecurityAlertType, body: String): Boolean {
        val admins = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll()
                .where { (Users.role eq UserRole.ADMIN) and (Users.approvalStatus eq ApprovalStatus.APPROVED) }
                .map { it[Users.id] }
        }
        if (admins.isEmpty()) return false
        if (!pushService.isConfigured()) {
            logger.info("Security alert {}: VAPID not configured; web-push targets will be skipped", type.wire)
        }

        var delivered = false
        for (adminId in admins) {
            runCatching {
                pushService.sendToUser(
                    userId = adminId,
                    title = type.title,
                    body = body,
                    url = ALERT_URL,
                )
            }.onSuccess { result ->
                // sendToUser reports failure as Either.Left, not as a throw — counting a Left as
                // a delivery would make `pushed` claim the admin was told when they were not.
                result.fold(
                    { error -> logger.warn("Security alert push to admin failed: {}", error.message) },
                    { delivered = true },
                )
            }.onFailure { logger.warn("Security alert push to admin threw: {}", it.message) }
        }
        return delivered
    }

    override suspend fun listRecent(admin: AuthenticatedUser): Either<AppError, List<SecurityAlertResponse>> = either {
        admin.requireAdminAccess().bind()
        newSuspendedTransaction(Dispatchers.IO) {
            SecurityAlerts.selectAll()
                .orderBy(SecurityAlerts.createdAt to SortOrder.DESC)
                .limit(HISTORY_LIMIT)
                .map { row ->
                    SecurityAlertResponse(
                        id = row[SecurityAlerts.id],
                        type = row[SecurityAlerts.alertType],
                        detail = row[SecurityAlerts.detail],
                        suppressedCount = row[SecurityAlerts.suppressedCount],
                        pushed = row[SecurityAlerts.pushed],
                        createdAt = row[SecurityAlerts.createdAt].toString(),
                    )
                }
        }
    }

    private companion object {
        const val ALERT_URL = "/"
        const val HISTORY_LIMIT = 50
        const val MAX_DETAIL_CHARS = 500
    }
}
