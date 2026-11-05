package com.garan.threshold

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.garan.threshold.data.antimetrics.AndroidAntiMetricsRepository
import com.garan.threshold.data.haptics.AndroidHapticEngine
import com.garan.threshold.data.packageindex.AndroidPackageIndexSource
import com.garan.threshold.ui.home.ThresholdHomeScreen
import com.garan.threshold.ui.launcher.AndroidAppLauncher
import com.garan.threshold.ui.launcher.DialerPackageResolver
import com.garan.threshold.ui.search.SearchViewModel
import com.garan.threshold.ui.theme.ThresholdTheme
import com.garan.threshold.ui.util.ImeUtils

class MainActivity : ComponentActivity() {

    private val packageIndexSource by lazy { AndroidPackageIndexSource(this) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }
    private val hapticEngine by lazy { AndroidHapticEngine(this) }
    private val antiMetricsRepository by lazy { AndroidAntiMetricsRepository(this) }
    internal lateinit var searchViewModel: SearchViewModel
        private set

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // As a root home launcher, never delegate to super.onBackPressed() which finishes the activity
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Pressing Home while already on Threshold collapses transient search state and hides IME
        if (::searchViewModel.isInitialized) {
            searchViewModel.dismissSearch()
        }
        ImeUtils.dismissIme(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        antiMetricsRepository.unregister()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Temporary test rules for Phase 2 Task 2.3 verification
        // Chrome -> TimedDelay (Caret Tracing 10s), YouTube -> DragSort (6 pairs)
        // TODO(Phase-3): Remove temporary hardcoded rules when Room profile persistence is introduced
        val youtubeRule = com.garan.threshold.core.model.ComponentKey("com.google.android.youtube", "*")
        val chromeRule = com.garan.threshold.core.model.ComponentKey("com.android.chrome", "*")
        val defaultProfile = com.garan.threshold.core.model.Profile(
            id = "ambient",
            name = "Ambient",
            defaultAccess = com.garan.threshold.core.model.AppAccessState.Direct,
            appRules = mapOf(
                chromeRule to com.garan.threshold.core.model.AppAccessState.Guarded(
                    com.garan.threshold.core.model.FrictionPolicy.TimedDelay(10),
                ),
                youtubeRule to com.garan.threshold.core.model.AppAccessState.Guarded(
                    com.garan.threshold.core.model.FrictionPolicy.DragSort(6),
                ),
            ),
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
                    initialProfile = defaultProfile,
                ) as T
            },
        )[SearchViewModel::class.java]

        setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = searchViewModel,
                    appLauncher = appLauncher,
                    hapticEngine = hapticEngine,
                    onDismissIme = { ImeUtils.dismissIme(this@MainActivity) },
                    antiMetricsRepository = antiMetricsRepository,
                )
            }
        }
    }
}
