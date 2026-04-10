package com.aiyougame.companion.engine

/**
 * 从 GGUF metadata 动态读取 tokenizer.chat_template。
 * ERR-02 约束：禁止硬编码 Prompt 模板，必须从 GGUF 动态读取。
 *
 * Phase 1（简化）：直接返回空字符串，PromptManager 跳过 tokenizer 配置。
 * Phase 2（llama.cpp）：用 gguf_load_data 读取 GGUF kv-cache 中的 chat_template。
 */
interface ChatTemplateLoader {
    /**
     * 从 GGUF 文件读取 chat_template JSON 字符串。
     * @param modelPath GGUF 绝对路径
     * @return chat_template JSON；读取失败返回空字符串
     */
    fun load(modelPath: String): String

    /**
     * Phase 1 简化实现：返回 null。
     */
    fun buildTokenizer(chatTemplate: String): Tokenizer? = null
}

interface Tokenizer {
    fun encode(text: String): List<Int>
    fun decode(tokenIds: List<Int>): String
}
