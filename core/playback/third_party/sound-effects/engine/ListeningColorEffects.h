#pragma once
#include "AdditionalSoundEffects.h"

namespace echo {
bool isListeningColorEffect(const std::string& preset) noexcept;
std::unique_ptr<AdditionalSoundEffect> createListeningColorEffect(const std::string& preset, double rate, int channels);
}
