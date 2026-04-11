package com.aiyougame.companion.data.interceptor

import com.aiyougame.companion.data.prefs.TokenManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [AuthInterceptor] — verifies JWT injection into HTTP requests.
 *
 * Uses a real [MockWebServer] so the full OkHttp call chain is exercised.
 */
class AuthInterceptorTest {

    private val mockTokenManager = mock<TokenManager>()
    private val authInterceptor = AuthInterceptor(mockTokenManager)

    private val server = MockWebServer()
    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `injects Bearer token when user is authenticated`() {
        val token = "jwt-eyJhbGciOiJIUzI1NiJ9.test"
        whenever(mockTokenManager.getToken()).thenReturn(token)
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"token":"ok"}}"""))

        client.newCall(
            Request.Builder().url(server.url("/api/v1/characters")).build()
        ).execute().use { response ->
            assertEquals(200, response.code)
        }

        val recorded = server.takeRequest()
        assertEquals("Bearer $token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `skips Authorization header when not authenticated`() {
        whenever(mockTokenManager.getToken()).thenReturn(null)
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{}}"""))

        client.newCall(
            Request.Builder().url(server.url("/api/v1/auth/device")).build()
        ).execute().use { response ->
            assertEquals(200, response.code)
        }

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `skips Authorization header when token is empty`() {
        whenever(mockTokenManager.getToken()).thenReturn("")
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{}}"""))

        client.newCall(
            Request.Builder().url(server.url("/api/v1/purchases")).build()
        ).execute().use { response ->
            assertEquals(200, response.code)
        }

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }
}
