package com.garan.threshold.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garan.threshold.R
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.ui.motion.ThresholdMotion
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.CharcoalGray
import com.garan.threshold.ui.theme.HairlineBorder
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray
import kotlinx.collections.immutable.ImmutableList

/**
 * Filter categories for auditing and managing app assignments.
 */
enum class AppListFilter {
    ALL,
    BLOCKED,
    GUARDED,
    ALLOW,
    DEFAULT,
}

@Composable
fun ConfigureAppsScreen(
    availableApps: ImmutableList<InstalledApp>,
    initialAssignments: Map<ComponentKey, AppAssignment>,
    defaultFriction: FrictionChoice,
    onSave: (Map<ComponentKey, AppAssignment>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEngine: HapticEngine = NoOpHapticEngine,
    isCustomProfile: Boolean = false,
    customProfileName: String? = null,
) {
    BackHandler(onBack = onBack)

    // In-memory working copy of assignments
    val assignments = remember {
        mutableStateMapOf<ComponentKey, AppAssignment>().apply {
            putAll(initialAssignments)
        }
    }

    var selectedFilter by remember { mutableStateOf(AppListFilter.ALL) }
    var expandedAppKey by remember { mutableStateOf<ComponentKey?>(null) }

    // Efficient derived counts for header bar
    val blockedCount by remember {
        derivedStateOf {
            availableApps.count { assignments[it.component] is AppAssignment.Blocked }
        }
    }
    val guardedCount by remember {
        derivedStateOf {
            availableApps.count { assignments[it.component] is AppAssignment.Guarded }
        }
    }
    val allowCount by remember {
        derivedStateOf {
            availableApps.count {
                val current = assignments[it.component]
                if (isCustomProfile) {
                    current is AppAssignment.Standard
                } else {
                    current == null || current is AppAssignment.Standard
                }
            }
        }
    }
    val defaultCount by remember {
        derivedStateOf {
            if (isCustomProfile) {
                availableApps.count { assignments[it.component] == null }
            } else {
                null
            }
        }
    }
    val allCount by remember {
        derivedStateOf { availableApps.size }
    }

    // Filtered unified alphabetical list
    val filteredApps by remember(availableApps) {
        derivedStateOf {
            availableApps
                .filter { app ->
                    val assignment = assignments[app.component]
                    when (selectedFilter) {
                        AppListFilter.ALL -> true
                        AppListFilter.BLOCKED -> assignment is AppAssignment.Blocked
                        AppListFilter.GUARDED -> assignment is AppAssignment.Guarded
                        AppListFilter.ALLOW -> if (isCustomProfile) {
                            assignment is AppAssignment.Standard
                        } else {
                            assignment == null || assignment is AppAssignment.Standard
                        }
                        AppListFilter.DEFAULT -> assignment == null
                    }
                }
                .sortedBy { it.label.lowercase() }
        }
    }

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
                    contentDescription = stringResource(R.string.action_go_back),
                    tint = PureWhite,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCustomProfile) {
                    customProfileName ?: "Configure Apps"
                } else {
                    stringResource(R.string.action_configure_default_apps)
                },
                style = MaterialTheme.typography.titleLarge,
                color = PureWhite,
            )
        }

        // Minimalist Filter Counter Header Bar
        FilterCounterHeaderBar(
            selectedFilter = selectedFilter,
            onSelectFilter = { filter ->
                if (filter != selectedFilter) {
                    hapticEngine.rotaryDetent()
                    selectedFilter = filter
                }
            },
            allCount = allCount,
            blockedCount = blockedCount,
            guardedCount = guardedCount,
            allowCount = allowCount,
            defaultCount = defaultCount,
        )

        HorizontalDivider(color = HairlineBorder, thickness = 1.dp)

        // Unified Alphabetical List
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (selectedFilter) {
                        AppListFilter.BLOCKED -> "NO BLOCKED APPS"
                        AppListFilter.GUARDED -> "NO GUARDED APPS"
                        AppListFilter.ALLOW -> "NO ALLOWED APPS"
                        AppListFilter.DEFAULT -> "NO DEFAULT APPS"
                        AppListFilter.ALL -> "NO APPS INSTALLED"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubtleGray,
                    letterSpacing = 1.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(filteredApps, key = { it.component.id }) { app ->
                    val assignment = assignments[app.component]
                    val isExpanded = expandedAppKey == app.component

                    UnifiedAppRow(
                        app = app,
                        assignment = assignment,
                        defaultFriction = defaultFriction,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedAppKey = if (isExpanded) null else app.component
                        },
                        onSelectToggle = { toggleState ->
                            when (toggleState) {
                                BaseToggleState.ALLOW -> {
                                    assignments.remove(app.component)
                                    if (isExpanded) expandedAppKey = null
                                }
                                BaseToggleState.GUARD -> {
                                    val existing = assignments[app.component] as? AppAssignment.Guarded
                                    assignments[app.component] = existing ?: AppAssignment.Guarded(null)
                                    expandedAppKey = app.component
                                }
                                BaseToggleState.BLOCK -> {
                                    assignments[app.component] = AppAssignment.Blocked
                                    if (isExpanded) expandedAppKey = null
                                }
                            }
                        },
                        onSelectOverrideToggle = { override ->
                            when (override) {
                                null -> {
                                    assignments.remove(app.component)
                                    if (isExpanded) expandedAppKey = null
                                }
                                BaseToggleState.ALLOW -> {
                                    assignments[app.component] = AppAssignment.Standard
                                    if (isExpanded) expandedAppKey = null
                                }
                                BaseToggleState.GUARD -> {
                                    val existing = assignments[app.component] as? AppAssignment.Guarded
                                    assignments[app.component] = existing ?: AppAssignment.Guarded(null)
                                    expandedAppKey = app.component
                                }
                                BaseToggleState.BLOCK -> {
                                    assignments[app.component] = AppAssignment.Blocked
                                    if (isExpanded) expandedAppKey = null
                                }
                            }
                        },
                        onUpdateGuardedAssignment = { updated ->
                            assignments[app.component] = updated
                        },
                        hapticEngine = hapticEngine,
                        isCustomProfile = isCustomProfile,
                    )
                }
            }
        }

        HorizontalDivider(color = HairlineBorder, thickness = 1.dp)

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
                    .height(52.dp),
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureWhite,
                    contentColor = AmoledBlack,
                ),
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

/**
 * Horizontal filter and count summary bar.
 */
@Composable
private fun FilterCounterHeaderBar(
    selectedFilter: AppListFilter,
    onSelectFilter: (AppListFilter) -> Unit,
    allCount: Int,
    blockedCount: Int,
    guardedCount: Int,
    allowCount: Int,
    defaultCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AmoledBlack)
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterPill(
            label = "ALL ($allCount)",
            isSelected = selectedFilter == AppListFilter.ALL,
            onClick = { onSelectFilter(AppListFilter.ALL) },
        )
        if (defaultCount != null) {
            FilterPill(
                label = "DEFAULT ($defaultCount)",
                isSelected = selectedFilter == AppListFilter.DEFAULT,
                onClick = { onSelectFilter(AppListFilter.DEFAULT) },
            )
        }
        FilterPill(
            label = "BLOCKED ($blockedCount)",
            isSelected = selectedFilter == AppListFilter.BLOCKED,
            onClick = { onSelectFilter(AppListFilter.BLOCKED) },
        )
        FilterPill(
            label = "GUARDED ($guardedCount)",
            isSelected = selectedFilter == AppListFilter.GUARDED,
            onClick = { onSelectFilter(AppListFilter.GUARDED) },
        )
        FilterPill(
            label = "ALLOW ($allowCount)",
            isSelected = selectedFilter == AppListFilter.ALLOW,
            onClick = { onSelectFilter(AppListFilter.ALLOW) },
        )
    }
}

@Composable
private fun FilterPill(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(2.dp)

    Box(
        modifier = modifier
            .height(30.dp)
            .clip(shape)
            .background(if (isSelected) PureWhite else CharcoalGray)
            .border(1.dp, if (isSelected) PureWhite else HairlineBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) AmoledBlack else SubtleGray,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.5.sp,
        )
    }
}

/**
 * Single app row in the unified alphabetical list.
 */
@Composable
private fun UnifiedAppRow(
    app: InstalledApp,
    assignment: AppAssignment?,
    defaultFriction: FrictionChoice,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectToggle: (BaseToggleState) -> Unit,
    onUpdateGuardedAssignment: (AppAssignment.Guarded) -> Unit,
    hapticEngine: HapticEngine,
    modifier: Modifier = Modifier,
    isCustomProfile: Boolean = false,
    onSelectOverrideToggle: (BaseToggleState?) -> Unit = {},
) {
    val currentToggleState = when (assignment) {
        is AppAssignment.Blocked -> BaseToggleState.BLOCK
        is AppAssignment.Guarded -> BaseToggleState.GUARD
        else -> BaseToggleState.ALLOW
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = assignment is AppAssignment.Guarded,
                onClick = onToggleExpand,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (currentToggleState == BaseToggleState.BLOCK) SubtleGray else PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (assignment is AppAssignment.Guarded) {
                    val effectiveFriction = assignment.frictionOverride ?: defaultFriction
                    val overrideSubtitle = if (assignment.frictionOverride != null) {
                        "${effectiveFriction.name.lowercase().replace('_', ' ')} (override)"
                    } else {
                        "default (${defaultFriction.name.lowercase().replace('_', ' ')})"
                    }
                    Text(
                        text = "GUARD · $overrideSubtitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp,
                        letterSpacing = 0.3.sp,
                    )
                }
            }

            // Precision Multi-State Toggle (Base or Profile Override)
            if (isCustomProfile) {
                ThresholdProfileOverrideToggle(
                    overrideState = when (assignment) {
                        is AppAssignment.Standard -> BaseToggleState.ALLOW
                        is AppAssignment.Guarded -> BaseToggleState.GUARD
                        is AppAssignment.Blocked -> BaseToggleState.BLOCK
                        null -> null
                    },
                    onOverrideChange = onSelectOverrideToggle,
                    hapticEngine = hapticEngine,
                )
            } else {
                ThresholdMultiStateToggle(
                    options = BaseToggleState.entries,
                    selected = currentToggleState,
                    onSelect = onSelectToggle,
                    label = { it.label },
                    modifier = Modifier.width(200.dp),
                    hapticEngine = hapticEngine,
                    isResistanceState = { it == BaseToggleState.BLOCK },
                )
            }
        }

        // Expandable Friction Override Section for Guarded Apps
        AnimatedVisibility(
            visible = isExpanded && assignment is AppAssignment.Guarded,
            enter = expandVertically(
                animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_OPEN_DURATION_MS),
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_CLOSE_DURATION_MS),
            ) + fadeOut(),
        ) {
            if (assignment is AppAssignment.Guarded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414))
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    val hasOverride = assignment.frictionOverride != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.setting_override_friction),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureWhite,
                        )
                        Switch(
                            checked = hasOverride,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onUpdateGuardedAssignment(assignment.copy(frictionOverride = defaultFriction))
                                } else {
                                    onUpdateGuardedAssignment(assignment.copy(frictionOverride = null))
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = Color(0xFF444444),
                                uncheckedThumbColor = SubtleGray,
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
                                onUpdateGuardedAssignment(
                                    assignment.copy(frictionOverride = FrictionChoice.TYPED_INTENTION),
                                )
                            },
                        )
                        FrictionRadioOption(
                            title = stringResource(R.string.friction_drag_sort),
                            selected = activeChoice == FrictionChoice.DRAG_SORT,
                            onSelect = {
                                onUpdateGuardedAssignment(assignment.copy(frictionOverride = FrictionChoice.DRAG_SORT))
                            },
                        )
                        FrictionRadioOption(
                            title = stringResource(R.string.friction_caret_tracing),
                            selected = activeChoice == FrictionChoice.CARET_TRACING,
                            onSelect = {
                                onUpdateGuardedAssignment(
                                    assignment.copy(frictionOverride = FrictionChoice.CARET_TRACING),
                                )
                            },
                        )
                        FrictionRadioOption(
                            title = stringResource(R.string.friction_random),
                            selected = activeChoice == FrictionChoice.RANDOM,
                            onSelect = {
                                onUpdateGuardedAssignment(assignment.copy(frictionOverride = FrictionChoice.RANDOM))
                            },
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = HairlineBorder,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = PureWhite,
                unselectedColor = Color(0xFF666666),
            ),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) PureWhite else Color(0xFFAAAAAA),
        )
    }
}
