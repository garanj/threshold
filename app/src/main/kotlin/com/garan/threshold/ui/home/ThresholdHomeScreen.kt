package com.garan.threshold.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.ui.launcher.AppLauncher
import com.garan.threshold.ui.search.SearchViewModel
import com.garan.threshold.ui.search.ThresholdSearchSurface

/**
 * Top-level home screen for Threshold.
 * Houses the ambient typographic canvas, search surface, and back navigation handling.
 *
 * Back Navigation Policy:
 * 1. When in search mode (active search or query non-empty), BackHandler is dynamically enabled.
 *    Swiping/pressing back dismisses search mode, resets the query, and hides the soft keyboard.
 * 2. When in ambient home mode, BackHandler is disabled (enabled = false). Combined with
 *    android:enableOnBackInvokedCallback="true", SystemUI suppresses the back gesture indicator
 *    entirely on the root home canvas without flickering.
 */
@Composable
fun ThresholdHomeScreen(
    viewModel: SearchViewModel,
    appLauncher: AppLauncher,
    modifier: Modifier = Modifier,
    onDismissIme: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val inSearch = isSearchActive || uiState.query.isNotEmpty()

    BackHandler(enabled = inSearch) {
        isSearchActive = false
        viewModel.onQueryChanged("")
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismissIme()
    }

    ThresholdHomeCanvas(modifier = modifier) {
        Spacer(modifier = Modifier.height(24.dp))

        ThresholdSearchSurface(
            query = uiState.query,
            onQueryChange = viewModel::onQueryChanged,
            results = uiState.results,
            onItemClick = { item ->
                if (item is SearchItem.App) {
                    val launched = appLauncher.launchApp(item.component)
                    if (launched) {
                        isSearchActive = false
                        viewModel.onQueryChanged("")
                    }
                }
            },
            isSearchActive = inSearch,
            onActivateSearch = {
                isSearchActive = true
                viewModel.ensureIndexLoaded()
            },
        )
    }
}
