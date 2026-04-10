package com.aiyougame.companion.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.di.MainDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * Settings screen state holder.
 * Manages cloud sync preference, OpenClaw IP, and logout.
 * Cloud sync defaults to OFF (privacy-first per PRIVACY.md).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val tokenManager: TokenManager,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewModel() {

    data class UiState(
        val cloudSyncEnabled: Boolean = false,
        val openClawIp: String = "",
        val isSaving: Boolean = false,
    )

    sealed class SettingsUiEvent {
        object LoggedOut : SettingsUiEvent()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsUiEvent>()
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    companion object {
        const val KEY_CLOUD_SYNC = "cloud_sync_enabled"
        const val KEY_OPENCLAW_IP = "openclaw_ip"
    }

    init {
        loadSettings()
    }

    fun loadSettings() {
        val syncEnabled = prefs.getBoolean(KEY_CLOUD_SYNC, false)
        val ip = prefs.getString(KEY_OPENCLAW_IP, null) ?: ""
        _uiState.update { it.copy(cloudSyncEnabled = syncEnabled, openClawIp = ip) }
    }

    fun toggleCloudSync(enabled: Boolean) {
        _uiState.update { it.copy(cloudSyncEnabled = enabled) }
        prefs.edit()
            .putBoolean(KEY_CLOUD_SYNC, enabled)
            .apply()
    }

    fun setOpenClawIp(ip: String) {
        _uiState.update { it.copy(openClawIp = ip) }
        prefs.edit()
            .putString(KEY_OPENCLAW_IP, ip)
            .apply()
    }

    fun saveSettings() {
        viewModelScope.launch(context = mainDispatcher, start = CoroutineStart.DEFAULT) {
            _uiState.update { it.copy(isSaving = true) }
            val state = _uiState.value
            prefs.edit()
                .putBoolean(KEY_CLOUD_SYNC, state.cloudSyncEnabled)
                .putString(KEY_OPENCLAW_IP, state.openClawIp)
                .apply()
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    fun logout() {
        viewModelScope.launch(context = mainDispatcher, start = CoroutineStart.DEFAULT) {
            tokenManager.clear()
            _uiState.update { it.copy(cloudSyncEnabled = false, openClawIp = "") }
            _events.emit(SettingsUiEvent.LoggedOut)
        }
    }

    fun clearAll() {
        logout()
    }
}
