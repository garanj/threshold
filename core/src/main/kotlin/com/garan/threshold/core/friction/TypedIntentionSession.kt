package com.garan.threshold.core.friction

/**
 * State machine for the Typed Intention anti-impulse friction checkpoint.
 *
 * Implements a mechanical "lock-tumbler" state:
 * - Case-insensitive character-by-character matching against [targetPhrase].
 * - Correct keystrokes advance the matched prefix.
 * - Incorrect keystrokes are rejected (lock tumbler remains locked), signaling resistance.
 * - Backspace decrements the matched count.
 * - Double-space period insertion is sanitized.
 */
class TypedIntentionSession(val targetPhrase: String) {
    init {
        require(targetPhrase.isNotBlank()) { "Target phrase cannot be blank" }
    }

    var matchedCount: Int = 0
        private set

    val isComplete: Boolean
        get() = matchedCount >= targetPhrase.length

    val matchedText: String
        get() = targetPhrase.substring(0, matchedCount)

    val remainingText: String
        get() = targetPhrase.substring(matchedCount)

    /**
     * Attempts to consume the next character typed by the user.
     *
     * @return [MatchResult.Matched] if [char] correctly matches the expected character,
     *         [MatchResult.Completed] if this keystroke completed the phrase,
     *         [MatchResult.Mismatch] if [char] does not match (lock tumbler resistance).
     */
    fun typeChar(char: Char): MatchResult {
        if (isComplete) return MatchResult.Completed

        val expected = targetPhrase[matchedCount]
        val isMatch = char.equals(expected, ignoreCase = true) ||
            (expected == ' ' && char == '.') // Sanitize double-tap space periods

        return if (isMatch) {
            matchedCount++
            if (isComplete) MatchResult.Completed else MatchResult.Matched
        } else {
            MatchResult.Mismatch
        }
    }

    /**
     * Steps back one character if possible.
     * @return true if a character was undone, false if already at beginning.
     */
    fun backspace(): Boolean {
        if (matchedCount > 0) {
            matchedCount--
            return true
        }
        return false
    }

    sealed interface MatchResult {
        data object Matched : MatchResult
        data object Completed : MatchResult
        data object Mismatch : MatchResult
    }
}
