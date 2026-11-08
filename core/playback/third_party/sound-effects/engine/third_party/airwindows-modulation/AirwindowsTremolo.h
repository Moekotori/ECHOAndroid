/* Airwindows Tremolo, Copyright (c) 2016 Chris Johnson / Airwindows.
 * MIT License; see LICENSE.txt. ECHO adaptation: native double-only DSP,
 * deterministic reset, fixed tuning, no VST SDK or denormal-noise injection. */
#pragma once
#include <cmath>
#include <cstdint>
namespace echo {
class AirwindowsTremolo final {
public:
    explicit AirwindowsTremolo(double sampleRate) noexcept : sampleRate_(sampleRate) { sweep = 1.5707963267948966; speedAmount = std::pow(A, 4); depthAmount = B; lastSpeed = speedAmount; lastDepth = depthAmount; }
    void reset() noexcept { *this = AirwindowsTremolo(sampleRate_); }
    void process(double** inputs, double** outputs, int sampleFrames) noexcept;
private:
    double getSampleRate() const noexcept { return sampleRate_; }
    double sampleRate_;
	uint32_t fpdL = 0x12345678;
	uint32_t fpdR = 0x87654321;


	double sweep {};
	double speedChase {};
	double depthChase {};
	double speedAmount {};
	double depthAmount {};
	double lastSpeed {};
	double lastDepth {};
	

    float A = 0.65f, B = 0.18f;
};
}
