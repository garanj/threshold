package com.garan.threshold.data.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.garan.threshold.core.port.HapticEngine

/**
 * Android implementation of [HapticEngine].
 *
 * Implements Threshold's Sensory Contract using hardware-tuned [VibrationEffect.Composition] primitives:
 * - Rotary Detents: [VibrationEffect.Composition.PRIMITIVE_TICK]
 * - Target Snap: [VibrationEffect.Composition.PRIMITIVE_CLICK]
 * - Resistance Detent: [VibrationEffect.Composition.PRIMITIVE_HEAVY_CLICK]
 * - Relay Snap: [VibrationEffect.Composition.PRIMITIVE_HEAVY_CLICK] + delayed [VibrationEffect.Composition.PRIMITIVE_TICK] (30ms)
 *
 * Provides graceful micro-pulse fallback for older devices (API 29) or hardware lacking primitive support.
 */
class AndroidHapticEngine(context: Context, private val vibrator: Vibrator? = resolveVibrator(context)) :
    HapticEngine {

    private val touchAttributes: VibrationAttributes? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        } else {
            null
        }
    }

    override fun rotaryDetent() {
        if (vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f)
                .compose()
            vibrate(effect)
        } else {
            vibrate(VibrationEffect.createOneShot(10L, 50))
        }
    }

    override fun targetSnap() {
        if (vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .compose()
            vibrate(effect)
        } else {
            vibrate(VibrationEffect.createOneShot(15L, 120))
        }
    }

    override fun resistanceDetent() {
        if (vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                .compose()
            vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .compose()
            vibrate(effect)
        } else {
            vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun relaySnap() {
        if (vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            )
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 30)
                .compose()
            vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            )
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 30)
                .compose()
            vibrate(effect)
        } else {
            // Dual-impulse fallback waveform: 20ms click, 30ms silence, 10ms tick
            val timings = longArrayOf(0L, 20L, 30L, 10L)
            val amplitudes = intArrayOf(0, 255, 0, 100)
            vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun vibrate(effect: VibrationEffect) {
        try {
            val attrs = touchAttributes
            if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator?.vibrate(effect, attrs)
            } else {
                vibrator?.vibrate(effect)
            }
        } catch (_: Exception) {
            // Guard against device-specific vibration dispatch failures
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun resolveVibrator(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
