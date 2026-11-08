#include "EchoPcEqualizer.h"
#include "EqProcessor.h"
#include "EqAuditionProcessor.h"
#include <algorithm>
#include <array>
#include <cmath>
#include <stdexcept>

namespace echo_android {
namespace {
constexpr int header = 6, width = 12;
int kind(const float* b, bool preserveAll = false) {
    const int type = static_cast<int>(b[0]);
    if (type == 6 || ((type == 3 || type == 4) && b[4] != 12)) return 2;
    if (preserveAll) return 1;
    return b[1] < 20 || b[2] < -24 || b[2] > 12 || b[3] < .1 || b[3] > (type == 5 ? 24 : 12) ? 1 : 0;
}
bool valid(std::span<const float> p) {
    if (p.size() < header || !std::all_of(p.begin(), p.end(), [](float x) { return std::isfinite(x); })) return false;
    const int count = static_cast<int>(p[5]);
    if (count < 0 || count > 64 || p[5] != count || p.size() != header + count * width || p[1] < -60 || p[1] > 24) return false;
    int dynamic = 0;
    for (int i = 0; i < count; ++i) {
        const float* b = p.data() + header + i * width;
        if (b[0] < 0 || b[0] > 6 || b[1] < 1 || b[1] > 20000 || b[2] < -60 || b[2] > 30 ||
            b[3] < .001 || b[3] > 1000 || b[6] < 0 || b[6] > 4 || b[7] < 0 || b[7] > 2) return false;
        if (b[7] != 0 && b[5] != 0) {
            if (++dynamic > 4 || kind(b) != 0 || b[0] > 2) return false;
        }
    }
    return true;
}
struct Node {
    virtual ~Node() = default;
    int first = 0, count = 0, mode = 0;
    virtual void update(const float* p) = 0;
    virtual void process(echo::DoubleAudioBuffer&, int) noexcept = 0;
    virtual void reset() noexcept = 0;
};
struct PcNode final : Node {
    echo::EqProcessor eq;
    echo::EqState state;
    PcNode(int rate, int channels, int begin, int length, int k, const float* p) : eq(k == 1, false) {
        first = begin; count = length; mode = k; state.enabled = true; state.bandEnabled.fill(false);
        update(p); eq.prepare(rate, 1024, channels); eq.reset();
    }
    void update(const float* p) override {
        for (int i = 0; i < count; ++i) {
            const float* b = p + (first + i) * width;
            state.bandFilterTypes[i] = static_cast<echo::EqFilterType>(static_cast<int>(b[0]));
            state.bandFrequenciesHz[i] = b[1]; state.bandGainsDb[i] = b[2]; state.bandQ[i] = b[3];
            state.bandEnabled[i] = b[5] != 0; state.bandScopes[i] = static_cast<echo::EqBandScope>(static_cast<int>(b[6]));
            state.bandDynamicModes[i] = static_cast<echo::EqDynamicMode>(static_cast<int>(b[7]));
            state.bandDynamicThresholdDb[i] = b[8]; state.bandDynamicRatio[i] = b[9];
            state.bandDynamicAttackMs[i] = b[10]; state.bandDynamicReleaseMs[i] = b[11];
        }
        eq.setState(state); // fixed atomics/arrays; no allocation or state reset on live edits
    }
    void process(echo::DoubleAudioBuffer& b, int n) noexcept override { eq.processBlock(b, 0, n); }
    void reset() noexcept override { eq.reset(); }
};
struct LegacyNode final : Node {
    int rate, channels, stages = 0, scope = 0;
    bool enabled = true;
    std::array<echo::BiquadCoefficients, 3> coefficients {};
    std::array<std::array<echo::BiquadState, 3>, 8> history {};
    LegacyNode(int fs, int ch, int begin, const float* p) : rate(fs), channels(ch) { first = begin; count = 1; mode = 2; update(p); }
    void update(const float* p) override {
        const auto* b = p + first * width; enabled = b[5] != 0;
        if (scope != static_cast<int>(b[6])) reset();
        scope = static_cast<int>(b[6]);
        const double f = std::clamp<double>(b[1], 1, rate * .45);
        if (b[0] == 6) { coefficients[0] = echo::makeBandpassCoefficients(rate, f, b[3], true); stages = 1; return; }
        const int order = std::clamp(static_cast<int>(b[4] / 6), 1, 6); stages = 0;
        const bool high = b[0] == 4;
        if (order % 2) {
            const double t = std::tan(3.141592653589793 * f / rate), gain = (high ? 1 : t) / (1 + t);
            coefficients[stages++] = {gain, high ? -gain : gain, 0, (t - 1) / (t + 1), 0};
        }
        for (int k = 1; k <= order / 2; ++k) {
            const double q = 1 / (2 * std::sin((2 * k - 1) * 3.141592653589793 / (2 * order)));
            coefficients[stages++] = high ? echo::makeHighPassCoefficients(rate, f, q, true) : echo::makeLowPassCoefficients(rate, f, q, true);
        }
    }
    void process(echo::DoubleAudioBuffer& buffer, int n) noexcept override {
        if (!enabled) return;
        const int effectiveScope = channels > 1 ? scope : 0;
        if (effectiveScope >= 3) {
            for (int c = 0; c < std::min(channels, 2); c += 2) for (int i = 0; i < n; ++i) {
                auto* l = buffer.getWritePointer(c);
                if (c + 1 >= channels) { if (scope == 3) { double v=l[i]; for(int j=0;j<stages;++j) v=history[c][j].processDouble(v,coefficients[j]); l[i]=v; } continue; }
                auto* r = buffer.getWritePointer(c+1); double mid=(l[i]+r[i])*.5, side=(l[i]-r[i])*.5;
                double v=scope==3?mid:side; for(int j=0;j<stages;++j) v=history[c][j].processDouble(v,coefficients[j]);
                if(scope==3) mid=v; else side=v; l[i]=mid+side; r[i]=mid-side;
            }
            return;
        }
        for (int c = 0; c < channels; ++c) {
            if ((effectiveScope == 1 && c != 0) || (effectiveScope == 2 && c != 1)) continue;
            auto* data = buffer.getWritePointer(c);
            for (int i = 0; i < n; ++i) { double v = data[i]; for (int j = 0; j < stages; ++j) v = history[c][j].processDouble(v, coefficients[j]); data[i] = v; }
        }
    }
    void reset() noexcept override { for (auto& channel : history) for (auto& filter : channel) filter.reset(); }
};
}
struct PcEqualizer::Runtime {
    int rate, channels;
    bool preserveAll;
    float targetPreamp = 0, preamp = 0, preampStep = 0, wet = 0;
    bool auditionActive = false;
    std::vector<std::unique_ptr<Node>> nodes;
    echo::DoubleAudioBuffer input, dry;
    echo::EqAuditionProcessor audition;
    std::vector<int> kinds;
    Runtime(int fs, int ch, std::span<const float> p, bool preserve) : rate(fs), channels(ch), preserveAll(preserve), input(ch, 1024), dry(ch, 1024) {
        const int count = static_cast<int>(p[5]); const float* bands = p.data() + header;
        for (int i = 0; i < count;) {
            const int k = kind(bands + i * width, preserveAll); int length = 1;
            if (k != 2) while (i + length < count && length < 31 && kind(bands + (i + length) * width, preserveAll) == k) ++length;
            if (k == 2) nodes.push_back(std::make_unique<LegacyNode>(rate, channels, i, bands));
            else nodes.push_back(std::make_unique<PcNode>(rate, channels, i, length, k, bands));
            i += length;
        }
        for (int i = 0; i < count; ++i) kinds.push_back(kind(bands + i * width, preserveAll));
        audition.prepare(rate, channels); update(p); preamp = targetPreamp;
    }
    bool update(std::span<const float> p) {
        if (!valid(p) || p[5] != kinds.size()) return false;
        const float* b = p.data() + header;
        for (size_t i = 0; i < kinds.size(); ++i) if (kind(b + i * width, preserveAll) != kinds[i]) return false;
        for (auto& node : nodes) node->update(b);
        targetPreamp = p[1]; preampStep = std::abs(targetPreamp - preamp) / std::max(1.f, rate * .008f); auditionActive = p[2] != 0;
        audition.set(auditionActive, std::clamp(p[3], 20.f, 20000.f), std::clamp(p[4], .1f, 12.f));
        return true;
    }
    void reset() noexcept { for (auto& node : nodes) node->reset(); audition.reset(); wet = 0; preamp = targetPreamp; }
    void process(float* interleaved, int n, bool enabled) noexcept {
        if (!enabled) audition.set(false, 1000, 1);
        if (!enabled && wet == 0 && !audition.isProcessing()) return;
        const bool wasWet = wet > 0;
        for (int i = 0; i < n; ++i) {
            preamp += std::clamp(targetPreamp - preamp, -preampStep, preampStep);
            const double gain = std::pow(10.0, preamp / 20.0);
            for (int c = 0; c < channels; ++c) {
                const float v = interleaved[i * channels + c];
                dry.getWritePointer(c)[i] = std::isfinite(v) ? v : 0;
                input.getWritePointer(c)[i] = dry.getReadPointer(c)[i] * gain;
            }
        }
        for (auto& node : nodes) node->process(input, n);
        audition.beginBlock();
        const float step = 1.f / std::max(1.f, rate * .006f);
        for (int i = 0; i < n; ++i) {
            wet = std::clamp(wet + (enabled ? step : -step), 0.f, 1.f);
            audition.nextSample();
            for (int c = 0; c < channels; ++c) {
                const double original = dry.getReadPointer(c)[i];
                const double processed = audition.process(c, original, input.getReadPointer(c)[i]);
                const double out = original + (processed - original) * wet;
                interleaved[i * channels + c] = std::isfinite(out) ? out : 0;
            }
        }
        if (!enabled && wasWet && wet == 0) for (auto& node : nodes) node->reset();
    }
};
PcEqualizer::PcEqualizer(int rate, int channels, std::span<const float> p, bool preserveAll) {
    if (rate < 8000 || rate > 384000 || channels < 1 || channels > 8 || !valid(p)) throw std::invalid_argument("invalid_pc_eq");
    runtime_ = std::make_unique<Runtime>(rate, channels, p, preserveAll);
}
PcEqualizer::~PcEqualizer() = default;
bool PcEqualizer::update(std::span<const float> p) { return runtime_->update(p); }
void PcEqualizer::process(float* p, int n, bool enabled) noexcept { if (p && n > 0 && n <= 1024) runtime_->process(p, n, enabled); }
void PcEqualizer::reset() noexcept { runtime_->reset(); }
bool PcEqualizer::processing() const noexcept { return runtime_->wet > 0 || runtime_->audition.isProcessing(); }
int PcEqualizer::channelCount() const noexcept { return runtime_->channels; }
}
