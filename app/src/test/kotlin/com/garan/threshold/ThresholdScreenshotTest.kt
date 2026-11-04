package com.garan.threshold

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.search.MatchType
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.core.search.SearchResult
import com.garan.threshold.ui.friction.TypedIntentionSurface
import com.garan.threshold.ui.home.ThresholdHomeCanvas
import com.garan.threshold.ui.search.ThresholdSearchSurface
import com.garan.threshold.ui.theme.ThresholdTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
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
class ThresholdScreenshotTest {

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

    @Test
    fun snapshotHomeCanvasDefault() {
        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Threshold",
                )
            }
        }
        verifyAndArchiveSnapshot("home_canvas.png")
    }

    @Test
    fun snapshotHomeCanvasWithZeros() {
        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "00:00",
                    dateText = "Sunday, October 31",
                    statusText = "Threshold · Night Rest",
                )
            }
        }
        verifyAndArchiveSnapshot("home_canvas_zeros.png")
    }

    @Test
    fun snapshotHomeCanvasWithProfileStatus() {
        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "14:32",
                    dateText = "Wednesday, November 4",
                    statusText = "Focus · Deep Work (25m remaining)",
                )
            }
        }
        verifyAndArchiveSnapshot("home_canvas_status.png")
    }

    @Test
    fun snapshotSearchSurfaceWithResults() {
        val cameraKey = ComponentKey("com.android.camera", "com.android.camera.CameraActivity", 0L)
        val calcKey = ComponentKey("com.android.calculator", "com.android.calculator.CalcActivity", 0L)

        val results = persistentListOf(
            SearchResult(
                item = SearchItem.App(cameraKey, "Camera", AppAccessState.Direct),
                score = 100,
                matchType = MatchType.PREFIX,
            ),
            SearchResult(
                item = SearchItem.App(calcKey, "Calculator", AppAccessState.Direct),
                score = 80,
                matchType = MatchType.SUBSTRING,
            ),
        )

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Threshold",
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    ThresholdSearchSurface(
                        query = "ca",
                        onQueryChange = {},
                        results = results,
                        onItemClick = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("search_results.png")
    }

    @Test
    fun snapshotSearchSurfaceGuardedFriction() {
        val socialKey = ComponentKey("com.example.social", "com.example.social.MainActivity", 0L)

        val results = persistentListOf(
            SearchResult(
                item = SearchItem.App(
                    component = socialKey,
                    label = "Social Feed",
                    accessState = AppAccessState.Guarded(FrictionPolicy.TimedDelay(10)),
                ),
                score = 95,
                matchType = MatchType.PREFIX,
            ),
        )

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Focus · Deep Work",
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    ThresholdSearchSurface(
                        query = "so",
                        onQueryChange = {},
                        results = results,
                        onItemClick = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("search_guarded.png")
    }

    @Test
    fun snapshotSearchSurfaceEmptyState() {
        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Threshold",
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    ThresholdSearchSurface(
                        query = "xyz",
                        onQueryChange = {},
                        results = persistentListOf(),
                        onItemClick = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("search_empty.png")
    }

    @Test
    fun snapshotSearchSurfaceShutterOpen() {
        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Threshold",
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    ThresholdSearchSurface(
                        query = "",
                        isSearchActive = true,
                        onQueryChange = {},
                        results = persistentListOf(),
                        onItemClick = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("search_shutter_open.png")
    }

    @Test
    fun snapshotTypedIntentionCheckpoint() {
        composeTestRule.setContent {
            ThresholdTheme {
                TypedIntentionSurface(
                    appLabel = "YouTube",
                    overridePhrase = "I need to use YouTube right now",
                    onConfirmOpen = {},
                    onDismiss = {},
                )
            }
        }
        verifyAndArchiveSnapshot("typed_intention_checkpoint.png")
    }

    @Test
    fun snapshotDragSortCheckpoint() {
        composeTestRule.setContent {
            ThresholdTheme {
                com.garan.threshold.ui.friction.DragSortSurface(
                    pairCount = 6,
                    seed = 42L,
                    onConfirmOpen = {},
                    onDismiss = {},
                )
            }
        }
        verifyAndArchiveSnapshot("drag_sort_checkpoint.png")
    }
}
