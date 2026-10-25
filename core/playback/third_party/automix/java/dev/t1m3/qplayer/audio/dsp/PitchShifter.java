package dev.t1m3.qplayer.audio.dsp;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.ChannelLayout;

import java.nio.FloatBuffer;

public final class PitchShifter {

    public static final float MAX_RATIO = 4.0f;

    private final int channels;

    private final int grain;

    private final int hop;

    private final Fft fft;

    private final float[] window;

    private final float olaScale;

    private final int bins;

    private float ratio;

    private final int ringLen;

    private final float[] ring;

    private long pushed;

    private long nextFrame;

    private long outPos;

    private final float[][] prevPhase;

    private final double[][] sumPhase;

    private final float[] ola;

    private final float[] re;
    private final float[] im;

    private final float[] mag;

    private final float[] omega;

    private final float[] tgtMag;

    private final int[] regionOfPeak;

    private final boolean[] isPeak;

    private final float[] scratch;

    private boolean draining;

    public static int grainForRate(int rate) {
        int want = Math.max(rate, 1) / 23;

        int upper = 512;
        while (upper < want && upper < 8192) {
            upper <<= 1;
        }
        int lower = Math.max(upper >> 1, 512);

        int pick = (upper > 512 && Math.max(want - lower, 0) <= Math.max(upper - want, 0))
                ? lower
                : upper;
        return Math.max(512, Math.min(8192, pick));
    }

    public PitchShifter(int channels, int rate, float semitones, int chunkFrames) {
        this.channels = Math.max(channels, 1);
        this.grain = grainForRate(rate);
        this.hop = Math.max(grain / 4, 1);
        this.fft = new Fft(grain);
        this.bins = grain / 2 + 1;

        this.window = new float[grain];
        Fft.hann(window);

        double sq = 0.0;
        for (int i = 0; i < hop && i < grain; i++) {
            double sum = 0.0;
            for (int k = 0; k * hop < grain; k++) {
                int idx = i + k * hop;
                if (idx < grain) {
                    sum += (double) window[idx] * window[idx];
                }
            }
            sq += sum;
        }
        this.olaScale = (float) (hop / Math.max(sq, 1e-9));

        this.ringLen = grain + hop + Math.max(chunkFrames, 1024) + 8;

        this.ratio = 1.0f;
        this.ring = new float[this.channels * ringLen];
        this.pushed = 0L;
        this.nextFrame = 0L;
        this.outPos = 0L;
        this.prevPhase = new float[this.channels][bins];
        this.sumPhase = new double[this.channels][bins];
        this.ola = new float[this.channels * grain];
        this.re = new float[grain];
        this.im = new float[grain];
        this.mag = new float[bins];
        this.omega = new float[bins];
        this.tgtMag = new float[bins];
        this.regionOfPeak = new int[bins];
        this.isPeak = new boolean[bins];
        this.scratch = new float[this.channels];

        setSemitones(semitones);
    }

    public static PitchShifter forLayout(ChannelLayout layout, int rate, float semitones, int chunk) {
        return new PitchShifter(layout.count(), rate, semitones, chunk);
    }

    public static float semitonesToRatio(float semitones) {
        return (float) Math.pow(2.0f, semitones / 12.0f);
    }

    public static float ratioToSemitones(float ratio) {
        return 12.0f * (float) (Math.log((double) Math.max(ratio, 1e-6f)) / Math.log(2.0));
    }

    public void setSemitones(float semitones) {
        float r = (float) Math.pow(2.0f, semitones / 12.0f);

        this.ratio = Math.min(Math.max(r, 1.0f / MAX_RATIO), MAX_RATIO);
    }

    public float ratio() {
        return ratio;
    }

    public boolean isUnity() {
        return Math.abs(ratio - 1.0f) < 1e-6f;
    }

    public int latencyFrames() {
        return Math.max(grain - hop, 0);
    }

    int grain() {
        return grain;
    }

    int hop() {
        return hop;
    }

    float windowAt(int i) {
        return window[i];
    }

    float olaNormalization() {
        return olaScale;
    }

    long pushed() {
        return pushed;
    }

    long base() {
        return outPos;
    }

    private float readInput(int ch, long idx) {
        if (idx < 0L || idx >= pushed) {
            return 0.0f;
        }
        int slot = (int) (idx % (long) ringLen);
        return ring[ch * ringLen + slot];
    }

    public int freeFrames() {

        long analysed = nextFrame * (long) hop;
        long used = pushed - analysed;
        if (used < 0L) {
            used = 0L;
        }
        return (int) Math.max(0L, (long) ringLen - used);
    }

    private static double wrapPi(double x) {
        double twoPi = 2.0 * Math.PI;
        double y = x - twoPi * Math.floor(x / twoPi + 0.5);
        return y;
    }

    private void findRegions() {
        int half = grain / 2;
        for (int k = 0; k < bins; k++) {
            isPeak[k] = false;
            regionOfPeak[k] = 0;
        }
        for (int k = 1; k < half; k++) {
            if (mag[k] > mag[k - 1] && mag[k] >= mag[k + 1]) {
                isPeak[k] = true;
            }
        }
        int last = 0;
        for (int k = 0; k < bins; k++) {
            if (isPeak[k]) {
                last = k;
            }
            regionOfPeak[k] = last;
        }

        last = bins - 1;
        for (int k = bins - 1; k >= 0; k--) {
            if (isPeak[k]) {
                last = k;
            } else if (mag[k] > mag[regionOfPeak[k] <= k ? regionOfPeak[k] : k]) {
                regionOfPeak[k] = last;
            }
        }
    }

    private boolean processFrame() {
        long start = nextFrame * (long) hop;
        if (!draining && pushed < start + (long) grain) {
            return false;
        }

        double r = (double) ratio;
        boolean unity = isUnity();
        double twoPi = 2.0 * Math.PI;
        int half = grain / 2;

        for (int ch = 0; ch < channels; ch++) {

            for (int i = 0; i < grain; i++) {
                re[i] = readInput(ch, start + i) * window[i];
                im[i] = 0.0f;
            }
            fft.forward(re, im);

            float[] prev = prevPhase[ch];
            double[] sum = sumPhase[ch];

            for (int k = 0; k <= half; k++) {
                float gr = re[k];
                float gi = im[k];
                mag[k] = (float) Math.sqrt((double) gr * gr + (double) gi * gi);
                double phase = Math.atan2((double) gi, (double) gr);

                double expected = twoPi * (double) k * (double) hop / (double) grain;
                double delta = wrapPi(phase - prev[k] - expected);
                omega[k] = (float) ((twoPi * (double) k / (double) grain) + delta / (double) hop);
                prev[k] = (float) phase;
            }

            if (unity) {

                for (int k = 0; k <= half; k++) {
                    sum[k] = prev[k];
                }
            } else {

                findRegions();

                for (int k = 0; k <= half; k++) {
                    int pk = regionOfPeak[k];
                    sum[k] += (double) omega[pk] * r * (double) hop;
                }

                double srcEnergy = 0.0;
                double tgtEnergy = 0.0;
                int srcLimit = (int) Math.floor((double) half * r);
                if (srcLimit > half) {
                    srcLimit = half;
                }
                for (int k = 0; k <= srcLimit; k++) {
                    srcEnergy += (double) mag[k] * (double) mag[k];
                }
                for (int j = 0; j <= half; j++) {
                    double kd = (double) j / r;
                    int k0 = (int) Math.floor(kd);
                    float frac = (float) (kd - (double) k0);
                    float m;
                    if (k0 >= 0 && k0 + 1 <= half) {
                        m = mag[k0] + (mag[k0 + 1] - mag[k0]) * frac;
                    } else if (k0 >= 0 && k0 <= half) {
                        m = mag[k0];
                    } else {
                        m = 0.0f;
                    }
                    tgtMag[j] = m;
                    tgtEnergy += (double) m * (double) m;
                }

                float gain = 1.0f;
                if (tgtEnergy > 1e-12 && srcEnergy > 1e-12) {
                    gain = (float) Math.sqrt(srcEnergy / tgtEnergy);

                    if (!(gain > 0.5f)) {
                        gain = 0.5f;
                    } else if (gain > 2.0f) {
                        gain = 2.0f;
                    }
                }

                for (int j = 0; j <= half; j++) {
                    double kd = (double) j / r;
                    int ks = (int) Math.round(kd);
                    if (ks < 0) {
                        ks = 0;
                    } else if (ks > half) {
                        ks = half;
                    }
                    int pk = regionOfPeak[ks];
                    double p = sum[pk] + Math.PI * ((double) j - (double) pk * r);
                    float m = tgtMag[j] * gain;
                    re[j] = (float) (m * Math.cos(p));
                    im[j] = (float) (m * Math.sin(p));

                    if (j > 0 && j < half) {
                        re[grain - j] = re[j];
                        im[grain - j] = -im[j];
                    }
                }
                im[0] = 0.0f;
                im[half] = 0.0f;
            }

            fft.inverse(re, im);
            int base = ch * grain;
            for (int i = 0; i < grain; i++) {
                int slot = (int) ((start + i) % (long) grain);
                ola[base + slot] += re[i] * window[i] * olaScale;
            }
        }

        nextFrame += 1L;
        return true;
    }

    private void emit(long pos) {
        int slot = (int) (pos % (long) grain);
        for (int ch = 0; ch < channels; ch++) {
            int idx = ch * grain + slot;
            scratch[ch] = ola[idx];

            ola[idx] = 0.0f;
        }
    }

    public int pushFrom(AudioBuf src, int offset) {
        int avail = Math.max(0, src.frames() - offset);
        if (avail == 0) {
            return 0;
        }
        int take = Math.min(avail, freeFrames());
        if (take == 0) {
            return 0;
        }

        draining = false;

        int srcCh = Math.min(src.channels(), channels);
        int start = (int) (pushed % (long) ringLen);

        float[] in = src.backingArray();

        for (int ch = 0; ch < srcCh; ch++) {
            int srcBase = src.channelBase(ch);
            int dstBase = ch * ringLen;

            int first = Math.min(take, ringLen - start);
            for (int i = 0; i < first; i++) {
                ring[dstBase + start + i] = in[srcBase + offset + i];
            }
            if (first < take) {
                for (int i = first; i < take; i++) {
                    ring[dstBase + (i - first)] = in[srcBase + offset + i];
                }
            }
        }

        for (int ch = srcCh; ch < channels; ch++) {
            int srcBase0 = src.channelBase(0);
            int dstBase = ch * ringLen;
            int first = Math.min(take, ringLen - start);
            for (int i = 0; i < first; i++) {
                ring[dstBase + start + i] = in[srcBase0 + offset + i];
            }
            if (first < take) {
                for (int i = first; i < take; i++) {
                    ring[dstBase + (i - first)] = in[srcBase0 + offset + i];
                }
            }
        }

        pushed += (long) take;
        return take;
    }

    private boolean canEmit() {
        return nextFrame > outPos / (long) hop;
    }

    public int pull(AudioBuf out) {
        return pull(out, 0);
    }

    public int pull(AudioBuf out, int offset) {
        int cap = out.capacityFrames();
        int written = 0;
        int outCh = Math.min(channels, out.channels());

        float[] dst = out.backingArray();

        while (offset + written < cap) {

            if (draining && outPos >= pushed) {
                break;
            }
            while (!canEmit()) {
                if (!processFrame()) {
                    break;
                }
            }
            if (!canEmit()) {
                break;
            }
            emit(outPos);
            int at = offset + written;
            for (int ch = 0; ch < outCh; ch++) {
                dst[out.channelBase(ch) + at] = scratch[ch];
            }
            outPos += 1L;
            written += 1;
        }

        out.setFrames(offset + written);
        return written;
    }

    public int drain(AudioBuf out, int offset) {
        draining = true;
        return pull(out, offset);
    }

    public void reset() {
        java.util.Arrays.fill(ring, 0.0f);
        java.util.Arrays.fill(ola, 0.0f);
        for (int ch = 0; ch < channels; ch++) {
            java.util.Arrays.fill(prevPhase[ch], 0.0f);
            java.util.Arrays.fill(sumPhase[ch], 0.0);
        }
        pushed = 0L;
        nextFrame = 0L;
        outPos = 0L;
        draining = false;
    }

    @Override
    public String toString() {
        return "PitchShifter{" + channels + "ch, fft=" + grain + ", hop=" + hop
                + ", ratio=" + ratio + ", latency=" + latencyFrames()
                + (isUnity() ? ", unity (caller should bypass the whole span)" : "") + '}';
    }
}
