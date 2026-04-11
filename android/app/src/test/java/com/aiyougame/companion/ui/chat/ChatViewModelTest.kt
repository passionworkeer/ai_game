package com.aiyougame.companion.ui.chat

import com.aiyougame.companion.data.repository.SyncRepository
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.di.MainDispatcher
import com.aiyougame.companion.llm.LlamaEngine
import com.aiyougame.companion.llm.LlamaEngineImpl
import com.aiyougame.companion.llm.LlamaEngineManager
import com.aiyougame.companion.llm.PromptManager
import com.aiyougame.companion.memory.MemoryManager
import com.aiyougame.companion.memory.ProfileExtractor
import com.aiyougame.companion.memory.db.ChatMessageDao
import com.aiyougame.companion.memory.db.KeyEventDao
import com.aiyougame.companion.memory.db.UserProfileDao
import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import io.mockk.coVerify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.After
import kotlin.test.*
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * Tests for ChatViewModel — verifies message state machine, multi-character support,
 * and P0-A5-1: onCleared() releases engine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var syncRepository: SyncRepository
    private lateinit var tokenManager: TokenManager
    private lateinit var chatMessageDao: ChatMessageDao
    private lateinit var userProfileDao: UserProfileDao
    private lateinit var keyEventDao: KeyEventDao
    private lateinit var profileExtractor: ProfileExtractor
    private lateinit var llamaEngineManager: LlamaEngineManager
    private lateinit var mockEngine: LlamaEngineImpl
    private lateinit var promptManager: PromptManager
    private lateinit var memoryManager: MemoryManager
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var voiceRecognitionManager: com.aiyougame.companion.speech.VoiceRecognitionManager

    private lateinit var viewModel: ChatViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        syncRepository = mockk()
        tokenManager = mockk()
        chatMessageDao = mockk()
        userProfileDao = mockk()
        keyEventDao = mockk()
        profileExtractor = ProfileExtractor()
        every { chatMessageDao.queryRecentByCharacter(any(), any()) } returns flowOf(emptyList())
        coEvery { chatMessageDao.insert(any()) } returns 1L
        coEvery { userProfileDao.getByCharacter(any()) } returns null
        coEvery { userProfileDao.insertOrUpdate(any()) } returns Unit
        coEvery { keyEventDao.insert(any()) } returns 0L

        // Mock the engine that LlamaEngineManager returns
        mockEngine = mockk(relaxed = true)
        coEvery { mockEngine.initialize() } returns Result.success(Unit)
        every { mockEngine.generateResponse(any(), any()) } answers {
            flow { emit("收到啦～谢谢你跟我说这些") }
        }
        every { mockEngine.release() } returns Unit

        // Mock LlamaEngineManager to return our mock engine
        llamaEngineManager = mockk(relaxed = true)
        coEvery { llamaEngineManager.getEngine(any()) } returns mockEngine as com.aiyougame.companion.llm.LlamaEngineImpl

        promptManager = mockk(relaxed = true)
        memoryManager = mockk(relaxed = true)
        coEvery { memoryManager.buildSnapshot(any()) } returns MemoryManager.MemorySnapshot(
            recentContext = "（暂无对话历史）",
            userProfile = "（暂无用户画像）",
            keyEvents = "（暂无关键事件）",
        )
        every { promptManager.buildSystemPrompt(any(), any(), any()) } returns "Mock System Prompt"

        savedStateHandle = mockk(relaxed = true)
        every { savedStateHandle.get<String>(any()) } returns null

        voiceRecognitionManager = mockk(relaxed = true)

        viewModel = ChatViewModel(
            syncRepository,
            tokenManager,
            chatMessageDao,
            userProfileDao,
            keyEventDao,
            profileExtractor,
            llamaEngineManager,
            promptManager,
            memoryManager,
            voiceRecognitionManager,
            testDispatcher,
            savedStateHandle,
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── P0-A5-1: onCleared releases engine ──────────────────────────────────────

    @Test
    fun `onCleared releases the current engine`() = runTest {
        // Trigger engine acquisition by sending a message
        viewModel.sendMessage("Test")
        advanceUntilIdle()

        // Clear the ViewModel (triggers onCleared)
        viewModel.clearForTest()
        advanceUntilIdle()

        // Verify release() was called on the engine
        verify { mockEngine.release() }
    }

    // ── Message validation ────────────────────────────────────────────────────────

    @Test
    fun `sendMessage should append user message to list`() = runTest {
        viewModel.sendMessage("你好，顾晨")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.messages.any { it.text == "你好，顾晨" && it.isFromUser })
    }

    @Test
    fun `sendMessage should reject empty message`() = runTest {
        val initialCount = viewModel.uiState.value.messages.size
        viewModel.sendMessage("")
        advanceUntilIdle()

        assertEquals(initialCount, viewModel.uiState.value.messages.size)
    }

    @Test
    fun `sendMessage should reject blank message`() = runTest {
        val initialCount = viewModel.uiState.value.messages.size
        viewModel.sendMessage("   ")
        advanceUntilIdle()

        assertEquals(initialCount, viewModel.uiState.value.messages.size)
    }

    @Test
    fun `sendMessage should reject message exceeding MAX_LENGTH`() = runTest {
        val longMessage = "a".repeat(ChatViewModel.MAX_MESSAGE_LENGTH + 1)
        val initialCount = viewModel.uiState.value.messages.size
        viewModel.sendMessage(longMessage)
        advanceUntilIdle()

        assertEquals(initialCount, viewModel.uiState.value.messages.size)
    }

    @Test
    fun `sendMessage should accept message at exactly MAX_LENGTH`() = runTest {
        val exactMessage = "a".repeat(ChatViewModel.MAX_MESSAGE_LENGTH)
        viewModel.sendMessage(exactMessage)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.messages.any { it.text == exactMessage && it.isFromUser })
    }

    @Test
    fun `sendMessage trims whitespace from valid message`() = runTest {
        viewModel.sendMessage("  消息  ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.messages.any { it.text == "消息" && it.isFromUser })
    }

    @Test
    fun `messages have unique IDs`() = runTest {
        viewModel.sendMessage("First")
        viewModel.sendMessage("Second")
        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages.filter { it.isFromUser }
        val ids = messages.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    // ── State machine ───────────────────────────────────────────────────────────

    @Test
    fun `sendMessage sets typingCharacter then clears it after response`() = runTest {
        viewModel.sendMessage("你好")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.typingCharacter, "typingCharacter should be null after response completes")
        assertTrue(state.messages.any { !it.isFromUser }, "Should have at least one AI response")
    }

    @Test
    fun `updateAffection should clamp score to valid range`() = runTest {
        viewModel.updateAffection(150)
        assertEquals(100, viewModel.uiState.value.affectionScore)

        viewModel.updateAffection(-10)
        assertEquals(0, viewModel.uiState.value.affectionScore)
    }

    @Test
    fun `updateAffection should accept valid score`() = runTest {
        viewModel.updateAffection(75)
        assertEquals(75, viewModel.uiState.value.affectionScore)
    }

    // ── Multi-character support ──────────────────────────────────────────────────

    @Test
    fun `default characterCode is gu_chen`() = runTest {
        assertEquals("gu_chen", viewModel.getCurrentCharacterCode())
        assertEquals("gu_chen", viewModel.uiState.value.characterCode)
    }

    @Test
    fun `switchCharacter clears messages and reloads`() = runTest {
        viewModel.sendMessage("First message")
        advanceUntilIdle()

        viewModel.switchCharacter("new_char")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("new_char", state.characterCode)
        assertEquals("new_char", viewModel.getCurrentCharacterCode())
        verify { chatMessageDao.queryRecentByCharacter("new_char", 20) }
    }

    @Test
    fun `switchCharacter to same character does nothing`() = runTest {
        val initialCharCode = viewModel.getCurrentCharacterCode()
        viewModel.switchCharacter(initialCharCode)
        advanceUntilIdle()
        // Should not reload since it's the same character
        verify(exactly = 1) { chatMessageDao.queryRecentByCharacter(initialCharCode, 20) }
    }

    @Test
    fun `switchCharacter gets new engine for different character`() = runTest {
        viewModel.switchCharacter("other_char")
        advanceUntilIdle()

        coVerify { llamaEngineManager.getEngine("other_char") }
    }
}
