package com.aiyougame.companion.data.repository

import com.aiyougame.companion.data.api.AiyougameApi
import com.aiyougame.companion.data.model.*
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.*
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * TDD RED: CharactersRepository tests — define the contract for character listing.
 */
class CharactersRepositoryTest {

    private val mockApi = mock<AiyougameApi>()
    private val repository = CharactersRepository(mockApi)

    @Test
    fun `getCharacters returns list of characters on success`() = runTest {
        val characters = listOf(
            CharacterDto(
                id = "char-001",
                code = "gaku",
                name = "Yuki Gaku",
                description = "A gentle university student.",
                price = 600,
                previewUrl = "https://cdn.example.com/gaku_preview.png",
                assetsUrl = "https://cdn.example.com/gaku_assets.zip",
                isOwned = false
            ),
            CharacterDto(
                id = "char-002",
                code = "ryu",
                name = "Ryu Hayabusa",
                description = "A mysterious swordsman.",
                price = 800,
                previewUrl = null,
                assetsUrl = "https://cdn.example.com/ryu_assets.zip",
                isOwned = true
            )
        )

        whenever(mockApi.getCharacters())
            .thenReturn(ApiResponse(
                success = true,
                data = CharacterListResponse(characters),
                error = null
            ))

        val result = repository.getCharacters()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        assertEquals("Yuki Gaku", result.getOrNull()?.get(0)?.name)
        assertEquals(true, result.getOrNull()?.get(1)?.isOwned)
    }

    @Test
    fun `getCharacters returns failure when API returns error`() = runTest {
        whenever(mockApi.getCharacters())
            .thenReturn(ApiResponse(
                success = false,
                data = null,
                error = ApiError(code = "CHAR_001", message = "Character data unavailable")
            ))

        val result = repository.getCharacters()

        assertTrue(result.isFailure)
        assertEquals("Character data unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getCharacters returns failure when network throws`() = runTest {
        whenever(mockApi.getCharacters())
            .thenThrow(RuntimeException("Connection timeout"))

        val result = repository.getCharacters()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Connection") == true)
    }

    @Test
    fun `getCharacters passes null previewUrl through correctly`() = runTest {
        val characters = listOf(
            CharacterDto(
                id = "char-003",
                code = "mystery",
                name = "???",
                description = "Locked character.",
                price = 0,
                previewUrl = null,
                assetsUrl = "https://cdn.example.com/mystery.zip",
                isOwned = false
            )
        )

        whenever(mockApi.getCharacters())
            .thenReturn(ApiResponse(
                success = true,
                data = CharacterListResponse(characters),
                error = null
            ))

        val result = repository.getCharacters()

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull()?.get(0)?.previewUrl)
    }
}
