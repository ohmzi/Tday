package com.ohmz.tday.compose.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ohmz.tday.compose.R

@OptIn(ExperimentalTextApi::class)
private fun tdayFont(weight: FontWeight, visualWeight: Int = weight.weight): Font =
    Font(
        resId = R.font.nunito_wght,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(visualWeight)),
    )

val TdayFontFamily = FontFamily(
    tdayFont(FontWeight.ExtraLight, 700),
    tdayFont(FontWeight.Light, 700),
    tdayFont(FontWeight.Normal, 700),
    tdayFont(FontWeight.Medium, 700),
    tdayFont(FontWeight.SemiBold, 700),
    tdayFont(FontWeight.Bold, 800),
    tdayFont(FontWeight.ExtraBold, 800),
    tdayFont(FontWeight.Black, 900),
)

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = TdayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)
