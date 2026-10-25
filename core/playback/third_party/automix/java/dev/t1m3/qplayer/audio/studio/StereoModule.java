package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public final class StereoModule implements StudioModule {
    private static final double MAX_SIDE_DELAY_MS = 2.0;

    private static final double CROSSFEED_DELAY_MS = 0.3;

    private final int rate;
    private final int channels;

    private volatile StereoParams params = StereoParams.neutral();

    private final StudioBiquad sideHighA;
    private final StudioBiquad sideHighB;
    private final float[] sideDelay;
    private final StudioBiquad feedLow;
    private final StudioBiquad feedLow2;
    private final float[] feedDelayLeft;
    private final float[] feedDelayRight;
    private int sideDelayAt;
    private int feedDelayAt;

    public StereoModule(int rate, int channels) {
        this.rate = Math.max(1, rate);
        this.channels = Math.max(channels, 1);
        this.sideHighA = StudioBiquad.highPass(this.rate, 200.0, 0.7071);
        this.sideHighB = StudioBiquad.highPass(this.rate, 200.0, 0.7071);
        this.sideDelay =
                new float[Math.max(1, (int) Math.ceil(MAX_SIDE_DELAY_MS / 1000.0 * this.rate)) + 1];
        this.feedLow = StudioBiquad.lowPass(this.rate, 700.0, 0.7071);
        this.feedLow2 = StudioBiquad.lowPass(this.rate, 700.0, 0.7071);
        int feedFrames = Math.max(1, (int) Math.ceil(CROSSFEED_DELAY_MS / 1000.0 * this.rate)) + 1;
        this.feedDelayLeft = new float[feedFrames];
        this.feedDelayRight = new float[feedFrames];
    }

    public void setParams(StereoParams value) {
        StereoParams next = value == null ? StereoParams.neutral() : value;
        if (next.bassMonoHz > 0.0) {
            sideHighA.updateFrom(StudioBiquad.highPass(rate, next.bassMonoHz, 0.7071));
            sideHighB.updateFrom(StudioBiquad.highPass(rate, next.bassMonoHz, 0.7071));
        }
        this.params = next;
    }

    public StereoParams params() {
        return params;
    }

    @Override
    public boolean active() {
        return params.active();
    }

    @Override
    public int latencyFrames() {
        return params.active() ? params.sideDelayFrames(rate) : 0;
    }

    @Override
    public void reset() {
        sideHighA.reset();
        sideHighB.reset();
        feedLow.reset();
        feedLow2.reset();
        java.util.Arrays.fill(sideDelay, 0f);
        java.util.Arrays.fill(feedDelayLeft, 0f);
        java.util.Arrays.fill(feedDelayRight, 0f);
        sideDelayAt = 0;
        feedDelayAt = 0;
    }

    @Override
    public void process(AudioBuf buf) {
        StereoParams p = params;
        if (!p.active() || buf.channels() < 2) {
            return;
        }
        int frames = buf.frames();
        if (frames <= 0) {
            return;
        }
        float[] backing = buf.backingArray();
        int baseL = buf.channelBase(0);
        int baseR = buf.channelBase(1);
        int sideDelayFrames = p.sideDelayFrames(rate);
        int feedDelayFrames = (int) Math.round(CROSSFEED_DELAY_MS / 1000.0 * rate);

        for (int i = 0; i < frames; i++) {
            float l = backing[baseL + i];
            float r = backing[baseR + i];
            if (p.channelSwap) {
                float t = l;
                l = r;
                r = t;
            }
            boolean needMs =
                    Math.abs(p.width - 1.0) > 1e-9 || p.bassMonoHz > 0.0 || sideDelayFrames > 0;
            double m = needMs ? (l + r) * 0.5 : 0.0;
            double s = needMs ? (l - r) * 0.5 : 0.0;
            s *= p.width;
            if (p.bassMonoHz > 0.0) {
                s = sideHighB.process(sideHighA.process(s));
            }
            if (sideDelayFrames > 0) {
                int readAt =
                        ((sideDelayAt - sideDelayFrames) % sideDelay.length + sideDelay.length)
                                % sideDelay.length;
                double delayed = sideDelay[readAt];
                sideDelay[sideDelayAt] = (float) s;
                sideDelayAt = (sideDelayAt + 1) % sideDelay.length;
                s = delayed;
            }
            double outL = needMs ? m + s : l;
            double outR = needMs ? m - s : r;
            if (p.crossfeed > 1e-9) {
                double fromLeft = feedLow.process(outL);
                double fromRight = feedLow2.process(outR);
                int readAt =
                        ((feedDelayAt - feedDelayFrames) % feedDelayLeft.length
                                        + feedDelayLeft.length)
                                % feedDelayLeft.length;
                double leftDelayed = feedDelayLeft[readAt];
                double rightDelayed = feedDelayRight[readAt];
                feedDelayLeft[feedDelayAt] = (float) fromLeft;
                feedDelayRight[feedDelayAt] = (float) fromRight;
                feedDelayAt = (feedDelayAt + 1) % feedDelayLeft.length;
                double keep = 1.0 - 0.5 * p.crossfeed;
                outL = outL * keep + rightDelayed * p.crossfeed;
                outR = outR * keep + leftDelayed * p.crossfeed;
            }
            backing[baseL + i] = (float) outL;
            backing[baseR + i] = (float) outR;
        }
    }

    @Override
    public String toString() {
        return "StereoModule{" + params + "}";
    }
}
