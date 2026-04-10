cmake_minimum_required(VERSION 3.22.1)

project(llama_jni)

# ── llama.cpp submodule ────────────────────────────────────────
# Source: E:/desktop/ai_game/android/app/src/main/cpp/llama.cpp/
# Commit: pinned via git submodule
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# ── llama.cpp as subdirectory ────────────────────────────────
# llama.cpp uses its own CMake build system — add it as subdirectory
# and link against the resulting `llama` library target.
# Disable all unnecessary features for faster Android compilation.
set(LLAMA_BUILD_TOOLS      OFF)
set(LLAMA_BUILD_EXAMPLES    OFF)
set(LLAMA_BUILD_SERVER      OFF)
set(LLAMA_BUILD_TESTS       OFF)
set(LLAMA_BUILD_COMMON     OFF)
set(LLAMA_BUILD_WEBUI      OFF)
set(LLAMA_OPENSSL          OFF)
set(LLAMA_LLGUIDANCE       OFF)
set(GGML_LLAMAFILE         OFF)

# CUDA / OpenCL / SYCL / Metal — all OFF for CPU-only Android build
set(GGML_CUDA              OFF)
set(GGML_CUBLAS            OFF)
set(GGML_METAL             OFF)
set(GGML_SYCL              OFF)
set(GGML_CANN              OFF)
set(GGML_RPC               OFF)

# Use system-provided ggml or build internal one
set(LLAMA_USE_SYSTEM_GGML OFF)

# ── Android ABI ──────────────────────────────────────────────
# Supported ABIs: arm64-v8a (primary), armeabi-v7a (fallback)
set(ANDROID_PLATFORM android-24)
set(ANDROID_STL            c++_shared)

# Add llama.cpp subdirectory (defines `llama` target)
add_subdirectory(${LLAMA_DIR} llama_cmake_build EXCLUDE_FROM_ALL)

# ── llama_jni shared library ──────────────────────────────────
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/src
    ${LLAMA_DIR}/common
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
