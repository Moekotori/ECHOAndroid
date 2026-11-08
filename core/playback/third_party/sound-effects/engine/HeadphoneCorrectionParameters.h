#pragma once

#include <cmath>

namespace echo
{
// Keep in sync with shared/audio/headphoneCorrectionParameters.ts.
// Dedicated OPRA domain; the personal EQ keeps its original controls/limits.
inline constexpr float correctionMinFrequencyHz = 1.0f;
inline constexpr float correctionMaxFrequencyHz = 20000.0f;
inline constexpr float correctionMinGainDb = -60.0f;
inline constexpr float correctionMaxGainDb = 30.0f;
inline constexpr float correctionMinPreampDb = -60.0f;
inline constexpr float correctionMaxPreampDb = 24.0f;
inline constexpr float correctionMinQ = 0.001f;
inline constexpr float correctionMaxQ = 1000.0f;

inline bool correctionNumberInRange(double value, double minimum, double maximum)
{
    return std::isfinite(value) && value >= minimum && value <= maximum;
}
} // namespace echo
