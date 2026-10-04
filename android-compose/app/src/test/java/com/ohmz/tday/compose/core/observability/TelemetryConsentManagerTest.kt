package com.ohmz.tday.compose.core.observability

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    fun `a new connect flow asks again even though the device answered on the last one`() {
        val manager = manager()
        manager.beginConnectFlow()
        manager.setShareReports(true)
        assertTrue(manager.answeredInConnectFlow.value)

        // Sign out and sign in again: the answer the last flow was given is not an answer for this
        // one, so the wizard's last step comes due again.
        manager.beginConnectFlow()

        assertFalse(manager.answeredInConnectFlow.value)
        // The device answer itself is untouched: Settings still reads it, and the SDK still obeys it.
        assertEquals(TelemetryConsentState.GRANTED, manager.state.value)
        assertEquals(TelemetryConsentState.GRANTED, store.state())
        assertTrue(gate.isOpen)
    }

    @Test
    fun `a new connect flow makes the question due without answering it or letting anything out`() {
        val manager = manager()

        manager.beginConnectFlow()

        // Due: the wizard has to ask...
        assertFalse(manager.answeredInConnectFlow.value)
        // ...and unanswered is still not a yes: nothing was granted, nothing started, and the gate
        // that stands in front of every report is still shut.
        assertEquals(TelemetryConsentState.UNANSWERED, manager.state.value)
        assertEquals(TelemetryConsentState.UNANSWERED, store.state())
        assertEquals(0L, store.grantedAtMs())
        assertEquals(emptyList<String>(), sdkEvents)
        assertFalse(gate.isOpen)
    }

    @Test
    fun `saying yes again on a new flow answers that flow without a second sdk start`() {
        store.grant(nowMs = 5L)
        val manager = manager()
        manager.beginConnectFlow()

        manager.setShareReports(true)

        assertTrue(manager.answeredInConnectFlow.value)
        assertEquals(TelemetryConsentState.GRANTED, manager.state.value)
        // The store already said yes, so there was nothing to write and no second SDK to start.
        assertEquals(emptyList<String>(), sdkEvents)
    }

    @Test
    fun `answers are applied in the order they were given even when applying one is slow`() {
        // The SDK blocks on a latch for its first transition, so every later tap is queued behind
        // it on a real thread pool. A consumer that launched one coroutine per tap would let those
        // queued taps race for the bootstrap lock and apply in whatever order the threads woke.
        val taps = List(12) { it % 2 == 0 }
        val executor = Executors.newFixedThreadPool(taps.size + 2)
        try {
            repeat(TRIALS) {
                val trialStore = TelemetryConsentStore(FakeSharedPreferences())
                val trialGate = TelemetryGate()
                val events = CopyOnWriteArrayList<String>()
                val firstTransitionMayFinish = CountDownLatch(1)
                val slowSdk = object : TelemetrySdk {
                    override fun start(grantedAtMs: Long) = transition("start")

                    override fun stop() = transition("stop")

                    private fun transition(name: String) {
                        if (events.isEmpty()) firstTransitionMayFinish.await()
                        Thread.sleep(1)
                        events += name
                    }
                }
                val bootstrap = TelemetryBootstrap(
                    store = trialStore,
                    gate = trialGate,
                    sdk = slowSdk,
                    dsn = "https://publickey@o1.ingest.sentry.io/2",
                    sentryCacheDir = File(temp.newFolder(), "sentry"),
                    installationFile = File(temp.newFolder(), "INSTALLATION"),
                    clock = { 1_000L },
                )
                val manager = TelemetryConsentManager(bootstrap, executor.asCoroutineDispatcher())

                taps.forEach(manager::setShareReports)
                firstTransitionMayFinish.countDown()

                val expected = taps.map { if (it) "start" else "stop" }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                while (events.size < expected.size && System.nanoTime() < deadline) Thread.sleep(5)

                assertEquals(expected, events.toList())
                assertEquals(TelemetryConsentState.DENIED, manager.state.value)
                assertEquals(TelemetryConsentState.DENIED, trialStore.state())
                assertFalse(trialGate.isOpen)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private companion object {
        const val TRIALS = 15
    }
}
