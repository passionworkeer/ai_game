// whisper_jni.cpp — JNI bridge for whisper.cpp
// Phase 2: Voice input (P0-A9) native backend
// whisper.cpp API: whisper_init_from_file, whisper_full, whisper_full_get_segment_text
// Build: cmake/ndk-build → libwhisper_jni.so

#include <jni.h>
#include <whisper.h>
#include <cstring>
#include <cstdlib>
#include <cstdint>
#include <vector>
#include <string>
#include <mutex>
#include <thread>
#include <unordered_map>
#include <android/log.h>
#include <sys/stat.h>
#include <cmath>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "WhisperJni", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "WhisperJni", __VA_ARGS__)

namespace {

struct WhisperJniContext {
    struct whisper_context* ctx = nullptr;
    int n_threads = 1;
};

std::unordered_map<jlong, WhisperJniContext*>& ctxMap() {
    static std::unordered_map<jlong, WhisperJniContext*> m;
    return m;
}
std::mutex& mapMu() { static std::mutex m; return m; }

// ── WAV reader ──────────────────────────────────────────────────────────────
// Reads a 16kHz 16-bit mono PCM WAV file into a float vector (range -1.0..+1.0).
// Returns empty vector on error.
std::vector<float> readWavPcm16(const char* path) {
    std::vector<float> result;

    FILE* fp = fopen(path, "rb");
    if (!fp) { LOGE("Cannot open WAV: %s", path); return result; }

    // RIFF header
    char riff[12];
    if (fread(riff, 1, 12, fp) != 12) { fclose(fp); return result; }
    if (std::memcmp(riff, "RIFF", 4) != 0 || std::memcmp(riff + 8, "WAVE", 4) != 0) {
        LOGE("Not a valid WAV file: %s", path);
        fclose(fp); return result;
    }

    bool found_fmt = false, found_data = false;
    int sample_rate = 16000;
    int num_channels = 1;
    int bits_per_sample = 16;
    int data_size = 0;

    // Read chunks
    char chunk[8];
    while (fread(chunk, 1, 8, fp) == 8) {
        int chunk_size = *reinterpret_cast<int*>(chunk + 4);
        // little-endian
        chunk_size = chunk[4] | (chunk[5] << 8) | (chunk[6] << 16) | (chunk[7] << 24);
        if (std::memcmp(chunk, "fmt ", 4) == 0) {
            if (chunk_size >= 16) {
                char fmt[16];
                if (fread(fmt, 1, 16, fp) == 16) {
                    int audio_fmt = fmt[0] | (fmt[1] << 8);
                    num_channels = fmt[2] | (fmt[3] << 8);
                    sample_rate = fmt[4] | (fmt[5] << 8) | (fmt[6] << 16) | (fmt[7] << 24);
                    bits_per_sample = fmt[14] | (fmt[15] << 8);
                    (void)audio_fmt;
                    found_fmt = true;
                }
                if (chunk_size > 16) fseek(fp, chunk_size - 16, SEEK_CUR);
            } else {
                fseek(fp, chunk_size, SEEK_CUR);
            }
        } else if (std::memcmp(chunk, "data", 4) == 0) {
            found_data = true;
            data_size = chunk_size;

            // Read PCM data
            std::vector<int16_t> pcm16;
            pcm16.resize(data_size / 2);
            size_t read = fread(pcm16.data(), 1, data_size, fp);
            (void)read;

            // Resample to 16kHz if needed (simple linear interpolation)
            std::vector<float> pcm_f32;
            if (sample_rate == 16000 && bits_per_sample == 16 && num_channels == 1) {
                pcm_f32.reserve(pcm16.size());
                for (int16_t s : pcm16) {
                    pcm_f32.push_back(static_cast<float>(s) / 32768.0f);
                }
            } else {
                // Downsample to 16kHz (simple decimation or averaging)
                int ratio = sample_rate / 16000;
                if (ratio < 1) ratio = 1;
                size_t out_size = (data_size / 2) / ratio;
                pcm_f32.reserve(out_size);
                for (size_t i = 0; i + ratio <= pcm16.size(); i += ratio) {
                    float sum = 0.0f;
                    for (int k = 0; k < ratio; ++k) {
                        sum += static_cast<float>(pcm16[i + k]);
                    }
                    pcm_f32.push_back(sum / (ratio * 32768.0f));
                }
                // Mix channels if stereo
                if (num_channels == 2) {
                    // already averaged, but this is simplified
                }
                LOGI("Resampled from %dHz to 16000: %zu -> %zu samples",
                     sample_rate, pcm16.size(), pcm_f32.size());
            }
            result.swap(pcm_f32);
            break;
        } else {
            // Skip unknown chunk (ensure word alignment)
            fseek(fp, (chunk_size + 1) & ~1, SEEK_CUR);
        }
    }

    fclose(fp);
    if (!found_fmt) LOGE("WAV fmt chunk not found: %s", path);
    if (!found_data) LOGE("WAV data chunk not found: %s", path);
    LOGI("WAV loaded: %zu samples @ 16000Hz", result.size());
    return result;
}

} // anonymous namespace

// ── JNI Methods ────────────────────────────────────────────────────────────────

extern "C" {

static jlong JNICALL nativeInitWhisper(JNIEnv* env, jclass, jstring jModelPath) {
    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    if (!modelPath) return 0L;

    LOGI("Loading Whisper model from: %s", modelPath);

    // Check file exists
    struct stat st;
    if (stat(modelPath, &st) != 0) {
        LOGE("Whisper model file not found: %s", modelPath);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        return 0L;
    }

    // Initialize whisper context using new params API
    auto* ctx = new WhisperJniContext();
    auto cparams = whisper_context_default_params();
    ctx->ctx = whisper_init_from_file_with_params(modelPath, cparams);
    if (!ctx->ctx) {
        LOGE("whisper_init_from_file failed: %s", modelPath);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        delete ctx;
        return 0L;
    }

    // Use all available threads (leave 1 for UI)
    int nth = 1;
    for (int t = std::thread::hardware_concurrency(); t > 1; t--) {
        nth = t - 1;
        break;
    }
    ctx->n_threads = (nth > 0) ? nth : 4;

    LOGI("Whisper model loaded OK: threads=%d", ctx->n_threads);

    env->ReleaseStringUTFChars(jModelPath, modelPath);
    jlong ptr = reinterpret_cast<jlong>(ctx);
    { std::lock_guard<std::mutex> lg(mapMu()); ctxMap()[ptr] = ctx; }
    return ptr;
}

static void JNICALL nativeFreeWhisper(JNIEnv*, jclass, jlong jptr) {
    auto* ctx = reinterpret_cast<WhisperJniContext*>(jptr);
    if (!ctx) return;
    if (ctx->ctx) {
        whisper_free(ctx->ctx);
        ctx->ctx = nullptr;
    }
    { std::lock_guard<std::mutex> lg(mapMu()); ctxMap().erase(jptr); }
    delete ctx;
    LOGI("Whisper freed");
}

static jstring JNICALL nativeTranscribe(JNIEnv* env, jclass, jlong jptr, jstring jAudioPath) {
    auto* ctx = reinterpret_cast<WhisperJniContext*>(jptr);
    if (!ctx || !ctx->ctx) return env->NewStringUTF("");

    const char* audioPath = env->GetStringUTFChars(jAudioPath, nullptr);
    if (!audioPath) return env->NewStringUTF("");

    // Read WAV PCM
    std::vector<float> pcm = readWavPcm16(audioPath);
    env->ReleaseStringUTFChars(jAudioPath, audioPath);

    if (pcm.empty()) {
        return env->NewStringUTF("");
    }

    // whisper_full_params — use GREEDY strategy
    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.n_threads = ctx->n_threads;
    wparams.offset_ms = 0;
    wparams.duration_ms = 0;
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.language = nullptr;       // auto-detect (English by default for tiny.en)
    wparams.no_context = true;
    wparams.max_len = 0;
    wparams.split_on_word = false;
    wparams.single_segment = false;
    wparams.print_special = false;
    wparams.debug_mode = false;
    wparams.translate = false;

    // Run transcription — whisper_full(ctx, params, samples, n_samples)
    if (whisper_full(ctx->ctx, wparams, pcm.data(), static_cast<int>(pcm.size())) != 0) {
        LOGE("whisper_full failed");
        return env->NewStringUTF("");
    }

    // Collect all segment text
    std::string transcript;
    const int n_segments = whisper_full_n_segments(ctx->ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char* text = whisper_full_get_segment_text(ctx->ctx, i);
        if (text) transcript += text;
    }

    LOGI("Transcription complete: %d segments, %zu chars", n_segments, transcript.size());
    return env->NewStringUTF(transcript.c_str());
}

// JNI_OnLoad: register native methods
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* jvm, void*) {
    JNIEnv* env = nullptr;
    if (jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    const char* cls = "com/aiyougame/companion/speech/WhisperEngine";
    jclass c = env->FindClass(cls);
    if (!c) {
        __android_log_print(ANDROID_LOG_ERROR, "WhisperJni", "FindClass: %s", cls);
        return JNI_ERR;
    }
    static const JNINativeMethod m[] = {
        {"nativeInitWhisper", "(Ljava/lang/String;)J",       (void*)nativeInitWhisper},
        {"nativeFreeWhisper", "(J)V",                       (void*)nativeFreeWhisper},
        {"nativeTranscribe",  "(JLjava/lang/String;)Ljava/lang/String;", (void*)nativeTranscribe},
    };
    if (env->RegisterNatives(c, m, 3) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "WhisperJni", "RegisterNatives failed");
        return JNI_ERR;
    }
    env->DeleteLocalRef(c);
    __android_log_print(ANDROID_LOG_INFO, "WhisperJni", "JNI_OnLoad OK — 3 methods registered");
    return JNI_VERSION_1_6;
}

} // extern "C"
