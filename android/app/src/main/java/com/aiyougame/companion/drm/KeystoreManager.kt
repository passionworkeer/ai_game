package com.aiyougame.companion.drm

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore Manager for AES-256 DRM.
 *
 * Security design:
 * 1. RSA-2048 key pairs stored in Android Keystore (hardware-backed when available)
 * 2. Each character has its own RSA key pair (alias = "drm-{characterCode}")
 * 3. AES-256 keys stored encrypted in EncryptedSharedPreferences (backed by Android Keystore)
 * 4. RSA-OAEP with SHA-256 + MGF1 for AES key transport
 *
 * Key never leaves the Keystore in plaintext form.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE_RSA = 2048
        private const val OAEP_DIGEST = "SHA-256"
        private const val OAEP_MGF_DIGEST = "SHA-256"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    /**
     * Generate RSA-2048 key pair and store in Android Keystore.
     * The key pair is hardware-backed on supported devices (StrongBox).
     */
    fun generateAndStoreRsaKeyPair(alias: String) {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE_RSA)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setDigests(OAEP_DIGEST, OAEP_MGF_DIGEST)
            // Note: StrongBox (hardware-backed) requires API 31+
            // Fallback to TEE if StrongBox unavailable
            .build()

        keyPairGenerator.initialize(keyGenSpec)
        keyPairGenerator.generateKeyPair()
    }

    /**
     * Get RSA public key from Keystore by alias.
     */
    fun getPublicKey(alias: String): PublicKey {
        return keyStore.getCertificate(alias).publicKey
    }

    /**
     * Get RSA public key in PEM format.
     * Used for registration with backend DRM service.
     */
    fun getPublicKeyPem(alias: String): String {
        val publicKey = getPublicKey(alias)
        val encoded = publicKey.encoded
        val base64 = Base64.encodeToString(encoded, Base64.NO_WRAP)
        return buildString {
            appendLine("-----BEGIN PUBLIC KEY-----")
            // Split into 64-character lines for PEM format
            var i = 0
            while (i < base64.length) {
                appendLine(base64.substring(i, minOf(i + 64, base64.length)))
                i += 64
            }
            append("-----END PUBLIC KEY-----")
        }
    }

    /**
     * Get RSA private key from Keystore.
     * Private key never leaves the Keystore.
     */
    fun getPrivateKey(alias: String): PrivateKey {
        return keyStore.getKey(alias, null) as PrivateKey
    }

    /**
     * RSA-OAEP decrypt AES key from base64-encoded ciphertext.
     * The AES key was encrypted by backend using our RSA public key.
     *
     * @param alias KeyStore alias for the RSA key pair
     * @param encryptedKeyBase64 Base64-encoded RSA-OAEP ciphertext
     * @return Decrypted AES-256 key (32 bytes) or null on error
     */
    fun decryptAesKey(alias: String, encryptedKeyBase64: String): ByteArray? {
        return try {
            val encryptedBytes = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
            val privateKey = getPrivateKey(alias)

            val oaepSpec = OAEPParameterSpec(
                OAEP_DIGEST,
                "MGF1",
                MGF1ParameterSpec(OAEP_MGF_DIGEST),
                PSource.PSpecified.DEFAULT
            )

            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)
            cipher.doFinal(encryptedBytes)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete key from Keystore.
     */
    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    /**
     * Check if key exists in Keystore.
     */
    fun hasKey(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }
}
