package com.garan.threshold.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class InstalledAppTest {

    @Test
    fun `valid component and label creates InstalledApp successfully`() {
        val component = ComponentKey("com.example.app", "MainActivity", userSerial = 0L)
        val app = InstalledApp(component, "Example App")

        assertThat(app.component).isEqualTo(component)
        assertThat(app.label).isEqualTo("Example App")
    }

    @Test
    fun `blank label throws IllegalArgumentException`() {
        val component = ComponentKey("com.example.app", "MainActivity", userSerial = 0L)

        assertThrows(IllegalArgumentException::class.java) {
            InstalledApp(component, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstalledApp(component, "   ")
        }
    }
}
