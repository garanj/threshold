package com.garan.threshold.ui.settings

import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.GeoTrigger
import com.garan.threshold.core.model.GeoTriggerMode
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.NfcTrigger
import com.garan.threshold.core.model.NfcTriggerMode
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.port.LocationTelemetry

internal fun DayOfWeek.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

internal fun DayOfWeek.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

internal fun formatProfileRulesSummary(
    profile: CustomProfile,
    savedTriggersById: Map<String, SavedTrigger>,
    currentLocation: LocationFix?,
): String {
    if (profile.activationRules.isNotEmpty()) {
        if (profile.activationRules.size == 1 && profile.activationRules[0].triggerIds.size == 1) {
            val singleId = profile.activationRules[0].triggerIds[0]
            val saved = savedTriggersById[singleId]
            if (saved != null) {
                val detail = formatTriggerDetail(saved.trigger, currentLocation)
                return if (saved.name.equals(profile.name, ignoreCase = true) || saved.id == "trigger-${profile.id}") {
                    detail
                } else {
                    "${saved.name} · $detail"
                }
            }
        }
        val formattedRules = profile.activationRules.mapNotNull { rule ->
            val names = rule.triggerIds.mapNotNull { savedTriggersById[it]?.name }
            if (names.isEmpty()) null else names.joinToString(" + ")
        }
        return if (formattedRules.isEmpty()) {
            "No triggers assigned"
        } else {
            formattedRules.joinToString(" OR ")
        }
    }

    val legacyTrigger = profile.trigger ?: return "No triggers assigned"
    return formatTriggerDetail(legacyTrigger, currentLocation)
}

internal fun formatTriggerDetail(trigger: ProfileTrigger, currentLocation: LocationFix?): String = when (trigger) {
    is ProfileTrigger.Schedule -> formatScheduleSummary(trigger.schedule)
    is ProfileTrigger.Geo -> formatGeoSummary(trigger.geo, currentLocation)
    is ProfileTrigger.Nfc -> formatNfcSummary(trigger.nfc)
}

internal fun formatNfcSummary(nfc: NfcTrigger): String {
    val modeText = when (nfc.mode) {
        NfcTriggerMode.TOGGLE_ON_TAP -> "Toggle on tap"
        NfcTriggerMode.TIMED_SESSION -> {
            val durationText = NfcTrigger.formatDuration(nfc.durationMinutes)
            val limit = nfc.effectiveMaxDailyUses
            if (limit != null) {
                "Timed session ($durationText, max $limit/day)"
            } else {
                "Timed session ($durationText)"
            }
        }
    }
    return when {
        nfc.tags.isEmpty() -> "$modeText · No tags registered"
        nfc.tags.size == 1 -> {
            val single = nfc.tags.first()
            val tagDesc = if (single.label.isNotBlank()) "${single.label} (${single.uid})" else single.uid
            "$modeText · $tagDesc"
        }
        else -> {
            val labeled = nfc.tags.mapNotNull { it.label.takeIf { l -> l.isNotBlank() } }
            if (labeled.isNotEmpty()) {
                "$modeText · ${nfc.tags.size} tags (${labeled.joinToString(", ")})"
            } else {
                "$modeText · ${nfc.tags.size} tags"
            }
        }
    }
}

internal fun formatGeoSummary(geo: GeoTrigger, currentLocation: LocationFix?): String {
    val modeLabel = when (geo.mode) {
        GeoTriggerMode.INSIDE -> "Inside"
        GeoTriggerMode.OUTSIDE -> "Outside"
    }
    val radiusLabel = GeoTrigger.formatRadius(geo.radiusMeters)
    val prefix = "$modeLabel $radiusLabel"

    if (currentLocation == null) {
        return "$prefix · ${geo.center.formattedCoordinates()}"
    }

    val distanceMeters = geo.distanceMetersFrom(currentLocation)
    val distanceLabel = GeoTrigger.formatDistance(distanceMeters)
    return if (distanceMeters <= geo.radiusMeters) {
        "$prefix · Currently inside ($distanceLabel away)"
    } else {
        "$prefix · $distanceLabel away"
    }
}

internal fun formatLocationTelemetryFooter(telemetry: LocationTelemetry): String {
    val base = "Location checks: ${telemetry.activeChecksLastHour}/hr · " +
        "${telemetry.activeChecksLast24h}/24h (${telemetry.cacheHitsLast24h} cached)"
    val provider = telemetry.lastProviderUsed ?: return base
    val latency = telemetry.lastFixLatencyMs
    return if (latency != null) {
        "$base · $provider (${latency}ms)"
    } else {
        "$base · $provider"
    }
}

internal fun formatScheduleSummary(schedule: ScheduleTrigger): String {
    val orderedDays = DayOfWeek.entries.filter { it in schedule.days }
    if (orderedDays.isEmpty()) return "Inactive (No days selected)"

    val daysText = if (orderedDays.size == 7) {
        "Every day"
    } else if (orderedDays.toSet() ==
        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    ) {
        "Weekdays"
    } else if (orderedDays.toSet() == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
        "Weekends"
    } else {
        orderedDays.joinToString(", ") { it.shortName() }
    }

    val distinctWindows = orderedDays.mapNotNull { schedule.dailyWindows[it] }.distinct()
    return if (schedule.sameScheduleEveryDay || distinctWindows.size <= 1) {
        val window = distinctWindows.firstOrNull()
        val start = window?.startTime ?: schedule.startTime
        val end = window?.endTime ?: schedule.endTime
        if (window?.isOvernight == true) {
            if (orderedDays.size == 1) {
                val day = orderedDays.first()
                val dayShort = day.shortName()
                val nextShort = day.nextDay().shortName()
                "$dayShort ${start.formatted()} – $nextShort ${end.formatted()}"
            } else {
                "$daysText · ${start.formatted()} – ${end.formatted()} (+1d)"
            }
        } else {
            "$daysText · ${start.formatted()} – ${end.formatted()}"
        }
    } else {
        if (orderedDays.size <= 3) {
            orderedDays.joinToString(" · ") { day ->
                val dayShort = day.shortName()
                val w = schedule.dailyWindows[day]
                if (w != null) {
                    if (w.isOvernight) {
                        val nextShort = day.nextDay().shortName()
                        "$dayShort ${w.startTime.formatted()}–$nextShort ${w.endTime.formatted()}"
                    } else {
                        "$dayShort ${w.startTime.formatted()}–${w.endTime.formatted()}"
                    }
                } else {
                    dayShort
                }
            }
        } else {
            "$daysText · Custom daily times"
        }
    }
}
