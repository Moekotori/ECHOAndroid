#include "EqProcessor.h"
#include "DspSafetyLimiter.h"

#include <algorithm>
#include <cmath>

namespace echo
{
namespace
{
bool nearlyEqual(double left, double right)
{
    return std::abs(left - right) <= 0.00001;
}

double dbToGain(double db)
{
    return std::pow(10.0, static_cast<double>(db) / 20.0);
}

double moveTowards(double current, double target, double step)
{
    if (std::abs(target - current) <= std::abs(step))
        return target;

    return current + (target > current ? std::abs(step) : -std::abs(step));
}

double logFrequency(double frequencyHz)
{
    return std::log(std::max<double>(frequencyHz, 1.0));
}

double logPositive(double value)
{
    return std::log(std::max<double>(value, 0.0001));
}

double protectClippingSample(double sample, bool shouldProtect, bool& risk)
{
    (void)shouldProtect;

    if (! std::isfinite(sample))
        return 0.0;

    constexpr double riskThreshold = 0.98;
    const double magnitude = std::abs(sample);
    risk = risk || magnitude > riskThreshold;
    return sample;
}
} // namespace

EqProcessor::EqProcessor(bool preserve, bool safety) : preserveOpraParameters(preserve), localSafetyLimiter(safety)
{
    for (int band = 0; band < eqBandCount; ++band)
    {
        atomicBandGainsDb[static_cast<size_t>(band)].store(0.0f, std::memory_order_relaxed);
        atomicBandFrequenciesHz[static_cast<size_t>(band)].store(eqFrequenciesHz[static_cast<size_t>(band)], std::memory_order_relaxed);
        atomicBandQ[static_cast<size_t>(band)].store(1.0f, std::memory_order_relaxed);
        atomicBandFilterTypes[static_cast<size_t>(band)].store(static_cast<int>(EqFilterType::Peaking), std::memory_order_relaxed);
        atomicBandEnabled[static_cast<size_t>(band)].store(true, std::memory_order_relaxed);
        atomicBandScopes[static_cast<size_t>(band)].store(static_cast<int>(EqBandScope::Stereo), std::memory_order_relaxed);
        atomicBandDynamicModes[static_cast<size_t>(band)].store(static_cast<int>(EqDynamicMode::Off), std::memory_order_relaxed);
        atomicBandDynamicThresholdDb[static_cast<size_t>(band)].store(eqDynamicThresholdDefaultDb, std::memory_order_relaxed);
        atomicBandDynamicRatio[static_cast<size_t>(band)].store(eqDynamicRatioDefault, std::memory_order_relaxed);
        atomicBandDynamicAttackMs[static_cast<size_t>(band)].store(eqDynamicAttackDefaultMs, std::memory_order_relaxed);
        atomicBandDynamicReleaseMs[static_cast<size_t>(band)].store(eqDynamicReleaseDefaultMs, std::memory_order_relaxed);
    }
}

void EqProcessor::prepare(double sampleRate, int maximumBlockSize, int channelCount)
{
    currentSampleRate = sampleRate > 0.0 ? sampleRate : 44100.0;
    preparedChannels = std::max<double>(1, channelCount);
    preparedBlockSize = std::max<double>(1, maximumBlockSize);
    channelStates.resize(static_cast<size_t>(preparedChannels));
    scopedWet.resize(static_cast<size_t>(preparedChannels));
    audition.prepare(currentSampleRate, preparedChannels);
    loudnessMeter.prepare(currentSampleRate);
    updateSmoothingSteps();
    reset();
}

void EqProcessor::reset()
{
    audition.reset();
    loudnessStore.invalidate();
    for (auto& channel : channelStates)
        for (auto& filter : channel.filters)
            filter.reset();
    for (auto& filter : midFilters)
        filter.reset();
    for (auto& filter : sideFilters)
        filter.reset();
    dynamicOffsetDb.fill(0.0f);
    blockDynamicActive.fill(false);
    for (auto& detector : dynamicDetectors)
    {
        detector.sidechain[0].reset();
        detector.sidechain[1].reset();
        detector.envelope = 0.0f;
    }

    updateTargetSnapshot();
    smoothedPreampDb = targetPreampDb;
    bypassMix = targetEnabled.load(std::memory_order_acquire) ? 1.0f : 0.0f;
    targetBypassMix = bypassMix;
    targetBandGains = {};
    smoothedBandGains = {};
    targetBandFrequencies = eqFrequenciesHz;
    smoothedBandFrequencies = eqFrequenciesHz;
    targetBandQ = eqDefaultQ;
    smoothedBandQ = eqDefaultQ;
    smoothedBandFilterTypes = {};
    smoothedBandEnabled = eqDefaultBandEnabled;

    for (int band = 0; band < eqBandCount; ++band)
    {
        targetBandGains[band] = preserveOpraParameters
            ? (atomicBandEnabled[band].load(std::memory_order_acquire)
                ? atomicBandGainsDb[band].load(std::memory_order_acquire) : 0.0f)
            : effectiveEqBandGainDb(
            atomicBandGainsDb[band].load(std::memory_order_acquire),
            atomicStrengthPercent.load(std::memory_order_acquire),
            atomicTiltDb.load(std::memory_order_acquire),
            atomicBandFrequenciesHz[band].load(std::memory_order_acquire),
            normalizeEqFilterType(atomicBandFilterTypes[band].load(std::memory_order_acquire)),
            atomicBandEnabled[band].load(std::memory_order_acquire));
        smoothedBandGains[band] = targetBandGains[band];
        targetBandFrequencies[band] = atomicBandFrequenciesHz[band].load(std::memory_order_acquire);
        smoothedBandFrequencies[band] = targetBandFrequencies[band];
        targetBandQ[band] = atomicBandQ[band].load(std::memory_order_acquire);
        smoothedBandQ[band] = targetBandQ[band];
        smoothedBandFilterTypes[band] = normalizeEqFilterType(atomicBandFilterTypes[band].load(std::memory_order_acquire));
        smoothedBandEnabled[band] = atomicBandEnabled[band].load(std::memory_order_acquire);
        currentBandScopes[band] = normalizeEqBandScope(atomicBandScopes[band].load(std::memory_order_acquire));
        updateBandCoefficient(band);
    }
    tailActive.store(false, std::memory_order_release);
}

template <typename Sample>
void EqProcessor::processBlock(echo::PlanarAudioBuffer<Sample>& buffer, int startSample, int numSamples)
{
    if (numSamples <= 0)
        return;

    updateTargetSnapshot();
    const bool measureLoudness = !preserveOpraParameters && loudnessStore.loudnessRequested();
    if (measureLoudness)
        loudnessMeter.begin(buffer, startSample, numSamples, loudnessStore.revision());

    const int channelCount = std::min<double>(buffer.getNumChannels(), preparedChannels);
    int dynamicSlots = 0;
    bool extended = false;
    for (int band = 0; band < eqBandCount; ++band)
    {
        const auto scope = normalizeEqBandScope(atomicBandScopes[static_cast<size_t>(band)].load(std::memory_order_acquire));
        const auto mode = normalizeEqDynamicMode(atomicBandDynamicModes[static_cast<size_t>(band)].load(std::memory_order_acquire));
        const auto type = normalizeEqFilterType(atomicBandFilterTypes[static_cast<size_t>(band)].load(std::memory_order_acquire));
        const bool enabled = atomicBandEnabled[static_cast<size_t>(band)].load(std::memory_order_acquire);
        const bool dynamic = enabled
            && mode != EqDynamicMode::Off
            && eqFilterGainBearing(type)
            && dynamicSlots < eqMaxDynamicBands;
        blockDynamicActive[static_cast<size_t>(band)] = dynamic;
        if (dynamic)
        {
            ++dynamicSlots;
            refreshDynamicTiming(band);
            extended = true;
        }
        else if (dynamicOffsetDb[static_cast<size_t>(band)] != 0.0)
        {
            dynamicOffsetDb[static_cast<size_t>(band)] = 0.0;
            dynamicDetectors[static_cast<size_t>(band)].envelope = 0.0;
            updateBandCoefficient(band);
        }
        if (scope != EqBandScope::Stereo)
            extended = true;
    }
    bool risk = false;
    audition.beginBlock();

    for (int sample = 0; sample < numSamples; ++sample)
    {
        audition.nextSample();
        smoothedPreampDb = moveTowards(smoothedPreampDb, targetPreampDb, preampStepDb);
        bypassMix = moveTowards(bypassMix, targetBypassMix, bypassStep);

        for (int band = 0; band < eqBandCount; ++band)
        {
            const double previousGain = smoothedBandGains[band];
            const double previousFrequency = smoothedBandFrequencies[band];
            const double previousQ = smoothedBandQ[band];
            const auto previousFilterType = smoothedBandFilterTypes[band];
            const bool previousEnabled = smoothedBandEnabled[band];
            smoothedBandGains[band] = moveTowards(previousGain, targetBandGains[band], bandGainSteps[band]);
            if (std::abs(bandFrequencySteps[band]) <= 1.0e-8)
                smoothedBandFrequencies[band] = targetBandFrequencies[band];
            else
                smoothedBandFrequencies[band] = std::exp(moveTowards(
                    logFrequency(previousFrequency),
                    logFrequency(targetBandFrequencies[band]),
                    bandFrequencySteps[band]));
            if (std::abs(bandQSteps[band]) <= 1.0e-8)
                smoothedBandQ[band] = targetBandQ[band];
            else
                smoothedBandQ[band] = std::exp(moveTowards(
                    logPositive(previousQ),
                    logPositive(targetBandQ[band]),
                    bandQSteps[band]));
            smoothedBandFilterTypes[band] = normalizeEqFilterType(atomicBandFilterTypes[band].load(std::memory_order_acquire));
            smoothedBandEnabled[band] = atomicBandEnabled[band].load(std::memory_order_acquire);
            currentBandScopes[band] = normalizeEqBandScope(atomicBandScopes[band].load(std::memory_order_acquire));

            if (! nearlyEqual(previousGain, smoothedBandGains[band])
                || ! nearlyEqual(previousFrequency, smoothedBandFrequencies[band])
                || ! nearlyEqual(previousQ, smoothedBandQ[band])
                || previousFilterType != smoothedBandFilterTypes[band]
                || previousEnabled != smoothedBandEnabled[band])
            {
                updateBandCoefficient(band);
            }
        }

        const double preampGain = dbToGain(smoothedPreampDb);
        if (extended)
        {
            processScopedSample(buffer, startSample, sample, channelCount, preampGain, risk);
            continue;
        }

        for (int channel = 0; channel < channelCount; ++channel)
        {
            auto* samples = buffer.getWritePointer(channel, startSample);
            const double dry = samples[sample];
            double wet = static_cast<double>(dry) * preampGain;

            for (int band = 0; band < eqBandCount; ++band)
                wet = channelStates[static_cast<size_t>(channel)].filters[band].processDouble(wet, coefficients[band]);

            const double mixed = static_cast<double>(dry + (wet - dry) * bypassMix);
            const bool shouldProtect = localSafetyLimiter && isDspSafetyLimiterEnabled() && (bypassMix > 0.0 || targetBypassMix > 0.0 || wasEnabled);
            samples[sample] = protectClippingSample(audition.process(channel, dry, mixed), shouldProtect, risk);
        }
    }

    if (measureLoudness) {
        auto measurement = loudnessMeter.end(buffer, startSample, numSamples);
        if (audition.isProcessing()) measurement.ready = false;
        loudnessStore.publish(measurement);
    }
    clippingRisk.store(risk, std::memory_order_release);
    wasEnabled = targetBypassMix > 0.5;

    constexpr double tailThreshold = 1.0e-7;
    bool hasTail = false;
    for (const auto& channel : channelStates)
    {
        for (const auto& filter : channel.filters)
        {
            if (filter.hasEnergy(tailThreshold))
            {
                hasTail = true;
                break;
            }
        }
        if (hasTail)
            break;
    }
    if (! hasTail)
    {
        for (int band = 0; band < eqBandCount && ! hasTail; ++band)
        {
            const auto& mid = midFilters[static_cast<size_t>(band)];
            const auto& side = sideFilters[static_cast<size_t>(band)];
            hasTail = mid.hasEnergy(tailThreshold) || side.hasEnergy(tailThreshold);
        }
    }
    tailActive.store(hasTail && (bypassMix > 0.0 || targetBypassMix > 0.0), std::memory_order_release);
}

int64_t EqProcessor::tailFramesRemaining() const noexcept
{
    return tailActive.load(std::memory_order_acquire) ? 1 : 0;
}

void EqProcessor::setEnabled(bool shouldBeEnabled)
{
    targetEnabled.store(shouldBeEnabled, std::memory_order_release);
    loudnessStore.invalidate();
}

void EqProcessor::setStrengthPercent(float value)
{
    atomicStrengthPercent.store(std::isfinite(value) ? std::clamp<double>(value, 0.0f, 100.0f) : 100.0f, std::memory_order_release);
    loudnessStore.invalidate();
}

void EqProcessor::setTiltDb(float value)
{
    atomicTiltDb.store(clampEqTiltDb(value), std::memory_order_release);
    loudnessStore.invalidate();
}

void EqProcessor::setPreampDb(float value)
{
    atomicPreampDb.store(preserveOpraParameters ? std::clamp(value, correctionMinPreampDb, correctionMaxPreampDb) : clampEqPreampDb(value), std::memory_order_release);
    loudnessStore.invalidate();
}

bool EqProcessor::setBandGainDb(int bandIndex, float value)
{
    if (bandIndex < 0 || bandIndex >= eqBandCount)
        return false;

    atomicBandGainsDb[static_cast<size_t>(bandIndex)].store(preserveOpraParameters ? std::clamp(value, correctionMinGainDb, correctionMaxGainDb) : clampEqGainDb(value), std::memory_order_release);
    loudnessStore.invalidate();
    return true;
}

bool EqProcessor::setBandFrequencyHz(int bandIndex, float value)
{
    if (bandIndex < 0 || bandIndex >= eqBandCount || ! std::isfinite(value))
        return false;

    atomicBandFrequenciesHz[static_cast<size_t>(bandIndex)].store(preserveOpraParameters ? std::clamp(value, correctionMinFrequencyHz, correctionMaxFrequencyHz) : clampEqFrequencyHz(value), std::memory_order_release);
    loudnessStore.invalidate();
    return true;
}

bool EqProcessor::setBandQ(int bandIndex, float value)
{
    if (bandIndex < 0 || bandIndex >= eqBandCount || ! std::isfinite(value))
        return false;

    const auto type = normalizeEqFilterType(atomicBandFilterTypes[static_cast<size_t>(bandIndex)].load(std::memory_order_acquire));
    atomicBandQ[static_cast<size_t>(bandIndex)].store(preserveOpraParameters ? std::clamp(value, correctionMinQ, correctionMaxQ) : clampEqQ(value, type), std::memory_order_release);
    loudnessStore.invalidate();
    return true;
}

bool EqProcessor::setBandFilterType(int bandIndex, EqFilterType value)
{
    if (bandIndex < 0 || bandIndex >= eqBandCount)
        return false;

    const auto normalized = normalizeEqFilterType(static_cast<int>(value));
    atomicBandFilterTypes[static_cast<size_t>(bandIndex)].store(static_cast<int>(normalized), std::memory_order_release);
    const float q = atomicBandQ[static_cast<size_t>(bandIndex)].load(std::memory_order_acquire);
    const float clampedQ = preserveOpraParameters ? std::clamp(q, correctionMinQ, correctionMaxQ) : clampEqQ(q, normalized);
    if (clampedQ != q)
        atomicBandQ[static_cast<size_t>(bandIndex)].store(clampedQ, std::memory_order_release);
    loudnessStore.invalidate();
    return true;
}

bool EqProcessor::setBandEnabled(int bandIndex, bool value)
{
    if (bandIndex < 0 || bandIndex >= eqBandCount)
        return false;

    atomicBandEnabled[static_cast<size_t>(bandIndex)].store(value, std::memory_order_release);
    loudnessStore.invalidate();
    return true;
}

bool EqProcessor::setBandProcessing(
    int bandIndex,
    EqBandScope scope,
    EqDynamicMode dynamicMode,
    float thresholdDb,
    float ratio,
    float attackMs,
    float releaseMs)
{
    if (bandIndex < 0 || bandIndex >= eqBandCount)
        return false;

    const auto index = static_cast<size_t>(bandIndex);
    atomicBandScopes[index].store(static_cast<int>(normalizeEqBandScope(static_cast<int>(scope))), std::memory_order_release);
    atomicBandDynamicModes[index].store(static_cast<int>(normalizeEqDynamicMode(static_cast<int>(dynamicMode))), std::memory_order_release);
    atomicBandDynamicThresholdDb[index].store(clampEqDynamicThresholdDb(thresholdDb), std::memory_order_release);
    atomicBandDynamicRatio[index].store(clampEqDynamicRatio(ratio), std::memory_order_release);
    atomicBandDynamicAttackMs[index].store(clampEqDynamicAttackMs(attackMs), std::memory_order_release);
    atomicBandDynamicReleaseMs[index].store(clampEqDynamicReleaseMs(releaseMs), std::memory_order_release);
    loudnessStore.invalidate();
    return true;
}

void EqProcessor::resetFlat()
{
    setPreampDb(0.0f);
    setStrengthPercent(100.0f);
    setTiltDb(0.0f);

    for (int band = 0; band < eqBandCount; ++band)
    {
        setBandGainDb(band, 0.0f);
        setBandFrequencyHz(band, eqFrequenciesHz[static_cast<size_t>(band)]);
        setBandFilterType(band, EqFilterType::Peaking);
        setBandQ(band, 1.0f);
        setBandEnabled(band, true);
        setBandProcessing(band, EqBandScope::Stereo, EqDynamicMode::Off, eqDynamicThresholdDefaultDb, eqDynamicRatioDefault, eqDynamicAttackDefaultMs, eqDynamicReleaseDefaultMs);
    }
}

void EqProcessor::setState(const EqState& state)
{
    setEnabled(state.enabled);
    setPreampDb(state.preampDb);
    setStrengthPercent(state.strengthPercent);
    setTiltDb(state.tiltDb);

    for (int band = 0; band < eqBandCount; ++band)
    {
        setBandGainDb(band, state.bandGainsDb[static_cast<size_t>(band)]);
        setBandFrequencyHz(band, state.bandFrequenciesHz[static_cast<size_t>(band)]);
        setBandFilterType(band, state.bandFilterTypes[static_cast<size_t>(band)]);
        setBandQ(band, state.bandQ[static_cast<size_t>(band)]);
        setBandEnabled(band, state.bandEnabled[static_cast<size_t>(band)]);
        setBandProcessing(
            band,
            state.bandScopes[static_cast<size_t>(band)],
            state.bandDynamicModes[static_cast<size_t>(band)],
            state.bandDynamicThresholdDb[static_cast<size_t>(band)],
            state.bandDynamicRatio[static_cast<size_t>(band)],
            state.bandDynamicAttackMs[static_cast<size_t>(band)],
            state.bandDynamicReleaseMs[static_cast<size_t>(band)]);
    }
}

EqState EqProcessor::getState() const
{
    EqState state;
    state.enabled = targetEnabled.load(std::memory_order_acquire);
    state.preampDb = atomicPreampDb.load(std::memory_order_acquire);
    state.strengthPercent = atomicStrengthPercent.load(std::memory_order_acquire);
    state.tiltDb = atomicTiltDb.load(std::memory_order_acquire);

    for (int band = 0; band < eqBandCount; ++band)
    {
        state.bandGainsDb[static_cast<size_t>(band)] = atomicBandGainsDb[static_cast<size_t>(band)].load(std::memory_order_acquire);
        state.bandFrequenciesHz[static_cast<size_t>(band)] = atomicBandFrequenciesHz[static_cast<size_t>(band)].load(std::memory_order_acquire);
        state.bandQ[static_cast<size_t>(band)] = atomicBandQ[static_cast<size_t>(band)].load(std::memory_order_acquire);
        state.bandFilterTypes[static_cast<size_t>(band)] =
            normalizeEqFilterType(atomicBandFilterTypes[static_cast<size_t>(band)].load(std::memory_order_acquire));
        state.bandEnabled[static_cast<size_t>(band)] = atomicBandEnabled[static_cast<size_t>(band)].load(std::memory_order_acquire);
        state.bandScopes[static_cast<size_t>(band)] = normalizeEqBandScope(atomicBandScopes[static_cast<size_t>(band)].load(std::memory_order_acquire));
        state.bandDynamicModes[static_cast<size_t>(band)] = normalizeEqDynamicMode(atomicBandDynamicModes[static_cast<size_t>(band)].load(std::memory_order_acquire));
        state.bandDynamicThresholdDb[static_cast<size_t>(band)] = atomicBandDynamicThresholdDb[static_cast<size_t>(band)].load(std::memory_order_acquire);
        state.bandDynamicRatio[static_cast<size_t>(band)] = atomicBandDynamicRatio[static_cast<size_t>(band)].load(std::memory_order_acquire);
        state.bandDynamicAttackMs[static_cast<size_t>(band)] = atomicBandDynamicAttackMs[static_cast<size_t>(band)].load(std::memory_order_acquire);
        state.bandDynamicReleaseMs[static_cast<size_t>(band)] = atomicBandDynamicReleaseMs[static_cast<size_t>(band)].load(std::memory_order_acquire);
    }

    return state;
}

bool EqProcessor::isEnabled() const
{
    return targetEnabled.load(std::memory_order_acquire);
}

bool EqProcessor::hasClippingRisk() const
{
    return clippingRisk.load(std::memory_order_acquire);
}

#if defined(ECHO_AUDIO_ENGINE_TESTS) && ECHO_AUDIO_ENGINE_TESTS
uint64_t EqProcessor::getCoefficientUpdateCountForTests() const
{
    return coefficientUpdateCount;
}
#endif

void EqProcessor::updateSmoothingSteps()
{
    gainSmoothingSamples = std::max<double>(1, static_cast<int>(currentSampleRate * 0.008));
    bypassSmoothingSamples = std::max<double>(1, static_cast<int>(currentSampleRate * 0.006));
}

void EqProcessor::updateBandCoefficient(int bandIndex)
{
    const auto index = static_cast<size_t>(bandIndex);
    coefficients[index] = makeEqBandCoefficients(
        currentSampleRate,
        smoothedBandFrequencies[index],
        preserveOpraParameters ? smoothedBandGains[index] : clampEqGainDb(smoothedBandGains[index] + dynamicOffsetDb[index]),
        smoothedBandQ[index],
        smoothedBandFilterTypes[index],
        smoothedBandEnabled[index],
        preserveOpraParameters);
    dynamicDetectors[index].coefficients = makeBandpassCoefficients(
        currentSampleRate,
        smoothedBandFrequencies[index],
        smoothedBandQ[index]);

#if defined(ECHO_AUDIO_ENGINE_TESTS) && ECHO_AUDIO_ENGINE_TESTS
    ++coefficientUpdateCount;
#endif
}

void EqProcessor::updateTargetSnapshot()
{
    targetPreampDb = atomicPreampDb.load(std::memory_order_acquire);
    preampStepDb = (targetPreampDb - smoothedPreampDb) / static_cast<float>(gainSmoothingSamples);
    targetBypassMix = targetEnabled.load(std::memory_order_acquire) ? 1.0f : 0.0f;
    bypassStep = (targetBypassMix - bypassMix) / static_cast<float>(bypassSmoothingSamples);

    for (int band = 0; band < eqBandCount; ++band)
    {
        targetBandGains[static_cast<size_t>(band)] = preserveOpraParameters
            ? (atomicBandEnabled[static_cast<size_t>(band)].load(std::memory_order_acquire)
                ? atomicBandGainsDb[static_cast<size_t>(band)].load(std::memory_order_acquire) : 0.0f)
            : effectiveEqBandGainDb(
            atomicBandGainsDb[static_cast<size_t>(band)].load(std::memory_order_acquire),
            atomicStrengthPercent.load(std::memory_order_acquire),
            atomicTiltDb.load(std::memory_order_acquire),
            atomicBandFrequenciesHz[static_cast<size_t>(band)].load(std::memory_order_acquire),
            normalizeEqFilterType(atomicBandFilterTypes[static_cast<size_t>(band)].load(std::memory_order_acquire)),
            atomicBandEnabled[static_cast<size_t>(band)].load(std::memory_order_acquire));
        bandGainSteps[static_cast<size_t>(band)] =
            (targetBandGains[static_cast<size_t>(band)] - smoothedBandGains[static_cast<size_t>(band)])
            / static_cast<float>(gainSmoothingSamples);
        targetBandFrequencies[static_cast<size_t>(band)] = atomicBandFrequenciesHz[static_cast<size_t>(band)].load(std::memory_order_acquire);
        bandFrequencySteps[static_cast<size_t>(band)] =
            (logFrequency(targetBandFrequencies[static_cast<size_t>(band)]) - logFrequency(smoothedBandFrequencies[static_cast<size_t>(band)]))
            / static_cast<float>(gainSmoothingSamples);
        targetBandQ[static_cast<size_t>(band)] = atomicBandQ[static_cast<size_t>(band)].load(std::memory_order_acquire);
        bandQSteps[static_cast<size_t>(band)] =
            (logPositive(targetBandQ[static_cast<size_t>(band)]) - logPositive(smoothedBandQ[static_cast<size_t>(band)]))
            / static_cast<float>(gainSmoothingSamples);
    }
}

void EqProcessor::refreshDynamicTiming(int bandIndex)
{
    const auto index = static_cast<size_t>(bandIndex);
    auto& detector = dynamicDetectors[index];
    const double attackSamples = std::max<double>(
        1.0,
        currentSampleRate * static_cast<double>(clampEqDynamicAttackMs(atomicBandDynamicAttackMs[index].load(std::memory_order_acquire))) / 1000.0);
    const double releaseSamples = std::max<double>(
        1.0,
        currentSampleRate * static_cast<double>(clampEqDynamicReleaseMs(atomicBandDynamicReleaseMs[index].load(std::memory_order_acquire))) / 1000.0);
    detector.attackCoefficient = static_cast<float>(std::exp(-1.0 / attackSamples));
    detector.releaseCoefficient = static_cast<float>(std::exp(-1.0 / releaseSamples));
    detector.thresholdDb = clampEqDynamicThresholdDb(atomicBandDynamicThresholdDb[index].load(std::memory_order_acquire));
    detector.ratio = clampEqDynamicRatio(atomicBandDynamicRatio[index].load(std::memory_order_acquire));
    detector.mode = normalizeEqDynamicMode(atomicBandDynamicModes[index].load(std::memory_order_acquire));
    detector.coefficients = makeBandpassCoefficients(
        currentSampleRate,
        smoothedBandFrequencies[index],
        smoothedBandQ[index]);
}

template <typename Sample>
void EqProcessor::processScopedSample(
    echo::PlanarAudioBuffer<Sample>& buffer,
    int startSample,
    int sample,
    int channelCount,
    double preampGain,
    bool& risk)
{
    const int channels = std::min<double>(channelCount, preparedChannels);
    if (channels <= 0)
        return;

    auto* wet = scopedWet.data();
    for (int channel = 0; channel < channels; ++channel)
    {
        wet[channel] = static_cast<double>(buffer.getReadPointer(channel, startSample)[sample]) * preampGain;
    }

    for (int band = 0; band < eqBandCount; ++band)
    {
        const auto index = static_cast<size_t>(band);
        if (blockDynamicActive[index])
        {
            auto& detector = dynamicDetectors[index];
            double level = 0.0;
            const int detectorChannels = std::min<double>(channels, 2);
            for (int channel = 0; channel < detectorChannels; ++channel)
            {
                level = std::max<double>(
                    level,
                    static_cast<double>(std::abs(detector.sidechain[static_cast<size_t>(channel)].processDouble(wet[channel], detector.coefficients))));
            }
            const double coefficient = level > detector.envelope
                ? detector.attackCoefficient
                : detector.releaseCoefficient;
            detector.envelope = coefficient * detector.envelope + (1.0 - coefficient) * level;
            const double envelopeDb = detector.envelope > 1.0e-8
                ? 20.0 * std::log10(detector.envelope)
                : -120.0;
            const double overDb = envelopeDb - detector.thresholdDb;
            double deltaDb = 0.0;
            if (overDb > 0.0 && detector.ratio > 1.0)
            {
                const double transferred = overDb * (1.0 - 1.0 / detector.ratio);
                deltaDb = detector.mode == EqDynamicMode::Up
                    ? std::min<double>(6.0, transferred)
                    : -transferred;
            }
            if (! nearlyEqual(deltaDb, dynamicOffsetDb[index]))
            {
                dynamicOffsetDb[index] = deltaDb;
                updateBandCoefficient(band);
            }
        }

        const auto scope = channels > 1 ? currentBandScopes[index] : EqBandScope::Stereo;
        if (scope == EqBandScope::Stereo)
        {
            for (int channel = 0; channel < channels; ++channel)
                wet[channel] = channelStates[static_cast<size_t>(channel)].filters[index].processDouble(wet[channel], coefficients[index]);
            continue;
        }
        if (scope == EqBandScope::Left)
        {
            wet[0] = channelStates[0].filters[index].processDouble(wet[0], coefficients[index]);
            continue;
        }
        if (scope == EqBandScope::Right)
        {
            wet[1] = channelStates[1].filters[index].processDouble(wet[1], coefficients[index]);
            continue;
        }

        const double mid = 0.5 * (wet[0] + wet[1]);
        const double side = 0.5 * (wet[0] - wet[1]);
        const double nextMid = scope == EqBandScope::Mid
            ? midFilters[index].processDouble(mid, coefficients[index])
            : mid;
        const double nextSide = scope == EqBandScope::Side
            ? sideFilters[index].processDouble(side, coefficients[index])
            : side;
        wet[0] = nextMid + nextSide;
        wet[1] = nextMid - nextSide;
    }

    const bool shouldProtect = localSafetyLimiter && isDspSafetyLimiterEnabled() && (bypassMix > 0.0 || targetBypassMix > 0.0 || wasEnabled);
    for (int channel = 0; channel < channels; ++channel)
    {
        const double dry = buffer.getReadPointer(channel, startSample)[sample];
        const double mixed = static_cast<double>(dry + (wet[channel] - dry) * bypassMix);
        buffer.getWritePointer(channel, startSample)[sample] = protectClippingSample(
            audition.process(channel, dry, mixed),
            shouldProtect,
            risk);
    }
}

template void EqProcessor::processScopedSample<float>(echo::PlanarAudioBuffer<float>&, int, int, int, double, bool&);
template void EqProcessor::processScopedSample<double>(echo::PlanarAudioBuffer<double>&, int, int, int, double, bool&);
template void EqProcessor::processBlock<float>(echo::PlanarAudioBuffer<float>&, int, int);
template void EqProcessor::processBlock<double>(echo::PlanarAudioBuffer<double>&, int, int);
} // namespace echo
