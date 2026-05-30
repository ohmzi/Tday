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

val SentryRequestPlugin = createApplicationPlugin(name = "SentryRequestPlugin") {
    onCall { call ->
        val routeTemplate = TdayObservability.routeTemplate(
            call.request.httpMethod.value,
            call.request.path(),
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
                "route" to TdayObservability.sanitizePath(call.request.path()),
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
