package com.aiyougame.companion.drm

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256 DRM Manager for Android.
 *
 * Security design:
 * 1. Each character has a unique RSA-2048 key pair in Android Keystore
 * 2. On purchase, backend generates AES-256 key and encrypts it with player's RSA public key
 * 3. Android decrypts AES key using RSA-OAEP in Keystore (private key never leaves Keystore)
 * 4. Decrypted AES key stored encrypted in EncryptedSharedPreferences (backed by Android Keystore)
 * 5. Key retrieval never exposes plaintext to app memory beyond the immediate operation
 *
 * Key hierarchy:
 *   Backend RSA Public Key (known to backend) → encrypts AES key
 *   Android RSA Private Key (in Keystore, hardware-backed) → decrypts AES key
 *   AES key (in EncryptedSharedPreferences) → encrypts game content
 */
@Singleton
class AesDrmManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val drmApi: DrmApi
) {

    companion object {
        private const val PREFS_FILE = "drm_keys.preferences"
        private const val KEY_PREFIX_AES = "aes_key_"
        private const val AES_KEY_SIZE = 32 // 256 bits
    }

    // EncryptedSharedPreferences for AES key storage
    // Initialized lazily to avoid accessing Context before it's ready
    private val encryptedPrefs: SharedPreferences by lazy {
        createEncryptedPrefs(context)
    }

    private fun createEncryptedPrefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            ctx,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Get RSA key alias for a character.
     */
    private fun getRsaAlias(characterCode: String) = "drm-$characterCode"

    /**
     * Get AES key preference key for a character.
     */
    private fun getAesPrefKey(characterCode: String) = KEY_PREFIX_AES + characterCode

    /**
     * Ensure RSA key pair exists for a character.
     * Called before purchase registration.
     */
    fun ensureRsaKeyPair(characterCode: String) {
        val alias = getRsaAlias(characterCode)
        if (!keystoreManager.hasKey(alias)) {
            keystoreManager.generateAndStoreRsaKeyPair(alias)
        }
    }

    /**
     * Get RSA public key PEM for a character.
     * Used for backend registration before purchase.
     */
    fun getPublicKeyPem(characterCode: String): String {
        ensureRsaKeyPair(characterCode)
        return keystoreManager.getPublicKeyPem(getRsaAlias(characterCode))
    }

    /**
     * Register public key with backend for a character.
     */
    suspend fun registerPublicKey(characterCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pem = getPublicKeyPem(characterCode)
            val response = drmApi.registerPublicKey(
                RegisterKeyRequest(characterCode, pem)
            )
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error?.message ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * RSA-OAEP decrypt AES key from base64-encoded ciphertext using a specific RSA key alias.
     *
     * @param alias KeyStore alias for the RSA key pair
     * @param encryptedKeyBase64 Base64 RSA-OAEP ciphertext from backend
     * @return Decrypted 32-byte AES key, or null if decryption fails
     */
    fun decryptAesKeyWithAlias(alias: String, encryptedKeyBase64: String): ByteArray? {
        val decrypted = keystoreManager.decryptAesKey(alias, encryptedKeyBase64)
        if (decrypted != null && decrypted.size != AES_KEY_SIZE) {
            return null // Wrong key size — not a valid AES-256 key
        }
        return decrypted
    }

    /**
     * Get AES key for a character.
     * Returns null if not purchased yet (key not stored).
     *
     * @param characterCode The character to get the key for
     * @return AES-256 key (32 bytes) or null if not purchased
     */
    fun getAesKey(characterCode: String): ByteArray? {
        val prefKey = getAesPrefKey(characterCode)
        val encoded = encryptedPrefs.getString(prefKey, null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    /**
     * Store AES key for a character.
     * The AES key bytes are stored encrypted in EncryptedSharedPreferences.
     *
     * @param characterCode The character to store the key for
     * @param aesKey 32-byte AES-256 key
     */
    fun storeAesKey(characterCode: String, aesKey: ByteArray) {
        require(aesKey.size == AES_KEY_SIZE) { "AES key must be exactly $AES_KEY_SIZE bytes" }
        val prefKey = getAesPrefKey(characterCode)
        val encoded = Base64.encodeToString(aesKey, Base64.NO_WRAP)
        encryptedPrefs.edit().putString(prefKey, encoded).apply()
    }

    /**
     * Handle successful purchase: fetch encrypted AES key from backend and store.
     *
     * @param characterCode The character that was purchased
     * @return Result containing the AES key bytes, or failure
     */
    suspend fun handlePurchaseSuccess(characterCode: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            // 1. Ensure RSA key pair exists
            ensureRsaKeyPair(characterCode)

            // 2. Fetch encrypted AES key from backend
            val response = drmApi.getEncryptedKey(characterCode)
            if (!response.success || response.data == null) {
                return@withContext Result.failure(
                    Exception(response.error?.message ?: "Failed to get encrypted key")
                )
            }

            // 3. Decrypt AES key with RSA-OAEP
            val alias = getRsaAlias(characterCode)
            val aesKey = decryptAesKeyWithAlias(alias, response.data.encryptedKey)
                ?: return@withContext Result.failure(Exception("Failed to decrypt AES key"))

            // 4. Store AES key in EncryptedSharedPreferences
            storeAesKey(characterCode, aesKey)

            Result.success(aesKey)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear all DRM keys for a character (on account logout or reset).
     */
    fun clearKeys(characterCode: String) {
        // Remove AES key from EncryptedSharedPreferences
        val prefKey = getAesPrefKey(characterCode)
        encryptedPrefs.edit().remove(prefKey).apply()

        // Remove RSA key pair from Keystore
        keystoreManager.deleteKey(getRsaAlias(characterCode))
    }
}
