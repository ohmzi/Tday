package com.ohmz.tday.observability

import com.ohmz.tday.config.AppConfig
import io.sentry.AsyncHttpTransportFactory
import io.sentry.ITransportFactory
import io.sentry.KeyValueCollectionBehavior
import io.sentry.Sentry
import io.sentry.SentryOptions

private const val IN_APP_PACKAGE = "com.ohmz.tday"

/**
 * Starts the backend's Sentry client, switched off until an admin opts in.
 *
 * The SDK is initialised exactly once, here, at process start, and stays initialised: closing and
 * re-initialising it when the setting flips would race the request threads. Instead every way
 * something leaves the process is checked against the [TelemetryGate] (default closed, loaded from
 * the database after migrations):
 *
 * - the transport drops every envelope while closed ([GatedTransportFactory]), which also covers
 *   items that never pass the hooks;
 * - `beforeSend`, `beforeSendTransaction` and `beforeBreadcrumb` return null while closed;
 * - `tracesSampler` returns 0 while closed, so no transaction is even recorded.
 *
 * There is no on-disk event cache, so a dropped event is gone rather than queued, and opening the
 * gate later sends nothing from before. A boot that fails before the gate can be read (database
 * down) reports nothing; that blind spot is accepted.
 */
object BackendSentry {
    fun init(config: AppConfig, gate: TelemetryGate) {
        Sentry.init { options -> configure(options, config, gate) }
    }

    @Suppress("DEPRECATION")
    internal fun configure(
        options: SentryOptions,
        config: AppConfig,
        gate: TelemetryGate,
        transportFactory: ITransportFactory = AsyncHttpTransportFactory(),
    ) {
        options.dsn = config.sentryDsn.orEmpty()
        options.environment = if (config.isProduction) "production" else "development"
        options.release = "tday-backend@${config.backendVersion}"
        options.isSendDefaultPii = false
        collectNoPersonalData(options)
        // The generic name keeps the container's hostname out of every event.
        options.serverName = "tday-backend"
        options.addInAppInclude(IN_APP_PACKAGE)
        options.setTracesSampler { context ->
            traceSampleRate(context.transactionContext.name, gate.isOpen, config.sentryTracesSampleRate)
        }
        options.isSendClientReports = false
        options.setTransportFactory(GatedTransportFactory(gate, transportFactory))
        options.setBeforeSend { event, _ ->
            if (!gate.isOpen || TelemetryScrubber.isClientAbort(event.throwable)) null else TelemetryScrubber.scrubEvent(event)
        }
        options.setBeforeSendTransaction { transaction, _ ->
            if (gate.isOpen) TelemetryScrubber.scrubTransaction(transaction) else null
        }
        options.setBeforeBreadcrumb { breadcrumb, _ ->
            if (gate.isOpen) TelemetryScrubber.scrubBreadcrumb(breadcrumb) else null
        }
    }

    /**
     * Setting any one `dataCollection` field moves the SDK off the legacy `sendDefaultPii` rules
     * and onto these, where every field left unset falls back to collecting. So all of them are
     * spelled out: `userInfo = false` alone would switch cookies, headers and bodies on.
     */
    private fun collectNoPersonalData(options: SentryOptions) {
        options.dataCollection.apply {
            userInfo = false
            cookies = KeyValueCollectionBehavior.off()
            urlQueryParams = KeyValueCollectionBehavior.off()
            httpHeaders.request = KeyValueCollectionBehavior.off()
            httpHeaders.response = KeyValueCollectionBehavior.off()
            httpBodies = emptySet()
            databaseQueryData = false
            filePaths = false
            graphql.document = false
            graphql.variables = false
        }
    }
}

/**
 * Sampling rate for one transaction, by its name (`METHOD /sanitized/path`).
 *
 * Liveness probes, the app's reachability probe, the realtime socket and the calendar feed are
 * polled or long-lived and say nothing about the app, so they are never traced: the health check
 * alone was about 576 transactions a day. These are the paths the rate limiter treats as
 * infrastructure.
 */
internal fun traceSampleRate(transactionName: String, gateOpen: Boolean, configuredRate: Double): Double =
    if (gateOpen && !isUntracedPath(transactionName.substringAfter(' '))) configuredRate else 0.0

private fun isUntracedPath(path: String): Boolean =
    path == "/health" || path == "/api/mobile/probe" || path == "/ws" || path.startsWith("/calendar/")
