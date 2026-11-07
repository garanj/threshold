package com.garan.threshold.core.port

import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.GeneralSettings
import com.garan.threshold.core.model.ThresholdSettings
import kotlinx.coroutines.flow.Flow

/**
 * Port for observing and persisting user settings, base app assignments, and quick actions.
 */
interface SettingsRepository {

    /**
     * Emits the latest [ThresholdSettings] whenever any preference or assignment updates.
     */
    fun observeSettings(): Flow<ThresholdSettings>

    /**
     * Reads the current snapshot of [ThresholdSettings].
     */
    suspend fun getSettings(): ThresholdSettings

    /**
     * Updates the [GeneralSettings].
     */
    suspend fun updateGeneralSettings(general: GeneralSettings)

    /**
     * Updates the [BaseProfileSettings] (friction preferences, quick actions, and app assignments).
     */
    suspend fun updateBaseProfile(baseProfile: BaseProfileSettings)

    /**
     * Sets whether the first-run onboarding hint has been displayed.
     */
    suspend fun setHasSeenFirstRunHint(hasSeen: Boolean)

    /**
     * Removes specified app components from assignments and quick action slots (e.g. upon uninstallation).
     */
    suspend fun removeApps(keys: Set<ComponentKey>)

    /**
     * Updates the full ordered list of [com.garan.threshold.core.model.CustomProfile]s.
     */
    suspend fun updateCustomProfiles(profiles: List<com.garan.threshold.core.model.CustomProfile>)

    /**
     * Adds or updates a single [com.garan.threshold.core.model.CustomProfile].
     */
    suspend fun saveCustomProfile(profile: com.garan.threshold.core.model.CustomProfile)

    /**
     * Deletes a [com.garan.threshold.core.model.CustomProfile] by its ID.
     */
    suspend fun deleteCustomProfile(profileId: String)
}
