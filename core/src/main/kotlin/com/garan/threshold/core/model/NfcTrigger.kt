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
 *
 * @property maxDailyUses Optional cap on how many times a [NfcTriggerMode.TIMED_SESSION] trigger can be
 *   activated per local calendar day (`null` = unlimited). Only taps that successfully start an active
 *   session count toward this cap.
 */
data class NfcTrigger(
    val tags: List<NfcTagEntry> = emptyList(),
    val mode: NfcTriggerMode = NfcTriggerMode.TOGGLE_ON_TAP,
    val durationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val maxDailyUses: Int? = null,
) {
    init {
        require(durationMinutes in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES) {
            "durationMinutes must be between $MIN_DURATION_MINUTES and $MAX_DURATION_MINUTES, was $durationMinutes"
        }
        require(maxDailyUses == null || maxDailyUses in MIN_DAILY_USES..MAX_DAILY_USES) {
            "maxDailyUses must be null or between $MIN_DAILY_USES and $MAX_DAILY_USES, was $maxDailyUses"
        }
    }

    /**
     * Effective daily activation cap (`null` when unlimited or when [mode] is [NfcTriggerMode.TOGGLE_ON_TAP]).
     */
    val effectiveMaxDailyUses: Int?
        get() = if (mode == NfcTriggerMode.TIMED_SESSION) maxDailyUses else null

    /**
     * Returns `true` if [rawTagUid] matches any registered tag in [tags].
     */
    fun matchesTagUid(rawTagUid: String): Boolean {
        val candidate = NfcTagEntry.normalizeUid(rawTagUid)
        if (candidate.isEmpty()) return false
        return tags.any { it.normalizedUid == candidate }
    }

    /**
     * Returns the number of timed sessions started on [currentLocalDate] (`"YYYY-MM-DD"`).
     */
    fun getTapsToday(usage: NfcDailyUsage?, currentLocalDate: String): Int = usage?.getTapsToday(currentLocalDate) ?: 0

    /**
     * Returns `true` if this trigger can be activated on [currentLocalDate] (`"YYYY-MM-DD"`).
     */
    fun hasRemainingTaps(usage: NfcDailyUsage?, currentLocalDate: String): Boolean =
        usage?.hasRemainingTaps(effectiveMaxDailyUses, currentLocalDate) ?: true

    /**
     * Returns an updated [NfcDailyUsage] recording one started session on [currentLocalDate] (`"YYYY-MM-DD"`).
     */
    fun markTapUsed(usage: NfcDailyUsage?, currentLocalDate: String): NfcDailyUsage =
        (usage ?: NfcDailyUsage(localDate = currentLocalDate, tapCount = 0)).markTapUsed(currentLocalDate)

    companion object {
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 240
        const val DEFAULT_DURATION_MINUTES = 60

        const val MIN_DAILY_USES = 1
        const val MAX_DAILY_USES = 99

        val DURATION_PRESETS_MINUTES = listOf(15, 30, 60, 120, 240)

        fun formatDuration(minutes: Int): String = when {
            minutes < 60 -> "${minutes}m"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
}

/**
 * Per-trigger daily activation counter for [NfcTriggerMode.TIMED_SESSION] triggers.
 * Compares [localDate] (`"YYYY-MM-DD"`) against the current local calendar date at evaluation time
 * so daily limits reset deterministically at local midnight without relying on OS broadcasts.
 *
 * @property localDate ISO-8601 local calendar date (`"YYYY-MM-DD"`) when [tapCount] was recorded.
 * @property tapCount Number of timed sessions started on [localDate].
 */
data class NfcDailyUsage(val localDate: String, val tapCount: Int = 0) {
    init {
        require(tapCount >= 0) { "tapCount must be non-negative, was $tapCount" }
    }

    fun getTapsToday(currentLocalDate: String): Int =
        if (currentLocalDate.isNotBlank() && localDate == currentLocalDate) tapCount else 0

    fun hasRemainingTaps(maxDailyUses: Int?, currentLocalDate: String): Boolean =
        maxDailyUses == null || getTapsToday(currentLocalDate) < maxDailyUses

    fun markTapUsed(currentLocalDate: String): NfcDailyUsage = NfcDailyUsage(
        localDate = currentLocalDate,
        tapCount = getTapsToday(currentLocalDate) + 1,
    )
}

/**
 * Runtime latch state of an [NfcTrigger] identified by its [SavedTrigger.id].
 *
 * @property triggerId The ID of the [SavedTrigger] currently latched ON.
 * @property latchedAtEpochMillis Wall-clock epoch milliseconds when the tag was tapped.
 * @property expiresAtEpochMillis Optional wall-clock epoch milliseconds when a [NfcTriggerMode.TIMED_SESSION] expires.
 * @property latchedByTagUid Optional normalized hardware UID of the physical tag that initiated this latch.
 */
data class NfcLatchState(
    val triggerId: String,
    val latchedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null,
    val latchedByTagUid: String? = null,
) {
    fun isActiveAt(nowEpochMillis: Long): Boolean =
        expiresAtEpochMillis == null || nowEpochMillis < expiresAtEpochMillis

    /**
     * Reconciles this latch state against a potentially updated [NfcTrigger] definition at [nowEpochMillis].
     * Returns `null` if the trigger no longer has tags, no longer includes [latchedByTagUid], or has expired.
     */
    fun reconcileWith(nfc: NfcTrigger, nowEpochMillis: Long): NfcLatchState? {
        if (nfc.tags.isEmpty()) return null
        if (latchedByTagUid != null && !nfc.matchesTagUid(latchedByTagUid)) return null
        val effectiveExpiresAt = when (nfc.mode) {
            NfcTriggerMode.TOGGLE_ON_TAP -> null
            NfcTriggerMode.TIMED_SESSION -> latchedAtEpochMillis + nfc.durationMinutes * 60_000L
        }
        if (effectiveExpiresAt != null && nowEpochMillis >= effectiveExpiresAt) return null
        return if (effectiveExpiresAt == expiresAtEpochMillis) {
            this
        } else {
            copy(expiresAtEpochMillis = effectiveExpiresAt)
        }
    }
}
