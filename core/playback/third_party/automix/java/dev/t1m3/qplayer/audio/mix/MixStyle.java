package dev.t1m3.qplayer.audio.mix;

public enum MixStyle {

    PLAIN_BLEND("plain blend", "plainBlend", 8f, 4f),

    BEAT_CUT("beat cut", "beatCut", 0f, 0.05f),

    BASS_SWAP("bass handover", "bassSwap", 16f, 8f),

    TAIL_RIDE("tail riding", "tailRide", 24f, 12f),

    SHOW_MODE("performance mode", "buildUp", 32f, 16f),

    LEARNED_EQ("learned EQ", "learnedEq", 8f, 4f),

    CROSSFADE_LONG("crossfade long", "crossfadeLong", 16f, 8f),

    CROSSFADE_OVERLAP("crossfade overlap", "crossfadeOverlap", 8f, 4f),

    CROSSFADE_RINGOUT("crossfade tail", "crossfadeRingout", 16f, 8f),

    CROSSFADE_OVERLAP_REVERB("crossfade overlap + reverb", "crossfadeOverlapReverbPendingFx", 8f, 4f),

    CROSSFADE_RINGOUT_REVERB("crossfade tail + reverb", "crossfadeRingoutReverbPendingFx", 16f, 8f),

    FILTER_HIGH_TO_LOW("filter high to low", "filterHighToLow", 16f, 8f),

    FILTER_LONG_HIGH_TO_LOW("filter long high to low", "filterLongHighToLow", 32f, 16f),

    FILTER_SHORT_HIGH_TO_LOW("filter short high to low", "filterShortHighToLow", 8f, 4f),

    FILTER_EXPANSION("filter expansion", "filterExpansion", 16f, 8f),

    FILTER_LONG_OUT_SHORT_IN("filter long out short in", "filterLongOutShortIn", 16f, 8f),

    FILTER_DYNAMIC_IN("filter dynamic in", "filterDynamicIn", 16f, 8f),

    FILTER_LONG_OUT_SHORT_IN_DELAY("filter long out short in + delay", "filterLongOutShortInDelayPendingFx", 16f, 8f),

    FILTER_LONG_OUT_SHORT_IN_REVERB("filter long out short in + reverb", "filterLongOutShortInReverbPendingFx", 16f, 8f),

    FILTER_SOFT_REVERB("filter soft + reverb", "filterSoftReverbPendingFx", 16f, 8f),

    NO_VOCAL_CLASH("no vocal clash", "noVocalClash", 16f, 8f),

    HIPHOP_DELAY_OUT("hip-hop delayed out", "hiphopDelayOutPendingFx", 16f, 8f),

    HIPHOP_LOOPING("hip-hop loop", "hiphopLooping", 8f, 4f),

    POP_DELAY_OUT("pop delayed out", "popDelayOutPendingFx", 16f, 8f),

    POP_REVERB("pop reverb", "popReverbPendingFx", 16f, 8f),

    DANCE_ANY_KEY("dance any key", "danceAnyKey", 16f, 8f),

    DANCE_REPEATER("dance repeater", "danceRepeater", 8f, 4f),

    GAPLESS("seamless join", "gapless", 0f, 0.05f),

    SPLICE("join", "splice", 0f, 0.05f),

    AUTO("auto", "auto", 8f, 4f);

    public static final MixStyle DEFAULT = PLAIN_BLEND;

    public static final MixStyle[] ALL = {

            CROSSFADE_LONG, CROSSFADE_OVERLAP, CROSSFADE_RINGOUT, CROSSFADE_OVERLAP_REVERB,
            CROSSFADE_RINGOUT_REVERB, FILTER_HIGH_TO_LOW, FILTER_LONG_HIGH_TO_LOW, FILTER_SHORT_HIGH_TO_LOW,
            FILTER_EXPANSION, FILTER_LONG_OUT_SHORT_IN, FILTER_DYNAMIC_IN, FILTER_LONG_OUT_SHORT_IN_DELAY,
            FILTER_LONG_OUT_SHORT_IN_REVERB, FILTER_SOFT_REVERB, NO_VOCAL_CLASH, HIPHOP_DELAY_OUT,
            HIPHOP_LOOPING, POP_DELAY_OUT, POP_REVERB, DANCE_ANY_KEY,
            DANCE_REPEATER, GAPLESS, SPLICE,
            PLAIN_BLEND, BEAT_CUT, BASS_SWAP, TAIL_RIDE, SHOW_MODE,

    };

    public static final MixStyle[] UI_ORDER = {

            AUTO, PLAIN_BLEND, BEAT_CUT, BASS_SWAP, TAIL_RIDE, SHOW_MODE, LEARNED_EQ,

            CROSSFADE_LONG, CROSSFADE_OVERLAP, CROSSFADE_RINGOUT, CROSSFADE_OVERLAP_REVERB,
            CROSSFADE_RINGOUT_REVERB, FILTER_HIGH_TO_LOW, FILTER_LONG_HIGH_TO_LOW, FILTER_SHORT_HIGH_TO_LOW,
            FILTER_EXPANSION, FILTER_LONG_OUT_SHORT_IN, FILTER_DYNAMIC_IN, FILTER_LONG_OUT_SHORT_IN_DELAY,
            FILTER_LONG_OUT_SHORT_IN_REVERB, FILTER_SOFT_REVERB, NO_VOCAL_CLASH, HIPHOP_DELAY_OUT,
            HIPHOP_LOOPING, POP_DELAY_OUT, POP_REVERB, DANCE_ANY_KEY,
            DANCE_REPEATER, GAPLESS, SPLICE
    };

    private final String label;
    private final String specName;
    private final float beats;
    private final float fallbackSecs;

    MixStyle(String label, String specName, float beats, float fallbackSecs) {
        this.label = label;
        this.specName = specName;
        this.beats = beats;
        this.fallbackSecs = fallbackSecs;
    }

    public String label() {
        return label;
    }

    public String specName() {
        return specName;
    }

    public float beats() {
        return beats;
    }

    public float fallbackSecs() {
        return fallbackSecs;
    }

    public boolean rewritesSamples() {
        return true;
    }

    public boolean isAuto() {
        return this == AUTO;
    }

    public int uiIndex() {
        for (int i = 0; i < UI_ORDER.length; i++) {
            if (UI_ORDER[i] == this) {
                return i;
            }
        }
        return 0;
    }

    public static MixStyle fromUiIndex(int index) {
        if (index < 0 || index >= UI_ORDER.length) {
            return AUTO;
        }
        final MixStyle picked = UI_ORDER[index];

        return picked == NO_VOCAL_CLASH ? AUTO : picked;
    }
}
