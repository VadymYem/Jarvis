cmake_minimum_required(VERSION 3.31.6)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM" CACHE STRING "Target architecture" FORCE)
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "Use KleidiAI kernels" FORCE)
    set(GGML_OPENMP ON CACHE BOOL "Use OpenMP" FORCE)
else()
    message(FATAL_ERROR "Jarvis | AuthorAi Local GGUF currently supports arm64-v8a only")
endif()

FetchContent_Declare(
    llama_cpp
    URL https://github.com/ggml-org/llama.cpp/archive/ed7ac35e1ee49cb70e4dfa9f0a2ce39b0a5ec4ea.tar.gz
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE
)
FetchContent_MakeAvailable(llama_cpp)

# llama.android currently calls __android_log_is_loggable(), which is only
# available from API 30. Jarvis supports API 26+, so keep the same severity
# filtering without linking an unavailable platform symbol on Android 8/9/10.
set(LLAMA_ANDROID_LOGGING_HEADER
    "${llama_cpp_SOURCE_DIR}/examples/llama.android/lib/src/main/cpp/logging.h")
file(READ "${LLAMA_ANDROID_LOGGING_HEADER}" LLAMA_ANDROID_LOGGING_CONTENT)
set(LLAMA_ANDROID_LOGGING_OLD
    "return __android_log_is_loggable(prio, LOG_TAG, LOG_MIN_LEVEL);")
set(LLAMA_ANDROID_LOGGING_NEW
    "return prio >= LOG_MIN_LEVEL;")
string(REPLACE
    "${LLAMA_ANDROID_LOGGING_OLD}"
    "${LLAMA_ANDROID_LOGGING_NEW}"
    LLAMA_ANDROID_LOGGING_PATCHED
    "${LLAMA_ANDROID_LOGGING_CONTENT}"
)
if(LLAMA_ANDROID_LOGGING_PATCHED STREQUAL LLAMA_ANDROID_LOGGING_CONTENT)
    message(FATAL_ERROR "Pinned llama.cpp logging compatibility patch no longer applies")
endif()
file(WRITE "${LLAMA_ANDROID_LOGGING_HEADER}" "${LLAMA_ANDROID_LOGGING_PATCHED}")

add_library(
    ${CMAKE_PROJECT_NAME}
    SHARED
    ${llama_cpp_SOURCE_DIR}/examples/llama.android/lib/src/main/cpp/ai_chat.cpp
)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
    GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
    GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${llama_cpp_SOURCE_DIR}
    ${llama_cpp_SOURCE_DIR}/common
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
    ${llama_cpp_SOURCE_DIR}/ggml/src
    ${llama_cpp_SOURCE_DIR}/examples/llama.android/lib/src/main/cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    llama-common
    android
    log
)
