package com.garan.threshold

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.garan.threshold.core.search.ActionCandidate
import com.garan.threshold.data.antimetrics.AndroidAntiMetricsRepository
import com.garan.threshold.data.haptics.AndroidHapticEngine
import com.garan.threshold.data.packageindex.AndroidPackageIndexSource
import com.garan.threshold.data.settings.AndroidSettingsRepository
import com.garan.threshold.ui.home.ThresholdHomeScreen
import com.garan.threshold.ui.launcher.AndroidAppLauncher
import com.garan.threshold.ui.launcher.DialerPackageResolver
import com.garan.threshold.ui.search.SearchViewModel
import com.garan.threshold.ui.settings.ConfigureAppsScreen
import com.garan.threshold.ui.settings.SettingsViewModel
import com.garan.threshold.ui.settings.ThresholdSettingsScreen
import com.garan.threshold.ui.theme.ThresholdTheme
import com.garan.threshold.ui.util.ImeUtils

enum class ThresholdScreen {
    HOME,
    SETTINGS,
    CONFIGURE_APPS,
}

class MainActivity : ComponentActivity() {

    private val packageIndexSource by lazy { AndroidPackageIndexSource(applicationContext ?: this) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }
    private val hapticEngine by lazy { AndroidHapticEngine(applicationContext ?: this) }
    private val settingsRepository by lazy { AndroidSettingsRepository(applicationContext ?: this) }
    internal val antiMetricsRepository by lazy { AndroidAntiMetricsRepository(applicationContext ?: this) }
    internal lateinit var searchViewModel: SearchViewModel
        private set
    internal lateinit var settingsViewModel: SettingsViewModel
        private set

    private var activeScreenState = mutableStateOf(ThresholdScreen.HOME)

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
        activeScreenState.value = ThresholdScreen.HOME
        ImeUtils.dismissIme(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsAction = ActionCandidate(
            id = "settings",
            label = "Settings",
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
                var currentScreen by activeScreenState
                val settingsUiState by settingsViewModel.uiState.collectAsState()

                when (currentScreen) {
                    ThresholdScreen.HOME -> {
                        ThresholdHomeScreen(
                            viewModel = searchViewModel,
                            appLauncher = appLauncher,
                            hapticEngine = hapticEngine,
                            onDismissIme = { ImeUtils.dismissIme(this@MainActivity) },
                            antiMetricsRepository = antiMetricsRepository,
                            onOpenSettings = { currentScreen = ThresholdScreen.SETTINGS },
                        )
                    }
                    ThresholdScreen.SETTINGS -> {
                        ThresholdSettingsScreen(
                            uiState = settingsUiState,
                            onBack = { currentScreen = ThresholdScreen.HOME },
                            onNavigateToConfigureApps = { currentScreen = ThresholdScreen.CONFIGURE_APPS },
                            onUpdateShowUnlocks = settingsViewModel::updateShowUnlocks,
                            onUpdateShowRest = settingsViewModel::updateShowRest,
                            onUpdateRequireFullTyping = settingsViewModel::updateRequireFullTyping,
                            onUpdateDefaultFriction = settingsViewModel::updateDefaultFriction,
                            onAddQuickAction = settingsViewModel::addQuickAction,
                            onRemoveQuickAction = settingsViewModel::removeQuickAction,
                        )
                    }
                    ThresholdScreen.CONFIGURE_APPS -> {
                        ConfigureAppsScreen(
                            availableApps = settingsUiState.availableApps,
                            initialAssignments = settingsUiState.baseProfile.appAssignments,
                            defaultFriction = settingsUiState.baseProfile.defaultFriction,
                            onSave = settingsViewModel::saveAppAssignments,
                            onBack = { currentScreen = ThresholdScreen.SETTINGS },
                        )
                    }
                }
            }
        }
    }
}
