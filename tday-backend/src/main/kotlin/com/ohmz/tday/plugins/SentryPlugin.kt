package com.ohmz.tday.plugins

import com.ohmz.tday.observability.TdayObservability
import com.ohmz.tday.observability.clientTags
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.TransactionOptions
import io.sentry.kotlin.SentryContext
import kotlinx.coroutines.withContext

private val sentryTransactionKey = AttributeKey<ITransaction>("SentryTransaction")

/** The request path after [TdayObservability.sanitizePath], computed once per call for telemetry and logging. */
internal val sanitizedPathKey = AttributeKey<String>("SanitizedRequestPath")

/**
 * Hands the handler the rest of the call to run. It sits in the first pipeline phase, so whatever
 * the handler sets up is already in place for [SentryRequestPlugin]'s own hooks and everything
 * after them.
 */
private object WrapCall : Hook<suspend (suspend () -> Unit) -> Unit> {
    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (suspend () -> Unit) -> Unit,
    ) {
        pipeline.intercept(ApplicationCallPipeline.Setup) { handler(::proceed) }
    }
}

val SentryRequestPlugin = createApplicationPlugin(name = "SentryRequestPlugin") {
    // The SDK keeps its scopes in a thread-local, and a request hops threads whenever it touches
    // the database. Left alone, breadcrumbs and tags written while serving one request pile up on
    // whatever scope the thread happens to hold and show up on another request's error. Forking
    // both scopes (breadcrumbs and tags live on the isolation scope, so SentryContext's default
    // of forking only the current one would still share them) gives each request its own pair,
    // and the context carries it across the thread hops.
    on(WrapCall) { proceed ->
        if (Sentry.isEnabled()) withContext(SentryContext(Sentry.forkedScopes("request"))) { proceed() } else proceed()
    }

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
        // Which app build triggered a backend error. The values are validated, not echoed.
        clientTags(
            clientHeader = call.request.headers["X-Tday-Client"],
            versionHeader = call.request.headers["X-Tday-App-Version"],
        ).forEach { (tag, value) -> Sentry.setTag(tag, value) }
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
