package com.ohmz.tday.compose.core.notification

/**
 * The Settings notification switch sits on top of two bits of state that move
 * independently: what Android currently allows, and what the user asked for
 * inside T'Day. Mirroring the OS bit alone would leave no way to silence the app
 * without leaving it; ignoring the OS bit would show ON while Android drops
 * every post. So the switch shows the AND of them, and a tap drives whichever
 * of the two is standing in the way.
 *
 * Kept free of Android imports so the whole table is unit-testable.
 */

/** The API level that introduced the POST_NOTIFICATIONS runtime permission. */
const val NOTIFICATION_PERMISSION_SDK = 33

data class NotificationOsState(
    /** Android will actually deliver what we post. */
    val authorized: Boolean,
    /**
     * A request would still put the system dialog on screen. Once Android stops
     * showing it, only the Settings app can turn notifications back on.
     */
    val canPrompt: Boolean,
)

enum class NotificationToggleAction {
    /** Store the preference; the OS already allows delivery. */
    EnablePreference,

    /** Store the preference. Alarms keep firing — delivery is dropped at the receiver. */
    DisablePreference,

    /** Ask for POST_NOTIFICATIONS; the preference only flips once the grant lands. */
    RequestOsPermission,

    /** Hand the user to the app's own notification settings page. */
    OpenOsSettings,
}

/**
 * Three bits have to line up before Android will put a T'Day notification on
 * screen, and each of them can be turned off somewhere different.
 *
 * The app-level switch and the API 33+ runtime permission move together on
 * modern releases; below 33 there is no permission and `areNotificationsEnabled()`
 * is the whole story. [reminderChannelEnabled] is the separate one: switching the
 * "Task reminders" channel off in Android settings leaves both app-level bits
 * true while the OS drops every reminder, every day-ahead digest and every server
 * push — the channel carries all three — so without it the switch would read ON
 * with nothing arriving.
 */
fun isNotificationOsAuthorized(
    sdkInt: Int,
    notificationsEnabled: Boolean,
    permissionGranted: Boolean,
    reminderChannelEnabled: Boolean,
): Boolean = notificationsEnabled && reminderChannelEnabled &&
    (sdkInt < NOTIFICATION_PERMISSION_SDK || permissionGranted)

/**
 * `shouldShowRequestPermissionRationale` reads false in three unrelated cases:
 * never asked, asked but dismissed without an answer, and denied to the point
 * where Android will not show the dialog again. Only the last of those is a dead
 * end, and it is the one the app can recognise for itself — a request that comes
 * back denied while the rationale flag is *still* false is a request Android
 * either refused to show or will not show again. [promptExhausted] is that
 * record; merely having launched the dialog is not, because a dismissal is not
 * an answer.
 *
 * Being wrong in the permissive direction costs a tap that lands on the blocked
 * hint; being wrong the other way sends the user to Android's notification
 * settings, which always works. So the tie is broken towards prompting.
 */
fun canPromptForNotificationPermission(
    sdkInt: Int,
    permissionGranted: Boolean,
    promptExhausted: Boolean,
    shouldShowRationale: Boolean,
): Boolean = when {
    sdkInt < NOTIFICATION_PERMISSION_SDK -> false
    permissionGranted -> false
    shouldShowRationale -> true
    else -> !promptExhausted
}

fun notificationToggleChecked(os: NotificationOsState, preferenceEnabled: Boolean): Boolean =
    os.authorized && preferenceEnabled

fun notificationToggleAction(
    requestedOn: Boolean,
    os: NotificationOsState,
): NotificationToggleAction = when {
    !requestedOn -> NotificationToggleAction.DisablePreference
    os.authorized -> NotificationToggleAction.EnablePreference
    os.canPrompt -> NotificationToggleAction.RequestOsPermission
    else -> NotificationToggleAction.OpenOsSettings
}
