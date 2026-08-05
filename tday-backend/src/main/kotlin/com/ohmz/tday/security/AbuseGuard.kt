package com.ohmz.tday.security

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.db.tables.AbuseBlocks
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.domain.requireAdminAccess
import com.ohmz.tday.domain.respondRateLimit
import com.ohmz.tday.models.response.AbuseBlockResponse
import com.ohmz.tday.plugins.authUser
import com.ohmz.tday.services.SecurityAlertService
import com.ohmz.tday.services.SecurityAlertType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.ceil

/**
 * A blockable PATH, never the whole app.
 *
 * Keeping these apart is a safety property, not tidiness: someone flooding registrations must not
 * be able to take the owner's own sign-in path down with them, and vice versa.
 */
enum class AbuseScope { register, auth }

/** Why a block was applied. Recorded and shown to the admin; never disclosed to the blocked caller. */
object AbuseReason {
    const val REGISTER_VIOLATIONS = "register_violations"
    const val REGISTER_PENDING_FLOOD = "register_pending_flood"
    const val AUTH_LOCKOUTS = "auth_lockouts"
}

/** Which counter an event feeds. */
enum class AbuseSignal { registerViolation, pendingSignup, authLockout }

data class AbuseBlockVerdict(val blocked: Boolean, val retryAfterSeconds: Int = 0) {
    companion object {
        val allowed = AbuseBlockVerdict(blocked = false)
    }
}

internal data class AbuseCounters(val signalCount: Int, val pendingSignups: Int)

internal data class AbuseThresholds(
    val registerViolationMax: Int,
    val registerPendingMax: Int,
    val authLockoutMax: Int,
)

/**
 * Has this subject earned a block on [scope]? Returns the reason, or null to keep serving them.
 *
 * Pure so the whole trip decision is testable without Postgres.
 *
 * The pending-signup rule is the load-bearing one for registration. Raw velocity only catches a
 * fast script — a patient one paces itself under the hourly cap forever. What no legitimate user
 * ever produces is a pile of accounts from one source that nobody ever approves: a real person
 * registers ONCE and then waits for the owner. So "N unapproved accounts from here in a day"
 * separates abuse from use far better than "N requests per hour" does, and it cannot be evaded by
 * simply slowing down.
 */
internal fun abuseTripReason(
    scope: AbuseScope,
    counters: AbuseCounters,
    thresholds: AbuseThresholds,
): String? = when (scope) {
    AbuseScope.register -> when {
        counters.pendingSignups > thresholds.registerPendingMax -> AbuseReason.REGISTER_PENDING_FLOOD
        counters.signalCount >= thresholds.registerViolationMax -> AbuseReason.REGISTER_VIOLATIONS
        else -> null
    }
    // Lockout EVENTS, not individual failures: one forgetful session produces exactly one, so it
    // takes several separate episodes to trip this.
    AbuseScope.auth -> if (counters.signalCount >= thresholds.authLockoutMax) AbuseReason.AUTH_LOCKOUTS else null
}

/**
 * Block length for the [strikes]th block: [baseSec], then x24 per strike, capped at [maxSec].
 * With the defaults that is 1h -> 24h -> 7d. Always finite — blocks expire on their own.
 */
internal fun abuseBlockDurationSec(strikes: Int, baseSec: Int, maxSec: Int): Int {
    if (strikes <= 0) return 0
    var duration = baseSec.toLong()
    // Bounded so the multiply cannot run away; the cap below decides the outcome long before this.
    repeat((strikes - 1).coerceAtMost(MAX_ESCALATION_STEPS)) { duration *= ESCALATION_FACTOR }
    return duration.coerceIn(1L, maxSec.toLong()).toInt()
}

/**
 * Strikes fade after [decaySec] of quiet.
 *
 * Without this, one bad afternoon years ago would still put the owner's own address on the 7-day
 * rung the next time they fat-finger a password from a new network.
 */
internal fun decayedStrikes(strikes: Int, lastActivityAt: LocalDateTime, now: LocalDateTime, decaySec: Int): Int =
    if (Duration.between(lastActivityAt, now).seconds >= decaySec) 0 else strikes

/** True once the counting window has rolled; counters restart from zero. */
internal fun abuseWindowExpired(windowStart: LocalDateTime, now: LocalDateTime, windowSec: Int): Boolean =
    Duration.between(windowStart, now).seconds >= windowSec

internal fun isAbuseBlockActive(blockedUntil: LocalDateTime?, now: LocalDateTime): Boolean =
    blockedUntil != null && blockedUntil.isAfter(now)

internal fun abuseRetryAfterSeconds(blockedUntil: LocalDateTime, now: LocalDateTime): Int =
    ceil(Duration.between(now, blockedUntil).toMillis() / 1000.0).toInt().coerceAtLeast(1)

private const val ESCALATION_FACTOR = 24L
private const val MAX_ESCALATION_STEPS = 4

/**
 * Longer-lived blocking for callers that keep abusing an unauthenticated path.
 *
 * This is NOT the rate limiter ([AuthThrottle] is). A throttle says "not this second"; this says
 * "not today", so sustained abuse stops costing the server anything.
 */
interface AbuseGuard {
    /** Is this caller currently blocked from [scope]? Authenticated callers never are. */
    suspend fun blockVerdict(scope: AbuseScope, call: ApplicationCall): AbuseBlockVerdict

    /** Feed one signal; may trip a block. Safe to call on a hot path — failures are swallowed. */
    suspend fun recordSignal(scope: AbuseScope, signal: AbuseSignal, request: ApplicationRequest)

    /** A successful sign-in by an approved user: clears auth pressure and any auth block for this IP. */
    suspend fun clearAuthPressure(request: ApplicationRequest)

    suspend fun listActiveBlocks(admin: AuthenticatedUser): Either<AppError, List<AbuseBlockResponse>>
    suspend fun clearBlock(blockId: String, admin: AuthenticatedUser): Either<AppError, String>
}

/**
 * Writes a 429 and returns true when the caller is blocked, so a route reads as one line.
 *
 * The response is deliberately indistinguishable from ordinary rate limiting: same status, same
 * `auth_limit` reason the clients already handle, no hint about which signal tripped or how long
 * the escalation ladder is.
 */
suspend fun ApplicationCall.rejectIfAbuseBlocked(guard: AbuseGuard, scope: AbuseScope): Boolean {
    val verdict = guard.blockVerdict(scope, this)
    if (!verdict.blocked) return false
    respondRateLimit(
        message = "Too many requests. Try again later.",
        reason = "auth_limit",
        retryAfterSeconds = verdict.retryAfterSeconds,
    )
    return true
}

/**
 * Records a credential/reset failure and, when it completed a lockout episode, feeds that one
 * episode to the abuse guard. Every failing auth path uses this instead of [AuthThrottle.recordFailure]
 * directly, so the "count episodes, not failures" rule holds at all of them.
 */
suspend fun AuthThrottle.recordFailureWithAbuse(
    guard: AbuseGuard,
    request: ApplicationRequest,
    identifier: String?,
) {
    val outcome = recordFailure(request, identifier)
    if (outcome.lockoutTriggered) {
        guard.recordSignal(AbuseScope.auth, AbuseSignal.authLockout, request)
    }
}

/**
 * Signals that a caller kept trying while a lockout against them was already active.
 *
 * This is the signal that actually makes auth blocking reachable, and it is a better one than
 * counting lockouts. `lockoutTriggered` fires only on the failure that *crosses* the threshold, and
 * the failure counter does not reset until `AUTH_LOCKOUT_RESET_SEC` of quiet — so accumulating
 * several lockout episodes takes days, by which time the earlier signals have aged out of the abuse
 * window. Left alone, an auth block was effectively unreachable no matter how hard someone hammered.
 *
 * Continuing to knock while locked out, on the other hand, is unambiguous: a person who mistyped
 * their password reads "try again in 30s" and waits, whereas a script does not notice and keeps
 * going. That is why the threshold for this counter is set well above what a frustrated human
 * produces, and why a successful sign-in wipes it.
 */
suspend fun AbuseGuard.noteLockoutPressure(request: ApplicationRequest, throttle: ThrottleResult) {
    if (throttle.allowed) return
    if (throttle.reasonCode != "auth_lockout") return
    recordSignal(AbuseScope.auth, AbuseSignal.authLockout, request)
}

class AbuseGuardImpl(
    private val config: AppConfig,
    private val clientSignals: ClientSignals,
    private val eventLogger: SecurityEventLogger,
    private val alertService: SecurityAlertService,
) : AbuseGuard {

    private val thresholds by lazy {
        AbuseThresholds(
            registerViolationMax = config.abuseRegisterViolationMax,
            registerPendingMax = config.abuseRegisterPendingMax,
            authLockoutMax = config.abuseAuthLockoutMax,
        )
    }

    override suspend fun blockVerdict(scope: AbuseScope, call: ApplicationCall): AbuseBlockVerdict {
        // Lockout safety, rule 1: a request carrying a valid session or API key is never blocked
        // by this feature, so an owner who is already signed in somewhere can always reach the
        // admin API and lift a block by hand.
        if (call.authUser() != null) return AbuseBlockVerdict.allowed

        val now = LocalDateTime.now(ZoneOffset.UTC)
        val subject = subjectHash(call.request)
        // Lockout safety: this read runs BEFORE every sign-in, so it must fail OPEN. If
        // abuse_blocks is unreachable — a migration that has not landed yet on this deploy, a
        // lock timeout — the owner must still be able to log in. The worst case of failing open
        // is that an abuser gets served for as long as the fault lasts; the worst case of failing
        // closed is a total auth outage on a box reachable only over SSH.
        val blockedUntil = try {
            newSuspendedTransaction(Dispatchers.IO) {
                AbuseBlocks.selectAll()
                    .where { (AbuseBlocks.subjectHash eq subject) and (AbuseBlocks.scope eq scope.name) }
                    .firstOrNull()
                    ?.get(AbuseBlocks.blockedUntil)
            }
        } catch (e: Exception) {
            eventLogger.log("abuse_verdict_failed", mapOf("scope" to scope.name, "error" to e.message))
            return AbuseBlockVerdict.allowed
        }
        if (!isAbuseBlockActive(blockedUntil, now)) return AbuseBlockVerdict.allowed
        return AbuseBlockVerdict(blocked = true, retryAfterSeconds = abuseRetryAfterSeconds(blockedUntil!!, now))
    }

    override suspend fun recordSignal(scope: AbuseScope, signal: AbuseSignal, request: ApplicationRequest) {
        val trip = try {
            applySignal(scope, signal, request)
        } catch (e: Exception) {
            // Accounting must never break the path it observes.
            eventLogger.log("abuse_signal_failed", mapOf("scope" to scope.name, "error" to e.message))
            return
        } ?: return

        eventLogger.log(
            "abuse_block_applied",
            mapOf(
                "scope" to scope.name,
                "reason" to trip.reason,
                "strikes" to trip.strikes,
                "durationSeconds" to trip.durationSec,
            ),
        )
        alertService.raise(
            SecurityAlertType.ABUSE_BLOCK,
            "Blocked the ${scope.name} path for one source (${trip.reason}, strike ${trip.strikes}, " +
                "${trip.durationSec / 60} min).",
        )
    }

    private data class AbuseTrip(val reason: String, val strikes: Int, val durationSec: Int)

    private suspend fun applySignal(scope: AbuseScope, signal: AbuseSignal, request: ApplicationRequest): AbuseTrip? {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val subject = subjectHash(request)

        return newSuspendedTransaction(Dispatchers.IO) {
            val row = AbuseBlocks.selectAll()
                .where { (AbuseBlocks.subjectHash eq subject) and (AbuseBlocks.scope eq scope.name) }
                .forUpdate()
                .firstOrNull()

            if (row == null) {
                val counters = bump(AbuseCounters(0, 0), signal)
                val reason = abuseTripReason(scope, counters, thresholds)
                val duration = if (reason != null) {
                    abuseBlockDurationSec(1, config.abuseBlockBaseSec, config.abuseBlockMaxSec)
                } else {
                    0
                }
                AbuseBlocks.insert {
                    it[id] = CuidGenerator.newCuid()
                    it[subjectHash] = subject
                    it[AbuseBlocks.scope] = scope.name
                    it[blockedUntil] = if (reason != null) now.plusSeconds(duration.toLong()) else null
                    it[strikes] = if (reason != null) 1 else 0
                    it[signalCount] = if (reason != null) 0 else counters.signalCount
                    it[pendingSignups] = if (reason != null) 0 else counters.pendingSignups
                    it[windowStart] = now
                    it[AbuseBlocks.reason] = reason
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                return@newSuspendedTransaction reason?.let { AbuseTrip(it, 1, duration) }
            }

            val rowId = row[AbuseBlocks.id]
            val windowStartAt = row[AbuseBlocks.windowStart]
            val rolled = abuseWindowExpired(windowStartAt, now, config.abuseSignalWindowSec)
            val base = if (rolled) {
                AbuseCounters(0, 0)
            } else {
                AbuseCounters(row[AbuseBlocks.signalCount], row[AbuseBlocks.pendingSignups])
            }
            val counters = bump(base, signal)
            val carriedStrikes = decayedStrikes(
                strikes = row[AbuseBlocks.strikes],
                lastActivityAt = row[AbuseBlocks.updatedAt],
                now = now,
                decaySec = config.abuseStrikeDecaySec,
            )
            val reason = abuseTripReason(scope, counters, thresholds)
            val nextStrikes = if (reason != null) carriedStrikes + 1 else carriedStrikes
            val duration = if (reason != null) {
                abuseBlockDurationSec(nextStrikes, config.abuseBlockBaseSec, config.abuseBlockMaxSec)
            } else {
                0
            }

            AbuseBlocks.update({ AbuseBlocks.id eq rowId }) {
                it[strikes] = nextStrikes
                // Counters reset on a trip, otherwise the very next signal re-trips immediately.
                it[signalCount] = if (reason != null) 0 else counters.signalCount
                it[pendingSignups] = if (reason != null) 0 else counters.pendingSignups
                it[windowStart] = if (rolled || reason != null) now else windowStartAt
                if (reason != null) {
                    it[blockedUntil] = now.plusSeconds(duration.toLong())
                    it[AbuseBlocks.reason] = reason
                }
                it[updatedAt] = now
            }

            reason?.let { AbuseTrip(it, nextStrikes, duration) }
        }
    }

    private fun bump(counters: AbuseCounters, signal: AbuseSignal): AbuseCounters = when (signal) {
        AbuseSignal.registerViolation, AbuseSignal.authLockout ->
            counters.copy(signalCount = counters.signalCount + 1)
        AbuseSignal.pendingSignup -> counters.copy(pendingSignups = counters.pendingSignups + 1)
    }

    /**
     * Lockout safety, rule 2: a real sign-in wipes the auth-path pressure for that address —
     * strikes, counters and any live block. Whoever holds the password can always talk their way
     * out, from the same address, without waiting for anything to expire.
     */
    override suspend fun clearAuthPressure(request: ApplicationRequest) {
        val subject = subjectHash(request)
        val now = LocalDateTime.now(ZoneOffset.UTC)
        // Swallowed for the same reason the verdict read fails open, and it matters more here:
        // this runs AFTER the password has been verified but BEFORE the session cookie is
        // issued, so an exception escaping would turn a correct password into a 500 with no
        // session — a self-lockout caused by the very code meant to prevent one.
        try {
            newSuspendedTransaction(Dispatchers.IO) {
                AbuseBlocks.update({
                    (AbuseBlocks.subjectHash eq subject) and (AbuseBlocks.scope eq AbuseScope.auth.name)
                }) {
                    it[blockedUntil] = null
                    it[strikes] = 0
                    it[signalCount] = 0
                    it[reason] = null
                    it[windowStart] = now
                    it[updatedAt] = now
                }
            }
        } catch (e: Exception) {
            eventLogger.log("abuse_clear_failed", mapOf("error" to e.message))
        }
    }

    override suspend fun listActiveBlocks(admin: AuthenticatedUser): Either<AppError, List<AbuseBlockResponse>> = either {
        admin.requireAdminAccess().bind()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        newSuspendedTransaction(Dispatchers.IO) {
            AbuseBlocks.selectAll()
                .where { AbuseBlocks.blockedUntil greater now }
                .orderBy(AbuseBlocks.blockedUntil to SortOrder.DESC)
                .limit(LIST_LIMIT)
                .map { row ->
                    AbuseBlockResponse(
                        id = row[AbuseBlocks.id],
                        subject = row[AbuseBlocks.subjectHash].take(SUBJECT_PREVIEW_CHARS),
                        scope = row[AbuseBlocks.scope],
                        reason = row[AbuseBlocks.reason],
                        strikes = row[AbuseBlocks.strikes],
                        blockedUntil = row[AbuseBlocks.blockedUntil]?.toString(),
                        createdAt = row[AbuseBlocks.createdAt].toString(),
                        updatedAt = row[AbuseBlocks.updatedAt].toString(),
                    )
                }
        }
    }

    /**
     * Lockout safety, rule 3: the admin can lift any block by hand. Strikes go with it, so the
     * cleared subject starts again from the bottom of the ladder rather than the 7-day rung.
     */
    override suspend fun clearBlock(blockId: String, admin: AuthenticatedUser): Either<AppError, String> = either {
        admin.requireAdminAccess().bind()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val updated = newSuspendedTransaction(Dispatchers.IO) {
            AbuseBlocks.update({ AbuseBlocks.id eq blockId }) {
                it[blockedUntil] = null
                it[strikes] = 0
                it[signalCount] = 0
                it[pendingSignups] = 0
                it[reason] = null
                it[windowStart] = now
                it[updatedAt] = now
            }
        }
        if (updated == 0) raise(AppError.NotFound("block not found"))
        "block cleared"
    }

    /** Identity is the client IP, stored only as an HMAC — never the address itself. */
    private fun subjectHash(request: ApplicationRequest): String =
        clientSignals.hashSecurityValue("ip:${clientSignals.getClientIp(request)}")

    private companion object {
        const val LIST_LIMIT = 100
        const val SUBJECT_PREVIEW_CHARS = 12
    }
}
