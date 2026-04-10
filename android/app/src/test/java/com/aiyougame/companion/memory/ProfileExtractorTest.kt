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
}
