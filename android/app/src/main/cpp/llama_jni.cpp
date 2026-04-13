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

// Multimodal (mtmd) support from llama.cpp tools/mtmd
#include "mtmd.h"

namespace {

struct LlamaJniContext {
    llama_model*   model    = nullptr;
    llama_context* ctx     = nullptr;
    llama_sampler* smpl     = nullptr;
    mtmd_context*  mtmd     = nullptr;
    llama_token    id_eos   = -1;
    llama_token    id_pad   = -1;
    int            n_ctx    = 0;
    llama_pos      n_past   = 0;
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

// ── mtmd decode helpers (trimmed from tools/mtmd/mtmd-helper.cpp) ─────────────
// We inline only what we need so we don't depend on external decoders (miniaudio/stb).

struct DecodeEmbdBatch {
    int n_pos_per_embd;
    int n_mmproj_embd;
    std::vector<llama_pos>      pos;
    std::vector<llama_pos>      pos_view; // used by mrope
    std::vector<int32_t>        n_seq_id;
    std::vector<llama_seq_id>   seq_id_0;
    std::vector<llama_seq_id *> seq_ids;
    std::vector<int8_t>         logits;
    llama_batch batch;

    DecodeEmbdBatch(float * embd, int32_t n_tokens, int n_pos_per_embd, int n_mmproj_embd)
        : n_pos_per_embd(n_pos_per_embd), n_mmproj_embd(n_mmproj_embd) {
        pos     .resize(n_tokens * n_pos_per_embd);
        n_seq_id.resize(n_tokens);
        seq_ids .resize(n_tokens + 1);
        logits  .resize(n_tokens);
        seq_id_0.resize(1);
        seq_ids[n_tokens] = nullptr;
        batch = {
            /*n_tokens       =*/ n_tokens,
            /*tokens         =*/ nullptr,
            /*embd           =*/ embd,
            /*pos            =*/ pos.data(),
            /*n_seq_id       =*/ n_seq_id.data(),
            /*seq_id         =*/ seq_ids.data(),
            /*logits         =*/ logits.data(),
        };
    }

    void set_position_normal(llama_pos pos_0, llama_seq_id seq_id) {
        seq_id_0[0] = seq_id;
        for (int i = 0; i < batch.n_tokens; i++) {
            batch.pos     [i] = pos_0 + i;
            batch.n_seq_id[i] = 1;
            batch.seq_id  [i] = seq_id_0.data();
            batch.logits  [i] = false;
        }
    }

    void set_position_mrope_2d(llama_pos pos_0, int nx, int ny, llama_seq_id seq_id) {
        // M-RoPE layout: 4 * n_tokens positions
        seq_id_0[0] = seq_id;
        for (int y = 0; y < ny; y++) {
            for (int x = 0; x < nx; x++) {
                int i = y * nx + x;
                pos[i                     ] = pos_0;
                pos[i + batch.n_tokens    ] = pos_0 + y;
                pos[i + batch.n_tokens * 2] = pos_0 + x;
                pos[i + batch.n_tokens * 3] = 0;
            }
        }
        for (int i = 0; i < batch.n_tokens; i++) {
            batch.n_seq_id[i] = 1;
            batch.seq_id  [i] = seq_id_0.data();
            batch.logits  [i] = false;
        }
    }

    llama_batch get_view(int offset, int n_tokens) {
        llama_pos * pos_ptr;
        pos_view.clear();
        pos_view.reserve(n_tokens * n_pos_per_embd);
        if (n_pos_per_embd > 1) {
            for (int i = 0; i < n_pos_per_embd; i++) {
                size_t src_idx = i * batch.n_tokens + offset;
                pos_view.insert(pos_view.end(),
                    pos.data() + src_idx,
                    pos.data() + src_idx + n_tokens);
            }
            pos_ptr = pos_view.data();
        } else {
            pos_ptr = pos.data() + offset;
        }
        return {
            /*n_tokens       =*/ n_tokens,
            /*tokens         =*/ nullptr,
            /*embd           =*/ batch.embd     + offset * n_mmproj_embd,
            /*pos            =*/ pos_ptr,
            /*n_seq_id       =*/ batch.n_seq_id + offset,
            /*seq_id         =*/ batch.seq_id   + offset,
            /*logits         =*/ batch.logits   + offset,
        };
    }
};

static int32_t decodeImageChunk(
        mtmd_context * mctx,
        llama_context * lctx,
        const mtmd_input_chunk * chunk,
        float * encoded_embd,
        llama_pos n_past,
        llama_seq_id seq_id,
        int32_t n_batch,
        llama_pos * new_n_past) {
    const llama_model * model = llama_get_model(lctx);
    const int n_mmproj_embd = llama_model_n_embd_inp(model);
    const int n_pos_per_embd = mtmd_decode_use_mrope(mctx) ? 4 : 1;
    const int32_t n_tokens = (int32_t)mtmd_input_chunk_get_n_tokens(chunk);
    if (n_tokens <= 0) return 1;

    DecodeEmbdBatch beb(encoded_embd, n_tokens, n_pos_per_embd, n_mmproj_embd);

    if (mtmd_decode_use_mrope(mctx)) {
        const auto image_tokens = mtmd_input_chunk_get_tokens_image(chunk);
        if (!image_tokens) return 1;
        const int nx = (int)mtmd_image_tokens_get_nx(image_tokens);
        const int ny = (int)mtmd_image_tokens_get_ny(image_tokens);
        beb.set_position_mrope_2d(n_past, nx, ny, seq_id);
    } else {
        beb.set_position_normal(n_past, seq_id);
    }

    if (mtmd_decode_use_non_causal(mctx)) {
        llama_set_causal_attn(lctx, false);
    }

    int32_t i_batch = 0;
    const int32_t n_img_batches = (n_tokens + n_batch - 1) / n_batch;
    while (i_batch < n_img_batches) {
        const int pos_offset = i_batch * n_batch;
        const int n_tokens_batch = std::min(n_batch, n_tokens - pos_offset);
        llama_batch view = beb.get_view(pos_offset, n_tokens_batch);
        if (llama_decode(lctx, view) != 0) {
            if (mtmd_decode_use_non_causal(mctx)) {
                llama_set_causal_attn(lctx, true);
            }
            return 1;
        }
        i_batch++;
    }

    n_past += (llama_pos)mtmd_input_chunk_get_n_pos(chunk);
    *new_n_past = n_past;

    if (mtmd_decode_use_non_causal(mctx)) {
        llama_set_causal_attn(lctx, true);
    }
    return 0;
}

static int32_t evalChunks(
        mtmd_context * mctx,
        llama_context * lctx,
        const mtmd_input_chunks * chunks,
        llama_pos n_past,
        llama_seq_id seq_id,
        int32_t n_batch,
        llama_pos * new_n_past) {
    llama_batch text_batch = llama_batch_init(n_batch, 0, 1);

    const size_t n_chunks = mtmd_input_chunks_size(chunks);
    for (size_t i = 0; i < n_chunks; i++) {
        const mtmd_input_chunk * chunk = mtmd_input_chunks_get(chunks, i);
        const auto t = mtmd_input_chunk_get_type(chunk);
        if (t == MTMD_INPUT_CHUNK_TYPE_TEXT) {
            size_t n_tokens = 0;
            const llama_token * toks = mtmd_input_chunk_get_tokens_text(chunk, &n_tokens);
            size_t k = 0;
            while (k < n_tokens) {
                const int32_t n = (int32_t)std::min((size_t)n_batch, n_tokens - k);
                // fill batch
                text_batch.n_tokens = n;
                text_batch.token = (llama_token *)(toks + k);
                for (int32_t j = 0; j < n; j++) {
                    text_batch.pos[j] = n_past + (llama_pos)j;
                    text_batch.n_seq_id[j] = 1;
                    text_batch.seq_id[j][0] = seq_id;
                    text_batch.logits[j] = false;
                }
                if (llama_decode(lctx, text_batch) != 0) {
                    llama_batch_free(text_batch);
                    return 1;
                }
                n_past += (llama_pos)n;
                k += (size_t)n;
            }
        } else if (t == MTMD_INPUT_CHUNK_TYPE_IMAGE) {
            if (mtmd_encode_chunk(mctx, chunk) != 0) {
                llama_batch_free(text_batch);
                return 1;
            }
            float * embd = mtmd_get_output_embd(mctx);
            if (!embd) {
                llama_batch_free(text_batch);
                return 1;
            }
            llama_pos np = 0;
            if (decodeImageChunk(mctx, lctx, chunk, embd, n_past, seq_id, n_batch, &np) != 0) {
                llama_batch_free(text_batch);
                return 1;
            }
            n_past = np;
        } else {
            // audio not supported in this app path
            llama_batch_free(text_batch);
            return 1;
        }
    }

    llama_batch_free(text_batch);
    *new_n_past = n_past;
    return 0;
}

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
static jlong JNICALL nativeInitEngine(JNIEnv* env, jclass, jstring jModelPath, jstring jMmprojPath) {
    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    const char* mmprojPath = jMmprojPath ? env->GetStringUTFChars(jMmprojPath, nullptr) : nullptr;
    if (!modelPath) return 0L;

    llama_backend_init(); // call once per process

    auto* ctx = new LlamaJniContext();

    // 1. Load model
    auto mparams = llama_model_default_params();
    mparams.n_gpu_layers = 99;       // max GPU offload (Vulkan/CPU)
    mparams.use_mmap     = true;
    mparams.use_mlock    = false;
    mparams.progress_callback = nullptr;
    ctx->model = llama_model_load_from_file(modelPath, mparams);
    if (!ctx->model) {
        LOGE("llama_model_load_from_file failed: %s", modelPath);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        if (mmprojPath) env->ReleaseStringUTFChars(jMmprojPath, mmprojPath);
        delete ctx;
        return 0L;
    }

    // 2. Read chat_template from GGUF metadata
    ctx->chat_tpl = readChatTemplate(modelPath);

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
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        if (mmprojPath) env->ReleaseStringUTFChars(jMmprojPath, mmprojPath);
        delete ctx;
        return 0L;
    }

    // 4. Build sampler chain
    ctx->smpl = buildSampler(llama_model_get_vocab(ctx->model));
    if (!ctx->smpl) {
        LOGE("sampler chain init failed");
        llama_free(ctx->ctx);
        llama_free_model(ctx->model);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        if (mmprojPath) env->ReleaseStringUTFChars(jMmprojPath, mmprojPath);
        delete ctx;
        return 0L;
    }

    // 5. Init mtmd (multimodal projector), optional
    if (mmprojPath && std::strlen(mmprojPath) > 0) {
        mtmd_context_params mp = mtmd_context_params_default();
        mp.use_gpu = true;
        mp.print_timings = false;
        mp.n_threads = nth;
        mp.media_marker = mtmd_default_marker();

        ctx->mtmd = mtmd_init_from_file(mmprojPath, ctx->model, mp);
        if (ctx->mtmd) {
            LOGI("mtmd init OK (vision=%d audio=%d)", (int)mtmd_support_vision(ctx->mtmd), (int)mtmd_support_audio(ctx->mtmd));
        } else {
            LOGE("mtmd init failed, continuing as text-only (mmproj=%s)", mmprojPath);
        }
    }

    // 5. EOS / PAD tokens
    const llama_vocab* vocab = llama_model_get_vocab(ctx->model);
    ctx->id_eos = llama_vocab_eos(vocab);
    ctx->id_pad = llama_vocab_pad(vocab);
    LOGI("init done: n_ctx=%d nth=%d id_eos=%d id_pad=%d",
         ctx->n_ctx, nth, (int)ctx->id_eos, (int)ctx->id_pad);
    LOGI("llama.cpp backend ready");

    env->ReleaseStringUTFChars(jModelPath, modelPath);
    if (mmprojPath) env->ReleaseStringUTFChars(jMmprojPath, mmprojPath);
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
    if (ctx->mtmd)  { mtmd_free(ctx->mtmd);           ctx->mtmd  = nullptr; }
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

    // Get JavaVM* from the calling thread's JNIEnv.
    // This is safe because JNIEnv is thread-local but JavaVM* is global.
    JavaVM* jvm = nullptr;
    env->GetJavaVM(&jvm);

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

    // Capture thread-safe data: JavaVM*, ctx ptr, prompt chars, maxTokens, callback ref.
    // Do NOT capture 'env' — it is thread-local to the calling thread and invalid here.
    ctx->worker = new std::thread([jvm, ctx, prompt, jMaxTokens, callback]() {
        JNIEnv* tenv = nullptr;
        bool attached = false;
        jint stat = jvm->GetEnv((void**)&tenv, JNI_VERSION_1_6);
        if (stat == JNI_EDETACHED) {
            if (jvm->AttachCurrentThreadAsDaemon(&tenv, nullptr) != JNI_OK) {
                // Failed to attach — cannot make JNI calls, terminate thread safely.
                LOGE("JNI AttachCurrentThread failed");
                { std::lock_guard<std::mutex> lg(ctx->mu); ctx->running = false; }
                return;
            }
            attached = true;
        } else if (stat != JNI_OK || !tenv) {
            LOGE("JNI GetEnv returned unexpected status: %d", stat);
            { std::lock_guard<std::mutex> lg(ctx->mu); ctx->running = false; }
            return;
        }

        jclass  cls      = tenv->GetObjectClass(callback);
        jmethodID midTok  = tenv->GetMethodID(cls, "onToken",  "(Ljava/lang/String;)V");
        jmethodID midDone = tenv->GetMethodID(cls, "onDone",   "(Ljava/lang/String;)V");
        jmethodID midErr  = tenv->GetMethodID(cls, "onError",  "(Ljava/lang/String;)V");

        const llama_vocab* vocab = llama_model_get_vocab(ctx->model);
        std::string accumulated;
        bool ok = false;

        do {
            // Reset state for single-turn generation
            ctx->n_past = 0;
            llama_memory_clear(llama_get_memory(ctx->ctx), true);
            llama_sampler_reset(ctx->smpl);

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
                    batch.pos[i]        = ctx->n_past + i;
                    batch.n_seq_id[i]   = 1;
                    batch.seq_id[i][0]  = 0;
                }
                if (llama_decode(ctx->ctx, batch) != 0) {
                    LOGE("prompt decode failed");
                    break;
                }
                ctx->n_past += (llama_pos)promptTokens.size();
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
                batch.pos[0]       = ctx->n_past;
                batch.n_seq_id[0]  = 1;
                batch.seq_id[0][0] = 0;
                if (llama_decode(ctx->ctx, batch) != 0) {
                    LOGE("decode failed at token %d", generated);
                    break;
                }
                ctx->n_past++;

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
            ctx->n_past  = 0;
        }
    });

    env->ReleaseStringUTFChars(jPrompt, prompt);
}

// Prompt + one image attachment (RGB bytes) encoded via mtmd.
static void JNICALL nativeGenerateStreamWithMedia(JNIEnv* env, jclass, jlong jptr,
                                                 jstring jPrompt, jbyteArray jRgb,
                                                 jint jWidth, jint jHeight,
                                                 jint jMaxTokens, jobject jCallback) {
    auto* ctx = reinterpret_cast<LlamaJniContext*>(jptr);
    if (!ctx || !ctx->mtmd) return;

    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    if (!prompt) return;

    const int width = (int)jWidth;
    const int height = (int)jHeight;
    if (width <= 0 || height <= 0) {
        env->ReleaseStringUTFChars(jPrompt, prompt);
        return;
    }

    jsize rgbLen = env->GetArrayLength(jRgb);
    jbyte* rgbBytes = env->GetByteArrayElements(jRgb, nullptr);
    const int64_t expected = (int64_t)width * (int64_t)height * 3;
    if (!rgbBytes || rgbLen != expected) {
        env->ReleaseStringUTFChars(jPrompt, prompt);
        if (rgbBytes) env->ReleaseByteArrayElements(jRgb, rgbBytes, JNI_ABORT);
        return;
    }

    jobject callback = env->NewGlobalRef(jCallback);
    JavaVM* jvm = nullptr;
    env->GetJavaVM(&jvm);

    {
        std::lock_guard<std::mutex> lg(ctx->mu);
        if (ctx->running) {
            env->ReleaseByteArrayElements(jRgb, rgbBytes, JNI_ABORT);
            env->ReleaseStringUTFChars(jPrompt, prompt);
            env->DeleteGlobalRef(callback);
            return;
        }
        ctx->running = true;
        ctx->abort.store(false);
    }

    // Copy RGB into std::vector so we can release JNI array immediately
    std::vector<unsigned char> rgb((unsigned char*)rgbBytes, (unsigned char*)rgbBytes + rgbLen);
    env->ReleaseByteArrayElements(jRgb, rgbBytes, JNI_ABORT);

    ctx->worker = new std::thread([jvm, ctx, prompt, jMaxTokens, callback, width, height, rgb = std::move(rgb)]() mutable {
        JNIEnv* tenv = nullptr;
        bool attached = false;
        jint stat = jvm->GetEnv((void**)&tenv, JNI_VERSION_1_6);
        if (stat == JNI_EDETACHED) {
            if (jvm->AttachCurrentThreadAsDaemon(&tenv, nullptr) != JNI_OK) {
                LOGE("JNI AttachCurrentThread failed");
                { std::lock_guard<std::mutex> lg(ctx->mu); ctx->running = false; }
                return;
            }
            attached = true;
        } else if (stat != JNI_OK || !tenv) {
            LOGE("JNI GetEnv returned unexpected status: %d", stat);
            { std::lock_guard<std::mutex> lg(ctx->mu); ctx->running = false; }
            return;
        }

        jclass  cls      = tenv->GetObjectClass(callback);
        jmethodID midTok  = tenv->GetMethodID(cls, "onToken",  "(Ljava/lang/String;)V");
        jmethodID midDone = tenv->GetMethodID(cls, "onDone",   "(Ljava/lang/String;)V");
        jmethodID midErr  = tenv->GetMethodID(cls, "onError",  "(Ljava/lang/String;)V");

        const llama_vocab* vocab = llama_model_get_vocab(ctx->model);
        std::string accumulated;
        bool ok = false;

        do {
            ctx->n_past = 0;
            llama_memory_clear(llama_get_memory(ctx->ctx), true);
            llama_sampler_reset(ctx->smpl);

            // Ensure prompt contains the marker once
            std::string p(prompt);
            const char * marker = mtmd_default_marker();
            if (p.find(marker) == std::string::npos) {
                p = std::string(marker) + "\n" + p;
            }

            mtmd_input_text text;
            text.text = p.c_str();
            text.add_special = true;
            text.parse_special = true;

            mtmd_bitmap * bmp = mtmd_bitmap_init((uint32_t)width, (uint32_t)height, rgb.data());
            if (!bmp) {
                LOGE("mtmd_bitmap_init failed");
                break;
            }

            const mtmd_bitmap * bmps[1] = { bmp };
            mtmd_input_chunks * chunks = mtmd_input_chunks_init();
            int32_t tres = mtmd_tokenize(ctx->mtmd, chunks, &text, bmps, 1);
            mtmd_bitmap_free(bmp);
            if (tres != 0) {
                LOGE("mtmd_tokenize failed: %d", (int)tres);
                mtmd_input_chunks_free(chunks);
                break;
            }

            llama_pos new_n_past = 0;
            int32_t eres = evalChunks(ctx->mtmd, ctx->ctx, chunks, ctx->n_past, 0, 512, &new_n_past);
            mtmd_input_chunks_free(chunks);
            if (eres != 0) {
                LOGE("evalChunks failed: %d", (int)eres);
                break;
            }
            ctx->n_past = new_n_past;

            int generated = 0;
            while (generated < jMaxTokens && !ctx->abort.load()) {
                llama_token newTok = llama_sampler_sample(ctx->smpl, ctx->ctx, -1);
                if (llama_vocab_is_eog(vocab, newTok) || newTok == ctx->id_pad) break;

                llama_batch batch = llama_batch_get_one(&newTok, 1);
                batch.pos[0]       = ctx->n_past;
                batch.n_seq_id[0]  = 1;
                batch.seq_id[0][0] = 0;
                if (llama_decode(ctx->ctx, batch) != 0) {
                    LOGE("decode failed at token %d", generated);
                    break;
                }
                ctx->n_past++;

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
            jstring jerr = tenv->NewStringUTF("multimodal inference error");
            tenv->CallVoidMethod(callback, midErr, jerr);
            tenv->DeleteLocalRef(jerr);
        }
        tenv->DeleteLocalRef(jout);
        tenv->DeleteGlobalRef(callback);

        if (attached) {
            jvm->DetachCurrentThread();
        }

        {
            std::lock_guard<std::mutex> lg(ctx->mu);
            ctx->running = false;
            ctx->n_past  = 0;
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
        {"nativeInitEngine",       "(Ljava/lang/String;Ljava/lang/String;)J",  (void*)nativeInitEngine},
        {"nativeFree",             "(J)V",                                      (void*)nativeFree},
        {"nativeGetChatTemplate",  "(J)Ljava/lang/String;",                    (void*)nativeGetChatTemplate},
        {"nativeGenerateStream",   "(JLjava/lang/String;ILjava/lang/Object;)V", (void*)nativeGenerateStream},
        {"nativeGenerateStreamWithMedia", "(JLjava/lang/String;[BIIILjava/lang/Object;)V", (void*)nativeGenerateStreamWithMedia},
        {"nativeAbort",            "(J)V",                                      (void*)nativeAbort},
    };
    if (env->RegisterNatives(c, m, 6) < 0) {
        __android_log_print(ANDROID_LOG_ERROR,"LlamaJni","RegisterNatives failed");
        return JNI_ERR;
    }
    env->DeleteLocalRef(c);
    __android_log_print(ANDROID_LOG_INFO,"LlamaJni","JNI_OnLoad OK — 5 methods registered");
    return JNI_VERSION_1_6;
}

} // extern "C"
