package com.aiyougame.companion.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 简化实现：load() 始终返回空字符串。
 * Phase 2 待接入 llama.cpp GGUF API 后，从 GGUF kv-cache 读取
 * chat_template 字段并实例化 SentencePiece/BPE Tokenizer。
 */
@Singleton
class ChatTemplateLoaderImpl @Inject constructor() : ChatTemplateLoader {

    /**
     * Phase 1：始终返回空字符串。
     * PromptManager 检测到空字符串时跳过 tokenizer 配置，走默认推理行为。
     */
    override fun load(modelPath: String): String = ""
}
