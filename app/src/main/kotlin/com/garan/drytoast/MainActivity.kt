package com.garan.drytoast

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.garan.drytoast.core.backup.ConfigBackupCodec
import com.garan.drytoast.core.backup.ConfigImportError
import com.garan.drytoast.core.model.BaseProfileSettings
import com.garan.drytoast.core.model.CustomProfile
import com.garan.drytoast.core.model.DayOfWeek
import com.garan.drytoast.core.model.LocationFix
import com.garan.drytoast.core.model.ProfileCapability
import com.garan.drytoast.core.model.SavedTrigger
import com.garan.drytoast.core.model.TimeOfDay
import com.garan.drytoast.core.port.ClockSource
import com.garan.drytoast.core.port.LocalDayTime
import com.garan.drytoast.core.search.ActionCandidate
import com.garan.drytoast.data.antimetrics.AndroidAntiMetricsRepository
import com.garan.drytoast.data.location.AndroidLocationSource
import com.garan.drytoast.schedule.ScheduleTransitionNotifier
import com.garan.drytoast.ui.home.DryToastHomeScreen
import com.garan.drytoast.ui.launcher.AndroidAppLauncher
import com.garan.drytoast.ui.motion.DryToastMotion
import com.garan.drytoast.ui.onboarding.OnboardingScreen
import com.garan.drytoast.ui.search.SearchViewModel
import com.garan.drytoast.ui.settings.BreakGlassScreen
import com.garan.drytoast.ui.settings.ConfigImportOutcome
import com.garan.drytoast.ui.settings.ConfigureAppsScreen
import com.garan.drytoast.ui.settings.DisableHardenedModeScreen
import com.garan.drytoast.ui.settings.DryToastSettingsScreen
import com.garan.drytoast.ui.settings.FeedbackEmailIntentFactory
import com.garan.drytoast.ui.settings.HardenedModeDeepLink
import com.garan.drytoast.ui.settings.HardenedModeScreen
import com.garan.drytoast.ui.settings.ProfilesScreen
import com.garan.drytoast.ui.settings.SETTINGS_EXPANDED_BREAKPOINT
import com.garan.drytoast.ui.settings.SettingsDetailSection
import com.garan.drytoast.ui.settings.SettingsLockScreen
import com.garan.drytoast.ui.settings.SettingsTwoPaneSceneStrategy
import com.garan.drytoast.ui.settings.SettingsViewModel
import com.garan.drytoast.ui.settings.exitSettingsFromMaster
import com.garan.drytoast.ui.settings.navigateFromSettingsMaster
import com.garan.drytoast.ui.settings.rememberSettingsTwoPaneSceneStrategy
import com.garan.drytoast.ui.theme.DryToastTheme
import com.garan.drytoast.ui.util.ImeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.InputStreamReader

@Serializable
sealed interface DryToastRoute : NavKey {
    @Serializable
    data object Onboarding : DryToastRoute

    @Serializable
    data object Home : DryToastRoute

    @Serializable
    data object Settings : DryToastRoute

    @Serializable
    data object HardenedMode : DryToastRoute

    @Serializable
    data object DisableHardenedMode : DryToastRoute

    @Serializable
    data object SettingsLock : DryToastRoute

    @Serializable
    data object BreakGlass : DryToastRoute

    @Serializable
    data object Profiles : DryToastRoute

    @Serializable
    data class ConfigureApps(val profileId: String? = null) : DryToastRoute
}

class MainActivity : ComponentActivity() {

    private val appContainer by lazy { DryToastAppContainer.from(this) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }

    internal val locationSource: AndroidLocationSource
        get() = appContainer.locationSource

    internal val antiMetricsRepository: AndroidAntiMetricsRepository
        get() = appContainer.antiMetricsRepository

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

    internal lateinit var searchViewModel: SearchViewModel
        private set
    internal lateinit var settingsViewModel: SettingsViewModel
        private set

    private val homeIntentResetCounter = mutableIntStateOf(0)
    private val disableHardenedModeDeepLinkCounter = mutableIntStateOf(0)
    private val settingsLockDeepLinkCounter = mutableIntStateOf(0)

    private val defaultLauncherRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        handleDefaultLauncherRoleResult()
    }

    internal fun isLaunchedAsStandardAppTask(): Boolean = intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true &&
        intent?.hasCategory(Intent.CATEGORY_HOME) != true

    internal fun handleDefaultLauncherRoleResult() {
        val granted = appContainer.launcherRoleController.refresh()
        if (granted) {
            appContainer.pendingOnboardingRoleRequest = false
            appContainer.hapticEngine.targetSnap()
            if (::settingsViewModel.isInitialized) {
                settingsViewModel.setShowRoleRequiredWarning(false)
                settingsViewModel.completeOnboarding()
            } else {
                lifecycleScope.launch {
                    appContainer.settingsRepository.setHasCompletedOnboarding(true)
                }
            }
        } else {
            appContainer.pendingOnboardingRoleRequest = false
            appContainer.hapticEngine.resistanceDetent()
            if (::settingsViewModel.isInitialized) {
                settingsViewModel.setShowRoleRequiredWarning(true)
                settingsViewModel.resetOnboarding()
            } else {
                lifecycleScope.launch {
                    appContainer.settingsRepository.setHasCompletedOnboarding(false)
                }
            }
        }
    }

    internal fun requestDefaultLauncherRole() {
        appContainer.pendingOnboardingRoleRequest = true
        if (::settingsViewModel.isInitialized) {
            settingsViewModel.completeOnboarding()
        }
        val intent = appContainer.launcherRoleController.createRequestRoleIntent()
        val launched = runCatching { defaultLauncherRoleLauncher.launch(intent) }.isSuccess
        if (!launched) {
            handleDefaultLauncherRoleResult()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // As a root home launcher, never delegate to super.onBackPressed() which finishes the activity
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::searchViewModel.isInitialized) {
            searchViewModel.dismissSearch()
        }
        ImeUtils.dismissIme(this)
        if (HardenedModeDeepLink.isValidDisableIntent(intent)) {
            HardenedModeDeepLink.markConsumed(intent)
            disableHardenedModeDeepLinkCounter.intValue++
        } else if (HardenedModeDeepLink.isValidSettingsLockIntent(intent)) {
            HardenedModeDeepLink.markConsumed(intent)
            settingsLockDeepLinkCounter.intValue++
        } else {
            // Pressing Home while already on Dry Toast collapses transient search/friction/settings state and hides IME
            homeIntentResetCounter.intValue++
        }
    }

    override fun onResume() {
        super.onResume()
        appContainer.nfcReaderSource.onActivityResumed(this)
        appContainer.appAccessController.isDeviceOwner()
        if (::settingsViewModel.isInitialized) {
            settingsViewModel.refreshHardenedModeStatus()
        }
        val isDefault = appContainer.launcherRoleController.refresh()
        if (isDefault) {
            if (::settingsViewModel.isInitialized) {
                settingsViewModel.setShowRoleRequiredWarning(false)
            }
            if (appContainer.pendingOnboardingRoleRequest) {
                appContainer.pendingOnboardingRoleRequest = false
                appContainer.hapticEngine.targetSnap()
                if (::settingsViewModel.isInitialized) {
                    settingsViewModel.completeOnboarding()
                } else {
                    lifecycleScope.launch {
                        appContainer.settingsRepository.setHasCompletedOnboarding(true)
                    }
                }
            }
        } else {
            if (appContainer.pendingOnboardingRoleRequest) {
                appContainer.pendingOnboardingRoleRequest = false
                if (::settingsViewModel.isInitialized) {
                    settingsViewModel.setShowRoleRequiredWarning(true)
                }
                appContainer.hapticEngine.resistanceDetent()
            }
            if (::settingsViewModel.isInitialized) {
                settingsViewModel.resetOnboarding()
            } else {
                lifecycleScope.launch {
                    appContainer.settingsRepository.setHasCompletedOnboarding(false)
                }
            }
        }
    }

    override fun onPause() {
        appContainer.nfcReaderSource.onActivityPaused(this)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations &&
            appContainer.launcherRoleController.isDefaultLauncher() &&
            isLaunchedAsStandardAppTask()
        ) {
            finishAndRemoveTask()
        }
    }

    @androidx.annotation.VisibleForTesting
    suspend fun configureScheduleProfilesForTesting(
        baseProfile: BaseProfileSettings = BaseProfileSettings(),
        customProfiles: List<CustomProfile>,
        savedTriggers: List<SavedTrigger> = emptyList(),
    ) {
        val repository = appContainer.settingsRepository
        val keepTriggerIds = savedTriggers.map { it.id }.toSet()
        repository.getSettings().savedTriggers
            .map { it.id }
            .filter { it !in keepTriggerIds }
            .forEach { repository.deleteTrigger(it) }
        repository.updateBaseProfile(baseProfile)
        savedTriggers.forEach { repository.saveTrigger(it) }
        repository.updateCustomProfiles(customProfiles)
        if (::searchViewModel.isInitialized) {
            searchViewModel.reconcileForTesting(repository.getSettings())
        }
    }

    @androidx.annotation.VisibleForTesting
    fun setClockAndTriggerAlarmForTesting(dayOfWeek: DayOfWeek, timeOfDay: TimeOfDay) {
        appContainer.clockOverrideForTesting = ClockSource { LocalDayTime(dayOfWeek, timeOfDay) }
        ScheduleTransitionNotifier.notifyTransition()
        if (::searchViewModel.isInitialized) {
            kotlinx.coroutines.runBlocking {
                searchViewModel.reconcileForTesting()
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    fun setExactAlarmPermissionForTesting(granted: Boolean?) {
        appContainer.exactAlarmOverrideForTesting = granted
        ScheduleTransitionNotifier.notifyTransition()
        if (::searchViewModel.isInitialized) {
            kotlinx.coroutines.runBlocking {
                searchViewModel.reconcileForTesting()
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    fun setNfcCapabilityForTesting(enabled: Boolean?) {
        appContainer.nfcCapabilityOverrideForTesting = enabled
        ScheduleTransitionNotifier.notifyTransition()
        if (::searchViewModel.isInitialized) {
            kotlinx.coroutines.runBlocking {
                searchViewModel.reconcileForTesting()
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    fun setNowEpochMillisForTesting(millis: Long?) {
        appContainer.nowEpochMillisOverrideForTesting = millis
        ScheduleTransitionNotifier.notifyTransition()
        if (::searchViewModel.isInitialized) {
            kotlinx.coroutines.runBlocking {
                searchViewModel.reconcileForTesting()
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    fun setDefaultLauncherForTesting(isDefault: Boolean?) {
        appContainer.defaultLauncherOverrideForTesting = isDefault
    }

    @androidx.annotation.VisibleForTesting
    fun setDeviceOwnerForTesting(isDeviceOwner: Boolean?) {
        appContainer.deviceOwnerOverrideForTesting = isDeviceOwner
    }

    @androidx.annotation.VisibleForTesting
    fun simulateNfcTapForTesting(rawUid: String) {
        val registrationListener = appContainer.nfcReaderSource.activeRegistrationListener
        val registrationResultListener = appContainer.nfcReaderSource.activeRegistrationResultListener
        if (registrationListener != null || registrationResultListener != null) {
            appContainer.nfcReaderSource.dispatchScannedUid(rawUid)
        } else if (::searchViewModel.isInitialized) {
            kotlinx.coroutines.runBlocking {
                searchViewModel.processScannedNfcTag(rawUid)
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

    private fun showSettingsLockedToast() {
        Toast.makeText(this, getString(R.string.toast_settings_locked), Toast.LENGTH_SHORT).show()
    }

    private fun applyPureBlackWallpaper() {
        lifecycleScope.launch {
            val applied = appContainer.wallpaperController.applyPureBlackWallpaper()
            if (applied) {
                appContainer.hapticEngine.targetSnap()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_black_wallpaper_applied),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun launchFeedbackEmail() {
        appContainer.hapticEngine.rotaryDetent()
        val intent = FeedbackEmailIntentFactory.createFeedbackEmailIntent(this)
        val launched = runCatching { startActivity(intent) }.isSuccess
        if (!launched) {
            Toast.makeText(this, getString(R.string.toast_no_email_app), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Call before super.onCreate() with explicit dark SystemBarStyle so light system bar icons
        // render cleanly over Dry Toast's pure black AMOLED canvas on all Android 10+ light/dark modes.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val container = appContainer
        val hapticEngine = container.hapticEngine
        val scheduleAlarmScheduler = container.scheduleAlarmScheduler

        val settingsAction = ActionCandidate(
            id = "settings",
            label = getString(R.string.action_dry_toast_settings),
            shortcutKeyword = ":settings",
        )
        val wallpaperAction = ActionCandidate(
            id = "wallpaper",
            label = getString(R.string.action_set_black_wallpaper_command),
            shortcutKeyword = ":wallpaper",
        )
        val feedbackAction = ActionCandidate(
            id = "feedback",
            label = getString(R.string.action_send_feedback),
            shortcutKeyword = ":feedback",
        )

        searchViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(
                    packageIndexSource = container.packageIndexSource,
                    hapticEngine = container.hapticEngine,
                    dialerPackageResolver = container.dialerPackageResolver,
                    actions = listOf(settingsAction, wallpaperAction, feedbackAction),
                    settingsRepository = container.settingsRepository,
                    clockSource = container.activeClockSource,
                    scheduleAlarmScheduler = container.scheduleAlarmScheduler,
                    capabilityChecker = container.capabilityChecker,
                    locationSource = container.locationSource,
                    nfcReaderSource = container.nfcReaderSource,
                    appAccessController = container.appAccessController,
                    nowEpochMillisProvider = container.activeNowEpochMillisProvider,
                ) as T
            },
        )[SearchViewModel::class.java]

        settingsViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
                    packageIndexSource = container.packageIndexSource,
                    settingsRepository = container.settingsRepository,
                    dialerPackageResolver = container.dialerPackageResolver,
                    appAccessController = container.appAccessController,
                    onConfigurationImported = {
                        container.reconcileDpcFromSettings()
                        ScheduleTransitionNotifier.notifyTransition()
                    },
                ) as T
            },
        )[SettingsViewModel::class.java]

        if (container.launcherRoleController.isDefaultLauncher() && container.pendingOnboardingRoleRequest) {
            container.pendingOnboardingRoleRequest = false
            hapticEngine.targetSnap()
            settingsViewModel.completeOnboarding()
        }

        if (HardenedModeDeepLink.isValidDisableIntent(intent)) {
            HardenedModeDeepLink.markConsumed(intent)
            disableHardenedModeDeepLinkCounter.intValue++
        } else if (HardenedModeDeepLink.isValidSettingsLockIntent(intent)) {
            HardenedModeDeepLink.markConsumed(intent)
            settingsLockDeepLinkCounter.intValue++
        }

        // IMPORTANT MEMORY INVARIANT (< 25 MB Typographic Mode / < 35 MB CI PSS Ceiling):
        // Never call searchViewModel.ensureIndexLoaded() eagerly in onCreate().
        // Resolving LauncherActivityInfo.getLabel() across all installed apps causes Android's
        // ResourcesManager to mmap foreign APK resources.arsc tables (+25 MB .apk mmap PSS).
        // Index loading must remain strictly lazy upon user search activation (activateSearch / onQueryChanged).

        setContent {
            DryToastTheme {
                val initialRoute = if (container.launcherRoleController.isDefaultLauncher()) {
                    DryToastRoute.Home
                } else {
                    DryToastRoute.Onboarding
                }
                val backStack = rememberNavBackStack(initialRoute)
                val resetTrigger by homeIntentResetCounter
                val disableHardenedModeTrigger by disableHardenedModeDeepLinkCounter
                val settingsLockTrigger by settingsLockDeepLinkCounter
                val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                val isDefaultLauncher by container.launcherRoleController.isDefaultLauncherFlow
                    .collectAsStateWithLifecycle()
                val currentLocation by locationSource.currentLocation.collectAsStateWithLifecycle()
                val locationTelemetry by locationSource.telemetry.collectAsStateWithLifecycle()
                val requiresOnboarding =
                    (!settingsUiState.general.hasCompletedOnboarding && !container.pendingOnboardingRoleRequest) ||
                        !isDefaultLauncher

                LaunchedEffect(settingsUiState.isSettingsLoaded, requiresOnboarding) {
                    if (!settingsUiState.isSettingsLoaded) return@LaunchedEffect
                    if (requiresOnboarding) {
                        if (backStack.firstOrNull() != DryToastRoute.Onboarding) {
                            backStack.clear()
                            backStack.add(DryToastRoute.Onboarding)
                        } else {
                            while (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            }
                        }
                    } else if (backStack.firstOrNull() == DryToastRoute.Onboarding) {
                        backStack.clear()
                        backStack.add(DryToastRoute.Home)
                    }
                }

                LaunchedEffect(resetTrigger) {
                    if (resetTrigger > 0) {
                        settingsViewModel.setShowRoleRequiredWarning(false)
                        while (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                    }
                }

                LaunchedEffect(disableHardenedModeTrigger, requiresOnboarding) {
                    if (disableHardenedModeTrigger > 0 && !requiresOnboarding) {
                        settingsViewModel.refreshHardenedModeStatus()
                        while (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                        if (backStack.lastOrNull() != DryToastRoute.DisableHardenedMode) {
                            backStack.add(DryToastRoute.DisableHardenedMode)
                        }
                    }
                }

                LaunchedEffect(settingsLockTrigger, requiresOnboarding) {
                    if (settingsLockTrigger > 0 && !requiresOnboarding) {
                        while (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                        if (backStack.lastOrNull() != DryToastRoute.SettingsLock) {
                            backStack.add(DryToastRoute.SettingsLock)
                        }
                    }
                }

                val topRoute = backStack.lastOrNull()
                LaunchedEffect(topRoute) {
                    if (topRoute == DryToastRoute.Home) {
                        settingsViewModel.releaseLoadedApps()
                    }
                }

                LaunchedEffect(settingsUiState.isSettingsLocked, topRoute) {
                    if (settingsUiState.isSettingsLocked &&
                        backStack.size > 1 &&
                        topRoute != DryToastRoute.DisableHardenedMode &&
                        topRoute != DryToastRoute.SettingsLock
                    ) {
                        while (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                        showSettingsLockedToast()
                    }
                }

                val isExpandedSettings =
                    LocalConfiguration.current.screenWidthDp.dp >= SETTINGS_EXPANDED_BREAKPOINT
                val settingsTwoPaneStrategy = rememberSettingsTwoPaneSceneStrategy<NavKey>(
                    isExpanded = isExpandedSettings,
                ) {
                    ConfigureAppsScreen(
                        availableApps = settingsUiState.availableApps,
                        initialAssignments = settingsUiState.baseProfile.appAssignments,
                        defaultFriction = settingsUiState.baseProfile.defaultFriction,
                        onSave = settingsViewModel::saveAppAssignments,
                        onBack = {
                            if (backStack.size > 1) backStack.removeLastOrNull()
                        },
                        hapticEngine = hapticEngine,
                    )
                }
                val sceneStrategies = remember(settingsTwoPaneStrategy) {
                    listOf(settingsTwoPaneStrategy, SinglePaneSceneStrategy())
                }

                @OptIn(ExperimentalComposeUiApi::class)
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                    sceneStrategies = sceneStrategies,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                    },
                    transitionSpec = {
                        fadeIn(
                            animationSpec = DryToastMotion.opticalTween(DryToastMotion.SHUTTER_OPEN_DURATION_MS),
                        ) togetherWith fadeOut(
                            animationSpec = DryToastMotion.opticalTween(DryToastMotion.SHUTTER_CLOSE_DURATION_MS),
                        )
                    },
                    popTransitionSpec = {
                        fadeIn(
                            animationSpec = DryToastMotion.opticalTween(DryToastMotion.SHUTTER_OPEN_DURATION_MS),
                        ) togetherWith fadeOut(
                            animationSpec = DryToastMotion.opticalTween(DryToastMotion.SHUTTER_CLOSE_DURATION_MS),
                        )
                    },
                    predictivePopTransitionSpec = {
                        fadeIn(
                            animationSpec = DryToastMotion.opticalTween(DryToastMotion.SHUTTER_OPEN_DURATION_MS),
                        ) togetherWith fadeOut(
                            animationSpec = DryToastMotion.opticalTween(DryToastMotion.SHUTTER_CLOSE_DURATION_MS),
                        )
                    },
                    entryProvider = entryProvider {
                        entry<DryToastRoute.Onboarding> {
                            OnboardingScreen(
                                isDefaultLauncher = isDefaultLauncher,
                                onRequestDefaultLauncher = ::requestDefaultLauncherRole,
                                onCompleteOnboarding = settingsViewModel::completeOnboarding,
                                showRoleRequiredWarning = settingsUiState.showRoleRequiredWarning,
                                hapticEngine = hapticEngine,
                            )
                        }

                        entry<DryToastRoute.Home> {
                            DryToastHomeScreen(
                                viewModel = searchViewModel,
                                appLauncher = appLauncher,
                                hapticEngine = hapticEngine,
                                onDismissIme = { ImeUtils.dismissIme(this@MainActivity) },
                                antiMetricsRepository = antiMetricsRepository,
                                onOpenSettings = {
                                    if (settingsUiState.isSettingsLocked) {
                                        showSettingsLockedToast()
                                    } else if (backStack.lastOrNull() != DryToastRoute.Settings) {
                                        backStack.add(DryToastRoute.Settings)
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
                                            val nfcIntent = Intent(Settings.ACTION_NFC_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            val opened = runCatching { startActivity(nfcIntent) }.isSuccess
                                            if (!opened) {
                                                if (settingsUiState.isSettingsLocked) {
                                                    showSettingsLockedToast()
                                                } else if (backStack.lastOrNull() != DryToastRoute.Profiles) {
                                                    backStack.add(DryToastRoute.Profiles)
                                                }
                                            }
                                        }
                                    }
                                },
                                onApplyPureBlackWallpaper = ::applyPureBlackWallpaper,
                                onSendFeedback = ::launchFeedbackEmail,
                            )
                        }

                        entry<DryToastRoute.Settings>(
                            metadata = SettingsTwoPaneSceneStrategy.masterMetadata(),
                        ) {
                            LaunchedEffect(Unit) {
                                settingsViewModel.ensureLoaded()
                            }
                            val coroutineScope = rememberCoroutineScope()
                            var backupStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }

                            val exportDocumentLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.CreateDocument("application/json"),
                            ) { uri ->
                                if (uri == null) {
                                    settingsViewModel.cancelPendingExportRequest()
                                    return@rememberLauncherForActivityResult
                                }
                                coroutineScope.launch {
                                    val encoded = runCatching {
                                        settingsViewModel.consumePendingExportJson()
                                    }.getOrNull()
                                    if (encoded == null) {
                                        backupStatusMessage = getString(R.string.backup_export_failed)
                                        return@launch
                                    }
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            contentResolver.openOutputStream(uri)?.use { stream ->
                                                stream.write(encoded.toByteArray(Charsets.UTF_8))
                                                stream.flush()
                                            } ?: error("Unable to open destination file")
                                        }
                                    }
                                    backupStatusMessage = if (result.isSuccess) {
                                        getString(R.string.backup_export_success)
                                    } else {
                                        getString(R.string.backup_export_failed)
                                    }
                                }
                            }

                            val importDocumentLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.OpenDocument(),
                            ) { uri ->
                                if (uri == null) return@rememberLauncherForActivityResult
                                coroutineScope.launch {
                                    val rawJson = withContext(Dispatchers.IO) {
                                        runCatching { readBoundedUtf8Text(uri) }.getOrNull()
                                    }
                                    if (rawJson == null) {
                                        backupStatusMessage = getString(R.string.backup_import_read_failed)
                                        return@launch
                                    }
                                    when (val outcome = settingsViewModel.handlePickedImportJson(rawJson)) {
                                        is ConfigImportOutcome.Success -> {
                                            backupStatusMessage = getString(
                                                if (outcome.stagedForReboot) {
                                                    R.string.backup_import_staged
                                                } else {
                                                    R.string.backup_import_success
                                                },
                                            )
                                        }
                                        is ConfigImportOutcome.PassphraseRequired -> Unit
                                        is ConfigImportOutcome.SettingsLocked -> {
                                            backupStatusMessage = getString(R.string.backup_import_locked)
                                        }
                                        is ConfigImportOutcome.Error -> {
                                            backupStatusMessage = formatImportError(outcome.reason, outcome.detail)
                                        }
                                    }
                                }
                            }

                            DryToastSettingsScreen(
                                uiState = settingsUiState,
                                onBack = {
                                    exitSettingsFromMaster(backStack)
                                },
                                onNavigateToConfigureApps = {
                                    navigateFromSettingsMaster(
                                        backStack = backStack,
                                        target = DryToastRoute.ConfigureApps(profileId = null),
                                        isExpanded = isExpandedSettings,
                                    )
                                },
                                onNavigateToProfiles = {
                                    navigateFromSettingsMaster(
                                        backStack = backStack,
                                        target = DryToastRoute.Profiles,
                                        isExpanded = isExpandedSettings,
                                    )
                                },
                                onNavigateToHardenedMode = {
                                    navigateFromSettingsMaster(
                                        backStack = backStack,
                                        target = DryToastRoute.HardenedMode,
                                        isExpanded = isExpandedSettings,
                                    )
                                },
                                onNavigateToBreakGlass = {
                                    navigateFromSettingsMaster(
                                        backStack = backStack,
                                        target = DryToastRoute.BreakGlass,
                                        isExpanded = isExpandedSettings,
                                    )
                                },
                                onUpdateShowUnlocks = settingsViewModel::updateShowUnlocks,
                                onUpdateShowRest = settingsViewModel::updateShowRest,
                                onUpdateShowDialerIcon = settingsViewModel::updateShowDialerIcon,
                                onUpdateRequireFullTyping = settingsViewModel::updateRequireFullTyping,
                                onUpdateDefaultFriction = settingsViewModel::updateDefaultFriction,
                                onAddQuickAction = settingsViewModel::addQuickAction,
                                onRemoveQuickAction = settingsViewModel::removeQuickAction,
                                onUpdateRequireReboot = settingsViewModel::updateRequireRebootForSettingsChange,
                                onUpdateGuardedSessionGraceMinutes =
                                settingsViewModel::updateGuardedSessionGraceMinutes,
                                onDiscardPendingChanges = settingsViewModel::discardPendingChanges,
                                onApplyPureBlackWallpaper = ::applyPureBlackWallpaper,
                                onSendFeedback = ::launchFeedbackEmail,
                                onRequestExport = { passphrase ->
                                    settingsViewModel.stageExportRequest(passphrase)
                                    exportDocumentLauncher.launch(getString(R.string.backup_default_filename))
                                },
                                onRequestImport = {
                                    importDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                showImportPassphraseSheet = settingsUiState.showImportPassphraseSheet,
                                importPassphraseError = if (settingsUiState.hasImportPassphraseError) {
                                    getString(R.string.backup_import_wrong_passphrase)
                                } else {
                                    null
                                },
                                isImportWorking = settingsUiState.isImportWorking,
                                onSubmitImportPassphrase = { passphrase ->
                                    coroutineScope.launch {
                                        val outcome = settingsViewModel.submitPendingImportPassphrase(passphrase)
                                            ?: return@launch
                                        when (outcome) {
                                            is ConfigImportOutcome.Success -> {
                                                backupStatusMessage = getString(
                                                    if (outcome.stagedForReboot) {
                                                        R.string.backup_import_staged
                                                    } else {
                                                        R.string.backup_import_success
                                                    },
                                                )
                                            }
                                            is ConfigImportOutcome.PassphraseRequired -> Unit
                                            is ConfigImportOutcome.SettingsLocked -> {
                                                backupStatusMessage = getString(R.string.backup_import_locked)
                                            }
                                            is ConfigImportOutcome.Error -> {
                                                if (outcome.reason !=
                                                    ConfigImportError.INVALID_PASSPHRASE_OR_TAMPERED
                                                ) {
                                                    backupStatusMessage =
                                                        formatImportError(outcome.reason, outcome.detail)
                                                }
                                            }
                                        }
                                    }
                                },
                                onDismissImportPassphraseSheet = settingsViewModel::dismissImportPassphraseSheet,
                                backupStatusMessage = backupStatusMessage,
                                onClearBackupStatusMessage = { backupStatusMessage = null },
                            )
                        }

                        entry<DryToastRoute.HardenedMode>(
                            metadata = SettingsTwoPaneSceneStrategy.detailMetadata(
                                section = SettingsDetailSection.HARDENED_MODE,
                            ),
                        ) {
                            LaunchedEffect(Unit) {
                                settingsViewModel.refreshHardenedModeStatus()
                            }
                            HardenedModeScreen(
                                isHardenedModeActive = settingsUiState.isHardenedModeActive,
                                blockNewAppInstalls = settingsUiState.general.blockNewAppInstalls,
                                hardenedOwnerName = settingsUiState.general.effectiveHardenedOwnerName(),
                                hardenedLockScreenInfo = settingsUiState.general.hardenedLockScreenInfo,
                                isSettingsLocked = settingsUiState.isSettingsLocked,
                                onToggleBlockNewAppInstalls = settingsViewModel::updateBlockNewAppInstalls,
                                onSaveLockScreenText = settingsViewModel::updateHardenedLockScreenText,
                                onBack = {
                                    if (backStack.size > 1) backStack.removeLastOrNull()
                                },
                            )
                        }

                        entry<DryToastRoute.BreakGlass>(
                            metadata = SettingsTwoPaneSceneStrategy.detailMetadata(
                                section = SettingsDetailSection.BREAK_GLASS,
                            ),
                        ) {
                            BreakGlassScreen(
                                breakGlassTagUid = settingsUiState.general.normalizedBreakGlassTagUid(),
                                breakGlassDurationMinutes = settingsUiState.general.breakGlassDurationMinutes,
                                breakGlassUntilEpochMillis = settingsUiState.breakGlassUntilEpochMillis,
                                savedTriggers = settingsUiState.savedTriggers,
                                isSettingsLocked = settingsUiState.isSettingsLocked,
                                onSaveBreakGlassTagUid = settingsViewModel::updateBreakGlassTagUid,
                                onUpdateDurationMinutes = settingsViewModel::updateBreakGlassDurationMinutes,
                                onEndBreakGlassEarly = settingsViewModel::endBreakGlassEarly,
                                onSetNfcRegistrationListener = { listener ->
                                    container.nfcReaderSource.setRegistrationResultListener(this@MainActivity, listener)
                                },
                                nowEpochMillisProvider = container.activeNowEpochMillisProvider,
                                onBack = {
                                    if (backStack.size > 1) backStack.removeLastOrNull()
                                },
                            )
                        }

                        entry<DryToastRoute.DisableHardenedMode> {
                            LaunchedEffect(Unit) {
                                settingsViewModel.refreshHardenedModeStatus()
                            }
                            DisableHardenedModeScreen(
                                isHardenedModeActive = settingsUiState.isHardenedModeActive,
                                nowEpochMillisProvider = container.activeNowEpochMillisProvider,
                                onConfirmDisable = {
                                    lifecycleScope.launch {
                                        container.appAccessController.deactivateHardenedMode()
                                        settingsViewModel.refreshHardenedModeStatus()
                                        ScheduleTransitionNotifier.notifyTransition()
                                        hapticEngine.targetSnap()
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(R.string.toast_hardened_mode_disabled),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        if (backStack.size > 1) {
                                            backStack.removeLastOrNull()
                                        }
                                    }
                                },
                                onCancel = {
                                    if (backStack.size > 1) backStack.removeLastOrNull()
                                },
                            )
                        }

                        entry<DryToastRoute.SettingsLock> {
                            SettingsLockScreen(
                                isSettingsLocked = settingsUiState.isSettingsLocked,
                                nowEpochMillisProvider = container.activeNowEpochMillisProvider,
                                onConfirmSetSettingsLocked = { locked ->
                                    lifecycleScope.launch {
                                        container.settingsRepository.setSettingsLocked(locked)
                                        hapticEngine.targetSnap()
                                        val toastRes = if (locked) {
                                            R.string.toast_settings_lock_enabled
                                        } else {
                                            R.string.toast_settings_lock_disabled
                                        }
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(toastRes),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        if (backStack.size > 1) {
                                            backStack.removeLastOrNull()
                                        }
                                    }
                                },
                                onCancel = {
                                    if (backStack.size > 1) backStack.removeLastOrNull()
                                },
                            )
                        }

                        entry<DryToastRoute.Profiles>(
                            metadata = SettingsTwoPaneSceneStrategy.detailMetadata(
                                section = SettingsDetailSection.PROFILES,
                            ),
                        ) {
                            ProfilesScreen(
                                profiles = settingsUiState.customProfiles,
                                onSaveProfile = settingsViewModel::saveCustomProfile,
                                onDeleteProfile = settingsViewModel::deleteCustomProfile,
                                onReorderProfiles = settingsViewModel::reorderCustomProfiles,
                                onConfigureProfileApps = { profileId ->
                                    backStack.add(DryToastRoute.ConfigureApps(profileId = profileId))
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
                                savedTriggers = settingsUiState.savedTriggers,
                                onSaveTrigger = settingsViewModel::saveTrigger,
                                onDeleteTrigger = settingsViewModel::deleteTrigger,
                                onRegisterNfcScanListener = { listener ->
                                    container.nfcReaderSource.setRegistrationListener(this@MainActivity, listener)
                                },
                                onRegisterNfcRegistrationListener = { listener ->
                                    container.nfcReaderSource.setRegistrationResultListener(this@MainActivity, listener)
                                },
                            )
                        }

                        entry<DryToastRoute.ConfigureApps>(
                            metadata = { route ->
                                SettingsTwoPaneSceneStrategy.detailMetadata(
                                    section = SettingsDetailSection.CONFIGURE_APPS,
                                    isSubDetail = route.profileId != null,
                                )
                            },
                        ) { route ->
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
                                profileDefaultRule = targetProfile?.defaultRule
                                    ?: com.garan.drytoast.core.model.ProfileDefaultRule.FALLTHROUGH,
                                onUpdateProfileDefaultRule = { rule ->
                                    targetProfile?.let { profile ->
                                        settingsViewModel.updateCustomProfileDefaultRule(profile.id, rule)
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun readBoundedUtf8Text(uri: Uri): String? {
        val stream = contentResolver.openInputStream(uri) ?: return null
        val maxReadChars = ConfigBackupCodec.MAX_INPUT_CHARS + 1
        val sb = StringBuilder()
        val buffer = CharArray(8192)
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            while (sb.length < maxReadChars) {
                val read = reader.read(buffer, 0, minOf(buffer.size, maxReadChars - sb.length))
                if (read < 0) break
                sb.append(buffer, 0, read)
            }
        }
        return sb.toString()
    }

    private fun formatImportError(reason: ConfigImportError, detail: String): String = when (reason) {
        ConfigImportError.INPUT_TOO_LARGE -> getString(R.string.backup_import_too_large)
        ConfigImportError.UNSUPPORTED_VERSION -> getString(R.string.backup_import_unsupported_version)
        ConfigImportError.INVALID_PASSPHRASE_OR_TAMPERED -> getString(R.string.backup_import_wrong_passphrase)
        ConfigImportError.INVALID_CONFIG_VALUES -> getString(R.string.backup_import_invalid_values_format, detail)
        ConfigImportError.EMPTY_INPUT,
        ConfigImportError.INVALID_JSON,
        ConfigImportError.UNSUPPORTED_FORMAT,
        ConfigImportError.MISSING_CONFIG_PAYLOAD,
        ConfigImportError.MISSING_CRYPTO_PAYLOAD,
        ConfigImportError.UNSUPPORTED_CRYPTO_PARAMETERS,
        ConfigImportError.CORRUPT_CRYPTO_DATA,
        -> getString(R.string.backup_import_invalid_format)
    }
}
