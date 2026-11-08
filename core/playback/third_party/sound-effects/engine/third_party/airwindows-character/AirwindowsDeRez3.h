/* Airwindows DeRez3, Copyright (c) Airwindows / Chris Johnson.
 * MIT License; see LICENSE.txt. ECHO adaptation: native double-only DSP,
 * deterministic reset, fixed tuning, no VST SDK or denormal-noise injection. */
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
namespace echo {
class AirwindowsDeRez3 final {
public:
    explicit AirwindowsDeRez3(double sampleRate) noexcept : sampleRate_(sampleRate) { rezA = rezB = std::min(1.0, std::pow(A, 3) * 44100 / sampleRate_); bitA = bitB = B * 15.0 + 1; wetA = wetB = C * 2.0; bez[bez_cycle] = 1.0; }
    void reset() noexcept { *this = AirwindowsDeRez3(sampleRate_); }
    void process(double** inputs, double** outputs, int sampleFrames) noexcept;
private:
    double getSampleRate() const noexcept { return sampleRate_; }
    double sampleRate_;

	
	
	double rezA {};
	double rezB {};
	double bitA {};
	double bitB {};
	double wetA {};
	double wetB {};
	
	enum {
		bez_AL,
		bez_BL,
		bez_CL,	
		bez_InL,
		bez_UnInL,
		bez_SampL,
		bez_AR,
		bez_BR,
		bez_CR,	
		bez_InR,
		bez_UnInR,
		bez_SampR,
		bez_cycle,
		bez_total
	}; //the new undersampling. bez signifies the bezier curve reconstruction
	double bez[bez_total] {};

	uint32_t fpdL = 0x12345678;
	uint32_t fpdR = 0x87654321;
	
    float A = .72f, B = .55f, C = .75f;
};
}
