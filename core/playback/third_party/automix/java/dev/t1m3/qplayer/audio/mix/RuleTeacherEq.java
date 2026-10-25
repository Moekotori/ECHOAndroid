package dev.t1m3.qplayer.audio.mix;

public final class RuleTeacherEq {

    public static final double CUT_DURATION_SECS = 2.0;

    static final double GATE_K = 30.0;

    private static final double EPS = 1e-6;

    private RuleTeacherEq() {
    }

    static double[] thresholds(double[] genres) {
        double edm = genres[0];
        double hiphop = genres[1];
        double dnb = genres[2];
        double rock = genres[3];
        double def = genres[4];
        double threshold = edm * 0.85 + hiphop * 0.75 + dnb * 0.8 + rock * 0.6 + def * 0.7;
        double cutTarget = edm * 0.30 + hiphop * 0.35 + dnb * 0.30 + rock * 0.40 + def * 0.40;
        return new double[] {threshold, cutTarget};
    }

    public static void curves(float[][] features, int frames, double windowSecs, float[][] out) {
        double dropP = clamp(features[0][29], 0.0, 1.0);
        double[] genres = new double[5];
        for (int k = 0; k < 5; k++) {
            genres[k] = features[0][31 + k];
        }
        double[] th = thresholds(genres);
        double cutDurationP = CUT_DURATION_SECS / Math.max(windowSecs, 1e-3);
        double cutStartP = Math.max(0.0, dropP - cutDurationP);

        for (int f = 0; f < frames; f++) {
            double conflict = features[f][0] * features[f][3];
            double gate = sigmoid((conflict - th[0]) * GATE_K);
            double p = clamp(features[f][8], 0.0, 1.0);
            double progress = (p - cutStartP) / Math.max(EPS, dropP - cutStartP);
            double low;
            if (p < cutStartP) {
                low = 1.0;
            } else if (p < dropP) {
                low = 1.0 - progress * gate * (1.0 - th[1]);
            } else {
                low = th[1] + (1.0 - th[1]) * Math.min(1.0, (p - dropP) * 4.0);
            }
            out[f][0] = (float) clamp(low, th[1], 1.0);
            out[f][1] = 1.0f;
            out[f][2] = 1.0f;
        }
    }

    public static double[] controlsFrom(float[][] features, int frames, double windowSecs) {
        float[][] curves = new float[frames][3];
        curves(features, frames, windowSecs, curves);
        double[] controls = new double[EqCurve.CONTROLS];
        double[] controlP = EqCurve.CONTROL_P;
        for (int band = 0; band < EqCurve.BANDS; band++) {
            for (int i = 0; i < EqCurve.PER_BAND; i++) {
                int frame = (int) Math.round(controlP[i] * Math.max(0, frames - 1));
                controls[band * EqCurve.PER_BAND + i] = curves[frame][band];
            }

            if (band > 0) {
                for (int i = 0; i < EqCurve.PER_BAND; i++) {
                    controls[band * EqCurve.PER_BAND + i] = 1.0;
                }
                continue;
            }

            controls[band * EqCurve.PER_BAND + EqCurve.PER_BAND - 1] = 1.0;
        }
        return controls;
    }

    static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-Math.max(-60.0, Math.min(60.0, x))));
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
