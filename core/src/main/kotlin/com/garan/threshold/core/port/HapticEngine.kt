package com.garan.threshold.core.port

/**
 * Outbound port for sensory micro-haptic feedback.
 *
 * Implements Threshold's tactile contract:
 * - Treats the smartphone as a precision mechanical instrument.
 * - Rotary detents for list scrolling / index scrubbing.
 * - Target snap detent when search locks in a candidate.
 * - Resistance detent on friction-guarded interactions.
 * - Relay snap (physical dual-impulse) for hardware/state confirmations.
 *
 * Strictly zero `android.*` dependencies.
 */
interface HapticEngine {

    /**
     * Subtle tactile transient mimicking a fine mechanical rotary dial or gear notch.
     * Used for list item traversal and index scrolling.
     */
    fun rotaryDetent()

    /**
     * Crisp mechanical detent signaling that the search index has acquired or snapped
     * a new top match candidate.
     */
    fun targetSnap()

    /**
     * Firm, weighted resistance notch signaling a friction barrier or guarded interaction.
     */
    fun resistanceDetent()

    /**
     * Physical dual-impulse relay snap (heavy click followed by delayed catch tick)
     * signaling completion of physical or high-intent confirmations (e.g. NFC tap, checklist completion).
     */
    fun relaySnap()
}

/**
 * No-op implementation of [HapticEngine] for previews, testing defaults, or environments without haptics.
 */
object NoOpHapticEngine : HapticEngine {
    override fun rotaryDetent() {}
    override fun targetSnap() {}
    override fun resistanceDetent() {}
    override fun relaySnap() {}
}
