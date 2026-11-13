package com.garan.threshold.data.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.garan.threshold.core.model.LocationFix
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@Suppress("DEPRECATION")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLocationSourceTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Before
    fun setUp() {
        context.getSharedPreferences("threshold_location_telemetry", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        shadowOf(context).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        shadowOf(locationManager).setLocationEnabled(true)
        shadowOf(locationManager).setProviderEnabled(LocationManager.FUSED_PROVIDER, false)
        shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
    }

    private fun buildLocation(
        provider: String = LocationManager.GPS_PROVIDER,
        lat: Double,
        lon: Double,
        elapsedNanos: Long = SystemClock.elapsedRealtimeNanos(),
        wallClockMs: Long = System.currentTimeMillis(),
    ): Location = Location(provider).apply {
        latitude = lat
        longitude = lon
        accuracy = 10f
        time = wallClockMs
        elapsedRealtimeNanos = elapsedNanos
    }

    @Test
    fun refreshLocation_readsMostRecentLastKnownLocationWhenPermittedAndEnabled_andRecordsCacheHit() = runTest {
        val olderGps = buildLocation(LocationManager.GPS_PROVIDER, 51.5000, -0.1200, elapsedNanos = 1_000L)
        val newerNetwork = buildLocation(LocationManager.NETWORK_PROVIDER, 51.5074, -0.1278, elapsedNanos = 5_000L)
        shadowOf(locationManager).setLastKnownLocation(LocationManager.GPS_PROVIDER, olderGps)
        shadowOf(locationManager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, newerNetwork)

        val source = AndroidLocationSource(context)
        val fix = source.refreshLocation()

        assertThat(fix).isEqualTo(LocationFix(51.5074, -0.1278))
        assertThat(source.currentLocation.value).isEqualTo(LocationFix(51.5074, -0.1278))
        assertThat(source.telemetry.value.cacheHitsLastHour).isEqualTo(1)
        assertThat(source.telemetry.value.cacheHitsLast24h).isEqualTo(1)
        assertThat(source.telemetry.value.activeChecksLastHour).isEqualTo(0)
    }

    @Test
    fun refreshLocation_retainsLastFixOnTemporarySignalLoss_andClearsWhenLocationDisabledOrPermissionRevoked() =
        runTest {
            val initialFix = buildLocation(LocationManager.GPS_PROVIDER, 51.5074, -0.1278)
            shadowOf(locationManager).setLastKnownLocation(LocationManager.GPS_PROVIDER, initialFix)

            val source = AndroidLocationSource(context)
            assertThat(source.refreshLocation()).isEqualTo(LocationFix(51.5074, -0.1278))

            // Simulate temporary underground signal loss (no lastKnownLocation in provider, but Location Services still ON)
            shadowOf(locationManager).setLastKnownLocation(LocationManager.GPS_PROVIDER, null)
            assertThat(source.refreshLocation()).isEqualTo(LocationFix(51.5074, -0.1278))
            assertThat(source.currentLocation.value).isEqualTo(LocationFix(51.5074, -0.1278))

            // Turn off Location Services -> must fail-safe to null immediately
            shadowOf(locationManager).setLocationEnabled(false)
            assertThat(source.refreshLocation()).isNull()
            assertThat(source.currentLocation.value).isNull()

            // Re-enable Location Services and restore fix, then revoke permission -> must fail-safe to null
            shadowOf(locationManager).setLocationEnabled(true)
            shadowOf(locationManager).setLastKnownLocation(LocationManager.GPS_PROVIDER, initialFix)
            assertThat(source.refreshLocation()).isEqualTo(LocationFix(51.5074, -0.1278))

            shadowOf(context).denyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            assertThat(source.refreshLocation()).isNull()
            assertThat(source.currentLocation.value).isNull()
        }

    @Test
    fun refreshLocation_whenLastKnownFixIs60SecondsOldOnMinuteTick_returnsCacheHitWithoutActiveRequest() = runTest {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val nowWallMs = System.currentTimeMillis()
        val minuteOldFix = buildLocation(
            provider = LocationManager.FUSED_PROVIDER,
            lat = 51.5000,
            lon = -0.1200,
            elapsedNanos = (nowNanos - 60_000_000_000L).coerceAtLeast(1L),
            wallClockMs = nowWallMs - 60_000L,
        )
        shadowOf(locationManager).setLastKnownLocation(LocationManager.FUSED_PROVIDER, minuteOldFix)
        shadowOf(locationManager).setProviderEnabled(LocationManager.FUSED_PROVIDER, true)

        val source = AndroidLocationSource(context, clockMs = { nowWallMs })
        val result = source.refreshLocation()

        assertThat(result).isEqualTo(LocationFix(51.5000, -0.1200))
        assertThat(source.telemetry.value.cacheHitsLastHour).isEqualTo(1)
        assertThat(source.telemetry.value.activeChecksLastHour).isEqualTo(0)
        assertThat(source.activeListenerCountForTesting).isEqualTo(0)
    }

    @Test
    fun refreshLocation_whenLastKnownFixIsOlderThan2Minutes_triggersFreshCurrentLocationUpdate() = runTest {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val staleHomeFix = buildLocation(
            provider = LocationManager.GPS_PROVIDER,
            lat = 51.5000,
            lon = -0.1200,
            elapsedNanos = (nowNanos - 150_000_000_000L).coerceAtLeast(1L),
            wallClockMs = System.currentTimeMillis() - 150_000L,
        )
        shadowOf(locationManager).setLastKnownLocation(LocationManager.GPS_PROVIDER, staleHomeFix)
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        val source = AndroidLocationSource(context)

        // 1. Immediately returns the cached fix so UI never blocks, while firing async single-shot update
        val initialResult = source.refreshLocation()
        assertThat(initialResult).isEqualTo(LocationFix(51.5000, -0.1200))
        assertThat(source.telemetry.value.activeChecksLastHour).isEqualTo(1)

        // 2. Simulate OS delivering the fresh location fix for the in-flight request
        val freshAwayFix = buildLocation(
            provider = LocationManager.GPS_PROVIDER,
            lat = 51.5200,
            lon = -0.1400,
            elapsedNanos = SystemClock.elapsedRealtimeNanos(),
        )
        shadowOf(locationManager).simulateLocation(freshAwayFix)
        shadowOf(Looper.getMainLooper()).idle()

        // currentLocation StateFlow automatically updates to the fresh fix and cleans up listeners
        assertThat(source.currentLocation.value).isEqualTo(LocationFix(51.5200, -0.1400))
        assertThat(source.activeListenerCountForTesting).isEqualTo(0)
    }

    @Test
    fun selectPreferredProvider_prioritizesFusedThenNetworkAndAvoidsGpsWhenLowPowerAvailable() {
        val source = AndroidLocationSource(context)

        shadowOf(locationManager).setProviderEnabled(LocationManager.FUSED_PROVIDER, true)
        shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        assertThat(source.selectPreferredProvider(locationManager)).isEqualTo(LocationManager.FUSED_PROVIDER)

        shadowOf(locationManager).setProviderEnabled(LocationManager.FUSED_PROVIDER, false)
        assertThat(source.selectPreferredProvider(locationManager)).isEqualTo(LocationManager.NETWORK_PROVIDER)

        shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        assertThat(source.selectPreferredProvider(locationManager)).isEqualTo(LocationManager.GPS_PROVIDER)
    }

    @Test
    fun refreshLocation_throttlesRapidRepeatedCallsWithinCooldownWindow_preventingOverUse() = runTest {
        var fakeClockMs = 1_000_000L
        val staleHomeFix = buildLocation(
            provider = LocationManager.NETWORK_PROVIDER,
            lat = 51.5000,
            lon = -0.1200,
            elapsedNanos = 1L,
            wallClockMs = fakeClockMs - 150_000L, // > 2 minutes old
        )
        shadowOf(locationManager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, staleHomeFix)
        shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

        val source = AndroidLocationSource(context, clockMs = { fakeClockMs })

        // Simulate ON_START + ON_RESUME + 8 rapid home screen visits within 15 seconds (10 total calls)
        repeat(10) { step ->
            fakeClockMs += (step * 1_000L)
            source.refreshLocation()
        }

        val telemetry = source.telemetry.value
        assertThat(telemetry.activeChecksLastHour).isEqualTo(1)
        assertThat(telemetry.activeChecksLast24h).isEqualTo(1)
        assertThat(telemetry.throttledSkipsLastHour).isEqualTo(9)
        assertThat(telemetry.lastProviderUsed).isEqualTo(LocationManager.NETWORK_PROVIDER)
    }

    @Test
    fun refreshLocation_cleansUpActiveListenerAndRecordsTimeoutAfter5Seconds() = runTest {
        val fakeClockMs = 1_000_000L
        val staleFix = buildLocation(
            provider = LocationManager.NETWORK_PROVIDER,
            lat = 51.5000,
            lon = -0.1200,
            elapsedNanos = 1L,
            wallClockMs = fakeClockMs - 150_000L,
        )
        shadowOf(locationManager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, staleFix)
        shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

        val source = AndroidLocationSource(context, clockMs = { fakeClockMs })
        source.refreshLocation()

        assertThat(source.activeListenerCountForTesting).isEqualTo(1)
        assertThat(source.telemetry.value.timeoutsLast24h).isEqualTo(0)

        // Advance main looper by 5 seconds without delivering a location fix
        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis(
                AndroidLocationSource.ACTIVE_REQUEST_TIMEOUT_MS + 100L,
            ),
        )

        assertThat(source.activeListenerCountForTesting).isEqualTo(0)
        assertThat(source.telemetry.value.timeoutsLast24h).isEqualTo(1)
    }

    @Test
    fun telemetry_agesOut1hAnd24hWindowsAndPersistsAcrossRestarts() = runTest {
        var fakeClockMs = 100_000_000L
        val staleFix = buildLocation(
            provider = LocationManager.NETWORK_PROVIDER,
            lat = 51.5000,
            lon = -0.1200,
            elapsedNanos = 1L,
            wallClockMs = 1_000L,
        )
        shadowOf(locationManager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, staleFix)
        shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

        val source = AndroidLocationSource(context, clockMs = { fakeClockMs })

        // First active check at T = 0
        source.refreshLocation()
        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis(
                AndroidLocationSource.ACTIVE_REQUEST_TIMEOUT_MS + 100L,
            ),
        )
        assertThat(source.telemetry.value.activeChecksLastHour).isEqualTo(1)
        assertThat(source.telemetry.value.activeChecksLast24h).isEqualTo(1)

        // Advance 2 hours -> first check ages out of 1h window, remains in 24h window; trigger second check
        fakeClockMs += 2 * 60 * 60 * 1_000L
        source.refreshLocation()
        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis(
                AndroidLocationSource.ACTIVE_REQUEST_TIMEOUT_MS + 100L,
            ),
        )
        assertThat(source.telemetry.value.activeChecksLastHour).isEqualTo(1)
        assertThat(source.telemetry.value.activeChecksLast24h).isEqualTo(2)

        // Recreate AndroidLocationSource (simulating process restart) -> 24h history is preserved
        val restartedSource = AndroidLocationSource(context, clockMs = { fakeClockMs })
        assertThat(restartedSource.telemetry.value.activeChecksLastHour).isEqualTo(1)
        assertThat(restartedSource.telemetry.value.activeChecksLast24h).isEqualTo(2)
    }
}
