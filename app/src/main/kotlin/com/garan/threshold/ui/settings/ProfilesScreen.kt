package com.garan.threshold.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import java.util.UUID

private val AmoledBlack = Color(0xFF000000)
private val CardBackground = Color(0xFF121212)
private val HairlineBorder = Color(0xFF222222)
private val PureWhite = Color(0xFFFFFFFF)
private val SubtleGray = Color(0xFF888888)

/**
 * Screen displaying the user-ordered list of custom profiles with drag-to-reorder,
 * trigger configuration, and app overrides.
 */
@Composable
fun ProfilesScreen(
    profiles: List<CustomProfile>,
    onSaveProfile: (CustomProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onReorderProfiles: (List<CustomProfile>) -> Unit,
    onConfigureProfileApps: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEngine: HapticEngine = NoOpHapticEngine,
) {
    BackHandler(onBack = onBack)

    var editingScheduleProfile by remember { mutableStateOf<CustomProfile?>(null) }
    var renamingProfile by remember { mutableStateOf<CustomProfile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = ThresholdIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = PureWhite,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Profiles",
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhite,
                )
                Text(
                    text = "Top active profile takes precedence",
                    style = MaterialTheme.typography.labelSmall,
                    color = SubtleGray,
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (profiles.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No custom profiles yet.\nAdd one to schedule focused app restrictions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SubtleGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = profiles,
                    key = { _, profile -> profile.id },
                ) { index, profile ->
                    ProfileCard(
                        profile = profile,
                        index = index,
                        totalProfiles = profiles.size,
                        onMoveUp = {
                            if (index > 0) {
                                hapticEngine.rotaryDetent()
                                val list = profiles.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                onReorderProfiles(list)
                            }
                        },
                        onMoveDown = {
                            if (index < profiles.size - 1) {
                                hapticEngine.rotaryDetent()
                                val list = profiles.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                onReorderProfiles(list)
                            }
                        },
                        onToggleEnabled = { enabled ->
                            hapticEngine.rotaryDetent()
                            onSaveProfile(profile.copy(enabled = enabled))
                        },
                        onEditName = { renamingProfile = profile },
                        onEditSchedule = { editingScheduleProfile = profile },
                        onConfigureApps = { onConfigureProfileApps(profile.id) },
                        onDelete = {
                            hapticEngine.rotaryDetent()
                            onDeleteProfile(profile.id)
                        },
                    )
                }
            }
        }

        // Add Profile Footer Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Button(
                onClick = {
                    hapticEngine.rotaryDetent()
                    showCreateDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureWhite,
                    contentColor = AmoledBlack,
                ),
            ) {
                Icon(
                    imageVector = ThresholdIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Profile",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }

    // Schedule Editor Dialog
    editingScheduleProfile?.let { profile ->
        val trigger = (profile.trigger as? ProfileTrigger.Schedule)?.schedule ?: ScheduleTrigger()
        ScheduleEditorDialog(
            initialSchedule = trigger,
            onSave = { updatedSchedule ->
                onSaveProfile(profile.copy(trigger = ProfileTrigger.Schedule(updatedSchedule)))
            },
            onDismiss = { editingScheduleProfile = null },
            hapticEngine = hapticEngine,
        )
    }

    // Rename Profile Dialog
    renamingProfile?.let { profile ->
        var newName by remember { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { renamingProfile = null },
            title = { Text(text = "Rename Profile", color = PureWhite) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = PureWhite,
                        unfocusedBorderColor = HairlineBorder,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotBlank()) {
                            onSaveProfile(profile.copy(name = trimmed))
                        }
                        renamingProfile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = AmoledBlack),
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingProfile = null }) {
                    Text("Cancel", color = SubtleGray)
                }
            },
            containerColor = CardBackground,
        )
    }

    // Create Profile Dialog
    if (showCreateDialog) {
        var profileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(text = "New Profile Name", color = PureWhite) },
            text = {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    placeholder = { Text("e.g. Deep Work, Bedtime", color = SubtleGray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = PureWhite,
                        unfocusedBorderColor = HairlineBorder,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = profileName.trim()
                        if (trimmed.isNotBlank()) {
                            val newProfile = CustomProfile(
                                id = UUID.randomUUID().toString(),
                                name = trimmed,
                                priority = profiles.size,
                                trigger = ProfileTrigger.Schedule(
                                    ScheduleTrigger(
                                        days = setOf(
                                            DayOfWeek.MONDAY,
                                            DayOfWeek.TUESDAY,
                                            DayOfWeek.WEDNESDAY,
                                            DayOfWeek.THURSDAY,
                                            DayOfWeek.FRIDAY,
                                        ),
                                        startTime = com.garan.threshold.core.model.TimeOfDay(9, 0),
                                        endTime = com.garan.threshold.core.model.TimeOfDay(17, 0),
                                    ),
                                ),
                            )
                            onSaveProfile(newProfile)
                        }
                        showCreateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = AmoledBlack),
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SubtleGray)
                }
            },
            containerColor = CardBackground,
        )
    }
}

/**
 * Full-width profile card with reorder controls, trigger summary, and configuration actions.
 */
@Composable
private fun ProfileCard(
    profile: CustomProfile,
    index: Int,
    totalProfiles: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEditName: () -> Unit,
    onEditSchedule: () -> Unit,
    onConfigureApps: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, HairlineBorder, RoundedCornerShape(4.dp)),
        color = CardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Top Row: Reorder grip, Name, Type icon, Active Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Up / Down Steppers for Priority Order
                Column(
                    modifier = Modifier.padding(end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(enabled = index > 0) { onMoveUp() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "▲",
                            color = if (index > 0) PureWhite else Color(0xFF333333),
                            fontSize = 11.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(enabled = index < totalProfiles - 1) { onMoveDown() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "▼",
                            color = if (index < totalProfiles - 1) PureWhite else Color(0xFF333333),
                            fontSize = 11.sp,
                        )
                    }
                }

                // Profile Name
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEditName() },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (profile.enabled) PureWhite else SubtleGray,
                        )
                        Text(
                            text = " #${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SubtleGray,
                        )
                    }
                }

                // Trigger Type Icon (Clock for Schedule)
                Icon(
                    imageVector = ThresholdIcons.Schedule,
                    contentDescription = "Scheduled Profile",
                    tint = if (profile.enabled) PureWhite else SubtleGray,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp),
                )

                // Enable/Disable Switch
                Switch(
                    checked = profile.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AmoledBlack,
                        checkedTrackColor = PureWhite,
                        uncheckedThumbColor = SubtleGray,
                        uncheckedTrackColor = CardBackground,
                        uncheckedBorderColor = HairlineBorder,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary Section
            val schedule = (profile.trigger as? ProfileTrigger.Schedule)?.schedule
            val scheduleSummary = if (schedule != null) {
                formatScheduleSummary(schedule)
            } else {
                "No schedule set"
            }

            val blockedCount = profile.appAssignments.count { it.value is AppAssignment.Blocked }
            val guardedCount = profile.appAssignments.count { it.value is AppAssignment.Guarded }
            val allowCount = profile.appAssignments.count { it.value is AppAssignment.Standard }
            val totalOverrides = blockedCount + guardedCount + allowCount
            val appSummary = if (totalOverrides == 0) {
                "All apps inherit default behavior"
            } else {
                "$blockedCount Blocked · $guardedCount Guarded · $allowCount Allow"
            }

            Text(
                text = scheduleSummary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (profile.enabled) PureWhite else SubtleGray,
            )
            Text(
                text = appSummary,
                style = MaterialTheme.typography.labelSmall,
                color = SubtleGray,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row: [ Schedule ] [ Configure Apps ] [ Delete ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Schedule Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AmoledBlack)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(2.dp))
                        .clickable { onEditSchedule() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Schedule",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PureWhite,
                    )
                }

                // Configure Apps Button
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AmoledBlack)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(2.dp))
                        .clickable { onConfigureApps() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Configure Apps",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PureWhite,
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = ThresholdIcons.Delete,
                        contentDescription = "Delete Profile",
                        tint = SubtleGray,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun formatScheduleSummary(schedule: ScheduleTrigger): String {
    if (schedule.days.isEmpty()) return "Inactive (No days selected)"

    val daysText = if (schedule.days.size == 7) {
        "Every day"
    } else if (schedule.days ==
        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    ) {
        "Weekdays"
    } else if (schedule.days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
        "Weekends"
    } else {
        schedule.days.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
    }

    return if (schedule.isAllDay) {
        "$daysText · All day"
    } else {
        "$daysText · ${schedule.startTime.formatted()} – ${schedule.endTime.formatted()}"
    }
}
