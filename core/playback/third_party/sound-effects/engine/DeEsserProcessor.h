#pragma once

#include "EqBand.h"
#include "buffer.h"

#include <array>
#include <atomic>

namespace echo
{
struct DeEsserState
{
    bool operator==(const DeEsserState&) const = default;
    bool enabled = false;
    float frequencyHz = 6500.0f;
    float q = 4.0f;
    float thresholdDb = -20.0f;
    float ratio = 4.0f;
    float attackMs = 1.0f;
    float releaseMs = 40.0f;
    float mix = 1.0f;
    float kneeDb = 6.0f;
    float rangeDb = 24.0f;
};

class DeEsserProcessor final
{
public:
    DeEsserProcessor();
    void prepare(double sampleRate, int maximumBlockSize, int channelCount);
    void reset();
    template <typename Sample>
    void processBlock(echo::PlanarAudioBuffer<Sample>& buffer, int startSample, int numSamples);

    void setState(const DeEsserState& state);
    DeEsserState getState() const;
    void setListen(bool listen);
    bool isListening() const;
    bool isEnabled() const;
    bool isProcessingActive() const;
    float gainReductionDb() const;

private:
    friend struct DspPreparedRuntime;
    static DeEsserState sanitizeState(const DeEsserState& state);

    std::atomic<bool> enabled_ { false };
    std::atomic<float> frequencyHz_ { 6500.0f };
    std::atomic<float> q_ { 4.0f };
    std::atomic<float> thresholdDb_ { -20.0f };
    std::atomic<float> ratio_ { 4.0f };
    std::atomic<float> attackMs_ { 1.0f };
    std::atomic<float> releaseMs_ { 40.0f };
    std::atomic<float> mix_ { 1.0f };
    std::atomic<float> kneeDb_ { 6.0f };
    std::atomic<float> rangeDb_ { 24.0f };
    std::atomic<bool> listening_ { false };
    std::atomic<bool> processingActive_ { false };
    std::atomic<float> gainReductionDb_ { 0.0f };

    double sampleRate_ = 48000.0;
    static constexpr int maximumChannels = 32;
    std::array<BiquadState, maximumChannels> bandpass_ {};
    std::array<double, maximumChannels> smoothedGain_ {};
    BiquadCoefficients bandpassCoefficients_ {};
    double smoothedFrequencyHz_ = 6500.0;
    double smoothedQ_ = 4.0;
    double smoothedThresholdDb_ = -20.0;
    double smoothedRatio_ = 4.0;
    double smoothedMix_ = 1.0;
    double smoothedKneeDb_ = 6.0;
    double smoothedRangeDb_ = 24.0;
    double bypassMix_ = 0.0;
    double listenMix_ = 0.0;
    double appliedFrequencyHz_ = 0.0;
    double appliedQ_ = 0.0;
};
} // namespace echo
