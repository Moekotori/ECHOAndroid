package dev.t1m3.qplayer.audio.dsp;

import java.nio.FloatBuffer;

public final class Biquad {

    private static final float PI = (float) Math.PI;

    private float b0;
    private float b1;
    private float b2;
    private float a1;
    private float a2;

    private float z1;
    private float z2;

    private Biquad(float b0, float b1, float b2, float a1, float a2) {
        this.b0 = b0;
        this.b1 = b1;
        this.b2 = b2;
        this.a1 = a1;
        this.a2 = a2;
        this.z1 = 0f;
        this.z2 = 0f;
    }

    public static Biquad unity() {
        return new Biquad(1f, 0f, 0f, 0f, 0f);
    }

    public static Biquad fromCoeffs(float b0, float b1, float b2, float a1, float a2) {
        return new Biquad(b0, b1, b2, a1, a2);
    }

    public Biquad copy() {
        Biquad c = new Biquad(b0, b1, b2, a1, a2);
        c.z1 = z1;
        c.z2 = z2;
        return c;
    }

    public void reset() {
        this.z1 = 0f;
        this.z2 = 0f;
    }

    public void setLowPass(int rate, float freq, float q) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];
        float a0 = 1f + alpha;
        float b = (1f - cosW0) / 2f;
        setCoeffs(b / a0, (1f - cosW0) / a0, b / a0, (-2f * cosW0) / a0, (1f - alpha) / a0);
    }

    public void setHighPass(int rate, float freq, float q) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];
        float a0 = 1f + alpha;
        float b = (1f + cosW0) / 2f;
        setCoeffs(b / a0, -(1f + cosW0) / a0, b / a0, (-2f * cosW0) / a0, (1f - alpha) / a0);
    }

    public void setCoeffs(float nb0, float nb1, float nb2, float na1, float na2) {
        this.b0 = nb0;
        this.b1 = nb1;
        this.b2 = nb2;
        this.a1 = na1;
        this.a2 = na2;
    }

    public void copyLowPassInto(int rate, float freq, float q, float[] dst) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];
        float a0 = 1f + alpha;
        float b = (1f - cosW0) / 2f;
        dst[0] = b / a0;
        dst[1] = (1f - cosW0) / a0;
        dst[2] = b / a0;
        dst[3] = (-2f * cosW0) / a0;
        dst[4] = (1f - alpha) / a0;
    }

    public static Biquad lowPass(int rate, float freq, float q) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];
        float a0 = 1f + alpha;
        float b = (1f - cosW0) / 2f;
        return fromCoeffs(
                b / a0,
                (1f - cosW0) / a0,
                b / a0,
                (-2f * cosW0) / a0,
                (1f - alpha) / a0);
    }

    public static Biquad highPass(int rate, float freq, float q) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];
        float a0 = 1f + alpha;
        float b = (1f + cosW0) / 2f;
        return fromCoeffs(
                b / a0,
                (-(1f + cosW0)) / a0,
                b / a0,
                (-2f * cosW0) / a0,
                (1f - alpha) / a0);
    }

    public static Biquad bandPass(int rate, float freq, float q) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];
        float a0 = 1f + alpha;
        return fromCoeffs(
                alpha / a0,
                0f,
                -alpha / a0,
                (-2f * cosW0) / a0,
                (1f - alpha) / a0);
    }

    public static Biquad lowShelf(int rate, float freq, float q, float gainDb) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];

        float a = (float) Math.pow(10.0, gainDb / 40f);
        float sqrtA = (float) Math.sqrt(a);
        float a0 = (a + 1f) + (a - 1f) * cosW0 + 2f * sqrtA * alpha;
        return fromCoeffs(
                (a * ((a + 1f) - (a - 1f) * cosW0 + 2f * sqrtA * alpha)) / a0,
                (2f * a * ((a - 1f) - (a + 1f) * cosW0)) / a0,
                (a * ((a + 1f) - (a - 1f) * cosW0 - 2f * sqrtA * alpha)) / a0,
                (-2f * ((a - 1f) + (a + 1f) * cosW0)) / a0,
                ((a + 1f) + (a - 1f) * cosW0 - 2f * sqrtA * alpha) / a0);
    }

    public static Biquad peaking(int rate, float freq, float q, float gainDb) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];
        float a = (float) Math.pow(10.0, gainDb / 40f);
        float a0 = 1f + alpha / a;
        return fromCoeffs(
                (1f + alpha * a) / a0,
                (-2f * cosW0) / a0,
                (1f - alpha * a) / a0,
                (-2f * cosW0) / a0,
                (1f - alpha / a) / a0);
    }

    public static Biquad highShelf(int rate, float freq, float q, float gainDb) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];
        float a = (float) Math.pow(10.0, gainDb / 40f);
        float sqrtA = (float) Math.sqrt(a);
        float a0 = (a + 1f) - (a - 1f) * cosW0 + 2f * sqrtA * alpha;
        return fromCoeffs(
                (a * ((a + 1f) + (a - 1f) * cosW0 + 2f * sqrtA * alpha)) / a0,
                (-2f * a * ((a - 1f) + (a + 1f) * cosW0)) / a0,
                (a * ((a + 1f) + (a - 1f) * cosW0 - 2f * sqrtA * alpha)) / a0,
                (2f * ((a - 1f) - (a + 1f) * cosW0)) / a0,
                ((a + 1f) - (a - 1f) * cosW0 - 2f * sqrtA * alpha) / a0);
    }

    public static Biquad notch(int rate, float freq, float q) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];
        float a0 = 1f + alpha;
        return fromCoeffs(
                1f / a0,
                (-2f * cosW0) / a0,
                1f / a0,
                (-2f * cosW0) / a0,
                (1f - alpha) / a0);
    }

    public static Biquad allPass(int rate, float freq, float q) {
        float[] pw = prewarp(rate, freq, q);
        float cosW0 = pw[0];
        float alpha = pw[1];
        float a0 = 1f + alpha;
        return fromCoeffs(
                (1f - alpha) / a0,
                (-2f * cosW0) / a0,
                (1f + alpha) / a0,
                (-2f * cosW0) / a0,
                (1f - alpha) / a0);
    }

    public float[] coeffs() {
        return new float[]{b0, b1, b2, a1, a2};
    }

    public void updatePeaking(int rate, float freq, float q, float gainDb) {
        copyCoeffs(peaking(rate, freq, q, gainDb));
    }

    public void updateHighShelf(int rate, float freq, float q, float gainDb) {
        copyCoeffs(highShelf(rate, freq, q, gainDb));
    }

    public void updateNotch(int rate, float freq, float q) {
        copyCoeffs(notch(rate, freq, q));
    }

    public void updateLowShelf(int rate, float freq, float q, float gainDb) {
        copyCoeffs(lowShelf(rate, freq, q, gainDb));
    }

    public void updateAllPass(int rate, float freq, float q) {
        copyCoeffs(allPass(rate, freq, q));
    }

    public void updateFrom(Biquad other) {
        copyCoeffs(other);
    }

    private void copyCoeffs(Biquad c) {
        this.b0 = c.b0;
        this.b1 = c.b1;
        this.b2 = c.b2;
        this.a1 = c.a1;
        this.a2 = c.a2;
    }

    public void updateLowPass(int rate, float freq, float q) {
        Biquad c = lowPass(rate, freq, q);
        this.b0 = c.b0;
        this.b1 = c.b1;
        this.b2 = c.b2;
        this.a1 = c.a1;
        this.a2 = c.a2;
    }

    public void updateHighPass(int rate, float freq, float q) {
        Biquad c = highPass(rate, freq, q);
        this.b0 = c.b0;
        this.b1 = c.b1;
        this.b2 = c.b2;
        this.a1 = c.a1;
        this.a2 = c.a2;
    }

    private static float[] prewarp(int rate, float freq, float q) {
        float rateF = (float) Math.max(rate, 2);
        float nyquist = rateF * 0.5f;
        float hi = Math.max(nyquist * 0.999f, 1f);
        float lo = Math.min(hi, 1f);
        float f = clamp(freq, lo, hi);
        float w0 = 2f * PI * f / rateF;

        float qq = Float.isNaN(q) ? 0.05f : Math.max(q, 0.05f);
        return new float[] {(float) Math.cos(w0), (float) Math.sin(w0) / (2f * qq)};
    }

    private static float clamp(float v, float min, float max) {
        if (min > max) {
            throw new IllegalArgumentException("clamp bounds are inverted: min=" + min + " > max=" + max);
        }
        return Math.max(min, Math.min(max, v));
    }

    public float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }

    public void processBuf(FloatBuffer buf) {
        int n = buf.limit();
        for (int i = 0; i < n; i++) {
            buf.put(i, process(buf.get(i)));
        }
    }

    @Override
    public String toString() {
        return "Biquad{b0=" + b0 + ", b1=" + b1 + ", b2=" + b2
                + ", a1=" + a1 + ", a2=" + a2 + ", z1=" + z1 + ", z2=" + z2 + '}';
    }
}
