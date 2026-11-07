package com.garan.threshold.data.antimetrics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidAntiMetricsRepositoryTest {

    private lateinit var context: Context
    private val fixedZone = ZoneId.of("UTC")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("threshold_anti_metrics", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun initialState_emptyPreferences_defaultsToZeroUnlocksAndNullRest() {
        val repo = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })
        assertThat(repo.metrics.value.unlockCount).isEqualTo(0)
        assertThat(repo.metrics.value.restDurationMs).isNull()
    }

    @Test
    fun onScreenOff_thenUnlock_calculatesRestDurationAndIncrementsUnlocks() {
        val repo = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })

        // Day: 2026-11-05 10:00:00 UTC
        val screenOffTime = LocalDate.of(2026, 11, 5).atTime(10, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        repo.onScreenOff(screenOffTime)

        // Unlock 48 minutes later: 2026-11-05 10:48:00 UTC
        val unlockTime = screenOffTime + 48 * 60_000L
        repo.onUnlock(unlockTime)

        assertThat(repo.metrics.value.restDurationMs).isEqualTo(48 * 60_000L)
        assertThat(repo.metrics.value.unlockCount).isEqualTo(1)
    }

    @Test
    fun multipleUnlocksSameDay_accumulatesCount() {
        val repo = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })

        var time = LocalDate.of(2026, 11, 5).atTime(8, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        repo.onUnlock(time)
        assertThat(repo.metrics.value.unlockCount).isEqualTo(1)

        time += 30 * 60_000L
        repo.onScreenOff(time)

        time += 15 * 60_000L
        repo.onUnlock(time)
        assertThat(repo.metrics.value.unlockCount).isEqualTo(2)
        assertThat(repo.metrics.value.restDurationMs).isEqualTo(15 * 60_000L)

        time += 60 * 60_000L
        repo.onScreenOff(time)

        time += 10 * 60_000L
        repo.onUnlock(time)
        assertThat(repo.metrics.value.unlockCount).isEqualTo(3)
        assertThat(repo.metrics.value.restDurationMs).isEqualTo(10 * 60_000L)
    }

    @Test
    fun nextDayUnlock_resetsCountTo1_andCalculatesCrossDayRest() {
        val repo = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })

        // Day 1: 2026-11-05 23:00:00 UTC -> phone goes to sleep
        val screenOffTime = LocalDate.of(2026, 11, 5).atTime(23, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        repo.onScreenOff(screenOffTime)

        // Day 2: 2026-11-06 07:00:00 UTC (8 hours sleep across midnight)
        val unlockTime = LocalDate.of(2026, 11, 6).atTime(7, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        repo.onUnlock(unlockTime)

        // Unlocks reset to 1 on the new calendar day without midnight alarms
        assertThat(repo.metrics.value.unlockCount).isEqualTo(1)
        assertThat(repo.metrics.value.restDurationMs).isEqualTo(8 * 3600_000L)
    }

    @Test
    fun processRecreation_preservesStateAndCarriesOver() {
        val repo1 = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })
        val today = LocalDate.now(fixedZone)
        val time = today.atTime(12, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        repo1.onUnlock(time)
        repo1.onScreenOff(time + 10_000L)

        // Simulate process death and restart
        val repo2 = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })
        // Initial state loaded from disk
        assertThat(repo2.metrics.value.unlockCount).isEqualTo(1)

        // Unlock in new process
        repo2.onUnlock(time + 10_000L + 20 * 60_000L)
        assertThat(repo2.metrics.value.unlockCount).isEqualTo(2)
        assertThat(repo2.metrics.value.restDurationMs).isEqualTo(20 * 60_000L)
    }

    @Test
    fun rapidDuplicateUnlocks_debouncedToOneIncrement() {
        val repo = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })
        val screenOffTime = LocalDate.of(2026, 11, 5).atTime(10, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        repo.onScreenOff(screenOffTime)

        // First broadcast at 10:48:00
        val unlockTime = screenOffTime + 48 * 60_000L
        repo.onUnlock(unlockTime)

        // Rapid duplicate broadcast 608ms later (e.g. Pixel two-stage biometric dismiss pipeline)
        repo.onUnlock(unlockTime + 608L)

        assertThat(repo.metrics.value.unlockCount).isEqualTo(1)
        assertThat(repo.metrics.value.restDurationMs).isEqualTo(48 * 60_000L)
    }

    @Test
    fun multipleRapidUnlocksWithinDebounceWindow_onlyCountsFirst() {
        val repo = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })
        val time = LocalDate.of(2026, 11, 5).atTime(10, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        repo.onUnlock(time)
        repo.onUnlock(time + 100L)
        repo.onUnlock(time + 500L)
        repo.onUnlock(time + 1999L)

        assertThat(repo.metrics.value.unlockCount).isEqualTo(1)

        // Unlock after debounce window passes (2001ms)
        repo.onUnlock(time + 2001L)
        assertThat(repo.metrics.value.unlockCount).isEqualTo(2)
    }

    @Test
    fun unregister_cleansUpReceiverGracefully_acrossMultipleInstances() {
        val repo1 = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })
        val repo2 = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })
        // Unregistering from the second instance should cleanly unregister the shared static receiver
        repo2.unregister()
        repo1.unregister()
    }

    @Test
    fun refreshForCurrentDate_resetsUnlockCountAtMidnightWhileAwake_preservingLastRest() {
        val repo = AndroidAntiMetricsRepository(context, zoneIdProvider = { fixedZone })
        val day1Off = LocalDate.of(2026, 11, 5).atTime(22, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val day1Unlock = day1Off + 30 * 60_000L

        repo.onScreenOff(day1Off)
        repo.onUnlock(day1Unlock)
        assertThat(repo.metrics.value.unlockCount).isEqualTo(1)
        assertThat(repo.metrics.value.restDurationMs).isEqualTo(30 * 60_000L)

        // Same day refresh does not reset count
        repo.refreshForCurrentDate(day1Unlock + 30 * 60_000L)
        assertThat(repo.metrics.value.unlockCount).isEqualTo(1)

        // Midnight rolls over to 2026-11-06 00:01:00 UTC while screen is still on
        val day2Awake = LocalDate.of(2026, 11, 6).atTime(0, 1, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        repo.refreshForCurrentDate(day2Awake)

        assertThat(repo.metrics.value.unlockCount).isEqualTo(0)
        assertThat(repo.metrics.value.restDurationMs).isEqualTo(30 * 60_000L)
    }

    @Test
    fun backwardWallClockShiftDuringSleep_usesMonotonicElapsedFallback() {
        var monotonicMs = 1_000_000L
        val repo = AndroidAntiMetricsRepository(
            context = context,
            zoneIdProvider = { fixedZone },
            elapsedRealtimeProvider = { monotonicMs },
        )

        val wallOffTime = LocalDate.of(2026, 11, 5).atTime(10, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        repo.onScreenOff(wallOffTime)

        // 20 minutes of real monotonic sleep pass, but NTP/timezone shifted wall clock backward by 5 minutes
        monotonicMs += 20 * 60_000L
        val shiftedWallUnlockTime = wallOffTime - 5 * 60_000L
        repo.onUnlock(shiftedWallUnlockTime)

        assertThat(repo.metrics.value.restDurationMs).isEqualTo(20 * 60_000L)
        assertThat(repo.metrics.value.unlockCount).isEqualTo(1)
    }
}
