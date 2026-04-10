package com.aiyougame.companion.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Mock LLM engine for Phase 1.
 * Context-aware responses with keyword detection, categorized response banks,
 * and a typewriter effect (50-150 ms random per character).
 */
class MockLlamaEngine : LlamaEngine {

    companion object {
        // Response banks by category
        private val GREETING = listOf(
            "早上好呀～今天心情怎么样？",
            "嗨，你来啦！",
            "想我了？"
        )
        private val QUESTION = listOf(
            "嗯……让我想想",
            "这个嘛，其实我也有点好奇",
            "说来听听？"
        )
        private val EMOTIONAL = listOf(
            "我在呢，别怕",
            "辛苦你了，抱抱",
            "难过的时候记得找我"
        )
        private val CASUAL = listOf(
            "哈哈，太有趣了",
            "嗯嗯，我懂我懂",
            "这样啊～"
        )
        private val DEFAULT = listOf(
            "收到～",
            "嗯，我知道了",
            "好呀"
        )

        // Keyword patterns (case-insensitive)
        private val GREETING_KEYWORDS = Regex(
            "早上|晚上|你好|在吗|嗨|hi|hello|在不在", RegexOption.IGNORE_CASE
        )
        private val QUESTION_KEYWORDS = Regex(
            "怎么|为什么|什么|如何|是不是|能不能|可以问", RegexOption.IGNORE_CASE
        )
        private val EMOTIONAL_KEYWORDS = Regex(
            "难过|伤心|累|辛苦|压力|焦虑|担心|害怕|怕", RegexOption.IGNORE_CASE
        )
        private val CASUAL_KEYWORDS = Regex(
            "哈哈|笑|好玩|有趣|哈哈哈哈哈", RegexOption.IGNORE_CASE
        )

        // Soft personality prefixes (30% chance)
        private val SOFT_PREFIXES = listOf("嗯～", "～", "啊")

        private const val MIN_DELAY_MS = 50L
        private const val MAX_DELAY_MS = 150L
        private const val SOFT_PREFIX_CHANCE = 0.30
    }

    override suspend fun initialize(): Result<Unit> = Result.success(Unit)

    override fun generateResponse(userMessage: String, systemPrompt: String): Flow<String> = flow {
        val category = detectCategory(userMessage)
        val baseResponse = pickResponse(category)
        val response = applySoftPrefix(baseResponse)

        // Typewriter effect: emit one character at a time with random delay
        for (char in response) {
            emit(char.toString())
            delay(Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1))
        }
    }

    /**
     * Detect category by keyword scanning.
     * Falls back to frequency scoring if no single category matches.
     */
    private fun detectCategory(message: String): ResponseCategory {
        val greetingMatches = GREETING_KEYWORDS.findAll(message).count()
        val questionMatches = QUESTION_KEYWORDS.findAll(message).count()
        val emotionalMatches = EMOTIONAL_KEYWORDS.findAll(message).count()
        val casualMatches = CASUAL_KEYWORDS.findAll(message).count()

        val scores = listOf(
            greetingMatches to ResponseCategory.GREETING,
            questionMatches to ResponseCategory.QUESTION,
            emotionalMatches to ResponseCategory.EMOTIONAL,
            casualMatches to ResponseCategory.CASUAL
        )

        val maxScore = scores.maxOfOrNull { it.first } ?: 0
        if (maxScore == 0) return ResponseCategory.DEFAULT

        // If multiple categories tie for max, pick randomly among them
        val tiedCategories = scores.filter { it.first == maxScore }.map { it.second }
        return tiedCategories.random()
    }

    private fun pickResponse(category: ResponseCategory): String {
        return when (category) {
            ResponseCategory.GREETING -> GREETING.random()
            ResponseCategory.QUESTION -> QUESTION.random()
            ResponseCategory.EMOTIONAL -> EMOTIONAL.random()
            ResponseCategory.CASUAL -> CASUAL.random()
            ResponseCategory.DEFAULT -> DEFAULT.random()
        }
    }

    /**
     * Apply soft personality prefix 30% of the time.
     */
    private fun applySoftPrefix(response: String): String {
        if (Random.nextFloat() >= SOFT_PREFIX_CHANCE) return response
        val prefix = SOFT_PREFIXES.random()
        // Avoid double prefix if already starts with soft prefix
        return if (response.startsWith("嗯～") || response.startsWith("～") || response.startsWith("啊")) {
            response
        } else {
            "$prefix$response"
        }
    }

    override fun release() {
        // No-op in mock
    }

    private enum class ResponseCategory {
        GREETING, QUESTION, EMOTIONAL, CASUAL, DEFAULT
    }
}
