package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.dsp.Fft;

public final class DereverbModule implements StudioModule {
    public static final int WINDOW = 1024;

    public static final int HOP = 256;

    static final double PEAK_DECAY = 0.99;

    static final double TAIL_GATE = 0.75;

    static final double GAIN_ATTACK = 0.5;
    static final double GAIN_RELEASE = 0.40;

    private final int channels;
    private final Fft fft = new Fft(WINDOW);
    private final float[] hann = new float[WINDOW];
    private final double olaNorm;

    private final float[][] fifo;
    private final float[][] acc;
    private final float[] dryHead = new float[HOP];
    private final float[] inScratch = new float[HOP];
    private final float[] re = new float[WINDOW];
    private final float[] im = new float[WINDOW];
    private final float[] mag = new float[WINDOW / 2 + 1];
    private final float[] est = new float[WINDOW / 2 + 1];
    private final float[] gain = new float[WINDOW / 2 + 1];
    private final float[] peak = new float[WINDOW / 2 + 1];

    private volatile DereverbParams params = DereverbParams.off();

    private int sinceAnalysis;
    private int consumed;
    private boolean ready;
    private int flushing;

    private boolean wasActive;

    @SuppressWarnings("unused")
    private DereverbParams lastParams = DereverbParams.off();

    private DereverbParams wasParams = DereverbParams.off();

    public DereverbModule(int rate, int channels) {
        this.channels = Math.max(channels, 1);
        Fft.hann(hann);
        fifo = new float[this.channels][WINDOW];
        acc = new float[this.channels][WINDOW];
        java.util.Arrays.fill(gain, 1f);
        double sum = 0.0;
        for (int n = 0; n < WINDOW; n += HOP) {
            sum += (double) hann[n] * hann[n];
        }
        olaNorm = sum > 1e-9 ? 1.0 / sum : 1.0;
    }

    public void setParams(DereverbParams value) {
        this.params = value == null ? DereverbParams.off() : value;
    }

    public DereverbParams params() {
        return params;
    }

    @Override
    public boolean active() {
        return params.active();
    }

    @Override
    public int latencyFrames() {
        return params.active() ? WINDOW : 0;
    }

    @Override
    public void reset() {
        for (float[] f : fifo) {
            java.util.Arrays.fill(f, 0f);
        }
        for (float[] a : acc) {
            java.util.Arrays.fill(a, 0f);
        }
        java.util.Arrays.fill(est, 0f);
        java.util.Arrays.fill(gain, 1f);
        java.util.Arrays.fill(peak, 0f);
        sinceAnalysis = 0;
        consumed = 0;
        ready = false;
        flushing = 0;
        wasActive = false;
        lastParams = DereverbParams.off();
    }

    @Override
    public void process(AudioBuf buf) {
        DereverbParams p = params;
        boolean now = p.active();
        if (wasActive && !now && ready) {
            flushing = WINDOW;
            lastParams = wasParams;
        }
        wasActive = now;
        wasParams = p;
        DereverbParams use = now ? p : (flushing > 0 ? lastParams : null);
        if (use == null) {
            return;
        }
        int frames = buf.frames();
        if (frames <= 0) {
            return;
        }
        int chs = Math.min(buf.channels(), channels);
        float[] backing = buf.backingArray();
        double over = use.overSubtraction();
        float floorLin = (float) use.floorLinear();

        int done = 0;
        while (done < frames) {
            int room = HOP - sinceAnalysis;
            int n = Math.min(room, frames - done);
            if (n < room) {
                done += n;
                break;
            }
            for (int ch = 0; ch < chs; ch++) {
                int base = buf.channelBase(ch);
                for (int i = 0; i < n; i++) {
                    inScratch[i] = backing[base + done + i];
                }
                System.arraycopy(inScratch, 0, scratchPerChannel(ch), 0, n);
            }
            analyzeAndAccumulate(chs, over, floorLin, p.amount);
            for (int ch = 0; ch < chs; ch++) {
                int base = buf.channelBase(ch);
                float[] f = fifo[ch];
                float[] a = acc[ch];
                float mix = (float) (use.mix * (now ? 1.0 : flushing / (double) WINDOW));
                for (int i = 0; i < n; i++) {
                    float dry = f[i];
                    float wet = (float) (a[i] * olaNorm);
                    backing[base + done + i] = dry + (wet - dry) * mix;
                }
                System.arraycopy(a, n, a, 0, WINDOW - n);
                java.util.Arrays.fill(a, WINDOW - n, WINDOW, 0f);
            }
            slideAndAppend(chs, n);
            sinceAnalysis = 0;
            consumed += n;
            ready = true;
            done += n;
            if (!now) {
                flushing -= n;
                if (flushing <= 0) {
                    return;
                }
            }
        }
    }

    private final float[][] inScratchPerChannel = new float[8][HOP];

    private float[] scratchPerChannel(int ch) {
        return inScratchPerChannel[Math.min(ch, inScratchPerChannel.length - 1)];
    }

    private void slideAndAppend(int chs, int n) {
        for (int ch = 0; ch < chs; ch++) {
            float[] f = fifo[ch];
            System.arraycopy(f, n, f, 0, WINDOW - n);
            System.arraycopy(scratchPerChannel(ch), 0, f, WINDOW - n, n);
        }
    }

    private void flush(AudioBuf buf) {
        process(buf);
    }

    private void analyzeAndAccumulate(int chs, double over, float floorLin, double amountScale) {
        for (int ch = 0; ch < chs; ch++) {
            float[] f = fifo[ch];
            for (int i = 0; i < WINDOW; i++) {
                re[i] = f[i] * hann[i];
                im[i] = 0f;
            }
            fft.forward(re, im);
            for (int k = 0; k <= WINDOW / 2; k++) {
                mag[k] = (float) Math.sqrt((double) re[k] * re[k] + (double) im[k] * im[k]);
            }
            for (int k = 0; k <= WINDOW / 2; k++) {
                float held = (float) (peak[k] * PEAK_DECAY);
                peak[k] = Math.max(mag[k], held);
                float ratio = peak[k] > 1e-9f ? mag[k] / peak[k] : 1f;
                float w = (float) ((1.0 - ratio) / (1.0 - TAIL_GATE));
                if (w < 0f) {
                    w = 0f;
                } else if (w > 1f) {
                    w = 1f;
                }
                est[k] = peak[k] * w * (float) amountScale;
            }
            for (int k = 0; k <= WINDOW / 2; k++) {
                float m = mag[k];
                float target = 1f;
                if (m > 1e-9f) {
                    float sub = (float) (m - over * est[k]);
                    target = sub / m;
                    if (target < floorLin) {
                        target = floorLin;
                    } else if (target > 1f) {
                        target = 1f;
                    }
                }
                float prev = gain[k];
                double a = target < prev ? GAIN_ATTACK : GAIN_RELEASE;
                gain[k] = (float) (prev + (target - prev) * a);
            }
            for (int k = 0; k <= WINDOW / 2; k++) {
                float g = gain[k];
                re[k] *= g;
                im[k] *= g;
                if (k > 0 && k < WINDOW / 2) {
                    re[WINDOW - k] = re[k];
                    im[WINDOW - k] = -im[k];
                }
            }
            fft.inverse(re, im);
            float[] a = acc[ch];
            for (int i = 0; i < WINDOW; i++) {
                a[i] += re[i] * hann[i];
            }
        }
    }

    @Override
    public String toString() {
        return "DereverbModule{"
                + params
                + ", window "
                + WINDOW
                + "/hop "
                + HOP
                + ", delay "
                + WINDOW
                + "}";
    }
}
