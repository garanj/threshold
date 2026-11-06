package com.garan.threshold.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garan.threshold.R
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.ui.motion.ThresholdMotion
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ConfigureAppsScreen(
    availableApps: ImmutableList<InstalledApp>,
    initialAssignments: Map<ComponentKey, AppAssignment>,
    defaultFriction: FrictionChoice,
    onSave: (Map<ComponentKey, AppAssignment>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    // In-memory working copy of assignments
    val assignments = remember {
        mutableStateMapOf<ComponentKey, AppAssignment>().apply {
            putAll(initialAssignments)
        }
    }

    var expandedAppKey by remember { mutableStateOf<ComponentKey?>(null) }

    // Partition available apps into Blocked, Guarded, Standard
    val blockedApps by remember(availableApps) {
        derivedStateOf {
            availableApps.filter { app ->
                assignments[app.component] is AppAssignment.Blocked
            }.sortedBy { it.label.lowercase() }
        }
    }

    val guardedApps by remember(availableApps) {
        derivedStateOf {
            availableApps.filter { app ->
                assignments[app.component] is AppAssignment.Guarded
            }.sortedBy { it.label.lowercase() }
        }
    }

    val standardApps by remember(availableApps) {
        derivedStateOf {
            availableApps.filter { app ->
                val current = assignments[app.component]
                current == null || current is AppAssignment.Standard
            }.sortedBy { it.label.lowercase() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = ThresholdIcons.ArrowBack,
                    contentDescription = stringResource(R.string.action_go_back),
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_configure_default_apps),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)

        // 3-Tier Categorized Lists
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // Section 1: BLOCKED
            item(key = "header_blocked") {
                SectionHeader(
                    title = stringResource(R.string.tier_blocked),
                    count = blockedApps.size,
                )
            }
            items(blockedApps, key = { "blocked_${it.component.id}" }) { app ->
                BlockedAppRow(
                    app = app,
                    onMoveToGuarded = {
                        assignments[app.component] = AppAssignment.Guarded(null)
                    },
                    onResetToStandard = {
                        assignments.remove(app.component)
                    },
                )
            }

            // Section 2: GUARDED
            item(key = "header_guarded") {
                SectionHeader(
                    title = stringResource(R.string.tier_guarded),
                    count = guardedApps.size,
                )
            }
            items(guardedApps, key = { "guarded_${it.component.id}" }) { app ->
                val currentAssignment = assignments[app.component] as? AppAssignment.Guarded
                val isExpanded = expandedAppKey == app.component
                GuardedAppRow(
                    app = app,
                    assignment = currentAssignment ?: AppAssignment.Guarded(null),
                    defaultFriction = defaultFriction,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedAppKey = if (isExpanded) null else app.component
                    },
                    onMoveToBlocked = {
                        assignments[app.component] = AppAssignment.Blocked
                        if (isExpanded) expandedAppKey = null
                    },
                    onResetToStandard = {
                        assignments.remove(app.component)
                        if (isExpanded) expandedAppKey = null
                    },
                    onUpdateAssignment = { updated ->
                        assignments[app.component] = updated
                    },
                )
            }

            // Section 3: STANDARD
            item(key = "header_standard") {
                SectionHeader(
                    title = stringResource(R.string.tier_standard),
                    count = standardApps.size,
                )
            }
            items(standardApps, key = { "standard_${it.component.id}" }) { app ->
                StandardAppRow(
                    app = app,
                    onMoveToBlocked = {
                        assignments[app.component] = AppAssignment.Blocked
                    },
                    onMoveToGuarded = {
                        assignments[app.component] = AppAssignment.Guarded(null)
                    },
                )
            }
        }

        HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)

        // Pinned Save Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Button(
                onClick = {
                    onSave(assignments.toMap())
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF111111))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF999999),
        )
        Text(
            text = if (count == 0) {
                stringResource(R.string.label_empty)
            } else if (count == 1) {
                stringResource(R.string.label_one_app)
            } else {
                stringResource(R.string.label_apps_count, count)
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF666666),
        )
    }
}

@Composable
private fun BlockedAppRow(
    app: InstalledApp,
    onMoveToGuarded: () -> Unit,
    onResetToStandard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF777777),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveToGuarded, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = ThresholdIcons.Shield,
                contentDescription = stringResource(R.string.cd_guard),
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onResetToStandard, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = ThresholdIcons.Delete,
                contentDescription = stringResource(R.string.cd_reset),
                tint = Color(0xFF666666),
                modifier = Modifier.size(20.dp),
            )
        }
    }
    HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun GuardedAppRow(
    app: InstalledApp,
    assignment: AppAssignment.Guarded,
    defaultFriction: FrictionChoice,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onMoveToBlocked: () -> Unit,
    onResetToStandard: () -> Unit,
    onUpdateAssignment: (AppAssignment.Guarded) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                val effectiveFriction = assignment.frictionOverride ?: defaultFriction
                Text(
                    text = if (assignment.frictionOverride != null) {
                        "${stringResource(
                            R.string.tier_guarded,
                        )} · ${effectiveFriction.name.lowercase().replace('_', ' ')} (override)"
                    } else {
                        "${stringResource(R.string.tier_guarded)} · default"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF888888),
                )
            }
            IconButton(onClick = onMoveToBlocked, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = ThresholdIcons.Block,
                    contentDescription = stringResource(R.string.cd_block),
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onResetToStandard, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = ThresholdIcons.Delete,
                    contentDescription = stringResource(R.string.cd_reset),
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Expanded friction override options
        AnimatedVisibility(
            visible = isExpanded,
            enter =
            expandVertically(
                animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_OPEN_DURATION_MS),
            ) +
                fadeIn(),
            exit =
            shrinkVertically(
                animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_CLOSE_DURATION_MS),
            ) +
                fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161616))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                val hasOverride = assignment.frictionOverride != null
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.setting_override_friction),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                    )
                    Switch(
                        checked = hasOverride,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onUpdateAssignment(assignment.copy(frictionOverride = defaultFriction))
                            } else {
                                onUpdateAssignment(assignment.copy(frictionOverride = null))
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF444444),
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF222222),
                        ),
                    )
                }

                if (hasOverride) {
                    val activeChoice = assignment.frictionOverride ?: defaultFriction
                    FrictionRadioOption(
                        title = stringResource(R.string.friction_typed_intention),
                        selected = activeChoice == FrictionChoice.TYPED_INTENTION,
                        onSelect = {
                            onUpdateAssignment(assignment.copy(frictionOverride = FrictionChoice.TYPED_INTENTION))
                        },
                    )
                    FrictionRadioOption(
                        title = stringResource(R.string.friction_drag_sort),
                        selected = activeChoice == FrictionChoice.DRAG_SORT,
                        onSelect = { onUpdateAssignment(assignment.copy(frictionOverride = FrictionChoice.DRAG_SORT)) },
                    )
                    FrictionRadioOption(
                        title = stringResource(R.string.friction_caret_tracing),
                        selected = activeChoice == FrictionChoice.CARET_TRACING,
                        onSelect = {
                            onUpdateAssignment(assignment.copy(frictionOverride = FrictionChoice.CARET_TRACING))
                        },
                    )
                    FrictionRadioOption(
                        title = stringResource(R.string.friction_random),
                        selected = activeChoice == FrictionChoice.RANDOM,
                        onSelect = { onUpdateAssignment(assignment.copy(frictionOverride = FrictionChoice.RANDOM)) },
                    )
                }
            }
        }
        HorizontalDivider(
            color = Color(0xFF1A1A1A),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun StandardAppRow(
    app: InstalledApp,
    onMoveToBlocked: () -> Unit,
    onMoveToGuarded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveToBlocked, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = ThresholdIcons.Block,
                contentDescription = stringResource(R.string.cd_block),
                tint = Color(0xFF888888),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onMoveToGuarded, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = ThresholdIcons.Shield,
                contentDescription = stringResource(R.string.cd_guard),
                tint = Color(0xFF888888),
                modifier = Modifier.size(20.dp),
            )
        }
    }
    HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
internal fun FrictionRadioOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.White,
                unselectedColor = Color(0xFF666666),
            ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Color.White else Color(0xFFAAAAAA),
        )
    }
}
