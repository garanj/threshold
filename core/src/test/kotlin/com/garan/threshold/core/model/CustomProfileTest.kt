package com.garan.threshold.core.model

import com.garan.threshold.core.port.ProfileCapabilityChecker
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CustomProfileTest {

    @Test
    fun initialLetter_extractsFirstUppercaseCharacter() {
        val profile = CustomProfile(id = "p1", name = "  deep work ")
        assertThat(profile.initialLetter).isEqualTo("D")
    }

    @Test
    fun nextColorIndex_assignsFirstUnusedSlotAndReusesDeletedSlots() {
        val p0 = CustomProfile(id = "0", name = "P0", colorIndex = 0)
        val p1 = CustomProfile(id = "1", name = "P1", colorIndex = 1)
        val p2 = CustomProfile(id = "2", name = "P2", colorIndex = 2)

        // Next after 0, 1, 2 is 3
        assertThat(CustomProfile.nextColorIndex(listOf(p0, p1, p2))).isEqualTo(3)

        // If p1 (colorIndex = 1) was deleted, nextColorIndex reuses slot 1
        assertThat(CustomProfile.nextColorIndex(listOf(p0, p2))).isEqualTo(1)

        // When all 6 palette slots (0..5) are used, wraps around deterministically
        val allSix = (0 until CustomProfile.PALETTE_SIZE).map { idx ->
            CustomProfile(id = "id-$idx", name = "P$idx", colorIndex = idx)
        }
        assertThat(CustomProfile.nextColorIndex(allSix)).isEqualTo(0)
    }

    @Test
    fun constructor_enforcesInvariants() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomProfile(id = " ", name = "Valid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomProfile(id = "valid", name = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomProfile(id = "valid", name = "Valid", priority = -1)
        }
    }

    @Test
    fun findCapabilityIssues_reportsOnlyEnabledProfilesWithMissingCapabilitiesInPriorityOrder() {
        val enabledHigh = CustomProfile(id = "p1", name = "Work", enabled = true, priority = 0)
        val disabledMid = CustomProfile(id = "p2", name = "Disabled", enabled = false, priority = 1)
        val enabledLow = CustomProfile(id = "p3", name = "Evening", enabled = true, priority = 2)

        val profiles = listOf(enabledLow, disabledMid, enabledHigh)

        val deniedChecker = ProfileCapabilityChecker { false }
        val issues = deniedChecker.findCapabilityIssues(profiles)

        assertThat(issues).containsExactly(
            ProfileCapabilityIssue(enabledHigh, setOf(ProfileCapability.EXACT_ALARM)),
            ProfileCapabilityIssue(enabledLow, setOf(ProfileCapability.EXACT_ALARM)),
        ).inOrder()

        val grantedChecker = ProfileCapabilityChecker { true }
        assertThat(grantedChecker.findCapabilityIssues(profiles)).isEmpty()
    }

    @Test
    fun withoutTrigger_stripsTriggerAndDisablesProfileWhenNoTriggersRemain() {
        val profile = CustomProfile(
            id = "work",
            name = "Work",
            enabled = true,
            trigger = null,
            activationRules = listOf(
                TriggerRule(listOf("t-schedule", "t-office")),
                TriggerRule(listOf("t-home")),
            ),
        )

        // 1. Delete t-schedule -> first rule keeps ["t-office"], second rule keeps ["t-home"], still enabled
        val afterFirstDelete = profile.withoutTrigger("t-schedule")
        assertThat(afterFirstDelete.enabled).isTrue()
        assertThat(afterFirstDelete.activationRules).containsExactly(
            TriggerRule(listOf("t-office")),
            TriggerRule(listOf("t-home")),
        ).inOrder()

        // 2. Delete t-office -> first rule becomes empty and is dropped; second rule ["t-home"] remains, still enabled
        val afterSecondDelete = afterFirstDelete.withoutTrigger("t-office")
        assertThat(afterSecondDelete.enabled).isTrue()
        assertThat(afterSecondDelete.activationRules).containsExactly(
            TriggerRule(listOf("t-home")),
        )

        // 3. Delete t-home -> no rules remain; profile is automatically disabled and never active
        val afterFinalDelete = afterSecondDelete.withoutTrigger("t-home")
        assertThat(afterFinalDelete.enabled).isFalse()
        assertThat(afterFinalDelete.trigger).isNull()
        assertThat(afterFinalDelete.activationRules).isEmpty()
        assertThat(
            afterFinalDelete.copy(enabled = true).isActive(
                currentDay = DayOfWeek.MONDAY,
                currentTime = TimeOfDay(10, 0),
                activeTriggerIds = setOf("t-home"),
            ),
        ).isFalse()
    }

    @Test
    fun findCapabilityIssues_withSavedTriggers_reportsUnionOfReferencedCapabilities() {
        val scheduleTrigger = SavedTrigger(
            id = "t-sched",
            name = "Work Hours",
            trigger = ProfileTrigger.Schedule(),
        )
        val geoTrigger = SavedTrigger(
            id = "t-geo",
            name = "Office",
            trigger = ProfileTrigger.Geo(),
        )
        val combinedProfile = CustomProfile(
            id = "combined",
            name = "Combined",
            enabled = true,
            priority = 0,
            trigger = null,
            activationRules = listOf(TriggerRule(listOf("t-sched", "t-geo"))),
        )

        val deniedChecker = ProfileCapabilityChecker { false }
        val issues = deniedChecker.findCapabilityIssues(
            profiles = listOf(combinedProfile),
            savedTriggers = listOf(scheduleTrigger, geoTrigger),
        )

        assertThat(issues).containsExactly(
            ProfileCapabilityIssue(
                combinedProfile,
                setOf(ProfileCapability.EXACT_ALARM, ProfileCapability.BACKGROUND_LOCATION),
            ),
        )
    }
}
