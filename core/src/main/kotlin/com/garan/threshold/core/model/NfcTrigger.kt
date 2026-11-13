package com.garan.threshold.core.model

/**
 * Activation behavior when a physical NFC tag bound to an [NfcTrigger] is tapped.
 */
enum class NfcTriggerMode {
    /**
     * Tap a bound tag to latch the trigger ON; tap a bound tag again to unlatch OFF
     * (or automatically unlatches if a companion `AND` condition ends).
     */
    TOGGLE_ON_TAP,

    /**
     * Tap a bound tag to latch the trigger ON for [NfcTrigger.durationMinutes];
     * expires automatically at the end of the session or when tapped again early.
     */
    TIMED_SESSION,
}

/**
 * A registered physical NFC tag identified by its read-only hardware UID.
 *
 * @property uid Canonical uppercase colon-separated hex hardware UID (e.g. `"04:A2:3F:8B:1C:90:80"`).
 * @property label Optional user-facing label for distinguishing multiple tags within a trigger.
 */
data class NfcTagEntry(val uid: String, val label: String = "") {
    val normalizedUid: String = normalizeUid(uid)

    init {
        require(normalizedUid.isNotBlank()) { "NFC tag UID must not be blank" }
    }

    companion object {
        /**
         * Normalizes a raw hex UID string (with or without colons, dashes, or spaces) into
         * uppercase colon-separated byte pairs (e.g. `"04a23f8b"` -> `"04:A2:3F:8B"`).
         */
        fun normalizeUid(raw: String): String {
            val hexOnly = raw.trim().filter { it.isLetterOrDigit() }.uppercase()
            if (hexOnly.isEmpty()) return ""
            if (!hexOnly.all { it in '0'..'9' || it in 'A'..'F' }) {
                return raw.trim().uppercase()
            }
            return if (hexOnly.length % 2 == 0) {
                hexOnly.chunked(2).joinToString(":")
            } else {
                hexOnly
            }
        }

        /**
         * Converts raw hardware tag ID bytes from `Tag.getId()` into a canonical uppercase hex UID.
         */
        fun formatUidBytes(bytes: ByteArray): String = bytes.joinToString(":") { byte ->
            val v = byte.toInt() and 0xFF
            val hi = "0123456789ABCDEF"[v ushr 4]
            val lo = "0123456789ABCDEF"[v and 0x0F]
            "$hi$lo"
        }
    }
}

/**
 * Physical NFC trigger definition containing one or more registered hardware tag UIDs
 * and an activation mode ([NfcTriggerMode.TOGGLE_ON_TAP] or [NfcTriggerMode.TIMED_SESSION]).
 */
data class NfcTrigger(
    val tags: List<NfcTagEntry> = emptyList(),
    val mode: NfcTriggerMode = NfcTriggerMode.TOGGLE_ON_TAP,
    val durationMinutes: Int = DEFAULT_DURATION_MINUTES,
) {
    init {
        require(durationMinutes in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES) {
            "durationMinutes must be between $MIN_DURATION_MINUTES and $MAX_DURATION_MINUTES, was $durationMinutes"
        }
    }

    /**
     * Returns `true` if [rawTagUid] matches any registered tag in [tags].
     */
    fun matchesTagUid(rawTagUid: String): Boolean {
        val candidate = NfcTagEntry.normalizeUid(rawTagUid)
        if (candidate.isEmpty()) return false
        return tags.any { it.normalizedUid == candidate }
    }

    companion object {
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 240
        const val DEFAULT_DURATION_MINUTES = 60

        val DURATION_PRESETS_MINUTES = listOf(15, 30, 60, 120, 240)

        fun formatDuration(minutes: Int): String = when {
            minutes < 60 -> "${minutes}m"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
}

/**
 * Runtime latch state of an [NfcTrigger] identified by its [SavedTrigger.id].
 *
 * @property triggerId The ID of the [SavedTrigger] currently latched ON.
 * @property latchedAtEpochMillis Wall-clock epoch milliseconds when the tag was tapped.
 * @property expiresAtEpochMillis Optional wall-clock epoch milliseconds when a [NfcTriggerMode.TIMED_SESSION] expires.
 */
data class NfcLatchState(
    val triggerId: String,
    val latchedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null,
) {
    fun isActiveAt(nowEpochMillis: Long): Boolean =
        expiresAtEpochMillis == null || nowEpochMillis < expiresAtEpochMillis
}
