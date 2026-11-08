/* Airwindows Point, Copyright (c) 2016 Chris Johnson / Airwindows.
 * MIT License; see LICENSE.txt. ECHO adaptation: native double-only DSP,
 * deterministic reset, fixed tuning, no VST SDK or denormal-noise injection. */
#pragma once
#include <cmath>
#include <cstdint>
namespace echo {
class AirwindowsPoint final {
public:
    explicit AirwindowsPoint(double sampleRate) noexcept : sampleRate_(sampleRate) {  }
    void reset() noexcept { *this = AirwindowsPoint(sampleRate_); }
    void process(double** inputs, double** outputs, int sampleFrames) noexcept;
private:
    double getSampleRate() const noexcept { return sampleRate_; }
    double sampleRate_;
	uint32_t fpdL = 0x12345678;
	uint32_t fpdR = 0x87654321;
	bool fpFlip = true;

	double nibAL {};
	double nobAL {};
	double nibBL {};
	double nobBL {};
	double nibAR {};
	double nobAR {};
	double nibBR {};
	double nobBR {};
	

    float A = 0.5f, B = 0.62f, C = 0.6f;
};
}
