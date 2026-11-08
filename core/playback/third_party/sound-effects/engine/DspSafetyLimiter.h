#pragma once

#include <algorithm>
#include <atomic>
#include <cmath>
#include <limits>

namespace echo
{
inline constexpr float dspLimiterCeilingMinDb = -6.0f;
inline constexpr float dspLimiterCeilingMaxDb = -0.1f;
inline constexpr float dspLimiterAutoUpstreamCeilingDb = -1.0f;

inline std::atomic<bool>& dspSafetyLimiterEnabledFlag()
{
    static std::atomic<bool> enabled { true };
    return enabled;
}

inline void setDspSafetyLimiterEnabled(bool enabled)
{
    dspSafetyLimiterEnabledFlag().store(enabled, std::memory_order_release);
}

inline bool isDspSafetyLimiterEnabled()
{
    return dspSafetyLimiterEnabledFlag().load(std::memory_order_acquire);
}

// NaN means the historical automatic ceiling: -1 dBTP while upstream PCM
// processing is active, otherwise 0 dBFS.
inline std::atomic<float>& dspLimiterCeilingDbFlag()
{
    static std::atomic<float> ceiling { std::numeric_limits<float>::quiet_NaN() };
    return ceiling;
}

inline void setDspLimiterCeilingDb(float ceilingDb)
{
    const float stored = std::isfinite(ceilingDb)
        ? std::clamp(ceilingDb, dspLimiterCeilingMinDb, dspLimiterCeilingMaxDb)
        : std::numeric_limits<float>::quiet_NaN();
    dspLimiterCeilingDbFlag().store(stored, std::memory_order_release);
}

inline float dspLimiterCeilingDb()
{
    return dspLimiterCeilingDbFlag().load(std::memory_order_acquire);
}

inline constexpr float dspLimiterReleaseMinMs = 10.0f;
inline constexpr float dspLimiterReleaseMaxMs = 400.0f;
inline constexpr float dspLimiterReleaseDefaultMs = 80.0f;

inline std::atomic<float>& dspLimiterReleaseMsFlag()
{
    static std::atomic<float> releaseMs { dspLimiterReleaseDefaultMs };
    return releaseMs;
}

inline void setDspLimiterReleaseMs(float releaseMs)
{
    const float stored = std::isfinite(releaseMs)
        ? std::clamp(releaseMs, dspLimiterReleaseMinMs, dspLimiterReleaseMaxMs)
        : dspLimiterReleaseDefaultMs;
    dspLimiterReleaseMsFlag().store(stored, std::memory_order_release);
}

inline float dspLimiterReleaseMs()
{
    return dspLimiterReleaseMsFlag().load(std::memory_order_acquire);
}

inline float resolveDspLimiterCeilingDb(bool upstreamPcmProcessingActive)
{
    const float manual = dspLimiterCeilingDb();
    if (!std::isfinite(manual))
        return upstreamPcmProcessingActive ? dspLimiterAutoUpstreamCeilingDb : 0.0f;
    return manual;
}
} // namespace echo
