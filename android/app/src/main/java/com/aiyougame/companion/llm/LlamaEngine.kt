package com.aiyougame.companion.llm

import kotlinx.coroutines.flow.Flow

/**
 * LLM inference engine interface.
 * Phase 2 will be implemented by LlamaEngineImpl (llama.cpp JNI).
 * Phase 1 uses MockLlamaEngine which returns preset responses.
 */
interface LlamaEngine {

    /**
     * Initialise the engine (load GGUF model).
     * Phase 1: immediately returns Success.
     * Phase 2: loads model file, takes 3-5 s.
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Send a message and receive a streaming reply.
     * The returned Flow is cold; each collection triggers one inference run.
     * Phase 1: returns preset text without real inference.
     */
    fun generateResponse(userMessage: String, systemPrompt: String): Flow<String>

    /**
     * Multimodal (image + text) inference.
     *
     * @param rgbImage RGB bytes in row-major order, length = width * height * 3
     */
    fun generateResponseWithImage(
        userMessage: String,
        systemPrompt: String,
        rgbImage: ByteArray,
        width: Int,
        height: Int,
    ): Flow<String> = generateResponse(userMessage, systemPrompt)

    /**
     * Release the model and free memory.
     */
    fun release()
}
