#pragma once
#include "AdditionalSoundEffects.h"
#include <algorithm>
#include <vector>

namespace echo {
// Prepared once per native instance; one independent history per channel pair.
template<class Core, bool PreserveMono = false>
class PairedSoundEffect final : public AdditionalSoundEffect {
public:
    PairedSoundEffect(double rate, int channels, double tailSeconds)
        : channels_(channels), tail_(static_cast<int>(rate * tailSeconds)) {
        pairs_.reserve((channels + 1) / 2);
        for (int c = 0; c < channels; c += 2) pairs_.emplace_back(rate);
    }
    void process(double* const* input, double* const* output, int frames) noexcept override {
        for (int c = 0; c < channels_; c += 2) {
            if constexpr (PreserveMono) {
                if (c + 1 == channels_) { std::copy_n(input[c], frames, output[c]); continue; }
            }
            double* inputs[2] {input[c], input[std::min(c + 1, channels_ - 1)]};
            // The sound-effect owner reserves the odd pair's extra output.
            double* outputs[2] {output[c], output[c + 1]};
            pairs_[c / 2].process(inputs, outputs, frames);
        }
    }
    void reset() noexcept override { for (auto& pair : pairs_) pair.reset(); }
    int tailSamples() const noexcept override { return tail_; }
private:
    int channels_, tail_;
    std::vector<Core> pairs_;
};
}
