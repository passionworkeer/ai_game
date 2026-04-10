package com.aiyougame.companion.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Mock LLM engine for Phase 1.
 * Returns preset responses with a typewriter effect (one character every 300 ms).
 */
class MockLlamaEngine : LlamaEngine {

    private val presetResponses = listOf(
        "收到啦～谢谢你跟我说这些",
        "嗯，我在呢。怎么了？",
        "这样啊……那你想怎么做？",
        "辛苦了，我陪着你。",
        "别急，慢慢说。"
    )

    override suspend fun initialize(): Result<Unit> = Result.success(Unit)

    override fun generateResponse(userMessage: String, systemPrompt: String): Flow<String> = flow {
        val chosen = presetResponses[Random.nextInt(presetResponses.size)]
        for (char in chosen) {
            emit(char.toString())
            delay(300)
        }
    }

    override fun release() {
        // No-op in mock
    }
}
