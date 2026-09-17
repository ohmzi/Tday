package com.ohmz.tday.compose.core.data.settings

import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.model.PreferencesResponse
import com.ohmz.tday.compose.core.network.TdayApiService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/**
 * The launch cache a preference write has to leave behind, pinned against the response the
 * `/api/preferences` route used to send.
 *
 * `PreferencesResponse.defaultHomeScreen` is a non-null String defaulting to `"scheduled"` and
 * the JSON layer ignores unknown keys, so an acknowledgement body — the route answered with
 * `{"message": "preferences updated"}` — decodes cleanly to `"scheduled"` rather than failing.
 * Reading that field back into the cache is what made a device which had just been told Floater
 * cold-start on Scheduled (and what silently turned the AI summary back on behind a user who had
 * just switched it off): the value reached the account, and the device then overwrote its own
 * copy of it with a default. What these tests protect is the rule that a 200 is the authority for
 * what was stored, rather than a field that may not have been in the body at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    private val api = mockk<TdayApiService>()
    private val cacheManager = mockk<OfflineCacheManager>()
    private val secureConfigStore = mockk<SecureConfigStore>()

    private val repository = SettingsRepository(api, cacheManager, secureConfigStore)

    /** Stands in for the real cache: applies the transform to the state it is holding. */
    private fun cacheHolds(initial: OfflineSyncState): () -> OfflineSyncState {
        var state = initial
        coEvery { cacheManager.updateOfflineState(any()) } coAnswers {
            state = firstArg<(OfflineSyncState) -> OfflineSyncState>().invoke(state)
            state
        }
        return { state }
    }

    @Test
    fun `a home screen change survives a response that carries no preferences`() = runTest {
        every { secureConfigStore.isLocalMode() } returns false
        coEvery { api.patchPreferences(any()) } returns Response.success(PreferencesResponse())
        val cache = cacheHolds(OfflineSyncState())

        repository.setDefaultHomeScreen("floater")

        assertEquals("floater", cache().defaultHomeScreen)
    }

    @Test
    fun `an AI summary change survives a response that carries no preferences`() = runTest {
        every { secureConfigStore.isLocalMode() } returns false
        coEvery { api.patchPreferences(any()) } returns Response.success(PreferencesResponse())
        val cache = cacheHolds(OfflineSyncState())

        repository.setAiSummaryEnabled(false)

        assertEquals(false, cache().aiSummaryEnabled)
    }

    @Test
    fun `the mirrored value is the one the server accepted, not a differing response field`() = runTest {
        every { secureConfigStore.isLocalMode() } returns false
        // A body that does carry the field, disagreeing with the request. The enum is validated
        // before the write, so this cannot happen against the real route; the write is what the
        // cache mirrors either way.
        coEvery { api.patchPreferences(any()) } returns
            Response.success(PreferencesResponse(defaultHomeScreen = "scheduled"))
        val cache = cacheHolds(OfflineSyncState())

        repository.setDefaultHomeScreen("floater")

        assertEquals("floater", cache().defaultHomeScreen)
    }
}
