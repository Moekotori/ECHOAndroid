#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <algorithm>
#include <new>
extern "C" {
#include <libswresample/swresample.h>
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
}

// Fixed scratch capacities; conversion input is sliced before entering this function.
static const int USB_INPUT_FRAMES = 1024;
static const int USB_OUTPUT_FRAMES = 8192;
struct UsbResampler {
    SwrContext* swr = nullptr;
    int in_channels, out_channels, in_bits, out_bits;
    int32_t input[USB_INPUT_FRAMES * 8];
    int32_t output[USB_OUTPUT_FRAMES * 2];
};

static void usb_resampler_close(UsbResampler* state) {
    if (!state) return;
    swr_free(&state->swr);
    delete state;
}

static UsbResampler* usb_resampler_create(int in_rate, int in_channels, int in_bits,
        int out_rate, int out_channels, int out_bits) {
    if (in_rate < 8000 || in_rate > 384000 || out_rate < 8000 || out_rate > 384000 ||
        in_channels < 1 || in_channels > 8 || out_channels < 1 || out_channels > 2 ||
        (in_bits != 16 && in_bits != 24 && in_bits != 32 && in_bits != -32) ||
        (out_bits != 16 && out_bits != 24 && out_bits != 32)) return nullptr;
    auto* state = new(std::nothrow) UsbResampler();
    if (!state) return nullptr;
    state->in_channels = in_channels; state->out_channels = out_channels;
    state->in_bits = in_bits; state->out_bits = out_bits;
    AVChannelLayout input_layout{}, output_layout{};
    av_channel_layout_default(&input_layout, in_channels);
    av_channel_layout_default(&output_layout, out_channels);
    const int result = swr_alloc_set_opts2(&state->swr, &output_layout,
        out_bits == 16 ? AV_SAMPLE_FMT_S16 : AV_SAMPLE_FMT_S32, out_rate,
        &input_layout, in_bits == -32 ? AV_SAMPLE_FMT_FLT : AV_SAMPLE_FMT_S32, in_rate, 0, nullptr);
    av_channel_layout_uninit(&input_layout); av_channel_layout_uninit(&output_layout);
    if (result < 0 || !state->swr) { usb_resampler_close(state); return nullptr; }
    if (out_bits < std::abs(in_bits) || in_rate != out_rate) {
        av_opt_set_int(state->swr, "dither_method", SWR_DITHER_TRIANGULAR_HIGHPASS, 0);
        av_opt_set_int(state->swr, "output_sample_bits", out_bits, 0);
    }
    if (swr_init(state->swr) < 0) { usb_resampler_close(state); return nullptr; }
    return state;
}

// Returns output bytes. Null input drains the finite filter tail at end of stream.
static int usb_resampler_convert(UsbResampler* state, const uint8_t* input, int frames,
        bool big_endian, uint8_t* output, int output_bytes) {
    if (!state || frames < 0 || frames > USB_INPUT_FRAMES || !output ||
        (frames > 0 && !input)) return -1;
    const int out_sample_bytes = state->out_bits / 8;
    const int capacity = std::min(USB_OUTPUT_FRAMES, output_bytes / (out_sample_bytes * state->out_channels));
    if (capacity <= 0 || swr_get_out_samples(state->swr, frames) > capacity) return -1;
    const int input_bytes = std::abs(state->in_bits) / 8;
    for (int n = 0; n < frames * state->in_channels; ++n) {
        uint32_t value = 0;
        const uint8_t* sample = input + n * input_bytes;
        for (int b = 0; b < input_bytes; ++b)
            value |= (uint32_t)sample[big_endian ? input_bytes - 1 - b : b] << (8 * b);
        if (state->in_bits != -32 && input_bytes < 4) value <<= (32 - state->in_bits);
        memcpy(&state->input[n], &value, sizeof(value));
    }
    const uint8_t* source = reinterpret_cast<const uint8_t*>(state->input);
    uint8_t* target = reinterpret_cast<uint8_t*>(state->output);
    const int converted = swr_convert(state->swr, &target, capacity,
        input ? &source : nullptr, frames);
    if (converted < 0) return converted;
    if (state->out_bits == 24) {
        for (int n = 0; n < converted * state->out_channels; ++n) {
            const uint32_t sample = (uint32_t)state->output[n] >> 8;
            output[n * 3] = (uint8_t)sample;
            output[n * 3 + 1] = (uint8_t)(sample >> 8);
            output[n * 3 + 2] = (uint8_t)(sample >> 16);
        }
    } else memcpy(output, target, (size_t)converted * state->out_channels * out_sample_bytes);
    return converted * state->out_channels * out_sample_bytes;
}

#ifndef ECHO_USB_RESAMPLER_TEST
#include <jni.h>
extern "C" JNIEXPORT jlong JNICALL
Java_app_echo_android_playback_EchoUsbResamplerNative_nativeCreate(JNIEnv*, jclass,
        jint in_rate, jint in_channels, jint in_bits, jint out_rate, jint out_channels, jint out_bits) {
    return reinterpret_cast<jlong>(usb_resampler_create(in_rate, in_channels, in_bits, out_rate, out_channels, out_bits));
}

extern "C" JNIEXPORT jint JNICALL
Java_app_echo_android_playback_EchoUsbResamplerNative_nativeConvert(JNIEnv* env, jclass,
        jlong handle, jobject input, jint offset, jint frames, jboolean big_endian, jobject output, jint capacity) {
    auto* state = reinterpret_cast<UsbResampler*>(handle);
    auto* destination = static_cast<uint8_t*>(env->GetDirectBufferAddress(output));
    const jlong out_capacity = env->GetDirectBufferCapacity(output);
    if (!state || !destination || capacity < 0 || capacity > out_capacity || offset < 0) return -1;
    const uint8_t* source = nullptr;
    if (input) {
        auto* address = static_cast<uint8_t*>(env->GetDirectBufferAddress(input));
        const jlong size = env->GetDirectBufferCapacity(input);
        const int64_t bytes = (int64_t)frames * state->in_channels * (std::abs(state->in_bits) / 8);
        if (!address || frames < 0 || offset > size || bytes > size - offset) return -1;
        source = address + offset;
    }
    return usb_resampler_convert(state, source, frames, big_endian, destination, capacity);
}

extern "C" JNIEXPORT void JNICALL
Java_app_echo_android_playback_EchoUsbResamplerNative_nativeClose(JNIEnv*, jclass, jlong handle) {
    usb_resampler_close(reinterpret_cast<UsbResampler*>(handle));
}
#endif
