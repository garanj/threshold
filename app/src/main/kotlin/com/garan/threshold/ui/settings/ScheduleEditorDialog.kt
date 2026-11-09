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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garan.threshold.core.model.DayOfWeek
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

private sealed interface TimeEditTarget {
    data object UniformStart : TimeEditTarget
    data object UniformEnd : TimeEditTarget
    data class DayStart(val day: DayOfWeek) : TimeEditTarget
    data class DayEnd(val day: DayOfWeek) : TimeEditTarget
}

/**
 * Cohesive Material 3 [ModalBottomSheet] for creating or editing a custom profile's
 * name, active days of the week, and either a shared daily schedule or per-day time windows
 * (supporting overnight schedules that span into the following day).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorSheet(
    initialName: String,
    initialSchedule: ScheduleTrigger,
    isNewProfile: Boolean,
    onSave: (name: String, schedule: ScheduleTrigger) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEngine: HapticEngine = NoOpHapticEngine,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var profileName by remember { mutableStateOf(initialName) }
    var selectedDays by remember { mutableStateOf(initialSchedule.days) }
    var sameScheduleEveryDay by remember { mutableStateOf(initialSchedule.sameScheduleEveryDay) }

    var uniformWindow by remember {
        mutableStateOf(
            TimeWindow(
                startTime = initialSchedule.startTime,
                endTime = initialSchedule.endTime,
            ),
        )
    }

    var perDayWindows by remember {
        mutableStateOf(
            DayOfWeek.entries.associateWith { day ->
                initialSchedule.dailyWindows[day]
                    ?: TimeWindow(initialSchedule.startTime, initialSchedule.endTime)
            },
        )
    }

    var activeTimeEdit by remember { mutableStateOf<TimeEditTarget?>(null) }

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
                text = if (isNewProfile) "New Profile" else "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                label = { Text("Profile name") },
                placeholder = { Text("e.g. Deep Work, Bedtime") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(20.dp))

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
                            selectedDays = if (isSelected) {
                                selectedDays - day
                            } else {
                                selectedDays + day
                            }
                        },
                        label = {
                            Text(
                                text = label,
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
                            sameScheduleEveryDay = useUniform
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
                        .clickable { activeTimeEdit = TimeEditTarget.UniformStart },
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
                        .clickable { activeTimeEdit = TimeEditTarget.UniformEnd },
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
                                        onClick = { activeTimeEdit = TimeEditTarget.DayStart(day) },
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
                                        onClick = { activeTimeEdit = TimeEditTarget.DayEnd(day) },
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

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val resolvedName = profileName.trim().ifBlank { "Custom Profile" }
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
                        onSave(resolvedName, newSchedule)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(4.dp),
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
