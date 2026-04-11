package com.aiyougame.companion.data.interceptor

import com.aiyougame.companion.data.prefs.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that automatically injects the Authorization: Bearer token
 * header into every outbound request when the user is authenticated.
 *
 * Unauthenticated requests (e.g. device registration) pass through unchanged.
 *
 * Privacy: this interceptor only reads a locally-stored JWT. No data leaves the device.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val token = tokenManager.getToken()
        if (token.isNullOrBlank()) {
            // Not authenticated — proceed without auth header (e.g. device register)
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
