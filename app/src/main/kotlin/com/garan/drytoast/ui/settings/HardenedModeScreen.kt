package com.garan.drytoast.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.MultiWindowModeChangedInfo
import androidx.core.app.OnMultiWindowModeChangedProvider
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.garan.drytoast.R
import com.garan.drytoast.core.model.GeneralSettings
import com.garan.drytoast.core.security.ChallengeResponseVerifier
import com.garan.drytoast.core.security.VerificationDomain

/**
 * Validates the QR-code deep link URIs used to open:
 * - [DisableHardenedModeScreen] (`drytoast://hardened/disable?key=...`)
 * - [SettingsLockScreen] (`drytoast://settings/lock?key=...`)
 *
 * The 64-character hex tokens prevent casually typing a short URL on the phone itself, while
 * the target screens additionally require a focus-locked 3-digit challenge + 5-minute TOTP
 * verification code so single-device screenshot scanning cannot complete the action.
 */
object HardenedModeDeepLink {
    const val SCHEME = "drytoast"
    const val HOST = "hardened"
    const val PATH_DISABLE = "/disable"
    const val HOST_SETTINGS = "settings"
    const val PATH_SETTINGS_LOCK = "/lock"
    const val QUERY_PARAM_KEY = "key"
    const val DISABLE_KEY = "4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce"
    const val SETTINGS_LOCK_KEY = "8b2f91c4e7d03a65f1298e4c7b3a1d509e6f2c8a4b7d1e3f5a9c2e4b6d8f0a1c"
    const val FULL_DISABLE_URI = "$SCHEME://$HOST$PATH_DISABLE?$QUERY_PARAM_KEY=$DISABLE_KEY"
    const val FULL_SETTINGS_LOCK_URI = "$SCHEME://$HOST_SETTINGS$PATH_SETTINGS_LOCK?$QUERY_PARAM_KEY=$SETTINGS_LOCK_KEY"
    private const val EXTRA_CONSUMED = "com.garan.drytoast.extra.DISABLE_DEEP_LINK_CONSUMED"

    fun isValidDisableIntent(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        if (intent.getBooleanExtra(EXTRA_CONSUMED, false)) return false
        val uri = intent.data ?: return false
        return uri.scheme == SCHEME &&
            uri.host == HOST &&
            uri.path == PATH_DISABLE &&
            uri.getQueryParameter(QUERY_PARAM_KEY) == DISABLE_KEY
    }

    fun isValidSettingsLockIntent(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        if (intent.getBooleanExtra(EXTRA_CONSUMED, false)) return false
        val uri = intent.data ?: return false
        return uri.scheme == SCHEME &&
            uri.host == HOST_SETTINGS &&
            uri.path == PATH_SETTINGS_LOCK &&
            uri.getQueryParameter(QUERY_PARAM_KEY) == SETTINGS_LOCK_KEY
    }

    fun markConsumed(intent: Intent?) {
        intent?.putExtra(EXTRA_CONSUMED, true)
    }
}

private val CHALLENGE_MONOLITH_MAX_WIDTH = 480.dp

object HardenedModeTestTags {
    const val SCREEN_LIST = "hardened_mode_list"
    const val STATUS_CARD = "hardened_mode_status_card"
    const val BLOCK_NEW_APP_INSTALLS_ROW = "hardened_mode_block_new_app_installs_row"
    const val BLOCK_NEW_APP_INSTALLS_SWITCH = "hardened_mode_block_new_app_installs_switch"
    const val LOCK_SCREEN_TEXT_ROW = "hardened_mode_lock_screen_text_row"
    const val LOCK_SCREEN_SHEET = "hardened_mode_lock_screen_sheet"
    const val OWNER_NAME_INPUT = "hardened_mode_owner_name_input"
    const val LOCK_SCREEN_INFO_INPUT = "hardened_mode_lock_screen_info_input"
    const val LOCK_SCREEN_SAVE_BUTTON = "hardened_mode_lock_screen_save_button"
    const val COMPANION_CONSOLE_ROW = "hardened_mode_companion_console_row"
    const val OPEN_WEBSITE_BUTTON = "hardened_mode_open_website_button"
    const val DISABLE_SCREEN_ROOT = "disable_hardened_mode_screen"
    const val CONFIRM_DISABLE_BUTTON = "disable_hardened_mode_confirm_button"
    const val CANCEL_DISABLE_BUTTON = "disable_hardened_mode_cancel_button"
    const val SETTINGS_LOCK_SCREEN_ROOT = "settings_lock_screen"
    const val CHALLENGE_SECTION = "challenge_verification_section"
    const val CONFIRM_SETTINGS_LOCK_BUTTON = "settings_lock_confirm_button"
    const val CANCEL_SETTINGS_LOCK_BUTTON = "settings_lock_cancel_button"
    const val CHALLENGE_CODE_VALUE = "challenge_code_value"
    const val RESPONSE_CODE_DISPLAY = "challenge_response_display"
    const val CHALLENGE_ERROR_TEXT = "challenge_error_text"
    const val CHALLENGE_MULTI_WINDOW_NOTICE = "challenge_multi_window_notice"
    const val KEYPAD_CLEAR = "challenge_key_clear"
    const val KEYPAD_BACKSPACE = "challenge_key_backspace"

    fun keypadDigitTag(digit: Int): String = "challenge_key_$digit"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HardenedModeScreen(
    isHardenedModeActive: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    blockNewAppInstalls: Boolean = false,
    hardenedOwnerName: String = GeneralSettings.DEFAULT_HARDENED_OWNER_NAME,
    hardenedLockScreenInfo: String = "",
    isSettingsLocked: Boolean = false,
    onToggleBlockNewAppInstalls: (Boolean) -> Unit = {},
    onSaveLockScreenText: (ownerName: String, lockScreenInfo: String) -> Unit = { _, _ -> },
    onOpenWebsite: ((String) -> Unit)? = null,
) {
    val hideBackArrow = LocalHideSettingsDetailBackArrow.current
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var pendingSecondDeviceUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showLockScreenTextSheet by rememberSaveable { mutableStateOf(false) }

    val openWebsiteHandler: (String) -> Unit = onOpenWebsite ?: { url ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopAppBar(
                    modifier = Modifier
                        .widthIn(max = SETTINGS_MEDIUM_MAX_WIDTH)
                        .fillMaxWidth(),
                    title = {
                        Text(
                            text = stringResource(R.string.hardened_mode_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        if (!hideBackArrow) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = DryToastIcons.ArrowBack,
                                    contentDescription = stringResource(R.string.action_go_back),
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        SettingsContentFrame {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(HardenedModeTestTags.SCREEN_LIST)
                    .consumeWindowInsets(innerPadding),
                contentPadding = innerPadding,
            ) {
                item(key = "status_banner") {
                    val statusLabel = if (isHardenedModeActive) {
                        stringResource(R.string.hardened_mode_status_active_label)
                    } else {
                        stringResource(R.string.hardened_mode_status_inactive_label)
                    }
                    val statusBody = if (isHardenedModeActive) {
                        stringResource(R.string.hardened_mode_status_active_body)
                    } else {
                        stringResource(R.string.hardened_mode_status_inactive_body)
                    }
                    val containerColor = if (isHardenedModeActive) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isHardenedModeActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        supportingContent = {
                            Text(
                                text = statusBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = containerColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(HardenedModeTestTags.STATUS_CARD),
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                    )
                }

                if (isHardenedModeActive) {
                    item(key = "header_controls") {
                        SettingsSectionHeader(title = stringResource(R.string.hardened_mode_section_controls))
                    }

                    item(key = "toggle_block_new_app_installs") {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.setting_block_new_app_installs_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.setting_block_new_app_installs_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = blockNewAppInstalls,
                                    onCheckedChange = null,
                                    enabled = !isSettingsLocked,
                                    modifier = Modifier.testTag(HardenedModeTestTags.BLOCK_NEW_APP_INSTALLS_SWITCH),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(HardenedModeTestTags.BLOCK_NEW_APP_INSTALLS_ROW)
                                .toggleable(
                                    value = blockNewAppInstalls,
                                    enabled = !isSettingsLocked,
                                    role = Role.Switch,
                                    onValueChange = onToggleBlockNewAppInstalls,
                                ),
                        )
                    }

                    item(key = "lock_screen_text_row") {
                        val effectiveName = hardenedOwnerName.trim().ifEmpty {
                            GeneralSettings.DEFAULT_HARDENED_OWNER_NAME
                        }
                        val ownerPreview = stringResource(R.string.setting_lock_screen_owner_preview, effectiveName)
                        val summaryText = if (hardenedLockScreenInfo.isNotBlank()) {
                            "$ownerPreview · ${hardenedLockScreenInfo.trim()}"
                        } else {
                            ownerPreview
                        }
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.setting_lock_screen_text_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .testTag(HardenedModeTestTags.LOCK_SCREEN_TEXT_ROW)
                                .clickable(enabled = !isSettingsLocked) {
                                    showLockScreenTextSheet = true
                                },
                        )
                    }

                    item(key = "divider_controls") {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                // SECTION 1: WHAT HARDENED MODE ENFORCES
                item(key = "header_capabilities") {
                    SettingsSectionHeader(title = stringResource(R.string.hardened_mode_section_capabilities))
                }

                item(key = "cap_suspend") {
                    HardenedFeatureRow(
                        title = stringResource(R.string.hardened_mode_cap_suspend_title),
                        body = stringResource(R.string.hardened_mode_cap_suspend_body),
                    )
                }

                item(key = "cap_home") {
                    HardenedFeatureRow(
                        title = stringResource(R.string.hardened_mode_cap_home_title),
                        body = stringResource(R.string.hardened_mode_cap_home_body),
                    )
                }

                item(key = "cap_tamper") {
                    HardenedFeatureRow(
                        title = stringResource(R.string.hardened_mode_cap_tamper_title),
                        body = stringResource(R.string.hardened_mode_cap_tamper_body),
                    )
                }

                item(key = "divider_1") {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // SECTION 2: COMPANION COMMITMENT LAYERS
                item(key = "header_companion") {
                    SettingsSectionHeader(title = stringResource(R.string.hardened_mode_section_companion))
                }

                item(key = "companion_intro") {
                    Text(
                        text = stringResource(R.string.hardened_mode_companion_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                item(key = "companion_reboot") {
                    HardenedFeatureRow(
                        title = stringResource(R.string.hardened_mode_companion_reboot_title),
                        body = stringResource(R.string.hardened_mode_companion_reboot_body),
                    )
                }

                item(key = "companion_adb") {
                    val consoleUrl = stringResource(R.string.console_website_url)
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.hardened_mode_companion_adb_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.hardened_mode_companion_adb_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            .testTag(HardenedModeTestTags.COMPANION_CONSOLE_ROW)
                            .clickable { pendingSecondDeviceUrl = consoleUrl },
                    )
                }

                item(key = "divider_2") {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // SECTION 3: SETUP & REMOVAL
                item(key = "header_setup") {
                    SettingsSectionHeader(title = stringResource(R.string.hardened_mode_section_setup))
                }

                item(key = "setup_intro") {
                    Text(
                        text = stringResource(R.string.hardened_mode_setup_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                item(key = "open_website_button") {
                    val websiteUrl = stringResource(R.string.hardened_mode_website_url)
                    OutlinedButton(
                        onClick = { pendingSecondDeviceUrl = websiteUrl },
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .heightIn(min = 48.dp)
                            .testTag(HardenedModeTestTags.OPEN_WEBSITE_BUTTON),
                    ) {
                        Text(
                            text = stringResource(R.string.action_open_hardened_mode_website),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
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

    if (showLockScreenTextSheet && isHardenedModeActive && !isSettingsLocked) {
        LockScreenTextBottomSheet(
            initialOwnerName = hardenedOwnerName,
            initialLockScreenInfo = hardenedLockScreenInfo,
            onSave = { ownerName, info ->
                onSaveLockScreenText(ownerName, info)
                showLockScreenTextSheet = false
            },
            onDismiss = { showLockScreenTextSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun LockScreenTextBottomSheet(
    initialOwnerName: String,
    initialLockScreenInfo: String,
    onSave: (ownerName: String, lockScreenInfo: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ownerName by rememberSaveable(initialOwnerName) { mutableStateOf(initialOwnerName) }
    var lockScreenInfo by rememberSaveable(initialLockScreenInfo) { mutableStateOf(initialLockScreenInfo) }
    val previewName = ownerName.trim().ifEmpty { GeneralSettings.DEFAULT_HARDENED_OWNER_NAME }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag(HardenedModeTestTags.LOCK_SCREEN_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_lock_screen_text_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = ownerName,
                onValueChange = { ownerName = it },
                label = { Text(stringResource(R.string.setting_lock_screen_owner_name_label)) },
                supportingText = {
                    Text(stringResource(R.string.setting_lock_screen_owner_preview, previewName))
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HardenedModeTestTags.OWNER_NAME_INPUT),
            )

            OutlinedTextField(
                value = lockScreenInfo,
                onValueChange = { lockScreenInfo = it },
                label = { Text(stringResource(R.string.setting_lock_screen_info_label)) },
                placeholder = { Text(stringResource(R.string.setting_lock_screen_info_placeholder)) },
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HardenedModeTestTags.LOCK_SCREEN_INFO_INPUT),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { onSave(ownerName, lockScreenInfo) },
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(HardenedModeTestTags.LOCK_SCREEN_SAVE_BUTTON),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

/**
 * Hidden screen reachable strictly via the external QR-code deep link
 * ([HardenedModeDeepLink.FULL_DISABLE_URI]). Remains accessible even when
 * `isSettingsLocked` is true so scanning the website QR code always provides a clean exit path.
 *
 * Requires a focus-locked 3-digit challenge + 5-minute UTC TOTP 6-digit verification code so
 * single-device screenshot scanning cannot complete deactivation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DisableHardenedModeScreen(
    isHardenedModeActive: Boolean,
    onConfirmDisable: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialChallenge: String? = null,
    nowEpochMillisProvider: () -> Long = System::currentTimeMillis,
    isMultiWindowOverride: Boolean? = null,
) {
    BackHandler(onBack = onCancel)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag(HardenedModeTestTags.DISABLE_SCREEN_ROOT),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.disable_hardened_mode_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = DryToastIcons.ArrowBack,
                            contentDescription = stringResource(R.string.action_go_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = CHALLENGE_MONOLITH_MAX_WIDTH)
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .padding(horizontal = 24.dp),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "headline") {
                    Text(
                        text = stringResource(R.string.disable_hardened_mode_headline),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                if (isHardenedModeActive) {
                    item(key = "challenge_verification") {
                        ChallengeVerificationSection(
                            domain = VerificationDomain.HARDENED_DISABLE,
                            confirmButtonText = stringResource(R.string.action_disable_hardened_mode),
                            confirmButtonTestTag = HardenedModeTestTags.CONFIRM_DISABLE_BUTTON,
                            cancelButtonTestTag = HardenedModeTestTags.CANCEL_DISABLE_BUTTON,
                            onVerified = onConfirmDisable,
                            onCancel = onCancel,
                            initialChallenge = initialChallenge,
                            nowEpochMillisProvider = nowEpochMillisProvider,
                            isMultiWindowOverride = isMultiWindowOverride,
                        )
                    }
                } else {
                    item(key = "inactive_body") {
                        Text(
                            text = stringResource(R.string.disable_hardened_mode_inactive_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    item(key = "cancel_only") {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(HardenedModeTestTags.CANCEL_DISABLE_BUTTON),
                        ) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hidden screen reachable strictly via the external QR-code deep link
 * ([HardenedModeDeepLink.FULL_SETTINGS_LOCK_URI]). Remains accessible even when
 * `isSettingsLocked` is true so scanning the website QR code can both lock and unlock
 * Dry Toast Settings using a focus-locked 3-digit challenge + 5-minute UTC TOTP 6-digit code.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsLockScreen(
    isSettingsLocked: Boolean,
    onConfirmSetSettingsLocked: (Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialChallenge: String? = null,
    nowEpochMillisProvider: () -> Long = System::currentTimeMillis,
    isMultiWindowOverride: Boolean? = null,
) {
    BackHandler(onBack = onCancel)

    val headlineRes = if (isSettingsLocked) {
        R.string.settings_lock_headline_locked
    } else {
        R.string.settings_lock_headline_unlocked
    }
    val confirmActionRes = if (isSettingsLocked) {
        R.string.action_unlock_settings
    } else {
        R.string.action_lock_settings
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag(HardenedModeTestTags.SETTINGS_LOCK_SCREEN_ROOT),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_lock_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = DryToastIcons.ArrowBack,
                            contentDescription = stringResource(R.string.action_go_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = CHALLENGE_MONOLITH_MAX_WIDTH)
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .padding(horizontal = 24.dp),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "headline") {
                    Text(
                        text = stringResource(headlineRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                item(key = "challenge_verification") {
                    ChallengeVerificationSection(
                        domain = VerificationDomain.SETTINGS_LOCK,
                        confirmButtonText = stringResource(confirmActionRes),
                        confirmButtonTestTag = HardenedModeTestTags.CONFIRM_SETTINGS_LOCK_BUTTON,
                        cancelButtonTestTag = HardenedModeTestTags.CANCEL_SETTINGS_LOCK_BUTTON,
                        onVerified = { onConfirmSetSettingsLocked(!isSettingsLocked) },
                        onCancel = onCancel,
                        initialChallenge = initialChallenge,
                        nowEpochMillisProvider = nowEpochMillisProvider,
                        isMultiWindowOverride = isMultiWindowOverride,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeVerificationSection(
    domain: VerificationDomain,
    confirmButtonText: String,
    confirmButtonTestTag: String,
    cancelButtonTestTag: String,
    onVerified: () -> Unit,
    onCancel: () -> Unit,
    initialChallenge: String?,
    nowEpochMillisProvider: () -> Long,
    isMultiWindowOverride: Boolean?,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current
    val keypadFocusRequester = remember { FocusRequester() }

    val isMultiWindow = rememberIsInMultiWindowMode(
        activity = activity,
        override = isMultiWindowOverride,
    )

    var challenge by rememberSaveable(initialChallenge) {
        mutableStateOf(initialChallenge ?: ChallengeResponseVerifier.generateChallenge())
    }
    var lastVisibleChallenge by rememberSaveable(initialChallenge) {
        mutableStateOf(challenge)
    }
    var enteredCode by rememberSaveable { mutableStateOf("") }
    var hasError by rememberSaveable { mutableStateOf(false) }
    var lastObservedMultiWindow by rememberSaveable { mutableStateOf(isMultiWindow) }
    var hadWindowFocus by remember { mutableStateOf(false) }

    val rotateChallenge: () -> Unit = {
        var next = ChallengeResponseVerifier.generateChallenge(previousChallenge = challenge)
        while (next == lastVisibleChallenge) {
            next = ChallengeResponseVerifier.generateChallenge(previousChallenge = challenge)
        }
        challenge = next
        if (!isMultiWindow) {
            lastVisibleChallenge = next
        }
        enteredCode = ""
        hasError = false
    }

    val appendDigit: (Int) -> Unit = { digit ->
        if (enteredCode.length < ChallengeResponseVerifier.RESPONSE_CODE_LENGTH) {
            enteredCode += digit.toString()
            hasError = false
        }
    }

    val submitVerification: () -> Unit = {
        if (enteredCode.length == ChallengeResponseVerifier.RESPONSE_CODE_LENGTH) {
            val valid = ChallengeResponseVerifier.verify(
                domain = domain,
                challenge = challenge,
                responseCode = enteredCode,
                nowEpochMillis = nowEpochMillisProvider(),
            )
            if (valid) {
                onVerified()
            } else {
                hasError = true
                enteredCode = ""
            }
        }
    }

    LaunchedEffect(isMultiWindow) {
        if (lastObservedMultiWindow != isMultiWindow) {
            lastObservedMultiWindow = isMultiWindow
            rotateChallenge()
        }
        if (!isMultiWindow) {
            lastVisibleChallenge = challenge
            runCatching { keypadFocusRequester.requestFocus() }
        }
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && activity?.isChangingConfigurations != true) {
                rotateChallenge()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isWindowFocused = windowInfo.isWindowFocused
    LaunchedEffect(isWindowFocused) {
        if (isWindowFocused) {
            hadWindowFocus = true
        } else if (hadWindowFocus && activity?.isChangingConfigurations != true) {
            hadWindowFocus = false
            rotateChallenge()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HardenedModeTestTags.CHALLENGE_SECTION)
            .onPreviewKeyEvent { keyEvent ->
                if (isMultiWindow || keyEvent.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                val digit = keyToDigit(keyEvent.key)
                when {
                    digit != null -> {
                        appendDigit(digit)
                        true
                    }
                    keyEvent.key == Key.Backspace -> {
                        if (enteredCode.isNotEmpty()) {
                            enteredCode = enteredCode.dropLast(1)
                            hasError = false
                        }
                        true
                    }
                    keyEvent.key == Key.Delete -> {
                        enteredCode = ""
                        hasError = false
                        true
                    }
                    keyEvent.key == Key.Escape && enteredCode.isNotEmpty() -> {
                        enteredCode = ""
                        hasError = false
                        true
                    }
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                        enteredCode.length == ChallengeResponseVerifier.RESPONSE_CODE_LENGTH -> {
                        submitVerification()
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(keypadFocusRequester)
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isMultiWindow) {
            Text(
                text = stringResource(R.string.challenge_multi_window_blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(HardenedModeTestTags.CHALLENGE_MULTI_WINDOW_NOTICE),
            )
        } else {
            Text(
                text = stringResource(R.string.challenge_code_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(0.42f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.challenge_code_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = challenge,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag(HardenedModeTestTags.CHALLENGE_CODE_VALUE),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.weight(0.58f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.challenge_response_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val formattedResponse = buildString {
                            for (i in 0 until ChallengeResponseVerifier.RESPONSE_CODE_LENGTH) {
                                if (i > 0) append(' ')
                                append(if (i < enteredCode.length) enteredCode[i] else '·')
                            }
                        }
                        Text(
                            text = formattedResponse,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag(HardenedModeTestTags.RESPONSE_CODE_DISPLAY),
                        )
                    }
                }
            }

            if (hasError) {
                Text(
                    text = stringResource(R.string.challenge_error_invalid_code),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(HardenedModeTestTags.CHALLENGE_ERROR_TEXT),
                )
            }

            val digitRows = listOf(
                listOf(1, 2, 3),
                listOf(4, 5, 6),
                listOf(7, 8, 9),
            )
            for (rowDigits in digitRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (digit in rowDigits) {
                        OutlinedButton(
                            onClick = { appendDigit(digit) },
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag(HardenedModeTestTags.keypadDigitTag(digit)),
                        ) {
                            Text(
                                text = digit.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        enteredCode = ""
                        hasError = false
                    },
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(HardenedModeTestTags.KEYPAD_CLEAR),
                ) {
                    Text(
                        text = stringResource(R.string.challenge_keypad_clear),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                OutlinedButton(
                    onClick = { appendDigit(0) },
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(HardenedModeTestTags.keypadDigitTag(0)),
                ) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (enteredCode.isNotEmpty()) {
                            enteredCode = enteredCode.dropLast(1)
                            hasError = false
                        }
                    },
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(HardenedModeTestTags.KEYPAD_BACKSPACE),
                ) {
                    Text(
                        text = stringResource(R.string.challenge_keypad_backspace),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(2.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(if (isMultiWindow) 1f else 0.35f)
                    .heightIn(min = 48.dp)
                    .testTag(cancelButtonTestTag),
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }

            if (!isMultiWindow) {
                Button(
                    onClick = submitVerification,
                    enabled = enteredCode.length == ChallengeResponseVerifier.RESPONSE_CODE_LENGTH,
                    shape = RoundedCornerShape(2.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(0.65f)
                        .heightIn(min = 48.dp)
                        .testTag(confirmButtonTestTag),
                ) {
                    Text(
                        text = confirmButtonText,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberIsInMultiWindowMode(activity: Activity?, override: Boolean?): Boolean {
    var inMultiWindow by remember(activity) {
        mutableStateOf(activity?.isInMultiWindowMode == true)
    }
    DisposableEffect(activity) {
        val provider = activity as? OnMultiWindowModeChangedProvider
        if (provider == null) {
            onDispose {}
        } else {
            inMultiWindow = activity.isInMultiWindowMode
            val listener = Consumer<MultiWindowModeChangedInfo> { info ->
                inMultiWindow = info.isInMultiWindowMode
            }
            provider.addOnMultiWindowModeChangedListener(listener)
            onDispose {
                provider.removeOnMultiWindowModeChangedListener(listener)
            }
        }
    }
    return override ?: inMultiWindow
}

private fun keyToDigit(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun HardenedFeatureRow(title: String, body: String, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}
