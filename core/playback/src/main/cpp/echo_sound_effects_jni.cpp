#include "EchoSoundEffectEngine.h"
#include <jni.h>
#include <cstdint>

using echo_android::SoundEffectEngine;
extern "C" JNIEXPORT jlong JNICALL Java_app_echo_android_playback_EchoSoundEffectNative_create(
    JNIEnv* env, jclass, jstring preset, jint rate, jint channels) {
    if (!preset) return 0;
    const char* text = env->GetStringUTFChars(preset, nullptr);
    if (!text) return 0;
    SoundEffectEngine* graph = nullptr;
    try { graph = new SoundEffectEngine(text, rate, channels); } catch (...) { }
    env->ReleaseStringUTFChars(preset, text);
    return reinterpret_cast<jlong>(graph);
}
extern "C" JNIEXPORT jboolean JNICALL Java_app_echo_android_playback_EchoSoundEffectNative_process(
    JNIEnv* env, jclass, jlong handle, jobject buffer, jint offset, jint frames, jint channels, jboolean enabled, jintArray metadata) {
    if (!handle || !buffer || !metadata || env->GetArrayLength(metadata) != 3 || frames < 1 || frames > 1024 ||
        channels < 1 || channels > 8 || offset < 0 || offset % 4 ||
        env->GetDirectBufferCapacity(buffer) < static_cast<jlong>(offset) + frames * channels * 4) return JNI_FALSE;
    auto* base = static_cast<char*>(env->GetDirectBufferAddress(buffer));
    if (!base) return JNI_FALSE;
    auto* graph = reinterpret_cast<SoundEffectEngine*>(handle);
    if (channels != graph->channelCount()) return JNI_FALSE;
    graph->process(reinterpret_cast<float*>(base + offset), frames, enabled);
    jint result[3] {graph->tailRemaining(), graph->latencyFrames(), graph->dry() ? 1 : 0};
    env->SetIntArrayRegion(metadata, 0, 3, result);
    return JNI_TRUE;
}
extern "C" JNIEXPORT void JNICALL Java_app_echo_android_playback_EchoSoundEffectNative_reset(JNIEnv*, jclass, jlong handle) {
    if (handle) reinterpret_cast<SoundEffectEngine*>(handle)->reset();
}
extern "C" JNIEXPORT void JNICALL Java_app_echo_android_playback_EchoSoundEffectNative_destroy(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<SoundEffectEngine*>(handle);
}
