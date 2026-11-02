package com.garan.threshold.ui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThresholdTypographyTest {

    @Test
    fun allTypographyStylesEnforceTabularFiguresAndSlashedZero() {
        val styles = listOf(
            ThresholdTypography.displayLarge,
            ThresholdTypography.headlineMedium,
            ThresholdTypography.bodyLarge,
            ThresholdTypography.bodyMedium,
            ThresholdTypography.labelSmall,
        )

        for (style in styles) {
            assertThat(style.fontFeatureSettings).contains("tnum")
            assertThat(style.fontFeatureSettings).contains("zero")
        }
    }

    @Test
    fun allTypographyStylesDisableFontPaddingToPreventVerticalDrift() {
        val styles = listOf(
            ThresholdTypography.displayLarge,
            ThresholdTypography.headlineMedium,
            ThresholdTypography.bodyLarge,
            ThresholdTypography.bodyMedium,
            ThresholdTypography.labelSmall,
        )

        for (style in styles) {
            assertThat(style.platformStyle?.paragraphStyle?.includeFontPadding).isFalse()
        }
    }
}
