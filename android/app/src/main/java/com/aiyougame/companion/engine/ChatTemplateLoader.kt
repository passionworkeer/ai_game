package com.aiyougame.companion.engine

import java.io.File
/**
 * 从 GGUF metadata 动态读取 tokenizer.chat_template。
 * ERR-02 约束：禁止硬编码 Prompt 模板，必须从 GGUF 动态读取。
 *
 * Phase 1（简化）：直接返回空字符串，PromptManager 跳过 tokenizer 配置。
 * Phase 2（pure Kotlin GGUF reader）：用 GGUFMetadataReader 读取 GGUF kv-cache 中的 chat_template。
 * Phase 3（llama.cpp NDK）：Replace with gguf_get_val_str() JNI call。
 */
interface ChatTemplateLoader {
    /**
     * 从 GGUF 文件读取 chat_template JSON 字符串。
     * @param modelFile GGUF File 对象，null 时跳过 GGUF 读取走 assets 回退
     * @return chat_template JSON；读取失败返回空字符串
     */
    suspend fun load(modelFile: File?): String

    /**
     * Phase 1 简化实现：返回 null。
     */
    fun buildTokenizer(chatTemplate: String): Tokenizer? = null
}

interface Tokenizer {
    fun encode(text: String): List<Int>
    fun decode(tokenIds: List<Int>): String
}
