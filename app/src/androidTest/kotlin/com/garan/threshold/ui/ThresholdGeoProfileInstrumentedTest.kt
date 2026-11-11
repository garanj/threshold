package com.garan.threshold.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.garan.threshold.MainActivity
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.GeoTrigger
import com.garan.threshold.core.model.GeoTriggerMode
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.ui.launcher.DialerPackageResolver
import com.garan.threshold.ui.util.ImeUtils
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ThresholdGeoProfileInstrumentedTest {

    private lateinit var device: UiDevice
    private lateinit var locationManager: LocationManager
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        device = UiDevice.getInstance(instrumentation)
        locationManager = targetContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val pkg = targetContext.packageName
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_FINE_LOCATION")
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_COARSE_LOCATION")
        device.executeShellCommand("appops set $pkg android:mock_location allow")
        device.executeShellCommand("cmd location set-location-enabled true")

        runCatching { locationManager.removeTestProvider(TEST_PROVIDER) }
        locationManager.addTestProvider(
            TEST_PROVIDER,
            false,
            false,
            false,
            false,
            true,
            true,
            true,
            ProviderProperties.POWER_USAGE_LOW,
            ProviderProperties.ACCURACY_FINE,
        )
        locationManager.setTestProviderEnabled(TEST_PROVIDER, true)

        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
    }

    @After
    fun tearDown() {
        runCatching {
            locationManager.setTestProviderEnabled(TEST_PROVIDER, false)
            locationManager.removeTestProvider(TEST_PROVIDER)
        }
        scenario?.onActivity { activity ->
            runBlocking {
                activity.configureScheduleProfilesForTesting(
                    baseProfile = BaseProfileSettings(),
                    customProfiles = emptyList(),
                )
            }
        }
        scenario?.close()
        try {
            InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        } catch (_: Exception) {}
        device.pressHome()
    }

    private fun pushMockLocation(latitude: Double, longitude: Double) {
        val mockLocation = Location(TEST_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.accuracy = 5.0f
            this.altitude = 0.0
            this.time = System.currentTimeMillis()
            this.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(TEST_PROVIDER, mockLocation)
        val deadline = SystemClock.uptimeMillis() + 1_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val last = runCatching { locationManager.getLastKnownLocation(TEST_PROVIDER) }.getOrNull()
            if (last != null &&
                abs(last.latitude - latitude) < 1e-5 &&
                abs(last.longitude - longitude) < 1e-5
            ) {
                break
            }
            SystemClock.sleep(15L)
        }
    }

    private fun dumpUiState(targetLabel: String): String {
        var dump = "targetLabel=$targetLabel"
        scenario?.onActivity { activity ->
            val st = activity.searchViewModel.uiState.value
            val loc = activity.locationSource.currentLocation.value
            dump = "targetLabel=$targetLabel, query='${st.query}', isSearching=${st.isSearching}, " +
                "activeProfiles=${st.activeProfiles.map { it.name }}, " +
                "results=${st.results.map { it.item.label }}, loc=$loc"
        }
        return dump
    }

    @Test
    fun geoProfile_activatesOnMockGpsEntryPreservesHysteresisAtPerimeterAndDeactivatesOnExit() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = targetContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = targetContext.getSystemService(Context.USER_SERVICE) as UserManager
        val myUser = Process.myUserHandle()
        val userSerial = userManager.getSerialNumberForUser(myUser)
        val dialerPkg = DialerPackageResolver.resolveDefaultDialerPackage(targetContext)

        val allActivities = launcherApps.getActivityList(null, myUser)
        val labelCounts = allActivities.groupingBy { it.label.toString().trim() }.eachCount()
        val targetActivity = allActivities.first { info ->
            val label = info.label.toString().trim()
            info.applicationInfo.packageName != targetContext.packageName &&
                info.applicationInfo.packageName != dialerPkg &&
                label.length >= 4 &&
                labelCounts[label] == 1
        }
        val targetLabel = targetActivity.label.toString().trim()
        val searchPrefix = targetLabel.take(3)
        val targetComponent = ComponentKey(
            packageName = targetActivity.componentName.packageName,
            activityName = targetActivity.componentName.className,
            userSerial = userSerial,
        )

        val officeLat = 51.5074
        val officeLon = -0.1278
        val officeProfile = CustomProfile(
            id = "office-geo",
            name = "Office",
            priority = 0,
            enabled = true,
            colorIndex = 1,
            trigger = ProfileTrigger.Geo(
                GeoTrigger(
                    latitude = officeLat,
                    longitude = officeLon,
                    radiusMeters = 250,
                    mode = GeoTriggerMode.INSIDE,
                ),
            ),
            appAssignments = mapOf(
                targetComponent to AppAssignment.Blocked,
            ),
        )

        val intent = Intent(targetContext, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        scenario = ActivityScenario.launch(intent)
        scenario?.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }

        // 1. Start 500m outside the 250m Office zone
        pushMockLocation(
            latitude = officeLat + (500.0 / METERS_PER_DEGREE_LAT),
            longitude = officeLon,
        )
        scenario?.onActivity { activity ->
            runBlocking {
                activity.configureScheduleProfilesForTesting(
                    baseProfile = BaseProfileSettings(),
                    customProfiles = listOf(officeProfile),
                )
            }
            activity.refreshLocationAndReconcileForTesting()
            activity.searchViewModel.activateSearch()
            activity.searchViewModel.onQueryChanged(searchPrefix)
            ImeUtils.dismissIme(activity)
        }
        device.waitForIdle()

        val appSelector = By.pkg(targetContext.packageName).text(targetLabel)

        // Outside zone: target app IS visible in search results, and Office badge is absent
        assertWithMessage(dumpUiState(targetLabel))
            .that(device.wait(Until.findObject(appSelector), 5000L))
            .isNotNull()
        assertWithMessage(dumpUiState(targetLabel))
            .that(device.findObject(By.desc("Active profile: Office")))
            .isNull()

        // 2. Teleport inside the Office zone (50m from center)
        pushMockLocation(
            latitude = officeLat + (50.0 / METERS_PER_DEGREE_LAT),
            longitude = officeLon,
        )
        scenario?.onActivity { activity ->
            activity.refreshLocationAndReconcileForTesting()
        }
        device.waitForIdle()

        // Inside zone: Office badge is visible, and target app disappears from search results
        assertWithMessage(dumpUiState(targetLabel))
            .that(device.wait(Until.findObject(By.desc("Active profile: Office")), 5000L))
            .isNotNull()
        val goneInside = device.wait(Until.gone(appSelector), 5000L)
        var debugDump = ""
        if (!goneInside) {
            // TEMP DEBUG (#62): capture on-screen Threshold nodes + activity records for CI diagnosis
            val out = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(out)
            val nodes = out.toString().lines()
                .filter { it.contains("package=\"${targetContext.packageName}\"") }
                .filter { !it.contains("text=\"\" ") || !it.contains("content-desc=\"\"") }
                .joinToString("\n") { line ->
                    Regex("(index|text|class|content-desc|bounds)=\"[^\"]*\"")
                        .findAll(line).joinToString(" ") { it.value }
                }
            val acts = device.executeShellCommand("dumpsys activity activities")
                .lines()
                .filter { it.contains("threshold") && (it.contains("ActivityRecord") || it.contains("Task{")) }
                .joinToString("\n") { it.trim() }
            debugDump = "\n--- NODES ---\n$nodes\n--- ACTIVITIES ---\n$acts"
        }
        assertWithMessage(dumpUiState(targetLabel) + debugDump)
            .that(goneInside)
            .isTrue()

        // 3. Teleport to 265m from center (inside the 250m + 25m hysteresis exit buffer)
        pushMockLocation(
            latitude = officeLat + (265.0 / METERS_PER_DEGREE_LAT),
            longitude = officeLon,
        )
        scenario?.onActivity { activity ->
            activity.refreshLocationAndReconcileForTesting()
        }
        device.waitForIdle()

        // Hysteresis buffer keeps Office active and target app blocked
        assertWithMessage(dumpUiState(targetLabel))
            .that(device.wait(Until.findObject(By.desc("Active profile: Office")), 5000L))
            .isNotNull()
        assertWithMessage(dumpUiState(targetLabel))
            .that(device.findObject(appSelector))
            .isNull()

        // 4. Teleport to 350m from center (outside the 275m hysteresis buffer)
        pushMockLocation(
            latitude = officeLat + (350.0 / METERS_PER_DEGREE_LAT),
            longitude = officeLon,
        )
        scenario?.onActivity { activity ->
            activity.refreshLocationAndReconcileForTesting()
        }
        device.waitForIdle()

        // Beyond hysteresis buffer: Office profile deactivates and target app reappears
        assertWithMessage(dumpUiState(targetLabel))
            .that(device.wait(Until.findObject(appSelector), 5000L))
            .isNotNull()
        assertWithMessage(dumpUiState(targetLabel))
            .that(device.wait(Until.gone(By.desc("Active profile: Office")), 5000L))
            .isTrue()
    }

    companion object {
        private const val TEST_PROVIDER = "threshold_mock_gps"
        private const val METERS_PER_DEGREE_LAT = 111_194.9266
    }
}
