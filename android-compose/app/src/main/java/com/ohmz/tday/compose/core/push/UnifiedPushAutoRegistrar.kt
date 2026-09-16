package com.ohmz.tday.compose.core.push

import android.content.Context
import android.util.Log
import com.ohmz.tday.compose.core.coroutines.BackgroundDispatcher
import com.ohmz.tday.compose.core.model.PushSubscribeRequest
import com.ohmz.tday.compose.core.model.PushUnsubscribeRequest
import com.ohmz.tday.compose.core.network.TdayApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.unifiedpush.android.connector.UnifiedPush
import javax.inject.Inject
import javax.inject.Singleton

/** The wire value the backend stores against this device's row; also read by the receiver. */
internal const val UNIFIEDPUSH_TRANSPORT = "unifiedpush"

private const val TAG = "UnifiedPushRegistrar"

/** Long enough for a reachable server to answer, short enough that a dead one cannot stall leaving. */
private const val SIGN_OUT_UNSUBSCRIBE_TIMEOUT_MS = 3_000L

/**
 * The connector's three answers, read together so the decision sees one consistent snapshot.
 *
 * Not a passive read: asking for the saved distributor is what makes the connector notice that
 * the package is gone, and noticing is what broadcasts UNREGISTERED — which is how an uninstall
 * reaches `UnifiedPushReceiver.onUnregistered` and gets the endpoint deleted from the backend.
 * Until now the only thing that ever asked was the Settings row, so a user who uninstalled their
 * distributor left the server POSTing at a dead URL until they happened to open Settings. Asking
 * on every sign-in and every foreground is what turns "uninstall the distributor" into an off
 * switch that actually reaches the server.
 */
fun readUnifiedPushDistributorState(context: Context): UnifiedPushDistributorState =
    UnifiedPushDistributorState(
        ackDistributor = runCatching { UnifiedPush.getAckDistributor(context) }.getOrNull(),
        savedDistributor = runCatching { UnifiedPush.getSavedDistributor(context) }.getOrNull(),
        installed = runCatching { UnifiedPush.getDistributors(context) }.getOrDefault(emptyList()),
    )

/**
 * Drives [unifiedPushRegistrationAction] from the two moments that can change its answer: the
 * app learning who is signed in, and the app coming back to the foreground. The second is not a
 * duplicate of the first — installing a distributor is something the user does in another app,
 * with T'Day in the background, and a once-per-launch attempt would leave them registered with
 * nothing until the next cold start.
 *
 * Everything here is idempotent by construction, because it is called more often than it acts:
 * the table answers `Idle` for an install that is already registered and current, so a launch
 * costs a prefs read and a package query and no traffic at all.
 */
@Singleton
class UnifiedPushAutoRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: TdayApiService,
    private val store: UnifiedPushPreferenceStore,
    @BackgroundDispatcher private val backgroundDispatcher: CoroutineDispatcher,
) {
    // Both triggers can land at once — a foreground that also re-authenticates — and the two
    // halves of a registration (save the distributor, then broadcast REGISTER) must not interleave
    // with a second pass reading the state between them.
    private val mutex = Mutex()

    suspend fun ensureRegistered(session: UnifiedPushSession) {
        withContext(backgroundDispatcher) {
            mutex.withLock {
                val action = unifiedPushRegistrationAction(
                    session = session,
                    distributors = readUnifiedPushDistributorState(context),
                    subscription = UnifiedPushSubscription(
                        endpoint = store.getEndpoint(),
                        subscribedUserId = store.getSubscribedUserId(),
                    ),
                )
                apply(action)
            }
        }
    }

    /**
     * Signing out is the off switch a user actually reaches for, and it was silently missing:
     * nothing unsubscribed, so the backend kept this device's endpoint filed against the account
     * that left and kept pushing its task titles into this notification shade — while the next
     * account to sign in on the same device got no push at all, because the connector was still
     * acknowledged and so nothing re-registered. The unsubscribe has to run before the session is
     * dropped; the local marker goes with it so the next sign-in re-subscribes for whoever it is.
     *
     * The distributor registration itself is deliberately left alone: it is the device's, not the
     * account's, and tearing it down would cost the next sign-in a round trip through the
     * distributor for an endpoint it already has.
     */
    suspend fun onSignedOut() {
        withContext(backgroundDispatcher) {
            mutex.withLock {
                val endpoint = store.getEndpoint()
                store.clearSubscribedUserId()
                if (endpoint.isNullOrBlank()) return@withLock
                // Bounded, because sign-out is already waiting on one request and a user leaving
                // an unreachable server must not be held on the screen they are trying to leave.
                // Losing the race costs a stale row the server will fail to deliver to; the local
                // marker is already gone, so nothing here pretends the subscription survived.
                val sent = withTimeoutOrNull(SIGN_OUT_UNSUBSCRIBE_TIMEOUT_MS) {
                    runCatching {
                        apiService.unsubscribePush(PushUnsubscribeRequest(endpoint = endpoint))
                    }.onFailure { Log.w(TAG, "Failed to unsubscribe on sign-out: ${it.message}") }
                }
                if (sent == null) Log.w(TAG, "Sign-out unsubscribe timed out")
            }
        }
    }

    private suspend fun apply(action: UnifiedPushRegistrationAction) {
        when (action) {
            // AwaitDistributorChoice is a decision to do nothing, not an oversight: the Settings
            // row named by `needsDistributorChoice` is the only thing that may resolve it.
            UnifiedPushRegistrationAction.Idle,
            UnifiedPushRegistrationAction.AwaitDistributorChoice,
            -> Unit

            is UnifiedPushRegistrationAction.SaveDistributorAndRegister -> runCatching {
                // `registerApp` returns silently when no distributor has been SAVED — the save is
                // not bookkeeping for the choice, it is the half of registration that makes the
                // broadcast happen at all.
                UnifiedPush.saveDistributor(context, action.distributor)
                UnifiedPush.registerApp(context)
            }.onFailure { Log.w(TAG, "Failed to register with a distributor: ${it.message}") }

            UnifiedPushRegistrationAction.RegisterWithSavedDistributor -> runCatching {
                UnifiedPush.registerApp(context)
            }.onFailure { Log.w(TAG, "Failed to re-register with the saved distributor: ${it.message}") }

            is UnifiedPushRegistrationAction.SubscribeStoredEndpoint -> subscribe(action)
        }
    }

    /**
     * The confirming POST. `onNewEndpoint` already sends one the moment an endpoint arrives, but
     * it cannot record who it was for, and a subscribe that failed offline leaves no trace there
     * — so this runs until the backend has been told, and only then writes the marker that stops
     * it running again. The backend's subscribe is a delete-then-insert on (user, endpoint) inside
     * one transaction, so a repeat is a rewrite of the same row, never a second one.
     */
    private suspend fun subscribe(action: UnifiedPushRegistrationAction.SubscribeStoredEndpoint) {
        runCatching {
            apiService.subscribePush(
                PushSubscribeRequest(endpoint = action.endpoint, transport = UNIFIEDPUSH_TRANSPORT),
            )
        }.onSuccess {
            store.setSubscribedUserId(action.userId)
        }.onFailure {
            Log.w(TAG, "Failed to confirm UnifiedPush endpoint: ${it.message}")
        }
    }
}
