package com.garan.threshold.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garan.threshold.R
import com.garan.threshold.core.model.AntiMetricsFormatter
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.model.ProfileCapability
import com.garan.threshold.core.model.ProfileCapabilityIssue
import com.garan.threshold.core.port.AntiMetricsRepository
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.LaunchResult
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.ui.friction.CaretTracingSurface
import com.garan.threshold.ui.friction.DragSortSurface
import com.garan.threshold.ui.friction.TypedIntentionSurface
import com.garan.threshold.ui.launcher.AppLauncher
import com.garan.threshold.ui.motion.ThresholdMotion
import com.garan.threshold.ui.search.SearchViewModel
import com.garan.threshold.ui.search.ThresholdSearchSurface
import com.garan.threshold.ui.theme.SubtleGray
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

internal const val SETTINGS_LONG_PRESS_TIMEOUT_MS = 3_000L

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
    onOpenSettings: () -> Unit = {},
    onResolveCapabilityIssue: (ProfileCapability) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showCapabilityFaultSheet by remember { mutableStateOf(false) }

    val metrics by antiMetricsRepository?.metrics?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val showUnlocks = uiState.generalSettings.showUnlocks
    val showRest = uiState.generalSettings.showRest
    val effectiveStatusText = statusText
        ?: if (!showUnlocks && !showRest) {
            stringResource(R.string.app_name)
        } else {
            metrics?.let { AntiMetricsFormatter.formatStatus(it, showUnlocks = showUnlocks, showRest = showRest) }
                ?.ifBlank { stringResource(R.string.app_name) }
                ?: stringResource(R.string.app_name)
        }

    val activeFriction = uiState.activeFriction
    val inSearch = uiState.isSearching || uiState.query.isNotEmpty()

    val hasSeenFirstRunHint = uiState.generalSettings.hasSeenFirstRunHint
    var showHint by remember(hasSeenFirstRunHint) { mutableStateOf(!hasSeenFirstRunHint) }
    val hintAlpha = remember { Animatable(1f) }

    if (showHint && !hasSeenFirstRunHint) {
        LaunchedEffect(Unit) {
            delay(20_000L)
            hintAlpha.animateTo(
                targetValue = 0f,
                animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.MAX_DURATION_MS),
            )
            showHint = false
            viewModel.markFirstRunHintSeen()
        }
    }

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
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME,
                -> {
                    viewModel.refreshScheduleEvaluation()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.dismissFriction()
                }
                else -> Unit
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
        val defaultViewConfig = LocalViewConfiguration.current
        val settingsLongPressViewConfig = remember(defaultViewConfig) {
            object : ViewConfiguration by defaultViewConfig {
                override val longPressTimeoutMillis: Long = SETTINGS_LONG_PRESS_TIMEOUT_MS
            }
        }
        val currentHasSeenFirstRunHint by rememberUpdatedState(hasSeenFirstRunHint)
        val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)

        Box(modifier = modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalViewConfiguration provides settingsLongPressViewConfig) {
                ThresholdHomeCanvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    if (!currentHasSeenFirstRunHint) {
                                        viewModel.markFirstRunHintSeen()
                                    }
                                    currentOnOpenSettings()
                                },
                            )
                        },
                    statusText = effectiveStatusText,
                    activeProfiles = uiState.activeProfiles,
                    hasCapabilityIssues = uiState.capabilityIssues.isNotEmpty(),
                    onCapabilityFaultClick = { showCapabilityFaultSheet = true },
                    onTimeTick = {
                        antiMetricsRepository?.refreshForCurrentDate()
                        viewModel.refreshScheduleEvaluation()
                    },
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Restore default ViewConfiguration for children so search result long-press
                    // (App Info) and text input handles retain the standard system timeout.
                    CompositionLocalProvider(LocalViewConfiguration provides defaultViewConfig) {
                        ThresholdSearchSurface(
                            query = uiState.query,
                            onQueryChange = viewModel::onQueryChanged,
                            results = uiState.results,
                            listState = searchListState,
                            quickActions = uiState.quickActions,
                            onQuickActionClick = { quickItem ->
                                viewModel.launchQuickAction(quickItem) {
                                    val launched = appLauncher.launchApp(quickItem.component)
                                    if (launched) {
                                        resetSearchAndHideIme()
                                    }
                                }
                            },
                            onItemClick = { item ->
                                when (item) {
                                    is SearchItem.App -> {
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
                                    is SearchItem.Action -> {
                                        if (item.id == "settings") {
                                            resetSearchAndHideIme()
                                            onOpenSettings()
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

            // First-run Onboarding Hint (displayed in center, optical fade out at 20s)
            if (showHint && !hasSeenFirstRunHint && !inSearch) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.hint_long_press_to_configure),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SubtleGray.copy(alpha = hintAlpha.value),
                    )
                }
            }

            if (showCapabilityFaultSheet && uiState.capabilityIssues.isNotEmpty()) {
                CapabilityFaultBottomSheet(
                    issues = uiState.capabilityIssues,
                    onGrantPermission = { capability ->
                        showCapabilityFaultSheet = false
                        onResolveCapabilityIssue(capability)
                    },
                    onDismiss = { showCapabilityFaultSheet = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CapabilityFaultBottomSheet(
    issues: ImmutableList<ProfileCapabilityIssue>,
    onGrantPermission: (ProfileCapability) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val primaryCapability = issues.firstOrNull()?.missingCapabilities?.firstOrNull()
        ?: ProfileCapability.EXACT_ALARM

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Profile permissions required",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The following enabled profiles are missing permissions needed to trigger reliably:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            issues.forEach { issue ->
                ListItem(
                    leadingContent = {
                        ActiveProfileSquareBadge(profile = issue.profile)
                    },
                    headlineContent = {
                        Text(
                            text = issue.profile.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = issue.missingCapabilities.joinToString(", ") { capabilityLabel(it) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                Button(onClick = { onGrantPermission(primaryCapability) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

private fun capabilityLabel(capability: ProfileCapability): String = when (capability) {
    ProfileCapability.EXACT_ALARM -> "Exact schedule alarms permission required"
    ProfileCapability.BACKGROUND_LOCATION -> "Location permission & Location Services required"
    ProfileCapability.NFC_HARDWARE_ENABLED -> "NFC must be enabled in system settings"
}
