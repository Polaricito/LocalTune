cmake_minimum_required(VERSION 3.22.1)

project("archivetune_whisper" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Build whisper.cpp and ggml as static libraries and expose a single shared
# library to the Android app. Only the portable CPU backend is needed.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_CPU ON CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp/ggml EXCLUDE_FROM_ALL)

add_library(whisper STATIC
    whisper.cpp/src/whisper.cpp
    whisper.cpp/src/whisper-arch.h
    whisper.cpp/src/aneforge/whisper-aneforge.cpp
    whisper.cpp/src/aneforge/whisper-aneforge.h
)

target_include_directories(whisper
    PUBLIC
        whisper.cpp/include
        whisper.cpp/src
)

target_compile_features(whisper PUBLIC cxx_std_17)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.9.5")
target_link_libraries(whisper PUBLIC ggml)

add_library(archivetune_whisper SHARED
    whisper_jni.cpp
)

target_include_directories(archivetune_whisper
    PRIVATE
        whisper.cpp/include
        whisper.cpp/src
        whisper.cpp/ggml/include
        whisper.cpp/ggml/src
        whisper.cpp/ggml/src/ggml-cpu
)

target_compile_options(archivetune_whisper PRIVATE
    -O3
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections
)

target_link_options(archivetune_whisper PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)

target_link_libraries(archivetune_whisper
    PRIVATE
        whisper
        ggml
        android
        log
)
