package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;

import java.nio.FloatBuffer;

public final class TransitionLimiter {

    static final double CEILING_DB = -1.0;

    static final double RELEASE_MS = 80.0;

    private final float ceiling;
    private final float releasePerFrame;
    private float gain = 1.0f;
    private boolean acted;

    public TransitionLimiter(int rate, int channels) {
        this.ceiling = (float) Math.pow(10.0, CEILING_DB / 20.0);
        this.releasePerFrame = (float) (1.0 - Math.exp(-1.0 / Math.max(1.0, rate * RELEASE_MS / 1000.0)));
    }

    public void reset() {
        gain = 1.0f;
        acted = false;
    }

    public boolean acted() {
        return acted;
    }

    float ceilingLinear() {
        return ceiling;
    }

    public void process(AudioBuf buf, int frames) {
        int n = Math.min(frames, buf.frames());
        if (n <= 0) {
            return;
        }
        int chs = Math.min(buf.channels(), 2);
        FloatBuffer left = buf.channelWriter(0);
        FloatBuffer right = chs > 1 ? buf.channelWriter(1) : null;
        float peak = 0f;
        for (int i = 0; i < n; i++) {
            peak = Math.max(peak, Math.abs(left.get(i)));
            if (right != null) {
                peak = Math.max(peak, Math.abs(right.get(i)));
            }
        }
        if (peak <= ceiling && gain >= 1.0f) {
            return;
        }
        acted = true;
        float target = peak > ceiling ? ceiling / peak : 1.0f;

        if (target < gain) {
            gain = target;
        }

        boolean holding = peak > ceiling;
        for (int i = 0; i < n; i++) {
            if (!holding && gain < 1.0f) {
                gain += (1.0f - gain) * releasePerFrame;
            }
            left.put(i, soft(left.get(i) * gain));
            if (right != null) {
                right.put(i, soft(right.get(i) * gain));
            }
        }
    }

    private float soft(float x) {
        float a = Math.abs(x);
        if (a <= ceiling) {
            return x;
        }
        float over = (a - ceiling) / Math.max(ceiling, 1e-6f);
        float shaped = ceiling + ceiling * (float) Math.tanh(over);
        return x < 0f ? -shaped : shaped;
    }
}
