package com.garan.threshold.data.packageindex

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.core.port.PackageChangeEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLauncherApps
import org.robolectric.shadows.ShadowUserManager
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPackageIndexSourceTest {

    private lateinit var context: Context
    private lateinit var launcherApps: LauncherApps
    private lateinit var userManager: UserManager
    private lateinit var shadowLauncherApps: ShadowLauncherApps
    private lateinit var shadowUserManager: ShadowUserManager
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var packageIndexSource: AndroidPackageIndexSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        shadowLauncherApps = Shadows.shadowOf(launcherApps)
        shadowUserManager = Shadows.shadowOf(userManager)

        packageIndexSource = AndroidPackageIndexSource(
            launcherApps = launcherApps,
            userManager = userManager,
            ioDispatcher = testDispatcher,
        )
    }

    private fun createLauncherActivityInfo(
        component: ComponentName,
        labelStr: String,
        userHandle: UserHandle,
    ): LauncherActivityInfo {
        val info = Shadow.newInstanceOf(LauncherActivityInfo::class.java)
        val activityInfo = ActivityInfo().apply {
            packageName = component.packageName
            name = component.className
            applicationInfo = ApplicationInfo().apply {
                packageName = component.packageName
                nonLocalizedLabel = labelStr
            }
            nonLocalizedLabel = labelStr
        }
        val internal = ReflectionHelpers.callConstructor(
            Class.forName("android.content.pm.LauncherActivityInfoInternal"),
            ReflectionHelpers.ClassParameter(ActivityInfo::class.java, activityInfo),
            ReflectionHelpers.ClassParameter(
                Class.forName("android.content.pm.IncrementalStatesInfo"),
                null,
            ),
            ReflectionHelpers.ClassParameter(UserHandle::class.java, userHandle),
        )
        ReflectionHelpers.setField(internal, "mComponentName", component)
        ReflectionHelpers.setField(info, "mInternal", internal)
        ReflectionHelpers.setField(info, "mPm", context.packageManager)
        return info
    }

    @Test
    fun `getInstalledApps returns apps across personal and work profiles`() = runTest(testDispatcher) {
        val personalUser = Process.myUserHandle()
        val workUser = UserHandle.getUserHandleForUid(100000)
        shadowUserManager.addUserProfile(personalUser)
        shadowUserManager.addUserProfile(workUser)
        shadowUserManager.setSerialNumberForUser(personalUser, 0L)
        shadowUserManager.setSerialNumberForUser(workUser, 10L)

        val personalClock = createLauncherActivityInfo(
            ComponentName("com.example.clock", "com.example.clock.ClockActivity"),
            "Clock",
            personalUser,
        )
        val workSlack = createLauncherActivityInfo(
            ComponentName("com.slack", "com.slack.SlackActivity"),
            "Slack (Work)",
            workUser,
        )

        shadowLauncherApps.addActivity(personalUser, personalClock)
        shadowLauncherApps.addActivity(workUser, workSlack)

        val apps = packageIndexSource.getInstalledApps()
        assertThat(apps).containsExactly(
            InstalledApp(
                component = ComponentKey("com.example.clock", "com.example.clock.ClockActivity", userSerial = 0L),
                label = "Clock",
            ),
            InstalledApp(
                component = ComponentKey("com.slack", "com.slack.SlackActivity", userSerial = 10L),
                label = "Slack (Work)",
            ),
        )

        val components = packageIndexSource.getInstalledComponents()
        assertThat(components).containsExactly(
            ComponentKey("com.example.clock", "com.example.clock.ClockActivity", userSerial = 0L),
            ComponentKey("com.slack", "com.slack.SlackActivity", userSerial = 10L),
        )
    }

    @Test
    fun `callback onPackageAdded and onPackageRemoved emit correct domain events`() = runTest(testDispatcher) {
        val user = Process.myUserHandle()
        shadowUserManager.addUserProfile(user)
        shadowUserManager.setSerialNumberForUser(user, 0L)

        val clockComponent = ComponentName("com.example.clock", "com.example.clock.ClockActivity")
        val clockInfo = createLauncherActivityInfo(clockComponent, "Clock", user)
        shadowLauncherApps.addActivity(user, clockInfo)

        val events = mutableListOf<PackageChangeEvent>()
        val callback = packageIndexSource.createLauncherCallback { events.add(it) }

        // Test onPackageAdded
        callback.onPackageAdded("com.example.clock", user)
        assertThat(events).hasSize(1)
        val addedEvent = events[0] as PackageChangeEvent.Added
        assertThat(addedEvent.app).isEqualTo(
            InstalledApp(
                component = ComponentKey("com.example.clock", "com.example.clock.ClockActivity", userSerial = 0L),
                label = "Clock",
            ),
        )

        // Test onPackageChanged with new activity added to package
        val alarmComponent = ComponentName("com.example.clock", "com.example.clock.AlarmActivity")
        val alarmInfo = createLauncherActivityInfo(alarmComponent, "Alarms", user)
        shadowLauncherApps.addActivity(user, alarmInfo)
        callback.onPackageChanged("com.example.clock", user)
        assertThat(events).hasSize(3)
        assertThat(events[1]).isInstanceOf(PackageChangeEvent.Changed::class.java)
        assertThat(events[2]).isInstanceOf(PackageChangeEvent.Changed::class.java)

        // Test onPackageRemoved
        callback.onPackageRemoved("com.example.clock", user)
        assertThat(events).hasSize(5)
        val removed1 = events[3] as PackageChangeEvent.Removed
        val removed2 = events[4] as PackageChangeEvent.Removed
        assertThat(setOf(removed1.component, removed2.component)).containsExactly(
            ComponentKey("com.example.clock", "com.example.clock.ClockActivity", userSerial = 0L),
            ComponentKey("com.example.clock", "com.example.clock.AlarmActivity", userSerial = 0L),
        )
        // Test onPackagesAvailable and onPackagesUnavailable
        callback.onPackagesAvailable(arrayOf("com.example.clock"), user, false)
        // 2 activities re-added
        assertThat(events).hasSize(7)

        callback.onPackagesUnavailable(arrayOf("com.example.clock"), user, false)
        // 2 activities removed
        assertThat(events).hasSize(9)
    }

    @Test
    fun `observePackageChanges emits events in real-time when callback is registered`() = runTest(testDispatcher) {
        val user = Process.myUserHandle()
        shadowUserManager.addUserProfile(user)
        shadowUserManager.setSerialNumberForUser(user, 0L)

        val clockComponent = ComponentName("com.example.clock", "com.example.clock.ClockActivity")
        val clockInfo = createLauncherActivityInfo(clockComponent, "Clock", user)
        shadowLauncherApps.addActivity(user, clockInfo)

        val collected = mutableListOf<PackageChangeEvent>()
        val job = launch(testDispatcher) {
            packageIndexSource.observePackageChanges().collect {
                collected.add(it)
            }
        }
        testScheduler.advanceUntilIdle()

        shadowLauncherApps.notifyPackageAdded("com.example.clock")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        assertThat(collected).isNotEmpty()
        assertThat(
            (collected.first() as PackageChangeEvent.Added).app.component.packageName,
        ).isEqualTo("com.example.clock")

        job.cancel()
    }

    @Test
    fun `observePackageChanges unregisters callback upon flow cancellation`() = runTest(testDispatcher) {
        val user = Process.myUserHandle()
        shadowUserManager.addUserProfile(user)
        shadowUserManager.setSerialNumberForUser(user, 0L)

        val emittedEvents = mutableListOf<PackageChangeEvent>()
        val job = launch(testDispatcher) {
            packageIndexSource.observePackageChanges().collect {
                emittedEvents.add(it)
            }
        }

        testScheduler.advanceUntilIdle()

        // Cancel collection
        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `getInstalledApps and callbacks exclude Threshold self package`() = runTest(testDispatcher) {
        val user = Process.myUserHandle()
        shadowUserManager.addUserProfile(user)
        shadowUserManager.setSerialNumberForUser(user, 0L)

        val selfActivity = createLauncherActivityInfo(
            ComponentName("com.garan.threshold", "com.garan.threshold.MainActivity"),
            "Threshold",
            user,
        )
        val cameraActivity = createLauncherActivityInfo(
            ComponentName("com.android.camera", "com.android.camera.CameraActivity"),
            "Camera",
            user,
        )
        shadowLauncherApps.addActivity(user, selfActivity)
        shadowLauncherApps.addActivity(user, cameraActivity)

        val apps = packageIndexSource.getInstalledApps()
        assertThat(apps).hasSize(1)
        assertThat(apps.first().component.packageName).isEqualTo("com.android.camera")

        val events = mutableListOf<PackageChangeEvent>()
        val callback = packageIndexSource.createLauncherCallback { events.add(it) }
        callback.onPackageAdded("com.garan.threshold", user)
        callback.onPackageChanged("com.garan.threshold", user)
        assertThat(events).isEmpty()
    }

    @Test
    fun `onPackageAdded for headless package never blocks caller thread`() = runTest(testDispatcher) {
        val user = Process.myUserHandle()
        shadowUserManager.addUserProfile(user)
        shadowUserManager.setSerialNumberForUser(user, 0L)

        val events = mutableListOf<PackageChangeEvent>()
        val callback = packageIndexSource.createLauncherCallback { events.add(it) }

        val startNanos = System.nanoTime()
        callback.onPackageAdded("com.example.headless.service", user)
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

        // Must return immediately (< 50ms), never blocking the main thread with Thread.sleep(200) * 5
        assertThat(elapsedMs).isLessThan(50L)
        assertThat(events).isEmpty()
    }

    @Test
    fun `observePackageChanges retries delayed package parsing asynchronously without blocking main thread`() =
        runTest(testDispatcher) {
            val user = Process.myUserHandle()
            shadowUserManager.addUserProfile(user)
            shadowUserManager.setSerialNumberForUser(user, 0L)

            val collected = mutableListOf<PackageChangeEvent>()
            val job = launch(testDispatcher) {
                packageIndexSource.observePackageChanges().collect {
                    collected.add(it)
                }
            }
            testScheduler.advanceUntilIdle()

            // Notify package added BEFORE activity is registered in PackageManager/LauncherApps
            val startNanos = System.nanoTime()
            shadowLauncherApps.notifyPackageAdded("com.example.delayed")
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val mainThreadElapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            assertThat(mainThreadElapsedMs).isLessThan(50L)

            // Register activity 150ms later in virtual time
            testScheduler.advanceTimeBy(150)
            val delayedInfo = createLauncherActivityInfo(
                ComponentName("com.example.delayed", "com.example.delayed.MainActivity"),
                "Delayed App",
                user,
            )
            shadowLauncherApps.addActivity(user, delayedInfo)

            testScheduler.advanceUntilIdle()
            assertThat(collected).hasSize(1)
            val added = collected.first() as PackageChangeEvent.Added
            assertThat(added.app.label).isEqualTo("Delayed App")

            job.cancel()
        }
}
