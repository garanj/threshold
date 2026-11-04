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
import com.garan.threshold.core.model.FrictionPolicy
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

private data class ActiveFriction(val app: SearchItem.App, val policy: FrictionPolicy)

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
    hapticEngine: HapticEngine = NoOpHapticEngine,
    onDismissIme: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var activeFriction by remember { mutableStateOf<ActiveFriction?>(null) }
    val inSearch = uiState.isSearching || uiState.query.isNotEmpty()

    BackHandler(enabled = true) {
        if (activeFriction != null) {
            activeFriction = null
        } else if (inSearch) {
            viewModel.dismissSearch()
            keyboardController?.hide()
            focusManager.clearFocus()
            onDismissIme()
        } else {
            // Ambient home canvas: consume back navigation cleanly as a no-op so the OS
            // never invokes finishAfterTransition() or restarts the launcher activity.
        }
    }

    val friction = activeFriction
    if (friction != null) {
        when (friction.policy) {
            is FrictionPolicy.DragSort -> {
                DragSortSurface(
                    pairCount = friction.policy.pairCount,
                    onConfirmOpen = {
                        val launched = appLauncher.launchApp(friction.app.component)
                        if (launched) {
                            activeFriction = null
                            viewModel.dismissSearch()
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onDismissIme()
                        }
                    },
                    onDismiss = {
                        activeFriction = null
                    },
                    hapticEngine = hapticEngine,
                )
            }
            is FrictionPolicy.TimedDelay -> {
                CaretTracingSurface(
                    totalDurationMs = friction.policy.seconds * 1000L,
                    onConfirmOpen = {
                        val launched = appLauncher.launchApp(friction.app.component)
                        if (launched) {
                            activeFriction = null
                            viewModel.dismissSearch()
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onDismissIme()
                        }
                    },
                    onDismiss = {
                        activeFriction = null
                    },
                    hapticEngine = hapticEngine,
                )
            }
            is FrictionPolicy.TypedIntention, is FrictionPolicy.SessionBudget -> {
                TypedIntentionSurface(
                    appLabel = friction.app.label,
                    onConfirmOpen = {
                        val launched = appLauncher.launchApp(friction.app.component)
                        if (launched) {
                            activeFriction = null
                            viewModel.dismissSearch()
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onDismissIme()
                        }
                    },
                    onDismiss = {
                        activeFriction = null
                    },
                    hapticEngine = hapticEngine,
                )
            }
        }
    } else {
        ThresholdHomeCanvas(modifier = modifier) {
            Spacer(modifier = Modifier.height(24.dp))

            ThresholdSearchSurface(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChanged,
                results = uiState.results,
                onItemClick = { item ->
                    if (item is SearchItem.App) {
                        when (val result = viewModel.evaluateLaunch(item)) {
                            is LaunchResult.Launched -> {
                                val launched = appLauncher.launchApp(item.component)
                                if (launched) {
                                    viewModel.dismissSearch()
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onDismissIme()
                                }
                            }
                            is LaunchResult.FrictionRequired -> {
                                hapticEngine.resistanceDetent()
                                if (result.policy !is FrictionPolicy.TypedIntention) {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onDismissIme()
                                }
                                activeFriction = ActiveFriction(item, result.policy)
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
                            viewModel.dismissSearch()
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onDismissIme()
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
