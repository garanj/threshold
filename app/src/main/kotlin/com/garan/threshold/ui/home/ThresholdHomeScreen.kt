package com.garan.threshold.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garan.threshold.core.port.LaunchResult
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.ui.launcher.AppLauncher
import com.garan.threshold.ui.search.SearchViewModel
import com.garan.threshold.ui.search.ThresholdSearchSurface

/**
 * Top-level home screen for Threshold.
 * Houses the ambient typographic canvas, search surface, and back navigation handling.
 *
 * Back Navigation Policy:
 * 1. When in search mode (active search or query non-empty), swiping/pressing back dismisses
 *    search mode, resets the query, and hides the soft keyboard.
 * 2. When in ambient home mode, back navigation is consumed cleanly as a no-op so the OS
 *    never finishes or restarts the root launcher activity window.
 */
@Composable
fun ThresholdHomeScreen(
    viewModel: SearchViewModel,
    appLauncher: AppLauncher,
    modifier: Modifier = Modifier,
    onDismissIme: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val inSearch = uiState.isSearching || uiState.query.isNotEmpty()

    BackHandler(enabled = true) {
        if (inSearch) {
            viewModel.dismissSearch()
            keyboardController?.hide()
            focusManager.clearFocus()
            onDismissIme()
        } else {
            // Ambient home canvas: consume back navigation cleanly as a no-op so the OS
            // never invokes finishAfterTransition() or restarts the launcher activity.
        }
    }

    ThresholdHomeCanvas(modifier = modifier) {
        Spacer(modifier = Modifier.height(24.dp))

        ThresholdSearchSurface(
            query = uiState.query,
            onQueryChange = viewModel::onQueryChanged,
            results = uiState.results,
            onItemClick = { item ->
                if (item is SearchItem.App) {
                    when (viewModel.evaluateLaunch(item)) {
                        is LaunchResult.Launched -> {
                            val launched = appLauncher.launchApp(item.component)
                            if (launched) {
                                viewModel.dismissSearch()
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onDismissIme()
                            }
                        }
                        is LaunchResult.FrictionRequired,
                        is LaunchResult.Blocked,
                        -> {
                            // Guarded apps require passing a friction checkpoint (Phase 2 Task 2.3)
                            // and Blocked apps are never launched.
                        }
                    }
                }
            },
            onItemLongClick = { item ->
                if (item is SearchItem.App) {
                    val opened = appLauncher.openAppInfo(item.component)
                    if (opened) {
                        viewModel.dismissSearch()
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onDismissIme()
                    }
                }
            },
            isSearchActive = inSearch,
            onActivateSearch = viewModel::activateSearch,
        )
    }
}
