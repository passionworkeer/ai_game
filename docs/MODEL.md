# Gemma 4 模型接入指南

> 版本 v2.3 | 重建自 GitHub 核实（2026-04-09）
> **关键发现**：原文档引用的 `llama-android` AAR（JitPack）**不存在**，已核实两种真实可行方案。

---

## 接入方案对比

| 方案 | 难度 | NDK | 推荐度 |
|------|------|-----|--------|
| **A. llama.cpp CMake 构建** | 中 | 必须 | ✅ Phase 1 推荐 |
| **B. 预编译 AAR** | 低 | 否 | ✅ 快速验证 |
| **C. ONNX Runtime** | 低 | 否 | ⚠️ Phase 2 备选 |

---

## 方案 A：llama.cpp CMake 构建（Phase 1 推荐）

> 参考：`nerve-sparks/iris_android`（Android 原生 CMake 方案）

### 原理

不依赖任何第三方 AAR，直接把 llama.cpp 编译成 Android NDK 库（`libllama.so`），通过 JNI 封装成 Kotlin 接口。

### 依赖配置

```kotlin
// android/app/build.gradle.kts
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DANDROID_PLATFORM=android-24"
                arguments += "-DANDROID_ARM_MODE=arm"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1+"
        }
    }
}

dependencies {
    // llama.cpp 通过 CMake 编译，无需 Maven 依赖
}
```

### CMakeLists.txt

```cmake
# android/app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22)

project(llama_jni)

set(LAMA_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common/common.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common/base64.cpp
)

add_library(llama_jni SHARED
    llama_jni.cpp
    ${LLAMA_SOURCES}
)

target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common
)

target_link_libraries(llama_jni
    android
    log
    cpu_features
)
```

### JNI 封装层

```kotlin
// LlamaEngineImpl.kt
class LlamaEngineImpl(private val context: Context) : LlamaEngine {

    companion object {
        init {
            System.loadLibrary("llama_jni")
        }
    }

    // native 方法声明
    external fun nativeInitEngine(modelPath: String, nCtx: Int, nThreads: Int): Long
    external fun nativePredictStream(ptr: Long, input: String): Boolean
    external fun nativeFree(ptr: Long)
    external fun nativeGetChatTemplate(): String

    override fun initEngine(modelPath: String, systemPrompt: String): Long {
        val nThreads = Runtime.getRuntime().availableProcessors() - 1
        return nativeInitEngine(modelPath, 8192, nThreads)
    }

    override fun getChatTemplate(): String = nativeGetChatTemplate()

    override fun freeEngine(ptr: Long) {
        nativeFree(ptr)
    }

    override fun predictStream(ptr: Long, userMessage: String, callback: TokenCallback) {
        // 使用 Channel 在 C++ 子线程和 Kotlin 协程之间传递 token
        // 关键：callback 必须在主线程上回调（StateFlow.emit 线程安全）
        val tokenChannel = Channel<String>(Channel.UNLIMITED)

        // 启动 C++ 推理线程（非阻塞）
        Thread {
            nativePredictStream(ptr, userMessage)
        }.start()

        // 在协程中消费 token 并回调，假设在 IO 线程池执行
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                for (token in tokenChannel) {
                    // ⚠️ 注意：StateFlow.emit 在主线程安全，但 callback.onToken
                    // 可能需要切主线程，取决于 UI 层如何处理
                    callback.onToken(token)
                }
            } catch (e: Exception) {
                callback.onError(e.message ?: "推理异常")
            }
        }
    }

    // C++ 端需要在推理过程中调用此 JNI 方法将 token 写回 Kotlin
    // JNI 签名示例：
    // JNIEXPORT void JNICALL Java_..._onToken(JNIEnv* env, jclass, jlong nativePtr, jstring token) {
    //     auto* ctx = reinterpret_cast<LlamaJniContext*>(nativePtr);
    //     jboolean isCopy;
    //     const char* chars = env->GetStringUTFChars(token, &isCopy);
    //     ctx->tokenChannel->offer(std::string(chars));
    //     if (isCopy) env->ReleaseStringUTFChars(token, chars);
    // }
}
```

### 优点 / 缺点

| | |
|---|---|
| 优点 | 完全可控，Vulkan 加速，无第三方依赖 |
| 缺点 | 需要 NDK + WSL2 编译，首次搭建复杂 |

> **Windows 用户**：必须在 WSL2 中编译 llama.cpp，不要在 Windows 原生环境硬撑。

---

## 方案 B：预编译 AAR（快速验证用）

> 参考：`Siddhesh2377/ToolNeuron` 使用自定义 `gguf_lib-release.aar`

如果有人已经编译好现成的 AAR，可以直接导入：

```kotlin
// build.gradle.kts
dependencies {
    implementation(files("libs/gguf_lib-release.aar"))
}
```

**⚠️ 警告**：来源不明的 AAR 存在安全风险，仅限个人学习用途。Phase 1 正式开发推荐方案 A。

---

## 方案 C：ONNX Runtime（Phase 2 备选）

> 参考：`Siddhesh2377/ToolNeuron` 使用 ONNX Runtime 加载 GGUF

ONNX Runtime 支持直接加载 ONNX 格式模型，**无需 NDK**：

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
}
```

**缺点**：Gemma 需要转换为 ONNX 格式，且 ONNX Runtime 对 LLM 的优化不如 llama.cpp 原生实现。

---

## 模型量化与转换

### 已有模型（直接使用）

```
gemma-4-E4B-it-Q4_0.gguf   # ✅ 已存在（4.6GB Q4_0），Phase 1 使用；不打入 APK，App 首次启动从 [待配置CDN] 下载
```

### 可选：重新量化（更高质量）

```bash
# WSL2 / Linux / macOS
git clone https://github.com/ggerganov/llama.cpp.git
cd llama.cpp && pip install -r requirements.txt

# F16 转换
python examples/convert_hf_to_gguf.py \
    /path/to/gemma-4-E4B-it-UD-MLX-4bit-main/ \
    --outfile gemma-4-e4b-it-F16.gguf --outtype f16

# Q4_K_M 量化（质量优于 Q4_0，推荐替换现有文件）
# 注意：llama.cpp 量化质量排序 Q4_K_M ≈ Q5_K_M > Q4_0
#       Q4_0 是基础 4-bit 量化，同参数下质量最低
mkdir build && cd build
cmake .. -DLLAMA_BUILD_EXAMPLES=ON
cmake --build . --config Release
./bin/quantize \
    ../gemma-4-e4b-it-F16.gguf \
    ../gemma-4-e4b-it-Q4_K_M.gguf Q4_K_M

# 验证
./bin/llama-cli -m ../gemma-4-e4b-it-Q4_K_M.gguf \
    -p "你叫顾晨，是用户的男朋友。" -n 128 --temp 0.7
```

### 核心推理参数

```kotlin
val nbParams = LlamaParams(
    nCtx = 8192,        // 上下文窗口（旗舰机可开 16384）
    nThreads = Runtime.getRuntime().availableProcessors() - 1,
    nGpuLayers = 99,     // Vulkan 加速（无 Vulkan 则设为 0）
    nBatch = 128,
    temp = 0.7f,
    maxTokens = 512,
    repeatPenalty = 1.1f
)
```

---

## PRD 关键修正

| # | 内容 | 状态 |
|---|------|------|
| ERR-02 | **禁止硬编码 Prompt 模板**，必须从 GGUF metadata 动态读取 `tokenizer.chat_template` | ✅ 遵守 |
| ERR-01 | **Gemma 4 不支持音频输入**（仅图像+文本），Phase 1 语音不实现 | ✅ 遵守 |
| ERR-06 | **设备分级量化顺序写反**（Q4_0 质量低于 Q4_K_M，旗舰档反而用最差量化），已修正 | 🆕 新增 |
| **ERR-08** | **原 llama-android AAR 不存在**，改为 CMake 构建 | ✅ 修正 |

---

## 设备分级配置

> ⚠️ Phase 1 简化：三个档位统一使用 **Gemma E4B Q4_0**（现有文件，不重新量化）。
> Phase 2 扩展方向：低端降为 Gemma E2B Q4_K_S，中端 Gemma E2B Q4_K_M，高端 Gemma E4B Q4_K_M。
> （Qwen 1.8B 方案从 PRD 移除，Phase 1 专注 Android + Gemma，不引入额外模型复杂度）

| 档位 | 量化 | nCtx | 目标设备 | 预期速度 | Phase 1 |
|------|------|------|---------|---------|---------|
| 旗舰档 | Q4_K_M | 8192 | 12GB+ RAM | ≥ 12 token/s | ⚠️ 未来替换 |
| 标准档 | Q4_K_S | 8192 | 6-8GB RAM | ≥ 10 token/s | ⚠️ 未来替换 |
| 流畅档 | Q4_K_S | 4096 | 4GB RAM | ≥ 15 token/s | ⚠️ 未来替换 |
| **Phase 1 实际** | **Q4_0** | **8192** | **中端机** | **≥ 10 token/s** | **✅ 使用现有文件** |

---

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| Windows NDK 编译失败 | 用 WSL2：`wsl --install`，WSL2 内执行 cmake |
| 模型加载 OOM | 降低 `nCtx` 到 4096，或换 Q4_K_S |
| Token 速度慢 | 开启 Vulkan（`nGpuLayers=99`），或换更快量化档 |
| GGUF 模型从哪来 | 项目根目录已有 `gemma-4-E4B-it-Q4_0.gguf`（4.6GB）|
