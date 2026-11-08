#include <jni.h>
#include <cmath>

extern "C" JNIEXPORT jboolean JNICALL
Java_app_echo_android_playback_EchoDspNative_nativeProcessPersonal(
    JNIEnv* env, jclass, jobject buffer, jint offset, jint frames,
    jfloatArray coefficients, jfloatArray history, jintArray cursor,
    jfloatArray state, jfloatArray params) {
    if (!buffer || !coefficients || !history || !cursor || !state || !params) return JNI_FALSE;
    const int taps = env->GetArrayLength(coefficients) / 4;
    if (taps < 1 || taps > 1024 || env->GetArrayLength(coefficients) != taps * 4 ||
        env->GetArrayLength(history) != taps * 2 || env->GetArrayLength(cursor) != 1 ||
        env->GetArrayLength(state) != 7 || env->GetArrayLength(params) != 6 ||
        frames < 1 || frames > 1024 || offset < 0 || offset % 4 ||
        env->GetDirectBufferCapacity(buffer) < static_cast<jlong>(offset) + frames * 8) return JNI_FALSE;
    auto* data = static_cast<char*>(env->GetDirectBufferAddress(buffer));
    if (!data) return JNI_FALSE;
    auto* c = env->GetFloatArrayElements(coefficients, nullptr);
    auto* h = env->GetFloatArrayElements(history, nullptr);
    float s[7], p[6]; jint pos;
    env->GetFloatArrayRegion(state, 0, 7, s);
    env->GetFloatArrayRegion(params, 0, 6, p);
    env->GetIntArrayRegion(cursor, 0, 1, &pos);
    if (!c || !h || pos < 0 || pos >= taps) {
        if (c) env->ReleaseFloatArrayElements(coefficients, c, JNI_ABORT);
        if (h) env->ReleaseFloatArrayElements(history, h, JNI_ABORT);
        return JNI_FALSE;
    }
    auto* samples = reinterpret_cast<float*>(data + offset);
    for (int frame = 0; frame < frames; ++frame) {
        float l = samples[frame * 2], r = samples[frame * 2 + 1];
        if (!std::isfinite(l)) l = 0;
        if (!std::isfinite(r)) r = 0;
        h[pos] = l; h[taps + pos] = r;
        for (int i = 0; i < 3; ++i) {
            s[i] += p[3] * (p[i] - s[i]);
            if (std::fabs(s[i] - p[i]) < 0.000001f) s[i] = p[i];
        }
        for (int ear = 0; ear < 2; ++ear) {
            const float dry = ear == 0 ? l : r;
            float wet = 0;
            if (s[0] != 0) {
                int read = pos;
                for (int tap = 0; tap < taps; ++tap) {
                    wet += h[read] * c[ear * taps + tap] + h[taps + read] * c[(2 + ear) * taps + tap];
                    if (--read < 0) read = taps - 1;
                }
            }
            const float x = dry + s[0] * (wet - dry);
            s[3 + ear] += p[4] * (x - s[3 + ear]);
            s[5 + ear] += p[5] * (x - s[5 + ear]);
            const float y = x + s[3 + ear] * (s[1] - 1) + (x - s[5 + ear]) * (s[2] - 1);
            samples[frame * 2 + ear] = std::isfinite(y) ? y : 0;
        }
        if (++pos == taps) pos = 0;
    }
    env->SetFloatArrayRegion(state, 0, 7, s);
    env->SetIntArrayRegion(cursor, 0, 1, &pos);
    env->ReleaseFloatArrayElements(coefficients, c, JNI_ABORT);
    env->ReleaseFloatArrayElements(history, h, 0);
    return JNI_TRUE;
}
