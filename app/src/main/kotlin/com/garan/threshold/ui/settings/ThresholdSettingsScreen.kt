package com.garan.threshold.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.garan.threshold.R
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionChoice

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
) {
    BackHandler(onBack = onBack)

    var showAddQuickActionDialog by remember { mutableStateOf(false) }

    val guardedCount = uiState.baseProfile.appAssignments.values.count { it is AppAssignment.Guarded }
    val blockedCount = uiState.baseProfile.appAssignments.values.count { it is AppAssignment.Blocked }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = ThresholdIcons.ArrowBack,
                    contentDescription = stringResource(R.string.action_go_back),
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // SECTION 1: QUICK ACTIONS
            SettingsSectionHeader(title = stringResource(R.string.section_quick_actions))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F), shape = RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF222222), shape = RoundedCornerShape(4.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.baseProfile.quickActions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.label_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    uiState.baseProfile.quickActions.forEachIndexed { index, comp ->
                        val appLabel = uiState.availableApps.find { it.component == comp }?.label
                            ?: comp.packageName
                        val slotFormatted = String.format("%02d", index + 1)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$slotFormatted  $appLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onRemoveQuickAction(comp) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = ThresholdIcons.Delete,
                                    contentDescription = stringResource(R.string.cd_delete),
                                    tint = Color(0xFF888888),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                if (uiState.baseProfile.quickActions.size < BaseProfileSettings.MAX_QUICK_ACTIONS) {
                    OutlinedButton(
                        onClick = { showAddQuickActionDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Icon(
                            imageVector = ThresholdIcons.Add,
                            contentDescription = stringResource(R.string.cd_add),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.cd_add), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // SECTION 2: GENERAL
            SettingsSectionHeader(title = stringResource(R.string.section_general))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F), shape = RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF222222), shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SettingsToggleRow(
                    label = stringResource(R.string.setting_show_unlocks),
                    checked = uiState.general.showUnlocks,
                    onCheckedChange = onUpdateShowUnlocks,
                )
                HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)
                SettingsToggleRow(
                    label = stringResource(R.string.setting_show_rest),
                    checked = uiState.general.showRest,
                    onCheckedChange = onUpdateShowRest,
                )
                HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)
                SettingsToggleRow(
                    label = stringResource(R.string.setting_require_full_typing),
                    checked = uiState.general.requireFullTypingOfGuardedApps,
                    onCheckedChange = onUpdateRequireFullTyping,
                )
            }

            // SECTION 3: APP CONFIGURATION
            SettingsSectionHeader(title = stringResource(R.string.section_app_configuration))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CountCard(
                    title = stringResource(R.string.card_guarded_apps),
                    count = guardedCount,
                    modifier = Modifier.weight(1f),
                )
                CountCard(
                    title = stringResource(R.string.card_blocked_apps),
                    count = blockedCount,
                    modifier = Modifier.weight(1f),
                )
            }

            // Default Friction Selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F), shape = RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF222222), shape = RoundedCornerShape(4.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_default_friction),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFCCCCCC),
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val currentFriction = uiState.baseProfile.defaultFriction
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

            // Configure Default Apps Button
            Button(
                onClick = onNavigateToConfigureApps,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_configure_default_apps),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    // Add Quick Action Dialog
    if (showAddQuickActionDialog) {
        val availableToAdd = uiState.availableApps.filter {
            it.component !in uiState.baseProfile.quickActions
        }
        Dialog(onDismissRequest = { showAddQuickActionDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF141414),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.cd_add),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    HorizontalDivider(color = Color(0xFF222222))
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(availableToAdd, key = { it.component.id }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddQuickAction(app.component)
                                        showAddQuickActionDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                )
                            }
                            HorizontalDivider(color = Color(0xFF1F1F1F), thickness = 0.5.dp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showAddQuickActionDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                    ) {
                        Text(text = stringResource(R.string.action_go_back), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF888888),
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF444444),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF222222),
            ),
        )
    }
}

@Composable
private fun CountCard(title: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF0F0F0F), shape = RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF222222), shape = RoundedCornerShape(4.dp))
            .padding(14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF888888),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (count == 0) {
                stringResource(R.string.label_empty)
            } else if (count == 1) {
                stringResource(R.string.label_one_app)
            } else {
                stringResource(R.string.label_apps_count, count)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}
