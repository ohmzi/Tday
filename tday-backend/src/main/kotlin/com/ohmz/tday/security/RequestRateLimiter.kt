package com.ohmz.tday.security

import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.path
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.max

enum class RequestRateLimitSubjectType { ip, user }

data class RequestRateLimitPolicy(
    val name: String,
    val reasonCode: String,
    val windowSec: Int,
    val maxRequests: Int,
)

data class RequestRateLimitAssessment(
    val allowed: Boolean,
    val policyName: String,
    val reasonCode: String? = null,
    val retryAfterSeconds: Int = 0,
    val subjectType: RequestRateLimitSubjectType? = null,
)

interface RequestRateLimiter {
    suspend fun assess(
        policy: RequestRateLimitPolicy,
        request: ApplicationRequest,
        authenticatedUserId: String? = null,
    ): RequestRateLimitAssessment
}

class InMemoryRequestRateLimiter(
    private val clientSignals: ClientSignals,
    private val eventLogger: SecurityEventLogger,
) : RequestRateLimiter {
    private data class Subject(
        val type: RequestRateLimitSubjectType,
        val bucketKey: String,
    )

    private data class Bucket(
        val timestamps: ArrayDeque<Long> = ArrayDeque(),
        val windowMs: Long,
        @Volatile var lastSeenAt: Long,
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val accessCount = AtomicLong(0)

    override suspend fun assess(
        policy: RequestRateLimitPolicy,
        request: ApplicationRequest,
        authenticatedUserId: String?,
    ): RequestRateLimitAssessment {
        val now = System.currentTimeMillis()
        maybeCleanup(now)

        val subject = resolveSubject(request, authenticatedUserId)
        val bucketKey = "${policy.name}:${subject.type.name}:${subject.bucketKey}"
        val windowMs = policy.windowSec * 1000L
        val bucket = buckets.computeIfAbsent(bucketKey) {
            Bucket(windowMs = windowMs, lastSeenAt = now)
        }

        val retryAfterSeconds = synchronized(bucket) {
            trimExpired(bucket, now)
            bucket.lastSeenAt = now
            if (bucket.timestamps.size >= policy.maxRequests) {
                retryAfterSeconds(bucket, now)
            } else {
                bucket.timestamps.addLast(now)
                0
            }
        }

        if (retryAfterSeconds == 0) {
            return RequestRateLimitAssessment(
                allowed = true,
                policyName = policy.name,
            )
        }

        eventLogger.log(
            "request_rate_limit_triggered",
            mapOf(
                "policy" to policy.name,
                "reason" to policy.reasonCode,
                "subjectType" to subject.type.name,
                "retryAfterSeconds" to retryAfterSeconds,
                "path" to request.path(),
            ),
        )

        return RequestRateLimitAssessment(
            allowed = false,
            policyName = policy.name,
            reasonCode = policy.reasonCode,
            retryAfterSeconds = retryAfterSeconds,
            subjectType = subject.type,
        )
    }

    private fun resolveSubject(request: ApplicationRequest, authenticatedUserId: String?): Subject {
        if (!authenticatedUserId.isNullOrBlank()) {
            return Subject(
                type = RequestRateLimitSubjectType.user,
                bucketKey = clientSignals.hashSecurityValue("user:$authenticatedUserId"),
            )
        }

        val ip = clientSignals.getClientIp(request)
        return Subject(
            type = RequestRateLimitSubjectType.ip,
            bucketKey = clientSignals.hashSecurityValue("ip:$ip"),
        )
    }

    private fun trimExpired(bucket: Bucket, now: Long) {
        val cutoff = now - bucket.windowMs
        while (bucket.timestamps.isNotEmpty() && bucket.timestamps.first() <= cutoff) {
            bucket.timestamps.removeFirst()
        }
    }

    private fun retryAfterSeconds(bucket: Bucket, now: Long): Int {
        val oldest = bucket.timestamps.firstOrNull() ?: return 1
        val waitMs = max(1L, oldest + bucket.windowMs - now)
        return max(1, ceil(waitMs / 1000.0).toInt())
    }

    private fun maybeCleanup(now: Long) {
        if (accessCount.incrementAndGet() % 256L != 0L) return

        buckets.entries.removeIf { (_, bucket) ->
            synchronized(bucket) {
                trimExpired(bucket, now)
                bucket.timestamps.isEmpty() && now - bucket.lastSeenAt >= bucket.windowMs
            }
        }
    }
}
