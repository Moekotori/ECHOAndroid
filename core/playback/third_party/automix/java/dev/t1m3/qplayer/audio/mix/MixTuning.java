package dev.t1m3.qplayer.audio.mix;

import java.util.ArrayList;

public final class MixTuning {

    public static final float BASS_CROSSOVER_HZ = 200.0f;

    public static final float BASS_SWAP_SPLIT = 0.5f;

    public static final float BASS_SWAP_TOGETHER = 0.5f;

    public static final float CUT_FADE_SECS = 0.03f;

    public static final double MAX_WINDOW_FRACTION = (double) 0.7f;

    public static final float FILTER_SWEEP_FROM_HZ = 18000f;

    public static final float FILTER_SWEEP_TO_HZ = 400f;

    public static final float FILTER_SWEEP_Q = 0.707f;

    public static final float REVERB_SEND = 0.35f;

    public static final float REVERB_WET = 0.5f;

    public static final float REVERB_DECAY_SECS = 1.8f;

    public static final float REVERB_DAMPING = 0.35f;

    public static final float DELAY_SEND = 0.4f;

    public static final float DELAY_WET = 0.5f;

    public static final float DELAY_BEATS = 0.75f;

    public static final float DELAY_FEEDBACK = 0.45f;

    public static final float SHORT_IN_FRACTION = 0.5f;

    public static final boolean STYLE_LOOP_ENABLED = true;

    public static final float LOOP_CONFIRM_MIN_COST = 0.5f;

    public static final float DEFAULT_LEVEL_MATCH = 1.0f;

    public static final float DEFAULT_TEMPO_STRETCH = 1.0f;

    public static final float DEFAULT_LOW_DUCK = 1.0f;

    public static final float DEFAULT_SPACE_WIDTH = 0.5f;

    public static final float TAIL_RIDE_OUTRO_POWER = 2.0f;

    public static final float TAIL_RIDE_INTRO_POWER = 1.0f;

    public static final float SHOW_HP_FROM = 20.0f;

    public static final float SHOW_HP_TO = 4000.0f;

    public static final float SHOW_LP_FROM = 300.0f;

    public static final float SHOW_LP_TO = 18000.0f;

    public static final float SHOW_OUTRO_GAIN_POWER = 0.5f;

    public static final float SHOW_INTRO_GAIN_POWER = 0.6f;

    private static final String SEPARATORS = "[,; \n\t]";

    public float windowScale;

    public double maxWindowFraction;

    public float crossoverHz;

    public float bassSwapSplit;

    public float bassSwapTogether;

    public float cutFadeSecs;

    public float spaceWidth;

    public float levelMatch;

    public float lowDuck;

    public float tempoStretch;

    public float cutLeadSecs;

    public float tailRideOutroPower;

    public float tailRideIntroPower;

    public float filterSweepFromHz;

    public float filterSweepToHz;

    public float filterSweepQ;

    public float shortInFraction;

    public float loopConfirmMinCost;

    public boolean styleLoopEnabled;

    public float delaySend;

    public float delayWet;

    public float delayBeats;

    public float delayFeedback;

    public float reverbSend;

    public float reverbWet;

    public float reverbDecaySecs;

    public float reverbDamping;

    public float showHpFrom;

    public float showHpTo;

    public float showLpFrom;

    public float showLpTo;

    public float showOutroGainPower;

    public float showIntroGainPower;

    public float harmonyClarityMin;

    public float harmonyMatchMinScore;

    public float harmonyMinImprovement;

    public int harmonyMaxShift;

    public float autoTempoConflict;

    public float autoKeySame;

    public float lowClashGate;

    public float dropGate;

    public float minOverlapSecs;

    public float learnedEqMinWindowSecs;

    public float tailLongDbPerSec;

    public float headCalmGate;

    public MixTuning() {

        this.windowScale = 1.0f;
        this.maxWindowFraction = MAX_WINDOW_FRACTION;

        this.crossoverHz = BASS_CROSSOVER_HZ;
        this.bassSwapSplit = BASS_SWAP_SPLIT;
        this.bassSwapTogether = BASS_SWAP_TOGETHER;

        this.cutFadeSecs = CUT_FADE_SECS;
        this.cutLeadSecs = 0.0f;

        this.tailRideOutroPower = TAIL_RIDE_OUTRO_POWER;
        this.tailRideIntroPower = TAIL_RIDE_INTRO_POWER;

        this.shortInFraction = SHORT_IN_FRACTION;
        this.loopConfirmMinCost = LOOP_CONFIRM_MIN_COST;
        this.styleLoopEnabled = STYLE_LOOP_ENABLED;
        this.delaySend = DELAY_SEND;
        this.delayWet = DELAY_WET;
        this.delayBeats = DELAY_BEATS;
        this.delayFeedback = DELAY_FEEDBACK;
        this.reverbSend = REVERB_SEND;
        this.reverbWet = REVERB_WET;
        this.reverbDecaySecs = REVERB_DECAY_SECS;
        this.reverbDamping = REVERB_DAMPING;
        this.filterSweepFromHz = FILTER_SWEEP_FROM_HZ;
        this.filterSweepToHz = FILTER_SWEEP_TO_HZ;
        this.filterSweepQ = FILTER_SWEEP_Q;
        this.showHpFrom = SHOW_HP_FROM;
        this.showHpTo = SHOW_HP_TO;
        this.showLpFrom = SHOW_LP_FROM;
        this.showLpTo = SHOW_LP_TO;
        this.showOutroGainPower = SHOW_OUTRO_GAIN_POWER;
        this.showIntroGainPower = SHOW_INTRO_GAIN_POWER;

        this.harmonyClarityMin = Harmonic.CLARITY_MIN;
        this.harmonyMatchMinScore = Harmonic.MATCH_MIN_SCORE;
        this.harmonyMinImprovement = Harmonic.MIN_IMPROVEMENT;
        this.harmonyMaxShift = Harmonic.MAX_SHIFT;

        this.autoTempoConflict = 0.10f;

        this.autoKeySame = 0.70f;

        this.lowClashGate = 0.5f;
        this.dropGate = 0.25f;
        this.minOverlapSecs = 1.5f;
        this.learnedEqMinWindowSecs = 2.0f;
        this.tailLongDbPerSec = -3.0f;
        this.headCalmGate = 0.25f;

        this.spaceWidth = DEFAULT_SPACE_WIDTH;
        this.levelMatch = DEFAULT_LEVEL_MATCH;
        this.lowDuck = DEFAULT_LOW_DUCK;
        this.tempoStretch = DEFAULT_TEMPO_STRETCH;
    }

    public static MixTuning defaults() {
        return new MixTuning();
    }

    public MixTuning(MixTuning other) {
        this.windowScale = other.windowScale;
        this.maxWindowFraction = other.maxWindowFraction;

        this.crossoverHz = other.crossoverHz;
        this.bassSwapSplit = other.bassSwapSplit;
        this.bassSwapTogether = other.bassSwapTogether;

        this.cutFadeSecs = other.cutFadeSecs;
        this.cutLeadSecs = other.cutLeadSecs;

        this.spaceWidth = other.spaceWidth;
        this.levelMatch = other.levelMatch;
        this.lowDuck = other.lowDuck;
        this.tempoStretch = other.tempoStretch;

        this.tailRideOutroPower = other.tailRideOutroPower;
        this.tailRideIntroPower = other.tailRideIntroPower;

        this.shortInFraction = other.shortInFraction;
        this.loopConfirmMinCost = other.loopConfirmMinCost;
        this.styleLoopEnabled = other.styleLoopEnabled;
        this.delaySend = other.delaySend;
        this.delayWet = other.delayWet;
        this.delayBeats = other.delayBeats;
        this.delayFeedback = other.delayFeedback;
        this.reverbSend = other.reverbSend;
        this.reverbWet = other.reverbWet;
        this.reverbDecaySecs = other.reverbDecaySecs;
        this.reverbDamping = other.reverbDamping;
        this.filterSweepFromHz = other.filterSweepFromHz;
        this.filterSweepToHz = other.filterSweepToHz;
        this.filterSweepQ = other.filterSweepQ;
        this.showHpFrom = other.showHpFrom;
        this.showHpTo = other.showHpTo;
        this.showLpFrom = other.showLpFrom;
        this.showLpTo = other.showLpTo;
        this.showOutroGainPower = other.showOutroGainPower;
        this.showIntroGainPower = other.showIntroGainPower;

        this.harmonyClarityMin = other.harmonyClarityMin;
        this.harmonyMatchMinScore = other.harmonyMatchMinScore;
        this.harmonyMinImprovement = other.harmonyMinImprovement;
        this.harmonyMaxShift = other.harmonyMaxShift;

        this.autoTempoConflict = other.autoTempoConflict;
        this.autoKeySame = other.autoKeySame;
        this.lowClashGate = other.lowClashGate;
        this.dropGate = other.dropGate;
        this.minOverlapSecs = other.minOverlapSecs;
        this.learnedEqMinWindowSecs = other.learnedEqMinWindowSecs;
        this.tailLongDbPerSec = other.tailLongDbPerSec;
        this.headCalmGate = other.headCalmGate;
    }

    public static MixTuning parse(String spec) {
        MixTuning t = new MixTuning();

        for (String raw : spec.split(SEPARATORS)) {
            if (raw.isEmpty()) {
                continue;
            }
            int eq = raw.indexOf('=');
            if (eq < 0) {

                throw new TuningException(raw, "expected key=value");
            }
            String k = raw.substring(0, eq);
            String v = raw.substring(eq + 1).trim();

            String key = lastSegment(asciiLower(k.trim()).replace('-', '_'));

            switch (key) {
                case "window_scale":
                case "scale":
                    t.windowScale = parseF32(v, key);
                    break;
                case "max_window_fraction":
                case "max_window":

                    t.maxWindowFraction = (double) parseF32(v, key);
                    break;
                case "crossover":
                case "crossover_hz":
                    t.crossoverHz = parseF32(v, key);
                    break;
                case "split":
                case "bass_swap_split":
                    t.bassSwapSplit = parseF32(v, key);
                    break;
                case "together":
                case "bass_swap_together":
                    t.bassSwapTogether = parseF32(v, key);
                    break;
                case "cut_fade":
                case "cut_fade_secs":
                    t.cutFadeSecs = parseF32(v, key);
                    break;
                case "width":
                    t.spaceWidth = Math.max(0.0f, Math.min(1.0f, parseF32(v, key)));
                    break;
                case "level_match":
                case "levelMatch":
                    t.levelMatch = Math.max(0.0f, Math.min(1.0f, parseF32(v, key)));
                    break;
                case "low_duck":
                case "lowDuck":
                    t.lowDuck = Math.max(0.0f, Math.min(1.0f, parseF32(v, key)));
                    break;
                case "tempo_stretch":
                case "tempoStretch":
                    t.tempoStretch = Math.max(0.0f, Math.min(1.0f, parseF32(v, key)));
                    break;
                case "cut_lead":
                case "cut_lead_secs":
                    t.cutLeadSecs = parseF32(v, key);
                    break;
                case "tail_outro":
                case "tail_ride_outro_power":
                    t.tailRideOutroPower = parseF32(v, key);
                    break;
                case "tail_intro":
                case "tail_ride_intro_power":
                    t.tailRideIntroPower = parseF32(v, key);
                    break;
                case "loop_cost":
                case "loop_confirm_min_cost":
                    t.loopConfirmMinCost = parseF32(v, key);
                    break;
                case "short_in":
                case "short_in_fraction":
                    t.shortInFraction = parseF32(v, key);
                    break;
                case "delay_send":
                case "delay_wet":
                case "delay_beats":
                case "delay_feedback":
                    if ("delay_send".equals(key)) {
                        t.delaySend = parseF32(v, key);
                    } else if ("delay_wet".equals(key)) {
                        t.delayWet = parseF32(v, key);
                    } else if ("delay_beats".equals(key)) {
                        t.delayBeats = parseF32(v, key);
                    } else {
                        t.delayFeedback = parseF32(v, key);
                    }
                    break;
                case "reverb_send":
                case "reverb_wet":
                case "reverb_decay":
                case "reverb_damping":
                    if ("reverb_send".equals(key) || "reverb_wet".equals(key)) {
                        float rv = parseF32(v, key);
                        if ("reverb_send".equals(key)) {
                            t.reverbSend = rv;
                        } else {
                            t.reverbWet = rv;
                        }
                    } else if ("reverb_decay".equals(key)) {
                        t.reverbDecaySecs = parseF32(v, key);
                    } else {
                        t.reverbDamping = parseF32(v, key);
                    }
                    break;
                case "sweep_from":
                case "filter_sweep_from_hz":
                    t.filterSweepFromHz = parseF32(v, key);
                    break;
                case "sweep_to":
                case "filter_sweep_to_hz":
                    t.filterSweepToHz = parseF32(v, key);
                    break;
                case "sweep_q":
                case "filter_sweep_q":
                    t.filterSweepQ = parseF32(v, key);
                    break;
                case "hp_from":
                case "show_hp_from":
                    t.showHpFrom = parseF32(v, key);
                    break;
                case "hp_to":
                case "show_hp_to":
                    t.showHpTo = parseF32(v, key);
                    break;
                case "lp_from":
                case "show_lp_from":
                    t.showLpFrom = parseF32(v, key);
                    break;
                case "lp_to":
                case "show_lp_to":
                    t.showLpTo = parseF32(v, key);
                    break;
                case "outro_gain":
                case "show_outro_gain_power":
                    t.showOutroGainPower = parseF32(v, key);
                    break;
                case "intro_gain":
                case "show_intro_gain_power":
                    t.showIntroGainPower = parseF32(v, key);
                    break;
                case "clarity_min":
                case "harmony_clarity_min":
                    t.harmonyClarityMin = parseF32(v, key);
                    break;
                case "match_min_score":
                case "harmony_match_min_score":
                    t.harmonyMatchMinScore = parseF32(v, key);
                    break;
                case "min_improvement":
                case "harmony_min_improvement":
                    t.harmonyMinImprovement = parseF32(v, key);
                    break;
                case "auto_tempo_conflict":
                case "tempo_conflict":
                    t.autoTempoConflict = parseF32(v, key);
                    break;
                case "auto_key_same":
                case "key_same":
                    t.autoKeySame = parseF32(v, key);
                    break;

                case "low_clash_gate":
                    t.lowClashGate = parseF32(v, key);
                    break;
                case "drop_gate":
                    t.dropGate = parseF32(v, key);
                    break;
                case "min_overlap_secs":
                    t.minOverlapSecs = parseF32(v, key);
                    break;
                case "learned_eq_min_window_secs":
                    t.learnedEqMinWindowSecs = parseF32(v, key);
                    break;
                case "tail_long_db_per_sec":
                    t.tailLongDbPerSec = parseF32(v, key);
                    break;
                case "head_calm_gate":
                    t.headCalmGate = parseF32(v, key);
                    break;
                case "max_shift":
                case "harmony_max_shift":

                    t.harmonyMaxShift = parseI32(v, key);
                    break;
                default:

                    throw new TuningException(key, "no such tuning key");
            }
        }
        t.check();
        return t;
    }

    public void check() {
        if (!(windowScale > 0.0f && windowScale <= 8.0f)) {
            throw bad("window_scale", "must be within (0, 8]");
        }
        if (!(maxWindowFraction > 0.0 && maxWindowFraction <= 1.0)) {
            throw bad("max_window_fraction", "must be within (0, 1]");
        }
        if (!(crossoverHz > 0.0f && crossoverHz < 20000.0f)) {
            throw bad("crossover", "must be within (0, 20000) Hz");
        }
        if (!(bassSwapSplit >= 0.0f && bassSwapSplit < 1.0f)) {
            throw bad("split", "must be within [0, 1)");
        }
        if (!(bassSwapTogether >= 0.0f && bassSwapTogether <= 0.9f)) {

            throw bad("together", "must be within [0, 0.9]");
        }
        if (!(cutFadeSecs > 0.0f && cutFadeSecs <= 1.0f)) {
            throw bad("cut_fade", "must be within (0, 1] seconds");
        }
        if (!(cutLeadSecs >= 0.0f && cutLeadSecs <= 1.0f)) {
            throw bad("cut_lead", "must be within [0, 1] seconds");
        }
        if (!(tailRideOutroPower > 0.0f) || !(tailRideIntroPower > 0.0f)) {
            throw bad("tail_outro/tail_intro", "the exponent must be positive");
        }
        if (!(showHpFrom > 0.0f && showHpTo > showHpFrom)) {
            throw bad("hp_from/hp_to", "the end must be above the start, and both must be positive");
        }
        if (!(showLpFrom > 0.0f && showLpTo > showLpFrom)) {
            throw bad("lp_from/lp_to", "the end must be above the start, and both must be positive");
        }
        if (!(showOutroGainPower > 0.0f) || !(showIntroGainPower > 0.0f)) {
            throw bad("outro_gain/intro_gain", "the exponent must be positive");
        }
        if (!(harmonyClarityMin >= 0.0f && harmonyClarityMin <= 1.0f)) {
            throw bad("clarity_min", "must be within [0, 1]");
        }
        if (!(harmonyMaxShift >= 0 && harmonyMaxShift <= 12)) {
            throw bad("max_shift", "must be within 0..=12 semitones");
        }
        if (!(autoTempoConflict > 0.0f && autoTempoConflict <= 1.0f)) {
            throw bad("auto_tempo_conflict", "must be within (0, 1]");
        }
        if (!(autoKeySame >= -1.0f && autoKeySame <= 1.0f)) {
            throw bad("auto_key_same", "must be within [-1, 1] (it is a cosine similarity)");
        }
    }

    public String toLine() {
        ArrayList<String> parts = new ArrayList<String>();
        parts.add("window_scale=" + displayF32(windowScale));
        parts.add("max_window_fraction=" + displayF64(maxWindowFraction));
        parts.add("crossover=" + displayF32(crossoverHz));
        parts.add("split=" + displayF32(bassSwapSplit));
        parts.add("together=" + displayF32(bassSwapTogether));
        parts.add("cut_fade=" + displayF32(cutFadeSecs));
        parts.add("cut_lead=" + displayF32(cutLeadSecs));
        parts.add("tail_outro=" + displayF32(tailRideOutroPower));
        parts.add("tail_intro=" + displayF32(tailRideIntroPower));
        parts.add("loop_cost=" + displayF32(loopConfirmMinCost));
        parts.add("short_in=" + displayF32(shortInFraction));
        parts.add("delay_send=" + displayF32(delaySend));
        parts.add("delay_wet=" + displayF32(delayWet));
        parts.add("delay_beats=" + displayF32(delayBeats));
        parts.add("delay_feedback=" + displayF32(delayFeedback));
        parts.add("reverb_send=" + displayF32(reverbSend));
        parts.add("reverb_wet=" + displayF32(reverbWet));
        parts.add("reverb_decay=" + displayF32(reverbDecaySecs));
        parts.add("reverb_damping=" + displayF32(reverbDamping));
        parts.add("sweep_from=" + displayF32(filterSweepFromHz));
        parts.add("sweep_to=" + displayF32(filterSweepToHz));
        parts.add("sweep_q=" + displayF32(filterSweepQ));
        parts.add("hp_from=" + displayF32(showHpFrom));
        parts.add("hp_to=" + displayF32(showHpTo));
        parts.add("lp_from=" + displayF32(showLpFrom));
        parts.add("lp_to=" + displayF32(showLpTo));
        parts.add("outro_gain=" + displayF32(showOutroGainPower));
        parts.add("intro_gain=" + displayF32(showIntroGainPower));
        parts.add("clarity_min=" + displayF32(harmonyClarityMin));
        parts.add("match_min_score=" + displayF32(harmonyMatchMinScore));
        parts.add("min_improvement=" + displayF32(harmonyMinImprovement));
        parts.add("max_shift=" + harmonyMaxShift);
        parts.add("auto_tempo_conflict=" + displayF32(autoTempoConflict));
        parts.add("auto_key_same=" + displayF32(autoKeySame));

        parts.add("low_clash_gate=" + displayF32(lowClashGate));
        parts.add("drop_gate=" + displayF32(dropGate));
        parts.add("min_overlap_secs=" + displayF32(minOverlapSecs));
        parts.add("learned_eq_min_window_secs=" + displayF32(learnedEqMinWindowSecs));
        parts.add("tail_long_db_per_sec=" + displayF32(tailLongDbPerSec));
        parts.add("head_calm_gate=" + displayF32(headCalmGate));

        parts.add("width=" + displayF32(spaceWidth));
        parts.add("level_match=" + displayF32(levelMatch));
        parts.add("low_duck=" + displayF32(lowDuck));
        parts.add("tempo_stretch=" + displayF32(tempoStretch));

        ArrayList<String> uniq = new ArrayList<String>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            if (i == 0 || !p.equals(parts.get(i - 1))) {
                uniq.add(p);
            }
        }
        return String.join(",", uniq);
    }

    public float beatsFor(MixStyle style) {
        return style.beats() * windowScale;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MixTuning)) {
            return false;
        }
        MixTuning m = (MixTuning) o;
        return Float.compare(m.windowScale, windowScale) == 0
                && Double.compare(m.maxWindowFraction, maxWindowFraction) == 0
                && Float.compare(m.crossoverHz, crossoverHz) == 0
                && Float.compare(m.bassSwapSplit, bassSwapSplit) == 0
                && Float.compare(m.bassSwapTogether, bassSwapTogether) == 0
                && Float.compare(m.cutFadeSecs, cutFadeSecs) == 0
                && Float.compare(m.cutLeadSecs, cutLeadSecs) == 0
                && Float.compare(m.tailRideOutroPower, tailRideOutroPower) == 0
                && Float.compare(m.tailRideIntroPower, tailRideIntroPower) == 0
                && Float.compare(m.loopConfirmMinCost, loopConfirmMinCost) == 0
                && Float.compare(m.shortInFraction, shortInFraction) == 0
                && Float.compare(m.delaySend, delaySend) == 0
                && Float.compare(m.delayWet, delayWet) == 0
                && Float.compare(m.delayBeats, delayBeats) == 0
                && Float.compare(m.delayFeedback, delayFeedback) == 0
                && Float.compare(m.reverbSend, reverbSend) == 0
                && Float.compare(m.reverbWet, reverbWet) == 0
                && Float.compare(m.reverbDecaySecs, reverbDecaySecs) == 0
                && Float.compare(m.reverbDamping, reverbDamping) == 0
                && Float.compare(m.filterSweepFromHz, filterSweepFromHz) == 0
                && Float.compare(m.filterSweepToHz, filterSweepToHz) == 0
                && Float.compare(m.filterSweepQ, filterSweepQ) == 0
                && Float.compare(m.showHpFrom, showHpFrom) == 0
                && Float.compare(m.showHpTo, showHpTo) == 0
                && Float.compare(m.showLpFrom, showLpFrom) == 0
                && Float.compare(m.showLpTo, showLpTo) == 0
                && Float.compare(m.showOutroGainPower, showOutroGainPower) == 0
                && Float.compare(m.showIntroGainPower, showIntroGainPower) == 0
                && Float.compare(m.harmonyClarityMin, harmonyClarityMin) == 0
                && Float.compare(m.harmonyMatchMinScore, harmonyMatchMinScore) == 0
                && Float.compare(m.harmonyMinImprovement, harmonyMinImprovement) == 0
                && m.harmonyMaxShift == harmonyMaxShift
                && Float.compare(m.autoTempoConflict, autoTempoConflict) == 0
                && Float.compare(m.autoKeySame, autoKeySame) == 0
                && Float.compare(m.lowClashGate, lowClashGate) == 0
                && Float.compare(m.dropGate, dropGate) == 0
                && Float.compare(m.minOverlapSecs, minOverlapSecs) == 0
                && Float.compare(m.learnedEqMinWindowSecs, learnedEqMinWindowSecs) == 0
                && Float.compare(m.tailLongDbPerSec, tailLongDbPerSec) == 0
                && Float.compare(m.headCalmGate, headCalmGate) == 0
                && Float.compare(m.spaceWidth, spaceWidth) == 0
                && Float.compare(m.levelMatch, levelMatch) == 0
                && Float.compare(m.lowDuck, lowDuck) == 0
                && Float.compare(m.tempoStretch, tempoStretch) == 0;
    }

    @Override
    public int hashCode() {
        int h = 1;
        h = 31 * h + Float.floatToIntBits(windowScale);
        h = 31 * h + (int) (Double.doubleToLongBits(maxWindowFraction) ^ (Double.doubleToLongBits(maxWindowFraction) >>> 32));
        h = 31 * h + Float.floatToIntBits(crossoverHz);
        h = 31 * h + Float.floatToIntBits(bassSwapSplit);
        h = 31 * h + Float.floatToIntBits(bassSwapTogether);
        h = 31 * h + Float.floatToIntBits(cutFadeSecs);
        h = 31 * h + Float.floatToIntBits(cutLeadSecs);
        h = 31 * h + Float.floatToIntBits(tailRideOutroPower);
        h = 31 * h + Float.floatToIntBits(tailRideIntroPower);
        h = 31 * h + Float.floatToIntBits(loopConfirmMinCost);
        h = 31 * h + Float.floatToIntBits(shortInFraction);
        h = 31 * h + Float.floatToIntBits(delaySend);
        h = 31 * h + Float.floatToIntBits(delayWet);
        h = 31 * h + Float.floatToIntBits(delayBeats);
        h = 31 * h + Float.floatToIntBits(delayFeedback);
        h = 31 * h + Float.floatToIntBits(reverbSend);
        h = 31 * h + Float.floatToIntBits(reverbWet);
        h = 31 * h + Float.floatToIntBits(reverbDecaySecs);
        h = 31 * h + Float.floatToIntBits(reverbDamping);
        h = 31 * h + Float.floatToIntBits(filterSweepFromHz);
        h = 31 * h + Float.floatToIntBits(filterSweepToHz);
        h = 31 * h + Float.floatToIntBits(filterSweepQ);
        h = 31 * h + Float.floatToIntBits(showHpFrom);
        h = 31 * h + Float.floatToIntBits(showHpTo);
        h = 31 * h + Float.floatToIntBits(showLpFrom);
        h = 31 * h + Float.floatToIntBits(showLpTo);
        h = 31 * h + Float.floatToIntBits(showOutroGainPower);
        h = 31 * h + Float.floatToIntBits(showIntroGainPower);
        h = 31 * h + Float.floatToIntBits(harmonyClarityMin);
        h = 31 * h + Float.floatToIntBits(harmonyMatchMinScore);
        h = 31 * h + Float.floatToIntBits(harmonyMinImprovement);
        h = 31 * h + harmonyMaxShift;
        h = 31 * h + Float.floatToIntBits(autoTempoConflict);
        h = 31 * h + Float.floatToIntBits(autoKeySame);
        h = 31 * h + Float.floatToIntBits(lowClashGate);
        h = 31 * h + Float.floatToIntBits(dropGate);
        h = 31 * h + Float.floatToIntBits(minOverlapSecs);
        h = 31 * h + Float.floatToIntBits(learnedEqMinWindowSecs);
        h = 31 * h + Float.floatToIntBits(tailLongDbPerSec);
        h = 31 * h + Float.floatToIntBits(headCalmGate);
        h = 31 * h + Float.floatToIntBits(spaceWidth);
        h = 31 * h + Float.floatToIntBits(levelMatch);
        h = 31 * h + Float.floatToIntBits(lowDuck);
        h = 31 * h + Float.floatToIntBits(tempoStretch);
        return h;
    }

    @Override
    public String toString() {
        return "MixTuning { window_scale: " + windowScale
                + ", max_window_fraction: " + maxWindowFraction
                + ", crossover_hz: " + crossoverHz
                + ", bass_swap_split: " + bassSwapSplit
                + ", bass_swap_together: " + bassSwapTogether
                + ", cut_fade_secs: " + cutFadeSecs
                + ", cut_lead_secs: " + cutLeadSecs
                + ", tail_ride_outro_power: " + tailRideOutroPower
                + ", tail_ride_intro_power: " + tailRideIntroPower
                + ", loop_cost: " + loopConfirmMinCost
                + ", short_in: " + shortInFraction
                + ", delay_send: " + delaySend
                + ", delay_wet: " + delayWet
                + ", delay_beats: " + delayBeats
                + ", delay_feedback: " + delayFeedback
                + ", reverb_send: " + reverbSend
                + ", reverb_wet: " + reverbWet
                + ", reverb_decay: " + reverbDecaySecs
                + ", reverb_damping: " + reverbDamping
                + ", sweep_from: " + filterSweepFromHz
                + ", sweep_to: " + filterSweepToHz
                + ", sweep_q: " + filterSweepQ
                + ", show_hp_from: " + showHpFrom
                + ", show_hp_to: " + showHpTo
                + ", show_lp_from: " + showLpFrom
                + ", show_lp_to: " + showLpTo
                + ", show_outro_gain_power: " + showOutroGainPower
                + ", show_intro_gain_power: " + showIntroGainPower
                + ", harmony_clarity_min: " + harmonyClarityMin
                + ", harmony_match_min_score: " + harmonyMatchMinScore
                + ", harmony_min_improvement: " + harmonyMinImprovement
                + ", harmony_max_shift: " + harmonyMaxShift
                + ", auto_tempo_conflict: " + autoTempoConflict
                + ", auto_key_same: " + autoKeySame
                + ", lowClashGate: " + lowClashGate
                + ", dropGate: " + dropGate
                + ", minOverlapSecs: " + minOverlapSecs
                + ", learnedEqMinWindowSecs: " + learnedEqMinWindowSecs
                + ", tailLongDbPerSec: " + tailLongDbPerSec
                + ", headCalmGate: " + headCalmGate + " }";
    }

    private static TuningException bad(String key, String why) {
        return new TuningException(key, why);
    }

    private static String lastSegment(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? key : key.substring(dot + 1);
    }

    private static String asciiLower(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= 'A' && c <= 'Z' ? (char) (c + 32) : c);
        }
        return b.toString();
    }

    private static float parseF32(String v, String key) {
        int n = v.length();
        int i = 0;
        boolean neg = false;
        if (i < n && (v.charAt(i) == '+' || v.charAt(i) == '-')) {
            neg = v.charAt(i) == '-';
            i++;
        }
        if (i >= n) {
            throw new TuningException(key, "expected a number, got " + v);
        }

        String lower = asciiLower(v.substring(i));
        if (lower.equals("inf") || lower.equals("infinity")) {
            return neg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        }
        if (lower.equals("nan")) {
            return Float.NaN;
        }

        int j = i;
        int digits = 0;
        while (j < n && isAsciiDigit(v.charAt(j))) {
            j++;
            digits++;
        }
        if (j < n && v.charAt(j) == '.') {
            j++;
            while (j < n && isAsciiDigit(v.charAt(j))) {
                j++;
                digits++;
            }
        }
        if (digits == 0) {
            throw new TuningException(key, "expected a number, got " + v);
        }
        if (j < n && (v.charAt(j) == 'e' || v.charAt(j) == 'E')) {
            j++;
            if (j < n && (v.charAt(j) == '+' || v.charAt(j) == '-')) {
                j++;
            }
            int expDigits = 0;
            while (j < n && isAsciiDigit(v.charAt(j))) {
                j++;
                expDigits++;
            }
            if (expDigits == 0) {
                throw new TuningException(key, "expected a number, got " + v);
            }
        }
        if (j != n) {

            throw new TuningException(key, "expected a number, got " + v);
        }
        return Float.parseFloat(v);
    }

    private static int parseI32(String v, String key) {
        int n = v.length();
        int i = 0;
        if (i < n && (v.charAt(i) == '+' || v.charAt(i) == '-')) {
            i++;
        }
        if (i >= n) {
            throw new TuningException(key, "expected a whole number of semitones, got " + v);
        }
        for (int j = i; j < n; j++) {
            if (!isAsciiDigit(v.charAt(j))) {
                throw new TuningException(key, "expected a whole number of semitones, got " + v);
            }
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new TuningException(key, "expected a whole number of semitones, got " + v);
        }
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static String displayF32(float v) {
        String s = Float.toString(v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static String displayF64(double v) {
        String s = Double.toString(v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
