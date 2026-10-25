package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.dsp.Lufs;
import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.AudioException;
import dev.t1m3.qplayer.audio.dsp.Biquad;
import dev.t1m3.qplayer.audio.dsp.Delay;
import dev.t1m3.qplayer.audio.dsp.Reverb;
import dev.t1m3.qplayer.audio.dsp.SplitLowHigh;
import dev.t1m3.qplayer.audio.model.BeatDetector;
import dev.t1m3.qplayer.audio.model.BeatModels;
import dev.t1m3.qplayer.audio.model.MelParams;
import dev.t1m3.qplayer.util.Logger;

import java.nio.FloatBuffer;
import java.util.Locale;

public final class AiMix implements Transition {

    private static volatile String autoAllowedStyleNames = "";

    public static void setAutoAllowedStyleNames(String names) {
        autoAllowedStyleNames = names == null ? "" : names.trim();
    }

    public static String autoAllowedStyleNames() {
        return autoAllowedStyleNames;
    }

    private static MixStyle whitelisted(MixStyle picked) {
        String allow = autoAllowedStyleNames;
        if (picked == null || allow == null || allow.isEmpty()) {
            return picked;
        }
        String first = null;
        for (String raw : allow.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (first == null) {
                first = name;
            }
            if (name.equals(picked.name())) {
                return picked;
            }
        }
        if (first == null) {
            return picked;
        }
        for (MixStyle m : MixStyle.values()) {
            if (m.name().equals(first)) {
                Logger.info("auto style: whitelist applied - originally {} (not enabled) -> switched to {} (first in whitelist)",
                        picked, m);
                return m;
            }
        }
        return picked;
    }

    private static volatile boolean modelMissingReported;

    private static final float FRAC_PI_2 = (float) (Math.PI / 2.0);

    private MixStyle style;

    private final java.util.function.DoubleSupplier harmonicSecs;

    private Transition harmonicBlend;

    private TransitionPlan harmonicPlan;

    private AutoCue.Cue harmonicCue;

    private AutoCue.Cue autoCue;

    private static final class ModeHolder {
        TransitionPlan plan;
    }

    private final int rate;

    private final int channels;

    long window;
    private long hostWindowLimit = Long.MAX_VALUE;

    /** Android host PCM budget, configured before prepare. */
    public void setWindowLimit(long frames) { hostWindowLimit = Math.max(1L, frames); }


    private Analysis.Tempo outroTempo;

    private Analysis.Tempo introTempo;

    private double outroTailSecs;

    private int introRate;

    float cutAt;

    private Float cutForcedAt;

    private String autoNote;

    private TempoSource outroSource;

    private TempoSource introSource;

    private final MixTuning tuning;

    private final LevelMatcher levelMatcher;

    private final LowDucker lowDucker;

    private final SplitLowHigh[] fromSplit;

    private final SplitLowHigh[] toSplit;

    private final SplitLowHigh[] toSplit2;

    private static final MixStyle[] ROUTE_CANDIDATES = {
            MixStyle.HIPHOP_LOOPING, MixStyle.HIPHOP_DELAY_OUT,
            MixStyle.FILTER_EXPANSION, MixStyle.POP_DELAY_OUT, MixStyle.POP_REVERB,
            MixStyle.FILTER_HIGH_TO_LOW, MixStyle.FILTER_LONG_HIGH_TO_LOW,
            MixStyle.FILTER_SHORT_HIGH_TO_LOW, MixStyle.FILTER_SOFT_REVERB,

            MixStyle.BASS_SWAP,

            MixStyle.CROSSFADE_OVERLAP_REVERB, MixStyle.CROSSFADE_RINGOUT_REVERB,
            MixStyle.FILTER_LONG_OUT_SHORT_IN, MixStyle.FILTER_DYNAMIC_IN,
            MixStyle.FILTER_LONG_OUT_SHORT_IN_DELAY, MixStyle.FILTER_LONG_OUT_SHORT_IN_REVERB,
            MixStyle.DANCE_ANY_KEY, MixStyle.DANCE_REPEATER,
            MixStyle.CROSSFADE_LONG, MixStyle.CROSSFADE_OVERLAP, MixStyle.CROSSFADE_RINGOUT,
            MixStyle.PLAIN_BLEND, MixStyle.TAIL_RIDE,
            MixStyle.GAPLESS, MixStyle.SPLICE, MixStyle.BEAT_CUT,
            MixStyle.SHOW_MODE, MixStyle.LEARNED_EQ,
    };

    private MaterialProfile autoProfile;

    private Analysis.Tempo lastTempoOut;

    private Analysis.Tempo lastTempoIn;

    public MaterialProfile autoProfile() {
        return autoProfile;
    }

    public Analysis.Tempo tempoOut() {
        return lastTempoOut;
    }

    public Analysis.Tempo tempoIn() {
        return lastTempoIn;
    }

    private static final float MAX_MODEL_BPM_GAP = 0.20f;

    private static Analysis.Tempo saneTempo(Analysis.Tempo model, Analysis.Tempo hard) {
        if (model == null || hard == null || model.bpm <= 0f || hard.bpm <= 0f) {
            return model;
        }
        float gap = Math.abs(model.bpm - hard.bpm)
                / Math.max((model.bpm + hard.bpm) * 0.5f, 1.0f);
        return gap > MAX_MODEL_BPM_GAP ? hard : model;
    }

    public String autoNote() {
        return autoNote;
    }

    private EqCurve learnedEq;

    private final float[] eqGains = new float[3];

    private final Biquad[] fromHp;

    private final Biquad[] toLp;

    private final Biquad[] sweepOut;

    private Reverb reverb;

    private final Delay delay;

    private final boolean delayRoute;

    private static final float DELAY_DAMPING = 0.35f;

    private double beatSecs = 0.5;

    private final boolean asymRoute;

    private final boolean reverbRoute;

    private float sweepHz = -1f;

    private final dev.t1m3.qplayer.audio.core.AudioBuf spaceScratch;

    static final float SPACE_MAX_SHIFT = 0.4f;

    static final double SPACE_MONO_RATIO = 0.02;

    public AiMix(MixStyle style, int rate, int channels) {
        this(style, rate, channels, MixTuning.defaults());
    }

    public AiMix(MixStyle style, int rate, int channels, MixTuning tuning) {
        this(style, rate, channels, tuning, null);
    }

    public AiMix(MixStyle style, int rate, int channels, MixTuning tuning,
                 java.util.function.DoubleSupplier harmonicSecs) {
        int r = Math.max(rate, 1);
        int ch = Math.max(channels, 1);
        this.harmonicSecs = harmonicSecs;
        this.style = style;
        this.rate = r;
        this.channels = ch;
        this.tuning = new MixTuning(tuning);

        this.levelMatcher = new LevelMatcher(r);
        this.lowDucker = this.tuning.lowDuck > 0.0f
                ? new LowDucker(r, ch, this.tuning.crossoverHz) : null;

        this.spaceScratch = this.tuning.spaceWidth > 0.0f
                ? new dev.t1m3.qplayer.audio.core.AudioBuf(
                        dev.t1m3.qplayer.audio.core.ChannelLayout.of(ch), 2 * Deck.RENDER_FRAMES)
                : null;
        this.window = Automix.fallbackWindowWith(style, r, tuning.windowScale);
        this.outroTempo = null;
        this.introTempo = null;
        this.outroTailSecs = 0.0;
        this.introRate = r;
        this.cutAt = 0.0f;
        this.cutForcedAt = null;
        this.autoNote = null;
        this.outroSource = TempoSource.COMPUTED;
        this.introSource = TempoSource.COMPUTED;
        this.fromSplit = new SplitLowHigh[ch];
        this.toSplit = new SplitLowHigh[ch];
        this.toSplit2 = new SplitLowHigh[ch];
        for (int i = 0; i < ch; i++) {
            this.fromSplit[i] = new SplitLowHigh(r, tuning.crossoverHz);
            this.toSplit[i] = new SplitLowHigh(r, tuning.crossoverHz);
            this.toSplit2[i] = new SplitLowHigh(r, EQ_MID_HZ);
        }

        this.fromHp = new Biquad[ch];
        this.toLp = new Biquad[ch];

        this.reverbRoute = style == MixStyle.CROSSFADE_OVERLAP_REVERB
                || style == MixStyle.CROSSFADE_RINGOUT_REVERB
                || style == MixStyle.POP_REVERB
                || style == MixStyle.FILTER_SOFT_REVERB
                || style == MixStyle.FILTER_LONG_OUT_SHORT_IN_REVERB;

        this.asymRoute = style == MixStyle.FILTER_LONG_OUT_SHORT_IN
                || style == MixStyle.FILTER_LONG_OUT_SHORT_IN_DELAY
                || style == MixStyle.FILTER_LONG_OUT_SHORT_IN_REVERB
                || style == MixStyle.FILTER_DYNAMIC_IN;
        this.delayRoute = style == MixStyle.HIPHOP_DELAY_OUT
                || style == MixStyle.POP_DELAY_OUT
                || style == MixStyle.FILTER_LONG_OUT_SHORT_IN_DELAY;
        boolean delayOn = delayRoute && tuning.delaySend > 0f && tuning.delayWet > 0f;
        this.delay = delayOn ? new Delay(rate, ch, tuning.delayFeedback, DELAY_DAMPING) : null;
        boolean reverbOn = reverbRoute && tuning.reverbSend > 0f && tuning.reverbWet > 0f;
        this.reverb = reverbOn
                ? new Reverb(rate, ch, tuning.reverbDecaySecs, tuning.reverbDamping) : null;
        this.blur = BLUR_WET > 0f ? new Reverb(rate, ch, BLUR_DECAY_SECS, 0.25f) : null;
        boolean sweepOn = tuning.filterSweepFromHz > 0f && tuning.filterSweepToHz > 0f
                && tuning.filterSweepFromHz > tuning.filterSweepToHz;
        this.sweepOut = sweepOn ? new Biquad[ch] : null;

        if (sweepOn) {
            for (int i = 0; i < ch; i++) {
                this.sweepOut[i] = Biquad.lowPass(rate, tuning.filterSweepFromHz,
                        tuning.filterSweepQ);
            }
        }
        for (int i = 0; i < ch; i++) {
            this.fromHp[i] = Biquad.highPass(r, tuning.showHpFrom, 0.707f);
            this.toLp[i] = Biquad.lowPass(r, tuning.showLpFrom, 0.707f);
        }
    }

    public static AiMix withTuning(MixStyle style, int rate, int channels, MixTuning tuning) {
        return new AiMix(style, rate, channels, tuning);
    }

    public MixTuning tuning() {
        return new MixTuning(tuning);
    }

    public boolean tuningForStyleLoop() {
        return tuning.styleLoopEnabled;
    }

    public MixStyle style() {
        return style;
    }

    public int channels() {
        return channels;
    }

    public int rate() {
        return rate;
    }

    public long window() {
        return window;
    }

    public Float outroBpm() {
        return outroTempo == null ? null : Float.valueOf(outroTempo.bpm);
    }

    public Float introBpm() {
        return introTempo == null ? null : Float.valueOf(introTempo.bpm);
    }

    public void reset() {
        if (levelMatcher != null) {
            levelMatcher.reset();
        }
        if (lowDucker != null) {
            lowDucker.reset();
        }
        for (SplitLowHigh s : fromSplit) {
            s.reset();
        }
        for (SplitLowHigh s : toSplit) {
            s.reset();
        }
        for (SplitLowHigh s : toSplit2) {
            s.reset();
        }
        learnedEq = null;
        for (Biquad f : fromHp) {
            f.reset();
        }
        for (Biquad f : toLp) {
            f.reset();
        }
        if (sweepOut != null) {
            for (Biquad f : sweepOut) {
                f.reset();
            }
            sweepHz = -1f;
        }
        if (reverb != null) {
            if (appleReverbParamsPort) {
                if (!appleRevResolved) {
                    resolveAppleReverb();
                }
                if (appleRevDecay > 0f
                        && Math.abs(appleRevDecay - tuning.reverbDecaySecs) > 1e-3f) {

                    reverb = new Reverb(rate, channels, appleRevDecay, tuning.reverbDamping);
                }
            }
            reverb.reset();
            if (repeat != null) {
                repeat.reset();
            }
            if (mixReverb != null) {
                mixReverb.reset();
            }
            if (mixFlanger != null) {
                mixFlanger.reset();
            }
        }
        if (delay != null) {
            delay.reset();
        }
    }

    @Override
    public long windowFrames(int sampleRate) {

        return window;
    }

    private static volatile int probeMuteSide = 0;

    private double carveHzA = 0.0;
    private double carveHzB = 0.0;
    private float carveGainDb = 0f;

    private int carveVersion = 0;

    private double[] carveHz;
    private float[] carveGain;

    static final double BASS_HANDOVER_HZ = 300.0;
    private Biquad[] bassOut;
    private double bassOutHz = -1.0;

    static final float BLUR_WET = 0f;
    static final float BLUR_SEND = 0.5f;
    static final float BLUR_DECAY_SECS = 0.6f;
    private final Reverb blur;

    private Biquad[] carveA;
    private Biquad[] carveB;

    private int carveBuiltVersion = -1;

    public void setCarveBands(double hzA, double hzB, float gainDb) {
        carveHzA = hzA;
        carveHzB = hzB;
        carveGainDb = gainDb;
        carveHz = null;
        carveGain = null;
        carveVersion++;
    }

    public void setCarvePlan(double[] hz, float[] gainDb) {
        carveHz = hz == null ? null : hz.clone();
        carveGain = gainDb == null ? null : gainDb.clone();
        carveHzA = 0.0;
        carveHzB = 0.0;
        carveGainDb = -1f;
        carveVersion++;
    }

    public static void setProbeMuteSide(int side) {
        probeMuteSide = side;
    }

    @Override
    public int render(AudioBuf out, AudioBuf from, AudioBuf to, float progress) {

        if (BASS_HANDOVER_HZ > 0.0 && from != null && from.frames() > 0) {
            applyBassHandover(from, progress);
        }

        if (blur != null && from != null && from.frames() > 0
                && (!appleReverbPort || appleHasReverb())) {
            float[] fb = from.backingArray();
            int fn = from.frames();
            for (int c = 0; c < Math.min(from.channels(), 2); c++) {
                int base = from.channelBase(c);
                for (int i = 0; i < fn; i++) {
                    fb[base + i] = fb[base + i] + blur.process(c, fb[base + i] * BLUR_SEND) * BLUR_WET;
                }
            }
        }
        ensureCarve();
        logStage("1 before carve", from, to, progress);
        if ((carveA != null && from != null) || (carveB != null && to != null)) {
            float k = (float) Math.sin(Math.PI * Math.max(0.0, Math.min(1.0, (double) progress)));
            if (k > 1e-4f) {
                if (carveA != null && from != null) {
                    if (carveHz != null) {
                        applyCarveChain(carveA, from, k);
                    } else {
                        applyCarve(carveA, from, k);
                    }
                }
                if (carveB != null && to != null) {
                    applyCarve(carveB, to, k);
                }
            }
        }
        int mute = probeMuteSide;
        if (mute == 1 && from != null) {
            zeroFrames(from);
        } else if (mute == 2 && to != null) {
            zeroFrames(to);
        }
        logStage("2 after carve", from, to, progress);
        int produced = renderBase(out, from, to, progress);
        logStage("3 after renderBase", out, null, progress);

        if (reverb != null && produced > 0) {
            applyReverb(out, from, produced, progress);
        }
        if (delay != null && produced > 0) {
            applyDelay(out, from, produced, progress);
        }
        return produced;
    }

    private static volatile boolean appleHarmonyPort = true;

    public static void setAppleHarmonyDevicesPort(boolean on) {
        appleHarmonyPort = on;
    }

    public static boolean appleHarmonyDevicesOn() {
        return appleHarmonyPort;
    }

    private boolean isMappedStyle() {
        return AppleStyleMap.appleId(style()) != AppleStyleMap.NONE;
    }

    private void applyBassHandover(AudioBuf from, float progress) {

        if (!appleHarmonyPort || !isMappedStyle()) {
            return;
        }
        double p = Math.max(0.0, Math.min(1.0, (double) progress));
        if (p <= 0.6) {
            return;
        }
        double k = (p - 0.6) / 0.4;
        float hz = (float) (20.0 + k * (BASS_HANDOVER_HZ - 20.0));
        if (bassOut == null) {
            bassOut = new Biquad[from.channels()];
            for (int c = 0; c < bassOut.length; c++) {
                bassOut[c] = Biquad.highPass(rate, 20f, 0.707f);
            }
        }
        if (Math.abs(hz - bassOutHz) > bassOutHz * 0.01) {
            bassOutHz = hz;
            for (Biquad f : bassOut) {
                f.setHighPass(rate, hz, 0.707f);
            }
        }
        float[] back = from.backingArray();
        int n = from.frames();
        for (int c = 0; c < bassOut.length; c++) {
            int base = from.channelBase(c);
            Biquad f = bassOut[c];
            for (int i = 0; i < n; i++) {
                back[base + i] = f.process(back[base + i]);
            }
        }
    }

    private void ensureCarve() {

        if (!appleHarmonyPort || !isMappedStyle()) {
            carveA = null;
            carveB = null;
            carveBuiltVersion = carveVersion;
            return;
        }
        if (carveBuiltVersion == carveVersion) {
            return;
        }
        carveBuiltVersion = carveVersion;
        carveA = null;
        carveB = null;
        double[] planHz = carveHz;
        float[] planGain = carveGain;
        if (planHz != null && planGain != null) {
            int n = Math.min(planHz.length, planGain.length);
            int used = 0;
            for (int i = 0; i < n; i++) {
                if (planHz[i] > 0.0 && planGain[i] < 0f) {
                    used++;
                }
            }
            if (used > 0) {

                carveA = new Biquad[channels * used];
                carveB = new Biquad[channels * used];
                int at = 0;
                for (int c = 0; c < channels; c++) {
                    for (int i = 0; i < n; i++) {
                        if (planHz[i] > 0.0 && planGain[i] < 0f) {
                            Biquad f = Biquad.peaking(rate, (float) planHz[i], 1.2f, planGain[i]);
                            carveA[at] = f;
                            carveB[at] = Biquad.peaking(rate, (float) planHz[i], 1.2f,
                                    planGain[i]);
                            at++;
                        }
                    }
                }
            }
        } else if (carveGainDb < 0f) {
            if (carveHzA > 0.0) {
                carveA = new Biquad[channels];
                for (int i = 0; i < channels; i++) {
                    carveA[i] = Biquad.peaking(rate, (float) carveHzA, 1.2f, carveGainDb);
                }
            }
            if (carveHzB > 0.0) {
                carveB = new Biquad[channels];
                for (int i = 0; i < channels; i++) {
                    carveB[i] = Biquad.peaking(rate, (float) carveHzB, 1.2f, carveGainDb);
                }
            }
        }
    }

    private static void applyCarveChain(Biquad[] chain, AudioBuf buf, float k) {
        float[] back = buf.backingArray();
        int n = buf.frames();
        int chs = buf.channels();
        int per = chain.length / Math.max(1, chs);
        for (int c = 0; c < chs; c++) {
            int base = buf.channelBase(c);
            for (int f = 0; f < per; f++) {
                Biquad flt = chain[c * per + f];
                for (int i = 0; i < n; i++) {
                    int at2 = base + i;
                    float dry = back[at2];
                    float wet = flt.process(dry);

                    back[at2] = dry + k * (wet - dry);
                }
            }
        }
    }

    private static void applyCarve(Biquad[] filters, AudioBuf buf, float k) {
        float[] back = buf.backingArray();
        int n = buf.frames();
        int chs = Math.min(buf.channels(), filters.length);
        for (int c = 0; c < chs; c++) {
            int base = buf.channelBase(c);
            Biquad f = filters[c];
            for (int i = 0; i < n; i++) {
                int at2 = base + i;
                float dry = back[at2];
                float wet = f.process(dry);
                back[at2] = dry + k * (wet - dry);
            }
        }
    }

    private static void zeroFrames(AudioBuf buf) {
        float[] back = buf.backingArray();
        int n = buf.frames();
        for (int c = 0; c < buf.channels(); c++) {
            int base = buf.channelBase(c);
            for (int i = 0; i < n; i++) {
                back[base + i] = 0f;
            }
        }
    }

    private static volatile boolean appleReverbParamsPort = true;

    public static void setAppleReverbParamsPort(boolean on) {
        appleReverbParamsPort = on;
    }

    private double windowSeconds;

    public void setWindowSeconds(double secs) {
        this.windowSeconds = secs > 0.0 ? secs : 0.0;
    }

    private AppleTransitionSpec.Auto appleRevSend;
    private float appleRevDecay = -1f;
    private float appleRevWet = -1f;
    private boolean appleRevResolved;

    private void resolveAppleReverb() {
        appleRevResolved = true;
        AppleTransitionSpec.Style spec = AppleStyleMap.specFor(style());
        if (spec == null) {
            return;
        }
        appleRevSend = AppleStyleMap.lane(spec, true, "send_mixer_gain");
        AppleTransitionSpec.Auto low = AppleStyleMap.lane(spec, true, "fx_reverb_low_frequency_decay_time");
        AppleTransitionSpec.Auto wetAuto = AppleStyleMap.lane(spec, true, "fx_reverb_dry_wet");
        if (low != null) {
            appleRevDecay = AppleTransitionSpec.valueAt(low, 1f);
        }
        if (wetAuto != null) {
            appleRevWet = AppleTransitionSpec.valueAt(wetAuto, 1f) / 100f;
        }
    }

    private void applyReverb(AudioBuf out, AudioBuf from, int n, float progress) {
        int chs = Math.min(out.channels(), from.channels());
        float send = tuning.reverbSend;
        float wet = tuning.reverbWet;
        if (appleReverbParamsPort) {
            if (!appleRevResolved) {
                resolveAppleReverb();
            }
            if (appleRevSend != null) {

                send = AppleTransitionSpec.valueAt(appleRevSend, progress, (float) windowSeconds);
            }
            if (appleRevWet >= 0f) {
                wet = appleRevWet;
            }
        }
        for (int ch = 0; ch < chs; ch++) {
            java.nio.FloatBuffer o = out.channelWriter(ch);
            for (int i = 0; i < n; i++) {
                float r = reverb.process(ch, sampleOf(from, ch, i) * send);
                o.put(i, o.get(i) + r * wet);
            }
        }
    }

    private static volatile boolean appleDelayParamsPort = true;

    public static void setAppleDelayParamsPort(boolean on) {
        appleDelayParamsPort = on;
    }

    private AppleTransitionSpec.Auto appleDelaySend;
    private float appleDelayWet = -1f;
    private boolean appleDelayResolved;

    private void resolveAppleDelay() {
        appleDelayResolved = true;
        AppleTransitionSpec.Style spec = AppleStyleMap.specFor(style());
        if (spec == null) {
            return;
        }
        AppleTransitionSpec.Auto wetAuto = AppleStyleMap.lane(spec, true, "fx_delay_dry_wet");
        if (wetAuto != null) {
            appleDelayWet = AppleTransitionSpec.valueAt(wetAuto, 1f) / 100f;
        }
        if (hasOp(spec.outgoingOps, "AUX_DELAY") || hasOp(spec.incomingOps, "AUX_DELAY")) {
            appleDelaySend = AppleStyleMap.lane(spec, true, "send_mixer_gain");
        }
    }

    private void applyDelay(AudioBuf out, AudioBuf from, int n, float progress) {
        long frames = Math.round(beatSecs * tuning.delayBeats * (double) rate);
        delay.setDelayFrames(frames);
        int chs = Math.min(out.channels(), from.channels());
        float send = tuning.delaySend;
        float wet = tuning.delayWet;
        if (appleDelayParamsPort) {
            if (!appleDelayResolved) {
                resolveAppleDelay();
            }
            if (appleDelaySend != null) {
                send = AppleTransitionSpec.valueAt(appleDelaySend, progress, (float) windowSeconds);
            }
            if (appleDelayWet >= 0f) {
                wet = appleDelayWet;
            }
        }
        for (int ch = 0; ch < chs; ch++) {
            java.nio.FloatBuffer o = out.channelWriter(ch);
            for (int i = 0; i < n; i++) {
                float d = delay.process(ch, sampleOf(from, ch, i) * send);
                o.put(i, o.get(i) + d * wet);
            }
        }
    }

    static final double HOLD_EXPONENT = 2.0;

    static final double MIX_AMOUNT = 0.5;

    private static final double[][] APPLE_A = {
        {0.00, 1.00}, {0.15, 1.00}, {0.30, 0.95}, {0.45, 0.85}, {0.55, 0.77},
        {0.70, 0.60}, {0.78, 0.50}, {0.85, 0.25}, {0.92, 0.07}, {1.00, 0.00},
    };
    private static final double[][] APPLE_B = {
        {0.00, 0.00}, {0.10, 0.09}, {0.20, 0.12}, {0.30, 0.18}, {0.40, 0.23},
        {0.55, 0.35}, {0.70, 0.50}, {0.80, 0.75}, {0.88, 0.92}, {1.00, 1.00},
    };

    private static float lerpTable(double[][] table, float p) {
        double x = Math.max(0.0, Math.min(1.0, (double) p));
        for (int i = 1; i < table.length; i++) {
            if (x <= table[i][0]) {
                double x0 = table[i - 1][0];
                double x1 = table[i][0];
                double k = x1 > x0 ? (x - x0) / (x1 - x0) : 0.0;
                return (float) (table[i - 1][1] + k * (table[i][1] - table[i - 1][1]));
            }
        }
        return (float) table[table.length - 1][1];
    }

    private static volatile boolean appleFilterPort = true;

    public static void setAppleFilterPort(boolean on) {
        appleFilterPort = on;
    }

    private Biquad[] appleLpA;
    private Biquad[] appleHpB;
    private Biquad[] appleLpB;
    private double appleLpAHz = -1.0;
    private double appleHpBHz = -1.0;
    private double appleLpBHz = -1.0;

    private static double easeOut(double p, double n) {
        return 1.0 - Math.pow(1.0 - p, n);
    }

    private static double easeIn(double p, double n) {
        return Math.pow(p, n);
    }

    private AppleTransitionSpec.Auto appleLpACurve;
    private AppleTransitionSpec.Auto appleHpBCurve;
    private AppleTransitionSpec.Auto appleLpBCurve;
    private boolean appleFilterResolved;

    private void resolveAppleFilters() {
        appleFilterResolved = true;
        AppleTransitionSpec.Style spec = AppleStyleMap.specFor(style());
        if (spec == null) {
            return;
        }
        appleLpACurve = AppleStyleMap.lane(spec, true, "lp_cutoff_freq");
        appleHpBCurve = AppleStyleMap.lane(spec, false, "hp_cutoff_freq");
        appleLpBCurve = AppleStyleMap.lane(spec, false, "lp_cutoff_freq");
    }

    private int renderAppleFilter(AudioBuf out, AudioBuf from, AudioBuf to, int n, float p0) {
        double p = Math.max(0.0, Math.min(1.0, (double) p0));
        if (appleLpA == null) {
            appleLpA = new Biquad[channels];
            appleHpB = new Biquad[channels];
            appleLpB = new Biquad[channels];
            for (int c = 0; c < channels; c++) {
                appleLpA[c] = Biquad.lowPass(rate, 20000f, 0.707f);
                appleHpB[c] = Biquad.highPass(rate, 20000f, 0.707f);
                appleLpB[c] = Biquad.lowPass(rate, 1200f, 0.707f);
            }
        }

        if (!appleFilterResolved) {
            resolveAppleFilters();
        }
        float lpA = appleLpACurve != null
                ? AppleTransitionSpec.valueAt(appleLpACurve, (float) p) : 20000f;
        float hpB = appleHpBCurve != null
                ? AppleTransitionSpec.valueAt(appleHpBCurve, (float) p) : 10f;
        float lpB = appleLpBCurve != null
                ? AppleTransitionSpec.valueAt(appleLpBCurve, (float) p) : 20000f;
        if (Math.abs(lpA - appleLpAHz) > Math.max(1.0, Math.abs(appleLpAHz) * 0.005)) {
            appleLpAHz = lpA;
            for (Biquad f : appleLpA) {
                f.setLowPass(rate, Math.max(20f, lpA), 0.707f);
            }
        }
        if (Math.abs(hpB - appleHpBHz) > Math.max(1.0, Math.abs(appleHpBHz) * 0.005)) {
            appleHpBHz = hpB;
            for (Biquad f : appleHpB) {
                f.setHighPass(rate, Math.max(10f, hpB), 0.707f);
            }
        }
        if (Math.abs(lpB - appleLpBHz) > Math.max(1.0, Math.abs(appleLpBHz) * 0.005)) {
            appleLpBHz = lpB;
            for (Biquad f : appleLpB) {
                f.setLowPass(rate, Math.max(20f, lpB), 0.707f);
            }
        }
        float gA = (float) (1.0 - p);
        float gB = (float) p;
        int chs = Math.min(out.channels(), Math.min(from.channels(), to.channels()));
        out.setFrames(n);
        float[] fb = from.backingArray();
        float[] tb = to.backingArray();
        for (int c = 0; c < chs; c++) {
            java.nio.FloatBuffer o = out.channelWriter(c);
            int bf = from.channelBase(c);
            int bt = to.channelBase(c);
            Biquad fa = appleLpA[Math.min(c, appleLpA.length - 1)];
            Biquad hb = appleHpB[Math.min(c, appleHpB.length - 1)];
            Biquad lb = appleLpB[Math.min(c, appleLpB.length - 1)];
            for (int i = 0; i < n; i++) {
                float a = fa.process(fb[bf + i]);
                float b = lb.process(hb.process(tb[bt + i]));
                o.put(i, a * gA + b * gB);
            }
        }
        for (int c = chs; c < out.channels(); c++) {
            java.nio.FloatBuffer o = out.channelWriter(c);
            for (int i = 0; i < n; i++) {
                o.put(i, 0f);
            }
        }
        return n;
    }

    private static volatile boolean appleRepeaterPort = false;

    public static void setAppleRepeaterPort(boolean on) {
        appleRepeaterPort = on;
    }

    private static volatile boolean appleGainPort = true;

    public static void setAppleGainPort(boolean on) {
        appleGainPort = on;
    }

    private static volatile boolean appleReverbPort = true;

    public static void setAppleReverbPort(boolean on) {
        appleReverbPort = on;
    }

    private AppleTransitionSpec.Auto appleOutGain;
    private AppleTransitionSpec.Auto appleInGain;
    private boolean appleGainResolved;

    private boolean appleHasReverbResolved;
    private boolean appleHasReverbValue;

    private boolean appleHasRemixResolved;
    private boolean appleHasRemixValue;

    private boolean appleHasRemix() {
        if (!appleHasRemixResolved) {
            appleHasRemixResolved = true;
            AppleTransitionSpec.Style spec = AppleStyleMap.specFor(style());
            appleHasRemixValue = spec != null
                    && (hasOp(spec.outgoingOps, "REMIX_FX") || hasOp(spec.incomingOps, "REMIX_FX"));
        }
        return appleHasRemixValue;
    }

    private boolean appleHasReverb() {
        if (!appleHasReverbResolved) {
            appleHasReverbResolved = true;
            AppleTransitionSpec.Style spec = AppleStyleMap.specFor(style());
            appleHasReverbValue = spec != null
                    && (hasOp(spec.outgoingOps, "AUX_REVERB")
                    || hasOp(spec.incomingOps, "AUX_REVERB"));
        }
        return appleHasReverbValue;
    }

    private static boolean hasOp(String[] ops, String name) {
        if (ops == null) {
            return false;
        }
        for (String o : ops) {
            if (name.equals(o)) {
                return true;
            }
        }
        return false;
    }

    private void resolveAppleGains() {
        appleGainResolved = true;
        AppleTransitionSpec.Style spec = AppleStyleMap.specFor(style());
        if (spec == null) {
            return;
        }
        appleOutGain = AppleStyleMap.lane(spec, true, "out_gain");
        appleInGain = AppleStyleMap.lane(spec, false, "out_gain");
    }

    private static AppleTransitionSpec.Auto findAuto(AppleTransitionSpec.Auto[] list, String key) {
        if (list == null) {
            return null;
        }
        for (AppleTransitionSpec.Auto a : list) {
            if (key.equals(a.key)) {
                return a;
            }
        }
        return null;
    }

    Mixer.Gains gains(float p) {

        if (appleGainPort) {
            if (!appleGainResolved) {
                resolveAppleGains();
            }
            if (appleOutGain != null || appleInGain != null) {
                double eqG = (Math.PI / 2.0) * Math.max(0.0, Math.min(1.0, (double) p));

                float gA = appleOutGain != null
                        ? AppleTransitionSpec.valueAt(appleOutGain, p) : 1f;
                float gB = appleInGain != null
                        ? AppleTransitionSpec.valueAt(appleInGain, p) : 1f;
                return new Mixer.Gains(gA, gB);
            }
        }
        float s = tuning.shortInFraction;
        if (!asymRoute || s <= 0f || s >= 1f) {

            if (!appleGainPort || AppleStyleMap.appleId(style()) == AppleStyleMap.NONE) {
                return Mixer.equalPowerGains(p);
            }

            double eq = (Math.PI / 2.0) * Math.max(0.0, Math.min(1.0, (double) p));
            double eqA = Math.cos(eq);
            double eqB = Math.sin(eq);
            double m = MIX_AMOUNT;
            return new Mixer.Gains(
                    (float) ((1.0 - m) * lerpTable(APPLE_A, p) + m * eqA),
                    (float) ((1.0 - m) * lerpTable(APPLE_B, p) + m * eqB));
        }

        double hold = HOLD_EXPONENT;
        double thA = (Math.PI / 2.0)
                * Math.pow(Math.max(0.0, Math.min(1.0, (double) p)), hold);
        double thB = (Math.PI / 2.0) * Math.min(1.0, (double) p / Math.max(0.05, (double) s));
        return new Mixer.Gains((float) Math.cos(thA), (float) Math.sin(thB));
    }

    private static volatile boolean appleRemixPort = true;

    public static void setAppleRemixPort(boolean on) {
        appleRemixPort = on;
    }

    private dev.t1m3.qplayer.audio.dsp.AppleBeatRepeat repeat;

    private dev.t1m3.qplayer.audio.dsp.AppleFlanger mixFlanger;

    private Reverb mixReverb;
    private float mixReverbTime = -1f;

    private final AppleRemixFx.State remixStateIn = new AppleRemixFx.State();
    private Biquad[] mixInFltA;
    private Biquad[] mixInFltB;
    private float mixInFltAHz = -1f;
    private float mixInFltBHz = -1f;

    private Biquad[] mixFltA;
    private Biquad[] mixFltB;
    private float mixFltAHz = -1f;
    private float mixFltBHz = -1f;
    private final AppleRemixFx.State remixState = new AppleRemixFx.State();

    private static volatile float appleOutDelay = 0f;

    private static volatile boolean appleOutSCurve = true;

    private static volatile float appleOutSpan = 0.78f;

    public static void setAppleOutSpan(float span) {
        appleOutSpan = Math.max(0.3f, Math.min(1f, span));
    }

    public static void setAppleOutSCurve(boolean on) {
        appleOutSCurve = on;
    }

    public static void setAppleOutDelay(float delay) {
        appleOutDelay = Math.max(0f, Math.min(0.9f, delay));
    }

    private void driveRemix(AudioBuf from, AudioBuf to, float progress) {
        if (!appleRemixPort || from == null || from.frames() <= 0) {
            return;
        }

        if (!appleHasRemix()) {
            return;
        }
        if (repeat == null) {
            int maxFrames = (int) Math.ceil(Math.max(0.05, beatSecs) * rate) + 4;
            repeat = new dev.t1m3.qplayer.audio.dsp.AppleBeatRepeat(from.channels(), maxFrames);
        }

        float pOut = appleOutDelay > 0f && progress <= appleOutDelay ? 0f
                : (progress - appleOutDelay) / (1f - appleOutDelay);
        pOut = Math.max(0f, Math.min(1f, pOut));
        if (appleOutSCurve) {

            pOut = Math.min(1f, pOut / appleOutSpan);
        }
        AppleRemixFx.fill(remixState, pOut);

        {
            final double win = windowSeconds;
            final double tailFrac = win > 0.0 ? Math.min(0.5, 0.25 / win) : 0.0;
            if (tailFrac > 0.0 && pOut > 1.0f - tailFrac) {
                final float k = (float) Math.max(0.0, (1.0 - (double) pOut) / tailFrac);
                remixState.reverbSend *= k;
                remixState.flangerMix *= k;
                remixState.repeaterMix *= k;
            }
        }
        boolean on = appleRepeaterPort && remixState.repeaterOn && remixState.repeaterRate > 0f;
        if (on) {
            int frames = (int) Math.round(beatSecs / (double) remixState.repeaterRate * rate);
            repeat.setLoopFrames(Math.max(1, frames));
            repeat.setMix(remixState.repeaterMix);
        }
        repeat.setActive(on);
        float[] back = from.backingArray();
        int n = from.frames();
        for (int ch = 0; ch < from.channels(); ch++) {
            int base = from.channelBase(ch);
            for (int i = 0; i < n; i++) {
                back[base + i] = repeat.process(ch, back[base + i]);
            }
        }

        applyRemixFilter(from, remixState.filterAOn,
                appleHpDelayCutoff(remixState.filterACutoff), true,
                remixState.filterAHigh);
        applyRemixFilter(from, remixState.filterBOn, remixState.filterBCutoff, false,
                remixState.filterBHigh);

        applyRemixReverb(from);

        applyRemixFlanger(from);

        if (to != null) {
            AppleRemixFx.fillIncoming(remixStateIn, progress);
            applyRemixFilterIn(to, remixStateIn.filterAOn, remixStateIn.filterACutoff, true,
                    remixStateIn.filterAHigh);
            applyRemixFilterIn(to, remixStateIn.filterBOn, remixStateIn.filterBCutoff, false,
                    remixStateIn.filterBHigh);
        }

        applyAppleInputGain(from, progress);
    }

    private static volatile float appleGainFloorWhenFadeOut = 0.35f;

    public static void setAppleGainFloorWhenFadeOut(float v) {
        appleGainFloorWhenFadeOut = Math.max(0f, Math.min(1f, v));
    }

    public static float appleGainFloorWhenFadeOut() {
        return appleGainFloorWhenFadeOut;
    }

    private AppleTransitionSpec.Auto appleInputGain;
    private boolean appleInputGainResolved;

    private static int stageLogged;

    private static void logStage(String tag, AudioBuf a, AudioBuf b, float progress) {
        stageLogged++;
        if (stageLogged > 80 || stageLogged % 4 != 1) {
            return;
        }
        Logger.debug("grading {} A={} B={} p={}", tag, rmsDb(a), rmsDb(b),
                String.format(java.util.Locale.ROOT, "%.2f", Float.valueOf(progress)));
    }

    private static String rmsDb(AudioBuf buf) {
        if (buf == null || buf.frames() <= 0) {
            return "-";
        }
        float[] back = buf.backingArray();
        int n = buf.frames();
        double sum = 0.0;
        int cnt = 0;
        for (int c = 0; c < buf.channels(); c++) {
            int base = buf.channelBase(c);
            for (int i = 0; i < n; i += 16) {
                double v = back[base + i];
                sum += v * v;
                cnt++;
            }
        }
        double rms = cnt > 0 ? Math.sqrt(sum / cnt) : 0.0;
        return String.format(java.util.Locale.ROOT, "%.1f",
                Double.valueOf(20.0 * Math.log10(Math.max(rms, 1e-7))));
    }

    private void applyAppleInputGain(AudioBuf from, float progress) {
        if (!appleRemixPort || from == null || from.frames() <= 0) {
            return;
        }
        if (!appleInputGainResolved) {
            appleInputGainResolved = true;
            AppleTransitionSpec.Style spec = AppleStyleMap.specFor(style());
            appleInputGain = AppleStyleMap.lane(spec, true, "player_gain");
        }
        if (appleInputGain == null) {
            return;
        }
        float g = AppleTransitionSpec.valueAt(appleInputGain, progress, (float) windowSeconds);

        if (appleGainFloorWhenFadeOut > 0f && appleInputGain.segs.length > 0) {
            AppleTransitionSpec.Seg lastSeg = appleInputGain.segs[appleInputGain.segs.length - 1];
            if (lastSeg.v1 <= 0.01f) {
                g = Math.max(g, appleGainFloorWhenFadeOut);
            }
        }
        if (g >= 0.9999f) {
            return;
        }
        float[] back = from.backingArray();
        int n = from.frames();
        for (int c = 0; c < from.channels(); c++) {
            int base = from.channelBase(c);
            for (int i = 0; i < n; i++) {
                back[base + i] = back[base + i] * g;
            }
        }
    }

    private void applyRemixFlanger(AudioBuf from) {
        if (!remixState.flangerOn || remixState.flangerMix <= 0f || from.frames() <= 0) {
            return;
        }
        if (mixFlanger == null) {
            mixFlanger = new dev.t1m3.qplayer.audio.dsp.AppleFlanger(rate, from.channels());
        }
        mixFlanger.setRateHz(AppleRemixFx.flangerRateHz());
        mixFlanger.setDepth(AppleRemixFx.flangerDepth());
        mixFlanger.setCenter(AppleRemixFx.flangerCenter());
        mixFlanger.setFeedback(AppleRemixFx.flangerFeedback());
        mixFlanger.setMix(remixState.flangerMix);
        float[] back = from.backingArray();
        int n = from.frames();
        for (int ch = 0; ch < from.channels(); ch++) {
            int base = from.channelBase(ch);
            for (int i = 0; i < n; i++) {
                back[base + i] = mixFlanger.process(ch, back[base + i]);
            }
        }
    }

    private void applyRemixReverb(AudioBuf from) {
        if (!remixState.reverbOn || remixState.reverbSend <= 0f || from.frames() <= 0) {
            return;
        }
        float decay = AppleRemixFx.reverbTimeSecs();
        if (mixReverb == null || Math.abs(decay - mixReverbTime) > 1e-3f) {
            mixReverb = new Reverb(rate, from.channels(), Math.max(0.05f, decay), 0.3f);
            mixReverbTime = decay;
        }
        float send = remixState.reverbSend;
        float wet = AppleRemixFx.reverbWet();
        float[] back = from.backingArray();
        int n = from.frames();
        for (int c = 0; c < from.channels(); c++) {
            int base = from.channelBase(c);
            for (int i = 0; i < n; i++) {
                float dry = back[base + i];
                back[base + i] = dry + wet * mixReverb.process(c, dry * send);
            }
        }
    }

    private static volatile boolean appleHpDelayPort = true;

    private static volatile float appleHpLimitHz = 350f;

    public static void setAppleHpLimitHz(float hz) {
        appleHpLimitHz = Math.max(20f, Math.min(4000f, hz));
    }

    public static float appleHpLimitHz() {
        return appleHpLimitHz;
    }

    public static void setAppleHpDelayPort(boolean on) {
        appleHpDelayPort = on;
    }

    private static float appleHpDelayCutoff(float hz) {
        if (!appleHpDelayPort || hz <= 20f) {
            return hz;
        }
        float span = Math.max(20f, appleHpLimitHz) - 20f;
        float x = Math.min(1f, (hz - 20f) / span);
        return 20f + span * x * x;
    }

    private void applyRemixFilterIn(AudioBuf buf, boolean on, float hz, boolean isA,
                                    boolean high) {
        if (!on || hz <= 0f) {
            return;
        }
        Biquad[] filters = isA ? mixInFltA : mixInFltB;
        if (filters == null || filters.length != buf.channels()) {
            filters = new Biquad[buf.channels()];
            for (int c = 0; c < filters.length; c++) {
                filters[c] = high ? Biquad.highPass(rate, Math.max(20f, hz), 0.707f)
                        : Biquad.lowPass(rate, Math.max(20f, hz), 0.707f);
            }
            if (isA) {
                mixInFltA = filters;
                mixInFltAHz = hz;
            } else {
                mixInFltB = filters;
                mixInFltBHz = hz;
            }
        }
        float last = isA ? mixInFltAHz : mixInFltBHz;
        if (Math.abs(hz - last) > Math.max(1f, Math.abs(last) * 0.005f)) {
            if (isA) {
                mixInFltAHz = hz;
            } else {
                mixInFltBHz = hz;
            }
            for (Biquad f : filters) {
                if (high) {
                    f.setHighPass(rate, Math.max(20f, hz), 0.707f);
                } else {
                    f.setLowPass(rate, Math.max(20f, hz), 0.707f);
                }
            }
        }
        float[] back = buf.backingArray();
        int n = buf.frames();
        for (int c = 0; c < filters.length; c++) {
            int base = buf.channelBase(c);
            Biquad f = filters[c];
            for (int i = 0; i < n; i++) {
                back[base + i] = f.process(back[base + i]);
            }
        }
    }

    private void applyRemixFilter(AudioBuf from, boolean on, float hz, boolean isA,
                                  boolean high) {
        if (!on || hz <= 0f) {
            return;
        }
        Biquad[] filters = isA ? mixFltA : mixFltB;
        if (filters == null || filters.length != from.channels()) {
            filters = new Biquad[from.channels()];
            for (int c = 0; c < filters.length; c++) {
                filters[c] = high ? Biquad.highPass(rate, Math.max(20f, hz), 0.707f)
                        : Biquad.lowPass(rate, Math.max(20f, hz), 0.707f);
            }
            if (isA) {
                mixFltA = filters;
                mixFltAHz = hz;
            } else {
                mixFltB = filters;
                mixFltBHz = hz;
            }
        }
        float last = isA ? mixFltAHz : mixFltBHz;
        if (Math.abs(hz - last) > Math.max(1f, Math.abs(last) * 0.005f)) {
            if (isA) {
                mixFltAHz = hz;
            } else {
                mixFltBHz = hz;
            }
            for (Biquad f : filters) {
                if (high) {
                    f.setHighPass(rate, Math.max(20f, hz), 0.707f);
                } else {
                    f.setLowPass(rate, Math.max(20f, hz), 0.707f);
                }
            }
        }
        float[] back = from.backingArray();
        int n = from.frames();
        for (int c = 0; c < filters.length; c++) {
            int base = from.channelBase(c);
            Biquad f = filters[c];
            for (int i = 0; i < n; i++) {
                back[base + i] = f.process(back[base + i]);
            }
        }
    }

    private int renderBase(AudioBuf out, AudioBuf from, AudioBuf to, float progress) {
        driveRemix(from, to, progress);
        float p0 = Automix.clampF32(progress, 0.0f, 1.0f);

        float spaceWidth = tuning == null ? 0.0f : tuning.spaceWidth;
        boolean fits = spaceScratch != null && from.frames() <= spaceScratch.capacityFrames();
        if (spaceWidth > 0.0f && fits && style != MixStyle.BEAT_CUT
                && from.channels() >= 2 && from.frames() > 0 && !isEssentiallyMono(from)) {
            float curve = spaceWidth * (float) Math.sin(Math.PI * (double) p0);
            if (curve > 1e-4f) {
                float widen = curve * SPACE_MAX_SHIFT;
                Mixer.copyWithWidth(from, spaceScratch,
                        Math.min(from.frames(), spaceScratch.capacityFrames()),
                        1.0f - widen, 1.0f + widen);
                from = spaceScratch;
            }
        }

        Transition harmonic = harmonicBlend;
        if (harmonic != null) {
            return harmonic.render(out, from, to, progress);
        }
        float p = Automix.clampF32(progress, 0.0f, 1.0f);
        int n = Math.min(out.capacityFrames(), Math.max(from.frames(), to.frames()));
        if (n == 0) {
            out.setFrames(0);
            return 0;
        }

        switch (style) {

            case PLAIN_BLEND:
            case AUTO:
                return renderPlainBlend(out, from, to, p);
            case BEAT_CUT:
                return renderBeatCut(out, from, to, p);
            case BASS_SWAP:
                return renderBassSwap(out, from, to, n, p);
            case TAIL_RIDE:
                return renderTailRide(out, from, to, p);
            case SHOW_MODE:
                return renderShowMode(out, from, to, n, p);
            case GAPLESS:
            case SPLICE:
                return renderBeatCut(out, from, to, p);
            case CROSSFADE_LONG:
            case CROSSFADE_OVERLAP:
            case CROSSFADE_OVERLAP_REVERB:
            case NO_VOCAL_CLASH:
            case POP_REVERB:
                return renderPlainBlend(out, from, to, p);
            case CROSSFADE_RINGOUT:
            case CROSSFADE_RINGOUT_REVERB:
            case HIPHOP_DELAY_OUT:
            case POP_DELAY_OUT:
                return renderTailRide(out, from, to, p);
            case FILTER_HIGH_TO_LOW:
            case FILTER_LONG_HIGH_TO_LOW:
            case FILTER_SHORT_HIGH_TO_LOW:
            case FILTER_SOFT_REVERB:

                if (appleFilterPort) {
                    return renderAppleFilter(out, from, to, n, p);
                }
                return renderBassSwap(out, from, to, n, p, true);
            case FILTER_EXPANSION:
            case FILTER_LONG_OUT_SHORT_IN:
            case FILTER_DYNAMIC_IN:
            case FILTER_LONG_OUT_SHORT_IN_DELAY:
            case FILTER_LONG_OUT_SHORT_IN_REVERB:
            case DANCE_ANY_KEY:
                return learnedEq == null ? renderPlainBlend(out, from, to, p)
                        : renderLearnedEq(out, from, to, n, p);
            case HIPHOP_LOOPING:
            case DANCE_REPEATER:

                return renderPlainBlend(out, from, to, p);
            case LEARNED_EQ:

                return learnedEq == null ? renderPlainBlend(out, from, to, p)
                        : renderLearnedEq(out, from, to, n, p);
            default:
                throw new IllegalStateException("unknown transition kind: " + style);
        }
    }

    @Override
    public String name() {
        return harmonicBlend != null ? style.label() + " -> harmonic blend" : style.label();
    }

    @Override
    public boolean rewritesSamples() {
        return harmonicBlend != null || style.rewritesSamples();
    }

    @Override
    public PitchPlan pitchAt(float progress) {
        Transition harmonic = harmonicBlend;
        return harmonic != null ? harmonic.pitchAt(progress) : PitchPlan.ZERO;
    }

    @Override
    public TransitionPlan prepare(String outro, String intro, int rate) {
        return prepare(outro, intro, rate, null, null);
    }

    @Override
    public TransitionPlan prepare(String outro, String intro, int rate,
                                  java.util.Map<String, String> outroHeaders,
                                  java.util.Map<String, String> introHeaders) {

        window = Automix.fallbackWindowWith(style, this.rate, tuning.windowScale);
        harmonicBlend = null;
        harmonicPlan = null;
        harmonicCue = null;
        autoCue = null;
        outroTempo = null;
        introTempo = null;
        outroTailSecs = 0.0;
        introRate = this.rate;
        cutAt = 0.0f;
        cutForcedAt = null;
        autoNote = null;
        outroSource = TempoSource.COMPUTED;
        introSource = TempoSource.COMPUTED;
        reset();

        final Automix.Mono[] reads = new Automix.Mono[2];
        final AudioException[] readErrors = new AudioException[2];

        // Android host: keep decoding inside the cancellable, bounded preparation worker.
        try {
            reads[0] = Automix.readMono(outro, outroHeaders, true, Automix.ANALYSIS_SECS);
        } catch (AudioException e) { readErrors[0] = e; }
        try {
            reads[1] = Automix.readMono(intro, introHeaders, false, Automix.ANALYSIS_SECS);
        } catch (AudioException e) { readErrors[1] = e; }
        String readFailure = readFailureNote(reads[0], readErrors[0], reads[1], readErrors[1]);
        if (readFailure != null) {
            return failurePlan(readFailure);
        }
        Automix.Mono tail = reads[0];
        Automix.Mono head = reads[1];

        float[] tailPcm = tail.pcm;
        int tailRate = tail.rate;
        Double outroDurOpt = tail.durationSecs;
        introRate = head.rate;
        outroTailSecs = (double) tailPcm.length / (double) Math.max(tailRate, 1);
        double outroDur = outroDurOpt == null ? 0.0 : outroDurOpt.doubleValue();

        Budget budget = new Budget();
        TempoResultHolder rOut = tempoOf(tailPcm, tailRate, budget);
        TempoResultHolder rIn = tempoOf(head.pcm, head.rate, budget);
        Analysis.Tempo tOut = rOut.tempo;
        Analysis.Tempo tIn = rIn.tempo;

        AutoCue.Envelope outEnv = null;
        AutoCue.Envelope inEnv = null;
        try {
            outEnv = AutoCue.scan(outro, outroHeaders);
            inEnv = AutoCue.scanIntro(intro, introHeaders);

        } catch (RuntimeException e) {
            outEnv = null;
            inEnv = null;
        }

        Analysis.Tempo sanOut = saneTempo(tOut, outEnv == null ? null : outEnv.tempo);
        Analysis.Tempo sanIn = saneTempo(tIn, inEnv == null ? null : inEnv.tempo);
        outroSource = rOut.source;
        introSource = rIn.source;
        outroTempo = tOut;
        introTempo = tIn;

        boolean autoPick = style == MixStyle.AUTO;
        Harmonic.Match harmony = null;
        if (autoPick) {
            harmony = Harmonic.match(tailPcm, tailRate, head.pcm, head.rate);

            MaterialProfile profile = MaterialProfile.of(tailPcm, head.pcm,
                    tailPcm.length / (double) Math.max(1, tailRate),
                    head.pcm.length / (double) Math.max(1, head.rate), tailRate);

            Chooser.AutoChoice choice;
            if (RouteScore.enabled()) {

                RouteGates.Signals sig = new RouteGates.Signals();
                sig.tempoDiff = sanOut == null || sanIn == null ? null
                        : Double.valueOf(Math.abs(sanOut.bpm - sanIn.bpm)
                                / Math.max((sanOut.bpm + sanIn.bpm) * 0.5f, 1.0f));

                sig.tailDecayDbPerSec = Double.valueOf(profile.tailDecayDbPerSec());

                sig.trailingSpanSecs = Double.valueOf(trailingSpanSecs(outEnv));

                float[] stereoLeft = null;
                float[] stereoRight = null;
                int stereoFrames = 0;
                try {
                    int want = (int) Math.round(Math.min(1.0, Math.max(0.2, tailPcm.length
                            / (double) Math.max(1, tailRate))));
                    float[][] st = Automix.readStereoWindow(outro, outroHeaders,
                            Math.max(0.0, tail.durationSecs == null ? 0.0
                                    : tail.durationSecs.doubleValue() - want), want, tailRate);
                    if (st != null && st.length >= 2) {
                        stereoLeft = st[0];
                        stereoRight = st[1];
                        stereoFrames = want;
                    }
                } catch (RuntimeException e) {
                    stereoFrames = 0;
                }

                double centre = RouteGates.centreRatio(stereoLeft, stereoRight, stereoFrames);
                boolean centreReliable = RouteGates.vocalPresenceIsReliable(centre);
                if (centreReliable) {

                    sig.vocalOverlapSecs = Double.valueOf(1.0);
                    sig.vocalOverlapReliable = Boolean.TRUE;

                    double kindWindow = Math.max(0.5, profile.overlapSecs());
                    double kindCut = Math.max(0.0, outroDur - kindWindow);
                    double overlapStrength = AutoCue.vocalOverlapStrength(outEnv, kindCut,
                            inEnv, 0.0, kindWindow);
                    if (!Double.isNaN(overlapStrength)) {
                        sig.vocalOverlapStrength = Double.valueOf(overlapStrength);
                    }
                    int kindA = AutoCue.vocalKind(outEnv, kindCut, kindCut + kindWindow);
                    int kindB = AutoCue.vocalKind(inEnv, 0.0, kindWindow);

                    int kind = kindA >= 0 && kindB >= 0
                            ? (overlapStrengthOfSide(outEnv, kindCut, kindWindow)
                                    >= overlapStrengthOfSide(inEnv, 0.0, kindWindow)
                                    ? kindA : kindB)
                            : Math.max(kindA, kindB);
                    if (kind >= 0) {
                        sig.vocalKind = Integer.valueOf(kind);
                    }
                } else {

                    sig.vocalOverlapSecs = Double.valueOf(
                            estimateVocalOverlapSecs(tailPcm, tailRate));
                    sig.vocalOverlapReliable = Boolean.FALSE;
                }
                sig.incomingQuieter = Boolean.valueOf(incomingIsQuieter(tailPcm, head, tailRate));

                if (outEnv != null && outEnv.tempo != null && outEnv.tempo.periodFrames > 0
                        && outEnv.beatRate > 0) {
                    double b = outEnv.tempo.periodFrames / (double) outEnv.beatRate;
                    if (b > 0.05 && b < 5.0) {
                        this.beatSecs = b;
                    }
                }
                double beatsA = beatCount(profile.overlapSecs(), outEnv);
                double beatsB = beatCount(profile.overlapSecs(), inEnv);
                sig.outroBeats = Double.valueOf(beatsA);
                sig.introBeats = Double.valueOf(beatsB);
                sig.matchingBars = Boolean.valueOf(Math.abs(beatsA - beatsB) <= 1.0);
                sig.bpmA = sanOut == null ? null : Double.valueOf(sanOut.bpm);
                sig.bpmB = sanIn == null ? null : Double.valueOf(sanIn.bpm);
                sig.keyScore = harmony == null ? null : Double.valueOf(harmony.plainScore);
                sig.lowClash = Double.valueOf(profile.lowClash());
                sig.dropReliable = Double.valueOf(profile.dropReliable());
                sig.overlapSecs = Double.valueOf(profile.overlapSecs());
                sig.buildUp = Boolean.valueOf(profile.buildUp());

                float bpmForGenre = sanOut == null ? 0f : sanOut.bpm;

                sig.outgoingLoudnessSignificant = Boolean.valueOf(
                        RouteGates.outgoingLoudnessSignificant(tailPcm, tailRate));

                if (outEnv != null && inEnv != null) {
                    double melodyWindow = Math.max(0.5, profile.overlapSecs());
                    double melodyCut = Math.max(0.0, outroDur - melodyWindow);
                    sig.melody = AutoCue.melodyOrNull(
                            AutoCue.melodyMatch(outEnv, melodyCut, inEnv, 0.0, melodyWindow));
                }
                sig.genres = EqFeatureExtractor.genreScores(bpmForGenre,
                        profile.onset(), profile.lowRatio(),
                        Math.max(0f, 1f - profile.lowRatio()));
                java.util.List<MixStyle> styles = new java.util.ArrayList<MixStyle>();
                java.util.List<java.util.List<Double>> sets =
                        new java.util.ArrayList<java.util.List<Double>>();
                java.util.List<Double> pos = new java.util.ArrayList<Double>();
                for (MixStyle cand : ROUTE_CANDIDATES) {
                    java.util.List<Double> f = RouteGates.factorsFor(cand, sig);
                    if (f == null) {
                        continue;
                    }
                    styles.add(cand);
                    sets.add(f);
                    pos.add(Double.valueOf(0.0));
                }
                MixStyle pick = RouteScore.best(styles, sets, pos);
                String kernelNote = "auto: scoring kernel (base x product of factors + 0.001*D) picked "
                        + (pick == null ? "**no candidate passed** (" + styles.size() + "/"
                                + ROUTE_CANDIDATES.length + " candidates passed the gate => falling back to the legacy picker)"
                                : pick.label())
                        + ", melody " + (sig.melody == null ? "not measured (neutral)"
                                : String.format(java.util.Locale.ROOT, "%.3f",
                                        sig.melody.doubleValue()))
                        + ", vocal overlap " + (sig.vocalOverlapReliable == Boolean.TRUE
                                ? "reliable" : "unreliable");
                choice = pick == null
                        ? withKernelNote(Chooser.choose(sanOut, sanIn, harmony, tuning,
                                profile, 8.0), kernelNote)
                        : new Chooser.AutoChoice(pick, "auto: scoring kernel (base x product of factors + 0.001*D) picked "
                                + pick.label()

                                + ", melody "
                                + (sig.melody == null ? "not measured (neutral)"
                                        : String.format(java.util.Locale.ROOT, "%.3f",
                                                sig.melody.doubleValue()))
                                + ", vocal overlap " + (sig.vocalOverlapReliable == Boolean.TRUE
                                        ? "reliable" : "unreliable")

                                + " (start-point convention)");
            } else {
                choice = Chooser.choose(sanOut, sanIn, harmony, tuning, profile, 8.0);
            }
            style = whitelisted(choice.style);

            if (autoPick && style == MixStyle.NO_VOCAL_CLASH && !autoPickNoVocalClash) {
                style = MixStyle.CROSSFADE_OVERLAP;
                Logger.info("auto style pick: skipping no-vocal-clash (measured: it drops A by 15 dB at the end of the blend)"
                        + "=> switching to '{}'", style);
            }
            this.autoProfile = profile;
            this.lastTempoOut = tOut;
            this.lastTempoIn = tIn;
            autoNote = choice.why;
        }

        long fallbackFrames = Automix.fallbackWindowWith(style, this.rate, tuning.windowScale);

        long idealFrames;
        if (tOut != null && style.beats() > 0.0f) {
            double period = (double) tOut.periodFrames / (double) Math.max(tailRate, 1);

            double alias = sanOut == null || sanIn == null
                    ? 0.0 : RouteGates.tempoAlias(sanOut.bpm, sanIn.bpm);
            double beats = RouteGates.snapBeatsToQuantum((double) style.beats(), alias)
                    * (double) tuning.windowScale;
            double want = Math.round(beats * period * (double) this.rate);
            idealFrames = Math.max((long) want, 1L);
        } else {
            idealFrames = fallbackFrames;
        }

        idealFrames = Automix.limitWindowWith(
                idealFrames, outroDur, this.rate, tuning.maxWindowFraction);

        if (tOut != null) {
            double periodSecs = (double) tOut.periodFrames / (double) Math.max(tailRate, 1);
            double need = (double) idealFrames / (double) this.rate + periodSecs + 1.0;

            if (periodSecs > 1e-3 && need > outroTailSecs + 1e-3 && need <= outroDur) {
                try {
                    Automix.Mono again = Automix.readMono(outro, outroHeaders, true, need);
                    tailPcm = again.pcm;
                    outroTailSecs = (double) tailPcm.length / (double) Math.max(again.rate, 1);

                    TempoResultHolder againTempo = tempoOf(tailPcm, tailRate, budget);
                    tOut = againTempo.tempo;
                    outroSource = againTempo.source;
                    outroTempo = tOut;
                } catch (AudioException e) {

                }
            }
        }

        long alignedWindow = idealFrames;
        if (tOut != null) {
            double fileRate = (double) Math.max(tailRate, 1);
            double period = (double) tOut.periodFrames / fileRate;
            double phase = (double) tOut.phaseFrames / fileRate;
            double tailDur = outroTailSecs;
            double w0 = (double) idealFrames / (double) this.rate;

            if (period > 1e-3 && tailDur > w0) {
                double target = tailDur - w0;
                double k = Math.floor((target - phase) / period);
                double start = phase + k * period;
                if (start >= 0.0 && start < tailDur) {
                    long aligned = Math.round((tailDur - start) * (double) this.rate);

                    long maxOk = idealFrames + (long) (period * (double) this.rate) + 1L;
                    if (aligned >= idealFrames && aligned <= maxOk) {
                        alignedWindow = aligned;
                    }
                }
            }
        }

        long styleWindow = Math.min(alignedWindow, hostWindowLimit);
        window = styleWindow;

        if (Boolean.getBoolean("qplayer.trace.window")) {
            System.out.println(String.format(java.util.Locale.ROOT,
                    "ideal=%.2fs aligned=%.2fs style=%.2fs kind=%s",
                    idealFrames / (double) this.rate, alignedWindow / (double) this.rate,
                    styleWindow / (double) this.rate, String.valueOf(style)));
        }

        if (autoPick && harmonicBlend == null) {
            java.util.function.DoubleSupplier supplier = harmonicSecs;
            double nominal = supplier == null ? 6.0 : supplier.getAsDouble();
            if (!(nominal > 0.0)) {
                nominal = 6.0;
            }
            double baselineSecs = (double) styleWindow / (double) Math.max(1, this.rate);
            try {

                if (outEnv == null) {
                    outEnv = AutoCue.scan(outro, outroHeaders);
                }
                if (inEnv == null) {
                    inEnv = AutoCue.scanIntro(intro, introHeaders);
                }

                dev.t1m3.qplayer.audio.mix.TempoRamp ramp =
                        AutoCueTransition.rampFor(outEnv, inEnv, nominal, false);
                double rampCap = ramp != null && ramp.active ? ramp.windowSecs : 0.0;

                double barTarget = AutoCueTransition.appleBarTargetSecs();
                double capHere = barTarget > 0.0 ? barTarget : rampCap;
                double[] withBaseline = capHere > 0.0
                        ? AutoCueTransition.windowCandidates(capHere, true)
                        : AutoCueTransition.windowCandidates(nominal, true);
AutoCue.PlanChoice decided = AutoCue.comparePlans(outEnv, inEnv,
                        baselineSecs,
                        withBaseline,
                        harmony == null ? 0.0 : (double) harmony.plainScore,

                        harmony != null && harmony.shouldShift);
                if (decided != null) {
                    autoNote = autoNote + "；" + decided.why;
                    if (!decided.harmonic && decided.cue != null) {

                        autoCue = decided.cue;
                        autoNote = autoNote + "; using auto cue (exit "
                                + String.format(java.util.Locale.ROOT, "%.3f",
                                        decided.cue.outroCutSecs)
                                + " s, entry "
                                + String.format(java.util.Locale.ROOT, "%.3f",
                                        decided.cue.introEntrySecs)
                                + " s）";
                    }
                    if (decided.harmonic && decided.windowSecs > 0.0) {
                        ModeHolder prepared = new ModeHolder();
                        harmonicBlend = new HarmonicBlend(this.rate, this.channels,
                                (float) decided.windowSecs);
                        prepared.plan = harmonicBlend.prepare(outro, intro, this.rate,
                                outroHeaders, introHeaders);
                        harmonicPlan = prepared.plan;

                        harmonicCue = decided.cue;

                        window = Math.max(1L, Math.round(decided.windowSecs * this.rate));

                        autoNote = autoNote + "；" + decided.note;
                    }
                }
            } catch (RuntimeException e) {
                Logger.debug("AI plan comparison skipped ({}), keeping the kind decision: {}",
                        e.toString(), style.label());
            }
        }

        long cutFrames = Math.round(tuning.cutFadeSecs * (float) this.rate);

        long leadFrames = Math.round(tuning.cutLeadSecs * (float) this.rate);
        long cutPos = cutFrames + leadFrames;
        if (style == MixStyle.BEAT_CUT && window > cutPos) {
            cutAt = Automix.clampF32((float) (window - cutPos) / (float) window, 0.0f, 1.0f);
        } else {
            cutAt = 0.0f;
        }

        float introStartAt = style == MixStyle.BEAT_CUT ? cutAt : 0.0f;

        double skipSecs = 0.0;
        if (tIn != null) {
            double skip = (double) tIn.phaseFrames / (double) Math.max(head.rate, 1);
            if (skip > 1e-4) {
                skipSecs = skip;
            }
        }

        if (harmonicBlend != null && harmonicPlan != null) {

            double entry = harmonicCue == null ? harmonicPlan.skipSecs
                    : harmonicCue.introEntrySecs;
            double droppedTail = harmonicCue == null ? harmonicPlan.outroTailSecs
                    : harmonicCue.tailDropSecs;

            return new TransitionPlan(entry, harmonicPlan.pitch,
                    harmonicPlan.introStartAt, noteWithActualWindow(), droppedTail);
        }

        double entrySecs = autoCue != null ? autoCue.introEntrySecs : skipSecs;
        double droppedTail = outroTailSecs;
        if (autoCue != null && outroDur > 0.0) {
            double windowSecsHere = window / (double) Math.max(1, this.rate);
            droppedTail = Math.max(0.0, outroDur - (autoCue.outroCutSecs + windowSecsHere));
        }
        return new TransitionPlan(entrySecs, PitchPlan.ZERO, introStartAt, noteWithActualWindow(),
                droppedTail);
    }

    private enum TempoSource {

        MODEL("model"),

        COMPUTED("hard computation");

        private final String label;

        TempoSource(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private static final class Budget {

        private double left = BeatModels.BUDGET_SECS;

        boolean spent() {
            return left <= 0.0;
        }

        void charge(long elapsedNanos) {
            left -= (double) elapsedNanos / 1e9;
        }
    }

    private TempoResultHolder tempoOf(float[] pcm, int fileRate, Budget budget) {
        String reason = null;
        if (!BeatModels.isInstalled()) {

            reason = "host did not install the inference backend";
        } else if (budget.spent()) {
            reason = "model budget exhausted";
        }

        if (reason == null) {
            long t0 = System.nanoTime();
            BeatDetector det = null;
            try {
                det = BeatModels.openPreferred();
                MelParams mp = det.melParams();

                float[] mono = BeatModels.resampleMono(pcm, fileRate, mp.sampleRate);
                float[] beats = det.beats(mono);

                Analysis.Tempo tempo = Analysis.tempoFromBeats(beats, fileRate);
                if (tempo == null) {
                    reason = "model returned no usable beats (fewer than two)";
                } else {
                    budget.charge(System.nanoTime() - t0);
                    Logger.debug("AI beat alignment: {} returned {} beats, {}",
                            det.label(), Integer.valueOf(beats.length), tempo.describe());
                    return new TempoResultHolder(tempo, TempoSource.MODEL);
                }
            } catch (RuntimeException e) {

                reason = e.getClass().getSimpleName() + ": " + briefMessage(e);
            } finally {
                if (det != null) {
                    det.close();
                }
                if (reason != null) {
                    budget.charge(System.nanoTime() - t0);
                }
            }
        }

        if (reason != null) {

            if (modelMissingReported) {
                Logger.debug("AI beat alignment: model still unavailable ({}), this side falls back to hard computation", reason);
            } else {
                modelMissingReported = true;
                Logger.warn("AI beat alignment: model unavailable ({}), this side falls back to hard computation"
                        + " (said once per process; every later transition still computes the hard way)", reason);
            }
        }

        Analysis.Tempo tracked = Analysis.tempoFromSpectralFluxTracked(pcm, fileRate);
        if (tracked != null) {
            return new TempoResultHolder(tracked, TempoSource.COMPUTED);
        }
        Logger.debug("AI beat alignment: new path gave no verdict ({}), falling back to the legacy RMS-difference path",
                BeatTracker.lastRejectReason());
        return new TempoResultHolder(Analysis.analyze(pcm, fileRate), TempoSource.COMPUTED);
    }

    static String readFailureNote(Automix.Mono tail, AudioException tailError,
                                  Automix.Mono head, AudioException headError) {
        if (tailError != null) {

            return "outgoing track analysis failed (" + tailError.getMessage() + "), falling back to a fixed window";
        }
        if (headError != null) {
            return "incoming track analysis failed (" + headError.getMessage() + "), falling back to a fixed window";
        }
        if (tail == null || head == null) {
            return "read interrupted (prepare thread was interrupted), falling back to a fixed window";
        }
        return null;
    }

    private static final class TempoResultHolder {

        final Analysis.Tempo tempo;

        final TempoSource source;

        TempoResultHolder(Analysis.Tempo tempo, TempoSource source) {
            this.tempo = tempo;
            this.source = source;
        }
    }

    private static String briefMessage(Throwable e) {
        String s = e.getMessage();
        if (s == null) {
            return e.toString();
        }
        int nl = s.indexOf('\n');
        String first = (nl >= 0 ? s.substring(0, nl) : s).trim();
        return first.length() > 200 ? first.substring(0, 200) + "…" : first;
    }

    private TransitionPlan failurePlan(String note) {
        return new TransitionPlan(0.0, PitchPlan.ZERO, 0.0f, note);
    }

    private String noteWithActualWindow() {
        String note = describePlan();
        double actual = window / (double) Math.max(1, this.rate);
        if (!(actual > 0.0) || note == null) {
            return note;
        }
        return note.replaceAll("overlap [0-9.]+s",
                String.format(Locale.ROOT, "overlap %.1fs", Double.valueOf(actual)));
    }

    private String describePlan() {
        String bpm;
        if (outroTempo != null && introTempo != null) {
            bpm = String.format(Locale.ROOT, "%.1f BPM（%s）→ %.1f BPM（%s）",
                    outroTempo.bpm, outroSource.label(),
                    introTempo.bpm, introSource.label());
        } else if (outroTempo != null) {
            bpm = String.format(Locale.ROOT, "exit %.1f BPM (%s), entry undetermined",
                    outroTempo.bpm, outroSource.label());
        } else if (introTempo != null) {
            bpm = String.format(Locale.ROOT, "exit undetermined, entry %.1f BPM (%s)",
                    introTempo.bpm, introSource.label());
        } else {
            bpm = "no beats determined, blending with a fixed window";
        }

        double secs = (double) window / (double) rate;
        String body = harmonicBlend != null
                ? String.format(Locale.ROOT, "harmonic blend (chosen by AI comparison, window %.1f s)",
                        Double.valueOf(secs))
                : String.format(Locale.ROOT, "%s (%s, window %.1f s)",
                        style.label(), bpm, Double.valueOf(secs));

        if (autoNote != null) {

            return autoNote + "；" + (harmonicBlend != null ? "actually ran " : "picked ") + body;
        }
        return body;
    }

    private int renderBeatCut(AudioBuf out, AudioBuf from, AudioBuf to, float p) {
        int n = Math.min(out.capacityFrames(), Math.max(from.frames(), to.frames()));
        if (n == 0) {
            out.setFrames(0);
            return 0;
        }
        float cut = cutAt;

        float perFrame = 1.0f / (float) Math.max(window, 1L);

        Float forcedFrom;
        if (from.frames() == 0) {
            if (cutForcedAt == null) {

                cutForcedAt = Float.valueOf(p);
            }
            forcedFrom = cutForcedAt;
        } else {
            cutForcedAt = null;
            forcedFrom = null;
        }

        out.setFrames(n);
        for (int ch = 0; ch < out.channels(); ch++) {
            FloatBuffer o = out.channelWriter(ch);
            for (int i = 0; i < n; i++) {
                float pi = Automix.minF32(p + (float) i * perFrame, 1.0f);
                if (forcedFrom != null) {

                    float start = forcedFrom.floatValue();
                    float span = Automix.minF32(
                            tuning.cutFadeSecs * (float) rate / (float) Math.max(window, 1L),
                            Automix.maxF32(1.0f - start, 1e-4f));
                    float local = Automix.clampF32((pi - start) / span, 0.0f, 1.0f);
                    Mixer.Gains g = Mixer.equalPowerGains(local);
                    o.put(i, sampleOf(from, ch, i) * g.from + sampleOf(to, ch, i) * g.to);
                } else if (pi < cut) {

                    o.put(i, sampleOf(from, ch, i));
                } else {
                    float local = cut >= 1.0f
                            ? 1.0f
                            : Automix.clampF32((pi - cut) / (1.0f - cut), 0.0f, 1.0f);
                    Mixer.Gains g = Mixer.equalPowerGains(local);
                    o.put(i, sampleOf(from, ch, i) * g.from + sampleOf(to, ch, i) * g.to);
                }
            }
        }
        return n;
    }

    private static final float HF_STAGGER = 0.35f;

    private int renderBassSwap(AudioBuf out, AudioBuf from, AudioBuf to, int n, float p) {
        return renderBassSwap(out, from, to, n, p, false);
    }

    private int renderBassSwap(AudioBuf out, AudioBuf from, AudioBuf to, int n, float p,
                               boolean sweep) {
        if (sweep && sweepOut != null) {

            double k = Math.max(0.0, Math.min(1.0, (double) p));
            float hz = (float) (tuning.filterSweepFromHz
                    * Math.pow(tuning.filterSweepToHz / (double) tuning.filterSweepFromHz, k));
            if (sweepHz < 0f || Math.abs(hz - sweepHz) > sweepHz * 0.005f) {
                sweepHz = hz;
                for (Biquad f : sweepOut) {
                    f.setLowPass(rate, hz, tuning.filterSweepQ);
                }
            }
        }

        float together = Automix.clampF32(tuning.bassSwapTogether, 0.0f, 0.9f);
        float hfF;
        float hfT;
        float lfF;
        float lfT;
        if (together <= 0.0f) {

            float split = Automix.clampF32(tuning.bassSwapSplit, 0.0f, 0.99f);
            if (split <= 0.0f) {
                hfF = 0.0f;
                hfT = 1.0f;
            } else {
                Mixer.Gains g = Mixer.equalPowerGains(Automix.minF32(p / split, 1.0f));
                hfF = g.from;
                hfT = g.to;
            }
            Mixer.Gains g = Mixer.equalPowerGains(
                    Automix.clampF32((p - split) / (1.0f - split), 0.0f, 1.0f));
            lfF = g.from;
            lfT = g.to;
        } else {

            float arc = (1.0f - together) * 0.5f;
            float theta = Automix.togetherAngle(p, together);
            float thetaLf;
            if (p <= arc) {
                thetaLf = 0.0f;
            } else if (p >= arc + together) {
                thetaLf = FRAC_PI_2;
            } else {
                thetaLf = ((p - arc) / together) * FRAC_PI_2;
            }

            float hfSplit = Automix.clampF32(tuning.bassSwapSplit, 0.0f, 0.99f);
            if (hfSplit <= 0.0f || HF_STAGGER <= 0.0f) {
                hfF = (float) Math.cos((double) theta);
                hfT = (float) Math.sin((double) theta);
            } else {

                Mixer.Gains hg = Mixer.equalPowerGains(Automix.minF32(p / hfSplit, 1.0f));
                float c = HF_STAGGER;
                hfF = (1.0f - c) * (float) Math.cos((double) theta) + c * hg.from;
                hfT = (1.0f - c) * (float) Math.sin((double) theta) + c * hg.to;
            }
            lfF = (float) Math.cos((double) thetaLf);
            lfT = (float) Math.sin((double) thetaLf);
        }

        int chs = Math.min(out.channels(), fromSplit.length);
        out.setFrames(n);

        float[] split = new float[2];
        for (int ch = 0; ch < chs; ch++) {
            SplitLowHigh fs = fromSplit[ch];
            SplitLowHigh ts = toSplit[ch];
            FloatBuffer o = out.channelWriter(ch);
            for (int i = 0; i < n; i++) {
                float aRaw = sampleOf(from, ch, i);
                if (sweep && sweepOut != null) {
                    aRaw = sweepOut[ch].process(aRaw);
                }
                fs.processInto(aRaw, split);
                float flo = split[0];
                float fhi = split[1];
                ts.processInto(sampleOf(to, ch, i), split);
                float tlo = split[0];
                float thi = split[1];

                o.put(i, flo * lfF + tlo * lfT + fhi * hfF + thi * hfT);
            }
        }

        for (int ch = chs; ch < out.channels(); ch++) {
            FloatBuffer o = out.channelWriter(ch);
            for (int i = 0; i < n; i++) {
                o.put(i, 0.0f);
            }
        }
        return n;
    }

    private int renderPlainBlend(AudioBuf out, AudioBuf from, AudioBuf to, float p) {
        Mixer.Gains g = gains(p);

        return Mixer.mixTwoMatched(out, from, g.from, to, g.to,
                levelMatcher, blendCurve(p) * tuning.levelMatch,
                lowDucker, blendCurve(p) * tuning.lowDuck);
    }

    private int renderLearnedEq(AudioBuf out, AudioBuf from, AudioBuf to, int n, float p) {
        Mixer.Gains g = gains(p);
        learnedEq.gainsAt(p, eqGains);
        int chs = Math.min(out.channels(), toSplit.length);
        out.setFrames(n);
        float[] split = new float[2];
        float[] split2 = new float[2];
        for (int ch = 0; ch < chs; ch++) {
            SplitLowHigh ts = toSplit[ch];
            SplitLowHigh ts2 = toSplit2[ch];
            FloatBuffer o = out.channelWriter(ch);
            for (int i = 0; i < n; i++) {
                float a = sampleOf(from, ch, i) * g.from;
                ts.processInto(sampleOf(to, ch, i), split);
                ts2.processInto(split[1], split2);
                float b = split[0] * eqGains[0] * g.to
                        + split2[0] * eqGains[1] * g.to
                        + split2[1] * eqGains[2] * g.to;
                o.put(i, a + b);
            }
        }
        for (int ch = chs; ch < out.channels(); ch++) {
            FloatBuffer o = out.channelWriter(ch);
            for (int i = 0; i < n; i++) {
                o.put(i, 0.0f);
            }
        }
        return n;
    }

    public void setLearnedEqCurve(EqCurve curve) {
        this.learnedEq = curve;
    }

    static final float EQ_MID_HZ = 2000f;

    private static boolean isEssentiallyMono(AudioBuf from) {
        int n = Math.min(from.frames(), 2 * Deck.RENDER_FRAMES);
        double midEnergy = 0.0;
        double sideEnergy = 0.0;
        FloatBuffer l = from.channel(0);
        FloatBuffer r = from.channel(1);
        for (int i = 0; i < n; i++) {
            double lv = l.get(i);
            double rv = r.get(i);
            double mid = (lv + rv) * 0.5;
            double side = (lv - rv) * 0.5;
            midEnergy += mid * mid;
            sideEnergy += side * side;
        }
        double total = midEnergy + sideEnergy;
        return total <= 0.0 || sideEnergy < SPACE_MONO_RATIO * total;
    }

    private static float blendCurve(float p) {

        if (p <= 0.0f || p >= 1.0f) {
            return 0.0f;
        }
        return (float) Math.sin(Math.PI * (double) p);
    }

    private int renderTailRide(AudioBuf out, AudioBuf from, AudioBuf to, float p) {
        float gf = (float) Math.sqrt((double) Automix.maxF32(
                1.0f - (float) Math.pow((double) p, (double) tuning.tailRideOutroPower), 0.0f));
        float gt = (float) Math.pow((double) p, (double) tuning.tailRideIntroPower);
        return Mixer.mixTwoMatched(out, from, gf, to, gt, levelMatcher,
                blendCurve(p) * tuning.levelMatch, lowDucker, blendCurve(p) * tuning.lowDuck);
    }

    private int renderShowMode(AudioBuf out, AudioBuf from, AudioBuf to, int n, float p) {

        MixTuning t = tuning;
        float hp = t.showHpFrom + (t.showHpTo - t.showHpFrom) * (p * p);

        float lp = t.showLpFrom + (t.showLpTo - t.showLpFrom) * (p * p * p);
        for (Biquad f : fromHp) {
            f.updateHighPass(rate, hp, 0.707f);
        }
        for (Biquad f : toLp) {
            f.updateLowPass(rate, lp, 0.707f);
        }

        float gf = (float) Math.pow((double) Automix.maxF32(1.0f - p, 0.0f),
                (double) t.showOutroGainPower);
        float gt = (float) Math.pow((double) p, (double) t.showIntroGainPower);

        int chs = Math.min(out.channels(), fromHp.length);
        out.setFrames(n);
        for (int ch = 0; ch < chs; ch++) {
            Biquad fh = fromHp[ch];
            Biquad tl = toLp[ch];
            FloatBuffer o = out.channelWriter(ch);
            for (int i = 0; i < n; i++) {
                float a = fh.process(sampleOf(from, ch, i)) * gf;
                float b = tl.process(sampleOf(to, ch, i)) * gt;
                o.put(i, a + b);
            }
        }
        for (int ch = chs; ch < out.channels(); ch++) {
            FloatBuffer o = out.channelWriter(ch);
            for (int i = 0; i < n; i++) {
                o.put(i, 0.0f);
            }
        }
        return n;
    }

    private static float sampleOf(AudioBuf buf, int ch, int i) {
        if (i < buf.frames() && ch < buf.channels()) {
            return buf.channel(ch).get(i);
        }
        return 0.0f;
    }

    @Override
    public String toString() {
        return "AiMix { style: \"" + style.specName() + "\", rate: " + rate
                + ", window: " + window
                + ", outro_bpm: " + optionDebug(outroBpm())
                + ", intro_bpm: " + optionDebug(introBpm()) + ", .. }";
    }

    private static String optionDebug(Float v) {
        return v == null ? "None" : "Some(" + v + ")";
    }

    private static volatile boolean autoPickNoVocalClash = true;

    public static void setAutoPickNoVocalClash(boolean on) {
        autoPickNoVocalClash = on;
    }

    public static boolean autoPickNoVocalClash() {
        return autoPickNoVocalClash;
    }

    private static Chooser.AutoChoice withKernelNote(Chooser.AutoChoice legacy,
                                                     String kernelNote) {
        if (legacy == null) {
            return null;
        }
        String why = legacy.why == null || legacy.why.isEmpty()
                ? kernelNote
                : legacy.why + "（" + kernelNote + "）";
        return new Chooser.AutoChoice(legacy.style, why);
    }

    private static double trailingSpanSecs(AutoCue.Envelope env) {
        if (env == null || env.rms == null || env.rms.length == 0) {
            return 0.0;
        }
        float peak = 0f;
        for (float v : env.rms) {
            peak = Math.max(peak, v);
        }
        if (peak <= 0f) {
            return 0.0;
        }
        float floor = peak * 0.1f;
        int last = -1;
        for (int i = 0; i < env.rms.length; i++) {
            if (env.rms[i] >= floor) {
                last = i;
            }
        }
        if (last < 0) {
            return 0.0;
        }

        double hop = 0.02;
        return Math.max(0.0, (env.rms.length - 1 - last) * hop);
    }

    private static boolean incomingIsQuieter(float[] tailPcm, Automix.Mono head, int rate) {
        if (tailPcm == null || head == null || head.pcm == null || rate <= 0) {
            return false;
        }
        double a = dev.t1m3.qplayer.audio.dsp.Lufs.integrated(tailPcm, null, rate);
        double b = dev.t1m3.qplayer.audio.dsp.Lufs.integrated(head.pcm, null, head.rate);
        return !Double.isInfinite(a) && !Double.isInfinite(b) && b < a;
    }

    private static double overlapStrengthOfSide(AutoCue.Envelope env, double fromSecs,
                                                double windowSecs) {
        double[] strength = AutoCue.vocalStrength(env);
        if (strength == null) {
            return -1.0;
        }
        int from = (int) Math.floor(fromSecs / AutoCue.HOP_SECS);
        int n = (int) Math.round(windowSecs / AutoCue.HOP_SECS);
        double best = 0.0;
        for (int k = 0; k < n; k++) {
            int i = from + k;
            if (i >= 0 && i < strength.length) {
                best = Math.max(best, strength[i]);
            }
        }
        return best;
    }

    private static double beatCount(double overlapSecs, AutoCue.Envelope env) {
        if (env == null || env.tempo == null || env.tempo.periodFrames <= 0 || env.beatRate <= 0) {
            return 0.0;
        }
        double beatSecs = env.tempo.periodFrames / (double) env.beatRate;
        if (beatSecs <= 0) {
            return 0.0;
        }
        double beats = overlapSecs / beatSecs;
        double wholeBarBeats = Math.floor(beats / 4.0) * 4.0;
        return Math.max(0.0, wholeBarBeats);
    }

    private static double estimateVocalOverlapSecs(float[] mono, int rate) {
        if (mono == null || mono.length == 0 || rate <= 0) {
            return 0.0;
        }
        float peak = 0f;
        for (float v : mono) {
            peak = Math.max(peak, Math.abs(v));
        }
        if (peak <= 0f) {
            return 0.0;
        }
        float floor = peak * 0.1f;
        int loud = 0;
        for (float v : mono) {
            if (Math.abs(v) >= floor) {
                loud++;
            }
        }
        return loud / (double) rate;
    }
}
