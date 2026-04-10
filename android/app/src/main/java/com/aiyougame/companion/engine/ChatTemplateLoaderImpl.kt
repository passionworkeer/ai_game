package com.aiyougame.companion.engine

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 实现：从 GGUF metadata 读取 tokenizer.chat_template。
 * ERR-02 约束：禁止硬编码 Prompt 模板。
 *
 * Phase 1（简化）：直接跳过 GGUF 读取，返回空字符串。
 * Phase 2（llama.cpp NDK）：Replace with gguf_get_val_str() JNI call.
 */
@Singleton
class ChatTemplateLoaderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ChatTemplateLoader {

    companion object {
        private const val TAG = "ChatTemplateLoaderImpl"
        private const val ASSETS_TEMPLATE_FILE = "chat_template.txt"
    }

    override suspend fun load(modelFile: File?): String = withContext(Dispatchers.IO) {
        // Phase 1: no-op, PromptManager uses assets/prompts/ directly
        ""
    }
}
