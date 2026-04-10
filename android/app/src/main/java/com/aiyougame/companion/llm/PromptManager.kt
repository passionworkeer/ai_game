package com.aiyougame.companion.llm

import android.content.Context
import com.aiyougame.companion.memory.MemoryManager
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
     * Convenience overload — extracts [UserProfileEntity] and [KeyEventEntity] from [snapshot].
     */
    fun buildSystemPrompt(
        characterId: String,
        affectionLevel: Int,
        snapshot: MemoryManager.MemorySnapshot,
    ): String {
        // Parse userProfile string into a synthetic UserProfileEntity for the existing builder
        val userProfile: UserProfileEntity? = snapshot.userProfile
            .takeIf { it.isNotBlank() && it != "（暂无用户画像）" }
            ?.let { parseUserProfileString(it) }

        val recentEvents: List<KeyEventEntity> = snapshot.keyEvents
            .takeIf { it.isNotBlank() && it != "（暂无关键事件）" }
            ?.let { parseKeyEventsString(it) }
            ?: emptyList()

        return buildSystemPrompt(
            characterId = characterId,
            affectionLevel = affectionLevel,
            userProfile = userProfile,
            recentEvents = recentEvents,
        )
    }

    /**
     * Parse a userProfile snapshot string back into a [UserProfileEntity].
     * Parses lines like "昵称：xxx", "喜好：xxx", "好感度：xx/100".
     */
    private fun parseUserProfileString(text: String): UserProfileEntity {
        var nickname: String? = null
        var interests: String? = null
        var affectionLevel: Int? = null
        text.lines().forEach { line ->
            when {
                line.startsWith("昵称：") -> nickname = line.removePrefix("昵称：").trim()
                line.startsWith("喜好：") -> {
                    val raw = line.removePrefix("喜好：").trim()
                    if (raw.isNotBlank()) {
                        interests = "[" + raw.split("、").joinToString(",") { "\"$it\"" } + "]"
                    }
                }
                line.startsWith("好感度：") -> {
                    val raw = line.removePrefix("好感度：").trim().substringBefore("/")
                    affectionLevel = raw.toIntOrNull()
                }
            }
        }
        return UserProfileEntity(
            nickname = nickname,
            interests = interests ?: "",
            affectionLevel = affectionLevel ?: 50,
        )
    }

    /**
     * Parse a keyEvents snapshot string back into a list of [KeyEventEntity].
     * Parses lines like "[birthday] 用户生日：xxx".
     */
    private fun parseKeyEventsString(text: String): List<KeyEventEntity> {
        return text.lines()
            .filter { it.startsWith("[") }
            .mapNotNull { line ->
                val closeBracket = line.indexOf(']')
                if (closeBracket < 0) return@mapNotNull null
                val type = line.substring(1, closeBracket).trim()
                val content = line.substring(closeBracket + 1).trim()
                KeyEventEntity(
                    characterId = "",
                    type = type,
                    content = content,
                    happenedAt = 0L,
                )
            }
    }

    /**
     * Full overload with raw entities.
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
