package com.ohmz.tday.compose.core.notification

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The in-app half of the notification gate, and the record of what has already
 * been asked of Android.
 *
 * Every place that posts a T'Day notification reads [isEnabled] first, so the
 * Settings switch is a real gate rather than a readout of the OS bit.
 *
 * Deliberately unscoped: MainActivity and the Settings row construct it by hand
 * rather than through the graph, so a `@Singleton` here would promise an instance
 * guarantee that does not hold. What actually makes the state shared is Android
 * caching one `SharedPreferencesImpl` per file name per process — every instance
 * reads and writes the same map, and every notification receiver runs in this
 * same process.
 */
class NotificationPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Default on: a fresh install that grants the first-launch prompt should get
     * its reminders without a second opt-in. With the permission denied the OS
     * half is false anyway, so the switch still reads OFF.
     */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * True once the system permission dialog has been launched at least once.
     * This is only what stops the first-launch ask repeating on every cold start —
     * it says nothing about whether the user answered, so it is not what decides
     * whether asking again is still worth it.
     */
    fun hasRequestedPermission(): Boolean = prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)

    fun markPermissionRequested() {
        prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
    }

    /**
     * True once a request came back denied while Android still would not offer the
     * rationale — the signature of a dialog it has stopped showing. From then on the
     * only way back in is the app's notification settings page.
     */
    fun hasExhaustedPermissionPrompt(): Boolean =
        prefs.getBoolean(KEY_PERMISSION_PROMPT_EXHAUSTED, false)

    fun markPermissionPromptExhausted() {
        prefs.edit().putBoolean(KEY_PERMISSION_PROMPT_EXHAUSTED, true).apply()
    }

    private companion object {
        const val PREF_NAME = "tday_notification_prefs"
        const val KEY_ENABLED = "notifications_enabled"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"
        const val KEY_PERMISSION_PROMPT_EXHAUSTED = "permission_prompt_exhausted"
    }
}
