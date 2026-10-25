package dev.t1m3.qplayer.audio.dsp;

import java.nio.FloatBuffer;

public final class OnePole {

    private static final float PI = (float) Math.PI;

    private float a;

    private float state;

    private OnePole(float a) {
        this.a = a;
        this.state = 0f;
    }

    public static OnePole lowPass(int rate, float freq) {
        float rateF = (float) Math.max(rate, 2);
        float hi = Math.max(rateF * 0.499f, 1f);
        float f = clamp(freq, Math.min(hi, 1f), hi);
        float a = 1f - (float) Math.exp((-2f * PI * f) / rateF);
        return new OnePole(clamp(a, 0f, 1f));
    }

    public static OnePole unity() {
        return new OnePole(1f);
    }

    public OnePole copy() {
        OnePole p = new OnePole(a);
        p.state = state;
        return p;
    }

    public void reset() {
        this.state = 0f;
    }

    public float process(float x) {
        state += a * (x - state);
        return state;
    }

    public void processBuf(FloatBuffer buf) {
        int n = buf.limit();
        for (int i = 0; i < n; i++) {
            buf.put(i, process(buf.get(i)));
        }
    }

    private static float clamp(float v, float min, float max) {
        if (min > max) {
            throw new IllegalArgumentException("clamp bounds are inverted: min=" + min + " > max=" + max);
        }
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public String toString() {
        return "OnePole{a=" + a + ", state=" + state + '}';
    }
}
