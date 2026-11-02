package com.garan.threshold.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * OpenType features:
 * - "tnum": Tabular numbers (monospaced figures 0-9 to prevent layout jitter as digits update).
 * - "zero": Slashed or dotted zero for mechanical clarity and distinction from 'O'.
 */
const val THRESHOLD_FONT_FEATURE_SETTINGS = "tnum, zero"

@Suppress("DEPRECATION")
private val DEFAULT_PLATFORM_TEXT_STYLE = PlatformTextStyle(
    includeFontPadding = false,
)

private val BASE_TEXT_STYLE = TextStyle(
    fontFamily = FontFamily.Default,
    fontFeatureSettings = THRESHOLD_FONT_FEATURE_SETTINGS,
    platformStyle = DEFAULT_PLATFORM_TEXT_STYLE,
)

val ThresholdTypography = Typography(
    displayLarge = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Light,
        fontSize = 72.sp,
        lineHeight = 76.sp,
        letterSpacing = (-1.5).sp,
    ),
    displayMedium = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Light,
        fontSize = 54.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.0).sp,
    ),
    displaySmall = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.5.sp,
    ),
    headlineSmall = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    titleLarge = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = BASE_TEXT_STYLE.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
