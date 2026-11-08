#pragma once

#include <algorithm>
#include <cmath>

namespace echo
{
// Shared soft-knee compressor law used by the broadband compressor,
// multiband compressor, and de-esser. Positive values are gain reduction.
inline double computeDynamicsReductionDb(double inputDb, double thresholdDb, double ratio, double kneeDb)
{
    const double safeRatio = std::max(ratio, 1.0);
    const double overDb = inputDb - thresholdDb;
    const double slope = 1.0 - 1.0 / safeRatio;
    if (kneeDb <= 0.001)
        return std::max(0.0, overDb * slope);

    const double halfKnee = kneeDb * 0.5;
    if (overDb <= -halfKnee)
        return 0.0;
    if (overDb >= halfKnee)
        return overDb * slope;

    const double kneePosition = overDb + halfKnee;
    return slope * kneePosition * kneePosition / (2.0 * kneeDb);
}
} // namespace echo
