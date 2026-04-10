// llama_jni.cpp -- JNI bridge for llama.cpp (include/llama.h API)
// Phase 2: LlamaEngineImpl native backend
// llama.cpp API: gguf, llama_model_load_from_file, llama_sampler_chain_init
// Build: cmake/ndk-build → libllama_jni.so

#include <jni.h>
#include <llama.h>
#include <gguf.h>
#include <atomic>
#include <thread>
#include <mutex>
#include <unordered_map>
#include <cstring>
#include <cstdint>
#include <vector>
#include <string>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "LlamaJni", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LlamaJni", __VA_ARGS__)

namespace {

struct LlamaJniContext {
    llama_model*   model    = nullptr;
    llama_context* ctx     = nullptr;
    llama_sampler* smpl     = nullptr;
    llama_token    id_eos   = -1;
    llama_token    id_pad   = -1;
    int            n_ctx    = 0;
    int            n_used   = 0;
    std::string    chat_tpl;
    std::atomic<bool> abort{false};
    std::thread*   worker   = nullptr;
    std::mutex     mu;
    bool           running  = false;
};

std::unordered_map<jlong, LlamaJniContext*>& ctxMap() {
    static std::unordered_map<jlong, LlamaJniContext*> m;
    return m;
}
std::mutex& mapMu() { static std::mutex m; return m; }

// Read chat_template from GGUF metadata using gguf_init_from_file(params)
std::string readChatTemplate(const char* path) {
    struct gguf_init_params params = {};
    params.no_alloc = true;
    params.ctx = nullptr;
    struct gguf_context* g = gguf_init_from_file(path, params);
    if (!g) { LOGE("gguf_init_from_file failed: %s", path); return ""; }
    int ki = gguf_find_key(g, "tokenizer.chat_template");
    std::string t;
    if (ki >= 0) {
        const char* v = gguf_get_val_str(g, ki);
        if (v) t = v;
    }
    gguf_free(g);
    LOGI("chat_template: %s", t.empty() ? "(none)" : "found");
    return t;
}

// Tokenize using llama_tokenize(vocab, ...) — vocab from llama_model_get_vocab
std::vector<llama_token> tokenizeWithVocab(const llama_vocab* vocab, const std::string& text) {
    std::vector<llama_token> result;
    int n = llama_tokenize(vocab, text.c_str(), (int)text.size(), nullptr, 0, true, true);
    if (n <= 0) return result;
    result.resize(n);
    llama_tokenize(vocab, text.c_str(), (int)text.size(), result.data(), n, true, true);
    return result;
}

// Convert token → text piece using llama_token_to_piece(vocab, ...)
bool tokenToPiece(const llama_vocab* vocab, llama_token tok, std::string& out) {
    char buf[256];
    int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf) - 1, 0, true);
    if (n > 0) { out.assign(buf, n); return true; }
    return false;
}

// Build sampler chain: dist → top-k → top-p → temp → repeat penalty
llama_sampler* buildSampler(const llama_vocab* vocab) {
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler* chain = llama_sampler_chain_init(sparams);
    if (!chain) return nullptr;
    // no grammar (pass NULL vocab — grammar disabled when grammar_str is NULL)
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(chain, llama_sampler_init_penalties(40, 1.1f, 0.0f, 0.0f));
    return chain;
}

} // anonymous namespace

// ── JNI Methods ────────────────────────────────────────────────────────────────

extern "C" {

// JNIEXPORT + JNICALL already in registration array below
static jlong JNICALL nativeInitEngine(JNIEnv* env, jclass, jstring jPath) {
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    if (!path) return 0L;

    llama_backend_init(); // call once per process

    auto* ctx = new LlamaJniContext();

    // 1. Load model
    auto mparams = llama_model_default_params();
    mparams.n_gpu_layers = 99;       // max GPU offload (Vulkan/CPU)
    mparams.use_mmap     = true;
    mparams.use_mlock    = false;
    mparams.progress_callback = nullptr;
    ctx->model = llama_model_load_from_file(path, mparams);
    if (!ctx->model) {
        LOGE("llama_model_load_from_file failed: %s", path);
        env->ReleaseStringUTFChars(jPath, path);
        delete ctx;
        return 0L;
    }

    // 2. Read chat_template from GGUF metadata
    ctx->chat_tpl = readChatTemplate(path);

    // 3. Create context
    auto cparams = llama_context_default_params();
    ctx->n_ctx = llama_model_n_ctx_train(ctx->model);
    if (ctx->n_ctx == 0) ctx->n_ctx = 4096;
    cparams.n_ctx   = ctx->n_ctx;
    cparams.n_batch = 512;
    cparams.no_perf = true;
    // use as many threads as available - 1
    int nth = (int)std::thread::hardware_concurrency();
    if (nth > 1) nth--;
    cparams.n_threads        = nth;
    cparams.n_threads_batch  = nth;
    ctx->ctx = llama_init_from_model(ctx->model, cparams);
    if (!ctx->ctx) {
        LOGE("llama_init_from_model failed");
        llama_free_model(ctx->model);
        env->ReleaseStringUTFChars(jPath, path);
        delete ctx;
        return 0L;
    }

    // 4. Build sampler chain
    ctx->smpl = buildSampler(llama_model_get_vocab(ctx->model));
    if (!ctx->smpl) {
        LOGE("sampler chain init failed");
        llama_free(ctx->ctx);
        llama_free_model(ctx->model);
        env->ReleaseStringUTFChars(jPath, path);
        delete ctx;
        return 0L;
    }

    // 5. EOS / PAD tokens
    const llama_vocab* vocab = llama_model_get_vocab(ctx->model);
    ctx->id_eos = llama_vocab_eos(vocab);
    ctx->id_pad = llama_vocab_pad(vocab);
    LOGI("init done: n_ctx=%d nth=%d id_eos=%d id_pad=%d",
         ctx->n_ctx, nth, (int)ctx->id_eos, (int)ctx->id_pad);

    env->ReleaseStringUTFChars(jPath, path);
    jlong ptr = reinterpret_cast<jlong>(ctx);
    { std::lock_guard<std::mutex> lg(mapMu()); ctxMap()[ptr] = ctx; }
    return ptr;
}

static void JNICALL nativeFree(JNIEnv*, jclass, jlong jptr) {
    auto* ctx = reinterpret_cast<LlamaJniContext*>(jptr);
    if (!ctx) return;
    ctx->abort.store(true);
    if (ctx->worker && ctx->worker->joinable()) {
        ctx->worker->join();
        delete ctx->worker;
        ctx->worker = nullptr;
    }
    if (ctx->smpl)  { llama_sampler_free(ctx->smpl);  ctx->smpl  = nullptr; }
    if (ctx->ctx)   { llama_free(ctx->ctx);           ctx->ctx   = nullptr; }
    if (ctx->model) { llama_free_model(ctx->model);  ctx->model = nullptr; }
    { std::lock_guard<std::mutex> lg(mapMu()); ctxMap().erase(jptr); }
    delete ctx;
    LOGI("engine freed");
}

static jstring JNICALL nativeGetChatTemplate(JNIEnv* env, jclass, jlong jptr) {
    auto* ctx = reinterpret_cast<LlamaJniContext*>(jptr);
    if (!ctx) return env->NewStringUTF("");
    return env->NewStringUTF(ctx->chat_tpl.c_str());
}

// Inference runs on a background thread; tokens stream back via JNI callback
static void JNICALL nativeGenerateStream(JNIEnv* env, jclass, jlong jptr,
                                        jstring jPrompt, jint jMaxTokens, jobject jCallback) {
    auto* ctx = reinterpret_cast<LlamaJniContext*>(jptr);
    if (!ctx) return;

    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    if (!prompt) return;

    jobject callback = env->NewGlobalRef(jCallback);

    {
        std::lock_guard<std::mutex> lg(ctx->mu);
        if (ctx->running) {
            env->ReleaseStringUTFChars(jPrompt, prompt);
            env->DeleteGlobalRef(callback);
            return;
        }
        ctx->running = true;
        ctx->abort.store(false);
    }

    ctx->worker = new std::thread([env, ctx, prompt, jMaxTokens, callback]() {
        // Attach this thread to JVM for JNI calls
        JavaVM* jvm = nullptr;
        env->GetJavaVM(&jvm);
        JNIEnv* tenv = nullptr;
        bool attached = false;
        if (jvm->GetEnv((void**)&tenv, JNI_VERSION_1_6) == JNI_EDETACHED) {
            jvm->AttachCurrentThreadAsDaemon((JNIEnv**)&tenv, nullptr);
            attached = true;
        }
        if (!tenv) { // fallback: use parent env if attach failed
            tenv = env;
        }

        jclass  cls      = tenv->GetObjectClass(callback);
        jmethodID midTok  = tenv->GetMethodID(cls, "onToken",  "(Ljava/lang/String;)V");
        jmethodID midDone = tenv->GetMethodID(cls, "onDone",   "(Ljava/lang/String;)V");
        jmethodID midErr  = tenv->GetMethodID(cls, "onError",  "(Ljava/lang/String;)V");

        const llama_vocab* vocab = llama_model_get_vocab(ctx->model);
        std::string accumulated;
        bool ok = false;

        do {
            // Tokenize prompt
            auto promptTokens = tokenizeWithVocab(vocab, prompt);
            if (promptTokens.empty()) {
                LOGE("prompt tokenize failed");
                break;
            }
            if ((int)promptTokens.size() + jMaxTokens > ctx->n_ctx) {
                LOGE("context overflow: prompt=%d max=%d n_ctx=%d",
                     (int)promptTokens.size(), jMaxTokens, ctx->n_ctx);
                break;
            }

            // Pre-fill prompt (batch decode)
            {
                llama_batch batch = llama_batch_get_one(promptTokens.data(), (int)promptTokens.size());
                // set sequence ids
                for (int i = 0; i < (int)promptTokens.size(); ++i) {
                    batch.pos[i]        = ctx->n_used + i;
                    batch.n_seq_id[i]   = 1;
                    batch.seq_id[i][0]  = 0;
                }
                if (llama_decode(ctx->ctx, batch) != 0) {
                    LOGE("prompt decode failed");
                    break;
                }
                ctx->n_used += (int)promptTokens.size();
            }

            // Sampling loop
            int generated = 0;
            while (generated < jMaxTokens && !ctx->abort.load()) {
                llama_token newTok = llama_sampler_sample(ctx->smpl, ctx->ctx, -1);

                if (llama_vocab_is_eog(vocab, newTok) || newTok == ctx->id_pad) {
                    break;
                }

                // Decode this token
                llama_batch batch = llama_batch_get_one(&newTok, 1);
                batch.pos[0]       = ctx->n_used;
                batch.n_seq_id[0]  = 1;
                batch.seq_id[0][0] = 0;
                if (llama_decode(ctx->ctx, batch) != 0) {
                    LOGE("decode failed at token %d", generated);
                    break;
                }
                ctx->n_used++;

                // Convert token to text
                std::string piece;
                if (tokenToPiece(vocab, newTok, piece)) {
                    accumulated += piece;
                    jstring jpiece = tenv->NewStringUTF(piece.c_str());
                    tenv->CallVoidMethod(callback, midTok, jpiece);
                    tenv->DeleteLocalRef(jpiece);
                }
                generated++;
            }
            ok = true;
        } while (false);

        jstring jout = tenv->NewStringUTF(accumulated.c_str());
        if (ok) {
            tenv->CallVoidMethod(callback, midDone, jout);
        } else {
            jstring jerr = tenv->NewStringUTF("inference error");
            tenv->CallVoidMethod(callback, midErr, jerr);
            tenv->DeleteLocalRef(jerr);
        }
        tenv->DeleteLocalRef(jout);
        tenv->DeleteGlobalRef(callback);

        if (attached) {
            jvm->DetachCurrentThread();
        }

        // Reset context state
        {
            std::lock_guard<std::mutex> lg(ctx->mu);
            ctx->running = false;
            ctx->n_used  = 0;
        }
    });

    env->ReleaseStringUTFChars(jPrompt, prompt);
}

static void JNICALL nativeAbort(JNIEnv*, jclass, jlong jptr) {
    auto* ctx = reinterpret_cast<LlamaJniContext*>(jptr);
    if (!ctx) return;
    LOGI("abort");
    ctx->abort.store(true);
}

// JNI_OnLoad: register native methods
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* jvm, void*) {
    JNIEnv* env = nullptr;
    if (jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    const char* cls = "com/aiyougame/companion/llm/LlamaEngineImpl";
    jclass c = env->FindClass(cls);
    if (!c) {
        __android_log_print(ANDROID_LOG_ERROR,"LlamaJni","FindClass: %s", cls);
        return JNI_ERR;
    }
    static const JNINativeMethod m[] = {
        {"nativeInitEngine",       "(Ljava/lang/String;)J",                    (void*)nativeInitEngine},
        {"nativeFree",             "(J)V",                                      (void*)nativeFree},
        {"nativeGetChatTemplate",  "(J)Ljava/lang/String;",                    (void*)nativeGetChatTemplate},
        {"nativeGenerateStream",   "(JLjava/lang/String;ILjava/lang/Object;)V", (void*)nativeGenerateStream},
        {"nativeAbort",            "(J)V",                                      (void*)nativeAbort},
    };
    if (env->RegisterNatives(c, m, 5) < 0) {
        __android_log_print(ANDROID_LOG_ERROR,"LlamaJni","RegisterNatives failed");
        return JNI_ERR;
    }
    env->DeleteLocalRef(c);
    __android_log_print(ANDROID_LOG_INFO,"LlamaJni","JNI_OnLoad OK — 5 methods registered");
    return JNI_VERSION_1_6;
}

} // extern "C"
