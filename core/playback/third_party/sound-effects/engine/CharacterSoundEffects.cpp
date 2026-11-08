#include "CharacterSoundEffects.h"
#include "PairedSoundEffect.h"
#include "third_party/airwindows-character/AirwindowsDeRez3.h"
#include "third_party/airwindows-character/AirwindowsRingModulator.h"
#include "third_party/signalsmith/dsp/filters.h"
#include <array>
#include <cmath>
#include <stdexcept>

namespace echo {
namespace {
using Filter = signalsmith::filters::BiquadStatic<double>;
constexpr auto design = signalsmith::filters::BiquadDesign::bilinear;

class TelephoneEffect final : public AdditionalSoundEffect {
public:
    TelephoneEffect(double rate, int channels) : rate_(rate), filters_(channels) {
        for (auto& channel : filters_) {
            channel[0].highpassQ(300 / rate, .707, design);
            channel[1].lowpassQ(std::min(.45, 3400 / rate), .707, design);
        }
    }
    void process(double* const* input, double* const* output, int frames) noexcept override {
        for (size_t c = 0; c < filters_.size(); ++c) {
            auto& channel = filters_[c];
            for (int i = 0; i < frames; ++i) output[c][i] = .9 * channel[1](channel[0](input[c][i]));
        }
    }
    void reset() noexcept override { for (auto& channel : filters_) for (auto& filter : channel) filter.reset(); }
    int tailSamples() const noexcept override { return static_cast<int>(rate_ * .12); }
private:
    double rate_;
    std::vector<std::array<Filter, 2>> filters_;
};

class AutoWahEffect final : public AdditionalSoundEffect {
public:
    AutoWahEffect(double rate, int channels) : rate_(rate),
        attack_(1 - std::exp(-1 / (rate * .008))), release_(1 - std::exp(-1 / (rate * .11))), filters_(channels) {}
    void process(double* const* input, double* const* output, int frames) noexcept override {
        for (int i = 0; i < frames; ++i) {
            // Link the native input envelope to preserve stereo balance.
            double peak = 0;
            for (size_t c = 0; c < filters_.size(); ++c) peak = std::max(peak, std::abs(input[c][i]));
            envelope_ += (peak - envelope_) * (peak > envelope_ ? attack_ : release_);
            if (controlCountdown_-- == 0) {
                const double frequency = 350 * std::pow(2200.0 / 350, std::min(1.0, envelope_ * 3));
                for (auto& filter : filters_) filter.bandpassQ(std::min(.45, frequency / rate_), 1.3, design);
                controlCountdown_ = 15;
            }
            for (size_t c = 0; c < filters_.size(); ++c)
                output[c][i] = .95 * (.65 * input[c][i] + .35 * filters_[c](input[c][i]));
        }
    }
    void reset() noexcept override {
        for (auto& filter : filters_) filter.reset();
        envelope_ = 0; controlCountdown_ = 0;
    }
    int tailSamples() const noexcept override { return static_cast<int>(rate_ * .12); }
private:
    double rate_, attack_, release_, envelope_ = 0;
    int controlCountdown_ = 0;
    std::vector<Filter> filters_;
};
}

bool isCharacterSoundEffect(const std::string& preset) noexcept {
    return preset == "telephone" || preset == "digital" || preset == "autowah" || preset == "ringmod";
}
std::unique_ptr<AdditionalSoundEffect> createCharacterSoundEffect(const std::string& preset, double rate, int channels) {
    if (preset == "telephone") return std::make_unique<TelephoneEffect>(rate, channels);
    if (preset == "digital") return std::make_unique<PairedSoundEffect<AirwindowsDeRez3>>(rate, channels, .02);
    if (preset == "autowah") return std::make_unique<AutoWahEffect>(rate, channels);
    if (preset == "ringmod") return std::make_unique<PairedSoundEffect<AirwindowsRingModulator>>(rate, channels, 0.0);
    throw std::runtime_error("unknown_sound_effect_preset");
}
}
