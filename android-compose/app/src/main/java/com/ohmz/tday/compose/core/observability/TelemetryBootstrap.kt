package com.ohmz.tday.compose.core.observability

import android.content.Context
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Starts and stops the crash reporter. A seam so the state machine can be tested without Sentry. */
interface TelemetrySdk {
    fun start(grantedAtMs: Long)

    /** Blocking: closes Sentry, which flushes and waits on its executors. Never call it on the main thread. */
    fun stop()
}

/**
 * The only thing that starts or stops Sentry in this app: one consent-gated path, so "nothing is
 * sent before consent" is a property of this class and not of every call site.
 *
 *  - [start] runs once per process from `Application.onCreate`, so a boot receiver, a widget
 *    refresh or an alarm that wakes the app cold is covered too. It starts the SDK only for a
 *    device that said yes; for every other state it deletes whatever an older build left on disk.
 *  - [apply] is a change of mind. Yes purges, records the moment, and starts fresh; no records the
 *    answer first (so a kill half way still ends as a no), shuts the gate, stops the SDK, then
 *    deletes its files.
 *
 * With no DSN configured (a fork, a debug build, a self-built APK) it does nothing at all: no
 * start, no purge, no answer stored for a later build that does have one.
 *
 * Transitions take a lock rather than being "single threaded by convention": [start] runs on the
 * main thread at launch while [apply] runs on a background dispatcher, and a revoke that is still
 * closing the SDK must finish before a following grant starts a new one.
 */
class TelemetryBootstrap internal constructor(
    private val store: TelemetryConsentStore,
    private val gate: TelemetryGate,
    private val sdk: TelemetrySdk,
    private val dsn: String,
    private val sentryCacheDir: File,
    private val installationFile: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = ReentrantLock()

    /** False on a build with no DSN: the card and the Settings row stay hidden. */
    val isAvailable: Boolean get() = dsn.isNotBlank()

    fun state(): TelemetryConsentState = store.state()

    fun start() {
        if (!isAvailable) return
        lock.withLock {
            if (store.state() == TelemetryConsentState.GRANTED) {
                startSdk(store.grantedAtMs())
            } else {
                purge()
            }
        }
    }

    fun apply(granted: Boolean) {
        if (!isAvailable) return
        lock.withLock {
            if (granted) grant() else revoke()
        }
    }

    private fun grant() {
        if (store.state() == TelemetryConsentState.GRANTED) return
        // Before the SDK starts, so it begins on an empty cache: no old envelope, no `last_anr_report`
        // marker for it to replay.
        purge()
        val now = clock()
        store.grant(now)
        startSdk(now)
    }

    private fun revoke() {
        store.deny()
        gate.close()
        // The gate is already shut, so a failure to stop the SDK cannot leak a report; the purge
        // below runs either way.
        runCatching { sdk.stop() }
        purge()
    }

    // The gate opens before the SDK starts: it files events of its own while starting up (the ANR
    // it finds in the exit history) and those must not meet a closed gate.
    private fun startSdk(grantedAtMs: Long) {
        gate.open()
        runCatching { sdk.start(grantedAtMs) }.onFailure { gate.close() }
    }

    /**
     * Everything the SDK persists: its folder under `cacheDir` (envelopes, native crash state, the
     * ANR markers, the cached scope) and the random install id it writes to `filesDir`.
     */
    internal fun purge() {
        sentryCacheDir.deleteRecursively()
        installationFile.delete()
    }

    companion object {
        private const val INSTALLATION_FILE = "INSTALLATION"

        @Volatile
        private var shared: TelemetryBootstrap? = null

        /** One per process, because Sentry itself is one per process. */
        fun shared(context: Context): TelemetryBootstrap =
            shared ?: synchronized(this) {
                shared ?: create(context.applicationContext).also { shared = it }
            }

        private fun create(app: Context): TelemetryBootstrap {
            val settings = TelemetrySettings.forApp(app)
            val gate = TelemetryGate()
            return TelemetryBootstrap(
                store = TelemetryConsentStore(app),
                gate = gate,
                sdk = SentryTelemetrySdk(app, settings, gate),
                dsn = settings.dsn,
                sentryCacheDir = File(settings.cacheDirPath),
                installationFile = File(app.filesDir, INSTALLATION_FILE),
            )
        }
    }
}

/** The production [TelemetrySdk]: manual `SentryAndroid.init`, with auto-init disabled in the manifest. */
internal class SentryTelemetrySdk(
    private val context: Context,
    private val settings: TelemetrySettings,
    private val gate: TelemetryGate,
) : TelemetrySdk {

    override fun start(grantedAtMs: Long) {
        SentryAndroid.init(context) { options ->
            TelemetryOptions.configure(options, settings, grantedAtMs, gate) {
                TelemetryEventTags.current(settings.appVersion)
            }
        }
    }

    override fun stop() {
        Sentry.clearBreadcrumbs()
        Sentry.configureScope { scope -> scope.clear() }
        Sentry.close()
    }
}
