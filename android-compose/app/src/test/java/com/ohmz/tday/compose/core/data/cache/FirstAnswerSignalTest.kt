package com.ohmz.tday.compose.core.data.cache

import com.ohmz.tday.compose.core.data.SecureConfigStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of the empty-state fix that [com.ohmz.tday.compose.core.ui.feedAnswer]
 * cannot speak for.
 *
 * `feedAnswer` takes `firstAnswerLanded` as a Boolean, so every test of it states
 * the term rather than producing it -- and a test that hard-codes
 * `firstAnswerLanded = true` and calls itself "an empty Local Mode workspace is
 * answered from its first frame" is asserting nothing about Local Mode at all. It
 * is a byte-identical copy of the plain empty-state assertion next to it, and
 * deleting the `isLocalMode()` line this file exists for left the whole Android
 * suite green.
 *
 * That line is the correction the fix needed and the regression it is one edit
 * from: Local Mode has no server, deliberately never records a successful sync
 * (`SyncManager`'s no-op path, `AppViewModel.enterLocalWorkspace`, and
 * `OfflineCacheManager.saveOfflineState` all hold `lastSuccessfulSyncEpochMs` at
 * zero), so a stamp read alone is false there for the life of the install. An
 * empty local workspace would sit in a row skeleton that never resolves and never
 * be allowed to say the one thing it actually knows.
 *
 * So this file tests the function, not a value someone typed in its place.
 */
class FirstAnswerSignalTest {

    @Test
    fun `an empty Local Mode workspace has its answer before Room is even asked`() {
        // The escape hatch, stated as the two facts that make it necessary: the
        // stamp is zero, and the answer is still yes.
        val cache = fakeCache(lastSuccessfulSyncEpochMs = 0L)
        val signal = FirstAnswerSignal(
            secureConfigStore = fakeStore(isLocalMode = true),
            offlineCacheManager = cache,
        )

        assertTrue(
            "Local Mode never syncs, so a zero stamp there is not 'no answer yet'",
            signal.hasLanded(),
        )
    }

    @Test
    fun `a synced workspace with a zero stamp has not answered yet`() {
        // The overshoot this signal exists to prevent, and the reason the local
        // half may not simply be `return true`: on a real account a zero stamp
        // means the first sync has not landed, and the screens must draw the row
        // skeleton rather than tell someone they have no tasks.
        val signal = FirstAnswerSignal(
            secureConfigStore = fakeStore(isLocalMode = false),
            offlineCacheManager = fakeCache(lastSuccessfulSyncEpochMs = 0L),
        )

        assertFalse("a fresh install has heard nothing yet", signal.hasLanded())
    }

    @Test
    fun `a synced workspace with a stamp has answered, however empty it turned out to be`() {
        val signal = FirstAnswerSignal(
            secureConfigStore = fakeStore(isLocalMode = false),
            offlineCacheManager = fakeCache(lastSuccessfulSyncEpochMs = 1_726_000_000_000L),
        )

        assertTrue(signal.hasLanded())
    }

    @Test
    fun `a stamp read that throws is not an answer`() {
        // Same rule the ViewModels' failure paths state: the better of the two
        // wrong answers available is a placeholder, not "you have no tasks" on the
        // strength of a read that failed.
        val cache = mockk<OfflineCacheManager> {
            every { syncMetadataVersion } returns MutableStateFlow(0L)
            every { lastSuccessfulSyncEpochMsBlocking() } throws IllegalStateException("Room is gone")
        }
        val signal = FirstAnswerSignal(
            secureConfigStore = fakeStore(isLocalMode = false),
            offlineCacheManager = cache,
        )

        assertFalse(signal.hasLanded())
    }

    @Test
    fun `Local Mode is answered without touching the cache at all`() {
        // The order of the two terms, pinned. Asked the other way round this still
        // returns true in Local Mode -- and charges a Room read per hydrate per
        // live feed to reach an answer the prefs already had. The mock has no stub
        // for the read, so reaching it fails this test rather than passing it
        // slowly.
        val cache = mockk<OfflineCacheManager> {
            every { syncMetadataVersion } returns MutableStateFlow(0L)
        }
        val signal = FirstAnswerSignal(
            secureConfigStore = fakeStore(isLocalMode = true),
            offlineCacheManager = cache,
        )

        assertTrue(signal.hasLanded())
    }

    @Test
    fun `the version collected by the feeds is the metadata counter, not the row counter`() {
        // The whole point of exposing a flow here. `cacheDataVersion` only advances
        // on `hasUiDataChanges`, so the one case this signal exists for -- a first
        // sync against an EMPTY account -- moves it not at all, and a feed watching
        // it would sit in its skeleton for as long as the app stayed open.
        val metadataVersion = MutableStateFlow(7L)
        val rowVersion = MutableStateFlow(3L)
        val cache = mockk<OfflineCacheManager> {
            every { syncMetadataVersion } returns metadataVersion
            every { cacheDataVersion } returns rowVersion
        }

        val signal = FirstAnswerSignal(
            secureConfigStore = fakeStore(isLocalMode = false),
            offlineCacheManager = cache,
        )

        assertSame(metadataVersion, signal.version)
        assertEquals(7L, signal.version.value)
    }

    private fun fakeStore(isLocalMode: Boolean): SecureConfigStore = mockk(relaxed = true) {
        every { isLocalMode() } returns isLocalMode
    }

    private fun fakeCache(lastSuccessfulSyncEpochMs: Long): OfflineCacheManager = mockk {
        every { syncMetadataVersion } returns MutableStateFlow(0L)
        every { lastSuccessfulSyncEpochMsBlocking() } returns lastSuccessfulSyncEpochMs
    }
}
