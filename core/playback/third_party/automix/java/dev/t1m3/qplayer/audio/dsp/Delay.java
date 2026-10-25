package dev.t1m3.qplayer.audio.dsp;

public final class Delay {

    private static final double MAX_SECS = 2.0;

    private final int channels;
    private final float[][] buf;
    private final int[] size;
    private final int[] write;
    private final float[] lp;
    private int delayFrames;
    private final float feedback;
    private final float damping;

    public Delay(int rate, int channels, float feedback, float damping) {
        this.channels = Math.max(1, channels);
        this.feedback = Math.max(0f, Math.min(0.9f, feedback));
        this.damping = Math.max(0f, Math.min(0.95f, damping));
        int cap = Math.max(64, (int) Math.round(Math.max(8000, rate) * MAX_SECS));
        this.buf = new float[this.channels][];
        this.size = new int[this.channels];
        this.write = new int[this.channels];
        this.lp = new float[this.channels];
        for (int c = 0; c < this.channels; c++) {
            this.buf[c] = new float[cap];
            this.size[c] = cap;
            this.write[c] = 0;
        }
        this.delayFrames = Math.max(1, cap / 4);
    }

    public void setDelayFrames(long frames) {
        this.delayFrames = (int) Math.max(1L, Math.min((long) size[0] - 1L, frames));
    }

    public int delayFrames() {
        return delayFrames;
    }

    public void reset() {
        for (int c = 0; c < channels; c++) {
            java.util.Arrays.fill(buf[c], 0f);
            write[c] = 0;
            lp[c] = 0f;
        }
    }

    public float process(int channel, float x) {
        int c = Math.min(Math.max(0, channel), channels - 1);
        float[] b = buf[c];
        int cap = size[c];
        int w = write[c];
        int r = w - delayFrames;
        if (r < 0) {
            r += cap;
        }
        float delayed = b[r];

        lp[c] = delayed * (1f - damping) + lp[c] * damping;
        b[w] = x + lp[c] * feedback;
        write[c] = w + 1 >= cap ? 0 : w + 1;
        return delayed;
    }
}
