package com.aiyougame.companion.ui.chat

import com.aiyougame.companion.data.repository.SyncRepository
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.di.MainDispatcher
import com.aiyougame.companion.llm.LlamaEngine
import com.aiyougame.companion.memory.ProfileExtractor
import com.aiyougame.companion.memory.db.ChatMessageDao
import com.aiyougame.companion.memory.db.KeyEventDao
import com.aiyougame.companion.memory.db.UserProfileDao
import io.mockk.*
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
 * TDD RED: ChatViewModel tests — define the contract for chat state management.
 * Following CLAUDE.md principles: immutable state updates, small focused tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var syncRepository: SyncRepository
    private lateinit var tokenManager: TokenManager
    private lateinit var chatMessageDao: ChatMessageDao
    private lateinit var userProfileDao: UserProfileDao
    private lateinit var keyEventDao: KeyEventDao
    private lateinit var profileExtractor: ProfileExtractor
    private lateinit var llamaEngine: LlamaEngine

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
        coEvery { userProfileDao.get() } returns null
        coEvery { userProfileDao.insertOrUpdate(any()) } returns Unit
        coEvery { keyEventDao.insert(any()) } returns 0L

        llamaEngine = mockk()
        coEvery { llamaEngine.initialize() } returns Result.success(Unit)
        every { llamaEngine.generateResponse(any(), any()) } answers {
            flow { emit("收到啦～谢谢你跟我说这些") }
        }

        viewModel = ChatViewModel(
            syncRepository,
            tokenManager,
            chatMessageDao,
            userProfileDao,
            keyEventDao,
            profileExtractor,
            llamaEngine,
            testDispatcher
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

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
    fun `sendMessage emits typing character then model response`() = runTest {
        viewModel.sendMessage("你好")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.typingCharacter)
        assertTrue(state.messages.any { !it.isFromUser })
    }

    @Test
    fun `sendMessage trims whitespace from valid message`() = runTest {
        viewModel.sendMessage("  消息  ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.messages.any { it.text == "消息" && it.isFromUser })
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

    @Test
    fun `messages have unique IDs`() = runTest {
        viewModel.sendMessage("First")
        viewModel.sendMessage("Second")
        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages.filter { it.isFromUser }
        val ids = messages.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
}
