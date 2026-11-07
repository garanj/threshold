package com.garan.threshold.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.ThresholdTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThresholdMultiStateToggleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun verifyAndArchiveSnapshot(fileName: String) {
        val goldenFile = File("src/test/snapshots/$fileName")
        composeTestRule.onRoot().captureRoboImage(goldenFile)
        val ciArtifactFile = File("build/outputs/roborazzi/$fileName")
        if (goldenFile.exists()) {
            ciArtifactFile.parentFile?.mkdirs()
            goldenFile.copyTo(ciArtifactFile, overwrite = true)
        }
    }

    private class TestHapticEngine : HapticEngine {
        var rotaryDetentCount = 0
        var targetSnapCount = 0
        var resistanceDetentCount = 0
        var relaySnapCount = 0

        override fun rotaryDetent() {
            rotaryDetentCount++
        }

        override fun targetSnap() {
            targetSnapCount++
        }

        override fun resistanceDetent() {
            resistanceDetentCount++
        }

        override fun relaySnap() {
            relaySnapCount++
        }
    }

    @Test
    fun selectionChangesStateAndFiresRotaryHaptic() {
        val testHaptic = TestHapticEngine()
        var selectedState by mutableStateOf<BaseToggleState?>(BaseToggleState.ALLOW)

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdMultiStateToggle(
                    options = BaseToggleState.entries,
                    selected = selectedState,
                    onSelect = { selectedState = it },
                    label = { it.label },
                    hapticEngine = testHaptic,
                    isResistanceState = { it == BaseToggleState.BLOCK },
                )
            }
        }

        composeTestRule.onNodeWithText("ALLOW").assertIsSelected()

        composeTestRule.onNodeWithText("GUARD").performClick()

        assertThat(selectedState).isEqualTo(BaseToggleState.GUARD)
        assertThat(testHaptic.rotaryDetentCount).isEqualTo(1)
        assertThat(testHaptic.resistanceDetentCount).isEqualTo(0)
    }

    @Test
    fun selectingResistanceStateFiresResistanceDetentHaptic() {
        val testHaptic = TestHapticEngine()
        var selectedState by mutableStateOf<BaseToggleState?>(BaseToggleState.ALLOW)

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdMultiStateToggle(
                    options = BaseToggleState.entries,
                    selected = selectedState,
                    onSelect = { selectedState = it },
                    label = { it.label },
                    hapticEngine = testHaptic,
                    isResistanceState = { it == BaseToggleState.BLOCK },
                )
            }
        }

        composeTestRule.onNodeWithText("BLOCK").performClick()

        assertThat(selectedState).isEqualTo(BaseToggleState.BLOCK)
        assertThat(testHaptic.resistanceDetentCount).isEqualTo(1)
        assertThat(testHaptic.rotaryDetentCount).isEqualTo(0)
    }

    @Test
    fun profileOverrideToggleSwitchesBetweenDefaultAndOverrides() {
        val testHaptic = TestHapticEngine()
        var overrideState by mutableStateOf<BaseToggleState?>(null)

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdProfileOverrideToggle(
                    overrideState = overrideState,
                    onOverrideChange = { overrideState = it },
                    hapticEngine = testHaptic,
                )
            }
        }

        // Initially in DEFAULT
        assertThat(overrideState).isNull()

        // Click GUARD on the slider to override
        composeTestRule.onNodeWithText("GUARD").performClick()
        assertThat(overrideState).isEqualTo(BaseToggleState.GUARD)
        assertThat(testHaptic.rotaryDetentCount).isEqualTo(1)

        // Click DEFAULT button to revert back to default
        composeTestRule.onNodeWithText("DEFAULT").performClick()
        assertThat(overrideState).isNull()
        assertThat(testHaptic.rotaryDetentCount).isEqualTo(2)
    }

    @Test
    fun snapshotBaseProfile3StateToggles() {
        composeTestRule.setContent {
            ThresholdTheme {
                Column(
                    modifier = Modifier
                        .background(AmoledBlack)
                        .padding(24.dp)
                        .width(320.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("3-STATE TOGGLE POSITIONS (BASE PROFILE)", color = PureWhite, fontSize = 12.sp)

                    ThresholdMultiStateToggle(
                        options = BaseToggleState.entries,
                        selected = BaseToggleState.ALLOW,
                        onSelect = {},
                        label = { it.label },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ThresholdMultiStateToggle(
                        options = BaseToggleState.entries,
                        selected = BaseToggleState.GUARD,
                        onSelect = {},
                        label = { it.label },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ThresholdMultiStateToggle(
                        options = BaseToggleState.entries,
                        selected = BaseToggleState.BLOCK,
                        onSelect = {},
                        label = { it.label },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        verifyAndArchiveSnapshot("multi_state_toggle_3state.png")
    }

    @Test
    fun snapshotProfileOverrideToggles() {
        composeTestRule.setContent {
            ThresholdTheme {
                Column(
                    modifier = Modifier
                        .background(AmoledBlack)
                        .padding(24.dp)
                        .width(340.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("PROFILE OVERRIDE (APPROACH 1)", color = PureWhite, fontSize = 12.sp)

                    // 1. Inactive/Default state: DEFAULT button highlighted, slider dimmed to 40%
                    ThresholdProfileOverrideToggle(
                        overrideState = null,
                        onOverrideChange = {},
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 2. Active override: ALLOW
                    ThresholdProfileOverrideToggle(
                        overrideState = BaseToggleState.ALLOW,
                        onOverrideChange = {},
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 3. Active override: GUARD
                    ThresholdProfileOverrideToggle(
                        overrideState = BaseToggleState.GUARD,
                        onOverrideChange = {},
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 4. Active override: BLOCK
                    ThresholdProfileOverrideToggle(
                        overrideState = BaseToggleState.BLOCK,
                        onOverrideChange = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        verifyAndArchiveSnapshot("multi_state_toggle_override.png")
    }

    @Test
    fun snapshotAppRowIntegration() {
        composeTestRule.setContent {
            ThresholdTheme {
                Column(
                    modifier = Modifier
                        .background(AmoledBlack)
                        .padding(16.dp)
                        .width(380.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppRowMock("Camera", null)
                    AppRowMock("Instagram", BaseToggleState.GUARD)
                    AppRowMock("YouTube", BaseToggleState.BLOCK)
                }
            }
        }

        verifyAndArchiveSnapshot("multi_state_toggle_app_rows.png")
    }

    @Composable
    private fun AppRowMock(appName: String, overrideState: BaseToggleState?) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = appName,
                color = PureWhite,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            ThresholdProfileOverrideToggle(
                overrideState = overrideState,
                onOverrideChange = {},
                modifier = Modifier.width(260.dp),
            )
        }
    }
}
