package com.garan.threshold.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.TriggerRule
import com.garan.threshold.core.port.HapticEngine
import java.util.UUID

internal sealed interface TriggerAttachTarget {
    data object NewOrRule : TriggerAttachTarget
    data class AndInRule(val ruleIndex: Int) : TriggerAttachTarget
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
    onRegisterNfcScanListener: (((String) -> Unit)?) -> Unit = {},
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfilesTestTags.PROFILE_EDITOR_NAME_INPUT),
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
                        .height(48.dp)
                        .testTag(ProfilesTestTags.PROFILE_EDITOR_ADD_FIRST_TRIGGER),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ProfilesTestTags.profileRuleCard(ruleIndex)),
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
                                                is ProfileTrigger.Nfc -> ThresholdIcons.Nfc
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
                                            modifier = Modifier.testTag(
                                                ProfilesTestTags.profileRuleRemoveTrigger(ruleIndex, triggerId),
                                            ),
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
                                        .testTag(ProfilesTestTags.profileRuleTriggerRow(ruleIndex, triggerId))
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
                                    .padding(horizontal = 8.dp)
                                    .testTag(ProfilesTestTags.profileRuleAddAnd(ruleIndex)),
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
                        .height(48.dp)
                        .testTag(ProfilesTestTags.PROFILE_EDITOR_ADD_OR_RULE),
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
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(ProfilesTestTags.PROFILE_EDITOR_CANCEL_BUTTON),
                ) {
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
                    modifier = Modifier.testTag(ProfilesTestTags.PROFILE_EDITOR_SAVE_BUTTON),
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
                                    is ProfileTrigger.Nfc -> ThresholdIcons.Nfc
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
                            .testTag(ProfilesTestTags.triggerPickerItem(existing.id))
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
                        .height(48.dp)
                        .testTag(ProfilesTestTags.TRIGGER_PICKER_CREATE_NEW),
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
            onRegisterNfcScanListener = onRegisterNfcScanListener,
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
            onRegisterNfcScanListener = onRegisterNfcScanListener,
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
