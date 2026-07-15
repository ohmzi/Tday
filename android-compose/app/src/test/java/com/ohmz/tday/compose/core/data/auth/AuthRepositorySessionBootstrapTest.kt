package com.ohmz.tday.compose.core.data.auth

import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.network.TdayApiService
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Regression guard for the "server offline must not log you out" fix. At app launch,
 * [AuthRepository.restoreSessionForBootstrap] must keep the last cached session (run
 * offline) for ANY server-unreachable/unhealthy failure, and only surrender the session
 * on a genuine 401. Mirrors the iOS fix in restoreSessionForBootstrap().
 */
class AuthRepositorySessionBootstrapTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val cachedRaw = """{"id":"u1","username":"alice"}"""

    private fun errorResponse(code: Int): Response<JsonElement> =
        Response.error(code, "{}".toResponseBody("application/json".toMediaTypeOrNull()))

    private fun successResponse(body: String): Response<JsonElement> =
        Response.success(json.parseToJsonElement(body))

    private fun repo(
        api: TdayApiService,
        store: SecureConfigStore,
        cache: OfflineCacheManager = mockk(relaxed = true),
    ) = AuthRepository(api, json, store, cache)

    private fun storeWithCachedUser(): SecureConfigStore = mockk {
        every { getCachedSessionUserRaw() } returns cachedRaw
        every { clearCachedSessionUser() } just Runs
        every { saveCachedSessionUserRaw(any()) } just Runs
        every { getLastUsername() } returns null
    }

    @Test
    fun `5xx keeps the cached session offline instead of logging out`() = runBlocking {
        val api = mockk<TdayApiService> { coEvery { getSession() } returns errorResponse(500) }
        val store = storeWithCachedUser()

        val restored = repo(api, store).restoreSessionForBootstrap()

        assertEquals("u1", restored?.user?.id)
        assertTrue("must be flagged as an offline/cached restore", restored?.usedCachedSession == true)
        verify(exactly = 0) { store.clearCachedSessionUser() }
    }

    @Test
    fun `429 rate limit keeps the cached session`() = runBlocking {
        val api = mockk<TdayApiService> { coEvery { getSession() } returns errorResponse(429) }
        val store = storeWithCachedUser()

        val restored = repo(api, store).restoreSessionForBootstrap()

        assertEquals("u1", restored?.user?.id)
        assertTrue(restored?.usedCachedSession == true)
    }

    @Test
    fun `transport failure keeps the cached session`() = runBlocking {
        val api = mockk<TdayApiService> { coEvery { getSession() } throws IOException("connection refused") }
        val store = storeWithCachedUser()

        val restored = repo(api, store).restoreSessionForBootstrap()

        assertEquals("u1", restored?.user?.id)
        assertTrue(restored?.usedCachedSession == true)
    }

    @Test
    fun `genuine 401 logs out and clears the cached session`() = runBlocking {
        val api = mockk<TdayApiService> { coEvery { getSession() } returns errorResponse(401) }
        val store = mockk<SecureConfigStore>(relaxed = true)

        val restored = repo(api, store).restoreSessionForBootstrap()

        assertNull("a genuine 401 must surrender the session", restored)
        verify { store.clearCachedSessionUser() }
    }

    @Test
    fun `valid 200 session returns a fresh (non-cached) restore`() = runBlocking {
        val api = mockk<TdayApiService> {
            coEvery { getSession() } returns successResponse("""{"user":{"id":"u1","username":"alice"}}""")
        }
        val store = storeWithCachedUser()

        val restored = repo(api, store).restoreSessionForBootstrap()

        assertEquals("u1", restored?.user?.id)
        assertEquals("a live session is not a cached/offline restore", false, restored?.usedCachedSession)
    }
}
