package com.ohmz.tday.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.ITransaction
import io.sentry.TransactionOptions

private val sentryTransactionKey = AttributeKey<ITransaction>("SentryTransaction")

val SentryRequestPlugin = createApplicationPlugin(name = "SentryRequestPlugin") {
    onCall { call ->
        val transaction = Sentry.startTransaction(
            "${call.request.httpMethod.value} ${call.request.path()}",
            "http.server",
            TransactionOptions().apply { isBindToScope = true },
        )
        call.attributes.put(sentryTransactionKey, transaction)
        Sentry.addBreadcrumb(
            Breadcrumb.http(call.request.uri, call.request.httpMethod.value),
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
