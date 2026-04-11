package com.aiyougame.companion.drm

import com.aiyougame.companion.data.model.ApiResponse
import retrofit2.http.*

/**
 * DRM API interface for registering public keys and fetching encrypted AES keys.
 * Endpoints:
 * - POST /drm/register: Register player's RSA public key for a character
 * - GET /drm/key/{characterCode}: Get encrypted AES key after purchase
 */
interface DrmApi {

    /**
     * Register player's RSA public key for a character.
     * Called before purchase to associate the player's key with the character.
     *
     * @param request Contains characterCode and base64-encoded public key in PKCS8 format
     */
    @POST("drm/register")
    suspend fun registerPublicKey(@Body request: RegisterKeyRequest): ApiResponse<Unit>

    /**
     * Get RSA-OAEP encrypted AES-256 key for a character.
     * Called after successful purchase to retrieve the DRM key.
     *
     * @param characterCode The character to get the key for
     * @return encryptedKey: Base64-encoded RSA-OAEP ciphertext of AES-256 key
     */
    @GET("drm/key/{characterCode}")
    suspend fun getEncryptedKey(
        @Path("characterCode") characterCode: String
    ): ApiResponse<EncryptedKeyResponse>
}

/**
 * Request body for public key registration.
 */
data class RegisterKeyRequest(
    val characterCode: String,
    val publicKeyPem: String
)

/**
 * Response for encrypted key retrieval.
 */
data class EncryptedKeyResponse(
    val encryptedKey: String,  // Base64 RSA-OAEP ciphertext
    val keyId: String?         // Optional key version identifier
)
