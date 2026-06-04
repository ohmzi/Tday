package com.ohmz.tday.compose.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TdayDarkPrimary,
    secondary = TdayDarkSecondary,
    tertiary = TdayDarkTertiary,
    background = TdayDarkBackground,
    surface = TdayDarkSurface,
    surfaceVariant = TdayDarkSurfaceVariant,
    onPrimary = TdayDarkOnPrimary,
    onSecondary = TdayDarkOnPrimary,
    onTertiary = TdayDarkOnPrimary,
    onBackground = TdayDarkOnSurface,
    onSurface = TdayDarkOnSurface,
    onSurfaceVariant = TdayDarkOnSurfaceVariant,
    error = TdayDarkError,
)

private val LightColorScheme = lightColorScheme(
    primary = TdayLightPrimary,
    secondary = TdayLightSecondary,
    tertiary = TdayLightTertiary,
    background = TdayLightBackground,
    surface = TdayLightSurface,
    surfaceVariant = TdayLightSurfaceVariant,
    onPrimary = TdayLightOnPrimary,
    onSecondary = TdayLightOnPrimary,
    onTertiary = TdayLightOnPrimary,
    onBackground = TdayLightOnSurface,
    onSurface = TdayLightOnSurface,
    onSurfaceVariant = TdayLightOnSurfaceVariant,
    error = TdayLightError,
)

@Composable
fun TdayTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
