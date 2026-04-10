package com.aiyougame.companion.data.repository

import com.aiyougame.companion.data.api.AiyougameApi
import com.aiyougame.companion.data.model.ProfileJson
import com.aiyougame.companion.data.model.SyncProfileRequest
import com.aiyougame.companion.data.model.SyncProfileResponse
import com.aiyougame.companion.data.prefs.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for profile sync (cloud backup of user preferences and key events).
 * Requires the user to be authenticated; returns failure if not logged in.
 * All network operations are dispatched to IO to avoid blocking the UI thread.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val api: AiyougameApi,
    private val tokenManager: TokenManager
) {

    suspend fun getProfile(): Result<SyncProfileResponse> = withContext(Dispatchers.IO) {
        val userId = tokenManager.getUserId()
            ?: return@withContext Result.failure(Exception("Not logged in — cannot fetch profile"))

        try {
            val response = api.getSyncProfile(userId)
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to retrieve sync data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        nickname: String?,
        profileJson: ProfileJson?
    ): Result<SyncProfileResponse> = withContext(Dispatchers.IO) {
        val userId = tokenManager.getUserId()
            ?: return@withContext Result.failure(Exception("Not logged in — cannot update profile"))

        try {
            val response = api.updateSyncProfile(
                userId,
                SyncProfileRequest(nickname = nickname, profileJson = profileJson)
            )
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to update sync data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
