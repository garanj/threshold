package com.garan.threshold.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.core.port.PackageChangeEvent
import com.garan.threshold.core.port.PackageIndexSource
import com.garan.threshold.ui.launcher.AppLauncher
import com.garan.threshold.ui.search.SearchViewModel
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
class ThresholdHomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val cameraComponent = ComponentKey("com.android.camera", "com.android.camera.CameraActivity", 0L)

    private val fakePackageIndexSource = FakePackageIndexSource(
        initialApps = listOf(
            InstalledApp(cameraComponent, "Camera"),
        ),
    )

    @Test
    fun ambientState_backHandlerDisabled_suppressingSystemUiIndicator() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()
        var imeDismissed = false

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                    onDismissIme = { imeDismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("THRESHOLD").assertIsDisplayed()
        composeTestRule.onNodeWithText("Type to launch...").assertIsDisplayed()

        // When in ambient state, back handler should NOT have enabled callbacks
        // so SystemUI suppresses the edge swipe back indicator.
        composeTestRule.activityRule.scenario.onActivity { activity ->
            assertThat(activity.onBackPressedDispatcher.hasEnabledCallbacks()).isFalse()
        }
        assertThat(imeDismissed).isFalse()
    }

    @Test
    fun searchActive_backPressed_clearsQueryAndDismissesSearch() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()
        var imeDismissed = false

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                    onDismissIme = { imeDismissed = true },
                )
            }
        }

        // Tap search placeholder to activate search
        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        // Type query into the editable text field
        composeTestRule.onNode(hasSetTextAction()).performTextInput("cam")
        composeTestRule.waitForIdle()

        assertThat(viewModel.uiState.value.query).isEqualTo("cam")
        composeTestRule.onNodeWithText("Camera").assertIsDisplayed()
        composeTestRule.activityRule.scenario.onActivity { activity ->
            assertThat(activity.onBackPressedDispatcher.hasEnabledCallbacks()).isTrue()
        }

        // Press back via activity back dispatcher
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Verify query cleared, IME dismissed, back callback disabled, and back to ambient placeholder
        assertThat(viewModel.uiState.value.query).isEmpty()
        assertThat(imeDismissed).isTrue()
        composeTestRule.activityRule.scenario.onActivity { activity ->
            assertThat(activity.onBackPressedDispatcher.hasEnabledCallbacks()).isFalse()
        }
        composeTestRule.onNodeWithText("Type to launch...").assertIsDisplayed()
        composeTestRule.onNodeWithText("THRESHOLD").assertIsDisplayed()
    }

    @Test
    fun appLaunched_resetsSearchState() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                )
            }
        }

        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("cam")
        composeTestRule.waitForIdle()

        // Click on the app item
        composeTestRule.onNodeWithText("Camera").performClick()
        composeTestRule.waitForIdle()

        assertThat(launcher.launchedComponents).containsExactly(cameraComponent)
        assertThat(viewModel.uiState.value.query).isEmpty()
        composeTestRule.onNodeWithText("Type to launch...").assertIsDisplayed()
    }

    private class FakeAppLauncher : AppLauncher {
        val launchedComponents = mutableListOf<ComponentKey>()

        override fun launchApp(component: ComponentKey): Boolean {
            launchedComponents.add(component)
            return true
        }
    }

    private class FakePackageIndexSource(private val initialApps: List<InstalledApp> = emptyList()) :
        PackageIndexSource {
        override suspend fun getInstalledApps(): List<InstalledApp> = initialApps
        override fun observePackageChanges(): Flow<PackageChangeEvent> = MutableSharedFlow()
    }
}
