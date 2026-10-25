package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.AudioException;
import dev.t1m3.qplayer.audio.core.ChannelLayout;
import dev.t1m3.qplayer.audio.decode.Decoder;
import dev.t1m3.qplayer.audio.decode.TrackSpec;
import dev.t1m3.qplayer.util.Logger;

public final class AutoCue {

    public static final double HOP_SECS = 0.02;

    static final int TARGET_ONSET_RATE = 22_050;

    public static final double MIN_OUTRO_FRACTION = 0.10;

    public static final double MIN_OUTRO_SECS = 10.0;

    public static final double TAIL_GUARD_SECS = 2.0;

    public static final double DROP_FREE_FRACTION = 0.12;

    public static final double OUTRO_FULL_RATIO = 0.99;

    static double tailTerm(double tailDropSecs, double durationSecs) {
        double fraction = Math.min(1.0,
                Math.max(0.0, tailDropSecs) / Math.max(durationSecs, 1e-6));
        if (fraction <= DROP_FREE_FRACTION) {
            return 1.0;
        }
        return 1.0 - (fraction - DROP_FREE_FRACTION) / (1.0 - DROP_FREE_FRACTION);
    }

    public static final double MAX_INTRO_SECS = 20.0;

    public static final double MAX_INTRO_FRACTION = 0.25;

    static final int MAX_CANDIDATES = 20_000;

    static final double BAND_LOW_HZ = 200.0;

    static final double BAND_HIGH_HZ = 2000.0;

    static final int WAVE_RATE = 1000;

    static final int SHORTLIST = 96;

    static final double COARSE_GATE_PENALTY = 10.0;

    static final double COARSE_INTENSITY_WEIGHT = 1.0;

    static final float MIN_BEAT_CONFIDENCE = 0.5f;

    static final double VOCAL_GATE_SECS = 0.5;

    static final double CENTER_SALIENCE_MIN = 0.62;

    public static final double MAX_CUT_FROM_END_FRACTION = 0.25;

    public static final double WINDOW_OPTIMUM_SECS = 6.5;

    private static volatile double appleBarWindowWeight = 0.0;

    public static void setAppleBarWindowWeight(double w) {
        appleBarWindowWeight = w > 0.0 ? w : 0.0;
    }

    private static Double appleBarWindowAdd(Double base, double windowSecs) {
        double add = appleBarWindowBonus(windowSecs);
        if (base == null) {
            return add > 0.0 ? Double.valueOf(add) : null;
        }
        return Double.valueOf(base.doubleValue() + add);
    }

    private static double appleBarWindowBonus(double windowSecs) {
        if (appleBarWindowWeight <= 0.0) {
            return 0.0;
        }
        double bar = appleBarTieSecsRef;
        if (!(bar > 0.0)) {
            return 0.0;
        }

        if (appleBarTargetBars <= 0) {
            return 0.0;
        }
        double bars = windowSecs / bar;
        double err = Math.abs(bars - appleBarTargetBars);
        return appleBarWindowWeight * Math.max(0.0, 1.0 - err / appleBarTargetBars);
    }

    private static volatile double appleBarTieSecsRef = 0.0;

    private static volatile int appleBarTargetBars = 0;

    public static void setAppleBarTargetBars(int bars) {
        appleBarTargetBars = Math.max(0, bars);
    }

    public static void setAppleBarTieSecsRef(double secs) {
        appleBarTieSecsRef = secs > 0.0 ? secs : 0.0;
    }

    public static final double WINDOW_LENGTH_BONUS = 0.06;

    public static final double WINDOW_LENGTH_FALLOFF_SECS = 8.0;

    public static double windowLengthBonus(double windowSecs) {
        double d = Math.abs(windowSecs - WINDOW_OPTIMUM_SECS);
        if (d >= WINDOW_LENGTH_FALLOFF_SECS) {
            return 0.0;
        }
        return WINDOW_LENGTH_BONUS * (1.0 - d / WINDOW_LENGTH_FALLOFF_SECS);
    }

    private static volatile double[] melodyGate;

    static final double[] LIMITS_BEFORE_RELAX = {0.15, 20.0, 0.20};

    static double[] limitsAfterRelax() {
        return new double[] {MAX_CUT_FROM_END_FRACTION, MIN_OUTRO_SECS, MIN_OUTRO_FRACTION};
    }

    static void applyMelodyGate(double melody) {
        double[] opened = limitsAfterRelax();
        double[] gated = new double[3];
        for (int i = 0; i < 3; i++) {
            gated[i] = gatedLimit(LIMITS_BEFORE_RELAX[i], opened[i], melody);
        }
        melodyGate = gated;
    }

    static void clearMelodyGate() {
        melodyGate = null;
    }

    static double cutFromEndFractionNow() {
        double[] g = melodyGate;
        return g == null ? MAX_CUT_FROM_END_FRACTION : g[0];
    }

    static double minOutroSecsNow() {
        double[] g = melodyGate;
        return g == null ? MIN_OUTRO_SECS : g[1];
    }

    static double minOutroFractionNow() {
        double[] g = melodyGate;
        return g == null ? MIN_OUTRO_FRACTION : g[2];
    }

    public static double maxCutFromEndFraction() {

        return cutFromEndFractionNow();
    }

    private static final double[] EMPTY_TIMES = new double[0];

    private static final class FloatList {
        private float[] data;
        private int size;

        FloatList(int capacity) {
            data = new float[Math.max(16, capacity)];
        }

        void add(float v) {
            if (size == data.length) {
                data = java.util.Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = v;
        }

        int size() {
            return size;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }

    private AutoCue() {
    }

    public static final class Envelope {

        public final float[] rms;

        public final float[] onset;

        public final double durationSecs;

        public final float structureOutroStartSecs;

        public final float structureIntroStartSecs;

        public float[] chroma;

        public int chromaFrames;

        public double chromaHopSecs;

        public Analysis.Tempo tempo;

        public final int beatRate;

        public final float[][] band;

        public final float[] center;

        public final float[] wave;

        public final int waveRate;

        private volatile float[] midCache;
        private volatile float[] voiceBasicCache;
        private volatile float[] voiceActiveCache;

        private volatile float[] intensityCache;

        float[] midColumn() {
            float[] c = midCache;
            if (c != null) {
                return c;
            }
            if (band == null) {
                return null;
            }
            float[] out = new float[band.length];
            for (int i = 0; i < band.length; i++) {
                out[i] = band[i][1];
            }
            midCache = out;
            return out;
        }

        float[] voiceBasicMask() {
            float[] m = voiceBasicCache;
            if (m != null) {
                return m;
            }
            float[] mid = midColumn();
            if (mid == null) {
                return null;
            }
            float[] mask = new float[mid.length];
            for (int i = 0; i < mid.length; i++) {
                mask[i] = voiceBasicAt(mid, i) ? 1f : 0f;
            }
            voiceBasicCache = mask;
            return mask;
        }

        float[] voiceActiveMask() {
            float[] m = voiceActiveCache;
            if (m != null) {
                return m;
            }
            float[] mid = midColumn();
            if (mid == null) {
                return null;
            }
            float[] mask = new float[mid.length];
            for (int i = 0; i < mid.length; i++) {
                mask[i] = voiceBasicAt(mid, i) && centered(Envelope.this, i) ? 1f : 0f;
            }
            voiceActiveCache = mask;
            return mask;
        }

        Envelope(float[] rms, float[] onset, double durationSecs) {
            this(rms, onset, durationSecs, null, 0, null, null, 0);
        }

        Envelope sliceForCue(double fromSecs, double toSecs) {
            if (rms == null || rms.length == 0 || !(durationSecs > 0.0)) {
                return this;
            }
            double from = Math.max(0.0, Math.min(fromSecs, durationSecs));
            double to = Math.max(from, Math.min(toSecs, durationSecs));
            if (to - from < 0.5) {
                return this;
            }
            double framesPerSec = rms.length / durationSecs;
            int i0 = (int) Math.floor(from * framesPerSec);
            int i1 = (int) Math.ceil(to * framesPerSec);
            i0 = Math.max(0, Math.min(i0, rms.length - 1));
            i1 = Math.max(i0 + 1, Math.min(i1, rms.length));
            float[] newRms = java.util.Arrays.copyOfRange(rms, i0, i1);
            float[] newOnset = (onset != null && onset.length >= i1)
                    ? java.util.Arrays.copyOfRange(onset, i0, i1) : onset;
            float[][] newBand = null;
            if (band != null) {
                newBand = new float[band.length][];
                for (int b = 0; b < band.length; b++) {
                    newBand[b] = (band[b] != null && band[b].length >= i1)
                            ? java.util.Arrays.copyOfRange(band[b], i0, i1) : band[b];
                }
            }
            float[] newWave = wave;
            if (wave != null && waveRate > 0) {
                int w0 = (int) Math.floor(from * waveRate);
                int w1 = (int) Math.ceil(to * waveRate);
                w0 = Math.max(0, Math.min(w0, wave.length - 1));
                w1 = Math.max(w0 + 1, Math.min(w1, wave.length));
                newWave = java.util.Arrays.copyOfRange(wave, w0, w1);
            }

            float newOutro = structureOutroStartSecs >= 0f
                    ? (float) Math.max(0.0, structureOutroStartSecs - from) : structureOutroStartSecs;
            float newIntro = structureIntroStartSecs >= 0f
                    ? (float) Math.max(0.0, structureIntroStartSecs - from) : structureIntroStartSecs;
            return new Envelope(newRms, newOnset, to - from, tempo, beatRate,
                    newBand, newWave, waveRate, newOutro, newIntro);
        }

        Envelope(float[] rms, float[] onset, double durationSecs,
                 Analysis.Tempo tempo, int beatRate) {
            this(rms, onset, durationSecs, tempo, beatRate, null, null, 0);
        }

        Envelope(float[] rms, float[] onset, double durationSecs,
                 Analysis.Tempo tempo, int beatRate,
                 float[][] band, float[] wave, int waveRate) {
            this(rms, onset, durationSecs, tempo, beatRate, band, wave, waveRate, -1f, -1f);
        }

        Envelope(float[] rms, float[] onset, double durationSecs,
                 Analysis.Tempo tempo, int beatRate,
                 float[][] band, float[] wave, int waveRate,
                 float structureOutroStartSecs, float structureIntroStartSecs) {
            this(rms, onset, durationSecs, tempo, beatRate, band, wave, waveRate,
                    structureOutroStartSecs, structureIntroStartSecs, null);
        }

        Envelope(float[] rms, float[] onset, double durationSecs,
                 Analysis.Tempo tempo, int beatRate,
                 float[][] band, float[] wave, int waveRate,
                 float structureOutroStartSecs, float structureIntroStartSecs,
                 float[] center) {
            this.center = center;
            this.structureOutroStartSecs = structureOutroStartSecs;
            this.structureIntroStartSecs = structureIntroStartSecs;
            this.rms = rms;
            this.onset = onset;
            this.durationSecs = durationSecs;
            this.tempo = tempo;
            this.beatRate = beatRate;
            this.band = band;
            this.wave = wave;
            this.waveRate = waveRate;
        }

        Envelope withTempo(Analysis.Tempo newTempo) {
            Envelope copy = new Envelope(rms, onset, durationSecs, newTempo, beatRate,
                    band, wave, waveRate, structureOutroStartSecs, structureIntroStartSecs,
                    center);
            copy.chroma = chroma;
            copy.chromaFrames = chromaFrames;
            copy.chromaHopSecs = chromaHopSecs;
            return copy;
        }

        public boolean hasBeatGrid() {
            return tempo != null && beatRate > 0 && tempo.periodFrames > 1.0f
                    && tempo.confidence >= MIN_BEAT_CONFIDENCE;
        }

        public double[] beatsIn(double fromSecs, double toSecs) {
            if (!hasBeatGrid() || toSecs <= fromSecs) {
                return EMPTY_TIMES;
            }
            double periodSecs = tempo.periodFrames / (double) beatRate;
            if (periodSecs <= 0.01 || periodSecs > 5.0) {
                return EMPTY_TIMES;
            }
            long fromFrame = (long) Math.ceil(fromSecs * beatRate);
            long toFrame = (long) Math.floor(toSecs * beatRate);
            if (toFrame <= fromFrame) {
                return EMPTY_TIMES;
            }
            long beat = tempo.nextBeatAfter(fromFrame);

            double[] out = new double[Math.min(MAX_CANDIDATES, 64)];
            int n = 0;
            while (beat <= toFrame && n < MAX_CANDIDATES) {
                if (n == out.length) {
                    out = java.util.Arrays.copyOf(out,
                            Math.min(MAX_CANDIDATES, out.length * 2));
                }
                out[n++] = beat / (double) beatRate;

                beat = tempo.nextBeatAfter(beat + 1L);
            }
            return n == out.length ? out : java.util.Arrays.copyOf(out, n);
        }

        public double secs(int index) {
            return index * HOP_SECS;
        }

        public int indexAt(double secs) {
            int index = (int) Math.round(secs / HOP_SECS);
            if (index < 0) {
                return 0;
            }
            return index >= rms.length ? Math.max(0, rms.length - 1) : index;
        }

        public int hops() {
            return rms.length;
        }
    }

    public static final class Segment {

        public final String source;

        public final double startSecs;

        public final double endSecs;

        public final int repeat;

        public final Double rotateStartSecs;

        static final double ROTATE_EPS_SECS = 1e-3;

        public Segment(String source, double startSecs, double endSecs, int repeat) {
            this(source, startSecs, endSecs, repeat, null);
        }

        public Segment(String source, double startSecs, double endSecs, int repeat,
                       Double rotateStartSecs) {
            this.source = source;
            this.startSecs = startSecs;
            this.endSecs = endSecs;
            this.repeat = Math.max(1, repeat);
            this.rotateStartSecs = rotateStartSecs != null
                    && rotateStartSecs.doubleValue() > startSecs + ROTATE_EPS_SECS
                    && rotateStartSecs.doubleValue() < endSecs - ROTATE_EPS_SECS
                    ? rotateStartSecs : null;
        }

        public double spanSecs() {
            return endSecs - startSecs;
        }

        public boolean rotated() {
            return rotateStartSecs != null;
        }

        public boolean fromIntro() {
            return "intro".equals(source);
        }

        @Override
        public String toString() {
            return (fromIntro() ? "B" : "A") + "[" + fmt(startSecs) + "→" + fmt(endSecs)
                    + ")s"
                    + (rotateStartSecs == null ? "" : ("↻" + fmt(rotateStartSecs.doubleValue()) + "s"))
                    + (repeat > 1 ? ("×" + repeat) : "");
        }
    }

    public enum PointMode {
        AGGRESSIVE(0.0, 0.0),
        STABLE(40.0, 60.0),
        CONSERVATIVE(20.0, 20.0);

        private final double aKeepSecs;
        private final double bKeepSecs;

        PointMode(double aKeepSecs, double bKeepSecs) {
            this.aKeepSecs = aKeepSecs;
            this.bKeepSecs = bKeepSecs;
        }

        public double aKeepSecs() {
            return aKeepSecs;
        }

        public double bKeepSecs() {
            return bKeepSecs;
        }
    }

    private static volatile PointMode pointMode = PointMode.AGGRESSIVE;

    public static void setPointMode(PointMode mode) {
        pointMode = mode == null ? PointMode.AGGRESSIVE : mode;
    }

    public static PointMode pointMode() {
        return pointMode;
    }

    public static final class Cue {

        public Cue withTimes(double outroCut, double introEntry) {
            return new Cue(outroCut, introEntry, windowSecs, tailDropSecs, cost, note);
        }

        public Cue withNote(String newNote) {
            return new Cue(outroCutSecs, introEntrySecs, windowSecs, tailDropSecs, cost, newNote);
        }

        public Cue withLanding(double outroCut, double introEntry, double outroTailDrop) {
            return new Cue(outroCut, introEntry, windowSecs, outroTailDrop, cost, note);
        }

        public Cue withOutroLoop(OutroLoopPlan loop, String addNote) {
            return new Cue(outroCutSecs, introEntrySecs, windowSecs, tailDropSecs, cost,
                    note + (addNote == null ? "" : addNote), intensityGap, intensityOk, loop,
                    segments);
        }

        public final double outroCutSecs;

        public final double introEntrySecs;

        public final double windowSecs;

        public final double tailDropSecs;

        public final double cost;

        public final String note;

        public final double intensityGap;

        public final boolean intensityOk;

        public final AutoCue.OutroLoopPlan outroLoop;

        public final java.util.List<Segment> segments;

        Cue(double outroCutSecs, double introEntrySecs, double windowSecs, double tailDropSecs,
            double cost, String note) {
            this(outroCutSecs, introEntrySecs, windowSecs, tailDropSecs, cost, note,
                    INTENSITY_UNKNOWN_BAND, true, null, null);
        }

        Cue(double outroCutSecs, double introEntrySecs, double windowSecs, double tailDropSecs,
            double cost, String note, double intensityGap, boolean intensityOk) {
            this(outroCutSecs, introEntrySecs, windowSecs, tailDropSecs, cost, note,
                    intensityGap, intensityOk, null, null);
        }

        Cue(double outroCutSecs, double introEntrySecs, double windowSecs, double tailDropSecs,
            double cost, String note, double intensityGap, boolean intensityOk,
            AutoCue.OutroLoopPlan outroLoop) {
            this(outroCutSecs, introEntrySecs, windowSecs, tailDropSecs, cost, note,
                    intensityGap, intensityOk, outroLoop, null);
        }

        Cue(double outroCutSecs, double introEntrySecs, double windowSecs, double tailDropSecs,
            double cost, String note, double intensityGap, boolean intensityOk,
            AutoCue.OutroLoopPlan outroLoop, java.util.List<Segment> segments) {
            this.outroCutSecs = outroCutSecs;
            this.introEntrySecs = introEntrySecs;
            this.windowSecs = windowSecs;
            this.tailDropSecs = tailDropSecs;
            this.cost = cost;
            this.note = note;
            this.intensityGap = intensityGap;
            this.intensityOk = intensityOk;
            this.outroLoop = outroLoop;
            this.segments = segments;
        }

        @Override
        public String toString() {
            return "Cue{exit " + fmt(outroCutSecs) + "s, entry " + fmt(introEntrySecs)
                    + "s, overlap " + fmt(windowSecs) + "s, tail drop " + fmt(tailDropSecs)
                    + "s, cost " + String.format(java.util.Locale.US, "%.3f", cost)
                    + (segments == null ? "" : (", path " + describePath(segments))) + "}";
        }
    }

    static String describePath(java.util.List<Segment> segments) {
        if (segments == null) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (Segment s : segments) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(s);
        }
        return sb.length() == 0 ? "empty" : sb.toString();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    public static Envelope scan(String source) {
        return scan(source, null);
    }

    public static Envelope scan(String source, java.util.Map<String, String> headers) {

        return cachedScan(source, headers, 0.0, false, false);
    }

    public static Envelope scanIntro(String source, java.util.Map<String, String> headers) {
        return scanIntro(source, headers, Bridge.OFF);
    }

    public static Envelope scanIntro(String source, java.util.Map<String, String> headers,
                                     Bridge bridge) {

        return cachedScan(source, headers, 0.0, true, bridge != null && bridge.widen);
    }

    static Envelope scanIntroUncached(String source, java.util.Map<String, String> headers) {
        return scanInternal(source, headers, 0.0, true, false);
    }

    public static Envelope scan(String source, java.util.Map<String, String> headers, double maxSecs) {
        return cachedScan(source, headers, maxSecs, false, false);
    }

    private static final java.util.Map<String, CachedEnvelope> SCAN_CACHE =
            java.util.Collections.synchronizedMap(
                    new java.util.LinkedHashMap<String, CachedEnvelope>(8, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(
                                java.util.Map.Entry<String, CachedEnvelope> eldest) {
                            return size() > SCAN_CACHE_MAX;
                        }
                    });

    static final int SCAN_CACHE_MAX = 4;
    static final long SCAN_CACHE_TTL_MS = 10L * 60L * 1000L;

    private static final class CachedEnvelope {
        final Envelope envelope;
        final long bornAtMs;

        CachedEnvelope(Envelope envelope) {
            this.envelope = envelope;
            this.bornAtMs = System.currentTimeMillis();
        }
    }

    private static Envelope cachedScan(String source, java.util.Map<String, String> headers,
                                       double maxSecs, boolean introBudget, boolean bridge) {
        String key = cacheKey(source, headers, maxSecs, introBudget, bridge);
        if (key == null) {

            return scanInternal(source, headers, maxSecs, introBudget, bridge);
        }
        java.util.Map<String, Object> scoped = Decoder.preparationCache();
        CachedEnvelope hit = scoped == null ? SCAN_CACHE.get(key) : (CachedEnvelope) scoped.get(key);
        if (hit != null && System.currentTimeMillis() - hit.bornAtMs <= SCAN_CACHE_TTL_MS) {
            return hit.envelope;
        }
        Envelope fresh = scanInternal(source, headers, maxSecs, introBudget, bridge);
        if (scoped == null) SCAN_CACHE.put(key, new CachedEnvelope(fresh));
        else scoped.put(key, new CachedEnvelope(fresh));
        return fresh;
    }

    private static String cacheKey(String source, java.util.Map<String, String> headers,
                                   double maxSecs, boolean introBudget, boolean bridge) {
        StringBuilder sb = new StringBuilder();
        sb.append(source).append('\u0000').append(introBudget ? 'I' : 'F')
                .append(bridge ? 'B' : 'b')
                .append('\u0000').append(maxSecs);
        if (headers != null) {
            java.util.TreeMap<String, String> sorted =
                    new java.util.TreeMap<String, String>(headers);
            for (java.util.Map.Entry<String, String> e : sorted.entrySet()) {
                sb.append('\u0000').append(e.getKey()).append('=').append(e.getValue());
            }
        }
        // Content/file URIs are not java.io.File paths. Scope their cache to one preparation,
        // so repeated scans reuse audio without retaining stale provider content across sessions.
        if (Decoder.preparationCache() != null) return sb.toString();
        try {
            java.io.File file = new java.io.File(source);
            if (!file.isFile()) {
                return null;
            }
            sb.append('\u0000').append(file.length()).append('@').append(file.lastModified());
        } catch (RuntimeException e) {
            return null;
        }
        return sb.toString();
    }

    static void clearScanCache() {
        SCAN_CACHE.clear();
    }

    private static Envelope scanInternal(String source, java.util.Map<String, String> headers,
                                         double maxSecs, boolean introBudget, boolean bridge) {
        Decoder decoder = headers == null || headers.isEmpty()
                ? Decoder.open(source)
                : Decoder.open(source, headers);
        try {
            TrackSpec spec = decoder.spec();
            int rate = Math.max(spec.sampleRate, 1);
            int channels = Math.max(spec.layout.count(), 1);

            int decimate = Math.max(1, (int) Math.round((double) rate / TARGET_ONSET_RATE));
            int workRate = Math.max(1, rate / decimate);
            int hopFrames = Math.max(1, (int) Math.round(workRate * HOP_SECS));
            Double trueDuration = spec.durationSecs();
            if (introBudget) {

                maxSecs = introScanSecs(trueDuration,
                        bridge ? Bridge.DEFAULT : Bridge.OFF);
            }

            long stopFrames = maxSecs > 0.0 ? (long) (maxSecs * rate) : Long.MAX_VALUE;

            PrepareStage.mark(introBudget ? "scanning the incoming head (whole track or the leading section)" : "scanning the whole outgoing track (decode included)");
            OnsetFlux flux = new OnsetFlux();

            FloatList rms = new FloatList(4096);
            FloatList wave = new FloatList(4096);
            FloatList centerList = null;
            AudioBuf block = new AudioBuf(ChannelLayout.of(channels), BLOCK_FRAMES);
            double sumSquares = 0.0;
            int inHop = 0;
            double partial = 0.0;
            int partialCount = 0;
            long decoded = 0L;

            dev.t1m3.qplayer.audio.dsp.Biquad bandLow =
                    dev.t1m3.qplayer.audio.dsp.Biquad.lowPass(workRate, (float) BAND_LOW_HZ, 0.7071f);
            dev.t1m3.qplayer.audio.dsp.Biquad bandHigh =
                    dev.t1m3.qplayer.audio.dsp.Biquad.lowPass(workRate, (float) BAND_HIGH_HZ, 0.7071f);
            double[] bandSum = new double[3];

            FloatList bandLowList = new FloatList(4096);
            FloatList bandMidList = new FloatList(4096);
            FloatList bandHighList = new FloatList(4096);

            boolean stereo = channels >= 2;
            if (stereo) {
                centerList = new FloatList(4096);
            }
            final FloatList center = centerList;
            double partialL = 0.0;
            double partialR = 0.0;
            double midPower = 0.0;
            double sidePower = 0.0;

            int waveDecimate = Math.max(1, (int) Math.round((double) workRate / WAVE_RATE));
            int waveRate = Math.max(1, workRate / waveDecimate);
            double wavePartial = 0.0;
            int waveCount = 0;

            SpectralOnsets.Stream spectral = new SpectralOnsets.Stream(workRate);

            while (true) {
                int got = decoder.decodeInto(block);
                if (got <= 0) {
                    break;
                }

                float[] backing = block.backingArray();
                int[] chanBase = new int[channels];
                for (int c = 0; c < channels; c++) {
                    chanBase[c] = block.channelBase(c);
                }
                for (int frame = 0; frame < got; frame++) {

                    float mono = 0f;
                    float left = 0f;
                    float right = 0f;
                    for (int c = 0; c < channels; c++) {
                        float v = backing[chanBase[c] + frame];
                        mono += v;
                        if (c == 0) {
                            left = v;
                        } else if (c == 1) {
                            right = v;
                        }
                    }
                    mono /= channels;
                    if (channels >= 2) {
                        partialL += left;
                        partialR += right;
                    }
                    partial += mono;
                    partialCount++;
                    if (partialCount < decimate) {
                        continue;
                    }
                    float sample = (float) (partial / partialCount);
                    if (stereo) {
                        float l = (float) (partialL / partialCount);
                        float r = (float) (partialR / partialCount);
                        double mid = (l + r) * 0.5;
                        double side = (l - r) * 0.5;
                        midPower += mid * mid;
                        sidePower += side * side;
                    }
                    partial = 0.0;
                    partialCount = 0;
                    partialL = 0.0;
                    partialR = 0.0;
                    sumSquares += (double) sample * sample;
                    spectral.push(sample);

                    double lowPart = bandLow.process(sample);
                    double highUpTo = bandHigh.process(sample);
                    double midPart = highUpTo - lowPart;
                    double highPart = sample - highUpTo;
                    bandSum[0] += lowPart * lowPart;
                    bandSum[1] += midPart * midPart;
                    bandSum[2] += highPart * highPart;
                    if (++inHop >= hopFrames) {
                        rms.add((float) Math.sqrt(sumSquares / inHop));
                        bandLowList.add((float) Math.sqrt(bandSum[0] / inHop));
                        bandMidList.add((float) Math.sqrt(bandSum[1] / inHop));
                        bandHighList.add((float) Math.sqrt(bandSum[2] / inHop));
                        if (stereo && center != null) {
                            center.add(centerSalience(midPower, sidePower));
                            midPower = 0.0;
                            sidePower = 0.0;
                        }
                        sumSquares = 0.0;
                        bandSum[0] = 0.0;
                        bandSum[1] = 0.0;
                        bandSum[2] = 0.0;
                        inHop = 0;
                    }
                    wavePartial += sample;
                    if (++waveCount >= waveDecimate) {
                        wave.add((float) (wavePartial / waveCount));
                        wavePartial = 0.0;
                        waveCount = 0;
                    }
                    flux.push(sample);
                }
                decoded += got;
                if (decoded >= stopFrames) {

                    break;
                }
            }
            if (inHop > 0) {
                rms.add((float) Math.sqrt(sumSquares / inHop));
                bandLowList.add((float) Math.sqrt(bandSum[0] / inHop));
                bandMidList.add((float) Math.sqrt(bandSum[1] / inHop));
                bandHighList.add((float) Math.sqrt(bandSum[2] / inHop));
            }
            if (rms.size() == 0) {
                throw AudioException.decode("cannot obtain an energy envelope (is the source empty?): " + source);
            }
            float[] values = rms.toArray();

            double durationSecs = trueDuration != null
                    ? trueDuration.doubleValue() : values.length * HOP_SECS;
            float[] waveValues = wave.toArray();

            float[] spectralOnset = spectral.values();

            Analysis.Tempo tempo = tempoFromSidecar(source, workRate);
            if (tempo != null) {
                Logger.debug("auto cue: beat grid from the sidecar cache ({} BPM, confidence {})",
                        Float.valueOf(tempo.bpm), Float.valueOf(tempo.confidence));
            } else {
                tempo = spectralOnset.length > 0
                        ? BeatTracker.track(spectralOnset, spectral.hop(), workRate)
                        : null;
            }
            if (tempo == null) {
                Logger.debug("auto cue: new path gave no beat grid ({}), falling back to the legacy RMS-difference path",
                        BeatTracker.lastRejectReason());
                tempo = Analysis.tempoFromRmsFlux(values, hopFrames, workRate);
            } else {
                Logger.debug("auto cue: beat grid from the new path ({} BPM, residual-scaled confidence {})",
                        Float.valueOf(tempo.bpm), Float.valueOf(tempo.confidence));
            }

            float[] chroma = spectral.chromaValues();
            double chromaHopSecs = spectral.hop() / (double) Math.max(1, workRate);
            int chromaFrames = spectral.chromaFrameCount();
            float[] novelty = noveltyCurve(chroma, spectral.chromaFrameCount(), chromaHopSecs);
            float[] level = spectral.levelValues();
            float structureOutro = (float) noveltyBoundary(novelty, level, chromaHopSecs, true,
                    durationSecs * 0.5, durationSecs);
            float structureIntro = (float) noveltyBoundary(novelty, level, chromaHopSecs, false,
                    Math.min(5.0, durationSecs * 0.1), Math.min(60.0, durationSecs * 0.5));
            Logger.debug("auto cue: segmentation (chroma novelty as a low-weight bonus "
                            + NOVELTY_WEIGHT + " participates in point picking) -> outro start {}, intro start {}",
                    structureOutro >= 0f ? fmt(structureOutro) + " s" : "none",
                    structureIntro >= 0f ? fmt(structureIntro) + " s" : "none");

            float[] centerValues = center == null ? null : center.toArray();

            float[] lowValues = bandLowList.toArray();
            float[] midValues = bandMidList.toArray();
            float[] highValues = bandHighList.toArray();
            float[][] bandValues = new float[lowValues.length][3];
            for (int i = 0; i < lowValues.length; i++) {
                bandValues[i][0] = lowValues[i];
                bandValues[i][1] = midValues[i];
                bandValues[i][2] = highValues[i];
            }
            Envelope scanned = envelopeOf(values, durationSecs, tempo, workRate,
                    bandValues, waveValues, waveRate,
                    structureOutro, structureIntro, centerValues);

            scanned.chroma = chroma;
            scanned.chromaFrames = chromaFrames;
            scanned.chromaHopSecs = chromaHopSecs;
            return scanned;
        } finally {
            decoder.close();
        }
    }

    static Analysis.Tempo tempoFromSidecar(String source, int workRate) {
        if (source == null || workRate <= 0) {
            return null;
        }
        BeatGridSidecar grid;
        try {

            grid = BeatGridSidecar.read(java.nio.file.Paths.get(source));
        } catch (RuntimeException e) {
            return null;
        } catch (LinkageError e) {

            Logger.warn("beat sidecar unavailable ({}), computing the hard way this time", e.toString());
            return null;
        }
        if (grid == null || grid.beats.length == 0) {
            return null;
        }
        float period = (float) (60.0 / grid.bpm * workRate);
        if (!(period > 1.0f)) {
            return null;
        }
        float phase = (float) ((grid.beats[0] * workRate) % period);
        if (phase < 0f) {
            phase += period;
        }
        return new Analysis.Tempo((float) grid.bpm, period, phase, (float) grid.confidence);
    }

    public static double[] vocalStrength(Envelope env) {
        double ref = vocalReference(env);
        return Double.isNaN(ref) ? null : vocalStrength(env, ref);
    }

    public static double vocalReference(Envelope env) {
        if (env == null || env.band == null || env.band.length < 2 || env.band[1] == null) {
            return Double.NaN;
        }
        float[] mid = env.band[1];
        if (mid.length == 0) {
            return Double.NaN;
        }
        double[] sorted = new double[mid.length];
        for (int i = 0; i < mid.length; i++) {
            sorted[i] = Math.max(0.0, mid[i]);
        }
        java.util.Arrays.sort(sorted);
        double ref = sorted[(int) Math.floor(0.95 * (sorted.length - 1))];
        return ref > 1e-9 ? ref : Double.NaN;
    }

    public static double[] vocalStrength(Envelope env, double reference) {
        if (env == null || env.band == null || env.band.length < 2 || env.band[1] == null
                || !(reference > 1e-9)) {
            return null;
        }
        float[] mid = env.band[1];
        if (mid.length == 0) {
            return null;
        }
        double[] out = new double[mid.length];
        for (int i = 0; i < mid.length; i++) {
            out[i] = Math.min(1.0, Math.max(0.0, mid[i] / reference));
        }
        return out;
    }

    public static double vocalOverlapStrength(Envelope a, double aFrom,
                                              Envelope b, double bFrom, double windowSecs) {

        double refA = vocalReference(a);
        double refB = vocalReference(b);
        if (Double.isNaN(refA) || Double.isNaN(refB) || !(windowSecs > 0.0)) {
            return Double.NaN;
        }
        double shared = Math.max(refA, refB);
        double[] sa = vocalStrength(a, shared);
        double[] sb = vocalStrength(b, shared);
        if (sa == null || sb == null) {
            return Double.NaN;
        }
        int ia = (int) Math.floor(aFrom / HOP_SECS);
        int ib = (int) Math.floor(bFrom / HOP_SECS);
        int n = (int) Math.round(windowSecs / HOP_SECS);
        if (n <= 0) {
            return Double.NaN;
        }
        double best = 0.0;
        boolean any = false;
        for (int k = 0; k < n; k++) {
            int x = ia + k;
            int y = ib + k;
            if (x < 0 || y < 0 || x >= sa.length || y >= sb.length) {
                continue;
            }
            any = true;
            best = Math.max(best, Math.min(sa[x], sb[y]));
        }
        return any ? best : Double.NaN;
    }

    public static final int KIND_SINGING = 0;
    public static final int KIND_SPEECH = 1;
    public static final int KIND_RAPPING = 2;

    static final double KIND_SING_CHROMA_STABILITY = 0.80;

    static final double KIND_SING_MAX_ONSET_RATE = 2.5;

    static final double KIND_RAP_MIN_ONSET_RATE = 3.5;

    static final double KIND_RAP_MIN_BEAT_ALIGNED = 0.60;

    public static int vocalKind(Envelope env, double fromSecs, double toSecs) {
        double[] strength = vocalStrength(env);
        if (strength == null || env.chroma == null || env.chromaFrames <= 0) {
            return -1;
        }
        int from = (int) Math.floor(fromSecs / HOP_SECS);
        int to = (int) Math.ceil(toSecs / HOP_SECS);
        from = Math.max(0, from);
        to = Math.min(strength.length, to);
        if (to - from < KIND_MIN_FRAMES) {
            return -1;
        }
        double stability = chromaStability(env, from, to);
        double rate = onsetRate(env, from, to);
        double aligned = beatAlignedFraction(env, from, to);
        if (!(rate >= 0.0) || !(stability >= 0.0)) {
            return -1;
        }
        if (stability >= KIND_SING_CHROMA_STABILITY && rate <= KIND_SING_MAX_ONSET_RATE) {
            return KIND_SINGING;
        }
        if (rate >= KIND_RAP_MIN_ONSET_RATE && aligned >= KIND_RAP_MIN_BEAT_ALIGNED) {
            return KIND_RAPPING;
        }
        return KIND_SPEECH;
    }

    static final int KIND_MIN_FRAMES = 25;

    static final double KIND_BEAT_TOL_SECS = 0.09;

    static final int KIND_BEAT_SUBDIVISIONS = 4;

    static double chromaStability(Envelope env, int from, int to) {
        int frames = env.chromaFrames;
        if (frames <= 1) {
            return -1.0;
        }
        double sum = 0.0;
        int pairs = 0;
        for (int f = from; f + 1 < to && f + 1 < frames; f++) {
            double dot = 0.0;
            double na = 0.0;
            double nb = 0.0;
            for (int c = 0; c < 12; c++) {
                double x = env.chroma[f * 12 + c];
                double y = env.chroma[(f + 1) * 12 + c];
                dot += x * y;
                na += x * x;
                nb += y * y;
            }
            if (na > 1e-12 && nb > 1e-12) {
                sum += dot / Math.sqrt(na * nb);
                pairs++;
            }
        }
        return pairs == 0 ? -1.0 : sum / pairs;
    }

    static double onsetRate(Envelope env, int from, int to) {
        if (env.onset == null || env.onset.length == 0) {
            return -1.0;
        }
        int lo = Math.max(0, from);
        int hi = Math.min(env.onset.length, to);
        if (hi - lo < 2) {
            return -1.0;
        }
        double sum = 0.0;
        for (int i = lo; i < hi; i++) {
            sum += Math.max(0.0, env.onset[i]);
        }
        double mean = sum / (hi - lo);
        int peaks = 0;
        for (int i = lo + 1; i + 1 < hi; i++) {
            if (env.onset[i] > mean && env.onset[i] >= env.onset[i - 1]
                    && env.onset[i] >= env.onset[i + 1]) {
                peaks++;
            }
        }
        double secs = (hi - lo) * HOP_SECS;
        return secs > 1e-6 ? peaks / secs : -1.0;
    }

    static double beatAlignedFraction(Envelope env, int from, int to) {
        if (env.onset == null || env.tempo == null || env.beatRate <= 0
                || !(env.tempo.periodFrames > 1f)) {
            return -1.0;
        }
        double periodSecs = env.tempo.periodFrames / (double) env.beatRate;
        double phaseSecs = env.tempo.phaseFrames / (double) env.beatRate;
        if (!(periodSecs > 1e-3)) {
            return -1.0;
        }
        int lo = Math.max(1, from);
        int hi = Math.min(env.onset.length - 1, to);
        double sum = 0.0;
        for (int i = lo; i < hi; i++) {
            sum += Math.max(0.0, env.onset[i]);
        }
        double mean = (hi - lo) > 0 ? sum / (hi - lo) : 0.0;
        int peaks = 0;
        int aligned = 0;
        for (int i = lo; i < hi; i++) {
            if (env.onset[i] > mean && env.onset[i] >= env.onset[i - 1]
                    && env.onset[i] >= env.onset[i + 1]) {
                peaks++;
                double t = i * HOP_SECS;
                double step = periodSecs / KIND_BEAT_SUBDIVISIONS;
                double off = (t - phaseSecs) % step;
                if (off < 0) {
                    off += step;
                }
                double dist = Math.min(off, step - off);
                if (dist <= KIND_BEAT_TOL_SECS) {
                    aligned++;
                }
            }
        }
        return peaks == 0 ? -1.0 : aligned / (double) peaks;
    }

    public static final boolean PHASE_ALIGN_ENABLED = true;

    public static double structureAlignedCut(Envelope outro, double windowSecs, double cutFloor,
                                             Envelope intro, double entrySecs) {
        if (!PHASE_ALIGN_ENABLED) {
            return structureAlignedCut(outro, windowSecs, cutFloor);
        }
        double cut = structureAlignedCut(outro, windowSecs, cutFloor);
        if (Double.isNaN(cut) || intro == null || intro.tempo == null || intro.tempo.bpm <= 0f
                || outro.tempo == null || outro.tempo.bpm <= 0f) {
            return cut;
        }
        double pA = 60.0 / outro.tempo.bpm;
        double pB = 60.0 / intro.tempo.bpm;
        double best = cut;
        double bestGap = Double.MAX_VALUE;
        double gapAtZero = -1.0;
        int steps = 36;
        for (int k = -steps; k <= steps; k++) {
            double delta = k * (PHASE_SHIFT_MAX_SECS / steps);
            double c = cut + delta;
            if (c <= Math.max(0.0, cutFloor - 1e-9)) {
                continue;
            }
            double gap = Math.abs(phaseOf(outro, c, pA) - phaseOf(intro, entrySecs, pB)) / pB;
            double score = gap + 0.05 * (Math.abs(k) / (double) steps);
            if (k == 0) {
                gapAtZero = gap;
            }
            if (score < bestGap) {
                bestGap = score;
                best = c;
            }
        }

        if (gapAtZero >= 0.0 && gapAtZero - Math.min(bestGap, gapAtZero) < 0.03) {
            return cut;
        }
        return best;
    }

    public static double structureAlignedCut(Envelope outro, double windowSecs, double cutFloor) {
        if (outro == null || !(windowSecs > 0.0)) {
            return Double.NaN;
        }
        double structure = outroStartSecs(outro);
        if (!(structure > 0.0)) {
            return Double.NaN;
        }
        double handover = snapToBeat(outro, structure, 0.0, outro.durationSecs);
        if (!(handover > 0.0)) {
            return Double.NaN;
        }
        double cut = handover - windowSecs;
        if (!(cut > Math.max(0.0, cutFloor - 1e-9)) || cut >= handover) {
            return Double.NaN;
        }
        return cut;
    }

    public static double structureOf(Envelope intro) {
        return intro == null ? Double.NaN : introStartSecs(intro);
    }

    public static double outroStructureOf(Envelope outro) {
        return outro == null ? Double.NaN : outroStartSecs(outro);
    }

    public static double structureAlignedEntry(Envelope outro, Envelope intro,
                                               double windowSecs, double entryCeil) {
        return structureAlignedEntry(outro, intro, windowSecs, entryCeil, 0.0);
    }

    public static double structureAlignedEntry(Envelope outro, Envelope intro,
                                               double windowSecs, double entryCeil,
                                               double extraShiftSecs) {
        double plain = structureAlignedEntry(intro, windowSecs, entryCeil);
        if (outro == null || Double.isNaN(plain) || intro == null || intro.tempo == null
                || intro.tempo.bpm <= 0f || outro.tempo == null || outro.tempo.bpm <= 0f) {
            return plain;
        }
        double beatB = 60.0 / intro.tempo.bpm;
        double structure = introStartSecs(intro);
        if (Double.isNaN(structure)) {
            return plain;
        }
        double target = structure - windowSecs * 0.5;

        double best = plain;
        double bestScore = Double.MAX_VALUE;

        double gridH = snapToBeat(intro, target, 0.0, intro.durationSecs);
        for (int k = -4; k <= 4; k++) {
            double h = gridH + k * (beatB / 16.0);
            double cut = h - windowSecs;
            if (h <= 0.0 || cut < 0.0 || h > intro.durationSecs) {
                continue;
            }
            if (h - windowSecs < entryCeil) {
                continue;
            }
            double dA = phaseDistance(outro, cut) / (60.0 / outro.tempo.bpm);
            double dB = phaseDistance(intro, h) / beatB;

            double score = Math.max(dA, dB) + 0.15 * (Math.abs(k) / 16.0);
            if (score < bestScore) {
                bestScore = score;
                best = h - windowSecs;
            }
        }
        return best;
    }

    public static final double PHASE_SHIFT_MAX_SECS = 0.09;

    public static double phaseAlignedDelta(Envelope outro, double cutSecs,
                                           Envelope intro, double entrySecs) {
        if (outro == null || intro == null || outro.tempo == null || intro.tempo == null
                || outro.tempo.bpm <= 0f || intro.tempo.bpm <= 0f) {
            return 0.0;
        }
        double pA = 60.0 / outro.tempo.bpm;
        double pB = 60.0 / intro.tempo.bpm;
        double best = 0.0;
        double bestScore = Double.MAX_VALUE;
        int steps = 36;
        for (int k = -steps; k <= steps; k++) {
            double delta = k * (PHASE_SHIFT_MAX_SECS / steps);
            double gap = Math.abs(phaseOf(outro, cutSecs, pA)
                    - phaseOf(intro, entrySecs + delta, pB)) / pB;
            double score = gap + 0.05 * (Math.abs(k) / (double) steps);
            if (score < bestScore) {
                bestScore = score;
                best = delta;
            }
        }
        return best;
    }

    static double phaseOf(Envelope env, double secs, double periodSecs) {
        if (periodSecs <= 0.0) {
            return 0.0;
        }
        double f = secs / periodSecs;
        return (f - Math.floor(f)) * periodSecs;
    }

    static double phaseDistance(Envelope env, double secs) {
        if (env == null || env.tempo == null || env.tempo.bpm <= 0f) {
            return 0.0;
        }
        double beat = 60.0 / env.tempo.bpm;
        double snapped = snapToBeat(env, secs, 0.0, Double.MAX_VALUE);
        double d = Math.abs(secs - snapped);
        d = d - Math.floor(d / beat) * beat;
        return Math.min(d, beat - d);
    }

    public static double structureAlignedEntry(Envelope intro, double windowSecs, double entryCeil) {
        if (intro == null || !(windowSecs > 0.0)) {
            return Double.NaN;
        }
        double structure = introStartSecs(intro);
        if (!(structure >= ENTRY_ANCHOR_MIN_SECS)) {
            return Double.NaN;
        }

        double handoverTarget = structure - windowSecs * 0.5;
        double handover = snapToBeat(intro, handoverTarget, 0.0, intro.durationSecs);
        if (!(handover > 0.0)) {
            return Double.NaN;
        }
        double entry = handover - windowSecs;
        if (!(entry > 0.0) || entry > entryCeil) {
            return Double.NaN;
        }
        return entry;
    }

    static final boolean TRACE_CUE =
            Boolean.getBoolean("qplayer.trace.cue");

    public static double barPhaseFromPhrases(LyricSpans spans, double barSecs) {
        if (spans == null || spans.starts == null || spans.starts.length < BAR_PHASE_MIN_PHRASES
                || !(barSecs > 0.0)) {
            return -1.0;
        }
        double bestPhase = -1.0;
        double bestCost = Double.MAX_VALUE;
        int steps = (int) Math.round(barSecs / BAR_PHASE_STEP_SECS);
        for (int k = 0; k < steps; k++) {
            double phase = k * BAR_PHASE_STEP_SECS;
            double cost = 0.0;
            for (double t : spans.starts) {
                double off = ((t - phase) % barSecs + barSecs) % barSecs;
                cost += Math.min(off, barSecs - off);
            }
            cost /= spans.starts.length;
            if (cost < bestCost) {
                bestCost = cost;
                bestPhase = phase;
            }
        }
        return bestPhase;
    }

    public static final class BarPhase {

        public final double phase;

        public final boolean fromPhrases;

        public final double disagreement;

        BarPhase(double phase, boolean fromPhrases, double disagreement) {
            this.phase = phase;
            this.fromPhrases = fromPhrases;
            this.disagreement = disagreement;
        }
    }

    static final double BAR_PHRASE_GOOD_BEATS = 0.5;

    public static BarPhase barPhaseResolved(Envelope env, LyricSpans spans, double barSecs) {
        double low = env == null ? Double.NaN : barPhaseOf(env, barSecs);
        double phr = barPhaseFromPhrases(spans, barSecs);
        double disagree = barsPhaseDisagreement(env, spans, barSecs);
        if (phr >= 0.0 && spans != null && spans.starts != null
                && spans.starts.length >= BAR_PHASE_MIN_PHRASES) {
            double beat = barSecs / 4.0;
            double sum = 0.0;
            for (double t : spans.starts) {
                double off = ((t - phr) % barSecs + barSecs) % barSecs;
                sum += Math.min(off, barSecs - off);
            }
            if (sum / spans.starts.length <= BAR_PHRASE_GOOD_BEATS * beat) {
                return new BarPhase(phr, true, disagree);
            }
        }
        return new BarPhase(low, false, disagree);
    }

    static final double BAR_PHASE_STEP_SECS = 0.01;

    static final int BAR_PHASE_MIN_PHRASES = 6;

    public static double barsPhaseDisagreement(Envelope env, LyricSpans spans, double barSecs) {
        if (env == null || !(barSecs > 0.0)) {
            return Double.NaN;
        }
        double low = barPhaseOf(env, barSecs);
        double phr = barPhaseFromPhrases(spans, barSecs);
        if (low < 0.0 || phr < 0.0) {
            return Double.NaN;
        }
        double d = Math.abs(low - phr) % barSecs;
        return Math.min(d, barSecs - d);
    }
    static final double INTRO_SCAN_MARGIN_SECS = 5.0;

    public static double introScanSecs(Double durationSecs) {
        return introScanSecs(durationSecs, Bridge.OFF);
    }

    public static double introScanSecs(Double durationSecs, Bridge bridge) {
        if (durationSecs == null || !(durationSecs.doubleValue() > 0.0)) {
            return 0.0;
        }
        double dur = durationSecs.doubleValue();

        double classic = Math.min(MAX_INTRO_SECS, dur * MAX_INTRO_FRACTION)
                + INTRO_SCAN_MARGIN_SECS;
        if (bridge == null || !bridge.widen) {
            return classic;
        }
        return Math.max(classic, Math.min(dur, bridge.entryCeil(dur)
                + INTRO_SCAN_MARGIN_SECS + 2.0 * INTENSITY_SEAM_WIN_SECS));
    }

    static final class OnsetFlux {

        private final dev.t1m3.qplayer.audio.dsp.Fft fft = new dev.t1m3.qplayer.audio.dsp.Fft(FRAME);

        private final float[] hann = new float[FRAME];

        private final float[] window = new float[FRAME];
        private final float[] re = new float[FRAME];
        private final float[] im = new float[FRAME];
        private final float[] mag = new float[FRAME / 2 + 1];
        private final float[] prev = new float[FRAME / 2 + 1];
        private final java.util.ArrayList<Float> values = new java.util.ArrayList<Float>();
        private boolean firstFrame = true;

        private long n;

        OnsetFlux() {
            dev.t1m3.qplayer.audio.dsp.Fft.hann(hann);
        }

        void push(float sample) {

            if (n < FRAME) {
                window[(int) n] = sample;
            } else {
                System.arraycopy(window, 1, window, 0, FRAME - 1);
                window[FRAME - 1] = sample;
            }
            n++;

            if (n < FRAME || n % HOP != 0L) {
                return;
            }
            for (int i = 0; i < FRAME; i++) {
                re[i] = window[i] * hann[i];
            }
            java.util.Arrays.fill(im, 0f);
            fft.forward(re, im);
            fft.magnitude(re, im, mag);
            if (firstFrame) {
                System.arraycopy(mag, 0, prev, 0, mag.length);
                firstFrame = false;
                values.add(Float.valueOf(0f));
                return;
            }
            float flux = 0f;
            for (int k = 0; k < mag.length; k++) {
                float d = mag[k] - prev[k];
                if (d > 0f) {
                    flux += d;
                }
            }
            values.add(Float.valueOf(flux / (float) mag.length));
            System.arraycopy(mag, 0, prev, 0, mag.length);
        }

        float[] envelopeValues() {
            float[] envelope = new float[values.size()];
            float sum = 0f;
            for (int i = 0; i < envelope.length; i++) {
                envelope[i] = values.get(i).floatValue();
                sum += envelope[i];
            }
            if (envelope.length == 0) {
                return envelope;
            }
            float mean = sum / envelope.length;
            for (int i = 0; i < envelope.length; i++) {
                envelope[i] -= mean;
            }
            return envelope;
        }

    }

    private static final int FRAME = 2048;
    private static final int HOP = 512;

    private static final int BLOCK_FRAMES = 8192;

    static Envelope envelopeOf(float[] rms, double durationSecs) {
        return envelopeOf(rms, durationSecs, null, 0);
    }

    static Envelope envelopeOf(float[] rms, double durationSecs,
                               Analysis.Tempo tempo, int beatRate) {
        return envelopeOf(rms, durationSecs, tempo, beatRate, null, null, 0);
    }

    static Envelope envelopeOf(float[] rms, double durationSecs, Analysis.Tempo tempo,
                               int beatRate, float[][] band, float[] wave, int waveRate) {
        return envelopeOf(rms, durationSecs, tempo, beatRate, band, wave, waveRate, -1f, -1f);
    }

    static Envelope envelopeOf(float[] rms, double durationSecs, Analysis.Tempo tempo,
                               int beatRate, float[][] band, float[] wave, int waveRate,
                               float structureOutroStartSecs, float structureIntroStartSecs) {
        return envelopeOf(rms, durationSecs, tempo, beatRate, band, wave, waveRate,
                structureOutroStartSecs, structureIntroStartSecs, null);
    }

    static Envelope envelopeOf(float[] rms, double durationSecs, Analysis.Tempo tempo,
                               int beatRate, float[][] band, float[] wave, int waveRate,
                               float structureOutroStartSecs, float structureIntroStartSecs,
                               float[] center) {
        float[] sorted = rms.clone();
        java.util.Arrays.sort(sorted);
        float reference = sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.95))];
        if (reference <= 1e-6f) {
            reference = 1f;
        }
        float[] normalized = new float[rms.length];
        float[] onset = new float[rms.length];
        for (int i = 0; i < rms.length; i++) {
            normalized[i] = Math.min(1f, rms[i] / reference);
        }
        for (int i = 1; i < rms.length; i++) {
            onset[i] = Math.max(0f, normalized[i] - normalized[i - 1]);
        }
        float[][] bandNormalized = null;
        if (band != null && band.length == normalized.length) {
            bandNormalized = new float[normalized.length][];
            for (int i = 0; i < band.length; i++) {
                float[] row = band[i];
                float[] out = new float[row.length];
                for (int b = 0; b < row.length; b++) {
                    out[b] = Math.min(1f, row[b] / reference);
                }
                bandNormalized[i] = out;
            }
        }
        return new Envelope(normalized, onset, durationSecs, tempo, beatRate,
                bandNormalized, wave, waveRate, structureOutroStartSecs, structureIntroStartSecs,
                center);
    }

    public static final double INTENSITY_SMOOTH_SECS = 0.6;

    public static final double INTENSITY_SEAM_WIN_SECS = 0.5;

    public static final double INTENSITY_SUSTAIN_SECS = 6.0;

    static final double INTENSITY_DYN_WIN_SECS = 1.0;

    static final float INTENSITY_W_ATTACK = 0.35f;

    static final float INTENSITY_W_BRIGHT = 0.30f;

    static final float INTENSITY_W_BASS = 0.15f;

    static final float INTENSITY_W_DYN = 0.20f;

    static final float INTENSITY_ONSET_SCALE = 0.15f;

    static final float INTENSITY_BRIGHT_SCALE = 0.45f;

    static final float INTENSITY_BASS_SCALE = 0.55f;

    static final float INTENSITY_DYN_SCALE = 0.80f;

    static final float INTENSITY_UNKNOWN_BAND = 0.5f;

    static float[] intensityCurve(Envelope env) {
        float[] cached = env.intensityCache;
        if (cached != null) {
            return cached;
        }
        int n = env.rms.length;
        float[] out = new float[n];
        if (n == 0) {
            env.intensityCache = out;
            return out;
        }

        float[] attack = new float[n];
        float[] bright = new float[n];
        float[] bass = new float[n];
        float[] dyn = new float[n];
        int dynWin = Math.max(1, (int) Math.round(INTENSITY_DYN_WIN_SECS / HOP_SECS));
        boolean hasBand = env.band != null && env.band.length == n;
        double[] prefix = prefixSums(env.rms);
        for (int i = 0; i < n; i++) {
            attack[i] = Math.max(0f, env.onset[i]);
            if (hasBand) {
                double lo = env.band[i][0];
                double mid = env.band[i][1];
                double hi = env.band[i][2];
                double sum = lo + mid + hi;
                if (sum > 1e-9) {
                    bright[i] = (float) (hi / sum);
                    bass[i] = (float) (lo / sum);
                } else {
                    bright[i] = INTENSITY_UNKNOWN_BAND;
                    bass[i] = INTENSITY_UNKNOWN_BAND;
                }
            } else {
                bright[i] = INTENSITY_UNKNOWN_BAND;
                bass[i] = INTENSITY_UNKNOWN_BAND;
            }
            int a = Math.max(0, i - dynWin);
            int b = Math.min(n - 1, i + dynWin);
            double mean = rangeMean(prefix, a, b);
            dyn[i] = mean <= 1e-9 ? 0f : (float) (Math.abs(env.rms[i] - mean) / mean);
        }

        if (!hasBand) {

            java.util.Arrays.fill(bright, INTENSITY_UNKNOWN_BAND);
            java.util.Arrays.fill(bass, INTENSITY_UNKNOWN_BAND);
        } else {
            scaleInPlace(bright, INTENSITY_BRIGHT_SCALE);
            scaleInPlace(bass, INTENSITY_BASS_SCALE);
        }
        scaleInPlace(attack, INTENSITY_ONSET_SCALE);
        scaleInPlace(dyn, INTENSITY_DYN_SCALE);
        for (int i = 0; i < n; i++) {
            out[i] = INTENSITY_W_ATTACK * attack[i]
                    + INTENSITY_W_BRIGHT * bright[i]
                    + INTENSITY_W_BASS * bass[i]
                    + INTENSITY_W_DYN * dyn[i];
        }
        smoothInPlace(out, Math.max(1, (int) Math.round(INTENSITY_SMOOTH_SECS / HOP_SECS)));
        env.intensityCache = out;
        return out;
    }

    private static double rangeMean(double[] prefix, int from, int to) {
        int a = Math.max(0, Math.min(from, to));
        int b = Math.max(a, to);
        return (prefix[b + 1] - prefix[a]) / Math.max(1, b - a + 1);
    }

    private static void scaleInPlace(float[] v, float scale) {
        float s = scale > 1e-6f ? scale : 1f;
        for (int i = 0; i < v.length; i++) {
            v[i] = Math.min(1f, Math.max(0f, v[i] / s));
        }
    }

    private static void smoothInPlace(float[] v, int half) {
        if (v.length == 0 || half <= 0) {
            return;
        }
        float[] src = v.clone();
        double[] prefix = prefixSums(src);
        for (int i = 0; i < v.length; i++) {
            int a = Math.max(0, i - half);
            int b = Math.min(v.length - 1, i + half);
            v[i] = (float) rangeMean(prefix, a, b);
        }
    }

    public static double intensityAt(Envelope env, double secs) {
        if (env == null || env.rms.length == 0) {
            return INTENSITY_UNKNOWN_BAND;
        }
        float[] curve = intensityCurve(env);
        int win = Math.max(0, (int) Math.round(INTENSITY_SEAM_WIN_SECS / HOP_SECS));
        int c = env.indexAt(secs);
        int a = Math.max(0, c - win);
        int b = Math.min(curve.length - 1, c + win);
        double sum = 0.0;
        for (int i = a; i <= b; i++) {
            sum += curve[i];
        }
        return sum / (b - a + 1);
    }

    public static double intensityAhead(Envelope env, double secs, double secsAhead) {
        if (env == null || env.rms.length == 0) {
            return INTENSITY_UNKNOWN_BAND;
        }
        if (!(secsAhead > 0.0)) {
            return intensityAt(env, secs);
        }
        float[] curve = intensityCurve(env);
        int from = Math.max(0, env.indexAt(secs));
        int span = Math.max(1, (int) Math.round(secsAhead / HOP_SECS));
        int to = Math.min(curve.length, from + span);
        double worst = Double.MAX_VALUE;
        for (int i = from; i < to; i++) {
            worst = Math.min(worst, curve[i]);
        }
        return worst == Double.MAX_VALUE ? INTENSITY_UNKNOWN_BAND : worst;
    }

    public static double intensityGap(Envelope outro, double cutSecs,
                                      Envelope intro, double entrySecs) {
        return Math.abs(intensityAt(outro, cutSecs) - intensityAt(intro, entrySecs));
    }

    public static final double INTENSITY_GAP_MAX = 0.12;

    public static final double INTENSITY_WEIGHT = 4.0;

    public static final double INTENSITY_DEAD_ZONE = 0.05;

    public static final double INTENSITY_SCALE = 0.25;

    public static final double INTENSITY_VETO_GAP = INTENSITY_DEAD_ZONE;

    public static final double INTENSITY_GOOD_ENOUGH_GAP = 0.05;

    public static final double ENTRY_JUMP_MAX_FRACTION = 0.35;

    public static final double ENTRY_OUTRO_FLOOR_SECS = 60.0;

    static final double ENTRY_ANCHOR_BACK_SECS = 6.0;

    static final double ENTRY_ANCHOR_AHEAD_SECS = 3.0;

    static final double ENTRY_ANCHOR_MIN_SECS = 8.0;

    static final double INTRO_SKIP_FLOOR_SECS = 8.0;

    public static final class Bridge {

        public static final Bridge OFF = new Bridge(false, false, false, false, false, false,
                0.0, 0.0, 0.0, 0.0);

        public static final Bridge MEASURE = new Bridge(true, false, false, false, false, false,
                INTENSITY_VETO_GAP, INTENSITY_GOOD_ENOUGH_GAP, 0.0, 0.0);

        public static final Bridge LYRIC = new Bridge(true, false, false, true, false,
                true,
                INTENSITY_VETO_GAP, INTENSITY_GOOD_ENOUGH_GAP,
                ENTRY_JUMP_MAX_FRACTION, ENTRY_OUTRO_FLOOR_SECS);

        public static final Bridge DEFAULT = new Bridge(true, true, true, true, true, true,
                INTENSITY_VETO_GAP, INTENSITY_GOOD_ENOUGH_GAP,
                ENTRY_JUMP_MAX_FRACTION, ENTRY_OUTRO_FLOOR_SECS);

        public final boolean measure;

        public final boolean score;

        public final boolean veto;

        public final boolean lyricGate;

        public final boolean widen;

        public final boolean sustain;

        public final double vetoGap;

        public final double goodEnoughGap;

        public final double gapMax;

        public final double jumpMaxFraction;

        public final double outroFloorSecs;

        Bridge(boolean measure, boolean score, boolean veto, boolean widen, boolean sustain,
               boolean lyricGate, double vetoGap, double goodEnoughGap, double jumpMaxFraction,
               double outroFloorSecs) {
            this.measure = measure;
            this.score = score;
            this.veto = veto;
            this.widen = widen;
            this.sustain = sustain;
            this.lyricGate = lyricGate;
            this.vetoGap = vetoGap;
            this.goodEnoughGap = goodEnoughGap;
            this.gapMax = vetoGap;
            this.jumpMaxFraction = jumpMaxFraction;
            this.outroFloorSecs = outroFloorSecs;
        }

        public Bridge noWiden() {
            return new Bridge(measure, score, veto, false, sustain, lyricGate, vetoGap,
                    goodEnoughGap, jumpMaxFraction, outroFloorSecs);
        }

        public double entryCeil(double introDurationSecs) {
            double classic = Math.min(MAX_INTRO_SECS, introDurationSecs * MAX_INTRO_FRACTION);
            if (!widen || !(introDurationSecs > 0.0)) {
                return classic;
            }
            double jump = Math.min(introDurationSecs - outroFloorSecs,
                    introDurationSecs * jumpMaxFraction);
            return Math.max(classic, jump);
        }
    }

    public static Cue plan(Envelope outro, Envelope intro, double windowSecs) {
        return plan(outro, intro, new double[]{windowSecs});
    }

    public static Cue plan(Envelope outro, Envelope intro, double[] windowCandidates) {
        return plan(outro, intro, windowCandidates, null, null);
    }

    public static Cue plan(Envelope outro, Envelope intro, double[] windowCandidates,
                           double[] outroLines, double[] introLines) {
        return plan(outro, intro, windowCandidates, outroLines, introLines, Bridge.OFF);
    }

    public static Cue planWithLyricSpans(Envelope outro, Envelope intro,
                                         double[] windowCandidates,
                                         LyricSpans outroLyrics, LyricSpans introLyrics,
                                         Bridge bridge) {
        return planCollect(outro, intro, windowCandidates, outroLyrics, introLyrics, null,
                bridge == null ? Bridge.OFF : bridge, null);
    }

    public static Cue planWithLyricSpans(Envelope outro, Envelope intro,
                                         double[] windowCandidates,
                                         LyricSpans outroLyrics, LyricSpans introLyrics,
                                         Bridge bridge,
                                         java.util.function.Supplier<KeyRelation> keyRelation) {
        return planCollect(outro, intro, windowCandidates, outroLyrics, introLyrics, null,
                bridge == null ? Bridge.OFF : bridge, keyRelation);
    }

    public static Cue planWithLyricSpans(Envelope outro, Envelope intro,
                                         double[] windowCandidates,
                                         LyricSpans outroLyrics, LyricSpans introLyrics,
                                         Bridge bridge,
                                         java.util.function.Supplier<KeyRelation> keyRelation,
                                         double pinnedOutroCutSecs) {
        return planCollect(outro, intro, windowCandidates, outroLyrics, introLyrics, null,
                bridge == null ? Bridge.OFF : bridge, keyRelation, pinnedOutroCutSecs);
    }

    public static Cue plan(Envelope outro, Envelope intro, double[] windowCandidates,
                           double[] outroLines, double[] introLines, Bridge bridge) {
        return planCollect(outro, intro, windowCandidates, spansOfStarts(outroLines),
                spansOfStarts(introLines), null, bridge == null ? Bridge.OFF : bridge, null);
    }

    public static Cue[] topCues(Envelope outro, Envelope intro, double[] windowCandidates,
                                int k) {
        java.util.List<Score> ranked = new java.util.ArrayList<Score>();
        Cue best = planCollect(outro, intro, windowCandidates, null, null, ranked, Bridge.OFF, null);
        if (best == null) {
            return new Cue[0];
        }
        int n = Math.min(Math.max(k, 1), ranked.size());
        Cue[] out = new Cue[n];
        for (int i = 0; i < n; i++) {
            Score sc = ranked.get(i);
            double tail = Math.max(0.0, outro.durationSecs - (sc.cutSecs + sc.windowSecs));
            out[i] = new Cue(sc.cutSecs, sc.entrySecs, sc.windowSecs, tail, 1.0 - sc.total,
                    "candidate " + (i + 1) + "：" + fmt(sc.cutSecs) + "s / " + fmt(sc.entrySecs)
                            + "s · " + fmt(sc.windowSecs) + "s (total " + f2(sc.total) + "）");
        }
        return out;
    }

    private static Cue planCollect(Envelope outro, Envelope intro, double[] windowCandidates,
                                   LyricSpans outroSpans, LyricSpans introSpans,
                                   java.util.List<Score> rankedSink, Bridge bridge,
                                   java.util.function.Supplier<KeyRelation> keyRelation) {
        return planCollect(outro, intro, windowCandidates, outroSpans, introSpans, rankedSink,
                bridge, keyRelation, 0.0);
    }

    private static Cue planCollect(Envelope outro, Envelope intro, double[] windowCandidates,
                                   LyricSpans outroSpans, LyricSpans introSpans,
                                   java.util.List<Score> rankedSink, Bridge bridge,
                                   java.util.function.Supplier<KeyRelation> keyRelation,
                                   double pinnedOutroCutSecs) {
        if (outro == null || intro == null || windowCandidates == null
                || windowCandidates.length == 0) {
            return null;
        }
        double[] windows = sanitizeWindows(windowCandidates);
        if (windows.length == 0) {
            return null;
        }

        double narrowest = windows[0];
       double outroDuration = outro.durationSecs;
        double introDuration = intro.durationSecs;
        double cutFloor = Math.max(minOutroSecsNow(), outroDuration * minOutroFractionNow());

        PointMode modeHere = pointMode;
        if (modeHere.aKeepSecs() > 0.0) {
            cutFloor = Math.max(cutFloor, outroDuration - modeHere.aKeepSecs());
        }
        double cutCeil = outroDuration - narrowest - TAIL_GUARD_SECS;
        if (modeHere.aKeepSecs() > 0.0 && cutFloor > cutCeil) {

            cutCeil = cutFloor;
        }
        if (pinnedOutroCutSecs > 0.0) {

            cutFloor = pinnedOutroCutSecs;
            cutCeil = pinnedOutroCutSecs;
        }
        double lastLegalCut = outroDuration - TAIL_GUARD_SECS;
        double widest = windows[windows.length - 1];

        double coveredSecs = intro.rms.length * HOP_SECS;
        double coverageCeil = Math.max(0.0,
                coveredSecs - INTENSITY_SEAM_WIN_SECS - 2.0 * HOP_SECS);
        double entryCeil = Math.min(bridge.entryCeil(introDuration),
                Math.max(coverageCeil, Math.min(MAX_INTRO_SECS, introDuration * MAX_INTRO_FRACTION)));

        if (modeHere.bKeepSecs() > 0.0) {
            entryCeil = Math.min(entryCeil, modeHere.bKeepSecs());
        }

        double outroStructure = outroStartSecs(outro);
        double introStructure = introStartSecs(intro);

        double entryFloorRaw = 0.0;
        double entryCeilRaw = entryCeil;
        double entryFloor = 0.0;

        if (bridge.widen && introStructure >= ENTRY_ANCHOR_MIN_SECS) {

            double target = introStructure + INTRO_ANCHOR_LEAD_SECS
                    - Math.min(widest, ENTRY_ANCHOR_MID_CAP_SECS) * 0.5;

            double lo = Math.max(0.0, Math.min(introStructure - INTRO_SKIP_FLOOR_SECS,
                    target - ENTRY_ANCHOR_BACK_SECS));

            if (entryCeil - lo >= 2.0) {
                entryFloor = lo;
            }
        }

        if (entryCeil <= 0.0) {
            return null;
        }

        double derivedWindowSecs = 0.0;
        if (bridge.lyricGate && introSpans != null && introSpans.starts.length >= 3
                && outroSpans != null) {
            double tB = phraseGapOf(introSpans, intro.durationSecs);
            double tA = phraseGapOf(outroSpans, outro.durationSecs);
            if (!Double.isNaN(tB) && !Double.isNaN(tA) && tB > 0.0 && tA > 0.0 && tB > tA) {
                double derived = Math.max(1.0,
                        Math.min(windows[0], tB - tA - 0.3));
                boolean known = false;
                for (int i = 0; i < windows.length; i++) {
                    if (Math.abs(windows[i] - derived) < 1e-6) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    double[] grown = java.util.Arrays.copyOf(windows, windows.length + 1);
                    grown[windows.length] = derived;
                    java.util.Arrays.sort(grown);
                    windows = grown;
                    derivedWindowSecs = derived;
                }
            }
        }

        double[] cutTimes = pinnedOutroCutSecs > 0.0
                ? new double[]{pinnedOutroCutSecs}
                : candidates(outro, cutFloor, cutCeil, SIDE_OUTRO);
        if (cutTimes.length == 0 && pinnedOutroCutSecs > 0.0) {
            return null;
        }
        double[] entryTimes = candidates(intro, entryFloor, entryCeil, SIDE_INTRO);
        if (cutTimes.length == 0 || entryTimes.length == 0) {
            return null;
        }

        int lyricLegalPoints = -1;
        int lyricScanPoints = entryTimes.length;
        boolean lyricAnchorRelaxed = false;

        double lyricPhraseGap = phraseGapOf(outroSpans, outro.durationSecs);
        if (bridge.lyricGate && introSpans != null && introSpans.starts.length > 0) {

            double[] kept = pickAligned(intro, introSpans, entryTimes, windows,
                    lyricPhraseGap, true);
            if (kept == null) {
                kept = pickAligned(intro, introSpans, entryTimes, windows,
                        lyricPhraseGap, false);
            }
            if (kept != null) {
                entryTimes = kept;
                lyricLegalPoints = kept.length;
            } else {

                lyricScanPoints = entryTimes.length;
            }
        }

        int outroLegalPoints = -1;
        if (bridge.lyricGate && outroSpans != null
                && outroSpans.starts.length > 0) {
            int keep = 0;
            for (int i = 0; i < cutTimes.length; i++) {
                if (cutEndsPhrase(outroSpans, cutTimes[i], windows)) {
                    keep++;
                }
            }
            if (keep >= MIN_LYRIC_HEAD_CELLS) {
                double[] kept = new double[keep];
                int n = 0;
                for (int i = 0; i < cutTimes.length; i++) {
                    if (cutEndsPhrase(outroSpans, cutTimes[i], windows)) {
                        kept[n++] = cutTimes[i];
                    }
                }
                cutTimes = kept;
                outroLegalPoints = keep;
            }
        }

        AutoCue.VampSpan vamp = outroVampSpan(outro);
        int buyTimeCutIndex = -1;
        if (vamp != null && vamp.to > cutCeil && vamp.to <= lastLegalCut) {
            cutTimes = java.util.Arrays.copyOf(cutTimes, cutTimes.length + 1);
            cutTimes[cutTimes.length - 1] = vamp.to;
            buyTimeCutIndex = cutTimes.length - 1;
        }

        double[] cutIntensity = null;
        double[] entryIntensity = null;

        double metricAhead = 0.0;
        if (bridge.measure) {

            double ahead = bridge.sustain ? INTENSITY_SUSTAIN_SECS : 0.0;
            metricAhead = ahead;
            cutIntensity = new double[cutTimes.length];
            for (int c = 0; c < cutTimes.length; c++) {
                Decoder.checkActive();
                cutIntensity[c] = intensityAhead(outro, cutTimes[c], ahead);
            }
            entryIntensity = new double[entryTimes.length];
            for (int e = 0; e < entryTimes.length; e++) {
                entryIntensity[e] = intensityAhead(intro, entryTimes[e], ahead);
            }
        }

        double[] entryScore = new double[entryTimes.length];
        int[] entryIndex = new int[entryTimes.length];
        for (int e = 0; e < entryTimes.length; e++) {
            entryIndex[e] = intro.indexAt(entryTimes[e]);
            double rising = Math.max(0.0, slope(intro, entryIndex[e]));
            entryScore[e] = 0.8 * (1.0 - Math.min(1.0, rising * SLOPE_NORM))
                    + 0.5 * (1.0 - intro.onset[entryIndex[e]]);
        }

        double[] prefixOutro = prefixSums(outro.rms);
        double[] prefixIntro = prefixSums(intro.rms);

        double[] prefixLowOutro = outro.band == null ? null : prefixSums(lowBandColumn(outro));
        double[] prefixLowIntro = intro.band == null ? null : prefixSums(lowBandColumn(intro));
        float[] voiceOutro = voiceMask(outro);
        float[] voiceIntro = voiceMask(intro);
        double[] prefixVoiceOutro = voiceOutro == null ? null : prefixSums(voiceOutro);
        double[] prefixVoiceIntro = voiceIntro == null ? null : prefixSums(voiceIntro);
        int coarseHops = Math.max(1, (int) Math.round(widest / HOP_SECS));

        final boolean[] cutFeasible;
        final boolean[] entryFeasible;
        if (bridge.lyricGate) {
            final LyricSpans oSpans = outroSpans;
            final LyricSpans iSpans = introSpans;
            final double gap = lyricPhraseGap;
            cutFeasible = new boolean[cutTimes.length];
            for (int c = 0; c < cutTimes.length; c++) {
                Decoder.checkActive();
                cutFeasible[c] = oSpans == null || cutEndsPhrase(oSpans, cutTimes[c], windows);
            }
            entryFeasible = new boolean[entryTimes.length];
            for (int e = 0; e < entryTimes.length; e++) {
                entryFeasible[e] = iSpans == null
                        || headFollowsPhraseGap(iSpans, entryTimes[e], windows, gap,
                                ENTRY_HEAD_TOL_SECS, false);
            }
        } else {
            cutFeasible = null;
            entryFeasible = null;
        }
        final int[] entryIdx = entryIndex;
        final double[] meanB = new double[entryTimes.length];
        final double[] lowB = new double[entryTimes.length];
        final double[] voiceB = new double[entryTimes.length];
        for (int e = 0; e < entryTimes.length; e++) {
            meanB[e] = windowMean(prefixIntro, entryIdx[e], coarseHops, intro.rms.length);
            lowB[e] = prefixLowIntro == null ? 0.0
                    : windowMean(prefixLowIntro, entryIdx[e], coarseHops,
                            prefixLowIntro.length - 1);
            voiceB[e] = prefixVoiceIntro == null ? 0.0
                    : windowMean(prefixVoiceIntro, entryIdx[e], coarseHops,
                            prefixVoiceIntro.length - 1);
        }

        java.util.PriorityQueue<double[]> heap = new java.util.PriorityQueue<double[]>(
                Math.min(SHORTLIST, cutTimes.length * entryTimes.length),
                new java.util.Comparator<double[]>() {
                    @Override
                    public int compare(double[] a, double[] b) {
                        return Double.compare(b[0], a[0]);
                    }
                });

        double minEntryScore = Double.MAX_VALUE;
        for (int e = 0; e < entryScore.length; e++) {
            minEntryScore = Math.min(minEntryScore, entryScore[e]);
        }

        java.util.PriorityQueue<double[]> buyTimeHeap = new java.util.PriorityQueue<double[]>(
                Math.min(SHORTLIST, entryTimes.length), heap.comparator());

        final double[] cutAlign = new double[cutTimes.length];
        final double[] entryAlign = new double[entryTimes.length];
        {

            boolean barOutro = barPhase(outro) >= 0;
            boolean barIntro = barPhase(intro) >= 0;
            int barSides = (barOutro ? 1 : 0) + (barIntro ? 1 : 0);
            double barShare = barSides == 0 ? 0.0 : BAR_WEIGHT / barSides;
            boolean novOutro = outro.structureOutroStartSecs >= 0f;
            boolean novIntro = intro.structureIntroStartSecs >= 0f;
            int novSides = (novOutro ? 1 : 0) + (novIntro ? 1 : 0);
            double novShare = novSides == 0 ? 0.0 : NOVELTY_WEIGHT / novSides;
            for (int c = 0; c < cutTimes.length; c++) {
                Decoder.checkActive();
                cutAlign[c] = coarsePointBonus(outro, cutTimes[c], cutTimes[c], outroStructure,
                        STRUCTURE_OUTRO_SCALE,
                        novOutro ? outro.structureOutroStartSecs : -1.0, barShare, novShare);
            }

            for (int e = 0; e < entryTimes.length; e++) {
                entryAlign[e] = coarsePointBonus(intro, entryTimes[e], entryTimes[e], -1.0, 0.0,
                        novIntro ? intro.structureIntroStartSecs : -1.0, barShare, novShare);
            }
        }

        double coarseMaxAlign = 0.0;
        for (int i = 0; i < cutAlign.length; i++) {
            coarseMaxAlign = Math.max(coarseMaxAlign, cutAlign[i]);
        }
        for (int i = 0; i < entryAlign.length; i++) {
            coarseMaxAlign = Math.max(coarseMaxAlign, entryAlign[i]);
        }
        final boolean coarseGateOn = coarseMaxAlign >= COARSE_ALIGN_MIN;
        for (int c = 0; c < cutTimes.length; c++) {
                Decoder.checkActive();

            java.util.PriorityQueue<double[]> into = c == buyTimeCutIndex ? buyTimeHeap : heap;
            int ci = outro.indexAt(cutTimes[c]);
            double tailDrop = Math.max(0.0, outroDuration - (cutTimes[c] + widest));
            double falling = Math.max(0.0, -slope(outro, ci));
            double cutScore = 0.8 * (1.0 - Math.min(1.0, falling * SLOPE_NORM))
                    + 0.5 * (1.0 - outro.onset[ci])
                    + 0.35 * (1.0 - tailTerm(tailDrop, outroDuration));
            if (into.size() >= SHORTLIST
                    && cutScore + minEntryScore >= into.peek()[0]) {
                continue;
            }
            double meanA = windowMean(prefixOutro, ci, coarseHops, outro.rms.length);
            double lowA = prefixLowOutro == null ? 0.0
                    : windowMean(prefixLowOutro, ci, coarseHops, prefixLowOutro.length - 1);
            double voiceA = prefixVoiceOutro == null ? 0.0
                    : windowMean(prefixVoiceOutro, ci, coarseHops, prefixVoiceOutro.length - 1);
            for (int e = 0; e < entryTimes.length; e++) {

                double continuity = Math.abs(meanA - meanB[e]);
                double lowClash = prefixLowIntro == null ? 0.0
                        : LOW_END_COARSE_WEIGHT * lowA * lowB[e];
                double voiceClash = prefixVoiceIntro == null ? 0.0
                        : VOICE_COARSE_WEIGHT * voiceA * voiceB[e];

                double coarse = continuity + cutScore + entryScore[e] + lowClash + voiceClash;
                double align = cutAlign[c] + entryAlign[e];
                coarse -= COARSE_FINE_SCALE * align;
                if (coarseGateOn && align < COARSE_ALIGN_SHARE * coarseMaxAlign) {
                    coarse += COARSE_GATE_PENALTY;
                }

                if (bridge.score) {

                    coarse += COARSE_INTENSITY_WEIGHT
                            * (1.0 - intensityTerm(Math.abs(cutIntensity[c] - entryIntensity[e])));
                }
                if (bridge.veto
                        && Math.abs(cutIntensity[c] - entryIntensity[e]) > bridge.vetoGap) {
                    coarse += COARSE_GATE_PENALTY;
                }
                if (cutFeasible != null
                        && (!cutFeasible[c] || !entryFeasible[e])) {
                    coarse += COARSE_GATE_PENALTY;
                }
                if (into.size() < SHORTLIST) {
                    into.add(new double[]{coarse, c, e});
                } else if (coarse < into.peek()[0]) {
                    into.poll();
                    into.add(new double[]{coarse, c, e});
                }
            }
        }

        Score best = null;

        Score bestClean = null;

        Score bestNearEnd = null;

        Score bestGated = null;

        Score bestBridgeGated = null;

        Score bestRelaxVocal = null;

        Score bestLyricTop = null;
        Score bestLyricNoVocal = null;
        Score bestLyricNoIntensity = null;

        Score bestLyricLoose = null;

        Score second = null;

        java.util.List<Score> ranked = new java.util.ArrayList<Score>();
        int evaluated = 0;

        int pairs = 0;

        double[] bestPerWindow = new double[windows.length];
        java.util.Arrays.fill(bestPerWindow, Double.NEGATIVE_INFINITY);
        int[] lyricBlocked = new int[4];

        while (!heap.isEmpty() || !buyTimeHeap.isEmpty()) {
            double[] item = heap.isEmpty() ? buyTimeHeap.poll() : heap.poll();
            int c = (int) item[1];
            int e = (int) item[2];
            pairs++;
            for (int w = 0; w < windows.length; w++) {
                Decoder.checkActive();

                if (cutTimes[c] + windows[w] > lastLegalCut
                        && !buysTimePair(outro, intro, vamp, cutTimes[c], windows[w])) {
                    continue;
                }
                Score score = evaluate(outro, intro, cutTimes[c], entryTimes[e], windows[w])
                        .withLineGap(lineGapTerm(outroSpans, introSpans,
                                cutTimes[c], entryTimes[e], windows[w]));
                score = score
                        .withStructure(structureTerm(cutTimes[c], outroStructure,

                                entryTimes[e] + windows[w] * 1.5,
                                introStructure,

                                STRUCTURE_OUTRO_SCALE,

                                bridge.widen ? STRUCTURE_INTRO_SCALE : 1.0))
                        .withBar(appleBarWindowAdd(barBonus(outro, intro, cutTimes[c], entryTimes[e]),
                                        windows[w]))
                        .withNovelty(noveltyBonus(outro, intro, cutTimes[c], entryTimes[e]))

                        .withMelody(melodyOrNull(melodyMatch(outro, cutTimes[c], intro,
                                entryTimes[e], windows[w])), null);
                evaluated++;

                if (TRACE_CUE) {
                    System.out.println(String.format(java.util.Locale.ROOT,
                            "cut=%.4f entry=%.4f win=%.4f total=%.5f"
                                    + " overlap=%.3f ripple=%.3f bands=%.3f level=%.3f motion=%.3f"
                                    + " beat=%.3f tail=%.3f vocal=%.3f lowEnd=%.3f struct=%s melody=%s",
                            Double.valueOf(score.cutSecs), Double.valueOf(score.entrySecs),
                            Double.valueOf(score.windowSecs), Double.valueOf(score.total),
                            Double.valueOf(score.overlap), Double.valueOf(score.ripple),
                            Double.valueOf(score.bands), Double.valueOf(score.level),
                            Double.valueOf(score.motion), Double.valueOf(score.beat),
                            Double.valueOf(score.tail), Double.valueOf(score.vocal),
                            Double.valueOf(score.lowEnd),
                            score.structure == null ? "-" : String.format(
                                    java.util.Locale.ROOT, "%.3f", score.structure),
                            score.melodyToScore == null ? "-" : String.format(
                                    java.util.Locale.ROOT, "%.3f", score.melodyToScore)));
                }
                if (score.total > bestPerWindow[w]) {
                    bestPerWindow[w] = score.total;
                }

                double fromEnd = outro.durationSecs - score.cutSecs;
                double allowedFromEnd = Math.max(
                        MAX_CUT_FROM_END_FRACTION * outro.durationSecs, score.windowSecs);
                boolean nearEnd = outro.durationSecs <= 0.0 || fromEnd <= allowedFromEnd;
                boolean vocallyClean = score.vocalSecs <= VOCAL_GATE_SECS;

                boolean lyricOk = true;
                if (bridge.lyricGate && !headFollowsPhraseGap(introSpans, entryTimes[e],
                        new double[]{windows[w]}, lyricPhraseGap, ENTRY_HEAD_TOL_SECS, false)) {

                    lyricOk = false;
                    lyricBlocked[0]++;
                }

                if (lyricOk && bridge.lyricGate
                        && !risesAtSettleHead(intro, introSpans, entryTimes[e], windows[w])) {
                    lyricOk = false;
                    lyricBlocked[3]++;
                }
                if (lyricOk && bridge.lyricGate
                        && !endsPhraseNear(outroSpans, cutTimes[c] + windows[w],
                                ENTRY_HEAD_TOL_SECS)) {
                    lyricOk = false;
                    lyricBlocked[1]++;
                }

                boolean gridOk = true;
                if (bridge.lyricGate) {
                    double gridErr = phraseGridError(outroSpans, cutTimes[c], windows[w],
                            introSpans, entryTimes[e]);
                    if (!Double.isNaN(gridErr)) {
                        double gA = medianGapBefore(outroSpans.starts, cutTimes[c]);
                        if (!(gridErr <= Math.min(1.5, gA * 0.5))) {

                            gridOk = false;
                            lyricBlocked[1]++;
                        }
                    }
                }
                boolean intensityOk = true;
                double gap = INTENSITY_UNKNOWN_BAND;
                if (bridge.measure) {
                    gap = Math.abs(cutIntensity[c] - entryIntensity[e]);
                    intensityOk = gap <= bridge.vetoGap;
                    score = score.withIntensity(Double.valueOf(gap), bridge.score);
                }
                if (vocallyClean && (bestClean == null || better(score, bestClean))) {
                    bestClean = score;
                }
                if (nearEnd && (bestNearEnd == null || better(score, bestNearEnd))) {
                    bestNearEnd = score;
                }

                if (nearEnd && vocallyClean && (bestGated == null || better(score, bestGated))) {
                    bestGated = score;
                }

                if (bridge.veto && nearEnd && vocallyClean && intensityOk
                        && (bestBridgeGated == null || better(score, bestBridgeGated))) {
                    bestBridgeGated = score;
                }
                if (bridge.lyricGate && nearEnd && vocallyClean && intensityOk
                        && lyricOk && (bestLyricTop == null || better(score, bestLyricTop))) {
                    bestLyricTop = score;
                }
                if (bridge.lyricGate && nearEnd && intensityOk && lyricOk
                        && (bestLyricNoVocal == null || better(score, bestLyricNoVocal))) {
                    bestLyricNoVocal = score;
                }

                if (bridge.lyricGate && nearEnd && intensityOk && gridOk
                        && (bestLyricLoose == null || better(score, bestLyricLoose))) {
                    bestLyricLoose = score;
                }
                if (bridge.lyricGate && nearEnd && vocallyClean && lyricOk
                        && (bestLyricNoIntensity == null
                                || better(score, bestLyricNoIntensity))) {
                    bestLyricNoIntensity = score;
                }

                if (bridge.veto && nearEnd && intensityOk
                        && (bestRelaxVocal == null || better(score, bestRelaxVocal))) {
                    bestRelaxVocal = score;
                }
                if (best == null || better(score, best)) {
                    second = best;
                    best = score;
                } else if (second == null || better(score, second)) {
                    second = score;
                }
                keepRanked(ranked, score);
            }
        }
        if (best == null) {
            return null;
        }

        String gateNote = "";

        if (bridge.lyricGate && bestLyricTop != null && bestLyricTop != best) {
            gateNote = lyricGateNote(best, bestLyricTop, "all four constraints satisfied at once");
            second = best;
            best = bestLyricTop;
        } else if (bridge.lyricGate && bestLyricTop == null
                && bestLyricNoVocal != null && bestLyricNoVocal != best) {
            gateNote = lyricGateNote(best, bestLyricNoVocal, "relaxed vocal overlap");
            second = best;
            best = bestLyricNoVocal;
        } else if (bridge.lyricGate && bestLyricTop == null
                && bestLyricNoVocal == null && bestLyricNoIntensity != null
                && bestLyricNoIntensity != best) {
            gateNote = lyricGateNote(best, bestLyricNoIntensity, "relaxed intensity");
            second = best;
            best = bestLyricNoIntensity;
        } else if (bridge.lyricGate && bestLyricTop == null
                && bestLyricNoVocal == null && bestLyricNoIntensity == null
                && bestLyricLoose != null && bestLyricLoose != best) {
            gateNote = lyricGateNote(best, bestLyricLoose,
                    "line-head gain unreachable (the breath is too narrow to shorten the window), kept the phrase grid");
            second = best;
            best = bestLyricLoose;
        } else if (bridge.veto && bestBridgeGated != null && bestBridgeGated != best) {
            gateNote = intensityGateNote(best, bestBridgeGated);
            second = best;
            best = bestBridgeGated;
        } else if (bridge.veto && bestBridgeGated == null && bestRelaxVocal != null
                && bestRelaxVocal != best) {
            gateNote = String.format(java.util.Locale.US,
                    "; intensity gate: relaxed vocal overlap (lighter than intensity), taking gap %.3f %s -> %s",
                    Double.valueOf(gapOf(bestRelaxVocal)), fmt(bestRelaxVocal.cutSecs),
                    fmt(bestRelaxVocal.entrySecs));
            second = best;
            best = bestRelaxVocal;
        } else if (bridge.veto && bestBridgeGated == null && bestRelaxVocal == null
                && bestGated != null && bestGated != best) {
            gateNote = String.format(java.util.Locale.US,
                    "; intensity gate: no landing in the scanned range has intensity gap <= %.2f, so intensity itself is relaxed"
                            + " (gap %.3f %s -> %s)",
                    Double.valueOf(bridge.vetoGap), Double.valueOf(gapOf(bestGated)),
                    fmt(bestGated.cutSecs), fmt(bestGated.entrySecs));
            second = best;
            best = bestGated;
        } else if (bestGated != null && bestGated != best) {

            gateNote = gateNoteFor(best, secondCuts(bestGated),
                    outro.durationSecs - best.cutSecs, outro.durationSecs);
            second = best;
            best = bestGated;
        } else if (bestGated == null && bestClean != null && bestClean != best) {
            gateNote = String.format(java.util.Locale.US,
                    "; hard constraint: no candidate exits within the last %.0f%%"
                            + " (most likely the outgoing track is simply short), so pick by vocal overlap: %.1fs overlap %s (%.1fs from the end)",
                    Double.valueOf(100.0 * MAX_CUT_FROM_END_FRACTION),
                    Double.valueOf(bestClean.vocalSecs), fmt(bestClean.cutSecs),
                    Double.valueOf(outro.durationSecs - bestClean.cutSecs));
            second = best;
            best = bestClean;
        } else if (bestGated == null && bestNearEnd != null && bestNearEnd != best) {
            gateNote = String.format(java.util.Locale.US,
                    "; hard constraint: no candidate satisfies both 'exit within the last %.0f%%' and 'vocal overlap <= %.1fs', "
                            + "so vocal overlap is relaxed first (it is far lighter): taking the %s near the outro (%.1fs from the end)",
                    Double.valueOf(100.0 * MAX_CUT_FROM_END_FRACTION),
                    Double.valueOf(VOCAL_GATE_SECS),
                    fmt(bestNearEnd.cutSecs),
                    Double.valueOf(outro.durationSecs - bestNearEnd.cutSecs));
            second = best;
            best = bestNearEnd;
        } else if (bestGated == null && bestNearEnd == null && bestClean == null) {
            gateNote = String.format(java.util.Locale.US,
                    "; hard constraint: no candidate satisfies 'exit within the last %.0f%%' or 'vocal overlap <= %.1fs'"
                            + " (the best one is %.1fs from the end with %.1fs overlap), so it has to be taken",
                    Double.valueOf(100.0 * MAX_CUT_FROM_END_FRACTION),
                    Double.valueOf(VOCAL_GATE_SECS),
                    Double.valueOf(outro.durationSecs - best.cutSecs),
                    Double.valueOf(best.vocalSecs));
        }

        if (gateNote.isEmpty()) {
            double fromEnd = outro.durationSecs - best.cutSecs;
            double allowed = Math.max(MAX_CUT_FROM_END_FRACTION * outro.durationSecs,
                    best.windowSecs);
            boolean tailOk = outro.durationSecs <= 0.0 || fromEnd <= allowed;
            boolean vocalOk = best.vocalSecs <= VOCAL_GATE_SECS;
            if (!tailOk || !vocalOk) {
                StringBuilder why = new StringBuilder();
                if (!tailOk) {
                    why.append(String.format(java.util.Locale.US,
                            "exit at %ss (%.1fs from the end, beyond the allowed %.1fs)",
                            fmt(best.cutSecs), Double.valueOf(fromEnd), Double.valueOf(allowed)));
                }
                if (!vocalOk) {
                    if (why.length() > 0) {
                        why.append("、");
                    }
                    why.append(String.format(java.util.Locale.US, "vocal overlap %.1fs (beyond %.1fs)",
                            Double.valueOf(best.vocalSecs), Double.valueOf(VOCAL_GATE_SECS)));
                }
                gateNote = String.format(java.util.Locale.US,
                        "; hard constraint: no candidate satisfies both, so this %1$s is the only option",
                        why.toString());
            }
        }
        if (windows.length > 1) {
            StringBuilder candidates = new StringBuilder();
            for (int w = 0; w < windows.length; w++) {
                Decoder.checkActive();
                if (w > 0) {
                    candidates.append("  ");
                }
                candidates.append(fmt(windows[w])).append("s→")
                        .append(String.format(java.util.Locale.US, "%.3f", bestPerWindow[w]));
            }
            Logger.debug("overlap window candidates (best per window): {} -> taking {}s",
                    candidates, fmt(best.windowSecs));
        }

        boolean beyondFit = best.cutSecs + best.windowSecs > lastLegalCut;
        double outroCut = beyondFit ? best.cutSecs
                : snapToBeat(outro, best.cutSecs, cutFloor,
                        Math.max(cutFloor, lastLegalCut - best.windowSecs));
        double introEntry = snapToBeat(intro, best.entrySecs, 0.0, entryCeil);
        introEntry = phaseAlignedEntry(outro, outroCut, intro, introEntry, best.windowSecs);

        double anchorWindowSecs = -1.0;
        if (bridge.lyricGate && LYRIC_ANCHOR_IN_PLAN) {

            double[] anchor = lyricAnchor(outro, outroCut, intro, introEntry, best.windowSecs,
                    outroSpans, introSpans, cutFloor,
                    Math.max(cutFloor, lastLegalCut - best.windowSecs));
            if (anchor != null) {
                outroCut = anchor[0];
                introEntry = anchor[1];

                anchorWindowSecs = anchor[2];
            }
        }

        if (bridge.lyricGate) {
            double settleWin = best.windowSecs;
            if (alignsWithHead(introSpans, best.entrySecs, new double[]{settleWin},
                    ENTRY_HEAD_TOL_SECS)
                    && !alignsWithHead(introSpans, introEntry, new double[]{settleWin},
                            ENTRY_HEAD_TOL_SECS)) {
                introEntry = best.entrySecs;
            }
            if (endsPhraseNear(outroSpans, best.cutSecs + settleWin, ENTRY_HEAD_TOL_SECS)
                    && !endsPhraseNear(outroSpans, outroCut + settleWin,
                            ENTRY_HEAD_TOL_SECS)) {
                outroCut = best.cutSecs;
            }
        }
        if (outroCut != best.cutSecs || introEntry != best.entrySecs || anchorWindowSecs > 0.0) {

        double win = anchorWindowSecs > 0.0 ? anchorWindowSecs : best.windowSecs;

            best = evaluate(outro, intro, outroCut, introEntry, win)
                    .withLineGap(lineGapTerm(outroSpans, introSpans, outroCut, introEntry,
                            win))
                    .withStructure(structureTerm(outroCut, outroStructure,
                            introEntry + win * 0.5,
                            introStructure + INTRO_ANCHOR_LEAD_SECS,
                            STRUCTURE_OUTRO_SCALE,
                            bridge.widen ? STRUCTURE_INTRO_SCALE : 1.0))

                    .withIntensity(bridge.measure
                            ? Double.valueOf(Math.abs(
                                    intensityAhead(outro, outroCut, metricAhead)
                                            - intensityAhead(intro, introEntry, metricAhead)))
                            : null, bridge.score)

                    .withMelody(melodyOrNull(melodyMatch(outro, outroCut, intro,
                            introEntry, win)), null);
        }

        KeyRelation key = null;
        if (vamp != null && keyRelation != null) {
            try {
                key = keyRelation.get();
            } catch (RuntimeException e) {
                key = null;
            }
        }

        AutoCue.OutroLoopPlan decided = vamp == null ? null
                : planOutroLoop(outro, intro, vamp, outroCut, best.windowSecs, key);

        AutoCue.OutroLoopPlan outroLoop = null;
        java.util.List<Segment> segments = null;

        AutoCue.VampSpan bed = vamp == null ? null : introBedSpan(intro);

        double beatA = outro.tempo != null && outro.tempo.bpm > 0f ? 60.0 / outro.tempo.bpm : 0.5;
        boolean pickSameAsHead = bed != null
                && Math.abs(introEntry - bed.from) <= beatA * 0.5 + 1e-6;
        double bedRotate = bed != null && !pickSameAsHead
                && introEntry > bed.from + Segment.ROTATE_EPS_SECS
                && introEntry < bed.to - Segment.ROTATE_EPS_SECS ? introEntry : Double.NaN;
        java.util.List<Segment> bedPath = null;
        double bedPlainTotal = Double.NaN;
        double bedAltTotal = Double.NaN;
        if (loopIsWired(decided) && bed != null && bed.from >= 0.0 && bed.from <= entryCeil) {
            double introScale = bridge.widen ? STRUCTURE_INTRO_SCALE : 1.0;
            double structureTarget = introStructure + INTRO_ANCHOR_LEAD_SECS;
            bedPlainTotal = seamScore(outro, intro, outroCut, introEntry, best.windowSecs,
                    outroStructure, structureTarget, introScale, bridge, metricAhead).total;
            bedAltTotal = seamScore(outro, intro, outroCut, bed.from, best.windowSecs,
                    outroStructure, structureTarget, introScale, bridge, metricAhead).total;

            boolean sameLanding = Math.abs(introEntry - bed.from) <= beatA * 0.5 + 1e-6;

            boolean rotateWins = !Double.isNaN(bedRotate) && bedPlainTotal + 1e-9 >= bedAltTotal;
            boolean headWins = sameLanding || bedAltTotal + 1e-9 >= bedPlainTotal;
            if (rotateWins || headWins) {
                java.util.List<Segment> path = introBedPath(outro, intro, vamp, decided, bed,
                        best.windowSecs, rotateWins ? bedRotate : Double.NaN);
                if (path != null) {
                    bedPath = path;
                    if (!sameLanding && !rotateWins) {

                        introEntry = bed.from;
                        best = evaluate(outro, intro, outroCut, introEntry, best.windowSecs)
                                .withLineGap(lineGapTerm(outroSpans, introSpans, outroCut,
                                        introEntry, best.windowSecs))
                                .withStructure(structureTerm(outroCut, outroStructure,
                                        introEntry + best.windowSecs * 0.5, structureTarget,
                                        STRUCTURE_OUTRO_SCALE, introScale))
                                .withIntensity(bridge.measure
                                        ? Double.valueOf(Math.abs(
                                                intensityAhead(outro, outroCut, metricAhead)
                                                        - intensityAhead(intro, introEntry,
                                                                metricAhead)))
                                        : null, bridge.score)
                                .withMelody(melodyOrNull(melodyMatch(outro, outroCut,
                                        intro, introEntry, best.windowSecs)), null);
                    }
                }
            } else {
                Logger.debug("three-leg path: B's landing segment start scores lower under the same geometry"
                                + " (landing {} vs search-picked {}) -> not used this time",
                        f2(bedAltTotal), f2(bedPlainTotal));
            }
        }
        int cutIndex = outro.indexAt(outroCut);
        int entryIndexBest = intro.indexAt(introEntry);
        double tailDrop = Math.max(0.0, outroDuration - (outroCut + best.windowSecs));
        String note = "auto cue: exit " + fmt(outroCut) + "s (tail drop " + fmt(tailDrop) + "s), entry "
                + fmt(introEntry) + "s, overlap " + fmt(best.windowSecs) + "s"

                + (best.melodyToScore == null ? "(melody overlap not measured, "
                        : "(melody overlap " + f2(best.melodyToScore.doubleValue()) + "、")
                + (best.emotionToScore == null ? ""
                        : "emotion " + f2(best.emotionToScore.doubleValue()) + "、")
                + "wave overlap " + f2(best.overlap) + ", fade ripple " + f2(best.ripple)
                + ", band complement " + f2(best.bands) + ", low-end clash " + f2(best.lowEnd)
                + ", vocal overlap " + f2(best.vocal)
                + "(vocal overlap " + fmt(vocalOverlapSecs(outro, intro, outro.indexAt(outroCut),
                        intro.indexAt(introEntry),
                        (int) Math.round(best.windowSecs / HOP_SECS))) + "s）"
                + ", loudness delta " + f2(best.level)
                + "、" + describe(outro, cutIndex) + "/" + describe(intro, entryIndexBest)
                + "、" + best.beatNote
                + "; candidates " + windows.length + " windows x " + pairs
                + " point pairs, taking the highest total)"
                + "，" + alignmentNote(outro, intro)
                + String.format(java.util.Locale.US, "; structure: outro start %s / intro start %s",
                        outroStructure >= 0.0 ? fmt(outroStructure) + "s" : "none",
                        introStructure >= 0.0 ? fmt(introStructure) + "s" : "none");

        if (vamp != null) {
            note = note + String.format(java.util.Locale.US,
                    "; outro vamp %s->%ss (%.1fs, self-similarity %.2f, vocal %.0f%%) loopable",
                    fmt(vamp.from), fmt(vamp.to), Double.valueOf(vamp.length()),
                    Double.valueOf(vamp.selfSim), Double.valueOf(100.0 * vamp.vocalFrac));
            if (loopIsWired(decided)) {
                outroLoop = decided;
                note = note + String.format(java.util.Locale.US,
                        "; outro loop: %s->%ss repeated %d times (%s)",
                        fmt(decided.span.from), fmt(decided.span.to),
                        Integer.valueOf(decided.repeats), decided.reason);
                if (bedPath != null) {

                    segments = bedPath;
                    Segment head = bedPath.get(0);
                    Segment loopSeg = bedPath.get(1);
                    Segment bedSeg = bedPath.get(2);
                    double barA = 4.0 * 60.0 / outro.tempo.bpm;
                    double barB = intro.tempo != null && intro.tempo.bpm > 0f
                            ? 4.0 * 60.0 / intro.tempo.bpm : barA;
                    String bedClause = bedSeg.rotated()
                            ? String.format(java.util.Locale.US,
                                    "B's landing segment %.1f bars x%d (starts at the search-picked %.1f s, "
                                            + "wrapping to the segment head: one pass = [landing, segment end) + [segment head, landing)); "
                                            + "under the same geometry, landing start %.3f vs search-picked %.3f",
                                    Double.valueOf(bedSeg.spanSecs() / barB),
                                    Integer.valueOf(bedSeg.repeat),
                                    bedSeg.rotateStartSecs, Double.valueOf(bedAltTotal),
                                    Double.valueOf(bedPlainTotal))
                            : String.format(java.util.Locale.US,
                                    "B's landing segment %.1f bars x%d; under the same geometry, landing start "
                                            + "%.3f vs search-picked %.3f",
                                    Double.valueOf(bedSeg.spanSecs() / barB),
                                    Integer.valueOf(bedSeg.repeat), Double.valueOf(bedAltTotal),
                                    Double.valueOf(bedPlainTotal));
                    note = note + String.format(java.util.Locale.US,
                            "; path: %s (three legs: A's outro %.1f bars + loop x%d + %s)"
                                    + "-> B's verse continues from the end of the landing segment",
                            describePath(bedPath), Double.valueOf(head.spanSecs() / barA),
                            Integer.valueOf(loopSeg.repeat), bedClause);
                } else {

                    segments = outroLoopPath(outro, decided);
                    if (segments != null) {
                        note = note + "; path: " + describePath(segments);
                    }
                }
            } else if (decided != null) {

                Logger.debug("outro loop: the decision layer produced a plan ({}), but this lane is not wired yet"
                                + " (only the 'buy time' kind is wired, see AutoCue.loopIsWired) -> no loop this time",
                        decided.reason);
            }
        }

        if (bridge.lyricGate && lyricSpanSource != null) {

            int a = outroSpans == null ? -1 : outroSpans.starts.length;
            int b = introSpans == null ? -1 : introSpans.starts.length;
            boolean usable = a >= 2 && b >= 1;
            if (!usable) {
                note = note + String.format(java.util.Locale.US,
                        "; lyrics: cannot measure (exit %s / entry %s, real spans required)",
                        a < 0 ? "none" : a + " lines", b < 0 ? "none" : b + " lines");
            } else {

                int blockedTotal = lyricBlocked[0] + lyricBlocked[1] + lyricBlocked[2];
                if (lyricLegalPoints < 0) {

                } else if (lyricAnchorRelaxed) {
                    note = note + String.format(java.util.Locale.US,
                            "; lyrics: no alignable landing in the anchor range, **relaxing the anchor** to pick among %d cells"
                                    + " (the structural anchor yields to the lyric rule)",
                            Integer.valueOf(lyricLegalPoints));
                } else if (lyricLegalPoints >= MIN_LYRIC_GAP_CELLS) {
                    note = note + String.format(java.util.Locale.US,
                            "; vocal span: blend end aligned to a line head (%d usable landings of %d cells)",
                            Integer.valueOf(lyricLegalPoints), Integer.valueOf(lyricScanPoints));
                } else {
                    note = note + String.format(java.util.Locale.US,
                            "; lyrics: **this rule could not apply** - the whole incoming range is sung, "
                                    + "leaving only %d landings between lines (fewer than %d) -> no filtering, picking as if there were no lyrics",
                            Integer.valueOf(lyricLegalPoints),
                            Integer.valueOf(MIN_LYRIC_GAP_CELLS));
                }
                double gain = headGain(introSpans, best.entrySecs, best.windowSecs);
                if (!Double.isNaN(gain)) {
                    double head = Double.NaN;
                    for (int i = 0; i < introSpans.starts.length; i++) {
                        if (introSpans.starts[i] >= best.entrySecs) {
                            head = introSpans.starts[i];
                            break;
                        }
                    }
                    note = note + String.format(java.util.Locale.US,
                            "; when the first line head appears at %.2fs, B is already pushed to %.0f%%%s",
                            Double.valueOf(head), Double.valueOf(100.0 * gain),
                            gain < ENTRY_HEAD_GAIN_MIN
                                    ? " (**too low**: the line head is buried under A, so by the time it is audible the line is already underway)" : "");
                }
                if (derivedWindowSecs > 0.0) {
                    note = note + String.format(java.util.Locale.US,
                            "; overlap length derived from the phrase rule (B's phrase period - gA - margin), "
                                    + "adding %.1fs to the candidates; the user-set value is still the upper bound",
                            Double.valueOf(derivedWindowSecs));
                }
                if (!Double.isNaN(lyricPhraseGap) && lyricPhraseGap > 0.0) {
                    note = note + String.format(java.util.Locale.US,
                            "; after A finishes there is a %.1fs gap (A's own phrase interval) before B's next line",
                            Double.valueOf(lyricPhraseGap));
                }
                note = note + String.format(java.util.Locale.US,
                        "; lyric gate: blocked %d pairs (blend end missed B's line head %d, missed A's line end %d, phrase grid %d)",
                        Integer.valueOf(blockedTotal), Integer.valueOf(lyricBlocked[0]),
                        Integer.valueOf(lyricBlocked[1]), Integer.valueOf(lyricBlocked[2]));
            }
        }
        if (!gateNote.isEmpty()) {
            note = note + gateNote;
        }
        String runnerUp = runnerUpNote(best, second);
        if (!runnerUp.isEmpty()) {
            note = note + "；" + runnerUp;
        }
        Logger.debug("[qplayer-engine] {}", note);
        if (rankedSink != null) {

            rankedSink.clear();
            rankedSink.add(best);
            for (int i = 0; i < ranked.size(); i++) {
                Score sc = ranked.get(i);
                if (Math.abs(sc.cutSecs - best.cutSecs) < BEAT_APART_SECS
                        && Math.abs(sc.entrySecs - best.entrySecs) < BEAT_APART_SECS) {
                    continue;
                }
                rankedSink.add(sc);
            }
        }

        return new Cue(outroCut, introEntry, best.windowSecs, tailDrop, 1.0 - best.total, note,
                gapOf(best), !bridge.measure || gapOf(best) <= bridge.vetoGap, outroLoop,
                segments);
    }

    private static String gateNoteFor(Score vetoed, double chosenCut, double fromEnd,
                                      double outroDuration) {
        boolean cutTooEarly = outroDuration > 0.0
                && fromEnd > MAX_CUT_FROM_END_FRACTION * outroDuration;
        String why = cutTooEarly
                ? String.format(java.util.Locale.US,
                        "exit at %ss (%.1fs from the end = %.0f%% of the track, beyond %.0f%%)",
                        fmt(vetoed.cutSecs), Double.valueOf(fromEnd),
                        Double.valueOf(100.0 * fromEnd / Math.max(1.0, outroDuration)),
                        Double.valueOf(100.0 * MAX_CUT_FROM_END_FRACTION))
                : String.format(java.util.Locale.US, "vocal overlap %.1fs (beyond %.1fs)",
                        Double.valueOf(vetoed.vocalSecs), Double.valueOf(VOCAL_GATE_SECS));
        return String.format(java.util.Locale.US,
                "; hard constraint: rejected the higher-scoring %s and took the %ss that satisfies both",
                why, fmt(chosenCut));
    }

    static double intensityTerm(double gap) {
        if (Double.isNaN(gap)) {
            return 0.0;
        }
        if (gap <= INTENSITY_DEAD_ZONE) {
            return 1.0;
        }
        double span = Math.max(1e-9, INTENSITY_SCALE - INTENSITY_DEAD_ZONE);
        return Math.max(0.0, 1.0 - (gap - INTENSITY_DEAD_ZONE) / span);
    }

    private static String lyricGateNote(Score relaxed, Score gated, String how) {
        return String.format(java.util.Locale.US,
                "; lyric clearance: %s, swapping %s -> %s for %s -> %s (so the blend midpoint does not fall mid-line)",
                how, fmt(relaxed.cutSecs), fmt(relaxed.entrySecs),
                fmt(gated.cutSecs), fmt(gated.entrySecs));
    }

    private static double gapOf(Score score) {
        return score == null || score.intensityGap == null
                ? INTENSITY_UNKNOWN_BAND : score.intensityGap.doubleValue();
    }

    private static String intensityGateNote(Score relaxed, Score gated) {
        return String.format(java.util.Locale.US,
                "; intensity gate: swapping gap %.3f %s -> %s for gap %.3f %s -> %s",
                Double.valueOf(gapOf(relaxed)), fmt(relaxed.cutSecs), fmt(relaxed.entrySecs),
                Double.valueOf(gapOf(gated)), fmt(gated.cutSecs), fmt(gated.entrySecs));
    }

    private static double secondCuts(Score s) {
        return s.cutSecs;
    }

    public static PlanChoice comparePlans(Envelope outro, Envelope intro,
                                          double baselineSecs, double[] harmonicWindows,
                                          double plainScore) {
        return comparePlans(outro, intro, baselineSecs, harmonicWindows, plainScore, true);
    }

    public static PlanChoice comparePlans(Envelope outro, Envelope intro,
                                          double baselineSecs, double[] harmonicWindows,
                                          double plainScore, boolean shiftWorthwhile) {

        Cue baseline = plan(outro, intro, baselineSecs);
        Cue harmonicCue = (!shiftWorthwhile || harmonicWindows == null)
                ? null : plan(outro, intro, harmonicWindows);
        if (baseline == null && harmonicCue == null) {
            return null;
        }
        double baselineScore = baseline == null ? -1.0 : 1.0 - baseline.cost;
        double harmonicSeven = harmonicCue == null ? -1.0 : 1.0 - harmonicCue.cost;

        double harmonicScore = harmonicSeven;
        boolean harmonic = harmonicCue != null
                && (baseline == null || harmonicScore > baselineScore);

        String harmonicPart = harmonicCue == null
                ? (shiftWorthwhile ? "cannot be computed" : "not applicable (keys already match, no shift needed)")
                : String.format(java.util.Locale.US, "seven terms %.3f",
                        Double.valueOf(harmonicSeven));
        String why = String.format(java.util.Locale.US,
                "AI plan comparison: current kind %.3f (window %.1fs) vs harmonic blend %s (window %.1fs) -> chose %s",
                Double.valueOf(baselineScore),
                Double.valueOf(baseline == null ? baselineSecs : baseline.windowSecs),
                harmonicPart,
                Double.valueOf(harmonicCue == null ? 0.0 : harmonicCue.windowSecs),
                harmonic ? "harmonic blend" : "current kind");

        Logger.debug("[qplayer-engine] {}", why);
        return new PlanChoice(harmonic, baselineScore, harmonicScore, why,
                harmonicCue == null
                        ? (baseline == null ? baselineSecs : baseline.windowSecs)
                        : harmonicCue.windowSecs,
                harmonic ? harmonicCue.note : (baseline == null ? "" : baseline.note),
                harmonic ? harmonicCue : baseline);
    }

    public static final class PlanChoice {

        public final boolean harmonic;

        public final double baselineScore;

        public final double harmonicScore;

        public final String why;

        public final double windowSecs;

        public final String note;

        public final Cue cue;

        PlanChoice(boolean harmonic, double baselineScore, double harmonicScore,
                   String why, double windowSecs, String note, Cue cue) {
            this.harmonic = harmonic;
            this.baselineScore = baselineScore;
            this.harmonicScore = harmonicScore;
            this.why = why;
            this.windowSecs = windowSecs;
            this.note = note;
            this.cue = cue;
        }
    }

    private static double[] prefixSums(float[] values) {
        double[] out = new double[values.length + 1];
        for (int i = 0; i < values.length; i++) {
            out[i + 1] = out[i] + values[i];
        }
        return out;
    }

    private static double windowMean(double[] prefix, int start, int hops, int length) {
        int from = Math.max(0, Math.min(start, length - 1));
        int to = Math.min(length, from + Math.max(1, hops));
        return (prefix[to] - prefix[from]) / Math.max(1, to - from);
    }

    private static double[] sanitizeWindows(double[] candidates) {
        java.util.TreeSet<Double> set = new java.util.TreeSet<Double>();
        for (int i = 0; i < candidates.length; i++) {
            double w = candidates[i];
            if (w > 0.25 && !Double.isNaN(w) && !Double.isInfinite(w)) {
                set.add(Double.valueOf(Math.min(w, MAX_WINDOW_SECS)));
            }
        }
        double[] out = new double[set.size()];
        int i = 0;
        for (Double d : set) {
            out[i++] = d.doubleValue();
        }
        return out;
    }

    static float[] noveltyCurve(float[] chroma, int frames, double hopSecs) {
        float[] out = new float[Math.max(0, frames)];
        if (chroma == null || frames <= 0 || hopSecs <= 0.0) {
            return out;
        }

        int[] widths = new int[NOVELTY_SCALES.length];
        for (int s2 = 0; s2 < NOVELTY_SCALES.length; s2++) {
            widths[s2] = Math.max(1, (int) Math.round(NOVELTY_SCALES[s2] / hopSecs));
        }

        for (int s2 = 0; s2 < widths.length; s2++) {
            int w = widths[s2];
            if (w <= 0 || 2 * w >= frames) {
                continue;
            }
            double[] left = new double[SpectralOnsets.PITCH_CLASSES];
            double[] right = new double[SpectralOnsets.PITCH_CLASSES];
            for (int k = 0; k < w; k++) {
                for (int pc = 0; pc < SpectralOnsets.PITCH_CLASSES; pc++) {
                    left[pc] += chroma[k * SpectralOnsets.PITCH_CLASSES + pc];
                    right[pc] += chroma[(k + w) * SpectralOnsets.PITCH_CLASSES + pc];
                }
            }
            for (int i = w; i < frames - w; i++) {
                double sum = 0.0;
                for (int pc = 0; pc < SpectralOnsets.PITCH_CLASSES; pc++) {
                    sum += Math.abs(left[pc] / w - right[pc] / w);
                }
                out[i] += (float) sum;

                for (int pc = 0; pc < SpectralOnsets.PITCH_CLASSES; pc++) {
                    left[pc] += chroma[i * SpectralOnsets.PITCH_CLASSES + pc]
                            - chroma[(i - w) * SpectralOnsets.PITCH_CLASSES + pc];
                    right[pc] += chroma[(i + w) * SpectralOnsets.PITCH_CLASSES + pc]
                            - chroma[i * SpectralOnsets.PITCH_CLASSES + pc];
                }
            }
        }
        return out;
    }

    static double noveltyBoundary(float[] novelty, double hopSecs, boolean last,
                                  double fromSecs, double toSecs) {
        return noveltyBoundary(novelty, null, hopSecs, last, fromSecs, toSecs);
    }

    static double noveltyBoundary(float[] novelty, float[] level, double hopSecs, boolean last,
                                  double fromSecs, double toSecs) {
        if (novelty == null || novelty.length < 4 || hopSecs <= 0.0) {
            return -1.0;
        }
        float[] sorted = novelty.clone();
        java.util.Arrays.sort(sorted);
        double p90 = sorted[Math.min(sorted.length - 1, (int) Math.floor(sorted.length * 0.9))];
        if (p90 <= 1e-9) {
            return -1.0;
        }
        double threshold = p90 * NOVELTY_PEAK_RATIO;
        int side = Math.max(1, (int) Math.round(NOVELTY_WINDOW_SECS / hopSecs));
        int first = Math.max(1, (int) Math.round(fromSecs / hopSecs));
        int lastIdx = Math.min(novelty.length - 2, (int) Math.round(toSecs / hopSecs));
        double found = -1.0;
        for (int i = last ? lastIdx : first; last ? i >= first : i <= lastIdx; i += last ? -1 : 1) {
            if (novelty[i] < threshold
                    || novelty[i] < novelty[i - 1] || novelty[i] < novelty[i + 1]) {
                continue;
            }
            if (level != null && i - side >= 0 && i + side < level.length) {
                double before = 0.0;
                double after = 0.0;
                for (int k = i - side; k < i; k++) {
                    before += level[k];
                }
                for (int k = i; k < i + side; k++) {
                    after += level[k];
                }

                if (after < before * NOVELTY_SIDE_LEVEL_RATIO) {
                    continue;
                }
            }
            found = i * hopSecs;
            break;
        }
        return found;
    }

    static double outroStartSecs(Envelope env) {

        if (env == null || env.rms.length < 8) {
            return -1.0;
        }
        float[] profile = smoothLevel(env);
        double reference = referenceLevel(profile);
        if (reference <= 1e-6) {
            return -1.0;
        }

        int w = Math.max(1, (int) Math.round(STRUCTURE_PEAK_SECS / HOP_SECS));
        int last = -1;
        for (int i = 0; i < profile.length; i++) {
            boolean peak = true;
            int lo = Math.max(0, i - w);
            int hi = Math.min(profile.length - 1, i + w);
            for (int j = lo; j <= hi; j++) {
                if (profile[j] > profile[i]) {
                    peak = false;
                    break;
                }
            }
            if (peak && profile[i] >= reference * OUTRO_KEEP_RATIO) {
                last = i;
            }
        }
        if (last >= 0) {
            double tailMax = 0.0;
            for (int i = last + 1; i < profile.length; i++) {
                tailMax = Math.max(tailMax, profile[i]);
            }
            double secs = last * HOP_SECS;

            if (tailMax <= profile[last] && env.durationSecs - secs >= STRUCTURE_MIN_RUN_SECS) {
                return toPhraseLine(env, secs);
            }
        }

        last = -1;
        for (int i = 0; i < profile.length; i++) {
            if (profile[i] >= reference * OUTRO_KEEP_RATIO) {
                last = i;
            }
        }
        if (last < 0) {
            return -1.0;
        }
        double tailMax = 0.0;
        for (int i = last + 1; i < profile.length; i++) {
            tailMax = Math.max(tailMax, profile[i]);
        }
        if (tailMax > reference * (OUTRO_KEEP_RATIO + 0.10)) {
            return -1.0;
        }
        double secs = last * HOP_SECS;
        if (env.durationSecs - secs < STRUCTURE_MIN_RUN_SECS) {
            return -1.0;
        }
        return toPhraseLine(env, secs);
    }

    private static double toPhraseLine(Envelope env, double secs) {
        if (!(secs >= 0.0) || env == null) {
            return secs;
        }
        double[] bars = eightBarLines(env);
        for (int i = 0; i < bars.length; i++) {
            double delta = bars[i] - secs;
            if (delta >= 0.0) {
                return delta <= STRUCTURE_TOLERANCE_SECS ? bars[i] : secs;
            }
        }
        return secs;
    }

    static double introStartSecs(Envelope env) {

        if (env == null || env.rms.length < 8) {
            return -1.0;
        }
        float[] profile = smoothLevel(env);

        double reference = introReferenceLevel(profile);
        if (reference <= 1e-6) {
            return -1.0;
        }
        for (int i = 0; i < profile.length; i++) {
            if (profile[i] >= reference * INTRO_KEEP_RATIO) {
                return i * HOP_SECS;
            }
        }
        return 0.0;

    }

    private static double introReferenceLevel(float[] profile) {
        int horizon = Math.min(profile.length,
                Math.max(8, (int) Math.round(INTRO_REFERENCE_SECS / HOP_SECS)));
        float[] head = new float[horizon];
        System.arraycopy(profile, 0, head, 0, horizon);
        return referenceLevel(head);
    }

    static double structureTerm(double cutSecs, double outroStart, double heardIntroSecs,
                                double introStart, double outroScale, double introScale) {

        double outro = 0.5 * proximity(cutSecs, outroStart);
        double intro = 0.5 * proximity(heardIntroSecs, introStart);
        return outroScale * outro + introScale * intro;
    }

    private static double proximity(double secs, double target) {
        if (!(target >= 0.0)) {
            return 0.5;
        }
        return Math.max(0.0, 1.0 - Math.abs(secs - target) / STRUCTURE_TOLERANCE_SECS);
    }

    private static double structureTolerance() {
        return STRUCTURE_TOLERANCE_SECS;
    }

    private static float[] smoothLevel(Envelope env) {
        int n = env.rms.length;
        int w = Math.max(1, (int) Math.round(STRUCTURE_SMOOTH_SECS / HOP_SECS));
        float[] out = new float[n];
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += env.rms[i];
            if (i >= w) {
                sum -= env.rms[i - w];
            }
            out[i] = (float) (sum / Math.min(i + 1, w));
        }
        return out;
    }

    private static double referenceLevel(float[] profile) {
        float[] copy = profile.clone();
        java.util.Arrays.sort(copy);
        return copy[Math.min(copy.length - 1, (int) Math.floor(copy.length * 0.85))];
    }

    public static final class OutroLoopPlan {

        public final VampSpan span;

        public final int bars;

        public final int repeats;

        public final boolean buysTime;

        public final String reason;

        public final boolean styleWanted;

        public final boolean confirmRepeat;

        OutroLoopPlan(VampSpan span, int bars, int repeats, boolean buysTime, String reason) {
            this(span, bars, repeats, buysTime, reason, false);
        }

        OutroLoopPlan(VampSpan span, int bars, int repeats, boolean buysTime, String reason,
                      boolean styleWanted) {
            this(span, bars, repeats, buysTime, reason, styleWanted, false);
        }

        OutroLoopPlan(VampSpan span, int bars, int repeats, boolean buysTime, String reason,
                      boolean styleWanted, boolean confirmRepeat) {
            this.span = span;
            this.bars = bars;
            this.repeats = repeats;
            this.buysTime = buysTime;
            this.reason = reason;
            this.styleWanted = styleWanted;
            this.confirmRepeat = confirmRepeat;
        }

        public OutroLoopPlan withStyleWanted() {
            return new OutroLoopPlan(span, bars, repeats, buysTime, reason, true, confirmRepeat);
        }
    }

    static final double LOOP_BPM_DIFF_MIN = 0.03;

    static final double LOOP_BPM_DIFF_MAX = 0.08;

    static final double LOOP_MIN_TEMPO_CONFIDENCE = 0.35;

    static final double LOOP_MAX_VOCAL_FRACTION = 0.0;

    static final double LOOP_SHORT_TAIL_BARS = 4.0;

    static final double LOOP_CONFIRM_WINDOW_BARS = 1.0;

    public static final double LOOP_CONFIRM_WINDOW_SECS = 2.0;

    static final int LOOP_CONFIRM_BARS = 2;

    public static final double LOOP_SHORT_INTRO_BARS = 4.0;

    static final double LOOP_INTRO_RISE_RATIO = 0.75;

    static final double LOOP_INTRO_FLOOR_RATIO = 0.15;

    static final float LOOP_KEY_SAME_SCORE = 0.85f;

    public static OutroLoopPlan planOutroLoop(Envelope outro, Envelope intro, VampSpan vamp,
                                              double cutSecs, double windowSecs,
                                              boolean keyClash) {

        return planOutroLoopCore(outro, intro, vamp, cutSecs, windowSecs, keyClash,
                keyClash ? KEY_NOTE_CLASH : "");
    }

    static OutroLoopPlan planOutroLoop(Envelope outro, Envelope intro, VampSpan vamp,
                                       double cutSecs, double windowSecs, KeyRelation key) {
        boolean keyClash = keyClashWithCompatibleGrid(key, outro, intro);
        return planOutroLoopCore(outro, intro, vamp, cutSecs, windowSecs, keyClash,
                keyClash ? keyNoteOf(key) : "");
    }

    private static final String KEY_NOTE_CLASH = ", key clash";

    private static String keyNoteOf(KeyRelation key) {
        return String.format(java.util.Locale.US,
                ", key clash but rhythm compatible (chroma similarity %.2f < %.2f)",
                Float.valueOf(key.plainScore), Float.valueOf(LOOP_KEY_SAME_SCORE));
    }

    private static OutroLoopPlan planOutroLoopCore(Envelope outro, Envelope intro, VampSpan vamp,
                                                   double cutSecs, double windowSecs,
                                                   boolean keyClash, String keyNote) {
        if (outro == null || vamp == null || outro.tempo == null || !(outro.tempo.bpm > 0f)
                || outro.tempo.confidence < LOOP_MIN_TEMPO_CONFIDENCE) {
            return null;
        }

        if (vamp.vocalFrac > LOOP_MAX_VOCAL_FRACTION) {
            return null;
        }
        double bpmA = outro.tempo.bpm;
        double bpmB = intro != null && intro.tempo != null && intro.tempo.bpm > 0f
                ? intro.tempo.bpm : bpmA;
        double diff = Math.abs(bpmB - bpmA) / bpmA;
        if (diff > LOOP_BPM_DIFF_MAX) {
            return null;
        }
        double barSecs = 4.0 * 60.0 / bpmA;
        double tailRemaining = outro.durationSecs - cutSecs;

        boolean shortTail = tailRemaining < LOOP_SHORT_TAIL_BARS * barSecs;
        boolean windowTooLong = tailRemaining < windowSecs
                || cutSecs + windowSecs > outro.durationSecs;
        double introLead = outroLoopIntroLeadSecs(intro);
        double barSecsB = intro != null && intro.tempo != null && intro.tempo.bpm > 0f
                ? 4.0 * 60.0 / intro.tempo.bpm : barSecs;
        boolean shortIntro = introLead > 0.0 && introLead < LOOP_SHORT_INTRO_BARS * barSecsB;
        boolean needsTime = windowTooLong || shortTail || shortIntro;
        boolean needsGrid = (diff >= LOOP_BPM_DIFF_MIN) || keyClash;

        boolean wantsConfirm = windowSecs > 0.0 && windowSecs < LOOP_CONFIRM_WINDOW_SECS;
        if (!needsTime && !needsGrid && !wantsConfirm) {

            return null;
        }
        int maxBars = (int) Math.floor(vamp.length() / barSecs + 1e-6);
        int bars = 0;

        if (wantsConfirm && !needsTime && !needsGrid) {
            bars = Math.min(maxBars, LOOP_CONFIRM_BARS);
            if (bars <= 0) {
                return null;
            }
        }

        if (bars == 0) {
            for (int candidate : new int[]{16, 8, 4}) {
                if (candidate <= maxBars) {
                    bars = candidate;
                    break;
                }
            }
        }
        if (bars == 0) {
            return null;
        }

        int repeats = needsTime && tailRemaining < bars * barSecs * 0.5 ? 2 : 1;

        String timeWhy = windowTooLong
                ? String.format(java.util.Locale.US,
                        "only %.1f s of outro left (a %.1f s window does not fit)",
                        Double.valueOf(tailRemaining), Double.valueOf(windowSecs))
                : shortTail
                        ? String.format(java.util.Locale.US,
                                "only %.1f s = %.1f bars of outro left (fewer than %.1f bars, a plain crossfade would feel rushed)",
                                Double.valueOf(tailRemaining),
                                Double.valueOf(tailRemaining / barSecs),
                                Double.valueOf(LOOP_SHORT_TAIL_BARS))
                        : String.format(java.util.Locale.US,
                                "B's intro is only %.1f s = %.1f bars (fewer than %.1f bars, "
                                        + "not enough time to ease B in)",
                                Double.valueOf(introLead),
                                Double.valueOf(introLead / barSecsB),
                                Double.valueOf(LOOP_SHORT_INTRO_BARS));

        final boolean confirm = wantsConfirm && !needsTime && !needsGrid;
        String reason;
        if (confirm) {
            reason = String.format(java.util.Locale.US,
                    "the window is only %.2f s (fewer than %.1f s and a direct transition sounds poor) -> confirming a %d-bar repeat",
                    Double.valueOf(windowSecs), Double.valueOf(LOOP_CONFIRM_WINDOW_SECS),
                    Integer.valueOf(bars));
        } else if (needsTime) {
            reason = timeWhy + String.format(java.util.Locale.US,
                    " -> looping %d bars to buy time", Integer.valueOf(bars));
        } else {
            reason = String.format(java.util.Locale.US,
                    "BPM gap %.1f%%%s -> looping %d bars to align the grid",
                    Double.valueOf(100.0 * diff), keyNote, Integer.valueOf(bars));
        }
        return new OutroLoopPlan(vamp, bars, repeats, needsTime, reason, false, confirm);
    }

    public static double outroLoopIntroLeadSecs(Envelope intro) {
        if (intro == null) {
            return -1.0;
        }
        double lead = introStartSecs(intro);
        if (!(lead > 0.0)) {
            return -1.0;
        }
        double covered = intro.rms.length * HOP_SECS;
        if (!(lead < covered)) {
            return -1.0;
        }
        float[] profile = smoothLevel(intro);
        double reference = introReferenceLevel(profile);
        if (!(reference > 1e-6)) {
            return -1.0;
        }
        int end = Math.min(profile.length, Math.max(1, (int) Math.round(lead / HOP_SECS)));
        double sum = 0.0;
        for (int i = 0; i < end; i++) {
            sum += profile[i];
        }
        double leadLevel = sum / (double) end;
        if (!(leadLevel < reference * LOOP_INTRO_RISE_RATIO)
                || !(leadLevel > reference * LOOP_INTRO_FLOOR_RATIO)) {
            return -1.0;
        }
        return lead;
    }

    public static boolean loopIsWired(OutroLoopPlan plan) {

        return plan != null && (plan.buysTime || plan.styleWanted || plan.confirmRepeat);
    }

    static final int LOOP_MAX_REPEATS = 2;

    static java.util.List<Segment> outroLoopPath(Envelope outro, OutroLoopPlan plan) {
        if (outro == null || plan == null || plan.span == null
                || outro.tempo == null || !(outro.tempo.bpm > 0f)) {
            return null;
        }
        double barSecs = 4.0 * 60.0 / outro.tempo.bpm;
        double phase = barPhaseOf(outro, barSecs);
        if (Double.isNaN(phase)) {
            return null;
        }
        int bars = Math.max(1, plan.bars);
        int repeats = Math.max(1, Math.min(plan.repeats, LOOP_MAX_REPEATS));
        double v0 = snapToBar(plan.span.from, phase, barSecs);
        double to = v0 + bars * barSecs;
        if (v0 < 0.0 || to > outro.durationSecs) {
            return null;
        }
        return java.util.Collections.singletonList(new Segment("outro", v0, to, repeats));
    }

    static java.util.List<Segment> introBedPath(Envelope outro, Envelope intro, VampSpan vamp,
                                                OutroLoopPlan plan, VampSpan bed,
                                                double windowSecs, double bedRotateSecs) {
        if (outro == null || intro == null || vamp == null || plan == null || bed == null
                || outro.tempo == null || !(outro.tempo.bpm > 0f)) {
            return null;
        }
        double barA = 4.0 * 60.0 / outro.tempo.bpm;
        double phaseA = barPhaseOf(outro, barA);
        if (Double.isNaN(phaseA)) {
            return null;
        }

        double barB = intro.tempo != null && intro.tempo.bpm > 0f
                ? 4.0 * 60.0 / intro.tempo.bpm : barA;
        double phaseB = barPhaseOf(intro, barB);
        double bedFrom = Double.isNaN(phaseB) ? bed.from : snapToBar(bed.from, phaseB, barB);
        double bedPass = bed.length();
        if (!(bedPass > 0.0) || bedFrom + bedPass > intro.durationSecs) {
            return null;
        }
        double v0 = snapToBar(vamp.from, phaseA, barA);
        int loopBars = Math.max(1, plan.bars);
        int loopRepeats = Math.max(1, Math.min(plan.repeats, LOOP_MAX_REPEATS));
        double loopTo = v0 + loopBars * barA;
        int tailBars = Math.max(1, (int) Math.round(windowSecs / barA));
        double tailFrom = v0 - tailBars * barA;
        if (tailFrom < 0.0 || loopTo > outro.durationSecs) {
            return null;
        }

        double aSideSecs = (tailBars + loopBars * (double) loopRepeats) * barA;
        double budget = INTRO_BED_PATH_MAX_BARS * barA;
        if (aSideSecs > budget) {

            Logger.debug("three-leg path: A side over the limit - A side {} bars (outro {} + loop "
                            + "{}x{}) = {}s > the {} bar limit ({}s); that limit only caps A's contribution, "
                            + "B's landing segment {} bars (B's own ruler) does not count towards it - B has its own {}s scan range"
                            + "and the two rules about {} bars of lead-in -> not chosen this time",
                    Integer.valueOf(tailBars + loopBars * loopRepeats), Integer.valueOf(tailBars),
                    Integer.valueOf(loopBars), Integer.valueOf(loopRepeats), fmt(aSideSecs),
                    fmt(INTRO_BED_PATH_MAX_BARS), fmt(budget), f2(bedPass / barB),
                    fmt(INTRO_BED_HEAD_SECS), fmt(INTRO_BED_LEAD_BARS));
            return null;
        }
        for (int n = LOOP_MAX_REPEATS; n >= 1; n--) {

            double total = aSideSecs + n * bedPass;
            if (total > outro.durationSecs) {
                continue;
            }
            java.util.List<Segment> path = new java.util.ArrayList<Segment>(3);
            path.add(new Segment("outro", tailFrom, v0, 1));
            path.add(new Segment("outro", v0, loopTo, loopRepeats));

            path.add(new Segment("intro", bedFrom, bedFrom + bedPass, n,
                    Double.isNaN(bedRotateSecs) ? null : Double.valueOf(bedRotateSecs)));
            return java.util.Collections.unmodifiableList(path);
        }

        Logger.debug("three-leg path: total window exceeds A's full length - A side {} bars ({}s) + "
                        + "B's landing segment {} bars (B's own ruler) x1 ({}s) = {}s > A's full length {}s"
                        + " (A's material must really exist; the render layer backs off by the same rule) -> not chosen this time",
                Integer.valueOf(tailBars + loopBars * loopRepeats), fmt(aSideSecs),
                f2(bedPass / barB), fmt(bedPass), fmt(aSideSecs + bedPass),
                fmt(outro.durationSecs));
        return null;
    }

    private static Score seamScore(Envelope outro, Envelope intro,
                                   double cutSecs, double entrySecs,
                                   double windowSecs, double outroStructure,
                                   double introStructure, double introScale, Bridge bridge,
                                   double metricAhead) {
        Score score = evaluate(outro, intro, cutSecs, entrySecs, windowSecs)
                .withStructure(structureTerm(cutSecs, outroStructure,
                        entrySecs + windowSecs * 0.5, introStructure,
                        STRUCTURE_OUTRO_SCALE, introScale))
                .withBar(barBonus(outro, intro, cutSecs, entrySecs))
                .withNovelty(noveltyBonus(outro, intro, cutSecs, entrySecs))
                .withMelody(melodyOrNull(melodyMatch(outro, cutSecs, intro, entrySecs,
                        windowSecs)), null);
        if (bridge.measure) {
            score = score.withIntensity(Double.valueOf(Math.abs(
                    intensityAhead(outro, cutSecs, metricAhead)
                            - intensityAhead(intro, entrySecs, metricAhead))), bridge.score);
        }
        return score;
    }

    static boolean buysTimePair(Envelope outro, Envelope intro, VampSpan vamp,
                                double cutSecs, double windowSecs) {
        return vamp != null
                && loopIsWired(planOutroLoop(outro, intro, vamp, cutSecs, windowSecs, false));
    }

    public static final class VampSpan {
        public final double from;
        public final double to;

        public final double selfSim;
        public final double levelCv;
        public final double vocalFrac;

        VampSpan(double from, double to, double selfSim, double levelCv, double vocalFrac) {
            this.from = from;
            this.to = to;
            this.selfSim = selfSim;
            this.levelCv = levelCv;
            this.vocalFrac = vocalFrac;
        }

        public double length() {
            return to - from;
        }
    }

    static final double VAMP_TAIL_SECS = 45.0;

    static final double VAMP_MIN_LEVEL_RATIO = 0.5;

    static final double VAMP_MAX_CV = 0.35;

    static final double VAMP_MAX_VOCAL_FRACTION = 0.2;

    static final double VAMP_MIN_SELF_SIM = 0.5;

    public static int downbeatIndex(Envelope env, double barSecs) {
        double[] sums = downbeatSums(env, barSecs);
        if (sums == null) {
            return -1;
        }
        double best = -1.0;
        int bestK = -1;
        for (int k = 0; k < sums.length; k++) {
            if (sums[k] > best) {
                best = sums[k];
                bestK = k;
            }
        }
        return bestK;
    }

    public static double[] downbeatSums(Envelope env, double barSecs) {
        if (env == null || !(barSecs > 0.0) || env.onset == null || env.onset.length == 0
                || !env.hasBeatGrid()) {
            return null;
        }
        double period = env.tempo.periodFrames / (double) env.beatRate;
        if (!(period > 0.0) || period > barSecs) {
            return null;
        }
        double[] first = env.beatsIn(0.0, period * 2.0);
        if (first.length == 0) {
            return null;
        }
        int count = Math.max(1, (int) Math.round(barSecs / period));
        double[] sums = new double[count];
        for (int k = 0; k < count; k++) {
            double phase = first[0] + k * period;
            for (double t = phase; t < env.durationSecs; t += barSecs) {
                int i = env.indexAt(t);
                for (int j = Math.max(0, i - 3); j < Math.min(env.onset.length, i + 4); j++) {
                    sums[k] += env.onset[j];
                }
            }
        }
        return sums;
    }

    static double melodyMatch(Envelope a, double cutSecs, Envelope b, double entrySecs,
                              double windowSecs) {
        if (a == null || b == null || a.chroma == null || b.chroma == null
                || !(a.chromaHopSecs > 0.0) || !(b.chromaHopSecs > 0.0) || !(windowSecs > 0.0)) {
            return Double.NaN;
        }
        int pc = SpectralOnsets.PITCH_CLASSES;
        int frames = (int) Math.floor(windowSecs / a.chromaHopSecs);
        if (frames < 2) {
            return Double.NaN;
        }
        int aStart = (int) Math.round(cutSecs / a.chromaHopSecs);
        int bStart = (int) Math.round(entrySecs / b.chromaHopSecs);
        double best = Double.NaN;
        for (int off = -MELODY_OFFSET_FRAMES; off <= MELODY_OFFSET_FRAMES; off++) {
            double sum = 0.0;
            double wsum = 0.0;
            int used = 0;
            for (int i = 0; i < frames; i++) {
                int ai = aStart + i;
                int bi = bStart + i + off;
                if (ai < 0 || ai >= a.chromaFrames || bi < 0 || bi >= b.chromaFrames) {
                    continue;
                }
                double dot = 0.0;
                double na = 0.0;
                double nb = 0.0;
                for (int k = 0; k < pc; k++) {
                    double va = a.chroma[ai * pc + k];
                    double vb = b.chroma[bi * pc + k];
                    dot += va * vb;
                    na += va * va;
                    nb += vb * vb;
                }
                if (na <= 1e-12 || nb <= 1e-12) {
                    continue;
                }
                double cos = dot / Math.sqrt(na * nb);
                double w = i * 2 < frames ? 1.0 : MELODY_FRONT_WEIGHT;
                sum += w * Math.max(0.0, cos);
                wsum += w;
                used++;
            }
            if (wsum > 0.0 && used >= 2) {
                double v = sum / wsum;
                if (Double.isNaN(best) || v > best) {
                    best = v;
                }
            }
        }
        return best;
    }

    static final double MELODY_STEP = 0.10;

    static final double GATE_MELODY_LOW = 0.7;

    static final double GATE_MELODY_HIGH = 0.9;

    static double gatedLimit(double original, double opened, double melody) {
        if (Double.isNaN(melody) || melody < GATE_MELODY_LOW) {
            return original;
        }
        if (melody >= GATE_MELODY_HIGH) {
            return opened;
        }
        double t = (melody - GATE_MELODY_LOW) / (GATE_MELODY_HIGH - GATE_MELODY_LOW);
        return original + (opened - original) * t;
    }

    static final boolean LYRIC_ANCHOR_IN_PLAN = false;

    static final double LYRIC_ANCHOR_ALPHA = 0.5;

    static final double LYRIC_ANCHOR_MIN_WINDOW_SECS = 1.2;

    static final double LYRIC_ANCHOR_MAX_WINDOW_SECS = 8.0;

    static final double LYRIC_ANCHOR_MIN_POS = 0.35;

    static final double LYRIC_ANCHOR_MAX_POS = 0.65;

    static double[] lyricAnchor(Envelope outro, double cutSecs, Envelope intro, double entrySecs,
                                double windowSecs, LyricSpans outroSpans, LyricSpans introSpans,
                                double cutFloor, double cutCeil) {
        if (outro == null || intro == null || outroSpans == null || introSpans == null
                || !outroSpans.hasTexts() || !introSpans.hasTexts() || !(windowSecs > 0.0)) {
            return null;
        }
        LyricOverlap.Match m = LyricOverlap.best(outroSpans, cutSecs, cutSecs + windowSecs,
                introSpans, entrySecs, entrySecs + windowSecs);
        if (!m.strong()) {
            return null;
        }
        double delta = m.bHandoverSecs - m.aHandoverSecs;

        double anchorWindow = Math.max(LYRIC_ANCHOR_MIN_WINDOW_SECS,
                Math.min(LYRIC_ANCHOR_MAX_WINDOW_SECS, m.aRunEndSecs - m.aRunStartSecs));

        double newCut = m.aHandoverSecs - anchorWindow * LYRIC_ANCHOR_ALPHA;
        double periodA = outro.tempo != null && outro.beatRate > 0
                ? outro.tempo.periodFrames / (double) outro.beatRate : 0.0;
        if (periodA > 0.05) {
            newCut = Math.round(newCut / periodA) * periodA;
        }

        if (newCut < cutFloor || newCut > cutCeil || Math.abs(newCut - cutSecs) > windowSecs) {
            return null;
        }
        double newEntry = newCut + delta;
        double periodB = intro.tempo != null && intro.beatRate > 0
                ? intro.tempo.periodFrames / (double) intro.beatRate : 0.0;
        if (periodB > 0.05) {
            newEntry = Math.round(newEntry / periodB) * periodB;
        }
        if (newEntry < 0.0 || newEntry + anchorWindow > intro.durationSecs) {
            return null;
        }

        double melody = melodyMatch(outro, newCut, intro, newEntry, anchorWindow);
        if (Double.isNaN(melody) || melody < LYRIC_ANCHOR_MIN_MELODY) {
            return null;
        }
        return new double[] {newCut, newEntry, anchorWindow};
    }

    static final double LYRIC_ANCHOR_MIN_MELODY = 0.55;

    static double lyricAnchoredEntry(Envelope outro, double cutSecs, Envelope intro,
                                     double entrySecs, double windowSecs,
                                     LyricSpans outroSpans, LyricSpans introSpans) {
        if (outro == null || intro == null || outroSpans == null || introSpans == null
                || !outroSpans.hasTexts() || !introSpans.hasTexts() || !(windowSecs > 0.0)) {
            return entrySecs;
        }
        LyricOverlap.Match m = LyricOverlap.best(outroSpans, cutSecs, cutSecs + windowSecs,
                introSpans, entrySecs, entrySecs + windowSecs);
        if (!m.strong()) {
            return entrySecs;
        }

        double delta = -m.offsetSecs();
        double period = intro.tempo != null && intro.beatRate > 0
                ? intro.tempo.periodFrames / (double) intro.beatRate : 0.0;
        if (period > 0.05) {
            delta = Math.round(delta / period) * period;
        }
        double candidate = cutSecs + delta;
        if (candidate < 0.0 || candidate + windowSecs > intro.durationSecs) {
            return entrySecs;
        }
        if (Math.abs(candidate - entrySecs) < 1e-6) {
            return entrySecs;
        }
        double melody = melodyMatch(outro, cutSecs, intro, candidate, windowSecs);
        if (Double.isNaN(melody) || melody < LYRIC_ANCHOR_MIN_MELODY) {
            return entrySecs;
        }
        return candidate;
    }

    static Double melodyOrNull(double melody) {
        if (Double.isNaN(melody)) {
            return null;
        }
        double q = Math.floor(melody / MELODY_STEP + 1e-9) * MELODY_STEP;
        return Double.valueOf(Math.min(1.0, q));
    }

    static final int MELODY_OFFSET_FRAMES = 8;

    static final double MELODY_FRONT_WEIGHT = 0.5;

    static final int BAR_PHASE_STEPS = 48;

    public static double barPhaseOf(Envelope env, double barSecs) {
        if (env == null || !(barSecs > 0.0) || env.onset == null || env.onset.length == 0) {
            return Double.NaN;
        }
        double[] onBeats = beatAlignedBarPhases(env, barSecs);
        if (onBeats != null) {
            return bestBarPhaseOf(env, barSecs, onBeats);
        }
        int steps = BAR_PHASE_STEPS;
        double[] uniform = new double[steps];
        for (int k = 0; k < steps; k++) {
            uniform[k] = barSecs * k / steps;
        }
        return bestBarPhaseOf(env, barSecs, uniform);
    }

    private static double[] beatAlignedBarPhases(Envelope env, double barSecs) {
        if (!env.hasBeatGrid()) {
            return null;
        }
        double period = env.tempo.periodFrames / (double) env.beatRate;
        if (!(period > 0.0) || period > barSecs) {
            return null;
        }
        double[] first = env.beatsIn(0.0, period * 2.0);
        if (first.length == 0) {
            return null;
        }
        int count = (int) Math.round(barSecs / period);
        if (count < 1) {
            return null;
        }
        double[] out = new double[count];
        for (int k = 0; k < count; k++) {
            out[k] = first[0] + k * period;
        }
        return out;
    }

    private static double bestBarPhaseOf(Envelope env, double barSecs, double[] candidates) {
        double best = -1.0;
        double bestPhase = Double.NaN;
        for (int c = 0; c < candidates.length; c++) {
            double phase = candidates[c];
            double sum = 0.0;
            for (double t = phase; t < env.durationSecs; t += barSecs) {
                int i = env.indexAt(t);
                for (int j = Math.max(0, i - 3); j < Math.min(env.onset.length, i + 4); j++) {
                    sum += env.onset[j];
                }
            }
            if (sum > best) {
                best = sum;
                bestPhase = phase;
            }
        }
        return bestPhase;
    }

    public static double snapToBar(double secs, double phase, double barSecs) {
        if (!(barSecs > 0.0)) {
            return secs;
        }
        return phase + Math.round((secs - phase) / barSecs) * barSecs;
    }

    public static VampSpan outroVampSpan(Envelope env) {
        if (env == null || env.band == null || env.tempo == null || !(env.tempo.bpm > 0f)) {
            return null;
        }
        double barSecs = 4.0 * 60.0 / env.tempo.bpm;
        double phase = barPhaseOf(env, barSecs);
        if (Double.isNaN(phase)) {
            return null;
        }
        float[] profile = smoothLevel(env);
        double reference = referenceLevel(profile);
        if (reference <= 1e-6) {
            return null;
        }
        AutoCue.LyricSpans voices = vocalPhraseSpans(env);
        VampSpan best = null;
        double bestScore = -1.0;
        for (int bars = 2; bars <= 8; bars += 2) {
            double span = bars * barSecs;
            double from = Math.max(0.0, env.durationSecs - VAMP_TAIL_SECS);
            for (double t = from; t + span <= env.durationSecs - HOP_SECS; t += barSecs) {
                double start = phase + Math.round((t - phase) / barSecs) * barSecs;
                if (start < 0.0 || start + span > env.durationSecs) {
                    continue;
                }
                VampSpan c = measureVamp(env, profile, reference, voices, start, start + span);
                if (c.levelCv > VAMP_MAX_CV || c.vocalFrac > VAMP_MAX_VOCAL_FRACTION
                        || c.selfSim < VAMP_MIN_SELF_SIM
                        || meanLevelBetween(profile, start, start + span) < VAMP_MIN_LEVEL_RATIO
                                * reference) {
                    continue;
                }

                double score = c.selfSim + 0.02 * bars;
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
        }
        return best;
    }

    static final double INTRO_BED_HEAD_SECS = 45.0;

    static final double INTRO_BED_LEAD_BARS = 8.0;

    static final double INTRO_BED_PATH_MAX_BARS = 16.0;

    public static VampSpan introBedSpan(Envelope env) {
        if (env == null || env.band == null || env.tempo == null || !(env.tempo.bpm > 0f)) {
            return null;
        }
        double barSecs = 4.0 * 60.0 / env.tempo.bpm;
        double phase = barPhaseOf(env, barSecs);
        if (Double.isNaN(phase)) {
            return null;
        }

        double start = Math.max(0.0, phase);
        if (start >= env.durationSecs - HOP_SECS) {
            return null;
        }

        double mainStart = introStartSecs(env);
        double ceiling = mainStart >= 0.0
                ? Math.min(INTRO_BED_HEAD_SECS, mainStart + INTRO_BED_LEAD_BARS * barSecs)
                : INTRO_BED_HEAD_SECS;
        float[] profile = smoothLevel(env);
        double reference = referenceLevel(profile);
        if (reference <= 1e-6) {
            return null;
        }
        AutoCue.LyricSpans voices = vocalPhraseSpans(env);
        VampSpan best = null;
        double bestScore = -1.0;

        for (int bars = 2; bars <= 8; bars += 2) {
            double to = start + bars * barSecs;
            if (to > env.durationSecs - HOP_SECS || to > ceiling) {
                continue;
            }
            VampSpan c = measureVamp(env, profile, reference, voices, start, to);
            if (c.levelCv > VAMP_MAX_CV || c.vocalFrac > VAMP_MAX_VOCAL_FRACTION
                    || c.selfSim < VAMP_MIN_SELF_SIM
                    || meanLevelBetween(profile, start, to) < VAMP_MIN_LEVEL_RATIO
                            * reference) {
                continue;
            }
            double score = c.selfSim + 0.02 * bars;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    private static VampSpan measureVamp(Envelope env, float[] profile, double reference,
                                        LyricSpans voices, double from, double to) {
        int i0 = Math.max(0, env.indexAt(from));
        int i1 = Math.min(profile.length - 1, env.indexAt(to));
        double sum = 0.0;
        double sumSq = 0.0;
        int n = Math.max(1, i1 - i0 + 1);
        for (int i = i0; i <= i1; i++) {
            sum += profile[i];
            sumSq += (double) profile[i] * profile[i];
        }
        double mean = sum / n;
        double cv = mean > 1e-6 ? Math.sqrt(Math.max(0.0, sumSq / n - mean * mean)) / mean : 9.9;
        double voiced = 0.0;
        if (voices != null) {
            for (int i = 0; i < voices.starts.length; i++) {
                double a = Math.max(voices.starts[i], from);
                double b = Math.min(voices.ends[i], to);
                if (b > a) {
                    voiced += b - a;
                }
            }
        }
        int mid = (i0 + i1) / 2;
        double sim = bandProfileSim(env, i0, mid, mid + 1, i1);
        return new VampSpan(from, to, sim, cv, voiced / Math.max(1e-6, to - from));
    }

    private static double meanLevelBetween(float[] profile, double from, double to) {
        int i0 = Math.max(0, (int) Math.round(from / HOP_SECS));
        int i1 = Math.min(profile.length - 1, (int) Math.round(to / HOP_SECS));
        double sum = 0.0;
        int n = 0;
        for (int i = i0; i <= i1; i++) {
            sum += profile[i];
            n++;
        }
        return n == 0 ? 0.0 : sum / n;
    }

    private static double bandProfileSim(Envelope env, int a0, int a1, int b0, int b1) {
        if (env.band == null || a1 < a0 || b1 < b0) {
            return 0.0;
        }
        int k = Math.min(3, env.band[0].length);
        double[] x = new double[k];
        double[] y = new double[k];
        for (int i = a0; i <= a1; i++) {
            for (int b = 0; b < k; b++) {
                x[b] += env.band[i][b];
            }
        }
        for (int i = b0; i <= b1; i++) {
            for (int b = 0; b < k; b++) {
                y[b] += env.band[i][b];
            }
        }
        double mx = 0.0;
        double my = 0.0;
        for (int b = 0; b < k; b++) {
            x[b] /= (a1 - a0 + 1);
            y[b] /= (b1 - b0 + 1);
            mx += x[b];
            my += y[b];
        }
        mx /= k;
        my /= k;
        double num = 0.0;
        double dx = 0.0;
        double dy = 0.0;
        for (int b = 0; b < k; b++) {
            num += (x[b] - mx) * (y[b] - my);
            dx += (x[b] - mx) * (x[b] - mx);
            dy += (y[b] - my) * (y[b] - my);
        }
        return dx <= 1e-12 || dy <= 1e-12 ? 0.0 : num / Math.sqrt(dx * dy);
    }

    public static final class LyricSpans {

        public final double[] starts;

        public final double[] ends;

        public final String[] texts;

        public LyricSpans(double[] starts, double[] ends) {
            this(starts, ends, null);
        }

        public LyricSpans(double[] starts, double[] ends, String[] texts) {
            this.starts = starts == null ? new double[0] : starts;
            this.ends = ends;
            this.texts = texts != null && starts != null && texts.length == starts.length
                    ? texts
                    : null;
        }

        public String textAt(int i) {
            return texts == null || i < 0 || i >= texts.length ? null : texts[i];
        }

        public boolean hasTexts() {
            return texts != null;
        }

        boolean hasEnds() {
            return ends != null && ends.length == starts.length;
        }
    }

    public static LyricSpans spansOfStarts(double[] starts) {
        if (starts == null) {
            return null;
        }

        return new LyricSpans(starts, null);
    }

    static Double lineGapTerm(double[] outroLines, double[] introLines,
                              double cutSecs, double entrySecs, double windowSecs) {
        return lineGapTerm(spansOfStarts(outroLines), spansOfStarts(introLines),
                cutSecs, entrySecs, windowSecs);
    }

    static Double lineGapTerm(LyricSpans outroSpans, LyricSpans introSpans,
                              double cutSecs, double entrySecs, double windowSecs) {
        return penaliseDirtyFade(outroSpans, introSpans, entrySecs, windowSecs,
                lineGapTermRaw(outroSpans, introSpans, cutSecs, entrySecs, windowSecs));
    }

    private static Double penaliseDirtyFade(LyricSpans outroSpans, LyricSpans introSpans,
                                            double entrySecs, double windowSecs, Double raw) {
        if (raw == null || introSpans == null) {
            return raw;
        }
        double gap = phraseGapOf(outroSpans, entrySecs + windowSecs);
        if (headFollowsPhraseGap(introSpans, entrySecs, new double[]{windowSecs}, gap,
                ENTRY_HEAD_TOL_SECS, true)) {
            return raw;
        }
        return Double.valueOf(raw.doubleValue() * DIRTY_FADE_PENALTY);
    }

    static Double lineGapTermRaw(LyricSpans outroSpans, LyricSpans introSpans,
                                 double cutSecs, double entrySecs, double windowSecs) {
        double[] outroLines = outroSpans == null ? null : outroSpans.starts;
        double[] introLines = introSpans == null ? null : introSpans.starts;
        if (outroLines == null || outroLines.length < 2 || introLines == null
                || introLines.length == 0) {
            return null;
        }

        double gA = medianGapBefore(outroLines, cutSecs);
        if (!(gA > 0.0)) {
            return null;
        }

        int aLastIdx = -1;
        for (int i = 0; i < outroLines.length; i++) {
            if (outroLines[i] > cutSecs + windowSecs) {
                break;
            }
            aLastIdx = i;
        }
        if (aLastIdx < 0) {
            return null;
        }

        double endAReal = Math.min(outroRealEnd(outroSpans, aLastIdx, cutSecs), windowSecs);
        double aNextReal = endAReal + gA;

        double bFirst = Double.NaN;
        for (int i = 0; i < introLines.length; i++) {
            if (introLines[i] >= entrySecs) {
                bFirst = introLines[i];
                break;
            }
        }
        if (Double.isNaN(bFirst)) {
            return null;
        }
        double bFirstReal = bFirst - entrySecs;
        double score = 1.0 - Math.min(1.0, Math.abs(bFirstReal - aNextReal) / Math.max(gA, 0.4));
        for (int i = 0; i < introLines.length; i++) {
            if (introLines[i] > entrySecs && introLines[i] < entrySecs + windowSecs) {
                score *= 0.5;
                break;
            }
        }

        double heardIntro = entrySecs + windowSecs * 0.5;
        if (score > 0.0) {
            int prevIdx = -1;
            for (int i = 0; i < introLines.length; i++) {
                if (introLines[i] <= heardIntro) {
                    prevIdx = i;
                } else {
                    break;
                }
            }
            if (prevIdx >= 0) {

                if (introSpans.hasEnds() && introSpans.ends[prevIdx] > introLines[prevIdx]) {

                    double len = introSpans.ends[prevIdx] - introLines[prevIdx];
                    double sung = Math.min(1.0, Math.max(0.0,
                            (heardIntro - introLines[prevIdx]) / len));
                    double hump = 4.0 * sung * (1.0 - sung);
                    score *= 1.0 - LINE_MID_STRENGTH * hump;
                    return Double.valueOf(score);
                }
                double sungFraction;
                if (prevIdx + 1 < introLines.length) {
                    double slot = introLines[prevIdx + 1] - introLines[prevIdx];
                    sungFraction = slot <= 1e-6 ? 0.0
                            : Math.min(1.0, Math.max(0.0,
                                    (heardIntro - introLines[prevIdx]) / slot));
                } else {
                    return Double.valueOf(score);
                }
                if (sungFraction < 1.0 && sungFraction > LINE_MID_GRACE) {
                    double slide = (sungFraction - LINE_MID_GRACE) / (1.0 - LINE_MID_GRACE);
                    score *= 1.0 - LINE_MID_STRENGTH * slide;
                }
            }
        }
        return Double.valueOf(score);
    }

    public static double phraseGapOf(LyricSpans outroSpans, double nearSecs) {
        return phraseGapOf(outroSpans, nearSecs, VOCAL_PHRASE_STRONG_SECS);
    }

    public static double phraseGapOf(LyricSpans spans, double nearSecs, double minPhraseSecs) {
        if (spans == null || spans.starts.length < 3) {
            return Double.NaN;
        }
        double[] starts = spans.starts;
        double[] ends = spans.ends;
        boolean real = ends != null && ends.length == starts.length;
        double[] gaps = new double[6];
        int n = 0;
        int lastStrong = -1;
        for (int i = 0; i < starts.length && starts[i] <= nearSecs; i++) {
            double len = real ? ends[i] - starts[i] : 0.0;
            if (len < minPhraseSecs) {
                continue;
            }
            if (lastStrong >= 0) {
                if (n < gaps.length) {
                    gaps[n++] = starts[i] - starts[lastStrong];
                } else {
                    System.arraycopy(gaps, 1, gaps, 0, gaps.length - 1);
                    gaps[gaps.length - 1] = starts[i] - starts[lastStrong];
                }
            }
            lastStrong = i;
        }
        if (n == 0) {

            return Double.NaN;
        }
        double[] used = java.util.Arrays.copyOf(gaps, n);
        java.util.Arrays.sort(used);
        return n % 2 == 1 ? used[n / 2] : 0.5 * (used[n / 2 - 1] + used[n / 2]);
    }

    private static double medianGapBefore(double[] lines, double cutSecs) {
        double[] gaps = new double[4];
        int n = 0;
        for (int i = lines.length - 1; i > 0 && n < gaps.length; i--) {
            if (lines[i] <= cutSecs) {
                gaps[n++] = lines[i] - lines[i - 1];
            }
        }
        if (n == 0) {

            return Double.NaN;
        }
        double[] used = java.util.Arrays.copyOf(gaps, n);
        java.util.Arrays.sort(used);
        return n % 2 == 1 ? used[n / 2] : 0.5 * (used[n / 2 - 1] + used[n / 2]);
    }

    static final double MAX_WINDOW_SECS = 60.0;

    private static volatile java.util.function.Function<String, double[]> lineSource;

    public static void installLyricSpanSource(
            java.util.function.Function<String, LyricSpans> source) {
        lyricSpanSource = source;
    }

    public static void installLineSource(java.util.function.Function<String, double[]> source) {
        lineSource = source;
    }

    static LyricSpans lyricSpansOf(String source) {
        java.util.function.Function<String, LyricSpans> spans = lyricSpanSource;
        if (spans != null) {
            LyricSpans got = spans.apply(source);
            if (got != null) {
                return got;
            }
        }
        return spansOfStarts(linesOf(source));
    }

    private static volatile java.util.function.Function<String, LyricSpans> lyricSpanSource;

    public static final class KeyRelation {

        public final float plainScore;

        public final String describe;

        public KeyRelation(float plainScore, String describe) {
            this.plainScore = plainScore;
            this.describe = describe;
        }

        @Override
        public String toString() {
            return Float.isNaN(plainScore) ? "key relation: no reading"
                    : String.format(java.util.Locale.US, "chroma similarity %.3f",
                            Float.valueOf(plainScore));
        }
    }

    @FunctionalInterface
    public interface KeyRelationSource {

        KeyRelation apply(String outroSource, java.util.Map<String, String> outroHeaders,
                          String introSource, java.util.Map<String, String> introHeaders);
    }

    private static volatile KeyRelationSource keyRelationSource;

    public static void installKeyRelationSource(KeyRelationSource source) {
        keyRelationSource = source;
    }

    public static KeyRelation keyRelationOf(String outroSource,
                                            java.util.Map<String, String> outroHeaders,
                                            String introSource,
                                            java.util.Map<String, String> introHeaders) {
        KeyRelationSource src = keyRelationSource;
        if (src == null || outroSource == null || introSource == null) {
            return null;
        }
        try {
            return src.apply(outroSource, outroHeaders, introSource, introHeaders);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static KeyRelationSource harmonicKeyRelationSource() {
        return new KeyRelationSource() {

            private final java.util.LinkedHashMap<String, KeyRelation> memo =
                    new java.util.LinkedHashMap<String, KeyRelation>(8, 0.75f, true);

            @Override
            public KeyRelation apply(String outroSource, java.util.Map<String, String> outroHeaders,
                                     String introSource, java.util.Map<String, String> introHeaders) {
                String key = outroSource + "\u0000" + introSource;
                synchronized (memo) {
                    KeyRelation hit = memo.get(key);
                    if (hit != null) {

                        return hit == UNKNOWN ? null : hit;
                    }
                }
                KeyRelation rel = matchOnce(outroSource, outroHeaders, introSource, introHeaders);
                synchronized (memo) {
                    memo.put(key, rel == null ? UNKNOWN : rel);
                    while (memo.size() > KEY_RELATION_MEMO) {
                        java.util.Iterator<String> it = memo.keySet().iterator();
                        it.next();
                        it.remove();
                    }
                }
                return rel;
            }
        };
    }

    private static KeyRelation matchOnce(String outroSource,
                                         java.util.Map<String, String> outroHeaders,
                                         String introSource,
                                         java.util.Map<String, String> introHeaders) {
        try {

            Automix.Mono tail = Automix.readMono(outroSource, outroHeaders, true,
                    Harmonic.WINDOW_SECS);
            Automix.Mono head = Automix.readMono(introSource, introHeaders, false,
                    Harmonic.WINDOW_SECS);
            Harmonic.Match m = Harmonic.match(tail.pcm, tail.rate, head.pcm, head.rate);
            if (m == null) {
                return null;
            }
            return new KeyRelation(m.plainScore, String.format(java.util.Locale.US,
                    "chroma similarity %.3f (suggests shifting %+d semitones)",
                    Float.valueOf(m.plainScore), Integer.valueOf(m.semitones)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final KeyRelation UNKNOWN = new KeyRelation(Float.NaN, null);

    private static final int KEY_RELATION_MEMO = 8;

    public static boolean keyClashWithCompatibleGrid(KeyRelation key, Envelope outro,
                                                     Envelope intro) {
        if (key == null || Float.isNaN(key.plainScore)) {
            return false;
        }
        if (!(key.plainScore < LOOP_KEY_SAME_SCORE)) {
            return false;
        }
        if (outro == null || intro == null || outro.tempo == null || intro.tempo == null
                || !(outro.tempo.bpm > 0f) || !(intro.tempo.bpm > 0f)) {
            return false;
        }
        double diff = Math.abs(intro.tempo.bpm - outro.tempo.bpm) / outro.tempo.bpm;
        return diff < LOOP_BPM_DIFF_MIN;
    }

    static double[] linesOf(String source) {
        java.util.function.Function<String, double[]> f = lineSource;
        if (f == null || source == null || source.isEmpty()) {
            return null;
        }
        try {
            double[] lines = f.apply(source);
            return lines == null || lines.length == 0 ? null : lines;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static final class Score {

        public final double overlap;

        public final double ripple;

        public final double bands;

        public final double level;

        public final double motion;

        public final double beat;

        public final double tail;

        public final double vocal;

        public final double vocalSecs;

        public final double lowEnd;

        public final Double lineGap;

        public final Double melodyToScore;

        public final Double emotionToScore;

        public final Double structure;

        public final Double bar;

        public final Double novelty;

        public final double total;

        public final Double intensityGap;

        final Double intensityToScore;

        final String beatNote;

        final double cutSecs;
        final double entrySecs;
        final double windowSecs;

        Score(double overlap, double ripple, double bands, double level, double motion,
              double beat, double tail, String beatNote,
              double cutSecs, double entrySecs, double windowSecs) {
            this(overlap, ripple, bands, level, motion, beat, tail, 0.5, 0.5, 0.0, beatNote,
                    cutSecs, entrySecs, windowSecs, null, null, null, null);
        }

        Score(double overlap, double ripple, double bands, double level, double motion,
              double beat, double tail, String beatNote,
              double cutSecs, double entrySecs, double windowSecs, Double lineGap) {
            this(overlap, ripple, bands, level, motion, beat, tail, 0.5, 0.5, 0.0, beatNote,
                    cutSecs, entrySecs, windowSecs, lineGap, null, null, null);
        }

        Score(double overlap, double ripple, double bands, double level, double motion,
              double beat, double tail, double lowEnd, double vocal, double vocalSecs,
              String beatNote,
              double cutSecs, double entrySecs, double windowSecs,
              Double lineGap, Double structure, Double bar, Double novelty) {
            this(overlap, ripple, bands, level, motion, beat, tail, lowEnd, vocal, vocalSecs,
                    beatNote, cutSecs, entrySecs, windowSecs, lineGap, structure, bar, novelty,
                    null);
        }

        Score(double overlap, double ripple, double bands, double level, double motion,
              double beat, double tail, double lowEnd, double vocal, double vocalSecs,
              String beatNote,
              double cutSecs, double entrySecs, double windowSecs,
              Double lineGap, Double structure, Double bar, Double novelty,
              Double intensityGap) {
            this(overlap, ripple, bands, level, motion, beat, tail, lowEnd, vocal, vocalSecs,
                    beatNote, cutSecs, entrySecs, windowSecs, lineGap, structure, bar, novelty,
                    intensityGap,
                    intensityGap == null ? null
                            : Double.valueOf(intensityTerm(intensityGap.doubleValue())),
                    null, null);
        }

        Score(double overlap, double ripple, double bands, double level, double motion,
              double beat, double tail, double lowEnd, double vocal, double vocalSecs,
              String beatNote,
              double cutSecs, double entrySecs, double windowSecs,
              Double lineGap, Double structure, Double bar, Double novelty,
              Double intensityGap, Double intensityToScore,
              Double melodyToScore, Double emotionToScore) {
            this.melodyToScore = melodyToScore;

            this.emotionToScore = emotionToScore != null
                    ? emotionToScore
                    : (intensityToScore == null ? null
                            : emotionMatch(intensityToScore, bands, lowEnd));
            this.lineGap = lineGap;
            this.structure = structure;
            this.bar = bar;
            this.novelty = novelty;
            this.intensityGap = intensityGap;
            this.intensityToScore = intensityToScore;
            this.overlap = overlap;
            this.ripple = ripple;
            this.bands = bands;
            this.level = level;
            this.motion = motion;
            this.beat = beat;
            this.tail = tail;
            this.lowEnd = lowEnd;
            this.vocal = vocal;
            this.vocalSecs = vocalSecs;
            this.beatNote = beatNote;
            this.cutSecs = cutSecs;
            this.entrySecs = entrySecs;
            this.windowSecs = windowSecs;
            double sum = WEIGHTS[0] * overlap + WEIGHTS[1] * ripple + WEIGHTS[2] * bands
                    + WEIGHTS[3] * level + WEIGHTS[4] * motion + WEIGHTS[5] * beat
                    + WEIGHTS[6] * tail + WEIGHTS[7] * lowEnd + WEIGHTS[8] * vocal;

            double base;
            if (intensityToScore == null) {
                base = sum / WEIGHT_SUM;
            } else {
                base = (sum + INTENSITY_WEIGHT * intensityToScore.doubleValue())
                        / (WEIGHT_SUM + INTENSITY_WEIGHT);
            }

            if (melodyToScore != null) {
                base += MELODY_WEIGHT * melodyToScore.doubleValue()
                        / (WEIGHT_SUM + MELODY_WEIGHT + EMOTION_WEIGHT);
            }
            if (emotionToScore != null) {
                base += EMOTION_WEIGHT * emotionToScore.doubleValue()
                        / (WEIGHT_SUM + MELODY_WEIGHT + EMOTION_WEIGHT);
            }

            if (lineGap != null) {
                base += LINE_GAP_WEIGHT * lineGap.doubleValue();
            }
            if (structure != null) {
                base += STRUCTURE_WEIGHT * structure.doubleValue();
            }
            if (bar != null) {
                base += BAR_WEIGHT * bar.doubleValue();
            }
            if (novelty != null) {
                base += NOVELTY_WEIGHT * novelty.doubleValue();
            }
            this.total = base;
        }

        Score withLineGap(Double term) {
            if (term == null) {
                return this;
            }
            return new Score(overlap, ripple, bands, level, motion, beat, tail, lowEnd, vocal, vocalSecs,
                    beatNote, cutSecs, entrySecs, windowSecs, term, structure, bar, novelty);
        }

        Score withMelody(Double melody, Double emotion) {
            if (melody == null && emotion == null) {
                return this;
            }
            return new Score(overlap, ripple, bands, level, motion, beat, tail, lowEnd, vocal, vocalSecs,
                    beatNote, cutSecs, entrySecs, windowSecs, lineGap, structure, bar, novelty,
                    intensityGap, intensityToScore, melody, emotion);
        }

        Score withNovelty(Double term) {
            if (term == null) {
                return this;
            }
            return new Score(overlap, ripple, bands, level, motion, beat, tail, lowEnd, vocal, vocalSecs,
                    beatNote, cutSecs, entrySecs, windowSecs, lineGap, structure, bar, term);
        }

        Score withBar(Double term) {
            if (term == null) {
                return this;
            }
            return new Score(overlap, ripple, bands, level, motion, beat, tail, lowEnd, vocal, vocalSecs,
                    beatNote, cutSecs, entrySecs, windowSecs, lineGap, structure, term, novelty);
        }

        Score withIntensity(Double gap, boolean scored) {
            return new Score(overlap, ripple, bands, level, motion, beat, tail, lowEnd, vocal,
                    vocalSecs, beatNote, cutSecs, entrySecs, windowSecs, lineGap, structure,
                    bar, novelty, gap,
                    scored && gap != null ? Double.valueOf(intensityTerm(gap.doubleValue())) : null,

                    melodyToScore, emotionToScore);
        }

        Score withStructure(Double term) {
            if (term == null) {
                return this;
            }
            return new Score(overlap, ripple, bands, level, motion, beat, tail, lowEnd, vocal, vocalSecs,
                    beatNote, cutSecs, entrySecs, windowSecs, lineGap, term, bar, novelty);
        }
    }

    static final double[] WEIGHTS = {1.6, 1.2, 0.8, 1.0, 0.8, 0.5, 0.35, 0.9, 0.6};

    static final double WEIGHT_SUM = 7.75;

    static final double MELODY_WEIGHT = 8.0;

    static final double EMOTION_WEIGHT = 3.0;

    static Double emotionMatch(Double intensityTerm, double bands, double lowEnd) {
        if (intensityTerm == null) {
            return null;
        }
        return Double.valueOf((intensityTerm.doubleValue() + bands + lowEnd) / 3.0);
    }

    static final double DRIFT_FREE_BEATS = 0.25;

    static final double DRIFT_FALLOFF_BEATS = 1.25;

    static final double LINE_GAP_WEIGHT = 0.10;

    static final double LINE_MID_FRACTION = 0.25;

    static final double LINE_MID_STRENGTH = 0.75;

    static final double LINE_MID_GRACE = 0.25;

    static final double LYRIC_MID_HUMP_MAX = 0.5;

    static final double LINE_MID_FROM = 0.30;

    static final double LINE_MID_TO = 0.70;

    public static double midLineHump(LyricSpans spans, double heardSecs) {
        double sung = sungFractionAt(spans, heardSecs);
        return Double.isNaN(sung) ? 0.0 : 4.0 * sung * (1.0 - sung);
    }

    public static double sungFractionAt(LyricSpans spans, double heardSecs) {
        if (spans == null || spans.starts.length == 0) {
            return Double.NaN;
        }
        int idx = -1;
        for (int i = 0; i < spans.starts.length; i++) {
            if (spans.starts[i] <= heardSecs) {
                idx = i;
            } else {
                break;
            }
        }
        if (idx < 0) {
            return Double.NaN;
        }
        double start = spans.starts[idx];

        double end = (spans.ends != null && spans.ends.length == spans.starts.length
                && spans.ends[idx] > start) ? spans.ends[idx]
                : (idx + 1 < spans.starts.length ? spans.starts[idx + 1] : Double.NaN);
        if (Double.isNaN(end) || !(end > start)) {
            return Double.NaN;
        }
        if (heardSecs >= end) {
            return 1.0;
        }
        return Math.min(1.0, Math.max(0.0, (heardSecs - start) / (end - start)));
    }

    static final double VOCAL_LINE_MIN_SECS = 2.5;

    static final int MIN_LYRIC_GAP_CELLS = 25;

    static final double ENTRY_HEAD_GAIN_MIN = 0.5;

    static final int MIN_LYRIC_HEAD_CELLS = 4;

    static final double ENTRY_HEAD_TOL_SECS = 0.35;

    static final double ENTRY_HEAD_RISE_RATIO = 1.5;

    static final double DIRTY_FADE_PENALTY = 0.4;

    static final double INTRO_ANCHOR_LEAD_SECS = 2.0;

    static final double INTRO_REFERENCE_SECS = 60.0;

    static final double ENTRY_ANCHOR_MID_CAP_SECS = 5.0;

    static final double VOCAL_HOLE_MERGE_SECS = 0.18;

    static final double VOCAL_PHRASE_MIN_SECS = 0.45;

    static final double VOCAL_PHRASE_STRONG_SECS = 1.5;

    static boolean vocalOverlap(LyricSpans outroSpans, double cutSecs, double windowSecs,
                                LyricSpans introSpans, double entrySecs) {
        if (outroSpans == null || introSpans == null
                || outroSpans.starts.length == 0 || introSpans.starts.length == 0) {
            return false;
        }
        for (double sa : audibleStarts(outroSpans, cutSecs, windowSecs)) {
            for (double sb : audibleStarts(introSpans, entrySecs, windowSecs)) {
                if (Math.abs((sa - cutSecs) - (sb - entrySecs)) < VOCAL_LINE_MIN_SECS) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double[] audibleStarts(LyricSpans spans, double from, double windowSecs) {
        double before = Double.NaN;
        double[] hit = new double[spans.starts.length];
        int n = 0;
        for (int i = 0; i < spans.starts.length; i++) {
            double t = spans.starts[i];
            if (t < from) {
                before = t;
                continue;
            }
            if (t > from + windowSecs) {
                break;
            }
            hit[n++] = t;
        }
        if (!Double.isNaN(before)) {
            double[] out = new double[n + 1];
            out[0] = before;
            System.arraycopy(hit, 0, out, 1, n);
            return out;
        }
        return java.util.Arrays.copyOf(hit, n);
    }

    public static double phraseGridError(LyricSpans outroSpans, double cutSecs, double windowSecs,
                                         LyricSpans introSpans, double entrySecs) {
        if (outroSpans == null || introSpans == null
                || outroSpans.starts.length < 2 || introSpans.starts.length == 0) {
            return Double.NaN;
        }
        double[] a = outroSpans.starts;
        double gA = medianGapBefore(a, cutSecs);
        if (!(gA > 0.0)) {
            return Double.NaN;
        }
        int aLastIdx = -1;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > cutSecs + windowSecs) {
                break;
            }
            aLastIdx = i;
        }
        if (aLastIdx < 0) {
            return Double.NaN;
        }
        double aLast = a[aLastIdx];
        double bFirst = Double.NaN;
        for (int i = 0; i < introSpans.starts.length; i++) {
            if (introSpans.starts[i] >= entrySecs) {
                bFirst = introSpans.starts[i];
                break;
            }
        }
        if (Double.isNaN(bFirst)) {
            return Double.NaN;
        }
        double endA = Math.min(outroRealEnd(outroSpans, aLastIdx, cutSecs), windowSecs);
        double ideal = endA + gA;
        return Math.abs((bFirst - entrySecs) - ideal);
    }

    private static double[] pickAligned(dev.t1m3.qplayer.audio.mix.AutoCue.Envelope intro,
                                        LyricSpans introSpans, double[] times, double[] windows,
                                        double gapSecs, boolean requireRise) {

        boolean cleanFade = false;
        int keep = 0;
        for (int i = 0; i < times.length; i++) {
            if (headFollowsPhraseGap(introSpans, times[i], windows, gapSecs,
                    ENTRY_HEAD_TOL_SECS, cleanFade)
                    && (!requireRise
                            || anyWindowRises(intro, introSpans, times[i], windows))) {
                keep++;
            }
        }
        if (keep < MIN_LYRIC_HEAD_CELLS) {
            return null;
        }
        double[] kept = new double[keep];
        int n = 0;
        for (int i = 0; i < times.length; i++) {
            if (headFollowsPhraseGap(introSpans, times[i], windows, gapSecs,
                    ENTRY_HEAD_TOL_SECS, cleanFade)
                    && (!requireRise
                            || anyWindowRises(intro, introSpans, times[i], windows))) {
                kept[n++] = times[i];
            }
        }
        return kept;
    }

    private static boolean anyWindowRises(dev.t1m3.qplayer.audio.mix.AutoCue.Envelope intro,
                                          LyricSpans introSpans, double entrySecs,
                                          double[] windows) {
        if (windows == null) {
            return true;
        }
        for (int k = 0; k < windows.length; k++) {
            if (risesAtSettleHead(intro, introSpans, entrySecs, windows[k])) {
                return true;
            }
        }
        return false;
    }

    static boolean risesAtSettleHead(dev.t1m3.qplayer.audio.mix.AutoCue.Envelope intro,
                                     LyricSpans introSpans, double entrySecs, double windowSecs) {
        if (intro == null || introSpans == null || introSpans.starts.length == 0) {
            return true;
        }
        double settle = entrySecs + windowSecs;
        double head = Double.NaN;
        for (int i = 0; i < introSpans.starts.length; i++) {
            if (introSpans.starts[i] >= settle) {
                head = introSpans.starts[i];
                break;
            }
        }
        if (Double.isNaN(head)) {
            return true;
        }
        double before = meanLevel(intro, head - 1.0, head - 0.2);
        double after = meanLevel(intro, head + 0.5, head + 2.0);
        if (!(before > 0.0)) {
            return after > 0.0;
        }
        return after >= before * ENTRY_HEAD_RISE_RATIO;
    }

    private static double meanLevel(dev.t1m3.qplayer.audio.mix.AutoCue.Envelope env,
                                    double from, double to) {
        int i0 = Math.max(0, env.indexAt(Math.max(0.0, from)));
        int i1 = Math.min(env.rms.length - 1, env.indexAt(to));
        if (i1 < i0) {
            return env.rms[Math.max(0, Math.min(env.rms.length - 1, i0))];
        }
        double sum = 0.0;
        for (int i = i0; i <= i1; i++) {
            sum += env.rms[i];
        }
        return sum / (i1 - i0 + 1);
    }

    static boolean cutEndsPhrase(LyricSpans outroSpans, double cutSecs, double[] windows) {
        if (outroSpans == null || windows == null) {
            return true;
        }
        for (int k = 0; k < windows.length; k++) {
            if (endsPhraseNear(outroSpans, cutSecs + windows[k], ENTRY_HEAD_TOL_SECS)) {
                return true;
            }
        }
        return false;
    }

    public static boolean endsPhraseNear(LyricSpans outroSpans, double secs, double tolSecs) {
        if (outroSpans == null || outroSpans.starts.length == 0) {
            return true;
        }

        if (!insideLyricSpan(outroSpans, secs)) {
            return true;
        }
        double[] starts = outroSpans.starts;
        boolean real = outroSpans.hasEnds();
        for (int i = 0; i < starts.length; i++) {
            double end;
            if (real && outroSpans.ends[i] > starts[i]) {
                end = outroSpans.ends[i];
            } else if (i + 1 < starts.length) {
                end = starts[i + 1];
            } else {
                continue;
            }
            if (Math.abs(end - secs) <= tolSecs) {
                return true;
            }
        }
        return false;
    }

    public static LyricSpans vocalPhraseSpans(Envelope env) {
        float[] mask = env == null ? null : env.voiceActiveMask();
        if (mask == null || mask.length == 0) {
            return null;
        }
        int mergeHops = Math.max(1, (int) Math.round(VOCAL_HOLE_MERGE_SECS / HOP_SECS));
        int minHops = Math.max(1, (int) Math.round(VOCAL_PHRASE_MIN_SECS / HOP_SECS));
        java.util.ArrayList<double[]> runs = new java.util.ArrayList<>();
        int start = -1;
        int lastActive = -1;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i] == 0f) {
                continue;
            }
            if (start < 0) {
                start = i;
            } else if (i - lastActive > mergeHops) {
                runs.add(new double[]{start, lastActive});
                start = i;
            }
            lastActive = i;
        }
        if (start >= 0) {
            runs.add(new double[]{start, lastActive});
        }
        int keep = 0;
        for (int i = 0; i < runs.size(); i++) {
            if (runs.get(i)[1] - runs.get(i)[0] + 1 >= minHops) {
                keep++;
            }
        }
        if (keep == 0) {
            return null;
        }
        double[] starts = new double[keep];
        double[] ends = new double[keep];
        int n = 0;
        for (int i = 0; i < runs.size(); i++) {
            double[] r = runs.get(i);
            if (r[1] - r[0] + 1 < minHops) {
                continue;
            }
            starts[n] = env.secs((int) r[0]);

            ends[n] = Math.min(env.durationSecs, env.secs((int) r[1] + 1));
            n++;
        }
        return new LyricSpans(starts, ends);
    }

    public static boolean headFollowsPhraseGap(LyricSpans introSpans, double entrySecs,
                                               double[] windows, double gapSecs,
                                               double tolSecs) {
        return headFollowsPhraseGap(introSpans, entrySecs, windows, gapSecs, tolSecs, true);
    }

    public static boolean headFollowsPhraseGap(LyricSpans introSpans, double entrySecs,
                                               double[] windows, double gapSecs,
                                               double tolSecs, boolean requireCleanFade) {
        if (introSpans == null || introSpans.starts.length == 0 || windows == null) {
            return true;
        }
        boolean gapKnown = !Double.isNaN(gapSecs);
        for (int k = 0; k < windows.length; k++) {
            double settle = entrySecs + windows[k];
            double head = Double.NaN;
            double firstFrom = Double.NaN;
            for (int i = 0; i < introSpans.starts.length; i++) {
                if (Double.isNaN(firstFrom) && introSpans.starts[i] >= entrySecs) {
                    firstFrom = introSpans.starts[i];
                }
                if (introSpans.starts[i] >= settle) {
                    head = introSpans.starts[i];
                    break;
                }
            }
            if (Double.isNaN(head)) {
                continue;
            }

            if (requireCleanFade && !Double.isNaN(firstFrom) && firstFrom < settle - tolSecs) {
                continue;
            }
            if (!gapKnown || Math.abs((head - settle) - gapSecs) <= tolSecs) {
                return true;
            }
        }
        return false;
    }

    public static boolean alignsWithHead(LyricSpans introSpans, double entrySecs,
                                         double[] windows, double tolSecs) {
        if (introSpans == null || introSpans.starts.length == 0 || windows == null) {
            return true;
        }
        for (int k = 0; k < windows.length; k++) {
            double settle = entrySecs + windows[k];
            for (int i = 0; i < introSpans.starts.length; i++) {
                if (Math.abs(introSpans.starts[i] - settle) <= tolSecs) {
                    return true;
                }
            }
        }
        return false;
    }

    public static double headGain(LyricSpans introSpans, double entrySecs, double windowSecs) {
        if (introSpans == null || introSpans.starts.length == 0 || !(windowSecs > 0.0)) {
            return Double.NaN;
        }
        double head = Double.NaN;
        for (int i = 0; i < introSpans.starts.length; i++) {
            if (introSpans.starts[i] >= entrySecs) {
                head = introSpans.starts[i];
                break;
            }
        }
        if (Double.isNaN(head)) {
            return Double.NaN;
        }
        double progress = Math.min(1.0, Math.max(0.0, (head - entrySecs) / windowSecs));
        return Math.sin(Math.PI / 2.0 * progress);
    }

    private static double outroRealEnd(LyricSpans outroSpans, int idx, double cutSecs) {
        double start = outroSpans.starts[idx];
        if (outroSpans.hasEnds() && outroSpans.ends[idx] > start) {
            return outroSpans.ends[idx] - cutSecs;
        }
        return (start - cutSecs) + VOCAL_LINE_MIN_SECS;
    }

    public static boolean isMidLyric(LyricSpans spans, double heardSecs) {
        double sung = sungFractionAt(spans, heardSecs);
        return !Double.isNaN(sung) && sung > LINE_MID_FROM && sung < LINE_MID_TO;
    }

    public static boolean insideLyricSpan(LyricSpans spans, double secs) {
        if (spans == null || spans.starts.length == 0) {
            return false;
        }
        double[] starts = spans.starts;
        if (secs < starts[0]) {
            return false;
        }
        boolean real = spans.hasEnds();
        for (int i = 0; i < starts.length; i++) {
            if (secs < starts[i]) {
                return false;
            }
            double len;
            if (real && spans.ends[i] > starts[i]) {
                len = spans.ends[i] - starts[i];
            } else {
                double slot = i + 1 < starts.length
                        ? starts[i + 1] - starts[i] : Double.MAX_VALUE;
                len = Math.min(VOCAL_LINE_MIN_SECS, slot);
            }
            if (secs < starts[i] + len) {
                return true;
            }
        }
        return false;
    }

    static final double LINE_MID_PENALTY = 1.0 - LINE_MID_STRENGTH;

    static final double STRUCTURE_WEIGHT = 0.06;

    static final double STRUCTURE_INTRO_SCALE = 5.0;

    static final double STRUCTURE_OUTRO_SCALE = 5.0;

    static final double STRUCTURE_TOLERANCE_SECS = 6.0;

    static final double OUTRO_KEEP_RATIO = 0.95;

    static final double INTRO_KEEP_RATIO = 0.95;

    static final double INTRO_RISE_FLOOR_RATIO = 0.35;

    static final double INTRO_RISE_BACK_TOLERANCE = 1.15;

    static final double STRUCTURE_SMOOTH_SECS = 2.0;

    static final double NOVELTY_WINDOW_SECS = 2.0;

    static final double[] NOVELTY_SCALES = {1.0, 2.0, 4.0};

    static final double NOVELTY_PEAK_RATIO = 0.6;

    static final double NOVELTY_SIDE_LEVEL_RATIO = 0.5;

    static final double STRUCTURE_PEAK_SECS = 3.0;

    static final double STRUCTURE_MIN_RUN_SECS = 4.0;

    static Score evaluate(Envelope outro, Envelope intro,
                          double cutSecs, double entrySecs, double windowSecs) {
        int c0 = outro.indexAt(cutSecs);
        int e0 = intro.indexAt(entrySecs);
        int hops = Math.max(1, (int) Math.round(windowSecs / HOP_SECS));

        double sum = 0.0;
        double sumSq = 0.0;
        double meanA = 0.0;
        double meanB = 0.0;
        for (int i = 0; i < hops; i++) {
            float p = hops <= 1 ? 0.5f : (float) i / (float) (hops - 1);
            Mixer.Gains g = Mixer.equalPowerGains(p);
            double a = outro.rms[Math.min(c0 + i, outro.rms.length - 1)];
            double b = intro.rms[Math.min(e0 + i, intro.rms.length - 1)];
            double mixed = a * g.from + b * g.to;
            sum += mixed;
            sumSq += mixed * mixed;
            meanA += a;
            meanB += b;
        }
        double mean = sum / hops;
        double variance = Math.max(0.0, sumSq / hops - mean * mean);
        double ripple = mean <= 1e-6 ? 1.0 : Math.min(1.0, Math.sqrt(variance) / mean);
        meanA /= hops;
        meanB /= hops;

        double overlap = waveOverlap(outro, intro, cutSecs, entrySecs, windowSecs);
        double bands = bandComplement(outro, intro, c0, e0, hops);

        double levelDelta = Math.abs(meanA - meanB);
        double level = levelDelta <= LEVEL_DEAD_ZONE ? 1.0
                : 1.0 - Math.min(1.0, (levelDelta - LEVEL_DEAD_ZONE)
                        / (1.0 - LEVEL_DEAD_ZONE));

        double motion = 0.5 * (1.0 - rising(outro, c0))
                + 0.5 * (1.0 - falling(intro, e0));
        double tailDrop = Math.max(0.0, outro.durationSecs - (cutSecs + windowSecs));
        double tail = tailTerm(tailDrop, outro.durationSecs);

        double beat = 0.5;
        String beatNote = "no beats detected";
        if (outro.hasBeatGrid() && intro.hasBeatGrid()) {
            double driftBeats = Math.abs(outro.tempo.bpm - intro.tempo.bpm) / 60.0 * windowSecs;
            double over = Math.max(0.0, driftBeats - DRIFT_FREE_BEATS);
            beat = 1.0 - Math.min(1.0, over / DRIFT_FALLOFF_BEATS);
            beatNote = String.format(java.util.Locale.US, "beat drift %.1f beats/window", Double.valueOf(driftBeats));
        }
        double lowEnd = lowEndClash(outro, intro, c0, e0, hops);

        double vocalSecs = vocalOverlapSecs(outro, intro, c0, e0, hops);
        double vocal = vocalOverlapTerm(vocalSecs);
        return new Score(overlap, 1.0 - ripple, bands, level, motion, beat, tail, lowEnd, vocal,
                vocalSecs,
                beatNote, cutSecs, entrySecs, windowSecs, null, null, null, null);
    }

    static final double LEVEL_DEAD_ZONE = 0.10;

    static final double OVERLAP_SPAN_SECS = 4.0;

    static double waveOverlap(Envelope a, Envelope b,
                              double cutSecs, double entrySecs, double windowSecs) {
        if (a.wave == null || b.wave == null || a.waveRate <= 0 || b.waveRate <= 0) {
            return 0.5;
        }
        int rate = Math.min(a.waveRate, b.waveRate);
        int span = (int) Math.max(1L, Math.round(Math.min(windowSecs, OVERLAP_SPAN_SECS) * rate));
        int a0 = (int) Math.round(cutSecs * rate);
        int b0 = (int) Math.round(entrySecs * rate);
        if (a0 + span >= a.wave.length || b0 + span >= b.wave.length || a0 < 0 || b0 < 0) {

            return 0.5;
        }
        double beatSecs = beatSecsFor(a, b);

        double[] lags = {0.0, 0.5 * beatSecs, -0.5 * beatSecs};
        double best = -1.0;
        for (int i = 0; i < lags.length; i++) {
            int lag = (int) Math.round(lags[i] * rate);
            int bStart = b0 + lag;
            if (bStart < 0 || bStart + span >= b.wave.length) {
                continue;
            }
            double c = correlation(a.wave, a0, b.wave, bStart, span);
            if (c > best) {
                best = c;
            }
        }
        if (best < -1.0) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, (best + 1.0) * 0.5));
    }

    private static double beatSecsFor(Envelope a, Envelope b) {
        double out = 0.5;
        if (a.hasBeatGrid()) {
            out = a.tempo.periodFrames / (double) a.beatRate;
        }
        if (b.hasBeatGrid()) {
            double other = b.tempo.periodFrames / (double) b.beatRate;
            out = Math.min(out > 0 ? out : other, other);
        }
        if (!(out > 0.05) || out > 5.0) {
            return 0.5;
        }
        return out;
    }

    private static double correlation(float[] a, int aStart, float[] b, int bStart, int n) {
        double meanA = 0.0;
        double meanB = 0.0;
        for (int i = 0; i < n; i++) {
            meanA += a[aStart + i];
            meanB += b[bStart + i];
        }
        meanA /= n;
        meanB /= n;
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < n; i++) {
            double x = a[aStart + i] - meanA;
            double y = b[bStart + i] - meanB;
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        double denom = Math.sqrt(normA * normB);
        if (denom <= 1e-12) {
            return 0.0;
        }
        return Math.max(-1.0, Math.min(1.0, dot / denom));
    }

    static double bandComplement(Envelope a, Envelope b, int aStart, int bStart, int hops) {
        if (a.band == null || b.band == null) {
            return 0.5;
        }
        double clash = 0.0;
        double energy = 0.0;
        for (int band = 0; band < 3; band++) {
            double ma = 0.0;
            double mb = 0.0;
            for (int i = 0; i < hops; i++) {
                int ai = Math.min(aStart + i, a.band.length - 1);
                int bi = Math.min(bStart + i, b.band.length - 1);
                ma += a.band[ai][band];
                mb += b.band[bi][band];
            }
            ma /= hops;
            mb /= hops;
            clash += Math.min(ma, mb);
            energy += Math.max(ma, mb);
        }
        if (energy <= 1e-6) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - clash / energy));
    }

    static double lowEndClash(Envelope a, Envelope b, int aStart, int bStart, int hops) {
        if (hops <= 0) {
            return 0.5;
        }

        boolean hasMud = a.band != null && b.band != null;
        boolean hasPhase = a.wave != null && b.wave != null
                && a.waveRate > 0 && a.waveRate == b.waveRate;
        if (!hasMud && !hasPhase) {
            return 0.5;
        }
        if (!hasPhase) {
            return clip01(lowEndOverlap(a, b, aStart, bStart, hops));
        }
        if (!hasMud) {
            return clip01(lowEndPhase(a, b, aStart, bStart, hops));
        }
        return clip01(LOW_END_MUD_SHARE * lowEndOverlap(a, b, aStart, bStart, hops)
                + LOW_END_PHASE_SHARE * lowEndPhase(a, b, aStart, bStart, hops));
    }

    static final double LOW_END_COARSE_WEIGHT = 0.7;

    static double vocalOverlap(Envelope a, Envelope b, int aStart, int bStart, int hops) {
        if (hops <= 0 || a.band == null || b.band == null) {
            return 0.5;
        }
        return vocalOverlapTerm(vocalOverlapSecs(a, b, aStart, bStart, hops));
    }

    static double vocalOverlapTerm(double overlapSecs) {
        if (overlapSecs <= VOICE_OVERLAP_FREE_SECS) {
            return 1.0;
        }
        double over = overlapSecs - VOICE_OVERLAP_FREE_SECS;
        return clip01(1.0 - over / (VOICE_OVERLAP_WORST_SECS - VOICE_OVERLAP_FREE_SECS));
    }

    static float[] vocalDuckCurve(Envelope a, Envelope b, int aStart, int bStart, int hops) {
        if (hops <= 0 || a.band == null || b.band == null) {
            return null;
        }
        float[] active = new float[hops];
        for (int i = 0; i < hops; i++) {
            int ai = aStart + i;
            int bi = bStart + i;
            boolean both = ai >= 0 && bi >= 0
                    && ai < a.band.length && bi < b.band.length
                    && voicePresent(a, ai) && voicePresent(b, bi);
            active[i] = both ? 1f : 0f;
        }

        float[] curve = new float[hops];
        int half = DUCK_CURVE_SMOOTH_HOPS / 2;
        for (int i = 0; i < hops; i++) {
            int from = Math.max(0, i - half);
            int to = Math.min(hops, i + half + 1);
            float sum = 0f;
            for (int j = from; j < to; j++) {
                sum += active[j];
            }

            curve[i] = (sum / (to - from)) * vocalDuckCap;
        }
        return curve;
    }

    static final int DUCK_CURVE_SMOOTH_HOPS = 15;

    private static volatile float vocalDuckCap = 0.75f;

    public static void setVocalDuckCap(float cap) {
        vocalDuckCap = Math.max(0f, Math.min(1f, cap));
    }

    public static float vocalDuckCap() {
        return vocalDuckCap;
    }

    static double vocalOverlapSecs(Envelope a, Envelope b, int aStart, int bStart, int hops) {
        if (a.band == null || b.band == null || hops <= 0) {
            return 0.0;
        }
        double overlapSecs = 0.0;
        for (int i = 0; i < hops; i++) {
            int ai = aStart + i;
            int bi = bStart + i;
            if (ai < 0 || bi < 0 || ai >= a.band.length || bi >= b.band.length) {
                continue;
            }
            if (voiceActive(a, ai) && voiceActive(b, bi)) {
                overlapSecs += HOP_SECS;
            }
        }
        return overlapSecs;
    }

    static double[] vocalOverlapRanges(Envelope a, Envelope b, int aStart, int bStart, int hops) {
        if (hops <= 0) {
            return new double[0];
        }
        if (a.band == null || b.band == null) {
            return null;
        }
        double[] out = new double[hops * 2];
        int n = 0;
        boolean inRange = false;
        double from = 0.0;
        double gap = 0.0;
        for (int i = 0; i <= hops; i++) {
            boolean active = false;
            if (i < hops) {
                int ai = aStart + i;
                int bi = bStart + i;
                active = ai >= 0 && bi >= 0 && ai < a.band.length && bi < b.band.length
                        && voicePresent(a, ai) && voicePresent(b, bi);
            }
            if (active) {
                if (!inRange) {
                    inRange = true;
                    from = i * HOP_SECS;
                }
                gap = 0.0;
            } else if (inRange) {
                gap += HOP_SECS;
                if (gap >= VOICE_RANGE_MIN_SECS || i >= hops) {
                    double to = i * HOP_SECS - gap;

                    if (n + 2 > out.length) {
                        out = java.util.Arrays.copyOf(out, out.length * 2);
                    }
                    out[n++] = from;
                    out[n++] = to;
                    inRange = false;
                }
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    static final double VOICE_RANGE_MIN_SECS = 0.4;

    static boolean voiceActive(Envelope e, int idx) {
        float[] mask = e.voiceActiveMask();
        return mask != null && idx >= 0 && idx < mask.length && mask[idx] != 0f;
    }

    private static boolean voiceBasic(Envelope e, int idx) {
        float[] mask = e.voiceBasicMask();
        return mask != null && idx >= 0 && idx < mask.length && mask[idx] != 0f;
    }

    static boolean voiceBasicAt(float[] mid, int idx) {
        if (mid == null || idx < 0 || idx >= mid.length) {
            return false;
        }
        if (mid[idx] < VOICE_FLOOR) {
            return false;
        }
        int from = Math.max(0, idx - VOICE_WINDOW_HOPS);
        int to = Math.min(mid.length, idx + VOICE_WINDOW_HOPS + 1);
        double sum = 0.0;
        double sumSq = 0.0;
        for (int i = from; i < to; i++) {
            sum += mid[i];
            sumSq += (double) mid[i] * mid[i];
        }
        int n = Math.max(1, to - from);
        double mean = sum / n;
        if (mean <= 1e-6) {
            return false;
        }
        double var = Math.max(0.0, sumSq / n - mean * mean);
        return Math.sqrt(var) / mean >= VOICE_MODULATION;
    }

    private static boolean centered(Envelope e, int idx) {
        return e.center == null || idx >= e.center.length
                || e.center[idx] >= CENTER_SALIENCE_MIN;
    }

    static boolean voicePresent(Envelope e, int idx) {
        return voiceBasic(e, idx);
    }

    static float centerSalience(double midPower, double sidePower) {
        double total = midPower + sidePower;
        if (total <= 0.0) {
            return 1.0f;
        }
        return (float) (midPower / total);
    }

    static float[] voiceMask(Envelope e) {

        return e.voiceActiveMask();
    }

    static final double VOICE_FLOOR = 0.08;

    static final double VOICE_MODULATION = 0.25;

    static final int VOICE_WINDOW_HOPS = 20;

    static final double VOICE_OVERLAP_FREE_SECS = 0.5;

    static final double VOICE_OVERLAP_WORST_SECS = 4.0;

    static final double VOICE_COARSE_WEIGHT = 0.7;

    static int barPhase(Envelope e) {
        if (!e.hasBeatGrid()) {
            return -1;
        }
        double periodSecs = e.tempo.periodFrames / (double) e.beatRate;
        if (periodSecs <= 0.0) {
            return -1;
        }

        double[] beats = e.beatsIn(0.0, e.durationSecs);
        if (beats.length < 8) {
            return -1;
        }
        double[] sum = new double[4];
        int[] count = new int[4];
        for (int i = 0; i < beats.length; i++) {
            int idx = e.indexAt(beats[i]);
            if (idx <= 0 || idx >= e.onset.length) {
                continue;
            }
            int phase = i % 4;
            sum[phase] += lowOnsetAt(e, idx);
            count[phase]++;
        }
        int best = -1;
        double bestMean = -1.0;
        double others = 0.0;
        int othersCount = 0;
        for (int p = 0; p < 4; p++) {
            if (count[p] == 0) {
                return -1;
            }
            double mean = sum[p] / count[p];
            if (mean > bestMean) {
                bestMean = mean;
                best = p;
            }
            others += sum[p];
            othersCount += count[p];
        }
        if (best < 0 || othersCount == 0) {
            return -1;
        }
        double rest = (others - sum[best]) / Math.max(1, othersCount - count[best]);
        double contrast = bestMean <= 1e-9 ? 0.0 : (bestMean - rest) / bestMean;
        return contrast >= BAR_CONFIDENCE ? best : -1;
    }

    static final double BAR_CONFIDENCE = 0.12;

    private static double lowOnsetAt(Envelope e, int idx) {
        double best = 0.0;
        for (int i = Math.max(1, idx - 1); i <= Math.min(e.onset.length - 1, idx + 1); i++) {
            double v = e.onset[i];
            if (e.band != null && i < e.band.length) {
                v = Math.max(v, e.band[i][0]);
            }
            best = Math.max(best, v);
        }
        return best;
    }

    static double barTerm(Envelope e, double secs) {
        int phase = barPhase(e);
        if (phase < 0) {
            return 0.0;
        }
        double periodSecs = e.tempo.periodFrames / (double) e.beatRate;
        if (periodSecs <= 0.0) {
            return 0.0;
        }

        double[] first = e.beatsIn(0.0, periodSecs * 2.0);
        if (first.length == 0) {
            return 0.0;
        }
        double beatsFromFirst = (secs - first[0]) / periodSecs;
        long index = Math.round(beatsFromFirst);
        if (Math.abs(beatsFromFirst - index) > 0.5) {
            return 0.0;
        }
        return Math.floorMod(index - phase, 4) == 0 ? 1.0 : 0.0;
    }

    static final double BAR_WEIGHT = 0.05;

    static final double NOVELTY_WEIGHT = 0.02;

    static Double noveltyBonus(Envelope outro, Envelope intro, double cutSecs, double entrySecs) {
        double outBoundary = outro == null ? -1.0 : outro.structureOutroStartSecs;
        double inBoundary = intro == null ? -1.0 : intro.structureIntroStartSecs;
        if (outBoundary < 0.0 && inBoundary < 0.0) {
            return null;
        }
        double sum = 0.0;
        int n = 0;
        if (outBoundary >= 0.0) {
            sum += nearBoundary(cutSecs, outBoundary);
            n++;
        }
        if (inBoundary >= 0.0) {
            sum += nearBoundary(entrySecs, inBoundary);
            n++;
        }
        return Double.valueOf(n == 0 ? 0.0 : sum / n);
    }

    private static double nearBoundary(double secs, double boundary) {
        double d = Math.abs(secs - boundary);
        return d >= NOVELTY_TOLERANCE_SECS ? 0.0 : 1.0 - d / NOVELTY_TOLERANCE_SECS;
    }

    static final double NOVELTY_TOLERANCE_SECS = 8.0;

    static Double barBonus(Envelope outro, Envelope intro, double cutSecs, double entrySecs) {
        int phaseOut = barPhase(outro);
        int phaseIn = barPhase(intro);
        if (phaseOut < 0 && phaseIn < 0) {
            return null;
        }
        double sum = 0.0;
        int n = 0;
        if (phaseOut >= 0) {
            sum += barTerm(outro, cutSecs);
            n++;
        }
        if (phaseIn >= 0) {
            sum += barTerm(intro, entrySecs);
            n++;
        }
        return Double.valueOf(n == 0 ? 0.0 : sum / n);
    }

    private static float[] lowBandColumn(Envelope e) {
        float[] out = new float[e.band.length];
        for (int i = 0; i < e.band.length; i++) {
            out[i] = e.band[i][0];
        }
        return out;
    }

    private static double clip01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double lowEndOverlap(Envelope a, Envelope b, int aStart, int bStart, int hops) {
        if (a.band == null || b.band == null) {
            return 0.5;
        }
        double clash = 0.0;
        double energy = 0.0;
        for (int i = 0; i < hops; i++) {
            int ai = Math.min(aStart + i, a.band.length - 1);
            int bi = Math.min(bStart + i, b.band.length - 1);
            if (ai < 0 || bi < 0) {
                continue;
            }
            double x = a.band[ai][0];
            double y = b.band[bi][0];
            clash += Math.min(x, y);
            energy += Math.max(x, y);
        }
        if (energy <= 1e-6) {
            return 1.0;
        }
        double overlap = clash / energy;

        if (overlap <= LOW_END_FREE_OVERLAP) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0,
                1.0 - (overlap - LOW_END_FREE_OVERLAP) / (1.0 - LOW_END_FREE_OVERLAP)));
    }

    private static double lowEndPhase(Envelope a, Envelope b, int aStart, int bStart, int hops) {
        if (a.wave == null || b.wave == null || a.waveRate <= 0 || b.waveRate <= 0
                || a.waveRate != b.waveRate) {
            return 0.5;
        }
        int rate = a.waveRate;

        int aFrom = (int) Math.round(aStart * HOP_SECS * rate);
        int bFrom = (int) Math.round(bStart * HOP_SECS * rate);
        int span = (int) Math.round(hops * HOP_SECS * rate);
        if (aFrom < 0 || bFrom < 0 || span <= rate / 4
                || aFrom + span >= a.wave.length || bFrom + span >= b.wave.length) {
            return 0.5;
        }
        double alpha = 1.0 - Math.exp(-2.0 * Math.PI * LOW_END_PHASE_HZ / rate);
        double ax = 0.0;
        double ay = 0.0;
        double bx = 0.0;
        double by = 0.0;
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < span; i++) {
            ax += alpha * (a.wave[aFrom + i] - ax);
            ay += alpha * (ax - ay);
            bx += alpha * (b.wave[bFrom + i] - bx);
            by += alpha * (bx - by);
            dot += ay * by;
            na += ay * ay;
            nb += by * by;
        }
        double denom = Math.sqrt(na * nb);
        if (denom <= 1e-12) {
            return 0.5;
        }
        double r = dot / denom;

        return Math.max(0.0, Math.min(1.0, (r + LOW_END_PHASE_FLOOR) / LOW_END_PHASE_FLOOR));
    }

    static final double LOW_END_FREE_OVERLAP = 0.45;

    static final double LOW_END_MUD_SHARE = 0.7;

    static final double LOW_END_PHASE_SHARE = 0.3;

    static final double LOW_END_PHASE_HZ = 120.0;

    static final double LOW_END_PHASE_FLOOR = 0.5;

    static String runnerUpNote(Score best, Score second) {
        if (best == null || second == null) {
            return "";
        }
        String[] names = {"wave overlap", "fade ripple", "band complement", "loudness continuity", "trend", "beat drift", "cut a little less",
                "low-end clash", "vocal overlap"};
        double[] mine = {best.overlap, best.ripple, best.bands, best.level, best.motion, best.beat,
                best.tail, best.lowEnd, best.vocal};
        double[] theirs = {second.overlap, second.ripple, second.bands, second.level,
                second.motion, second.beat, second.tail, second.lowEnd, second.vocal};
        int worst = -1;
        double worstGap = 0.0;
        for (int i = 0; i < names.length; i++) {
            double gap = WEIGHTS[i] / WEIGHT_SUM * (mine[i] - theirs[i]);
            if (gap > worstGap) {
                worstGap = gap;
                worst = i;
            }
        }
        boolean samePoint = Math.abs(best.cutSecs - second.cutSecs) < HOP_SECS
                && Math.abs(best.entrySecs - second.entrySecs) < HOP_SECS;
        boolean tied = best.total - second.total < 1e-4;
        if (samePoint && tied) {

            return String.format(java.util.Locale.ROOT,
                    "the runner-up ties on both point and score (%s, total %s) - the scoring is flat this time and no other point was rejected",
                    fmt(best.cutSecs) + "s/" + fmt(best.entrySecs) + "s", f2(best.total));
        }
        String why = worst < 0 ? "slightly worse on every term"
                : String.format(java.util.Locale.ROOT, "loses on '%s' (%s vs %s)",
                        names[worst], f2(theirs[worst]), f2(mine[worst]));
        return String.format(java.util.Locale.ROOT,
                "runner-up %s/%s (total %s), %s",
                fmt(second.cutSecs) + "s", fmt(second.entrySecs) + "s", f2(second.total), why);
    }

    static final int TOP_K = 4;

    private static void keepRanked(java.util.List<Score> ranked, Score score) {
        for (int i = 0; i < ranked.size(); i++) {
            Score other = ranked.get(i);
            if (Math.abs(other.cutSecs - score.cutSecs) < BEAT_APART_SECS
                    && Math.abs(other.entrySecs - score.entrySecs) < BEAT_APART_SECS) {
                if (better(score, other)) {
                    ranked.set(i, score);
                    ranked.sort(new java.util.Comparator<Score>() {
                        @Override
                        public int compare(Score a, Score b) {
                            return better(a, b) ? -1 : (better(b, a) ? 1 : 0);
                        }
                    });
                }
                return;
            }
        }
        ranked.add(score);
        ranked.sort(new java.util.Comparator<Score>() {
            @Override
            public int compare(Score a, Score b) {
                return better(a, b) ? -1 : (better(b, a) ? 1 : 0);
            }
        });
        while (ranked.size() > TOP_K) {
            ranked.remove(ranked.size() - 1);
        }
    }

    static double driftSecs(double bpmA, double bpmB, double windowSecs) {
        if (bpmA <= 0.0 || bpmB <= 0.0 || windowSecs <= 0.0) {
            return 0.0;
        }

        double periodA = 60.0 / bpmA;
        double periodB = 60.0 / bpmB;
        return windowSecs * Math.abs(1.0 - periodB / periodA);
    }

    static final double BEAT_APART_SECS = 0.25;

    private static boolean better(Score a, Score b) {
        if (a.total > b.total + WINDOW_TIE_EPS) {
            return true;
        }
        if (b.total > a.total + WINDOW_TIE_EPS) {
            return false;
        }

        double da = Math.abs(a.windowSecs - WINDOW_OPTIMUM_SECS);
        double db = Math.abs(b.windowSecs - WINDOW_OPTIMUM_SECS);
        if (da != db) {
            return da < db;
        }

        if (a.total != b.total) {
            return a.total > b.total;
        }
        return a.windowSecs < b.windowSecs;
    }

    static final double WINDOW_TIE_EPS = 0.01;

    private static String f2(double v) {
        return String.format(java.util.Locale.US, "%.2f", Double.valueOf(v));
    }

    public static final double STRUCTURE_SNAP_TOL_SECS = 0.02;

    static final int STRUCTURE_BAR_EVERY = 8;

    static final double STRUCTURE_JUMP_WIN_SECS = 0.5;

    static final double STRUCTURE_LEVEL_HALF_SECS = 1.0;

    static final float STRUCTURE_JUMP_DIFF = 0.05f;

    static final int MIN_STRUCTURE_CANDIDATES = 4;

    static final double STRUCTURE_MAX_GAP_SECS = 40.0;

    static final String SIDE_OUTRO = "exit";
    static final String SIDE_INTRO = "entry";

    public static double[] structureBoundaries(Envelope env, double fromSecs, double toSecs,
                                               String side) {
        if (env == null || env.rms == null || env.rms.length == 0 || !(toSecs > fromSecs)) {
            return EMPTY_TIMES;
        }
        double lo = Math.max(0.0, fromSecs);
        double hi = Math.min(toSecs, env.durationSecs);
        if (!(hi > lo)) {
            return EMPTY_TIMES;
        }

        double[] bars = eightBarLines(env);
        java.util.ArrayList<Double> all = new java.util.ArrayList<Double>();
        for (int i = 0; i < bars.length; i++) {
            if (bars[i] >= lo && bars[i] <= hi) {
                all.add(Double.valueOf(bars[i]));
            }
        }

        double anchor = SIDE_INTRO.equals(side) ? introStartSecs(env) : outroStartSecs(env);
        addBoundary(all, anchor, bars, lo, hi);

        LyricSpans spans = vocalPhraseSpans(env);
        if (spans != null) {
            for (int i = 0; i < spans.starts.length; i++) {
                addBoundary(all, spans.starts[i], bars, lo, hi);
            }
            if (spans.ends != null && spans.ends.length == spans.starts.length) {
                for (int i = 0; i < spans.ends.length; i++) {
                    addBoundary(all, spans.ends[i], bars, lo, hi);
                }
            }
        }

        collectJumps(env, all, bars, lo, hi);
        if (all.isEmpty()) {
            return EMPTY_TIMES;
        }
        java.util.Collections.sort(all);
        double[] out = new double[Math.min(all.size(), MAX_CANDIDATES)];
        int n = 0;
        double last = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < all.size() && n < out.length; i++) {
            double v = all.get(i).doubleValue();
            if (n > 0 && v - last <= STRUCTURE_SNAP_TOL_SECS) {
                continue;
            }
            out[n++] = v;
            last = v;
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    private static double[] eightBarLines(Envelope env) {
        if (!env.hasBeatGrid()) {
            return EMPTY_TIMES;
        }
        double beatSecs = env.tempo.periodFrames / (double) env.beatRate;
        if (!(beatSecs > 0.0)) {
            return EMPTY_TIMES;
        }
        double barSecs = beatSecs * 4.0;
        double span = barSecs * STRUCTURE_BAR_EVERY;
        if (!(span > 0.0) || span > env.durationSecs) {
            return EMPTY_TIMES;
        }

        BarPhase ruler = barPhaseResolved(env, vocalPhraseSpans(env), barSecs);
        double phase = ruler.phase;
        if (Double.isNaN(phase)) {
            return EMPTY_TIMES;
        }

        int bars = (int) Math.floor((env.durationSecs - phase) / barSecs + 1e-6);
        if (bars < 0) {
            return EMPTY_TIMES;
        }
        int count = bars / STRUCTURE_BAR_EVERY + 1;
        double[] out = new double[Math.min(count, MAX_CANDIDATES)];
        int n = 0;
        for (int k = 0; k * STRUCTURE_BAR_EVERY <= bars && n < out.length; k++) {
            out[n++] = phase + (double) k * span;
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    private static void addBoundary(java.util.List<Double> out, double secs, double[] bars,
                                    double lo, double hi) {
        if (Double.isNaN(secs) || secs < lo || secs > hi) {
            return;
        }
        double snapped = secs;
        for (int i = 0; i < bars.length; i++) {
            if (Math.abs(bars[i] - secs) <= STRUCTURE_SNAP_TOL_SECS) {
                snapped = bars[i];
                break;
            }
        }
        out.add(Double.valueOf(snapped));
    }

    private static void collectJumps(Envelope env, java.util.List<Double> out, double[] bars,
                                     double lo, double hi) {
        float[] level = env.rms.clone();
        smoothInPlace(level, Math.max(1, (int) Math.round(STRUCTURE_LEVEL_HALF_SECS / HOP_SECS)));
        int n = level.length;
        int half = Math.max(1, (int) Math.round(STRUCTURE_JUMP_WIN_SECS / HOP_SECS));
        if (n < 2 * half + 1) {
            return;
        }
        double[] prefix = prefixSums(level);
        for (int i = half; i + half <= n && out.size() < MAX_CANDIDATES; ) {
            if (diffAt(prefix, i, half) < STRUCTURE_JUMP_DIFF) {
                i++;
                continue;
            }

            double weight = 0.0;
            double weighted = 0.0;
            while (i + half <= n) {
                double d = diffAt(prefix, i, half);
                if (d < STRUCTURE_JUMP_DIFF) {
                    break;
                }
                weight += d;
                weighted += d * i;
                i++;
            }
            if (weight <= 0.0) {
                continue;
            }
            addBoundary(out, env.secs((int) Math.round(weighted / weight)), bars, lo, hi);
        }
    }

    private static double diffAt(double[] prefix, int i, int half) {
        double before = rangeMean(prefix, i - half, i - 1);
        double after = rangeMean(prefix, i, i + half - 1);
        return Math.abs(after - before);
    }

    static double[] candidates(Envelope env, double fromSecs, double toSecs, String side) {
        double[] structure = structureBoundaries(env, fromSecs, toSecs, side);
        if (structure.length >= MIN_STRUCTURE_CANDIDATES
                && maxGapSecs(structure, env, fromSecs, toSecs) <= STRUCTURE_MAX_GAP_SECS) {
            return structure;
        }

        int first = env.indexAt(fromSecs);
        int last = env.indexAt(toSecs);
        if (last < first) {
            return EMPTY_TIMES;
        }
        int count = Math.min(last - first + 1, MAX_CANDIDATES);
        double[] times = new double[count];
        for (int i = 0; i < count; i++) {
            times[i] = env.secs(first + i);
        }
        return times;
    }

    private static double maxGapSecs(double[] times, Envelope env, double fromSecs, double toSecs) {
        double lo = Math.max(0.0, fromSecs);
        double hi = Math.min(toSecs, env.durationSecs);
        if (!(hi > lo) || times.length == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double best = 0.0;
        double prev = lo;
        for (int i = 0; i < times.length; i++) {
            best = Math.max(best, times[i] - prev);
            prev = times[i];
        }
        return Math.max(best, hi - prev);
    }

    static final double PHASE_ALIGN_MAX_SECS = 0.050;

    static final double PHASE_ALIGN_MIN_WINDOW_SECS = 4.0;

    static double phaseAlignedEntry(Envelope outro, double cutSecs, Envelope intro,
                                    double entrySecs, double windowSecs) {
        if (outro == null || intro == null || outro.wave == null || intro.wave == null
                || outro.waveRate <= 0 || intro.waveRate <= 0) {
            return entrySecs;
        }
        if (!(windowSecs > PHASE_ALIGN_MIN_WINDOW_SECS)) {
            return entrySecs;
        }
        int rate = Math.min(outro.waveRate, intro.waveRate);
        int n = (int) Math.round(windowSecs * rate);
        if (n < rate / 4) {
            return entrySecs;
        }
        int aStart = (int) Math.round(cutSecs * outro.waveRate);
        int bStart = (int) Math.round(entrySecs * intro.waveRate);
        int maxLag = (int) Math.round(PHASE_ALIGN_MAX_SECS * intro.waveRate);
        double best = Double.NEGATIVE_INFINITY;
        int bestLag = 0;
        for (int lag = -maxLag; lag <= maxLag; lag++) {
            double dot = 0.0;
            double na = 0.0;
            double nb = 0.0;
            int used = 0;
            for (int i = 0; i < n; i++) {

                int ai = aStart + i * outro.waveRate / rate;
                int bi = bStart + lag + i * intro.waveRate / rate;
                if (ai < 0 || ai >= outro.wave.length || bi < 0 || bi >= intro.wave.length) {
                    continue;
                }
                double va = outro.wave[ai];
                double vb = intro.wave[bi];
                dot += va * vb;
                na += va * va;
                nb += vb * vb;
                used++;
            }
            if (used < n / 2 || na <= 1e-12 || nb <= 1e-12) {
                continue;
            }
            double corr = dot / Math.sqrt(na * nb);
            if (corr > best) {
                best = corr;
                bestLag = lag;
            }
        }
        if (best == Double.NEGATIVE_INFINITY || bestLag == 0) {
            return entrySecs;
        }
        return entrySecs + bestLag / (double) intro.waveRate;
    }

    private static double snapToBeat(Envelope env, double secs, double low, double high) {
        if (env == null || !env.hasBeatGrid()) {
            return secs;
        }

        double period = env.tempo.periodFrames / (double) Math.max(1, env.beatRate);
        if (!(period > 0.0)) {
            return secs;
        }

        double bestSecs = secs;
        double bestDiff = Double.MAX_VALUE;
        long fromFrame = (long) Math.ceil((secs - period) * env.beatRate);
        long toFrame = (long) Math.floor((secs + period) * env.beatRate);
        if (toFrame > fromFrame) {
            long beat = env.tempo.nextBeatAfter(fromFrame);
            int guard = 0;
            while (beat <= toFrame && guard++ < MAX_CANDIDATES) {
                double candidate = beat / (double) env.beatRate;
                if (candidate >= low && candidate <= high) {
                    double diff = Math.abs(candidate - secs);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestSecs = candidate;
                    }
                }
                beat = env.tempo.nextBeatAfter(beat + 1L);
            }
        }
        return bestSecs;
    }

    private static String alignmentNote(Envelope outro, Envelope intro) {
        boolean o = outro.hasBeatGrid();
        boolean i = intro.hasBeatGrid();
        if (o && i) {
            return String.format(java.util.Locale.US, "beat alignment %.1f/%.1f BPM",
                    Float.valueOf(outro.tempo.bpm), Float.valueOf(intro.tempo.bpm));
        }
        if (i) {
            return String.format(java.util.Locale.US, "entry aligned to the %.1f BPM grid (no beats detected on the exit side)",
                    Float.valueOf(intro.tempo.bpm));
        }
        if (o) {
            return String.format(java.util.Locale.US, "exit aligned to the %.1f BPM grid (no beats detected on the entry side)",
                    Float.valueOf(outro.tempo.bpm));
        }
        return "no beats detected on either side -> falling back to the 20 ms grid";
    }

    private static final double SLOPE_NORM = 6.0;

    private static double slope(Envelope env, int index) {
        int left = Math.max(0, index - 1);
        int right = Math.min(env.rms.length - 1, index + 1);
        if (right == left) {
            return 0.0;
        }
        return (env.rms[right] - env.rms[left]) / (right - left);
    }

    static final double DIRECTION_SPAN_BARS = 1.0;

    static final double DIRECTION_FALLBACK_BAR_SECS = 2.0;

    static final double DIRECTION_NORM = 0.20;

    static final double DIRECTION_DESCRIBE_DEAD_ZONE = 0.15;

    private static double directionSpanSecs(Envelope env) {
        double bar = env != null && env.tempo != null
                && env.tempo.bpm > 40f && env.tempo.bpm < 250f
                ? 4.0 * 60.0 / env.tempo.bpm
                : DIRECTION_FALLBACK_BAR_SECS;
        return DIRECTION_SPAN_BARS * bar;
    }

    private static double direction(Envelope env, int index) {
        int n = Math.max(1, (int) Math.round(directionSpanSecs(env) / HOP_SECS));
        int lo = Math.max(0, index - n);
        int hi = Math.min(env.rms.length - 1, index + n);
        if (hi <= lo) {
            return 0.0;
        }
        double left = 0.0;
        double right = 0.0;
        int ln = 0;
        int rn = 0;
        for (int i = lo; i <= hi; i++) {
            if (i < index) {
                left += env.rms[i];
                ln++;
            } else if (i > index) {
                right += env.rms[i];
                rn++;
            }
        }
        if (ln == 0 || rn == 0) {
            return 0.0;
        }
        left /= ln;
        right /= rn;
        double sum = left + right;
        return sum <= 1e-9 ? 0.0 : (right - left) / sum;
    }

    private static double rising(Envelope env, int index) {
        return clip01(direction(env, index) / DIRECTION_NORM);
    }

    private static double falling(Envelope env, int index) {
        return clip01(-direction(env, index) / DIRECTION_NORM);
    }

    static final double COARSE_FINE_SCALE = WEIGHT_SUM;

    static final double COARSE_ALIGN_SHARE = 0.6;

    static final double COARSE_ALIGN_MIN = STRUCTURE_WEIGHT;

    private static double coarsePointBonus(Envelope env, double pointSecs, double structureSecs,
                                           double structureTarget, double structureScale,
                                           double noveltyBoundary,
                                           double barShare, double noveltyShare) {
        double bonus = 0.0;
        if (structureTarget >= 0.0 && structureScale > 0.0) {
            bonus += STRUCTURE_WEIGHT * structureScale * 0.5
                    * proximity(structureSecs, structureTarget);
        }
        bonus += barShare * barTerm(env, pointSecs);
        if (noveltyBoundary >= 0.0) {
            bonus += noveltyShare * nearBoundary(pointSecs, noveltyBoundary);
        }
        return bonus;
    }

    private static String describe(Envelope env, int index) {
        double d = direction(env, index);
        if (d > DIRECTION_DESCRIBE_DEAD_ZONE * DIRECTION_NORM) {
            return "starting";
        }
        if (d < -DIRECTION_DESCRIBE_DEAD_ZONE * DIRECTION_NORM) {
            return "decaying";
        }
        return "steady";
    }
}
