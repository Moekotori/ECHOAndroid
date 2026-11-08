/* Airwindows Tube2, Copyright (c) 2016 Chris Johnson / Airwindows.
 * MIT License; see LICENSE.txt. ECHO adaptation: DSP-only double path,
 * typed frame count, deterministic reset and no denormal-noise injection. */
#pragma once
#include <cstdint>
namespace echo {
class AirwindowsTube final {
public:
    explicit AirwindowsTube(double sampleRate) noexcept : sampleRate_(sampleRate) {}
    void reset() noexcept { *this = AirwindowsTube(sampleRate_); }
    void process(double** inputs, double** outputs, int sampleFrames) noexcept;
private:
    double getSampleRate() const noexcept { return sampleRate_; }
    double sampleRate_;
	double previousSampleA {};
	double previousSampleB {};
	double previousSampleC {};
	double previousSampleD {};
	double previousSampleE {};
	double previousSampleF {};
	
	uint32_t fpdL = 0x12345678;
	uint32_t fpdR = 0x87654321;

    float A = 1.0f, B = 0.55f;
};
}
