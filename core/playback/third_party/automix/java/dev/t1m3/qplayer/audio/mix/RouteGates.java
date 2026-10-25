package dev.t1m3.qplayer.audio.mix;

import java.util.ArrayList;
import java.util.List;

public final class RouteGates {

    public static final double ALIAS_TOLERANCE = 0.06;

    public static double tempoAlias(double bpmA, double bpmB) {
        return tempoAlias(bpmA, bpmB, ALIAS_TOLERANCE);
    }

    public static double tempoAlias(double bpmA, double bpmB, double tolerance) {
        if (!(bpmA > 0.0) || !(bpmB > 0.0) || !(tolerance > 0.0)) {
            return 0.0;
        }
        double ratio = bpmA / bpmB;
        if (Math.abs(ratio - 2.0) <= tolerance * 2.0) {
            return 2.0;
        }
        if (Math.abs(ratio - 0.5) <= tolerance * 0.5) {
            return 0.5;
        }
        if (Math.abs(ratio - 1.0) <= tolerance) {
            return 1.0;
        }
        return 0.0;
    }

    public static double quantumBeats(double alias) {
        return alias == 2.0 || alias == 0.5 ? 2.0 : 4.0;
    }

    public static double snapBeatsToQuantum(double beats, double alias) {
        if (!(beats > 0.0)) {
            return beats;
        }
        double quantum = quantumBeats(alias);

        double snapped = Math.ceil(beats / quantum - 1e-9) * quantum;
        return snapped < quantum ? quantum : snapped;
    }

    static final double STRICT_TEMPO = 0.03;

    static final double LOOSE_TEMPO = 0.10;

    static final double KEY_GATE = 0.70;

    static final double MIN_OVERLAP = 1.5;

    static final double MIN_BEATS = 4.0;

    static final double GENRE_GATE = 0.30;

    public static final double GENRE_DISPATCH_GATE = 0.5;

    static int dispatchedGenre(Signals s) {
        if (s.genres == null) {
            return -1;
        }
        int best = -1;
        double bestScore = GENRE_DISPATCH_GATE;
        int[] slots = {0, 1, 3};
        for (int slot : slots) {
            if (slot < s.genres.length && s.genres[slot] >= bestScore) {
                bestScore = s.genres[slot];
                best = slot;
            }
        }
        return best;
    }

    static Double genreDispatchFactor(Signals s, int mySlot) {
        int dispatched = dispatchedGenre(s);
        if (dispatched < 0) {
            return genre(s.genres, mySlot);
        }
        return gate(dispatched == mySlot);
    }

    private RouteGates() {
    }

    public static final class Signals {

        public Double tempoDiff;

        public Double keyScore;

        public Double lowClash;

        public Double dropReliable;

        public Double overlapSecs;

        public Boolean buildUp;

        public Double vocalOverlapSecs;

        public boolean vocalOverlapReliable = false;

        public Double vocalOverlapStrength;

        public Integer vocalKind;

        public Double outroBeats;
        public Double introBeats;

        public Double trailingSpanSecs;

        public Boolean incomingQuieter;

        public Boolean matchingBars;

        public Double tailDecayDbPerSec;

        public Double bpmA;
        public Double bpmB;

        public double[] genres;

        public Double melody;

        public Boolean outgoingLoudnessSignificant;
        public Double incomingLoudnessRelationCode;
    }

    private static Double num(Double v) {
        return (v == null || Double.isNaN(v.doubleValue())) ? null : v;
    }

    static Double reasonBonus(Double v) {
        if (v == null || Double.isNaN(v.doubleValue())) {
            return Double.valueOf(1.0);
        }
        double x = Math.max(0.0, Math.min(1.0, v.doubleValue()));
        return Double.valueOf(0.7 + 0.3 * x);
    }

    static Double melodyFactor(Double melody) {
        if (melody == null || Double.isNaN(melody.doubleValue())) {
            return Double.valueOf(1.0);
        }
        double m = Math.max(0.0, Math.min(1.0, melody.doubleValue()));
        return Double.valueOf(0.7 + 0.3 * m);
    }

    public static final double LOUDNESS_SIGNIFICANT_LUFS = -40.0;

    public static boolean outgoingLoudnessSignificant(float[] mono, int rate) {
        if (mono == null || mono.length == 0) {
            return false;
        }
        double lufs = dev.t1m3.qplayer.audio.dsp.Lufs.integrated(mono, null, rate);
        return lufs > LOUDNESS_SIGNIFICANT_LUFS;
    }

    static Double tempoGateFactor(Signals s) {
        if (s == null) {
            return null;
        }
        if (s.bpmA != null && s.bpmB != null
                && tempoAlias(s.bpmA.doubleValue(), s.bpmB.doubleValue()) != 0.0) {
            return Double.valueOf(1.0);
        }
        return s.tempoDiff == null ? null
                : gate(s.tempoDiff.doubleValue() <= LOOSE_TEMPO);
    }

    private static Double gate(boolean ok) {
        return ok ? 1.0 : 0.0;
    }

    public static final double SOFT_FACTOR = 0.75;

    static Double gateWithConfidence(boolean ok, boolean reliable) {
        if (ok) {
            return 1.0;
        }
        return reliable ? 0.0 : SOFT_FACTOR;
    }

    private static Double genre(double[] genres, int index) {
        if (genres == null || index >= genres.length) {
            return null;
        }
        double g = genres[index];
        return g >= GENRE_GATE ? g : 0.0;
    }

    public static List<Double> factorsFor(MixStyle style, Signals s) {
        if (style == null || s == null) {
            return null;
        }

        List<Double> f = new ArrayList<Double>(6);
        boolean halfDouble = aliasPair(s);
        boolean strictTempo = s.tempoDiff != null && s.tempoDiff.doubleValue() <= STRICT_TEMPO;
        boolean looseTempo = s.tempoDiff != null && s.tempoDiff.doubleValue() <= LOOSE_TEMPO;
        boolean keyOk = s.keyScore == null || s.keyScore.doubleValue() >= KEY_GATE;
        boolean overlapOk = s.overlapSecs != null && s.overlapSecs.doubleValue() >= MIN_OVERLAP;
        boolean shortOverlap = s.overlapSecs != null && s.overlapSecs.doubleValue() < MIN_OVERLAP;
        boolean beatsOk = s.outroBeats != null && s.outroBeats.doubleValue() >= MIN_BEATS
                && s.introBeats != null && s.introBeats.doubleValue() >= MIN_BEATS;
        boolean longTail = s.tailDecayDbPerSec != null
                && s.tailDecayDbPerSec.doubleValue() <= LONG_TAIL_DB_PER_SEC;
        switch (style) {

            case FILTER_EXPANSION:

                f.add(gate(strictTempo));
                f.add(gate(keyOk));
                f.add(gate(matchingBarsOk(s)));
                f.add(gateWithConfidence(vocalOverlapOk(s), vocalVerdictReliable(s)));
                f.add(gateWithConfidence(vocalOverlapOk(s), vocalVerdictReliable(s)));
                f.add(melodyFactor(s.melody));
                return f;

            case FILTER_HIGH_TO_LOW:

                f.add(gate(halfDouble));
                f.add(gate(strictTempo));

                f.add(gate(s.outroBeats != null && s.outroBeats.doubleValue() >= 5.0
                        && !longWindow(s)));
                f.add(gate(s.introBeats != null && s.introBeats.doubleValue() >= MIN_BEATS));
                f.add(melodyFactor(s.melody));
                return f;
            case FILTER_SHORT_HIGH_TO_LOW:

                f.add(gate(halfDouble));
                f.add(gate(strictTempo));
                f.add(gate(s.outroBeats != null && s.outroBeats.doubleValue() <= 4.0));
                f.add(melodyFactor(s.melody));
                return f;

            case FILTER_LONG_HIGH_TO_LOW:
                f.add(gate(strictTempo));
                f.add(gate(looseTempo));
                f.add(gate(matchingBarsOk(s)));
                f.add(gate(longWindow(s)));
                f.add(melodyFactor(s.melody));
                return f;
            case FILTER_LONG_OUT_SHORT_IN:
                f.add(gate(looseTempo));
                f.add(gate(overlapOk));
                f.add(gate(longOutShortIn(s)));

                f.add(gate(s.incomingQuieter == null || s.incomingQuieter.booleanValue()));

                f.add(gate(s.trailingSpanSecs == null || s.trailingSpanSecs.doubleValue() < 3.0));
                f.add(gate(matchingBarsOk(s)));
                f.add(melodyFactor(s.melody));
                return f;
            case FILTER_DYNAMIC_IN:
                f.add(gate(looseTempo));
                f.add(gate(overlapOk));
                f.add(gate(longOutShortIn(s)));

                f.add(gate(s.incomingQuieter != null && !s.incomingQuieter.booleanValue()));
                f.add(melodyFactor(s.melody));
                return f;
            case FILTER_LONG_OUT_SHORT_IN_DELAY:
                f.add(gate(looseTempo));
                f.add(gate(overlapOk));
                f.add(gate(longOutShortIn(s)));
                f.add(gate(trailingSpanOk(s)));
                f.add(gate(!matchingBarsOk(s)));
                f.add(melodyFactor(s.melody));
                return f;
            case FILTER_LONG_OUT_SHORT_IN_REVERB:
                f.add(gate(looseTempo));
                f.add(gate(overlapOk));
                f.add(gate(longOutShortIn(s)));

                f.add(gate(s.trailingSpanSecs != null && s.trailingSpanSecs.doubleValue() >= 3.0));
                f.add(gate(matchingBarsOk(s)));
                f.add(melodyFactor(s.melody));
                return f;
            case FILTER_SOFT_REVERB:
                f.add(gate(looseTempo));
                f.add(gate(overlapOk));
                f.add(gate(s.lowClash != null && s.lowClash.doubleValue() < 0.3));
                f.add(melodyFactor(s.melody));
                return f;

            case HIPHOP_LOOPING:

                f.add(genreDispatchFactor(s, 1));
                f.add(gate(halfDouble));
                f.add(gate(beatsOk));
                f.add(gate(trailingSpanOk(s)));
                f.add(gate(matchingBarsOk(s)));
                f.add(melodyFactor(s.melody));
                return f;
            case HIPHOP_DELAY_OUT:
                f.add(genreDispatchFactor(s, 1));
                f.add(gate(halfDouble));
                f.add(gate(s.outroBeats != null && s.outroBeats.doubleValue() >= MIN_BEATS));
                f.add(gateWithConfidence(!trailingSpanOk(s), s.trailingSpanSecs != null));
                f.add(melodyFactor(s.melody));
                return f;

            case POP_DELAY_OUT:
                f.add(genreDispatchFactor(s, 3));
                f.add(gate(s.introBeats != null && s.introBeats.doubleValue() >= MIN_BEATS));
                f.add(gateWithConfidence(vocalOverlapOk(s), vocalVerdictReliable(s)));
                f.add(s.outgoingLoudnessSignificant == null
                        ? null : gate(s.outgoingLoudnessSignificant.booleanValue()));
                f.add(gateWithConfidence(!trailingSpanOk(s), s.trailingSpanSecs != null));
                f.add(melodyFactor(s.melody));
                return f;
            case POP_REVERB:
                f.add(genreDispatchFactor(s, 3));
                f.add(gate(s.introBeats != null && s.introBeats.doubleValue() >= MIN_BEATS));
                f.add(gateWithConfidence(vocalOverlapOk(s), vocalVerdictReliable(s)));
                f.add(s.outgoingLoudnessSignificant == null
                        ? null : gate(s.outgoingLoudnessSignificant.booleanValue()));
                f.add(gate(trailingSpanOk(s)));
                f.add(melodyFactor(s.melody));
                return f;

            case DANCE_ANY_KEY:
                f.add(genreDispatchFactor(s, 0));
                f.add(gate(beatsOk));
                f.add(gate(s.keyScore == null || s.keyScore.doubleValue() < KEY_GATE));
                f.add(melodyFactor(s.melody));
                return f;
            case DANCE_REPEATER:
                f.add(genreDispatchFactor(s, 0));
                f.add(gate(overlapOk));
                f.add(gate(s.outroBeats != null && s.outroBeats.doubleValue() >= MIN_BEATS));
                f.add(gate(trailingSpanOk(s)));
                f.add(melodyFactor(s.melody));
                return f;

            case CROSSFADE_OVERLAP:
                f.add(gate(s.incomingQuieter != null && !s.incomingQuieter.booleanValue()));
                f.add(gate(s.trailingSpanSecs != null && s.trailingSpanSecs.doubleValue() < MIN_TRAILING_SPAN_SECS));
                f.add(melodyFactor(s.melody));
                return f;
            case CROSSFADE_OVERLAP_REVERB:
                f.add(gate(s.incomingQuieter != null && !s.incomingQuieter.booleanValue()));
                f.add(gate(s.trailingSpanSecs != null && s.trailingSpanSecs.doubleValue() >= MIN_TRAILING_SPAN_SECS));
                f.add(melodyFactor(s.melody));
                return f;
            case CROSSFADE_RINGOUT:
                f.add(gate(s.incomingQuieter != null && s.incomingQuieter.booleanValue()));
                f.add(gate(s.trailingSpanSecs != null && s.trailingSpanSecs.doubleValue() < MIN_TRAILING_SPAN_SECS));
                f.add(melodyFactor(s.melody));
                return f;
            case CROSSFADE_RINGOUT_REVERB:
                f.add(gate(s.incomingQuieter != null && s.incomingQuieter.booleanValue()));
                f.add(gate(s.trailingSpanSecs != null && s.trailingSpanSecs.doubleValue() >= MIN_TRAILING_SPAN_SECS));
                f.add(melodyFactor(s.melody));
                return f;

            case CROSSFADE_LONG:
                f.add(gate(s.incomingQuieter == null));
                f.add(gate(longWindow(s)));
                f.add(tempoGateFactor(s));
                f.add(melodyFactor(s.melody));
                return f;
            case TAIL_RIDE:
                f.add(gate(s.incomingQuieter == null));
                f.add(gate(s.trailingSpanSecs != null
                        && s.trailingSpanSecs.doubleValue() >= 3.0));
                f.add(gate(!longWindow(s)));
                f.add(melodyFactor(s.melody));
                return f;
            case PLAIN_BLEND:
                f.add(gate(s.incomingQuieter == null && s.trailingSpanSecs == null));
                f.add(tempoGateFactor(s));
                f.add(gate(overlapOk));
                f.add(melodyFactor(s.melody));
                return f;

            case BASS_SWAP:

                f.add(tempoGateFactor(s));
                f.add(gate(s.lowClash != null && s.lowClash.doubleValue() >= 0.6));
                f.add(reasonBonus(s.lowClash));
                f.add(gate(overlapOk));
                f.add(melodyFactor(s.melody));
                return f;

            case NO_VOCAL_CLASH:
                f.add(gateWithConfidence(vocalOverlapOk(s), vocalVerdictReliable(s)));
                f.add(gate(overlapOk));
                f.add(melodyFactor(s.melody));
                return f;

            case GAPLESS:
                f.add(gate(shortOverlap));
                f.add(gate(s.outroBeats != null && s.outroBeats.doubleValue() >= 4.0));
                f.add(melodyFactor(s.melody));
                return f;
            case BEAT_CUT:
                f.add(gate(shortOverlap));
                f.add(gate(s.outroBeats != null && s.outroBeats.doubleValue() >= 1.0
                        && s.outroBeats.doubleValue() < 4.0));
                f.add(melodyFactor(s.melody));
                return f;
            case SPLICE:
                f.add(gate(shortOverlap));
                f.add(gate(s.outroBeats == null || s.outroBeats.doubleValue() < 1.0));
                f.add(melodyFactor(s.melody));
                return f;

            case SHOW_MODE:
                f.add(gate(s.buildUp != null && s.buildUp.booleanValue()));
                f.add(reasonBonus(s.dropReliable));
                f.add(melodyFactor(s.melody));
                return f;
            case LEARNED_EQ:

                f.add(tempoGateFactor(s));

                f.add(gate(s.dropReliable != null && s.dropReliable.doubleValue() >= 0.7
                        && s.lowClash != null && s.lowClash.doubleValue() >= 0.6));
                f.add(num(s.lowClash));
                f.add(reasonBonus(s.dropReliable));
                f.add(melodyFactor(s.melody));
                return f;
            case AUTO:
            default:
                return null;
        }
    }

    static final double LONG_TAIL_DB_PER_SEC = -3.0;

    static boolean longOutShortIn(Signals s) {
        return s.outroBeats != null && s.introBeats != null
                && s.outroBeats.doubleValue() >= 8.0 && s.introBeats.doubleValue() < 8.0;
    }

    static boolean longWindow(Signals s) {
        return s.outroBeats != null && s.introBeats != null
                && s.outroBeats.doubleValue() >= 8.0 && s.introBeats.doubleValue() >= 8.0;
    }

    public static final double MIN_TRAILING_SPAN_SECS = 1.0;

    static boolean trailingSpanOk(Signals s) {
        return s.trailingSpanSecs != null
                && s.trailingSpanSecs.doubleValue() >= MIN_TRAILING_SPAN_SECS;
    }

    static boolean incomingQuieterOk(Signals s) {
        return s.incomingQuieter != null && s.incomingQuieter.booleanValue();
    }

    static boolean matchingBarsOk(Signals s) {
        if (s.outroBeats == null || s.introBeats == null) {
            return false;
        }
        double a = s.outroBeats.doubleValue();
        double b = s.introBeats.doubleValue();
        if (a < 4.0 || b < 4.0) {
            return false;
        }

        return a % 4.0 == 0.0 && b % 4.0 == 0.0;
    }

    public static double centreRatio(float[] left, float[] right, int frames) {
        if (left == null || right == null || frames <= 0) {
            return Double.NaN;
        }
        int n = Math.min(frames, Math.min(left.length, right.length));
        double mid = 0.0;
        double side = 0.0;
        for (int i = 0; i < n; i += 16) {
            double m = (left[i] + right[i]) * 0.5;
            double sd = (left[i] - right[i]) * 0.5;
            mid += m * m;
            side += sd * sd;
        }
        double total = mid + side;
        return total <= 1e-12 ? Double.NaN : mid / total;
    }

    public static final double CENTRE_RELIABLE_MIN = 0.75;

    public static boolean vocalPresenceIsReliable(double centreRatio) {
        return !Double.isNaN(centreRatio) && centreRatio >= CENTRE_RELIABLE_MIN;
    }

    static boolean vocalOverlapOk(Signals s) {
        Double strength = s.vocalOverlapStrength;
        Integer kind = s.vocalKind;
        if (strength != null && !Double.isNaN(strength.doubleValue())
                && kind != null && kind.intValue() >= 0) {
            return strength.doubleValue() <= vocalToleranceFor(kind.intValue());
        }
        return s.vocalOverlapSecs == null || s.vocalOverlapSecs.doubleValue() < 0.5;
    }

    static double vocalToleranceFor(int kind) {
        switch (kind) {
            case 2:  return 0.6;
            case 1:  return 0.5;
            default: return 0.25;
        }
    }

    static boolean vocalVerdictReliable(Signals s) {
        Double strength = s.vocalOverlapStrength;
        Integer kind = s.vocalKind;
        boolean byStrength = strength != null && !Double.isNaN(strength.doubleValue())
                && kind != null && kind.intValue() >= 0;
        return byStrength || Boolean.TRUE.equals(s.vocalOverlapReliable);
    }

    static boolean aliasPair(Signals s) {
        return s.bpmA != null && s.bpmB != null
                && tempoAlias(s.bpmA.doubleValue(), s.bpmB.doubleValue()) != 0.0;
    }
}
