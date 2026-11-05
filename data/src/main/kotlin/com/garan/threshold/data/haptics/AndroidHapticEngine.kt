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
 * - Resistance Detent: [VibrationEffect.Composition.PRIMITIVE_THUD] (or double-notch [VibrationEffect.Composition.PRIMITIVE_CLICK] stutter)
 * - Relay Snap: [VibrationEffect.Composition.PRIMITIVE_THUD] (or [VibrationEffect.Composition.PRIMITIVE_CLICK]) + delayed [VibrationEffect.Composition.PRIMITIVE_TICK] (30ms)
 *
 * Pre-computes and caches immutable [VibrationEffect] instances on first access so 120Hz scroll detents
 * incur zero heap allocations and only a single Binder call.
 */
class AndroidHapticEngine(
    context: Context,
    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext ?: context),
) : HapticEngine {

    private val hasVibratorHardware: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        vibrator?.hasVibrator() == true
    }

    private val touchAttributes: VibrationAttributes? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        } else {
            null
        }
    }

    internal val rotaryDetentEffect: VibrationEffect? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val v = vibrator ?: return@lazy null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f)
                .compose()
        } else {
            VibrationEffect.createOneShot(10L, 50)
        }
    }

    internal val targetSnapEffect: VibrationEffect? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val v = vibrator ?: return@lazy null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .compose()
        } else {
            VibrationEffect.createOneShot(15L, 120)
        }
    }

    internal val resistanceDetentEffect: VibrationEffect? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val v = vibrator ?: return@lazy null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                .compose()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
        ) {
            // Double-notch lock-tumbler resistance so it feels tactilely distinct from single-click targetSnap
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 25)
                .compose()
        } else {
            VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    internal val relaySnapEffect: VibrationEffect? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val v = vibrator ?: return@lazy null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            v.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            )
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 30)
                .compose()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            v.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            )
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 30)
                .compose()
        } else {
            // Dual-impulse fallback waveform: 20ms click, 30ms silence, 10ms tick
            val timings = longArrayOf(0L, 20L, 30L, 10L)
            val amplitudes = intArrayOf(0, 255, 0, 100)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        }
    }

    override fun rotaryDetent() {
        if (!hasVibratorHardware) return
        rotaryDetentEffect?.let(::vibrate)
    }

    override fun targetSnap() {
        if (!hasVibratorHardware) return
        targetSnapEffect?.let(::vibrate)
    }

    override fun resistanceDetent() {
        if (!hasVibratorHardware) return
        resistanceDetentEffect?.let(::vibrate)
    }

    override fun relaySnap() {
        if (!hasVibratorHardware) return
        relaySnapEffect?.let(::vibrate)
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
        fun resolveVibrator(context: Context): Vibrator? {
            val appCtx = context.applicationContext ?: context
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator ?: (appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
    }
}
