# The JNI wrapper around FFmpeg's AC-3 and E-AC-3 decoders (Media3's decoder_ffmpeg, Apache 2.0;
# its CMakeLists.txt with the FFmpeg location passed in by Gradle: -DFFMPEG_DIR).
cmake_minimum_required(VERSION 3.21.0 FATAL_ERROR)
set(CMAKE_CXX_STANDARD 11)
project(libffmpegJNI C CXX)

set(ffmpeg_binaries "${FFMPEG_DIR}/${ANDROID_ABI}")
foreach(ffmpeg_lib avutil swresample avcodec)
    add_library(${ffmpeg_lib} STATIC IMPORTED)
    set_target_properties(${ffmpeg_lib} PROPERTIES IMPORTED_LOCATION ${ffmpeg_binaries}/lib/lib${ffmpeg_lib}.a)
endforeach()

include_directories(${ffmpeg_binaries}/include)
find_library(android_log_lib log)

add_library(ffmpegJNI SHARED ffmpeg_jni.cc)
set_target_properties(ffmpegJNI PROPERTIES CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN ON)
target_link_libraries(ffmpegJNI PRIVATE android PRIVATE swresample PRIVATE avcodec PRIVATE avutil PRIVATE ${android_log_lib})
# Unused code out: smaller library.
target_link_options(ffmpegJNI PRIVATE "-Wl,--gc-sections" "-Wl,--exclude-libs,ALL")

# Needed for arm64-v8a from NDK 23.1 (https://github.com/google/ExoPlayer/issues/9933).
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_link_options(ffmpegJNI PRIVATE "-Wl,-Bsymbolic")
endif()
