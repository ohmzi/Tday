package com.ohmz.tday.compose.core.push

import android.content.Context
import android.util.Log
import com.ohmz.tday.compose.core.model.MessageResponse
import com.ohmz.tday.compose.core.model.PushUnsubscribeRequest
import com.ohmz.tday.compose.core.network.TdayApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Sign-out is the only part of the registrar that a user waits on: `AppViewModel.logout()` awaits
 * it before dropping the session, so whatever it does happens on the screen they are leaving. Its
 * budget is therefore a behaviour, not a detail — and it is one of the few things here that IS
 * provable without a device, because the path reaches neither the connector nor a real socket:
 * a prefs read, a mutex, and one Retrofit call that a mock can simply refuse to answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnifiedPushAutoRegistrarTest {

    private val context = mockk<Context>(relaxed = true)
    private val apiService = mockk<TdayApiService>()
    private val store = mockk<UnifiedPushPreferenceStore>(relaxed = true)

    /**
     * `android.util.Log` is an unimplemented stub under the JVM test runner and throws when
     * called, and the give-up path these tests exist to pin logs on its way out — so without
     * this the timeout test would fail on the platform's test double rather than on timing.
     */
    @Before
    fun stubAndroidLog() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun restoreAndroidLog() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `sign-out tells the backend and drops the marker`() = runTest {
        every { store.getEndpoint() } returns ENDPOINT
        coEvery { apiService.unsubscribePush(any()) } returns okResponse()

        registrar().onSignedOut()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            apiService.unsubscribePush(PushUnsubscribeRequest(endpoint = ENDPOINT))
        }
        // The marker is the half that has to go: it is what would otherwise tell the next
        // sign-in that this endpoint is already subscribed, for an account that has left.
        verify(exactly = 1) { store.clearSubscribedUserId() }
        assertEquals("a reachable server costs no wait", 0L, testScheduler.currentTime)
    }

    @Test
    fun `sign-out with nothing subscribed sends no request`() = runTest {
        every { store.getEndpoint() } returns null

        registrar().onSignedOut()
        advanceUntilIdle()

        coVerify(exactly = 0) { apiService.unsubscribePush(any()) }
    }

    /**
     * The regression this exists for. The budget has to cover ACQUIRING the mutex, not just the
     * request underneath it: the other holder is `subscribe()`, whose POST has no timeout of its
     * own, and the decision table hands out SubscribeStoredEndpoint on every foreground until
     * that POST succeeds — so on an unreachable server, the one that motivates the bound, there
     * is usually a subscribe in flight holding the lock.
     *
     * Two concurrent sign-outs stand in for "somebody else holds the lock" without reaching for
     * the connector's statics. Both start at t=0 against a server that never answers. Bounded
     * across acquisition, both are done at the budget. Bounded only around the request — the
     * shape that shipped — the second one waits out the first's full hold and then starts its
     * own, and virtual time reads twice the budget.
     */
    @Test
    fun `sign-out stays bounded while another caller holds the lock`() = runTest {
        every { store.getEndpoint() } returns ENDPOINT
        coEvery { apiService.unsubscribePush(any()) } coAnswers { awaitCancellation() }

        val registrar = registrar()
        launch { registrar.onSignedOut() }
        launch { registrar.onSignedOut() }
        advanceUntilIdle()

        assertEquals(SIGN_OUT_UNSUBSCRIBE_TIMEOUT_MS, testScheduler.currentTime)
        // Giving up on the request is not giving up on the local half: a caller that never even
        // reached the critical section still has to leave the marker cleared behind it.
        verify(atLeast = 2) { store.clearSubscribedUserId() }
    }

    private fun TestScope.registrar() = UnifiedPushAutoRegistrar(
        context = context,
        apiService = apiService,
        store = store,
        // Shares the test scheduler, so the timeout is virtual time rather than three real
        // seconds of a unit-test run.
        backgroundDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun okResponse(): Response<MessageResponse> = mockk(relaxed = true)

    private companion object {
        const val ENDPOINT = "https://ntfy.example/UP123"
    }
}
