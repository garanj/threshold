package com.garan.threshold

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                )
            }
        }
    }
}
