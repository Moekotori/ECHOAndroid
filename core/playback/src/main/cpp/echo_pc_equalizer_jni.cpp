#include "EchoPcEqualizer.h"
#include <jni.h>
using echo_android::PcEqualizer;
extern "C" JNIEXPORT jlong JNICALL Java_app_echo_android_playback_EchoPcEqNative_create(JNIEnv* env, jclass, jint rate, jint channels, jfloatArray parameters, jboolean preserve) {
    if (!parameters) return 0;
    auto* p = env->GetFloatArrayElements(parameters, nullptr); if (!p) return 0;
    PcEqualizer* graph = nullptr;
    try { graph = new PcEqualizer(rate, channels, {p, static_cast<size_t>(env->GetArrayLength(parameters))}, preserve); } catch (...) { }
    env->ReleaseFloatArrayElements(parameters, p, JNI_ABORT); return reinterpret_cast<jlong>(graph);
}
extern "C" JNIEXPORT jboolean JNICALL Java_app_echo_android_playback_EchoPcEqNative_update(JNIEnv* env, jclass, jlong handle, jfloatArray parameters) {
    if (!handle || !parameters) return JNI_FALSE;
    auto* p = env->GetFloatArrayElements(parameters, nullptr); if (!p) return JNI_FALSE;
    const bool ok = reinterpret_cast<PcEqualizer*>(handle)->update({p, static_cast<size_t>(env->GetArrayLength(parameters))});
    env->ReleaseFloatArrayElements(parameters, p, JNI_ABORT); return ok;
}
extern "C" JNIEXPORT jint JNICALL Java_app_echo_android_playback_EchoPcEqNative_process(JNIEnv* env, jclass, jlong handle, jobject buffer, jint offset, jint frames, jint channels, jboolean enabled) {
    if (!handle || !buffer || offset < 0 || offset % 4 || frames < 1 || frames > 1024 || channels < 1 || channels > 8 ||
        env->GetDirectBufferCapacity(buffer) < static_cast<jlong>(offset) + frames * channels * 4) return -1;
    auto* base = static_cast<char*>(env->GetDirectBufferAddress(buffer)); if (!base) return -1;
    auto* graph = reinterpret_cast<PcEqualizer*>(handle);
    if (channels != graph->channelCount()) return -1;
    graph->process(reinterpret_cast<float*>(base + offset), frames, enabled);
    return graph->processing() ? 1 : 0;
}
extern "C" JNIEXPORT void JNICALL Java_app_echo_android_playback_EchoPcEqNative_reset(JNIEnv*, jclass, jlong handle) { if (handle) reinterpret_cast<PcEqualizer*>(handle)->reset(); }
extern "C" JNIEXPORT void JNICALL Java_app_echo_android_playback_EchoPcEqNative_destroy(JNIEnv*, jclass, jlong handle) { delete reinterpret_cast<PcEqualizer*>(handle); }
