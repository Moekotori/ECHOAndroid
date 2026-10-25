package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.dsp.Fft;

import java.util.Arrays;
import java.util.Locale;

public final class Analysis {

    static final int FRAME = 2048;

    static final int HOP = 512;

    public static final float MIN_BPM = 60.0f;

    public static final float MAX_BPM = 200.0f;

    private static final float PRIOR_CENTER_BPM = 120.0f;

    private static final float PRIOR_SIGMA = 0.5f;

    private static final float MIN_CONFIDENCE = 0.15f;

    private static final float MIN_AC_RATIO = 0.15f;

    static final float ENV_CENTER_OFFSET = ((float) FRAME - (float) HOP * 0.5f) / (float) HOP;

    private Analysis() {
    }

    public static final class Onsets {

        public final float[] envelope;

        public final int hop;

        public final int frame;

        public Onsets(float[] envelope, int hop, int frame) {
            this.envelope = envelope;
            this.hop = hop;
            this.frame = frame;
        }

        public int len() {
            return envelope.length;
        }

        public boolean isEmpty() {
            return envelope.length == 0;
        }

        public float frameRate(int sampleRate) {
            return (float) sampleRate / (float) hop;
        }

        @Override
        public String toString() {
            return "Onsets { envelope: [" + envelope.length + " envelope values], hop: " + hop
                    + ", frame: " + frame + " }";
        }
    }

    public static final class Tempo {

        public final float bpm;

        public final float periodFrames;

        public final float phaseFrames;

        public final float confidence;

        public Tempo(float bpm, float periodFrames, float phaseFrames, float confidence) {
            this.bpm = bpm;
            this.periodFrames = periodFrames;
            this.phaseFrames = phaseFrames;
            this.confidence = confidence;
        }

        public long nextBeatAfter(long frame) {
            if (periodFrames <= 1.0f) {
                return frame;
            }
            float f = (float) frame;
            float k = ceilF32((f - phaseFrames) / periodFrames);
            float beat = phaseFrames + k * periodFrames;
            if (beat < 0.0f) {
                return frame;
            }
            return (long) roundF32(beat);
        }

        public long prevBeatBefore(long frame) {
            if (periodFrames <= 1.0f) {
                return frame;
            }
            float f = (float) frame;
            float k = floorF32((f - phaseFrames) / periodFrames);
            float beat = phaseFrames + k * periodFrames;
            if (beat < 0.0f) {
                return frame;
            }
            return (long) roundF32(beat);
        }

        public long beatOffsetBy(long frame, long n) {
            long base = nextBeatAfter(frame);
            float t = (float) base + (float) n * periodFrames;
            if (t < 0.0f) {
                return 0L;
            }
            return (long) roundF32(t);
        }

        public String describe() {
            return String.format(Locale.ROOT, "%.1f BPM (period %.0f frames, first beat %.0f, confidence %.2f)",
                    bpm, periodFrames, phaseFrames, confidence);
        }

        @Override
        public String toString() {
            return "Tempo { bpm: " + bpm
                    + ", period_frames: " + periodFrames
                    + ", phase_frames: " + phaseFrames
                    + ", confidence: " + confidence + " }";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Tempo)) {
                return false;
            }
            Tempo t = (Tempo) o;
            return Float.compare(bpm, t.bpm) == 0
                    && Float.compare(periodFrames, t.periodFrames) == 0
                    && Float.compare(phaseFrames, t.phaseFrames) == 0
                    && Float.compare(confidence, t.confidence) == 0;
        }

        @Override
        public int hashCode() {
            int h = Float.floatToIntBits(bpm);
            h = 31 * h + Float.floatToIntBits(periodFrames);
            h = 31 * h + Float.floatToIntBits(phaseFrames);
            h = 31 * h + Float.floatToIntBits(confidence);
            return h;
        }
    }

    public static Onsets onsetEnvelope(float[] pcm, int sampleRate) {
        if (pcm.length < FRAME * 2 || sampleRate == 0) {
            return new Onsets(new float[0], HOP, FRAME);
        }

        Fft fft = new Fft(FRAME);
        int bins = FRAME / 2 + 1;

        float[] window = new float[FRAME];
        Fft.hann(window);

        float[] re = new float[FRAME];
        float[] im = new float[FRAME];
        float[] mag = new float[bins];
        float[] prev = new float[bins];
        boolean firstFrame = true;

        int frames = (pcm.length - FRAME) / HOP + 1;

        float[] envelope = new float[frames];

        for (int f = 0; f < frames; f++) {
            int start = f * HOP;
            for (int i = 0; i < FRAME; i++) {
                re[i] = pcm[start + i] * window[i];
                im[i] = 0.0f;
            }
            fft.forward(re, im);
            fft.magnitude(re, im, mag);

            if (firstFrame) {
                System.arraycopy(mag, 0, prev, 0, bins);
                firstFrame = false;
                envelope[f] = 0.0f;
                continue;
            }

            float flux = 0.0f;
            for (int k = 0; k < bins; k++) {
                float d = mag[k] - prev[k];
                if (d > 0.0f) {
                    flux += d;
                }
            }

            envelope[f] = flux / (float) bins;

            System.arraycopy(mag, 0, prev, 0, bins);
        }

        Onsets out = new Onsets(envelope, HOP, FRAME);

        removeMean(out.envelope);
        return out;
    }

    private static void removeMean(float[] x) {
        if (x.length == 0) {
            return;
        }
        float sum = 0.0f;
        for (int i = 0; i < x.length; i++) {
            sum += x[i];
        }
        float m = sum / (float) x.length;
        for (int i = 0; i < x.length; i++) {
            x[i] -= m;
        }
    }

    public static Tempo estimateTempo(Onsets onsets, int sampleRate) {
        float[] env = onsets.envelope;
        if (sampleRate == 0 || env.length < 64) {
            return null;
        }

        float fr = onsets.frameRate(sampleRate);
        if (fr <= 0.0f) {
            return null;
        }

        int lagMin = Math.max((int) floorF32(fr * 60.0f / MAX_BPM), 2);
        int lagMax = Math.min((int) ceilF32(fr * 60.0f / MIN_BPM), env.length / 2);
        if (lagMax <= lagMin + 1) {
            return null;
        }

        float[] ac = new float[lagMax + 1];
        Fft.autocorrelate(env, lagMax, ac);

        float zero = Math.abs(ac.length > 0 ? ac[0] : 0.0f);
        if (zero <= 1e-12f) {
            return null;
        }

        int bestLag = 0;
        float bestScore = -Float.MAX_VALUE;

        for (int lag = lagMin; lag <= lagMax; lag++) {
            float bpm = fr * 60.0f / (float) lag;
            float score = ac[lag] * tempoPrior(bpm);
            if (score > bestScore) {
                bestScore = score;
                bestLag = lag;
            }
        }
        if (bestLag == 0) {
            return null;
        }

        float periodEnv = refinePeak(ac, bestLag);

        float sum = 0.0f;
        for (int l = lagMin; l <= lagMax; l++) {
            sum += Math.abs(ac[l]);
        }

        float mean = (sum > 1e-12f ? sum : 1e-12f) / (float) (lagMax - lagMin + 1);
        float denom = mean > 1e-12f ? mean : 1e-12f;
        float confidence = clamp(Math.abs(ac[bestLag]) / denom / 8.0f, 0.0f, 1.0f);

        if (confidence < MIN_CONFIDENCE) {
            return null;
        }

        float phaseEnv = beatPhase(env, periodEnv);
        float hop = (float) onsets.hop;
        float periodFrames = periodEnv * hop;
        if (periodFrames < 1.0f) {
            return null;
        }

        return new Tempo(
                fr * 60.0f / periodEnv,
                periodFrames,

                (phaseEnv * hop) % periodFrames,
                confidence);
    }

    static float tempoPrior(float bpm) {
        if (!(bpm > 0.0f)) {
            return 0.0f;
        }
        float r = (float) Math.log((double) (bpm / PRIOR_CENTER_BPM));
        return (float) Math.exp((double) (-(r * r) / (2.0f * PRIOR_SIGMA * PRIOR_SIGMA)));
    }

    private static float refinePeak(float[] ac, int lag) {
        if (lag == 0 || lag + 1 >= ac.length) {
            return (float) lag;
        }
        float a = ac[lag - 1];
        float b = ac[lag];
        float c = ac[lag + 1];
        float denom = a - 2.0f * b + c;
        if (Math.abs(denom) < 1e-12f) {
            return (float) lag;
        }
        float delta = 0.5f * (a - c) / denom;

        if (Math.abs(delta) > 0.5f) {
            return (float) lag;
        }
        return (float) lag + delta;
    }

    static float beatPhase(float[] env, float period) {
        if (period <= 1.0f) {

            return ENV_CENTER_OFFSET;
        }
        float sub = 4.0f;
        int steps = (int) ceilF32(period * sub);
        int loops = Math.max(steps, 1);
        float bestOff = 0.0f;
        float best = -Float.MAX_VALUE;

        for (int s = 0; s < loops; s++) {
            float off = (float) s / sub;
            float sum = 0.0f;
            float t = off;
            while (t < (float) env.length) {
                sum += sampleLinear(env, t);
                t += period;
            }
            if (sum > best) {
                best = sum;
                bestOff = off;
            }
        }
        return bestOff + ENV_CENTER_OFFSET;
    }

    private static float peakAround(float[] onset, float t) {
        int lo = (int) floorF32(t);
        int hi = lo + 1;
        float a = (lo >= 0 && lo < onset.length) ? onset[lo] : 0.0f;
        float b = (hi >= 0 && hi < onset.length) ? onset[hi] : 0.0f;
        return Math.max(a, b);
    }

    private static float sampleLinear(float[] x, float pos) {
        if (x.length == 0 || pos < 0.0f) {
            return 0.0f;
        }
        int i = (int) floorF32(pos);
        if (i + 1 >= x.length) {
            return (i >= 0 && i < x.length) ? x[i] : 0.0f;
        }
        float frac = pos - (float) i;
        return x[i] + (x[i + 1] - x[i]) * frac;
    }

    public static Tempo analyze(float[] pcm, int sampleRate) {

        int hops = pcm.length / ENERGY_HOP;
        if (sampleRate <= 0 || hops < ENERGY_HOP_MIN_HOPS) {
            return null;
        }
        float[] rms = new float[hops];
        for (int h = 0; h < hops; h++) {
            int base = h * ENERGY_HOP;
            double sum = 0.0;
            for (int i = 0; i < ENERGY_HOP; i++) {
                double v = pcm[base + i];
                sum += v * v;
            }
            rms[h] = (float) Math.sqrt(sum / ENERGY_HOP);
        }
        return tempoFromRmsFlux(rms, ENERGY_HOP, sampleRate);
    }

    static final int ENERGY_HOP = 512;

    static final int ENERGY_HOP_MIN_HOPS = 16;

    static final float ENERGY_MIN_BPM = 70.0f;
    static final float ENERGY_MAX_BPM = 180.0f;

    static final double FAST_TEMPO_BIAS_PER_LAG = 0.0005;

    static final int PHASE_SUBSTEPS = 4;

    static final int PERIOD_SUBSTEPS = 32;
    static final int PERIOD_SPAN_SUBSTEPS = 16;

    static final double OCTAVE_RATIO = 0.6;

    static final double OCTAVE_COVERAGE_MARGIN = 1.1;

    static final int OCTAVE_FOLD_DEPTH = 3;

    static double coverageOf(float[] onset, int hops, float period) {
        if (period <= 1.0f) {
            return 0.0;
        }
        float phase = phaseOf(onset, hops, period);
        float tol = period * 0.25f;
        double total = 0.0;
        double covered = 0.0;
        for (int i = 0; i < hops; i++) {
            float v = onset[i];
            if (!(v > 0.0f)) {
                continue;
            }
            total += v;
            float rel = (float) i - phase;
            float mod = rel - period * (float) Math.floor((double) rel / (double) period);
            if (Math.min(mod, period - mod) <= tol) {
                covered += v;
            }
        }
        return total > 0.0 ? covered / total : 0.0;
    }

    static float phaseOf(float[] onset, int hops, float period) {
        double bestSum = Double.NEGATIVE_INFINITY;
        float bestOff = 0.0f;
        int steps = Math.max(1, Math.round(period * PHASE_SUBSTEPS));
        for (int st = 0; st < steps; st++) {
            float off = (float) st / (float) PHASE_SUBSTEPS;
            double sum = 0.0;
            float t = off;
            while (t < (float) hops) {
                sum += sampleLinear(onset, t);
                t += period;
            }
            if (sum > bestSum) {
                bestSum = sum;
                bestOff = off;
            }
        }
        return bestOff;
    }

    private static double windowedAutocorrelation(float[] onset, int hops, float period) {
        int lag = (int) Math.ceil(period);
        if (lag <= 0 || lag >= hops) {
            return 0.0;
        }
        double ac = 0.0;
        for (int h = lag; h < hops; h++) {
            ac += (double) onset[h] * (double) peakAround(onset, (float) h - period);
        }
        return ac;
    }

    public static Tempo tempoFromRmsFlux(float[] rms, int hop, int sampleRate) {
        if (rms == null || sampleRate <= 0 || hop <= 0 || rms.length < ENERGY_HOP_MIN_HOPS) {
            return null;
        }
        int hops = rms.length;

        float[] onset = new float[hops];
        float prevE = 0.0f;
        double total = 0.0;
        for (int h = 0; h < hops; h++) {
            float e = rms[h];
            float flux = e - prevE;
            onset[h] = flux > 0.0f ? flux : 0.0f;
            prevE = e;
            total += onset[h];
        }
        if (total < 1e-4) {
            return null;
        }
        float mean = (float) (total / (double) hops);
        for (int h = 0; h < hops; h++) {
            onset[h] -= mean;
        }

        return tempoFromOnsetEnvelope(onset, hop, sampleRate, 1.0f);
    }

    public static Tempo tempoFromOnsetEnvelope(float[] onset, int hop, int sampleRate) {
        return tempoFromOnsetEnvelope(onset, hop, sampleRate, 0.0f);
    }

    public static Tempo tempoFromOnsetEnvelope(float[] onset, int hop, int sampleRate,
                                               float attributionBins) {
        if (onset == null || sampleRate <= 0 || hop <= 0
                || onset.length < ENERGY_HOP_MIN_HOPS) {
            return null;
        }
        int hops = onset.length;
        float envRate = (float) sampleRate / (float) hop;
        int minLag = Math.max(1, Math.round(envRate * 60.0f / ENERGY_MAX_BPM));
        int maxLag = Math.min(hops - 1, Math.round(envRate * 60.0f / ENERGY_MIN_BPM));
        if (maxLag <= minLag) {
            return null;
        }

        float[] acRaw = new float[maxLag + 1];
        int bestLag = minLag;
        double bestAc = Double.NEGATIVE_INFINITY;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double ac = 0.0;
            for (int h = lag; h < hops; h++) {
                ac += (double) onset[h] * (double) onset[h - lag];
            }
            acRaw[lag] = (float) ac;

            double biased = ac * (1.0 + FAST_TEMPO_BIAS_PER_LAG * (double) (maxLag - lag));
            if (biased > bestAc) {
                bestAc = biased;
                bestLag = lag;
            }
        }

        float periodEnv = refinePeak(acRaw, bestLag);
        float bestOff = 0.0f;

        float chosenPeriod = periodEnv;
        for (int k = 1; k <= OCTAVE_FOLD_DEPTH; k++) {
            float faster = chosenPeriod * 0.5f;
            if (faster <= 1.0f || 60.0f * envRate / faster > ENERGY_MAX_BPM) {
                break;
            }

            boolean byCoverage = coverageOf(onset, hops, faster)
                    >= coverageOf(onset, hops, chosenPeriod) * OCTAVE_COVERAGE_MARGIN;
            boolean byAutocorrelation = windowedAutocorrelation(onset, hops, faster)
                    >= windowedAutocorrelation(onset, hops, chosenPeriod) * OCTAVE_RATIO;
            if (!byCoverage && !byAutocorrelation) {
                break;
            }
            chosenPeriod = faster;
        }
        periodEnv = refinePeak(acRaw, Math.max(2, Math.round(chosenPeriod)));

        double bestPhaseSum = Double.NEGATIVE_INFINITY;
        float basePeriod = periodEnv;
        for (int pi = -PERIOD_SPAN_SUBSTEPS; pi <= PERIOD_SPAN_SUBSTEPS; pi++) {
            float period = basePeriod + (float) pi / (float) PERIOD_SUBSTEPS;
            if (period <= 1.0f || period >= (float) hops) {
                continue;
            }
            int steps = Math.max(1, Math.round(period * PHASE_SUBSTEPS));
            for (int st = 0; st < steps; st++) {
                float off = (float) st / (float) PHASE_SUBSTEPS;
                double sum = 0.0;
                float t = off;
                while (t < (float) hops) {
                    sum += sampleLinear(onset, t);
                    t += period;
                }
                if (sum > bestPhaseSum) {
                    bestPhaseSum = sum;
                    periodEnv = period;
                    bestOff = off;
                }
            }
        }

        int chosenLag = Math.max(1, Math.min(hops - 1, Math.round(periodEnv)));
        double acAtChosen = 0.0;
        for (int h = chosenLag; h < hops; h++) {
            acAtChosen += (double) onset[h] * (double) onset[h - chosenLag];
        }
        double zeroLag = 0.0;
        for (int h = 0; h < hops; h++) {
            zeroLag += (double) onset[h] * (double) onset[h];
        }
        float confidence = zeroLag > 1e-12
                ? clamp((float) (Math.abs(acAtChosen) / zeroLag), 0.0f, 1.0f)
                : 0.0f;
        if (confidence < MIN_AC_RATIO) {
            return null;
        }
        float hopF = (float) hop;
        float periodFrames = periodEnv * hopF;
        if (periodFrames < 1.0f) {
            return null;
        }
        float phaseFrames = ((bestOff + attributionBins) * hopF) % periodFrames;
        return new Tempo(60.0f * envRate / periodEnv, periodFrames, phaseFrames, confidence);
    }

    public static Tempo tempoFromSpectralFlux(float[] pcm, int rate) {
        float[] onset = SpectralOnsets.envelope(pcm, rate);
        if (onset == null) {
            return null;
        }
        return tempoFromOnsetEnvelope(onset, SpectralOnsets.HOP, rate,
                SpectralOnsets.ATTRIBUTION_BINS);
    }

    public static Tempo tempoFromSpectralFluxTracked(float[] pcm, int rate) {
        float[] onset = SpectralOnsets.envelope(pcm, rate);
        if (onset == null) {
            return null;
        }
        return BeatTracker.track(onset, SpectralOnsets.HOP, rate);
    }

    public static Tempo tempoFromBeats(float[] beats, int sampleRate) {
        if (sampleRate <= 0 || beats == null || beats.length < 2) {
            return null;
        }

        float[] intervals = new float[beats.length - 1];
        int n = 0;
        for (int i = 1; i < beats.length; i++) {
            float d = beats[i] - beats[i - 1];
            if (d > 1e-6f && !Float.isNaN(d) && !Float.isInfinite(d)) {
                intervals[n] = d;
                n++;
            }
        }
        if (n == 0) {
            return null;
        }

        float[] sorted = Arrays.copyOf(intervals, n);
        Arrays.sort(sorted);
        float periodSecs = median(sorted);
        if (!(periodSecs > 1e-3f)) {
            return null;
        }

        float[] dev = new float[n];
        for (int i = 0; i < n; i++) {
            dev[i] = Math.abs(intervals[i] - periodSecs);
        }
        Arrays.sort(dev);
        float mad = median(dev);
        float confidence = clamp(1.0f - mad / periodSecs, 0.0f, 1.0f);

        float period = periodSecs;
        float firstBeatSecs = beats[0];
        float[] fit = refineGrid(beats, periodSecs);
        if (fit != null) {
            period = fit[0];
            firstBeatSecs = fit[1];
        }

        float rate = (float) sampleRate;
        float periodFrames = period * rate;
        if (periodFrames < 1.0f) {
            return null;
        }

        float phaseFrames = (firstBeatSecs * rate) % periodFrames;

        return new Tempo(60.0f / period, periodFrames, phaseFrames, confidence);
    }

    private static float[] refineGrid(float[] beats, float periodGuess) {
        int m = beats.length;
        if (m < 4) {
            return null;
        }
        int[] k = new int[m];
        boolean[] keep = new boolean[m];
        for (int i = 0; i < m; i++) {

            k[i] = Math.round((beats[i] - beats[0]) / periodGuess);
            keep[i] = true;
        }

        if (k[m - 1] - k[0] < 2) {
            return null;
        }

        for (int pass = 0; pass < 2; pass++) {

            double sk = 0.0;
            double st = 0.0;
            double skk = 0.0;
            double skt = 0.0;
            int cnt = 0;
            for (int i = 0; i < m; i++) {
                if (!keep[i]) {
                    continue;
                }
                double ki = (double) k[i];
                double ti = (double) beats[i];
                sk += ki;
                st += ti;
                skk += ki * ki;
                skt += ki * ti;
                cnt++;
            }
            if (cnt < 4) {
                return null;
            }
            double denom = (double) cnt * skk - sk * sk;
            if (Math.abs(denom) < 1e-9) {
                return null;
            }
            double slope = ((double) cnt * skt - sk * st) / denom;
            double intercept = (st - slope * sk) / (double) cnt;

            if (!(slope > 0.0)
                    || slope < (double) periodGuess * 0.5
                    || slope > (double) periodGuess * 1.5) {
                return null;
            }

            boolean dropped = false;
            double tol = (double) periodGuess * 0.25;
            for (int i = 0; i < m; i++) {
                if (!keep[i]) {
                    continue;
                }
                double residual = Math.abs((double) beats[i] - (intercept + slope * (double) k[i]));
                if (residual > tol) {
                    keep[i] = false;
                    dropped = true;
                }
            }
            if (!dropped) {
                return new float[]{(float) slope, (float) intercept};
            }
        }

        return null;
    }

    private static float median(float[] sorted) {
        int n = sorted.length;
        if (n == 0) {
            return 0.0f;
        }
        int mid = n / 2;
        if ((n & 1) == 1) {
            return sorted[mid];
        }
        return (sorted[mid - 1] + sorted[mid]) * 0.5f;
    }

    public static float[] foldToMono(float[][] channels) {
        if (channels.length == 0) {
            return new float[0];
        }
        float[] first = channels[0];
        int n = first.length;
        if (channels.length == 1) {
            return Arrays.copyOf(first, n);
        }
        float[] out = new float[n];
        for (int c = 0; c < channels.length; c++) {
            float[] ch = channels[c];
            int take = Math.min(ch.length, n);
            for (int i = 0; i < take; i++) {
                out[i] += ch[i];
            }
        }
        float inv = 1.0f / (float) channels.length;
        for (int i = 0; i < n; i++) {
            out[i] *= inv;
        }
        return out;
    }

    private static float roundF32(float v) {
        double d = (double) v;
        if (d < 0.0) {
            return (float) Math.ceil(d - 0.5);
        }
        return (float) Math.floor(d + 0.5);
    }

    private static float ceilF32(float v) {
        return (float) Math.ceil((double) v);
    }

    private static float floorF32(float v) {
        return (float) Math.floor((double) v);
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
}
