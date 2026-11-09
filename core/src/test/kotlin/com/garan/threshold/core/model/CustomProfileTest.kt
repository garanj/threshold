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
}
