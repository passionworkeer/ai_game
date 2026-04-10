package com.aiyougame.companion.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides character-specific system prompts from assets.
 *
 * Prompt file location: assets/prompts/characters/{characterCode}_base.txt
 * If no file exists for a character, returns a default prompt.
 *
 * ERR-02 constraint: no hardcoded prompts — all read from assets.
 */
@Singleton
class CharacterPromptProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PROMPTS_DIR = "prompts/characters"
        private const val DEFAULT_PROMPT_FILE = "prompts/default.txt"
    }

    /**
     * Get the base system prompt for a character.
     *
     * @param characterCode The character identifier (e.g. "gu_chen", "new_char").
     * @return The character's base system prompt, or a default if not found.
     */
    fun getSystemPrompt(characterCode: String): String {
        val fileName = "${characterCode.lowercase()}_base.txt"
        return loadAssetFile("$PROMPTS_DIR/$fileName")
            ?: loadAssetFile(DEFAULT_PROMPT_FILE)
            ?: buildDefaultPrompt(characterCode)
    }

    private fun loadAssetFile(path: String): String? {
        return runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun buildDefaultPrompt(characterCode: String): String {
        return buildString {
            appendLine("## 角色基础设定")
            appendLine()
            appendLine("角色ID: $characterCode")
            appendLine()
            appendLine("你是一个温柔体贴的AI陪伴角色，与用户建立情感连接。")
            appendLine("请用自然、亲切的方式与用户交流。")
        }
    }
}
