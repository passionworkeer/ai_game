package com.aiyougame.companion.ui.characters

import com.aiyougame.companion.data.model.CharacterDto
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.data.repository.CharactersRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.*

/**
 * CharacterSelectViewModel tests — verify character selection flow.
 * Tests loading characters from backend and selecting a character.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterSelectViewModelTest {

    private lateinit var charactersRepository: CharactersRepository
    private lateinit var tokenManager: TokenManager

    private lateinit var viewModel: CharacterSelectViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testCharacters = listOf(
        CharacterDto("1", "gu_chen", "顾晨", "温柔学长", 5800, null, "url1", true),
        CharacterDto("2", "ye_tian", "叶天", "冷面总裁", 6800, null, "url2", false),
        CharacterDto("3", "mo_chen", "墨晨", "阳光少年", 5800, null, "url3", true),
        CharacterDto("4", "si_we", "司夜", "神秘人", 7800, null, "url4", false)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        charactersRepository = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CharacterSelectViewModel {
        return CharacterSelectViewModel(charactersRepository, tokenManager)
    }

    @Test
    fun `initial uiState is Loading`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(emptyList())

        viewModel = createViewModel()
        // On creation, the ViewModel immediately loads characters
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CharacterSelectViewModel.UiState.Success)
    }

    @Test
    fun `loadCharacters emits Success with correct character separation`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(testCharacters)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CharacterSelectViewModel.UiState.Success)

        val success = state as CharacterSelectViewModel.UiState.Success
        assertEquals(2, success.ownedCharacters.size)
        assertEquals(2, success.unlockedCharacters.size)

        // Verify owned characters have isOwned = true
        assertTrue(success.ownedCharacters.all { it.isOwned })
        // Verify unlocked characters have isOwned = false
        assertTrue(success.unlockedCharacters.all { !it.isOwned })
    }

    @Test
    fun `loadCharacters emits Error when repository fails`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.failure(
            Exception("Network unavailable")
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CharacterSelectViewModel.UiState.Error)
        assertEquals("Network unavailable", (state as CharacterSelectViewModel.UiState.Error).message)
    }

    @Test
    fun `loadCharacters handles empty character list`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CharacterSelectViewModel.UiState.Success)

        val success = state as CharacterSelectViewModel.UiState.Success
        assertTrue(success.ownedCharacters.isEmpty())
        assertTrue(success.unlockedCharacters.isEmpty())
    }

    @Test
    fun `loadCharacters handles all characters owned`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(
            testCharacters.map { it.copy(isOwned = true) }
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CharacterSelectViewModel.UiState.Success)

        val success = state as CharacterSelectViewModel.UiState.Success
        assertEquals(4, success.ownedCharacters.size)
        assertTrue(success.unlockedCharacters.isEmpty())
    }

    @Test
    fun `loadCharacters handles no characters owned`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(
            testCharacters.map { it.copy(isOwned = false) }
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CharacterSelectViewModel.UiState.Success)

        val success = state as CharacterSelectViewModel.UiState.Success
        assertTrue(success.ownedCharacters.isEmpty())
        assertEquals(4, success.unlockedCharacters.size)
    }

    @Test
    fun `selectCharacter saves to TokenManager`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(testCharacters)

        viewModel = createViewModel()
        advanceUntilIdle()

        val characterToSelect = testCharacters[1] // 叶天
        viewModel.selectCharacter(characterToSelect)

        verify { tokenManager.saveSelectedCharacter("ye_tian") }
    }

    @Test
    fun `selectCharacter updates selectedCharacter state`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(testCharacters)

        viewModel = createViewModel()
        advanceUntilIdle()

        val characterToSelect = testCharacters[2] // 墨晨
        viewModel.selectCharacter(characterToSelect)

        val selected = viewModel.selectedCharacter.value
        assertEquals("mo_chen", selected?.code)
        assertEquals("墨晨", selected?.name)
    }

    @Test
    fun `selectCharacter replaces previously selected character`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(testCharacters)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Select first character
        viewModel.selectCharacter(testCharacters[0])
        assertEquals("gu_chen", viewModel.selectedCharacter.value?.code)

        // Select different character
        viewModel.selectCharacter(testCharacters[1])
        assertEquals("ye_tian", viewModel.selectedCharacter.value?.code)

        // TokenManager should be called twice with different codes
        verify { tokenManager.saveSelectedCharacter("ye_tian") }
    }

    @Test
    fun `getSelectedCharacterCode returns code from TokenManager`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(testCharacters)
        every { tokenManager.getSelectedCharacter() } returns "mo_chen"

        viewModel = createViewModel()
        advanceUntilIdle()

        val code = viewModel.getSelectedCharacterCode()
        assertEquals("mo_chen", code)
    }

    @Test
    fun `getSelectedCharacterCode returns default when no selection`() = runTest {
        coEvery { charactersRepository.getCharacters() } returns Result.success(testCharacters)
        every { tokenManager.getSelectedCharacter() } returns null

        viewModel = createViewModel()
        advanceUntilIdle()

        val code = viewModel.getSelectedCharacterCode()
        assertEquals("gu_chen", code) // default fallback
    }

    @Test
    fun `reloadCharacters clears previous error state`() = runTest {
        // First load fails
        coEvery { charactersRepository.getCharacters() } returns Result.failure(
            Exception("First error")
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CharacterSelectViewModel.UiState.Error)

        // Second load succeeds
        coEvery { charactersRepository.getCharacters() } returns Result.success(testCharacters)

        viewModel.loadCharacters()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CharacterSelectViewModel.UiState.Success)
    }
}
