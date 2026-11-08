#pragma once
#include "buffer.h"
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>

namespace echo {
struct DspMeasurement {
    bool ready = false;
    double inputDb = -120, outputDb = -120, gainDb = 0, inputPeakDb = -120, outputPeakDb = -120;
    double windowMs = 0, realtimeRatio = 0, latencyMs = -1, ageMs = 0;
    uint64_t revision = 0;
};

// Single realtime publisher, bounded non-realtime snapshot attempt. Control
// invalidation never touches callback-owned filter/window state.
class DspMeasurementStore {
public:
    uint64_t revision() const noexcept { return generation.load(std::memory_order_acquire); }
    void invalidate() noexcept { generation.fetch_add(1, std::memory_order_acq_rel); }
    static double nowMs() noexcept {
        return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now().time_since_epoch()).count();
    }
    // Control reads lease the comparison meter; status publication never renews it.
    void requestLoudness() noexcept {
        const double now = nowMs();
        if (loudnessDeadline.exchange(now + 2000, std::memory_order_acq_rel) <= now)
            invalidate();
    }
    bool loudnessLeaseActive() const noexcept {
        return loudnessDeadline.load(std::memory_order_acquire) > nowMs();
    }
    bool loudnessRequested() noexcept {
        double deadline = loudnessDeadline.load(std::memory_order_acquire);
        if (deadline == 0) return false;
        if (nowMs() < deadline) return true;
        loudnessDeadline.compare_exchange_strong(deadline, 0, std::memory_order_acq_rel);
        return false;
    }
    void publish(const DspMeasurement& value) noexcept {
        sequence.fetch_add(1, std::memory_order_seq_cst);
        const std::array<double, 10> words { value.ready ? 1.0 : 0.0, value.inputDb, value.outputDb,
            value.gainDb, value.inputPeakDb, value.windowMs, value.realtimeRatio, value.latencyMs, nowMs(), value.outputPeakDb };
        for (size_t i = 0; i < words.size(); ++i) values[i].store(words[i], std::memory_order_relaxed);
        publishedGeneration.store(value.revision, std::memory_order_relaxed);
        sequence.fetch_add(1, std::memory_order_seq_cst);
    }
    DspMeasurement read() const noexcept {
        DspMeasurement result;
        const auto before = sequence.load(std::memory_order_seq_cst);
        if (before == 0 || (before & 1)) return result;
        std::array<double, 10> words {};
        for (size_t i = 0; i < words.size(); ++i) words[i] = values[i].load(std::memory_order_relaxed);
        result.revision = publishedGeneration.load(std::memory_order_relaxed);
        if (before != sequence.load(std::memory_order_seq_cst) || result.revision != revision()) return {};
        result.ready = words[0] != 0;
        result.inputDb = words[1]; result.outputDb = words[2]; result.gainDb = words[3];
        result.inputPeakDb = words[4]; result.windowMs = words[5];
        result.realtimeRatio = words[6]; result.latencyMs = words[7];
        result.ageMs = std::max(0.0, nowMs() - words[8]);
        result.outputPeakDb = words[9];
        if (result.ageMs > 500) result.ready = false;
        return result;
    }
private:
    static_assert(std::atomic<double>::is_always_lock_free);
    std::atomic<uint64_t> generation { 1 }, publishedGeneration { 0 }, sequence { 0 };
    std::array<std::atomic<double>, 10> values {};
    std::atomic<double> loudnessDeadline { 0 };
};

// K-weighted, approximately one-second programme energy. This is a comparison
// meter, not integrated/gated R128 LUFS. Holds only 10 energy buckets and two
// channels of IIR history, never PCM. Unsupported layouts fail unavailable.
class DspLoudnessMeter {
    struct Biquad {
        double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, z1 = 0, z2 = 0;
        double process(double x) noexcept {
            if (!std::isfinite(x)) x = 0;
            const double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y;
            return y;
        }
        void clear() noexcept { z1 = z2 = 0; }
    };
    struct Weighting {
        Biquad shelf, highpass;
        double process(double x) noexcept { return highpass.process(shelf.process(x)); }
        void clear() noexcept { shelf.clear(); highpass.clear(); }
    };
    struct Bucket { double input = 0, output = 0, peak = 0, outputPeak = 0; int frames = 0; };
public:
    void prepare(double rate) noexcept {
        sampleRate = std::max(8000.0, rate);
        // BS.1770 K-weighting parameterization, adapted to the processing rate.
        constexpr double pi = 3.14159265358979323846;
        const double k = std::tan(pi * 1681.974450955533 / sampleRate);
        const double q = 0.7071752369554196, vh = std::pow(10.0, 3.999843853973347 / 20.0);
        const double vb = std::pow(vh, 0.4996667741545416), d = 1 + k / q + k * k;
        Biquad shelf {(vh + vb * k / q + k*k)/d, 2*(k*k-vh)/d,
            (vh-vb*k/q+k*k)/d, 2*(k*k-1)/d, (1-k/q+k*k)/d};
        const double hp = std::tan(pi * 38.13547087602444 / sampleRate), hq = 0.5003270373238773;
        const double hd = 1 + hp/hq + hp*hp;
        Biquad highpass {1, -2, 1, 2*(hp*hp-1)/hd, (1-hp/hq+hp*hp)/hd};
        for (auto& filter : inputFilters) filter = {shelf, highpass};
        for (auto& filter : outputFilters) filter = {shelf, highpass};
        reset(0);
    }
    void reset(uint64_t revision) noexcept {
        currentRevision = revision; buckets = {}; partial = {}; bucketIndex = filled = 0;
        settlingFrames = static_cast<int>(sampleRate * 0.1);
        for (auto& filter : inputFilters) filter.clear();
        for (auto& filter : outputFilters) filter.clear();
    }
    template<class Sample> void begin(const PlanarAudioBuffer<Sample>& buffer, int start, int count, uint64_t revision) noexcept {
        if (revision != currentRevision) reset(revision);
        inputChannels = buffer.getNumChannels(); inputSum = inputPeak = 0;
        if (inputChannels <= 0 || inputChannels > 2) return;
        for (int channel = 0; channel < inputChannels; ++channel) {
            const auto* data = buffer.getReadPointer(channel, start);
            for (int i = 0; i < count; ++i) {
                const double x = std::isfinite(data[i]) ? data[i] : 0;
                const double weighted = inputFilters[channel].process(x);
                inputSum += weighted * weighted; inputPeak = std::max(inputPeak, std::abs(x));
            }
        }
    }
    template<class Sample> DspMeasurement end(const PlanarAudioBuffer<Sample>& buffer, int start, int count) noexcept {
        DspMeasurement result; result.revision = currentRevision;
        if (inputChannels <= 0 || inputChannels > 2 || buffer.getNumChannels() != inputChannels) {
            reset(currentRevision); return result;
        }
        double outputSum = 0, outputPeak = 0;
        for (int channel = 0; channel < inputChannels; ++channel) {
            const auto* data = buffer.getReadPointer(channel, start);
            for (int i = 0; i < count; ++i) {
                const double x = std::isfinite(data[i]) ? data[i] : 0;
                const double y = outputFilters[channel].process(x); outputSum += y*y;
                outputPeak = std::max(outputPeak, std::abs(x));
            }
        }
        if (settlingFrames > 0) { settlingFrames -= count; return result; }
        partial.input += inputSum; partial.output += outputSum;
        partial.peak = std::max(partial.peak, inputPeak); partial.frames += count;
        partial.outputPeak = std::max(partial.outputPeak, outputPeak);
        if (partial.frames >= sampleRate * 0.1) {
            buckets[bucketIndex] = partial; partial = {};
            bucketIndex = (bucketIndex + 1) % buckets.size(); filled = std::min(filled + 1, buckets.size());
        }
        double in = 0, out = 0, peak = 0, outPeak = 0; int frames = 0;
        for (const auto& bucket : buckets) {
            in += bucket.input; out += bucket.output; frames += bucket.frames;
            peak = std::max(peak, bucket.peak); outPeak = std::max(outPeak, bucket.outputPeak);
        }
        result.windowMs = frames * 1000.0 / sampleRate;
        if (frames == 0) return result;
        const auto db = [](double value) { return 10 * std::log10(std::max(1e-12, value)); };
        result.inputDb = db(in / (frames * inputChannels)); result.outputDb = db(out / (frames * inputChannels));
        result.inputPeakDb = 20 * std::log10(std::max(1e-6, peak));
        result.outputPeakDb = 20 * std::log10(std::max(1e-6, outPeak));
        result.gainDb = result.outputDb - result.inputDb;
        result.ready = filled == buckets.size() && result.inputDb > -70 && result.outputDb > -70
            && std::isfinite(result.gainDb);
        return result;
    }
private:
    double sampleRate = 48000, inputSum = 0, inputPeak = 0;
    uint64_t currentRevision = 0;
    int inputChannels = 0, settlingFrames = 0;
    size_t bucketIndex = 0, filled = 0;
    Bucket partial;
    std::array<Bucket, 10> buckets {};
    std::array<Weighting, 2> inputFilters {}, outputFilters {};
};
} // namespace echo
