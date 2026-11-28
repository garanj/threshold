package com.garan.drytoast.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garan.drytoast.core.model.CustomProfile
import com.garan.drytoast.core.model.LocationFix
import com.garan.drytoast.core.model.NfcTagRegistrationResult
import com.garan.drytoast.core.model.ProfileTrigger
import com.garan.drytoast.core.model.SavedTrigger
import com.garan.drytoast.core.model.TriggerRule
import com.garan.drytoast.core.port.HapticEngine
import com.garan.drytoast.core.port.LocationTelemetry
import com.garan.drytoast.core.port.NoOpHapticEngine
import java.util.UUID
import androidx.compose.foundation.lazy.staggeredgrid.items as gridItems
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed as gridItemsIndexed

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
    onRegisterNfcRegistrationListener: (((NfcTagRegistrationResult) -> Unit)?) -> Unit = {},
) {
    val hideBackArrow = LocalHideSettingsDetailBackArrow.current
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
    var selectedTab by rememberSaveable { mutableStateOf(ProfilesTab.PROFILES) }
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingProfile = editingProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
    var isCreatingNewProfile by rememberSaveable { mutableStateOf(false) }
    var editingTriggerId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingTrigger = editingTriggerId?.let { id -> savedTriggersById[id] }
    var isCreatingNewStandaloneTrigger by rememberSaveable { mutableStateOf(false) }
    var showExactAlarmPermissionSheet by rememberSaveable { mutableStateOf(false) }
    var profilePendingDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
    val profilePendingDeletion = profilePendingDeletionId?.let { id -> profiles.firstOrNull { it.id == id } }
    var triggerPendingDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
    val triggerPendingDeletion = triggerPendingDeletionId?.let { id -> savedTriggersById[id] }

    fun checkPermissionsForTriggers(triggers: List<ProfileTrigger>) {
        if (triggers.any { it is ProfileTrigger.Schedule } && !canScheduleExactAlarms()) {
            showExactAlarmPermissionSheet = true
        }
        if (triggers.any { it is ProfileTrigger.Geo }) {
            onEnsureLocationPermissionForProfile()
        }
    }

    fun moveProfile(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in profiles.indices || toIndex !in profiles.indices) return
        hapticEngine.rotaryDetent()
        val list = profiles.toMutableList()
        val temp = list[fromIndex]
        list[fromIndex] = list[toIndex]
        list[toIndex] = temp
        onReorderProfiles(list)
    }

    fun toggleProfileEnabled(profile: CustomProfile, enabled: Boolean) {
        hapticEngine.rotaryDetent()
        onSaveProfile(profile.copy(enabled = enabled))
        if (enabled) {
            checkPermissionsForTriggers(profile.resolvedTriggers(savedTriggersById))
        }
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
                Column(
                    modifier = Modifier
                        .widthIn(max = SETTINGS_GRID_MAX_WIDTH)
                        .fillMaxWidth(),
                ) {
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
                            if (!hideBackArrow) {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = DryToastIcons.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
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
                                    imageVector = DryToastIcons.Add,
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
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val useTwoColumnGrid = maxWidth >= SETTINGS_GRID_BREAKPOINT
            val listContentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp,
            )
            val hasGeo = profiles.any { profile ->
                profile.resolvedTriggers(savedTriggersById).any { it is ProfileTrigger.Geo }
            }

            when (selectedTab) {
                ProfilesTab.PROFILES -> {
                    if (useTwoColumnGrid) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            modifier = Modifier
                                .widthIn(max = SETTINGS_GRID_MAX_WIDTH)
                                .fillMaxSize()
                                .consumeWindowInsets(innerPadding)
                                .testTag(ProfilesTestTags.PROFILES_GRID),
                            contentPadding = listContentPadding,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                        ) {
                            if (profiles.isEmpty()) {
                                item(key = "empty_state", span = StaggeredGridItemSpan.FullLine) {
                                    EmptyProfilesNotice()
                                }
                            } else {
                                gridItemsIndexed(
                                    items = profiles,
                                    key = { _, profile -> profile.id },
                                ) { index, profile ->
                                    ProfileCard(
                                        profile = profile,
                                        index = index,
                                        totalProfiles = profiles.size,
                                        savedTriggersById = savedTriggersById,
                                        currentLocation = currentLocation,
                                        onMoveUp = { moveProfile(index, index - 1) },
                                        onMoveDown = { moveProfile(index, index + 1) },
                                        onToggleEnabled = { enabled -> toggleProfileEnabled(profile, enabled) },
                                        onEditProfile = { editingProfileId = profile.id },
                                        onConfigureApps = { onConfigureProfileApps(profile.id) },
                                        onDelete = {
                                            hapticEngine.rotaryDetent()
                                            profilePendingDeletionId = profile.id
                                        },
                                    )
                                }
                            }

                            item(key = "add_profile_row", span = StaggeredGridItemSpan.FullLine) {
                                AddEntityOutlinedButton(
                                    label = "Add Profile",
                                    testTag = ProfilesTestTags.ADD_PROFILE_BUTTON,
                                    onClick = {
                                        hapticEngine.rotaryDetent()
                                        isCreatingNewProfile = true
                                    },
                                )
                            }

                            if (locationTelemetry != null && hasGeo) {
                                item(key = "location_telemetry_footer", span = StaggeredGridItemSpan.FullLine) {
                                    LocationTelemetryFooterText(locationTelemetry)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = SETTINGS_MEDIUM_MAX_WIDTH)
                                .fillMaxSize()
                                .consumeWindowInsets(innerPadding)
                                .testTag(ProfilesTestTags.PROFILES_LIST),
                            contentPadding = listContentPadding,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (profiles.isEmpty()) {
                                item(key = "empty_state") {
                                    EmptyProfilesNotice()
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
                                        onMoveUp = { moveProfile(index, index - 1) },
                                        onMoveDown = { moveProfile(index, index + 1) },
                                        onToggleEnabled = { enabled -> toggleProfileEnabled(profile, enabled) },
                                        onEditProfile = { editingProfileId = profile.id },
                                        onConfigureApps = { onConfigureProfileApps(profile.id) },
                                        onDelete = {
                                            hapticEngine.rotaryDetent()
                                            profilePendingDeletionId = profile.id
                                        },
                                    )
                                }
                            }

                            item(key = "add_profile_row") {
                                AddEntityOutlinedButton(
                                    label = "Add Profile",
                                    testTag = ProfilesTestTags.ADD_PROFILE_BUTTON,
                                    onClick = {
                                        hapticEngine.rotaryDetent()
                                        isCreatingNewProfile = true
                                    },
                                )
                            }

                            if (locationTelemetry != null && hasGeo) {
                                item(key = "location_telemetry_footer") {
                                    LocationTelemetryFooterText(locationTelemetry)
                                }
                            }
                        }
                    }
                }

                ProfilesTab.TRIGGERS -> {
                    if (useTwoColumnGrid) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            modifier = Modifier
                                .widthIn(max = SETTINGS_GRID_MAX_WIDTH)
                                .fillMaxSize()
                                .consumeWindowInsets(innerPadding)
                                .testTag(ProfilesTestTags.TRIGGERS_GRID),
                            contentPadding = listContentPadding,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                        ) {
                            if (effectiveSavedTriggers.isEmpty()) {
                                item(key = "empty_triggers_state", span = StaggeredGridItemSpan.FullLine) {
                                    EmptyTriggersNotice()
                                }
                            } else {
                                gridItems(
                                    items = effectiveSavedTriggers,
                                    key = { it.id },
                                ) { savedTrigger ->
                                    val referencingProfiles = profiles.filter { profile ->
                                        savedTrigger.id in profile.referencedTriggerIds ||
                                            (
                                                profile.activationRules.isEmpty() &&
                                                    "trigger-${profile.id}" == savedTrigger.id
                                                )
                                    }
                                    TriggerCard(
                                        savedTrigger = savedTrigger,
                                        referencingProfiles = referencingProfiles,
                                        currentLocation = currentLocation,
                                        onEdit = { editingTriggerId = savedTrigger.id },
                                        onDelete = {
                                            hapticEngine.rotaryDetent()
                                            triggerPendingDeletionId = savedTrigger.id
                                        },
                                    )
                                }
                            }

                            item(key = "add_trigger_row", span = StaggeredGridItemSpan.FullLine) {
                                AddEntityOutlinedButton(
                                    label = "Add Trigger",
                                    testTag = ProfilesTestTags.ADD_TRIGGER_BUTTON,
                                    onClick = {
                                        hapticEngine.rotaryDetent()
                                        isCreatingNewStandaloneTrigger = true
                                    },
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = SETTINGS_MEDIUM_MAX_WIDTH)
                                .fillMaxSize()
                                .consumeWindowInsets(innerPadding)
                                .testTag(ProfilesTestTags.TRIGGERS_LIST),
                            contentPadding = listContentPadding,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (effectiveSavedTriggers.isEmpty()) {
                                item(key = "empty_triggers_state") {
                                    EmptyTriggersNotice()
                                }
                            } else {
                                items(
                                    items = effectiveSavedTriggers,
                                    key = { it.id },
                                ) { savedTrigger ->
                                    val referencingProfiles = profiles.filter { profile ->
                                        savedTrigger.id in profile.referencedTriggerIds ||
                                            (
                                                profile.activationRules.isEmpty() &&
                                                    "trigger-${profile.id}" == savedTrigger.id
                                                )
                                    }
                                    TriggerCard(
                                        savedTrigger = savedTrigger,
                                        referencingProfiles = referencingProfiles,
                                        currentLocation = currentLocation,
                                        onEdit = { editingTriggerId = savedTrigger.id },
                                        onDelete = {
                                            hapticEngine.rotaryDetent()
                                            triggerPendingDeletionId = savedTrigger.id
                                        },
                                    )
                                }
                            }

                            item(key = "add_trigger_row") {
                                AddEntityOutlinedButton(
                                    label = "Add Trigger",
                                    testTag = ProfilesTestTags.ADD_TRIGGER_BUTTON,
                                    onClick = {
                                        hapticEngine.rotaryDetent()
                                        isCreatingNewStandaloneTrigger = true
                                    },
                                )
                            }
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
            onRegisterNfcRegistrationListener = onRegisterNfcRegistrationListener,
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
                editingProfileId = null
            },
            onDismiss = { editingProfileId = null },
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
            onRegisterNfcRegistrationListener = onRegisterNfcRegistrationListener,
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
            onRegisterNfcRegistrationListener = onRegisterNfcRegistrationListener,
            hapticEngine = hapticEngine,
            onSaveTrigger = { updatedName, updatedTrigger ->
                persistTrigger(
                    target.copy(
                        name = updatedName,
                        trigger = updatedTrigger,
                    ),
                )
                checkPermissionsForTriggers(listOf(updatedTrigger))
                editingTriggerId = null
            },
            onDismiss = { editingTriggerId = null },
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
            onRegisterNfcRegistrationListener = onRegisterNfcRegistrationListener,
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
            modifier = Modifier.semantics { testTagsAsResourceId = true },
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
                        "grant Dry Toast the Alarms & reminders permission in system settings.",
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
            onDismissRequest = { profilePendingDeletionId = null },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
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
                        profilePendingDeletionId = null
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
                    onClick = { profilePendingDeletionId = null },
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
            onDismissRequest = { triggerPendingDeletionId = null },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
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
                        triggerPendingDeletionId = null
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
                    onClick = { triggerPendingDeletionId = null },
                    modifier = Modifier.testTag(ProfilesTestTags.DELETE_CANCEL_BUTTON),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun EmptyProfilesNotice() {
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

@Composable
private fun EmptyTriggersNotice() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No triggers yet.\nCreate reusable schedule or location triggers for your profiles.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddEntityOutlinedButton(label: String, testTag: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Icon(
            imageVector = DryToastIcons.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LocationTelemetryFooterText(locationTelemetry: LocationTelemetry) {
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
