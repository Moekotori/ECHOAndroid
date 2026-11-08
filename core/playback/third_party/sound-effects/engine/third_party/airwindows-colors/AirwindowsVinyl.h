/* Airwindows ToVinyl4, Copyright (c) 2016 Chris Johnson / Airwindows.
 * MIT License; see LICENSE.txt. ECHO adaptation: DSP-only double path,
 * typed frame count, deterministic reset and no denormal-noise injection. */
#pragma once
#include <cstdint>
namespace echo {
class AirwindowsVinyl final {
public:
    explicit AirwindowsVinyl(double sampleRate) noexcept : sampleRate_(sampleRate) {}
    void reset() noexcept { *this = AirwindowsVinyl(sampleRate_); }
    void process(double** inputs, double** outputs, int sampleFrames) noexcept;
private:
    double getSampleRate() const noexcept { return sampleRate_; }
    double sampleRate_;
	double ataLastOutL {};
	double ataLastOutR {};
	
	double s1L {};
	double s2L {};
	double s3L {};
	double o1L {};
	double o2L {};
	double o3L {};
	double m1L {};
	double m2L {};
	double s1R {};
	double s2R {};
	double s3R {};
	double o1R {};
	double o2R {};
	double o3R {};
	double m1R {};
	double m2R {};
	double desL {};
	double desR {};
	
	double midSampleA {};
	double midSampleB {};
	double midSampleC {};
	double midSampleD {};
	double midSampleE {};
	double midSampleF {};
	double midSampleG {};
	double midSampleH {};
	double midSampleI {};
	double midSampleJ {};
	double midSampleK {};
	double midSampleL {};
	double midSampleM {};
	double midSampleN {};
	double midSampleO {};
	double midSampleP {};
	double midSampleQ {};
	double midSampleR {};
	double midSampleS {};
	double midSampleT {};
	double midSampleU {};
	double midSampleV {};
	double midSampleW {};
	double midSampleX {};
	double midSampleY {};
	double midSampleZ {};
	
	double sideSampleA {};
	double sideSampleB {};
	double sideSampleC {};
	double sideSampleD {};
	double sideSampleE {};
	double sideSampleF {};
	double sideSampleG {};
	double sideSampleH {};
	double sideSampleI {};
	double sideSampleJ {};
	double sideSampleK {};
	double sideSampleL {};
	double sideSampleM {};
	double sideSampleN {};
	double sideSampleO {};
	double sideSampleP {};
	double sideSampleQ {};
	double sideSampleR {};
	double sideSampleS {};
	double sideSampleT {};
	double sideSampleU {};
	double sideSampleV {};
	double sideSampleW {};
	double sideSampleX {};
	double sideSampleY {};
	double sideSampleZ {};
	
	double aMid[11] {};
	double bMid[11] {};
	double fMid[11] {};		
	double aSide[11] {};
	double bSide[11] {};
	double fSide[11] {};		
	double aMidPrev {};
	double aSidePrev {};
	double bMidPrev {};
	double bSidePrev {};
	
	uint32_t fpdL = 0x12345678;
	uint32_t fpdR = 0x87654321;

    float A = 0.203419f, B = 0.60f, C = 0.32f, D = 0.064f;
};
}
