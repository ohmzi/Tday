package com.ohmz.tday.compose.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * There is no device here, and the behaviour this pins — "does an upgrade re-register anybody?"
 * — is otherwise only observable on one. So the decision is a table and the table is walked.
 */
class UnifiedPushRegistrationTest {

    @Test
    fun `local mode never gets a distributor saved`() {
        assertEquals(
            UnifiedPushRegistrationAction.Idle,
            unifiedPushRegistrationAction(
                session = session(isLocalMode = true),
                distributors = distributors(installed = listOf(NTFY)),
                subscription = UnifiedPushSubscription(endpoint = null, subscribedUserId = null),
            ),
        )
    }

    @Test
    fun `nothing happens before there is a session to file the endpoint against`() {
        for (userId in listOf(null, "", "   ")) {
            assertEquals(
                "userId=$userId",
                UnifiedPushRegistrationAction.Idle,
                unifiedPushRegistrationAction(
                    session = UnifiedPushSession(
                        authenticated = true,
                        isLocalMode = false,
                        userId = userId,
                    ),
                    distributors = distributors(installed = listOf(NTFY)),
                    subscription = UnifiedPushSubscription(null, null),
                ),
            )
        }
        assertEquals(
            UnifiedPushRegistrationAction.Idle,
            unifiedPushRegistrationAction(
                session = session(authenticated = false),
                distributors = distributors(installed = listOf(NTFY)),
                subscription = UnifiedPushSubscription(null, null),
            ),
        )
    }

    @Test
    fun `one installed distributor is a choice that makes itself`() {
        assertEquals(
            UnifiedPushRegistrationAction.SaveDistributorAndRegister(NTFY),
            unifiedPushRegistrationAction(
                session = session(),
                distributors = distributors(installed = listOf(NTFY)),
                subscription = UnifiedPushSubscription(null, null),
            ),
        )
    }

    @Test
    fun `no distributor installed is not an error, it is nothing to do`() {
        assertEquals(
            UnifiedPushRegistrationAction.Idle,
            unifiedPushRegistrationAction(
                session = session(),
                distributors = distributors(installed = emptyList()),
                subscription = UnifiedPushSubscription(null, null),
            ),
        )
    }

    @Test
    fun `two installed distributors are never picked between automatically`() {
        val state = distributors(installed = listOf(NTFY, OTHER))
        assertEquals(
            UnifiedPushRegistrationAction.AwaitDistributorChoice,
            unifiedPushRegistrationAction(
                session = session(),
                distributors = state,
                subscription = UnifiedPushSubscription(null, null),
            ),
        )
        // The registrar's refusal and the Settings row's reason to exist are the same predicate;
        // if they ever disagreed the row would be the only thing on screen with nothing to do.
        assertTrue(needsDistributorChoice(state))
    }

    @Test
    fun `the choice row is gone the moment there is an answer on file`() {
        assertFalse(
            needsDistributorChoice(
                distributors(
                    ack = NTFY,
                    saved = NTFY,
                    installed = listOf(NTFY, OTHER),
                ),
            ),
        )
        assertFalse(
            needsDistributorChoice(distributors(saved = NTFY, installed = listOf(NTFY, OTHER))),
        )
        assertFalse(needsDistributorChoice(distributors(installed = listOf(NTFY))))
    }

    @Test
    fun `a chosen distributor that has not answered is asked again, not replaced`() {
        assertEquals(
            UnifiedPushRegistrationAction.RegisterWithSavedDistributor,
            unifiedPushRegistrationAction(
                session = session(),
                distributors = distributors(saved = NTFY, installed = listOf(NTFY, OTHER)),
                subscription = UnifiedPushSubscription(null, null),
            ),
        )
    }

    /**
     * The upgrade case, and the one the whole guard exists for: an install that was already
     * registered through the old Settings row wakes up with the row gone, and must not
     * re-register, must not be handed a new endpoint, and must not spend a request.
     */
    @Test
    fun `an already registered and confirmed device does nothing on every launch`() {
        assertEquals(
            UnifiedPushRegistrationAction.Idle,
            unifiedPushRegistrationAction(
                session = session(),
                distributors = distributors(ack = NTFY, saved = NTFY, installed = listOf(NTFY)),
                subscription = UnifiedPushSubscription(ENDPOINT, USER),
            ),
        )
    }

    @Test
    fun `an endpoint the backend has not been told about is sent, once`() {
        val registered = distributors(ack = NTFY, saved = NTFY, installed = listOf(NTFY))
        // Upgrading from the old row: the endpoint is on file, nothing recorded who it is for.
        assertEquals(
            UnifiedPushRegistrationAction.SubscribeStoredEndpoint(ENDPOINT, USER),
            unifiedPushRegistrationAction(
                session = session(),
                distributors = registered,
                subscription = UnifiedPushSubscription(ENDPOINT, subscribedUserId = null),
            ),
        )
        // And the same endpoint after someone else signs in on this device — the repair for a
        // backend row still pointing this device's push at the account that left.
        assertEquals(
            UnifiedPushRegistrationAction.SubscribeStoredEndpoint(ENDPOINT, USER),
            unifiedPushRegistrationAction(
                session = session(),
                distributors = registered,
                subscription = UnifiedPushSubscription(ENDPOINT, subscribedUserId = "someone-else"),
            ),
        )
    }

    @Test
    fun `an acknowledged distributor with no endpoint on file is asked for one again`() {
        assertEquals(
            UnifiedPushRegistrationAction.RegisterWithSavedDistributor,
            unifiedPushRegistrationAction(
                session = session(),
                distributors = distributors(ack = NTFY, saved = NTFY, installed = listOf(NTFY)),
                subscription = UnifiedPushSubscription(endpoint = null, subscribedUserId = null),
            ),
        )
    }

    private fun session(
        authenticated: Boolean = true,
        isLocalMode: Boolean = false,
        userId: String? = USER,
    ) = UnifiedPushSession(authenticated, isLocalMode, userId)

    private fun distributors(
        ack: String? = null,
        saved: String? = null,
        installed: List<String> = emptyList(),
    ) = UnifiedPushDistributorState(ack, saved, installed)

    private companion object {
        const val NTFY = "io.heckel.ntfy"
        const val OTHER = "org.unifiedpush.distributor.other"
        const val ENDPOINT = "https://ntfy.example/UP123"
        const val USER = "user-1"
    }
}
