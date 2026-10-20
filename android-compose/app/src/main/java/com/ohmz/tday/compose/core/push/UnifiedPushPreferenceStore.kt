package com.ohmz.tday.compose.core.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last UnifiedPush endpoint so the receiver can unregister it from the
 * backend when the distributor rotates or unregisters, and who that endpoint was last
 * subscribed for, so a launch that changes nothing costs no request. The endpoint URL is
 * not secret (it's an opaque distributor callback) and the user id is already all over
 * the cached session, so plain prefs are fine.
 */
@Singleton
class UnifiedPushPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getEndpoint(): String? = preferences.getString(KEY_ENDPOINT, null)

    /**
     * A *different* endpoint invalidates the confirmation that went with the old one, and the
     * two are cleared in one edit so no reader can see the new endpoint still wearing the old
     * endpoint's subscription. An endpoint the distributor merely re-announces unchanged keeps
     * it: that is the same URL the backend already holds, and dropping the marker would buy a
     * redundant POST on every re-announcement.
     */
    fun setEndpoint(endpoint: String) {
        if (getEndpoint() == endpoint) return
        preferences.edit()
            .putString(KEY_ENDPOINT, endpoint)
            .remove(KEY_SUBSCRIBED_USER_ID)
            .apply()
    }

    /** Null until a caller that knows the signed-in user has had the backend accept the endpoint. */
    fun getSubscribedUserId(): String? = preferences.getString(KEY_SUBSCRIBED_USER_ID, null)

    fun setSubscribedUserId(userId: String) {
        preferences.edit().putString(KEY_SUBSCRIBED_USER_ID, userId).apply()
    }

    fun clearSubscribedUserId() {
        preferences.edit().remove(KEY_SUBSCRIBED_USER_ID).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_ENDPOINT).remove(KEY_SUBSCRIBED_USER_ID).apply()
    }

    private companion object {
        const val PREF_NAME = "unifiedpush_prefs"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_SUBSCRIBED_USER_ID = "subscribed_user_id"
    }
}
