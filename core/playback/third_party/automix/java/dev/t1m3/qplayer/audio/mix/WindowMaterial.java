package dev.t1m3.qplayer.audio.mix;

import java.util.Map;

public final class WindowMaterial {

    private WindowMaterial() {
    }

    public static float[][] process(String path, Map<String, String> headers, double startSecs,
                                    int frames, int rate, int vocalMode, double[] duckRanges,
                                    double anchorSecs, double targetLufs) {
        float[][] material;
        try {
            material = Automix.readStereoWindow(path, headers, startSecs, frames, rate);
        } catch (RuntimeException e) {

            return null;
        }
        if (material == null || material.length == 0 || frames <= 0 || rate <= 0) {
            return material;
        }
        float[][] result = material;

        if (vocalMode != 0 && duckRanges != null && duckRanges.length >= 2) {
            StemMaterial stems = StemMaterials.forWindow(path, headers, startSecs, frames, rate,
                    vocalMode);
            if (stems != null && stems.hasStems() && stems.frames() >= frames) {
                float[] gain = new float[frames];
                for (int i = 0; i < frames; i++) {
                    double timeline = (startSecs - anchorSecs) + i / (double) rate;
                    gain[i] = (float) duckGainAt(duckRanges, timeline);
                }
                result = stems.mixWith(gain);
            }
        }

        if (!Double.isNaN(targetLufs)) {
            alignLoudness(result, rate, targetLufs);
        }
        return result;
    }

    static double duckGainAt(double[] ranges, double timelineSecs) {
        if (ranges == null || ranges.length < 2) {
            return 1.0;
        }
        double gain = 1.0;
        for (int i = 0; i + 1 < ranges.length; i += 2) {
            double a = ranges[i];
            double b = ranges[i + 1];
            if (b <= a || timelineSecs <= a || timelineSecs >= b) {
                continue;
            }
            double edge = 0.05;
            double in = Math.min(1.0, (timelineSecs - a) / edge);
            double out = Math.min(1.0, (b - timelineSecs) / edge);
            double depth = 0.5 - 0.5 * Math.cos(Math.PI * Math.min(in, out));
            gain = Math.min(gain, 1.0 - depth);
        }
        return Math.max(0.0, Math.min(1.0, gain));
    }

    static double alignLoudness(float[][] material, int rate, double targetLufs) {
        if (Double.isNaN(targetLufs)) {
            return 1.0;
        }
        double sum = 0.0;
        int n = 0;
        for (float[] ch : material) {
            if (ch == null || ch.length == 0) {
                continue;
            }
            double lufs = dev.t1m3.qplayer.audio.dsp.Lufs.integrated(ch, null, rate);
            if (!Double.isInfinite(lufs) && !Double.isNaN(lufs)) {
                sum += lufs;
                n++;
            }
        }
        if (n == 0) {
            return 1.0;
        }
        double current = sum / n;
        double db = Math.max(-dev.t1m3.qplayer.audio.dsp.Lufs.MAX_NORMALIZE_DB,
                Math.min(dev.t1m3.qplayer.audio.dsp.Lufs.MAX_NORMALIZE_DB, targetLufs - current));
        double gain = Math.pow(10.0, db / 20.0);
        if (Math.abs(gain - 1.0) < 1e-9) {
            return 1.0;
        }
        for (float[] ch : material) {
            if (ch == null) {
                continue;
            }
            for (int i = 0; i < ch.length; i++) {
                ch[i] = (float) (ch[i] * gain);
            }
        }
        return gain;
    }
}
