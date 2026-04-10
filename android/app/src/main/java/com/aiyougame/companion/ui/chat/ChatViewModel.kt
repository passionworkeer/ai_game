package com.aiyougame.companion.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyougame.companion.data.repository.SyncRepository
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.di.MainDispatcher
import com.aiyougame.companion.llm.LlamaEngine
import com.aiyougame.companion.llm.LlamaEngineManager
import com.aiyougame.companion.llm.PromptManager
import com.aiyougame.companion.memory.MemoryManager
import com.aiyougame.companion.memory.ProfileExtractor
import com.aiyougame.companion.memory.db.ChatMessageDao
import com.aiyougame.companion.memory.db.ChatMessageEntity
import com.aiyougame.companion.memory.db.KeyEventDao
import com.aiyougame.companion.memory.db.KeyEventEntity
import com.aiyougame.companion.memory.db.UserProfileDao
import com.aiyougame.companion.memory.db.UserProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

/**
 * Chat screen state holder.
 * Manages message list, typing indicator, and affection score.
 * All AI inference is delegated to Phase 2; Phase 1 simulates responses.
 *
 * Supports multi-character: each character has isolated chat history and profile.
 * Uses LlamaEngineManager for per-character engine instances with LRU eviction.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val tokenManager: TokenManager,
    private val chatMessageDao: ChatMessageDao,
    private val userProfileDao: UserProfileDao,
    private val keyEventDao: KeyEventDao,
    private val profileExtractor: ProfileExtractor,
    private val llamaEngineManager: LlamaEngineManager,
    private val promptManager: PromptManager,
    private val memoryManager: MemoryManager,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * Current character code for this chat session.
     * Defaults to 'gu_chen'. Can be changed via [switchCharacter].
     */
    private var currentCharacterCode: String = savedStateHandle.get<String>("characterCode") ?: "gu_chen"

    /** Current engine instance for this character. Lazily initialized. */
    private var currentEngine: LlamaEngine? = null

    init {
        // Initialize LLM engine for the current character (Phase 1 mock returns immediately)
        viewModelScope.launch(mainDispatcher) {
            val engine = llamaEngineManager.getEngine(currentCharacterCode)
            engine.initialize()
            currentEngine = engine
        }
        // Load recent chat history from Room on startup
        loadChatHistory(currentCharacterCode)

        // Load affection level from Room on startup
        loadAffectionLevel(currentCharacterCode)
    }

    private fun loadChatHistory(characterCode: String) {
        viewModelScope.launch(mainDispatcher) {
            chatMessageDao.queryRecentByCharacter(characterCode, 20)
                .first()
                .map { entity ->
                    ChatMessageUi(
                        id = entity.msgId.toString(),
                        text = entity.content,
                        isFromUser = entity.role == "user",
                        timestamp = entity.timestamp ?: 0L
                    )
                }
                .let { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
        }
    }

    private fun loadAffectionLevel(characterCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userProfileDao.getByCharacter(characterCode)?.let { profile ->
                withContext(mainDispatcher) {
                    _uiState.update { it.copy(affectionScore = profile.affectionLevel.coerceIn(0, 100)) }
                }
            }
        }
    }

    data class UiState(
        val messages: List<ChatMessageUi> = emptyList(),
        val isLoading: Boolean = false,
        val typingCharacter: String? = null,
        val affectionScore: Int = 50,
        val characterCode: String = "gu_chen",
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

    /**
     * Switch to a different character.
     * Releases the current engine, loads the new character's chat history.
     * Persists the new character code to SavedStateHandle for process-death recovery.
     */
    fun switchCharacter(characterCode: String) {
        if (characterCode == currentCharacterCode) return

        // Release the old engine for this character (but keep it in the pool for LRU eviction)
        currentCharacterCode = characterCode
        _uiState.update { it.copy(messages = emptyList(), characterCode = characterCode) }

        // Get engine for new character (may evict LRU if at capacity)
        viewModelScope.launch(mainDispatcher) {
            val engine = llamaEngineManager.getEngine(characterCode)
            currentEngine = engine
        }

        loadChatHistory(characterCode)
        loadAffectionLevel(characterCode)
    }

    fun getCurrentCharacterCode(): String = currentCharacterCode

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

        // Persist user message to Room on IO thread
        viewModelScope.launch(Dispatchers.IO) {
            chatMessageDao.insert(
                ChatMessageEntity(
                    role = "user",
                    content = trimmed,
                    timestamp = userMsg.timestamp,
                    characterCode = currentCharacterCode
                )
            )
        }

        // Phase 1: no real AI — simulate a typing indicator then a canned response
        viewModelScope.launch(context = mainDispatcher, start = CoroutineStart.DEFAULT) {
            simulateModelResponse(trimmed)
        }
    }

    private suspend fun simulateModelResponse(trimmed: String) {
        _uiState.update { it.copy(typingCharacter = "顾晨") }

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val snapshot = memoryManager.buildSnapshot(currentCharacterCode)
        val systemPrompt = promptManager.buildSystemPrompt(
            characterId = currentCharacterCode,
            affectionLevel = _uiState.value.affectionScore,
            snapshot = snapshot,
        )

        // Get the engine for this character (ensure it's initialized)
        val engine = currentEngine ?: llamaEngineManager.getEngine(currentCharacterCode).also {
            currentEngine = it
        }

        // Accumulate tokens streamed from the engine for real-time display
        val accumulated = StringBuilder()
        engine.generateResponse(userMessage = trimmed, systemPrompt = systemPrompt)
            .collect { token ->
                accumulated.append(token)
                _uiState.update { state ->
                    val replaced = state.messages.toMutableList()
                    val lastIndex = replaced.indexOfLast { !it.isFromUser }
                    val partial = ChatMessageUi(
                        id = messageId,
                        text = accumulated.toString(),
                        isFromUser = false,
                        timestamp = timestamp,
                        isTyping = true
                    )
                    if (lastIndex >= 0) {
                        replaced[lastIndex] = partial
                    } else {
                        replaced.add(partial)
                    }
                    state.copy(messages = replaced)
                }
            }

        // Mark the message as complete (no typing indicator)
        _uiState.update { state ->
            val replaced = state.messages.toMutableList()
            val lastIndex = replaced.indexOfLast { !it.isFromUser }
            if (lastIndex >= 0) {
                replaced[lastIndex] = replaced[lastIndex].copy(isTyping = false)
            }
            state.copy(messages = replaced, typingCharacter = null)
        }

        // Persist AI response to Room on IO thread
        viewModelScope.launch(Dispatchers.IO) {
            chatMessageDao.insert(
                ChatMessageEntity(
                    role = "assistant",
                    content = accumulated.toString(),
                    timestamp = timestamp,
                    characterCode = currentCharacterCode
                )
            )
        }

        // Analyse user profile and update affection score
        viewModelScope.launch(Dispatchers.IO) {
            updateProfile(trimmed)
        }
    }

    /**
     * Extract profile information from the user's message and persist it locally.
     * All Room operations run on Dispatchers.IO.
     * Nickname is only set if not already present (avoids overwriting existing).
     * Affection increases by 1 per message round, capped at 100.
     */
    private suspend fun updateProfile(text: String) {
        val extraction = profileExtractor.extract(text)

        // Load current profile for this character (create default if absent)
        val current = userProfileDao.getByCharacter(currentCharacterCode)
            ?: UserProfileEntity(characterCode = currentCharacterCode)

        // Only set nickname if not already set — prefer first extracted value
        val updatedNickname = current.nickname ?: extraction.nickname

        // Merge likes/dislikes if extracted (append to existing JSON arrays)
        val existingLikes = if (current.interests.isNotBlank() && current.interests != "[]") {
            profileExtractor.parseJsonArray(current.interests).toMutableList()
        } else {
            mutableListOf()
        }
        extraction.likes?.let { newLikes ->
            existingLikes.addAll(profileExtractor.parseJsonArray(newLikes))
        }
        val updatedLikes = if (existingLikes.isNotEmpty()) {
            "[" + existingLikes.distinct().joinToString(",") { "\"$it\"" } + "]"
        } else {
            current.interests
        }

        // Increment affection: +1 per round, cap at 100
        val newAffection = (current.affectionLevel + 1).coerceAtMost(100)

        // Persist updated profile
        userProfileDao.insertOrUpdate(
            current.copy(
                nickname = updatedNickname,
                interests = updatedLikes,
                affectionLevel = newAffection,
                updatedAt = System.currentTimeMillis()
            )
        )

        // Update UI state with new affection score on main thread
        withContext(mainDispatcher) {
            _uiState.update { it.copy(affectionScore = newAffection) }
        }

        // Persist key event if one was extracted
        extraction.keyEvent?.let { event ->
            keyEventDao.insert(
                KeyEventEntity(
                    characterId = currentCharacterCode,
                    type = event.category,
                    content = event.summary,
                    happenedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateAffection(score: Int) {
        _uiState.update {
            it.copy(affectionScore = score.coerceIn(0, 100))
        }
    }

    // ── P0-A5-1: Memory double insurance — release engine on ViewModel cleared ──
    // P0-A8: Release the current engine when ViewModel is cleared.
    // The engine is returned to the pool for potential reuse by other ViewModels.

    /**
     * Test-only: releases the current engine (mirrors onCleared cleanup).
     * Do not call in production code.
     */
    fun clearForTest() {
        currentEngine?.release()
        currentEngine = null
    }

    override fun onCleared() {
        super.onCleared()
        clearForTest()
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
