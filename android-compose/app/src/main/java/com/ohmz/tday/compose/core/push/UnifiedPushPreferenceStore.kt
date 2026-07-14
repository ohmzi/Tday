package com.ohmz.tday.compose.core.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last UnifiedPush endpoint so the receiver can unregister it from the
 * backend when the distributor rotates or unregisters. The endpoint URL is not secret
 * (it's an opaque distributor callback), so plain prefs are fine.
 */
@Singleton
class UnifiedPushPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getEndpoint(): String? = preferences.getString(KEY_ENDPOINT, null)

    fun setEndpoint(endpoint: String) {
        preferences.edit().putString(KEY_ENDPOINT, endpoint).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_ENDPOINT).apply()
    }

    private companion object {
        const val PREF_NAME = "unifiedpush_prefs"
        const val KEY_ENDPOINT = "endpoint"
    }
}
