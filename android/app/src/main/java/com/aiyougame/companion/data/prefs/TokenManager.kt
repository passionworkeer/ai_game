package com.aiyougame.companion.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the JWT token and user identity stored in SharedPreferences.
 * Thread-safe via SharedPreferences editor apply().
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String, expiresAt: Long, userId: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0)

    fun isLoggedIn(): Boolean {
        val token = getToken()
        val expiresAt = getExpiresAt()
        return token != null && System.currentTimeMillis() < expiresAt
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "auth"
        private const val KEY_TOKEN = "token"
        private const val KEY_EXPIRES_AT = "expiresAt"
        private const val KEY_USER_ID = "userId"
    }
}
