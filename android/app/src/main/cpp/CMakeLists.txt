cmake_minimum_required(VERSION 3.22.1)

project(llama_jni)

# ── llama.cpp sources ──────────────────────────────────────────
# TODO (Phase 2): Run this from the git submodule:
#   git submodule add https://github.com/ggerganov/llama.cpp.git
# For now, assume llama.cpp is at ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

set(LLAMA_SOURCES
    ${LLAMA_DIR}/llama.cpp
    ${LLAMA_DIR}/common/common.cpp
    ${LLAMA_DIR}/common/base64.cpp
    ${LLAMA_DIR}/common/chat.cpp
)

set(LLAMA_HEADERS
    ${LLAMA_DIR}/llama.h
    ${LLAMA_DIR}/common/common.h
)

# ── llama_jni shared library ──────────────────────────────────
add_library(llama_jni SHARED
    llama_jni.cpp
    ${LLAMA_SOURCES}
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_DIR}
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/gguf
)

# Disable unused llama.cpp examples/tests to speed up compilation
target_compile_definitions(llama_jni PRIVATE
    LLAMA_STATIC
    LLAMA_ALL_WARNINGS=0
    NDEBUG
)

# Android settings
target_link_libraries(llama_jni
    android
    log
    cpu_features
    # Vulkan for GPU acceleration (optional — enable if device supports it)
    # vulkan
)

# ── ABI split ─────────────────────────────────────────────────
# By default, CMake builds for the current host (x86_64).
# On Android CI / dev machine, this builds for arm64-v8a:
if(CMAKE_SYSTEM_NAME STREQUAL "Android")
    set(ANDROID_PLATFORM android-24)
    set(ANDROID_STL c++_shared)
    set(ANDROID_LD_FLAGS "-llog -lcpu_features")
endif()
