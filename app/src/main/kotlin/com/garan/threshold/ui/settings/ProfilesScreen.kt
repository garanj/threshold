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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
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

/**
 * Screen displaying the user-ordered list of custom profiles and reusable triggers (`Profiles | Triggers`),
 * with `AND`/`OR` trigger activation rules via [ProfileRulesEditorSheet], reusable trigger management
 * via [TriggerEditorSheet], and app overrides.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
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
    onRegisterNfcScanListener: (((String) -> Unit)?) -> Unit = {},
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
            .semantics { testTagsAsResourceId = true }
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
                            modifier = Modifier.testTag(ProfilesTestTags.TOP_ADD_BUTTON),
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
                        modifier = Modifier.testTag(ProfilesTestTags.TAB_PROFILES),
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
                        modifier = Modifier.testTag(ProfilesTestTags.TAB_TRIGGERS),
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
                                .height(48.dp)
                                .testTag(ProfilesTestTags.ADD_PROFILE_BUTTON),
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
                                .height(48.dp)
                                .testTag(ProfilesTestTags.ADD_TRIGGER_BUTTON),
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
            onRegisterNfcScanListener = onRegisterNfcScanListener,
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
            onRegisterNfcScanListener = onRegisterNfcScanListener,
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
            onRegisterNfcScanListener = onRegisterNfcScanListener,
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
            onRegisterNfcScanListener = onRegisterNfcScanListener,
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
                    modifier = Modifier.testTag(ProfilesTestTags.DELETE_CONFIRM_BUTTON),
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { profilePendingDeletion = null },
                    modifier = Modifier.testTag(ProfilesTestTags.DELETE_CANCEL_BUTTON),
                ) {
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
                    modifier = Modifier.testTag(ProfilesTestTags.DELETE_CONFIRM_BUTTON),
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { triggerPendingDeletion = null },
                    modifier = Modifier.testTag(ProfilesTestTags.DELETE_CANCEL_BUTTON),
                ) {
                    Text("Cancel")
                }
            },
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
                        is ProfileTrigger.Nfc -> ThresholdIcons.Nfc
                    },
                    contentDescription = when (savedTrigger.trigger) {
                        is ProfileTrigger.Schedule -> "Schedule trigger"
                        is ProfileTrigger.Geo -> "Geo trigger"
                        is ProfileTrigger.Nfc -> "NFC trigger"
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
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag(ProfilesTestTags.triggerDelete(savedTrigger.id)),
                ) {
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
                .testTag(ProfilesTestTags.triggerCard(savedTrigger.id))
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
    val hasNfc = resolvedTriggers.any { it is ProfileTrigger.Nfc }

    val triggerSummary = formatProfileRulesSummary(
        profile = profile,
        savedTriggersById = savedTriggersById,
        currentLocation = currentLocation,
    )

    val editButtonLabel = when {
        hasSchedule && !hasGeo && !hasNfc && profile.referencedTriggerIds.size <= 1 -> "Schedule"
        hasGeo && !hasSchedule && !hasNfc && profile.referencedTriggerIds.size <= 1 -> "Location"
        hasNfc && !hasSchedule && !hasGeo && profile.referencedTriggerIds.size <= 1 -> "NFC"
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
        modifier = modifier
            .fillMaxWidth()
            .testTag(ProfilesTestTags.profileCard(profile.id)),
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
                        if (hasSchedule || (!hasSchedule && !hasGeo && !hasNfc)) {
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
                        if (hasNfc) {
                            Icon(
                                imageVector = ThresholdIcons.Nfc,
                                contentDescription = "NFC profile",
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
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag(ProfilesTestTags.profileMoveUp(profile.id)),
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
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag(ProfilesTestTags.profileMoveDown(profile.id)),
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
                        modifier = Modifier.testTag(ProfilesTestTags.profileToggle(profile.id)),
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
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ProfilesTestTags.profileEditRules(profile.id)),
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
                    modifier = Modifier
                        .weight(1.25f)
                        .testTag(ProfilesTestTags.profileConfigureApps(profile.id)),
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

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag(ProfilesTestTags.profileDelete(profile.id)),
                ) {
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
