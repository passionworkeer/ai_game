package com.aiyougame.companion.data.repository

import com.aiyougame.companion.data.api.AiyougameApi
import com.aiyougame.companion.data.model.*
import com.aiyougame.companion.data.prefs.TokenManager
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.*
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * TDD RED: SyncRepository tests — define the contract for profile sync.
 */
class SyncRepositoryTest {

    private val mockApi = mock<AiyougameApi>()
    private val mockTokenManager = mock<TokenManager>()

    private val repository = SyncRepository(mockApi, mockTokenManager)

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

        whenever(mockTokenManager.getUserId()).thenReturn(userId)
        whenever(mockApi.getSyncProfile(userId))
            .thenReturn(ApiResponse(success = true, data = expectedResponse, error = null))

        val result = repository.getProfile()

        assertTrue(result.isSuccess)
        assertEquals("Alice", result.getOrNull()?.nickname)
        assertEquals(2, result.getOrNull()?.keyEvents?.size)
        assertEquals("First date", result.getOrNull()?.keyEvents?.get(0)?.summary)
    }

    @Test
    fun `getProfile returns failure when not logged in`() = runTest {
        whenever(mockTokenManager.getUserId()).thenReturn(null)

        val result = repository.getProfile()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("未登录") == true)
    }

    @Test
    fun `getProfile returns failure when API returns error`() = runTest {
        whenever(mockTokenManager.getUserId()).thenReturn("user-123")
        whenever(mockApi.getSyncProfile("user-123"))
            .thenReturn(ApiResponse(
                success = false,
                data = null,
                error = ApiError(code = "SYNC_001", message = "Profile not found")
            ))

        val result = repository.getProfile()

        assertTrue(result.isFailure)
        assertEquals("Profile not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `updateProfile returns success with updated data`() = runTest {
        val userId = "user-123"
        val request = SyncProfileRequest(
            nickname = "NewNick",
            profileJson = ProfileJson(
                likes = listOf("coding"),
                dislikes = null,
                currentMood = "excited",
                importantDates = null
            )
        )

        val expectedResponse = SyncProfileResponse(
            nickname = "NewNick",
            profileJson = request.profileJson!!,
            keyEvents = emptyList(),
            updatedAt = System.currentTimeMillis()
        )

        whenever(mockTokenManager.getUserId()).thenReturn(userId)
        whenever(mockApi.updateSyncProfile(eq(userId), any()))
            .thenReturn(ApiResponse(success = true, data = expectedResponse, error = null))

        val result = repository.updateProfile("NewNick", request.profileJson)

        assertTrue(result.isSuccess)
        assertEquals("NewNick", result.getOrNull()?.nickname)
        assertEquals("excited", result.getOrNull()?.profileJson?.currentMood)
    }

    @Test
    fun `updateProfile returns failure when not logged in`() = runTest {
        whenever(mockTokenManager.getUserId()).thenReturn(null)

        val result = repository.updateProfile("NewNick", null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("未登录") == true)
    }

    @Test
    fun `updateProfile returns failure when API throws`() = runTest {
        whenever(mockTokenManager.getUserId()).thenReturn("user-123")
        whenever(mockApi.updateSyncProfile(eq("user-123"), any()))
            .thenThrow(RuntimeException("Server error 500"))

        val result = repository.updateProfile("NewNick", null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Server error") == true)
    }

    @Test
    fun `updateProfile passes null nickname and profileJson gracefully`() = runTest {
        val userId = "user-123"
        val request = SyncProfileRequest(nickname = null, profileJson = null)

        val expectedResponse = SyncProfileResponse(
            nickname = "ExistingNick",
            profileJson = ProfileJson(null, null, null, null),
            keyEvents = emptyList(),
            updatedAt = System.currentTimeMillis()
        )

        whenever(mockTokenManager.getUserId()).thenReturn(userId)
        whenever(mockApi.updateSyncProfile(eq(userId), any()))
            .thenReturn(ApiResponse(success = true, data = expectedResponse, error = null))

        val result = repository.updateProfile(null, null)

        assertTrue(result.isSuccess)
    }
}
