package com.garan.threshold.data.packageindex

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.core.port.PackageChangeEvent
import com.garan.threshold.core.port.PackageIndexSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Android implementation of [PackageIndexSource] querying [LauncherApps] and observing lifecycle callbacks.
 * Supports multi-user and Android Work profiles by mapping [UserHandle] to long serial numbers via [UserManager].
 */
class AndroidPackageIndexSource(
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val selfPackageName: String = "com.garan.threshold",
) : PackageIndexSource {

    constructor(context: Context, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : this(
        launcherApps = (context.applicationContext ?: context)
            .getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps,
        userManager = (context.applicationContext ?: context)
            .getSystemService(Context.USER_SERVICE) as UserManager,
        mainHandler = Handler(Looper.getMainLooper()),
        ioDispatcher = ioDispatcher,
        selfPackageName = (context.applicationContext ?: context).packageName,
    )

    // Tracks known installed components to resolve activities upon onPackageRemoved events
    private val knownComponents = ConcurrentHashMap<ComponentKey, String>()

    override suspend fun getInstalledApps(): List<InstalledApp> = withContext(ioDispatcher) {
        val apps = mutableListOf<InstalledApp>()
        val profiles = launcherApps.profiles

        for (user in profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val activities = launcherApps.getActivityList(null, user)

            for (info in activities) {
                val pkg = info.componentName.packageName
                if (pkg == selfPackageName) continue

                val component = ComponentKey(
                    packageName = pkg,
                    activityName = info.componentName.className,
                    userSerial = serial,
                )
                val label = info.label?.toString()?.ifBlank { null } ?: pkg
                val installedApp = InstalledApp(component = component, label = label)
                apps.add(installedApp)
                knownComponents[component] = label
            }
        }

        apps
    }

    override suspend fun getApp(component: ComponentKey): InstalledApp? = withContext(ioDispatcher) {
        val cachedLabel = knownComponents[component]
        if (cachedLabel != null) {
            return@withContext InstalledApp(component, cachedLabel)
        }

        val profiles = launcherApps.profiles
        for (user in profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            if (serial == component.userSerial) {
                val activities = launcherApps.getActivityList(component.packageName, user)
                for (info in activities) {
                    if (info.componentName.className == component.activityName) {
                        val label = info.label?.toString()?.ifBlank { null } ?: info.componentName.packageName
                        knownComponents[component] = label
                        return@withContext InstalledApp(component = component, label = label)
                    }
                }
            }
        }
        null
    }

    override fun observePackageChanges(): Flow<PackageChangeEvent> = callbackFlow {
        val callback = createLauncherCallback(
            retryScope = this,
            onEvent = { event -> trySend(event) },
        )

        launcherApps.registerCallback(callback, mainHandler)

        awaitClose {
            launcherApps.unregisterCallback(callback)
        }
    }.flowOn(ioDispatcher)

    internal fun createLauncherCallback(
        retryScope: CoroutineScope? = null,
        onEvent: (PackageChangeEvent) -> Unit,
    ): LauncherApps.Callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            if (packageName == selfPackageName) return

            val serial = userManager.getSerialNumberForUser(user)
            val activities = launcherApps.getActivityList(packageName, user)

            if (activities.isNotEmpty()) {
                emitAddedActivities(activities, serial, onEvent)
            } else if (retryScope != null) {
                // Non-blocking coroutine retry on ioDispatcher for newly installed packages
                // whose launcher intent filters are still being parsed by PackageManager.
                retryScope.launch {
                    var retries = 0
                    while (retries < 5) {
                        delay(200)
                        val delayedActivities = launcherApps.getActivityList(packageName, user)
                        if (delayedActivities.isNotEmpty()) {
                            emitAddedActivities(delayedActivities, serial, onEvent)
                            break
                        }
                        retries++
                    }
                }
            }
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            if (packageName == selfPackageName) return

            val serial = userManager.getSerialNumberForUser(user)

            // Match and remove all known components for this package and user serial
            val removed = knownComponents.keys.filter {
                it.packageName == packageName && it.userSerial == serial
            }

            for (component in removed) {
                knownComponents.remove(component)
                onEvent(PackageChangeEvent.Removed(component))
            }
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            if (packageName == selfPackageName) return

            val serial = userManager.getSerialNumberForUser(user)
            val activities = launcherApps.getActivityList(packageName, user)
            val currentComponentKeys = mutableSetOf<ComponentKey>()

            for (info in activities) {
                val component = ComponentKey(
                    packageName = info.componentName.packageName,
                    activityName = info.componentName.className,
                    userSerial = serial,
                )
                currentComponentKeys.add(component)
                val label = info.label?.toString()?.ifBlank { null } ?: info.componentName.packageName
                val app = InstalledApp(component = component, label = label)
                knownComponents[component] = label
                onEvent(PackageChangeEvent.Changed(app))
            }

            // Any previously known component for this package that is no longer in activities was removed
            val stale = knownComponents.keys.filter {
                it.packageName == packageName && it.userSerial == serial && it !in currentComponentKeys
            }
            for (component in stale) {
                knownComponents.remove(component)
                onEvent(PackageChangeEvent.Removed(component))
            }
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            for (pkg in packageNames) {
                onPackageAdded(pkg, user)
            }
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            for (pkg in packageNames) {
                onPackageRemoved(pkg, user)
            }
        }

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) {}
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) {}
        override fun onShortcutsChanged(packageName: String, shortcuts: List<ShortcutInfo>, user: UserHandle) {}
    }

    private fun emitAddedActivities(
        activities: List<android.content.pm.LauncherActivityInfo>,
        serial: Long,
        onEvent: (PackageChangeEvent) -> Unit,
    ) {
        for (info in activities) {
            val pkg = info.componentName.packageName
            if (pkg == selfPackageName) continue

            val component = ComponentKey(
                packageName = pkg,
                activityName = info.componentName.className,
                userSerial = serial,
            )
            val label = info.label?.toString()?.ifBlank { null } ?: pkg
            val app = InstalledApp(component = component, label = label)
            knownComponents[component] = label
            onEvent(PackageChangeEvent.Added(app))
        }
    }
}
