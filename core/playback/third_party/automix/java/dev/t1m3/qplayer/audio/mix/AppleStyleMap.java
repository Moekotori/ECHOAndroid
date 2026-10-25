package dev.t1m3.qplayer.audio.mix;

public final class AppleStyleMap {

    public static final int NONE = -1;

    private AppleStyleMap() {
    }

    public static int appleId(MixStyle style) {
        if (style == null) {
            return NONE;
        }
        switch (style) {
            case GAPLESS:                       return 0;
            case PLAIN_BLEND:                   return 1;
            case CROSSFADE_LONG:                return 2;
            case CROSSFADE_OVERLAP:             return 3;
            case CROSSFADE_RINGOUT:             return 4;
            case FILTER_SHORT_HIGH_TO_LOW:      return 5;
            case FILTER_LONG_OUT_SHORT_IN:      return 6;
            case FILTER_DYNAMIC_IN:             return 7;
            case FILTER_HIGH_TO_LOW:            return 8;
            case FILTER_EXPANSION:              return 9;
            case FILTER_LONG_OUT_SHORT_IN_DELAY:    return 10;
            case FILTER_LONG_OUT_SHORT_IN_REVERB:   return 11;
            case FILTER_LONG_HIGH_TO_LOW:       return 12;
            case FILTER_SOFT_REVERB:            return 13;
            case NO_VOCAL_CLASH:                return 14;
            case HIPHOP_DELAY_OUT:              return 15;
            case POP_DELAY_OUT:                 return 16;
            case DANCE_REPEATER:                return 17;
            case HIPHOP_LOOPING:                return 18;
            case DANCE_ANY_KEY:                 return 19;
            case POP_REVERB:                    return 20;
            case SPLICE:                        return 22;
            case CROSSFADE_OVERLAP_REVERB:      return 33;
            case CROSSFADE_RINGOUT_REVERB:      return 44;
            default:                            return NONE;

        }
    }

    public static AppleTransitionSpec.Auto lane(AppleTransitionSpec.Style spec, boolean outgoing,
                                                String key) {
        if (spec == null) {
            return null;
        }
        AppleTransitionSpec.Auto[] list = outgoing ? spec.outgoing : spec.incoming;
        int n = 0;
        for (AppleTransitionSpec.Auto x : list) {
            if (key.equals(x.key)) {
                n += x.segs.length;
            }
        }
        if (n == 0) {
            return null;
        }
        AppleTransitionSpec.Seg[] segs = new AppleTransitionSpec.Seg[n];
        int at = 0;
        for (AppleTransitionSpec.Auto x : list) {
            if (key.equals(x.key)) {
                for (AppleTransitionSpec.Seg seg : x.segs) {
                    segs[at++] = seg;
                }
            }
        }
        return AppleTransitionSpec.merged(key, segs);
    }

    public static AppleTransitionSpec.Style byAppleId(int id) {
        for (AppleTransitionSpec.Style s : AppleTransitionSpec.STYLES) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    public static AppleTransitionSpec.Style specFor(MixStyle style) {
        int id = appleId(style);
        return id == NONE ? null : byAppleId(id);
    }
}
