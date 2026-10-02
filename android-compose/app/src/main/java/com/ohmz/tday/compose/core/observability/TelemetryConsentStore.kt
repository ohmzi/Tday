package com.ohmz.tday.compose.core.observability

import android.content.Context
import android.content.SharedPreferences

/**
 * Whether this device has agreed to send crash and problem reports. [UNANSWERED] behaves exactly
 * like [DENIED]: nothing starts and nothing is stored. The difference is only that the one-time
 * consent card still has to ask.
 */
enum class TelemetryConsentState { UNANSWERED, GRANTED, DENIED }

/**
 * The device's answer to "send crash reports?", and the moment it said yes.
 *
 * Plain prefs, deliberately: the answer is not a secret, and it has to be readable synchronously
 * in `Application.onCreate` so a boot, widget or alarm process knows whether to start the SDK
 * without an EncryptedSharedPreferences Keystore round trip. For the same reason it is not in
 * `SecureConfigStore`, and never cleared by sign-out or `OfflineCacheManager.clearAllLocalData`:
 * consent belongs to the device, not to an account, so whoever signs in next inherits the choice
 * the person holding the phone made.
 *
 * [grantedAtMs] is half of the answer. Android replays an ANR from `ApplicationExitInfo` on the
 * next launch for up to 91 days, so a fresh start after opt-in would otherwise upload a hang that
 * happened while the switch was off; `beforeSend` drops any event older than this.
 */
class TelemetryConsentStore internal constructor(private val preferences: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE))

    fun state(): TelemetryConsentState = when (preferences.getString(KEY_STATE, null)) {
        // A grant without its timestamp is not one: with no moment to compare against, the replay
        // guard would pass everything.
        VALUE_GRANTED -> if (grantedAtMs() > 0L) TelemetryConsentState.GRANTED else TelemetryConsentState.UNANSWERED
        VALUE_DENIED -> TelemetryConsentState.DENIED
        else -> TelemetryConsentState.UNANSWERED
    }

    /** When consent was given, or 0 if it has not been. */
    fun grantedAtMs(): Long = preferences.getLong(KEY_GRANTED_AT_MS, 0L)

    fun grant(nowMs: Long) {
        preferences.edit()
            .putString(KEY_STATE, VALUE_GRANTED)
            .putLong(KEY_GRANTED_AT_MS, nowMs)
            .commit()
    }

    fun deny() {
        preferences.edit()
            .putString(KEY_STATE, VALUE_DENIED)
            .remove(KEY_GRANTED_AT_MS)
            .commit()
    }

    private companion object {
        const val PREF_NAME = "telemetry_consent_prefs"
        const val KEY_STATE = "state"
        const val KEY_GRANTED_AT_MS = "granted_at_ms"
        const val VALUE_GRANTED = "granted"
        const val VALUE_DENIED = "denied"
    }
}
