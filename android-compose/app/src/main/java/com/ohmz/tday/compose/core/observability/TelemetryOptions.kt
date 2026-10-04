package com.ohmz.tday.compose.core.observability

import com.ohmz.tday.compose.core.testcrash.TestCrash // TEST-CRASH
import android.content.Context
import com.ohmz.tday.compose.BuildConfig
import io.sentry.IConnectionStatusProvider.ConnectionStatus
import io.sentry.KeyValueCollectionBehavior
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.transport.ITransportGate
import java.io.File

/** What identifies this build to Sentry, and where the SDK keeps its files. */
data class TelemetrySettings(
    val dsn: String,
    val environment: String,
    val release: String,
    val dist: String,
    val appVersion: String,
    val cacheDirPath: String,
) {
    companion object {
        /** The folder under `cacheDir` that holds everything the SDK writes; opt-out deletes it. */
        const val CACHE_DIR_NAME = "sentry"

        fun forApp(context: Context) = TelemetrySettings(
            dsn = BuildConfig.SENTRY_DSN,
            environment = if (BuildConfig.DEBUG) "development" else "production",
            release = "tday-android@${BuildConfig.VERSION_NAME}",
            dist = BuildConfig.VERSION_CODE.toString(),
            appVersion = BuildConfig.VERSION_NAME,
            cacheDirPath = File(context.cacheDir, CACHE_DIR_NAME).path,
        )
    }
}

/**
 * Whether an event happened before the user said yes. Android replays an ANR from the system's exit
 * history on the next start, stamped with when the hang really was, so this is what keeps a hang
 * from while the switch was off from arriving after it is turned on.
 */
internal fun predatesConsent(eventTimestampMs: Long, grantedAtMs: Long): Boolean =
    eventTimestampMs < grantedAtMs

/**
 * The SDK configuration for a consenting device: failures only.
 *
 * Every option that matters is written out, including the ones that are the SDK's default today,
 * because a default is a promise only until the next 8.x release and this is a privacy contract.
 * The one that is not a default is `dataCollection`: it resolves every field to `true` once any
 * field is set, so all of them are set.
 *
 * Pure over [SentryAndroidOptions], which is what makes `TelemetryOptionsTest` possible: it
 * configures a real options object and reads the fields back.
 */
object TelemetryOptions {

    fun configure(
        options: SentryAndroidOptions,
        settings: TelemetrySettings,
        grantedAtMs: Long,
        gate: TelemetryGate,
        eventTags: () -> Map<String, String>,
    ) {
        options.dsn = settings.dsn
        options.environment = settings.environment
        options.release = settings.release
        options.dist = settings.dist
        options.cacheDirPath = settings.cacheDirPath

        configureTransport(options, gate)
        configureCollection(options)
        configureTracing(options)
        configureFailureDetection(options)
        configureCallbacks(options, grantedAtMs, gate, eventTags)
    }

    /**
     * Two gates, one at each end of the SDK's send queue. [GatedTransportFactory] stops an envelope
     * from being accepted once the answer is no; this one is asked again when a queued envelope is
     * about to go on the wire, so one accepted a moment earlier is dropped instead of delivered while
     * `Sentry.close()` drains the queue.
     *
     * Setting a transport gate replaces the SDK's own connectivity check, so that is repeated here:
     * with no network the envelope stays on disk and goes out later, as it would have.
     */
    private fun configureTransport(options: SentryAndroidOptions, gate: TelemetryGate) {
        options.setTransportFactory(GatedTransportFactory(gate))
        options.setTransportGate(
            ITransportGate {
                gate.isOpen && options.connectionStatusProvider.connectionStatus != ConnectionStatus.DISCONNECTED
            },
        )
    }

    /** What the SDK may attach to an event beyond the failure itself: nothing. */
    private fun configureCollection(options: SentryAndroidOptions) {
        // The one that matters. Any field set below would otherwise flip an unset `userInfo` to
        // true, and with it the SDK stamps `ip_address = {{auto}}` and an install id on every event.
        options.dataCollection.userInfo = false
        options.dataCollection.apply {
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
        // Deprecated in favour of dataCollection but still read by older integrations, and pinned
        // by the privacy guardrail.
        @Suppress("DEPRECATION")
        options.isSendDefaultPii = false

        options.isAttachScreenshot = false
        options.isAttachViewHierarchy = false
        options.isAttachServerName = false
        options.isEnableRootCheck = false
        options.sessionReplay.sessionSampleRate = 0.0
        options.sessionReplay.onErrorSampleRate = 0.0
        options.logs.isEnabled = false
        options.metrics.isEnabled = false
    }

    /** Failures only: no session pings, no performance data, no trace headers to anyone's server. */
    private fun configureTracing(options: SentryAndroidOptions) {
        options.isEnableAutoSessionTracking = false
        options.isSendClientReports = false
        options.tracesSampleRate = 0.0
        // Empty, not unset: unset means "every host", which would send `sentry-trace` and `baggage`
        // (and the owner's DSN public key) to every user's own server.
        options.setTracePropagationTargets(emptyList())
        options.isPropagateTraceparent = false
        options.isEnableAutoActivityLifecycleTracing = false
        options.isEnableFramesTracking = false
        options.isEnableUserInteractionTracing = false
        options.isEnableTimeToFullDisplayTracing = false
        options.isEnableStandaloneAppStartTracing = false
        options.isEnablePerformanceV2 = false
        options.isEnableAppStartProfiling = false
    }

    /** The detectors the product is for stay on; the ones that replay history or watch input go. */
    private fun configureFailureDetection(options: SentryAndroidOptions) {
        options.isEnableUncaughtExceptionHandler = true
        options.isAnrEnabled = true
        options.isEnableNdk = true
        options.isCollectAdditionalContext = true

        options.isReportHistoricalAnrs = false
        options.isAttachAnrThreadDump = false
        options.isTombstoneEnabled = false
        options.isReportHistoricalTombstones = false
        options.isAttachRawTombstone = false
        options.isMemoryLimiterEnabled = false
        options.isReportHistoricalMemoryLimiterExits = false

        options.isEnableUserInteractionBreadcrumbs = false
        options.isEnableSystemEventBreadcrumbs = false
        options.isEnableSystemEventBreadcrumbsExtras = false
    }

    private fun configureCallbacks(
        options: SentryAndroidOptions,
        grantedAtMs: Long,
        gate: TelemetryGate,
        eventTags: () -> Map<String, String>,
    ) {
        options.setBeforeSend { event, _ ->
            when {
                !gate.isOpen -> null
                predatesConsent(event.timestamp.time, grantedAtMs) -> null
                // TEST-CRASH: the harness gives each trigger a stable issue of its own. This line
                // and the import above it go with the rest of the harness.
                else -> TelemetryScrubber.scrub(event, eventTags())?.let(TestCrash::applyTestCrashFingerprint)
            }
        }
        options.setBeforeBreadcrumb { breadcrumb, _ ->
            if (gate.isOpen) TelemetryScrubber.scrubBreadcrumb(breadcrumb) else null
        }
        // Nothing here starts a transaction, a log or a metric. These make that a guarantee rather
        // than an observation: a future call site that does would be dropped, not sent.
        options.setBeforeSendTransaction { _, _ -> null }
        options.logs.setBeforeSend { _ -> null }
        options.metrics.setBeforeSend { _, _ -> null }
    }
}
