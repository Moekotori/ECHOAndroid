package dev.t1m3.qplayer.audio.mix;

public final class EqCurve {

    static final double[] CONTROL_P = {0.0, 0.33, 0.66, 1.0};

    static final double[] MIN_DB = {-30.0, -18.0, -18.0};

    static final double MAX_DB = 0.0;

    static final int PER_BAND = 4;

    public static final int BANDS = 3;

    public static final int CONTROLS = PER_BAND * BANDS;

    private final int frames;

    private final float[][] gains = new float[BANDS][];

    private EqCurve(int frames) {
        this.frames = frames;
    }

    public int frames() {
        return frames;
    }

    public static EqCurve of(double[] controls, int frames) {
        EqCurve curve = new EqCurve(Math.max(0, frames));
        if (controls == null || controls.length < CONTROLS || frames <= 0) {
            for (int b = 0; b < BANDS; b++) {
                curve.gains[b] = new float[Math.max(0, frames)];
                java.util.Arrays.fill(curve.gains[b], 1.0f);
            }
            return curve;
        }
        for (int b = 0; b < BANDS; b++) {
            double[] c = new double[PER_BAND];
            for (int i = 0; i < PER_BAND; i++) {
                c[i] = clamp(controls[b * PER_BAND + i], 0.0, 1.0);
            }
            double[] m = secondDerivatives(c);
            float[] band = new float[frames];
            for (int f = 0; f < frames; f++) {
                double p = frames == 1 ? 0.0 : f / (double) (frames - 1);
                double v = sampleSpline(c, m, p);
                double db = MIN_DB[b] + clamp(v, 0.0, 1.0) * (MAX_DB - MIN_DB[b]);
                double linear = Math.pow(10.0, db / 20.0);
                band[f] = (float) softLimit(linear);
            }
            curve.gains[b] = band;
        }
        return curve;
    }

    public void gainsAt(double progress, float[] out) {
        double p = clamp(progress, 0.0, 1.0);
        double x = p * Math.max(0, frames - 1);
        int i = (int) Math.floor(x);
        int j = Math.min(frames - 1, i + 1);
        float t = (float) (x - i);
        for (int b = 0; b < BANDS; b++) {
            float[] g = gains[b];
            if (g.length == 0) {
                out[b] = 1.0f;
            } else {
                out[b] = g[i] + (g[j] - g[i]) * t;
            }
        }
    }

    float gain(int band, int frame) {
        float[] g = gains[band];
        if (g.length == 0) {
            return 1.0f;
        }
        return g[Math.max(0, Math.min(g.length - 1, frame))];
    }

    static double[] secondDerivatives(double[] y) {
        int n = y.length;
        double[] m = new double[n];
        if (n < 3) {
            return m;
        }
        double[] h = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            h[i] = CONTROL_P[i + 1] - CONTROL_P[i];
        }
        double[] a = new double[n];
        double[] b = new double[n];
        double[] c = new double[n];
        double[] d = new double[n];
        for (int i = 1; i < n - 1; i++) {
            a[i] = h[i - 1];
            b[i] = 2.0 * (h[i - 1] + h[i]);
            c[i] = h[i];
            d[i] = 6.0 * ((y[i + 1] - y[i]) / h[i] - (y[i] - y[i - 1]) / h[i - 1]);
        }
        double[] cp = new double[n];
        double[] dp = new double[n];
        cp[1] = c[1] / b[1];
        dp[1] = d[1] / b[1];
        for (int i = 2; i < n - 1; i++) {
            double den = b[i] - a[i] * cp[i - 1];
            cp[i] = c[i] / den;
            dp[i] = (d[i] - a[i] * dp[i - 1]) / den;
        }
        for (int i = n - 2; i >= 1; i--) {
            m[i] = dp[i] - cp[i] * m[i + 1];
        }
        return m;
    }

    static double sampleSpline(double[] y, double[] m, double p) {
        double x = clamp(p, 0.0, 1.0);
        int i = 0;
        for (int j = 0; j < y.length - 1; j++) {
            if (x >= CONTROL_P[j]) {
                i = j;
            }
        }
        double x0 = CONTROL_P[i];
        double x1 = CONTROL_P[i + 1];
        double h = x1 - x0;
        double t = h <= 0.0 ? 0.0 : (x - x0) / h;
        double t2 = t * t;
        double t3 = t2 * t;
        double h00 = 2 * t3 - 3 * t2 + 1;
        double h10 = t3 - 2 * t2 + t;
        double h01 = -2 * t3 + 3 * t2;
        double h11 = t3 - t2;
        return h00 * y[i] + h10 * h * m[i] + h01 * y[i + 1] + h11 * h * m[i + 1];
    }

    static double softLimit(double g) {
        return g > 1.0 ? 1.0 + Math.tanh(g - 1.0) * 0.5 : g;
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private EqCurve() {
        this(0);
    }
}
