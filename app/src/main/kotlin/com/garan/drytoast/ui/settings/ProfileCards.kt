package com.garan.drytoast.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garan.drytoast.core.model.AppAssignment
import com.garan.drytoast.core.model.CustomProfile
import com.garan.drytoast.core.model.LocationFix
import com.garan.drytoast.core.model.ProfileDefaultRule
import com.garan.drytoast.core.model.ProfileTrigger
import com.garan.drytoast.core.model.SavedTrigger
import com.garan.drytoast.ui.theme.profileBaseColor

/**
 * Material 3 card for a reusable [SavedTrigger] in the `Triggers` tab of [ProfilesScreen].
 */
@Composable
internal fun TriggerCard(
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
                        is ProfileTrigger.Schedule -> DryToastIcons.Schedule
                        is ProfileTrigger.Geo -> DryToastIcons.LocationOn
                        is ProfileTrigger.Nfc -> DryToastIcons.Nfc
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
                        imageVector = DryToastIcons.Delete,
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
internal fun ProfileCard(
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
        val ruleCounts = "$blockedCount Blocked · $guardedCount Guarded · $allowCount Allow"
        val defaultPrefix = when (profile.defaultRule) {
            ProfileDefaultRule.FALLTHROUGH -> null
            ProfileDefaultRule.ALLOW_ALL -> "Default: Allow all"
            ProfileDefaultRule.GUARD_ALL -> "Default: Guard all"
            ProfileDefaultRule.BLOCK_ALL -> "Default: Block all"
        }
        if (defaultPrefix != null) "$defaultPrefix · $ruleCounts" else ruleCounts
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
                                imageVector = DryToastIcons.Schedule,
                                contentDescription = "Schedule profile",
                                modifier = Modifier.size(22.dp),
                                tint = iconTint,
                            )
                        }
                        if (hasGeo) {
                            Icon(
                                imageVector = DryToastIcons.LocationOn,
                                contentDescription = "Geo profile",
                                modifier = Modifier.size(22.dp),
                                tint = iconTint,
                            )
                        }
                        if (hasNfc) {
                            Icon(
                                imageVector = DryToastIcons.Nfc,
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
                                        imageVector = DryToastIcons.KeyboardArrowUp,
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
                                        imageVector = DryToastIcons.KeyboardArrowDown,
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
                        imageVector = DryToastIcons.Delete,
                        contentDescription = "Delete Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
