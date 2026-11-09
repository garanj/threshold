package com.garan.threshold.ui.theme

import androidx.compose.ui.graphics.Color

val AmoledBlack = Color(0xFF000000)
val PureWhite = Color(0xFFFFFFFF)
val MutedWhite = Color(0xFFCCCCCC)
val SubtleGray = Color(0xFF888888)
val CharcoalGray = Color(0xFF1E1E1E)
val HairlineBorder = Color(0xFF262626)

// Material 3 Surface Container & Outline Hierarchy (Monochrome Instrument Palette)
val SurfaceContainerLow = Color(0xFF0C0C0C)
val SurfaceContainer = Color(0xFF121212)
val SurfaceContainerHigh = Color(0xFF1A1A1A)
val SurfaceContainerHighest = Color(0xFF242424)
val OutlineGray = Color(0xFF444444)

// Profile Accent Palette (blended at low alpha over surface containers for dark/light adaptability)
val ProfileAmber = Color(0xFFD97706)
val ProfileEmerald = Color(0xFF059669)
val ProfileCyan = Color(0xFF0284C7)
val ProfileIndigo = Color(0xFF4F46E5)
val ProfileViolet = Color(0xFF9333EA)
val ProfileCrimson = Color(0xFFE11D48)

val ProfileColorPalette: List<Color> = listOf(
    ProfileAmber,
    ProfileEmerald,
    ProfileCyan,
    ProfileIndigo,
    ProfileViolet,
    ProfileCrimson,
)

fun profileBaseColor(colorIndex: Int): Color {
    val size = ProfileColorPalette.size
    val normalizedIndex = ((colorIndex % size) + size) % size
    return ProfileColorPalette[normalizedIndex]
}
