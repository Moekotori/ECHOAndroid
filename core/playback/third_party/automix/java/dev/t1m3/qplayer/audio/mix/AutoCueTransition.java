package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;

import java.nio.FloatBuffer;
import dev.t1m3.qplayer.util.Logger;

public final class AutoCueTransition implements Transition {

    private final TransitionLimiter limiter;

    private static final float HEADROOM_DB = 0.5f;

    private static volatile boolean antiClipEnabled = true;

    public static void setAntiClipEnabled(boolean on) {
        antiClipEnabled = on;
    }

    public static boolean antiClipEnabled() {
        return antiClipEnabled;
    }

    private final Transition base;

    private float headroomDb = HEADROOM_DB;

    static final double LOOP_ROUTE_WINDOW_SECS = 1.5;

    static final double PHASE_JOINT_MAX_SECS = 0.09;

    public double probeFinalWindowSecs() {
        return windowFrames(rate) / (double) Math.max(1, rate);
    }

    public static double probeLoopRouteWindowSecs() {
        return LOOP_ROUTE_WINDOW_SECS;
    }

    static final double[] CARVE_BAND_HZ = {60, 120, 250, 500, 1000, 2000, 4000, 8000};

    static final float CARVE_GAIN_DB = -4.0f;

    static final double CARVE_WITHIN_DB = 4.0;

    static final double CARVE_MAX_DB = 3.0;

    private final int rate;
    private final int channels;

    private final java.util.function.DoubleSupplier liveWindowSecs;

    private final boolean tuneWindow;

    private long autoWindow;

    private double autoEntrySecs;

    private double autoTailSecs;

    private String autoNote = "";

    static double relativePhase(AutoCue.Envelope env, double secs, double periodSecs) {
        double f = secs / periodSecs;
        return (f - Math.floor(f)) * periodSecs;
    }

    private static double worstGapBefore(AutoCue.Cue cue, double pA, double pB) {
        return 0.0;
    }

    private boolean autoTuningForStyleLoop() {
        return base instanceof AiMix && ((AiMix) base).tuningForStyleLoop();
    }

    public AutoCueTransition(Transition base, int rate, int channels) {
        this(base, rate, channels, null, false);
    }

    public AutoCueTransition(Transition base, int rate, int channels,
                             java.util.function.DoubleSupplier liveWindowSecs,
                             boolean tuneWindow) {
        this(base, rate, channels, liveWindowSecs, tuneWindow, MixTuning.defaults());
    }

    public AutoCueTransition(Transition base, int rate, int channels,
                             java.util.function.DoubleSupplier liveWindowSecs,
                             boolean tuneWindow, MixTuning tuning) {
        this.base = base;
        this.rate = rate;

        this.limiter = new TransitionLimiter(rate, channels);
        this.channels = channels;
        this.liveWindowSecs = liveWindowSecs;
        this.tuneWindow = tuneWindow;
        this.tuning = tuning == null ? MixTuning.defaults() : new MixTuning(tuning);
    }

    private final MixTuning tuning;

    private static final SegmentFeed[] NO_SEGMENTS = new SegmentFeed[0];

    private SegmentFeed[] segmentedOutgoing = NO_SEGMENTS;

    private int segmentIndex;

    private AudioBuf segmentScratch;

    private long segmentWindowFrames;

    private int segmentInsertFrames;

    private double segmentCutSecs;

    private double segmentFadeStartSecs;

    private double segmentTailSecs;

    private boolean explicitPath;

    public interface MaterialSource {

        float[][] read(String path, java.util.Map<String, String> headers, double startSecs,
                       int frames, int rate);
    }

    private static volatile MaterialSource materialSource;

    public static void installMaterialSource(MaterialSource source) {
        materialSource = source;
    }

    private MaterialSource instanceSource;

    public void setMaterialSource(MaterialSource source) {
        this.instanceSource = source;
    }

    float[][] materialFor(String path, java.util.Map<String, String> headers,
                          double startSecs, int frames, int rate) {

        double[] ranges = vocalDuckRanges;
        double anchor = materialAnchorSecs;
        if (ranges != null && ranges.length >= 2 && materialVocalMode != 0
                && !Double.isNaN(anchor) && frames > 0) {
            StemMaterial stems = StemMaterials.forWindow(
                    path, headers, startSecs, frames, rate, materialVocalMode);
            if (stems != null && stems.hasStems() && stems.frames() >= frames) {
                float[] gain = new float[frames];
                for (int i = 0; i < frames; i++) {
                    double timeline = (startSecs - anchor) + i / (double) Math.max(1, rate);
                    gain[i] = (float) vocalGainAt(timeline);
                }
                return matchLoudness(stems.mixWith(gain), rate);
            }

        }
        float[][] material = materialForRaw(path, headers, startSecs, frames, rate);
        return matchLoudness(material, rate);
    }

    private volatile double[] vocalDuckRanges;

    private volatile double materialAnchorSecs = Double.NaN;

    private volatile int materialVocalMode;

    private WindowTicket windowTicket = WindowTicket.identity(0.0);

    public WindowTicket windowTicket() {
        return windowTicket;
    }

    private WindowTicket buildWindowTicket(String outro, java.util.Map<String, String> headers,
                                          AutoCue.Cue cue, int rate) {
        try {
            int frames = (int) Math.round(cue.windowSecs * rate);
            if (frames <= 0) {
                return WindowTicket.identity(cue.outroCutSecs);
            }
            float[][] window = Automix.readStereoWindow(outro, headers, cue.outroCutSecs,
                    frames, rate);
            if (window == null) {
                return WindowTicket.identity(cue.outroCutSecs);
            }
            WindowTicket ticket = WindowTicket.build(window, rate, vocalDuckRanges,
                    cue.outroCutSecs, Double.NaN);
            if (ticket.doesAnything()) {
                dev.t1m3.qplayer.util.Logger.info(String.format(java.util.Locale.ROOT,
                        "window ticket: vocal curve %d frames, loudness gain %.4f",
                        Integer.valueOf(ticket.vocalGainFrames()),
                        Double.valueOf(ticket.loudnessGain)));
            }
            return ticket;
        } catch (RuntimeException e) {
            return WindowTicket.identity(cue == null ? 0.0 : cue.outroCutSecs);
        }
    }

    private boolean logRefineMeasurement = true;

    private volatile boolean refineCueTimingEnabled = true;

    public void setRefineCueTimingEnabled(boolean on) {
        this.refineCueTimingEnabled = on;
    }

    private static double beatPhase(double secs, Analysis.Tempo tempo,
                                    int gridRate) {
        if (tempo == null || tempo.periodFrames <= 0 || gridRate <= 0) {
            return 0.0;
        }
        double frames = secs * gridRate - tempo.phaseFrames;
        double m = frames % tempo.periodFrames;
        return m < 0 ? m + tempo.periodFrames : m;
    }

    private double refineSecs(String path, java.util.Map<String, String> headers, double secs) {
        try {
            int frames = (int) Math.round(0.2 * rate);
            float[][] win = Automix.readStereoWindow(path, headers,
                    Math.max(0.0, secs - 0.1), frames, rate);
            if (win == null || win.length == 0 || win[0].length < frames) {
                return secs;
            }
            float[] mono = new float[frames];
            for (int i = 0; i < frames; i++) {
                float acc = 0f;
                for (int c = 0; c < win.length; c++) {
                    acc += win[c][i];
                }
                mono[i] = acc / win.length;
            }
            int approx = (int) Math.round(0.1 * rate);
            int refined = BeatRefine.refineFrame(mono, rate, approx);
            return secs + (refined - approx) / (double) rate;
        } catch (RuntimeException e) {
            return secs;
        }
    }

    private void measureRefinement(String path, java.util.Map<String, String> headers,
                                   double secs, String label) {
        try {
            int frames = (int) Math.round(0.2 * rate);
            float[][] win = Automix.readStereoWindow(path, headers,
                    Math.max(0.0, secs - 0.1), frames, rate);
            if (win == null || win.length == 0 || win[0].length < frames) {
                return;
            }
            float[] mono = new float[frames];
            for (int i = 0; i < frames; i++) {
                float acc = 0f;
                for (int c = 0; c < win.length; c++) {
                    acc += win[c][i];
                }
                mono[i] = acc / win.length;
            }
            int approx = (int) Math.round(0.1 * rate);
            int refined = BeatRefine.refineFrame(mono, rate, approx);
            double deltaMs = (refined - approx) * 1000.0 / rate;
            if (Math.abs(deltaMs) >= 0.05) {
                dev.t1m3.qplayer.util.Logger.info(String.format(java.util.Locale.ROOT,
                        "frame refinement (%s): at %.4f s can move %.1f ms (envelope grid 20 ms)",
                        label, Double.valueOf(secs), Double.valueOf(deltaMs)));
            }
        } catch (RuntimeException e) {
            dev.t1m3.qplayer.util.Logger.debug("frame refinement measurement skipped ({})",
                    e.toString());
        }
    }

    private volatile int installedVocalMode;

    public void setVocalMode(int mode) {
        this.installedVocalMode = mode;
    }

    public int installedVocalMode() {
        return installedVocalMode;
    }

    private int vocalModeForMaterial() {
        return installedVocalMode;
    }

    void setMaterialContext(double anchorSecs, int vocalMode) {
        this.materialAnchorSecs = anchorSecs;
        this.materialVocalMode = vocalMode;
    }

    static final double DUCK_EDGE_SECS = 0.05;

    public void setVocalDuckRanges(double[] ranges) {
        this.vocalDuckRanges = ranges == null ? null : ranges.clone();
    }

    public double[] vocalDuckRanges() {
        return vocalDuckRanges == null ? null : vocalDuckRanges.clone();
    }

    double vocalGainAt(double timelineSecs) {
        double[] ranges = vocalDuckRanges;
        if (ranges == null || ranges.length < 2) {
            return 1.0;
        }
        double gain = 1.0;
        for (int i = 0; i + 1 < ranges.length; i += 2) {
            double a = ranges[i];
            double b = ranges[i + 1];
            if (b <= a) {
                continue;
            }
            if (timelineSecs <= a || timelineSecs >= b) {
                continue;
            }

            double in = Math.min(1.0, (timelineSecs - a) / DUCK_EDGE_SECS);
            double out = Math.min(1.0, (b - timelineSecs) / DUCK_EDGE_SECS);
            double depth = 0.5 - 0.5 * Math.cos(Math.PI * Math.min(in, out));
            gain = Math.min(gain, 1.0 - depth);
        }
        return Math.max(0.0, Math.min(1.0, gain));
    }

    static final double TARGET_LUFS = -16.0;

    private float[][] matchLoudness(float[][] material, int rate) {
        if (material == null || material.length == 0 || rate <= 0) {
            return material;
        }
        if (!dev.t1m3.qplayer.audio.dsp.Lufs.normalizationEnabled()) {
            return material;
        }
        double sum = 0.0;
        int n = 0;
        for (float[] ch : material) {
            if (ch == null || ch.length == 0) {
                continue;
            }
            double lufs = dev.t1m3.qplayer.audio.dsp.Lufs.integrated(ch, null, rate);
            if (!Double.isInfinite(lufs) && !Double.isNaN(lufs)) {

                sum += lufs;
                n++;
            }
        }
        if (n == 0) {
            return material;
        }
        double current = sum / n;
        double db = TARGET_LUFS - current;
        db = Math.max(-dev.t1m3.qplayer.audio.dsp.Lufs.MAX_NORMALIZE_DB,
                Math.min(dev.t1m3.qplayer.audio.dsp.Lufs.MAX_NORMALIZE_DB, db));
        double gain = Math.pow(10.0, db / 20.0);
        for (float[] ch : material) {
            if (ch == null) {
                continue;
            }
            for (int i = 0; i < ch.length; i++) {
                ch[i] = (float) (ch[i] * gain);
            }
        }
        return material;
    }

    float[][] materialForRaw(String path, java.util.Map<String, String> headers,
                          double startSecs, int frames, int rate) {
        MaterialSource src = instanceSource;
        if (src != null) {
            float[][] out = src.read(path, headers, startSecs, frames, rate);
            if (out != null) {
                return out;
            }
        }
        return readWindowMaterial(path, headers, startSecs, frames, rate);
    }

    static float[][] readWindowMaterial(String path, java.util.Map<String, String> headers,
                                        double startSecs, int frames, int rate) {
        MaterialSource src = materialSource;
        if (src != null) {
            float[][] out = src.read(path, headers, startSecs, frames, rate);
            if (out != null) {
                return out;
            }
        }
        return Automix.readStereoWindow(path, headers, startSecs, frames, rate);
    }

    private SegmentFeed stretchFeed;

    private double introSpeedStart = 1.0;
    private int introSpeedWindow = 0;

    private SegmentFeed[] introOutgoing = new SegmentFeed[0];

    private int introIndex;

    private AudioBuf introScratch;

    private double stretchSpeed = 1.0;

    private double stretchSpeedStart = 1.0;

    private double stretchSpeedMean = 1.0;

    private TempoRamp rampPlan;

    private SegmentFeed loopFeed;

    private AutoCue.OutroLoopPlan loopPlan;

    private static final class SegmentFeed {

        final String source;

        final double startSecs;

        final double endSecs;

        final int repeat;

        final float[][] pcm;

        final int passFrames;

        final int seamFrames;

        final long windowFrames;

        final long windowOffset;

        final int insertFrames;

        final double rotateStartSecs;

        final int rotFrames;

        int cursor;

        long delivered;

        SegmentFeed(String source, double startSecs, double endSecs, int repeat, float[][] pcm,
                    int passFrames, int seamFrames, long windowFrames, long windowOffset,
                    int insertFrames) {
            this(source, startSecs, endSecs, repeat, pcm, passFrames, seamFrames, windowFrames,
                    windowOffset, insertFrames, Double.NaN, 0);
        }

        SegmentFeed(String source, double startSecs, double endSecs, int repeat, float[][] pcm,
                    int passFrames, int seamFrames, long windowFrames, long windowOffset,
                    int insertFrames, double rotateStartSecs, int rotFrames) {
            this.source = source;
            this.startSecs = startSecs;
            this.endSecs = endSecs;
            this.repeat = Math.max(1, repeat);
            this.pcm = pcm;
            this.passFrames = passFrames;
            this.seamFrames = seamFrames;
            this.windowFrames = windowFrames;
            this.windowOffset = windowOffset;
            this.insertFrames = insertFrames;
            this.rotateStartSecs = rotateStartSecs;
            this.rotFrames = rotFrames;
        }

        SegmentFeed withWindow(int seamFrames, long windowFrames, long windowOffset,
                               int insertFrames) {
            return new SegmentFeed(source, startSecs, endSecs, repeat, pcm, passFrames,
                    seamFrames, windowFrames, windowOffset, insertFrames, rotateStartSecs,
                    rotFrames);
        }

        long totalFrames() {
            return (long) repeat * (long) passFrames;
        }

        float progressFor(float orchestratorProgress) {
            if (windowFrames <= 0L) {
                return orchestratorProgress;
            }
            return loopProgress(windowOffset + delivered, windowFrames, insertFrames);
        }

        float exhaustedProgress(float orchestratorProgress) {
            return windowFrames <= 0L ? orchestratorProgress : 1.0f;
        }

        double secsAt(long localPos, int rate) {
            if (passFrames <= 0) {
                return startSecs;
            }
            int pos = (int) (localPos % passFrames);
            int perSec = Math.max(1, rate);
            if (rotFrames <= 0 || rotFrames >= passFrames) {
                return startSecs + pos / (double) perSec;
            }
            int head = passFrames - rotFrames;
            return pos < head
                    ? rotateStartSecs + pos / (double) perSec
                    : startSecs + (pos - head) / (double) perSec;
        }

        @Override
        public String toString() {
            return (source == null ? "?" : source) + "[" + startSecs + "→" + endSecs + ")"
                    + (rotFrames > 0 ? ("↻" + rotateStartSecs) : "")
                    + (repeat > 1 ? ("×" + repeat) : "");
        }
    }

    public double stretchedSpeed() {
        return stretchSpeed;
    }

    public double stretchedStartSpeed() {
        return stretchSpeedStart;
    }

    public double stretchedMeanSpeed() {
        return stretchSpeedMean;
    }

    public TempoRamp tempoRamp() {
        return rampPlan;
    }

    public boolean hasStretchedOutgoing() {
        return stretchFeed != null;
    }

    static final double LOOP_SEAM_FADE_SECS = 0.02;

    public AutoCue.OutroLoopPlan outroLoopPlan() {
        return loopPlan;
    }

    public boolean hasLoopOutgoing() {
        return loopFeed != null;
    }

    public double loopCutSecs() {
        return (loopFeed == null && !explicitPath) ? 0.0 : segmentCutSecs;
    }

    public long loopWindowFrames() {
        return loopFeed == null ? 0L : segmentWindowFrames;
    }

    public double outroLoopLeadInSecs() {
        return loopFeed == null ? 0.0
                : (double) Math.max(0, segmentInsertFrames) / (double) Math.max(1, this.rate);
    }

    public long loopFadeFrames() {
        return loopFeed == null ? 0L : segmentWindowFrames - segmentInsertFrames;
    }

    public boolean usesExplicitPath() {
        return explicitPath;
    }

    public int outgoingSegmentCount() {
        return segmentedOutgoing.length;
    }

    public long outgoingWindowFrames() {
        return segmentWindowFrames;
    }

    @Override
    public long windowFrames(int sampleRate) {
        if (autoWindow > 0L) {
            return autoWindow;
        }
        return base.windowFrames(sampleRate);
    }

    @Override
    public int render(AudioBuf out, AudioBuf from, AudioBuf to, float progress) {

        if (introOutgoing.length > 0 && to != null && to.frames() > 0) {
            if (!fillIntroScratch(to.frames(), to.channels())) {
                return renderSegments(out, from, to, progress);
            }
            int n = renderSegments(out, from, introScratch, progress);
            SegmentFeed seg = introIndex < introOutgoing.length ? introOutgoing[introIndex] : null;
            if (seg != null) {
                int used = Math.max(0, Math.min(to.frames(), n));
                seg.cursor = seg.passFrames <= 0 ? 0 : (seg.cursor + used) % seg.passFrames;
                seg.delivered += used;
            }
            return finish(out, n, progress);
        }
        return finish(out, renderSegments(out, from, to, progress), progress);
    }

    private int finish(AudioBuf out, int n, float progress) {
        if (n <= 0 || out == null || !antiClipEnabled) {
            return n;
        }
        float p = Automix.clampF32(progress, 0.0f, 1.0f);
        double shape = Math.sin(Math.PI * Math.min(1.0, Math.max(0.0, (double) p)));
        if (shape > 1e-4) {
            float g = (float) Math.pow(10.0, -headroomDb * shape / 20.0);
            if (g < 0.9999f) {
                int chs = Math.min(out.channels(), 2);
                for (int ch = 0; ch < chs; ch++) {
                    FloatBuffer o = out.channelWriter(ch);
                    for (int i = 0; i < n; i++) {
                        o.put(i, o.get(i) * g);
                    }
                }
            }
        }
        limiter.process(out, n);
        return n;
    }

    private boolean fillIntroScratch(int want, int channels) {
        while (introIndex < introOutgoing.length) {
            SegmentFeed seg = introOutgoing[introIndex];
            if (seg.totalFrames() - seg.delivered > 0L) {
                fillScratchInto(seg, want, channels, true);
                return true;
            }
            introIndex++;
        }
        return false;
    }

    private int renderSegments(AudioBuf out, AudioBuf from, AudioBuf to, float progress) {
        if (segmentedOutgoing.length == 0) {
            if (base instanceof AiMix) {

                ((AiMix) base).setWindowSeconds(windowFrames(rate) / (double) Math.max(1, rate));
            }
            return base.render(out, from, to, progress);
        }
        while (true) {
            if (segmentIndex >= segmentedOutgoing.length) {

                SegmentFeed last = segmentedOutgoing[segmentedOutgoing.length - 1];
                if (base instanceof AiMix) {

                    ((AiMix) base).setWindowSeconds(windowFrames(rate) / (double) Math.max(1, rate));
                }
                return base.render(out, from, to, last.exhaustedProgress(progress));
            }
            SegmentFeed seg = segmentedOutgoing[segmentIndex];
            float mapped = seg.progressFor(progress);
            long left = seg.totalFrames() - seg.delivered;
            if (left <= 0L) {
                segmentIndex++;
                continue;
            }
            int want = (int) Math.min((long) from.frames(), left);
            if (want <= 0) {

                if (base instanceof AiMix) {

                    ((AiMix) base).setWindowSeconds(windowFrames(rate) / (double) Math.max(1, rate));
                }
                return base.render(out, from, to, mapped);
            }
            fillScratch(seg, want, from.channels());
            if (base instanceof AiMix) {

                ((AiMix) base).setWindowSeconds(windowFrames(rate) / (double) Math.max(1, rate));
            }
            int n = base.render(out, segmentScratch, to, mapped);

            int used = Math.max(0, Math.min(want, n));
            seg.cursor = seg.passFrames <= 0 ? 0 : (seg.cursor + used) % seg.passFrames;
            seg.delivered += used;
            return n;
        }
    }

    private void fillScratch(SegmentFeed seg, int want, int channels) {
        fillScratchInto(seg, want, channels, false);
    }

    private void fillScratchInto(SegmentFeed seg, int want, int channels, boolean introSide) {
        AudioBuf scratch = introSide ? introScratch : segmentScratch;
        if (scratch == null
                || scratch.channels() != channels
                || scratch.capacityFrames() < want) {
            scratch = new AudioBuf(
                    dev.t1m3.qplayer.audio.core.ChannelLayout.of(channels),
                    Math.max(want, 2 * Deck.RENDER_FRAMES));
            if (introSide) {
                introScratch = scratch;
            } else {
                segmentScratch = scratch;
            }
        }
        int cap = scratch.capacityFrames();
        float[] dst = scratch.backingArray();
        int chans = Math.min(scratch.channels(), seg.pcm.length);
        for (int c = 0; c < chans; c++) {
            float[] src = seg.pcm[c];
            int base = c * cap;
            int idx = seg.cursor;
            if (seg.seamFrames <= 0 && idx + want <= seg.passFrames) {
                System.arraycopy(src, idx, dst, base, want);
                continue;
            }

            final int seam = seg.seamFrames;
            final int tailStart = seg.passFrames - seam;
            for (int i = 0; i < want; i++) {
                float v = src[idx];
                if (seam > 0 && idx >= tailStart) {
                    int over = idx - tailStart;
                    float p = (float) over / (float) Math.max(1, seam - 1);
                    Mixer.Gains g = Mixer.equalPowerGains(p);
                    v = v * g.from + src[over] * g.to;
                }

                dst[base + i] = v;
                if (++idx >= seg.passFrames) {
                    idx = 0;
                }
            }
        }
        scratch.setFrames(want);
    }

    static float loopProgress(long deliveredFrames, long windowFrames, int insertFrames) {
        long fade = windowFrames - insertFrames;
        if (fade <= 0L) {
            return 1.0f;
        }
        double p = (double) (deliveredFrames - insertFrames) / (double) fade;
        if (p <= 0.0) {
            return 0.0f;
        }
        return p >= 1.0 ? 1.0f : (float) p;
    }

    @Override
    public String name() {

        return base.name() + " - auto cue";
    }

    @Override
    public boolean rewritesSamples() {
        return base.rewritesSamples();
    }

    @Override
    public PitchPlan pitchAt(float progress) {
        if (segmentWindowFrames <= 0L) {
            return base.pitchAt(progress);
        }

        long pos = (long) ((double) progress * (double) segmentWindowFrames);
        return base.pitchAt(loopProgress(pos, segmentWindowFrames, segmentInsertFrames));
    }

    static double stretchSpeedFor(double outroBpm, double outroConfidence,
                                  double introBpm, double introConfidence) {
        if (!(outroBpm > 0.0) || !(introBpm > 0.0)) {
            return 1.0;
        }
        if (outroConfidence < STRETCH_MIN_CONFIDENCE || introConfidence < STRETCH_MIN_CONFIDENCE) {
            return 1.0;
        }
        double speed = introBpm / outroBpm;
        if (speed < STRETCH_MIN_SPEED || speed > STRETCH_MAX_SPEED) {
            return 1.0;
        }
        if (Math.abs(speed - 1.0) < STRETCH_MIN_DEVIATION) {
            return 1.0;
        }
        return speed;
    }

    public static double introSkipBonusSecs(double windowSecs, double speedA) {
        if (!(windowSecs > 0.0) || !(speedA > 0.0) || Math.abs(speedA - 1.0) < 1e-12) {
            return 0.0;
        }
        return windowSecs * (1.0 / speedA - 1.0);
    }

    static final double STRETCH_MIN_CONFIDENCE = 0.8;

    static final double STRETCH_MIN_SPEED = TempoBridge.MIN_SPEED;

    static final double STRETCH_MAX_SPEED = TempoBridge.MAX_SPEED;

    static final double STRETCH_MIN_DEVIATION = 0.005;

    private void prepareRampedOutgoing(String outro, java.util.Map<String, String> headers,
                                       TempoRamp ramp, double cutSecs, double windowSecs) {
        stretchFeed = null;
        stretchSpeed = 1.0;
        stretchSpeedStart = 1.0;
        stretchSpeedMean = 1.0;
        if (tuning.tempoStretch <= 0.0f || ramp == null || !ramp.active) {
            return;
        }
        double end = ramp.endSpeedFor(windowSecs);
        if (1.0 - end < STRETCH_MIN_DEVIATION) {

            return;
        }
        int windowFrames = (int) Math.round(windowSecs * rate);
        if (windowFrames <= 0) {
            return;
        }
        try {

            double needFrac = Math.abs(1.0 - end)
                    / (TempoRamp.SLEW_MAX * Math.max(1e-6, windowSecs));
            double convergeFrac = Math.min(TempoRamp.CONVERGE_FRAC, needFrac);
            int need = TempoBridge.requiredFramesConverge(windowFrames, rate, 1.0, end, convergeFrac);
            setMaterialContext(cutSecs, vocalModeForMaterial());
            float[][] source = materialFor(outro, headers, cutSecs, need, rate);
            if (source == null) {
                Logger.warn("tempo ramp down: cannot read the outgoing material -> no ramp this time (each side keeps its own tempo)");
                return;
            }
            float[][] stretched = TempoBridge.stretchConverge(source, rate, windowFrames, 1.0, end,
                    convergeFrac);
            stretchFeed = new SegmentFeed("outro", cutSecs, cutSecs + windowFrames / (double) rate,
                    1, stretched, windowFrames, 0, 0L, 0L, 0);
            enqueue(stretchFeed);
            stretchSpeed = end;
            stretchSpeedStart = 1.0;
            stretchSpeedMean = ramp.meanSpeedFor(windowSecs);
            Logger.debug("tempo ramp down: the outgoing window material is time-warped with pitch-preserving WSOLA "
                            + "1.0000->{}x (mean {}x, source {}s -> output {}s; neither curve has a seam, "
                            + "rate decreases monotonically across the window)",
                    String.format(java.util.Locale.US, "%.4f", Double.valueOf(end)),
                    String.format(java.util.Locale.US, "%.4f", Double.valueOf(stretchSpeedMean)),
                    String.format(java.util.Locale.US, "%.2f", Double.valueOf(need / (double) rate)),
                    String.format(java.util.Locale.US, "%.2f", Double.valueOf(windowSecs)));
        } catch (RuntimeException e) {

            stretchFeed = null;
            stretchSpeed = 1.0;
            stretchSpeedStart = 1.0;
            stretchSpeedMean = 1.0;
            Logger.warn("tempo ramp down failed ({}), no ramp this time", e.toString());
        }
    }

    private static volatile boolean appleRatePort = true;

    public static void setAppleRatePort(boolean on) {
        appleRatePort = on;
    }

    private AutoCue.Cue withActualWindowNote(AutoCue.Cue c) {
        if (c == null || c.note == null) {
            return c;
        }
        double actual = base.windowFrames(rate) / (double) Math.max(1, rate);
        if (!(actual > 0.0)) {
            return c;
        }
        String fixed = c.note.replaceAll("overlap [0-9.]+s",
                String.format(java.util.Locale.ROOT, "overlap %.1fs", Double.valueOf(actual)));
        return fixed.equals(c.note) ? c : c.withNote(fixed);
    }

    private double[] appleBarChain(double windowSecsNow, AutoCue.Envelope outro,
                                   AutoCue.Envelope intro, boolean mapped) {
        appleBarTargetSecs = 0.0;
        if (appleBarWindowBars != 0 && mapped && outro != null && outro.tempo != null
                && outro.tempo.bpm > 0f) {
            double bar = AppleWindowRule.barSecs(outro.tempo.bpm);
            double room = outro.durationSecs - outro.structureOutroStartSecs;
            int fit = bar > 0.0 ? (int) Math.floor(room / bar) : 0;
            int target = appleBarWindowBars < 0 ? Math.max(2, Math.min(8, fit))
                    : Math.min(appleBarWindowBars, fit);
            if (fit >= 1 && target > 0) {
                appleBarTargetSecs = target * bar;
            }
        }
        double nominal = appleBarTargetSecs > 0.0 ? appleBarTargetSecs : windowSecsNow;

        double fadeFloor = appleFadeOutFloorSecs(outro);

        {
            final double durationFloor = appleDurationFloorSecs();
            if (durationFloor > fadeFloor) {
                fadeFloor = durationFloor;
            }
        }
        if (fadeFloor > 0.0 && fadeFloor > nominal) {
            nominal = fadeFloor;
            return appleBarEnable(appleBarCandidates(
                    windowCandidates(nominal, tuneWindow, fadeFloor), outro, intro), outro, mapped);
        }
        double[] base = appleBarTargetSecs > 0.0
                ? windowCandidates(nominal, tuneWindow, appleBarTargetSecs)
                : windowCandidates(windowSecsNow);
        return appleBarEnable(appleBarCandidates(base, outro, intro), outro, mapped);
    }

    private double appleDurationFloorSecs() {
        if (!(base instanceof AiMix)) {
            return 0.0;
        }
        final MixStyle style = ((AiMix) base).style();
        final AppleTransitionSpec.Style spec = AppleStyleMap.specFor(style);
        if (spec == null || spec.outgoingOps == null) {
            return 0.0;
        }
        boolean heavy = false;
        for (String op : spec.outgoingOps) {
            if (op == null) {
                continue;
            }
            final String u = op.toUpperCase(java.util.Locale.ROOT);
            if (u.contains("REMIX_FX") || u.contains("REPEATER")
                    || u.contains("REVERB") || u.contains("FLANGER")) {
                heavy = true;
                break;
            }
        }
        if (!heavy || spec.durationSecs <= 0f) {
            return 0.0;
        }
        return spec.durationSecs;
    }

    private double appleFadeOutFloorSecs(AutoCue.Envelope outro) {
        if (!(base instanceof AiMix)) {
            return 0.0;
        }
        AppleTransitionSpec.Style spec = AppleStyleMap.specFor(((AiMix) base).style());
        if (spec == null || AppleStyleMap.appleId(((AiMix) base).style()) == AppleStyleMap.NONE) {
            return 0.0;
        }
        AppleTransitionSpec.Auto lane = AppleStyleMap.lane(spec, true, "player_gain");
        if (lane == null || lane.segs.length == 0) {
            return 0.0;
        }
        AppleTransitionSpec.Seg last = lane.segs[lane.segs.length - 1];
        if (!(last.v1 <= 0.01f)) {
            return 0.0;
        }
        double room = outro != null
                ? Math.max(1.0, outro.durationSecs - outro.structureOutroStartSecs) : 0.0;
        return Math.min(10.0, room);
    }

    static double windowMinBlockRms(float[] rms, double fromSecs, double windowSecs) {
        if (rms == null || rms.length == 0 || windowSecs <= 0.5) {
            return windowRmsOf(rms, fromSecs, windowSecs);
        }
        double worst = Double.MAX_VALUE;
        for (double t = 0.0; t + 0.5 <= windowSecs; t += 0.5) {
            double r = windowRmsOf(rms, fromSecs + t, 0.5);
            if (r < worst) {
                worst = r;
            }
        }
        return worst == Double.MAX_VALUE ? windowRmsOf(rms, fromSecs, windowSecs) : worst;
    }

    private static float[][] directTail(float[][] stretched, float[][] source,
                                        int total, double endSpeed, int rate) {
        if (stretched == null || source == null || total <= 0) {
            return stretched;
        }
        final int grain = (int) Math.round(0.040 * rate);
        if (grain <= 1 || total <= grain || stretched[0] == null || source[0] == null) {
            return stretched;
        }
        final int outFrom = Math.min(total, stretched[0].length) - grain;
        if (outFrom < 0) {
            return stretched;
        }
        final double srcEnd = total * (endSpeed + 1.0) * 0.5;
        final int base = (int) Math.round(srcEnd) - grain;
        final int span = (int) Math.round(0.060 * rate);
        int bestAt = -1;
        double bestR = -2.0;
        for (int off = -span; off <= span; off++) {
            final int sf = base + off;
            if (sf < 0 || sf + grain >= source[0].length) {
                continue;
            }
            double r = corr(stretched[0], outFrom, source[0], sf, grain);
            if (r > bestR) {
                bestR = r;
                bestAt = sf;
            }
        }
        if (bestAt < 0 || bestR < 0.9) {
            return stretched;
        }
        for (int c = 0; c < stretched.length && c < source.length; c++) {
            if (stretched[c] == null || source[c] == null) {
                continue;
            }
            for (int i = 0; i < grain && i < stretched[c].length; i++) {
                final int dst = outFrom + i;
                final int src = bestAt + i;
                if (dst < stretched[c].length && src < source[c].length) {
                    stretched[c][dst] = source[c][src];
                }
            }
        }
        Logger.debug("handover tail passthrough: offset {} frames ({} ms), correlation {}",
                Integer.valueOf(bestAt - base),
                String.format(java.util.Locale.ROOT, "%.1f", Double.valueOf((bestAt - base) * 1000.0 / Math.max(1, rate))),
                String.format(java.util.Locale.ROOT, "%.3f", Double.valueOf(bestR)));
        return stretched;
    }

    private static double corr(float[] a, int aFrom, float[] b, int bFrom, int n) {
        double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            if (aFrom + i >= a.length || bFrom + i >= b.length) {
                break;
            }
            double x = a[aFrom + i], y = b[bFrom + i];
            sa += x; sb += y; saa += x * x; sbb += y * y; sab += x * y;
            cnt++;
        }
        if (cnt < 2) {
            return 0.0;
        }
        double va = saa - sa * sa / cnt, vb = sbb - sb * sb / cnt;
        if (va <= 1e-12 || vb <= 1e-12) {
            return 0.0;
        }
        return (sab - sa * sb / cnt) / Math.sqrt(va * vb);
    }

    static double windowRmsOf(float[] rms, double fromSecs, double windowSecs) {
        if (rms == null || rms.length == 0 || windowSecs <= 0.0) {
            return 0.0;
        }
        double hop = AutoCue.HOP_SECS;
        int i0 = (int) Math.floor(fromSecs / hop);
        int i1 = (int) Math.ceil((fromSecs + windowSecs) / hop);
        i0 = Math.max(0, Math.min(i0, rms.length - 1));
        i1 = Math.max(i0 + 1, Math.min(i1, rms.length));
        double sum = 0.0;
        for (int i = i0; i < i1; i++) {
            double v = rms[i];
            sum += v * v;
        }
        return Math.sqrt(sum / (i1 - i0));
    }

    private static double[] appleBarEnable(double[] base, AutoCue.Envelope outro, boolean mapped) {

        if (appleBarWindowBars > 0 && mapped
                && outro != null && outro.tempo != null && outro.tempo.bpm > 0f) {
            double bar = AppleWindowRule.barSecs(outro.tempo.bpm);

            double room = outro.durationSecs - outro.structureOutroStartSecs;
            int fit = bar > 0.0 ? (int) Math.floor(room / bar) : 0;
            if (fit < 1) {
                AutoCue.setAppleBarTieSecsRef(0.0);
                AutoCue.setAppleBarWindowWeight(0.0);
                AutoCue.setAppleBarTargetBars(0);
                return base;
            }
            int target = Math.min(appleBarWindowBars, fit);
            Logger.debug("bar window: requested {} bars, outro material {:.2f} bars => taking {} bars",
                    Integer.valueOf(appleBarWindowBars), Double.valueOf(room / bar),
                    Integer.valueOf(target));
            AutoCue.setAppleBarTieSecsRef(bar);
            AutoCue.setAppleBarWindowWeight(15.0);
            AutoCue.setAppleBarTargetBars(target);
        } else {
            AutoCue.setAppleBarTieSecsRef(0.0);
            AutoCue.setAppleBarWindowWeight(0.0);
            AutoCue.setAppleBarTargetBars(0);
        }
        return base;
    }

    private static volatile int appleBarWindowBars = -1;

    public static void setAppleBarWindowBars(int bars) {
        appleBarWindowBars = Math.max(0, bars);
    }

    private static volatile double appleBarTargetSecs = 0.0;

    public static double appleBarTargetSecs() {
        return appleBarTargetSecs;
    }

    public static int appleBarWindowBars() {
        return appleBarWindowBars;
    }

    private static double[] appleBarCandidates(double[] base, AutoCue.Envelope outro,
                                              AutoCue.Envelope intro) {
        if (appleBarWindowBars <= 0 || outro == null || intro == null
                || outro.tempo == null || intro.tempo == null) {
            return base;
        }
        double bpmA = outro.tempo.bpm;
        double bpmB = intro.tempo.bpm;
        if (!(bpmA > 0f) || !(bpmB > 0f)) {
            return base;
        }
        int[] bars = {2, 4, 6, 8};
        double[] out = new double[bars.length + base.length];
        int at = 0;
        for (int b : bars) {
            double secs = AppleWindowRule.barAlignedWindow(1.0, bpmA, bpmB, b);
            if (secs > 0.0) {
                out[at++] = Math.max(1.0, Math.min(20.0, secs));
            }
        }
        for (double v : base) {
            out[at++] = v;
        }
        double[] trimmed = new double[at];
        System.arraycopy(out, 0, trimmed, 0, at);
        Logger.debug("window candidates (bar-based): {} ({} bars each; BPM {} / {})",
                java.util.Arrays.toString(trimmed), Integer.valueOf(appleBarWindowBars),
                fmt(bpmA), fmt(bpmB));
        return trimmed;
    }

    private boolean appleHasTsRate() {
        if (!appleRatePort || !(base instanceof AiMix)) {
            return false;
        }
        AppleTransitionSpec.Style spec = AppleStyleMap.specFor(((AiMix) base).style());
        if (spec == null) {
            return false;
        }
        for (AppleTransitionSpec.Auto au : spec.outgoing) {
            if ("ts_rate".equals(au.key)) {
                return true;
            }
        }
        for (AppleTransitionSpec.Auto au : spec.incoming) {
            if ("ts_rate".equals(au.key)) {
                return true;
            }
        }
        return false;
    }

    static TempoRamp rampFor(AutoCue.Envelope outroEnvelope, AutoCue.Envelope introEnvelope,
                             double userWindowSecs, boolean appleTsRate) {
        if (outroEnvelope == null || introEnvelope == null) {
            return TempoRamp.off(userWindowSecs, "envelopes incomplete on both sides, no ramp this time");
        }
        double speed = stretchSpeedFor(
                outroEnvelope.tempo == null ? 0.0 : outroEnvelope.tempo.bpm,
                outroEnvelope.tempo == null ? 0.0 : outroEnvelope.tempo.confidence,
                introEnvelope.tempo == null ? 0.0 : introEnvelope.tempo.bpm,
                introEnvelope.tempo == null ? 0.0 : introEnvelope.tempo.confidence);
        if (speed == 1.0 && !appleTsRate) {

            return TempoRamp.off(userWindowSecs, "BPM gate not passed, no ramp this time");
        }

        return TempoRamp.deriveHalved(speed, userWindowSecs);
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
        long baseWindow = base.windowFrames(sampleRate);
        if (baseWindow <= 0L) {

            return basePlan;
        }

        double windowSecs = (double) baseWindow / Math.max(1, rate);

        if (appleBarWindowBars > 0) {
            try {
                AutoCue.Envelope oe0 = AutoCue.scan(outro, null);
                AutoCue.Envelope ie0 = AutoCue.scan(intro, null);
                double bpmA0 = oe0 != null && oe0.tempo != null ? oe0.tempo.bpm : 0.0;
                double bpmB0 = ie0 != null && ie0.tempo != null ? ie0.tempo.bpm : 0.0;
                double now0 = windowSecs;
                double aligned = AppleWindowRule.barAlignedWindow(now0, bpmA0, bpmB0,
                        appleBarWindowBars);
                if (aligned > 0.0 && Math.abs(aligned - now0) > 1e-6) {
                    Logger.debug("window computed in bars: {}s -> {}s ({} bars on each side; "
                                    + "BPM {} / {}）", fmt(now0), fmt(aligned),
                            Integer.valueOf(appleBarWindowBars), fmt(bpmA0), fmt(bpmB0));
                    windowSecs = aligned;
                }
            } catch (RuntimeException e) {
                Logger.debug("bar-window computation failed ({}), using the original window this time",
                        e.toString());
            }
        }
        if (liveWindowSecs != null) {
            double live = liveWindowSecs.getAsDouble();
            if (live > 0.0 && !Double.isNaN(live)) {
                if (Math.abs(live - windowSecs) > 0.01) {
                    Logger.debug("auto cue: picking points with the **current setting** window {}s"
                                    + " (the kind's own default is {}s)", fmt(live), fmt(windowSecs));
                }
                windowSecs = live;
            }
        }
        AutoCue.Envelope outroEnvelope;
        AutoCue.Envelope introEnvelope;

        try {

            outroEnvelope = AutoCue.scan(outro, outroHeaders);
        } catch (RuntimeException e) {
            Logger.warn("auto cue: outgoing scan failed ({}): {}"
                            + ", falling back to a fixed cue ({}s)",
                    outro, e.toString(), fmt(windowSecs));
            return basePlan;
        }
        try {

            introEnvelope = AutoCue.scanIntro(intro, introHeaders);
        } catch (RuntimeException e) {
            Logger.warn("auto cue: incoming scan failed ({}): {}"
                            + ", falling back to a fixed cue ({}s)",
                    intro, e.toString(), fmt(windowSecs));
            return basePlan;
        }
        AutoCue.Cue cue;

        TempoRamp ramp = tuning.tempoStretch > 0.0f
                ? rampFor(outroEnvelope, introEnvelope, windowSecs, appleHasTsRate())
                : TempoRamp.off(windowSecs, "the tempo_stretch master switch is off, no ramp this time");
        rampPlan = ramp;

        if (ramp.active && Math.abs(ramp.windowSecs - windowSecs) > 0.01) {
            Logger.debug("ramp: the window upper bound shrinks with the BPM gap {}s -> {}s"
                            + " (decrease only: the shortest ramp that completes one beatmatch = |1 - BPM ratio| / {})",
                    fmt(windowSecs), fmt(ramp.windowSecs), fmt(TempoRamp.SLEW_MAX));
        }
        try {

            if (base instanceof AiMix) {
                AiMix model = (AiMix) base;

                if (introEnvelope != null && model.tempoIn() != null) {
                    introEnvelope = introEnvelope.withTempo(model.tempoIn());
                }
            }
            cue = AutoCue.planWithLyricSpans(outroEnvelope, introEnvelope,
                    appleBarChain(windowSecs, outroEnvelope, introEnvelope,
                            this.base instanceof AiMix
                                    && AppleStyleMap.appleId(((AiMix) this.base).style())
                                            != AppleStyleMap.NONE),

                    AutoCue.vocalPhraseSpans(outroEnvelope),
                    AutoCue.vocalPhraseSpans(introEnvelope),

                    AutoCue.Bridge.MEASURE,

                    keyRelationOfPair(outro, outroHeaders, intro, introHeaders));

            if (logRefineMeasurement) {
                measureRefinement(outro, outroHeaders, cue.outroCutSecs, "exit");
                measureRefinement(intro, introHeaders, cue.introEntrySecs, "entry");
            }

            windowTicket = buildWindowTicket(outro, outroHeaders, cue, rate);
            if (refineCueTimingEnabled) {
                double cut = refineSecs(outro, outroHeaders, cue.outroCutSecs);
                double entry = refineSecs(intro, introHeaders, cue.introEntrySecs);
                if (Math.abs(cut - cue.outroCutSecs) > 1e-9
                        || Math.abs(entry - cue.introEntrySecs) > 1e-9) {
                    dev.t1m3.qplayer.util.Logger.info(String.format(java.util.Locale.ROOT,
                            "frame beat alignment: exit %.4f->%.4f s, entry %.4f->%.4f s",
                            Double.valueOf(cue.outroCutSecs), Double.valueOf(cut),
                            Double.valueOf(cue.introEntrySecs), Double.valueOf(entry)));

                    dev.t1m3.qplayer.util.Logger.info(String.format(java.util.Locale.ROOT,
                            "frame beat alignment, phase diag: A grid %s (beat %s frames @%s Hz)"
                                    + " - B grid %s (beat %s frames @%s Hz)",
                            String.valueOf(outroEnvelope != null && outroEnvelope.hasBeatGrid()),
                            String.valueOf(outroEnvelope == null || outroEnvelope.tempo == null
                                    ? "?" : Double.valueOf(outroEnvelope.tempo.periodFrames)),
                            String.valueOf(outroEnvelope == null ? "?" : Integer.valueOf(
                                    outroEnvelope.beatRate)),
                            String.valueOf(introEnvelope != null && introEnvelope.hasBeatGrid()),
                            String.valueOf(introEnvelope == null || introEnvelope.tempo == null
                                    ? "?" : Double.valueOf(introEnvelope.tempo.periodFrames)),
                            String.valueOf(introEnvelope == null ? "?" : Integer.valueOf(
                                    introEnvelope.beatRate))));
                    if (outroEnvelope != null && introEnvelope != null
                            && outroEnvelope.hasBeatGrid() && introEnvelope.hasBeatGrid()) {
                        Analysis.Tempo ta = outroEnvelope.tempo;
                        Analysis.Tempo tb = introEnvelope.tempo;
                        double pa = beatPhase(cut, ta, outroEnvelope.beatRate);
                        double pb = beatPhase(entry, tb, introEnvelope.beatRate);
                        double delta = pb - pa;
                        if (delta > tb.periodFrames / 2.0) {
                            delta -= tb.periodFrames;
                        } else if (delta < -tb.periodFrames / 2.0) {
                            delta += tb.periodFrames;
                        }
                        if (tb.periodFrames > 0 && Math.abs(delta) >= 1.0) {
                            entry -= delta / (double) introEnvelope.beatRate;
                            dev.t1m3.qplayer.util.Logger.info(String.format(
                                    java.util.Locale.ROOT,
                                    "frame beat alignment, phase correction: B moved %+.1f envelope frames"
                                            + " (%.1f ms, beat %.0f frames @%d Hz)",
                                    Double.valueOf(-delta), Double.valueOf(-delta * 1000.0
                                            / introEnvelope.beatRate),
                                    Double.valueOf(tb.periodFrames),
                                    Integer.valueOf(introEnvelope.beatRate)));
                        }
                    }
                    cue = cue.withTimes(cut, entry);
                }
            }

            boolean lyricBroken = false;
            if (cue != null && (cue.intensityGap > AutoCue.INTENSITY_GOOD_ENOUGH_GAP
                    || lyricBroken)) {
                AutoCue.Envelope deep = null;
                try {
                    deep = AutoCue.scanIntro(intro, introHeaders, AutoCue.Bridge.DEFAULT);
                } catch (RuntimeException e) {
                    Logger.warn("auto cue: incoming deep scan failed ({}), "
                                    + "picking from whatever was scanned this time", intro, e.toString());
                }
                if (deep != null && deep.rms.length > introEnvelope.rms.length) {
                    AutoCue.Cue deeper = AutoCue.planWithLyricSpans(outroEnvelope, deep,
                            appleBarChain(windowSecs, outroEnvelope, introEnvelope,
                            this.base instanceof AiMix
                                    && AppleStyleMap.appleId(((AiMix) this.base).style())
                                            != AppleStyleMap.NONE),

                    AutoCue.vocalPhraseSpans(outroEnvelope),
                    AutoCue.vocalPhraseSpans(introEnvelope),
                            AutoCue.Bridge.DEFAULT,

                            keyRelationOfPair(outro, outroHeaders, intro, introHeaders),

                            cue.outroCutSecs);

                    if (deeper != null && (deeper.intensityOk || !lyricRulesHold(deeper, outroEnvelope, deep))) {
                        Logger.info("auto cue: {}, after the deep scan {} -> {} (intensity gap {})",
                                lyricBroken
                                        ? "lyrics do not line up in that range (A's phrase end / B's line head / gap gA)"
                                        : "energy does not carry over from A in that range (gap " + fmt(cue.intensityGap)
                                                + ", B's chorus is outside this range)",
                                fmt(deeper.outroCutSecs), fmt(deeper.introEntrySecs),
                                fmt(deeper.intensityGap));
                        cue = deeper;
                        introEnvelope = deep;
                    }
                }
            }
        } catch (RuntimeException e) {
            Logger.warn("auto cue: point picking failed ({}), falling back to a fixed cue", e.toString());
            return basePlan;
        }
        if (cue == null) {
            Logger.info("auto cue: no legal candidate this time (exit {}s, entry {}s, overlap {}s), "
                            + "falling back to a fixed cue", fmt(outroEnvelope.durationSecs),
                    fmt(introEnvelope.durationSecs), fmt(windowSecs));
            return basePlan;
        }

        if (tuneWindow || liveWindowSecs != null || ramp.active) {
            autoWindow = Math.max(1L, Math.round(cue.windowSecs * (double) Math.max(1, rate)));
        }

        if (Boolean.getBoolean("qplayer.trace.window")) {
            System.out.println(String.format(java.util.Locale.ROOT,
                    "introEnv=%s duration=%.2f entry=%.2f autoWindow=%d inner=%d",
                    introEnvelope == null ? "null" : "yes",
                    Double.valueOf(introEnvelope == null ? -1 : introEnvelope.durationSecs),
                    Double.valueOf(cue.introEntrySecs), Long.valueOf(autoWindow),
                    Long.valueOf(base.windowFrames(rate))));
        }
        if (introEnvelope != null && introEnvelope.durationSecs > 1.0) {
            long inUse = autoWindow > 0L ? autoWindow : base.windowFrames(rate);
            long roomFrames = Math.round(
                    Math.max(0.5, introEnvelope.durationSecs - cue.introEntrySecs - 0.5)
                            * (double) Math.max(1, rate));
            if (inUse > roomFrames) {
                Logger.info("auto cue: window {}s exceeds the incoming track's remaining material"
                                + " (entry {}s / duration {}s) => clamped to {}s (otherwise the handover lands on dead air)",
                        fmt(inUse / (double) Math.max(1, rate)),
                        fmt(cue.introEntrySecs), fmt(introEnvelope.durationSecs),
                        fmt(roomFrames / (double) Math.max(1, rate)));
                autoWindow = Math.max(1L, roomFrames);
            }
        }

        if (cue != null) {

            double bonusHint = 0.0;
            double aligned = AutoCue.structureAlignedEntry(outroEnvelope, introEnvelope,
                    cue.windowSecs,
                    Math.max(0.0, introEnvelope == null ? 0.0
                            : Math.min(introEnvelope.durationSecs, cue.introEntrySecs + 30.0)),
                    bonusHint);
            Logger.info("structural alignment diag: structure point {}s, window {}s, after alignment {}s,"
                            + " now {}s (NaN = not computed)",
                    fmt(AutoCue.structureOf(introEnvelope)), fmt(cue.windowSecs),
                    Double.isNaN(aligned) ? "NaN" : fmt(aligned), fmt(cue.introEntrySecs));

            if (!Double.isNaN(aligned)) {
                TempoRamp probe = rampFor(outroEnvelope, introEnvelope, cue.windowSecs,
                        appleHasTsRate());
                if (probe != null && probe.active) {
                    aligned -= introSkipBonusSecs(cue.windowSecs, probe.endSpeedFor(cue.windowSecs));

                                    }
            }
            if (!Double.isNaN(aligned) && aligned > 0.0
                    && Math.abs(aligned - cue.introEntrySecs) > 1e-9) {
                Logger.info("auto cue: entry point aligned to structure {}s -> {}s"
                                + " (handover = {}s, B's structure point minus half a window => the build-up belongs to B alone)",
                        fmt(cue.introEntrySecs), fmt(aligned),
                        fmt(aligned + cue.windowSecs));

                if (Boolean.getBoolean("qplayer.trace.window")) {
                    System.out.println(String.format(java.util.Locale.ROOT,
                            "entry computed on the B side %.3f (cue says %.3f), about to recompute the exit on the A side"
                                    + " (A structure %s)",
                            Double.valueOf(aligned), Double.valueOf(cue.introEntrySecs),
                            fmt(AutoCue.structureOf(outroEnvelope))));
                }

                double cut = AutoCue.structureAlignedCut(outroEnvelope, cue.windowSecs, 0.0,
                        introEnvelope, cue.introEntrySecs);
                double newCut = Double.isNaN(cut) ? cue.outroCutSecs : cut;
                if (!Double.isNaN(cut)) {
                    Logger.info("auto cue: exit point aligned to structure {}s -> {}s"
                                    + " (handover = {}s, A's structure point => one rule for both sides)",
                            fmt(cue.outroCutSecs), fmt(newCut), fmt(newCut + cue.windowSecs));
                }

                    double newTail = outroEnvelope == null ? cue.tailDropSecs
                            : Math.max(0.0, outroEnvelope.durationSecs - (newCut + cue.windowSecs));
                    cue = cue.withLanding(newCut, aligned, newTail);
            }
        }

        autoEntrySecs = cue.introEntrySecs;

        double effWindow = cue == null ? 0.0 : cue.windowSecs;
        if (cue != null && base instanceof AiMix) {
            MixStyle st0 = ((AiMix) base).style();
            if (st0 == MixStyle.HIPHOP_LOOPING || st0 == MixStyle.DANCE_REPEATER) {
                effWindow = Math.min(effWindow, LOOP_ROUTE_WINDOW_SECS);
            }
        }
        if (cue != null && cue.outroLoop == null && autoTuningForStyleLoop()
                && outroEnvelope != null && introEnvelope != null
                && effWindow > 0.0
                && effWindow < AutoCue.LOOP_CONFIRM_WINDOW_SECS) {

            float minCost = MixTuning.defaults().loopConfirmMinCost;
            if (base instanceof AiMix) {
                minCost = ((AiMix) base).tuning().loopConfirmMinCost;
            }
            boolean poorDirect = cue.cost > minCost;
            if (effWindow < cue.windowSecs) {
                Logger.debug("this lane repeats: window {}s -> {}s ((b1) decided by the user)",
                        fmt(cue.windowSecs), fmt(effWindow));
            }
            if (Boolean.getBoolean("qplayer.trace.window")) {
                System.out.println(String.format(java.util.Locale.ROOT,
                        "window %.3f s (threshold %.1f s), direct-transition cost %.4f (threshold %.2f)"
                                + " => lane=%s",
                        Double.valueOf(cue.windowSecs),
                        Double.valueOf(AutoCue.LOOP_CONFIRM_WINDOW_SECS),
                        Double.valueOf(cue.cost), Double.valueOf(minCost),
                        cue.windowSecs < AutoCue.LOOP_CONFIRM_WINDOW_SECS && poorDirect
                                ? "yes" : "no"));
            }
            String why = String.format(java.util.Locale.ROOT,
                    "; the window is only %.2f s (fewer than %.1f s)", Double.valueOf(effWindow),
                    Double.valueOf(AutoCue.LOOP_CONFIRM_WINDOW_SECS));
            if (!poorDirect) {
                cue = cue.withOutroLoop(null, why + ", but the direct transition joins well (cost "
                        + fmt(cue.cost) + " ≤ " + fmt(minCost) + ") => no repeat");
                Logger.debug("window {}s is short, but the direct-transition cost is only {} <= {} => "
                        + "not really short of time, no repeat", fmt(cue.windowSecs), fmt(cue.cost),
                        fmt(minCost));
            } else {
            try {
                AutoCue.VampSpan vamp = AutoCue.outroVampSpan(outroEnvelope);
                AutoCue.OutroLoopPlan want = AutoCue.planOutroLoop(outroEnvelope,
                        introEnvelope, vamp, cue.outroCutSecs, effWindow, false);
                if (want == null) {
                    cue = cue.withOutroLoop(null, why + ", but no qualifying vamp => plain crossfade as before");
                    Logger.debug("window {}s is short, but the outro has no qualifying vamp"
                            + " (no vocal span / self-similar / steady beat) => no repeat this time",
                            fmt(cue.windowSecs));
                } else {
                    cue = cue.withOutroLoop(want, why + " ✓");
                    Logger.debug("repeat confirmed: {} bars x {} passes ({})",
                            Integer.valueOf(want.bars), Integer.valueOf(want.repeats),
                            want.reason);
                }
            } catch (RuntimeException | LinkageError e) {
                cue = cue.withOutroLoop(null, why + ", but the loop request errored => plain crossfade as before");
                Logger.warn("repeat confirmation failed ({}) => no repeat this time", e.toString());
            }
            }
        }

        if (Boolean.getBoolean("qplayer.trace.window")) {
            System.out.println(String.format(java.util.Locale.ROOT,
                    "final: exit %.3f, entry %.3f, window %.3f (handover %.3f)",
                    Double.valueOf(cue.outroCutSecs), Double.valueOf(cue.introEntrySecs),
                    Double.valueOf(cue.windowSecs),
                    Double.valueOf(cue.outroCutSecs + cue.windowSecs)));
        }

        if (!Boolean.getBoolean("qplayer.carve.off")
                && base instanceof AiMix && outroEnvelope != null && introEnvelope != null
                && outroEnvelope.band != null && introEnvelope.band != null) {
            int ia0 = outroEnvelope.indexAt(cue.outroCutSecs);
            int ib0 = introEnvelope.indexAt(cue.introEntrySecs);
            int hops = (int) Math.round(cue.windowSecs / AutoCue.HOP_SECS);
            int bands = Math.min(
                    outroEnvelope.band.length > ia0 ? outroEnvelope.band[ia0].length : 0,
                    introEnvelope.band.length > ib0 ? introEnvelope.band[ib0].length : 0);
            if (hops > 2 && bands > 1) {

                double[] hzPlan = new double[bands];
                float[] gainPlan = new float[bands];
                int carved = 0;
                for (int b = 0; b < bands; b++) {
                    double la = 0.0;
                    double lb = 0.0;
                    int used = 0;
                    for (int k2 = 0; k2 < hops; k2++) {
                        int i1 = ia0 + k2;
                        int i2 = ib0 + k2;
                        if (i1 >= outroEnvelope.band.length || i2 >= introEnvelope.band.length
                                || outroEnvelope.band[i1] == null
                                || introEnvelope.band[i2] == null
                                || b >= outroEnvelope.band[i1].length
                                || b >= introEnvelope.band[i2].length) {
                            continue;
                        }
                        la += outroEnvelope.band[i1][b];
                        lb += introEnvelope.band[i2][b];
                        used++;
                    }
                    if (used < 2) {
                        continue;
                    }

                    double laAvg = la / used;
                    double lbAvg = lb / used;
                    double gapAvg = Math.abs(20.0 * Math.log10(Math.max(1e-9, laAvg))
                            - 20.0 * Math.log10(Math.max(1e-9, lbAvg)));
                    double gapWorst = Double.MAX_VALUE;
                    for (int k3 = 0; k3 < hops; k3++) {
                        int i1 = ia0 + k3;
                        int i2 = ib0 + k3;
                        if (i1 >= outroEnvelope.band.length || i2 >= introEnvelope.band.length
                                || outroEnvelope.band[i1] == null
                                || introEnvelope.band[i2] == null
                                || b >= outroEnvelope.band[i1].length
                                || b >= introEnvelope.band[i2].length) {
                            continue;
                        }
                        double d = Math.abs(20.0 * Math.log10(Math.max(1e-9,
                                        outroEnvelope.band[i1][b]))
                                - 20.0 * Math.log10(Math.max(1e-9, introEnvelope.band[i2][b])));
                        gapWorst = Math.min(gapWorst, d);
                    }
                    double gap = Math.min(gapAvg, gapWorst == Double.MAX_VALUE ? gapAvg : gapWorst);
                    hzPlan[b] = CARVE_BAND_HZ[Math.min(b, CARVE_BAND_HZ.length - 1)];
                    if (gap < CARVE_WITHIN_DB) {

                        double cut = Math.min(CARVE_MAX_DB, (CARVE_WITHIN_DB - gap) + 1.5);
                        gainPlan[b] = (float) -cut;
                        carved++;
                    }
                }
                if (carved > 0) {

                    ((AiMix) base).setCarvePlan(hzPlan, gainPlan);

                    Logger.debug("complementary make-room (multiband): {} bands yielding"
                                    + " (threshold <= {} dB, cap {} dB)",
                            Integer.valueOf(carved), fmt(CARVE_WITHIN_DB), fmt(CARVE_MAX_DB));

                }
            }
        }

        boolean appleMappedHere = AiMix.appleHarmonyDevicesOn() && base instanceof AiMix
                && AppleStyleMap.appleId(((AiMix) base).style()) != AppleStyleMap.NONE;
        float dip = HEADROOM_DB;
        if (outroEnvelope != null && introEnvelope != null && cue != null) {
            int ia0 = outroEnvelope.indexAt(cue.outroCutSecs);
            int ib0 = introEnvelope.indexAt(cue.introEntrySecs);
            int hops = (int) Math.round(Math.max(0.0, cue.windowSecs) / AutoCue.HOP_SECS);
            double worst = 0.0;
            for (int k = 0; k < hops; k++) {
                int i1 = ia0 + k;
                int i2 = ib0 + k;
                if (i1 >= outroEnvelope.rms.length || i2 >= introEnvelope.rms.length) {
                    break;
                }
                double pa = outroEnvelope.rms[i1] * (double) outroEnvelope.rms[i1];
                double pb = introEnvelope.rms[i2] * (double) introEnvelope.rms[i2];
                double solo = Math.max(pa, pb);
                if (solo > 1e-9) {
                    worst = Math.max(worst, 10.0 * Math.log10((pa + pb) / solo));
                }
            }
            if (worst > 0.0 && appleMappedHere) {

                float want = (float) Math.min(1.0, Math.max(HEADROOM_DB, worst - 1.0));
                headroomDb = want;
                Logger.debug("adaptive make-room: estimated bump {} dB => making {} dB of room",
                        fmt(worst), fmt(want));
            }
        }
        autoTailSecs = cue.tailDropSecs;
        autoNote = cue.note;

        if (cue.segments != null && cue.segments.size() > 1) {
            prepareLoopOutgoing(outro, outroHeaders, intro, introHeaders, outroEnvelope,
                    introEnvelope, cue, windowSecs);
        } else {
            prepareLoopOutgoing(outro, outroHeaders, outroEnvelope, cue, windowSecs);
        }

        finishGeometry();

        final boolean loopish = loopFeed != null || explicitPath;
        int outroIndex = outroEnvelope.indexAt(loopish
                ? segmentFadeStartSecs : cue.outroCutSecs);
        int introIndex = introEnvelope.indexAt(cue.introEntrySecs);
        int hops = (int) Math.round((loopish
                ? (double) (segmentWindowFrames - segmentInsertFrames)
                        / (double) Math.max(1, rate)
                : cue.windowSecs)
                / AutoCue.HOP_SECS);
        double[] duckRanges = AutoCue.vocalOverlapRanges(outroEnvelope, introEnvelope,
                outroIndex, introIndex, hops);
        if (duckRanges != null && duckRanges.length > 0 && Logger.verbose()) {

            Logger.debug("auto cue: {} spans where both sides sing at once ({}) -> "
                            + "removing vocals only in those spans", Integer.valueOf(duckRanges.length / 2),
                    describeRanges(duckRanges));
        }

        float[] duckCurve = AutoCue.vocalDuckCurve(outroEnvelope, introEnvelope, outroIndex,
                introIndex, hops);

        if (duckCurve != null && base instanceof AiMix
                && ((AiMix) base).style() == MixStyle.NO_VOCAL_CLASH) {
            java.util.Arrays.fill(duckCurve, 1f);
            duckRanges = new double[]{0.0, cue.windowSecs};
            cue = cue.withOutroLoop(null, "; this lane (no vocal clash) makes vocals yield across the whole overlap");
            Logger.info("no vocal clash: vocals yield across the whole overlap ({}s)",
                    fmt(cue.windowSecs));
        }

        if (segmentWindowFrames <= 0L) {
            prepareRampedOutgoing(outro, outroHeaders, ramp, cue.outroCutSecs, cue.windowSecs);

            prepareRampedIncoming(intro, introHeaders, ramp, cue.introEntrySecs, cue.windowSecs);

            prepareLearnedEq(outroEnvelope, introEnvelope, cue);
        } else {
            Logger.debug("outro loop/path: no ramp this time (queued material plays at its own rate)");
        }

        if (introEnvelope != null && outroEnvelope != null && cue.windowSecs > 0.0) {
            double aRms = windowRmsOf(outroEnvelope.rms, cue.outroCutSecs, cue.windowSecs);
            double bRms = windowRmsOf(introEnvelope.rms, cue.introEntrySecs, cue.windowSecs);
            if (aRms > 1e-6 && bRms < aRms * 0.5) {
                double entry = cue.introEntrySecs;

                double bestEntry = -1.0;
                double bestTail = -1.0;
                for (int k = 1; k <= 60; k++) {
                    double cand = cue.introEntrySecs + k * 0.5;
                    if (cand + cue.windowSecs > introEnvelope.durationSecs) {
                        break;
                    }
                    double r = windowRmsOf(introEnvelope.rms, cand, cue.windowSecs);

                    double lowest = windowMinBlockRms(introEnvelope.rms,
                            cand + Math.max(0.0, cue.windowSecs - 2.5), Math.min(2.5, cue.windowSecs));

                    double after = windowMinBlockRms(introEnvelope.rms,
                            cand + cue.windowSecs, 3.0);

                    if (lowest > bestTail) {
                        bestTail = lowest;
                        bestEntry = cand;
                    }
                    if (r >= aRms * 0.5 && lowest >= aRms * 0.5 && after >= aRms * 0.7079) {
                        entry = cand;
                        break;
                    }
                }
                if (entry <= cue.introEntrySecs && bestEntry > cue.introEntrySecs) {
                    entry = bestEntry;
                    Logger.info("entry level gate: no candidate satisfies both rulers => taking the loudest tail at {}s",
                            fmt(entry));
                }
                if (entry > cue.introEntrySecs) {
                    Logger.info("entry level gate: B's window level is below A by "
                            + "{} dB => entry point {}s -> {}s (requires >= A - 6 dB)",
                            String.format(java.util.Locale.ROOT, "%.1f", Double.valueOf(
                                    20.0 * Math.log10(Math.max(bRms, 1e-7) / Math.max(aRms, 1e-7)))),
                            fmt(cue.introEntrySecs), fmt(entry));
                    cue = cue.withTimes(cue.outroCutSecs, entry);
                }
            }
        }
        Logger.info("auto cue: exit {}s ({}s from the end) -> entry {}s, window {}s{}{}",
                fmt(cue.outroCutSecs),
                fmt(Math.max(0.0, outroEnvelope.durationSecs - cue.outroCutSecs)),
                fmt(cue.introEntrySecs), fmt(cue.windowSecs),
                stretchFeed == null ? "" : (" - gradual ramp 1.0000->" + String.format(
                        java.util.Locale.US, "%.4f", Double.valueOf(stretchSpeed)) + "×"
                        + " (mean " + String.format(java.util.Locale.US, "%.4f",
                                Double.valueOf(stretchSpeedMean)) + "×）"),
                duckRanges == null ? " - whole-window vocal removal"
                        : (duckRanges.length == 0 ? " - no vocal removal"
                                : (" - vocal overlap " + (duckRanges.length / 2) + " spans")));

        float introStartAt = basePlan.introStartAt;
        double planTailSecs = cue.tailDropSecs;
        if (loopFeed != null || explicitPath) {
            introStartAt = (float) ((double) segmentInsertFrames / (double) autoWindow);
            planTailSecs = segmentTailSecs;
        }

        double introSkipBonus = introOutgoing.length > 0 && ramp != null && ramp.active
                ? introSkipBonusSecs(cue.windowSecs, ramp.endSpeedFor(cue.windowSecs))
                : 0.0;

        if (introEnvelope != null && introEnvelope.durationSecs > 1.0) {
            long roomFrames = Math.round(
                    Math.max(0.5, introEnvelope.durationSecs - cue.introEntrySecs - 0.5)
                            * (double) Math.max(1, rate));
            long inUse = autoWindow > 0L ? autoWindow : base.windowFrames(rate);
            if (inUse > roomFrames) {
                Logger.info("auto cue: window {}s exceeds the incoming track's remaining material"
                                + " (entry {}s / duration {}s) => clamped to {}s (otherwise the handover lands on dead air)",
                        fmt(inUse / (double) Math.max(1, rate)),
                        fmt(cue.introEntrySecs), fmt(introEnvelope.durationSecs),
                        fmt(roomFrames / (double) Math.max(1, rate)));
                autoWindow = Math.max(1L, roomFrames);
                finishGeometry();
            }
        }

        return new TransitionPlan(cue.introEntrySecs + introSkipBonus, basePlan.pitch, introStartAt,
                cue.note, planTailSecs, duckRanges, duckCurve);
    }

    private void prepareRampedIncoming(String intro, java.util.Map<String, String> headers,
                                       TempoRamp ramp, double entrySecs, double windowSecs) {
        introOutgoing = new SegmentFeed[0];
        introIndex = 0;
        if (tuning.tempoStretch <= 0.0f || ramp == null || !ramp.active || rate <= 0) {
            return;
        }
        double endA = ramp.endSpeedFor(windowSecs);
        double endB = 1.0 / endA;

        this.introSpeedStart = endB;
        this.introSpeedWindow = Math.max(1, (int) Math.round(windowSecs * rate));
        if (Math.abs(1.0 - endB) < STRETCH_MIN_DEVIATION) {
            return;
        }
        int windowFrames = (int) Math.round(windowSecs * rate);
        if (windowFrames <= 0) {
            return;
        }
        try {
            double needFrac = Math.abs(1.0 - endB)
                    / (TempoRamp.SLEW_MAX * Math.max(1e-6, windowSecs));
            double convergeFrac = Math.min(TempoRamp.CONVERGE_FRAC, needFrac);

            int need = TempoBridge.requiredFramesConverge(windowFrames, rate, endB, 1.0, convergeFrac);

            int margin = (int) Math.ceil(rate * 0.044) + (int) Math.ceil(rate * 0.004) + 8;

            int stopgap = (int) Math.round(rate * 0.5);
            setMaterialContext(entrySecs, vocalModeForMaterial());
            float[][] source = materialFor(intro, headers, entrySecs,
                    need + margin + stopgap, rate);
            if (source == null) {
                Logger.warn("tempo ramp down: cannot read the outgoing material -> no ramp this time (each side keeps its own tempo)");
                return;
            }
            float[][] stretched = TempoBridge.stretchConverge(source, rate, windowFrames, endB, 1.0,
                    convergeFrac);

            {
                final int grain = (int) Math.round(0.040 * rate);
                final int total = windowFrames;
                final double srcEnd = total * (endB + 1.0) * 0.5;
                final int from = Math.max(0, total - grain);
                for (int c = 0; c < stretched.length && c < source.length; c++) {
                    if (stretched[c] == null || source[c] == null) {
                        continue;
                    }
                    for (int i = from; i < Math.min(total, stretched[c].length); i++) {
                        int si = (int) Math.round(srcEnd - (total - i));
                        if (si >= 0 && si < source[c].length) {
                            stretched[c][i] = source[c][si];
                        }
                    }
                }
            }
            introOutgoing = new SegmentFeed[] {new SegmentFeed("intro", entrySecs,
                    entrySecs + windowFrames / (double) rate, 1, stretched, windowFrames, 0, 0L, 0L, 0)};

            Logger.debug("tempo ramp up: the incoming window material is time-warped with pitch-preserving WSOLA "
                            + "{}x->1.0000 (starts slightly fast, ends at its own rate; reciprocal of the exit side; window {}s)",
                    String.format(java.util.Locale.US, "%.4f", Double.valueOf(endB)),
                    String.format(java.util.Locale.US, "%.2f", Double.valueOf(windowSecs)));
        } catch (RuntimeException e) {
            introOutgoing = new SegmentFeed[0];
            introIndex = 0;
            Logger.warn("tempo ramp up failed ({}), B plays at its own rate this time", e.toString());
        }
    }

    private void prepareLearnedEq(AutoCue.Envelope a, AutoCue.Envelope b, AutoCue.Cue cue) {
        if (!(base instanceof AiMix) || a == null || b == null || cue == null
                || a.band == null || b.band == null || cue.windowSecs <= 0.0) {
            return;
        }
        int frames = (int) Math.round(cue.windowSecs / AutoCue.HOP_SECS);
        int fa = (int) Math.round(cue.outroCutSecs / AutoCue.HOP_SECS);
        int fb = (int) Math.round(cue.introEntrySecs / AutoCue.HOP_SECS);
        frames = Math.min(frames, Math.min(a.band.length - fa, b.band.length - fb));
        if (frames < 20) {
            return;
        }
        try {
            EqFeatureExtractor.Side sa = sideOf(a, fa, frames);
            EqFeatureExtractor.Side sb = sideOf(b, fb, frames);
            float bpmA = a.tempo == null ? 0f : a.tempo.bpm;
            float bpmB = b.tempo == null ? 0f : b.tempo.bpm;
            float[][] feats = new float[frames][EqFeatureExtractor.DIM];
            int n = EqFeatureExtractor.extract(sa, sb, cue.windowSecs, bpmA, bpmB, 0.0, feats);
            if (n < 20) {
                return;
            }
            double[] controls = controlsFor(feats, n, cue.windowSecs);
            if (controls == null) {
                return;
            }
            ((AiMix) base).setLearnedEqCurve(EqCurve.of(controls, n));
            Logger.debug("learned EQ: window {}s x {} frames -> 12 control points ({})",
                    String.format(java.util.Locale.US, "%.2f", Double.valueOf(cue.windowSecs)),
                    Integer.valueOf(n),
                    MixEqMlp.available() ? "MLP embedded weights" : "rule teacher (MLP not converged / unavailable)");
        } catch (RuntimeException | LinkageError e) {

            Logger.warn("learned EQ: curve computation failed ({}), falling back to a plain blend this time",
                    e.getClass().getName() + (e.getCause() == null ? ""
                            : " ⟵ " + e.getCause().getClass().getName()
                                    + "(" + e.getCause().getMessage() + ")"));
        }
    }

    private static double[] controlsFor(float[][] feats, int frames, double windowSecs) {

        MixEqMlp mlp = MixEqMlp.available() ? MixEqMlp.embedded() : null;
        if (mlp == null) {
            return RuleTeacherEq.controlsFrom(feats, frames, windowSecs);
        }

        float[] mean = new float[EqFeatureExtractor.DIM];
        double[] out = new double[MixEqMlp.OUT];
        double[] sum = new double[MixEqMlp.OUT];
        int used = 0;
        for (int f = 0; f < frames; f++) {
            mlp.forward(feats[f], out);
            for (int k = 0; k < MixEqMlp.OUT; k++) {
                sum[k] += out[k];
            }
            used++;
        }
        if (used == 0) {
            return RuleTeacherEq.controlsFrom(feats, frames, windowSecs);
        }
        for (int k = 0; k < MixEqMlp.OUT; k++) {
            sum[k] /= used;
        }
        return sum;
    }

    private static EqFeatureExtractor.Side sideOf(AutoCue.Envelope env, int from, int frames) {
        float[][] band = new float[frames][];
        float[] rms = new float[frames];
        for (int f = 0; f < frames; f++) {
            band[f] = env.band[from + f];
            rms[f] = env.rms[from + f];
        }
        return new EqFeatureExtractor.Side(band, rms,
                EqFeatureExtractor.centroidStandIn(env.rms, from, frames), null);
    }

    public boolean hasStretchedIncoming() {
        return introOutgoing.length > 0;
    }

    void prepareLoopOutgoing(String outro, java.util.Map<String, String> headers,
                             AutoCue.Envelope outroEnvelope, AutoCue.Cue cue,
                             double windowSecs) {
        prepareLoopOutgoing(outro, headers, null, null, outroEnvelope, null, cue, windowSecs);
    }

    void prepareLoopOutgoing(String outro, java.util.Map<String, String> outroHeaders,
                             String intro, java.util.Map<String, String> introHeaders,
                             AutoCue.Envelope outroEnvelope, AutoCue.Envelope introEnvelope,
                             AutoCue.Cue cue, double windowSecs) {
        resetSegments();
        limiter.reset();
        instanceSource = null;
        if (cue == null) {
            return;
        }
        AutoCue.OutroLoopPlan plan = cue.outroLoop;

        final boolean multiPath = cue.segments != null && cue.segments.size() > 1;
        final boolean hasPath = cue.segments != null && !cue.segments.isEmpty();
        if (plan == null && !multiPath) {
            return;
        }

        final boolean mayTuneWindow = tuneWindow || liveWindowSecs != null;
        if (!multiPath && !mayTuneWindow && !AutoCue.loopIsWired(plan)) {

            Logger.warn("outro loop: this layer may not decide the window (in AI mode the window comes from"
                            + "the style / bar count), and this plan is not the 'buy time' kind ({}) -> "
                            + "no loop this time, plain crossfade as before", plan.reason);
            return;
        }
        if (outroEnvelope == null || outroEnvelope.tempo == null
                || !(outroEnvelope.tempo.bpm > 0f)) {
            return;
        }
        double barSecs = 4.0 * 60.0 / outroEnvelope.tempo.bpm;
        double phase = AutoCue.barPhaseOf(outroEnvelope, barSecs);
        if (Double.isNaN(phase)) {
            phase = 0.0;
        }

        if (multiPath) {
            prepareFromPath(outro, outroHeaders, intro, introHeaders, outroEnvelope,
                    introEnvelope, cue.segments, windowSecs, barSecs);
            return;
        }
        int loopBars = Math.max(1, plan.bars);

        double v0 = AutoCue.snapToBar(plan.span.from, phase, barSecs);
        if (v0 < 0.0) {
            return;
        }

        int loopFrames = (int) Math.round(loopBars * barSecs * (double) rate);
        if (loopFrames <= 0
                || v0 + loopFrames / (double) rate > outroEnvelope.durationSecs) {
            Logger.warn("outro loop: the loop segment ({} bars) does not fit at the end of the track -> "
                    + "no loop this time", Integer.valueOf(loopBars));
            return;
        }

        int fadeBars = (int) Math.round(windowSecs / barSecs);
        if (!mayTuneWindow && fadeBars > loopBars) {
            Logger.warn("outro loop: snapping the user window ({}s) to whole bars needs {} bars, "
                            + "but one pass of this outro vamp is only {} bars -> no time can be bought (it would only shorten the transition), "
                            + "falling back to a plain crossfade this time",
                    fmt(windowSecs), Integer.valueOf(fadeBars), Integer.valueOf(loopBars));
            return;
        }

        if (fadeBars < 1) {
            fadeBars = 1;
        }
        if (fadeBars > loopBars) {
            fadeBars = loopBars;
        }
        int fadeFrames = (int) Math.round(fadeBars * barSecs * (double) rate);
        int repeats = clampRepeats(plan.repeats);
        long windowTotal = (long) repeats * (long) loopFrames;

        if (!mayTuneWindow && windowTotal / (double) rate > outroEnvelope.durationSecs) {
            Logger.warn("outro loop: buying time would extend the window to {}s, but A only has {}s"
                            + " -> that would trigger the transition at the very start of the track, falling back to a plain crossfade this time",
                    fmt(windowTotal / (double) rate), fmt(outroEnvelope.durationSecs));
            return;
        }

        int insertFrames = (int) (windowTotal - fadeFrames);
        int seamFrames = (int) Math.min(Math.round(LOOP_SEAM_FADE_SECS * (double) rate),
                Math.max(0, insertFrames));
        AutoCue.Segment synthesized =
                new AutoCue.Segment("outro", v0, v0 + loopFrames / (double) rate, repeats);
        final boolean fromPlan = hasPath
                && pathMatchesDecision(cue.segments, synthesized, loopFrames, windowTotal);
        if (fromPlan) {
            synthesized = cue.segments.get(0);
        }
        SegmentFeed feed = readFeed(outro, outroHeaders, synthesized,
                fromPlan ? passFramesOf(synthesized) : loopFrames, seamFrames,
                windowTotal, 0L, insertFrames);
        if (feed == null) {
            Logger.warn("outro loop: cannot read the loop material -> no loop this time");
            return;
        }

        enqueue(feed);
        explicitPath = fromPlan;
        loopFeed = feed;
        loopPlan = plan;
        segmentWindowFrames = windowTotal;
        segmentInsertFrames = insertFrames;
        segmentCutSecs = feed.endSecs;
        autoWindow = windowTotal;
        autoTailSecs = Math.max(0.0, outroEnvelope.durationSecs
                - (feed.endSecs + windowTotal / (double) rate));
        finishGeometry();

        dropStretch();
        if (!mayTuneWindow) {

            Logger.info("outro loop: in AI mode the window comes from the style / bar count (this layer must not touch it), "
                            + "but this case is 'buy time' (the outro cannot fit this window, or what is left would feel rushed"
                            + " - see the next line for which one it is) -> extending the transition window from {}s "
                            + "to {}s (extend only, never shorten; one loop pass is {} bars, repeated {} times)",
                    fmt(windowSecs), fmt(windowTotal / (double) rate),
                    Integer.valueOf(loopBars), Integer.valueOf(repeats));
        }
        Logger.info("outro loop: {}->{}s ({} bars) repeated {} times, window {}s"
                        + " (first {}s is A alone, the last {}s crossfades with B), cut {}s, {}{}",
                fmt(v0), fmt(v0 + loopFrames / (double) rate), Integer.valueOf(loopBars),
                Integer.valueOf(repeats),
                fmt(windowTotal / (double) rate), fmt(segmentInsertFrames / (double) rate),
                fmt(loopFadeFrames() / (double) rate), fmt(segmentCutSecs), plan.reason,
                explicitPath ? " - following the plan layer's path" : "");
    }

    private void prepareFromPath(String outro, java.util.Map<String, String> outroHeaders,
                                 String intro, java.util.Map<String, String> introHeaders,
                                 AutoCue.Envelope outroEnvelope, AutoCue.Envelope introEnvelope,
                                 java.util.List<AutoCue.Segment> path, double windowSecs,
                                 double barSecs) {
        if (path.get(0) == null || path.get(0).fromIntro()) {
            Logger.warn("path: the first leg is not outgoing-track material ({}) -> there is no seam to speak of, "
                    + "falling back to a plain crossfade this time", AutoCue.describePath(path));
            return;
        }
        java.util.List<SegmentFeed> feeds = new java.util.ArrayList<SegmentFeed>(path.size());
        long total = 0L;
        for (AutoCue.Segment seg : path) {
            if (seg == null || !(seg.spanSecs() > 0.0)) {
                Logger.warn("path: one leg has a non-positive length ({}) -> falling back to a plain crossfade this time",
                        AutoCue.describePath(path));
                return;
            }
            boolean fromIntro = seg.fromIntro();
            String source = fromIntro ? intro : outro;
            java.util.Map<String, String> headers = fromIntro ? introHeaders : outroHeaders;
            if (source == null) {
                Logger.warn("path: this leg needs incoming-track material ({}), "
                        + "but this layer has no incoming source -> falling back to a plain crossfade this time",
                        AutoCue.describePath(path));
                return;
            }

            AutoCue.Envelope srcEnv = fromIntro ? introEnvelope : outroEnvelope;
            if (srcEnv != null && srcEnv.durationSecs > 0.0
                    && seg.endSecs > srcEnv.durationSecs + 1e-6) {
                Logger.warn("path: leg {} reaches past the end of the track ({} has only {}s) -> "
                                + "falling back to a plain crossfade this time",
                        seg, fromIntro ? "incoming track" : "outgoing track", fmt(srcEnv.durationSecs));
                return;
            }
            int passFrames = passFramesOf(seg);

            SegmentFeed feed = readFeed(source, headers, seg, passFrames, 0, 0L, total, 0);
            if (feed == null) {
                Logger.warn("path: leg {} material unreadable -> falling back to a plain crossfade this time",
                        seg);
                return;
            }
            feeds.add(feed);
            total += feed.totalFrames();
        }
        if (total <= 0L) {
            Logger.warn("path: total length is 0 -> falling back to a plain crossfade this time");
            return;
        }

        int lastPass = feeds.get(feeds.size() - 1).passFrames;
        int fadeFrames = (int) Math.round(Math.max(1, Math.round(windowSecs / barSecs))
                * barSecs * (double) rate);
        if (fadeFrames < 1) {
            fadeFrames = 1;
        }
        if (fadeFrames > lastPass) {
            fadeFrames = lastPass;
        }
        int insertFrames = (int) Math.max(0L, total - fadeFrames);
        int seamFrames = (int) Math.min(Math.round(LOOP_SEAM_FADE_SECS * (double) rate),
                insertFrames);
        for (int i = 0; i < feeds.size(); i++) {
            SegmentFeed f = feeds.get(i);

            SegmentFeed wired = f.withWindow(i == 0 ? seamFrames : 0, total, f.windowOffset,
                    insertFrames);
            segmentedOutgoing = append(segmentedOutgoing, wired);
        }
        explicitPath = true;
        segmentWindowFrames = total;
        segmentInsertFrames = insertFrames;
        segmentCutSecs = path.get(0).endSecs;
        autoWindow = total;
        autoTailSecs = Math.max(0.0, outroEnvelope.durationSecs
                - (segmentCutSecs + total / (double) rate));
        finishGeometry();

        dropStretch();

        double fadeStartSecs = segmentCutSecs + Math.max(0L, segmentInsertFrames) / (double) rate;
        double goneSecs = segmentCutSecs + total / (double) rate;
        Logger.info("transition path: {} ({} legs, window {}s, path start {}s, "
                        + "A starts fading at {}s, the last {}s crossfades with B, A is gone by {}s)",
                AutoCue.describePath(path), Integer.valueOf(feeds.size()),
                fmt(total / (double) rate), fmt(segmentCutSecs), fmt(fadeStartSecs),
                fmt(loopFadeFrames() / (double) rate), fmt(goneSecs));
    }

    private SegmentFeed readFeed(String source, java.util.Map<String, String> headers,
                                 AutoCue.Segment seg, int passFrames, int seamFrames,
                                 long windowFrames, long windowOffset, int insertFrames) {
        if (source == null || seg == null || passFrames <= 0) {
            return null;
        }
        float[][] pcm;
        try {

            setMaterialContext(seg.startSecs - windowOffset / (double) rate,
                    vocalModeForMaterial());
            pcm = materialFor(source, headers, seg.startSecs, passFrames, rate);
        } catch (RuntimeException e) {
            Logger.warn("path/loop: material read failed ({})", e.toString());
            return null;
        }
        if (pcm == null || pcm.length == 0 || pcm[0].length < passFrames) {
            Logger.warn("path/loop: material read gave only {} frames ({} needed)",
                    Integer.valueOf(pcm == null || pcm.length == 0 ? 0 : pcm[0].length),
                    Integer.valueOf(passFrames));
            return null;
        }
        double rotateSecs = Double.NaN;
        int rotFrames = 0;
        if (seg.rotated()) {
            int want = (int) Math.round(
                    (seg.rotateStartSecs.doubleValue() - seg.startSecs) * (double) rate);
            if (want > 0 && want < passFrames) {
                rotateLeft(pcm, want, passFrames);
                rotateSecs = seg.rotateStartSecs.doubleValue();
                rotFrames = want;
            } else {
                Logger.warn("path: leg {} rotation point falls outside one pass ({} frames / one pass {} frames)"
                                + " -> this leg simply does not rotate", seg, Integer.valueOf(want),
                        Integer.valueOf(passFrames));
            }
        }
        return new SegmentFeed(seg.source, seg.startSecs, seg.endSecs, seg.repeat, pcm,
                passFrames, seamFrames, windowFrames, windowOffset, insertFrames, rotateSecs,
                rotFrames);
    }

    private static void rotateLeft(float[][] pcm, int k, int frames) {
        if (pcm == null || k <= 0 || k >= frames) {
            return;
        }
        float[] head = new float[k];
        for (int c = 0; c < pcm.length; c++) {
            float[] ch = pcm[c];
            if (ch == null || ch.length < frames) {
                continue;
            }
            boolean seen = false;
            for (int prev = 0; prev < c; prev++) {
                if (pcm[prev] == ch) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                continue;
            }
            System.arraycopy(ch, 0, head, 0, k);
            System.arraycopy(ch, k, ch, 0, frames - k);
            System.arraycopy(head, 0, ch, frames - k, k);
        }
    }

    int passFramesOf(AutoCue.Segment seg) {
        return (int) Math.round(seg.spanSecs() * (double) rate);
    }

    private boolean pathMatchesDecision(java.util.List<AutoCue.Segment> path,
                                        AutoCue.Segment synthesized, int loopFrames,
                                        long windowTotal) {
        if (path == null || path.size() != 1) {
            return false;
        }
        AutoCue.Segment seg = path.get(0);
        if (seg == null || !seg.source.equals(synthesized.source)
                || seg.repeat != synthesized.repeat) {
            return false;
        }
        if (seg.rotated()) {

            return false;
        }
        if (passFramesOf(seg) != loopFrames) {
            Logger.debug("path: the plan layer says {} frames, the decision says {} frames -> following the decision",
                    Integer.valueOf(passFramesOf(seg)), Integer.valueOf(loopFrames));
            return false;
        }
        return (long) seg.repeat * (long) loopFrames == windowTotal;
    }

    private void finishGeometry() {
        segmentTailSecs = autoTailSecs;
        segmentFadeStartSecs = segmentedOutgoing.length == 0
                ? 0.0 : sourceSecsAt(segmentInsertFrames);
    }

    private double sourceSecsAt(long windowPos) {
        for (SegmentFeed f : segmentedOutgoing) {
            long local = windowPos - f.windowOffset;
            if (local >= 0L && local < f.totalFrames()) {
                return f.secsAt(local, rate);
            }
        }

        return segmentCutSecs;
    }

    private static int clampRepeats(int repeats) {
        return Math.max(1, Math.min(repeats, 2));
    }

    private void resetSegments() {
        segmentedOutgoing = NO_SEGMENTS;
        segmentIndex = 0;
        segmentWindowFrames = 0L;
        segmentInsertFrames = 0;
        segmentCutSecs = 0.0;
        segmentFadeStartSecs = 0.0;
        segmentTailSecs = 0.0;
        explicitPath = false;
        loopFeed = null;
        loopPlan = null;
        stretchFeed = null;
        stretchSpeed = 1.0;
        stretchSpeedStart = 1.0;
        stretchSpeedMean = 1.0;
    }

    private void enqueue(SegmentFeed feed) {
        segmentedOutgoing = append(segmentedOutgoing, feed);
    }

    private void dropStretch() {
        if (stretchFeed == null) {
            return;
        }
        stretchFeed = null;
        stretchSpeed = 1.0;
        stretchSpeedStart = 1.0;
        stretchSpeedMean = 1.0;
        SegmentFeed[] kept = NO_SEGMENTS;
        for (SegmentFeed f : segmentedOutgoing) {
            if (f != null && f.windowFrames > 0L) {
                kept = append(kept, f);
            }
        }
        segmentedOutgoing = kept;
    }

    private static SegmentFeed[] append(SegmentFeed[] array, SegmentFeed feed) {
        SegmentFeed[] out = java.util.Arrays.copyOf(array, array.length + 1);
        out[array.length] = feed;
        return out;
    }

    static double snapToBar(double secs, double phase, double barSecs) {
        return AutoCue.snapToBar(secs, phase, barSecs);
    }

    private static String describeRanges(double[] ranges) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < ranges.length; i += 2) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format(java.util.Locale.ROOT, "%.1f~%.1fs",
                    Double.valueOf(ranges[i]), Double.valueOf(ranges[i + 1])));
        }
        return sb.toString();
    }

    public Transition inner() {
        return base;
    }

    static double[] windowCandidates(double nominal, boolean tune) {
        if (!tune) {
            return new double[]{nominal};
        }
        double[] raw = {nominal * 0.5, nominal * 0.75, nominal, nominal * 1.5, nominal * 2.0};
        double[] out = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = Math.max(1.0, Math.min(20.0, raw[i]));
        }

        out[2] = nominal;
        return out;
    }

    public boolean tunesWindow() {
        return tuneWindow;
    }

    static java.util.function.Supplier<AutoCue.KeyRelation> keyRelationOfPair(
            String outro, java.util.Map<String, String> outroHeaders,
            String intro, java.util.Map<String, String> introHeaders) {
        return () -> AutoCue.keyRelationOf(outro, outroHeaders, intro, introHeaders);
    }

    private double[] windowCandidates(double nominal) {
        TempoRamp r = rampPlan;
        double cap = r != null && r.active ? Math.min(nominal, r.windowSecs) : 0.0;
        return windowCandidates(nominal, tuneWindow, cap);
    }

    static double[] windowCandidates(double nominal, boolean tune, double cap) {
        if (!(cap > 0.0)) {
            return windowCandidates(nominal, tune);
        }
        double top = Math.max(1.0, Math.min(nominal, cap));
        if (!tune) {
            return new double[]{top};
        }
        double[] raw = {top * 0.5, top * 0.75, top};
        double[] out = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = Math.max(1.0, Math.min(20.0, raw[i]));
        }

        out[out.length - 1] = top;
        return out;
    }

    private static boolean lyricRulesHold(AutoCue.Cue cue, AutoCue.Envelope outroEnv,
                                          AutoCue.Envelope introEnv) {
        try {
            AutoCue.LyricSpans introSpans = AutoCue.vocalPhraseSpans(introEnv);
            AutoCue.LyricSpans outroSpans = AutoCue.vocalPhraseSpans(outroEnv);
            if (introSpans == null || introSpans.starts.length == 0) {
                return true;
            }
            double gA = AutoCue.phraseGapOf(outroSpans, cue.outroCutSecs + cue.windowSecs);
            return AutoCue.headFollowsPhraseGap(introSpans, cue.introEntrySecs,
                    new double[]{cue.windowSecs}, gA, AutoCue.ENTRY_HEAD_TOL_SECS);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    @Override
    public String toString() {
        return "AutoCueTransition{" + base + ", auto cue=" + (autoWindow > 0L
                ? ("exit tail drop " + fmt(autoTailSecs) + "s / entry " + fmt(autoEntrySecs) + "s")
                : "not computed") + "}";
    }

    @Override
    public long contentFramesFor(long outFrames) {
        final double s0 = introSpeedStart;
        final int w = introSpeedWindow;
        if (w <= 1 || Math.abs(1.0 - s0) < 1e-6 || outFrames <= 0L) {
            return outFrames;
        }
        final long p = Math.min(outFrames, w);
        final double src = s0 * p + (1.0 - s0) * (double) p * (double) p / (2.0 * w);
        return Math.round(src) + Math.max(0L, outFrames - p);
    }
}
