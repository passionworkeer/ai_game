cmake_minimum_required(VERSION 3.22.1)

project(llama_jni)

# ── llama.cpp submodule ────────────────────────────────────────
# Source: E:/desktop/ai_game/android/app/src/main/cpp/llama.cpp/
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# ── whisper.cpp ───────────────────────────────────────────────
# Source: E:/desktop/ai_game/android/app/src/main/cpp/whisper.cpp/
set(WHisper_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# ── Common Android settings ───────────────────────────────────
set(ANDROID_PLATFORM android-24)
set(ANDROID_STL            c++_shared)

# ══════════════════════════════════════════════════════════════
# Build llama.cpp — defines `llama` and `ggml` targets
# ══════════════════════════════════════════════════════════════
set(LLAMA_BUILD_TOOLS      OFF)
set(LLAMA_BUILD_EXAMPLES    OFF)
set(LLAMA_BUILD_SERVER      OFF)
set(LLAMA_BUILD_TESTS      OFF)
set(LLAMA_BUILD_COMMON     OFF)
set(LLAMA_BUILD_WEBUI      OFF)
set(LLAMA_OPENSSL          OFF)
set(LLAMA_LLGUIDANCE      OFF)
set(GGML_LLAMAFILE         OFF)
# CPU-only for Android
set(GGML_CUDA              OFF)
set(GGML_CUBLAS            OFF)
set(GGML_METAL             OFF)
set(GGML_SYCL              OFF)
set(GGML_CANN              OFF)
set(GGML_RPC               OFF)
set(LLAMA_USE_SYSTEM_GGML  OFF)

add_subdirectory(${LLAMA_DIR} llama_cmake_build EXCLUDE_FROM_ALL)

# ══════════════════════════════════════════════════════════════
# Build whisper.cpp — uses llama.cpp's `ggml` target (same version)
# whisper.cpp's CMake skips its own ggml because `ggml` target
# is already defined by llama.cpp above.
# ══════════════════════════════════════════════════════════════
set(WHISPER_ALL_WARNINGS   OFF)
set(WHISPER_COREML         OFF)
set(WHISPER_OPENVINO       OFF)
set(WHISPER_USE_SYSTEM_GGML OFF)
# WHISPER_GGML_DIR is not a standard variable; we rely on the
# `if (NOT TARGET ggml)` check in whisper.cpp CMakeLists.txt.
# Both are ggml v1.8.4 so they are API-compatible.
add_subdirectory(${WHisper_DIR} whisper_cmake_build EXCLUDE_FROM_ALL)

# ── llama_jni shared library ──────────────────────────────────
add_library(llama_jni SHARED
    llama_jni.cpp
    ${LLAMA_DIR}/tools/mtmd/clip.cpp
    ${LLAMA_DIR}/tools/mtmd/mtmd.cpp
    ${LLAMA_DIR}/tools/mtmd/mtmd-image.cpp
    ${LLAMA_DIR}/tools/mtmd/mtmd-audio.cpp
    ${LLAMA_DIR}/tools/mtmd/models/cogvlm.cpp
    ${LLAMA_DIR}/tools/mtmd/models/conformer.cpp
    ${LLAMA_DIR}/tools/mtmd/models/deepseekocr.cpp
    ${LLAMA_DIR}/tools/mtmd/models/dotsocr.cpp
    ${LLAMA_DIR}/tools/mtmd/models/glm4v.cpp
    ${LLAMA_DIR}/tools/mtmd/models/hunyuanocr.cpp
    ${LLAMA_DIR}/tools/mtmd/models/internvl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/gemma4v.cpp
    ${LLAMA_DIR}/tools/mtmd/models/kimik25.cpp
    ${LLAMA_DIR}/tools/mtmd/models/kimivl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/llama4.cpp
    ${LLAMA_DIR}/tools/mtmd/models/llava.cpp
    ${LLAMA_DIR}/tools/mtmd/models/mobilenetv5.cpp
    ${LLAMA_DIR}/tools/mtmd/models/minicpmv.cpp
    ${LLAMA_DIR}/tools/mtmd/models/nemotron-v2-vl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/paddleocr.cpp
    ${LLAMA_DIR}/tools/mtmd/models/pixtral.cpp
    ${LLAMA_DIR}/tools/mtmd/models/qwen2vl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/qwen3vl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/siglip.cpp
    ${LLAMA_DIR}/tools/mtmd/models/step3vl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/whisper-enc.cpp
    ${LLAMA_DIR}/tools/mtmd/models/youtuvl.cpp
)
target_include_directories(llama_jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/src
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/tools/mtmd
    ${LLAMA_DIR}/tools/mtmd/models
    ${LLAMA_DIR}/vendor/stb
)
target_link_libraries(llama_jni
    llama
    android
    log
)
target_compile_definitions(llama_jni PRIVATE
    LLAMA_STATIC
    NDEBUG
)

# ── whisper_jni shared library ─────────────────────────────────
add_library(whisper_jni SHARED
    whisper_jni.cpp
)
target_include_directories(whisper_jni PRIVATE
    ${WHisper_DIR}/include
    ${WHisper_DIR}/src
    ${WHisper_DIR}/ggml/include
)
target_link_libraries(whisper_jni
    whisper
    ggml
    android
    log
)
target_compile_definitions(whisper_jni PRIVATE
    NDEBUG
)
