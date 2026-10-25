package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.util.Logger;

public final class ManualCue implements Transition {

    private final Transition base;

    private final double outroSecs;

    private final double introSecs;

    public ManualCue(Transition base, double outroSecs, double introSecs) {
        if (base == null) {
            throw new IllegalArgumentException("wrapped kind must not be null: this layer only changes the cue");
        }
        this.base = base;
        this.outroSecs = outroSecs;
        this.introSecs = introSecs;
    }

    public Transition inner() {
        return base;
    }

    public double outroSecs() {
        return outroSecs;
    }

    public double introSecs() {
        return introSecs;
    }

    @Override
    public long windowFrames(int sampleRate) {
        return base.windowFrames(sampleRate);
    }

    @Override
    public int render(AudioBuf out, AudioBuf from, AudioBuf to, float progress) {
        return base.render(out, from, to, progress);
    }

    @Override
    public String name() {
        return base.name() + " - manual cue";
    }

    @Override
    public boolean rewritesSamples() {
        return base.rewritesSamples();
    }

    @Override
    public PitchPlan pitchAt(float progress) {
        return base.pitchAt(progress);
    }

    @Override
    public TransitionPlan prepare(String outro, String intro, int sampleRate) {
        return prepare(outro, intro, sampleRate, null, null);
    }

    @Override
    public TransitionPlan prepare(String outro, String intro, int sampleRate,
                                  java.util.Map<String, String> outroHeaders,
                                  java.util.Map<String, String> introHeaders) {
        TransitionPlan basePlan = base.prepare(outro, intro, sampleRate, outroHeaders, introHeaders);
        double entry = basePlan.skipSecs;
        double tail = basePlan.outroTailSecs;
        String what = "";

        if (introSecs > 0.0) {

            Double introDuration = durationOf(intro, introHeaders);
            double value = introSecs;
            if (introDuration != null && introDuration.doubleValue() > 0.0) {

                double window = (double) base.windowFrames(sampleRate) / Math.max(1, sampleRate);
                double max = Math.max(0.0, introDuration.doubleValue() - window);
                value = Math.min(value, max);
            }
            entry = value;
            what = "entry " + fmt(value) + "s";
        }
        if (outroSecs > 0.0) {
            Double outroDuration = durationOf(outro, outroHeaders);
            double value = outroSecs;
            if (outroDuration != null && outroDuration.doubleValue() > 0.0) {
                double window = (double) base.windowFrames(sampleRate) / Math.max(1, sampleRate);

                double max = Math.max(0.0, outroDuration.doubleValue() - window - 2.0);
                value = Math.min(value, max);
                tail = Math.max(0.0, outroDuration.doubleValue() - (value + window));
            }
            what = what.isEmpty() ? ("exit " + fmt(value) + "s")
                    : (what + ", exit " + fmt(value) + "s");
        }
        if (what.isEmpty()) {

            return basePlan;
        }
        String note = "manual cue: " + what + "(tail drop " + fmt(tail) + "s, overlap "
                + fmt((double) base.windowFrames(sampleRate) / Math.max(1, sampleRate)) + "s）";
        Logger.info("[qplayer-engine] {}", note);
        return new TransitionPlan(entry, basePlan.pitch, basePlan.introStartAt, note, tail);
    }

    private static Double durationOf(String source, java.util.Map<String, String> headers) {
        try (dev.t1m3.qplayer.audio.decode.Decoder decoder =
                     headers == null || headers.isEmpty()
                             ? dev.t1m3.qplayer.audio.decode.Decoder.open(source)
                             : dev.t1m3.qplayer.audio.decode.Decoder.open(source, headers)) {
            return decoder.spec().durationSecs();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.1f", Double.valueOf(v));
    }

    @Override
    public String toString() {
        return "ManualCue{" + base + ", exit " + fmt(outroSecs) + "s / entry " + fmt(introSecs) + "s}";
    }
}
