package com.garan.threshold.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.garan.threshold.core.model.CoordinateParseResult
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.GeoCoordinateParser
import com.garan.threshold.core.model.GeoTrigger
import com.garan.threshold.core.model.GeoTriggerMode
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.NfcTagEntry
import com.garan.threshold.core.model.NfcTrigger
import com.garan.threshold.core.model.NfcTriggerMode
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.model.TimeWindow
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine

private val DAY_ABBREVIATIONS = listOf(
    DayOfWeek.MONDAY to "M",
    DayOfWeek.TUESDAY to "T",
    DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY to "T",
    DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "S",
)

private enum class EditorTriggerKind {
    SCHEDULE,
    GEO,
    NFC,
}

private sealed interface TimeEditTarget {
    data object UniformStart : TimeEditTarget
    data object UniformEnd : TimeEditTarget
    data class DayStart(val day: DayOfWeek) : TimeEditTarget
    data class DayEnd(val day: DayOfWeek) : TimeEditTarget
}

/**
 * Cohesive Material 3 [ModalBottomSheet] for creating or editing a reusable [ProfileTrigger]
 * (Schedule or Geo) with its user-defined name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerEditorSheet(
    initialName: String,
    initialTrigger: ProfileTrigger = ProfileTrigger.Schedule(
        ScheduleTrigger(
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            startTime = TimeOfDay(9, 0),
            endTime = TimeOfDay(17, 0),
        ),
    ),
    isNewTrigger: Boolean,
    onSaveTrigger: (name: String, trigger: ProfileTrigger) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEngine: HapticEngine = NoOpHapticEngine,
    currentLocation: LocationFix? = null,
    onUseCurrentLocation: ((onLocationResolved: (LocationFix) -> Unit) -> Unit)? = null,
    onOpenMapApp: (LocationFix?) -> Unit = {},
    onRegisterNfcScanListener: (((String) -> Unit)?) -> Unit = {},
) {
    ProfileEditorSheet(
        initialName = initialName,
        initialTrigger = initialTrigger,
        isNewProfile = isNewTrigger,
        onSaveTrigger = onSaveTrigger,
        onDismiss = onDismiss,
        modifier = modifier,
        hapticEngine = hapticEngine,
        currentLocation = currentLocation,
        onUseCurrentLocation = onUseCurrentLocation,
        onOpenMapApp = onOpenMapApp,
        onRegisterNfcScanListener = onRegisterNfcScanListener,
        nameFieldLabel = "Trigger name",
        newTitle = "New Trigger",
        editTitle = "Edit Trigger",
        editGeoTitle = "Edit Location Trigger",
        editNfcTitle = "Edit NFC Trigger",
        typeSectionLabel = "TRIGGER TYPE",
        scheduleDefaultName = "Schedule Trigger",
        geoDefaultName = "Geo Trigger",
        nfcDefaultName = "NFC Trigger",
    )
}

/**
 * Cohesive Material 3 [ModalBottomSheet] for creating or editing a custom profile's
 * name and either a temporal [ScheduleTrigger], spatial [GeoTrigger], or physical [NfcTrigger].
 *
 * When [isNewProfile] is `true`, a Material 3 [SingleChoiceSegmentedButtonRow] (`Schedule | Geo | NFC`)
 * lets the user choose the trigger type. When editing an existing trigger, the editor opens
 * directly to that trigger's dedicated Schedule, Location, or NFC configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorSheet(
    initialName: String,
    initialSchedule: ScheduleTrigger = ScheduleTrigger(
        days = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        ),
        startTime = TimeOfDay(9, 0),
        endTime = TimeOfDay(17, 0),
    ),
    isNewProfile: Boolean,
    onSave: (name: String, schedule: ScheduleTrigger) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEngine: HapticEngine = NoOpHapticEngine,
    initialTrigger: ProfileTrigger? = ProfileTrigger.Schedule(initialSchedule),
    currentLocation: LocationFix? = null,
    onUseCurrentLocation: ((onLocationResolved: (LocationFix) -> Unit) -> Unit)? = null,
    onOpenMapApp: (LocationFix?) -> Unit = {},
    onRegisterNfcScanListener: (((String) -> Unit)?) -> Unit = {},
    onSaveTrigger: ((name: String, trigger: ProfileTrigger) -> Unit)? = null,
    nameFieldLabel: String = "Profile name",
    newTitle: String = "New Profile",
    editTitle: String = "Edit Profile",
    editGeoTitle: String = "Edit Location Profile",
    editNfcTitle: String = "Edit NFC Profile",
    typeSectionLabel: String = "PROFILE TYPE",
    scheduleDefaultName: String = "Custom Profile",
    geoDefaultName: String = "Geo Profile",
    nfcDefaultName: String = "NFC Profile",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var profileName by remember { mutableStateOf(initialName) }

    var selectedKind by remember {
        mutableStateOf(
            when (initialTrigger) {
                is ProfileTrigger.Geo -> EditorTriggerKind.GEO
                is ProfileTrigger.Nfc -> EditorTriggerKind.NFC
                is ProfileTrigger.Schedule, null -> EditorTriggerKind.SCHEDULE
            },
        )
    }

    // Schedule state
    val seedSchedule = (initialTrigger as? ProfileTrigger.Schedule)?.schedule ?: initialSchedule
    var selectedDays by remember { mutableStateOf(seedSchedule.days) }
    var sameScheduleEveryDay by remember { mutableStateOf(seedSchedule.sameScheduleEveryDay) }

    var uniformWindow by remember {
        mutableStateOf(
            TimeWindow(
                startTime = seedSchedule.startTime,
                endTime = seedSchedule.endTime,
            ),
        )
    }

    var perDayWindows by remember {
        mutableStateOf(
            DayOfWeek.entries.associateWith { day ->
                seedSchedule.dailyWindows[day]
                    ?: TimeWindow(seedSchedule.startTime, seedSchedule.endTime)
            },
        )
    }

    var activeTimeEdit by remember { mutableStateOf<TimeEditTarget?>(null) }

    // Geo state
    val seedGeo = (initialTrigger as? ProfileTrigger.Geo)?.geo
    var geoMode by remember { mutableStateOf(seedGeo?.mode ?: GeoTriggerMode.INSIDE) }
    var geoRadiusMeters by remember { mutableIntStateOf(seedGeo?.radiusMeters ?: GeoTrigger.DEFAULT_RADIUS_METERS) }
    var coordinateInput by remember {
        mutableStateOf(
            when {
                seedGeo != null -> seedGeo.center.formattedCoordinates()
                currentLocation != null -> currentLocation.formattedCoordinates()
                else -> ""
            },
        )
    }

    val parseResult = remember(coordinateInput) {
        GeoCoordinateParser.parse(coordinateInput)
    }
    val parsedFix = (parseResult as? CoordinateParseResult.Success)?.location

    // NFC state
    val seedNfc = (initialTrigger as? ProfileTrigger.Nfc)?.nfc
    var nfcMode by remember { mutableStateOf(seedNfc?.mode ?: NfcTriggerMode.TOGGLE_ON_TAP) }
    var nfcDurationMinutes by remember {
        mutableIntStateOf(seedNfc?.durationMinutes ?: NfcTrigger.DEFAULT_DURATION_MINUTES)
    }
    var nfcTags by remember { mutableStateOf(seedNfc?.tags ?: emptyList()) }

    DisposableEffect(selectedKind, onRegisterNfcScanListener) {
        if (selectedKind == EditorTriggerKind.NFC) {
            onRegisterNfcScanListener { rawUid ->
                val normalized = NfcTagEntry.normalizeUid(rawUid)
                if (normalized.isNotEmpty() && nfcTags.none { it.uid == normalized }) {
                    nfcTags = nfcTags + NfcTagEntry(uid = normalized)
                }
            }
        } else {
            onRegisterNfcScanListener(null)
        }
        onDispose {
            onRegisterNfcScanListener(null)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = when {
                    isNewProfile -> newTitle
                    selectedKind == EditorTriggerKind.GEO -> editGeoTitle
                    selectedKind == EditorTriggerKind.NFC -> editNfcTitle
                    else -> editTitle
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                label = { Text(nameFieldLabel) },
                placeholder = {
                    Text(
                        when (selectedKind) {
                            EditorTriggerKind.GEO -> "e.g. Office, Home"
                            EditorTriggerKind.NFC -> "e.g. Bedside Tag, Desk Tag"
                            EditorTriggerKind.SCHEDULE -> "e.g. Deep Work, Bedtime"
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfilesTestTags.TRIGGER_EDITOR_NAME_INPUT),
            )

            if (isNewProfile) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = typeSectionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedKind == EditorTriggerKind.SCHEDULE,
                        onClick = {
                            if (selectedKind != EditorTriggerKind.SCHEDULE) {
                                hapticEngine.rotaryDetent()
                                selectedKind = EditorTriggerKind.SCHEDULE
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        modifier = Modifier.testTag(ProfilesTestTags.TRIGGER_EDITOR_TYPE_SCHEDULE),
                        icon = {
                            Icon(
                                imageVector = ThresholdIcons.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) {
                        Text("Schedule")
                    }

                    SegmentedButton(
                        selected = selectedKind == EditorTriggerKind.GEO,
                        onClick = {
                            if (selectedKind != EditorTriggerKind.GEO) {
                                hapticEngine.rotaryDetent()
                                selectedKind = EditorTriggerKind.GEO
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        modifier = Modifier.testTag(ProfilesTestTags.TRIGGER_EDITOR_TYPE_GEO),
                        icon = {
                            Icon(
                                imageVector = ThresholdIcons.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) {
                        Text("Geo")
                    }

                    SegmentedButton(
                        selected = selectedKind == EditorTriggerKind.NFC,
                        onClick = {
                            if (selectedKind != EditorTriggerKind.NFC) {
                                hapticEngine.rotaryDetent()
                                selectedKind = EditorTriggerKind.NFC
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        modifier = Modifier.testTag(ProfilesTestTags.TRIGGER_EDITOR_TYPE_NFC),
                        icon = {
                            Icon(
                                imageVector = ThresholdIcons.Nfc,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) {
                        Text("NFC")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (selectedKind) {
                EditorTriggerKind.SCHEDULE -> {
                    ScheduleEditorFields(
                        selectedDays = selectedDays,
                        onSelectedDaysChange = { selectedDays = it },
                        sameScheduleEveryDay = sameScheduleEveryDay,
                        onSameScheduleEveryDayChange = { sameScheduleEveryDay = it },
                        uniformWindow = uniformWindow,
                        perDayWindows = perDayWindows,
                        onSelectTimeEdit = { activeTimeEdit = it },
                        hapticEngine = hapticEngine,
                    )
                }

                EditorTriggerKind.GEO -> {
                    GeoEditorFields(
                        geoMode = geoMode,
                        onGeoModeChange = {
                            hapticEngine.rotaryDetent()
                            geoMode = it
                        },
                        geoRadiusMeters = geoRadiusMeters,
                        onGeoRadiusChange = {
                            hapticEngine.rotaryDetent()
                            geoRadiusMeters = it
                        },
                        coordinateInput = coordinateInput,
                        onCoordinateInputChange = { coordinateInput = it },
                        parseResult = parseResult,
                        currentLocation = currentLocation,
                        onUseCurrentLocationClick = {
                            hapticEngine.rotaryDetent()
                            if (onUseCurrentLocation != null) {
                                onUseCurrentLocation { resolvedFix ->
                                    coordinateInput = resolvedFix.formattedCoordinates()
                                }
                            } else if (currentLocation != null) {
                                coordinateInput = currentLocation.formattedCoordinates()
                            }
                        },
                        onOpenMapAppClick = {
                            hapticEngine.rotaryDetent()
                            onOpenMapApp(parsedFix ?: currentLocation)
                        },
                    )
                }

                EditorTriggerKind.NFC -> {
                    NfcEditorFields(
                        nfcMode = nfcMode,
                        onNfcModeChange = {
                            hapticEngine.rotaryDetent()
                            nfcMode = it
                        },
                        nfcDurationMinutes = nfcDurationMinutes,
                        onNfcDurationMinutesChange = {
                            hapticEngine.rotaryDetent()
                            nfcDurationMinutes = it
                        },
                        nfcTags = nfcTags,
                        onTagLabelChange = { uid, updatedLabel ->
                            nfcTags = nfcTags.map { entry ->
                                if (entry.uid == uid) entry.copy(label = updatedLabel) else entry
                            }
                        },
                        onRemoveTag = { uid ->
                            hapticEngine.rotaryDetent()
                            nfcTags = nfcTags.filterNot { it.uid == uid }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val canSubmit = when (selectedKind) {
                EditorTriggerKind.SCHEDULE -> selectedDays.isNotEmpty()
                EditorTriggerKind.GEO -> parsedFix != null
                EditorTriggerKind.NFC -> nfcTags.isNotEmpty()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(ProfilesTestTags.TRIGGER_EDITOR_CANCEL_BUTTON),
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val defaultFallbackName = when (selectedKind) {
                            EditorTriggerKind.GEO -> geoDefaultName
                            EditorTriggerKind.NFC -> nfcDefaultName
                            EditorTriggerKind.SCHEDULE -> scheduleDefaultName
                        }
                        val resolvedName = profileName.trim().ifBlank { defaultFallbackName }

                        when (selectedKind) {
                            EditorTriggerKind.SCHEDULE -> {
                                val dailyMap = selectedDays.associateWith { day ->
                                    if (sameScheduleEveryDay) {
                                        uniformWindow
                                    } else {
                                        perDayWindows[day] ?: uniformWindow
                                    }
                                }
                                val newSchedule = ScheduleTrigger(
                                    dailyWindows = dailyMap,
                                    sameScheduleEveryDay = sameScheduleEveryDay,
                                )
                                if (onSaveTrigger != null) {
                                    onSaveTrigger(resolvedName, ProfileTrigger.Schedule(newSchedule))
                                } else {
                                    onSave(resolvedName, newSchedule)
                                }
                                onDismiss()
                            }

                            EditorTriggerKind.GEO -> {
                                val targetLocation = parsedFix ?: return@Button
                                val newGeo = GeoTrigger(
                                    latitude = targetLocation.latitude,
                                    longitude = targetLocation.longitude,
                                    radiusMeters = geoRadiusMeters,
                                    mode = geoMode,
                                )
                                onSaveTrigger?.invoke(resolvedName, ProfileTrigger.Geo(newGeo))
                                onDismiss()
                            }

                            EditorTriggerKind.NFC -> {
                                if (nfcTags.isEmpty()) return@Button
                                val newNfc = NfcTrigger(
                                    tags = nfcTags.map { it.copy(label = it.label.trim()) },
                                    mode = nfcMode,
                                    durationMinutes = nfcDurationMinutes,
                                )
                                onSaveTrigger?.invoke(resolvedName, ProfileTrigger.Nfc(newNfc))
                                onDismiss()
                            }
                        }
                    },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag(ProfilesTestTags.TRIGGER_EDITOR_SAVE_BUTTON),
                ) {
                    Text(if (isNewProfile) "Create" else "Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    when (val target = activeTimeEdit) {
        is TimeEditTarget.UniformStart -> {
            ThresholdTimeInputDialog(
                title = "Start time",
                initialHour = uniformWindow.startTime.hour,
                initialMinute = uniformWindow.startTime.minute,
                onConfirm = { h, m ->
                    val updated = uniformWindow.copy(startTime = TimeOfDay(h, m))
                    uniformWindow = updated
                    perDayWindows = DayOfWeek.entries.associateWith { updated }
                    activeTimeEdit = null
                },
                onDismiss = { activeTimeEdit = null },
            )
        }
        is TimeEditTarget.UniformEnd -> {
            ThresholdTimeInputDialog(
                title = "End time",
                initialHour = uniformWindow.endTime.hour,
                initialMinute = uniformWindow.endTime.minute,
                onConfirm = { h, m ->
                    val updated = uniformWindow.copy(endTime = TimeOfDay(h, m))
                    uniformWindow = updated
                    perDayWindows = DayOfWeek.entries.associateWith { updated }
                    activeTimeEdit = null
                },
                onDismiss = { activeTimeEdit = null },
            )
        }
        is TimeEditTarget.DayStart -> {
            val currentWindow = perDayWindows[target.day] ?: uniformWindow
            ThresholdTimeInputDialog(
                title = "${target.day.displayName()} start time",
                initialHour = currentWindow.startTime.hour,
                initialMinute = currentWindow.startTime.minute,
                onConfirm = { h, m ->
                    val updatedWindow = currentWindow.copy(startTime = TimeOfDay(h, m))
                    perDayWindows = perDayWindows + (target.day to updatedWindow)
                    activeTimeEdit = null
                },
                onDismiss = { activeTimeEdit = null },
            )
        }
        is TimeEditTarget.DayEnd -> {
            val currentWindow = perDayWindows[target.day] ?: uniformWindow
            ThresholdTimeInputDialog(
                title = "${target.day.displayName()} end time",
                initialHour = currentWindow.endTime.hour,
                initialMinute = currentWindow.endTime.minute,
                onConfirm = { h, m ->
                    val updatedWindow = currentWindow.copy(endTime = TimeOfDay(h, m))
                    perDayWindows = perDayWindows + (target.day to updatedWindow)
                    activeTimeEdit = null
                },
                onDismiss = { activeTimeEdit = null },
            )
        }
        null -> Unit
    }
}

@Composable
private fun ScheduleEditorFields(
    selectedDays: Set<DayOfWeek>,
    onSelectedDaysChange: (Set<DayOfWeek>) -> Unit,
    sameScheduleEveryDay: Boolean,
    onSameScheduleEveryDayChange: (Boolean) -> Unit,
    uniformWindow: TimeWindow,
    perDayWindows: Map<DayOfWeek, TimeWindow>,
    onSelectTimeEdit: (TimeEditTarget) -> Unit,
    hapticEngine: HapticEngine,
) {
    Text(
        text = "ACTIVE DAYS",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DAY_ABBREVIATIONS.forEach { (day, label) ->
            val isSelected = day in selectedDays
            FilterChip(
                selected = isSelected,
                onClick = {
                    hapticEngine.rotaryDetent()
                    onSelectedDaysChange(
                        if (isSelected) selectedDays - day else selectedDays + day,
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(ProfilesTestTags.triggerEditorScheduleDay(day)),
                shape = RoundedCornerShape(4.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    ListItem(
        headlineContent = { Text("Same schedule every day") },
        supportingContent = { Text("Use the same start and end time across all active days") },
        trailingContent = {
            Switch(
                checked = sameScheduleEveryDay,
                onCheckedChange = null,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = sameScheduleEveryDay,
                role = Role.Switch,
                onValueChange = { useUniform ->
                    hapticEngine.rotaryDetent()
                    onSameScheduleEveryDayChange(useUniform)
                },
            ),
    )

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
    )

    if (sameScheduleEveryDay) {
        val startFormatted = uniformWindow.startTime.formatted()
        val endFormatted = uniformWindow.endTime.formatted()

        ListItem(
            leadingContent = {
                Icon(
                    imageVector = ThresholdIcons.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            headlineContent = { Text("Start time") },
            trailingContent = {
                Text(
                    text = startFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectTimeEdit(TimeEditTarget.UniformStart) },
        )

        ListItem(
            leadingContent = {
                Icon(
                    imageVector = ThresholdIcons.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            headlineContent = { Text("End time") },
            supportingContent = if (uniformWindow.isOvernight) {
                { Text("Ends at $endFormatted the following day") }
            } else {
                null
            },
            trailingContent = {
                Text(
                    text = endFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectTimeEdit(TimeEditTarget.UniformEnd) },
        )
    } else {
        val orderedSelectedDays = DayOfWeek.entries.filter { it in selectedDays }
        if (orderedSelectedDays.isEmpty()) {
            Text(
                text = "Select at least one active day above to configure daily times.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            orderedSelectedDays.forEachIndexed { index, day ->
                val dayWindow = perDayWindows[day] ?: uniformWindow
                val nextDayShort = day.nextDay().shortName()
                val dayShort = day.shortName()

                ListItem(
                    headlineContent = {
                        Text(
                            text = day.displayName(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    supportingContent = if (dayWindow.isOvernight) {
                        {
                            val spanText = "${dayWindow.startTime.formatted()} $dayShort – " +
                                "${dayWindow.endTime.formatted()} $nextDayShort (next day)"
                            Text(
                                text = spanText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onSelectTimeEdit(TimeEditTarget.DayStart(day)) },
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = dayWindow.startTime.formatted(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            Text(
                                text = "–",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedButton(
                                onClick = { onSelectTimeEdit(TimeEditTarget.DayEnd(day)) },
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = dayWindow.endTime.formatted(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (index < orderedSelectedDays.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeoEditorFields(
    geoMode: GeoTriggerMode,
    onGeoModeChange: (GeoTriggerMode) -> Unit,
    geoRadiusMeters: Int,
    onGeoRadiusChange: (Int) -> Unit,
    coordinateInput: String,
    onCoordinateInputChange: (String) -> Unit,
    parseResult: CoordinateParseResult,
    currentLocation: LocationFix?,
    onUseCurrentLocationClick: () -> Unit,
    onOpenMapAppClick: () -> Unit,
) {
    Text(
        text = "TRIGGER MODE",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(8.dp))

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = geoMode == GeoTriggerMode.INSIDE,
            onClick = { onGeoModeChange(GeoTriggerMode.INSIDE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("Inside zone")
        }

        SegmentedButton(
            selected = geoMode == GeoTriggerMode.OUTSIDE,
            onClick = { onGeoModeChange(GeoTriggerMode.OUTSIDE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("Outside zone")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "ZONE RADIUS",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GeoTrigger.PRESET_RADII.forEach { presetMeters ->
            val isSelected = geoRadiusMeters == presetMeters
            FilterChip(
                selected = isSelected,
                onClick = { onGeoRadiusChange(presetMeters) },
                label = {
                    Text(
                        text = GeoTrigger.formatRadius(presetMeters),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(4.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "CENTER POINT",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onUseCurrentLocationClick,
            modifier = Modifier
                .weight(1.2f)
                .height(48.dp),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = ThresholdIcons.MyLocation,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Use Current Location",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }

        OutlinedButton(
            onClick = onOpenMapAppClick,
            modifier = Modifier
                .weight(0.8f)
                .height(48.dp),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = ThresholdIcons.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Open Map",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    val isError = coordinateInput.isNotBlank() && parseResult !is CoordinateParseResult.Success
    val supportingMessage = when (parseResult) {
        is CoordinateParseResult.UrlNotSupported ->
            "Short links require internet. Long-press a pin in your map app to copy coordinates (lat, lng)."
        is CoordinateParseResult.Invalid -> {
            if (coordinateInput.isBlank()) {
                "Tip: Long-press any spot in your map app and tap the coordinates to copy"
            } else {
                "Enter valid coordinates (e.g. 51.5074, -0.1278)"
            }
        }
        is CoordinateParseResult.Success -> {
            if (currentLocation != null) {
                val dist = parseResult.location.distanceMetersTo(currentLocation)
                "Valid coordinates · ${GeoTrigger.formatDistance(dist)} from current location"
            } else {
                "Valid coordinates (${parseResult.location.formattedCoordinates()})"
            }
        }
    }

    OutlinedTextField(
        value = coordinateInput,
        onValueChange = onCoordinateInputChange,
        label = { Text("Coordinates (lat, lng)") },
        placeholder = { Text("e.g. 51.50740, -0.12780") },
        singleLine = true,
        isError = isError,
        supportingText = {
            Text(text = supportingMessage)
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NfcEditorFields(
    nfcMode: NfcTriggerMode,
    onNfcModeChange: (NfcTriggerMode) -> Unit,
    nfcDurationMinutes: Int,
    onNfcDurationMinutesChange: (Int) -> Unit,
    nfcTags: List<NfcTagEntry>,
    onTagLabelChange: (uid: String, label: String) -> Unit,
    onRemoveTag: (uid: String) -> Unit,
) {
    Text(
        text = "ACTIVATION MODE",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(8.dp))

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = nfcMode == NfcTriggerMode.TOGGLE_ON_TAP,
            onClick = { onNfcModeChange(NfcTriggerMode.TOGGLE_ON_TAP) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag(ProfilesTestTags.TRIGGER_EDITOR_NFC_MODE_TOGGLE),
        ) {
            Text("Toggle on tap")
        }

        SegmentedButton(
            selected = nfcMode == NfcTriggerMode.TIMED_SESSION,
            onClick = { onNfcModeChange(NfcTriggerMode.TIMED_SESSION) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag(ProfilesTestTags.TRIGGER_EDITOR_NFC_MODE_TIMED),
        ) {
            Text("Timed session")
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = when (nfcMode) {
            NfcTriggerMode.TOGGLE_ON_TAP ->
                "Tap a registered tag to latch this trigger ON; tap again to turn OFF."
            NfcTriggerMode.TIMED_SESSION ->
                "Tap a registered tag to activate for the selected duration, or tap again early to turn OFF."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (nfcMode == NfcTriggerMode.TIMED_SESSION) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SESSION DURATION",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NfcTrigger.DURATION_PRESETS_MINUTES.forEach { presetMinutes ->
                val isSelected = nfcDurationMinutes == presetMinutes
                FilterChip(
                    selected = isSelected,
                    onClick = { onNfcDurationMinutesChange(presetMinutes) },
                    label = {
                        Text(
                            text = NfcTrigger.formatDuration(presetMinutes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ProfilesTestTags.triggerEditorNfcDuration(presetMinutes)),
                    shape = RoundedCornerShape(4.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "REGISTERED TAGS",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(8.dp))

    ListItem(
        leadingContent = {
            Icon(
                imageVector = ThresholdIcons.Nfc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        },
        headlineContent = {
            Text(
                text = "Hold an NFC tag against your phone",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = if (nfcTags.isEmpty()) {
                    "Scan at least one NFC tag to create this trigger."
                } else {
                    "Scan additional NFC tags to bind them to this same trigger."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    if (nfcTags.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))

        nfcTags.forEachIndexed { index, tagEntry ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = tagEntry.label,
                    onValueChange = { onTagLabelChange(tagEntry.uid, it) },
                    label = { Text("Tag ${tagEntry.uid}") },
                    placeholder = { Text("Optional label (e.g. Bedside)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ProfilesTestTags.triggerEditorNfcTagLabel(tagEntry.uid)),
                )

                IconButton(
                    onClick = { onRemoveTag(tagEntry.uid) },
                    modifier = Modifier.testTag(ProfilesTestTags.triggerEditorNfcTagRemove(tagEntry.uid)),
                ) {
                    Icon(
                        imageVector = ThresholdIcons.Delete,
                        contentDescription = "Remove NFC tag ${tagEntry.uid}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun DayOfWeek.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun DayOfWeek.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThresholdTimeInputDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("OK", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
