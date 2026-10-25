package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public final class PitchModule implements StudioModule {
    public static final int LATENCY_FRAMES = StreamingPitchShift.LATENCY_FRAMES;

    private final StreamingPitchShift shifter;

    private volatile PitchParams params = PitchParams.off();

    public PitchModule(int rate, int channels) {
        this.shifter = new StreamingPitchShift(rate, channels);
    }

    public void setParams(PitchParams value) {
        this.params = value == null ? PitchParams.off() : value;
    }

    public PitchParams params() {
        return params;
    }

    @Override
    public boolean active() {
        return params.active() || shifter.active();
    }

    @Override
    public int latencyFrames() {
        return active() ? LATENCY_FRAMES : 0;
    }

    @Override
    public void reset() {
        shifter.reset();
    }

    @Override
    public void process(AudioBuf buf) {
        PitchParams p = params;
        if (!p.active()) {
            if (shifter.active()) {
                shifter.flushHold(buf);
            }
            return;
        }
        shifter.setSemitones(p.totalSemitones());
        shifter.process(buf, (float) p.mix);
    }

    @Override
    public String toString() {
        return "PitchModule{" + params + (shifter.active() ? ", finishing" : "") + "}";
    }
}
