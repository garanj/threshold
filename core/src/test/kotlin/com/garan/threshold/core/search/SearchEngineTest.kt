package com.garan.threshold.core.search

import com.garan.threshold.core.gatekeeper.IntentGatekeeper
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.model.Profile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchEngineTest {

    private val gatekeeper = IntentGatekeeper()
    private val matcher = FuzzyMatcher()
    private val engine = SearchEngine(gatekeeper, matcher)

    private val dialerKey = ComponentKey("com.google.android.dialer", "DialtactsActivity")
    private val clockKey = ComponentKey("com.google.android.deskclock", "DeskClock")
    private val slackKey = ComponentKey("com.Slack", "MainActivity")
    private val youtubeKey = ComponentKey("com.google.android.youtube", "HomeActivity")

    private val installedApps = listOf(
        AppSearchCandidate(dialerKey, "Phone"),
        AppSearchCandidate(clockKey, "Clock"),
        AppSearchCandidate(slackKey, "Slack"),
        AppSearchCandidate(youtubeKey, "YouTube"),
    )

    private val launcherActions = listOf(
        ActionCandidate("settings", "Launcher Settings", shortcutKeyword = "settings"),
        ActionCandidate("profiles", "Edit Profiles", shortcutKeyword = "profiles"),
    )

    private val defaultDialerPackage = "com.google.android.dialer"

    @Test
    fun `blocked apps never appear in search results`() {
        val workProfile = Profile(
            id = "work",
            name = "Work",
            appRules = mapOf(
                youtubeKey to AppAccessState.Blocked,
            ),
        )

        val results = engine.search(
            query = "you",
            candidates = installedApps,
            actions = launcherActions,
            profile = workProfile,
            defaultDialerPackage = defaultDialerPackage,
        )

        assertThat(results).isEmpty()
    }

    @Test
    fun `dialer appears in search results even during strict bedtime lockout`() {
        val bedtimeProfile = Profile(
            id = "bedtime",
            name = "Bedtime",
            defaultAccess = AppAccessState.Blocked,
        )

        val results = engine.search(
            query = "pho",
            candidates = installedApps,
            actions = launcherActions,
            profile = bedtimeProfile,
            defaultDialerPackage = defaultDialerPackage,
        )

        assertThat(results).hasSize(1)
        val match = results.first()
        assertThat((match.item as SearchItem.App).component).isEqualTo(dialerKey)
        assertThat(match.item.accessState).isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun `colon or comma prefix restricts search to launcher actions`() {
        val ambientProfile = Profile(id = "ambient", name = "Ambient")

        val colonResults = engine.search(
            query = ":set",
            candidates = installedApps,
            actions = launcherActions,
            profile = ambientProfile,
            defaultDialerPackage = defaultDialerPackage,
        )

        assertThat(colonResults).hasSize(1)
        assertThat((colonResults.first().item as SearchItem.Action).id).isEqualTo("settings")

        val commaResults = engine.search(
            query = ",prof",
            candidates = installedApps,
            actions = launcherActions,
            profile = ambientProfile,
            defaultDialerPackage = defaultDialerPackage,
        )

        assertThat(commaResults).hasSize(1)
        assertThat((commaResults.first().item as SearchItem.Action).id).isEqualTo("profiles")
    }

    @Test
    fun `reverse search drag prevents auto-launch on unique match for guarded apps`() {
        val focusProfile = Profile(
            id = "focus",
            name = "Focus",
            appRules = mapOf(
                slackKey to AppAccessState.Guarded(FrictionPolicy.TypedIntention),
            ),
        )

        // Query "sla" uniquely matches Slack, but Slack is Guarded.
        val searchResponse = engine.searchWithAutoLaunch(
            query = "sla",
            candidates = installedApps,
            actions = launcherActions,
            profile = focusProfile,
            defaultDialerPackage = defaultDialerPackage,
        )

        assertThat(searchResponse.results).hasSize(1)
        // Reverse search drag prevents auto-launch on partial prefix
        assertThat(searchResponse.autoLaunchCandidate).isNull()
    }

    @Test
    fun `direct app auto-launches on unique match when prefix matches`() {
        val ambientProfile = Profile(id = "ambient", name = "Ambient")

        // Query "clo" uniquely matches Clock, which is Direct.
        val searchResponse = engine.searchWithAutoLaunch(
            query = "clo",
            candidates = installedApps,
            actions = launcherActions,
            profile = ambientProfile,
            defaultDialerPackage = defaultDialerPackage,
        )

        assertThat(searchResponse.results).hasSize(1)
        assertThat(searchResponse.autoLaunchCandidate).isNotNull()
        assertThat((searchResponse.autoLaunchCandidate as SearchItem.App).component).isEqualTo(clockKey)
    }
}
