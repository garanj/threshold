package com.garan.threshold.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.garan.threshold.core.model.GeoTrigger
import com.garan.threshold.core.model.GeoTriggerMode
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.NfcTagEntry
import com.garan.threshold.core.model.NfcTrigger
import com.garan.threshold.core.model.NfcTriggerMode
import com.garan.threshold.core.model.TimeWindow
import com.garan.threshold.core.port.HapticEngine

private val DAY_ABBREVIATIONS = listOf(
    DayOfWeek.MONDAY to "M",
    DayOfWeek.TUESDAY to "T",
    DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY to "T",
    DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "S",
)

@Composable
internal fun ScheduleEditorFields(
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
internal fun GeoEditorFields(
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
internal fun NfcEditorFields(
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
