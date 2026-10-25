package dev.t1m3.qplayer.audio.mix;

import java.util.List;

public final class RouteScore {

    private static volatile boolean enabled = true;

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static final double POSITION_WEIGHT = 0.001;

    public static final int BASE_OURS = 30;

    private RouteScore() {
    }

    public static int base(MixStyle style) {
        switch (style) {

            case HIPHOP_DELAY_OUT:
            case HIPHOP_LOOPING:
                return 100;

            case FILTER_EXPANSION:
            case POP_DELAY_OUT:
            case POP_REVERB:
                return 50;

            case FILTER_HIGH_TO_LOW:
            case FILTER_LONG_HIGH_TO_LOW:
            case FILTER_SHORT_HIGH_TO_LOW:
            case FILTER_LONG_OUT_SHORT_IN:
            case FILTER_DYNAMIC_IN:
            case FILTER_LONG_OUT_SHORT_IN_DELAY:
            case FILTER_LONG_OUT_SHORT_IN_REVERB:
            case FILTER_SOFT_REVERB:
            case NO_VOCAL_CLASH:
                return 20;

            case BASS_SWAP:
                return 12;

            case DANCE_ANY_KEY:
            case DANCE_REPEATER:
                return 17;

            case CROSSFADE_LONG:
            case CROSSFADE_OVERLAP:
            case CROSSFADE_RINGOUT:
            case CROSSFADE_OVERLAP_REVERB:
            case CROSSFADE_RINGOUT_REVERB:
            case PLAIN_BLEND:
            case TAIL_RIDE:
                return 10;

            case GAPLESS:
            case SPLICE:
            case BEAT_CUT:
                return 1;

            case SHOW_MODE:
            case LEARNED_EQ:
            case AUTO:
            default:
                return BASE_OURS;
        }
    }

    public static Double score(MixStyle style, List<Double> factors, double positionD) {
        if (style == null || factors == null) {
            return null;
        }
        double product = 1.0;
        for (Double f : factors) {
            if (f == null || Double.isNaN(f)) {
                return null;
            }
            product *= f;
        }
        double p = base(style) * product;
        if (p <= 0.0) {

            return null;
        }
        return p + POSITION_WEIGHT * positionD;
    }

    public static MixStyle best(List<MixStyle> styles, List<List<Double>> factorSets,
                               List<Double> positions) {
        MixStyle pick = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int n = Math.min(styles.size(), Math.min(factorSets.size(), positions.size()));
        for (int i = 0; i < n; i++) {
            Double s = score(styles.get(i), factorSets.get(i), positions.get(i).doubleValue());
            if (s != null && s > bestScore) {
                bestScore = s;
                pick = styles.get(i);
            }
        }
        return pick;
    }
}
