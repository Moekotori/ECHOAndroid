#pragma once

#include "EqTypes.h"

#include <algorithm>
#include <array>
#include <cmath>

namespace echo
{
inline double eqCoefficientFrequency(double sampleRate, double frequencyHz, bool preserveParameters)
{
    return std::clamp(frequencyHz, preserveParameters ? 1.0 : 10.0,
        preserveParameters ? std::nextafter(sampleRate * 0.5, 0.0) : sampleRate * 0.45);
}

struct BiquadCoefficients
{
    double b0 = 1.0;
    double b1 = 0.0;
    double b2 = 0.0;
    double a1 = 0.0;
    double a2 = 0.0;
};

struct BiquadState
{
    double x1 = 0.0;
    double x2 = 0.0;
    double y1 = 0.0;
    double y2 = 0.0;

    void reset()
    {
        x1 = 0.0;
        x2 = 0.0;
        y1 = 0.0;
        y2 = 0.0;
    }

    // Direct Form I in double. Float coefficients lose the low-frequency poles
    // that a studio bell relies on, especially below 50 Hz at high Q.
    float process(float input, const BiquadCoefficients& coefficients)
    {
        return static_cast<float>(processDouble(input, coefficients));
    }

    double processDouble(double input, const BiquadCoefficients& coefficients)
    {
        const double in = input;
        const double output = coefficients.b0 * in
            + coefficients.b1 * x1
            + coefficients.b2 * x2
            - coefficients.a1 * y1
            - coefficients.a2 * y2;

        x2 = x1;
        x1 = in;
        y2 = y1;
        y1 = std::isfinite(output) ? output : 0.0;
        if (std::abs(y1) < 1.0e-20)
            y1 = 0.0;

        return y1;
    }
};

inline BiquadCoefficients makePeakingCoefficients(
    double sampleRate,
    double frequencyHz,
    double gainDb,
    double q,
    bool preserveParameters = false)
{
    if (sampleRate <= 0.0 || std::abs(gainDb) < 0.0001f)
        return {};

    constexpr double pi = 3.14159265358979323846;
    const double safeFrequency = eqCoefficientFrequency(sampleRate, frequencyHz, preserveParameters);
    const double safeQ = std::max(0.1, static_cast<double>(q));
    const double a = std::pow(10.0, static_cast<double>(gainDb) / 40.0);
    const double omega = 2.0 * pi * safeFrequency / sampleRate;
    const double sinOmega = std::sin(omega);
    const double cosOmega = std::cos(omega);
    const double alpha = sinOmega / (2.0 * safeQ);

    const double b0 = 1.0 + alpha * a;
    const double b1 = -2.0 * cosOmega;
    const double b2 = 1.0 - alpha * a;
    const double a0 = 1.0 + alpha / a;
    const double a1 = -2.0 * cosOmega;
    const double a2 = 1.0 - alpha / a;

    if (std::abs(a0) < 1.0e-12)
        return {};

    return {
        b0 / a0,
        b1 / a0,
        b2 / a0,
        a1 / a0,
        a2 / a0,
    };
}

inline BiquadCoefficients makeLowShelfCoefficients(
    double sampleRate,
    double frequencyHz,
    double gainDb,
    double q,
    bool preserveParameters = false)
{
    if (sampleRate <= 0.0 || std::abs(gainDb) < 0.0001f)
        return {};

    constexpr double pi = 3.14159265358979323846;
    const double safeFrequency = eqCoefficientFrequency(sampleRate, frequencyHz, preserveParameters);
    const double safeShelfSlope = std::max(preserveParameters ? 0.001 : 0.1, q);
    const double a = std::pow(10.0, static_cast<double>(gainDb) / 40.0);
    const double omega = 2.0 * pi * safeFrequency / sampleRate;
    const double sinOmega = std::sin(omega);
    const double cosOmega = std::cos(omega);
    const double sqrtA = std::sqrt(a);
    const double alpha = sinOmega / 2.0 * std::sqrt(std::max(0.0, (a + 1.0 / a) * (1.0 / safeShelfSlope - 1.0) + 2.0));
    const double twoSqrtAAlpha = 2.0 * sqrtA * alpha;

    const double b0 = a * ((a + 1.0) - (a - 1.0) * cosOmega + twoSqrtAAlpha);
    const double b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosOmega);
    const double b2 = a * ((a + 1.0) - (a - 1.0) * cosOmega - twoSqrtAAlpha);
    const double a0 = (a + 1.0) + (a - 1.0) * cosOmega + twoSqrtAAlpha;
    const double a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosOmega);
    const double a2 = (a + 1.0) + (a - 1.0) * cosOmega - twoSqrtAAlpha;

    if (std::abs(a0) < 1.0e-12)
        return {};

    return {
        b0 / a0,
        b1 / a0,
        b2 / a0,
        a1 / a0,
        a2 / a0,
    };
}

inline BiquadCoefficients makeHighShelfCoefficients(
    double sampleRate,
    double frequencyHz,
    double gainDb,
    double q,
    bool preserveParameters = false)
{
    if (sampleRate <= 0.0 || std::abs(gainDb) < 0.0001f)
        return {};

    constexpr double pi = 3.14159265358979323846;
    const double safeFrequency = eqCoefficientFrequency(sampleRate, frequencyHz, preserveParameters);
    const double safeShelfSlope = std::max(preserveParameters ? 0.001 : 0.1, q);
    const double a = std::pow(10.0, static_cast<double>(gainDb) / 40.0);
    const double omega = 2.0 * pi * safeFrequency / sampleRate;
    const double sinOmega = std::sin(omega);
    const double cosOmega = std::cos(omega);
    const double sqrtA = std::sqrt(a);
    const double alpha = sinOmega / 2.0 * std::sqrt(std::max(0.0, (a + 1.0 / a) * (1.0 / safeShelfSlope - 1.0) + 2.0));
    const double twoSqrtAAlpha = 2.0 * sqrtA * alpha;

    const double b0 = a * ((a + 1.0) + (a - 1.0) * cosOmega + twoSqrtAAlpha);
    const double b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosOmega);
    const double b2 = a * ((a + 1.0) + (a - 1.0) * cosOmega - twoSqrtAAlpha);
    const double a0 = (a + 1.0) - (a - 1.0) * cosOmega + twoSqrtAAlpha;
    const double a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosOmega);
    const double a2 = (a + 1.0) - (a - 1.0) * cosOmega - twoSqrtAAlpha;

    if (std::abs(a0) < 1.0e-12)
        return {};

    return {
        b0 / a0,
        b1 / a0,
        b2 / a0,
        a1 / a0,
        a2 / a0,
    };
}

inline BiquadCoefficients makeLowPassCoefficients(
    double sampleRate,
    double frequencyHz,
    double q,
    bool preserveParameters = false)
{
    if (sampleRate <= 0.0)
        return {};

    constexpr double pi = 3.14159265358979323846;
    const double safeFrequency = eqCoefficientFrequency(sampleRate, frequencyHz, preserveParameters);
    const double safeQ = std::max(0.1, static_cast<double>(q));
    const double omega = 2.0 * pi * safeFrequency / sampleRate;
    const double sinOmega = std::sin(omega);
    const double cosOmega = std::cos(omega);
    const double alpha = sinOmega / (2.0 * safeQ);

    const double b0 = (1.0 - cosOmega) / 2.0;
    const double b1 = 1.0 - cosOmega;
    const double b2 = (1.0 - cosOmega) / 2.0;
    const double a0 = 1.0 + alpha;
    const double a1 = -2.0 * cosOmega;
    const double a2 = 1.0 - alpha;

    if (std::abs(a0) < 1.0e-12)
        return {};

    return {
        b0 / a0,
        b1 / a0,
        b2 / a0,
        a1 / a0,
        a2 / a0,
    };
}

inline BiquadCoefficients makeHighPassCoefficients(
    double sampleRate,
    double frequencyHz,
    double q,
    bool preserveParameters = false)
{
    if (sampleRate <= 0.0)
        return {};

    constexpr double pi = 3.14159265358979323846;
    const double safeFrequency = eqCoefficientFrequency(sampleRate, frequencyHz, preserveParameters);
    const double safeQ = std::max(0.1, static_cast<double>(q));
    const double omega = 2.0 * pi * safeFrequency / sampleRate;
    const double sinOmega = std::sin(omega);
    const double cosOmega = std::cos(omega);
    const double alpha = sinOmega / (2.0 * safeQ);

    const double b0 = (1.0 + cosOmega) / 2.0;
    const double b1 = -(1.0 + cosOmega);
    const double b2 = (1.0 + cosOmega) / 2.0;
    const double a0 = 1.0 + alpha;
    const double a1 = -2.0 * cosOmega;
    const double a2 = 1.0 - alpha;

    if (std::abs(a0) < 1.0e-12)
        return {};

    return {
        b0 / a0,
        b1 / a0,
        b2 / a0,
        a1 / a0,
        a2 / a0,
    };
}

inline BiquadCoefficients makeNotchCoefficients(
    double sampleRate,
    double frequencyHz,
    double q,
    bool preserveParameters = false)
{
    if (sampleRate <= 0.0)
        return {};

    constexpr double pi = 3.14159265358979323846;
    const double safeFrequency = eqCoefficientFrequency(sampleRate, frequencyHz, preserveParameters);
    const double safeQ = std::max(0.1, static_cast<double>(q));
    const double omega = 2.0 * pi * safeFrequency / sampleRate;
    const double sinOmega = std::sin(omega);
    const double cosOmega = std::cos(omega);
    const double alpha = sinOmega / (2.0 * safeQ);

    const double b0 = 1.0;
    const double b1 = -2.0 * cosOmega;
    const double b2 = 1.0;
    const double a0 = 1.0 + alpha;
    const double a1 = -2.0 * cosOmega;
    const double a2 = 1.0 - alpha;

    if (std::abs(a0) < 1.0e-12)
        return {};

    return {
        b0 / a0,
        b1 / a0,
        b2 / a0,
        a1 / a0,
        a2 / a0,
    };
}

inline BiquadCoefficients makeEqCoefficients(
    double sampleRate,
    double frequencyHz,
    double gainDb,
    double q,
    EqFilterType filterType,
    bool enabled,
    bool preserveParameters = false)
{
    if (! enabled || ! std::isfinite(frequencyHz) || ! std::isfinite(gainDb) || ! std::isfinite(q))
        return {};

    switch (filterType)
    {
        case EqFilterType::LowShelf:
            return makeLowShelfCoefficients(sampleRate, frequencyHz, gainDb, q, preserveParameters);
        case EqFilterType::HighShelf:
            return makeHighShelfCoefficients(sampleRate, frequencyHz, gainDb, q, preserveParameters);
        case EqFilterType::LowPass:
            return makeLowPassCoefficients(sampleRate, frequencyHz, q, preserveParameters);
        case EqFilterType::HighPass:
            return makeHighPassCoefficients(sampleRate, frequencyHz, q, preserveParameters);
        case EqFilterType::Notch:
            return makeNotchCoefficients(sampleRate, frequencyHz, q, preserveParameters);
        case EqFilterType::Peaking:
        default:
            return makePeakingCoefficients(sampleRate, frequencyHz, gainDb, q, preserveParameters);
    }
}

inline BiquadCoefficients makeBandpassCoefficients(double sampleRate, double frequencyHz, double q, bool preserveParameters = false)
{
    if (sampleRate <= 0.0 || ! std::isfinite(frequencyHz) || ! std::isfinite(q))
        return {};

    constexpr double pi = 3.14159265358979323846;
    const double safeFrequency = eqCoefficientFrequency(sampleRate, frequencyHz, preserveParameters);
    const double safeQ = std::max(0.1, static_cast<double>(q));
    const double omega = 2.0 * pi * safeFrequency / sampleRate;
    const double alpha = std::sin(omega) / (2.0 * safeQ);
    const double a0 = 1.0 + alpha;
    if (std::abs(a0) < 1.0e-12)
        return {};

    return {
        alpha / a0,
        0.0,
        -alpha / a0,
        (-2.0 * std::cos(omega)) / a0,
        (1.0 - alpha) / a0,
    };
}
} // namespace echo
