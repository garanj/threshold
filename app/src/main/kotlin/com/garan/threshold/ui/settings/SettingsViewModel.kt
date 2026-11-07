package com.garan.threshold.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.GeneralSettings
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.core.port.PackageIndexSource
import com.garan.threshold.core.port.SettingsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val general: GeneralSettings = GeneralSettings(),
    val baseProfile: BaseProfileSettings = BaseProfileSettings(),
    val customProfiles: ImmutableList<com.garan.threshold.core.model.CustomProfile> = persistentListOf(),
    val availableApps: ImmutableList<InstalledApp> = persistentListOf(),
    val isLoading: Boolean = true,
)

class SettingsViewModel(
    private val packageIndexSource: PackageIndexSource,
    private val settingsRepository: SettingsRepository,
    private val dialerPackageResolver: () -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val isIndexLoaded = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.observeSettings().collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        general = settings.general,
                        baseProfile = settings.baseProfile,
                        customProfiles = settings.customProfiles.toImmutableList(),
                    )
                }
            }
        }
    }

    /**
     * Lazily resolves installed applications when entering Settings or Configuration screens.
     * Preserves low ambient memory footprint (< 25 MB PSS) on the home canvas.
     */
    fun ensureLoaded() {
        if (!isIndexLoaded.compareAndSet(false, true)) return
        viewModelScope.launch(ioDispatcher) {
            val dialerPkg = dialerPackageResolver()
            val apps = packageIndexSource.getInstalledApps()
                .filterNot { it.component.packageName == dialerPkg }
                .sortedBy { it.label.lowercase() }

            _uiState.update {
                it.copy(
                    availableApps = apps.toImmutableList(),
                    isLoading = false,
                )
            }
        }
    }

    fun updateShowUnlocks(show: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val current = settingsRepository.getSettings().general
            settingsRepository.updateGeneralSettings(current.copy(showUnlocks = show))
        }
    }

    fun updateShowRest(show: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val current = settingsRepository.getSettings().general
            settingsRepository.updateGeneralSettings(current.copy(showRest = show))
        }
    }

    fun updateRequireFullTyping(requireFull: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val current = settingsRepository.getSettings().general
            settingsRepository.updateGeneralSettings(current.copy(requireFullTypingOfGuardedApps = requireFull))
        }
    }

    fun updateDefaultFriction(friction: FrictionChoice) {
        viewModelScope.launch(ioDispatcher) {
            val current = settingsRepository.getSettings().baseProfile
            settingsRepository.updateBaseProfile(current.copy(defaultFriction = friction))
        }
    }

    fun addQuickAction(component: ComponentKey) {
        viewModelScope.launch(ioDispatcher) {
            val current = settingsRepository.getSettings().baseProfile
            if (current.quickActions.size < BaseProfileSettings.MAX_QUICK_ACTIONS &&
                component !in current.quickActions
            ) {
                settingsRepository.updateBaseProfile(
                    current.copy(quickActions = current.quickActions + component),
                )
            }
        }
    }

    fun removeQuickAction(component: ComponentKey) {
        viewModelScope.launch(ioDispatcher) {
            val current = settingsRepository.getSettings().baseProfile
            settingsRepository.updateBaseProfile(
                current.copy(quickActions = current.quickActions - component),
            )
        }
    }

    fun reorderQuickActions(reordered: List<ComponentKey>) {
        viewModelScope.launch(ioDispatcher) {
            val current = settingsRepository.getSettings().baseProfile
            if (reordered.size <= BaseProfileSettings.MAX_QUICK_ACTIONS) {
                settingsRepository.updateBaseProfile(current.copy(quickActions = reordered))
            }
        }
    }

    fun saveAppAssignments(assignments: Map<ComponentKey, AppAssignment>) {
        viewModelScope.launch(ioDispatcher) {
            val current = settingsRepository.getSettings().baseProfile
            settingsRepository.updateBaseProfile(current.copy(appAssignments = assignments))
        }
    }

    fun saveCustomProfile(profile: com.garan.threshold.core.model.CustomProfile) {
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.saveCustomProfile(profile)
        }
    }

    fun deleteCustomProfile(profileId: String) {
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.deleteCustomProfile(profileId)
        }
    }

    fun reorderCustomProfiles(profiles: List<com.garan.threshold.core.model.CustomProfile>) {
        viewModelScope.launch(ioDispatcher) {
            val updated = profiles.mapIndexed { index, profile ->
                profile.copy(priority = index)
            }
            settingsRepository.updateCustomProfiles(updated)
        }
    }

    fun saveCustomProfileAppAssignments(profileId: String, assignments: Map<ComponentKey, AppAssignment>) {
        viewModelScope.launch(ioDispatcher) {
            val current = settingsRepository.getSettings().customProfiles
            val target = current.firstOrNull { it.id == profileId } ?: return@launch
            val updated = target.copy(appAssignments = assignments)
            settingsRepository.saveCustomProfile(updated)
        }
    }
}
