package com.garan.threshold.data.haptics

import android.content.Context
import android.os.VibrationEffect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AndroidHapticEngineTest {

    private lateinit var context: Context
    private lateinit var shadowVibrator: ShadowVibrator
    private lateinit var engine: AndroidHapticEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val vibrator = AndroidHapticEngine.resolveVibrator(context)!!
        shadowVibrator = Shadows.shadowOf(vibrator)
        ShadowVibrator.reset()
        engine = AndroidHapticEngine(context, vibrator)
    }

    @Test
    fun noVibratorHardware_dispatchesNoVibrations() {
        shadowVibrator.setHasVibrator(false)

        engine.rotaryDetent()
        engine.targetSnap()
        engine.resistanceDetent()
        engine.relaySnap()

        assertThat(shadowVibrator.isVibrating).isFalse()
        assertThat(shadowVibrator.primitiveEffects.orEmpty()).isEmpty()
    }

    @Test
    fun primitiveSupported_dispatchesPrimitiveComposition() {
        shadowVibrator.setHasVibrator(true)
        shadowVibrator.setSupportedPrimitives(
            listOf(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD,
            ),
        )

        engine.rotaryDetent()
        assertThat(shadowVibrator.isVibrating).isTrue()

        engine.targetSnap()
        assertThat(shadowVibrator.isVibrating).isTrue()

        engine.resistanceDetent()
        assertThat(shadowVibrator.isVibrating).isTrue()

        engine.relaySnap()
        assertThat(shadowVibrator.isVibrating).isTrue()
    }

    @Test
    fun primitivesUnsupported_fallsBackGracefully() {
        shadowVibrator.setHasVibrator(true)
        shadowVibrator.setSupportedPrimitives(emptyList())

        engine.rotaryDetent()
        engine.targetSnap()
        engine.resistanceDetent()
        engine.relaySnap()

        // Primitives list will be empty because fallback one-shot/waveform was used instead
        assertThat(shadowVibrator.primitiveEffects.orEmpty()).isEmpty()
    }

    @Test
    fun defaultConstructor_resolvesSystemVibrator() {
        val defaultEngine = AndroidHapticEngine(context)
        defaultEngine.rotaryDetent()
        defaultEngine.targetSnap()
        defaultEngine.resistanceDetent()
        defaultEngine.relaySnap()
    }

    @Test
    fun whenThudUnsupported_resistanceDetentIsDistinctFromTargetSnap_andEffectsAreCached() {
        shadowVibrator.setHasVibrator(true)
        shadowVibrator.setSupportedPrimitives(
            listOf(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
            ),
        )

        val targetEffect1 = engine.targetSnapEffect
        val targetEffect2 = engine.targetSnapEffect
        val resistanceEffect = engine.resistanceDetentEffect

        assertThat(targetEffect1).isNotNull()
        assertThat(targetEffect1).isSameInstanceAs(targetEffect2)
        assertThat(resistanceEffect).isNotNull()
        assertThat(resistanceEffect).isNotEqualTo(targetEffect1)
    }
}
