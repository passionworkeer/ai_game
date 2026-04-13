package com.aiyougame.companion.engine

import kotlinx.coroutines.flow.Flow

/**
 * Model downloader interface for Phase 1 CDN download.
 *
 * Download flow:
 * 1. Check if model already exists via [isModelReady]
 * 2. If not, call [download] with CDN URL + SHA-256
 * 3. Observe progress via [progressFlow]
 * 4. On success, [download] returns the local file path
 *
 * Phase 1 CDN: https://cdn.aiyougame.com/models/gemma-4-E4B-it-Q4_0.gguf
 * Target path: context.filesDir/models/
 */
interface ModelDownloader {

    /**
     * Progress snapshot for a single download.
     */
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val isComplete: Boolean,
    ) {
        val percentage: Int
            get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
    }

    /**
     * Download the GGUF model from CDN with SHA-256 verification.
     *
     * @param cdnUrl  Full CDN URL, e.g. "https://cdn.aiyougame.com/models/gemma-4-E4B-it-Q4_0.gguf"
     * @param sha256  Expected SHA-256 of the file (hex lowercase)
     * @return Result.success(localPath) on success, Result.failure on any error
     *
     * Behavior:
     * - Supports resume: checks existing file size, sends Range header if partial
     * - Verifies SHA-256 after full download
     * - Cleans up corrupt partial file on failure
     */
    suspend fun download(cdnUrl: String, sha256: String): Result<String>

    /**
     * Download an arbitrary file into `filesDir/models/<fileName>` with SHA-256 verification.
     *
     * This is used for multimodal setups where we need both the text GGUF and an `mmproj` GGUF.
     */
    suspend fun download(cdnUrl: String, sha256: String, fileName: String): Result<String> =
        download(cdnUrl, sha256) // default: legacy implementations ignore fileName

    /**
     * Cold Flow of download progress. Emits after each chunk is written.
     * Completes when download finishes (success or failure).
     */
    fun progressFlow(): Flow<Progress>

    /**
     * Check if the model file exists and matches the expected SHA-256.
     * Fast check — no network call.
     */
    suspend fun isModelReady(sha256: String): Boolean

    /**
     * Check if a specific file exists and matches SHA-256.
     */
    suspend fun isFileReady(fileName: String, sha256: String): Boolean = isModelReady(sha256)

    /**
     * Delete the local model file (for reset / re-download).
     * Safe to call even if file does not exist.
     */
    suspend fun deleteLocalModel()

    /**
     * Get the local model file path (e.g. filesDir/models/gemma-4-E4B-it-Q4_0.gguf).
     * Does not check if file exists; use [isModelReady] first.
     */
    fun getModelPath(): String

    /**
     * Get a local path for a specific file under `filesDir/models/`.
     */
    fun getFilePath(fileName: String): String = getModelPath()
}
