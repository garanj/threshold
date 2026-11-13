package com.garan.threshold.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.GeneralSettings
import com.garan.threshold.core.model.GeoTrigger
import com.garan.threshold.core.model.GeoTriggerMode
import com.garan.threshold.core.model.NfcTagEntry
import com.garan.threshold.core.model.NfcTrigger
import com.garan.threshold.core.model.NfcTriggerMode
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.ThresholdSettings
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.model.TimeWindow
import com.garan.threshold.core.model.TriggerRule
import com.garan.threshold.core.port.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "threshold_settings")

/**
 * Android DataStore implementation of [SettingsRepository].
 */
class AndroidSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsRepository {

    constructor(context: Context, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : this(
        dataStore = (context.applicationContext ?: context).settingsDataStore,
        ioDispatcher = ioDispatcher,
    )

    private object Keys {
        val SHOW_UNLOCKS = booleanPreferencesKey("show_unlocks")
        val SHOW_REST = booleanPreferencesKey("show_rest")
        val REQUIRE_FULL_TYPING = booleanPreferencesKey("require_full_typing")
        val HAS_SEEN_FIRST_RUN_HINT = booleanPreferencesKey("has_seen_first_run_hint")
        val DEFAULT_FRICTION = stringPreferencesKey("default_friction")
        val QUICK_ACTIONS = stringPreferencesKey("quick_actions")
        val APP_ASSIGNMENTS = stringPreferencesKey("app_assignments")
        val CUSTOM_PROFILES = stringPreferencesKey("custom_profiles")
        val SAVED_TRIGGERS = stringPreferencesKey("saved_triggers")
    }

    override fun observeSettings(): Flow<ThresholdSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> mapToSettings(prefs) }
        .flowOn(ioDispatcher)

    override suspend fun getSettings(): ThresholdSettings = withContext(ioDispatcher) {
        observeSettings().first()
    }

    override suspend fun updateGeneralSettings(general: GeneralSettings) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs[Keys.SHOW_UNLOCKS] = general.showUnlocks
                prefs[Keys.SHOW_REST] = general.showRest
                prefs[Keys.REQUIRE_FULL_TYPING] = general.requireFullTypingOfGuardedApps
                prefs[Keys.HAS_SEEN_FIRST_RUN_HINT] = general.hasSeenFirstRunHint
            }
        }
    }

    override suspend fun updateBaseProfile(baseProfile: BaseProfileSettings) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs[Keys.DEFAULT_FRICTION] = baseProfile.defaultFriction.name
                prefs[Keys.QUICK_ACTIONS] = baseProfile.quickActions.joinToString(";") { it.flattenToString() }
                prefs[Keys.APP_ASSIGNMENTS] = serializeAppAssignments(baseProfile.appAssignments)
            }
        }
    }

    override suspend fun setHasSeenFirstRunHint(hasSeen: Boolean) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs[Keys.HAS_SEEN_FIRST_RUN_HINT] = hasSeen
            }
        }
    }

    override suspend fun updateCustomProfiles(profiles: List<CustomProfile>) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs[Keys.CUSTOM_PROFILES] = serializeCustomProfiles(profiles)
            }
        }
    }

    override suspend fun saveCustomProfile(profile: CustomProfile) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                val current = deserializeCustomProfiles(prefs[Keys.CUSTOM_PROFILES].orEmpty())
                val existingIndex = current.indexOfFirst { it.id == profile.id }
                val updated = if (existingIndex >= 0) {
                    current.toMutableList().apply { set(existingIndex, profile) }
                } else {
                    current + profile
                }
                prefs[Keys.CUSTOM_PROFILES] = serializeCustomProfiles(updated)
            }
        }
    }

    override suspend fun deleteCustomProfile(profileId: String) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                val current = deserializeCustomProfiles(prefs[Keys.CUSTOM_PROFILES].orEmpty())
                val updated = current.filter { it.id != profileId }
                prefs[Keys.CUSTOM_PROFILES] = serializeCustomProfiles(updated)
            }
        }
    }

    override suspend fun saveTrigger(trigger: SavedTrigger) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                val currentSettings = mapToSettings(prefs)
                val current = currentSettings.savedTriggers
                val existingIndex = current.indexOfFirst { it.id == trigger.id }
                val updated = if (existingIndex >= 0) {
                    current.toMutableList().apply { set(existingIndex, trigger) }
                } else {
                    current + trigger
                }
                prefs[Keys.SAVED_TRIGGERS] = serializeSavedTriggers(updated)
            }
        }
    }

    override suspend fun deleteTrigger(triggerId: String) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                val currentSettings = mapToSettings(prefs)
                val updatedTriggers = currentSettings.savedTriggers.filter { it.id != triggerId }
                val updatedProfiles = currentSettings.customProfiles.map { profile ->
                    if (triggerId in profile.referencedTriggerIds) {
                        profile.withoutTrigger(triggerId)
                    } else {
                        profile
                    }
                }
                prefs[Keys.SAVED_TRIGGERS] = serializeSavedTriggers(updatedTriggers)
                prefs[Keys.CUSTOM_PROFILES] = serializeCustomProfiles(updatedProfiles)
            }
        }
    }

    override suspend fun removeApps(keys: Set<ComponentKey>) {
        if (keys.isEmpty()) return
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                val currentSettings = mapToSettings(prefs)
                val removedPkgNames = keys.map { it.packageName }.toSet()

                val prunedQuickActions = currentSettings.baseProfile.quickActions.filter {
                    it !in keys && it.packageName !in removedPkgNames
                }
                val prunedAssignments = currentSettings.baseProfile.appAssignments.filter { (comp, _) ->
                    comp !in keys && comp.packageName !in removedPkgNames
                }

                val prunedProfiles = currentSettings.customProfiles.map { profile ->
                    val pruned = profile.appAssignments.filter { (comp, _) ->
                        comp !in keys && comp.packageName !in removedPkgNames
                    }
                    val shouldDisable = pruned.isEmpty() && profile.appAssignments.isNotEmpty()
                    profile.copy(
                        appAssignments = pruned,
                        enabled = if (shouldDisable) false else profile.enabled,
                    )
                }

                prefs[Keys.QUICK_ACTIONS] = prunedQuickActions.joinToString(";") { it.flattenToString() }
                prefs[Keys.APP_ASSIGNMENTS] = serializeAppAssignments(prunedAssignments)
                prefs[Keys.CUSTOM_PROFILES] = serializeCustomProfiles(prunedProfiles)
            }
        }
    }

    private fun mapToSettings(prefs: Preferences): ThresholdSettings {
        val showUnlocks = prefs[Keys.SHOW_UNLOCKS] ?: true
        val showRest = prefs[Keys.SHOW_REST] ?: true
        val requireFullTyping = prefs[Keys.REQUIRE_FULL_TYPING] ?: false
        val hasSeenHint = prefs[Keys.HAS_SEEN_FIRST_RUN_HINT] ?: false

        val frictionName = prefs[Keys.DEFAULT_FRICTION]
        val defaultFriction = frictionName?.let {
            runCatching { FrictionChoice.valueOf(it) }.getOrNull()
        } ?: FrictionChoice.TYPED_INTENTION

        val quickActionsRaw = prefs[Keys.QUICK_ACTIONS].orEmpty()
        val quickActions = if (quickActionsRaw.isBlank()) {
            emptyList()
        } else {
            quickActionsRaw.split(";")
                .filter { it.isNotBlank() }
                .mapNotNull { runCatching { ComponentKey.fromFlatString(it) }.getOrNull() }
        }

        val appAssignmentsRaw = prefs[Keys.APP_ASSIGNMENTS].orEmpty()
        val appAssignments = deserializeAppAssignments(appAssignmentsRaw)

        val savedTriggersRaw = prefs[Keys.SAVED_TRIGGERS].orEmpty()
        val rawSavedTriggers = deserializeSavedTriggers(savedTriggersRaw)

        val customProfilesRaw = prefs[Keys.CUSTOM_PROFILES].orEmpty()
        val rawCustomProfiles = deserializeCustomProfiles(customProfilesRaw)

        // Legacy migration: any profile that has an inline `trigger` and no `activationRules`
        // gets a synthesized SavedTrigger and a single-trigger TriggerRule.
        val migratedTriggers = rawSavedTriggers.toMutableList()
        val existingTriggerIds = migratedTriggers.map { it.id }.toMutableSet()
        val migratedProfiles = rawCustomProfiles.map { profile ->
            val inlineTrigger = profile.trigger
            if (profile.activationRules.isEmpty() && inlineTrigger != null) {
                val migratedTriggerId = "trigger-${profile.id}"
                if (existingTriggerIds.add(migratedTriggerId)) {
                    migratedTriggers.add(
                        SavedTrigger(
                            id = migratedTriggerId,
                            name = profile.name,
                            trigger = inlineTrigger,
                        ),
                    )
                }
                profile.copy(
                    activationRules = listOf(TriggerRule(listOf(migratedTriggerId))),
                )
            } else {
                profile
            }
        }

        return ThresholdSettings(
            general = GeneralSettings(
                showUnlocks = showUnlocks,
                showRest = showRest,
                requireFullTypingOfGuardedApps = requireFullTyping,
                hasSeenFirstRunHint = hasSeenHint,
            ),
            baseProfile = BaseProfileSettings(
                defaultFriction = defaultFriction,
                appAssignments = appAssignments,
                quickActions = quickActions,
            ),
            customProfiles = migratedProfiles,
            savedTriggers = migratedTriggers,
        )
    }

    private fun serializeAppAssignments(assignments: Map<ComponentKey, AppAssignment>): String =
        assignments.entries.joinToString("\n") { (key, assignment) ->
            val assignmentStr = when (assignment) {
                is AppAssignment.Standard -> "S"
                is AppAssignment.Blocked -> "B"
                is AppAssignment.Guarded -> {
                    val override = assignment.frictionOverride
                    if (override != null) {
                        "G:${override.name}"
                    } else {
                        "G"
                    }
                }
            }
            "${key.flattenToString()}=$assignmentStr"
        }

    private fun deserializeAppAssignments(raw: String): Map<ComponentKey, AppAssignment> {
        if (raw.isBlank()) return emptyMap()
        val map = mutableMapOf<ComponentKey, AppAssignment>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                val eqIndex = trimmed.indexOf('=')
                if (eqIndex > 0) {
                    val keyStr = trimmed.substring(0, eqIndex)
                    val valStr = trimmed.substring(eqIndex + 1)
                    val key = runCatching { ComponentKey.fromFlatString(keyStr) }.getOrNull()
                    if (key != null) {
                        val assignment = when {
                            valStr == "S" -> AppAssignment.Standard
                            valStr == "B" -> AppAssignment.Blocked
                            valStr == "G" -> AppAssignment.Guarded(null)
                            valStr.startsWith("G:") -> {
                                val choiceName = valStr.substring(2)
                                val choice = runCatching { FrictionChoice.valueOf(choiceName) }.getOrNull()
                                AppAssignment.Guarded(choice)
                            }
                            else -> null
                        }
                        if (assignment != null) {
                            map[key] = assignment
                        }
                    }
                }
            }
        }
        return map
    }

    private fun serializeSavedTriggers(triggers: List<SavedTrigger>): String {
        val jsonArray = JSONArray()
        for (saved in triggers) {
            val obj = JSONObject()
            obj.put("id", saved.id)
            obj.put("name", saved.name)
            obj.put("trigger", serializeProfileTrigger(saved.trigger))
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun deserializeSavedTriggers(raw: String): List<SavedTrigger> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<SavedTrigger>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val trigger = deserializeProfileTrigger(obj.optJSONObject("trigger"))
                    ?: ProfileTrigger.Schedule()
                list.add(SavedTrigger(id = id, name = name, trigger = trigger))
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun serializeProfileTrigger(trigger: ProfileTrigger): JSONObject {
        val triggerObj = JSONObject()
        when (trigger) {
            is ProfileTrigger.Schedule -> {
                val schedule = trigger.schedule
                triggerObj.put("type", "SCHEDULE")
                triggerObj.put("sameScheduleEveryDay", schedule.sameScheduleEveryDay)
                val dailyWindowsObj = JSONObject()
                schedule.dailyWindows.forEach { (day, window) ->
                    val windowObj = JSONObject()
                    windowObj.put("startHour", window.startTime.hour)
                    windowObj.put("startMinute", window.startTime.minute)
                    windowObj.put("endHour", window.endTime.hour)
                    windowObj.put("endMinute", window.endTime.minute)
                    dailyWindowsObj.put(day.name, windowObj)
                }
                triggerObj.put("dailyWindows", dailyWindowsObj)

                // Legacy fallback fields
                val daysArray = JSONArray()
                schedule.days.forEach { daysArray.put(it.name) }
                triggerObj.put("days", daysArray)
                triggerObj.put("startHour", schedule.startTime.hour)
                triggerObj.put("startMinute", schedule.startTime.minute)
                triggerObj.put("endHour", schedule.endTime.hour)
                triggerObj.put("endMinute", schedule.endTime.minute)
            }
            is ProfileTrigger.Geo -> {
                val geo = trigger.geo
                triggerObj.put("type", "GEO")
                triggerObj.put("latitude", geo.latitude)
                triggerObj.put("longitude", geo.longitude)
                triggerObj.put("radiusMeters", geo.radiusMeters)
                triggerObj.put("mode", geo.mode.name)
            }
            is ProfileTrigger.Nfc -> {
                val nfc = trigger.nfc
                triggerObj.put("type", "NFC")
                triggerObj.put("mode", nfc.mode.name)
                triggerObj.put("durationMinutes", nfc.durationMinutes)
                val tagsArray = JSONArray()
                for (tag in nfc.tags) {
                    val tagObj = JSONObject()
                    tagObj.put("uid", tag.normalizedUid)
                    tagObj.put("label", tag.label)
                    tagsArray.put(tagObj)
                }
                triggerObj.put("tags", tagsArray)
            }
        }
        return triggerObj
    }

    private fun deserializeProfileTrigger(triggerObj: JSONObject?): ProfileTrigger? {
        if (triggerObj == null) return null
        return when (triggerObj.optString("type")) {
            "SCHEDULE" -> {
                val sameScheduleEveryDay = triggerObj.optBoolean("sameScheduleEveryDay", true)
                val dailyWindowsObj = triggerObj.optJSONObject("dailyWindows")
                if (dailyWindowsObj != null) {
                    val dailyWindows = mutableMapOf<DayOfWeek, TimeWindow>()
                    val keys = dailyWindowsObj.keys()
                    while (keys.hasNext()) {
                        val dayKey = keys.next()
                        val day = runCatching { DayOfWeek.valueOf(dayKey) }.getOrNull()
                        val windowObj = dailyWindowsObj.optJSONObject(dayKey)
                        if (day != null && windowObj != null) {
                            val startHour = windowObj.optInt("startHour", 9)
                            val startMinute = windowObj.optInt("startMinute", 0)
                            val endHour = windowObj.optInt("endHour", 17)
                            val endMinute = windowObj.optInt("endMinute", 0)
                            dailyWindows[day] = TimeWindow(
                                startTime = TimeOfDay(startHour, startMinute),
                                endTime = TimeOfDay(endHour, endMinute),
                            )
                        }
                    }
                    ProfileTrigger.Schedule(
                        ScheduleTrigger(
                            dailyWindows = dailyWindows,
                            sameScheduleEveryDay = sameScheduleEveryDay,
                        ),
                    )
                } else {
                    val daysArray = triggerObj.optJSONArray("days")
                    val days = mutableSetOf<DayOfWeek>()
                    if (daysArray != null) {
                        for (j in 0 until daysArray.length()) {
                            runCatching { DayOfWeek.valueOf(daysArray.getString(j)) }
                                .getOrNull()
                                ?.let { days.add(it) }
                        }
                    }
                    val startHour = triggerObj.optInt("startHour", 9)
                    val startMinute = triggerObj.optInt("startMinute", 0)
                    val endHour = triggerObj.optInt("endHour", 17)
                    val endMinute = triggerObj.optInt("endMinute", 0)
                    ProfileTrigger.Schedule(
                        ScheduleTrigger(
                            days = days,
                            startTime = TimeOfDay(startHour, startMinute),
                            endTime = TimeOfDay(endHour, endMinute),
                            sameScheduleEveryDay = sameScheduleEveryDay,
                        ),
                    )
                }
            }
            "GEO" -> {
                val latitude = triggerObj.optDouble("latitude", 0.0).coerceIn(-90.0, 90.0)
                val longitude = triggerObj.optDouble("longitude", 0.0).coerceIn(-180.0, 180.0)
                val radiusMeters = triggerObj.optInt("radiusMeters", GeoTrigger.DEFAULT_RADIUS_METERS)
                    .coerceAtLeast(1)
                val mode = runCatching {
                    GeoTriggerMode.valueOf(triggerObj.optString("mode", GeoTriggerMode.INSIDE.name))
                }.getOrDefault(GeoTriggerMode.INSIDE)
                ProfileTrigger.Geo(
                    GeoTrigger(
                        latitude = latitude,
                        longitude = longitude,
                        radiusMeters = radiusMeters,
                        mode = mode,
                    ),
                )
            }
            "NFC" -> {
                val mode = runCatching {
                    NfcTriggerMode.valueOf(triggerObj.optString("mode", NfcTriggerMode.TOGGLE_ON_TAP.name))
                }.getOrDefault(NfcTriggerMode.TOGGLE_ON_TAP)
                val durationMinutes = triggerObj.optInt("durationMinutes", NfcTrigger.DEFAULT_DURATION_MINUTES)
                    .coerceIn(NfcTrigger.MIN_DURATION_MINUTES, NfcTrigger.MAX_DURATION_MINUTES)
                val tags = mutableListOf<NfcTagEntry>()
                val tagsArray = triggerObj.optJSONArray("tags")
                if (tagsArray != null) {
                    for (t in 0 until tagsArray.length()) {
                        val tagObj = tagsArray.optJSONObject(t)
                        if (tagObj != null) {
                            val uid = tagObj.optString("uid", "")
                            val label = tagObj.optString("label", "")
                            val normalized = NfcTagEntry.normalizeUid(uid)
                            if (normalized.isNotBlank()) {
                                tags.add(NfcTagEntry(uid = normalized, label = label))
                            }
                        }
                    }
                }
                ProfileTrigger.Nfc(
                    NfcTrigger(
                        tags = tags,
                        mode = mode,
                        durationMinutes = durationMinutes,
                    ),
                )
            }
            else -> null
        }
    }

    private fun serializeCustomProfiles(profiles: List<CustomProfile>): String {
        val jsonArray = JSONArray()
        for (profile in profiles) {
            val obj = JSONObject()
            obj.put("id", profile.id)
            obj.put("name", profile.name)
            obj.put("enabled", profile.enabled)
            obj.put("priority", profile.priority)
            obj.put("colorIndex", profile.colorIndex)
            profile.frictionOverride?.let { obj.put("frictionOverride", it.name) }

            val rulesArray = JSONArray()
            for (rule in profile.activationRules) {
                val ruleIdsArray = JSONArray()
                rule.triggerIds.forEach { ruleIdsArray.put(it) }
                rulesArray.put(ruleIdsArray)
            }
            obj.put("activationRules", rulesArray)

            profile.trigger?.let { obj.put("trigger", serializeProfileTrigger(it)) }
            obj.put("appAssignments", serializeAppAssignments(profile.appAssignments))
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun deserializeCustomProfiles(raw: String): List<CustomProfile> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<CustomProfile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val enabled = obj.optBoolean("enabled", true)
                val priority = obj.optInt("priority", i)
                val colorIndex = obj.optInt("colorIndex", i % CustomProfile.PALETTE_SIZE)
                val frictionOverride = if (obj.has("frictionOverride")) {
                    runCatching { FrictionChoice.valueOf(obj.getString("frictionOverride")) }.getOrNull()
                } else {
                    null
                }

                val activationRules = mutableListOf<TriggerRule>()
                val rulesArray = obj.optJSONArray("activationRules")
                if (rulesArray != null) {
                    for (r in 0 until rulesArray.length()) {
                        val ruleIdsArray = rulesArray.optJSONArray(r)
                        if (ruleIdsArray != null) {
                            val triggerIds = mutableListOf<String>()
                            for (t in 0 until ruleIdsArray.length()) {
                                val triggerId = ruleIdsArray.optString(t)
                                if (triggerId.isNotBlank()) {
                                    triggerIds.add(triggerId)
                                }
                            }
                            if (triggerIds.isNotEmpty()) {
                                activationRules.add(TriggerRule(triggerIds))
                            }
                        }
                    }
                }

                val trigger = deserializeProfileTrigger(obj.optJSONObject("trigger"))

                val appAssignmentsRaw = obj.optString("appAssignments", "")
                val appAssignments = deserializeAppAssignments(appAssignmentsRaw)

                list.add(
                    CustomProfile(
                        id = id,
                        name = name,
                        enabled = enabled,
                        priority = priority,
                        activationRules = activationRules,
                        trigger = trigger,
                        appAssignments = appAssignments,
                        frictionOverride = frictionOverride,
                        colorIndex = colorIndex,
                    ),
                )
            }
            list.sortedBy { it.priority }
        }.getOrDefault(emptyList())
    }
}
