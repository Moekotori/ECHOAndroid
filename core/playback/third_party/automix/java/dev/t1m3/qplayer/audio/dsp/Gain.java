package dev.t1m3.qplayer.audio.dsp;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public final class Gain {

    public static final float DEFAULT_RAMP_MS = 5.0f;

    private static final float F32_EPSILON = 0x1p-23f;

    private float current;
    private float target;

    private float step;

    private int remaining;

    private int rampFrames;

    public Gain(int sampleRate) {

        this.rampFrames = Math.max(1,
                Math.round((float) sampleRate * DEFAULT_RAMP_MS / 1000.0f));
        this.current = 1.0f;
        this.target = 1.0f;
        this.step = 0.0f;
        this.remaining = 0;
    }

    public Gain(Gain other) {
        this.current = other.current;
        this.target = other.target;
        this.step = other.step;
        this.remaining = other.remaining;
        this.rampFrames = other.rampFrames;
    }

    public static Gain withRampMs(int sampleRate, float rampMs) {
        Gain g = new Gain(sampleRate);

        g.rampFrames = Math.max(1,
                Math.round((float) sampleRate * Math.max(0.0f, rampMs) / 1000.0f));
        return g;
    }

    public float current() {
        return current;
    }

    public float target() {
        return target;
    }

    public boolean isRamping() {
        return remaining > 0;
    }

    public void setTarget(float value) {
        float v = Math.max(0.0f, value);
        this.target = v;
        if (Math.abs(v - this.current) < F32_EPSILON) {

            this.step = 0.0f;
            this.remaining = 0;
            return;
        }

        this.step = (v - this.current) / (float) this.rampFrames;
        this.remaining = this.rampFrames;
    }

    public void setTargetDb(float db) {
        setTarget(dbToLinear(db));
    }

    public void snapTo(float value) {
        this.current = Math.max(0.0f, value);
        this.target = this.current;
        this.step = 0.0f;
        this.remaining = 0;
    }

    public void process(AudioBuf buf) {
        int frames = buf.frames();
        int channels = buf.channels();
        if (frames == 0) {
            return;
        }

        if (!isRamping() && Math.abs(this.current - 1.0f) < F32_EPSILON) {
            return;
        }

        float[] data = buf.backingArray();
        int stride = buf.capacityFrames();

        for (int i = 0; i < frames; i++) {
            if (this.remaining > 0) {
                this.current += this.step;
                this.remaining -= 1;
                if (this.remaining == 0) {

                    this.current = this.target;
                }
            }
            float g = this.current;
            for (int c = 0; c < channels; c++) {
                int idx = c * stride + i;
                data[idx] *= g;
            }
        }
    }

    public static float dbToLinear(float db) {
        if (db <= -144.0f) {
            return 0.0f;
        }

        float e = db / 20.0f;
        return (float) Math.pow(10.0, e);
    }

    public static float linearToDb(float v) {
        if (v <= 1e-7f) {
            return -144.0f;
        }

        return 20.0f * (float) Math.log10(v);
    }

    @Override
    public String toString() {
        return "Gain{current=" + current + ", target=" + target
                + ", remaining=" + remaining + "/" + rampFrames + '}';
    }
}
