package com.garan.threshold.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ComponentKeyTest {

    @Test
    fun `valid component key creates successfully`() {
        val key = ComponentKey(
            packageName = "com.example.phone",
            activityName = "com.example.phone.DialerActivity",
            userSerial = 0L,
        )

        assertThat(key.packageName).isEqualTo("com.example.phone")
        assertThat(key.activityName).isEqualTo("com.example.phone.DialerActivity")
        assertThat(key.userSerial).isEqualTo(0L)
    }

    @Test
    fun `blank package name throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            ComponentKey(
                packageName = "   ",
                activityName = "com.example.app.MainActivity",
            )
        }
    }

    @Test
    fun `blank activity name throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            ComponentKey(
                packageName = "com.example.app",
                activityName = "",
            )
        }
    }

    @Test
    fun `negative user serial throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            ComponentKey(
                packageName = "com.example.app",
                activityName = "com.example.app.MainActivity",
                userSerial = -1L,
            )
        }
    }

    @Test
    fun `component key equality and hash code behave correctly`() {
        val key1 = ComponentKey("com.example.app", "MainActivity", 0L)
        val key2 = ComponentKey("com.example.app", "MainActivity", 0L)
        val keyWork = ComponentKey("com.example.app", "MainActivity", 10L)

        assertThat(key1).isEqualTo(key2)
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode())
        assertThat(key1).isNotEqualTo(keyWork)
    }

    @Test
    fun `flattenToString and fromFlatString roundtrip correctly`() {
        val original = ComponentKey("com.example.app", "MainActivity", 10L)
        val flattened = original.flattenToString()

        assertThat(flattened).isEqualTo("com.example.app/MainActivity#10")
        assertThat(original.id).isEqualTo("com.example.app/MainActivity#10")
        assertThat(original.flattenToString()).isSameInstanceAs(original.id)
        assertThat(ComponentKey.fromFlatString(flattened)).isEqualTo(original)
    }

    @Test
    fun `fromFlatString with default user serial parses successfully`() {
        val parsed = ComponentKey.fromFlatString("com.example.app/MainActivity")
        assertThat(parsed).isEqualTo(ComponentKey("com.example.app", "MainActivity", 0L))
    }

    @Test
    fun `fromFlatString invalid formats throw IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            ComponentKey.fromFlatString("invalid-format-without-slash")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ComponentKey.fromFlatString("pkg/activity#notanumber")
        }
    }
}
