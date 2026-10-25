package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.AudioException;
import dev.t1m3.qplayer.audio.core.ChannelLayout;
import dev.t1m3.qplayer.audio.decode.Decoder;
import dev.t1m3.qplayer.audio.decode.TrackSpec;
import dev.t1m3.qplayer.audio.dsp.Resampler;
import dev.t1m3.qplayer.audio.model.InferException;
import dev.t1m3.qplayer.audio.model.StemSeparator;
import dev.t1m3.qplayer.audio.studio.AutotuneParams;
import dev.t1m3.qplayer.audio.studio.DereverbParams;
import dev.t1m3.qplayer.audio.studio.VocalPolisher;
import dev.t1m3.qplayer.audio.stem.StemSeparators;
import dev.t1m3.qplayer.util.Logger;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Locale;

public final class VocalSeparationTransition implements Transition {

    public static final float DEFAULT_BUDGET_SECS = 15.0f;

    static final long VOCAL_COVER_TOLERANCE_FRAMES = 2_400L;

    private static final int DECODE_CHUNK_FRAMES = 4096;

    private static final int RESAMPLE_MARGIN_FRAMES = 128;

    private static final String NOTE_DONE = "vocals removed";

    private static final String NOTE_SKIPPED = "vocals kept";

    private final Transition inner;

    private final int rate;

    private final int channels;

    private AudioBuf vocalScratch;

    private final int vocalMode;

    private final float budgetSecs;

    private final AutotuneParams autotune;

    private final DereverbParams dereverb;

    private float[] instrumental0;

    private float[] vocals0;

    private float[] instrumental1;

    private float[] vocals1;

    private static volatile boolean vocalSanityGate;

    public static void setVocalSanityGate(boolean on) {
        vocalSanityGate = on;
    }

    public static boolean vocalSanityGate() {
        return vocalSanityGate;
    }

    private int covered;

    private boolean separated;

    private double[] duckRanges;

    private float[] duckCurve;

    private boolean duckNothing;

    private String preparedNote;

    private java.util.Map<String, String> introHeaders;

    private int cursor;

    private boolean reportedExhausted;

    public VocalSeparationTransition(Transition inner, int rate, int channels, int vocalMode) {
        this(inner, rate, channels, vocalMode, DEFAULT_BUDGET_SECS);
    }

    public VocalSeparationTransition(Transition inner, int rate, int channels, int vocalMode,
                                     float budgetSecs) {
        this(inner, rate, channels, vocalMode, budgetSecs,
                AutotuneParams.off(), DereverbParams.off());
    }

    public VocalSeparationTransition(Transition inner, int rate, int channels, int vocalMode,
                                     float budgetSecs, AutotuneParams autotune,
                                     DereverbParams dereverb) {
        if (inner == null) {
            throw new IllegalArgumentException("wrapped kind must not be null: this layer only changes the material, the kind decides the rest");
        }
        this.inner = inner;
        this.rate = Math.max(rate, 1);
        this.channels = Math.max(channels, 1);
        this.vocalScratch = new AudioBuf(ChannelLayout.of(this.channels), 2 * Deck.RENDER_FRAMES);
        this.vocalMode = vocalMode;
        this.budgetSecs = budgetSecs;
        this.autotune = autotune == null ? AutotuneParams.off() : autotune;
        this.dereverb = dereverb == null ? DereverbParams.off() : dereverb;
    }

    public boolean polishesVocals() {
        return autotune.active() || dereverb.active();
    }

    public Transition inner() {
        return inner;
    }

    public int vocalMode() {
        return vocalMode;
    }

    public boolean separatedVocals() {
        return separated;
    }

    int coveredFrames() {
        return covered;
    }

    @Override
    public long windowFrames(int sampleRate) {
        return inner.windowFrames(sampleRate);
    }

    @Override
    public String name() {
        return inner.name() + " + vocal removal";
    }

    @Override
    public boolean rewritesSamples() {
        return inner.rewritesSamples();
    }

    @Override
    public PitchPlan pitchAt(float progress) {
        return inner.pitchAt(progress);
    }

    @Override
    public TransitionPlan prepare(String outro, String intro, int rate) {
        return prepare(outro, intro, rate, null, null);
    }

    @Override
    public TransitionPlan prepare(String outro, String intro, int rate,
                                  java.util.Map<String, String> outroHeaders,
                                  java.util.Map<String, String> introHeaders) {

        this.introHeaders = introHeaders;
        TransitionPlan plan = inner.prepare(outro, intro, rate, outroHeaders, introHeaders);

        duckRanges = plan == null ? null : plan.vocalDuckRanges;

        if (inner instanceof AutoCueTransition) {

            ((AutoCueTransition) inner).setVocalMode(vocalMode);

            ((AutoCueTransition) inner).setVocalDuckRanges(duckRanges);
        }
        duckCurve = plan == null ? null : plan.vocalDuckCurve;

        duckNothing = duckCurve == null && duckRanges != null && duckRanges.length == 0;

        if (duckCurve != null) {
            int peakHop = 0;
            for (int i = 1; i < duckCurve.length; i++) {
                if (duckCurve[i] > duckCurve[peakHop]) {
                    peakHop = i;
                }
            }
            dev.t1m3.qplayer.util.Logger.info(
                    "vocal removal: yielding along a continuous weight curve (peak {}/1 at {}s, "
                            + "the steepest change spread over 0.3 s)",
                    String.format(java.util.Locale.US, "%.2f", Float.valueOf(duckCurve[peakHop])),
                    String.format(java.util.Locale.US, "%.2f",
                            Double.valueOf(peakHop * AutoCue.HOP_SECS)));
        } else if (duckRanges == null) {
            dev.t1m3.qplayer.util.Logger.info(
                    "vocal removal: no overlap information (auto cue off, or no band envelope available) -> remove vocals across the whole window");
        } else if (duckRanges.length == 0) {
            dev.t1m3.qplayer.util.Logger.debug(
                    "vocal removal: the verdict is that nothing overlaps here -> no vocal removal at all"
                            + " (if you still hear two vocals at this point, that verdict was wrong)");
        } else {
            double total = 0.0;
            for (int i = 0; i + 1 < duckRanges.length; i += 2) {
                total += duckRanges[i + 1] - duckRanges[i];
            }
            dev.t1m3.qplayer.util.Logger.info(
                    "vocal removal: swapping material only in {} spans ({}s in total)",
                    Integer.valueOf(duckRanges.length / 2), String.format(
                            java.util.Locale.US, "%.2f", Double.valueOf(total)));
        }
        if (duckNothing) {
            return degrade(plan, "auto cue says nothing overlaps in this window -> no vocal removal this time");
        }

        if (preparedNote != null) {

            return withNote(plan, preparedNote);
        }

        long window = inner.windowFrames(rate);

        if (window <= 0) {

            return degrade(plan, "the window is 0 (hard join), there is nothing to separate");
        }

        if (Math.abs(plan.pitch.intro) > 1e-6f) {
            return degrade(plan, "the inner layer wants to shift the incoming track (" + plan.pitch.intro
                    + " semitones) -> no vocal removal this time (it would cut off the deck's pitch-shift path)");
        }
        if (!StemSeparators.available(vocalMode)) {

            return degrade(plan, StemSeparators.unavailableReason(vocalMode));
        }

        long t0 = System.nanoTime();
        String failure = separateInto(intro, plan.skipSecs, (int) Math.min(window, Integer.MAX_VALUE - 8L));
        double spentSecs = (double) (System.nanoTime() - t0) / 1e9;

        if (failure == null && spentSecs > (double) budgetSecs) {

            clearMaterial();
            failure = String.format(Locale.ROOT, "separation took %.1f s, over the %.1f s budget",
                    Double.valueOf(spentSecs), Float.valueOf(budgetSecs));
        }

        if (failure != null) {
            return degrade(plan, failure);
        }

        if (window - covered > VOCAL_COVER_TOLERANCE_FRAMES) {
            Logger.warn("blend vocal removal: {} covers only {} frames ({}.{} s) but the window needs {} frames (short by {} frames / {} ms) -- "
                            + "the tail stays unseparated (if the incoming track is long enough, too little was read this time)",
                    StemSeparators.label(vocalMode), Integer.valueOf(covered),
                    Integer.valueOf(covered / rate),
                    String.format(java.util.Locale.ROOT, "%03d",
                            Integer.valueOf((int) Math.round(covered % rate * 1000.0 / rate))),
                    Long.valueOf(window), Long.valueOf(window - covered),
                    Long.valueOf((window - covered) * 1000L / Math.max(1, rate)));
        }
        Logger.debug("blend vocal removal: {} separated the incoming window ({} frames, took {} s)",
                StemSeparators.label(vocalMode), Integer.valueOf(covered),
                String.format(Locale.ROOT, "%.1f", Double.valueOf(spentSecs)));
        String ok = NOTE_DONE + "（" + StemSeparators.label(vocalMode) + ", window "
                + String.format(Locale.ROOT, "%.1f", Double.valueOf((double) covered / (double) rate))
                + " s）";
        preparedNote = ok;
        return withNote(plan, ok);
    }

    public static float[][] accompanimentWindow(String path, java.util.Map<String, String> headers,
                                                double startSecs, int frames, int rate, int mode) {
        float[][] material = readWindow(path, headers, startSecs, frames, rate);
        if (material == null || material.length < 2 || material[0].length == 0) {
            return null;
        }
        StemSeparator sep = StemSeparators.forMode(mode);
        if (sep == null) {
            return null;
        }
        StemSeparator.StemSet set = sep.separate(material[0], material[1], rate);
        if (set == null || set.frames() != material[0].length) {
            return null;
        }
        return new float[][] {set.instrumentalLeft, set.instrumentalRight};
    }

    public static float[][] stemsWindow(String path, java.util.Map<String, String> headers,
                                        double startSecs, int frames, int rate, int mode) {
        try {
            float[][] material = readWindow(path, headers, startSecs, frames, rate);
            if (material == null || material.length < 2 || material[0].length == 0) {
                return null;
            }
            StemSeparator sep = StemSeparators.forMode(mode);
            if (sep == null) {
                return null;
            }
            StemSeparator.StemSet set = sep.separate(material[0], material[1], rate);
            if (set == null || set.frames() != material[0].length) {
                return null;
            }
            return new float[][] {set.instrumentalLeft, set.instrumentalRight,
                    set.vocalsLeft, set.vocalsRight};
        } catch (RuntimeException e) {
            return null;
        }
    }

    static float[][] readWindow(String path, java.util.Map<String, String> headers,
                                double startSecs, int frames, int rate) {
        return Automix.readStereoWindow(path, headers, startSecs, frames, rate);
    }

    private String separateInto(String intro, double startSecs, int windowFrames) {
        StemSeparator sep = null;
        try {

            sep = StemSeparators.forMode(vocalMode);
            float[][] material = readIntroWindow(intro, introHeaders, startSecs, windowFrames);
            float[] left = material[0];
            float[] right = material[1];
            if (left.length == 0) {
                return "the incoming track has no content past this point";
            }

            StemSeparator.StemSet set = sep.separate(left, right, rate);
            if (set == null) {
                return "the stem separator returned null";
            }

            if (set.frames() != left.length) {
                return "stem result length differs from the input (the seam requires them to match): "
                        + set.frames() + " ≠ " + left.length;
            }

            assemble(set);

            if (vocalLevelIsNegligible()) {
                clearMaterial();
                separated = false;
                return "this part of the incoming track has no vocals anyway (the vocal track is below "
                        + String.format(Locale.ROOT, "%.0f%%", Double.valueOf(VOCAL_ABSENT_RATIO * 100.0))
                        + ") -> no vocal removal this time (saving the separation)";
            }

            polishVocals();
            covered = left.length;
            separated = true;
            return null;
        } catch (InferException e) {

            return "inference failed (" + InferException.brief(e) + "）";
        } catch (AudioException e) {
            return "reading the incoming track failed (" + e.getMessage() + "）";
        } catch (RuntimeException e) {

            return e.getClass().getSimpleName() + "：" + InferException.brief(e);
        } finally {
            closeQuietly(sep);
        }
    }

    private void assemble(StemSeparator.StemSet set) {
        if (channels < 2) {
            instrumental0 = average(set.instrumentalLeft, set.instrumentalRight);
            vocals0 = average(set.vocalsLeft, set.vocalsRight);
            instrumental1 = null;
            vocals1 = null;
            return;
        }
        instrumental0 = set.instrumentalLeft;
        vocals0 = set.vocalsLeft;
        instrumental1 = set.instrumentalRight;
        vocals1 = set.vocalsRight;
    }

    private void polishVocals() {
        if (!polishesVocals() || vocals0 == null || vocals1 == null) {
            return;
        }
        dev.t1m3.qplayer.audio.studio.VocalPolisher polisher =
                new dev.t1m3.qplayer.audio.studio.VocalPolisher(rate, autotune, dereverb);
        int frames = polisher.polish(vocals0, vocals1);
        if (frames > 0) {
            Logger.debug("blend vocal post-processing: {} ({} frames, applied to the vocal track only)", polisher, Integer.valueOf(frames));
        }
    }

    private static float[] average(float[] a, float[] b) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (a[i] + b[i]) * 0.5f;
        }
        return out;
    }

    static final double VOCAL_ABSENT_RATIO = 0.05;

    private boolean vocalLevelIsNegligible() {
        return vocalAbsent(vocals0, vocals1, instrumental0, instrumental1);
    }

    static boolean vocalAbsent(float[] vocals0, float[] vocals1,
                               float[] instrumental0, float[] instrumental1) {
        double vocals = meanAbs(vocals0) + meanAbs(vocals1);
        double instrumental = meanAbs(instrumental0) + meanAbs(instrumental1);
        if (vocals < 0.0 || instrumental <= 1e-9) {
            return false;
        }
        return vocals < instrumental * VOCAL_ABSENT_RATIO;
    }

    static double duckCurveAt(float[] curve, double secs) {
        if (curve == null || curve.length == 0) {
            return 0.0;
        }
        double pos = secs / AutoCue.HOP_SECS;
        if (pos <= 0.0) {
            return curve[0];
        }
        if (pos >= curve.length - 1) {
            return curve[curve.length - 1];
        }
        int i = (int) Math.floor(pos);
        double frac = pos - i;
        return curve[i] * (1.0 - frac) + curve[i + 1] * frac;
    }

    static double duckWeight(double[] ranges, double secs) {
        if (ranges == null) {
            return 1.0;
        }
        double best = 0.0;
        for (int i = 0; i + 1 < ranges.length; i += 2) {
            double from = ranges[i];
            double to = ranges[i + 1];
            double w;
            if (secs < from - DUCK_EDGE_RAMP_SECS || secs > to + DUCK_EDGE_RAMP_SECS) {
                continue;
            }
            if (secs < from) {
                w = (secs - (from - DUCK_EDGE_RAMP_SECS)) / DUCK_EDGE_RAMP_SECS;
            } else if (secs <= to) {
                w = 1.0;
            } else {
                w = ((to + DUCK_EDGE_RAMP_SECS) - secs) / DUCK_EDGE_RAMP_SECS;
            }
            best = Math.max(best, Math.max(0.0, Math.min(1.0, w)));
        }
        return best;
    }

    static final double DUCK_EDGE_RAMP_SECS = 0.03;

    private static double meanAbs(float[] track) {
        if (track == null || track.length == 0) {
            return -1.0;
        }
        double sum = 0.0;
        for (int i = 0; i < track.length; i++) {
            sum += Math.abs(track[i]);
        }
        return sum / track.length;
    }

    private void clearMaterial() {
        instrumental0 = null;
        vocals0 = null;
        instrumental1 = null;
        vocals1 = null;
        covered = 0;
        separated = false;
    }

    private TransitionPlan degrade(TransitionPlan plan, String reason) {
        clearMaterial();
        Logger.warn("blend vocal removal: {}, the transition proceeds as usual but without vocal removal", reason);
        String suffix = NOTE_SKIPPED + "（" + reason + "）";
        preparedNote = suffix;
        return withNote(plan, suffix);
    }

    private static TransitionPlan withNote(TransitionPlan plan, String suffix) {
        String note = plan.note == null || plan.note.isEmpty() ? suffix : plan.note + " · " + suffix;
        return new TransitionPlan(plan.skipSecs, plan.pitch, plan.introStartAt, note,
                plan.outroTailSecs);
    }

    @Override
    public int render(AudioBuf out, AudioBuf from, AudioBuf to, float progress) {
        int n = to.frames();
        AudioBuf material = to;
        if (separated && n > 0) {
            if (n > vocalScratch.capacityFrames() || to.channels() != vocalScratch.channels()) {

                vocalScratch = new AudioBuf(ChannelLayout.of(to.channels()), Math.max(n, vocalScratch.capacityFrames()));
            }
            vocalScratch.clear();
            vocalScratch.append(to);
            mixInto(vocalScratch, n, progress);
            material = vocalScratch;
        }
        int written = inner.render(out, from, material, progress);

        cursor += Math.max(0, Math.min(n, written));

        if (separated && !reportedExhausted && cursor >= covered && progress < 1.0f) {
            reportedExhausted = true;
            Logger.info("blend vocal removal: separated material runs out at {} percent of the window ({} frames of material / {} frames of window), "
                            + "the rest stays unseparated",
                    String.format(Locale.ROOT, "%.0f", Float.valueOf(progress * 100.0f)),
                    Integer.valueOf(covered), Long.valueOf(inner.windowFrames(rate)));
        }
        return written;
    }

    private void mixInto(AudioBuf to, int n, float progress) {

        float perFrame = 1.0f / (float) Math.max(inner.windowFrames(rate), 1L);
        int chs = Math.min(to.channels(), 2);
        for (int ch = 0; ch < chs; ch++) {
            float[] instrumental = ch == 0 ? instrumental0 : instrumental1;
            float[] vocals = ch == 0 ? vocals0 : vocals1;
            if (instrumental == null || vocals == null) {
                continue;
            }
            FloatBuffer o = to.channelWriter(ch);
            for (int i = 0; i < n; i++) {
                int idx = cursor + i;
                if (idx < 0 || idx >= covered) {
                    continue;
                }

                double secs = (double) idx / (double) Math.max(rate, 1);
                double duck = duckCurve != null ? duckCurveAt(duckCurve, secs)
                        : duckWeight(duckRanges, secs);
                if (duck <= 0.0) {
                    continue;
                }

                float p = Automix.minF32(progress + (float) i * perFrame, 1.0f);
                float envelope = Automix.smoothstep(0.0f, 1.0f, p);

                int blk = Math.min(256, Math.min(n - i, covered - idx));
                double so = 0.0;
                double ss = 0.0;
                for (int j = 0; j < Math.max(0, blk); j++) {
                    double v0 = o.get(i + j);
                    double v1 = instrumental[idx + j] + vocals[idx + j] * envelope;
                    so += v0 * v0;
                    ss += v1 * v1;
                }
                if (vocalSanityGate && ss * 16.0 < so) {

                    continue;
                }

                float separated = instrumental[idx] + vocals[idx] * envelope;
                if (duck >= 1.0) {
                    o.put(i, separated);
                } else {

                    o.put(i, (float) (o.get(i) * (1.0 - duck) + separated * duck));
                }
            }
        }
    }

    private float[][] readIntroWindow(String intro, double startSecs, int windowFrames) {
        return readIntroWindow(intro, null, startSecs, windowFrames);
    }

    private float[][] readIntroWindow(String intro, java.util.Map<String, String> headers,
                                      double startSecs, int windowFrames) {

        return Automix.readStereoWindow(intro, headers, startSecs, windowFrames, rate);
    }

    private static int copyOut(int produced, float[] dst, int at, int limit, AudioBuf src) {
        int n = Math.min(Math.min(produced, src.frames()), limit - at);
        if (n <= 0) {
            return 0;
        }
        FloatBuffer r = src.channel(0);
        for (int i = 0; i < n; i++) {
            dst[at + i] = r.get(i);
        }
        return n;
    }

    private static void closeQuietly(StemSeparator sep) {
        if (sep == null) {
            return;
        }
        try {
            sep.close();
        } catch (RuntimeException e) {

            Logger.warn("blend vocal removal: releasing the stem session failed (does not affect the transition): {}", InferException.brief(e));
        }
    }

    @Override
    public String toString() {
        return "VocalSeparationTransition{" + name()
                + ", separated=" + separated
                + ", covered=" + covered
                + ", budget=" + budgetSecs + "s}";
    }
}
