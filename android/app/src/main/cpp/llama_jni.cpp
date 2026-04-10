/*
 * llama_jni.cpp — JNI bridge between Kotlin and llama.cpp
 *
 * Phase 2: implements the JNI callbacks that LlamaEngineImpl expects.
 * Currently this is a STUB — the real implementation uses llama.cpp API.
 *
 * Build requires:
 *   1. Android NDK installed (see docs/MODEL.md)
 *   2. llama.cpp as git submodule at cpp/llama.cpp/
 *   3. Run: cmake --build build/ --config Release
 *
 * JNI method signatures:
 *   nativeInitEngine(String modelPath, int nCtx, int nThreads) → jlong
 *   nativeGenerateStream(String userMessage, String systemPrompt) → jobject (Flow)
 *   nativeFree(jlong ptr)
 *   nativeGetChatTemplate(String modelPath) → jstring
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <memory>
#include <vector>
#include <cstdlib>

#define LOG_TAG "llama_jni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declare llama.cpp types (Phase 2: #include actual headers)
// struct llama_context;
// struct llama_model;

// ── Context struct ────────────────────────────────────────────
struct LlamaJniContext {
    // Phase 2: replace with real llama_context*
    void* model_ctx = nullptr;
    std::string model_path;
    int n_ctx = 8192;
    int n_threads = 4;
    bool loaded = false;
};

// ── Singleton global context (simplified for Android) ────────
static LlamaJniContext g_ctx;

// ══════════════════════════════════════════════════════════════
// JNI Methods
// ══════════════════════════════════════════════════════════════

extern "C" {

/*
 * Class:     com_aiyougame_companion_llm_LlamaEngineImpl
 * Method:    nativeInitEngine
 * Signature: (Ljava/lang/String;II)J
 *
 * Phase 2: Load GGUF model file and initialize llama context.
 * Returns: native pointer as jlong (0 on failure).
 */
JNIEXPORT jlong JNICALL
Java_com_aiyougame_1companion_llm_LlamaEngineImpl_nativeInitEngine(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath,
        jint nCtx,
        jint nThreads) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGD("nativeInitEngine: path=%s, nCtx=%d, nThreads=%d", path, nCtx, nThreads);

    // ── Phase 2: Real llama.cpp init ──────────────────────────
    // #include "llama.h"
    // auto params = llama_model_params_from_file(...);
    // auto* model = llama_load_model_from_file(path, params);
    // auto* ctx = llama_init_from_model(model, ...);
    // return reinterpret_cast<jlong>(ctx);
    // ────────────────────────────────────────────────────────────

    // STUB: Just record the config
    g_ctx.model_path = path;
    g_ctx.n_ctx = nCtx;
    g_ctx.n_threads = nThreads;
    g_ctx.loaded = true;

    env->ReleaseStringUTFChars(modelPath, path);

    LOGD("nativeInitEngine: STUB — using mock. Returns 1.");
    return 1L;  // Non-zero = "loaded"
}

/*
 * Class:     com_aiyougame_companion_llm_LlamaEngineImpl
 * Method:    nativeFree
 * Signature: (J)V
 *
 * Phase 2: Free the llama context and model.
 */
JNIEXPORT void JNICALL
Java_com_aiyougame_1companion_llm_LlamaEngineImpl_nativeFree(
        JNIEnv* env,
        jobject /* this */,
        jlong ptr) {

    LOGD("nativeFree: ptr=%ld", ptr);

    // ── Phase 2: Real llama.cpp cleanup ────────────────────────
    // if (ptr != 0) {
    //     auto* ctx = reinterpret_cast<llama_context*>(ptr);
    //     llama_free(ctx);
    // }
    // ────────────────────────────────────────────────────────────

    g_ctx.loaded = false;
}

/*
 * Class:     com_aiyougame_companion_llm_LlamaEngineImpl
 * Method:    nativeGetChatTemplate
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 *
 * Phase 2: Read tokenizer.chat_template from GGUF file metadata.
 * Returns: chat template string or empty string on failure.
 */
JNIEXPORT jstring JNICALL
Java_com_aiyougame_1companion_llm_LlamaEngineImpl_nativeGetChatTemplate(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGD("nativeGetChatTemplate: path=%s", path);

    // ── Phase 2: Read GGUF metadata ───────────────────────────
    // gguf_init_params params = {.init_from_file = true, .path = path};
    // struct gguf_context* ctx = gguf_init_from_file(path, params);
    // const char* tmpl = gguf_get_val_str(ctx, gguf_find_key(ctx, "tokenizer.chat_template"));
    // return env->NewStringUTF(tmpl);
    // ────────────────────────────────────────────────────────────

    env->ReleaseStringUTFChars(modelPath, path);

    // STUB: return empty (ChatTemplateLoader will use assets fallback)
    return env->NewStringUTF("");
}

}  // extern "C"
