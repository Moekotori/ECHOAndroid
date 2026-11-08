#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <string>
#include <vector>

namespace echo
{
constexpr int eqBandCount = 31;
constexpr float eqMinGainDb = -24.0f;
constexpr float eqMaxGainDb = 12.0f;
constexpr float eqMinPreampDb = -12.0f;
constexpr float eqMaxPreampDb = 6.0f;
constexpr float eqMinFrequencyHz = 20.0f;
constexpr float eqMaxFrequencyHz = 20000.0f;
constexpr float eqMinQ = 0.1f;
constexpr float eqMaxQ = 12.0f;
constexpr float eqMaxNotchQ = 24.0f;
constexpr float eqTiltMinDb = -6.0f;
constexpr float eqTiltMaxDb = 6.0f;

enum class EqFilterType : int
{
    Peaking = 0,
    LowShelf = 1,
    HighShelf = 2,
    LowPass = 3,
    HighPass = 4,
    Notch = 5,
};

enum class EqBandScope : int
{
    Stereo = 0,
    Left = 1,
    Right = 2,
    Mid = 3,
    Side = 4,
};

enum class EqDynamicMode : int
{
    Off = 0,
    Down = 1,
    Up = 2,
};

constexpr int eqMaxDynamicBands = 4;
constexpr float eqDynamicThresholdMinDb = -48.0f;
constexpr float eqDynamicThresholdMaxDb = 0.0f;
constexpr float eqDynamicRatioMin = 1.0f;
constexpr float eqDynamicRatioMax = 20.0f;
constexpr float eqDynamicAttackMinMs = 0.5f;
constexpr float eqDynamicAttackMaxMs = 80.0f;
constexpr float eqDynamicReleaseMinMs = 10.0f;
constexpr float eqDynamicReleaseMaxMs = 400.0f;
constexpr float eqDynamicThresholdDefaultDb = -24.0f;
constexpr float eqDynamicRatioDefault = 2.0f;
constexpr float eqDynamicAttackDefaultMs = 5.0f;
constexpr float eqDynamicReleaseDefaultMs = 80.0f;

using EqGainArray = std::array<float, eqBandCount>;
using EqFrequencyArray = std::array<float, eqBandCount>;
using EqQArray = std::array<float, eqBandCount>;
using EqFilterTypeArray = std::array<EqFilterType, eqBandCount>;
using EqBandEnabledArray = std::array<bool, eqBandCount>;
using EqBandScopeArray = std::array<EqBandScope, eqBandCount>;
using EqDynamicModeArray = std::array<EqDynamicMode, eqBandCount>;

inline constexpr EqFrequencyArray eqFrequenciesHz {
    20.0f,
    25.0f,
    31.5f,
    40.0f,
    50.0f,
    63.0f,
    80.0f,
    100.0f,
    125.0f,
    160.0f,
    200.0f,
    250.0f,
    315.0f,
    400.0f,
    500.0f,
    630.0f,
    800.0f,
    1000.0f,
    1250.0f,
    1600.0f,
    2000.0f,
    2500.0f,
    3150.0f,
    4000.0f,
    5000.0f,
    6300.0f,
    8000.0f,
    10000.0f,
    12500.0f,
    16000.0f,
    20000.0f,
};

inline constexpr EqQArray eqDefaultQ {
    1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
    1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
    1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
    1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
};

constexpr EqGainArray eqDefaultFilledGain(float value)
{
    EqGainArray values {};
    for (auto& entry : values)
        entry = value;
    return values;
}

inline constexpr EqBandEnabledArray eqDefaultBandEnabled {
    true, true, true, true, true, true, true, true,
    true, true, true, true, true, true, true, true,
    true, true, true, true, true, true, true, true,
    true, true, true, true, true, true, true,
};

struct EqBandState
{
    float frequencyHz = 1000.0f;
    float gainDb = 0.0f;
    float q = 1.0f;
    EqFilterType filterType = EqFilterType::Peaking;
    bool enabled = true;
    EqBandScope scope = EqBandScope::Stereo;
    EqDynamicMode dynamicMode = EqDynamicMode::Off;
    float dynamicThresholdDb = eqDynamicThresholdDefaultDb;
    float dynamicRatio = eqDynamicRatioDefault;
    float dynamicAttackMs = eqDynamicAttackDefaultMs;
    float dynamicReleaseMs = eqDynamicReleaseDefaultMs;
};

struct EqState
{
    bool operator==(const EqState&) const = default;
    bool enabled = false;
    float preampDb = 0.0f;
    float strengthPercent = 100.0f;
    float tiltDb = 0.0f;
    EqGainArray bandGainsDb {};
    EqFrequencyArray bandFrequenciesHz = eqFrequenciesHz;
    EqQArray bandQ = eqDefaultQ;
    EqFilterTypeArray bandFilterTypes {};
    EqBandEnabledArray bandEnabled = eqDefaultBandEnabled;
    EqBandScopeArray bandScopes {};
    EqDynamicModeArray bandDynamicModes {};
    EqGainArray bandDynamicThresholdDb = eqDefaultFilledGain(eqDynamicThresholdDefaultDb);
    EqGainArray bandDynamicRatio = eqDefaultFilledGain(eqDynamicRatioDefault);
    EqGainArray bandDynamicAttackMs = eqDefaultFilledGain(eqDynamicAttackDefaultMs);
    EqGainArray bandDynamicReleaseMs = eqDefaultFilledGain(eqDynamicReleaseDefaultMs);
    std::string presetName = "Flat";
};

inline EqBandScope normalizeEqBandScope(int value)
{
    switch (value)
    {
        case static_cast<int>(EqBandScope::Left): return EqBandScope::Left;
        case static_cast<int>(EqBandScope::Right): return EqBandScope::Right;
        case static_cast<int>(EqBandScope::Mid): return EqBandScope::Mid;
        case static_cast<int>(EqBandScope::Side): return EqBandScope::Side;
        case static_cast<int>(EqBandScope::Stereo):
        default:
            return EqBandScope::Stereo;
    }
}

inline EqDynamicMode normalizeEqDynamicMode(int value)
{
    switch (value)
    {
        case static_cast<int>(EqDynamicMode::Down): return EqDynamicMode::Down;
        case static_cast<int>(EqDynamicMode::Up): return EqDynamicMode::Up;
        case static_cast<int>(EqDynamicMode::Off):
        default:
            return EqDynamicMode::Off;
    }
}

inline const char* eqBandScopeText(EqBandScope scope)
{
    switch (scope)
    {
        case EqBandScope::Left: return "left";
        case EqBandScope::Right: return "right";
        case EqBandScope::Mid: return "mid";
        case EqBandScope::Side: return "side";
        case EqBandScope::Stereo: return "stereo";
    }
    return "stereo";
}

inline EqBandScope parseEqBandScope(const std::string& text)
{
    if (text == "left") return EqBandScope::Left;
    if (text == "right") return EqBandScope::Right;
    if (text == "mid") return EqBandScope::Mid;
    if (text == "side") return EqBandScope::Side;
    return EqBandScope::Stereo;
}

inline const char* eqDynamicModeText(EqDynamicMode mode)
{
    switch (mode)
    {
        case EqDynamicMode::Down: return "down";
        case EqDynamicMode::Up: return "up";
        case EqDynamicMode::Off: return "off";
    }
    return "off";
}

inline EqDynamicMode parseEqDynamicMode(const std::string& text)
{
    if (text == "down") return EqDynamicMode::Down;
    if (text == "up") return EqDynamicMode::Up;
    return EqDynamicMode::Off;
}

inline float clampEqDynamicThresholdDb(float value)
{
    if (! std::isfinite(value))
        return eqDynamicThresholdDefaultDb;
    return std::clamp(value, eqDynamicThresholdMinDb, eqDynamicThresholdMaxDb);
}

inline float clampEqDynamicRatio(float value)
{
    if (! std::isfinite(value))
        return eqDynamicRatioDefault;
    return std::clamp(value, eqDynamicRatioMin, eqDynamicRatioMax);
}

inline float clampEqDynamicAttackMs(float value)
{
    if (! std::isfinite(value))
        return eqDynamicAttackDefaultMs;
    return std::clamp(value, eqDynamicAttackMinMs, eqDynamicAttackMaxMs);
}

inline float clampEqDynamicReleaseMs(float value)
{
    if (! std::isfinite(value))
        return eqDynamicReleaseDefaultMs;
    return std::clamp(value, eqDynamicReleaseMinMs, eqDynamicReleaseMaxMs);
}

struct EqPreset
{
    std::string id;
    std::string name;
    float preampDb = 0.0f;
    float strengthPercent = 100.0f;
    float tiltDb = 0.0f;
    std::vector<EqBandState> bands;
    std::string createdAt;
    std::string updatedAt;
    bool readonlyPreset = false;
};

inline float clampEqGainDb(float value)
{
    if (! std::isfinite(value))
        return 0.0f;

    if (value < eqMinGainDb)
        return eqMinGainDb;

    if (value > eqMaxGainDb)
        return eqMaxGainDb;

    return value;
}

inline float clampEqPreampDb(float value)
{
    if (! std::isfinite(value))
        return 0.0f;

    if (value < eqMinPreampDb)
        return eqMinPreampDb;

    if (value > eqMaxPreampDb)
        return eqMaxPreampDb;

    return value;
}

inline float clampEqFrequencyHz(float value)
{
    if (! std::isfinite(value))
        return 1000.0f;

    if (value < eqMinFrequencyHz)
        return eqMinFrequencyHz;

    if (value > eqMaxFrequencyHz)
        return eqMaxFrequencyHz;

    return value;
}

inline float eqQLimit(EqFilterType type)
{
    return type == EqFilterType::Notch ? eqMaxNotchQ : eqMaxQ;
}

inline bool eqFilterGainBearing(EqFilterType type)
{
    return type == EqFilterType::Peaking || type == EqFilterType::LowShelf || type == EqFilterType::HighShelf;
}

inline float clampEqTiltDb(float value)
{
    if (! std::isfinite(value))
        return 0.0f;

    if (value < eqTiltMinDb)
        return eqTiltMinDb;

    if (value > eqTiltMaxDb)
        return eqTiltMaxDb;

    return value;
}

inline float clampEqQ(float value, EqFilterType type = EqFilterType::Peaking)
{
    if (! std::isfinite(value))
        return 1.0f;

    if (value < eqMinQ)
        return eqMinQ;

    const float limit = eqQLimit(type);
    if (value > limit)
        return limit;

    return value;
}

inline float eqTiltOffsetDb(float frequencyHz, float tiltDb)
{
    if (! std::isfinite(tiltDb) || tiltDb == 0.0f || ! std::isfinite(frequencyHz) || frequencyHz <= 0.0f)
        return 0.0f;

    const float tilt = clampEqTiltDb(tiltDb);
    const float frequency = clampEqFrequencyHz(frequencyHz);
    const float position = std::log(frequency / eqMinFrequencyHz) / std::log(eqMaxFrequencyHz / eqMinFrequencyHz);
    return tilt * (2.0f * position - 1.0f);
}

inline float effectiveEqBandGainDb(
    float storedGainDb,
    float strengthPercent,
    float tiltDb,
    float frequencyHz,
    EqFilterType type,
    bool enabled)
{
    const float strength = std::isfinite(strengthPercent) ? std::clamp(strengthPercent, 0.0f, 100.0f) : 100.0f;
    float gain = storedGainDb * strength / 100.0f;
    if (enabled && eqFilterGainBearing(type))
        gain += eqTiltOffsetDb(frequencyHz, tiltDb);

    return clampEqGainDb(gain);
}

inline EqFilterType normalizeEqFilterType(int value)
{
    switch (static_cast<EqFilterType>(value))
    {
        case EqFilterType::LowShelf:
            return EqFilterType::LowShelf;
        case EqFilterType::HighShelf:
            return EqFilterType::HighShelf;
        case EqFilterType::LowPass:
            return EqFilterType::LowPass;
        case EqFilterType::HighPass:
            return EqFilterType::HighPass;
        case EqFilterType::Notch:
            return EqFilterType::Notch;
        case EqFilterType::Peaking:
        default:
            return EqFilterType::Peaking;
    }
}
} // namespace echo
