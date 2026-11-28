package com.garan.drytoast.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garan.drytoast.R
import com.garan.drytoast.core.model.AppAssignment
import com.garan.drytoast.core.model.BaseProfileSettings
import com.garan.drytoast.core.model.ComponentKey
import com.garan.drytoast.core.model.FrictionChoice
import com.garan.drytoast.core.model.GeneralSettings
import com.garan.drytoast.core.model.InstalledApp
import com.garan.drytoast.ui.search.SearchKeyboardOptions

object SettingsTestTags {
    const val SETTINGS_LIST = "settings_list"
    const val NAV_PROFILES = "settings_nav_profiles"
    const val NAV_CONFIGURE_APPS = "settings_nav_configure_apps"
    const val ADD_QUICK_ACTION = "settings_add_quick_action"
    const val ADD_QUICK_ACTION_SHEET = "settings_add_quick_action_sheet"
    const val ADD_QUICK_ACTION_SEARCH_INPUT = "settings_add_quick_action_search_input"
    const val PENDING_REBOOT_BANNER = "settings_pending_reboot_banner"
    const val DISCARD_PENDING_CHANGES_BUTTON = "settings_discard_pending_changes"
    const val TOGGLE_SHOW_DIALER_ICON = "settings_toggle_show_dialer_icon"
    const val TOGGLE_REQUIRE_REBOOT = "settings_toggle_require_reboot"
    const val NAV_HARDENED_MODE = "settings_nav_hardened_mode"
    const val NAV_BREAK_GLASS = "settings_nav_break_glass"
    const val GUARDED_GRACE_PERIOD_ROW = "settings_guarded_grace_period_row"
    const val GUARDED_GRACE_PERIOD_SHEET = "settings_guarded_grace_period_sheet"
    const val CONFIRM_REQUIRE_REBOOT_DIALOG = "settings_confirm_require_reboot_dialog"
    const val CONFIRM_REQUIRE_REBOOT_BUTTON = "settings_confirm_require_reboot_button"
    const val CANCEL_REQUIRE_REBOOT_BUTTON = "settings_cancel_require_reboot_button"
    const val SET_BLACK_WALLPAPER_ACTION = "settings_set_black_wallpaper"
    const val CONFIRM_BLACK_WALLPAPER_DIALOG = "settings_confirm_black_wallpaper_dialog"
    const val CONFIRM_BLACK_WALLPAPER_BUTTON = "settings_confirm_black_wallpaper_button"
    const val CANCEL_BLACK_WALLPAPER_BUTTON = "settings_cancel_black_wallpaper_button"
    const val DOCS_LINK_ROW = "settings_docs_link_row"
    const val CONSOLE_LINK_ROW = "settings_console_link_row"
    const val FEEDBACK_BUTTON = "settings_feedback_button"
    const val EXPORT_CONFIG_ACTION = "settings_export_config_action"
    const val IMPORT_CONFIG_ACTION = "settings_import_config_action"
    const val EXPORT_CONFIG_SHEET = "settings_export_config_sheet"
    const val EXPORT_PASSPHRASE_INPUT = "settings_export_passphrase_input"
    const val EXPORT_CONFIRM_PASSPHRASE_INPUT = "settings_export_confirm_passphrase_input"
    const val EXPORT_CONFIRM_BUTTON = "settings_export_confirm_button"
    const val EXPORT_CANCEL_BUTTON = "settings_export_cancel_button"
    const val IMPORT_PASSPHRASE_SHEET = "settings_import_passphrase_sheet"
    const val IMPORT_PASSPHRASE_INPUT = "settings_import_passphrase_input"
    const val IMPORT_PASSPHRASE_ERROR = "settings_import_passphrase_error"
    const val IMPORT_UNLOCK_BUTTON = "settings_import_unlock_button"
    const val IMPORT_CANCEL_BUTTON = "settings_import_cancel_button"

    fun guardedGraceOptionTag(minutes: Int): String = "settings_guarded_grace_option_$minutes"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DryToastSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onNavigateToConfigureApps: () -> Unit,
    onUpdateShowUnlocks: (Boolean) -> Unit,
    onUpdateShowRest: (Boolean) -> Unit,
    onUpdateRequireFullTyping: (Boolean) -> Unit,
    onUpdateDefaultFriction: (FrictionChoice) -> Unit,
    onAddQuickAction: (InstalledApp) -> Unit,
    onRemoveQuickAction: (ComponentKey) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToHardenedMode: () -> Unit = {},
    onNavigateToBreakGlass: () -> Unit = {},
    onUpdateShowDialerIcon: (Boolean) -> Unit = {},
    onUpdateRequireReboot: (Boolean) -> Unit = {},
    onUpdateGuardedSessionGraceMinutes: (Int) -> Unit = {},
    onDiscardPendingChanges: () -> Unit = {},
    onApplyPureBlackWallpaper: () -> Unit = {},
    onSendFeedback: () -> Unit = {},
    onOpenWebsite: ((String) -> Unit)? = null,
    onRequestExport: (passphrase: String?) -> Unit = {},
    onRequestImport: () -> Unit = {},
    showImportPassphraseSheet: Boolean = false,
    importPassphraseError: String? = null,
    isImportWorking: Boolean = false,
    onSubmitImportPassphrase: (String) -> Unit = {},
    onDismissImportPassphraseSheet: () -> Unit = {},
    backupStatusMessage: String? = null,
    onClearBackupStatusMessage: () -> Unit = {},
) {
    val isMasterPane = LocalIsSettingsMasterPane.current
    val isMasterWithPushedDetail = LocalIsSettingsMasterWithPushedDetail.current
    val selectedDetail = LocalSelectedSettingsDetail.current
    BackHandler(enabled = !isMasterWithPushedDetail, onBack = onBack)

    val context = LocalContext.current
    val openWebsiteHandler: (String) -> Unit = onOpenWebsite ?: { url ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    var showAddQuickActionSheet by rememberSaveable { mutableStateOf(false) }
    var showGuardedGraceSheet by rememberSaveable { mutableStateOf(false) }
    var showExportConfigSheet by rememberSaveable { mutableStateOf(false) }
    var showConfirmRequireRebootDialog by rememberSaveable { mutableStateOf(false) }
    var showConfirmBlackWallpaperDialog by rememberSaveable { mutableStateOf(false) }
    var pendingSecondDeviceUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(backupStatusMessage) {
        if (backupStatusMessage != null) {
            snackbarHostState.showSnackbar(backupStatusMessage)
            onClearBackupStatusMessage()
        }
    }

    val guardedCount = uiState.baseProfile.appAssignments.values.count { it is AppAssignment.Guarded }
    val blockedCount = uiState.baseProfile.appAssignments.values.count { it is AppAssignment.Blocked }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                            imageVector = DryToastIcons.ArrowBack,
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
        SettingsContentFrame {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SettingsTestTags.SETTINGS_LIST)
                    .consumeWindowInsets(innerPadding),
                contentPadding = innerPadding,
            ) {
                if (uiState.hasPendingRebootChanges) {
                    item(key = "pending_reboot_banner") {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.banner_pending_reboot_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.banner_pending_reboot_body),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                TextButton(
                                    onClick = onDiscardPendingChanges,
                                    modifier = Modifier.testTag(SettingsTestTags.DISCARD_PENDING_CHANGES_BUTTON),
                                ) {
                                    Text(text = stringResource(R.string.action_discard_pending_changes))
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SettingsTestTags.PENDING_REBOOT_BANNER),
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                        )
                    }
                }

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
                    uiState.baseProfile.quickActions.forEachIndexed { index, quickApp ->
                        val comp = quickApp.component
                        item(key = "quick_action_${comp.id}") {
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
                                        text = quickApp.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { onRemoveQuickAction(comp) }) {
                                        Icon(
                                            imageVector = DryToastIcons.Delete,
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
                                    imageVector = DryToastIcons.Add,
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
                                .testTag(SettingsTestTags.ADD_QUICK_ACTION)
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
                    val isSelected = isMasterPane && selectedDetail == SettingsDetailSection.PROFILES

                    ListItem(
                        headlineContent = {
                            Text(
                                text = "Custom profiles",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                                imageVector = DryToastIcons.ChevronRight,
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { if (isMasterPane) selected = isSelected }
                            .testTag(SettingsTestTags.NAV_PROFILES)
                            .clickable(onClick = onNavigateToProfiles),
                    )
                }

                item(key = "nav_configure_apps") {
                    val isSelected = isMasterPane && selectedDetail == SettingsDetailSection.CONFIGURE_APPS
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.action_configure_default_apps),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                                imageVector = DryToastIcons.ChevronRight,
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { if (isMasterPane) selected = isSelected }
                            .testTag(SettingsTestTags.NAV_CONFIGURE_APPS)
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

                item(key = "toggle_show_dialer_icon") {
                    SettingsToggleItem(
                        label = stringResource(R.string.setting_show_dialer_icon),
                        checked = uiState.general.showDialerIcon,
                        onCheckedChange = onUpdateShowDialerIcon,
                        modifier = Modifier.testTag(SettingsTestTags.TOGGLE_SHOW_DIALER_ICON),
                    )
                }

                item(key = "toggle_require_full_typing") {
                    SettingsToggleItem(
                        label = stringResource(R.string.setting_require_full_typing),
                        checked = uiState.general.requireFullTypingOfGuardedApps,
                        onCheckedChange = onUpdateRequireFullTyping,
                    )
                }

                item(key = "toggle_require_reboot") {
                    SettingsToggleItem(
                        label = stringResource(R.string.setting_require_reboot),
                        supportingText = stringResource(R.string.setting_require_reboot_summary),
                        checked = uiState.general.requireRebootForSettingsChange,
                        onCheckedChange = { newChecked ->
                            if (newChecked && !uiState.isRequireRebootActiveOnLive) {
                                showConfirmRequireRebootDialog = true
                            } else {
                                onUpdateRequireReboot(newChecked)
                            }
                        },
                        modifier = Modifier.testTag(SettingsTestTags.TOGGLE_REQUIRE_REBOOT),
                    )
                }

                item(key = "nav_hardened_mode") {
                    val summary = if (uiState.isHardenedModeActive) {
                        stringResource(R.string.setting_hardened_mode_active_summary)
                    } else {
                        stringResource(R.string.setting_hardened_mode_inactive_summary)
                    }
                    val isSelected = isMasterPane && selectedDetail == SettingsDetailSection.HARDENED_MODE
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_hardened_mode),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                                imageVector = DryToastIcons.ChevronRight,
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { if (isMasterPane) selected = isSelected }
                            .testTag(SettingsTestTags.NAV_HARDENED_MODE)
                            .clickable(onClick = onNavigateToHardenedMode),
                    )
                }

                item(key = "nav_break_glass") {
                    val nowMillis = System.currentTimeMillis()
                    val breakGlassRemainingMin = uiState.breakGlassUntilEpochMillis?.let { until ->
                        val diff = until - nowMillis
                        if (diff > 0L) ((diff + 59_999L) / 60_000L).toInt().coerceAtLeast(1) else 0
                    } ?: 0
                    val summary = when {
                        breakGlassRemainingMin > 0 ->
                            stringResource(R.string.setting_break_glass_active_summary, breakGlassRemainingMin)
                        uiState.general.normalizedBreakGlassTagUid() != null ->
                            stringResource(
                                R.string.setting_break_glass_configured_summary,
                                uiState.general.breakGlassDurationMinutes,
                            )
                        else -> stringResource(R.string.setting_break_glass_unconfigured)
                    }
                    val isSelected = isMasterPane && selectedDetail == SettingsDetailSection.BREAK_GLASS
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_break_glass),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                                imageVector = DryToastIcons.ChevronRight,
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { if (isMasterPane) selected = isSelected }
                            .testTag(SettingsTestTags.NAV_BREAK_GLASS)
                            .clickable(onClick = onNavigateToBreakGlass),
                    )
                }

                if (uiState.isHardenedModeActive) {
                    item(key = "setting_guarded_grace_period") {
                        val graceMinutes = uiState.general.guardedSessionGraceMinutes
                        val summary = formatGuardedGraceOptionLabel(graceMinutes)
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.setting_guarded_grace_period),
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
                                    imageVector = DryToastIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SettingsTestTags.GUARDED_GRACE_PERIOD_ROW)
                                .clickable { showGuardedGraceSheet = true },
                        )
                    }
                }

                item(key = "action_set_black_wallpaper") {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.action_set_black_wallpaper),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.action_set_black_wallpaper_summary),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = DryToastIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsTestTags.SET_BLACK_WALLPAPER_ACTION)
                            .clickable { showConfirmBlackWallpaperDialog = true },
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

                item(key = "divider_4") {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // SECTION 5: BACKUP & RESTORE
                item(key = "header_backup_and_restore") {
                    SettingsSectionHeader(title = stringResource(R.string.section_backup_and_restore))
                }

                item(key = "action_export_config") {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_export_config_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_export_config_summary),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = DryToastIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsTestTags.EXPORT_CONFIG_ACTION)
                            .clickable { showExportConfigSheet = true },
                    )
                }

                item(key = "action_import_config") {
                    val importEnabled = !uiState.isSettingsLocked
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_import_config_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (importEnabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(
                                    if (importEnabled) {
                                        R.string.setting_import_config_summary
                                    } else {
                                        R.string.setting_import_config_locked_summary
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = DryToastIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsTestTags.IMPORT_CONFIG_ACTION)
                            .clickable(enabled = importEnabled, onClick = onRequestImport),
                    )
                }

                item(key = "divider_5") {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // SECTION 6: GUIDE & CONSOLE
                item(key = "header_guide_and_console") {
                    SettingsSectionHeader(title = stringResource(R.string.section_guide_and_console))
                }

                item(key = "link_docs") {
                    val docsUrl = stringResource(R.string.docs_website_url)
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_docs_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_docs_summary),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = DryToastIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsTestTags.DOCS_LINK_ROW)
                            .clickable { openWebsiteHandler(docsUrl) },
                    )
                }

                item(key = "link_console") {
                    val consoleUrl = stringResource(R.string.console_website_url)
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_console_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_console_summary),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = DryToastIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsTestTags.CONSOLE_LINK_ROW)
                            .clickable { pendingSecondDeviceUrl = consoleUrl },
                    )
                }

                item(key = "action_feedback") {
                    OutlinedButton(
                        onClick = onSendFeedback,
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                            .testTag(SettingsTestTags.FEEDBACK_BUTTON),
                    ) {
                        Text(
                            text = stringResource(R.string.action_feedback_is_a_gift),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }

    // Add Quick Action Searchable ModalBottomSheet
    if (showAddQuickActionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var searchQuery by rememberSaveable { mutableStateOf("") }
        val availableToAdd = remember(uiState.availableApps, uiState.baseProfile.quickActions, searchQuery) {
            val pinnedComponents = uiState.baseProfile.quickActions.mapTo(hashSetOf()) { it.component }
            uiState.availableApps
                .filter { it.component !in pinnedComponents }
                .filter { searchQuery.isBlank() || it.label.contains(searchQuery, ignoreCase = true) }
                .sortedBy { it.label.lowercase() }
        }

        ModalBottomSheet(
            onDismissRequest = { showAddQuickActionSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag(SettingsTestTags.ADD_QUICK_ACTION_SHEET),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(SettingsTestTags.ADD_QUICK_ACTION_SEARCH_INPUT),
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
                                    onAddQuickAction(app)
                                    showAddQuickActionSheet = false
                                },
                        )
                    }
                }
            }
        }
    }

    if (showGuardedGraceSheet && uiState.isHardenedModeActive) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val currentMinutes = uiState.general.guardedSessionGraceMinutes

        ModalBottomSheet(
            onDismissRequest = { showGuardedGraceSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
                    .selectableGroup()
                    .testTag(SettingsTestTags.GUARDED_GRACE_PERIOD_SHEET),
            ) {
                Text(
                    text = stringResource(R.string.setting_guarded_grace_period),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Text(
                    text = stringResource(R.string.setting_guarded_grace_period_sheet_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )

                for (optionMinutes in GeneralSettings.GUARDED_SESSION_GRACE_OPTIONS_MINUTES) {
                    FrictionRadioOption(
                        title = formatGuardedGraceOptionLabel(optionMinutes),
                        selected = currentMinutes == optionMinutes,
                        onSelect = {
                            onUpdateGuardedSessionGraceMinutes(optionMinutes)
                            showGuardedGraceSheet = false
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.testTag(SettingsTestTags.guardedGraceOptionTag(optionMinutes)),
                    )
                }
            }
        }
    }

    pendingSecondDeviceUrl?.let { targetUrl ->
        SecondDeviceLinkBottomSheet(
            url = targetUrl,
            onOpenHere = openWebsiteHandler,
            onDismiss = { pendingSecondDeviceUrl = null },
        )
    }

    if (showConfirmRequireRebootDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmRequireRebootDialog = false },
            title = {
                Text(text = stringResource(R.string.dialog_require_reboot_title))
            },
            text = {
                Text(text = stringResource(R.string.dialog_require_reboot_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmRequireRebootDialog = false
                        onUpdateRequireReboot(true)
                    },
                    modifier = Modifier.testTag(SettingsTestTags.CONFIRM_REQUIRE_REBOOT_BUTTON),
                ) {
                    Text(text = stringResource(R.string.action_enable))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmRequireRebootDialog = false },
                    modifier = Modifier.testTag(SettingsTestTags.CANCEL_REQUIRE_REBOOT_BUTTON),
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
            modifier = Modifier.testTag(SettingsTestTags.CONFIRM_REQUIRE_REBOOT_DIALOG),
        )
    }

    if (showExportConfigSheet) {
        ExportConfigBottomSheet(
            onDismiss = { showExportConfigSheet = false },
            onConfirmExport = { passphrase ->
                showExportConfigSheet = false
                onRequestExport(passphrase)
            },
        )
    }

    if (showImportPassphraseSheet) {
        ImportPassphraseBottomSheet(
            errorMessage = importPassphraseError,
            isWorking = isImportWorking,
            onDismiss = onDismissImportPassphraseSheet,
            onSubmitPassphrase = onSubmitImportPassphrase,
        )
    }

    if (showConfirmBlackWallpaperDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmBlackWallpaperDialog = false },
            title = {
                Text(text = stringResource(R.string.dialog_black_wallpaper_title))
            },
            text = {
                Text(text = stringResource(R.string.dialog_black_wallpaper_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmBlackWallpaperDialog = false
                        onApplyPureBlackWallpaper()
                    },
                    modifier = Modifier.testTag(SettingsTestTags.CONFIRM_BLACK_WALLPAPER_BUTTON),
                ) {
                    Text(text = stringResource(R.string.action_apply))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmBlackWallpaperDialog = false },
                    modifier = Modifier.testTag(SettingsTestTags.CANCEL_BLACK_WALLPAPER_BUTTON),
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
            modifier = Modifier.testTag(SettingsTestTags.CONFIRM_BLACK_WALLPAPER_DIALOG),
        )
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
    supportingText: String? = null,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = if (supportingText != null) {
            {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            null
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

@Composable
private fun formatGuardedGraceOptionLabel(minutes: Int): String = when (minutes) {
    0 -> stringResource(R.string.guarded_grace_immediate)
    1 -> stringResource(R.string.guarded_grace_minutes_one)
    else -> stringResource(R.string.guarded_grace_minutes_format, minutes)
}
