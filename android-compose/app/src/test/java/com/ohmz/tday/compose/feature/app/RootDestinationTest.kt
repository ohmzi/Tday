package com.ohmz.tday.compose.feature.app

import com.ohmz.tday.compose.core.data.AppDataMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The root routing decision is a pure function of [AppUiState] so the cold-launch contract can be
 * pinned here: nothing user-facing — neither the workspace nor the sign-in wizard — is chosen
 * until the persisted session has actually been resolved. A signed-in user cold-launching must
 * never be shown sign-in, not even for the frame it takes bootstrap to answer.
 */
class RootDestinationTest {

    @Test
    fun `should hold the splash when the state is still at its cold-launch defaults`() {
        // This is the state every screen sees on the first composition after process start.
        assertEquals(RootDestination.SPLASH, AppUiState().rootDestination)
    }

    @Test
    fun `should never route to onboarding when the session is unresolved`() {
        // Every signed-out-looking shape the state can take before bootstrap has answered. Each
        // of these used to render the sign-in wizard the moment the home route was composed.
        val unresolved = listOf(
            AppUiState(),
            AppUiState(loading = true, authenticated = false),
            AppUiState(loading = false, authenticated = false),
            AppUiState(loading = true, authenticated = false, requiresServerSetup = true),
            AppUiState(
                loading = true,
                authenticated = false,
                requiresLogin = true,
                dataMode = AppDataMode.SERVER,
            ),
        )

        unresolved.forEach { state ->
            assertEquals(SessionResolution.UNKNOWN, state.sessionResolution)
            assertEquals(state.toString(), RootDestination.SPLASH, state.rootDestination)
        }
    }

    @Test
    fun `should route to the workspace when a server session has resolved`() {
        val state = AppUiState(
            loading = false,
            authenticated = true,
            dataMode = AppDataMode.SERVER,
            sessionResolution = SessionResolution.RESOLVED,
        )

        assertEquals(RootDestination.WORKSPACE, state.rootDestination)
    }

    @Test
    fun `should route to the workspace when Local Mode has resolved`() {
        val state = AppUiState(
            loading = false,
            authenticated = false,
            dataMode = AppDataMode.LOCAL,
            sessionResolution = SessionResolution.RESOLVED,
        )

        assertEquals(RootDestination.WORKSPACE, state.rootDestination)
    }

    @Test
    fun `should route to onboarding when the session resolved with no workspace`() {
        val noServer = AppUiState(
            loading = false,
            authenticated = false,
            requiresServerSetup = true,
            sessionResolution = SessionResolution.RESOLVED,
        )
        val signInRequired = AppUiState(
            loading = false,
            authenticated = false,
            requiresLogin = true,
            dataMode = AppDataMode.SERVER,
            sessionResolution = SessionResolution.RESOLVED,
        )

        assertEquals(RootDestination.ONBOARDING, noServer.rootDestination)
        assertEquals(RootDestination.ONBOARDING, signInRequired.rootDestination)
    }

    @Test
    fun `should keep onboarding rather than the splash when a re-bootstrap is in flight`() {
        // After a sign-in the wizard calls refreshSession(), which re-arms `loading`. The wizard
        // must stay up through that (it shows its own completing state); the splash must not
        // come back, so `loading` alone can never send the root back to SPLASH.
        val state = AppUiState(
            loading = true,
            authenticated = false,
            dataMode = AppDataMode.SERVER,
            sessionResolution = SessionResolution.RESOLVED,
        )

        assertEquals(RootDestination.ONBOARDING, state.rootDestination)
    }
}
