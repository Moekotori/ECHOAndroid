package dev.t1m3.qplayer.audio.dsp;

public final class AppleBeatRepeat {

    private float[][] buf;
    private int loopFrames = 1;
    private int pos;
    private boolean active;
    private boolean captured;
    private float mix = 1f;

    public AppleBeatRepeat(int channels, int maxLoopFrames) {
        int n = Math.max(1, maxLoopFrames);
        buf = new float[Math.max(1, channels)][n];
    }

    public void reset() {
        for (float[] ch : buf) {
            java.util.Arrays.fill(ch, 0f);
        }
        pos = 0;
        captured = false;
    }

    public void setMix(float m) {
        mix = m < 0f ? 0f : (m > 1f ? 1f : m);
    }

    public int loopFrames() {
        return loopFrames;
    }

    public void setLoopFrames(int frames) {
        int f = Math.max(1, Math.min(buf[0].length, frames));
        if (f != loopFrames) {
            loopFrames = f;
            pos = 0;
            captured = false;
        }
    }

    public void setActive(boolean on) {
        if (on && !active) {
            pos = 0;
            captured = false;
        }
        active = on;
    }

    public boolean active() {
        return active;
    }

    public float process(int channel, float in) {
        int c = Math.max(0, Math.min(buf.length - 1, channel));
        float[] b = buf[c];
        if (!active) {
            return in;
        }
        float out;
        if (!captured) {
            b[pos] = in;
            out = in;
        } else {
            out = b[pos];
        }
        if (++pos >= loopFrames) {
            pos = 0;
            captured = true;
        }
        return in + mix * (out - in);
    }
}
