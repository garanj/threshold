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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.GeoTrigger
import com.garan.threshold.core.model.GeoTriggerMode
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TriggerRule
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.LocationTelemetry
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.ui.theme.profileBaseColor
import java.util.UUID

private enum class ProfilesTab {
    PROFILES,
    TRIGGERS,
}

private sealed interface TriggerAttachTarget {
    data object NewOrRule : TriggerAttachTarget
    data class AndInRule(val ruleIndex: Int) : TriggerAttachTarget
}

/**
 * Screen displaying the user-ordered list of custom profiles and reusable triggers (`Profiles | Triggers`),
 * with `AND`/`OR` trigger activation rules via [ProfileRulesEditorSheet], reusable trigger management
 * via [TriggerEditorSheet], and app overrides.
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
    currentLocation: LocationFix? = null,
    locationTelemetry: LocationTelemetry? = null,
    onUseCurrentLocation: ((onLocationResolved: (LocationFix) -> Unit) -> Unit)? = null,
    onOpenMapApp: (LocationFix?) -> Unit = {},
    onEnsureLocationPermissionForProfile: () -> Unit = {},
    savedTriggers: List<SavedTrigger> = emptyList(),
    onSaveTrigger: ((SavedTrigger) -> Unit)? = null,
    onDeleteTrigger: ((String) -> Unit)? = null,
) {
    BackHandler(onBack = onBack)

    // Local fallback trigger store for standalone tests that do not supply `onSaveTrigger`
    var localTriggers by remember { mutableStateOf<List<SavedTrigger>>(emptyList()) }

    val effectiveSavedTriggers = remember(savedTriggers, localTriggers, profiles) {
        val combined = LinkedHashMap<String, SavedTrigger>()
        savedTriggers.forEach { combined[it.id] = it }
        localTriggers.forEach { combined[it.id] = it }
        profiles.forEach { profile ->
            val inlineTrigger = profile.trigger
            if (profile.activationRules.isEmpty() && inlineTrigger != null) {
                val synthId = "trigger-${profile.id}"
                if (synthId !in combined) {
                    combined[synthId] = SavedTrigger(
                        id = synthId,
                        name = profile.name,
                        trigger = inlineTrigger,
                    )
                }
            }
        }
        combined.values.toList()
    }
    val savedTriggersById = remember(effectiveSavedTriggers) {
        effectiveSavedTriggers.associateBy { it.id }
    }

    fun persistTrigger(trigger: SavedTrigger) {
        if (onSaveTrigger != null) {
            onSaveTrigger(trigger)
        } else {
            val idx = localTriggers.indexOfFirst { it.id == trigger.id }
            localTriggers = if (idx >= 0) {
                localTriggers.toMutableList().apply { set(idx, trigger) }
            } else {
                localTriggers + trigger
            }
        }
    }

    fun removeTrigger(triggerId: String) {
        if (onDeleteTrigger != null) {
            onDeleteTrigger(triggerId)
        } else {
            localTriggers = localTriggers.filterNot { it.id == triggerId }
            val updatedProfiles = profiles.map { profile ->
                if (triggerId in profile.referencedTriggerIds || "trigger-${profile.id}" == triggerId) {
                    profile.withoutTrigger(triggerId)
                } else {
                    profile
                }
            }
            onReorderProfiles(updatedProfiles)
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var selectedTab by remember { mutableStateOf(ProfilesTab.PROFILES) }
    var editingProfile by remember { mutableStateOf<CustomProfile?>(null) }
    var isCreatingNewProfile by remember { mutableStateOf(false) }
    var editingTrigger by remember { mutableStateOf<SavedTrigger?>(null) }
    var isCreatingNewStandaloneTrigger by remember { mutableStateOf(false) }
    var showExactAlarmPermissionSheet by remember { mutableStateOf(false) }
    var profilePendingDeletion by remember { mutableStateOf<CustomProfile?>(null) }
    var triggerPendingDeletion by remember { mutableStateOf<SavedTrigger?>(null) }

    fun checkPermissionsForTriggers(triggers: List<ProfileTrigger>) {
        if (triggers.any { it is ProfileTrigger.Schedule } && !canScheduleExactAlarms()) {
            showExactAlarmPermissionSheet = true
        }
        if (triggers.any { it is ProfileTrigger.Geo }) {
            onEnsureLocationPermissionForProfile()
        }
    }

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
                                text = if (selectedTab == ProfilesTab.PROFILES) {
                                    "Top active profile takes precedence"
                                } else {
                                    "Reusable conditions for activating profiles"
                                },
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
                                if (selectedTab == ProfilesTab.PROFILES) {
                                    isCreatingNewProfile = true
                                } else {
                                    isCreatingNewStandaloneTrigger = true
                                }
                            },
                        ) {
                            Icon(
                                imageVector = ThresholdIcons.Add,
                                contentDescription = if (selectedTab == ProfilesTab.PROFILES) {
                                    "Add Profile"
                                } else {
                                    "Add Trigger"
                                },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                ) {
                    SegmentedButton(
                        selected = selectedTab == ProfilesTab.PROFILES,
                        onClick = {
                            if (selectedTab != ProfilesTab.PROFILES) {
                                hapticEngine.rotaryDetent()
                                selectedTab = ProfilesTab.PROFILES
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text("Profiles")
                    }
                    SegmentedButton(
                        selected = selectedTab == ProfilesTab.TRIGGERS,
                        onClick = {
                            if (selectedTab != ProfilesTab.TRIGGERS) {
                                hapticEngine.rotaryDetent()
                                selectedTab = ProfilesTab.TRIGGERS
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text("Triggers")
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when (selectedTab) {
            ProfilesTab.PROFILES -> {
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
                                savedTriggersById = savedTriggersById,
                                currentLocation = currentLocation,
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
                                    if (enabled) {
                                        checkPermissionsForTriggers(profile.resolvedTriggers(savedTriggersById))
                                    }
                                },
                                onEditProfile = { editingProfile = profile },
                                onConfigureApps = { onConfigureProfileApps(profile.id) },
                                onDelete = {
                                    hapticEngine.rotaryDetent()
                                    profilePendingDeletion = profile
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

                    val hasGeo = profiles.any { profile ->
                        profile.resolvedTriggers(savedTriggersById).any { it is ProfileTrigger.Geo }
                    }
                    if (locationTelemetry != null && hasGeo) {
                        item(key = "location_telemetry_footer") {
                            Text(
                                text = formatLocationTelemetryFooter(locationTelemetry),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            ProfilesTab.TRIGGERS -> {
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
                    if (effectiveSavedTriggers.isEmpty()) {
                        item(key = "empty_triggers_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No triggers yet.\n" +
                                        "Create reusable schedule or location triggers for your profiles.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        items(
                            items = effectiveSavedTriggers,
                            key = { it.id },
                        ) { savedTrigger ->
                            val referencingProfiles = profiles.filter { profile ->
                                savedTrigger.id in profile.referencedTriggerIds ||
                                    (profile.activationRules.isEmpty() && "trigger-${profile.id}" == savedTrigger.id)
                            }
                            TriggerCard(
                                savedTrigger = savedTrigger,
                                referencingProfiles = referencingProfiles,
                                currentLocation = currentLocation,
                                onEdit = { editingTrigger = savedTrigger },
                                onDelete = {
                                    hapticEngine.rotaryDetent()
                                    triggerPendingDeletion = savedTrigger
                                },
                            )
                        }
                    }

                    item(key = "add_trigger_row") {
                        OutlinedButton(
                            onClick = {
                                hapticEngine.rotaryDetent()
                                isCreatingNewStandaloneTrigger = true
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
                                text = "Add Trigger",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Existing Profile (Name + AND/OR Trigger Rules)
    editingProfile?.let { profile ->
        val seedRules = if (profile.activationRules.isNotEmpty()) {
            profile.activationRules
        } else if (profile.trigger != null) {
            listOf(TriggerRule(listOf("trigger-${profile.id}")))
        } else {
            emptyList()
        }
        ProfileRulesEditorSheet(
            initialName = profile.name,
            initialRules = seedRules,
            isNewProfile = false,
            savedTriggers = effectiveSavedTriggers,
            currentLocation = currentLocation,
            hapticEngine = hapticEngine,
            onUseCurrentLocation = onUseCurrentLocation,
            onOpenMapApp = onOpenMapApp,
            onSaveTrigger = { trigger ->
                persistTrigger(trigger)
                checkPermissionsForTriggers(listOf(trigger.trigger))
            },
            onSaveProfileRules = { updatedName, updatedRules ->
                val updatedProfile = profile.copy(
                    name = updatedName,
                    activationRules = updatedRules,
                    trigger = null,
                    enabled = if (updatedRules.isEmpty()) false else profile.enabled,
                )
                onSaveProfile(updatedProfile)
                if (updatedProfile.enabled) {
                    val currentMap = effectiveSavedTriggers.associateBy { it.id }
                    checkPermissionsForTriggers(updatedProfile.resolvedTriggers(currentMap))
                }
                editingProfile = null
            },
            onDismiss = { editingProfile = null },
        )
    }

    // Create New Profile (Name + AND/OR Trigger Rules)
    if (isCreatingNewProfile) {
        ProfileRulesEditorSheet(
            initialName = "",
            initialRules = emptyList(),
            isNewProfile = true,
            savedTriggers = effectiveSavedTriggers,
            currentLocation = currentLocation,
            hapticEngine = hapticEngine,
            onUseCurrentLocation = onUseCurrentLocation,
            onOpenMapApp = onOpenMapApp,
            onSaveTrigger = { trigger ->
                persistTrigger(trigger)
                checkPermissionsForTriggers(listOf(trigger.trigger))
            },
            onSaveProfileRules = { name, rules ->
                val newProfile = CustomProfile(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    priority = profiles.size,
                    enabled = rules.isNotEmpty(),
                    activationRules = rules,
                    trigger = null,
                    colorIndex = CustomProfile.nextColorIndex(profiles),
                )
                onSaveProfile(newProfile)
                if (newProfile.enabled) {
                    val currentMap = effectiveSavedTriggers.associateBy { it.id }
                    checkPermissionsForTriggers(newProfile.resolvedTriggers(currentMap))
                }
                isCreatingNewProfile = false
            },
            onDismiss = { isCreatingNewProfile = false },
        )
    }

    // Edit Standalone Trigger Sheet
    editingTrigger?.let { target ->
        TriggerEditorSheet(
            initialName = target.name,
            initialTrigger = target.trigger,
            isNewTrigger = false,
            currentLocation = currentLocation,
            onUseCurrentLocation = onUseCurrentLocation,
            onOpenMapApp = onOpenMapApp,
            hapticEngine = hapticEngine,
            onSaveTrigger = { updatedName, updatedTrigger ->
                persistTrigger(
                    target.copy(
                        name = updatedName,
                        trigger = updatedTrigger,
                    ),
                )
                checkPermissionsForTriggers(listOf(updatedTrigger))
                editingTrigger = null
            },
            onDismiss = { editingTrigger = null },
        )
    }

    // Create New Standalone Trigger Sheet
    if (isCreatingNewStandaloneTrigger) {
        TriggerEditorSheet(
            initialName = "",
            isNewTrigger = true,
            currentLocation = currentLocation,
            onUseCurrentLocation = onUseCurrentLocation,
            onOpenMapApp = onOpenMapApp,
            hapticEngine = hapticEngine,
            onSaveTrigger = { name, trigger ->
                val created = SavedTrigger(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    trigger = trigger,
                )
                persistTrigger(created)
                checkPermissionsForTriggers(listOf(trigger))
                isCreatingNewStandaloneTrigger = false
            },
            onDismiss = { isCreatingNewStandaloneTrigger = false },
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

    profilePendingDeletion?.let { targetProfile ->
        AlertDialog(
            onDismissRequest = { profilePendingDeletion = null },
            title = {
                Text(
                    text = "Delete profile?",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = "“${targetProfile.name}” and its app rules will be permanently removed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        hapticEngine.rotaryDetent()
                        onDeleteProfile(targetProfile.id)
                        profilePendingDeletion = null
                    },
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDeletion = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    triggerPendingDeletion?.let { targetTrigger ->
        val referencingProfiles = profiles.filter { profile ->
            targetTrigger.id in profile.referencedTriggerIds ||
                (profile.activationRules.isEmpty() && "trigger-${profile.id}" == targetTrigger.id)
        }
        val warningText = if (referencingProfiles.isEmpty()) {
            "“${targetTrigger.name}” will be permanently removed."
        } else {
            val names = referencingProfiles.joinToString(", ") { "“${it.name}”" }
            "“${targetTrigger.name}” is used by $names. Deleting it will remove it from those profiles " +
                "and disable any profile left with no triggers."
        }
        AlertDialog(
            onDismissRequest = { triggerPendingDeletion = null },
            title = {
                Text(
                    text = "Delete trigger?",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        hapticEngine.rotaryDetent()
                        removeTrigger(targetTrigger.id)
                        triggerPendingDeletion = null
                    },
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { triggerPendingDeletion = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Material 3 [ModalBottomSheet] for editing a [CustomProfile]'s name and its `AND` / `OR`
 * [TriggerRule] activation conditions, with inline trigger creation and editing via [TriggerEditorSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRulesEditorSheet(
    initialName: String,
    initialRules: List<TriggerRule>,
    isNewProfile: Boolean,
    savedTriggers: List<SavedTrigger>,
    currentLocation: LocationFix?,
    hapticEngine: HapticEngine,
    onUseCurrentLocation: ((onLocationResolved: (LocationFix) -> Unit) -> Unit)?,
    onOpenMapApp: (LocationFix?) -> Unit,
    onSaveTrigger: (SavedTrigger) -> Unit,
    onSaveProfileRules: (name: String, rules: List<TriggerRule>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var profileName by remember { mutableStateOf(initialName) }
    var rules by remember { mutableStateOf(initialRules) }

    // Ephemeral map of newly-created/edited triggers within this sheet session so UI updates immediately
    var sessionTriggers by remember { mutableStateOf<Map<String, SavedTrigger>>(emptyMap()) }
    val triggersById = remember(savedTriggers, sessionTriggers) {
        val map = LinkedHashMap<String, SavedTrigger>()
        savedTriggers.forEach { map[it.id] = it }
        sessionTriggers.forEach { (k, v) -> map[k] = v }
        map
    }

    var attachTarget by remember { mutableStateOf<TriggerAttachTarget?>(null) }
    var isCreatingTriggerForTarget by remember { mutableStateOf<TriggerAttachTarget?>(null) }
    var editingInlineTrigger by remember { mutableStateOf<SavedTrigger?>(null) }

    fun attachTriggerIdToTarget(triggerId: String, target: TriggerAttachTarget) {
        rules = when (target) {
            is TriggerAttachTarget.NewOrRule -> {
                rules + TriggerRule(listOf(triggerId))
            }
            is TriggerAttachTarget.AndInRule -> {
                rules.mapIndexed { idx, rule ->
                    if (idx == target.ruleIndex && triggerId !in rule.triggerIds) {
                        TriggerRule(rule.triggerIds + triggerId)
                    } else {
                        rule
                    }
                }
            }
        }
    }

    fun requestAttach(target: TriggerAttachTarget) {
        val alreadyInTargetRow = when (target) {
            is TriggerAttachTarget.NewOrRule -> emptySet()
            is TriggerAttachTarget.AndInRule ->
                rules.getOrNull(target.ruleIndex)?.triggerIds?.toSet().orEmpty()
        }
        val availableExisting = triggersById.values.filter { it.id !in alreadyInTargetRow }
        if (availableExisting.isEmpty()) {
            isCreatingTriggerForTarget = target
        } else {
            attachTarget = target
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
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "ACTIVATE WHEN",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (rules.isEmpty()) {
                Text(
                    text = "No triggers attached. Add a trigger to activate this profile automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                OutlinedButton(
                    onClick = {
                        hapticEngine.rotaryDetent()
                        requestAttach(TriggerAttachTarget.NewOrRule)
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
                        text = "Add Trigger",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                rules.forEachIndexed { ruleIndex, rule ->
                    if (ruleIndex > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp,
                            )
                            Text(
                                text = "OR",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            rule.triggerIds.forEachIndexed { triggerIndex, triggerId ->
                                if (triggerIndex > 0) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "AND",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            thickness = 0.5.dp,
                                        )
                                    }
                                }

                                val saved = triggersById[triggerId]
                                val triggerName = saved?.name ?: triggerId
                                val triggerDetail = saved?.let {
                                    formatTriggerDetail(it.trigger, currentLocation)
                                } ?: "Trigger unavailable"

                                ListItem(
                                    leadingContent = {
                                        Icon(
                                            imageVector = when (saved?.trigger) {
                                                is ProfileTrigger.Geo -> ThresholdIcons.LocationOn
                                                is ProfileTrigger.Schedule, null -> ThresholdIcons.Schedule
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            text = triggerName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = triggerDetail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                hapticEngine.rotaryDetent()
                                                val remaining = rule.triggerIds.filterNot { it == triggerId }
                                                rules = if (remaining.isEmpty()) {
                                                    rules.filterIndexed { idx, _ -> idx != ruleIndex }
                                                } else {
                                                    rules.mapIndexed { idx, r ->
                                                        if (idx == ruleIndex) TriggerRule(remaining) else r
                                                    }
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = ThresholdIcons.Close,
                                                contentDescription = "Remove trigger $triggerName",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = saved != null) {
                                            if (saved != null) {
                                                editingInlineTrigger = saved
                                            }
                                        },
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )

                            TextButton(
                                onClick = {
                                    hapticEngine.rotaryDetent()
                                    requestAttach(TriggerAttachTarget.AndInRule(ruleIndex))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                            ) {
                                Icon(
                                    imageVector = ThresholdIcons.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add AND trigger",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        hapticEngine.rotaryDetent()
                        requestAttach(TriggerAttachTarget.NewOrRule)
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
                        text = "Add OR condition",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                        val fallbackName = rules.firstOrNull()?.triggerIds?.firstOrNull()
                            ?.let { triggersById[it]?.name }
                            ?: "Custom Profile"
                        val resolvedName = profileName.trim().ifBlank { fallbackName }
                        onSaveProfileRules(resolvedName, rules)
                    },
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = if (isNewProfile) "Create" else "Save",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    // Picker sheet for selecting an existing SavedTrigger or creating a new one
    attachTarget?.let { target ->
        val alreadyInTargetRow = when (target) {
            is TriggerAttachTarget.NewOrRule -> emptySet()
            is TriggerAttachTarget.AndInRule ->
                rules.getOrNull(target.ruleIndex)?.triggerIds?.toSet().orEmpty()
        }
        val availableExisting = triggersById.values.filter { it.id !in alreadyInTargetRow }

        ModalBottomSheet(
            onDismissRequest = { attachTarget = null },
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
                    text = when (target) {
                        is TriggerAttachTarget.AndInRule -> "Add AND Trigger"
                        is TriggerAttachTarget.NewOrRule -> "Add Trigger"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(12.dp))

                availableExisting.forEach { existing ->
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = when (existing.trigger) {
                                    is ProfileTrigger.Schedule -> ThresholdIcons.Schedule
                                    is ProfileTrigger.Geo -> ThresholdIcons.LocationOn
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = existing.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = formatTriggerDetail(existing.trigger, currentLocation),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                hapticEngine.rotaryDetent()
                                attachTriggerIdToTarget(existing.id, target)
                                attachTarget = null
                            },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        hapticEngine.rotaryDetent()
                        attachTarget = null
                        isCreatingTriggerForTarget = target
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
                        text = "Create New Trigger",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    // Inline creation of a new SavedTrigger for the target rule
    isCreatingTriggerForTarget?.let { target ->
        TriggerEditorSheet(
            initialName = profileName.trim(),
            isNewTrigger = true,
            currentLocation = currentLocation,
            onUseCurrentLocation = onUseCurrentLocation,
            onOpenMapApp = onOpenMapApp,
            hapticEngine = hapticEngine,
            onSaveTrigger = { triggerName, profileTrigger ->
                val created = SavedTrigger(
                    id = UUID.randomUUID().toString(),
                    name = triggerName,
                    trigger = profileTrigger,
                )
                sessionTriggers = sessionTriggers + (created.id to created)
                onSaveTrigger(created)
                attachTriggerIdToTarget(created.id, target)
                isCreatingTriggerForTarget = null
            },
            onDismiss = { isCreatingTriggerForTarget = null },
        )
    }

    // Inline editing of an already-attached SavedTrigger
    editingInlineTrigger?.let { existing ->
        TriggerEditorSheet(
            initialName = existing.name,
            initialTrigger = existing.trigger,
            isNewTrigger = false,
            currentLocation = currentLocation,
            onUseCurrentLocation = onUseCurrentLocation,
            onOpenMapApp = onOpenMapApp,
            hapticEngine = hapticEngine,
            onSaveTrigger = { updatedName, updatedTrigger ->
                val updated = existing.copy(
                    name = updatedName,
                    trigger = updatedTrigger,
                )
                sessionTriggers = sessionTriggers + (updated.id to updated)
                onSaveTrigger(updated)
                editingInlineTrigger = null
            },
            onDismiss = { editingInlineTrigger = null },
        )
    }
}

/**
 * Material 3 card for a reusable [SavedTrigger] in the `Triggers` tab of [ProfilesScreen].
 */
@Composable
private fun TriggerCard(
    savedTrigger: SavedTrigger,
    referencingProfiles: List<CustomProfile>,
    currentLocation: LocationFix?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailSummary = formatTriggerDetail(savedTrigger.trigger, currentLocation)
    val usageSummary = if (referencingProfiles.isEmpty()) {
        "Not used by any profile"
    } else {
        "Used by: ${referencingProfiles.joinToString(", ") { it.name }}"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = when (savedTrigger.trigger) {
                        is ProfileTrigger.Schedule -> ThresholdIcons.Schedule
                        is ProfileTrigger.Geo -> ThresholdIcons.LocationOn
                    },
                    contentDescription = when (savedTrigger.trigger) {
                        is ProfileTrigger.Schedule -> "Schedule trigger"
                        is ProfileTrigger.Geo -> "Geo trigger"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            },
            headlineContent = {
                Text(
                    text = savedTrigger.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Column(modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = detailSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = usageSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = ThresholdIcons.Delete,
                        contentDescription = "Delete Trigger",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit),
        )
    }
}

/**
 * Material 3 profile container with accessible vector reorder controls, trigger summary, and action buttons.
 */
@Composable
private fun ProfileCard(
    profile: CustomProfile,
    index: Int,
    totalProfiles: Int,
    savedTriggersById: Map<String, SavedTrigger>,
    currentLocation: LocationFix?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onConfigureApps: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedTriggers = profile.resolvedTriggers(savedTriggersById)
    val hasSchedule = resolvedTriggers.any { it is ProfileTrigger.Schedule }
    val hasGeo = resolvedTriggers.any { it is ProfileTrigger.Geo }

    val triggerSummary = formatProfileRulesSummary(
        profile = profile,
        savedTriggersById = savedTriggersById,
        currentLocation = currentLocation,
    )

    val editButtonLabel = when {
        hasSchedule && !hasGeo && profile.referencedTriggerIds.size <= 1 -> "Schedule"
        hasGeo && !hasSchedule && profile.referencedTriggerIds.size <= 1 -> "Location"
        else -> "Triggers"
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val iconTint = if (profile.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        if (hasSchedule || (!hasSchedule && !hasGeo)) {
                            Icon(
                                imageVector = ThresholdIcons.Schedule,
                                contentDescription = "Schedule profile",
                                modifier = Modifier.size(22.dp),
                                tint = iconTint,
                            )
                        }
                        if (hasGeo) {
                            Icon(
                                imageVector = ThresholdIcons.LocationOn,
                                contentDescription = "Geo profile",
                                modifier = Modifier.size(22.dp),
                                tint = iconTint,
                            )
                        }

                        if (totalProfiles > 1) {
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
                            text = triggerSummary,
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
                        text = editButtonLabel,
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

internal fun formatProfileRulesSummary(
    profile: CustomProfile,
    savedTriggersById: Map<String, SavedTrigger>,
    currentLocation: LocationFix?,
): String {
    if (profile.activationRules.isNotEmpty()) {
        if (profile.activationRules.size == 1 && profile.activationRules[0].triggerIds.size == 1) {
            val singleId = profile.activationRules[0].triggerIds[0]
            val saved = savedTriggersById[singleId]
            if (saved != null) {
                val detail = formatTriggerDetail(saved.trigger, currentLocation)
                return if (saved.name.equals(profile.name, ignoreCase = true) || saved.id == "trigger-${profile.id}") {
                    detail
                } else {
                    "${saved.name} · $detail"
                }
            }
        }
        val formattedRules = profile.activationRules.mapNotNull { rule ->
            val names = rule.triggerIds.mapNotNull { savedTriggersById[it]?.name }
            if (names.isEmpty()) null else names.joinToString(" + ")
        }
        return if (formattedRules.isEmpty()) {
            "No triggers assigned"
        } else {
            formattedRules.joinToString(" OR ")
        }
    }

    val legacyTrigger = profile.trigger ?: return "No triggers assigned"
    return formatTriggerDetail(legacyTrigger, currentLocation)
}

internal fun formatTriggerDetail(trigger: ProfileTrigger, currentLocation: LocationFix?): String = when (trigger) {
    is ProfileTrigger.Schedule -> formatScheduleSummary(trigger.schedule)
    is ProfileTrigger.Geo -> formatGeoSummary(trigger.geo, currentLocation)
}

internal fun formatGeoSummary(geo: GeoTrigger, currentLocation: LocationFix?): String {
    val modeLabel = when (geo.mode) {
        GeoTriggerMode.INSIDE -> "Inside"
        GeoTriggerMode.OUTSIDE -> "Outside"
    }
    val radiusLabel = GeoTrigger.formatRadius(geo.radiusMeters)
    val prefix = "$modeLabel $radiusLabel"

    if (currentLocation == null) {
        return "$prefix · ${geo.center.formattedCoordinates()}"
    }

    val distanceMeters = geo.distanceMetersFrom(currentLocation)
    val distanceLabel = GeoTrigger.formatDistance(distanceMeters)
    return if (distanceMeters <= geo.radiusMeters) {
        "$prefix · Currently inside ($distanceLabel away)"
    } else {
        "$prefix · $distanceLabel away"
    }
}

internal fun formatLocationTelemetryFooter(telemetry: LocationTelemetry): String {
    val base = "Location checks: ${telemetry.activeChecksLastHour}/hr · " +
        "${telemetry.activeChecksLast24h}/24h (${telemetry.cacheHitsLast24h} cached)"
    val provider = telemetry.lastProviderUsed ?: return base
    val latency = telemetry.lastFixLatencyMs
    return if (latency != null) {
        "$base · $provider (${latency}ms)"
    } else {
        "$base · $provider"
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
