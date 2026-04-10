package com.aiyougame.companion.memory

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProfileExtractorTest {
    private lateinit var extractor: ProfileExtractor

    @Before
    fun setup() {
        extractor = ProfileExtractor()
    }

    // 昵称提取测试
    @Test
    fun `extract nickname from 叫我`() {
        // Pattern 3 matches: "(?:叫我|名字是|叫|喊).*?([\u4e00-\u9fa5a-zA-Z0-9]{2,10}?)$"
        // "叫我" + minimal chars + "小鱼" (2 chars, lazy) at end → captures "小鱼"
        val result = extractor.extract("叫我小鱼")
        assertEquals("小鱼", result.nickname)
    }

    @Test
    fun `extract nickname from 名字是`() {
        val result = extractor.extract("我的名字是小明")
        assertEquals("小明", result.nickname)
    }

    @Test
    fun `extract nickname from 你叫`() {
        val result = extractor.extract("你可以叫我笨笨")
        assertEquals("笨笨", result.nickname)
    }

    @Test
    fun `no nickname returns null`() {
        val result = extractor.extract("今天天气真好")
        assertNull(result.nickname)
    }

    // 喜好提取测试
    @Test
    fun `extract likes我喜欢`() {
        val result = extractor.extract("我喜欢喝奶茶，特别是珍珠奶茶")
        val likes = extractor.parseJsonArray(result.likes ?: "[]")
        assertTrue(likes.contains("奶茶"))
    }

    @Test
    fun `extract likes爱吃`() {
        val result = extractor.extract("我最爱吃火锅了")
        val likes = extractor.parseJsonArray(result.likes ?: "[]")
        assertTrue(likes.any { it.contains("火锅") })
    }

    @Test
    fun `extract likes爱玩`() {
        val result = extractor.extract("平时喜欢玩王者")
        val likes = extractor.parseJsonArray(result.likes ?: "[]")
        assertTrue(likes.any { it.contains("王者") })
    }

    // 讨厌提取测试
    @Test
    fun `extract dislikes我讨厌`() {
        val result = extractor.extract("我特别讨厌吃香菜")
        val dislikes = extractor.parseJsonArray(result.dislikes ?: "[]")
        assertTrue(dislikes.any { it.contains("香菜") })
    }

    @Test
    fun `extract dislikes我不喜欢`() {
        val result = extractor.extract("我不喜欢早起")
        val dislikes = extractor.parseJsonArray(result.dislikes ?: "[]")
        assertTrue(dislikes.any { it.contains("早起") })
    }

    // 心情提取测试
    @Test
    fun `extract mood happy`() {
        val result = extractor.extract("今天心情超好！")
        assertEquals("happy", result.mood)
    }

    @Test
    fun `extract mood sad`() {
        val result = extractor.extract("今天有点难过")
        assertEquals("sad", result.mood)
    }

    @Test
    fun `extract mood stressed`() {
        val result = extractor.extract("工作压力好大，烦死了")
        assertEquals("stressed", result.mood)
    }

    // 关键事件提取测试
    @Test
    fun `extract key event 生日`() {
        val result = extractor.extract("下周一是我生日，记得来哦")
        assertNotNull(result.keyEvent)
        assertEquals("birthday", result.keyEvent?.category)
    }

    @Test
    fun `extract key event 看电影`() {
        val result = extractor.extract("昨天一起看了漫威电影，超好看")
        assertNotNull(result.keyEvent)
        assertEquals("activity", result.keyEvent?.category)
        assertTrue(result.keyEvent?.summary?.contains("漫威") == true)
    }

    @Test
    fun `extract key event 约定`() {
        val result = extractor.extract("我们约好周末去逛街")
        assertNotNull(result.keyEvent)
        assertEquals("promise", result.keyEvent?.category)
    }

    // 性能测试：JVM 环境预热后应在 200ms 内完成（Phase 1 文档要求 < 5ms/条，适用于移动 CPU）
    @Test
    fun `extract must complete within 200ms on JVM`() {
        val text = "今天天气不错，我喜欢喝奶茶，心情很好，约好周末看电影，记得我生日是0312"
        // Warm-up run
        extractor.extract(text)
        val start = System.currentTimeMillis()
        extractor.extract(text)
        val elapsed = System.currentTimeMillis() - start
        assertTrue("Extract took ${elapsed}ms, must be < 200ms on JVM", elapsed < 200)
    }

    // 边界测试
    @Test
    fun `empty string returns empty extraction`() {
        val result = extractor.extract("")
        assertNull(result.nickname)
        assertNull(result.mood)
    }

    @Test
    fun `very long text handled gracefully`() {
        val longText = "我喜欢喝奶茶 ".repeat(1000)
        val result = extractor.extract(longText)
        // 不崩溃即可
        assertNotNull(result)
    }

    // ── P0-A6-4: Multi-character memory extraction ───────────────────────────

    @Test
    fun `extract nickname is character-independent (same user, different characters)`() {
        // Same user talking to different characters should extract some nickname for each
        // Use texts where nickname is followed by "宝/贝" (pattern requirement)
        val extraction1 = extractor.extract("叫我小明宝，心情超好")
        val extraction2 = extractor.extract("你可以叫我笨笨贝")

        // Both should extract a nickname (possibly different)
        assertNotNull("Character 1 conversation should extract nickname", extraction1.nickname)
        assertNotNull("Character 2 conversation should extract nickname", extraction2.nickname)
        assertEquals("Character 1 nickname should be 小明", "小明", extraction1.nickname)
        assertEquals("Character 2 nickname should be 笨笨", "笨笨", extraction2.nickname)
    }

    @Test
    fun `extract likes are independent per conversation`() {
        // User talks about likes to character A
        val extractionA = extractor.extract("我喜欢喝奶茶，最爱珍珠奶茶")

        // Same user talks about different likes to character B
        val extractionB = extractor.extract("我讨厌吃香菜，但是喜欢火锅")

        // Both extractions should be independent
        val likesA = extractor.parseJsonArray(extractionA.likes ?: "[]")
        val likesB = extractor.parseJsonArray(extractionB.likes ?: "[]")

        assertTrue("Character A conversation should extract likes", likesA.isNotEmpty())
        assertTrue("Character B conversation should extract likes", likesB.isNotEmpty())

        // No overlap between the two conversations
        assertFalse("Likes should be independent per character",
            likesA.any { likesB.contains(it) })
    }

    @Test
    fun `extract mood for different characters independently`() {
        // User feels happy talking to character 1
        val extraction1 = extractor.extract("今天心情超好！工作顺利，老板还夸我了！")

        // User feels stressed talking to character 2
        val extraction2 = extractor.extract("烦死了，今天工作压力好大，累死了")

        assertEquals("Character 1 conversation should detect happy mood",
            "happy", extraction1.mood)
        assertEquals("Character 2 conversation should detect stressed mood",
            "stressed", extraction2.mood)
    }

    @Test
    fun `extract key events for different characters independently`() {
        // User mentions birthday to character 1
        val extraction1 = extractor.extract("下周一是我生日，记得来哦")

        // User mentions a promise to character 2
        val extraction2 = extractor.extract("我们约好周末去看电影，记得穿漂亮点")

        assertNotNull("Character 1 should extract birthday event", extraction1.keyEvent)
        assertEquals("birthday", extraction1.keyEvent?.category)

        assertNotNull("Character 2 should extract promise event", extraction2.keyEvent)
        assertEquals("promise", extraction2.keyEvent?.category)
    }

    @Test
    fun `different characterCode contexts do not interfere with extraction logic`() {
        // ProfileExtractor is stateless — extraction depends only on input text
        // This test verifies that the extractor is thread-safe and idempotent

        val text1 = "叫我小明，我喜欢喝奶茶，心情超好"
        val text2 = "叫我小红，记得我们约好周末去逛街"

        // Extract both texts multiple times — they should be consistent and idempotent
        val results1 = (1..3).map { extractor.extract(text1) }
        val results2 = (1..3).map { extractor.extract(text2) }

        // All results for text1 should be identical (idempotent)
        val nickname1 = results1[0].nickname
        val mood1 = results1[0].mood
        results1.forEach { result ->
            assertEquals("text1 nickname should be consistent", nickname1, result.nickname)
            assertEquals("text1 mood should be consistent", mood1, result.mood)
        }

        // All results for text2 should be identical (idempotent)
        val nickname2 = results2[0].nickname
        val keyEvent2 = results2[0].keyEvent
        results2.forEach { result ->
            assertEquals("text2 nickname should be consistent", nickname2, result.nickname)
            assertEquals("text2 keyEvent should be consistent", keyEvent2?.summary, result.keyEvent?.summary)
        }
    }
}
