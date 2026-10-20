package com.ohmz.tday.compose.core.data

import android.content.Context
import android.content.SharedPreferences

/**
 * The in-app half of "reduce motion", default off.
 *
 * Not a mirror of Android's animator duration scale — a second, narrower answer to the
 * same question. The system setting is device-wide, sits several screens deep in
 * Android's own Settings (behind developer options on some builds, and unreachable
 * altogether on a managed profile that locks them), and a user who only wants *this*
 * app to stop moving has nowhere to say so. This is that switch. The two compose in
 * `TdayMotion.kt`, where the one rule is that this one can only ever subtract.
 *
 * Plain prefs — there is nothing sensitive in "this person prefers less motion" — and
 * deliberately never cleared, the same call [RestingFloatersPreferenceStore] makes. A
 * sign-out is not a request to start animating again, and neither is an expired token:
 * whoever picks the phone up next is the same person who asked for stillness.
 */
class ReduceMotionPreferenceStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Calls [onChanged] whenever the switch moves, and returns the cancel that
     * unregisters it.
     *
     * A preference the whole app times itself against has to be observable. The switch
     * lives in Settings; the motion it governs is on every other screen, held by a
     * composition that a `SharedPreferences` write does not invalidate — so without
     * this, turning it on would take effect on the next cold start and nowhere else.
     *
     * The returned lambda is not merely tidiness. `SharedPreferences` holds its
     * listeners **weakly**, so dropping it on the floor lets the listener be collected
     * at the next GC, with no symptom but a switch that quietly stops working; holding
     * it is what keeps the registration alive.
     */
    fun observeEnabled(onChanged: (Boolean) -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            // A null key means the whole file was cleared (Android 11+), which moves
            // this value like any other write does.
            if (key == null || key == KEY_ENABLED) {
                onChanged(prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val PREF_NAME = "motion_prefs"
        const val KEY_ENABLED = "reduce_motion"

        /** Off: the device's own scale is the whole answer until the user says otherwise. */
        const val DEFAULT_ENABLED = false
    }
}
