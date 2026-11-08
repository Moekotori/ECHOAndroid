#pragma once
#include <memory>
#include <string>

namespace echo {
// A selected, prepared algorithm owned by the existing native sound-effect
// instance. No settings, transport, UI or playback facts are owned here.
class AdditionalSoundEffect {
public:
    virtual ~AdditionalSoundEffect() = default;
    virtual void process(double* const* input, double* const* output, int frames) noexcept = 0;
    virtual void reset() noexcept = 0;
    virtual int latencySamples() const noexcept { return 0; }
    virtual int tailSamples() const noexcept { return 0; }
};
bool isAdditionalSoundEffect(const std::string& preset) noexcept;
std::unique_ptr<AdditionalSoundEffect> createAdditionalSoundEffect(
    const std::string& preset, double sampleRate, int maximumBlock, int channels);
}
