package com.garan.threshold.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = PureWhite,
        onPrimary = AmoledBlack,
        background = AmoledBlack,
        onBackground = PureWhite,
        surface = AmoledBlack,
        onSurface = PureWhite,
        surfaceVariant = CharcoalGray,
        onSurfaceVariant = MutedWhite,
    )

@Composable
fun ThresholdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ThresholdTypography,
        content = content,
    )
}
