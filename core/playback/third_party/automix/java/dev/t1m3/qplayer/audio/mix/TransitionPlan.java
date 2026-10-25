package dev.t1m3.qplayer.audio.mix;

public final class TransitionPlan {

    public static final TransitionPlan NONE = new TransitionPlan(0.0, PitchPlan.ZERO, 0f, "");

    public final double outroTailSecs;

    public final double skipSecs;

    public final PitchPlan pitch;

    public final float introStartAt;

    public final String note;

    public final double[] vocalDuckRanges;

    public final float[] vocalDuckCurve;

    public TransitionPlan(double skipSecs, PitchPlan pitch, float introStartAt, String note) {
        this(skipSecs, pitch, introStartAt, note, 0.0);
    }

    public TransitionPlan(double skipSecs, PitchPlan pitch, float introStartAt, String note,
                          double outroTailSecs, double[] vocalDuckRanges) {
        this(skipSecs, pitch, introStartAt, note, outroTailSecs, vocalDuckRanges, null);
    }

    public TransitionPlan(double skipSecs, PitchPlan pitch, float introStartAt, String note,
                          double outroTailSecs, double[] vocalDuckRanges,
                          float[] vocalDuckCurve) {
        this.skipSecs = skipSecs;
        this.pitch = pitch == null ? PitchPlan.ZERO : pitch;
        this.introStartAt = introStartAt;
        this.note = note == null ? "" : note;
        this.outroTailSecs = outroTailSecs > 0.0 ? outroTailSecs : 0.0;
        this.vocalDuckRanges = vocalDuckRanges;
        this.vocalDuckCurve = vocalDuckCurve;
    }

    public TransitionPlan(double skipSecs, PitchPlan pitch, float introStartAt, String note,
                          double outroTailSecs) {
        this(skipSecs, pitch, introStartAt, note, outroTailSecs, null);
    }

    public boolean isNoop() {
        return skipSecs <= 0.0 && pitch.isZero() && introStartAt <= 0f && note.isEmpty()
                && outroTailSecs <= 0.0 && vocalDuckRanges == null;
    }

    @Override
    public String toString() {
        return "TransitionPlan{skipSecs=" + skipSecs + ", pitch=" + pitch
                + ", introStartAt=" + introStartAt + ", outroTailSecs=" + outroTailSecs
                + ", note='" + note + "'}";
    }
}
