package com.aiyougame.companion.data.api

import com.aiyougame.companion.data.model.*
import com.google.gson.annotations.SerializedName
import org.junit.Test
import org.mockito.kotlin.*
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * TDD RED: ApiService tests — these define the expected contract for AiyougameApi.
 * All tests in this file should FAIL until AiyougameApi and ApiModels are implemented.
 */
class ApiServiceTest {

    @Test
    fun `ApiResponse wraps success payload correctly`() {
        val response = ApiResponse(
            success = true,
            data = DeviceRegisterResponse(
                token = "jwt-token-123",
                expiresAt = System.currentTimeMillis() + 86400000,
                userId = "user-001"
            ),
            error = null
        )

        assertTrue(response.success)
        assertEquals("jwt-token-123", response.data?.token)
        assertEquals("user-001", response.data?.userId)
        assertEquals(null, response.error)
    }

    @Test
    fun `ApiResponse wraps error correctly`() {
        val response = ApiResponse<DeviceRegisterResponse>(
            success = false,
            data = null,
            error = ApiError(code = "AUTH_001", message = "Invalid device ID")
        )

        assertFalse(response.success)
        assertEquals(null, response.data)
        assertEquals("AUTH_001", response.error?.code)
        assertEquals("Invalid device ID", response.error?.message)
    }

    @Test
    fun `CharacterDto serializes all fields correctly`() {
        val dto = CharacterDto(
            id = "char-001",
            code = "gaku",
            name = "Yuki Gaku",
            description = "A gentle university student with a secret past.",
            price = 600,
            previewUrl = "https://cdn.example.com/gaku_preview.png",
            assetsUrl = "https://cdn.example.com/gaku_assets.zip",
            isOwned = false
        )

        assertEquals("char-001", dto.id)
        assertEquals("gaku", dto.code)
        assertEquals("Yuki Gaku", dto.name)
        assertEquals(600, dto.price)
        assertEquals(false, dto.isOwned)
    }

    @Test
    fun `VerifyPurchaseRequest contains all required fields`() {
        val request = VerifyPurchaseRequest(
            characterId = "char-001",
            channel = "google",
            channelOrderId = "order-abc123",
            paidAmount = 600,
            paidAt = System.currentTimeMillis(),
            signature = "sig-xyz"
        )

        assertEquals("char-001", request.characterId)
        assertEquals("google", request.channel)
        assertEquals("order-abc123", request.channelOrderId)
        assertEquals(600, request.paidAmount)
    }

    @Test
    fun `ProfileJson serializes nullable fields correctly`() {
        val profile = ProfileJson(
            likes = listOf("music", "reading"),
            dislikes = null,
            currentMood = "happy",
            importantDates = mapOf("birthday" to "1995-03-14")
        )

        assertEquals(2, profile.likes?.size)
        assertEquals(null, profile.dislikes)
        assertEquals("happy", profile.currentMood)
        assertEquals("1995-03-14", profile.importantDates?.get("birthday"))
    }

    @Test
    fun `SyncProfileResponse contains keyEvents list`() {
        val response = SyncProfileResponse(
            nickname = "Alice",
            profileJson = ProfileJson(
                likes = listOf("gaming"),
                dislikes = null,
                currentMood = null,
                importantDates = null
            ),
            keyEvents = listOf(
                KeyEventDto(
                    summary = "First meeting",
                    timestamp = 1710000000000L,
                    category = "story"
                )
            ),
            updatedAt = 1710000000000L
        )

        assertEquals("Alice", response.nickname)
        assertEquals(1, response.keyEvents.size)
        assertEquals("First meeting", response.keyEvents[0].summary)
    }
}
