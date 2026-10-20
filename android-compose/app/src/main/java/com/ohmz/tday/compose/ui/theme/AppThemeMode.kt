package com.ohmz.tday.compose.ui.theme

import androidx.annotation.StringRes
import com.ohmz.tday.compose.R

enum class AppThemeMode(
    val storageValue: String,
    @StringRes val labelRes: Int,
) {
    SYSTEM("system", R.string.settings_theme_system),
    LIGHT("light", R.string.settings_theme_light),
    DARK("dark", R.string.settings_theme_dark),
    ;

    companion object {
        fun fromStorageValue(value: String?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}
