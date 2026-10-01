package com.ohmz.tday.plugins

import com.ohmz.tday.observability.TdayObservability
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.ITransaction
import io.sentry.TransactionOptions

private val sentryTransactionKey = AttributeKey<ITransaction>("SentryTransaction")

/** The request path after [TdayObservability.sanitizePath], computed once per call for telemetry and logging. */
internal val sanitizedPathKey = AttributeKey<String>("SanitizedRequestPath")

val SentryRequestPlugin = createApplicationPlugin(name = "SentryRequestPlugin") {
    onCall { call ->
        val sanitizedPath = TdayObservability.sanitizePath(call.request.path())
        call.attributes.put(sanitizedPathKey, sanitizedPath)
        val routeTemplate = TdayObservability.routeTemplateFromSanitized(
            call.request.httpMethod.value,
            sanitizedPath,
        )
        val transaction = Sentry.startTransaction(
            routeTemplate,
            "http.server",
            TransactionOptions().apply { isBindToScope = true },
        )
        call.attributes.put(sentryTransactionKey, transaction)
        TdayObservability.addBreadcrumb(
            operation = "api.request",
            category = "http",
            data = mapOf(
                "method" to call.request.httpMethod.value,
                "route" to sanitizedPath,
            ),
        )
    }

    onCallRespond { call, _ ->
        call.attributes.getOrNull(sentryTransactionKey)?.let { txn ->
            txn.status = SpanStatus.fromHttpStatusCode(
                call.response.status()?.value ?: 200,
            )
            txn.finish()
        }
    }
}
