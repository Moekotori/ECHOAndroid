package dev.t1m3.qplayer.audio.dsp;

public final class Lufs {

    public static final double ABSOLUTE_GATE = -70.0;

    public static final double RELATIVE_GATE = -10.0;

    static final double BLOCK_SECS = 0.4;
    static final double HOP_SECS = 0.1;

    private Lufs() {
    }

    public static double integrated(float[] left, float[] right, int rate) {
        if (rate <= 0 || left == null || left.length == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        int frames = Math.min(left.length, right == null ? left.length : right.length);
        float[] l = kWeight(left, rate, frames);
        float[] r = right == null ? null : kWeight(right, rate, frames);
        int block = Math.max(1, (int) Math.round(BLOCK_SECS * rate));
        int hop = Math.max(1, (int) Math.round(HOP_SECS * rate));
        if (frames < block) {
            return Double.NEGATIVE_INFINITY;
        }

        java.util.List<Double> powers = new java.util.ArrayList<Double>();
        java.util.List<Double> loudness = new java.util.ArrayList<Double>();
        for (int at = 0; at + block <= frames; at += hop) {
            double sum = 0.0;
            for (int i = at; i < at + block; i++) {
                sum += (double) l[i] * l[i];
                if (r != null) {
                    sum += (double) r[i] * r[i];
                }
            }
            double z = sum / block;
            double level = -0.691 + 10.0 * Math.log10(Math.max(z, 1e-12));
            if (level > ABSOLUTE_GATE) {
                powers.add(Double.valueOf(z));
                loudness.add(Double.valueOf(level));
            }
        }
        if (powers.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        double mean = 0.0;
        for (Double p : powers) {
            mean += p.doubleValue();
        }
        mean /= powers.size();
        double relative = -0.691 + 10.0 * Math.log10(Math.max(mean, 1e-12)) + RELATIVE_GATE;
        double kept = 0.0;
        int n = 0;
        for (int i = 0; i < powers.size(); i++) {
            if (loudness.get(i).doubleValue() > relative) {
                kept += powers.get(i).doubleValue();
                n++;
            }
        }
        if (n == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        return -0.691 + 10.0 * Math.log10(Math.max(kept / n, 1e-12));
    }

    private static volatile boolean normalizationEnabled = false;

    public static void setNormalizationEnabled(boolean on) {
        normalizationEnabled = on;
    }

    public static boolean normalizationEnabled() {
        return normalizationEnabled;
    }

    public static final double MAX_NORMALIZE_DB = 12.0;

    public static double gainToMatch(float[] source, float[] target, int rate) {
        double a = integrated(source, null, rate);
        double b = integrated(target, null, rate);
        if (Double.isInfinite(a) || Double.isInfinite(b) || Double.isNaN(a) || Double.isNaN(b)) {
            return 1.0;
        }
        double db = b - a;
        db = Math.max(-MAX_NORMALIZE_DB, Math.min(MAX_NORMALIZE_DB, db));
        return Math.pow(10.0, db / 20.0);
    }

    public static boolean normalizeTo(float[] source, float[] target, int rate) {
        double g = gainToMatch(source, target, rate);
        if (Math.abs(g - 1.0) < 1e-9) {
            return false;
        }
        for (int i = 0; i < source.length; i++) {
            source[i] = (float) (source[i] * g);
        }
        return true;
    }

    static float[] kWeight(float[] pcm, int rate, int frames) {
        float[] out = shelf(pcm, rate, frames);
        return highPass(out, rate, frames);
    }

    private static float[] shelf(float[] pcm, int rate, int frames) {
        double f0 = 1681.974450955533;
        double gainDb = 3.999843853973347;
        double q = 0.7071752369554196;
        double k = Math.tan(Math.PI * f0 / rate);
        double vh = Math.pow(10.0, gainDb / 20.0);
        double vb = Math.pow(vh, 0.4996667741545416);
        double a0 = 1.0 + k / q + k * k;
        double b0 = (vh + vb * k / q + k * k) / a0;
        double b1 = 2.0 * (k * k - vh) / a0;
        double b2 = (vh - vb * k / q + k * k) / a0;
        double a1 = 2.0 * (k * k - 1.0) / a0;
        double a2 = (1.0 - k / q + k * k) / a0;
        return biquad(pcm, frames, b0, b1, b2, a1, a2);
    }

    private static float[] highPass(float[] pcm, int rate, int frames) {
        double f0 = 38.13547087602444;
        double q = 0.5003270373238773;
        double k = Math.tan(Math.PI * f0 / rate);
        double a0 = 1.0 + k / q + k * k;
        double b0 = 1.0 / a0;
        double b1 = -2.0 / a0;
        double b2 = 1.0 / a0;
        double a1 = 2.0 * (k * k - 1.0) / a0;
        double a2 = (1.0 - k / q + k * k) / a0;
        return biquad(pcm, frames, b0, b1, b2, a1, a2);
    }

    private static float[] biquad(float[] pcm, int frames, double b0, double b1, double b2,
                                  double a1, double a2) {
        float[] out = new float[frames];
        double x1 = 0.0;
        double x2 = 0.0;
        double y1 = 0.0;
        double y2 = 0.0;
        for (int i = 0; i < frames; i++) {
            double x = pcm[i];
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            out[i] = (float) y;
        }
        return out;
    }
}
