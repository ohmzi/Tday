package com.ohmz.tday.compose.core.push

/**
 * Registering with a UnifiedPush distributor used to be a Settings row the user had to find and
 * tap. It was labelled with a protocol name, and tapping it with nothing installed answered
 * "go install another app" — a dead end rather than a setting. The capability behind it is not
 * optional in the same way: it carries task-due reminders, "a list was shared with you", admin
 * security alerts, and the silent `data-changed` ping that wakes `WidgetSyncWorker` with this
 * process dead. So the control went and the decision moved here, where it is a table rather than
 * a tap.
 *
 * Kept free of Android imports — the connector calls, the prefs reads and the network POST all
 * live in [UnifiedPushAutoRegistrar] — so the whole table is walkable by a unit test on a
 * machine with no device, which is the only way any of this is provable here.
 */

/** Everything the decision needs to know about who is signed in. */
data class UnifiedPushSession(
    val authenticated: Boolean,
    val isLocalMode: Boolean,
    /**
     * The id the current session reports. It is only ever compared against the id recorded
     * beside the endpoint — the backend takes the user from the session cookie, never from
     * this — so an id that changes shape between an online and an offline sign-in costs one
     * idempotent re-subscribe, never a subscription filed against the wrong account.
     */
    val userId: String?,
)

/**
 * The connector's own three answers. [ackDistributor] and [savedDistributor] both return null
 * for a distributor that has been uninstalled (the connector re-checks `getDistributors` before
 * answering), which is what lets an uninstall read here as "no distributor" rather than as a
 * choice that can never be acted on again.
 */
data class UnifiedPushDistributorState(
    /** Saved AND it has answered us at least once: registration is live. */
    val ackDistributor: String?,
    /** Chosen, but the answer may still be outstanding. */
    val savedDistributor: String?,
    val installed: List<String>,
)

/** What this device last told the backend, and on whose behalf. */
data class UnifiedPushSubscription(
    val endpoint: String?,
    /**
     * Null until something that knows the signed-in user has confirmed the endpoint with the
     * backend. `onNewEndpoint` deliberately cannot write it: the receiver subscribes without a
     * session in hand, and recording a subscription that a failed POST never created would
     * suppress the retry forever.
     */
    val subscribedUserId: String?,
)

sealed interface UnifiedPushRegistrationAction {
    /** Registered and current, or nothing to register with. Either way, no work. */
    data object Idle : UnifiedPushRegistrationAction

    /** Exactly one distributor installed and none chosen: the choice makes itself. */
    data class SaveDistributorAndRegister(val distributor: String) : UnifiedPushRegistrationAction

    /**
     * A distributor is already saved, but either it has never answered or its answer is no
     * longer on file. Re-broadcasting REGISTER reuses the connector's persisted token, so the
     * distributor hands back the endpoint it already issued rather than minting a new one —
     * this is a retry, not a rotation.
     */
    data object RegisterWithSavedDistributor : UnifiedPushRegistrationAction

    /** The endpoint on file is not the one the backend holds for this user. Tell the backend. */
    data class SubscribeStoredEndpoint(val endpoint: String, val userId: String) :
        UnifiedPushRegistrationAction

    /**
     * Two or more distributors installed and none chosen. The automation stops here on purpose:
     * choosing a distributor is choosing which third-party server sees this device's push
     * traffic, and `getDistributors` returns package-manager resolve order, so `first()` would
     * be the app making a privacy decision by coin toss. [needsDistributorChoice] is the same
     * question asked by the one Settings row that survives.
     */
    data object AwaitDistributorChoice : UnifiedPushRegistrationAction
}

/**
 * The only state in which a human still has something to decide — and therefore the only state
 * in which Settings shows a push row at all. Shared by the registrar (which refuses to act on
 * it) and by the row (which exists to resolve it), so the two can never disagree about when the
 * row is worth showing.
 */
fun needsDistributorChoice(distributors: UnifiedPushDistributorState): Boolean =
    distributors.ackDistributor == null &&
        distributors.savedDistributor == null &&
        distributors.installed.size >= 2

/**
 * Note what is *not* an input: the in-app notification switch. The switch is honoured where it
 * belongs, in `UnifiedPushReceiver.onMessage`, which drops every user-visible push while it is
 * off — and deliberately lets the silent `data-changed` ping through above that gate. Gating
 * registration on it as well would cost nothing in spam (there is none to prevent) and would
 * take the widget's only realtime path away from exactly the users who asked for silence, not
 * for a stale widget. The old row was greyed out with the switch off; that was over-reach on
 * the day it was written, and it left with the row.
 *
 * Local Mode is an input, and it comes first: without a backend there is no one to send the
 * endpoint to, so a Local-Mode install must not even get a distributor saved.
 */
fun unifiedPushRegistrationAction(
    session: UnifiedPushSession,
    distributors: UnifiedPushDistributorState,
    subscription: UnifiedPushSubscription,
): UnifiedPushRegistrationAction {
    val userId = session.userId
    // Sign-in is what makes the endpoint POST answerable, so registration waits for it rather
    // than racing bootstrap and spending its first endpoint on a 401.
    if (session.isLocalMode || !session.authenticated || userId.isNullOrBlank()) {
        return UnifiedPushRegistrationAction.Idle
    }

    if (distributors.ackDistributor != null) {
        val endpoint = subscription.endpoint
        return when {
            // Acknowledged with nothing on file: a sign-out cleared the endpoint, or the
            // distributor's answer never landed. Ask again — same token, same endpoint.
            endpoint.isNullOrBlank() -> UnifiedPushRegistrationAction.RegisterWithSavedDistributor
            // The guard that keeps a launch from costing a POST. Without it every cold start
            // re-announces an endpoint the backend already holds.
            subscription.subscribedUserId == userId -> UnifiedPushRegistrationAction.Idle
            else -> UnifiedPushRegistrationAction.SubscribeStoredEndpoint(endpoint, userId)
        }
    }

    return when {
        distributors.savedDistributor != null ->
            UnifiedPushRegistrationAction.RegisterWithSavedDistributor

        needsDistributorChoice(distributors) -> UnifiedPushRegistrationAction.AwaitDistributorChoice

        distributors.installed.size == 1 ->
            UnifiedPushRegistrationAction.SaveDistributorAndRegister(distributors.installed.first())

        else -> UnifiedPushRegistrationAction.Idle
    }
}
