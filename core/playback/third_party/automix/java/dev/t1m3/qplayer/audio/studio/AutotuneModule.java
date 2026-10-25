package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public final class AutotuneModule implements StudioModule {
    public static final double MIN_CLARITY = 0.60;

    private final PitchDetector detector;
    private final StreamingPitchShift shifter;
    private final int channels;

    private volatile AutotuneParams params = AutotuneParams.off();

    private double smoothedMidi;

    private double targetHz;

    private boolean pitchPrimed;

    private double smoothedCents;
    private double appliedCents;
    private double lastHz;
    private long voicedFrames;

    public AutotuneModule(int rate, int channels) {
        this.channels = Math.max(channels, 1);
        this.detector = new PitchDetector(rate);
        this.shifter = new StreamingPitchShift(rate, this.channels);
    }

    public void setParams(AutotuneParams value) {
        this.params = value == null ? AutotuneParams.off() : value;
    }

    public AutotuneParams params() {
        return params;
    }

    public double detectedHz() {
        return lastHz;
    }

    public long voicedFrames() {
        return voicedFrames;
    }

    @Override
    public boolean active() {
        return params.active() || shifter.active();
    }

    @Override
    public int latencyFrames() {
        return active() ? StreamingPitchShift.LATENCY_FRAMES : 0;
    }

    @Override
    public void reset() {
        detector.reset();
        shifter.reset();
        smoothedMidi = 0.0;
        targetHz = 0.0;
        pitchPrimed = false;
        smoothedCents = 0.0;
        appliedCents = 0.0;
        lastHz = 0.0;
        voicedFrames = 0L;
    }

    @Override
    public void process(AudioBuf buf) {
        AutotuneParams p = params;
        if (!p.active()) {
            if (shifter.active()) {
                shifter.flushHold(buf);
            }
            return;
        }
        int frames = buf.frames();
        if (frames <= 0) {
            return;
        }
        updateCorrection(buf, frames, p);
        shifter.setSemitones(appliedCents / 100.0);
        shifter.process(buf, (float) p.mix);
    }

    private void updateCorrection(AudioBuf buf, int frames, AutotuneParams p) {
        int chs = Math.min(buf.channels(), channels);
        float[] backing = buf.backingArray();
        double pitchAlpha = 1.0 - Math.exp(-PitchDetector.HOP_SECS / 0.120);
        double smoothAlpha = 1.0 - Math.exp(-PitchDetector.HOP_SECS / 0.100);
        double retuneSecs = p.retuneMs / 1000.0;
        double retuneAlpha =
                retuneSecs <= 1e-9 ? 1.0 : 1.0 - Math.exp(-PitchDetector.HOP_SECS / retuneSecs);
        for (int i = 0; i < frames; i++) {
            float mono = 0f;
            for (int ch = 0; ch < chs; ch++) {
                mono += backing[buf.channelBase(ch) + i];
            }
            mono /= chs;
            if (!detector.push(mono)) {
                continue;
            }
            double hz = detector.hz();
            lastHz = hz;
            if (hz <= 0.0 || detector.clarity() < MIN_CLARITY) {
                smoothedCents *= (1.0 - smoothAlpha * 0.5);
                continue;
            }
            double midi = ScaleQuantizer.midiOf(hz, p.a4);
            if (!pitchPrimed) {
                smoothedMidi = midi;
                smoothedCents = 0.0;
                pitchPrimed = true;
            } else {
                smoothedMidi += (midi - smoothedMidi) * pitchAlpha;
            }
            double smoothedHz = ScaleQuantizer.hzOf(smoothedMidi, p.a4);
            targetHz = ScaleQuantizer.targetHz(smoothedHz, p.scale, p.a4, p.tonic);
            if (!(targetHz > 0.0)) {
                continue;
            }
            double cents = 1200.0 * Math.log(hz / targetHz) / Math.log(2.0);
            smoothedCents += (cents - smoothedCents) * smoothAlpha;
            double basis = p.preserveVibrato ? smoothedCents : cents;
            double want = -p.strength * basis;
            appliedCents += (want - appliedCents) * retuneAlpha;
            voicedFrames++;
        }
    }

    @Override
    public String toString() {
        return String.format(
                java.util.Locale.ROOT,
                "AutotuneModule{%s, last pitch %.1f Hz, correction %+.1f cents%s}",
                params,
                Double.valueOf(lastHz),
                Double.valueOf(appliedCents),
                shifter.active() ? ", finishing" : "");
    }
}
