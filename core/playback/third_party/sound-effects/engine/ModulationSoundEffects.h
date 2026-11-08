#pragma once
#include "AdditionalSoundEffects.h"

namespace echo {
bool isModulationSoundEffect(const std::string& preset) noexcept;
std::unique_ptr<AdditionalSoundEffect> createModulationSoundEffect(
    const std::string& preset, double sampleRate, int channels);
}
