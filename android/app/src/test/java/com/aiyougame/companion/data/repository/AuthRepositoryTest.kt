package com.aiyougame.companion.data.repository

import com.aiyougame.companion.data.api.AiyougameApi
import com.aiyougame.companion.data.model.*
import com.aiyougame.companion.data.prefs.TokenManager
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.*
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * TDD RED: AuthRepository tests — define the contract for device registration.
 */
class AuthRepositoryTest {

    private val mockApi = mock<AiyougameApi>()
    private val mockTokenManager = mock<TokenManager>()

    private val authRepository = AuthRepository(mockApi, mockTokenManager)

    @Test
    fun `registerDevice returns success when API responds with valid data`() = runTest {
        val deviceId = "device-uuid-123"
        val version = "1.0.0"
        val platform = "android"

        val expectedResponse = DeviceRegisterResponse(
            token = "jwt-token-abc",
            expiresAt = System.currentTimeMillis() + 86400000,
            userId = "user-xyz"
        )

        whenever(mockApi.deviceRegister(any()))
            .thenReturn(ApiResponse(success = true, data = expectedResponse, error = null))

        val result = authRepository.registerDevice(deviceId, version, platform)

        assertTrue(result.isSuccess)
        assertEquals("jwt-token-abc", result.getOrNull()?.token)
        assertEquals("user-xyz", result.getOrNull()?.userId)

        verify(mockTokenManager).saveToken(
            token = eq("jwt-token-abc"),
            expiresAt = eq(expectedResponse.expiresAt),
            userId = eq("user-xyz")
        )
    }

    @Test
    fun `registerDevice returns failure when API returns error`() = runTest {
        whenever(mockApi.deviceRegister(any()))
            .thenReturn(ApiResponse(
                success = false,
                data = null,
                error = ApiError(code = "AUTH_001", message = "Invalid device ID")
            ))

        val result = authRepository.registerDevice("bad-device", "1.0.0", "android")

        assertTrue(result.isFailure)
        // SECURITY: error message is sanitized — raw server message never surfaces to UI
        assertEquals("设备注册失败，请稍后重试", result.exceptionOrNull()?.message)
    }

    @Test
    fun `registerDevice returns failure when network throws exception`() = runTest {
        whenever(mockApi.deviceRegister(any()))
            .thenThrow(RuntimeException("Network unreachable"))

        val result = authRepository.registerDevice("device-123", "1.0.0", "android")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Network") == true)
    }

    @Test
    fun `isLoggedIn delegates to TokenManager`() {
        whenever(mockTokenManager.isLoggedIn()).thenReturn(true)
        assertTrue(authRepository.isLoggedIn())

        whenever(mockTokenManager.isLoggedIn()).thenReturn(false)
        assertFalse(authRepository.isLoggedIn())
    }

    @Test
    fun `getUserId returns userId from TokenManager`() {
        whenever(mockTokenManager.getUserId()).thenReturn("user-123")
        assertEquals("user-123", authRepository.getUserId())
    }
}
