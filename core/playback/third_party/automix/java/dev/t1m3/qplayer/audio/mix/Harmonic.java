package dev.t1m3.qplayer.audio.mix;

import java.util.Arrays;
import java.util.Locale;

public final class Harmonic {

    public static final int PITCH_CLASSES = 12;

    public static final double WINDOW_SECS = 8.0;

    static final int FRAME_LEN = 2048;

    private static final int HOP = 512;

    private static final float BASE_HZ = 110.0f;

    private static final int OCTAVES = 5;

    public static final float CLARITY_MIN = 0.28f;

    private static final float LOUDNESS_MIN = 0.005f;

    private static final int MIN_FRAMES = 8;

    private static final float SHIFT_PENALTY_PER_SEMITONE = 0.015f;

    public static final float MATCH_MIN_SCORE = 0.72f;

    public static final float MIN_IMPROVEMENT = 0.10f;

    public static final int MAX_SHIFT = 3;

    private static final float PI = (float) Math.PI;

    private Harmonic() {
    }

    public static final class Match {

        public final int semitones;

        public final float plainScore;

        public final float bestScore;

        public final boolean shouldShift;

        public Match(int semitones, float plainScore, float bestScore, boolean shouldShift) {
            this.semitones = semitones;
            this.plainScore = plainScore;
            this.bestScore = bestScore;
            this.shouldShift = shouldShift;
        }

        public String describe() {
            if (shouldShift) {
                String signed = (semitones >= 0 ? "+" : "-") + Math.abs(semitones);
                return String.format(Locale.ROOT, "shifting %s semitones (similarity %.3f <- %.3f)",
                        signed, bestScore, plainScore);
            }
            return String.format(Locale.ROOT, "no shift (similarity %.3f)", plainScore);
        }

        @Override
        public String toString() {
            return "Match { semitones: " + semitones
                    + ", plain_score: " + plainScore
                    + ", best_score: " + bestScore
                    + ", should_shift: " + shouldShift + " }";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Match)) {
                return false;
            }
            Match m = (Match) o;
            return semitones == m.semitones
                    && Float.compare(plainScore, m.plainScore) == 0
                    && Float.compare(bestScore, m.bestScore) == 0
                    && shouldShift == m.shouldShift;
        }

        @Override
        public int hashCode() {
            int h = semitones;
            h = 31 * h + Float.floatToIntBits(plainScore);
            h = 31 * h + Float.floatToIntBits(bestScore);
            h = 31 * h + (shouldShift ? 1 : 0);
            return h;
        }
    }

    public static float[] tailWindow(float[] pcm, int rate) {
        int want = (int) ((double) rate * WINDOW_SECS);
        if (pcm.length <= want) {
            return pcm;
        }
        return Arrays.copyOfRange(pcm, pcm.length - want, pcm.length);
    }

    public static float[] headWindow(float[] pcm, int rate) {
        int want = (int) ((double) rate * WINDOW_SECS);
        return Arrays.copyOfRange(pcm, 0, Math.min(pcm.length, want));
    }

    public static Match match(float[] outro, int outroRate, float[] intro, int introRate) {
        float[] a = weightedChroma(tailWindow(outro, outroRate), outroRate);
        if (a == null) {
            return null;
        }
        float[] b = weightedChroma(headWindow(intro, introRate), introRate);
        if (b == null) {
            return null;
        }

        float plain = cosine(a, b);

        int bestShift = 0;
        float bestRaw = plain;
        float bestPenalized = plain;

        for (int shift = -MAX_SHIFT; shift <= MAX_SHIFT; shift++) {
            if (shift == 0) {
                continue;
            }
            float raw = cosine(rotate(a, shift), b);

            float penalized = raw - SHIFT_PENALTY_PER_SEMITONE * (float) Math.abs(shift);
            if (penalized > bestPenalized) {
                bestPenalized = penalized;
                bestRaw = raw;
                bestShift = shift;
            }
        }

        boolean shouldShift = bestShift != 0
                && bestRaw >= MATCH_MIN_SCORE
                && (bestRaw - plain) >= MIN_IMPROVEMENT;

        if (shouldShift) {
            return new Match(bestShift, plain, bestRaw, true);
        }
        return new Match(0, plain, plain, false);
    }

    public static float[] weightedChroma(float[] pcm, int rate) {
        PrepareStage.mark("determining key (per-frame chroma)");
        if (rate == 0 || pcm.length < FRAME_LEN) {
            return null;
        }

        float[] acc = new float[PITCH_CLASSES];
        float weightSum = 0.0f;
        int used = 0;

        int frames = 1 + (pcm.length - FRAME_LEN) / HOP;
        float[][] chromaOfFrame = new float[frames][];
        float[] clarityOfFrame = new float[frames];
        float[] loudnessOfFrame = new float[frames];
        computeFrames(pcm, rate, frames, chromaOfFrame, clarityOfFrame, loudnessOfFrame);

        for (int f = 0; f < frames; f++) {
            float[] chroma = chromaOfFrame[f];
            if (chroma == null) {
                continue;
            }
            float clarity = clarityOfFrame[f];
            float loudness = loudnessOfFrame[f];

            if (clarity >= CLARITY_MIN && loudness > LOUDNESS_MIN) {
                float w = clarity * loudness;
                for (int i = 0; i < PITCH_CLASSES; i++) {
                    acc[i] += chroma[i] * w;
                }
                weightSum += w;
                used++;
            }
        }

        if (used < MIN_FRAMES || weightSum <= 0.0f) {
            return null;
        }

        normalize(acc);
        return acc;
    }

    public static float[] rotate(float[] chroma, int semitones) {
        float[] out = new float[PITCH_CLASSES];
        for (int i = 0; i < PITCH_CLASSES; i++) {
            int j = Math.floorMod(i + semitones, PITCH_CLASSES);
            out[j] = chroma[i];
        }
        return out;
    }

    private static float cosine(float[] a, float[] b) {
        float dot = 0.0f;
        for (int i = 0; i < PITCH_CLASSES; i++) {
            dot += a[i] * b[i];
        }
        float na = 0.0f;
        for (int i = 0; i < PITCH_CLASSES; i++) {
            na += a[i] * a[i];
        }
        na = (float) Math.sqrt((double) na);
        float nb = 0.0f;
        for (int i = 0; i < PITCH_CLASSES; i++) {
            nb += b[i] * b[i];
        }
        nb = (float) Math.sqrt((double) nb);
        if (na <= 0.0f || nb <= 0.0f) {
            return 0.0f;
        }
        return clamp(dot / (na * nb), -1.0f, 1.0f);
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    private static void normalize(float[] v) {
        float n = 0.0f;
        for (int i = 0; i < PITCH_CLASSES; i++) {
            n += v[i] * v[i];
        }
        n = (float) Math.sqrt((double) n);
        if (n > 0.0f) {
            for (int i = 0; i < PITCH_CLASSES; i++) {
                v[i] /= n;
            }
        }
    }

    static float[] frameChroma(float[] frame, int rate) {
        ChromaPlan plan = planFor(rate, frame.length);
        float[] powers = new float[plan.distinct];
        return frameChromaAt(frame, 0, plan, powers);
    }

    static final class ChromaPlan {

        final int frameLen;

        final int[] pitchClass;

        final int[] slot;

        final float[] coeff;

        final int distinct;

        ChromaPlan(int frameLen, int[] pitchClass, int[] slot, float[] coeff, int distinct) {
            this.frameLen = frameLen;
            this.pitchClass = pitchClass;
            this.slot = slot;
            this.coeff = coeff;
            this.distinct = distinct;
        }
    }

    // Android host: do not retain a plan for every sample rate encountered over a long session.
    private static final java.util.Map<Long, ChromaPlan> PLANS =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<Long, ChromaPlan>(9, 0.75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, ChromaPlan> entry) {
                    return size() > 8;
                }
            });

    static ChromaPlan planFor(int rate, int frameLen) {
        long key = ((long) rate << 32) | (frameLen & 0xffffffffL);
        ChromaPlan cached = PLANS.get(Long.valueOf(key));
        if (cached != null) {
            return cached;
        }
        int cap = PITCH_CLASSES * OCTAVES;
        int[] pcOf = new int[cap];
        int[] slotOf = new int[cap];
        int[] kOfSlot = new int[cap];
        int[] slotTmp = new int[cap];
        int[] pcTmp = new int[cap];
        int[] kTmp = new int[cap];
        int n = 0;
        int distinct = 0;
        float nf = (float) frameLen;
        float ceiling = (float) rate * 0.45f;
        float base = BASE_HZ;
        for (int oct = 0; oct < OCTAVES; oct++) {
            if (base >= ceiling) {
                break;
            }
            for (int pc = 0; pc < PITCH_CLASSES; pc++) {

                float f = base * (float) Math.pow(2.0, (double) ((float) pc / 12.0f));
                if (f >= ceiling) {
                    continue;
                }

                int ki = (int) ((float) Math.floor(0.5f + nf * f / (float) rate));
                int slot = -1;
                for (int i = 0; i < distinct; i++) {
                    if (kOfSlot[i] == ki) {
                        slot = i;
                        break;
                    }
                }
                if (slot < 0) {
                    slot = distinct;
                    kOfSlot[distinct] = ki;
                    distinct++;
                }
                pcOf[n] = pc;
                slotOf[n] = slot;
                slotTmp[n] = slot;
                pcTmp[n] = pc;
                kTmp[n] = ki;
                n++;
            }
            base *= 2.0f;
        }
        float[] coeff = new float[distinct];
        for (int i = 0; i < distinct; i++) {
            float w = 2.0f * PI * (float) kOfSlot[i] / nf;
            coeff[i] = 2.0f * (float) Math.cos((double) w);
        }
        ChromaPlan plan = new ChromaPlan(frameLen, java.util.Arrays.copyOf(pcOf, n),
                java.util.Arrays.copyOf(slotOf, n), coeff, distinct);

        if (PLANS.size() > 64) {
            PLANS.clear();
        }
        PLANS.put(Long.valueOf(key), plan);
        return plan;
    }

    private static void goertzelBatch(float[] pcm, int offset, int len, float[] coeffs,
                                      int from, int count, float[] powers) {
        if (count <= 0 || len <= 0) {
            return;
        }

        int s = from;
        int end = from + count;
        for (; s + 4 <= end; s += 4) {
            float c0 = coeffs[s];
            float c1 = coeffs[s + 1];
            float c2 = coeffs[s + 2];
            float c3 = coeffs[s + 3];
            float a1 = 0.0f;
            float a2 = 0.0f;
            float b1 = 0.0f;
            float b2 = 0.0f;
            float d1 = 0.0f;
            float d2 = 0.0f;
            float e1 = 0.0f;
            float e2 = 0.0f;
            for (int i = 0; i < len; i++) {
                float x = pcm[offset + i];
                float a0 = x + c0 * a1 - a2;
                a2 = a1;
                a1 = a0;
                float b0 = x + c1 * b1 - b2;
                b2 = b1;
                b1 = b0;
                float d0 = x + c2 * d1 - d2;
                d2 = d1;
                d1 = d0;
                float e0 = x + c3 * e1 - e2;
                e2 = e1;
                e1 = e0;
            }
            powers[s] = goertzelPower(a1, a2, c0);
            powers[s + 1] = goertzelPower(b1, b2, c1);
            powers[s + 2] = goertzelPower(d1, d2, c2);
            powers[s + 3] = goertzelPower(e1, e2, c3);
        }
        for (; s < end; s++) {
            powers[s] = goertzelCoeff(pcm, offset, len, coeffs[s]);
        }
    }

    private static float goertzelPower(float s1, float s2, float coeff) {
        float power = s1 * s1 + s2 * s2 - coeff * s1 * s2;
        return (power > 0.0f) ? power : 0.0f;
    }

    private static float[] frameChromaAt(float[] pcm, int offset, ChromaPlan plan,
                                         float[] powers) {
        goertzelBatch(pcm, offset, plan.frameLen, plan.coeff, 0, plan.distinct, powers);
        float[] chroma = new float[PITCH_CLASSES];
        for (int e = 0; e < plan.pitchClass.length; e++) {

            chroma[plan.pitchClass[e]] += powers[plan.slot[e]];
        }
        return chroma;
    }

    private static float goertzel(float[] samples, int rate, float freq) {
        float n = (float) samples.length;
        if (n <= 0.0f) {
            return 0.0f;
        }

        float k = (float) Math.floor(0.5f + n * freq / (float) rate);
        float w = 2.0f * PI * k / n;
        float coeff = 2.0f * (float) Math.cos((double) w);
        return goertzelCoeffRaw(samples, 0, samples.length, coeff);
    }

    private static float goertzelCoeff(float[] pcm, int offset, int len, float coeff) {
        return goertzelCoeffRaw(pcm, offset, len, coeff);
    }

    private static float goertzelCoeffRaw(float[] samples, int offset, int len, float coeff) {
        if (len <= 0) {
            return 0.0f;
        }
        float s1 = 0.0f;
        float s2 = 0.0f;
        for (int i = 0; i < len; i++) {
            float x = samples[offset + i];
            float s0 = x + coeff * s1 - s2;
            s2 = s1;
            s1 = s0;
        }

        float power = s1 * s1 + s2 * s2 - coeff * s1 * s2;
        return (power > 0.0f) ? power : 0.0f;
    }

    private static void computeFrames(float[] pcm, int rate, int frames,
                                      float[][] chromaOut, float[] clarityOut,
                                      float[] loudnessOut) {
        ChromaPlan plan = planFor(rate, FRAME_LEN);
        int cores = Runtime.getRuntime().availableProcessors();
        int threads = Math.max(1, Math.min(CHROMA_THREADS_MAX, cores - 1));
        if (threads <= 1 || frames < CHROMA_MIN_FRAMES_TO_SPLIT) {
            float[] powers = new float[plan.distinct];
            float[] frame = new float[FRAME_LEN];
            for (int f = 0; f < frames; f++) {
                dev.t1m3.qplayer.audio.decode.Decoder.checkActive();
                computeFrame(pcm, f, plan, powers, frame, chromaOut, clarityOut, loudnessOut);
            }
            return;
        }

        int chunk = (frames + threads - 1) / threads;
        java.util.List<java.util.concurrent.Future<?>> tasks =
                new java.util.ArrayList<java.util.concurrent.Future<?>>(threads);
        for (int t = 0; t < threads; t++) {
            final int from = t * chunk;
            final int to = Math.min(frames, from + chunk);
            if (from >= to) {
                break;
            }
            tasks.add(CHROMA_POOL.submit(new Runnable() {
                @Override
                public void run() {

                    float[] powers = new float[plan.distinct];
                    float[] frame = new float[FRAME_LEN];
                    for (int f = from; f < to; f++) {
                        computeFrame(pcm, f, plan, powers, frame, chromaOut, clarityOut,
                                loudnessOut);
                    }
                }
            }));
        }
        for (java.util.concurrent.Future<?> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                throw new IllegalStateException("key analysis was interrupted; refusing to hand over a half-finished chroma");
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            }
        }
    }

    private static final int CHROMA_THREADS_MAX = 1;

    private static final int CHROMA_MIN_FRAMES_TO_SPLIT = 16;

    private static final java.util.concurrent.ExecutorService CHROMA_POOL =
            java.util.concurrent.Executors.newFixedThreadPool(
                    Math.max(1, Math.min(CHROMA_THREADS_MAX,
                            Runtime.getRuntime().availableProcessors() - 1)),
                    new java.util.concurrent.ThreadFactory() {
                        private final java.util.concurrent.atomic.AtomicInteger n =
                                new java.util.concurrent.atomic.AtomicInteger();
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread th = new Thread(r, "qplayer-chroma-" + n.getAndIncrement());
                            th.setDaemon(true);
                            return th;
                        }
                    });

    private static void computeFrame(float[] pcm, int f, ChromaPlan plan, float[] powers,
                                     float[] frame, float[][] chromaOut, float[] clarityOut,
                                     float[] loudnessOut) {
        int off = f * HOP;
        System.arraycopy(pcm, off, frame, 0, FRAME_LEN);
        float[] chroma = frameChromaAt(frame, 0, plan, powers);
        chromaOut[f] = chroma;
        clarityOut[f] = clarityOf(chroma);
        loudnessOut[f] = rms(frame);
    }

    static float clarityOf(float[] chroma) {
        float sum = 0.0f;
        for (int i = 0; i < PITCH_CLASSES; i++) {
            sum += chroma[i];
        }

        if (!(sum > 0.0f)) {
            return 0.0f;
        }

        float max = -Float.MAX_VALUE;
        for (int i = 0; i < PITCH_CLASSES; i++) {
            if (chroma[i] > max) {
                max = chroma[i];
            }
        }
        return clamp(max / sum, 0.0f, 1.0f);
    }

    static float rms(float[] frame) {
        if (frame.length == 0) {
            return 0.0f;
        }
        float sum = 0.0f;
        for (int i = 0; i < frame.length; i++) {
            float x = frame[i];
            sum += x * x;
        }
        return (float) Math.sqrt((double) (sum / (float) frame.length));
    }
}
