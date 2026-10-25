package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public final class SaturationModule implements StudioModule {
    private static final int MAX_OVERSAMPLE = 4;

    private final int rate;
    private final int channels;

    private volatile SaturationParams params = SaturationParams.off();

    private final StudioBiquad[][] interpolation;
    private final StudioBiquad[][] decimation;
    private final double[] dcX1;
    private final double[] dcY1;
    private double dcCoef;

    public SaturationModule(int rate, int channels) {
        this.rate = Math.max(1, rate);
        this.channels = Math.max(1, channels);
        interpolation = new StudioBiquad[this.channels][2 * (MAX_OVERSAMPLE - 1)];
        decimation = new StudioBiquad[this.channels][2 * (MAX_OVERSAMPLE - 1)];
        for (int ch = 0; ch < this.channels; ch++) {
            for (int k = 0; k < interpolation[ch].length; k++) {
                interpolation[ch][k] = StudioBiquad.lowPass(this.rate, this.rate * 0.45, 0.5412);
                decimation[ch][k] = StudioBiquad.lowPass(this.rate, this.rate * 0.45, 1.3066);
            }
        }
        dcX1 = new double[this.channels];
        dcY1 = new double[this.channels];
        dcCoef = 1.0 - 2.0 * Math.PI * 10.0 / rate;
    }

    public void setParams(SaturationParams value) {
        SaturationParams next = value == null ? SaturationParams.off() : value;
        if (next.oversample != params.oversample) {
            designFilters(next.oversample);
        }
        this.params = next;
    }

    private void designFilters(int os) {
        int internalRate = rate * Math.max(1, os);
        double cutoff = rate * 0.45;
        for (int ch = 0; ch < channels; ch++) {
            for (int k = 0; k < interpolation[ch].length; k++) {
                interpolation[ch][k].updateFrom(StudioBiquad.lowPass(internalRate, cutoff, 0.5412));
                decimation[ch][k].updateFrom(StudioBiquad.lowPass(internalRate, cutoff, 1.3066));
            }
        }
    }

    public SaturationParams params() {
        return params;
    }

    @Override
    public boolean active() {
        return params.active();
    }

    @Override
    public int latencyFrames() {
        return 0;
    }

    @Override
    public void reset() {
        for (StudioBiquad[] row : interpolation) {
            for (StudioBiquad b : row) {
                b.reset();
            }
        }
        for (StudioBiquad[] row : decimation) {
            for (StudioBiquad b : row) {
                b.reset();
            }
        }
        java.util.Arrays.fill(dcX1, 0.0);
        java.util.Arrays.fill(dcY1, 0.0);
    }

    @Override
    public void process(AudioBuf buf) {
        SaturationParams p = params;
        if (!p.active()) {
            return;
        }
        int frames = buf.frames();
        if (frames <= 0) {
            return;
        }
        int chs = Math.min(buf.channels(), channels);
        float[] backing = buf.backingArray();
        int os = p.oversample;
        double drive = p.driveLinear();
        double makeup = p.makeupLinear();
        float mix = (float) p.mix;

        for (int i = 0; i < frames; i++) {
            for (int ch = 0; ch < chs; ch++) {
                int at = buf.channelBase(ch) + i;
                double dry = backing[at];
                double v;
                if (os == 1) {
                    v = shape(p.curve, dry * drive) * makeup;
                } else {
                    int stages = 2 * (os - 1);
                    double decimated = 0.0;
                    for (int k = 0; k < os; k++) {
                        double up = k == 0 ? dry * os : 0.0;
                        double filtered = up;
                        for (int s = 0; s < stages; s++) {
                            filtered = interpolation[ch][s].process(filtered);
                        }
                        filtered = shape(p.curve, filtered * drive) * makeup;
                        for (int s = 0; s < stages; s++) {
                            filtered = decimation[ch][s].process(filtered);
                        }
                        decimated = filtered;
                    }
                    v = decimated;
                }
                double y = v - dcX1[ch] + dcCoef * dcY1[ch];
                dcX1[ch] = v;
                dcY1[ch] = y;
                backing[at] = (float) (dry + (y - dry) * mix);
            }
        }
    }

    private static double shape(SaturationParams.Curve curve, double x) {
        switch (curve) {
            case TANH:
                return Math.tanh(x);
            case SOFT_CLIP:
                if (x >= 1.0) {
                    return 2.0 / 3.0;
                }
                if (x <= -1.0) {
                    return -2.0 / 3.0;
                }
                return x - x * x * x / 3.0;
            default:
                if (x > 0.7) {
                    return 0.7 + (x - 0.7) * 0.35;
                }
                if (x < -0.5) {
                    return -0.5 + (x + 0.5) * 0.5;
                }
                return x;
        }
    }

    @Override
    public String toString() {
        return "SaturationModule{" + params + "}";
    }
}
