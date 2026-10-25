package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.AudioException;

public final class HarmonicBlend implements Transition {

    private final int rate;

    private final int channels;

    private final long window;

    float shift;

    float score;

    public HarmonicBlend(int rate, int channels, float seconds) {
        int r = Math.max(rate, 1);
        this.rate = r;
        this.channels = Math.max(channels, 1);
        this.window = Math.round(Math.max(seconds, 0.0f) * (float) r);
        this.shift = 0.0f;
        this.score = 0.0f;
    }

    public float shift() {
        return shift;
    }

    public static final float PITCH_GLIDE_SECS = 0.20f;

    public static final boolean TRANSPOSITION_ENABLED = true;

    public float score() {
        return score;
    }

    public int channels() {
        return channels;
    }

    public int rate() {
        return rate;
    }

    @Override
    public long windowFrames(int sampleRate) {
        return window;
    }

    @Override
    public int render(AudioBuf out, AudioBuf from, AudioBuf to, float progress) {

        Mixer.Gains g = Mixer.equalPowerGains(Automix.clampF32(progress, 0.0f, 1.0f));
        return Mixer.mixTwo(out, from, g.from, to, g.to);
    }

    @Override
    public String name() {
        return "harmonic blend";
    }

    @Override
    public boolean rewritesSamples() {
        return true;
    }

    @Override
    public TransitionPlan prepare(String outro, String intro, int rate) {
        return prepare(outro, intro, rate, null, null);
    }

    @Override
    public TransitionPlan prepare(String outro, String intro, int rate,
                                  java.util.Map<String, String> outroHeaders,
                                  java.util.Map<String, String> introHeaders) {

        shift = 0.0f;
        score = 0.0f;

        Automix.Mono tail;
        try {
            tail = Automix.readMono(outro, outroHeaders, true, Harmonic.WINDOW_SECS);
        } catch (AudioException e) {
            return failurePlan("outgoing track analysis failed (" + e.getMessage() + "), no shift this time");
        }
        Automix.Mono head;
        try {
            head = Automix.readMono(intro, introHeaders, false, Harmonic.WINDOW_SECS);
        } catch (AudioException e) {
            return failurePlan("incoming track analysis failed (" + e.getMessage() + "), no shift this time");
        }

        Harmonic.Match m = Harmonic.match(tail.pcm, tail.rate, head.pcm, head.rate);
        PitchPlan pitch = PitchPlan.ZERO;
        String note;
        if (m != null) {

            score = Automix.maxF32(m.bestScore, m.plainScore);
            if (m.shouldShift && TRANSPOSITION_ENABLED) {
                shift = (float) m.semitones;

                pitch = new PitchPlan(shift, 0.0f);
            }
            note = intervalNote(m);
        } else {

            note = "harmonic blend - the material carries no key information, treating it as a plain blend";
        }
        return new TransitionPlan(0.0, pitch, 0.0f, note);
    }

    @Override
    public PitchPlan pitchAt(float progress) {
        if (Math.abs(shift) < 1e-6f) {
            return PitchPlan.ZERO;
        }
        float half = shift / 2.0f;
        if (Math.abs(half) > MAX_SIDE_SHIFT) {

            float g = Automix.smoothstep(0.0f, glideEnd(), progress);
            return new PitchPlan(shift * g, 0.0f);
        }
        float f = pitchCurve(progress);
        return new PitchPlan(half * f, -half * f);
    }

    static final float MAX_SIDE_SHIFT = 3.0f;

    static final float PITCH_CONVERGE_END = 0.25f;

    static final float PITCH_RETURN_START = 0.70f;

    static float pitchCurve(float progress) {
        float p = Math.max(0.0f, Math.min(1.0f, progress));
        if (p < PITCH_CONVERGE_END) {
            return smooth(p / PITCH_CONVERGE_END);
        }
        if (p <= PITCH_RETURN_START) {
            return 1.0f;
        }
        return smooth((1.0f - p) / (1.0f - PITCH_RETURN_START));
    }

    private static float smooth(float x) {
        float v = Math.max(0.0f, Math.min(1.0f, x));
        return v * v * (3.0f - 2.0f * v);
    }

    float glideEnd() {
        if (window <= 0L || rate <= 0) {
            return Automix.OUTRO_PITCH_GLIDE_END;
        }
        float capped = (PITCH_GLIDE_SECS * (float) rate) / (float) window;
        return Math.min(Automix.OUTRO_PITCH_GLIDE_END, capped);
    }

    static String intervalNote(Harmonic.Match m) {
        if (!TRANSPOSITION_ENABLED) {
            return "harmonic blend - " + m.describe() + "; pitch shifting is disabled for this lane";
        }
        if (!m.shouldShift) {
            return "harmonic blend - " + m.describe();
        }
        return "harmonic blend - " + m.describe() + "; the shift is applied to both instrumentals only (A's and B's vocals stay as they are)";
    }

    private TransitionPlan failurePlan(String note) {
        return new TransitionPlan(0.0, PitchPlan.ZERO, 0.0f, note);
    }

    @Override
    public String toString() {
        return "HarmonicBlend { rate: " + rate
                + ", channels: " + channels
                + ", window: " + window
                + ", shift: " + shift
                + ", score: " + score + " }";
    }
}
