package com.garan.threshold.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BaseProfileSettingsTest {

    private val dialerComponent = ComponentKey("com.google.android.dialer", "DialtactsActivity")
    private val clockComponent = ComponentKey("com.google.android.deskclock", "DeskClock")
    private val browserComponent = ComponentKey("com.android.chrome", "Main")
    private val socialComponent = ComponentKey("com.instagram.android", "MainActivity")

    @Test
    fun `quick actions exceeding max slots throws IllegalArgumentException`() {
        val keys = (1..8).map { ComponentKey("pkg.$it", "act.$it") }
        assertThrows(IllegalArgumentException::class.java) {
            BaseProfileSettings(quickActions = keys)
        }
    }

    @Test
    fun `duplicate quick actions throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            BaseProfileSettings(quickActions = listOf(browserComponent, browserComponent))
        }
    }

    @Test
    fun `universal dialer invariant always resolves to Direct access`() {
        val settings = BaseProfileSettings(
            appAssignments = mapOf(
                dialerComponent to AppAssignment.Blocked,
            ),
        )

        assertThat(settings.resolveAccess(dialerComponent, isDefaultDialer = true))
            .isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun `unassigned app defaults to Standard Direct access`() {
        val settings = BaseProfileSettings()
        assertThat(settings.resolveAccess(clockComponent, isDefaultDialer = false))
            .isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun `blocked assignment resolves to Blocked`() {
        val settings = BaseProfileSettings(
            appAssignments = mapOf(socialComponent to AppAssignment.Blocked),
        )
        assertThat(settings.resolveAccess(socialComponent, isDefaultDialer = false))
            .isEqualTo(AppAccessState.Blocked)
    }

    @Test
    fun `guarded assignment without override uses default friction`() {
        val settings = BaseProfileSettings(
            defaultFriction = FrictionChoice.DRAG_SORT,
            appAssignments = mapOf(browserComponent to AppAssignment.Guarded(frictionOverride = null)),
        )
        assertThat(settings.resolveAccess(browserComponent, isDefaultDialer = false))
            .isEqualTo(AppAccessState.Guarded(FrictionPolicy.DragSort(pairCount = 6)))
    }

    @Test
    fun `guarded assignment with override takes precedence over default friction`() {
        val settings = BaseProfileSettings(
            defaultFriction = FrictionChoice.DRAG_SORT,
            appAssignments = mapOf(
                browserComponent to AppAssignment.Guarded(frictionOverride = FrictionChoice.CARET_TRACING),
            ),
        )
        assertThat(settings.resolveAccess(browserComponent, isDefaultDialer = false))
            .isEqualTo(AppAccessState.Guarded(FrictionPolicy.TimedDelay(seconds = 10)))
    }

    @Test
    fun `package-level assignment matches component when activity differs`() {
        val pkgKey = ComponentKey("com.android.chrome", "*")
        val settings = BaseProfileSettings(
            appAssignments = mapOf(pkgKey to AppAssignment.Blocked),
        )
        assertThat(settings.resolveAccess(browserComponent, isDefaultDialer = false))
            .isEqualTo(AppAccessState.Blocked)
    }
}
