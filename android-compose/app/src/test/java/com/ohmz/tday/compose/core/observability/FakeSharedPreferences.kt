package com.ohmz.tday.compose.core.observability

import android.content.SharedPreferences

/**
 * An in-memory [SharedPreferences] for the unit tests: the android.jar the JVM tests link against
 * only stubs the framework, so a real `getSharedPreferences` is not available here.
 *
 * Writes land immediately whether the test calls `apply()` or `commit()`, which is the
 * observable behaviour of the real thing from the same process.
 */
internal class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as? MutableSet<String> ?: defValues
    }

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }

        override fun putStringSet(key: String, values: MutableSet<String>?) =
            apply { this@FakeSharedPreferences.values[key] = values }

        override fun putInt(key: String, value: Int) = apply { values[key] = value }

        override fun putLong(key: String, value: Long) = apply { values[key] = value }

        override fun putFloat(key: String, value: Float) = apply { values[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }

        override fun remove(key: String) = apply { values.remove(key) }

        override fun clear() = apply { values.clear() }

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
