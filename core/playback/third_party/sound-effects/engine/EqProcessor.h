#pragma once

#include "EqBand.h"
#include "EqAuditionProcessor.h"
#include "EqSvf.h"
#include "EqTypes.h"
#include "HeadphoneCorrectionParameters.h"
#include "DspMeasurement.h"

#include "buffer.h"

#include <array>
#include <atomic>
#include <cstdint>
#include <vector>

namespace echo
{
class EqProcessor
{
public:
    explicit EqProcessor(bool preserveOpraParameters = false, bool localSafetyLimiter = true);

    void prepare(double sampleRate, int maximumBlockSize, int channelCount);
    void reset();
    template <typename Sample>
    void processBlock(echo::PlanarAudioBuffer<Sample>& buffer, int startSample, int numSamples);
    int64_t tailFramesRemaining() const noexcept;
    bool isTailDrained() const noexcept { return tailFramesRemaining() == 0; }

    void setEnabled(bool shouldBeEnabled);
    void setPreampDb(float value);
    void setStrengthPercent(float value);
    void setTiltDb(float value);
    bool setBandGainDb(int bandIndex, float value);
    bool setBandFrequencyHz(int bandIndex, float value);
    bool setBandQ(int bandIndex, float value);
    bool setBandFilterType(int bandIndex, EqFilterType value);
    bool setBandEnabled(int bandIndex, bool value);
    bool setBandProcessing(
        int bandIndex,
        EqBandScope scope,
        EqDynamicMode dynamicMode,
        float thresholdDb,
        float ratio,
        float attackMs,
        float releaseMs);
    void resetFlat();
    void setState(const EqState& state);
    EqState getState() const;

    bool isEnabled() const;
    bool setAudition(bool active, float frequencyHz, float q) { return audition.set(active, frequencyHz, q); }
    bool isAuditionActive() const { return audition.isActive(); }
    bool isAuditionProcessing() const { return audition.isProcessing(); }
    bool hasClippingRisk() const;
    DspMeasurement loudnessMeasurement() const noexcept { return loudnessStore.read(); }
    void requestLoudnessMeasurement() noexcept { loudnessStore.requestLoudness(); }
    bool isLoudnessMeasurementRequested() const noexcept { return loudnessStore.loudnessLeaseActive(); }
    uint64_t measurementRevision() const noexcept { return loudnessStore.revision(); }

#if defined(ECHO_AUDIO_ENGINE_TESTS) && ECHO_AUDIO_ENGINE_TESTS
    uint64_t getCoefficientUpdateCountForTests() const;
#endif

private:
    friend struct DspPreparedRuntime;
    const bool preserveOpraParameters;
    const bool localSafetyLimiter;
    EqAuditionProcessor audition;
    DspLoudnessMeter loudnessMeter;
    DspMeasurementStore loudnessStore;
    struct ChannelState
    {
        std::array<EqBandFilter, eqBandCount> filters;
    };

    void updateSmoothingSteps();
    void updateTargetSnapshot();
    void updateBandCoefficient(int bandIndex);
    void refreshDynamicTiming(int bandIndex);
    template <typename Sample>
    void processScopedSample(
        echo::PlanarAudioBuffer<Sample>& buffer,
        int startSample,
        int sample,
        int channelCount,
        double preampGain,
        bool& risk);

    double currentSampleRate = 44100.0;
    int preparedChannels = 0;
    int preparedBlockSize = 0;
    int gainSmoothingSamples = 1;
    int bypassSmoothingSamples = 1;
    bool wasEnabled = false;
    double smoothedPreampDb = 0.0;
    double targetPreampDb = 0.0;
    double preampStepDb = 0.0;
    double bypassMix = 0.0;
    double targetBypassMix = 0.0;
    double bypassStep = 0.0;
    EqGainArray smoothedBandGains {};
    EqGainArray targetBandGains {};
    EqGainArray bandGainSteps {};
    EqFrequencyArray smoothedBandFrequencies {};
    EqFrequencyArray targetBandFrequencies {};
    EqFrequencyArray bandFrequencySteps {};
    EqQArray smoothedBandQ {};
    EqQArray targetBandQ {};
    EqQArray bandQSteps {};
    EqFilterTypeArray smoothedBandFilterTypes {};
    EqBandEnabledArray smoothedBandEnabled {};
    EqBandScopeArray currentBandScopes {};
    std::array<bool, eqBandCount> blockDynamicActive {};
    std::array<double, eqBandCount> dynamicOffsetDb {};
    struct DynamicDetector
    {
        std::array<BiquadState, 2> sidechain {};
        BiquadCoefficients coefficients {};
        double envelope = 0.0;
        double attackCoefficient = 0.0;
        double releaseCoefficient = 1.0;
        double thresholdDb = eqDynamicThresholdDefaultDb;
        double ratio = eqDynamicRatioDefault;
        EqDynamicMode mode = EqDynamicMode::Off;
    };
    std::array<DynamicDetector, eqBandCount> dynamicDetectors {};
    std::array<EqBandFilter, eqBandCount> midFilters {};
    std::array<EqBandFilter, eqBandCount> sideFilters {};
    std::array<EqBandCoefficients, eqBandCount> coefficients;
#if defined(ECHO_AUDIO_ENGINE_TESTS) && ECHO_AUDIO_ENGINE_TESTS
    uint64_t coefficientUpdateCount = 0;
#endif
    std::vector<ChannelState> channelStates;
    // One sample per prepared channel, allocated only on the control path.
    // Scales with the existing channel states: 256 bytes for 32 channels.
    std::vector<double> scopedWet;

    std::atomic<bool> targetEnabled { false };
    std::atomic<float> atomicStrengthPercent { 100.0f };
    std::atomic<float> atomicTiltDb { 0.0f };
    std::atomic<float> atomicPreampDb { 0.0f };
    std::array<std::atomic<float>, eqBandCount> atomicBandGainsDb;
    std::array<std::atomic<float>, eqBandCount> atomicBandFrequenciesHz;
    std::array<std::atomic<float>, eqBandCount> atomicBandQ;
    std::array<std::atomic<int>, eqBandCount> atomicBandFilterTypes;
    std::array<std::atomic<bool>, eqBandCount> atomicBandEnabled;
    std::array<std::atomic<int>, eqBandCount> atomicBandScopes;
    std::array<std::atomic<int>, eqBandCount> atomicBandDynamicModes;
    std::array<std::atomic<float>, eqBandCount> atomicBandDynamicThresholdDb;
    std::array<std::atomic<float>, eqBandCount> atomicBandDynamicRatio;
    std::array<std::atomic<float>, eqBandCount> atomicBandDynamicAttackMs;
    std::array<std::atomic<float>, eqBandCount> atomicBandDynamicReleaseMs;
    std::atomic<bool> clippingRisk { false };
    std::atomic<bool> tailActive { false };
};
} // namespace echo
