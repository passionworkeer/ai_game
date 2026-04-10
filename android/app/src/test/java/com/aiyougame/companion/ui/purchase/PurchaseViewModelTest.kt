package com.aiyougame.companion.ui.purchase

import com.aiyougame.companion.data.model.CharacterDto
import com.aiyougame.companion.data.model.VerifyPurchaseRequest
import com.aiyougame.companion.data.model.VerifyPurchaseResponse
import com.aiyougame.companion.data.repository.CharactersRepository
import com.aiyougame.companion.data.repository.PurchaseRepository
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.ui.purchase.PurchaseViewModel.PurchaseResult
import com.aiyougame.companion.ui.purchase.PurchaseViewModel.PurchaseUiState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.After
import kotlin.test.*
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * TDD RED: PurchaseViewModel tests — define the contract for purchase screen state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseViewModelTest {

    private lateinit var charactersRepository: CharactersRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var tokenManager: TokenManager

    private lateinit var viewModel: PurchaseViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        charactersRepository = mockk()
        purchaseRepository = mockk()
        tokenManager = mockk()
        viewModel = PurchaseViewModel(charactersRepository, purchaseRepository, tokenManager)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadCharacters separates owned and unowned characters`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(listOf(
            CharacterDto("1", "gu_chen", "顾晨", "desc", 5800, null, "url", true),
            CharacterDto("2", "another", "另一个", "desc", 6800, null, "url", false),
            CharacterDto("3", "third", "第三个", "desc", 5800, null, "url", true)
        ))

        viewModel.loadCharacters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PurchaseUiState.Success)
        val s = state as PurchaseUiState.Success
        assertEquals(2, s.ownedCharacters.size)
        assertEquals(1, s.unlockedCharacters.size)
    }

    @Test
    fun `loadCharacters emits Error when repository fails`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.failure(Exception("Network error"))

        viewModel.loadCharacters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PurchaseUiState.Error)
        assertEquals("Network error", (state as PurchaseUiState.Error).message)
    }

    @Test
    fun `loadCharacters emits Loading then Success`() = runTest {
        // getCharacters() is not a suspend function — with StandardTestDispatcher,
        // the coroutine completes synchronously, so we can only verify the final state.
        coEvery { charactersRepository.getCharacters() } returns Result.success(emptyList())

        viewModel.loadCharacters()
        advanceUntilIdle()

        val successState = viewModel.uiState.value
        assertTrue(successState is PurchaseUiState.Success)
    }

    @Test
    fun `initial uiState is Idle`() = runTest {
        assertTrue(viewModel.uiState.value is PurchaseUiState.Idle)
    }

    @Test
    fun `initial purchaseResult is Idle`() = runTest {
        assertTrue(viewModel.purchaseResult.value is PurchaseResult.Idle)
    }

    @Test
    fun `verifyPurchase emits Success and reloads characters`() = runTest {
        coEvery { purchaseRepository.verifyPurchase(any()) } returns Result.success(
            VerifyPurchaseResponse("purchase-1", "active", "https://model.url")
        )
        coEvery { charactersRepository.getCharacters() } returns Result.success(listOf(
            CharacterDto("1", "gu_chen", "顾晨", "desc", 5800, null, "url", true)
        ))

        viewModel.verifyPurchase("char-1", "alipay", 5800)
        advanceUntilIdle()

        val result = viewModel.purchaseResult.value
        assertTrue(result is PurchaseResult.Success)
    }

    @Test
    fun `verifyPurchase ALREADY_PURCHASED maps to readable error message`() = runTest {
        coEvery { purchaseRepository.verifyPurchase(any()) } returns Result.failure(
            Exception("角色已购买，无需重复购买")
        )
        coEvery { charactersRepository.getCharacters() } returns Result.success(emptyList())

        viewModel.verifyPurchase("char-1", "alipay", 5800)
        advanceUntilIdle()

        val result = viewModel.purchaseResult.value
        assertTrue(result is PurchaseResult.Error)
        assertTrue((result as PurchaseResult.Error).message.contains("已购买"))
    }

    @Test
    fun `verifyPurchase LOW_AMOUNT maps to readable error message`() = runTest {
        coEvery { purchaseRepository.verifyPurchase(any()) } returns Result.failure(
            Exception("支付金额不足，最低需要5800积分")
        )
        coEvery { charactersRepository.getCharacters() } returns Result.success(emptyList())

        viewModel.verifyPurchase("char-1", "alipay", 100)
        advanceUntilIdle()

        val result = viewModel.purchaseResult.value
        assertTrue(result is PurchaseResult.Error)
        assertTrue((result as PurchaseResult.Error).message.contains("金额不足"))
    }

    @Test
    fun `verifyPurchase unknown error passes through original message`() = runTest {
        coEvery { purchaseRepository.verifyPurchase(any()) } returns Result.failure(
            Exception("Server internal error")
        )

        viewModel.verifyPurchase("char-1", "alipay", 5800)
        advanceUntilIdle()

        val result = viewModel.purchaseResult.value
        assertTrue(result is PurchaseResult.Error)
        assertEquals("Server internal error", (result as PurchaseResult.Error).message)
    }

    @Test
    fun `verifyPurchase emits Loading then result`() = runTest {
        coEvery { purchaseRepository.verifyPurchase(any()) } returns Result.success(
            VerifyPurchaseResponse("p1", "active", "url")
        )
        coEvery { charactersRepository.getCharacters() } returns Result.success(emptyList())

        viewModel.verifyPurchase("char-1", "alipay", 5800)
        advanceUntilIdle()

        val result = viewModel.purchaseResult.value
        assertTrue(result is PurchaseResult.Success)
    }

    @Test
    fun `resetPurchaseResult sets purchaseResult back to Idle`() = runTest {
        coEvery { purchaseRepository.verifyPurchase(any()) } returns Result.failure(
            Exception("Already purchased")
        )

        viewModel.verifyPurchase("char-1", "alipay", 5800)
        advanceUntilIdle()
        assertTrue(viewModel.purchaseResult.value is PurchaseResult.Error)

        viewModel.resetPurchaseResult()
        assertTrue(viewModel.purchaseResult.value is PurchaseResult.Idle)
    }

    @Test
    fun `loadCharacters with empty list emits Success with empty lists`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(emptyList())

        viewModel.loadCharacters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PurchaseUiState.Success)
        val s = state as PurchaseUiState.Success
        assertTrue(s.ownedCharacters.isEmpty())
        assertTrue(s.unlockedCharacters.isEmpty())
    }
}
