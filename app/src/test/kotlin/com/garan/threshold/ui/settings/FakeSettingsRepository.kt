package com.garan.threshold.ui.settings

import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.GeneralSettings
import com.garan.threshold.core.model.NfcDailyUsage
import com.garan.threshold.core.model.NfcLatchState
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.ThresholdSettings
import com.garan.threshold.core.port.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initialSettings: ThresholdSettings = ThresholdSettings()) : SettingsRepository {

    private val _settings = MutableStateFlow(initialSettings)

    override fun observeSettings(): Flow<ThresholdSettings> = _settings.asStateFlow()

    override suspend fun getSettings(): ThresholdSettings = _settings.value

    override suspend fun updateGeneralSettings(general: GeneralSettings) {
        _settings.update { it.copy(general = general) }
    }

    override suspend fun updateBaseProfile(baseProfile: BaseProfileSettings) {
        _settings.update { it.copy(baseProfile = baseProfile) }
    }

    override suspend fun setHasSeenFirstRunHint(hasSeen: Boolean) {
        _settings.update { it.copy(general = it.general.copy(hasSeenFirstRunHint = hasSeen)) }
    }

    override suspend fun removeApps(keys: Set<ComponentKey>) {
        _settings.update { current ->
            val pkgNames = keys.map { it.packageName }.toSet()
            val newQuick = current.baseProfile.quickActions.filter { it !in keys && it.packageName !in pkgNames }
            val newAssignments = current.baseProfile.appAssignments.filter { (comp, _) ->
                comp !in keys && comp.packageName !in pkgNames
            }
            val newProfiles = current.customProfiles.map { profile ->
                val pruned = profile.appAssignments.filter { (comp, _) ->
                    comp !in keys && comp.packageName !in pkgNames
                }
                profile.copy(
                    appAssignments = pruned,
                    enabled = if (pruned.isEmpty() && profile.appAssignments.isNotEmpty()) false else profile.enabled,
                )
            }
            current.copy(
                baseProfile = current.baseProfile.copy(
                    quickActions = newQuick,
                    appAssignments = newAssignments,
                ),
                customProfiles = newProfiles,
            )
        }
    }

    override suspend fun updateCustomProfiles(profiles: List<com.garan.threshold.core.model.CustomProfile>) {
        _settings.update { it.copy(customProfiles = profiles) }
    }

    override suspend fun saveCustomProfile(profile: com.garan.threshold.core.model.CustomProfile) {
        _settings.update { current ->
            val existingIndex = current.customProfiles.indexOfFirst { it.id == profile.id }
            val updated = if (existingIndex >= 0) {
                current.customProfiles.toMutableList().apply { set(existingIndex, profile) }
            } else {
                current.customProfiles + profile
            }
            current.copy(customProfiles = updated)
        }
    }

    override suspend fun deleteCustomProfile(profileId: String) {
        _settings.update { current ->
            current.copy(customProfiles = current.customProfiles.filter { it.id != profileId })
        }
    }

    override suspend fun saveTrigger(trigger: SavedTrigger) {
        _settings.update { current ->
            val existingIndex = current.savedTriggers.indexOfFirst { it.id == trigger.id }
            val updated = if (existingIndex >= 0) {
                current.savedTriggers.toMutableList().apply { set(existingIndex, trigger) }
            } else {
                current.savedTriggers + trigger
            }
            val existingLatch = current.nfcLatches[trigger.id]
            val updatedLatches = if (existingLatch != null) {
                val mutableLatches = LinkedHashMap(current.nfcLatches)
                val reconciled = (trigger.trigger as? ProfileTrigger.Nfc)
                    ?.nfc
                    ?.let { existingLatch.reconcileWith(it, nowEpochMillis = 0L) }
                if (reconciled == null) {
                    mutableLatches.remove(trigger.id)
                } else {
                    mutableLatches[trigger.id] = reconciled
                }
                mutableLatches
            } else {
                current.nfcLatches
            }
            current.copy(
                savedTriggers = updated,
                nfcLatches = updatedLatches,
            )
        }
    }

    override suspend fun deleteTrigger(triggerId: String) {
        _settings.update { current ->
            val updatedTriggers = current.savedTriggers.filter { it.id != triggerId }
            val updatedProfiles = current.customProfiles.map { profile ->
                if (triggerId in profile.referencedTriggerIds) {
                    profile.withoutTrigger(triggerId)
                } else {
                    profile
                }
            }
            current.copy(
                savedTriggers = updatedTriggers,
                customProfiles = updatedProfiles,
                nfcLatches = current.nfcLatches - triggerId,
                nfcDailyUsage = current.nfcDailyUsage - triggerId,
            )
        }
    }

    override suspend fun updateNfcLatches(
        latches: Map<String, NfcLatchState>,
        dailyUsage: Map<String, NfcDailyUsage>?,
    ) {
        _settings.update {
            it.copy(
                nfcLatches = latches,
                nfcDailyUsage = dailyUsage ?: it.nfcDailyUsage,
            )
        }
    }
}
