package dev.t1m3.qplayer.audio.mix;

import java.util.Locale;
import java.util.Objects;

public final class Chooser {

    private Chooser() {
    }

    public static final class AutoChoice {

        public final MixStyle style;

        public final String why;

        public AutoChoice(MixStyle style, String why) {
            this.style = style;
            this.why = why;
        }

        @Override
        public String toString() {
            return "AutoChoice { style: " + style + ", why: \"" + why + "\" }";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AutoChoice)) {
                return false;
            }
            AutoChoice c = (AutoChoice) o;
            return style == c.style && Objects.equals(why, c.why);
        }

        @Override
        public int hashCode() {
            int h = style == null ? 0 : style.hashCode();
            return 31 * h + (why == null ? 0 : why.hashCode());
        }
    }

    public static AutoChoice choose(Analysis.Tempo tOut, Analysis.Tempo tIn,
                                    Harmonic.Match harmony, MixTuning tuning,
                                    MaterialProfile profile, double windowSecs) {
        if (profile == null || !profile.known()) {
            return choose(tOut, tIn, harmony, tuning);
        }
        if (tOut == null || tIn == null) {
            return new AutoChoice(MixStyle.PLAIN_BLEND,
                    "auto: no beats determined, falling back to a plain blend (no guessing)");
        }
        float mean = maxF32((tOut.bpm + tIn.bpm) * 0.5f, 1.0f);
        float diff = Math.abs(tOut.bpm - tIn.bpm) / mean;
        boolean tempoConflict = diff >= tuning.autoTempoConflict;
        boolean keyConflict = harmony != null && harmony.plainScore < tuning.autoKeySame;
        boolean anyConflict = tempoConflict || keyConflict;
        boolean lowClash = profile.lowClash() >= tuning.lowClashGate;

        if (!anyConflict) {
            return new AutoChoice(MixStyle.PLAIN_BLEND,
                    String.format(Locale.ROOT,
                            "auto: tempo gap %.1f%%, key %.3f and low-end clash %.2f are all below threshold -> "
                                    + "plain blend (no unnecessary filtering)",
                            Float.valueOf(diff * 100.0f),
                            Float.valueOf(harmony == null ? 0f : harmony.plainScore),
                            Float.valueOf(profile.lowClash())));
        }

        if (profile.overlapSecs() < tuning.minOverlapSecs) {
            return new AutoChoice(MixStyle.BEAT_CUT,
                    String.format(Locale.ROOT,
                            "auto: only %.1fs of overlappable material (under %.1fs) -> beat cut"
                                    + " (with nothing to overlap, a clean cut on the beat is the least muddy)",
                            Float.valueOf(profile.overlapSecs()),
                            Float.valueOf(tuning.minOverlapSecs)));
        }

        if (profile.buildUp() && profile.dropReliable() >= tuning.dropGate) {
            return new AutoChoice(MixStyle.SHOW_MODE,
                    String.format(Locale.ROOT,
                            "auto: A's tail is building and B starts right on an energy peak (salience %.2f) -> "
                                    + "performance mode (high-pass sweep up + low-pass opening; the sweep serves the music itself)",
                            Float.valueOf(profile.dropReliable())));
        }

        if (profile.tailDecayDbPerSec() >= tuning.tailLongDbPerSec
                && profile.headOnset() <= tuning.headCalmGate) {
            return new AutoChoice(MixStyle.TAIL_RIDE,
                    String.format(Locale.ROOT,
                            "auto: A's tail decays at only %+.1f dB/s (long tail) while B's head jumps %.2f (entering into space) -> "
                                    + "tail riding (the tail lies over B and decays more slowly)",
                            Float.valueOf(profile.tailDecayDbPerSec()),
                            Float.valueOf(profile.headOnset())));
        }

        if (anyConflict && lowClash && profile.dropReliable() >= tuning.dropGate
                && windowSecs >= tuning.learnedEqMinWindowSecs) {
            return new AutoChoice(MixStyle.LEARNED_EQ,
                    String.format(Locale.ROOT,
                            "auto: low-end clash %.2f, drop salience %.2f, window %.1fs -> "
                                    + "learned EQ (outgoing fades equal-power, incoming gets its lows cut by clash and knee)"

                                    + "| clash basis: tempo gap %.2f%% (threshold %.0f%%), key %.3f (threshold %.2f)"
                                    + " - low end %.2f (threshold %.2f)",
                            Float.valueOf(profile.lowClash()),
                            Float.valueOf(profile.dropReliable()),
                            Float.valueOf((float) windowSecs),
                            Float.valueOf(diff * 100.0f),
                            Float.valueOf(tuning.autoTempoConflict * 100.0f),
                            Float.valueOf(harmony == null ? -1f : harmony.plainScore),
                            Float.valueOf(tuning.autoKeySame),
                            Float.valueOf(profile.lowClash()),
                            Float.valueOf(tuning.lowClashGate)));
        }

        return new AutoChoice(MixStyle.BASS_SWAP,
                String.format(Locale.ROOT,
                        "auto: there is a clash (tempo gap %.1f%%%s) but %s -> bass handover"
                                + " (both tracks overlap at equal loudness and the lows change hands on a plateau, no knee needed)",
                        Float.valueOf(diff * 100.0f),
                        keyConflict ? ", key mismatch" : "",
                        profile.dropReliable() < tuning.dropGate ? "no drop that holds up" : "the window is on the short side"));
    }

    public static AutoChoice choose(Analysis.Tempo tOut, Analysis.Tempo tIn,
                                    Harmonic.Match harmony, MixTuning tuning) {

        if (tOut == null || tIn == null) {
            return new AutoChoice(
                    MixStyle.PLAIN_BLEND,
                    "auto: no beats determined, falling back to a plain blend (no guessing)");
        }
        Analysis.Tempo a = tOut;
        Analysis.Tempo b = tIn;

        float mean = maxF32((a.bpm + b.bpm) * 0.5f, 1.0f);
        float diff = Math.abs(a.bpm - b.bpm) / mean;

        if (diff >= tuning.autoTempoConflict) {

            return new AutoChoice(
                    MixStyle.BASS_SWAP,
                    String.format(Locale.ROOT,
                            "auto: tempo gap %.1f%% (%.1f -> %.1f BPM) reaches the %.0f%% threshold, "
                                    + "rhythms do not line up -> bass handover (equal-loudness overlap, lows change hands, "
                                    + "no sweep and no mid scoop - the auto lane applies no audible processing)",
                            diff * 100.0f,
                            a.bpm,
                            b.bpm,
                            tuning.autoTempoConflict * 100.0f));
        }

        if (harmony != null) {
            if (harmony.plainScore >= tuning.autoKeySame) {
                return new AutoChoice(
                        MixStyle.PLAIN_BLEND,
                        String.format(Locale.ROOT,
                                "auto: tempo gap %.1f%% and key similarity %.3f reaches %.2f, "
                                        + "the two already fit -> plain blend (no unnecessary filtering)",
                                diff * 100.0f,
                                harmony.plainScore,
                                tuning.autoKeySame));
            }
            return new AutoChoice(
                    MixStyle.BASS_SWAP,
                    String.format(Locale.ROOT,
                            "auto: tempo lines up (gap %.1f%%) but key similarity is only %.3f -> "
                                    + "bass handover: both tracks overlap at equal loudness and the lows change hands on a plateau, "
                                    + "so the two sets of harmony never push against each other",
                            diff * 100.0f,
                            harmony.plainScore));
        }
        return new AutoChoice(
                MixStyle.PLAIN_BLEND,
                String.format(Locale.ROOT,
                        "auto: tempo lines up (gap %.1f%%) but the key is undetermined, falling back to a plain blend (no guessing)",
                        diff * 100.0f));
    }

    private static float maxF32(float a, float b) {
        if (Float.isNaN(a)) {
            return b;
        }
        if (Float.isNaN(b)) {
            return a;
        }
        return Math.max(a, b);
    }
}
