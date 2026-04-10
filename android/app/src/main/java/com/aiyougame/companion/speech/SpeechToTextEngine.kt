package com.aiyougame.companion.speech

/**
 * Interface for speech-to-text engines.
 * All implementations must perform inference locally (zero network requests).
 */
interface SpeechToTextEngine {

    /**
     * Transcribe an audio file to text.
     *
     * @param audioFilePath Path to a WAV audio file (16kHz, 16-bit PCM mono).
     * @return Result.success(text) on success, Result.failure(exception) on error.
     */
    suspend fun transcribe(audioFilePath: String): Result<String>

    /**
     * Check if the engine is ready to transcribe.
     * May return false if the model is still downloading.
     */
    fun isReady(): Boolean
}
