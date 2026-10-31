package com.garan.threshold.core.gatekeeper

import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.model.Profile
import com.garan.threshold.core.port.LaunchResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntentGatekeeperTest {

    private val gatekeeper = IntentGatekeeper()

    private val dialerPackage = "com.google.android.dialer"
    private val dialerComponent = ComponentKey(dialerPackage, "DialtactsActivity")
    private val clockComponent = ComponentKey("com.google.android.deskclock", "DeskClock")
    private val slackComponent = ComponentKey("com.Slack", "MainActivity")
    private val instagramComponent = ComponentKey("com.instagram.android", "MainActivity")

    private val allApps = listOf(dialerComponent, clockComponent, slackComponent, instagramComponent)

    @Test
    fun `filterVisible retains Direct and Guarded apps while filtering Blocked apps`() {
        val workProfile = Profile(
            id = "work",
            name = "Work",
            defaultAccess = AppAccessState.Direct,
            appRules = mapOf(
                slackComponent to AppAccessState.Guarded(FrictionPolicy.TypedIntention),
                instagramComponent to AppAccessState.Blocked,
            ),
        )

        val visible = gatekeeper.filterVisible(allApps, workProfile, dialerPackage)

        assertThat(visible).containsExactly(dialerComponent, clockComponent, slackComponent).inOrder()
        assertThat(visible).doesNotContain(instagramComponent)
    }

    @Test
    fun `universal dialer allowlist invariant guarantees dialer is visible even in lockdown`() {
        val lockdownProfile = Profile(
            id = "lockdown",
            name = "Bedtime Lockdown",
            defaultAccess = AppAccessState.Blocked,
            appRules = mapOf(
                dialerComponent to AppAccessState.Blocked, // Malicious or misconfigured rule
            ),
        )

        val visible = gatekeeper.filterVisible(allApps, lockdownProfile, dialerPackage)

        assertThat(visible).containsExactly(dialerComponent)
    }

    @Test
    fun `resolveAccess guarantees dialer is always Direct`() {
        val lockdownProfile = Profile(
            id = "lockdown",
            name = "Lockdown",
            defaultAccess = AppAccessState.Blocked,
            appRules = mapOf(
                dialerComponent to AppAccessState.Blocked,
            ),
        )

        val access = gatekeeper.resolveAccess(dialerComponent, lockdownProfile, dialerPackage)
        assertThat(access).isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun `evaluateLaunch returns Launched for Direct apps`() {
        val ambientProfile = Profile(id = "ambient", name = "Ambient")
        val result = gatekeeper.evaluateLaunch(dialerComponent, ambientProfile, dialerPackage)

        assertThat(result).isEqualTo(LaunchResult.Launched)
    }

    @Test
    fun `evaluateLaunch returns FrictionRequired for Guarded apps`() {
        val profile = Profile(
            id = "focus",
            name = "Focus",
            appRules = mapOf(
                slackComponent to AppAccessState.Guarded(FrictionPolicy.TimedDelay(10)),
            ),
        )

        val result = gatekeeper.evaluateLaunch(slackComponent, profile, dialerPackage)
        assertThat(result).isEqualTo(LaunchResult.FrictionRequired(FrictionPolicy.TimedDelay(10)))
    }

    @Test
    fun `evaluateLaunch returns Blocked for Blocked apps`() {
        val profile = Profile(
            id = "focus",
            name = "Focus",
            appRules = mapOf(
                instagramComponent to AppAccessState.Blocked,
            ),
        )

        val result = gatekeeper.evaluateLaunch(instagramComponent, profile, dialerPackage)
        assertThat(result).isEqualTo(LaunchResult.Blocked)
    }

    @Test
    fun `evaluateLaunch with dialer always returns Launched regardless of profile blockage`() {
        val strictProfile = Profile(
            id = "strict",
            name = "Strict",
            defaultAccess = AppAccessState.Blocked,
            appRules = mapOf(
                dialerComponent to AppAccessState.Blocked,
            ),
        )

        val result = gatekeeper.evaluateLaunch(dialerComponent, strictProfile, dialerPackage)
        assertThat(result).isEqualTo(LaunchResult.Launched)
    }
}
