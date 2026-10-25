package dev.t1m3.qplayer.audio.mix;

public final class EqFeatureExtractor {

    public static final int DIM = 36;

    static final int LOOK = 5;

    private static final double EPS = 1e-6;

    private EqFeatureExtractor() {
    }

    public static final class Side {

        public final float[][] band;

        public final float[] rms;

        public final float[] centroid;

        public final float[] zcr;

        public Side(float[][] band, float[] rms, float[] centroid, float[] zcr) {
            this.band = band;
            this.rms = rms;
            this.centroid = centroid;
            this.zcr = zcr;
        }

        int frames() {
            return band == null ? 0 : band.length;
        }

        float bandAt(int frame, int k) {
            float[] row = band[frame];
            return k < row.length ? row[k] : 0f;
        }

        float centroidAt(int frame) {
            if (centroid != null && frame < centroid.length) {
                return clamp01(centroid[frame]);
            }
            if (zcr != null && frame < zcr.length) {
                return clamp01(zcr[frame]);
            }
            return 0f;
        }

        float lowRatio(int frame) {
            float low = bandAt(frame, 0);
            float sum = low + bandAt(frame, 1) + bandAt(frame, 2);
            return (float) (low / Math.max(sum, EPS));
        }
    }

    static double onsetStrength(Side side, int frames) {
        if (frames < 2) {
            return 0.0;
        }
        double peak = 0.0;
        double sum = 0.0;
        for (int f = 1; f < frames; f++) {
            double d = 0.0;
            for (int k = 0; k < 3; k++) {
                d += Math.max(0.0, side.bandAt(f, k) - side.bandAt(f - 1, k));
            }
            peak = Math.max(peak, d);
            sum += d;
        }
        double mean = sum / Math.max(1, frames - 1);
        return (peak - mean) / (peak + mean + EPS);
    }

    static double[] genreScores(double bpm, double onset, double lowRatio, double midRatio) {
        double edm = sigmoid((bpm - 115.0) / 5.0) * onset;
        double hiphop = sigmoid((90.0 - bpm) / 5.0) * onset * lowRatio;
        double dnb = sigmoid((bpm - 160.0) / 5.0) * onset;
        double rock = sigmoid((bpm - 105.0) / 8.0) * (1.0 - onset) * midRatio;
        double def = 1.0 - Math.max(Math.max(edm, hiphop), Math.max(dnb, rock));
        double[] s = {Math.max(0.0, edm), Math.max(0.0, hiphop), Math.max(0.0, dnb),
                Math.max(0.0, rock), Math.max(0.0, def)};
        double sum = s[0] + s[1] + s[2] + s[3] + s[4];
        for (int i = 0; i < 5; i++) {
            s[i] = s[i] / Math.max(sum, EPS);
        }
        return s;
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-Math.max(-60.0, Math.min(60.0, x))));
    }

    public static int extract(Side a, Side b, double windowSecs, double bpmA, double bpmB,
                              double keyMatch, float[][] out) {
        int frames = Math.min(Math.min(a.frames(), b.frames()), out.length);
        if (frames <= 0) {
            return 0;
        }
        double bpmGap = Math.abs(bpmA - bpmB) / Math.max((bpmA + bpmB) / 2.0, 1e-3);
        double winNorm = windowSecs / 8.0;
        double bpmAbs = bpmA / 180.0;
        double onset = onsetStrength(a, frames);

        double lowSum = 0.0;
        double midSum = 0.0;
        double energyPeak = 0.0;
        double energySum = 0.0;
        int peakFrame = 0;
        for (int f = 0; f < frames; f++) {
            lowSum += a.lowRatio(f);
            midSum += a.bandAt(f, 1) / Math.max(a.bandAt(f, 0) + a.bandAt(f, 1) + a.bandAt(f, 2), EPS);
            double energy = a.bandAt(f, 0) + a.bandAt(f, 1) + a.bandAt(f, 2)
                    + b.bandAt(f, 0) + b.bandAt(f, 1) + b.bandAt(f, 2);
            energySum += energy;
            if (energy > energyPeak) {
                energyPeak = energy;
                peakFrame = f;
            }
        }
        double lowRatioMean = lowSum / frames;
        double midRatioMean = midSum / frames;
        double[] genres = genreScores(bpmA, onset, lowRatioMean, midRatioMean);
        double peakPos = peakFrame / (double) Math.max(1, frames);
        double peakAmp = Math.min(5.0, energyPeak / Math.max(energySum / frames, 1e-3));

        for (int f = 0; f < frames; f++) {
            float[] row = out[f];
            int past = Math.max(0, f - LOOK);
            int future = Math.min(frames - 1, f + LOOK);
            row[0] = a.bandAt(f, 0);
            row[1] = a.bandAt(f, 1);
            row[2] = a.bandAt(f, 2);
            row[3] = b.bandAt(f, 0);
            row[4] = b.bandAt(f, 1);
            row[5] = b.bandAt(f, 2);
            row[6] = a.centroidAt(f);
            row[7] = b.centroidAt(f);
            row[8] = (float) (f / (double) Math.max(1, frames));
            row[9] = (float) winNorm;
            row[10] = (float) bpmGap;
            row[11] = (float) keyMatch;
            row[12] = (float) onset;
            row[13] = (float) bpmAbs;
            row[14] = a.lowRatio(f);
            row[15] = b.lowRatio(f);
            row[16] = flux(a, f);
            for (int k = 0; k < 3; k++) {
                row[17 + k] = a.bandAt(f, k) - a.bandAt(past, k);
                row[20 + k] = b.bandAt(f, k) - b.bandAt(past, k);
                row[23 + k] = a.bandAt(future, k) - a.bandAt(f, k);
                row[26 + k] = b.bandAt(future, k) - b.bandAt(f, k);
            }
            row[29] = (float) peakPos;
            row[30] = (float) peakAmp;
            for (int k = 0; k < 5; k++) {
                row[31 + k] = (float) genres[k];
            }
        }
        return frames;
    }

    public static float[] centroidStandIn(float[] rms, int from, int frames) {
        float[] out = new float[Math.max(0, frames)];
        for (int f = 0; f < out.length; f++) {
            float cur = rms[from + f];
            float prev = rms[Math.max(0, from + f - 1)];
            out[f] = Math.max(0f, Math.min(1f, (cur - prev) * 4f + 0.5f));
        }
        return out;
    }

    private static float flux(Side side, int frame) {
        if (frame == 0) {
            return 0f;
        }
        float sum = 0f;
        for (int k = 0; k < 3; k++) {
            sum += Math.max(0f, side.bandAt(frame, k) - side.bandAt(frame - 1, k));
        }
        return sum;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
