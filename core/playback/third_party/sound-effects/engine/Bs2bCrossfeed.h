#pragma once
// BS2B coefficient/filter adaptation from libbs2b 3.1.0.
// Source: https://github.com/alexmarsev/libbs2b/blob/master/src/bs2b.c
/*
Copyright (c) 2005 Boris Mikhaylov

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
#include <algorithm>
#include <array>
#include <cmath>
namespace echo {
// Audio-thread owned histories; no buffers or heap allocation.
class Bs2bCrossfeed final {
public:
    void configure(double sampleRate, double cutoffHz, double feedDb, bool smooth = false) noexcept {
        if (sampleRate == rate_ && cutoffHz == cutoff_ && feedDb == feed_) {
            if (!smooth) { coefficients_ = target_; remaining_ = 0; }
            return;
        }
        rate_ = std::max(1.0, sampleRate); cutoff_ = cutoffHz; feed_ = feedDb;
        const double lowDb = -feedDb * 5.0 / 6.0 - 3.0;
        const double highDb = feedDb / 6.0 - 3.0;
        const double lowGain = std::pow(10.0, lowDb / 20.0);
        const double highGain = 1.0 - std::pow(10.0, highDb / 20.0);
        const double highCutoff = cutoffHz * std::pow(2.0, (lowDb - 20.0 * std::log10(highGain)) / 12.0);
        const double lowPole = std::exp(-2.0 * 3.14159265358979323846 * cutoffHz / rate_);
        const double highPole = std::exp(-2.0 * 3.14159265358979323846 * highCutoff / rate_);
        target_ = { lowGain * (1.0 - lowPole), lowPole,
            1.0 - highGain * (1.0 - highPole), highPole,
            1.0 / (1.0 - highGain + lowGain) };
        remaining_ = smooth ? std::max(1, static_cast<int>(std::ceil(rate_ * .020))) : 0;
        if (!remaining_) coefficients_ = target_;
    }
    void reset() noexcept { low_ = {}; high_ = {}; input_ = {}; }
    void process(double& left, double& right) noexcept {
        // Interpolate stable poles and gains, preserving audio-thread histories.
        // Transcendental coefficient calculation happens only on target changes.
        if (remaining_) {
            for (size_t i = 0; i < coefficients_.size(); ++i)
                coefficients_[i] += (target_[i] - coefficients_[i]) / remaining_;
            --remaining_;
        }
        const auto [lowA_, lowPole_, highA_, highPole_, gain_] = coefficients_;
        const std::array<double, 2> in {left, right};
        for (int c = 0; c < 2; ++c) {
            low_[c] = lowA_ * in[c] + lowPole_ * low_[c];
            high_[c] = highA_ * in[c] - highPole_ * input_[c] + highPole_ * high_[c];
            if (std::abs(low_[c]) < 1e-20) low_[c] = 0;
            if (std::abs(high_[c]) < 1e-20) high_[c] = 0;
        }
        input_ = in;
        left = (high_[0] + low_[1]) * gain_;
        right = (high_[1] + low_[0]) * gain_;
    }
    bool hasTail() const noexcept {
        for (int c = 0; c < 2; ++c) if (std::abs(low_[c]) > 1e-7 || std::abs(high_[c]) > 1e-7) return true;
        return false;
    }
private:
    double rate_ = 0, cutoff_ = 0, feed_ = 0;
    std::array<double, 5> coefficients_ { 0, 0, 1, 0, 1 }, target_ { 0, 0, 1, 0, 1 };
    int remaining_ = 0;
    std::array<double, 2> low_ {}, high_ {}, input_ {};
};
}
