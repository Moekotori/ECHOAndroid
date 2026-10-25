package dev.t1m3.qplayer.audio.studio;

public final class PitchDetector {
    public static final double WINDOW_SECS = 0.040;

    public static final double HOP_SECS = 0.010;

    public static final double MIN_HZ = 60.0;

    public static final double MAX_HZ = 1200.0;

    public static final double YIN_THRESHOLD = 0.15;

    static final int WORK_RATE = 24_000;

    private final int rate;
    private final int decimate;
    private final int workRate;
    private final int windowFrames;
    private final int hopFrames;
    private final int minLag;
    private final int maxLag;

    private double acc;
    private int accCount;

    private final StudioBiquad antiAlias;

    private final float[] history;
    private final float[] inbox;
    private int inCount;
    private final double[] diff;
    private final double[] norm;

    private final float[] historyFine;
    private final double[] fineDiff;

    private double hz;
    private double clarity;

    public PitchDetector(int sampleRate) {
        this.rate = Math.max(1, sampleRate);
        this.decimate = Math.max(1, (int) Math.round((double) rate / WORK_RATE));
        this.workRate = Math.max(1, rate / decimate);
        this.windowFrames = Math.max(16, (int) Math.round(workRate * WINDOW_SECS));
        this.hopFrames = Math.max(1, (int) Math.round(workRate * HOP_SECS));
        this.minLag = Math.max(2, (int) Math.floor((double) workRate / MAX_HZ));
        this.maxLag = Math.min(windowFrames - 2, (int) Math.ceil((double) workRate / MIN_HZ));
        this.antiAlias = StudioBiquad.lowPass(rate, Math.min(MAX_HZ * 2.5, rate * 0.45), 0.7071);
        this.history = new float[windowFrames];
        this.inbox = new float[hopFrames];
        this.diff = new double[maxLag + 2];
        this.norm = new double[maxLag + 2];
        this.historyFine = new float[windowFrames * 2];
        this.fineDiff = new double[9];
    }

    public int hopInputFrames() {
        return hopFrames * decimate;
    }

    public int windowInputFrames() {
        return windowFrames * decimate;
    }

    public double hz() {
        return hz;
    }

    public double clarity() {
        return clarity;
    }

    public void reset() {
        acc = 0.0;
        accCount = 0;
        inCount = 0;
        java.util.Arrays.fill(history, 0f);
        antiAlias.reset();
        hz = 0.0;
        clarity = 0.0;
    }

    public boolean push(float mono) {
        double v = mono;
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            v = 0.0;
        }
        acc += antiAlias.process(v);
        if (++accCount < decimate) {
            return false;
        }
        float s = (float) (acc / accCount);
        acc = 0.0;
        accCount = 0;

        inbox[inCount++] = s;
        if (inCount < hopFrames) {
            return false;
        }
        System.arraycopy(history, hopFrames, history, 0, windowFrames - hopFrames);
        System.arraycopy(inbox, 0, history, windowFrames - hopFrames, hopFrames);
        inCount = 0;
        estimate();
        return true;
    }

    private void estimate() {
        int n = windowFrames;
        for (int tau = minLag; tau <= maxLag; tau++) {
            double sum = 0.0;
            for (int i = 0; i + tau < n; i++) {
                double d = history[i] - history[i + tau];
                sum += d * d;
            }
            diff[tau] = sum;
        }
        double running = 0.0;
        norm[minLag - 1] = 1.0;
        for (int tau = minLag; tau <= maxLag; tau++) {
            running += diff[tau];
            double mean = running / (tau - minLag + 1);
            norm[tau] = mean > 1e-12 ? diff[tau] / mean : 1.0;
        }
        int best = -1;
        for (int tau = minLag; tau <= maxLag; tau++) {
            if (norm[tau] < YIN_THRESHOLD) {
                while (tau + 1 <= maxLag && norm[tau + 1] < norm[tau]) {
                    tau++;
                }
                best = tau;
                break;
            }
        }
        if (best < 0) {
            double bestVal = Double.MAX_VALUE;
            for (int tau = minLag; tau <= maxLag; tau++) {
                if (norm[tau] < bestVal) {
                    bestVal = norm[tau];
                    best = tau;
                }
            }
            clarity = Math.max(0.0, Math.min(1.0, 1.0 - bestVal));
            if (clarity < 0.5 && best >= 0) {
                hz = 0.0;
                return;
            }
        } else {
            clarity = Math.max(0.0, Math.min(1.0, 1.0 - norm[best]));
        }
        if (best <= 0) {
            hz = 0.0;
            return;
        }
        double y0 = best > minLag ? norm[best - 1] : norm[best];
        double y1 = norm[best];
        double y2 = best < maxLag ? norm[best + 1] : norm[best];
        double denom = y0 - 2 * y1 + y2;
        double delta = Math.abs(denom) < 1e-12 ? 0.0 : 0.5 * (y0 - y2) / denom;
        if (delta > 1.0) {
            delta = 1.0;
        } else if (delta < -1.0) {
            delta = -1.0;
        }
        double tau = best + delta;
        double fineTau = refineTau(tau);
        hz = fineTau > 0.0 ? (2.0 * workRate) / fineTau : 0.0;
    }

    private double refineTau(double coarseTau) {
        int n = windowFrames;
        for (int i = 0; i < n - 1; i++) {
            float a = history[i];
            float b = history[i + 1];
            historyFine[2 * i] = a;
            historyFine[2 * i + 1] = 0.5f * (a + b);
        }
        historyFine[2 * n - 2] = history[n - 1];
        historyFine[2 * n - 1] = history[n - 1];

        int center = (int) Math.round(coarseTau * 2.0);
        int lo = Math.max(2, center - 4);
        int hi = Math.min(2 * maxLag, center + 4);
        int bestFine = center;
        double bestVal = Double.MAX_VALUE;
        for (int tau = lo; tau <= hi; tau++) {
            double sum = 0.0;
            for (int i = 0; i + tau < 2 * n; i++) {
                double d = historyFine[i] - historyFine[i + tau];
                sum += d * d;
            }
            fineDiff[tau - lo] = sum;
            if (sum < bestVal) {
                bestVal = sum;
                bestFine = tau;
            }
        }
        if (bestFine <= lo || bestFine >= hi) {
            return 2.0 * coarseTau;
        }
        double y0 = fineDiff[bestFine - 1 - lo];
        double y1 = fineDiff[bestFine - lo];
        double y2 = fineDiff[bestFine + 1 - lo];
        double denom = y0 - 2 * y1 + y2;
        double delta = Math.abs(denom) < 1e-12 ? 0.0 : 0.5 * (y0 - y2) / denom;
        if (delta > 1.0) {
            delta = 1.0;
        } else if (delta < -1.0) {
            delta = -1.0;
        }
        return bestFine + delta;
    }

    @Override
    public String toString() {
        return String.format(
                java.util.Locale.ROOT,
                "PitchDetector{%d Hz input → %d Hz analysis, window %.0f ms / hop %.0f ms, range"
                    + " %.0f–%.0f Hz}",
                Integer.valueOf(rate),
                Integer.valueOf(workRate),
                Double.valueOf(WINDOW_SECS * 1000),
                Double.valueOf(HOP_SECS * 1000),
                Double.valueOf(MIN_HZ),
                Double.valueOf(MAX_HZ));
    }
}
