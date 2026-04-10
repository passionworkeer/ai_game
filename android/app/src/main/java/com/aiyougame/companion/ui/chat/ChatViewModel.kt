package com.aiyougame.companion.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyougame.companion.data.repository.SyncRepository
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.di.MainDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

/**
 * Chat screen state holder.
 * Manages message list, typing indicator, and affection score.
 * All AI inference is delegated to Phase 2; Phase 1 simulates responses.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val tokenManager: TokenManager,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewModel() {

    data class UiState(
        val messages: List<ChatMessageUi> = emptyList(),
        val isLoading: Boolean = false,
        val typingCharacter: String? = null,
        val affectionScore: Int = 50,
    )

    sealed class UiEvent {
        data class Error(val message: String) : UiEvent()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    companion object {
        const val MAX_MESSAGE_LENGTH = 1000
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_MESSAGE_LENGTH) return

        val userMsg = ChatMessageUi(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            isFromUser = true,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update { it.copy(messages = it.messages + userMsg) }

        // Phase 1: no real AI — simulate a typing indicator then a canned response
        viewModelScope.launch(context = mainDispatcher, start = CoroutineStart.DEFAULT) {
            simulateModelResponse()
        }
    }

    private suspend fun simulateModelResponse() {
        _uiState.update { it.copy(typingCharacter = "顾晨") }
        delay(1500) // simulate typing delay

        val modelMsg = ChatMessageUi(
            id = UUID.randomUUID().toString(),
            text = "收到啦～谢谢你跟我说这些",
            isFromUser = false,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update {
            it.copy(
                messages = it.messages + modelMsg,
                typingCharacter = null
            )
        }
    }

    fun updateAffection(score: Int) {
        _uiState.update {
            it.copy(affectionScore = score.coerceIn(0, 100))
        }
    }
}

/**
 * Immutable UI model for a single chat message.
 */
data class ChatMessageUi(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long,
    val isToolCall: Boolean = false,
    val isTyping: Boolean = false,
)
