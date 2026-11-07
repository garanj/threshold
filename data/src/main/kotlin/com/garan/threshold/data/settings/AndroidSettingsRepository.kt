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
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.GeneralSettings
import com.garan.threshold.core.model.ThresholdSettings
import com.garan.threshold.core.port.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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

                prefs[Keys.QUICK_ACTIONS] = prunedQuickActions.joinToString(";") { it.flattenToString() }
                prefs[Keys.APP_ASSIGNMENTS] = serializeAppAssignments(prunedAssignments)
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
}
