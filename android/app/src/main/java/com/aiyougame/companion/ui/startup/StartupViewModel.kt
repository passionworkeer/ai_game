package com.aiyougame.companion.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyougame.companion.data.repository.AuthRepository
import com.aiyougame.companion.data.prefs.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Startup flow: checks login status and auto-registers device if needed.
 * All operations run on IO dispatcher to avoid blocking UI.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    sealed class StartupState {
        object Loading : StartupState()
        object AlreadyLoggedIn : StartupState()
        data class RegistrationSuccess(val userId: String) : StartupState()
        data class RegistrationError(val message: String) : StartupState()
    }

    private val _uiState = MutableStateFlow<StartupState>(StartupState.Loading)
    val uiState: StateFlow<StartupState> = _uiState.asStateFlow()

    companion object {
        private const val APP_VERSION = "1.0.0"
        private const val PLATFORM = "android"
    }

    init {
        checkAndRegister()
    }

    fun checkAndRegister() {
        viewModelScope.launch {
            _uiState.value = StartupState.Loading

            // Already logged in - skip registration
            if (authRepository.isLoggedIn()) {
                _uiState.update {
                    StartupState.AlreadyLoggedIn
                }
                return@launch
            }

            // Get or generate device ID
            val deviceId = tokenManager.getDeviceId() ?: UUID.randomUUID().toString().also {
                tokenManager.saveDeviceId(it)
            }

            // Register device
            val result = authRepository.registerDevice(
                deviceId = deviceId,
                version = APP_VERSION,
                platform = PLATFORM
            )

            result.fold(
                onSuccess = { response ->
                    _uiState.update {
                        StartupState.RegistrationSuccess(response.userId)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        StartupState.RegistrationError(
                            error.message ?: "Registration failed"
                        )
                    }
                }
            )
        }
    }

    fun retry() {
        checkAndRegister()
    }
}
