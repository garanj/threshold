package com.garan.threshold.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garan.threshold.R
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.ui.search.SearchKeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onNavigateToConfigureApps: () -> Unit,
    onUpdateShowUnlocks: (Boolean) -> Unit,
    onUpdateShowRest: (Boolean) -> Unit,
    onUpdateRequireFullTyping: (Boolean) -> Unit,
    onUpdateDefaultFriction: (FrictionChoice) -> Unit,
    onAddQuickAction: (ComponentKey) -> Unit,
    onRemoveQuickAction: (ComponentKey) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToProfiles: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    var showAddQuickActionSheet by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val guardedCount = uiState.baseProfile.appAssignments.values.count { it is AppAssignment.Guarded }
    val blockedCount = uiState.baseProfile.appAssignments.values.count { it is AppAssignment.Blocked }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = ThresholdIcons.ArrowBack,
                            contentDescription = stringResource(R.string.action_go_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = innerPadding,
        ) {
            // SECTION 1: QUICK ACTIONS
            item(key = "header_quick_actions") {
                SettingsSectionHeader(title = stringResource(R.string.section_quick_actions))
            }

            if (uiState.baseProfile.quickActions.isEmpty()) {
                item(key = "quick_actions_empty") {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.label_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            } else {
                uiState.baseProfile.quickActions.forEachIndexed { index, comp ->
                    item(key = "quick_action_${comp.id}") {
                        val appLabel = uiState.availableApps.find { it.component == comp }?.label
                            ?: comp.packageName
                        val slotFormatted = String.format("%02d", index + 1)

                        ListItem(
                            leadingContent = {
                                Text(
                                    text = slotFormatted,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = appLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onRemoveQuickAction(comp) }) {
                                    Icon(
                                        imageVector = ThresholdIcons.Delete,
                                        contentDescription = stringResource(R.string.cd_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            if (uiState.baseProfile.quickActions.size < BaseProfileSettings.MAX_QUICK_ACTIONS) {
                item(key = "quick_action_add") {
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = ThresholdIcons.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.cd_add),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddQuickActionSheet = true },
                    )
                }
            }

            item(key = "divider_1") {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // SECTION 2: PROFILES & APP CONFIGURATION
            item(key = "header_app_config") {
                SettingsSectionHeader(title = stringResource(R.string.section_app_configuration))
            }

            item(key = "nav_profiles") {
                val profileCount = uiState.customProfiles.size
                val activeCount = uiState.customProfiles.count { it.enabled }
                val summary = when {
                    profileCount == 0 -> "No custom profiles configured"
                    activeCount == 1 -> "1 profile active"
                    else -> "$profileCount profiles ($activeCount active)"
                }

                ListItem(
                    headlineContent = {
                        Text(
                            text = "Custom profiles",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = ThresholdIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToProfiles),
                )
            }

            item(key = "nav_configure_apps") {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.action_configure_default_apps),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "$guardedCount guarded · $blockedCount blocked",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = ThresholdIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToConfigureApps),
                )
            }

            item(key = "divider_2") {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // SECTION 3: GENERAL
            item(key = "header_general") {
                SettingsSectionHeader(title = stringResource(R.string.section_general))
            }

            item(key = "toggle_show_unlocks") {
                SettingsToggleItem(
                    label = stringResource(R.string.setting_show_unlocks),
                    checked = uiState.general.showUnlocks,
                    onCheckedChange = onUpdateShowUnlocks,
                )
            }

            item(key = "toggle_show_rest") {
                SettingsToggleItem(
                    label = stringResource(R.string.setting_show_rest),
                    checked = uiState.general.showRest,
                    onCheckedChange = onUpdateShowRest,
                )
            }

            item(key = "toggle_require_full_typing") {
                SettingsToggleItem(
                    label = stringResource(R.string.setting_require_full_typing),
                    checked = uiState.general.requireFullTypingOfGuardedApps,
                    onCheckedChange = onUpdateRequireFullTyping,
                )
            }

            item(key = "divider_3") {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // SECTION 4: DEFAULT FRICTION
            item(key = "header_default_friction") {
                SettingsSectionHeader(title = stringResource(R.string.label_default_friction))
            }

            item(key = "default_friction_group") {
                val currentFriction = uiState.baseProfile.defaultFriction
                Column(modifier = Modifier.selectableGroup()) {
                    FrictionRadioOption(
                        title = stringResource(R.string.friction_typed_intention),
                        selected = currentFriction == FrictionChoice.TYPED_INTENTION,
                        onSelect = { onUpdateDefaultFriction(FrictionChoice.TYPED_INTENTION) },
                    )
                    FrictionRadioOption(
                        title = stringResource(R.string.friction_drag_sort),
                        selected = currentFriction == FrictionChoice.DRAG_SORT,
                        onSelect = { onUpdateDefaultFriction(FrictionChoice.DRAG_SORT) },
                    )
                    FrictionRadioOption(
                        title = stringResource(R.string.friction_caret_tracing),
                        selected = currentFriction == FrictionChoice.CARET_TRACING,
                        onSelect = { onUpdateDefaultFriction(FrictionChoice.CARET_TRACING) },
                    )
                    FrictionRadioOption(
                        title = stringResource(R.string.friction_random),
                        selected = currentFriction == FrictionChoice.RANDOM,
                        onSelect = { onUpdateDefaultFriction(FrictionChoice.RANDOM) },
                    )
                }
            }
        }
    }

    // Add Quick Action Searchable ModalBottomSheet
    if (showAddQuickActionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var searchQuery by remember { mutableStateOf("") }
        val availableToAdd = remember(uiState.availableApps, uiState.baseProfile.quickActions, searchQuery) {
            uiState.availableApps
                .filter { it.component !in uiState.baseProfile.quickActions }
                .filter { searchQuery.isBlank() || it.label.contains(searchQuery, ignoreCase = true) }
                .sortedBy { it.label.lowercase() }
        }

        ModalBottomSheet(
            onDismissRequest = { showAddQuickActionSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.section_quick_actions),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    singleLine = true,
                    keyboardOptions = SearchKeyboardOptions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(availableToAdd, key = { it.component.id }) { app ->
                        ListItem(
                            headlineContent = { Text(app.label) },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAddQuickAction(app.component)
                                    showAddQuickActionSheet = false
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsToggleItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    )
}
