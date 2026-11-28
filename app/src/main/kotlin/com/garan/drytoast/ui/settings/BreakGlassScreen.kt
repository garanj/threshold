package com.garan.drytoast.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.garan.drytoast.R
import com.garan.drytoast.core.model.GeneralSettings
import com.garan.drytoast.core.model.NfcTagEntry
import com.garan.drytoast.core.model.NfcTagRegistrationResult
import com.garan.drytoast.core.model.ProfileTrigger
import com.garan.drytoast.core.model.SavedTrigger

object BreakGlassTestTags {
    const val SCREEN = "break_glass_screen"
    const val BACK_BUTTON = "break_glass_back"
    const val ACTIVE_BANNER = "break_glass_active_banner"
    const val RELOCK_NOW_BUTTON = "break_glass_relock_now"
    const val REGISTER_BUTTON = "break_glass_register"
    const val REMOVE_BUTTON = "break_glass_remove"
    const val CANCEL_SCAN_BUTTON = "break_glass_cancel_scan"
    const val SCANNING_PROMPT = "break_glass_scanning_prompt"
    const val ERROR_MESSAGE = "break_glass_error_message"
    const val DURATION_OPTION_PREFIX = "break_glass_duration_"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun BreakGlassScreen(
    breakGlassTagUid: String?,
    breakGlassDurationMinutes: Int,
    breakGlassUntilEpochMillis: Long?,
    savedTriggers: List<SavedTrigger>,
    isSettingsLocked: Boolean,
    onSaveBreakGlassTagUid: (String?) -> Unit,
    onUpdateDurationMinutes: (Int) -> Unit,
    onEndBreakGlassEarly: () -> Unit,
    onSetNfcRegistrationListener: (((NfcTagRegistrationResult) -> Unit)?) -> Unit,
    onBack: () -> Unit,
    nowEpochMillisProvider: () -> Long = { System.currentTimeMillis() },
    modifier: Modifier = Modifier,
) {
    val hideBackArrow = LocalHideSettingsDetailBackArrow.current
    BackHandler(onBack = onBack)

    val nowMillis = nowEpochMillisProvider()
    val remainingMinutes = breakGlassUntilEpochMillis?.let { until ->
        val diff = until - nowMillis
        if (diff > 0L) ((diff + 59_999L) / 60_000L).toInt().coerceAtLeast(1) else 0
    } ?: 0
    val isActive = remainingMinutes > 0

    var isScanning by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val collisionErrorText = stringResource(R.string.break_glass_error_collision)
    val unsupportedErrorText = stringResource(R.string.break_glass_error_unsupported)
    val readOnlyErrorText = stringResource(R.string.break_glass_error_read_only)
    val tooSmallErrorText = stringResource(R.string.break_glass_error_too_small)
    val writeFailedErrorText = stringResource(R.string.break_glass_error_write_failed)

    val existingProfileNfcUids = remember(savedTriggers) {
        savedTriggers.flatMap { saved ->
            (saved.trigger as? ProfileTrigger.Nfc)?.nfc?.tags?.map { it.normalizedUid }.orEmpty()
        }.toSet()
    }

    DisposableEffect(isScanning, existingProfileNfcUids) {
        if (isScanning) {
            onSetNfcRegistrationListener { result ->
                isScanning = false
                when (result) {
                    is NfcTagRegistrationResult.Success -> {
                        val normalized = NfcTagEntry.normalizeUid(result.uid)
                        if (normalized in existingProfileNfcUids) {
                            errorMessage = collisionErrorText
                        } else {
                            errorMessage = null
                            onSaveBreakGlassTagUid(normalized)
                        }
                    }
                    is NfcTagRegistrationResult.UnsupportedCard -> errorMessage = unsupportedErrorText
                    is NfcTagRegistrationResult.ReadOnlyTag -> errorMessage = readOnlyErrorText
                    is NfcTagRegistrationResult.InsufficientCapacity -> errorMessage = tooSmallErrorText
                    is NfcTagRegistrationResult.WriteInterrupted -> errorMessage = writeFailedErrorText
                }
            }
        } else {
            onSetNfcRegistrationListener(null)
        }
        onDispose {
            onSetNfcRegistrationListener(null)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag(BreakGlassTestTags.SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopAppBar(
                    modifier = Modifier
                        .widthIn(max = SETTINGS_MEDIUM_MAX_WIDTH)
                        .fillMaxWidth(),
                    title = {
                        Text(
                            text = stringResource(R.string.break_glass_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        if (!hideBackArrow) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag(BreakGlassTestTags.BACK_BUTTON),
                            ) {
                                Icon(
                                    imageVector = DryToastIcons.ArrowBack,
                                    contentDescription = stringResource(R.string.action_go_back),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
    ) { innerPadding ->
        SettingsContentFrame {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(BreakGlassTestTags.ACTIVE_BANNER),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.break_glass_active_banner_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.break_glass_active_banner_body, remainingMinutes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onEndBreakGlassEarly,
                                modifier = Modifier.testTag(BreakGlassTestTags.RELOCK_NOW_BUTTON),
                            ) {
                                Text(text = stringResource(R.string.break_glass_action_relock_now))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Text(
                    text = stringResource(R.string.break_glass_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )

                if (isSettingsLocked) {
                    Text(
                        text = stringResource(R.string.break_glass_locked_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = stringResource(R.string.break_glass_section_card),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                )

                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(
                                if (breakGlassTagUid != null) {
                                    R.string.break_glass_card_registered_headline
                                } else {
                                    R.string.break_glass_no_card_headline
                                },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (breakGlassTagUid != null) {
                                stringResource(R.string.break_glass_card_uid_format, breakGlassTagUid)
                            } else {
                                stringResource(R.string.break_glass_no_card_supporting)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )

                if (isScanning) {
                    Text(
                        text = stringResource(R.string.break_glass_scanning_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .testTag(BreakGlassTestTags.SCANNING_PROMPT),
                    )
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .testTag(BreakGlassTestTags.ERROR_MESSAGE),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isScanning) {
                        OutlinedButton(
                            onClick = { isScanning = false },
                            modifier = Modifier.testTag(BreakGlassTestTags.CANCEL_SCAN_BUTTON),
                        ) {
                            Text(text = stringResource(R.string.break_glass_action_cancel_scan))
                        }
                    } else {
                        Button(
                            onClick = {
                                errorMessage = null
                                isScanning = true
                            },
                            enabled = !isSettingsLocked,
                            modifier = Modifier.testTag(BreakGlassTestTags.REGISTER_BUTTON),
                        ) {
                            Text(
                                text = stringResource(
                                    if (breakGlassTagUid != null) {
                                        R.string.break_glass_action_replace
                                    } else {
                                        R.string.break_glass_action_register
                                    },
                                ),
                            )
                        }
                        if (breakGlassTagUid != null) {
                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    onSaveBreakGlassTagUid(null)
                                },
                                enabled = !isSettingsLocked,
                                modifier = Modifier.testTag(BreakGlassTestTags.REMOVE_BUTTON),
                            ) {
                                Text(text = stringResource(R.string.break_glass_action_remove))
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Text(
                    text = stringResource(R.string.break_glass_section_duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                )

                Text(
                    text = stringResource(R.string.break_glass_duration_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                val options = GeneralSettings.BREAK_GLASS_DURATION_OPTIONS_MINUTES
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    options.forEachIndexed { index, minutes ->
                        SegmentedButton(
                            selected = breakGlassDurationMinutes == minutes,
                            onClick = { onUpdateDurationMinutes(minutes) },
                            enabled = !isSettingsLocked,
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            modifier = Modifier.testTag(BreakGlassTestTags.DURATION_OPTION_PREFIX + minutes),
                        ) {
                            Text(text = stringResource(R.string.break_glass_duration_option_format, minutes))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
