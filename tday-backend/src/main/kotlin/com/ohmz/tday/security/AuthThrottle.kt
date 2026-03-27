package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.db.tables.AuthSignals
import com.ohmz.tday.db.tables.AuthThrottles
import com.ohmz.tday.db.util.CuidGenerator
import io.ktor.server.request.ApplicationRequest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class ThrottleAction { credentials, register, csrf, session }

enum class ThrottleDimension { ip, email, device }

data class ThrottleResult(
    val allowed: Boolean,
    val reasonCode: String? = null,
    val retryAfterSeconds: Int = 0,
    val dimension: ThrottleDimension? = null,
)

data class SubjectKey(val scope: String, val bucketKey: String, val dimension: ThrottleDimension)

interface AuthThrottle {
    fun enforceRateLimit(action: ThrottleAction, request: ApplicationRequest, identifier: String? = null): ThrottleResult
    fun recordFailure(request: ApplicationRequest, identifier: String? = null)
    fun clearFailures(request: ApplicationRequest, identifier: String? = null)
    fun requiresCaptcha(action: ThrottleAction, request: ApplicationRequest, identifier: String? = null): Boolean
    fun recordSuccessSignal(request: ApplicationRequest, identifier: String? = null)
    fun formatRetryWait(seconds: Int): String
}

class AuthThrottleImpl(
    private val config: AppConfig,
    private val clientSignals: ClientSignals,
    private val eventLogger: SecurityEventLogger,
) : AuthThrottle {
    private data class Policy(val windowMs: Long, val maxRequests: Int)

    private val policies by lazy {
        mapOf(
            ThrottleAction.credentials to Policy(config.limitCredentialsWindowSec * 1000L, config.limitCredentialsMax),
            ThrottleAction.register to Policy(config.limitRegisterWindowSec * 1000L, config.limitRegisterMax),
            ThrottleAction.csrf to Policy(config.limitCsrfWindowSec * 1000L, config.limitCsrfMax),
            ThrottleAction.session to Policy(config.limitSessionWindowSec * 1000L, config.limitSessionMax),
        )
    }

    override fun enforceRateLimit(action: ThrottleAction, request: ApplicationRequest, identifier: String?): ThrottleResult {
        val subjects = buildSubjects(action, request, identifier)
        val policy = policies[action] ?: return ThrottleResult(allowed = true)

        var blocked: ThrottleResult? = null
        for (subject in subjects) {
            val verdict = consumeRequestQuota(policy, subject)
            if (!verdict.allowed) {
                blocked = pickStronger(blocked, verdict)
            }
        }

        if (blocked != null) {
            eventLogger.log(
                blocked.reasonCode ?: "auth_limit",
                mapOf(
                    "action" to action.name,
                    "retryAfterSeconds" to blocked.retryAfterSeconds,
                    "dimension" to blocked.dimension?.name,
                ),
            )
            return blocked
        }
        return ThrottleResult(allowed = true)
    }

    override fun recordFailure(request: ApplicationRequest, identifier: String?) {
        val subjects = buildSubjects(ThrottleAction.credentials, request, identifier)
        var longestLock = 0
        var highestIpFailures = 0

        for (subject in subjects) {
            val result = incrementFailureCounter(subject)
            longestLock = max(longestLock, result.first)
            if (subject.dimension == ThrottleDimension.ip) {
                highestIpFailures = max(highestIpFailures, result.second)
            }
        }

        if (longestLock > 0) {
            eventLogger.log("auth_lockout", mapOf("action" to "credentials", "retryAfterSeconds" to longestLock))
            if (longestLock >= config.alertLockoutBurstSec) {
                eventLogger.log("auth_alert_lockout_burst", mapOf("retryAfterSeconds" to longestLock))
            }
        }
        if (highestIpFailures >= config.alertIpFailureThreshold) {
            eventLogger.log("auth_alert_ip_concentration", mapOf("ipFailureCount" to highestIpFailures))
        }
    }

    override fun clearFailures(request: ApplicationRequest, identifier: String?) {
        val subjects = buildSubjects(ThrottleAction.credentials, request, identifier)
        transaction {
            for (subject in subjects) {
                AuthThrottles.update({
                    (AuthThrottles.scope eq subject.scope) and (AuthThrottles.bucketKey eq subject.bucketKey)
                }) {
                    it[failureCount] = 0
                    it[lockUntil] = null
                    it[lastFailureAt] = null
                }
            }
        }
    }

    override fun requiresCaptcha(action: ThrottleAction, request: ApplicationRequest, identifier: String?): Boolean {
        val subjects = buildSubjects(action, request, identifier)
        val now = System.currentTimeMillis()

        return transaction {
            for (subject in subjects) {
                val row = AuthThrottles.selectAll().where {
                    (AuthThrottles.scope eq subject.scope) and (AuthThrottles.bucketKey eq subject.bucketKey)
                }.firstOrNull() ?: continue

                val lastFailure = row[AuthThrottles.lastFailureAt]
                val failureCount = row[AuthThrottles.failureCount]
                val requestCount = row[AuthThrottles.requestCount]
                val resetMs = config.lockoutResetSec * 1000L

                val activeFailures = if (lastFailure != null &&
                    now - lastFailure.toInstant(ZoneOffset.UTC).toEpochMilli() <= resetMs
                ) {
                    failureCount
                } else {
                    0
                }

                if (activeFailures >= config.captchaTriggerFailures) return@transaction true

                if (action == ThrottleAction.register) {
                    val registerPolicy = policies[ThrottleAction.register] ?: continue
                    if (requestCount >= max(3, registerPolicy.maxRequests / 2)) return@transaction true
                }
            }
            false
        }
    }

    override fun recordSuccessSignal(request: ApplicationRequest, identifier: String?) {
        val normalized = clientSignals.normalizeIdentifier(identifier) ?: return
        val identifierHash = clientSignals.hashSecurityValue("email:$normalized")
        val ipHash = clientSignals.hashSecurityValue("ip:${clientSignals.getClientIp(request)}")
        val deviceHint = clientSignals.getDeviceHint(request)
        val deviceHash = deviceHint?.let { clientSignals.hashSecurityValue("device:$it") }
        val now = LocalDateTime.now()
        val anomalyWindowMs = config.signalAnomalyWindowSec * 1000L

        transaction {
            val existing = AuthSignals.selectAll().where { AuthSignals.identifierHash eq identifierHash }.firstOrNull()

            if (existing != null) {
                val lastSeen = existing[AuthSignals.lastSeenAt]
                val lastIp = existing[AuthSignals.lastIpHash]
                val lastDevice = existing[AuthSignals.lastDeviceHash]

                if (lastSeen != null && lastIp != null && lastDevice != null && deviceHash != null) {
                    val elapsed = now.toInstant(ZoneOffset.UTC).toEpochMilli() -
                        lastSeen.toInstant(ZoneOffset.UTC).toEpochMilli()
                    if (elapsed <= anomalyWindowMs && lastIp != ipHash && lastDevice != deviceHash) {
                        eventLogger.log(
                            "auth_signal_anomaly",
                            mapOf(
                                "reason" to "ip_and_device_changed",
                                "identifierHash" to identifierHash,
                            ),
                        )
                    }
                }

                AuthSignals.update({ AuthSignals.identifierHash eq identifierHash }) {
                    it[lastIpHash] = ipHash
                    it[lastDeviceHash] = deviceHash
                    it[lastSeenAt] = now
                    it[updatedAt] = now
                }
            } else {
                AuthSignals.insert {
                    it[id] = CuidGenerator.newCuid()
                    it[AuthSignals.identifierHash] = identifierHash
                    it[lastIpHash] = ipHash
                    it[lastDeviceHash] = deviceHash
                    it[lastSeenAt] = now
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
    }

    override fun formatRetryWait(seconds: Int): String {
        if (seconds < 60) return "${seconds}s"
        val m = seconds / 60
        val s = seconds % 60
        return if (s == 0) "${m}m" else "${m}m ${s}s"
    }

    private fun buildSubjects(action: ThrottleAction, request: ApplicationRequest, identifier: String?): List<SubjectKey> {
        val subjects = mutableListOf<SubjectKey>()
        val ip = clientSignals.getClientIp(request)
        subjects.add(makeSubject(action, ThrottleDimension.ip, ip))

        val device = clientSignals.getDeviceHint(request)
        if (device != null) subjects.add(makeSubject(action, ThrottleDimension.device, device))

        if (action != ThrottleAction.csrf) {
            val norm = clientSignals.normalizeIdentifier(identifier)
            if (norm != null) subjects.add(makeSubject(action, ThrottleDimension.email, norm))
        }
        return subjects
    }

    private fun makeSubject(action: ThrottleAction, dimension: ThrottleDimension, value: String): SubjectKey =
        SubjectKey("${action.name}:${dimension.name}", clientSignals.hashSecurityValue("${dimension.name}:$value"), dimension)

    private fun consumeRequestQuota(policy: Policy, subject: SubjectKey): ThrottleResult {
        val now = LocalDateTime.now()
        return transaction {
            val row = AuthThrottles.selectAll().where {
                (AuthThrottles.scope eq subject.scope) and (AuthThrottles.bucketKey eq subject.bucketKey)
            }.forUpdate().firstOrNull()

            if (row == null) {
                AuthThrottles.insert {
                    it[id] = CuidGenerator.newCuid()
                    it[scope] = subject.scope
                    it[bucketKey] = subject.bucketKey
                    it[windowStart] = now
                    it[requestCount] = 1
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                return@transaction ThrottleResult(allowed = true)
            }

            val lockUntil = row[AuthThrottles.lockUntil]
            if (lockUntil != null && lockUntil.isAfter(now)) {
                return@transaction ThrottleResult(
                    allowed = false,
                    reasonCode = "auth_lockout",
                    retryAfterSeconds = retryAfterFromDateTime(lockUntil, now),
                    dimension = subject.dimension,
                )
            }

            val windowStart = row[AuthThrottles.windowStart]
            val windowResetNeeded = java.time.Duration.between(windowStart, now).toMillis() >= policy.windowMs
            val nextWindowStart = if (windowResetNeeded) now else windowStart
            val nextRequestCount = if (windowResetNeeded) 1 else row[AuthThrottles.requestCount] + 1
            val rowId = row[AuthThrottles.id]

            AuthThrottles.update({ AuthThrottles.id eq rowId }) {
                it[AuthThrottles.windowStart] = nextWindowStart
                it[requestCount] = nextRequestCount
                it[updatedAt] = now
            }

            if (nextRequestCount <= policy.maxRequests) {
                return@transaction ThrottleResult(allowed = true)
            }

            val windowEndsAt = nextWindowStart.plusNanos(policy.windowMs * 1_000_000)
            ThrottleResult(
                allowed = false,
                reasonCode = if (subject.dimension == ThrottleDimension.email) "auth_limit_email" else "auth_limit_ip",
                retryAfterSeconds = retryAfterFromDateTime(windowEndsAt, now),
                dimension = subject.dimension,
            )
        }
    }

    private fun incrementFailureCounter(subject: SubjectKey): Pair<Int, Int> {
        val now = LocalDateTime.now()
        val lockoutResetMs = config.lockoutResetSec * 1000L

        return transaction {
            val row = AuthThrottles.selectAll().where {
                (AuthThrottles.scope eq subject.scope) and (AuthThrottles.bucketKey eq subject.bucketKey)
            }.forUpdate().firstOrNull()

            val lastFailure = row?.get(AuthThrottles.lastFailureAt)
            val baseFailures = if (row != null && lastFailure != null) {
                val elapsed = java.time.Duration.between(lastFailure, now).toMillis()
                if (elapsed <= lockoutResetMs) row[AuthThrottles.failureCount] else 0
            } else {
                0
            }

            val nextFailures = baseFailures + 1
            val computedLock = lockUntilFromFailures(nextFailures, now)
            val existingLock = row?.get(AuthThrottles.lockUntil)?.takeIf { it.isAfter(now) }
            val lockUntil = laterDateTime(computedLock, existingLock)

            if (row == null) {
                AuthThrottles.insert {
                    it[id] = CuidGenerator.newCuid()
                    it[scope] = subject.scope
                    it[bucketKey] = subject.bucketKey
                    it[windowStart] = now
                    it[requestCount] = 0
                    it[failureCount] = nextFailures
                    it[lastFailureAt] = now
                    it[AuthThrottles.lockUntil] = lockUntil
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            } else {
                AuthThrottles.update({ AuthThrottles.id eq row[AuthThrottles.id] }) {
                    it[failureCount] = nextFailures
                    it[lastFailureAt] = now
                    it[AuthThrottles.lockUntil] = lockUntil
                    it[updatedAt] = now
                }
            }

            val lockSec = if (lockUntil != null) retryAfterFromDateTime(lockUntil, now) else 0
            Pair(lockSec, nextFailures)
        }
    }

    private fun lockUntilFromFailures(failures: Int, now: LocalDateTime): LocalDateTime? {
        if (failures < config.lockoutFailThreshold) return null
        val exponent = max(0, failures - config.lockoutFailThreshold)
        val lockMs = min(
            config.lockoutMaxSec * 1000L,
            config.lockoutBaseSec * 1000L * 2.0.pow(exponent).toLong(),
        )
        return now.plusNanos(lockMs * 1_000_000)
    }

    private fun laterDateTime(a: LocalDateTime?, b: LocalDateTime?): LocalDateTime? {
        if (a == null) return b
        if (b == null) return a
        return if (a.isAfter(b)) a else b
    }

    private fun retryAfterFromDateTime(target: LocalDateTime, now: LocalDateTime): Int {
        val seconds = ceil(java.time.Duration.between(now, target).toMillis() / 1000.0).toInt()
        return max(1, seconds)
    }

    private fun pickStronger(current: ThrottleResult?, candidate: ThrottleResult): ThrottleResult {
        if (current == null) return candidate
        if (candidate.reasonCode == "auth_lockout" && current.reasonCode != "auth_lockout") return candidate
        if (candidate.retryAfterSeconds > current.retryAfterSeconds) return candidate
        return current
    }
}
