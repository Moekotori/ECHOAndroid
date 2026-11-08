#pragma once

#include "EqBand.h"

namespace echo
{
// Trapezoidal SVF bell from Andrew Simper, Cytomic SvfLinearTrapAllOutputs.
// g = tan(pi f / fs) stays stable while Q and frequency move. Shelves, passes
// and notches stay RBJ because saved curves and headphone correction use that slope.
struct EqSvfCoefficients
{
    double gt1 = 0.0;
    double gk1 = 0.0;
    double gt2 = 0.0;
    double m0x = 1.0;
    double m1x = 0.0;
    double m2x = 0.0;
};

struct EqBandCoefficients
{
    bool useSvf = true;
    bool transparent = true;
    double stateDecay = 0.999;
    EqSvfCoefficients svf {};
    BiquadCoefficients biquad {};
};

struct EqSvfState
{
    double ic1 = 0.0;
    double ic2 = 0.0;

    void reset()
    {
        ic1 = 0.0;
        ic2 = 0.0;
    }

    void decay(double amount)
    {
        ic1 *= amount;
        ic2 *= amount;
        if (std::abs(ic1) < 1.0e-20)
            ic1 = 0.0;
        if (std::abs(ic2) < 1.0e-20)
            ic2 = 0.0;
    }

    float process(float input, const EqSvfCoefficients& coefficients)
    {
        return static_cast<float>(processDouble(input, coefficients));
    }

    double processDouble(double input, const EqSvfCoefficients& coefficients)
    {
        const double x = input;
        const double t0 = x - ic2;
        const double t1 = coefficients.gt1 * t0 - coefficients.gk1 * ic1;
        const double t2 = coefficients.gt2 * t0 + coefficients.gt1 * ic1;
        const double v1 = ic1 + t1;
        const double v2 = ic2 + t2;
        ic1 += 2.0 * t1;
        ic2 += 2.0 * t2;
        if (! std::isfinite(ic1) || ! std::isfinite(ic2))
        {
            reset();
            return 0.0f;
        }

        const double output = coefficients.m0x * x + coefficients.m1x * v1 + coefficients.m2x * v2;
        return std::isfinite(output) ? output : 0.0;
    }
};

struct EqBandFilter
{
    EqSvfState svf {};
    BiquadState biquad {};
    bool svfMode = true;

    void reset()
    {
        svf.reset();
        biquad.reset();
    }

    bool hasEnergy(double threshold) const
    {
        return std::abs(svf.ic1) > threshold || std::abs(svf.ic2) > threshold
            || std::abs(biquad.y1) > threshold || std::abs(biquad.y2) > threshold;
    }

    float process(float input, const EqBandCoefficients& coefficients)
    {
        return static_cast<float>(processDouble(input, coefficients));
    }

    double processDouble(double input, const EqBandCoefficients& coefficients)
    {
        if (coefficients.useSvf != svfMode)
        {
            reset();
            svfMode = coefficients.useSvf;
        }

        if (coefficients.transparent)
        {
            svf.decay(coefficients.stateDecay);
            biquad.x1 *= coefficients.stateDecay;
            biquad.x2 *= coefficients.stateDecay;
            biquad.y1 *= coefficients.stateDecay;
            biquad.y2 *= coefficients.stateDecay;
            return input;
        }

        return coefficients.useSvf
            ? svf.processDouble(input, coefficients.svf)
            : biquad.processDouble(input, coefficients.biquad);
    }
};

inline EqSvfCoefficients makePeakingSvfCoefficients(
    double sampleRate,
    double frequencyHz,
    double gainDb,
    double q)
{
    const double safeFrequency = std::clamp<double>(frequencyHz, 10.0, sampleRate * 0.45);
    const double safeQ = std::max(0.1, static_cast<double>(q));
    const double amplitude = std::pow(10.0, static_cast<double>(gainDb) / 40.0);
    const double g = std::tan(3.14159265358979323846 * safeFrequency / sampleRate);
    const double k0 = 1.0 / safeQ;
    const double k = k0 / amplitude;
    const double m0 = 1.0;
    const double m1 = k0 * amplitude;
    const double m2 = 1.0;
    const double gt0 = 1.0 / (1.0 + g * (g + k));
    const double gt1 = g * gt0;
    const double gk1 = g * (g + k) * gt0;
    const double gt2 = g * gt1;
    return {
        gt1,
        gk1,
        gt2,
        m0,
        m1 - k * m0,
        m2 - m0,
    };
}

inline EqBandCoefficients makeEqBandCoefficients(
    double sampleRate,
    double frequencyHz,
    double gainDb,
    double q,
    EqFilterType filterType,
    bool enabled,
    bool preserveParameters = false)
{
    EqBandCoefficients coefficients;
    const double safeRate = sampleRate > 0.0 ? sampleRate : 48000.0;
    coefficients.stateDecay = std::exp(-1.0 / (safeRate * 0.005));
    const bool gainBearing = filterType == EqFilterType::Peaking
        || filterType == EqFilterType::LowShelf
        || filterType == EqFilterType::HighShelf;
    if (! enabled || (gainBearing && std::abs(gainDb) < 0.0001f))
        return coefficients;

    if (filterType == EqFilterType::Peaking && !preserveParameters)
    {
        coefficients.transparent = false;
        coefficients.useSvf = true;
        coefficients.svf = makePeakingSvfCoefficients(safeRate, frequencyHz, gainDb, q);
        return coefficients;
    }

    coefficients.useSvf = false;
    coefficients.biquad = makeEqCoefficients(safeRate, frequencyHz, gainDb, q, filterType, true, preserveParameters);
    const bool identity = coefficients.biquad.b0 == 1.0
        && coefficients.biquad.b1 == 0.0
        && coefficients.biquad.b2 == 0.0
        && coefficients.biquad.a1 == 0.0
        && coefficients.biquad.a2 == 0.0;
    coefficients.transparent = identity;
    return coefficients;
}
} // namespace echo
