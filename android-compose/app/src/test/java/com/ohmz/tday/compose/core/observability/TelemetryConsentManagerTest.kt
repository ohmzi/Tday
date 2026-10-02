package com.ohmz.tday.compose.core.observability

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryConsentManagerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val store = TelemetryConsentStore(FakeSharedPreferences())
    private val gate = TelemetryGate()
    private val sdkEvents = mutableListOf<String>()
    private val sdk = object : TelemetrySdk {
        override fun start(grantedAtMs: Long) {
            sdkEvents += "start"
        }

        override fun stop() {
            sdkEvents += "stop"
        }
    }

    private fun manager(dsn: String = "https://publickey@o1.ingest.sentry.io/2"): TelemetryConsentManager {
        val bootstrap = TelemetryBootstrap(
            store = store,
            gate = gate,
            sdk = sdk,
            dsn = dsn,
            sentryCacheDir = File(temp.root, "cache/sentry"),
            installationFile = File(temp.root, "files/INSTALLATION"),
            clock = { 1_000L },
        )
        return TelemetryConsentManager(bootstrap, UnconfinedTestDispatcher())
    }

    @Test
    fun `starts from whatever the device already answered`() {
        store.grant(nowMs = 5L)

        assertEquals(TelemetryConsentState.GRANTED, manager().state.value)
    }

    @Test
    fun `a build without a dsn is not available`() {
        assertTrue(manager().isAvailable)
        assertFalse(manager(dsn = "").isAvailable)
    }

    @Test
    fun `saying yes starts the sdk and publishes the new state`() {
        val manager = manager()

        manager.setShareReports(true)

        assertEquals(TelemetryConsentState.GRANTED, manager.state.value)
        assertEquals(listOf("start"), sdkEvents)
        assertTrue(gate.isOpen)
    }

    @Test
    fun `saying no after a yes stops the sdk and publishes the new state`() {
        val manager = manager()
        manager.setShareReports(true)

        manager.setShareReports(false)

        assertEquals(TelemetryConsentState.DENIED, manager.state.value)
        assertEquals(listOf("start", "stop"), sdkEvents)
        assertFalse(gate.isOpen)
    }

    @Test
    fun `the last tap wins when answers arrive faster than they are applied`() {
        val manager = manager()

        listOf(true, false, true).forEach(manager::setShareReports)

        assertEquals(TelemetryConsentState.GRANTED, manager.state.value)
        assertEquals(TelemetryConsentState.GRANTED, store.state())
        assertTrue(gate.isOpen)
    }
}
