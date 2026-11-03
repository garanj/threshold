package com.garan.threshold.core.port

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HapticEngineTest {

    @Test
    fun `fake haptic engine records invocations accurately`() {
        val engine = FakeHapticEngine()

        engine.rotaryDetent()
        engine.targetSnap()
        engine.targetSnap()
        engine.resistanceDetent()
        engine.relaySnap()

        assertThat(engine.rotaryDetentCount).isEqualTo(1)
        assertThat(engine.targetSnapCount).isEqualTo(2)
        assertThat(engine.resistanceDetentCount).isEqualTo(1)
        assertThat(engine.relaySnapCount).isEqualTo(1)

        engine.reset()
        assertThat(engine.rotaryDetentCount).isEqualTo(0)
        assertThat(engine.targetSnapCount).isEqualTo(0)
        assertThat(engine.resistanceDetentCount).isEqualTo(0)
        assertThat(engine.relaySnapCount).isEqualTo(0)
    }
}

class FakeHapticEngine : HapticEngine {
    var rotaryDetentCount = 0
        private set
    var targetSnapCount = 0
        private set
    var resistanceDetentCount = 0
        private set
    var relaySnapCount = 0
        private set

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

    fun reset() {
        rotaryDetentCount = 0
        targetSnapCount = 0
        resistanceDetentCount = 0
        relaySnapCount = 0
    }
}
