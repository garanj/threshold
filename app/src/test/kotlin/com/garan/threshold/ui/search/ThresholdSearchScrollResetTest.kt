package com.garan.threshold.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.core.port.PackageChangeEvent
import com.garan.threshold.core.port.PackageIndexSource
import com.garan.threshold.ui.home.ThresholdHomeScreen
import com.garan.threshold.ui.launcher.AppLauncher
import com.garan.threshold.ui.theme.ThresholdTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThresholdSearchScrollResetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val chromeKey = ComponentKey("com.android.chrome", "com.google.android.apps.chrome.Main", 0L)
    private val cycleHireKey = ComponentKey("com.example.cyclehire", "com.example.cyclehire.MainActivity", 0L)
    private val schoolPortalKey = ComponentKey("com.example.myschool", "com.example.myschool.MainActivity", 0L)
    private val contactsKey = ComponentKey("com.android.contacts", "com.android.contacts.DialtactsActivity", 0L)
    private val clockKey = ComponentKey("com.android.deskclock", "com.android.deskclock.DeskClock", 0L)
    private val chessKey = ComponentKey("com.example.chess", "com.example.chess.MainActivity", 0L)
    private val checkersKey = ComponentKey("com.example.checkers", "com.example.checkers.MainActivity", 0L)

    private val testApps = listOf(
        InstalledApp(chromeKey, "Chrome"),
        InstalledApp(cycleHireKey, "Cycle hire"),
        InstalledApp(schoolPortalKey, "My School portal"),
        InstalledApp(contactsKey, "Contacts"),
        InstalledApp(clockKey, "Clock"),
        InstalledApp(chessKey, "Chess"),
        InstalledApp(checkersKey, "Checkers"),
    )

    private val fakePackageIndexSource = object : PackageIndexSource {
        override suspend fun getInstalledApps(): List<InstalledApp> = testApps
        override fun observePackageChanges(): Flow<PackageChangeEvent> = MutableSharedFlow()
    }

    private val fakeLauncher = object : AppLauncher {
        override fun launchApp(component: ComponentKey): Boolean = true
        override fun openAppInfo(component: ComponentKey): Boolean = true
    }

    @Test
    fun queryChanges_resetScrollPosition_ensuringTopRankedResultRemainsVisible() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = fakeLauncher,
                )
            }
        }

        // 1. Activate search input
        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        // 2. Type 'c', 'h', 'r' as individual keystrokes
        val input = composeTestRule.onNode(hasSetTextAction())
        input.performTextInput("c")
        composeTestRule.waitForIdle()
        input.performTextInput("h")
        composeTestRule.waitForIdle()
        input.performTextInput("r")
        composeTestRule.waitForIdle()

        // 3. Backspace twice: 'chr' -> 'ch' -> 'c'
        input.performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.waitForIdle()
        input.performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.waitForIdle()

        // 4. Type 'o' -> 'co', then 'o' -> 'coo'
        input.performTextInput("o")
        composeTestRule.waitForIdle()
        input.performTextInput("o")
        composeTestRule.waitForIdle()

        // 5. Backspace twice: 'coo' -> 'co' -> 'c'
        input.performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.waitForIdle()
        input.performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.waitForIdle()

        // 6. Type 'h' -> 'ch', then 'r' -> 'chr'
        input.performTextInput("h")
        composeTestRule.waitForIdle()
        input.performTextInput("r")
        composeTestRule.waitForIdle()

        // Invariant: Chrome MUST be displayed at the top without being scrolled off
        val chromeNode = composeTestRule.onNodeWithText("Chrome").fetchSemanticsNode()
        val cycleHireNode = composeTestRule.onNodeWithText("Cycle hire").fetchSemanticsNode()
        assertThat(chromeNode.boundsInRoot.top).isLessThan(cycleHireNode.boundsInRoot.top)
        composeTestRule.onNodeWithText("Chrome").assertIsDisplayed()
    }

    @Test
    fun manualScroll_thenTypeQuery_resetsScrollPositionToTop() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val listState = androidx.compose.foundation.lazy.LazyListState()

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = fakeLauncher,
                    searchListState = listState,
                )
            }
        }

        // 1. Activate search and type "c" -> 5 apps in results
        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("c")
        composeTestRule.waitForIdle()
        assertThat(viewModel.uiState.value.results.size).isAtLeast(3)

        // 2. Simulate user scrolling down to index 2
        composeTestRule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(2)
            }
        }
        composeTestRule.waitForIdle()
        println("Index after scrollToItem(2): ${listState.firstVisibleItemIndex}")
        assertThat(listState.firstVisibleItemIndex).isEqualTo(2)

        // 3. User types another character 'h' -> query becomes "ch"
        composeTestRule.onNode(hasSetTextAction()).performTextInput("h")
        composeTestRule.waitForIdle()
        println("Index after typing 'h': ${listState.firstVisibleItemIndex}")
        assertThat(viewModel.uiState.value.query).isEqualTo("ch")

        // Invariant: Changing query must reset the list scroll position back to index 0!
        assertThat(listState.firstVisibleItemIndex).isEqualTo(0)
    }

    @Test
    fun queryChange_scrollToTop_doesNotTriggerRotaryDetent() {
        val testHaptics = TestHapticEngine()
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val listState = androidx.compose.foundation.lazy.LazyListState()

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = fakeLauncher,
                    hapticEngine = testHaptics,
                    searchListState = listState,
                )
            }
        }

        // 1. Activate search and type "c"
        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("c")
        composeTestRule.waitForIdle()

        // 2. Scroll to index 2 (simulating position displacement)
        composeTestRule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(2)
            }
        }
        composeTestRule.waitForIdle()
        val hapticCountBefore = testHaptics.rotaryDetentCount

        // 3. Type another character 'h'
        composeTestRule.onNode(hasSetTextAction()).performTextInput("h")
        composeTestRule.waitForIdle()

        // Verified: Scroll position reset to 0
        assertThat(listState.firstVisibleItemIndex).isEqualTo(0)
        // Invariant: Programmatic reset must NOT trigger false rotary detents
        assertThat(testHaptics.rotaryDetentCount).isEqualTo(hapticCountBefore)
    }

    private class TestHapticEngine : com.garan.threshold.core.port.HapticEngine {
        var rotaryDetentCount = 0
            private set

        override fun rotaryDetent() {
            rotaryDetentCount++
        }

        override fun resistanceDetent() {}
        override fun targetSnap() {}
        override fun relaySnap() {}
    }
}
