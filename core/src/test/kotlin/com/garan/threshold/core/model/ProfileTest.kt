package com.garan.threshold.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileTest {

    private val dialerComponent = ComponentKey("com.google.android.dialer", "DialtactsActivity")
    private val clockComponent = ComponentKey("com.google.android.deskclock", "DeskClock")
    private val slackComponent = ComponentKey("com.Slack", "MainActivity")
    private val socialComponent = ComponentKey("com.instagram.android", "MainActivity")

    @Test
    fun `profile with blank id throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            Profile(
                id = "",
                name = "Bedtime",
            )
        }
    }

    @Test
    fun `profile with blank name throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            Profile(
                id = "bedtime",
                name = "   ",
            )
        }
    }

    @Test
    fun `universal dialer allowlist invariant always resolves to Direct`() {
        val bedtimeProfile = Profile(
            id = "bedtime",
            name = "Bedtime",
            defaultAccess = AppAccessState.Blocked,
            appRules = mapOf(
                dialerComponent to AppAccessState.Blocked, // Explicit attempt to block dialer
            ),
        )

        // Regardless of explicit Blocked rule in profile, isDefaultDialer = true MUST return Direct
        val dialerAccess = bedtimeProfile.resolveAccess(dialerComponent, isDefaultDialer = true)
        assertThat(dialerAccess).isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun `explicit app rules override default access`() {
        val workProfile = Profile(
            id = "work",
            name = "Work",
            defaultAccess = AppAccessState.Direct,
            appRules = mapOf(
                slackComponent to AppAccessState.Guarded(FrictionPolicy.TypedIntention),
                socialComponent to AppAccessState.Blocked,
            ),
        )

        val slackAccess = workProfile.resolveAccess(slackComponent, isDefaultDialer = false)
        assertThat(slackAccess).isEqualTo(AppAccessState.Guarded(FrictionPolicy.TypedIntention))

        val socialAccess = workProfile.resolveAccess(socialComponent, isDefaultDialer = false)
        assertThat(socialAccess).isEqualTo(AppAccessState.Blocked)

        val unlistedClockAccess = workProfile.resolveAccess(clockComponent, isDefaultDialer = false)
        assertThat(unlistedClockAccess).isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun `package level rule matches when activity differs`() {
        val packageRuleKey = ComponentKey("com.google.android.youtube", "*")
        val profile = Profile(
            id = "test",
            name = "Test",
            defaultAccess = AppAccessState.Direct,
            appRules = mapOf(
                packageRuleKey to AppAccessState.Guarded(FrictionPolicy.TypedIntention),
            ),
        )

        val actualYouTubeComponent =
            ComponentKey("com.google.android.youtube", "com.google.android.apps.youtube.app.HomeActivity")
        assertThat(profile.resolveAccess(actualYouTubeComponent, isDefaultDialer = false))
            .isEqualTo(AppAccessState.Guarded(FrictionPolicy.TypedIntention))
    }

    @Test
    fun `restrictive profile with default Blocked allows explicit Direct apps`() {
        val lockedProfile = Profile(
            id = "locked",
            name = "Locked",
            defaultAccess = AppAccessState.Blocked,
            appRules = mapOf(
                clockComponent to AppAccessState.Direct,
            ),
        )

        assertThat(lockedProfile.resolveAccess(clockComponent, isDefaultDialer = false))
            .isEqualTo(AppAccessState.Direct)

        assertThat(lockedProfile.resolveAccess(socialComponent, isDefaultDialer = false))
            .isEqualTo(AppAccessState.Blocked)
    }

    @Test
    fun `profile copy maintains immutability`() {
        val base = Profile(
            id = "default",
            name = "Default",
            dndPolicy = DndPolicy.Off,
        )

        val updated = base.copy(
            dndPolicy = DndPolicy.PriorityOnly,
            appRules = mapOf(socialComponent to AppAccessState.Blocked),
        )

        assertThat(base.dndPolicy).isEqualTo(DndPolicy.Off)
        assertThat(base.appRules).isEmpty()
        assertThat(updated.dndPolicy).isEqualTo(DndPolicy.PriorityOnly)
        assertThat(updated.appRules).containsKey(socialComponent)
    }
}
