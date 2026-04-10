package com.aiyougame.companion.data.repository

import com.aiyougame.companion.data.api.AiyougameApi
import com.aiyougame.companion.data.model.DeviceRegisterRequest
import com.aiyougame.companion.data.model.DeviceRegisterResponse
import com.aiyougame.companion.data.prefs.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for device authentication.
 * All operations are offloaded to IO dispatcher to avoid blocking the UI thread.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: AiyougameApi,
    private val tokenManager: TokenManager
) {

    suspend fun registerDevice(
        deviceId: String,
        version: String,
        platform: String
    ): Result<DeviceRegisterResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.deviceRegister(
                DeviceRegisterRequest(
                    deviceId = deviceId,
                    clientVersion = version,
                    platform = platform
                )
            )
            if (response.success && response.data != null) {
                // Persist token so subsequent requests can use it
                tokenManager.saveToken(
                    token = response.data.token,
                    expiresAt = response.data.expiresAt,
                    userId = response.data.userId
                )
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error?.message ?: "Device registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    fun getUserId(): String? = tokenManager.getUserId()
}
