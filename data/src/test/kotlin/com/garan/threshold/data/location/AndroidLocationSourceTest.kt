package com.garan.threshold.data.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
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

@Suppress("DEPRECATION")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLocationSourceTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        shadowOf(locationManager).setLocationEnabled(true)
    }

    private fun buildLocation(
        provider: String = LocationManager.GPS_PROVIDER,
        lat: Double,
        lon: Double,
        elapsedNanos: Long = SystemClock.elapsedRealtimeNanos(),
    ): Location = Location(provider).apply {
        latitude = lat
        longitude = lon
        accuracy = 10f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = elapsedNanos
    }

    @Test
    fun refreshLocation_readsMostRecentLastKnownLocationWhenPermittedAndEnabled() = runTest {
        val olderGps = buildLocation(LocationManager.GPS_PROVIDER, 51.5000, -0.1200, elapsedNanos = 1_000L)
        val newerNetwork = buildLocation(LocationManager.NETWORK_PROVIDER, 51.5074, -0.1278, elapsedNanos = 5_000L)
        shadowOf(locationManager).setLastKnownLocation(LocationManager.GPS_PROVIDER, olderGps)
        shadowOf(locationManager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, newerNetwork)

        val source = AndroidLocationSource(context)
        val fix = source.refreshLocation()

        assertThat(fix).isEqualTo(LocationFix(51.5074, -0.1278))
        assertThat(source.currentLocation.value).isEqualTo(LocationFix(51.5074, -0.1278))
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
    fun refreshLocation_whenLastKnownFixIsOlderThan30Seconds_triggersFreshCurrentLocationUpdate() = runTest {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val staleHomeFix = buildLocation(
            provider = LocationManager.GPS_PROVIDER,
            lat = 51.5000,
            lon = -0.1200,
            elapsedNanos = (nowNanos - 60_000_000_000L).coerceAtLeast(1L), // 60s old (> 30s threshold)
        ).apply {
            time = System.currentTimeMillis() - 60_000L
        }
        shadowOf(locationManager).setLastKnownLocation(LocationManager.GPS_PROVIDER, staleHomeFix)
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        val source = AndroidLocationSource(context)

        // 1. Immediately returns the cached fix so UI never blocks, while firing async getCurrentLocation
        val initialResult = source.refreshLocation()
        assertThat(initialResult).isEqualTo(LocationFix(51.5000, -0.1200))

        // 2. Simulate OS delivering the fresh location fix for the in-flight getCurrentLocation request
        val freshAwayFix = buildLocation(
            provider = LocationManager.GPS_PROVIDER,
            lat = 51.5200,
            lon = -0.1400,
            elapsedNanos = SystemClock.elapsedRealtimeNanos(),
        )
        shadowOf(locationManager).simulateLocation(freshAwayFix)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // currentLocation StateFlow automatically updates to the fresh fix
        assertThat(source.currentLocation.value).isEqualTo(LocationFix(51.5200, -0.1400))
    }
}
