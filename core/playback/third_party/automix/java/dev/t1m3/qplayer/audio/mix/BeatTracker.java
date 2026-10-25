package dev.t1m3.qplayer.audio.mix;

public final class BeatTracker {

    static final float MIN_BPM = 70.0f;
    static final float MAX_BPM = 180.0f;

    static final double PREFERRED_DEVIATION = 0.12;

    static final double TIGHTNESS = 5.0;

    static final double RESONANCE_CENTER_BPM = 120.0;
    static final double RESONANCE_SIGMA = 0.5;

    static final double VARIANT_FAST_1 = 0.5;
    static final double VARIANT_FAST_2 = 2.0 / 3.0;
    static final double VARIANT_FAST_3 = 0.75;
    static final double VARIANT_SLOW_1 = 4.0 / 3.0;
    static final double VARIANT_SLOW_2 = 1.5;
    static final double VARIANT_SLOW_3 = 2.0;

    static final int ACF_SEEDS = 4;

    static final int MAX_CANDIDATES = 24;

    static final int MAX_PASSES = 5;

    static final double PERIOD_CONVERGED_FRACTION = 1.0e-4;

    static final int MIN_BEATS = 8;

    static final double EMPTY_COST = 0.25;

    static final int REFINE_FRAMES = 3;

    static final double VALID_BEAT_PEAK = 0.10;

    static final double MIN_VALID_RATIO = 0.35;

    static final double RESIDUAL_FULL_FRACTION = 0.25;

    static final double MAX_RESIDUAL_FRACTION = 0.10;

    private static volatile String lastReject;

    public static String lastRejectReason() {
        return lastReject;
    }

    private static Analysis.Tempo no(String why) {
        lastReject = why;
        return null;
    }

    private BeatTracker() {
    }

    public static Analysis.Tempo track(float[] onset, int hop, int sampleRate) {
        if (onset == null || hop <= 0 || sampleRate <= 0 || onset.length < 32) {
            return no("onset envelope too short, or invalid parameters");
        }
        int n = onset.length;
        double envRate = (double) sampleRate / (double) hop;
        int minLag = Math.max(1, (int) Math.round(envRate * 60.0 / MAX_BPM));
        int maxLag = Math.min(n - 2, (int) Math.round(envRate * 60.0 / MIN_BPM));
        if (maxLag <= minLag + 1) {
            return no("material too short: the lag range for 70-180 BPM does not fit (" + n + " cells)");
        }
        double scale = percentile95(onset);
        if (scale <= 1e-12) {
            return no("onset envelope is all zeros (this material has no onsets)");
        }
        float[] x = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = (float) (onset[i] / scale);
        }

        double energy = 0.0;
        for (int i = 0; i < n; i++) {
            energy += (double) x[i] * x[i];
        }
        if (energy <= 1e-12) {
            return no("onset envelope energy is zero");
        }

        double[] ac = new double[maxLag + 1];
        for (int lag = minLag; lag <= maxLag; lag++) {
            double sum = 0.0;
            for (int i = lag; i < n; i++) {
                sum += (double) x[i] * x[i - lag];
            }
            ac[lag] = sum / energy;
        }
        java.util.TreeMap<Double, Boolean> seeds = new java.util.TreeMap<Double, Boolean>();
        for (int pick = 0; pick < ACF_SEEDS; pick++) {
            int best = -1;
            for (int lag = minLag + 1; lag < maxLag; lag++) {
                if (ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1] && ac[lag] > 0.0) {
                    boolean taken = false;
                    for (Double s : seeds.keySet()) {
                        if (Math.abs(s.doubleValue() - lag) < 2.0) {
                            taken = true;
                            break;
                        }
                    }
                    if (!taken && (best < 0 || ac[lag] > ac[best])) {
                        best = lag;
                    }
                }
            }
            if (best < 0) {
                break;
            }
            seeds.put(Double.valueOf(best), Boolean.TRUE);
        }
        if (seeds.isEmpty()) {
            return no("onset envelope energy is zero");
        }

        java.util.TreeSet<Double> candidates = new java.util.TreeSet<Double>();
        double[] variants = {1.0, VARIANT_FAST_1, VARIANT_FAST_2, VARIANT_FAST_3,
                VARIANT_SLOW_1, VARIANT_SLOW_2, VARIANT_SLOW_3};
        for (Double seed : seeds.keySet()) {
            for (double v : variants) {
                double p = seed.doubleValue() * v;
                if (p >= minLag - 0.5 && p <= maxLag + 0.5) {
                    candidates.add(Double.valueOf(p));
                }
            }
            if (candidates.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        if (candidates.isEmpty()) {
            return no("onset envelope energy is zero");
        }

        double bestScore = Double.NEGATIVE_INFINITY;
        double bestPeriod = 0.0;
        int[] bestPath = null;
        for (Double cand : candidates) {
            int[] path = dp(x, cand.doubleValue(), minLag, maxLag);
            if (path == null || path.length < MIN_BEATS) {
                continue;
            }

            double sum = 0.0;
            for (int idx : path) {
                sum += Math.max(0.0, x[idx]);
            }
            double bpm = 60.0 * envRate / cand.doubleValue();
            double score = (sum - EMPTY_COST * path.length) * resonancePrior(bpm);
            if (score > bestScore) {
                bestScore = score;
                bestPeriod = cand.doubleValue();
                bestPath = path;
            }
        }
        if (bestPath == null) {
            return no("material too short: the lag range for 70-180 BPM does not fit (" + MIN_BEATS + " cells)");
        }

        Fit fit = null;
        double trackPeriod = bestPeriod;

        for (int pass = 0; pass < MAX_PASSES; pass++) {
            int[] path = pass == 0 ? bestPath : dp(x, trackPeriod, minLag, maxLag);
            if (path == null || path.length < MIN_BEATS) {
                if (pass == 0) {
                    return no("material too short: the lag range for 70-180 BPM does not fit (" + MIN_BEATS + " cells)");
                }
                break;
            }
            int span = REFINE_FRAMES;
            double[] times = new double[path.length];
            double[] numbers = new double[path.length];
            for (int i = 0; i < path.length; i++) {
                times[i] = refinePeak(x, path[i], span);
                if (i == 0) {
                    numbers[i] = 0.0;
                } else {

                    numbers[i] = numbers[i - 1] + Math.max(1.0,
                            Math.round((times[i] - times[i - 1]) / trackPeriod));
                }
            }
            Fit got = robustFit(numbers, times, envRate);
            if (got == null || got.count < MIN_BEATS) {
                if (pass == 0) {
                    return no("robust regression could not fit a grid (not enough valid beats)");
                }
                break;
            }
            double previous = trackPeriod;
            fit = got;
            trackPeriod = got.period;
            if (Math.abs(got.period - previous) / got.period < PERIOD_CONVERGED_FRACTION) {
                break;
            }
        }
        if (fit == null) {
            return no("onset envelope energy is zero");
        }

        int residSpan = Math.max(1, (int) Math.round(fit.period / 4.0));
        int first = (int) Math.ceil((0.0 - fit.phase) / fit.period);
        int last = (int) Math.floor((n - 1.0 - fit.phase) / fit.period);
        int total = Math.max(0, last - first + 1);
        double[] offsets = new double[total];
        int valid = 0;
        for (int k = first; k <= last; k++) {
            double want = fit.phase + fit.period * k;
            int centre = (int) Math.round(want);
            if (centre < 0 || centre >= n) {
                continue;
            }
            int lo = Math.max(0, centre - residSpan);
            int hi = Math.min(n - 1, centre + residSpan);
            int peak = lo;
            for (int i = lo; i <= hi; i++) {
                if (x[i] > x[peak]) {
                    peak = i;
                }
            }
            if (x[peak] < VALID_BEAT_PEAK) {
                continue;
            }
            offsets[valid++] = refinePeak(x, peak, 1) - want;
        }
        if (valid < MIN_BEATS || total <= 0 || valid < MIN_VALID_RATIO * total) {
            return no(String.format(java.util.Locale.ROOT,
                    "not enough valid beats: %d/%d land on onsets (threshold %.0f%% and at least %d beats)",
                    Integer.valueOf(valid), Integer.valueOf(total),
                    Double.valueOf(MIN_VALID_RATIO * 100.0), Integer.valueOf(MIN_BEATS)));
        }
        double[] absOff = new double[valid];
        for (int i = 0; i < valid; i++) {
            absOff[i] = Math.abs(offsets[i]);
        }
        java.util.Arrays.sort(absOff);
        double medianOffset = absOff[valid / 2];
        double[] dev = new double[valid];
        for (int i = 0; i < valid; i++) {
            dev[i] = Math.abs(Math.abs(offsets[i]) - medianOffset);
        }
        java.util.Arrays.sort(dev);
        double residualMs = 1.4826 * dev[valid / 2] / envRate * 1000.0;
        double residualFraction = residualMs / (fit.period / envRate * 1000.0);
        if (residualFraction > MAX_RESIDUAL_FRACTION) {
            return no(String.format(java.util.Locale.ROOT,
                    "beats deviate too far from the grid: residual %.1f ms = %.1f%% of a beat > %.0f%%",
                    Double.valueOf(residualMs), Double.valueOf(residualFraction * 100.0),
                    Double.valueOf(MAX_RESIDUAL_FRACTION * 100.0)));
        }
        double bpm = 60.0 * envRate / fit.period;
        if (bpm < MIN_BPM * 0.95 || bpm > MAX_BPM * 1.05) {
            return no(String.format(java.util.Locale.ROOT,
                    "the fitted tempo %.2f BPM falls outside 70-180", Double.valueOf(bpm)));
        }
        double confidence = Math.max(0.0,
                Math.min(1.0, 1.0 - residualFraction / RESIDUAL_FULL_FRACTION));
        lastReject = null;
        return new Analysis.Tempo((float) bpm, (float) (fit.period * hop),
                (float) ((fit.phase % fit.period) * hop), (float) confidence);
    }

    private static int[] dp(float[] x, double period, int minLag, int maxLag) {
        int n = x.length;
        int back = Math.max(1, (int) Math.round(period * 0.5));
        int fwd = Math.max(back + 1, (int) Math.round(period * 1.5));
        double[] score = new double[n];
        int[] from = new int[n];

        double[] penaltyByGap = new double[fwd + 1];
        for (int gap = 0; gap <= fwd; gap++) {
            double d = (double) gap / period;
            penaltyByGap[gap] = TIGHTNESS * (-0.5
                    * Math.pow(Math.log(Math.max(d, 1e-6)) / PREFERRED_DEVIATION, 2.0));
        }
        for (int t = 0; t < n; t++) {
            score[t] = Math.max(0.0, x[t]);
            from[t] = -1;
            int lo = Math.max(0, t - fwd);
            int hi = t - back;
            double bestPrev = Double.NEGATIVE_INFINITY;
            int bestIdx = -1;
            for (int tau = lo; tau <= hi; tau++) {
                double cand = score[tau] + penaltyByGap[t - tau];
                if (cand > bestPrev) {
                    bestPrev = cand;
                    bestIdx = tau;
                }
            }

            if (bestIdx >= 0 && bestPrev > 0.0) {
                score[t] += bestPrev;
                from[t] = bestIdx;
            }
        }

        int tailFrom = Math.max(0, n - Math.max(1, (int) Math.round(period)));
        int end = -1;
        for (int t = tailFrom; t < n; t++) {
            if (end < 0 || score[t] > score[end]) {
                end = t;
            }
        }
        if (end < 0) {
            return null;
        }
        int count = 0;
        for (int t = end; t >= 0; t = from[t]) {
            count++;
            if (count > n) {
                return null;
            }
        }
        int[] path = new int[count];
        int i = count - 1;
        for (int t = end; t >= 0; t = from[t]) {
            path[i--] = t;
        }
        return path;
    }

    private static double resonancePrior(double bpm) {
        double ratio = Math.log(Math.max(bpm, 1e-6) / RESONANCE_CENTER_BPM);
        return Math.exp(-0.5 * Math.pow(ratio / RESONANCE_SIGMA, 2.0));
    }

    private static double refinePeak(float[] x, int center, int span) {
        int lo = Math.max(0, center - span);
        int hi = Math.min(x.length - 1, center + span);
        int best = lo;
        for (int i = lo; i <= hi; i++) {
            if (x[i] > x[best]) {
                best = i;
            }
        }
        if (best <= 0 || best >= x.length - 1) {
            return best;
        }
        double a = x[best - 1];
        double b = x[best];
        double c = x[best + 1];
        double denom = a - 2.0 * b + c;
        if (Math.abs(denom) < 1e-12) {
            return best;
        }
        double delta = 0.5 * (a - c) / denom;
        return best + Math.max(-0.5, Math.min(0.5, delta));
    }

    private static final class Fit {
        final double period;
        final double phase;
        final double residualMs;
        final int count;

        Fit(double period, double phase, double residualMs, int count) {
            this.period = period;
            this.phase = phase;
            this.residualMs = residualMs;
            this.count = count;
        }
    }

    private static Fit robustFit(double[] numbers, double[] times, double envRate) {
        int n = times.length;
        if (n < MIN_BEATS) {
            return null;
        }
        boolean[] keep = new boolean[n];
        java.util.Arrays.fill(keep, true);
        double period = 0.0;
        double phase = 0.0;
        double madMs = 0.0;
        int kept = n;
        for (int iter = 0; iter < 6; iter++) {
            double sx = 0.0;
            double sy = 0.0;
            double sxx = 0.0;
            double sxy = 0.0;
            double si = 0.0;
            int k = 0;
            for (int i = 0; i < n; i++) {
                if (!keep[i]) {
                    continue;
                }
                double xi = numbers[i];
                double yi = times[i] - times[0];
                sx += xi;
                sy += yi;
                sxx += xi * xi;
                sxy += xi * yi;
                si += 1.0;
                k++;
            }
            if (k < MIN_BEATS) {
                return null;
            }
            double denom = si * sxx - sx * sx;
            if (Math.abs(denom) < 1e-9) {
                return null;
            }
            period = (si * sxy - sx * sy) / denom;
            phase = times[0] + (sy - period * sx) / si;

            if (!(period > 0.0)) {
                return null;
            }
            double[] residuals = new double[k];
            int j = 0;
            for (int i = 0; i < n; i++) {
                if (keep[i]) {
                    residuals[j++] = times[i] - (phase + period * numbers[i]);
                }
            }
            double[] sorted = residuals.clone();
            java.util.Arrays.sort(sorted);
            double median = sorted[sorted.length / 2];
            double[] abs = new double[sorted.length];
            for (int i = 0; i < sorted.length; i++) {
                abs[i] = Math.abs(sorted[i] - median);
            }
            java.util.Arrays.sort(abs);
            double mad = abs[abs.length / 2];
            double sigma = 1.4826 * mad;
            madMs = sigma / envRate * 1000.0;
            if (sigma <= 1e-9) {
                break;
            }
            int dropped = 0;
            for (int i = 0; i < n; i++) {
                if (!keep[i]) {
                    continue;
                }
                double r = Math.abs(times[i] - (phase + period * numbers[i]) - median);
                if (r > 2.5 * sigma) {
                    keep[i] = false;
                    dropped++;
                }
            }
            kept = k - dropped;
            if (dropped == 0 || kept < MIN_BEATS) {
                break;
            }
        }
        return new Fit(period, phase % period, madMs, kept);
    }

    private static final class Line {
        final double slope;
        final double intercept;
        final double residualMs;

        Line(double slope, double intercept, double residualMs) {
            this.slope = slope;
            this.intercept = intercept;
            this.residualMs = residualMs;
        }
    }

    private static Line robustLine(double[] k, double[] pos, int count, double envRate) {
        boolean[] keep = new boolean[count];
        java.util.Arrays.fill(keep, true);
        double slope = 0.0;
        double intercept = 0.0;
        double residualMs = Double.NaN;
        int kept = count;
        for (int iter = 0; iter < 6; iter++) {
            double sx = 0.0;
            double sy = 0.0;
            double sxx = 0.0;
            double sxy = 0.0;
            double m = 0.0;
            for (int i = 0; i < count; i++) {
                if (!keep[i]) {
                    continue;
                }
                sx += k[i];
                sy += pos[i];
                sxx += k[i] * k[i];
                sxy += k[i] * pos[i];
                m += 1.0;
            }
            if (m < MIN_BEATS) {
                return null;
            }
            double denom = m * sxx - sx * sx;
            if (Math.abs(denom) < 1e-9) {
                return null;
            }
            slope = (m * sxy - sx * sy) / denom;
            intercept = (sy - slope * sx) / m;
            if (!(slope > 0.0)) {
                return null;
            }
            double[] residuals = new double[(int) m];
            int j = 0;
            for (int i = 0; i < count; i++) {
                if (keep[i]) {
                    residuals[j++] = pos[i] - (intercept + slope * k[i]);
                }
            }
            java.util.Arrays.sort(residuals);
            double median = residuals[residuals.length / 2];
            double[] abs = new double[residuals.length];
            for (int i = 0; i < residuals.length; i++) {
                abs[i] = Math.abs(residuals[i] - median);
            }
            java.util.Arrays.sort(abs);
            double sigma = 1.4826 * abs[abs.length / 2];
            residualMs = sigma / envRate * 1000.0;
            if (sigma <= 1e-9) {
                break;
            }
            int dropped = 0;
            for (int i = 0; i < count; i++) {
                if (!keep[i]) {
                    continue;
                }
                if (Math.abs(pos[i] - (intercept + slope * k[i]) - median) > 2.5 * sigma) {
                    keep[i] = false;
                    dropped++;
                }
            }
            kept = (int) m - dropped;
            if (dropped == 0 || kept < MIN_BEATS) {
                break;
            }
        }
        return kept < MIN_BEATS ? null : new Line(slope, intercept, residualMs);
    }

    private static double percentile95(float[] x) {
        float[] copy = x.clone();
        java.util.Arrays.sort(copy);
        int idx = Math.min(copy.length - 1, (int) Math.floor(copy.length * 0.95));
        double v = copy[idx];
        if (v < 0.0) {

            v = 0.0;
            for (float f : copy) {
                v = Math.max(v, Math.abs(f));
            }
        }
        return v;
    }
}
