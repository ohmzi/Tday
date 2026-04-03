package com.ohmz.tday.plugins

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.domain.respondRateLimit
import com.ohmz.tday.security.RequestRateLimitAssessment
import com.ohmz.tday.security.RequestRateLimitPolicy
import com.ohmz.tday.security.RequestRateLimiter
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.koin.ktor.ext.inject

fun Application.configureRateLimiting() {
    val config by inject<AppConfig>()
    val requestRateLimiter by inject<RequestRateLimiter>()

    intercept(ApplicationCallPipeline.Plugins) {
        val policies = resolvePolicies(call.request.path(), call.request.httpMethod, config)
        if (policies.isEmpty()) return@intercept

        val userId = call.authUser()?.id
        val blocked = policies
            .map { policy -> requestRateLimiter.assess(policy, call.request, userId) }
            .filterNot(RequestRateLimitAssessment::allowed)
            .maxByOrNull(RequestRateLimitAssessment::retryAfterSeconds)

        if (blocked != null) {
            call.respondRateLimit(
                message = "Too many requests. Try again in ${formatRetryWait(blocked.retryAfterSeconds)}.",
                reason = blocked.reasonCode ?: "request_rate_limit",
                retryAfterSeconds = blocked.retryAfterSeconds,
            )
            finish()
        }
    }
}

private fun resolvePolicies(
    path: String,
    method: HttpMethod,
    config: AppConfig,
): List<RequestRateLimitPolicy> = buildList {
    if (path.startsWith("/api/")) {
        add(
            RequestRateLimitPolicy(
                name = "api_global",
                reasonCode = "api_rate_limit",
                windowSec = config.apiRateLimitWindowSec,
                maxRequests = config.apiRateLimitMax,
            ),
        )
    }

    if (path == "/health" || path == "/api/mobile/probe") {
        add(
            RequestRateLimitPolicy(
                name = "infra",
                reasonCode = "infra_rate_limit",
                windowSec = config.infraRateLimitWindowSec,
                maxRequests = config.infraRateLimitMax,
            ),
        )
    }

    if (method == HttpMethod.Post && path == "/api/todo/summary") {
        add(
            RequestRateLimitPolicy(
                name = "todo_summary",
                reasonCode = "summary_rate_limit",
                windowSec = config.summaryRateLimitWindowSec,
                maxRequests = config.summaryRateLimitMax,
            ),
        )
    }

    if (method == HttpMethod.Post && path == "/api/user/change-password") {
        add(
            RequestRateLimitPolicy(
                name = "change_password",
                reasonCode = "change_password_rate_limit",
                windowSec = config.changePasswordRateLimitWindowSec,
                maxRequests = config.changePasswordRateLimitMax,
            ),
        )
    }

    if (path == "/ws") {
        add(
            RequestRateLimitPolicy(
                name = "websocket_connect",
                reasonCode = "websocket_rate_limit",
                windowSec = config.wsRateLimitWindowSec,
                maxRequests = config.wsRateLimitMax,
            ),
        )
    }
}

private fun formatRetryWait(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (remainder == 0) "${minutes}m" else "${minutes}m ${remainder}s"
}
