package com.aiyougame.companion.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 assets 目录按角色 ID + 亲密度等级动态加载亲密度片段。
 *
 * 文件路径规范：
 *   assets/prompts/intimacy/{characterId}_{level}.txt
 *   例如：gu_chen_flirty.txt、shen_jin_deep.txt、lin_zhixia_close.txt
 *
 * 等级名称小写：daily / flirty / close / deep
 *
 * 加载失败时返回空字符串，不抛出异常，确保 Prompt 构建不中断。
 */
@Singleton
class IntimacyPromptLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val promptsDir = "prompts/intimacy"

    /**
     * 加载指定角色 + 等级的亲密片段。
     *
     * @param characterId 角色 ID（小写），例如 "gu_chen"
     * @param level       亲密度等级
     * @return 亲密度片段文本；文件不存在或读取失败时返回空字符串
     */
    fun load(characterId: String, level: IntimacyLevel): String {
        val fileName = "${characterId.lowercase()}_${level.name.lowercase()}.txt"
        return runCatching {
            context.assets.open("$promptsDir/$fileName").bufferedReader().use { it.readText() }
        }.onFailure { e ->
            if (e is IOException) {
                // 文件不存在，继续（Prompt 构建不受影响）
            } else {
                throw e
            }
        }.getOrDefault("")
    }
}
