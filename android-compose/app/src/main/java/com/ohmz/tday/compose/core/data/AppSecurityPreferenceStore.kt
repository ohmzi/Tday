package com.ohmz.tday.compose.core.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The two user-facing privacy switches for task content on this device.
 *
 * Plain prefs, deliberately: neither value is a secret (the database key lives in
 * [com.ohmz.tday.compose.core.data.db.DatabasePassphraseStore]), and both are read on the UI
 * thread during `onCreate`, where an EncryptedSharedPreferences load would cost a Keystore round
 * trip on every launch. Mirrors RestingFloatersPreferenceStore.
 */
class AppSecurityPreferenceStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Whether to set `FLAG_SECURE`, which keeps task content out of screenshots, screen
     * recordings and the app-switcher thumbnail.
     *
     * Defaults on. Android offers no way to hide only the recents thumbnail, so this necessarily
     * blocks deliberate screenshots too — hence the setting to turn it back off.
     */
    fun isScreenshotProtectionEnabled(): Boolean =
        preferences.getBoolean(KEY_SCREENSHOT_PROTECTION, true)

    fun setScreenshotProtectionEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, enabled).apply()
    }

    /**
     * Whether to require a biometric or device credential before showing the app.
     *
     * Defaults **off** and stays off unless the user turns it on. This is a UI gate only: it is
     * not tied to the database key, because the home-screen widget has to keep rendering while
     * the device is locked. Encryption at rest is what protects the data; this protects the
     * screen of an unlocked, handed-over phone.
     */
    fun isAppLockEnabled(): Boolean = preferences.getBoolean(KEY_APP_LOCK, false)

    fun setAppLockEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
    }

    /**
     * Observable form of [isAppLockEnabled], for the Glance widgets.
     *
     * A widget's `provideGlance` runs only ONCE per Glance session, so a plain read there is
     * frozen until the session is recreated (process death, or re-adding the widget). The
     * widgets collect this inside their composition instead, so flipping the toggle recomposes
     * them in place. Process-wide because every reader lives in the same process; a fresh
     * process seeds it from disk on first access.
     */
    val appLockEnabled: StateFlow<Boolean>
        get() {
            ensureAppLockFlowStarted(preferences)
            return appLockEnabledFlow
        }

    private companion object {
        const val PREF_NAME = "tday_app_security_prefs"
        const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection_enabled"
        const val KEY_APP_LOCK = "app_lock_enabled"

        val appLockEnabledMutable = MutableStateFlow(false)

        /**
         * Hoisted so every read hands back the SAME instance. `asStateFlow()` allocates a new
         * wrapper per call, and `collectAsState` remembers keyed on the flow instance — handing
         * out a fresh one each recomposition would restart the collector, emit, and recompose
         * again in a loop.
         */
        val appLockEnabledFlow: StateFlow<Boolean> = appLockEnabledMutable.asStateFlow()

        @Volatile
        var appLockFlowStarted = false

        /**
         * Seeds the flow from disk and keeps it current via a preference listener, so ANY write
         * path updates it — not just [setAppLockEnabled]. The listener is held in a field
         * because SharedPreferences only keeps a weak reference to it.
         */
        @Suppress("unused")
        var appLockListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

        fun ensureAppLockFlowStarted(preferences: SharedPreferences) {
            if (appLockFlowStarted) return
            synchronized(this) {
                if (appLockFlowStarted) return
                appLockEnabledMutable.value = preferences.getBoolean(KEY_APP_LOCK, false)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    if (key == KEY_APP_LOCK) {
                        appLockEnabledMutable.value = prefs.getBoolean(KEY_APP_LOCK, false)
                    }
                }
                preferences.registerOnSharedPreferenceChangeListener(listener)
                appLockListener = listener
                appLockFlowStarted = true
            }
        }
    }
}

/**
 * Applies (or clears) `FLAG_SECURE` on this activity's window per the user's setting.
 *
 * Every activity that can show or accept task text has to call this, not just the main one: the
 * widget's create-task sheet and the share receiver are where the user *types* a title, and a
 * window without the flag is screenshot-able and lands in the recents thumbnail regardless of what
 * MainActivity does. Called from `onStart` so flipping the setting takes effect on the next
 * foreground without a restart.
 */
fun Activity.applyScreenshotProtection(
    store: AppSecurityPreferenceStore = AppSecurityPreferenceStore(applicationContext),
) {
    if (store.isScreenshotProtectionEnabled()) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
