package com.aiyougame.companion.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for TokenManager.
 * Tests JWT token management, login state, and device ID persistence.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class TokenManagerTest {

    private lateinit var tokenManager: TokenManager
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tokenManager = TokenManager(context)
    }

    @After
    fun teardown() {
        // Clean up after each test
        tokenManager.clear()
    }

    @Test
    fun saveToken_and_getToken_roundTrip() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
        val expiresAt = System.currentTimeMillis() + 3600000 // 1 hour
        val userId = "user-123"

        tokenManager.saveToken(token, expiresAt, userId)

        assertEquals(token, tokenManager.getToken())
    }

    @Test
    fun getToken_returnsNull_whenNotSet() {
        assertNull(tokenManager.getToken())
    }

    @Test
    fun saveToken_savesUserId() {
        val token = "test-token"
        val expiresAt = System.currentTimeMillis() + 3600000
        val userId = "user-456"

        tokenManager.saveToken(token, expiresAt, userId)

        assertEquals(userId, tokenManager.getUserId())
    }

    @Test
    fun getUserId_returnsNull_whenNotSet() {
        assertNull(tokenManager.getUserId())
    }

    @Test
    fun saveToken_savesExpiresAt() {
        val token = "test-token"
        val expiresAt = 1715500000000L

        tokenManager.saveToken(token, expiresAt, "user-789")

        assertEquals(expiresAt, tokenManager.getExpiresAt())
    }

    @Test
    fun getExpiresAt_returnsZero_whenNotSet() {
        assertEquals(0L, tokenManager.getExpiresAt())
    }

    @Test
    fun isLoggedIn_returnsTrue_whenTokenExistsAndNotExpired() {
        val token = "valid-token"
        val expiresAt = System.currentTimeMillis() + 3600000 // Future

        tokenManager.saveToken(token, expiresAt, "user-123")

        assertTrue(tokenManager.isLoggedIn())
    }

    @Test
    fun isLoggedIn_returnsFalse_whenTokenExpired() {
        val token = "expired-token"
        val expiresAt = System.currentTimeMillis() - 1000 // Past

        tokenManager.saveToken(token, expiresAt, "user-123")

        assertFalse(tokenManager.isLoggedIn())
    }

    @Test
    fun isLoggedIn_returnsFalse_whenTokenIsNull() {
        // No token saved
        assertFalse(tokenManager.isLoggedIn())
    }

    @Test
    fun saveDeviceId_and_getDeviceId_roundTrip() {
        val deviceId = "550e8400-e29b-41d4-a716-446655440000"

        tokenManager.saveDeviceId(deviceId)

        assertEquals(deviceId, tokenManager.getDeviceId())
    }

    @Test
    fun getDeviceId_returnsNull_whenNotSet() {
        assertNull(tokenManager.getDeviceId())
    }

    @Test
    fun saveSelectedCharacter_and_getSelectedCharacter_roundTrip() {
        val characterCode = "gu_chen"

        tokenManager.saveSelectedCharacter(characterCode)

        assertEquals(characterCode, tokenManager.getSelectedCharacter())
    }

    @Test
    fun getSelectedCharacter_returnsNull_whenNotSet() {
        assertNull(tokenManager.getSelectedCharacter())
    }

    @Test
    fun clear_removesAllData() {
        tokenManager.saveToken("token", System.currentTimeMillis() + 3600000, "user-123")
        tokenManager.saveDeviceId("device-456")
        tokenManager.saveSelectedCharacter("char-789")

        tokenManager.clear()

        assertNull(tokenManager.getToken())
        assertNull(tokenManager.getUserId())
        assertEquals(0L, tokenManager.getExpiresAt())
        assertNull(tokenManager.getDeviceId())
        assertNull(tokenManager.getSelectedCharacter())
        assertFalse(tokenManager.isLoggedIn())
    }

    @Test
    fun multipleSave_callsLastOneWins() {
        tokenManager.saveToken("first-token", 1000L, "first-user")
        tokenManager.saveToken("second-token", 2000L, "second-user")

        assertEquals("second-token", tokenManager.getToken())
        assertEquals("second-user", tokenManager.getUserId())
        assertEquals(2000L, tokenManager.getExpiresAt())
    }
}
