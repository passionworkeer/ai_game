package com.aiyougame.companion.llm

import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * PromptManager TDD — RED phase.
 *
 * 定义 PromptManager 的预期行为：
 * 1. 好感度 0/39/40/64/65/84/85/100 → 注入对应等级亲密片段
 * 2. 三个 section 都出现：角色基础 / {MEMORY_SNAPSHOT} / 工具规范
 * 3. 好感度超界时优雅降级
 */
class PromptManagerTest {

    private val mockAssets: android.content.res.AssetManager = mockk(relaxed = true)
    private val mockContext: android.content.Context = mockk(relaxed = true)

    private lateinit var promptManager: PromptManager

    // 顾晨四级亲密片段（测试中作为断言锚点）
    private val dailyText    = "## 当前相处状态：日常温柔"
    private val flirtyText   = "## 当前相处状态：暧昧升温"
    private val closeText    = "## 当前相处状态：确认心意"
    private val deepText     = "## 当前相处状态：深度陪伴"
    private val baseText     = "你叫顾晨，24岁，是用户的男朋友。"
    private val toolSpecText = "{TOOL_SPEC}\n你可以通过工具与用户互动。"

    // 路径 → 内容 映射（测试体内填充）
    private val stubs = mutableMapOf<String, String>()

    // slot：每次 open() 调用时捕获路径，查找 stub
    private val pathSlot = slot<String>()

    @Before
    fun setup() {
        mockkStatic(android.content.Context::class)
        every { mockContext.assets } returns mockAssets

        // 所有 open() 调用走 slot → 查 stubs → fallback 抛异常
        every { mockAssets.open(capture(pathSlot)) } answers {
            val path = pathSlot.captured
            val content = stubs[path]
            if (content != null) {
                ByteArrayInputStream(content.toByteArray())
            } else {
                throw java.io.IOException("PromptManagerTest: no stub for '$path'")
            }
        }

        // 用 mock Context 手动构造 PromptManager（绕开 Hilt）
        val intimacyLoader = IntimacyPromptLoader(mockContext)
        promptManager = PromptManager(mockContext, intimacyLoader)
    }

    @After
    fun teardown() {
        unmockkStatic(android.content.Context::class)
    }

    // ─── Helper ─────────────────────────────────────────────────

    /** 向 stubs 注册一个路径 → 内容，之后 open(path) 返回该内容 */
    private fun stub(path: String, content: String) {
        stubs[path] = content
    }

    // ─── Section 完整性测试 ──────────────────────────────────────

    @Test
    fun `buildSystemPrompt - contains character base section`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_flirty.txt", flirtyText)
        stub("prompts/toolspec.txt", toolSpecText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 50,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue("Prompt should contain baseText:\n$result", result.contains(baseText))
    }

    @Test
    fun `buildSystemPrompt - contains MEMORY_SNAPSHOT markers`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 50,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains("{MEMORY_SNAPSHOT}"))
        assertTrue(result.contains("{/MEMORY_SNAPSHOT}"))
    }

    @Test
    fun `buildSystemPrompt - contains tool spec section`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/toolspec.txt", toolSpecText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 50,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue("Prompt should contain toolSpec:\n$result", result.contains(toolSpecText))
    }

    // ─── 亲密度等级注入测试 ──────────────────────────────────────

    @Test
    fun `buildSystemPrompt - score 0 injects DAILY level`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_daily.txt", dailyText)
        stub("prompts/intimacy/gu_chen_flirty.txt", flirtyText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 0,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains(dailyText))
        assertFalse(result.contains(flirtyText))
    }

    @Test
    fun `buildSystemPrompt - score 39 injects DAILY level`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_daily.txt", dailyText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 39,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains(dailyText))
    }

    @Test
    fun `buildSystemPrompt - score 40 injects FLIRTY level`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_daily.txt", dailyText)
        stub("prompts/intimacy/gu_chen_flirty.txt", flirtyText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 40,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains(flirtyText))
        assertFalse(result.contains(dailyText))
    }

    @Test
    fun `buildSystemPrompt - score 64 injects FLIRTY level`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_flirty.txt", flirtyText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 64,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains(flirtyText))
    }

    @Test
    fun `buildSystemPrompt - score 65 injects CLOSE level`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_flirty.txt", flirtyText)
        stub("prompts/intimacy/gu_chen_close.txt", closeText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 65,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains(closeText))
        assertFalse(result.contains(flirtyText))
    }

    @Test
    fun `buildSystemPrompt - score 84 injects CLOSE level`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_close.txt", closeText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 84,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains(closeText))
    }

    @Test
    fun `buildSystemPrompt - score 85 injects DEEP level`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_close.txt", closeText)
        stub("prompts/intimacy/gu_chen_deep.txt", deepText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 85,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains(deepText))
        assertFalse(result.contains(closeText))
    }

    @Test
    fun `buildSystemPrompt - score 100 injects DEEP level`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_deep.txt", deepText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 100,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains(deepText))
    }

    // ─── 边界行为测试 ───────────────────────────────────────────

    @Test
    fun `buildSystemPrompt - negative score falls back to DAILY`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_daily.txt", dailyText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = -10,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains(dailyText))
    }

    @Test
    fun `buildSystemPrompt - score over 100 falls back to DEEP`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        stub("prompts/intimacy/gu_chen_deep.txt", deepText)

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 999,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(result.contains(deepText))
    }

    @Test
    fun `buildSystemPrompt - intimacy file missing does not crash`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)
        // 不 stub intimacy 文件 → 抛 IOException，runCatching 兜底不崩溃

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 50,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertNotNull(result)
        assertTrue(result.contains(baseText))
    }

    // ─── 记忆快照内容测试 ────────────────────────────────────────

    @Test
    fun `buildSystemPrompt - memory snapshot contains user nickname`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)

        val profile = com.aiyougame.companion.memory.db.UserProfileEntity(
            nickname = "小鱼",
            affectionLevel = 50,
        )

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 50,
            userProfile = profile,
            recentEvents = emptyList(),
        )

        assertTrue("Memory snapshot should contain nickname '小鱼':\n$result", result.contains("小鱼"))
    }

    @Test
    fun `buildSystemPrompt - memory snapshot contains key events`() {
        stub("prompts/characters/gu_chen_base.txt", baseText)

        val events = listOf(
            com.aiyougame.companion.memory.db.KeyEventEntity(
                id = 1,
                characterId = "gu_chen",
                type = "activity",
                content = "周末一起看了漫威电影",
                happenedAt = System.currentTimeMillis(),
            )
        )

        val result = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 50,
            userProfile = null,
            recentEvents = events,
        )

        assertTrue(result.contains("周末一起看了漫威电影"))
        assertTrue(result.contains("activity"))
    }

    // ─── 角色隔离测试 ───────────────────────────────────────────

    @Test
    fun `buildSystemPrompt - different character loads different base file`() {
        stub("prompts/characters/gu_chen_base.txt", "顾晨视角")
        stub("prompts/characters/shen_jin_base.txt", "沈烬视角")

        val guChen = promptManager.buildSystemPrompt(
            characterId = "gu_chen",
            affectionLevel = 50,
            userProfile = null,
            recentEvents = emptyList(),
        )
        val shenJin = promptManager.buildSystemPrompt(
            characterId = "shen_jin",
            affectionLevel = 50,
            userProfile = null,
            recentEvents = emptyList(),
        )

        assertTrue(guChen.contains("顾晨视角"))
        assertTrue(shenJin.contains("沈烬视角"))
    }
}
