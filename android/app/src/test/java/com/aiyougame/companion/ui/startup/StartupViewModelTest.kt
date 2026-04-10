package com.aiyougame.companion.ui.startup

import com.aiyougame.companion.data.model.DeviceRegisterResponse
import com.aiyougame.companion.data.repository.AuthRepository
import com.aiyougame.companion.data.prefs.TokenManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * StartupViewModel tests — auto-registration flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var tokenManager: TokenManager
    private lateinit var viewModel: StartupViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init transitions to AlreadyLoggedIn when user is logged in`() = runTest {
        every { authRepository.isLoggedIn() } returns true
        viewModel = StartupViewModel(authRepository, tokenManager)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is StartupViewModel.StartupState.AlreadyLoggedIn)
    }

    @Test
    fun `init transitions to RegistrationSuccess when registration succeeds`() = runTest {
        every { authRepository.isLoggedIn() } returns false
        every { tokenManager.getDeviceId() } returns "device-123"
        coEvery { authRepository.registerDevice(any(), any(), any()) } returns Result.success(
            DeviceRegisterResponse(
                token = "token-123",
                expiresAt = System.currentTimeMillis() + 86400000,
                userId = "user-456"
            )
        )
        viewModel = StartupViewModel(authRepository, tokenManager)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is StartupViewModel.StartupState.RegistrationSuccess)
        assertEquals("user-456", (viewModel.uiState.value as StartupViewModel.StartupState.RegistrationSuccess).userId)
    }

    @Test
    fun `init transitions to RegistrationError when registration fails`() = runTest {
        every { authRepository.isLoggedIn() } returns false
        every { tokenManager.getDeviceId() } returns "device-123"
        coEvery { authRepository.registerDevice(any(), any(), any()) } returns Result.failure(
            Exception("Network unreachable")
        )
        viewModel = StartupViewModel(authRepository, tokenManager)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is StartupViewModel.StartupState.RegistrationError)
        assertEquals("Network unreachable", (viewModel.uiState.value as StartupViewModel.StartupState.RegistrationError).message)
    }

    @Test
    fun `retry calls checkAndRegister again`() = runTest {
        every { authRepository.isLoggedIn() } returns false
        every { tokenManager.getDeviceId() } returns "device-123"
        coEvery { authRepository.registerDevice(any(), any(), any()) } returns Result.failure(
            Exception("First attempt failed")
        )
        viewModel = StartupViewModel(authRepository, tokenManager)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is StartupViewModel.StartupState.RegistrationError)

        coEvery { authRepository.registerDevice(any(), any(), any()) } returns Result.success(
            DeviceRegisterResponse(
                token = "token-retry",
                expiresAt = System.currentTimeMillis() + 86400000,
                userId = "user-retry"
            )
        )

        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is StartupViewModel.StartupState.RegistrationSuccess)
    }
}
