package com.aiyougame.companion.ui.chat

import com.aiyougame.companion.data.repository.SyncRepository
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.di.MainDispatcher
import io.mockk.*
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * TDD RED: ChatViewModel tests — define the contract for chat state management.
 * Following CLAUDE.md principles: immutable state updates, small focused tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class ChatViewModelTest {

    @MockK private lateinit var syncRepository: SyncRepository
    @MockK private lateinit var tokenManager: TokenManager

    private lateinit var viewModel: ChatViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this)
        // ViewModel uses @MainDispatcher which is resolved from Dispatchers.Main (our testDispatcher)
        viewModel = ChatViewModel(syncRepository, tokenManager, testDispatcher)
    }

    @AfterEach
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
        // After simulateModelResponse completes, typingCharacter should be null
        assertNull(state.typingCharacter)
        // Model should have appended a response
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
        assertEquals(ids.size, ids.distinct().size, "All message IDs should be unique")
    }
}
