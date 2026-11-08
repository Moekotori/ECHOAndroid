/* Airwindows RingModulator, Copyright (c) Airwindows / Chris Johnson.
 * MIT License; see LICENSE.txt. ECHO adaptation: native double-only DSP,
 * deterministic reset, fixed tuning, no VST SDK or denormal-noise injection. */
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
namespace echo {
class AirwindowsRingModulator final {
public:
    explicit AirwindowsRingModulator(double sampleRate) noexcept : sampleRate_(sampleRate) { sinePosL = sinePosR = 1.5707963267948966; incLA = incLB = std::pow(A, 5) * 44100 / sampleRate_; incRA = incRB = std::pow(B, 5) * 44100 / sampleRate_; }
    void reset() noexcept { *this = AirwindowsRingModulator(sampleRate_); }
    void process(double** inputs, double** outputs, int sampleFrames) noexcept;
private:
    double getSampleRate() const noexcept { return sampleRate_; }
    double sampleRate_;

	
	double sinePosL {};
	double sinePosR {};
	double incLA {};
	double incLB {};
	double incRA {};
	double incRB {};	

	uint32_t fpdL = 0x12345678;
	uint32_t fpdR = 0x87654321;
	
    float A = .52f, B = .52f, C = .5f, D = .65f;
};
}
