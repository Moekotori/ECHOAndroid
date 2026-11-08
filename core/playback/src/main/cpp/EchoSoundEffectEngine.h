#pragma once
#include <memory>
#include <string>

namespace echo_android {
// Prepared off the audio thread. Owns bounded planar scratch and all effect history.
class SoundEffectEngine {
public:
    SoundEffectEngine(const std::string& preset, int rate, int channels);
    ~SoundEffectEngine();
    void process(float* interleaved, int frames, bool enabled) noexcept;
    void reset() noexcept;
    int tailRemaining() const noexcept;
    int latencyFrames() const noexcept;
    bool dry() const noexcept;
    int channelCount() const noexcept;
private:
    struct Runtime;
    std::unique_ptr<Runtime> runtime_;
};
}
