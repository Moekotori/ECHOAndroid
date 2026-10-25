package dev.t1m3.qplayer.audio.studio;

public final class StudioBiquad {
    private double b0 = 1;
    private double b1;
    private double b2;
    private double a1;
    private double a2;
    private double z1;
    private double z2;

    private StudioBiquad() {}

    public static StudioBiquad unity() {
        return new StudioBiquad();
    }

    public static StudioBiquad fromCoeffs(double b0, double b1, double b2, double a1, double a2) {
        StudioBiquad f = new StudioBiquad();
        f.b0 = b0;
        f.b1 = b1;
        f.b2 = b2;
        f.a1 = a1;
        f.a2 = a2;
        return f;
    }

    public double process(double x) {
        double y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }

    public void reset() {
        z1 = 0;
        z2 = 0;
    }

    public void updateFrom(StudioBiquad other) {
        this.b0 = other.b0;
        this.b1 = other.b1;
        this.b2 = other.b2;
        this.a1 = other.a1;
        this.a2 = other.a2;
    }

    public double[] coeffs() {
        return new double[] {b0, b1, b2, a1, a2};
    }

    public static StudioBiquad peaking(int rate, double freq, double q, double gainDb) {
        double[] pw = prewarp(rate, freq, q);
        double cos = pw[0];
        double alpha = pw[1];
        double a = Math.pow(10.0, gainDb / 40.0);
        double a0 = 1 + alpha / a;
        return fromCoeffs(
                (1 + alpha * a) / a0,
                -2 * cos / a0,
                (1 - alpha * a) / a0,
                -2 * cos / a0,
                (1 - alpha / a) / a0);
    }

    public static StudioBiquad lowShelf(int rate, double freq, double q, double gainDb) {
        double[] pw = prewarp(rate, freq, q);
        double cos = pw[0];
        double alpha = pw[1];
        double a = Math.pow(10.0, gainDb / 40.0);
        double sqrtA = Math.sqrt(a);
        double a0 = (a + 1) + (a - 1) * cos + 2 * sqrtA * alpha;
        return fromCoeffs(
                a * ((a + 1) - (a - 1) * cos + 2 * sqrtA * alpha) / a0,
                2 * a * ((a - 1) - (a + 1) * cos) / a0,
                a * ((a + 1) - (a - 1) * cos - 2 * sqrtA * alpha) / a0,
                -2 * ((a - 1) + (a + 1) * cos) / a0,
                ((a + 1) + (a - 1) * cos - 2 * sqrtA * alpha) / a0);
    }

    public static StudioBiquad highShelf(int rate, double freq, double q, double gainDb) {
        double[] pw = prewarp(rate, freq, q);
        double cos = pw[0];
        double alpha = pw[1];
        double a = Math.pow(10.0, gainDb / 40.0);
        double sqrtA = Math.sqrt(a);
        double a0 = (a + 1) - (a - 1) * cos + 2 * sqrtA * alpha;
        return fromCoeffs(
                a * ((a + 1) + (a - 1) * cos + 2 * sqrtA * alpha) / a0,
                -2 * a * ((a - 1) + (a + 1) * cos) / a0,
                a * ((a + 1) + (a - 1) * cos - 2 * sqrtA * alpha) / a0,
                2 * ((a - 1) - (a + 1) * cos) / a0,
                ((a + 1) - (a - 1) * cos - 2 * sqrtA * alpha) / a0);
    }

    public static StudioBiquad highPass(int rate, double freq, double q) {
        double[] pw = prewarp(rate, freq, q);
        double cos = pw[0];
        double alpha = pw[1];
        double a0 = 1 + alpha;
        return fromCoeffs(
                (1 + cos) / 2 / a0,
                -(1 + cos) / a0,
                (1 + cos) / 2 / a0,
                -2 * cos / a0,
                (1 - alpha) / a0);
    }

    public static StudioBiquad lowPass(int rate, double freq, double q) {
        double[] pw = prewarp(rate, freq, q);
        double cos = pw[0];
        double alpha = pw[1];
        double a0 = 1 + alpha;
        return fromCoeffs(
                (1 - cos) / 2 / a0,
                (1 - cos) / a0,
                (1 - cos) / 2 / a0,
                -2 * cos / a0,
                (1 - alpha) / a0);
    }

    public static StudioBiquad bandPass(int rate, double freq, double q) {
        double[] pw = prewarp(rate, freq, q);
        double cos = pw[0];
        double alpha = pw[1];
        double a0 = 1 + alpha;
        return fromCoeffs(alpha / a0, 0, -alpha / a0, -2 * cos / a0, (1 - alpha) / a0);
    }

    public static StudioBiquad notch(int rate, double freq, double q) {
        double[] pw = prewarp(rate, freq, q);
        double cos = pw[0];
        double alpha = pw[1];
        double a0 = 1 + alpha;
        return fromCoeffs(1 / a0, -2 * cos / a0, 1 / a0, -2 * cos / a0, (1 - alpha) / a0);
    }

    public static StudioBiquad allPass(int rate, double freq, double q) {
        double[] pw = prewarp(rate, freq, q);
        double cos = pw[0];
        double alpha = pw[1];
        double a0 = 1 + alpha;
        return fromCoeffs(
                (1 - alpha) / a0, -2 * cos / a0, (1 + alpha) / a0, -2 * cos / a0, (1 - alpha) / a0);
    }

    private static double[] prewarp(int rate, double freq, double q) {
        double rateF = Math.max(rate, 2);
        double nyquist = rateF * 0.5;
        double hi = Math.max(nyquist * 0.999, 1.0);
        double lo = Math.min(hi, 1.0);
        double f = Math.max(lo, Math.min(hi, freq));
        double w0 = 2 * Math.PI * f / rateF;
        double qq = Double.isNaN(q) ? 0.05 : Math.max(q, 0.05);
        return new double[] {Math.cos(w0), Math.sin(w0) / (2 * qq)};
    }
}
