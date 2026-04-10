package com.aiyougame.companion.engine

import java.io.File

/**
 * Phase 2 GGUF metadata reader.
 * Phase 1: stub — returns null, ChatTemplateLoader skips GGUF reading.
 * Phase 2 (NDK ready): Replace with llama.cpp JNI call via gguf_get_val_str().
 */
object GGUFMetadataReader {
    fun getChatTemplate(modelFile: File): String? = null
}
