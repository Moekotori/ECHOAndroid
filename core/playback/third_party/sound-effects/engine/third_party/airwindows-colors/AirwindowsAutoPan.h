/* Airwindows AutoPan, Copyright (c) 2016 Chris Johnson / Airwindows.
 * MIT License; see LICENSE.txt. ECHO adaptation: DSP-only double path,
 * typed frame count, deterministic reset and no denormal-noise injection. */
#pragma once
#include <cstdint>
namespace echo {
class AirwindowsAutoPan final {
public:
    explicit AirwindowsAutoPan(double sampleRate) noexcept : sampleRate_(sampleRate) {}
    void reset() noexcept { *this = AirwindowsAutoPan(sampleRate_); }
    void process(double** inputs, double** outputs, int sampleFrames) noexcept;
private:
    double getSampleRate() const noexcept { return sampleRate_; }
    double sampleRate_;
	double rate = 0.0, oldfpd = 0.6;
	
	uint32_t fpdL = 0x12345678;
	uint32_t fpdR = 0x87654321;

    float A = 0.23f, B = 0.0f, C = 0.0f, D = 0.55f;
};
}
