#pragma once
#include "EqBand.h"
#include <atomic>
#include <chrono>
#include <vector>

namespace echo
{
// Temporary monitor only. No PCM queue, persistent preset mutation or callback allocation.
// A short host-side lease also restores playback after a renderer crash/disconnect.
class EqAuditionProcessor
{
public:
    static int64_t nowMs() noexcept
    {
        return std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    }
    bool set(bool active, float frequency, float q) noexcept
    {
        if (!active) { deadline.store(0, std::memory_order_release); return true; }
        if (!std::isfinite(frequency) || !std::isfinite(q)
            || frequency < eqMinFrequencyHz || frequency > eqMaxFrequencyHz || q < eqMinQ || q > eqMaxQ)
            return false;
        targetFrequency.store(frequency, std::memory_order_relaxed);
        targetQ.store(q, std::memory_order_relaxed);
        deadline.store(nowMs() + 750, std::memory_order_release);
        return true;
    }
    bool isActive() const noexcept { return deadline.load(std::memory_order_acquire) > nowMs(); }
    bool isProcessing() const noexcept { return isActive() || fading.load(std::memory_order_acquire); }
    void prepare(double rate, int channels)
    {
        sampleRate = rate;
        filters.resize(static_cast<size_t>(channels));
        reset();
    }
    void reset()
    {
        deadline.store(0, std::memory_order_release);
        mix = 0.0f;
        fading.store(false, std::memory_order_release);
        for (auto& filter : filters) filter.reset();
    }
    void beginBlock() noexcept
    {
        active = isActive();
        frequencyTarget = targetFrequency.load(std::memory_order_relaxed);
        qTarget = targetQ.load(std::memory_order_relaxed);
        if (active && mix == 0.0f) { frequency = frequencyTarget; q = qTarget; coefficientTick = 0; }
    }
    void nextSample() noexcept
    {
        const float step = static_cast<float>(1.0 / (sampleRate * 0.02));
        const bool wasMixing = mix > 0.0f;
        mix = active ? std::min(1.0f, mix + step) : std::max(0.0f, mix - step);
        if (wasMixing != (mix > 0.0f)) fading.store(mix > 0.0f, std::memory_order_release);
        if (mix == 0.0f) return;
        // Smooth the requested frequency and bandwidth before updating the filter.
        frequency += (frequencyTarget - frequency) * step;
        q += (qTarget - q) * step;
        if ((coefficientTick++ % 16) != 0) return;
        const double omega = 6.283185307179586 * std::min<double>(frequency, sampleRate * 0.45) / sampleRate;
        const double alpha = std::sin(omega) / (2.0 * q);
        const double a0 = 1.0 + alpha;
        coefficients = { alpha / a0, 0.0f, -alpha / a0,
            -2.0 * std::cos(omega) / a0, (1.0 - alpha) / a0 };
    }
    double process(int channel, double dry, double normal) noexcept
    {
        if (mix == 0.0f) { filters[static_cast<size_t>(channel)].reset(); return normal; }
        const double monitor = filters[static_cast<size_t>(channel)].processDouble(dry, coefficients);
        return normal + (monitor - normal) * mix;
    }
private:
    friend struct DspPreparedRuntime;
    std::atomic<bool> fading { false };
    std::atomic<int64_t> deadline { 0 };
    std::atomic<float> targetFrequency { 1000.0f }, targetQ { 1.0f };
    double sampleRate = 44100.0;
    float frequency = 1000.0f, q = 1.0f, frequencyTarget = 1000.0f, qTarget = 1.0f, mix = 0.0f;
    bool active = false;
    unsigned coefficientTick = 0;
    BiquadCoefficients coefficients;
    std::vector<BiquadState> filters;
};
}
