package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.dsp.SplitLowHigh;

public final class LowDucker {

    static final float ENVELOPE_TAU_SECS = 0.5f;

    static final float GAIN_TAU_SECS = 0.2f;

    static final float MIN_GAIN = 0.5f;

    private static final float SILENT_POWER = 1e-10f;

    private final SplitLowHigh[] fromSplit;
    private final SplitLowHigh[] toSplit;
    private final float[] scratch = new float[2];

    private final float envelopeAlpha;
    private final float gainAlpha;

    private float envA;
    private float envB;
    private float gain = 1.0f;
    private float lastGain = 1.0f;

    public LowDucker(int rate, int channels, float crossoverHz) {
        int ch = Math.max(channels, 1);
        this.fromSplit = new SplitLowHigh[ch];
        this.toSplit = new SplitLowHigh[ch];
        for (int c = 0; c < ch; c++) {
            this.fromSplit[c] = new SplitLowHigh(Math.max(rate, 1), crossoverHz);
            this.toSplit[c] = new SplitLowHigh(Math.max(rate, 1), crossoverHz);
        }
        this.envelopeAlpha = alphaFor(ENVELOPE_TAU_SECS, rate);
        this.gainAlpha = alphaFor(GAIN_TAU_SECS, rate);
    }

    private static float alphaFor(float tauSecs, int rate) {
        if (rate <= 0) {
            return 1.0f;
        }
        double a = 1.0 - Math.exp(-1.0 / ((double) tauSecs * rate));
        return (float) Math.max(0.0, Math.min(1.0, a));
    }

    public void reset() {
        for (int c = 0; c < fromSplit.length; c++) {
            fromSplit[c].reset();
        }
        for (int c = 0; c < toSplit.length; c++) {
            toSplit[c].reset();
        }
        envA = 0.0f;
        envB = 0.0f;
        gain = 1.0f;
        lastGain = 1.0f;
    }

    public float lowOf(boolean from, int channel, float x) {
        SplitLowHigh[] splits = from ? fromSplit : toSplit;
        if (channel < 0 || channel >= splits.length) {
            return 0.0f;
        }
        return splits[channel].processInto(x, scratch)[0];
    }

    public void track(float lowA, float lowB) {
        envA += (lowA * lowA - envA) * envelopeAlpha;
        envB += (lowB * lowB - envB) * envelopeAlpha;
    }

    public float gainForFrame(float strength) {
        float target = 1.0f;
        float sum = envA + envB;
        if (sum > SILENT_POWER) {
            float louder = Math.max(envA, envB);
            target = louder / sum;
            if (target < MIN_GAIN) {
                target = MIN_GAIN;
            } else if (target > 1.0f) {
                target = 1.0f;
            }
        }
        gain += (target - gain) * gainAlpha;

        float s = strength;
        if (s <= 0.0f) {
            lastGain = 1.0f;
            return lastGain;
        }
        if (s > 1.0f) {
            s = 1.0f;
        }
        lastGain = 1.0f + (gain - 1.0f) * s;
        return lastGain;
    }

    public float[] buffer(int frames) {
        if (gainBuffer == null || gainBuffer.length < frames) {
            gainBuffer = new float[Math.max(frames, 1024)];
        }
        return gainBuffer;
    }

    private float[] gainBuffer;

    public float lastGain() {
        return lastGain;
    }
}
