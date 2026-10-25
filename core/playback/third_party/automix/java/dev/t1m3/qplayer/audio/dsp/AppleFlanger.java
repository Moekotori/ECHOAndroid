package dev.t1m3.qplayer.audio.dsp;

public final class AppleFlanger {

    public static final float MAX_DELAY_SECS = 0.01f;

    private final float[][] buf;
    private final int mask;
    private final int size;
    private final int rate;
    private int writePos;
    private float phase;
    private float rateHz = 2f;
    private float depth = 0.32f;
    private float center = 0.51f;
    private float feedback = 0.52f;
    private float mix;

    public AppleFlanger(int sampleRate, int channels) {
        this.rate = Math.max(1, sampleRate);
        int n = 1;
        while (n < (int) (MAX_DELAY_SECS * this.rate) + 4) {
            n <<= 1;
        }
        this.size = n;
        this.mask = n - 1;
        this.buf = new float[Math.max(1, channels)][n];
    }

    public void reset() {
        for (float[] ch : buf) {
            java.util.Arrays.fill(ch, 0f);
        }
        writePos = 0;
        phase = 0f;
    }

    public void setMix(float m) {
        mix = m < 0f ? 0f : (m > 1f ? 1f : m);
    }

    public void setRateHz(float hz) {
        rateHz = hz > 0f ? hz : 0f;
    }

    public void setDepth(float d) {
        depth = d < 0f ? 0f : (d > 1f ? 1f : d);
    }

    public void setCenter(float c) {
        center = c < 0f ? 0f : (c > 1f ? 1f : c);
    }

    public void setFeedback(float f) {
        feedback = f < -0.95f ? -0.95f : (f > 0.95f ? 0.95f : f);
    }

    public float process(int channel, float in) {
        if (mix <= 0f) {
            return in;
        }
        int c = Math.max(0, Math.min(buf.length - 1, channel));
        float[] b = buf[c];
        int maxFrames = size - 2;
        float lfo = (float) Math.sin(2.0 * Math.PI * (double) phase);
        float delayFrames = (center + depth * lfo) * (MAX_DELAY_SECS * rate);
        if (delayFrames < 1f) {
            delayFrames = 1f;
        }
        if (delayFrames > maxFrames) {
            delayFrames = maxFrames;
        }
        int i0 = writePos - (int) delayFrames;
        float frac = delayFrames - (int) delayFrames;
        float a = b[i0 & mask];
        float d = b[(i0 - 1) & mask];
        float delayed = a + frac * (d - a);
        b[writePos & mask] = in + feedback * delayed;
        writePos++;
        phase += rateHz / rate;
        if (phase >= 1f) {
            phase -= 1f;
        }
        return in + mix * (delayed - in);
    }
}
