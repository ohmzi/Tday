package com.ohmz.tday.compose.core.observability

import io.mockk.every
import io.mockk.mockk
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IConnectionStatusProvider
import io.sentry.IConnectionStatusProvider.ConnectionStatus
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * What the SDK is told, field by field. The values are asserted rather than trusted to a default
 * because most of them ARE the SDK's default today and a 8.x bump is free to change one: the
 * data-collection block in particular resolves every unset field to `true` the moment any other
 * field is set, so "explicit" is the only spelling that is safe.
 */
class TelemetryOptionsTest {
    private val gate = TelemetryGate().apply { open() }
    private val options = SentryAndroidOptions()

    private fun configure(
        grantedAtMs: Long = 0L,
        tags: () -> Map<String, String> = { mapOf("client" to "android") },
    ) = TelemetryOptions.configure(options, SETTINGS, grantedAtMs, gate, tags)

    @Test
    fun `identifies the build and nothing else about the user`() {
        configure()

        assertEquals(SETTINGS.dsn, options.dsn)
        assertEquals("production", options.environment)
        assertEquals("tday-android@0.8.0", options.release)
        assertEquals("80000", options.dist)
        // The SDK appends a per-dsn folder, so the purge target is the parent it is told about.
        assertTrue(options.cacheDirPath!!.startsWith(SETTINGS.cacheDirPath))
    }

    @Test
    fun `sends no performance data of any kind`() {
        configure()

        assertEquals(0.0, options.tracesSampleRate!!, 0.0)
        assertNull(options.tracesSampler)
        assertFalse(options.isEnableAutoActivityLifecycleTracing)
        assertFalse(options.isEnableFramesTracking)
        assertFalse(options.isEnableUserInteractionTracing)
        assertFalse(options.isEnableTimeToFullDisplayTracing)
        assertFalse(options.isEnableStandaloneAppStartTracing)
        assertFalse(options.isEnablePerformanceV2)
        assertNull(options.profilesSampleRate)
        assertNull(options.profileSessionSampleRate)
        assertFalse(options.isEnableAppStartProfiling)
    }

    @Test
    fun `sends no session pings and no client reports`() {
        configure()

        assertFalse(options.isEnableAutoSessionTracking)
        assertFalse(options.isSendClientReports)
    }

    @Test
    fun `sends trace headers to nobody, so the owner's key never reaches a self hosted server`() {
        configure()

        assertTrue(options.tracePropagationTargets.isEmpty())
        assertFalse(options.isPropagateTraceparent)
    }

    @Test
    fun `collects nothing the data collection block can switch off`() {
        configure()

        val data = options.dataCollection
        assertEquals(false, data.userInfo)
        assertEquals(false, data.databaseQueryData)
        assertEquals(false, data.filePaths)
        assertEquals(emptySet<io.sentry.HttpBodyType>(), data.httpBodies)
        assertEquals(io.sentry.KeyValueCollectionBehavior.Mode.OFF, data.cookies!!.mode)
        assertEquals(io.sentry.KeyValueCollectionBehavior.Mode.OFF, data.urlQueryParams!!.mode)
        assertEquals(io.sentry.KeyValueCollectionBehavior.Mode.OFF, data.httpHeaders.request!!.mode)
        assertEquals(io.sentry.KeyValueCollectionBehavior.Mode.OFF, data.httpHeaders.response!!.mode)
        assertEquals(false, data.graphql.document)
        assertEquals(false, data.graphql.variables)
        @Suppress("DEPRECATION")
        assertFalse(options.isSendDefaultPii)
    }

    @Test
    fun `attaches no pictures, no view tree, no host name, no replay`() {
        configure()

        assertFalse(options.isAttachScreenshot)
        assertFalse(options.isAttachViewHierarchy)
        assertFalse(options.isAttachServerName)
        assertEquals(0.0, options.sessionReplay.sessionSampleRate!!, 0.0)
        assertEquals(0.0, options.sessionReplay.onErrorSampleRate!!, 0.0)
        assertFalse(options.logs.isEnabled)
        assertFalse(options.metrics.isEnabled)
        assertFalse(options.isEnableRootCheck)
    }

    @Test
    fun `keeps the failure detectors on and the history replays off`() {
        configure()

        assertTrue(options.isEnableUncaughtExceptionHandler)
        assertTrue(options.isAnrEnabled)
        assertTrue(options.isEnableNdk)
        assertTrue(options.isCollectAdditionalContext)
        assertFalse(options.isReportHistoricalAnrs)
        assertFalse(options.isTombstoneEnabled)
        assertFalse(options.isReportHistoricalTombstones)
        assertFalse(options.isAttachRawTombstone)
        assertFalse(options.isAttachAnrThreadDump)
        assertFalse(options.isMemoryLimiterEnabled)
        assertFalse(options.isReportHistoricalMemoryLimiterExits)
    }

    @Test
    fun `turns off the breadcrumbs that record what the user touched`() {
        configure()

        assertFalse(options.isEnableUserInteractionBreadcrumbs)
        assertFalse(options.isEnableSystemEventBreadcrumbs)
        assertFalse(options.isEnableSystemEventBreadcrumbsExtras)
    }

    @Test
    fun `routes every envelope through the gated transport`() {
        configure()

        assertTrue(options.transportFactory is GatedTransportFactory)
    }

    @Test
    fun `asks the gate again when a queued envelope is about to be sent`() {
        configure()

        assertTrue(options.transportGate.isConnected)
        gate.close()
        assertFalse(options.transportGate.isConnected)
    }

    @Test
    fun `keeps the connectivity check the sdk would have installed for itself`() {
        configure()
        val connectivity = mockk<IConnectionStatusProvider>()
        options.connectionStatusProvider = connectivity

        for ((status, sends) in mapOf(
            ConnectionStatus.CONNECTED to true,
            ConnectionStatus.UNKNOWN to true,
            ConnectionStatus.NO_PERMISSION to true,
            ConnectionStatus.DISCONNECTED to false,
        )) {
            every { connectivity.connectionStatus } returns status
            assertEquals("$status", sends, options.transportGate.isConnected)
        }
    }

    // ── The callbacks ───────────────────────────────────────────────────────────────────────

    private fun failure(at: Long = 10_000L) = SentryEvent(Date(at)).apply {
        user = User().apply { id = "install"; ipAddress = "{{auto}}" }
    }

    @Test
    fun `beforeSend scrubs and stamps an event from a consenting session`() {
        configure(grantedAtMs = 5_000L)

        val sent = options.beforeSend!!.execute(failure(at = 10_000L), Hint())

        assertNotNull(sent)
        assertNull(sent!!.user)
        assertEquals("android", sent.getTag("client"))
    }

    @Test
    fun `beforeSend drops an event that happened before the user said yes`() {
        configure(grantedAtMs = 5_000L)

        assertNull(options.beforeSend!!.execute(failure(at = 4_999L), Hint()))
    }

    @Test
    fun `beforeSend keeps an event stamped at the very moment of consent`() {
        configure(grantedAtMs = 5_000L)

        assertNotNull(options.beforeSend!!.execute(failure(at = 5_000L), Hint()))
    }

    @Test
    fun `beforeSend drops everything once the gate is shut`() {
        configure(grantedAtMs = 5_000L)
        gate.close()

        assertNull(options.beforeSend!!.execute(failure(), Hint()))
    }

    @Test
    fun `beforeSend reads the tags fresh for every event`() {
        var mode = "local"
        configure { mapOf("mode" to mode) }

        assertEquals("local", options.beforeSend!!.execute(failure(), Hint())!!.getTag("mode"))
        mode = "server"
        assertEquals("server", options.beforeSend!!.execute(failure(), Hint())!!.getTag("mode"))
    }

    @Test
    fun `drops every transaction, log and metric, because nothing here may send performance data`() {
        configure()

        assertNull(options.beforeSendTransaction!!.execute(mockk(relaxed = true), Hint()))
        assertNull(options.logs.beforeSend!!.execute(mockk(relaxed = true)))
        assertNull(options.metrics.beforeSend!!.execute(mockk(relaxed = true), Hint()))
    }

    @Test
    fun `beforeBreadcrumb filters through the scrubber and drops everything once the gate is shut`() {
        configure()
        val click = Breadcrumb().apply { category = "ui.click" }
        val lifecycle = Breadcrumb().apply { category = "app.lifecycle" }

        assertNull(options.beforeBreadcrumb!!.execute(click, Hint()))
        assertNotNull(options.beforeBreadcrumb!!.execute(lifecycle, Hint()))

        gate.close()
        assertNull(options.beforeBreadcrumb!!.execute(lifecycle, Hint()))
    }

    @Test
    fun `the timestamp gate keeps events at or after consent only`() {
        assertTrue(predatesConsent(eventTimestampMs = 4_999L, grantedAtMs = 5_000L))
        assertFalse(predatesConsent(eventTimestampMs = 5_000L, grantedAtMs = 5_000L))
        assertFalse(predatesConsent(eventTimestampMs = 5_001L, grantedAtMs = 5_000L))
    }

    private companion object {
        val SETTINGS = TelemetrySettings(
            dsn = "https://publickey@o123.ingest.sentry.io/456",
            environment = "production",
            release = "tday-android@0.8.0",
            dist = "80000",
            appVersion = "0.8.0",
            cacheDirPath = "/data/user/0/com.ohmz.tday.compose/cache/sentry",
        )
    }
}
