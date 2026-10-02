package com.ohmz.tday.compose.feature.telemetry

import com.ohmz.tday.compose.core.observability.TelemetryConsentState
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryConsentGateTest {
    private fun shows(
        available: Boolean = true,
        state: TelemetryConsentState = TelemetryConsentState.UNANSWERED,
        deferredThisSession: Boolean = false,
        workspaceOpen: Boolean = true,
        aHigherGateIsUp: Boolean = false,
    ) = shouldShowTelemetryCard(available, state, deferredThisSession, workspaceOpen, aHigherGateIsUp)

    @Test
    fun `shows once, in an open workspace, to a device nobody has asked yet`() {
        assertEquals(true, shows())
    }

    @Test
    fun `never shows on a build without a dsn`() {
        assertEquals(false, shows(available = false))
    }

    @Test
    fun `never shows again once the question has been answered, either way`() {
        assertEquals(false, shows(state = TelemetryConsentState.GRANTED))
        assertEquals(false, shows(state = TelemetryConsentState.DENIED))
    }

    @Test
    fun `stays away for the rest of the session once deferred`() {
        assertEquals(false, shows(deferredThisSession = true))
    }

    @Test
    fun `waits for a workspace, so it never appears over the sign in wizard`() {
        assertEquals(false, shows(workspaceOpen = false))
    }

    @Test
    fun `yields to update required, security questions and the app lock`() {
        assertEquals(false, shows(aHigherGateIsUp = true))
    }
}
