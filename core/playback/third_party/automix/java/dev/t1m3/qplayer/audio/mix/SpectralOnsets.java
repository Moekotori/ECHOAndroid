package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.dsp.Fft;

public final class SpectralOnsets {

    public static final int FRAME = 2048;

    public static final int HOP = 512;

    public static final float ATTRIBUTION_BINS = (FRAME - HOP / 2.0f) / HOP;

    public static final int PITCH_CLASSES = 12;

    static final float CHROMA_MIN_HZ = 55.0f;
    static final float CHROMA_MAX_HZ = 2000.0f;

    static final int BANDS_PER_OCTAVE = 24;

    static final float F_MIN = 30.0f;

    static final float F_MAX_NYQUIST_RATIO = 0.45f;

    static final float GAMMA = 1000.0f;

    static final int MAXFILTER_BANDS = 1;

    static final double PEAK_HALF_LIFE_SECS = 0.4;

    static final double WHITEN_FLOOR = 0.08;

    static final double FLAT_BAND_WEIGHT = 1.0;

    private SpectralOnsets() {
    }

    public static float[] envelope(float[] pcm, int rate) {
        if (pcm == null || rate <= 0 || pcm.length < FRAME) {
            return null;
        }
        int frames = 1 + (pcm.length - FRAME) / HOP;
        if (frames < 2) {
            return null;
        }
        BandMap map = BandMap.of(rate, FRAME);
        if (map == null || map.count == 0) {
            return null;
        }
        int bands = map.count;
        Fft fft = new Fft(FRAME);
        float[] window = new float[FRAME];
        Fft.hann(window);
        float[] curMax = new float[bands];
        float[] prevWhite = new float[bands];
        float[] white = new float[bands];
        float[] mu = new float[bands];
        float[] onset = new float[frames];
        double decay = Math.pow(0.5, (double) HOP / (PEAK_HALF_LIFE_SECS * rate));
        boolean first = true;

        int threads = Math.max(1, Math.min(ONSET_THREADS_MAX,
                Runtime.getRuntime().availableProcessors() - 1));

        final int batch = 32;
        float[][] batchCurMax = new float[batch][];
        for (int from = 0; from < frames; from += batch) {
            final int batchFrom = from;
            final int batchTo = Math.min(frames, from + batch);
            for (int i = 0; i < batchCurMax.length; i++) {
                if (i >= batchTo - batchFrom) {
                    break;
                }
                if (batchCurMax[i] == null) {
                    batchCurMax[i] = new float[bands];
                }
            }
            int count = batchTo - batchFrom;
            if (threads > 1 && count >= ONSET_MIN_FRAMES_TO_SPLIT) {
                int per = (count + threads - 1) / threads;
                java.util.List<java.util.concurrent.Future<?>> tasks =
                        new java.util.ArrayList<java.util.concurrent.Future<?>>(threads);
                for (int t = 0; t < threads; t++) {
                    final int partFrom = batchFrom + t * per;
                    final int partTo = Math.min(batchTo, partFrom + per);
                    if (partFrom >= partTo) {
                        break;
                    }
                    tasks.add(ONSET_POOL.submit(new Runnable() {
                        @Override
                        public void run() {
                            for (int f = partFrom; f < partTo; f++) {
                                frameBandsInto(pcm, f, fft, window, map, bands,
                                        batchCurMax[f - batchFrom]);
                            }
                        }
                    }));
                }
                for (java.util.concurrent.Future<?> task : tasks) {
                    try {
                        task.get();
                    } catch (InterruptedException e) {

                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "onset analysis was interrupted; refusing to hand over a half-finished envelope");
                    } catch (java.util.concurrent.ExecutionException e) {
                        throw new IllegalStateException(e.getCause());
                    }
                    }
            } else {
                for (int f = batchFrom; f < batchTo; f++) {
                    frameBandsInto(pcm, f, fft, window, map, bands,
                            batchCurMax[f - batchFrom]);
                }
            }

            for (int f = batchFrom; f < batchTo; f++) {
            curMax = batchCurMax[f - batchFrom];
            if (!first) {
                float acc = 0.0f;
                for (int b = 0; b < bands; b++) {

                    double denom = Math.max(mu[b], WHITEN_FLOOR);
                    white[b] = (float) (curMax[b] / denom);
                    double flux = white[b] - prevWhite[b];
                    if (flux > 0.0) {
                        acc += flux;
                    }
                    mu[b] = (float) Math.max(mu[b] * decay, curMax[b]);
                }
                onset[f] = acc;
            } else {
                for (int b = 0; b < bands; b++) {
                    white[b] = 1.0f;
                    mu[b] = Math.max(mu[b], curMax[b]);
                }
                first = false;
            }
            System.arraycopy(white, 0, prevWhite, 0, bands);
            }
        }
        if (frames < 3) {
            return null;
        }

        double total = 0.0;
        for (float v : onset) {
            total += v;
        }
        float mean = (float) (total / frames);
        for (int i = 0; i < frames; i++) {
            onset[i] -= mean;
        }
        return onset;
    }

    public static final class Stream {

        private final BandMap map;
        private final Fft fft;
        private final float[] window;
        private final float[] re;
        private final float[] im;
        private final float[] mag;
        private final float[] cur;
        private final float[] curMax;
        private final float[] prevWhite;
        private final float[] white;
        private final float[] mu;
        private final float[] ring;

        private int ringAt;
        private final int frame;
        private final int hop;
        private final double decay;

        private float[] valuesBuf = new float[4096];
        private int valueCount;

        private static final int BATCH_FRAMES = 32;

        private final float[][] batchSamples = new float[BATCH_FRAMES][];
        private final float[][] batchCurMax = new float[BATCH_FRAMES][];
        private final float[] batchChroma = new float[BATCH_FRAMES * PITCH_CLASSES];
        private final float[] batchLevel = new float[BATCH_FRAMES];
        private int batchCount;

        private final int[] binClass;

        private float[] chroma = new float[1024 * PITCH_CLASSES];
        private int chromaFrames;

        private float[] level = new float[1024];
        private long n;
        private boolean first = true;

        public Stream(int rate) {
            int r = Math.max(1, rate);

            double want = (double) FRAME * r / 48000.0;
            int levels = (int) Math.round(Math.log(Math.max(2.0, want)) / Math.log(2.0));
            this.frame = Math.max(256, 1 << levels);
            this.hop = Math.max(1, frame / (FRAME / HOP));
            this.map = BandMap.of(r, frame);
            if (map == null) {
                throw new IllegalArgumentException("at sample rate " + r + " no logarithmic bands can be resolved");
            }
            this.fft = new Fft(frame);
            this.window = new float[frame];
            Fft.hann(window);
            this.re = new float[frame];
            this.im = new float[frame];
            this.mag = new float[frame / 2 + 1];
            this.cur = new float[map.count];
            this.curMax = new float[map.count];
            this.prevWhite = new float[map.count];
            this.white = new float[map.count];
            this.mu = new float[map.count];
            this.ring = new float[frame];
            this.decay = Math.pow(0.5, (double) hop / (PEAK_HALF_LIFE_SECS * r));

            this.binClass = new int[mag.length];
            for (int k = 1; k < binClass.length; k++) {
                double hz = (double) k * r / frame;
                if (hz < CHROMA_MIN_HZ || hz > CHROMA_MAX_HZ) {
                    binClass[k] = -1;
                    continue;
                }
                double semis = 12.0 * Math.log(hz / 440.0) / Math.log(2.0);
                int pc = (int) Math.round(semis) % PITCH_CLASSES;
                binClass[k] = pc < 0 ? pc + PITCH_CLASSES : pc;
            }
        }

        public void push(float sample) {
            ring[ringAt] = sample;
            ringAt++;
            if (ringAt == frame) {
                ringAt = 0;
            }
            n++;
            if (n >= frame && (n - frame) % hop == 0) {
                emit();
            }
        }

        private void emit() {

            int slot = batchCount;
            float[] samples = batchSamples[slot];
            if (samples == null) {
                samples = new float[frame];
                batchSamples[slot] = samples;
            }
            int oldest = ringAt;
            int tail = frame - oldest;
            System.arraycopy(ring, oldest, samples, 0, tail);
            System.arraycopy(ring, 0, samples, tail, oldest);
            batchCount++;
            if (batchCount == BATCH_FRAMES) {
                processBatch();
            }
        }

        private void processBatch() {
            int n = batchCount;
            if (n == 0) {
                return;
            }
            int threads = Math.max(1, Math.min(ONSET_THREADS_MAX,
                    Runtime.getRuntime().availableProcessors() - 1));
            if (threads > 1 && n >= ONSET_MIN_FRAMES_TO_SPLIT) {
                int per = (n + threads - 1) / threads;
                java.util.List<java.util.concurrent.Future<?>> tasks =
                        new java.util.ArrayList<java.util.concurrent.Future<?>>(threads);
                for (int t = 0; t < threads; t++) {
                    final int from = t * per;
                    final int to = Math.min(n, from + per);
                    if (from >= to) {
                        break;
                    }
                    tasks.add(ONSET_POOL.submit(new Runnable() {
                        @Override
                        public void run() {
                            for (int i = from; i < to; i++) {
                                analyzeFrame(i);
                            }
                        }
                    }));
                }
                for (java.util.concurrent.Future<?> task : tasks) {
                    try {
                        task.get();
                    } catch (InterruptedException e) {

                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "onset analysis was interrupted; refusing to hand over a half-finished envelope");
                    } catch (java.util.concurrent.ExecutionException e) {
                        throw new IllegalStateException(e.getCause());
                    }
                    }
            } else {
                for (int i = 0; i < n; i++) {
                    analyzeFrame(i);
                }
            }
            for (int i = 0; i < n; i++) {
                fold(i);
            }
            batchCount = 0;
        }

        private void analyzeFrame(int i) {
            FrameScratch sc = FRAME_SCRATCH.get();
            sc.ensure(frame, map.count, mag.length);
            float[] samples = batchSamples[i];
            float[] re = sc.re;
            float[] im = sc.im;
            float[] mag = sc.mag;
            for (int k = 0; k < frame; k++) {
                re[k] = samples[k] * window[k];
                im[k] = 0.0f;
            }
            fft.forward(re, im);
            fft.magnitude(re, im, mag);

            int base = i * PITCH_CLASSES;
            for (int c = 0; c < PITCH_CLASSES; c++) {
                batchChroma[base + c] = 0.0f;
            }
            double sum = 0.0;
            for (int k = 1; k < binClass.length; k++) {
                int pc = binClass[k];
                if (pc < 0) {
                    continue;
                }
                batchChroma[base + pc] += mag[k];
                sum += mag[k];
            }
            if (sum > 1e-9) {
                float inv = (float) (1.0 / sum);
                for (int c = 0; c < PITCH_CLASSES; c++) {
                    batchChroma[base + c] *= inv;
                }
            }
            batchLevel[i] = (float) sum;
            map.apply(mag, sc.cur);
            for (int b = 0; b < map.count; b++) {
                sc.cur[b] = (float) Math.log1p(GAMMA * sc.cur[b]);
            }
            maxFilter(sc.cur, sc.curMax);
            float[] slotMax = batchCurMax[i];
            if (slotMax == null) {
                slotMax = new float[map.count];
                batchCurMax[i] = slotMax;
            }
            System.arraycopy(sc.curMax, 0, slotMax, 0, map.count);
        }

        private void fold(int i) {
            float[] curMax = batchCurMax[i];
            if (!first) {
                float acc = 0.0f;
                for (int b = 0; b < map.count; b++) {
                    double denom = Math.max(mu[b], WHITEN_FLOOR);
                    white[b] = (float) (curMax[b] / denom);
                    double flux = white[b] - prevWhite[b];
                    if (flux > 0.0) {
                        acc += flux;
                    }
                    mu[b] = (float) Math.max(mu[b] * decay, curMax[b]);
                }
                appendValue(acc);
            } else {
                for (int b = 0; b < map.count; b++) {
                    white[b] = 1.0f;
                    mu[b] = Math.max(mu[b], curMax[b]);
                }
                first = false;
                appendValue(0.0f);
            }
            System.arraycopy(white, 0, prevWhite, 0, map.count);

            if (chromaFrames * PITCH_CLASSES + PITCH_CLASSES > chroma.length) {
                float[] grown = new float[chroma.length * 2];
                System.arraycopy(chroma, 0, grown, 0, chroma.length);
                chroma = grown;
            }
            if (chromaFrames + 1 > level.length) {
                float[] grown = new float[level.length * 2];
                System.arraycopy(level, 0, grown, 0, level.length);
                level = grown;
            }
            System.arraycopy(batchChroma, i * PITCH_CLASSES, chroma,
                    chromaFrames * PITCH_CLASSES, PITCH_CLASSES);
            level[chromaFrames] = batchLevel[i];
            chromaFrames++;
        }

        public void flush() {
            if (batchCount > 0) {
                processBatch();
            }
        }

        public int chromaFrameCount() {
            flush();
            return chromaFrames;
        }

        public float[] levelValues() {
            flush();
            float[] out = new float[chromaFrames];
            System.arraycopy(level, 0, out, 0, out.length);
            return out;
        }

        public float[] chromaValues() {
            flush();
            float[] out = new float[chromaFrames * PITCH_CLASSES];
            System.arraycopy(chroma, 0, out, 0, out.length);
            return out;
        }

        public int hop() {
            return hop;
        }

        public float[] values() {
            flush();
            int size = valueCount;
            float[] out = new float[size];

            double total = 0.0;
            for (int i = 0; i < size; i++) {
                out[i] = valuesBuf[i];
                total += out[i];
            }
            float mean = size == 0 ? 0f : (float) (total / size);
            for (int i = 0; i < size; i++) {
                out[i] -= mean;
            }
            return out;
        }

        private void appendValue(float v) {
            if (valueCount == valuesBuf.length) {
                valuesBuf = java.util.Arrays.copyOf(valuesBuf, valuesBuf.length * 2);
            }
            valuesBuf[valueCount++] = v;
        }
    }

    private static final int ONSET_THREADS_MAX = 1;

    private static final int ONSET_MIN_FRAMES_TO_SPLIT = 4;

    private static final java.util.concurrent.ExecutorService ONSET_POOL =
            java.util.concurrent.Executors.newFixedThreadPool(
                    Math.max(1, Math.min(ONSET_THREADS_MAX,
                            Runtime.getRuntime().availableProcessors() - 1)),
                    new java.util.concurrent.ThreadFactory() {
                        private final java.util.concurrent.atomic.AtomicInteger n =
                                new java.util.concurrent.atomic.AtomicInteger();
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread th = new Thread(r, "qplayer-onset-" + n.getAndIncrement());
                            th.setDaemon(true);
                            return th;
                        }
                    });

    private static void frameBandsInto(float[] pcm, int f, Fft fft, float[] window,
                                       BandMap map, int bands, float[] out) {
        dev.t1m3.qplayer.audio.decode.Decoder.checkActive();
        FrameScratch sc = FRAME_SCRATCH.get();
        sc.ensure(FRAME, bands, FRAME / 2 + 1);
        float[] re = sc.re;
        float[] im = sc.im;
        float[] mag = sc.mag;
        float[] cur = sc.cur;
        System.arraycopy(pcm, f * HOP, re, 0, FRAME);
        for (int i = 0; i < FRAME; i++) {
            re[i] *= window[i];
            im[i] = 0.0f;
        }
        fft.forward(re, im);
        fft.magnitude(re, im, mag);
        map.apply(mag, cur);
        for (int b = 0; b < bands; b++) {
            cur[b] = (float) Math.log1p(GAMMA * cur[b]);
        }
        maxFilter(cur, out);
    }

    static final class FrameScratch {
        float[] re = new float[0];
        float[] im = new float[0];
        float[] mag = new float[0];
        float[] cur = new float[0];
        float[] curMax = new float[0];
        private int frame;
        private int bands;
        private int magLen;

        void ensure(int frame, int bands, int magLen) {
            if (this.frame == frame && this.bands == bands && this.magLen == magLen) {
                return;
            }
            this.frame = frame;
            this.bands = bands;
            this.magLen = magLen;
            re = new float[frame];
            im = new float[frame];
            mag = new float[magLen];
            cur = new float[bands];
            curMax = new float[bands];
        }
    }

    private static final ThreadLocal<FrameScratch> FRAME_SCRATCH =
            new ThreadLocal<FrameScratch>() {
                @Override
                protected FrameScratch initialValue() {
                    return new FrameScratch();
                }
            };

    private static void maxFilter(float[] in, float[] out) {
        int n = in.length;
        for (int b = 0; b < n; b++) {
            float best = in[b];
            for (int d = 1; d <= MAXFILTER_BANDS; d++) {
                if (b - d >= 0 && in[b - d] > best) {
                    best = in[b - d];
                }
                if (b + d < n && in[b + d] > best) {
                    best = in[b + d];
                }
            }
            out[b] = best;
        }
    }

    static final class BandMap {

        final int count;

        private final int[] binStart;
        private final int[] binEnd;
        private final float[] weights;

        private BandMap(int count, int[] binStart, int[] binEnd, float[] weights) {
            this.count = count;
            this.binStart = binStart;
            this.binEnd = binEnd;
            this.weights = weights;
        }

        static BandMap of(int rate, int frame) {
            if (rate <= 0 || frame <= 0) {
                return null;
            }
            double nyquist = rate / 2.0;
            double fMax = Math.min(nyquist * F_MAX_NYQUIST_RATIO, 0.98 * nyquist);
            double binHz = rate / (double) frame;
            int bands = 0;
            for (double f = F_MIN; f < fMax; f *= Math.pow(2.0, 1.0 / BANDS_PER_OCTAVE)) {
                bands++;
            }
            if (bands <= 0) {
                return null;
            }
            int[] start = new int[bands];
            int[] end = new int[bands];
            float[] weights = new float[bands];
            double ratio = Math.pow(2.0, 1.0 / BANDS_PER_OCTAVE);
            for (int b = 0; b < bands; b++) {
                double centre = F_MIN * Math.pow(ratio, b);
                double lower = centre / ratio;
                double upper = centre * ratio;
                int k0 = Math.max(1, (int) Math.ceil(lower / binHz));
                int k1 = Math.min(frame / 2, (int) Math.floor(upper / binHz));
                if (k1 < k0) {
                    k0 = k1 = Math.max(1, Math.min(frame / 2, (int) Math.round(centre / binHz)));
                }
                start[b] = k0;
                end[b] = k1;

                double sum = 0.0;
                for (int k = k0; k <= k1; k++) {
                    sum += 1.0;
                }
                weights[b] = (float) (1.0 / Math.max(1.0, sum));
            }
            return new BandMap(bands, start, end, weights);
        }

        void apply(float[] mag, float[] out) {
            for (int b = 0; b < count; b++) {
                double sum = 0.0;
                for (int k = binStart[b]; k <= binEnd[b]; k++) {
                    sum += mag[k];
                }
                out[b] = (float) (sum * weights[b]);
            }
        }
    }
}
