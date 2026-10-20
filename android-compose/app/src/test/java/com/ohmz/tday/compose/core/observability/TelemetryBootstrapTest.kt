package com.ohmz.tday.compose.core.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TelemetryBootstrapTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val preferences = FakeSharedPreferences()
    private val store = TelemetryConsentStore(preferences)
    private val gate = TelemetryGate()
    private var now = 1_000L
    private val persistFailures = mutableListOf<String>()

    private lateinit var sentryDir: File
    private lateinit var installationFile: File
    private lateinit var sdk: FakeSdk

    @Before
    fun setUp() {
        sentryDir = File(temp.root, "cache/sentry")
        installationFile = File(temp.root, "files/INSTALLATION")
        sdk = FakeSdk(gate, sentryDir)
    }

    private fun bootstrap(dsn: String = DSN) = TelemetryBootstrap(
        store = store,
        gate = gate,
        sdk = sdk,
        dsn = dsn,
        sentryCacheDir = sentryDir,
        installationFile = installationFile,
        clock = { now },
        onPersistFailure = { persistFailures += it },
    )

    /** What an earlier run of the SDK leaves behind: envelopes, native crash state, the install id. */
    private fun leaveSdkResidue() {
        File(sentryDir, "abc123/outbox").mkdirs()
        File(sentryDir, "abc123/outbox/crash.envelope").writeText("{}")
        File(sentryDir, "last_anr_report").writeText("1")
        installationFile.parentFile.mkdirs()
        installationFile.writeText("0f8fad5bd9cb469fa16570867728950e")
    }

    private fun assertResidueGone() {
        assertFalse(sentryDir.exists())
        assertFalse(installationFile.exists())
    }

    @Test
    fun `launch with no answer starts nothing and purges what an older build left`() {
        leaveSdkResidue()

        bootstrap().start()

        assertEquals(emptyList<String>(), sdk.events)
        assertFalse(gate.isOpen)
        assertResidueGone()
    }

    @Test
    fun `launch after a no starts nothing and purges`() {
        store.deny()
        leaveSdkResidue()

        bootstrap().start()

        assertEquals(emptyList<String>(), sdk.events)
        assertFalse(gate.isOpen)
        assertResidueGone()
    }

    @Test
    fun `launch after a yes starts the sdk with the grant time and keeps its files`() {
        store.grant(nowMs = 500L)
        leaveSdkResidue()

        bootstrap().start()

        assertEquals(listOf("start@500"), sdk.events)
        assertTrue(gate.isOpen)
        assertTrue(File(sentryDir, "abc123/outbox/crash.envelope").exists())
    }

    @Test
    fun `launch opens the gate before the sdk starts so its own startup events are not dropped`() {
        store.grant(nowMs = 500L)

        bootstrap().start()

        assertTrue(sdk.gateOpenAtStart)
    }

    @Test
    fun `no dsn means nothing starts and nothing is touched, even after a yes`() {
        store.grant(nowMs = 500L)
        leaveSdkResidue()

        bootstrap(dsn = "").start()

        assertEquals(emptyList<String>(), sdk.events)
        assertFalse(gate.isOpen)
        assertTrue(sentryDir.exists())
    }

    @Test
    fun `a sdk that fails to start does not take the app down and leaves the gate shut`() {
        store.grant(nowMs = 500L)
        sdk.failOnStart = true

        bootstrap().start()

        assertFalse(gate.isOpen)
    }

    @Test
    fun `saying yes purges first, then records the moment and starts fresh`() {
        leaveSdkResidue()
        val bootstrap = bootstrap()

        bootstrap.apply(granted = true)

        assertEquals(TelemetryConsentState.GRANTED, store.state())
        assertEquals(now, store.grantedAtMs())
        assertEquals(listOf("start@$now"), sdk.events)
        assertFalse("the SDK must start on an empty cache", sdk.cacheExistedAtStart)
        assertTrue(gate.isOpen)
    }

    @Test
    fun `saying yes twice neither restarts the sdk nor moves the grant time`() {
        val bootstrap = bootstrap()
        bootstrap.apply(granted = true)
        now = 9_000L

        bootstrap.apply(granted = true)

        assertEquals(listOf("start@1000"), sdk.events)
        assertEquals(1_000L, store.grantedAtMs())
    }

    @Test
    fun `saying no after a yes shuts the gate, then stops the sdk, then purges`() {
        store.grant(nowMs = 500L)
        val bootstrap = bootstrap()
        bootstrap.start()
        // What the running SDK writes between start and stop.
        File(sentryDir, "abc123/outbox").mkdirs()
        File(sentryDir, "abc123/outbox/pending.envelope").writeText("{}")

        bootstrap.apply(granted = false)

        assertEquals(listOf("start@500", "stop"), sdk.events)
        assertFalse("the gate must already be shut when the sdk is told to stop", sdk.gateOpenAtStop)
        assertFalse(gate.isOpen)
        assertEquals(TelemetryConsentState.DENIED, store.state())
        assertEquals(0L, store.grantedAtMs())
        assertFalse("anything waiting to be sent must be deleted", sentryDir.exists())
    }

    @Test
    fun `the no is persisted and the purge still runs when stopping the sdk throws`() {
        store.grant(nowMs = 500L)
        leaveSdkResidue()
        val bootstrap = bootstrap()
        bootstrap.start()
        sdk.failOnStop = true

        bootstrap.apply(granted = false)

        assertEquals(TelemetryConsentState.DENIED, store.state())
        assertFalse(gate.isOpen)
        assertResidueGone()
    }

    @Test
    fun `a no that fails to reach the disk is retried once and still reports success`() {
        store.grant(nowMs = 500L)
        val bootstrap = bootstrap()
        bootstrap.start()
        preferences.failingCommits = 1

        val persisted = bootstrap.apply(granted = false)

        assertTrue(persisted)
        assertEquals(emptyList<String>(), persistFailures)
        assertEquals(TelemetryConsentState.DENIED, store.state())
        assertFalse(gate.isOpen)
    }

    @Test
    fun `a no that cannot be written still turns everything off in this process and says so`() {
        store.grant(nowMs = 500L)
        leaveSdkResidue()
        val bootstrap = bootstrap()
        bootstrap.start()
        preferences.failingCommits = 2

        val persisted = bootstrap.apply(granted = false)

        assertFalse(persisted)
        assertEquals(listOf("consent.deny.not_persisted"), persistFailures)
        assertEquals(TelemetryConsentState.DENIED, store.state())
        assertEquals(listOf("start@500", "stop"), sdk.events)
        assertFalse(gate.isOpen)
        assertResidueGone()
    }

    @Test
    fun `a yes that cannot be written does not start the sdk and leaves the answer off`() {
        preferences.failingCommits = 1

        val persisted = bootstrap().apply(granted = true)

        assertFalse(persisted)
        assertEquals(listOf("consent.grant.not_persisted"), persistFailures)
        assertEquals(emptyList<String>(), sdk.events)
        assertFalse(gate.isOpen)
        assertEquals(TelemetryConsentState.DENIED, store.state())
    }

    @Test
    fun `a first no on a build that never started the sdk is still recorded and purged`() {
        leaveSdkResidue()

        bootstrap().apply(granted = false)

        assertEquals(TelemetryConsentState.DENIED, store.state())
        assertResidueGone()
    }

    @Test
    fun `an answer on a build without a dsn is ignored rather than stored for a later build`() {
        bootstrap(dsn = "").apply(granted = true)

        assertEquals(TelemetryConsentState.UNANSWERED, store.state())
        assertEquals(emptyList<String>(), sdk.events)
    }

    @Test
    fun `reports whether a dsn is configured`() {
        assertTrue(bootstrap().isAvailable)
        assertFalse(bootstrap(dsn = "  ").isAvailable)
    }

    private class FakeSdk(private val gate: TelemetryGate, private val sentryDir: File) : TelemetrySdk {
        val events = mutableListOf<String>()
        var gateOpenAtStart = false
        var gateOpenAtStop = true
        var cacheExistedAtStart = true
        var failOnStart = false
        var failOnStop = false

        override fun start(grantedAtMs: Long) {
            gateOpenAtStart = gate.isOpen
            cacheExistedAtStart = sentryDir.exists()
            if (failOnStart) error("invalid dsn")
            events += "start@$grantedAtMs"
        }

        override fun stop() {
            gateOpenAtStop = gate.isOpen
            events += "stop"
            if (failOnStop) error("close failed")
        }
    }

    private companion object {
        const val DSN = "https://publickey@o123.ingest.sentry.io/456"
    }
}
