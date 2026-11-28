package com.garan.drytoast.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.garan.drytoast.DryToastRoute

internal val SETTINGS_MEDIUM_MAX_WIDTH = 720.dp
internal val SETTINGS_GRID_BREAKPOINT = 720.dp
internal val SETTINGS_GRID_MAX_WIDTH = 960.dp
internal val SETTINGS_EXPANDED_BREAKPOINT = 840.dp
internal val SETTINGS_MASTER_PANE_WIDTH = 360.dp

object AdaptiveSettingsTestTags {
    const val TWO_PANE_ROOT = "settings_two_pane_root"
    const val MASTER_PANE = "settings_master_pane"
    const val DETAIL_PANE = "settings_detail_pane"
    const val HAIRLINE_DIVIDER = "settings_two_pane_divider"
    const val CONTENT_FRAME = "settings_content_frame"
}

enum class SettingsDetailSection {
    CONFIGURE_APPS,
    PROFILES,
    HARDENED_MODE,
    BREAK_GLASS,
}

internal val LocalIsSettingsMasterPane = compositionLocalOf { false }
internal val LocalIsSettingsMasterWithPushedDetail = compositionLocalOf { false }
internal val LocalSelectedSettingsDetail = compositionLocalOf<SettingsDetailSection?> { null }
internal val LocalHideSettingsDetailBackArrow = compositionLocalOf { false }
internal val LocalIsDefaultSettingsDetail = compositionLocalOf { false }

/**
 * Centers configuration list/scroll content within [maxWidth] (`720.dp` by default) on `MEDIUM`
 * (`600–839dp`) and `EXPANDED` (`>= 840dp`) panes while preserving edge-to-edge full width on
 * compact phones.
 */
@Composable
internal fun SettingsContentFrame(
    maxWidth: Dp = SETTINGS_MEDIUM_MAX_WIDTH,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .let { mod -> if (testTag != null) mod.testTag(testTag) else mod },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxSize(),
        ) {
            content()
        }
    }
}

/**
 * Navigates from a top-level Settings row while keeping the user-owned Navigation 3 back stack
 * free of sibling detail duplication in `EXPANDED` 2-pane mode:
 * - On compact/medium (`!isExpanded`), pushes [target] onto [backStack].
 * - On expanded (`isExpanded`), pops any active detail routes above [DryToastRoute.Settings] and
 *   pushes [target] unless [target] is the default [DryToastRoute.ConfigureApps] (`profileId == null`),
 *   which is already displayed alongside [DryToastRoute.Settings].
 */
internal fun <T : Any> navigateFromSettingsMaster(backStack: MutableList<T>, target: T, isExpanded: Boolean) {
    if (!isExpanded) {
        if (backStack.lastOrNull() != target) {
            backStack.add(target)
        }
        return
    }

    val settingsIndex = backStack.indexOfLast { it == DryToastRoute.Settings }
    if (settingsIndex >= 0) {
        while (backStack.size > settingsIndex + 1) {
            backStack.removeLastOrNull()
        }
    }
    if (target != DryToastRoute.ConfigureApps(profileId = null) && backStack.lastOrNull() != target) {
        backStack.add(target)
    }
}

/**
 * Exits the Settings hierarchy back to the route beneath [DryToastRoute.Settings] (normally
 * [DryToastRoute.Home]) when the user explicitly taps the Up/Back button in the master Settings
 * top bar.
 */
internal fun <T : Any> exitSettingsFromMaster(backStack: MutableList<T>) {
    val settingsIndex = backStack.indexOfLast { it == DryToastRoute.Settings }
    if (settingsIndex > 0) {
        while (backStack.size > settingsIndex) {
            backStack.removeLastOrNull()
        }
    } else if (backStack.size > 1) {
        backStack.removeLastOrNull()
    }
}

@Composable
internal fun <T : Any> rememberSettingsTwoPaneSceneStrategy(
    isExpanded: Boolean,
    defaultDetailContent: @Composable () -> Unit,
): SettingsTwoPaneSceneStrategy<T> {
    val latestDefaultDetail = rememberUpdatedState(defaultDetailContent)
    return remember(isExpanded) {
        SettingsTwoPaneSceneStrategy(
            isExpanded = isExpanded,
            defaultDetailContentState = latestDefaultDetail,
        )
    }
}

private data class SettingsTwoPaneSceneKey(val masterKey: Any, val detailKey: Any)

private class SettingsTwoPaneScene<T : Any>(
    override val key: Any,
    private val masterEntry: NavEntry<T>,
    private val detailEntry: NavEntry<T>?,
    override val previousEntries: List<NavEntry<T>>,
    private val selectedSection: SettingsDetailSection,
    private val isSubDetail: Boolean,
    private val defaultDetailContent: @Composable () -> Unit,
) : Scene<T> {

    override val entries: List<NavEntry<T>> =
        if (detailEntry != null) listOf(masterEntry, detailEntry) else listOf(masterEntry)

    override val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag(AdaptiveSettingsTestTags.TWO_PANE_ROOT),
        ) {
            Box(
                modifier = Modifier
                    .width(SETTINGS_MASTER_PANE_WIDTH)
                    .fillMaxHeight()
                    .testTag(AdaptiveSettingsTestTags.MASTER_PANE),
            ) {
                CompositionLocalProvider(
                    LocalIsSettingsMasterPane provides true,
                    LocalIsSettingsMasterWithPushedDetail provides (detailEntry != null),
                    LocalSelectedSettingsDetail provides selectedSection,
                ) {
                    masterEntry.Content()
                }
            }

            VerticalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .fillMaxHeight()
                    .testTag(AdaptiveSettingsTestTags.HAIRLINE_DIVIDER),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(AdaptiveSettingsTestTags.DETAIL_PANE),
            ) {
                CompositionLocalProvider(
                    LocalHideSettingsDetailBackArrow provides !isSubDetail,
                    LocalIsDefaultSettingsDetail provides (detailEntry == null),
                ) {
                    if (detailEntry != null) {
                        detailEntry.Content()
                    } else {
                        defaultDetailContent()
                    }
                }
            }
        }
    }
}

internal class SettingsTwoPaneSceneStrategy<T : Any>(
    private val isExpanded: Boolean,
    private val defaultDetailContentProvider: @Composable () -> Unit,
) : SceneStrategy<T> {

    constructor(
        isExpanded: Boolean,
        defaultDetailContentState: State<@Composable () -> Unit>,
    ) : this(
        isExpanded = isExpanded,
        defaultDetailContentProvider = { defaultDetailContentState.value.invoke() },
    )

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!isExpanded || entries.isEmpty()) return null

        val masterIndex = entries.indexOfLast { it.metadata[MASTER_KEY] == true }
        if (masterIndex < 0) return null

        val trailingEntries = entries.subList(masterIndex + 1, entries.size)
        if (trailingEntries.any { it.metadata[DETAIL_SECTION_KEY] !is SettingsDetailSection }) {
            // A non-detail full-screen route (e.g., DisableHardenedMode or SettingsLock) sits on top
            return null
        }

        val masterEntry = entries[masterIndex]
        val detailEntry = trailingEntries.lastOrNull()
        val selectedSection = (detailEntry?.metadata?.get(DETAIL_SECTION_KEY) as? SettingsDetailSection)
            ?: SettingsDetailSection.CONFIGURE_APPS
        val isSubDetail = detailEntry?.metadata?.get(DETAIL_IS_SUB_KEY) == true

        return SettingsTwoPaneScene(
            key = SettingsTwoPaneSceneKey(
                masterKey = masterEntry.contentKey,
                detailKey = detailEntry?.contentKey ?: DEFAULT_DETAIL_SCENE_KEY,
            ),
            masterEntry = masterEntry,
            detailEntry = detailEntry,
            previousEntries = entries.dropLast(1),
            selectedSection = selectedSection,
            isSubDetail = isSubDetail,
            defaultDetailContent = defaultDetailContentProvider,
        )
    }

    companion object {
        private const val MASTER_KEY = "drytoast.settings.master"
        private const val DETAIL_SECTION_KEY = "drytoast.settings.detail.section"
        private const val DETAIL_IS_SUB_KEY = "drytoast.settings.detail.sub"
        private const val DEFAULT_DETAIL_SCENE_KEY = "drytoast.settings.detail.default_configure_apps"

        fun masterMetadata(): Map<String, Any> = mapOf(MASTER_KEY to true)

        fun detailMetadata(section: SettingsDetailSection, isSubDetail: Boolean = false): Map<String, Any> = mapOf(
            DETAIL_SECTION_KEY to section,
            DETAIL_IS_SUB_KEY to isSubDetail,
        )
    }
}
