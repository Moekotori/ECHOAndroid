/* Airwindows Tape, Copyright (c) 2016 Chris Johnson / Airwindows.
 * MIT License; see LICENSE.txt. ECHO adaptation: DSP-only double path,
 * typed frame count, deterministic reset and no denormal-noise injection. */
#pragma once
#include <cstdint>
namespace echo {
class AirwindowsTape final {
public:
    explicit AirwindowsTape(double rate) noexcept : rate_(rate) {}
    void reset() noexcept { *this = AirwindowsTape(rate_); }
    void process(double** inputs, double** outputs, int sampleFrames) noexcept;
private:
    double getSampleRate() const noexcept { return rate_; }
    double rate_;
	double iirMidRollerAL {};
	double iirMidRollerBL {};
	double iirHeadBumpAL {};
	double iirHeadBumpBL {};
	
	double iirMidRollerAR {};
	double iirMidRollerBR {};
	double iirHeadBumpAR {};
	double iirHeadBumpBR {};
	
	double biquadAL[9] {};
	double biquadBL[9] {};
	double biquadCL[9] {};
	double biquadDL[9] {};
	
	double biquadAR[9] {};
	double biquadBR[9] {};
	double biquadCR[9] {};
	double biquadDR[9] {};
	bool flip = false;
	
	double lastSampleL {};
	double lastSampleR {};
	
	uint32_t fpdL = 0x12345678;
	uint32_t fpdR = 0x87654321;

    float A = 0.5f, B = 0.4f;
};
}
