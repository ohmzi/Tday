package com.ohmz.tday.compose.core.data

import android.app.Activity
import android.content.Context
import android.view.WindowManager

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

    private companion object {
        const val PREF_NAME = "tday_app_security_prefs"
        const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection_enabled"
        const val KEY_APP_LOCK = "app_lock_enabled"
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
