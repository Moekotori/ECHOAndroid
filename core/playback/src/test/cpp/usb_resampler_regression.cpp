#define ECHO_USB_RESAMPLER_TEST
#include "../../main/cpp/echo_usb_resample.cpp"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

static std::vector<uint8_t> convert(const std::vector<uint8_t>& bytes, int in_rate, int in_channels,
        int in_bits, int out_rate, int out_channels, int out_bits) {
    auto* state = usb_resampler_create(in_rate, in_channels, in_bits, out_rate, out_channels, out_bits);
    assert(state);
    std::vector<uint8_t> result;
    uint8_t output[8192 * 2 * 4];
    const int frame_bytes = in_channels * std::abs(in_bits) / 8;
    int offset = 0;
    while (offset < (int)bytes.size()) {
        const int frames = std::min(128, ((int)bytes.size() - offset) / frame_bytes);
        const int written = usb_resampler_convert(state, bytes.data() + offset, frames, false, output, sizeof(output));
        assert(written >= 0);
        result.insert(result.end(), output, output + written);
        offset += frames * frame_bytes;
    }
    for (int i = 0; i < 8; ++i) {
        const int written = usb_resampler_convert(state, nullptr, 0, false, output, sizeof(output));
        assert(written >= 0);
        result.insert(result.end(), output, output + written);
        if (!written) break;
        assert(i < 7);
    }
    usb_resampler_close(state);
    return result;
}

static void append16(std::vector<uint8_t>& bytes, int16_t value) {
    bytes.push_back((uint8_t)value); bytes.push_back((uint8_t)((uint16_t)value >> 8));
}

int main() {
    assert(!usb_resampler_create(0, 2, 16, 48000, 2, 16));
    std::vector<uint8_t> pcm;
    for (int value : {-32768, -1000, 0, 1000, 32767}) append16(pcm, (int16_t)value);
    assert(convert(pcm, 44100, 1, 16, 44100, 1, 16) == pcm);
    const auto widened = convert(pcm, 44100, 1, 16, 44100, 1, 24);
    assert(widened.size() == 15);
    for (int i = 0; i < 5; ++i) {
        assert(widened[i * 3] == 0);
        assert(widened[i * 3 + 1] == pcm[i * 2]);
        assert(widened[i * 3 + 2] == pcm[i * 2 + 1]);
    }
    std::vector<uint8_t> stereo;
    for (int i = 0; i < 512; ++i) { append16(stereo, 12000); append16(stereo, -12000); }
    const auto mono = convert(stereo, 48000, 2, 16, 48000, 1, 16);
    assert(mono.size() == 1024);
    for (uint8_t value : mono) assert(value == 0);
    std::vector<uint8_t> sine;
    for (int i = 0; i < 4096; ++i) append16(sine, (int16_t)(10000 * std::sin(2 * 3.141592653589793 * i * 1000 / 44100)));
    const auto resampled = convert(sine, 44100, 1, 16, 48000, 1, 32);
    const int frames = (int)resampled.size() / 4;
    assert(std::abs(frames - 4096.0 * 48000 / 44100) <= 2);
    int crossings = 0; int32_t previous = 0; double energy = 0;
    for (int i = 0; i < frames; ++i) {
        int32_t sample; memcpy(&sample, &resampled[i * 4], sizeof(sample));
        if (i > 0 && ((sample >= 0) != (previous >= 0))) ++crossings;
        previous = sample;
        const double normalized = sample / 2147483648.0;
        energy += normalized * normalized;
    }
    assert(std::abs(crossings - frames * 2000.0 / 48000) < 4);
    assert(std::sqrt(energy / frames) > 0.19 && std::sqrt(energy / frames) < 0.24);
    assert(convert(sine, 44100, 1, 16, 48000, 1, 32) == resampled);
    puts("PASS: PCM identity, 16-to-24 widening, stereo downmix, 44.1-to-48 kHz rate/pitch/tail and reset");
}
