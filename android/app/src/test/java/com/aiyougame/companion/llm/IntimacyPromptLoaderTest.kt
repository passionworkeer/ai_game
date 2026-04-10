package com.aiyougame.companion.llm

import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * IntimacyPromptLoader 单元测试 — 纯 JVM，无需 Android 模拟器。
 *
 * 测试范围：
 * - IntimacyLevel.from() 边界覆盖（10 个 case）
 * - IntimacyPromptLoader.load() 文件名构造、文件存在/不存在行为
 */
class IntimacyPromptLoaderTest {

    private val mockAssets: android.content.res.AssetManager = mockk(relaxed = true)
    private val mockContext: android.content.Context = mockk(relaxed = true)

    private lateinit var loader: IntimacyPromptLoader

    @Before
    fun setup() {
        mockkStatic(android.content.Context::class)
        every { mockContext.assets } returns mockAssets
        loader = IntimacyPromptLoader(mockContext)
    }

    @After
    fun teardown() {
        unmockkStatic(android.content.Context::class)
    }

    // ─── IntimacyLevel.from() ─────────────────────────────────────

    @Test
    fun `from - score 0 returns DAILY`() {
        assertEquals(IntimacyLevel.DAILY, IntimacyLevel.from(0))
    }

    @Test
    fun `from - score 39 returns DAILY`() {
        assertEquals(IntimacyLevel.DAILY, IntimacyLevel.from(39))
    }

    @Test
    fun `from - score 40 returns FLIRTY`() {
        assertEquals(IntimacyLevel.FLIRTY, IntimacyLevel.from(40))
    }

    @Test
    fun `from - score 64 returns FLIRTY`() {
        assertEquals(IntimacyLevel.FLIRTY, IntimacyLevel.from(64))
    }

    @Test
    fun `from - score 65 returns CLOSE`() {
        assertEquals(IntimacyLevel.CLOSE, IntimacyLevel.from(65))
    }

    @Test
    fun `from - score 84 returns CLOSE`() {
        assertEquals(IntimacyLevel.CLOSE, IntimacyLevel.from(84))
    }

    @Test
    fun `from - score 85 returns DEEP`() {
        assertEquals(IntimacyLevel.DEEP, IntimacyLevel.from(85))
    }

    @Test
    fun `from - score 100 returns DEEP`() {
        assertEquals(IntimacyLevel.DEEP, IntimacyLevel.from(100))
    }

    @Test
    fun `from - negative score coerced to DAILY`() {
        assertEquals(IntimacyLevel.DAILY, IntimacyLevel.from(-5))
    }

    @Test
    fun `from - score over 100 coerced to DEEP`() {
        assertEquals(IntimacyLevel.DEEP, IntimacyLevel.from(999))
    }

    // ─── IntimacyPromptLoader.load() ───────────────────────────────

    @Test
    fun `load - file exists returns content`() {
        val content = "## 当前相处状态：日常温柔"
        every {
            mockAssets.open("prompts/intimacy/gu_chen_daily.txt")
        } returns ByteArrayInputStream(content.toByteArray())

        val result = loader.load("gu_chen", IntimacyLevel.DAILY)

        assertEquals(content, result)
    }

    @Test
    fun `load - file not found returns empty string`() {
        every {
            mockAssets.open("prompts/intimacy/gu_chen_daily.txt")
        } throws java.io.IOException("not found")

        val result = loader.load("gu_chen", IntimacyLevel.DAILY)

        assertEquals("", result)
    }

    @Test
    fun `load - character id is lowercased`() {
        val content = "## 当前相处状态：暧昧升温"
        every {
            mockAssets.open("prompts/intimacy/shen_jin_flirty.txt")
        } returns ByteArrayInputStream(content.toByteArray())

        val result = loader.load("SHEN_JIN", IntimacyLevel.FLIRTY)

        assertEquals(content, result)
        verify(exactly = 1) { mockAssets.open("prompts/intimacy/shen_jin_flirty.txt") }
    }

    @Test
    fun `load - all four levels produce correct file names`() {
        every { mockAssets.open(any()) } returns ByteArrayInputStream("x".toByteArray())

        loader.load("lin_zhixia", IntimacyLevel.DAILY)
        loader.load("lin_zhixia", IntimacyLevel.FLIRTY)
        loader.load("lin_zhixia", IntimacyLevel.CLOSE)
        loader.load("lin_zhixia", IntimacyLevel.DEEP)

        verify {
            mockAssets.open("prompts/intimacy/lin_zhixia_daily.txt")
            mockAssets.open("prompts/intimacy/lin_zhixia_flirty.txt")
            mockAssets.open("prompts/intimacy/lin_zhixia_close.txt")
            mockAssets.open("prompts/intimacy/lin_zhixia_deep.txt")
        }
    }

    @Test
    fun `load - deep level for gu_chen`() {
        val content = "## 当前相处状态：深度陪伴"
        every {
            mockAssets.open("prompts/intimacy/gu_chen_deep.txt")
        } returns ByteArrayInputStream(content.toByteArray())

        val result = loader.load("gu_chen", IntimacyLevel.DEEP)

        assertEquals(content, result)
    }
}
