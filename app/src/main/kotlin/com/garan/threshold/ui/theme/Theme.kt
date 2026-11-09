package com.garan.threshold.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = PureWhite,
        onPrimary = AmoledBlack,
        primaryContainer = SurfaceContainerHigh,
        onPrimaryContainer = PureWhite,
        secondary = MutedWhite,
        onSecondary = AmoledBlack,
        secondaryContainer = SurfaceContainer,
        onSecondaryContainer = PureWhite,
        background = AmoledBlack,
        onBackground = PureWhite,
        surface = AmoledBlack,
        onSurface = PureWhite,
        surfaceVariant = CharcoalGray,
        onSurfaceVariant = SubtleGray,
        surfaceContainerLowest = AmoledBlack,
        surfaceContainerLow = SurfaceContainerLow,
        surfaceContainer = SurfaceContainer,
        surfaceContainerHigh = SurfaceContainerHigh,
        surfaceContainerHighest = SurfaceContainerHighest,
        outline = OutlineGray,
        outlineVariant = HairlineBorder,
    )

@Composable
fun ThresholdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ThresholdTypography,
        content = content,
    )
}
