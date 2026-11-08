#include "EchoSoundEffectEngine.h"
#include "AdditionalSoundEffects.h"
#include "EqProcessor.h"
#include "DeEsserProcessor.h"
#include "third_party/signalsmith/basics/include/signalsmith-basics/reverb.h"
#include "third_party/signalsmith/basics/include/signalsmith-basics/chorus.h"
#include "third_party/signalsmith/basics/include/signalsmith-basics/crunch.h"
#include "third_party/signalsmith/basics/include/signalsmith-basics/dynamics.h"
#include <array>
#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace echo_android {
// Preset parameters and algorithms follow ECHOSteam a8f1bd0091db9fd103144909ff36257d6de54b2f.
// Android adapter owns interleaving, bounded blocks and click-free activation/deactivation.
struct SoundEffectEngine::Runtime {
    static constexpr int block = 1024;
    int rate, channels, roundedChannels, tailLength = 0, remaining = 0, latency = 0;
    double gain = 1, wet = 0, step;
    echo::DoubleAudioBuffer input, output;
    std::array<double*, 8> inputs {}, outputs {};
    std::unique_ptr<echo::EqProcessor> eq;
    std::unique_ptr<echo::DeEsserProcessor> deesser;
    std::unique_ptr<echo::AdditionalSoundEffect> additional;
    std::vector<std::unique_ptr<signalsmith::basics::ReverbDouble>> reverbs;
    std::unique_ptr<signalsmith::basics::ChorusDouble> chorus;
    std::unique_ptr<signalsmith::basics::CrunchDouble> crunch;
    std::unique_ptr<signalsmith::basics::DynamicsDouble> dynamics;

    Runtime(const std::string& preset, int sampleRate, int channelCount)
        : rate(sampleRate), channels(channelCount), roundedChannels(channelCount + channelCount % 2),
          step(1.0 / std::max(1.0, sampleRate * .005)), input(roundedChannels, block), output(roundedChannels, block) {
        input.setActiveChannels(channels);
        for (int c = 0; c < roundedChannels; ++c) {
            inputs[c] = input.getWritePointer(c); outputs[c] = output.getWritePointer(c);
        }
        if (echo::isAdditionalSoundEffect(preset)) {
            additional = echo::createAdditionalSoundEffect(preset, rate, block, channels);
            tailLength = additional->tailSamples(); latency = additional->latencySamples();
        } else if (preset == "deess") {
            deesser = std::make_unique<echo::DeEsserProcessor>();
            echo::DeEsserState state; state.enabled = true; state.frequencyHz = 6500; state.q = 2.5f;
            state.thresholdDb = -26; state.ratio = 4; state.attackMs = 1; state.releaseMs = 70; state.rangeDb = 8;
            deesser->setState(state); deesser->prepare(rate, block, channels); deesser->reset();
            tailLength = static_cast<int>(rate * .2);
        } else if (preset == "vocal" || preset == "bass") {
            eq = std::make_unique<echo::EqProcessor>();
            echo::EqState state; state.enabled = true; state.bandEnabled.fill(false);
            const auto band = [&](int i, echo::EqFilterType type, float hz, float db) {
                state.bandEnabled[i] = true; state.bandFilterTypes[i] = type;
                state.bandFrequenciesHz[i] = hz; state.bandGainsDb[i] = db; state.bandQ[i] = .7f;
            };
            if (preset == "vocal") {
                band(0, echo::EqFilterType::Peaking, 220, -1.8f); band(1, echo::EqFilterType::Peaking, 2800, 1.8f);
                gain = std::pow(10.0, -1.5 / 20);
            } else {
                band(0, echo::EqFilterType::LowShelf, 95, 3.5f); band(1, echo::EqFilterType::Peaking, 260, -1);
                gain = std::pow(10.0, -3.0 / 20);
            }
            eq->setState(state); eq->prepare(rate, block, channels); eq->reset();
            dynamics = std::make_unique<signalsmith::basics::DynamicsDouble>();
            dynamics->compressor.limitDb = -16; dynamics->compressor.invRatio = preset == "vocal" ? .8 : .7;
            dynamics->compressor.autoGain = 0; dynamics->expander.ratio = 1; dynamics->gate.limitDb = -160;
            dynamics->timing.lookaheadMs = 0; dynamics->timing.attackMs = 20; dynamics->timing.releaseMs = 150;
            if (!dynamics->configure(rate, block, channels)) throw std::runtime_error("effect_configuration");
            tailLength = static_cast<int>(rate * .2);
        } else if (preset == "warm") {
            crunch = std::make_unique<signalsmith::basics::CrunchDouble>();
            crunch->drive = 1.15; crunch->fuzz = 0; crunch->cutHz = 20; crunch->toneHz = 14000; crunch->outGain = .9;
            if (!crunch->configure(rate, block, channels)) throw std::runtime_error("effect_configuration");
            latency = crunch->latencySamples(); tailLength = latency * 2;
        } else if (preset == "wide") {
            chorus = std::make_unique<signalsmith::basics::ChorusDouble>();
            chorus->mix = .12; chorus->depthMs = 6; chorus->detune = 1.2; chorus->stereo = 1;
            if (!chorus->configure(rate, block, channels)) throw std::runtime_error("effect_configuration");
            tailLength = static_cast<int>(rate * .1); gain = .95;
        } else if (preset == "live" || preset == "hall") {
            for (int c = 0; c < channels; c += 2) {
                auto reverb = std::make_unique<signalsmith::basics::ReverbDouble>(80);
                reverb->dry = .96; reverb->wet = preset == "live" ? .12 : .18;
                reverb->roomMs = preset == "live" ? 30 : 65; reverb->rt20 = preset == "live" ? .22 : .65;
                reverb->early = 1.2; reverb->lowCutHz = 110; reverb->highCutHz = 10000;
                if (!reverb->configure(rate, block, 2)) throw std::runtime_error("effect_configuration");
                tailLength = std::max(tailLength, reverb->tailSamples()); reverbs.push_back(std::move(reverb));
            }
            gain = .95;
        } else throw std::invalid_argument("unknown_sound_effect");
        tailLength = std::clamp(tailLength, 0, rate * 10);
    }

    void process(float* samples, int frames, bool enabled) noexcept {
        bool hasInput = false;
        for (int c = 0; c < roundedChannels; ++c) for (int i = 0; i < frames; ++i) {
            const float x = samples[i * channels + std::min(c, channels - 1)];
            inputs[c][i] = std::isfinite(x) ? x : 0;
            hasInput = hasInput || std::abs(inputs[c][i]) > 1e-12;
        }
        if (eq) eq->processBlock(input, 0, frames);
        if (deesser) {
            deesser->processBlock(input, 0, frames);
            for (int c = 0; c < channels; ++c) std::copy_n(inputs[c], frames, outputs[c]);
        } else if (dynamics) dynamics->process(inputs, outputs, frames);
        else if (crunch) crunch->process(inputs, outputs, frames);
        else if (chorus) chorus->process(inputs, outputs, frames);
        else if (additional) additional->process(inputs.data(), outputs.data(), frames);
        else for (size_t pair = 0; pair < reverbs.size(); ++pair)
            reverbs[pair]->process(inputs.data() + pair * 2, outputs.data() + pair * 2, frames);
        for (int i = 0; i < frames; ++i) {
            wet = std::clamp(wet + (enabled ? step : -step), 0.0, 1.0);
            for (int c = 0; c < channels; ++c) {
                const double value = !reverbs.empty() && c == channels - 1 && channels % 2
                    ? .5 * (outputs[c][i] + outputs[c + 1][i]) : outputs[c][i];
                const float original = samples[i * channels + c];
                const double dry = std::isfinite(original) ? original : 0;
                const double out = dry + (value * gain - dry) * wet;
                samples[i * channels + c] = std::isfinite(out) ? static_cast<float>(out) : 0;
            }
        }
        remaining = hasInput ? tailLength : std::max(0, remaining - frames);
    }
    void reset() noexcept {
        if (eq) eq->reset(); if (deesser) deesser->reset(); if (additional) additional->reset();
        if (dynamics) dynamics->reset(); if (crunch) crunch->reset(); if (chorus) chorus->reset();
        for (auto& reverb : reverbs) reverb->reset();
        remaining = 0; wet = 0;
    }
};

SoundEffectEngine::SoundEffectEngine(const std::string& preset, int rate, int channels) {
    if (rate < 8000 || rate > 192000 || channels < 1 || channels > 8) throw std::invalid_argument("unsupported_format");
    runtime_ = std::make_unique<Runtime>(preset, rate, channels);
}
SoundEffectEngine::~SoundEffectEngine() = default;
void SoundEffectEngine::process(float* samples, int frames, bool enabled) noexcept {
    if (samples && frames > 0 && frames <= Runtime::block) runtime_->process(samples, frames, enabled);
}
void SoundEffectEngine::reset() noexcept { runtime_->reset(); }
int SoundEffectEngine::tailRemaining() const noexcept { return runtime_->remaining; }
int SoundEffectEngine::latencyFrames() const noexcept { return runtime_->latency; }
bool SoundEffectEngine::dry() const noexcept { return runtime_->wet == 0; }
int SoundEffectEngine::channelCount() const noexcept { return runtime_->channels; }
}
