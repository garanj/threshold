package com.garan.threshold

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.ProfileCapability
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.port.ClockSource
import com.garan.threshold.core.port.LocalDayTime
import com.garan.threshold.core.port.SystemClockSource
import com.garan.threshold.core.search.ActionCandidate
import com.garan.threshold.data.antimetrics.AndroidAntiMetricsRepository
import com.garan.threshold.data.haptics.AndroidHapticEngine
import com.garan.threshold.data.location.AndroidLocationSource
import com.garan.threshold.data.packageindex.AndroidPackageIndexSource
import com.garan.threshold.data.settings.AndroidSettingsRepository
import com.garan.threshold.schedule.AndroidProfileCapabilityChecker
import com.garan.threshold.schedule.AndroidScheduleAlarmScheduler
import com.garan.threshold.schedule.ScheduleTransitionNotifier
import com.garan.threshold.ui.home.ThresholdHomeScreen
import com.garan.threshold.ui.launcher.AndroidAppLauncher
import com.garan.threshold.ui.launcher.DialerPackageResolver
import com.garan.threshold.ui.motion.ThresholdMotion
import com.garan.threshold.ui.search.SearchViewModel
import com.garan.threshold.ui.settings.ConfigureAppsScreen
import com.garan.threshold.ui.settings.ProfilesScreen
import com.garan.threshold.ui.settings.SettingsViewModel
import com.garan.threshold.ui.settings.ThresholdSettingsScreen
import com.garan.threshold.ui.theme.ThresholdTheme
import com.garan.threshold.ui.util.ImeUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
sealed interface ThresholdRoute : NavKey {
    @Serializable
    data object Home : ThresholdRoute

    @Serializable
    data object Settings : ThresholdRoute

    @Serializable
    data object Profiles : ThresholdRoute

    @Serializable
    data class ConfigureApps(val profileId: String? = null) : ThresholdRoute
}

class MainActivity : ComponentActivity() {

    private val packageIndexSource by lazy { AndroidPackageIndexSource(applicationContext ?: this) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }
    private val hapticEngine by lazy { AndroidHapticEngine(applicationContext ?: this) }
    private val settingsRepository by lazy { AndroidSettingsRepository(applicationContext ?: this) }
    internal val locationSource by lazy { AndroidLocationSource(applicationContext ?: this) }

    @Volatile
    private var pendingLocationResolvedCallback: ((LocationFix) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            lifecycleScope.launch {
                val fix = locationSource.refreshLocation()
                if (fix != null) {
                    pendingLocationResolvedCallback?.invoke(fix)
                }
                pendingLocationResolvedCallback = null
                ScheduleTransitionNotifier.notifyTransition()
            }
        } else {
            pendingLocationResolvedCallback = null
            ScheduleTransitionNotifier.notifyTransition()
        }
    }

    @Volatile
    private var clockOverrideForTesting: ClockSource? = null

    @Volatile
    private var exactAlarmOverrideForTesting: Boolean? = null

    private val activeClockSource = ClockSource {
        clockOverrideForTesting?.now() ?: SystemClockSource.now()
    }

    private val scheduleAlarmScheduler by lazy {
        AndroidScheduleAlarmScheduler(
            context = applicationContext ?: this,
            clockSource = activeClockSource,
        )
    }
    private val capabilityChecker by lazy {
        AndroidProfileCapabilityChecker(
            context = applicationContext ?: this,
            exactAlarmOverride = { exactAlarmOverrideForTesting },
        )
    }
    internal val antiMetricsRepository by lazy { AndroidAntiMetricsRepository(applicationContext ?: this) }
    internal lateinit var searchViewModel: SearchViewModel
        private set
    internal lateinit var settingsViewModel: SettingsViewModel
        private set

    private val homeIntentResetCounter = mutableIntStateOf(0)

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // As a root home launcher, never delegate to super.onBackPressed() which finishes the activity
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Pressing Home while already on Threshold collapses transient search/friction/settings state and hides IME
        if (::searchViewModel.isInitialized) {
            searchViewModel.dismissSearch()
        }
        homeIntentResetCounter.intValue++
        ImeUtils.dismissIme(this)
    }

    @androidx.annotation.VisibleForTesting
    suspend fun configureScheduleProfilesForTesting(
        baseProfile: BaseProfileSettings = BaseProfileSettings(),
        customProfiles: List<CustomProfile>,
    ) {
        settingsRepository.updateBaseProfile(baseProfile)
        settingsRepository.updateCustomProfiles(customProfiles)
        if (::searchViewModel.isInitialized) {
            searchViewModel.reconcileForTesting(settingsRepository.getSettings())
        }
    }

    @androidx.annotation.VisibleForTesting
    fun setClockAndTriggerAlarmForTesting(dayOfWeek: DayOfWeek, timeOfDay: TimeOfDay) {
        clockOverrideForTesting = ClockSource { LocalDayTime(dayOfWeek, timeOfDay) }
        ScheduleTransitionNotifier.notifyTransition()
        if (::searchViewModel.isInitialized) {
            kotlinx.coroutines.runBlocking {
                searchViewModel.reconcileForTesting()
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    fun setExactAlarmPermissionForTesting(granted: Boolean?) {
        exactAlarmOverrideForTesting = granted
        ScheduleTransitionNotifier.notifyTransition()
        if (::searchViewModel.isInitialized) {
            kotlinx.coroutines.runBlocking {
                searchViewModel.reconcileForTesting()
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    fun refreshLocationAndReconcileForTesting() {
        kotlinx.coroutines.runBlocking {
            locationSource.refreshLocation()
            if (::searchViewModel.isInitialized) {
                searchViewModel.reconcileForTesting()
            }
        }
    }

    private fun requestLocationOrRefresh(onLocationResolved: ((LocationFix) -> Unit)? = null) {
        if (locationSource.hasLocationPermission()) {
            lifecycleScope.launch {
                val fix = locationSource.refreshLocation()
                if (fix != null) {
                    onLocationResolved?.invoke(fix)
                }
            }
        } else {
            pendingLocationResolvedCallback = onLocationResolved
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    private fun openExternalMapApp(center: LocationFix?) {
        val uriString = if (center != null) {
            "geo:${center.latitude},${center.longitude}?q=${center.latitude},${center.longitude}"
        } else {
            "geo:0,0"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsAction = ActionCandidate(
            id = "settings",
            label = "Threshold Settings",
            shortcutKeyword = ":settings",
        )

        searchViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(
                    packageIndexSource = packageIndexSource,
                    hapticEngine = hapticEngine,
                    dialerPackageResolver = {
                        DialerPackageResolver.resolveDefaultDialerPackage(this@MainActivity.applicationContext)
                    },
                    actions = listOf(settingsAction),
                    settingsRepository = settingsRepository,
                    clockSource = activeClockSource,
                    scheduleAlarmScheduler = scheduleAlarmScheduler,
                    capabilityChecker = capabilityChecker,
                    locationSource = locationSource,
                ) as T
            },
        )[SearchViewModel::class.java]

        settingsViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
                    packageIndexSource = packageIndexSource,
                    settingsRepository = settingsRepository,
                    dialerPackageResolver = {
                        DialerPackageResolver.resolveDefaultDialerPackage(this@MainActivity.applicationContext)
                    },
                ) as T
            },
        )[SettingsViewModel::class.java]

        // IMPORTANT MEMORY INVARIANT (< 25 MB Typographic Mode / < 35 MB CI PSS Ceiling):
        // Never call searchViewModel.ensureIndexLoaded() eagerly in onCreate().
        // Resolving LauncherActivityInfo.getLabel() across all installed apps causes Android's
        // ResourcesManager to mmap foreign APK resources.arsc tables (+25 MB .apk mmap PSS).
        // Index loading must remain strictly lazy upon user search activation (activateSearch / onQueryChanged).

        setContent {
            ThresholdTheme {
                val backStack = rememberNavBackStack(ThresholdRoute.Home)
                val resetTrigger by homeIntentResetCounter
                val settingsUiState by settingsViewModel.uiState.collectAsState()
                val currentLocation by locationSource.currentLocation.collectAsState()
                val locationTelemetry by locationSource.telemetry.collectAsState()

                LaunchedEffect(resetTrigger) {
                    if (resetTrigger > 0) {
                        while (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                    }
                }

                NavDisplay(
                    backStack = backStack,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                    },
                    transitionSpec = {
                        fadeIn(
                            animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_OPEN_DURATION_MS),
                        ) togetherWith fadeOut(
                            animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_CLOSE_DURATION_MS),
                        )
                    },
                    popTransitionSpec = {
                        fadeIn(
                            animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_OPEN_DURATION_MS),
                        ) togetherWith fadeOut(
                            animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_CLOSE_DURATION_MS),
                        )
                    },
                    predictivePopTransitionSpec = {
                        fadeIn(
                            animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_OPEN_DURATION_MS),
                        ) togetherWith fadeOut(
                            animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_CLOSE_DURATION_MS),
                        )
                    },
                    entryProvider = entryProvider {
                        entry<ThresholdRoute.Home> {
                            ThresholdHomeScreen(
                                viewModel = searchViewModel,
                                appLauncher = appLauncher,
                                hapticEngine = hapticEngine,
                                onDismissIme = { ImeUtils.dismissIme(this@MainActivity) },
                                antiMetricsRepository = antiMetricsRepository,
                                onOpenSettings = {
                                    if (backStack.lastOrNull() != ThresholdRoute.Settings) {
                                        backStack.add(ThresholdRoute.Settings)
                                    }
                                },
                                onResolveCapabilityIssue = { capability ->
                                    when (capability) {
                                        ProfileCapability.EXACT_ALARM -> {
                                            scheduleAlarmScheduler.requestExactAlarmPermission()
                                        }
                                        ProfileCapability.BACKGROUND_LOCATION -> {
                                            if (!locationSource.hasLocationPermission()) {
                                                requestLocationOrRefresh()
                                            } else if (!locationSource.isLocationServicesEnabled()) {
                                                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                runCatching { startActivity(intent) }
                                            }
                                        }
                                        ProfileCapability.NFC_HARDWARE_ENABLED -> {
                                            if (backStack.lastOrNull() != ThresholdRoute.Profiles) {
                                                backStack.add(ThresholdRoute.Profiles)
                                            }
                                        }
                                    }
                                },
                            )
                        }

                        entry<ThresholdRoute.Settings> {
                            LaunchedEffect(Unit) {
                                settingsViewModel.ensureLoaded()
                            }
                            ThresholdSettingsScreen(
                                uiState = settingsUiState,
                                onBack = {
                                    if (backStack.size > 1) backStack.removeLastOrNull()
                                },
                                onNavigateToConfigureApps = {
                                    backStack.add(ThresholdRoute.ConfigureApps(profileId = null))
                                },
                                onNavigateToProfiles = {
                                    backStack.add(ThresholdRoute.Profiles)
                                },
                                onUpdateShowUnlocks = settingsViewModel::updateShowUnlocks,
                                onUpdateShowRest = settingsViewModel::updateShowRest,
                                onUpdateRequireFullTyping = settingsViewModel::updateRequireFullTyping,
                                onUpdateDefaultFriction = settingsViewModel::updateDefaultFriction,
                                onAddQuickAction = settingsViewModel::addQuickAction,
                                onRemoveQuickAction = settingsViewModel::removeQuickAction,
                            )
                        }

                        entry<ThresholdRoute.Profiles> {
                            LaunchedEffect(Unit) {
                                settingsViewModel.ensureLoaded()
                            }
                            ProfilesScreen(
                                profiles = settingsUiState.customProfiles,
                                onSaveProfile = settingsViewModel::saveCustomProfile,
                                onDeleteProfile = settingsViewModel::deleteCustomProfile,
                                onReorderProfiles = settingsViewModel::reorderCustomProfiles,
                                onConfigureProfileApps = { profileId ->
                                    backStack.add(ThresholdRoute.ConfigureApps(profileId = profileId))
                                },
                                onBack = {
                                    if (backStack.size > 1) backStack.removeLastOrNull()
                                },
                                hapticEngine = hapticEngine,
                                canScheduleExactAlarms = scheduleAlarmScheduler::canScheduleExactAlarms,
                                onRequestExactAlarmPermission = scheduleAlarmScheduler::requestExactAlarmPermission,
                                currentLocation = currentLocation,
                                locationTelemetry = locationTelemetry,
                                onUseCurrentLocation = { onResolved ->
                                    requestLocationOrRefresh(onResolved)
                                },
                                onOpenMapApp = ::openExternalMapApp,
                                onEnsureLocationPermissionForProfile = {
                                    requestLocationOrRefresh()
                                },
                            )
                        }

                        entry<ThresholdRoute.ConfigureApps> { route ->
                            LaunchedEffect(Unit) {
                                settingsViewModel.ensureLoaded()
                            }
                            val targetProfile: CustomProfile? = if (route.profileId != null) {
                                settingsUiState.customProfiles.firstOrNull { it.id == route.profileId }
                            } else {
                                null
                            }
                            val initialAssignments =
                                targetProfile?.appAssignments ?: settingsUiState.baseProfile.appAssignments
                            val isCustomProfile = targetProfile != null

                            ConfigureAppsScreen(
                                availableApps = settingsUiState.availableApps,
                                initialAssignments = initialAssignments,
                                defaultFriction = settingsUiState.baseProfile.defaultFriction,
                                onSave = { savedAssignments ->
                                    if (targetProfile != null) {
                                        settingsViewModel.saveCustomProfileAppAssignments(
                                            targetProfile.id,
                                            savedAssignments,
                                        )
                                    } else {
                                        settingsViewModel.saveAppAssignments(savedAssignments)
                                    }
                                },
                                onBack = {
                                    if (backStack.size > 1) backStack.removeLastOrNull()
                                },
                                hapticEngine = hapticEngine,
                                isCustomProfile = isCustomProfile,
                                customProfileName = targetProfile?.name,
                            )
                        }
                    },
                )
            }
        }
    }
}
