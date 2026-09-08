cmake_minimum_required(VERSION 3.22.1)
project(echo_ffmpeg C CXX)
set(CMAKE_CXX_STANDARD 11)

set(ffmpeg_prefix "${ECHO_FFMPEG_ROOT}/${ANDROID_ABI}")
foreach(component avutil swresample avcodec)
    add_library(${component} STATIC IMPORTED)
    set_target_properties(${component} PROPERTIES
        IMPORTED_LOCATION "${ffmpeg_prefix}/lib/lib${component}.a")
endforeach()

add_library(ffmpegJNI SHARED ../../../third_party/media3-ffmpeg/jni/ffmpeg_jni.cc)
target_include_directories(ffmpegJNI PRIVATE "${ffmpeg_prefix}/include")
target_link_libraries(ffmpegJNI PRIVATE avcodec swresample avutil android log)
target_link_options(ffmpegJNI PRIVATE "-Wl,-z,max-page-size=16384")
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_link_options(ffmpegJNI PRIVATE "-Wl,-Bsymbolic")
endif()
