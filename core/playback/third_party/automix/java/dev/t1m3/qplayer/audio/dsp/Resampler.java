package dev.t1m3.qplayer.audio.dsp;

import dev.t1m3.qplayer.audio.core.AudioBuf;

import java.nio.FloatBuffer;

public final class Resampler {

    static final int TAPS_PER_SIDE = 16;

    static final int TAPS = TAPS_PER_SIDE * 2;

    static final int OVERSAMPLE = 256;

    static final int HISTORY_FRAMES = 8192;

    private static final double TAU = 2.0 * Math.PI;

    private final int channels;

    private final double step;

    private double pos;

    private final float[] history;

    private int histLen;

    private final float[] kernel;

    private final int inRate;
    private final int outRate;

    public Resampler(int inRate, int outRate, int channels) {

        if (channels <= 0) {
            throw new IllegalArgumentException("channel count must be > 0");
        }
        if (inRate <= 0 || outRate <= 0) {
            throw new IllegalArgumentException("sample rate must be > 0");
        }

        this.channels = channels;
        this.step = (double) inRate / (double) outRate;
        double cutoff;
        if (outRate < inRate) {

            cutoff = ((double) outRate / (double) inRate) * 0.95;
        } else {

            cutoff = 1.0;
        }
        this.kernel = buildKernel(cutoff);

        this.pos = 0.0;
        this.history = new float[channels * HISTORY_FRAMES];
        this.histLen = 0;
        this.inRate = inRate;
        this.outRate = outRate;
    }

    public void reset() {
        this.histLen = 0;
        this.pos = 0.0;
    }

    public int inRate() {
        return inRate;
    }

    public int outRate() {
        return outRate;
    }

    public boolean isPassthrough() {
        return inRate == outRate;
    }

    public int neededInputFrames(int outFrames) {
        return (int) Math.ceil((double) outFrames * step) + TAPS;
    }

    public int push(AudioBuf input) {
        return pushFrom(input, 0);
    }

    public int pushFrom(AudioBuf input, int offset) {

        if (input.channels() != channels) {
            throw new IllegalArgumentException(
                    "channel count mismatch: resampler " + channels + ", input " + input.channels());
        }

        int available = Math.max(input.frames() - offset, 0);
        if (available == 0) {
            return 0;
        }
        reclaim();
        int room = HISTORY_FRAMES - histLen;
        int take = Math.min(available, room);
        if (take == 0) {
            return 0;
        }
        int start = histLen;
        for (int c = 0; c < channels; c++) {

            FloatBuffer src = input.channel(c);
            src.position(offset);
            src.get(history, c * HISTORY_FRAMES + start, take);
        }
        histLen += take;
        return take;
    }

    public int pushAll(AudioBuf input) {
        int offset = 0;
        while (offset < input.frames()) {
            int accepted = pushFrom(input, offset);
            if (accepted == 0) {

                break;
            }
            offset += accepted;
        }
        return offset;
    }

    public int pull(AudioBuf out) {
        return pullInternal(out, false);
    }

    public int drain(AudioBuf out) {
        return pullInternal(out, true);
    }

    private int pullInternal(AudioBuf out, boolean endOfStream) {

        if (out.channels() != channels) {
            throw new IllegalArgumentException(
                    "channel count mismatch: resampler " + channels + ", output " + out.channels());
        }
        int capacity = out.capacityFrames();
        int produced = 0;

        FloatBuffer[] writers = new FloatBuffer[channels];
        for (int c = 0; c < channels; c++) {
            writers[c] = out.channelWriter(c);
        }

        while (produced < capacity) {

            if (histLen == 0) {
                break;
            }

            double i0 = Math.floor(pos);
            if (endOfStream) {

                if (i0 >= (double) histLen) {
                    break;
                }
            } else {
                double highest = i0 + (double) TAPS_PER_SIDE;
                if (highest > (double) (histLen - 1)) {
                    break;
                }
            }

            double frac = pos - i0;
            int phase = (int) Math.min(Math.round(frac * (double) OVERSAMPLE), (long) (OVERSAMPLE - 1));

            int kernelRow = phase * TAPS;

            int base = (int) i0 - TAPS_PER_SIDE + 1;
            for (int c = 0; c < channels; c++) {
                int row = c * HISTORY_FRAMES;
                float acc = 0.0f;
                for (int k = 0; k < TAPS; k++) {
                    float coeff = kernel[kernelRow + k];
                    int idx = base + k;
                    if (idx >= 0 && idx < histLen) {
                        acc += history[row + idx] * coeff;
                    }
                }
                writers[c].put(produced, acc);
            }

            produced += 1;
            pos += step;
        }

        out.setFrames(produced);
        return produced;
    }

    private void reclaim() {
        int consumed = (int) Math.floor(pos);

        int keepFrom = Math.max(consumed - TAPS_PER_SIDE, 0);
        if (keepFrom == 0) {
            return;
        }
        int keep = Math.max(histLen - keepFrom, 0);
        if (keep == 0) {
            histLen = 0;
            pos -= (double) keepFrom;
            return;
        }
        for (int c = 0; c < channels; c++) {
            int row = c * HISTORY_FRAMES;

            System.arraycopy(history, row + keepFrom, history, row, histLen - keepFrom);
        }
        histLen = keep;
        pos -= (double) keepFrom;
    }

    @Override
    public String toString() {
        return "Resampler{" + inRate + "→" + outRate + ", " + channels + "ch"
                + ", passthrough=" + isPassthrough()
                + ", histLen=" + histLen + ", pos=" + pos + '}';
    }

    public static float[] buildKernel(double cutoff) {
        float[] kernel = new float[OVERSAMPLE * TAPS];

        for (int phase = 0; phase < OVERSAMPLE; phase++) {
            double frac = (double) phase / (double) OVERSAMPLE;
            double sum = 0.0;
            int row = phase * TAPS;

            for (int k = 0; k < TAPS; k++) {

                double d = ((double) k - (double) TAPS_PER_SIDE + 1.0) - frac;
                double v = cutoff * sinc(cutoff * d) * blackmanHarris(d);
                kernel[row + k] = (float) v;
                sum += v;
            }

            if (Math.abs(sum) > 1e-12) {
                float inv = (float) (1.0 / sum);
                for (int k = 0; k < TAPS; k++) {
                    kernel[row + k] *= inv;
                }
            }
        }

        return kernel;
    }

    public static double sinc(double x) {
        if (Math.abs(x) < 1e-12) {
            return 1.0;
        }
        double px = Math.PI * x;
        return Math.sin(px) / px;
    }

    public static double blackmanHarris(double d) {
        double r = Math.abs(d) / (double) TAPS_PER_SIDE;
        if (r >= 1.0) {
            return 0.0;
        }
        double a0 = 0.35875;
        double a1 = 0.48829;
        double a2 = 0.14128;
        double a3 = 0.01168;

        double u = (r + 1.0) * 0.5;
        double t = TAU * u;
        return a0 - a1 * Math.cos(t) + a2 * Math.cos(2.0 * t) - a3 * Math.cos(3.0 * t);
    }
}
