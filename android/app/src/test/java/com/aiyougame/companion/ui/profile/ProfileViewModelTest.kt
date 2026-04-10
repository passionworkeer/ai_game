package com.aiyougame.companion.ui.profile

import com.aiyougame.companion.data.model.KeyEventDto
import com.aiyougame.companion.data.model.ProfileJson
import com.aiyougame.companion.data.model.SyncProfileResponse
import com.aiyougame.companion.data.repository.SyncRepository
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.ui.profile.ProfileViewModel.ProfileUiState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.After
import org.junit.Ignore
import kotlin.test.*
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * TDD RED: ProfileViewModel tests — define the contract for profile screen state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private lateinit var syncRepository: SyncRepository
    private lateinit var tokenManager: TokenManager

    private lateinit var viewModel: ProfileViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        syncRepository = mockk()
        tokenManager = mockk()
        viewModel = ProfileViewModel(syncRepository, tokenManager)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadProfile emits Loading then Success when logged in`() = runTest {
        coEvery { tokenManager.getUserId() } returns "user-123"
        coEvery { syncRepository.getProfile() } returns Result.success(
            SyncProfileResponse(
                nickname = "小鱼",
                profileJson = ProfileJson(
                    likes = listOf("奶茶"),
                    dislikes = emptyList(),
                    currentMood = "happy",
                    importantDates = emptyMap()
                ),
                keyEvents = listOf(
                    KeyEventDto("第一次约会", System.currentTimeMillis(), "story")
                ),
                updatedAt = System.currentTimeMillis()
            )
        )

        viewModel.loadProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Success)
        val success = state as ProfileUiState.Success
        assertEquals("小鱼", success.nickname)
        assertEquals(listOf("奶茶"), success.profileJson.likes)
        assertEquals(1, success.keyEvents.size)
    }

    @Test
    fun `loadProfile emits Error when not logged in`() = runTest {
        coEvery { tokenManager.getUserId() } returns null

        viewModel.loadProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Error)
        assertTrue((state as ProfileUiState.Error).message.contains("注册"))
    }

    @Test
    fun `loadProfile emits Error when repository fails`() = runTest {
        coEvery { tokenManager.getUserId() } returns "user-123"
        coEvery { syncRepository.getProfile() } returns Result.failure(Exception("Network error"))

        viewModel.loadProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Error)
        assertEquals("Network error", (state as ProfileUiState.Error).message)
    }

    @Test
    fun `initial state is Idle`() = runTest {
        assertTrue(viewModel.uiState.value is ProfileUiState.Idle)
    }

    @Test
    fun `updateNickname calls repository and reloads on success`() = runTest {
        coEvery { tokenManager.getUserId() } returns "user-123"
        coEvery { syncRepository.updateProfile(nickname = eq("新昵称"), profileJson = null) } returns Result.success(
            SyncProfileResponse(
                nickname = "新昵称",
                profileJson = ProfileJson(null, null, null, null),
                keyEvents = emptyList(),
                updatedAt = System.currentTimeMillis()
            )
        )
        coEvery { syncRepository.getProfile() } returns Result.success(
            SyncProfileResponse(
                nickname = "新昵称",
                profileJson = ProfileJson(null, null, null, null),
                keyEvents = emptyList(),
                updatedAt = System.currentTimeMillis()
            )
        )

        viewModel.updateNickname("新昵称")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Success)
        assertEquals("新昵称", (state as ProfileUiState.Success).nickname)
    }

    @Test
    fun `updateNickname silences failure without crashing`() = runTest {
        coEvery { tokenManager.getUserId() } returns "user-123"
        coEvery { syncRepository.updateProfile(nickname = any(), profileJson = null) } returns Result.failure(
            Exception("Update failed")
        )

        // Should not throw
        viewModel.updateNickname("BadNick")
        advanceUntilIdle()
    }

    @Ignore("TokenManager is @Singleton — mocking companion method requires mockkStatic; deferred")
    @Test
    fun `loadProfile emits Error when getUserId returns null`() = runTest {
        val nullTokenManager = mockk<TokenManager>()
        every { nullTokenManager.getUserId() } returns null
        val vm = ProfileViewModel(syncRepository, nullTokenManager)

        vm.loadProfile()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is ProfileUiState.Error)
        assertTrue((state as ProfileUiState.Error).message.contains("注册"))
    }
}
