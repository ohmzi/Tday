package com.ohmz.tday.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TdayPrimary,
    secondary = TdaySecondary,
    tertiary = TdayTertiary,
    background = TdayBackground,
    surface = TdaySurface,
    onPrimary = TdayText,
    onSecondary = TdayText,
    onTertiary = TdayText,
    onBackground = TdayText,
    onSurface = TdayText,
    error = TdayError,
)

private val LightColorScheme = lightColorScheme(
    primary = TdayPrimary,
    secondary = TdaySecondary,
    tertiary = TdayTertiary,
)

@Composable
fun TdayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
