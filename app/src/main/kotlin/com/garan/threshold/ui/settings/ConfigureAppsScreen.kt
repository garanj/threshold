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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garan.threshold.R
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.ui.motion.ThresholdMotion
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

@OptIn(ExperimentalMaterial3Api::class)
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Reactive working map that persists immediately via onSave whenever mutated
    val assignments = remember(initialAssignments) {
        mutableStateMapOf<ComponentKey, AppAssignment>().apply {
            putAll(initialAssignments)
        }
    }

    fun commitAssignments() {
        onSave(assignments.toMap())
    }

    var selectedFilter by remember { mutableStateOf(AppListFilter.ALL) }
    var expandedAppKey by remember { mutableStateOf<ComponentKey?>(null) }

    // Efficient derived counts for filter chip bar
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

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isCustomProfile) {
                                customProfileName ?: stringResource(R.string.action_configure_default_apps)
                            } else {
                                stringResource(R.string.action_configure_default_apps)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = ThresholdIcons.ArrowBack,
                                contentDescription = stringResource(R.string.action_go_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                )

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

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding),
                contentPadding = innerPadding,
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
                            commitAssignments()
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
                            commitAssignments()
                        },
                        onUpdateGuardedAssignment = { updated ->
                            assignments[app.component] = updated
                            commitAssignments()
                        },
                        hapticEngine = hapticEngine,
                        isCustomProfile = isCustomProfile,
                    )
                }
            }
        }
    }
}

/**
 * Horizontal Material 3 FilterChip bar.
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
    val chips = buildList {
        add(AppListFilter.ALL to "ALL ($allCount)")
        if (defaultCount != null) {
            add(AppListFilter.DEFAULT to "DEFAULT ($defaultCount)")
        }
        add(AppListFilter.BLOCKED to "BLOCKED ($blockedCount)")
        add(AppListFilter.GUARDED to "GUARDED ($guardedCount)")
        add(AppListFilter.ALLOW to "ALLOW ($allowCount)")
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(chips, key = { it.first.name }) { (filter, label) ->
            val isSelected = selectedFilter == filter
            FilterChip(
                selected = isSelected,
                onClick = { onSelectFilter(filter) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                shape = RoundedCornerShape(4.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
}

/**
 * Single app row in the unified alphabetical list.
 *
 * In Base Profile mode (`isCustomProfile == false`), renders a single-row layout with the app name on the
 * start and the 3-state [ThresholdMultiStateToggle] on the end.
 *
 * In Custom Profile mode (`isCustomProfile == true`), stacks the full-width app name above the 4-state
 * [ThresholdProfileOverrideToggle] so app names are never crushed or truncated on narrow displays.
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

    val guardedSubtitle = if (assignment is AppAssignment.Guarded) {
        val effectiveFriction = assignment.frictionOverride ?: defaultFriction
        val overrideText = if (assignment.frictionOverride != null) {
            "${effectiveFriction.name.lowercase().replace('_', ' ')} (override)"
        } else {
            "default (${defaultFriction.name.lowercase().replace('_', ' ')})"
        }
        "GUARD · $overrideText"
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = assignment is AppAssignment.Guarded,
                onClick = onToggleExpand,
            ),
    ) {
        if (isCustomProfile) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (currentToggleState == BaseToggleState.BLOCK) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (guardedSubtitle != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = guardedSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ThresholdProfileOverrideToggle(
                    overrideState = when (assignment) {
                        is AppAssignment.Standard -> BaseToggleState.ALLOW
                        is AppAssignment.Guarded -> BaseToggleState.GUARD
                        is AppAssignment.Blocked -> BaseToggleState.BLOCK
                        null -> null
                    },
                    onOverrideChange = onSelectOverrideToggle,
                    modifier = Modifier.fillMaxWidth(),
                    hapticEngine = hapticEngine,
                )
            }
        } else {
            ListItem(
                headlineContent = {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (currentToggleState == BaseToggleState.BLOCK) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = guardedSubtitle?.let { subtitle ->
                    {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    ThresholdMultiStateToggle(
                        options = BaseToggleState.entries,
                        selected = currentToggleState,
                        onSelect = onSelectToggle,
                        label = { it.label },
                        modifier = Modifier.width(168.dp),
                        hapticEngine = hapticEngine,
                        isResistanceState = { it == BaseToggleState.BLOCK },
                    )
                },
            )
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
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(vertical = 4.dp),
                ) {
                    val hasOverride = assignment.frictionOverride != null
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_override_friction),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = hasOverride,
                                onCheckedChange = null,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = hasOverride,
                                role = Role.Switch,
                                onValueChange = { checked ->
                                    if (checked) {
                                        onUpdateGuardedAssignment(assignment.copy(frictionOverride = defaultFriction))
                                    } else {
                                        onUpdateGuardedAssignment(assignment.copy(frictionOverride = null))
                                    }
                                },
                            ),
                    )

                    if (hasOverride) {
                        val activeChoice = assignment.frictionOverride ?: defaultFriction
                        Column(modifier = Modifier.selectableGroup()) {
                            FrictionRadioOption(
                                title = stringResource(R.string.friction_typed_intention),
                                selected = activeChoice == FrictionChoice.TYPED_INTENTION,
                                onSelect = {
                                    onUpdateGuardedAssignment(
                                        assignment.copy(frictionOverride = FrictionChoice.TYPED_INTENTION),
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            )
                            FrictionRadioOption(
                                title = stringResource(R.string.friction_drag_sort),
                                selected = activeChoice == FrictionChoice.DRAG_SORT,
                                onSelect = {
                                    onUpdateGuardedAssignment(
                                        assignment.copy(frictionOverride = FrictionChoice.DRAG_SORT),
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            )
                            FrictionRadioOption(
                                title = stringResource(R.string.friction_caret_tracing),
                                selected = activeChoice == FrictionChoice.CARET_TRACING,
                                onSelect = {
                                    onUpdateGuardedAssignment(
                                        assignment.copy(frictionOverride = FrictionChoice.CARET_TRACING),
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            )
                            FrictionRadioOption(
                                title = stringResource(R.string.friction_random),
                                selected = activeChoice == FrictionChoice.RANDOM,
                                onSelect = {
                                    onUpdateGuardedAssignment(
                                        assignment.copy(frictionOverride = FrictionChoice.RANDOM),
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
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
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
) {
    ListItem(
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
    )
}
