package com.ohmz.tday.compose.core.observability

import android.content.Context
import android.content.SharedPreferences

/**
 * Whether this device has agreed to send crash and problem reports. [UNANSWERED] behaves exactly
 * like [DENIED]: nothing starts and nothing is stored. The difference is only that the question
 * still has to be asked — by the connect flow's last step, or by the standalone card for a device
 * that reaches a workspace without one.
 */
enum class TelemetryConsentState { UNANSWERED, GRANTED, DENIED }

/**
 * The device's answer to "send crash reports?", and the moment it said yes.
 *
 * Plain prefs, deliberately: the answer is not a secret, and it has to be readable synchronously
 * in `Application.onCreate` so a boot, widget or alarm process knows whether to start the SDK
 * without an EncryptedSharedPreferences Keystore round trip. For the same reason it is not in
 * `SecureConfigStore`, and never cleared by sign-out or `OfflineCacheManager.clearAllLocalData`:
 * consent belongs to the device, not to an account, so it survives a sign-out and is what governs
 * sending until it changes.
 *
 * What a sign-in does change is when the question is asked, not what the answer means: the connect
 * flow asks again on every sign-in, so the stored answer is not treated as an answer for the flow in
 * progress. That per-flow half lives in memory, in `TelemetryConsentManager`; this store stays the
 * one place the answer itself lives — what Settings reads and writes, and what `TelemetryBootstrap`
 * obeys.
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

    /** Whether the answer reached the disk; in memory it is changed either way. */
    fun grant(nowMs: Long): Boolean =
        preferences.edit()
            .putString(KEY_STATE, VALUE_GRANTED)
            .putLong(KEY_GRANTED_AT_MS, nowMs)
            .commit()

    /** Whether the answer reached the disk; in memory it is changed either way. */
    fun deny(): Boolean =
        preferences.edit()
            .putString(KEY_STATE, VALUE_DENIED)
            .remove(KEY_GRANTED_AT_MS)
            .commit()

    private companion object {
        const val PREF_NAME = "telemetry_consent_prefs"
        const val KEY_STATE = "state"
        const val KEY_GRANTED_AT_MS = "granted_at_ms"
        const val VALUE_GRANTED = "granted"
        const val VALUE_DENIED = "denied"
    }
}
