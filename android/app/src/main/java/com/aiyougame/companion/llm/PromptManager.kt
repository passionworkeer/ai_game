package com.aiyougame.companion.llm

import android.content.Context
import com.aiyougame.companion.memory.db.KeyEventEntity
import com.aiyougame.companion.memory.db.UserProfileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统 Prompt 构建器。
 *
 * Prompt 拼接顺序（从上到下注入）：
 * 1. [角色基础设定]   — assets/prompts/characters/{characterId}_base.txt
 * 2. [亲密度片段]     — assets/prompts/intimacy/{characterId}_{level}.txt  ← 动态
 * 3. [记忆快照]       — {MEMORY_SNAPSHOT} 块
 * 4. [工具调用规范]   — assets/prompts/toolspec.txt
 *
 * ERR-02 约束：不得硬编码 Prompt 模板，所有内容从 assets 读取。
 * 亲密度等级由调用方传入，实时反映 UserProfile.affectionLevel 变化。
 */
@Singleton
class PromptManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val intimacyLoader: IntimacyPromptLoader,
) {

    // ──────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────

    /**
     * 构建完整 System Prompt。
     *
     * @param characterId   角色 ID（小写），对应 assets 目录结构
     * @param affectionLevel 好感度分数（0–100），决定注入哪级亲密片段
     * @param userProfile   用户画像（昵称/喜好/心情），可为空
     * @param recentEvents  近期关键事件列表，可为空
     * @return 拼接后的完整 System Prompt 字符串
     */
    fun buildSystemPrompt(
        characterId: String,
        affectionLevel: Int,
        userProfile: UserProfileEntity?,
        recentEvents: List<KeyEventEntity>,
    ): String {
        val intimacyLevel = IntimacyLevel.from(affectionLevel)

        val baseSection   = loadCharacterBase(characterId)
        val intimacySection = intimacyLoader.load(characterId, intimacyLevel)
        val memorySection = buildMemorySnapshot(userProfile, recentEvents)
        val toolSpec      = loadToolSpec()

        return buildString {
            appendLine(baseSection)
            if (intimacySection.isNotBlank()) {
                appendLine()
                appendLine(intimacySection)
            }
            appendLine()
            appendLine("{MEMORY_SNAPSHOT}")
            appendLine(memorySection)
            appendLine("{/MEMORY_SNAPSHOT}")
            appendLine()
            appendLine(toolSpec)
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Private — section loaders
    // ──────────────────────────────────────────────────────────────

    private fun loadCharacterBase(characterId: String): String {
        return runCatching {
            context.assets.open("prompts/characters/${characterId.lowercase()}_base.txt")
                .bufferedReader().use { it.readText() }
        }.getOrDefault("## 角色基础设定\n\n[角色ID: $characterId]")
    }

    private fun buildMemorySnapshot(
        userProfile: UserProfileEntity?,
        recentEvents: List<KeyEventEntity>,
    ): String {
        val sb = StringBuilder()

        // 用户基本信息
        if (userProfile != null) {
            userProfile.nickname?.let { sb.appendLine("用户昵称：$it") }
            if (userProfile.gender != null) sb.appendLine("性别：${userProfile.gender}")
            if (userProfile.age != null)  sb.appendLine("年龄：${userProfile.age}")
            // 喜好解析
            val likes = runCatching {
                userProfile.interests.let {
                    if (it.startsWith("[")) it.removeSurrounding("[", "]").replace("\"", "")
                    else it
                }
            }.getOrDefault("")
            if (likes.isNotBlank()) sb.appendLine("用户喜好：$likes")
            // 心情日志取最新一条
            if (userProfile.moodLogs != "[]" && userProfile.moodLogs.isNotBlank()) {
                val latestMood = userProfile.moodLogs
                    .removeSurrounding("[", "]")
                    .split("},{")
                    .lastOrNull()
                    ?.let { "{${it}}" }
                    ?: ""
                if (latestMood.isNotBlank()) sb.appendLine("用户最近心情：$latestMood")
            }
        }

        // 关键事件
        if (recentEvents.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("你们之间的重要回忆：")
            recentEvents.take(5).forEach { event ->
                sb.appendLine("- ${event.content}（${event.type}）")
            }
        }

        return sb.toString().ifBlank { "（暂无记忆）" }
    }

    private fun loadToolSpec(): String {
        return runCatching {
            context.assets.open("prompts/toolspec.txt")
                .bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
}
