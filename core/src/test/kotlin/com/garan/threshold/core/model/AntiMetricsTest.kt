package com.garan.threshold.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AntiMetricsTest {

    @Test
    fun formatRestDuration_lessThanOneMinute_returnsLessThanOneMinute() {
        assertThat(AntiMetricsFormatter.formatRestDuration(0L)).isEqualTo("REST < 1M")
        assertThat(AntiMetricsFormatter.formatRestDuration(45_000L)).isEqualTo("REST < 1M")
        assertThat(AntiMetricsFormatter.formatRestDuration(59_999L)).isEqualTo("REST < 1M")
    }

    @Test
    fun formatRestDuration_minutesOnly_returnsMinutes() {
        assertThat(AntiMetricsFormatter.formatRestDuration(60_000L)).isEqualTo("REST 1M")
        assertThat(AntiMetricsFormatter.formatRestDuration(48 * 60_000L)).isEqualTo("REST 48M")
        assertThat(AntiMetricsFormatter.formatRestDuration(59 * 60_000L + 59_000L)).isEqualTo("REST 59M")
    }

    @Test
    fun formatRestDuration_hoursAndMinutes_returnsHoursAndMinutes() {
        assertThat(AntiMetricsFormatter.formatRestDuration(60 * 60_000L)).isEqualTo("REST 1H")
        assertThat(AntiMetricsFormatter.formatRestDuration(2 * 3600_000L + 15 * 60_000L)).isEqualTo("REST 2H 15M")
        assertThat(AntiMetricsFormatter.formatRestDuration(23 * 3600_000L + 59 * 60_000L)).isEqualTo("REST 23H 59M")
    }

    @Test
    fun formatRestDuration_daysAndHours_returnsDaysAndHours() {
        assertThat(AntiMetricsFormatter.formatRestDuration(24 * 3600_000L)).isEqualTo("REST 1D")
        assertThat(AntiMetricsFormatter.formatRestDuration(24 * 3600_000L + 4 * 3600_000L)).isEqualTo("REST 1D 4H")
        assertThat(
            AntiMetricsFormatter.formatRestDuration(3 * 24 * 3600_000L + 12 * 3600_000L),
        ).isEqualTo("REST 3D 12H")
    }

    @Test
    fun formatRestDuration_negativeValue_clampedToZero() {
        assertThat(AntiMetricsFormatter.formatRestDuration(-5000L)).isEqualTo("REST < 1M")
    }

    @Test
    fun formatUnlocks_singularAndPlural() {
        assertThat(AntiMetricsFormatter.formatUnlocks(0)).isEqualTo("0 UNLOCKS")
        assertThat(AntiMetricsFormatter.formatUnlocks(1)).isEqualTo("1 UNLOCK")
        assertThat(AntiMetricsFormatter.formatUnlocks(6)).isEqualTo("6 UNLOCKS")
        assertThat(AntiMetricsFormatter.formatUnlocks(99)).isEqualTo("99 UNLOCKS")
    }

    @Test
    fun formatStatus_withRestAndUnlocks_combinesCleanly() {
        val metrics = AntiMetrics(restDurationMs = 48 * 60_000L, unlockCount = 6)
        assertThat(AntiMetricsFormatter.formatStatus(metrics)).isEqualTo("REST 48M  ·  6 UNLOCKS")
    }

    @Test
    fun formatStatus_withoutRest_showsUnlocksOnly() {
        val metrics = AntiMetrics(restDurationMs = null, unlockCount = 1)
        assertThat(AntiMetricsFormatter.formatStatus(metrics)).isEqualTo("1 UNLOCK")
    }
}
