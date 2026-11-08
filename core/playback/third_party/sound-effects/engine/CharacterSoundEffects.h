#pragma once
#include "AdditionalSoundEffects.h"

namespace echo {
bool isCharacterSoundEffect(const std::string& preset) noexcept;
std::unique_ptr<AdditionalSoundEffect> createCharacterSoundEffect(
    const std::string& preset, double sampleRate, int channels);
}
