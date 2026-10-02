package com.ohmz.tday.compose.core.observability

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryConsentStoreTest {
    private val preferences = FakeSharedPreferences()
    private val store = TelemetryConsentStore(preferences)

    @Test
    fun `a fresh install is unanswered and behaves as off`() {
        assertEquals(TelemetryConsentState.UNANSWERED, store.state())
        assertEquals(0L, store.grantedAtMs())
    }

    @Test
    fun `granting stores the answer and the moment it was given`() {
        store.grant(nowMs = 1_700_000_000_000L)

        assertEquals(TelemetryConsentState.GRANTED, store.state())
        assertEquals(1_700_000_000_000L, store.grantedAtMs())
    }

    @Test
    fun `denying stores the answer and forgets the grant timestamp`() {
        store.grant(nowMs = 1_700_000_000_000L)

        store.deny()

        assertEquals(TelemetryConsentState.DENIED, store.state())
        assertEquals(0L, store.grantedAtMs())
    }

    @Test
    fun `a grant with no timestamp is not a grant`() {
        // The timestamp is what keeps an earlier ANR from being replayed after opt-in, so a
        // `granted` that lost it (a half-written or hand-edited file) must read as unanswered
        // rather than as consent that predates everything.
        preferences.edit().putString("state", "granted").apply()

        assertEquals(TelemetryConsentState.UNANSWERED, store.state())
    }

    @Test
    fun `an unknown stored value reads as unanswered`() {
        preferences.edit().putString("state", "maybe").apply()

        assertEquals(TelemetryConsentState.UNANSWERED, store.state())
    }

    @Test
    fun `a later answer replaces an earlier one`() {
        store.deny()
        store.grant(nowMs = 42L)
        assertEquals(TelemetryConsentState.GRANTED, store.state())

        store.deny()
        assertEquals(TelemetryConsentState.DENIED, store.state())
    }
}
