#include "ListeningColorEffects.h"
#include "third_party/airwindows-colors/AirwindowsTube.h"
#include "third_party/airwindows-colors/AirwindowsVinyl.h"
#include "third_party/airwindows-colors/AirwindowsAutoPan.h"
#include "PairedSoundEffect.h"
#include <stdexcept>

namespace echo {
bool isListeningColorEffect(const std::string& preset) noexcept {
    return preset == "tube" || preset == "vinyl" || preset == "autopan";
}
std::unique_ptr<AdditionalSoundEffect> createListeningColorEffect(const std::string& preset, double rate, int channels) {
    if (preset == "tube") return std::make_unique<PairedSoundEffect<AirwindowsTube>>(rate, channels, .05);
    if (preset == "vinyl") return std::make_unique<PairedSoundEffect<AirwindowsVinyl>>(rate, channels, 1.0);
    if (preset == "autopan") return std::make_unique<PairedSoundEffect<AirwindowsAutoPan, true>>(rate, channels, 0.0);
    throw std::runtime_error("unknown_sound_effect_preset");
}
}
