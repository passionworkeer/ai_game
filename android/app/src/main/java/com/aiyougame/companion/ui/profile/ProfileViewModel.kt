package com.aiyougame.companion.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyougame.companion.data.model.ProfileJson
import com.aiyougame.companion.data.model.KeyEventDto
import com.aiyougame.companion.data.repository.SyncRepository
import com.aiyougame.companion.data.prefs.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Profile screen state holder.
 * Handles loading and updating the user's nickname and profileJson.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    sealed class ProfileUiState {
        object Idle : ProfileUiState()
        object Loading : ProfileUiState()
        data class Success(
            val nickname: String,
            val profileJson: ProfileJson,
            val keyEvents: List<KeyEventDto>,
        ) : ProfileUiState()
        data class Error(val message: String) : ProfileUiState()
    }

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile() {
        val userId = tokenManager.getUserId()
        if (userId == null) {
            _uiState.value = ProfileUiState.Error("请先注册设备")
            return
        }

        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            syncRepository.getProfile()
                .onSuccess { data ->
                    _uiState.value = ProfileUiState.Success(
                        nickname = data.nickname,
                        profileJson = data.profileJson,
                        keyEvents = data.keyEvents,
                    )
                }
                .onFailure { e ->
                    _uiState.value = ProfileUiState.Error(e.message ?: "加载失败")
                }
        }
    }

    fun updateNickname(nickname: String) {
        viewModelScope.launch {
            syncRepository.updateProfile(nickname = nickname, profileJson = null)
                .onSuccess { loadProfile() }
                .onFailure { /* silently fail — UI may show a toast */ }
        }
    }
}
