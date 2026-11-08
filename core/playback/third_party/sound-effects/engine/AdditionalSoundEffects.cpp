#include "AdditionalSoundEffects.h"
#include "ListeningColorEffects.h"
#include "ModulationSoundEffects.h"
#include "CharacterSoundEffects.h"
#include "Bs2bCrossfeed.h"
#include "third_party/airwindows-tape/AirwindowsTape.h"
#include "third_party/signalsmith/basics/include/signalsmith-basics/dynamics.h"
#include <algorithm>
#include <cmath>
#include <stdexcept>
#include <vector>

namespace echo {
namespace {
class TapeEffect final : public AdditionalSoundEffect {
public:
    TapeEffect(double rate, int channels) : channels_(channels), tail_(static_cast<int>(rate)) {
        pairs_.reserve((channels + 1) / 2);
        for (int c = 0; c < channels; c += 2) pairs_.emplace_back(rate);
    }
    void process(double* const* input, double* const* output, int frames) noexcept override {
        for (int c = 0; c < channels_; c += 2) {
            double* inputs[2] {input[c], input[std::min(c + 1, channels_ - 1)]};
            // Runtime reserves one extra channel for an odd final pair.
            double* outputs[2] {output[c], output[c + 1]};
            pairs_[c / 2].process(inputs, outputs, frames);
        }
        for (int c = 0; c < channels_; ++c)
            for (int i = 0; i < frames; ++i) output[c][i] *= 0.95;
    }
    void reset() noexcept override { for (auto& pair : pairs_) pair.reset(); }
    int tailSamples() const noexcept override { return tail_; }
private:
    int channels_, tail_;
    std::vector<AirwindowsTape> pairs_;
};

class NightEffect final : public AdditionalSoundEffect {
public:
    NightEffect(double rate, int block, int channels) : channels_(channels) {
        dynamics_.compressor.limitDb = -26;
        dynamics_.compressor.invRatio = 0.3;
        dynamics_.compressor.autoGain = 0;
        dynamics_.compressor.makeupDb = 6;
        dynamics_.expander.ratio = 1;
        dynamics_.gate.limitDb = -160;
        dynamics_.timing.lookaheadMs = 0;
        dynamics_.timing.attackMs = 8;
        dynamics_.timing.releaseMs = 180;
        if (!dynamics_.configure(rate, block, channels)) throw std::runtime_error("sound_effect_configuration_failed");
    }
    void process(double* const* input, double* const* output, int frames) noexcept override {
        dynamics_.process(input, output, frames);
        for (int c = 0; c < channels_; ++c)
            for (int i = 0; i < frames; ++i) output[c][i] *= 0.9;
    }
    void reset() noexcept override { dynamics_.reset(); }
private:
    int channels_;
    signalsmith::basics::DynamicsDouble dynamics_;
};

class HeadphonesEffect final : public AdditionalSoundEffect {
public:
    HeadphonesEffect(double rate, int channels) : channels_(channels), tail_(static_cast<int>(std::ceil(rate * .05))), pairs_(channels / 2) {
        for (auto& pair : pairs_) pair.configure(rate, 700, 6);
    }
    void process(double* const* input, double* const* output, int frames) noexcept override {
        for (int c = 0; c < channels_; ++c) std::copy_n(input[c], frames, output[c]);
        for (int c = 0; c + 1 < channels_; c += 2)
            for (int i = 0; i < frames; ++i) pairs_[c / 2].process(output[c][i], output[c + 1][i]);
    }
    void reset() noexcept override { for (auto& pair : pairs_) pair.reset(); }
    int tailSamples() const noexcept override { return tail_; }
private:
    int channels_, tail_;
    std::vector<Bs2bCrossfeed> pairs_;
};

class ShortEchoEffect final : public AdditionalSoundEffect {
public:
    ShortEchoEffect(double rate, int channels) : channels_(channels), delayFrames_(static_cast<int>(std::round(rate * .12))),
        fadeFrames_(std::max(1, static_cast<int>(std::round(rate * .005)))), history_(static_cast<size_t>(channels) * delayFrames_, 0) {}
    void process(double* const* input, double* const* output, int frames) noexcept override {
        for (int i = 0; i < frames; ++i) {
            // A delay enabled mid-wave must not reveal a hard-edged recording
            // boundary when its first repeat reaches the output 120 ms later.
            const double captureMix = std::min(1.0, static_cast<double>(writtenFrames_) / fadeFrames_);
            for (int c = 0; c < channels_; ++c) {
                auto& delayed = history_[static_cast<size_t>(c) * delayFrames_ + position_];
                output[c][i] = input[c][i] * .9 + delayed * .22;
                delayed = input[c][i] * captureMix + delayed * .24;
            }
            if (++position_ == delayFrames_) position_ = 0;
            if (writtenFrames_ < fadeFrames_) ++writtenFrames_;
        }
    }
    void reset() noexcept override { std::fill(history_.begin(), history_.end(), 0); position_ = 0; writtenFrames_ = 0; }
    // Twelve repeats put the feedback below -140 dB; this bounds EOF flushing.
    int tailSamples() const noexcept override { return delayFrames_ * 12; }
private:
    int channels_, delayFrames_, fadeFrames_, position_ = 0, writtenFrames_ = 0;
    std::vector<double> history_;
};
}

bool isAdditionalSoundEffect(const std::string& preset) noexcept {
    return preset == "tape" || preset == "night" || preset == "headphones" || preset == "echo"
        || isListeningColorEffect(preset) || isModulationSoundEffect(preset) || isCharacterSoundEffect(preset);
}
std::unique_ptr<AdditionalSoundEffect> createAdditionalSoundEffect(const std::string& preset, double rate, int block, int channels) {
    if (preset == "tape") return std::make_unique<TapeEffect>(rate, channels);
    if (preset == "night") return std::make_unique<NightEffect>(rate, block, channels);
    if (preset == "headphones") return std::make_unique<HeadphonesEffect>(rate, channels);
    if (preset == "echo") return std::make_unique<ShortEchoEffect>(rate, channels);
    if (isListeningColorEffect(preset)) return createListeningColorEffect(preset, rate, channels);
    if (isModulationSoundEffect(preset)) return createModulationSoundEffect(preset, rate, channels);
    if (isCharacterSoundEffect(preset)) return createCharacterSoundEffect(preset, rate, channels);
    throw std::runtime_error("unknown_sound_effect_preset");
}
}
