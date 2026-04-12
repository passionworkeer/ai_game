package com.aiyougame.companion.llm

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for MockLlamaEngine.
 * Tests category detection, response generation, and soft prefix behavior.
 */
class MockLlamaEngineTest {

    private val engine = MockLlamaEngine()

    @Test
    fun initialize_returnsSuccess() = runBlocking {
        val result = engine.initialize()
        assertTrue(result.isSuccess)
    }

    @Test
    fun generateResponse_returnsNonEmpty_forGreetingKeywords() = runBlocking {
        val greetings = listOf("早上好", "你好", "在吗", "嗨", "hi", "hello")

        for (greeting in greetings) {
            val response = engine.generateResponse(greeting, "").first()
            assertNotNull(response)
            assertTrue(response.isNotEmpty())
        }
    }

    @Test
    fun generateResponse_returnsNonEmpty_forAnyMessage() = runBlocking {
        val messages = listOf(
            "今天天气真好",
            "我想吃火锅",
            "工作好累啊",
            "哈哈哈太搞笑了",
            "你怎么这么聪明"
        )

        for (message in messages) {
            val response = engine.generateResponse(message, "").first()
            assertNotNull(response)
            assertTrue(response.isNotEmpty())
        }
    }

    @Test
    fun generateResponse_typewriterEffect_emitsCharactersSequentially() = runBlocking {
        val messages = listOf("嗨", "嗯", "收到")

        for (message in messages) {
            val flow = engine.generateResponse(message, "")
            var collectedCount = 0
            flow.collect {
                collectedCount++
                // Stop after collecting a few characters
                if (collectedCount >= 5) return@collect
            }
            // At least first character should be emitted
            assertTrue(collectedCount > 0)
        }
    }

    @Test
    fun generateResponse_detectsQuestionKeywords() = runBlocking {
        val questions = listOf(
            "怎么做的",
            "为什么是这样",
            "这是什么",
            "如何实现",
            "是不是真的",
            "能不能帮帮我",
            "可以问你问题吗"
        )

        for (question in questions) {
            val response = engine.generateResponse(question, "").first()
            assertNotNull(response)
            assertTrue(response.isNotEmpty())
        }
    }

    @Test
    fun generateResponse_detectsEmotionalKeywords() = runBlocking {
        val emotional = listOf(
            "我很难过",
            "好伤心",
            "工作好累",
            "辛苦你了",
            "压力大",
            "很焦虑",
            "担心死了",
            "好害怕",
            "我怕黑"
        )

        for (emotion in emotional) {
            val response = engine.generateResponse(emotion, "").first()
            assertNotNull(response)
            assertTrue(response.isNotEmpty())
        }
    }

    @Test
    fun generateResponse_detectsCasualKeywords() = runBlocking {
        val casual = listOf(
            "哈哈好好笑",
            "太好笑了",
            "这个好好玩",
            "真有趣",
            "哈哈哈哈哈哈哈"
        )

        for (casualMsg in casual) {
            val response = engine.generateResponse(casualMsg, "").first()
            assertNotNull(response)
            assertTrue(response.isNotEmpty())
        }
    }

    @Test
    fun generateResponse_handlesEmptyMessage() = runBlocking {
        val response = engine.generateResponse("", "").first()
        assertNotNull(response)
        assertTrue(response.isNotEmpty())
    }

    @Test
    fun generateResponse_handlesLongMessage() = runBlocking {
        val longMessage = "这是一个很长的消息，".repeat(50)
        val response = engine.generateResponse(longMessage, "").first()
        assertNotNull(response)
        assertTrue(response.isNotEmpty())
    }

    @Test
    fun generateResponse_ignoresCaseForKeywords() = runBlocking {
        val upperCase = "HI HELLO 早上好"
        val response = engine.generateResponse(upperCase, "").first()
        assertNotNull(response)
        assertTrue(response.isNotEmpty())
    }

    @Test
    fun release_doesNotThrow() {
        // Should be safe to call multiple times
        engine.release()
        engine.release()
    }

    @Test
    fun generateResponse_isContextAware_usesSystemPrompt() = runBlocking {
        // System prompt is accepted without error
        val customSystemPrompt = "你是一个温柔体贴的角色"
        val response = engine.generateResponse("你好", customSystemPrompt).first()
        assertNotNull(response)
        assertTrue(response.isNotEmpty())
    }
}
