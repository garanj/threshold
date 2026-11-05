package com.garan.threshold.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garan.threshold.R
import com.garan.threshold.core.model.AntiMetricsFormatter
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.port.AntiMetricsRepository
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.LaunchResult
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.ui.friction.CaretTracingSurface
import com.garan.threshold.ui.friction.DragSortSurface
import com.garan.threshold.ui.friction.TypedIntentionSurface
import com.garan.threshold.ui.launcher.AppLauncher
import com.garan.threshold.ui.search.SearchViewModel
import com.garan.threshold.ui.search.ThresholdSearchSurface

/**
 * Top-level home screen for Threshold.
 * Houses the ambient typographic canvas, search surface, and back navigation handling.
 *
 * Back Navigation Policy:
 * 1. When a friction checkpoint is active, swiping/pressing back dismisses the checkpoint.
 * 2. When in search mode (active search or query non-empty), swiping/pressing back dismisses
 *    search mode, resets the query, and hides the soft keyboard.
 * 3. When in ambient home mode, back navigation is consumed cleanly as a no-op so the OS
 *    never finishes or restarts the root launcher activity window.
 */
@Composable
fun ThresholdHomeScreen(
    viewModel: SearchViewModel,
    appLauncher: AppLauncher,
    modifier: Modifier = Modifier,
    hapticEngine: HapticEngine = NoOpHapticEngine,
    onDismissIme: () -> Unit = {},
    searchListState: LazyListState = rememberLazyListState(),
    antiMetricsRepository: AntiMetricsRepository? = null,
    statusText: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val metrics by antiMetricsRepository?.metrics?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val effectiveStatusText = statusText
        ?: metrics?.let { AntiMetricsFormatter.formatStatus(it) }
        ?: stringResource(R.string.app_name)

    val activeFriction = uiState.activeFriction
    val inSearch = uiState.isSearching || uiState.query.isNotEmpty()

    fun hideImeAndClearFocus() {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismissIme()
    }

    fun resetSearchAndHideIme() {
        viewModel.dismissSearch()
        hideImeAndClearFocus()
    }

    // Clear any open or completed friction gate when the screen locks or the user leaves the launcher,
    // preventing pre-warmed friction checkpoints from lingering across sessions.
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.dismissFriction()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = true) {
        if (activeFriction != null) {
            viewModel.dismissFriction()
        } else if (inSearch) {
            resetSearchAndHideIme()
        } else {
            // Ambient home canvas: consume back navigation cleanly as a no-op so the OS
            // never invokes finishAfterTransition() or restarts the launcher activity.
        }
    }

    if (activeFriction != null) {
        when (val policy = activeFriction.policy) {
            is FrictionPolicy.DragSort -> {
                DragSortSurface(
                    pairCount = policy.pairCount,
                    onConfirmOpen = {
                        val launched = appLauncher.launchApp(activeFriction.app.component)
                        if (launched) {
                            resetSearchAndHideIme()
                        }
                    },
                    onDismiss = {
                        viewModel.dismissFriction()
                    },
                    hapticEngine = hapticEngine,
                )
            }
            is FrictionPolicy.TimedDelay -> {
                CaretTracingSurface(
                    totalDurationMs = policy.seconds * 1000L,
                    onConfirmOpen = {
                        val launched = appLauncher.launchApp(activeFriction.app.component)
                        if (launched) {
                            resetSearchAndHideIme()
                        }
                    },
                    onDismiss = {
                        viewModel.dismissFriction()
                    },
                    hapticEngine = hapticEngine,
                )
            }
            is FrictionPolicy.TypedIntention, is FrictionPolicy.SessionBudget -> {
                TypedIntentionSurface(
                    appLabel = activeFriction.app.label,
                    onConfirmOpen = {
                        val launched = appLauncher.launchApp(activeFriction.app.component)
                        if (launched) {
                            resetSearchAndHideIme()
                        }
                    },
                    onDismiss = {
                        viewModel.dismissFriction()
                    },
                    hapticEngine = hapticEngine,
                )
            }
        }
    } else {
        ThresholdHomeCanvas(
            modifier = modifier,
            statusText = effectiveStatusText,
            onTimeTick = { antiMetricsRepository?.refreshForCurrentDate() },
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            ThresholdSearchSurface(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChanged,
                results = uiState.results,
                listState = searchListState,
                onItemClick = { item ->
                    if (item is SearchItem.App) {
                        when (val result = viewModel.evaluateLaunch(item)) {
                            is LaunchResult.Launched -> {
                                val launched = appLauncher.launchApp(item.component)
                                if (launched) {
                                    resetSearchAndHideIme()
                                }
                            }
                            is LaunchResult.FrictionRequired -> {
                                hapticEngine.resistanceDetent()
                                val requiresKeyboard = result.policy is FrictionPolicy.TypedIntention ||
                                    result.policy is FrictionPolicy.SessionBudget
                                if (!requiresKeyboard) {
                                    hideImeAndClearFocus()
                                }
                                viewModel.openFriction(item, result.policy)
                            }
                            is LaunchResult.Blocked -> {
                                hapticEngine.resistanceDetent()
                            }
                        }
                    }
                },
                onItemLongClick = { item ->
                    if (item is SearchItem.App) {
                        hapticEngine.resistanceDetent()
                        val opened = appLauncher.openAppInfo(item.component)
                        if (opened) {
                            resetSearchAndHideIme()
                        }
                    }
                },
                isSearchActive = inSearch,
                onActivateSearch = viewModel::activateSearch,
                hapticEngine = hapticEngine,
            )
        }
    }
}
