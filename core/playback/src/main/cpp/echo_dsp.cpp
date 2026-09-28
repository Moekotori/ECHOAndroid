#include <jni.h>

#include <cmath>
#include <cstdint>

namespace {

constexpr float kSnap = 0.000001f;

float sanitize(float value) {
    return std::isfinite(value) ? value : 0.f;
}

float clampUnit(float value) {
    if (value > 1.f) return 1.f;
    if (value < -1.f) return -1.f;
    return value;
}

float* directFloats(JNIEnv* env, jobject buffer, jint byteOffset, jint floatCount) {
    if (buffer == nullptr || byteOffset < 0 || (byteOffset % 4) != 0) return nullptr;
    void* address = env->GetDirectBufferAddress(buffer);
    if (address == nullptr) return nullptr;
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    const jlong end = static_cast<jlong>(byteOffset) + static_cast<jlong>(floatCount) * 4;
    if (capacity < end) return nullptr;
    return reinterpret_cast<float*>(static_cast<char*>(address) + byteOffset);
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_app_echo_android_playback_EchoDspNative_nativeProcessEq(
        JNIEnv* env,
        jobject /* clazz */,
        jobject input,
        jint inputOffset,
        jobject output,
        jint outputOffset,
        jint frames,
        jint channels,
        jfloat preamp,
        jboolean clamp,
        jfloatArray coeffs,
        jfloatArray delay) {
    if (frames <= 0 || channels <= 0 || channels > 8) return JNI_FALSE;
    const jint samples = frames * channels;
    float* in = directFloats(env, input, inputOffset, samples);
    float* out = directFloats(env, output, outputOffset, samples);
    if (in == nullptr || out == nullptr) return JNI_FALSE;

    const jsize coeffCount = coeffs == nullptr ? 0 : env->GetArrayLength(coeffs);
    const jint bands = coeffCount / 5;
    if (bands < 0 || coeffCount != bands * 5) return JNI_FALSE;
    const jsize delayCount = delay == nullptr ? 0 : env->GetArrayLength(delay);
    if (delayCount != channels * bands * 2) return JNI_FALSE;

    jfloat* coeffValues = bands == 0 ? nullptr : env->GetFloatArrayElements(coeffs, nullptr);
    jfloat* delayValues = bands == 0 ? nullptr : env->GetFloatArrayElements(delay, nullptr);
    if (bands > 0 && (coeffValues == nullptr || delayValues == nullptr)) {
        if (coeffValues != nullptr) env->ReleaseFloatArrayElements(coeffs, coeffValues, JNI_ABORT);
        if (delayValues != nullptr) env->ReleaseFloatArrayElements(delay, delayValues, JNI_ABORT);
        return JNI_FALSE;
    }

    for (jint frame = 0; frame < frames; ++frame) {
        for (jint channel = 0; channel < channels; ++channel) {
            float sample = sanitize(in[frame * channels + channel]) * preamp;
            float* state = delayValues + channel * bands * 2;
            for (jint band = 0; band < bands; ++band) {
                const float* coeff = coeffValues + band * 5;
                const jint stateIndex = band * 2;
                const float s1 = state[stateIndex];
                const float s2 = state[stateIndex + 1];
                const float y = coeff[0] * sample + s1;
                state[stateIndex] = coeff[1] * sample - coeff[3] * y + s2;
                state[stateIndex + 1] = coeff[2] * sample - coeff[4] * y;
                sample = y;
            }
            out[frame * channels + channel] = clamp == JNI_TRUE ? clampUnit(sample) : sample;
        }
    }

    if (bands > 0) {
        env->ReleaseFloatArrayElements(coeffs, coeffValues, JNI_ABORT);
        env->ReleaseFloatArrayElements(delay, delayValues, 0);
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_echo_android_playback_EchoDspNative_nativeProcessKernel(
        JNIEnv* env,
        jobject /* clazz */,
        jobject samples,
        jint byteOffset,
        jint frames,
        jint channels,
        jfloatArray state,
        jintArray cursor,
        jfloatArray delayedLeft,
        jfloatArray delayedRight,
        jboolean limiting) {
    if (frames <= 0 || channels < 1 || channels > 2) return JNI_FALSE;
    if (state == nullptr || cursor == nullptr || delayedLeft == nullptr || delayedRight == nullptr) return JNI_FALSE;
    if (env->GetArrayLength(state) < 11 || env->GetArrayLength(cursor) < 1) return JNI_FALSE;
    float* pcm = directFloats(env, samples, byteOffset, frames * channels);
    if (pcm == nullptr) return JNI_FALSE;

    jfloat* fields = env->GetFloatArrayElements(state, nullptr);
    jint* cursorValues = env->GetIntArrayElements(cursor, nullptr);
    jfloat* leftDelay = env->GetFloatArrayElements(delayedLeft, nullptr);
    jfloat* rightDelay = env->GetFloatArrayElements(delayedRight, nullptr);
    const jsize delaySize = env->GetArrayLength(delayedLeft);
    if (fields == nullptr || cursorValues == nullptr || leftDelay == nullptr || rightDelay == nullptr ||
            delaySize <= 0 || env->GetArrayLength(delayedRight) != delaySize) {
        if (fields != nullptr) env->ReleaseFloatArrayElements(state, fields, JNI_ABORT);
        if (cursorValues != nullptr) env->ReleaseIntArrayElements(cursor, cursorValues, JNI_ABORT);
        if (leftDelay != nullptr) env->ReleaseFloatArrayElements(delayedLeft, leftDelay, JNI_ABORT);
        if (rightDelay != nullptr) env->ReleaseFloatArrayElements(delayedRight, rightDelay, JNI_ABORT);
        return JNI_FALSE;
    }

    float lowLeft = fields[0];
    float lowRight = fields[1];
    const float lowAlpha = fields[2];
    const float smooth = fields[3];
    const float release = fields[4];
    float crossMix = fields[5];
    float level = fields[6];
    float reduction = fields[7];
    const float ceiling = fields[8];
    const float targetMix = fields[9];
    const float targetLevel = fields[10];
    int index = cursorValues[0];
    if (index < 0 || index >= delaySize) index = 0;
    const bool stereo = channels == 2;

    for (jint frame = 0; frame < frames; ++frame) {
        const float leftIn = sanitize(pcm[frame * channels]);
        const float rightIn = stereo ? sanitize(pcm[frame * channels + 1]) : leftIn;
        crossMix += smooth * (targetMix - crossMix);
        level += smooth * (targetLevel - level);
        if (std::fabs(crossMix - targetMix) < kSnap) crossMix = targetMix;
        if (std::fabs(level - targetLevel) < kSnap) level = targetLevel;
        lowLeft += lowAlpha * (leftIn - lowLeft);
        lowRight += lowAlpha * (rightIn - lowRight);
        const float delayedL = leftDelay[index];
        const float delayedR = rightDelay[index];
        leftDelay[index] = lowLeft;
        rightDelay[index] = lowRight;
        index += 1;
        if (index >= delaySize) index = 0;
        const float mix = stereo ? crossMix : 0.f;
        const float denominator = 1.f + mix;
        const float a = ((leftIn + delayedR * mix) / denominator) * level;
        const float b = ((rightIn + delayedL * mix) / denominator) * level;
        const float peak = std::fmax(std::fabs(a), stereo ? std::fabs(b) : 0.f);
        const float wanted = (limiting == JNI_TRUE && peak > ceiling) ? ceiling / peak : 1.f;
        reduction = wanted < reduction ? wanted : reduction + release * (wanted - reduction);
        if (std::fabs(reduction - 1.f) < kSnap) reduction = 1.f;
        pcm[frame * channels] = a * reduction;
        if (stereo) pcm[frame * channels + 1] = b * reduction;
    }

    fields[0] = lowLeft;
    fields[1] = lowRight;
    fields[5] = crossMix;
    fields[6] = level;
    fields[7] = reduction;
    cursorValues[0] = index;
    env->ReleaseFloatArrayElements(state, fields, 0);
    env->ReleaseIntArrayElements(cursor, cursorValues, 0);
    env->ReleaseFloatArrayElements(delayedLeft, leftDelay, 0);
    env->ReleaseFloatArrayElements(delayedRight, rightDelay, 0);
    return JNI_TRUE;
}
