cmake_minimum_required(VERSION 3.22.1)
project(echo_ffmpeg C CXX)
set(CMAKE_CXX_STANDARD 11)

set(ffmpeg_prefix "${ECHO_FFMPEG_ROOT}/${ANDROID_ABI}")
foreach(component avutil swresample avcodec avformat)
    add_library(${component} STATIC IMPORTED)
    set_target_properties(${component} PROPERTIES
        IMPORTED_LOCATION "${ffmpeg_prefix}/lib/lib${component}.a")
endforeach()

add_library(echo_dsp SHARED echo_dsp.cpp echo_personal_audio.cpp)
set(sound_effect_root "${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/sound-effects/engine")
target_compile_features(echo_dsp PRIVATE cxx_std_20)
target_include_directories(echo_dsp PRIVATE "${sound_effect_root}" "${sound_effect_root}/third_party/signalsmith/dsp/include")
target_sources(echo_dsp PRIVATE EchoSoundEffectEngine.cpp echo_sound_effects_jni.cpp
    EchoPcEqualizer.cpp echo_pc_equalizer_jni.cpp
    "${sound_effect_root}/AdditionalSoundEffects.cpp" "${sound_effect_root}/CharacterSoundEffects.cpp"
    "${sound_effect_root}/ListeningColorEffects.cpp" "${sound_effect_root}/ModulationSoundEffects.cpp"
    "${sound_effect_root}/EqProcessor.cpp" "${sound_effect_root}/DeEsserProcessor.cpp")
file(GLOB sound_effect_airwindows CONFIGURE_DEPENDS "${sound_effect_root}/third_party/airwindows-*/*.cpp")
target_sources(echo_dsp PRIVATE ${sound_effect_airwindows})
target_link_libraries(echo_dsp android log)
target_link_options(echo_dsp PRIVATE "-Wl,-z,max-page-size=16384")

add_library(ffmpegJNI SHARED ../../../third_party/media3-ffmpeg/jni/ffmpeg_jni.cc)
target_sources(ffmpegJNI PRIVATE echo_usb_resample.cpp)
target_sources(ffmpegJNI PRIVATE echo_ffmpeg_demux.cpp)
target_include_directories(ffmpegJNI PRIVATE "${ffmpeg_prefix}/include")
target_link_libraries(ffmpegJNI PRIVATE avformat avcodec swresample avutil android log)
target_link_options(ffmpegJNI PRIVATE "-Wl,-z,max-page-size=16384")
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_link_options(ffmpegJNI PRIVATE "-Wl,-Bsymbolic")
endif()
