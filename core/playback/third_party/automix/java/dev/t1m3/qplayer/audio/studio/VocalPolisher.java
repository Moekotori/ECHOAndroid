package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.ChannelLayout;

public final class VocalPolisher {
    private static final int BLOCK = 1024;

    private final int rate;
    private final AutotuneParams autotune;
    private final DereverbParams dereverb;

    public VocalPolisher(int rate, AutotuneParams autotune, DereverbParams dereverb) {
        this.rate = Math.max(rate, 1);
        this.autotune = autotune == null ? AutotuneParams.off() : autotune;
        this.dereverb = dereverb == null ? DereverbParams.off() : dereverb;
    }

    public boolean active() {
        return autotune.active() || dereverb.active();
    }

    public int polish(float[] left, float[] right) {
        if (!active() || left == null || right == null || left.length == 0) {
            return 0;
        }
        int frames = Math.min(left.length, right.length);
        if (autotune.active()) {
            AutotuneModule module = new AutotuneModule(rate, 2);
            module.setParams(autotune);
            frames = runModule(module, frames, left, right);
        }
        if (dereverb.active()) {
            DereverbModule module = new DereverbModule(rate, 2);
            module.setParams(dereverb);
            frames = runModule(module, frames, left, right);
        }
        return frames;
    }

    private int runModule(StudioModule module, int frames, float[] left, float[] right) {
        int latency = Math.max(0, module.latencyFrames());
        int total = frames + latency;
        float[] outL = new float[total];
        float[] outR = new float[total];
        AudioBuf buf = new AudioBuf(ChannelLayout.STEREO, BLOCK);
        int at = 0;
        while (at < total) {
            int n = Math.min(BLOCK, total - at);
            buf.setFrames(n);
            java.nio.FloatBuffer l = buf.channelWriter(0);
            java.nio.FloatBuffer r = buf.channelWriter(1);
            for (int i = 0; i < n; i++) {
                int src = at + i;
                l.put(i, src < frames ? left[src] : 0f);
                r.put(i, src < frames ? right[src] : 0f);
            }
            module.process(buf);
            float[] backing = buf.backingArray();
            int baseL = buf.channelBase(0);
            int baseR = buf.channelBase(1);
            for (int i = 0; i < n; i++) {
                outL[at + i] = backing[baseL + i];
                outR[at + i] = backing[baseR + i];
            }
            at += n;
        }
        for (int i = 0; i < frames; i++) {
            left[i] = outL[i + latency];
            right[i] = outR[i + latency];
        }
        return frames;
    }

    @Override
    public String toString() {
        return "VocalPolisher{" + autotune + "，" + dereverb + "}";
    }
}
