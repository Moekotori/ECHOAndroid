#include "ModulationSoundEffects.h"
#include "PairedSoundEffect.h"
#include "third_party/airwindows-modulation/AirwindowsTremolo.h"
#include "third_party/airwindows-modulation/AirwindowsPoint.h"
#include "third_party/signalsmith/dsp/filters.h"
#include "third_party/signalsmith/dsp/delay.h"
#include <array>
#include <cmath>
#include <stdexcept>

namespace echo {
namespace {
constexpr double tau = 6.283185307179586;

class PhaserEffect final : public AdditionalSoundEffect {
public:
    PhaserEffect(double rate, int channels)
        : rate_(rate), increment_(tau * .12 / rate), filters_(channels), feedback_(channels, 0) {}
    void process(double* const* input, double* const* output, int frames) noexcept override {
        for (int i = 0; i < frames; ++i) {
            // Native sample clock; coefficient updates are independent of block size.
            if (controlCountdown_-- == 0) { updateFilters(); controlCountdown_ = 15; }
            for (size_t c = 0; c < filters_.size(); ++c) {
                double wet = input[c][i] + .15 * feedback_[c];
                for (auto& filter : filters_[c]) wet = filter(wet);
                feedback_[c] = wet;
                output[c][i] = .9 * (.55 * input[c][i] + .45 * wet);
            }
            phase_ += increment_;
            if (phase_ >= tau) phase_ -= tau;
        }
    }
    void reset() noexcept override {
        for (auto& channel : filters_) for (auto& filter : channel) filter.reset();
        std::fill(feedback_.begin(), feedback_.end(), 0);
        phase_ = 0; controlCountdown_ = 0;
    }
    int tailSamples() const noexcept override { return static_cast<int>(rate_ * .3); }
private:
    void updateFilters() noexcept {
        constexpr std::array<double, 6> ratios {.55, .8, 1.2, 1.8, 2.6, 3.8};
        const double center = 180 * std::pow(1400.0 / 180, .5 + .5 * std::sin(phase_));
        for (auto& channel : filters_) for (size_t j = 0; j < ratios.size(); ++j)
            channel[j].allpassQ(std::min(.45, center * ratios[j] / rate_), .7,
                signalsmith::filters::BiquadDesign::bilinear);
    }
    double rate_, increment_, phase_ = 0;
    int controlCountdown_ = 0;
    std::vector<std::array<signalsmith::filters::BiquadStatic<double>, 6>> filters_;
    std::vector<double> feedback_;
};

class FlangerEffect final : public AdditionalSoundEffect {
public:
    FlangerEffect(double rate, int channels)
        : rate_(rate), increment_(tau * .18 / rate),
          fadeFrames_(std::max(1, static_cast<int>(std::round(rate * .005)))),
          delay_(channels, static_cast<int>(std::ceil(rate * .006)) + 2), frame_(channels, 0) {}
    void process(double* const* input, double* const* output, int frames) noexcept override {
        for (int i = 0; i < frames; ++i) {
            const double samples = rate_ * (.002 + .0015 * std::sin(phase_));
            const auto delayed = delay_.read(samples);
            // Taper the capture edge as well as the owner's dry/wet activation.
            const double captureMix = std::min(1.0, static_cast<double>(captured_) / fadeFrames_);
            for (size_t c = 0; c < frame_.size(); ++c) {
                const double wet = delayed[static_cast<int>(c)];
                output[c][i] = .92 * (.70 * input[c][i] + .30 * wet);
                frame_[c] = input[c][i] * captureMix + .23 * wet;
            }
            delay_.write(frame_);
            if (captured_ < fadeFrames_) ++captured_;
            phase_ += increment_;
            if (phase_ >= tau) phase_ -= tau;
        }
    }
    void reset() noexcept override {
        delay_.reset(); std::fill(frame_.begin(), frame_.end(), 0); phase_ = 0; captured_ = 0;
    }
    // 30 maximum-delay repeats reduce feedback below -380 dB.
    int tailSamples() const noexcept override { return static_cast<int>(rate_ * .12); }
private:
    double rate_, increment_, phase_ = 0;
    int fadeFrames_, captured_ = 0;
    signalsmith::delay::MultiDelay<double> delay_;
    std::vector<double> frame_;
};
}

bool isModulationSoundEffect(const std::string& preset) noexcept {
    return preset == "tremolo" || preset == "phaser" || preset == "transient" || preset == "flanger";
}
std::unique_ptr<AdditionalSoundEffect> createModulationSoundEffect(const std::string& preset, double rate, int channels) {
    if (preset == "tremolo") return std::make_unique<PairedSoundEffect<AirwindowsTremolo>>(rate, channels, 0.0);
    if (preset == "transient") return std::make_unique<PairedSoundEffect<AirwindowsPoint>>(rate, channels, 0.0);
    if (preset == "phaser") return std::make_unique<PhaserEffect>(rate, channels);
    if (preset == "flanger") return std::make_unique<FlangerEffect>(rate, channels);
    throw std::runtime_error("unknown_sound_effect_preset");
}
}
