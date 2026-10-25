package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.dsp.Biquad;

public final class RackProcessor {
    private final int rate;
    private final int channels;
    private final EqProcessor eq;
    private final PitchModule pitch;

    private final SaturationModule saturation;

    private final DynamicsModule dynamics;

    private final StereoModule stereo;

    private volatile StudioRack rack = StudioRack.EMPTY;

    private final float[] gainTarget;
    private final float[] gainCurrent;

    private final Biquad loudLow;
    private final Biquad loudHigh;
    private double loudLowDb;
    private double loudHighDb;

    private volatile float volume = 1.0f;

    public RackProcessor(int rate, int channels) {
        this.rate = Math.max(rate, 1);
        this.channels = Math.max(channels, 1);
        this.eq = new EqProcessor(this.rate, this.channels);
        this.pitch = new PitchModule(this.rate, this.channels);
        this.saturation = new SaturationModule(this.rate, this.channels);
        this.dynamics = new DynamicsModule(this.rate, this.channels);
        this.stereo = new StereoModule(this.rate, this.channels);
        this.gainTarget = new float[this.channels];
        this.gainCurrent = new float[this.channels];
        java.util.Arrays.fill(this.gainCurrent, 1.0f);
        this.loudLow = Biquad.lowShelf(this.rate, 120f, 0.7071f, 0f);
        this.loudHigh = Biquad.highShelf(this.rate, 6000f, 0.7071f, 0f);
    }

    public void setRack(StudioRack value) {
        StudioRack next = value == null ? StudioRack.EMPTY : value;
        this.rack = next;
        eq.setBands(next.bands);
        pitch.setParams(next.pitch);
        saturation.setParams(next.saturation);
        dynamics.setParams(next.dynamics);
        stereo.setParams(next.stereo);
        recomputeGains(next);
        updateLoudness(next, volume);
    }

    public void setVolume(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        if (Math.abs(v - volume) < 1e-4f) {
            return;
        }
        volume = v;
        updateLoudness(rack, v);
    }

    public int rate() {
        return rate;
    }

    public boolean active() {
        return rack.active()
                || pitch.active()
                || saturation.active()
                || dynamics.active()
                || stereo.active();
    }

    public int latencyFrames() {
        return rack.latencyFrames(rate);
    }

    public float gainReductionDb() {
        return dynamics.gainReductionDb();
    }

    public int activeBands() {
        StudioRack r = rack;
        return r.active() ? r.activeBands() : 0;
    }

    public void reset() {
        eq.reset();
        pitch.reset();
        saturation.reset();
        dynamics.reset();
        stereo.reset();
        loudLow.reset();
        loudHigh.reset();
        java.util.Arrays.fill(gainCurrent, 1.0f);
    }

    public void process(AudioBuf buf) {
        StudioRack r = rack;
        if (!r.active()) {
            pitch.process(buf);
            saturation.process(buf);
            dynamics.process(buf);
            stereo.process(buf);
            return;
        }
        int frames = buf.frames();
        if (frames <= 0) {
            return;
        }
        float[] backing = buf.backingArray();
        int chs = Math.min(buf.channels(), channels);
        for (int ch = 0; ch < chs; ch++) {
            float from = gainCurrent[ch];
            float to = gainTarget[Math.min(ch, gainTarget.length - 1)];
            int base = buf.channelBase(ch);
            float step = (to - from) / (float) frames;
            float g = from;
            for (int i = 0; i < frames; i++) {
                g += step;
                backing[base + i] *= g;
            }
            gainCurrent[ch] = to;
        }
        eq.process(buf);
        pitch.process(buf);
        saturation.process(buf);
        dynamics.process(buf);
        stereo.process(buf);
        if (r.loudness && r.loudnessAmount > 1e-6) {
            for (int ch = 0; ch < chs; ch++) {
                int base = buf.channelBase(ch);
                for (int i = 0; i < frames; i++) {
                    float v = backing[base + i];
                    v = loudLow.process(v);
                    backing[base + i] = loudHigh.process(v);
                }
            }
        }
    }

    private void recomputeGains(StudioRack r) {
        float preamp = (float) Math.pow(10.0, r.preampDb / 20.0);
        float polarity = r.polarityInvert ? -1f : 1f;
        for (int ch = 0; ch < channels; ch++) {
            float balance = 1f;
            if (ch == 0 && r.balance > 0) {
                balance = (float) (1.0 - r.balance);
            } else if (ch > 0 && r.balance < 0) {
                balance = (float) (1.0 + r.balance);
            }
            gainTarget[ch] = preamp * balance * polarity;
        }
    }

    private void updateLoudness(StudioRack r, float volume) {
        double wantLow = 0.0;
        double wantHigh = 0.0;
        if (r.loudness && r.loudnessAmount > 1e-6 && volume < 1.0f) {
            double cutDb = -20.0 * Math.log10(Math.max(volume, 1e-4));
            wantLow = Math.min(12.0, cutDb * 0.6) * r.loudnessAmount;
            wantHigh = Math.min(6.0, cutDb * 0.3) * r.loudnessAmount;
        }
        if (Math.abs(wantLow - loudLowDb) > 0.1 || Math.abs(wantHigh - loudHighDb) > 0.1) {
            loudLow.updateLowShelf(rate, 120f, 0.7071f, (float) wantLow);
            loudHigh.updateHighShelf(rate, 6000f, 0.7071f, (float) wantHigh);
            loudLowDb = wantLow;
            loudHighDb = wantHigh;
        }
    }
}
