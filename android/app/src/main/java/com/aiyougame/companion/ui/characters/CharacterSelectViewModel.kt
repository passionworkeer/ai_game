package com.aiyougame.companion.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyougame.companion.data.model.CharacterDto
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.data.repository.CharactersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Character selection screen state holder.
 * Loads character catalog from backend, manages user selection.
 */
@HiltViewModel
class CharacterSelectViewModel @Inject constructor(
    private val charactersRepository: CharactersRepository,
    private val tokenManager: TokenManager,
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Success(
            val ownedCharacters: List<CharacterDto>,
            val unlockedCharacters: List<CharacterDto>,
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedCharacter = MutableStateFlow<CharacterDto?>(null)
    val selectedCharacter: StateFlow<CharacterDto?> = _selectedCharacter.asStateFlow()

    init {
        loadCharacters()
    }

    fun loadCharacters() {
        viewModelScope.launch {
            _uiState.update { UiState.Loading }
            charactersRepository.getCharacters().fold(
                onSuccess = { characters ->
                    _uiState.update {
                        UiState.Success(
                            ownedCharacters = characters.filter { it.isOwned },
                            unlockedCharacters = characters.filter { !it.isOwned },
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        UiState.Error(error.message ?: "加载角色列表失败")
                    }
                }
            )
        }
    }

    fun selectCharacter(character: CharacterDto) {
        _selectedCharacter.update { character }
        tokenManager.saveSelectedCharacter(character.code)
    }

    fun getSelectedCharacterCode(): String {
        return tokenManager.getSelectedCharacter() ?: "gu_chen"
    }
}
