#include "DeEsserProcessor.h"

#include "DynamicsReduction.h"

#include <algorithm>
#include <cmath>

namespace echo
{
namespace
{
constexpr double minimumFrequencyHz = 2000.0;
constexpr double maximumFrequencyHz = 12000.0;
constexpr double minimumQ = 0.5;
constexpr double maximumQ = 12.0;
constexpr double minimumThresholdDb = -48.0;
constexpr double maximumThresholdDb = 0.0;
constexpr double minimumRatio = 1.0;
constexpr double maximumRatio = 20.0;
constexpr double minimumAttackMs = 0.1;
constexpr double maximumAttackMs = 30.0;
constexpr double minimumReleaseMs = 5.0;
constexpr double maximumReleaseMs = 200.0;
constexpr double minimumKneeDb = 0.0;
constexpr double maximumKneeDb = 18.0;
constexpr double minimumRangeDb = 0.0;
constexpr double maximumRangeDb = 24.0;
constexpr double minimumDetectorLevel = 1.0e-9;
constexpr double telemetryFloorDb = -96.0;
constexpr double parameterSmoothingMs = 20.0;
constexpr double bypassSmoothingMs = 8.0;

double clampFinite(double value, double minimum, double maximum, double fallback)
{
    return std::isfinite(value) ? std::clamp<double>(value, minimum, maximum) : fallback;
}

double dbToGain(double value)
{
    return std::pow(10.0, value / 20.0);
}

double gainToDb(double value)
{
    return 20.0 * std::log10(std::max<double>(value, minimumDetectorLevel));
}

double smoothingCoefficient(double milliseconds, double sampleRate)
{
    const double samples = std::max<double>(1.0, sampleRate * static_cast<double>(milliseconds) / 1000.0);
    return static_cast<double>(std::exp(-1.0 / samples));
}
} // namespace

DeEsserProcessor::DeEsserProcessor()
{
    reset();
}

void DeEsserProcessor::prepare(double sampleRate, int, int)
{
    sampleRate_ = std::max<double>(1.0, sampleRate);
    appliedFrequencyHz_ = 0.0f;
    appliedQ_ = 0.0f;
    reset();
}

void DeEsserProcessor::reset()
{
    const auto state = getState();
    for (auto& filter : bandpass_)
        filter.reset();
    smoothedGain_.fill(1.0f);
    smoothedFrequencyHz_ = state.frequencyHz;
    smoothedQ_ = state.q;
    smoothedThresholdDb_ = state.thresholdDb;
    smoothedRatio_ = state.ratio;
    smoothedMix_ = state.mix;
    smoothedKneeDb_ = state.kneeDb;
    smoothedRangeDb_ = state.rangeDb;
    bypassMix_ = state.enabled ? 1.0f : 0.0f;
    listenMix_ = 0.0f;
    processingActive_.store(state.enabled || isListening(), std::memory_order_release);
    gainReductionDb_.store(0.0f, std::memory_order_release);
    appliedFrequencyHz_ = 0.0f;
    appliedQ_ = 0.0f;
}

void DeEsserProcessor::setState(const DeEsserState& state)
{
    const auto safe = sanitizeState(state);
    enabled_.store(safe.enabled, std::memory_order_release);
    frequencyHz_.store(safe.frequencyHz, std::memory_order_release);
    q_.store(safe.q, std::memory_order_release);
    thresholdDb_.store(safe.thresholdDb, std::memory_order_release);
    ratio_.store(safe.ratio, std::memory_order_release);
    attackMs_.store(safe.attackMs, std::memory_order_release);
    releaseMs_.store(safe.releaseMs, std::memory_order_release);
    mix_.store(safe.mix, std::memory_order_release);
    kneeDb_.store(safe.kneeDb, std::memory_order_release);
    rangeDb_.store(safe.rangeDb, std::memory_order_release);
}

DeEsserState DeEsserProcessor::getState() const
{
    return sanitizeState({
        enabled_.load(std::memory_order_acquire),
        frequencyHz_.load(std::memory_order_acquire),
        q_.load(std::memory_order_acquire),
        thresholdDb_.load(std::memory_order_acquire),
        ratio_.load(std::memory_order_acquire),
        attackMs_.load(std::memory_order_acquire),
        releaseMs_.load(std::memory_order_acquire),
        mix_.load(std::memory_order_acquire),
        kneeDb_.load(std::memory_order_acquire),
        rangeDb_.load(std::memory_order_acquire),
    });
}

void DeEsserProcessor::setListen(bool listen)
{
    listening_.store(listen, std::memory_order_release);
}

bool DeEsserProcessor::isListening() const
{
    return listening_.load(std::memory_order_acquire);
}

bool DeEsserProcessor::isEnabled() const
{
    return enabled_.load(std::memory_order_acquire);
}

bool DeEsserProcessor::isProcessingActive() const
{
    return processingActive_.load(std::memory_order_acquire);
}

float DeEsserProcessor::gainReductionDb() const
{
    return gainReductionDb_.load(std::memory_order_acquire);
}

DeEsserState DeEsserProcessor::sanitizeState(const DeEsserState& state)
{
    DeEsserState safe;
    safe.enabled = state.enabled;
    safe.frequencyHz = clampFinite(state.frequencyHz, minimumFrequencyHz, maximumFrequencyHz, 6500.0f);
    safe.q = clampFinite(state.q, minimumQ, maximumQ, 4.0f);
    safe.thresholdDb = clampFinite(state.thresholdDb, minimumThresholdDb, maximumThresholdDb, -20.0f);
    safe.ratio = clampFinite(state.ratio, minimumRatio, maximumRatio, 4.0f);
    safe.attackMs = clampFinite(state.attackMs, minimumAttackMs, maximumAttackMs, 1.0f);
    safe.releaseMs = clampFinite(state.releaseMs, minimumReleaseMs, maximumReleaseMs, 40.0f);
    safe.mix = clampFinite(state.mix, 0.0f, 1.0f, 1.0f);
    safe.kneeDb = clampFinite(state.kneeDb, minimumKneeDb, maximumKneeDb, 6.0f);
    safe.rangeDb = clampFinite(state.rangeDb, minimumRangeDb, maximumRangeDb, 24.0f);
    return safe;
}

template <typename Sample>
void DeEsserProcessor::processBlock(echo::PlanarAudioBuffer<Sample>& buffer, int startSample, int numSamples)
{
    if (numSamples <= 0)
        return;

    const auto state = getState();
    const bool listening = isListening();
    if (!state.enabled && !listening && !processingActive_.load(std::memory_order_acquire))
        return;

    const int channelCount = std::clamp<double>(buffer.getNumChannels(), 0, maximumChannels);
    if (channelCount <= 0)
        return;

    const double parameterCoefficient = smoothingCoefficient(parameterSmoothingMs, sampleRate_);
    const double bypassCoefficient = smoothingCoefficient(bypassSmoothingMs, sampleRate_);
    const double attackCoefficient = smoothingCoefficient(state.attackMs, sampleRate_);
    const double releaseCoefficient = smoothingCoefficient(state.releaseMs, sampleRate_);
    double peakReductionDb = 0.0;

    for (int sample = 0; sample < numSamples; ++sample)
    {
        smoothedFrequencyHz_ = parameterCoefficient * smoothedFrequencyHz_
            + (1.0 - parameterCoefficient) * state.frequencyHz;
        smoothedQ_ = parameterCoefficient * smoothedQ_ + (1.0 - parameterCoefficient) * state.q;
        smoothedThresholdDb_ = parameterCoefficient * smoothedThresholdDb_
            + (1.0 - parameterCoefficient) * state.thresholdDb;
        smoothedRatio_ = parameterCoefficient * smoothedRatio_
            + (1.0 - parameterCoefficient) * state.ratio;
        smoothedMix_ = parameterCoefficient * smoothedMix_
            + (1.0 - parameterCoefficient) * state.mix;
        smoothedKneeDb_ = parameterCoefficient * smoothedKneeDb_
            + (1.0 - parameterCoefficient) * state.kneeDb;
        smoothedRangeDb_ = parameterCoefficient * smoothedRangeDb_
            + (1.0 - parameterCoefficient) * state.rangeDb;
        bypassMix_ = bypassCoefficient * bypassMix_
            + (1.0 - bypassCoefficient) * (state.enabled ? 1.0 : 0.0);
        listenMix_ = bypassCoefficient * listenMix_
            + (1.0 - bypassCoefficient) * (listening ? 1.0 : 0.0);

        if (std::abs(smoothedFrequencyHz_ - appliedFrequencyHz_) > 0.5
            || std::abs(smoothedQ_ - appliedQ_) > 0.01)
        {
            bandpassCoefficients_ = makeBandpassCoefficients(sampleRate_, smoothedFrequencyHz_, smoothedQ_);
            appliedFrequencyHz_ = smoothedFrequencyHz_;
            appliedQ_ = smoothedQ_;
        }

        for (int channel = 0; channel < channelCount; ++channel)
        {
            auto* samples = buffer.getWritePointer(channel, startSample);
            const double input = samples[sample];
            const double band = bandpass_[static_cast<size_t>(channel)].processDouble(input, bandpassCoefficients_);
            const double requestedReductionDb = std::min<double>(
                smoothedRangeDb_,
                computeDynamicsReductionDb(
                    std::max<double>(telemetryFloorDb, gainToDb(std::abs(band))),
                    smoothedThresholdDb_,
                    smoothedRatio_,
                    smoothedKneeDb_));
            const double targetGain = dbToGain(-requestedReductionDb);
            const double coefficient = targetGain < smoothedGain_[static_cast<size_t>(channel)]
                ? attackCoefficient
                : releaseCoefficient;
            smoothedGain_[static_cast<size_t>(channel)] = coefficient * smoothedGain_[static_cast<size_t>(channel)]
                + (1.0 - coefficient) * targetGain;
            const double reductionDb = std::max<double>(0.0, -gainToDb(smoothedGain_[static_cast<size_t>(channel)])) * bypassMix_;
            peakReductionDb = std::max<double>(peakReductionDb, reductionDb);

            const double wetAmount = std::clamp<double>(smoothedMix_ * bypassMix_, 0.0, 1.0);
            const double attenuatedBand = band * smoothedGain_[static_cast<size_t>(channel)];
            const double processed = input + (attenuatedBand - band) * wetAmount;
            const double output = processed * (1.0 - listenMix_) + band * listenMix_;
            samples[sample] = std::isfinite(output) ? output : 0.0;
        }
    }

    gainReductionDb_.store(peakReductionDb, std::memory_order_release);
    const bool stillProcessing = state.enabled || listening || bypassMix_ > 0.0001 || listenMix_ > 0.0001;
    processingActive_.store(stillProcessing, std::memory_order_release);
    if (!stillProcessing)
    {
        smoothedGain_.fill(1.0);
        gainReductionDb_.store(0.0, std::memory_order_release);
    }
}

template void DeEsserProcessor::processBlock<float>(echo::PlanarAudioBuffer<float>&, int, int);
template void DeEsserProcessor::processBlock<double>(echo::PlanarAudioBuffer<double>&, int, int);
} // namespace echo
