#pragma once
#include <memory>
#include <span>

namespace echo_android {
// Configuration: six header floats, then 12 per band (maximum 64 including MSEB).
class PcEqualizer {
public:
    PcEqualizer(int rate, int channels, std::span<const float> configuration, bool preserveAll = false);
    ~PcEqualizer();
    bool update(std::span<const float> configuration);
    void process(float* interleaved, int frames, bool enabled) noexcept;
    void reset() noexcept;
    bool processing() const noexcept;
    int channelCount() const noexcept;
private:
    struct Runtime;
    std::unique_ptr<Runtime> runtime_;
};
}
