package com.ohmz.tday.compose.feature.telemetry

import com.ohmz.tday.compose.core.observability.FakeSharedPreferences
import com.ohmz.tday.compose.core.observability.TelemetryBootstrap
import com.ohmz.tday.compose.core.observability.TelemetryConsentManager
import com.ohmz.tday.compose.core.observability.TelemetryConsentState
import com.ohmz.tday.compose.core.observability.TelemetryConsentStore
import com.ohmz.tday.compose.core.observability.TelemetryGate
import com.ohmz.tday.compose.core.observability.TelemetrySdk
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
class TelemetryConsentViewModelTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val store = TelemetryConsentStore(FakeSharedPreferences())
    private val sdkEvents = mutableListOf<String>()
    private val sdk = object : TelemetrySdk {
        override fun start(grantedAtMs: Long) {
            sdkEvents += "start"
        }

        override fun stop() {
            sdkEvents += "stop"
        }
    }

    private fun viewModel(dsn: String = "https://publickey@o1.ingest.sentry.io/2"): TelemetryConsentViewModel {
        val bootstrap = TelemetryBootstrap(
            store = store,
            gate = TelemetryGate(),
            sdk = sdk,
            dsn = dsn,
            sentryCacheDir = File(temp.root, "cache/sentry"),
            installationFile = File(temp.root, "files/INSTALLATION"),
            clock = { 1_000L },
        )
        return TelemetryConsentViewModel(TelemetryConsentManager(bootstrap, UnconfinedTestDispatcher()))
    }

    @Test
    fun `deferring the card changes nothing but the flag that hides it`() {
        val viewModel = viewModel()
        assertFalse(viewModel.cardDeferred.value)

        viewModel.deferCard()

        assertTrue(viewModel.cardDeferred.value)
        assertEquals(TelemetryConsentState.UNANSWERED, viewModel.state.value)
        assertEquals(emptyList<String>(), sdkEvents)
    }

    @Test
    fun `an answer from the card is the answer the settings row shows`() {
        val card = viewModel()

        card.setShareReports(true)

        assertEquals(TelemetryConsentState.GRANTED, card.state.value)
        assertEquals(listOf("start"), sdkEvents)
        // A second screen builds its own view model over the same device answer.
        assertEquals(TelemetryConsentState.GRANTED, viewModel().state.value)
    }

    @Test
    fun `is not available on a build without a dsn`() {
        assertTrue(viewModel().isAvailable)
        assertFalse(viewModel(dsn = "").isAvailable)
    }
}
