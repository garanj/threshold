package com.garan.threshold.data.packageindex

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.garan.threshold.core.port.PackageChangeEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AndroidPackageIndexSourceLifecycleInstrumentedTest {

    private val targetPackage = "com.garan.threshold.fixtures.dummy"
    private lateinit var device: UiDevice
    private lateinit var targetContext: Context
    private lateinit var testContext: Context
    private lateinit var apkFile: File
    private lateinit var packageIndexSource: AndroidPackageIndexSource
    private var testScope: CoroutineScope? = null

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        targetContext = instrumentation.targetContext
        testContext = instrumentation.context

        // Extract fixture APK from androidTest assets to target cache directory
        apkFile = File(targetContext.cacheDir, "dummy-app.apk")
        testContext.assets.open("dummy-app.apk").use { input ->
            FileOutputStream(apkFile).use { output ->
                input.copyTo(output)
            }
        }

        // Disable package verifiers to prevent package manager verification pauses
        device.executeShellCommand("settings put global package_verifier_enable 0")
        device.executeShellCommand("settings put global verifier_verify_adb_installs 0")
        device.executeShellCommand("settings put global package_verifier_user_consent -1")

        // Clean up any stale fixture install prior to test run
        device.executeShellCommand("pm uninstall $targetPackage")

        packageIndexSource = AndroidPackageIndexSource(targetContext)
    }

    @After
    fun tearDown() {
        testScope?.cancel()
        device.executeShellCommand("pm uninstall $targetPackage")
        if (apkFile.exists()) {
            apkFile.delete()
        }
    }

    @Test
    fun realOsPackageInstallAndUninstallEmitsAddedAndRemovedEvents() = runBlocking {
        testScope = CoroutineScope(Dispatchers.Default + Job())

        val events = mutableListOf<PackageChangeEvent>()
        val collectionJob = testScope!!.launch {
            packageIndexSource.observePackageChanges()
                .filter { event ->
                    when (event) {
                        is PackageChangeEvent.Added -> event.component.packageName == targetPackage
                        is PackageChangeEvent.Removed -> event.component.packageName == targetPackage
                        is PackageChangeEvent.Changed -> event.component.packageName == targetPackage
                    }
                }
                .collect { event ->
                    events.add(event)
                }
        }

        // 1. Install fixture APK via real Android OS package manager shell command
        val installOutput = device.executeShellCommand("pm install -t -r -d -g ${apkFile.absolutePath}")
        assertThat(installOutput.lowercase()).contains("success")

        // Wait up to 10 seconds for OS LauncherApps.Callback Binder dispatch
        withTimeout(10_000) {
            while (events.none { it is PackageChangeEvent.Added }) {
                kotlinx.coroutines.delay(100)
            }
        }

        val addedEvent = events.first { it is PackageChangeEvent.Added } as PackageChangeEvent.Added
        assertThat(addedEvent.component.packageName).isEqualTo(targetPackage)
        assertThat(addedEvent.app.label).isNotNull()

        // Verify package now appears in real query
        val installedApps = packageIndexSource.getInstalledApps()
        assertThat(installedApps.any { it.component.packageName == targetPackage }).isTrue()

        // 2. Uninstall fixture APK via real Android OS package manager shell command
        val uninstallOutput = device.executeShellCommand("pm uninstall $targetPackage")
        assertThat(uninstallOutput.lowercase()).contains("success")

        // Wait up to 10 seconds for OS LauncherApps.Callback uninstall dispatch
        withTimeout(10_000) {
            while (events.none { it is PackageChangeEvent.Removed }) {
                kotlinx.coroutines.delay(100)
            }
        }

        val removedEvent = events.first { it is PackageChangeEvent.Removed } as PackageChangeEvent.Removed
        assertThat(removedEvent.component.packageName).isEqualTo(targetPackage)

        // Verify package no longer appears in real query
        val postUninstallApps = packageIndexSource.getInstalledApps()
        assertThat(postUninstallApps.none { it.component.packageName == targetPackage }).isTrue()

        collectionJob.cancel()
    }
}
