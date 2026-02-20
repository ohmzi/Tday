package com.ohmz.tday.compose.core.data

import android.content.Context
import com.ohmz.tday.compose.ui.theme.AppThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(): AppThemeMode {
        val storedValue = preferences.getString(KEY_THEME_MODE, null)
        return AppThemeMode.fromStorageValue(storedValue)
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.storageValue).apply()
    }

    private companion object {
        const val PREF_NAME = "tday_display_preferences"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
