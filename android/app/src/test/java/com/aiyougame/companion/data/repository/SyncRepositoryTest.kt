package com.aiyougame.companion.data.repository

import com.aiyougame.companion.data.api.AiyougameApi
import com.aiyougame.companion.data.model.*
import com.aiyougame.companion.data.prefs.TokenManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED: SyncRepository tests — define the contract for profile sync.
 */
class SyncRepositoryTest {

    private lateinit var mockApi: AiyougameApi
    private lateinit var mockTokenManager: TokenManager

    private lateinit var repository: SyncRepository

    @Before
    fun setup() {
        mockApi = mockk()
        mockTokenManager = mockk()
        repository = SyncRepository(mockApi, mockTokenManager)
    }

    @Test
    fun `getProfile returns success when logged in`() = runTest {
        val userId = "user-123"
        val expectedResponse = SyncProfileResponse(
            nickname = "Alice",
            profileJson = ProfileJson(
                likes = listOf("music", "gaming"),
                dislikes = listOf("spicy food"),
                currentMood = "happy",
                importantDates = mapOf("anniversary" to "2026-02-14")
            ),
            keyEvents = listOf(
                KeyEventDto("First date", 1710000000000L, "story"),
                KeyEventDto("Confession", 1710100000000L, "story")
            ),
            updatedAt = 1710100000000L
        )

        every { mockTokenManager.getUserId() } returns userId
        coEvery { mockApi.getSyncProfile(userId) } returns ApiResponse(
            success = true, data = expectedResponse, error = null
        )

        val result = repository.getProfile()

        assertTrue(result.isSuccess)
        assertEquals("Alice", result.getOrNull()?.nickname)
        assertEquals(2, result.getOrNull()?.keyEvents?.size)
        assertEquals("First date", result.getOrNull()?.keyEvents?.get(0)?.summary)
    }

    @Test
    fun `getProfile returns failure when not logged in`() = runTest {
        every { mockTokenManager.getUserId() } returns null

        val result = repository.getProfile()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Not logged in") == true)
    }

    @Test
    fun `getProfile returns failure when API returns error`() = runTest {
        every { mockTokenManager.getUserId() } returns "user-123"
        coEvery { mockApi.getSyncProfile("user-123") } returns ApiResponse(
            success = false,
            data = null,
            error = ApiError(code = "SYNC_001", message = "Profile not found")
        )

        val result = repository.getProfile()

        assertTrue(result.isFailure)
        assertEquals("Profile not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `updateProfile returns success with updated data`() = runTest {
        val userId = "user-123"
        every { mockTokenManager.getUserId() } returns userId
        coEvery { mockApi.updateSyncProfile(eq(userId), any()) } returns ApiResponse(
            success = true,
            data = SyncProfileResponse(
                nickname = "NewNick",
                profileJson = ProfileJson(
                    likes = listOf("coding"),
                    dislikes = null,
                    currentMood = "excited",
                    importantDates = null
                ),
                keyEvents = emptyList(),
                updatedAt = System.currentTimeMillis()
            ),
            error = null
        )

        val result = repository.updateProfile("NewNick", ProfileJson(
            likes = listOf("coding"),
            dislikes = null,
            currentMood = "excited",
            importantDates = null
        ))

        assertTrue(result.isSuccess)
        assertEquals("NewNick", result.getOrNull()?.nickname)
        assertEquals("excited", result.getOrNull()?.profileJson?.currentMood)
    }

    @Test
    fun `updateProfile returns failure when not logged in`() = runTest {
        every { mockTokenManager.getUserId() } returns null

        val result = repository.updateProfile("NewNick", null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Not logged in") == true)
    }

    @Test
    fun `updateProfile returns failure when API throws`() = runTest {
        every { mockTokenManager.getUserId() } returns "user-123"
        coEvery { mockApi.updateSyncProfile(eq("user-123"), any()) } throws RuntimeException("Server error 500")

        val result = repository.updateProfile("NewNick", null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Server error") == true)
    }

    @Test
    fun `updateProfile passes null nickname and profileJson gracefully`() = runTest {
        val userId = "user-123"
        every { mockTokenManager.getUserId() } returns userId
        coEvery { mockApi.updateSyncProfile(eq(userId), any()) } returns ApiResponse(
            success = true,
            data = SyncProfileResponse(
                nickname = "ExistingNick",
                profileJson = ProfileJson(null, null, null, null),
                keyEvents = emptyList(),
                updatedAt = System.currentTimeMillis()
            ),
            error = null
        )

        val result = repository.updateProfile(null, null)

        assertTrue(result.isSuccess)
    }
}
