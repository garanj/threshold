package com.garan.threshold.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.ui.theme.profileBaseColor
import java.util.UUID

/**
 * Screen displaying the user-ordered list of custom profiles with priority reordering,
 * schedule configuration via [ProfileEditorSheet], and app overrides.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    canScheduleExactAlarms: () -> Boolean = { true },
    onRequestExactAlarmPermission: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var editingProfile by remember { mutableStateOf<CustomProfile?>(null) }
    var isCreatingNewProfile by remember { mutableStateOf(false) }
    var showExactAlarmPermissionSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Profiles",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "Top active profile takes precedence",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = ThresholdIcons.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                hapticEngine.rotaryDetent()
                                isCreatingNewProfile = true
                            },
                        ) {
                            Icon(
                                imageVector = ThresholdIcons.Add,
                                contentDescription = "Add Profile",
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
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
                            if (enabled && profile.trigger is ProfileTrigger.Schedule && !canScheduleExactAlarms()) {
                                showExactAlarmPermissionSheet = true
                            }
                        },
                        onEditProfile = { editingProfile = profile },
                        onConfigureApps = { onConfigureProfileApps(profile.id) },
                        onDelete = {
                            hapticEngine.rotaryDetent()
                            onDeleteProfile(profile.id)
                        },
                    )
                }
            }

            item(key = "add_profile_row") {
                OutlinedButton(
                    onClick = {
                        hapticEngine.rotaryDetent()
                        isCreatingNewProfile = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Icon(
                        imageVector = ThresholdIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Profile",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    // Edit Existing Profile Sheet
    editingProfile?.let { profile ->
        val trigger = (profile.trigger as? ProfileTrigger.Schedule)?.schedule ?: ScheduleTrigger()
        ProfileEditorSheet(
            initialName = profile.name,
            initialSchedule = trigger,
            isNewProfile = false,
            onSave = { updatedName, updatedSchedule ->
                onSaveProfile(
                    profile.copy(
                        name = updatedName,
                        trigger = ProfileTrigger.Schedule(updatedSchedule),
                    ),
                )
                if (profile.enabled && !canScheduleExactAlarms()) {
                    showExactAlarmPermissionSheet = true
                }
            },
            onDismiss = { editingProfile = null },
            hapticEngine = hapticEngine,
        )
    }

    // Create New Profile Sheet
    if (isCreatingNewProfile) {
        val defaultSchedule = ScheduleTrigger(
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            startTime = TimeOfDay(9, 0),
            endTime = TimeOfDay(17, 0),
        )
        ProfileEditorSheet(
            initialName = "",
            initialSchedule = defaultSchedule,
            isNewProfile = true,
            onSave = { name, schedule ->
                val newProfile = CustomProfile(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    priority = profiles.size,
                    trigger = ProfileTrigger.Schedule(schedule),
                    colorIndex = CustomProfile.nextColorIndex(profiles),
                )
                onSaveProfile(newProfile)
                if (!canScheduleExactAlarms()) {
                    showExactAlarmPermissionSheet = true
                }
            },
            onDismiss = { isCreatingNewProfile = false },
            hapticEngine = hapticEngine,
        )
    }

    if (showExactAlarmPermissionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExactAlarmPermissionSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "Allow exact schedule alarms",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To activate and end scheduled profiles at the exact time you set, " +
                        "grant Threshold the Alarms & reminders permission in system settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showExactAlarmPermissionSheet = false }) {
                        Text("Not now")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showExactAlarmPermissionSheet = false
                            onRequestExactAlarmPermission()
                        },
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text("Open Settings", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Material 3 profile container with accessible vector reorder controls, schedule summary, and action buttons.
 */
@Composable
private fun ProfileCard(
    profile: CustomProfile,
    index: Int,
    totalProfiles: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onConfigureApps: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

    val baseColor = profileBaseColor(profile.colorIndex)
    val cardBackground = baseColor.copy(alpha = if (profile.enabled) 0.16f else 0.06f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    val cardBorder = baseColor.copy(alpha = if (profile.enabled) 0.42f else 0.18f)
        .compositeOver(MaterialTheme.colorScheme.outlineVariant)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = cardBackground,
        border = BorderStroke(1.dp, cardBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                leadingContent = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = ThresholdIcons.KeyboardArrowUp,
                                contentDescription = "Move profile up",
                                modifier = Modifier.size(20.dp),
                                tint = if (index > 0) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            )
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = index < totalProfiles - 1,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = ThresholdIcons.KeyboardArrowDown,
                                contentDescription = "Move profile down",
                                modifier = Modifier.size(20.dp),
                                tint = if (index < totalProfiles - 1) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            )
                        }
                    }
                },
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (profile.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "#${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                supportingContent = {
                    Column(modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            text = scheduleSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (profile.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = appSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = profile.enabled,
                        onCheckedChange = onToggleEnabled,
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = cardBackground,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditProfile),
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onEditProfile,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Schedule",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }

                OutlinedButton(
                    onClick = onConfigureApps,
                    modifier = Modifier.weight(1.25f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Configure Apps",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = ThresholdIcons.Delete,
                        contentDescription = "Delete Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatScheduleSummary(schedule: ScheduleTrigger): String {
    val orderedDays = DayOfWeek.entries.filter { it in schedule.days }
    if (orderedDays.isEmpty()) return "Inactive (No days selected)"

    val daysText = if (orderedDays.size == 7) {
        "Every day"
    } else if (orderedDays.toSet() ==
        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    ) {
        "Weekdays"
    } else if (orderedDays.toSet() == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
        "Weekends"
    } else {
        orderedDays.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
    }

    val distinctWindows = orderedDays.mapNotNull { schedule.dailyWindows[it] }.distinct()
    return if (schedule.sameScheduleEveryDay || distinctWindows.size <= 1) {
        val window = distinctWindows.firstOrNull()
        val start = window?.startTime ?: schedule.startTime
        val end = window?.endTime ?: schedule.endTime
        if (window?.isOvernight == true) {
            if (orderedDays.size == 1) {
                val day = orderedDays.first()
                val dayShort = day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                val nextShort = day.nextDay().name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                "$dayShort ${start.formatted()} – $nextShort ${end.formatted()}"
            } else {
                "$daysText · ${start.formatted()} – ${end.formatted()} (+1d)"
            }
        } else {
            "$daysText · ${start.formatted()} – ${end.formatted()}"
        }
    } else {
        if (orderedDays.size <= 3) {
            orderedDays.joinToString(" · ") { day ->
                val dayShort = day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                val w = schedule.dailyWindows[day]
                if (w != null) {
                    if (w.isOvernight) {
                        val nextShort = day.nextDay().name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                        "$dayShort ${w.startTime.formatted()}–$nextShort ${w.endTime.formatted()}"
                    } else {
                        "$dayShort ${w.startTime.formatted()}–${w.endTime.formatted()}"
                    }
                } else {
                    dayShort
                }
            }
        } else {
            "$daysText · Custom daily times"
        }
    }
}
