package dev.t1m3.qplayer.audio.mix;

public final class WindowTicket {

    public final double anchorSecs;

    private final float[] vocalGain;

    public final double loudnessGain;

    private WindowTicket(double anchorSecs, float[] vocalGain, double loudnessGain) {
        this.anchorSecs = anchorSecs;
        this.vocalGain = vocalGain == null ? null : vocalGain.clone();
        this.loudnessGain = loudnessGain;
    }

    public static WindowTicket identity(double anchorSecs) {
        return new WindowTicket(anchorSecs, null, 1.0);
    }

    public static WindowTicket build(float[][] window, int rate, double[] duckRanges,
                                     double anchorSecs, double targetLufs) {
        float[] gain = null;
        boolean duck = duckRanges != null && duckRanges.length >= 2 && rate > 0;
        if (duck) {
            int frames = window == null || window.length == 0 || window[0] == null
                    ? 0 : window[0].length;
            if (frames > 0) {
                gain = new float[frames];
                for (int i = 0; i < frames; i++) {
                    gain[i] = (float) WindowMaterial.duckGainAt(duckRanges, i / (double) rate);
                }
            } else {
                duck = false;
            }
        }
        double loud = 1.0;
        if (!Double.isNaN(targetLufs) && window != null && window.length > 0) {

            float[][] copy = new float[window.length][];
            for (int c = 0; c < window.length; c++) {
                copy[c] = window[c] == null ? null : window[c].clone();
            }
            loud = WindowMaterial.alignLoudness(copy, rate, targetLufs);
        }
        return new WindowTicket(anchorSecs, gain, loud);
    }

    public boolean doesAnything() {
        return vocalGain != null || Math.abs(loudnessGain - 1.0) > 1e-9;
    }

    public float vocalGainAt(double timelineSecs, int rate) {
        if (vocalGain == null || rate <= 0) {
            return 1.0f;
        }
        int idx = (int) Math.floor(timelineSecs * rate);
        if (idx < 0 || idx >= vocalGain.length) {
            return 1.0f;
        }
        return vocalGain[idx];
    }

    public boolean applyLoudness(float[][] chunk, int frames) {
        if (chunk == null || Math.abs(loudnessGain - 1.0) < 1e-9) {
            return false;
        }
        float g = (float) loudnessGain;
        for (float[] ch : chunk) {
            if (ch == null) {
                continue;
            }
            int n = Math.min(frames, ch.length);
            for (int i = 0; i < n; i++) {
                ch[i] = ch[i] * g;
            }
        }
        return true;
    }

    public boolean applyVocals(float[][] chunk, int frames, double chunkStartSecs, int rate,
                              dev.t1m3.qplayer.audio.model.StemSeparator sep) {
        if (chunk == null || chunk.length < 2 || sep == null || vocalGain == null || rate <= 0) {
            return false;
        }
        float[] left = chunk[0];
        float[] right = chunk[1];
        if (left == null || right == null) {
            return false;
        }
        dev.t1m3.qplayer.audio.model.StemSeparator.StemSet set;
        try {
            set = sep.separate(left, right, rate);
        } catch (RuntimeException e) {
            return false;
        }
        if (set == null || set.vocalsLeft == null || set.instrumentalLeft == null) {
            return false;
        }
        int n = Math.min(frames, Math.min(left.length, set.vocalsLeft.length));
        for (int i = 0; i < n; i++) {
            double timeline = (chunkStartSecs - anchorSecs) + i / (double) rate;
            float gain = vocalGainAt(timeline, rate);
            if (gain == 1.0f) {
                continue;
            }
            float vocL = set.vocalsLeft[i];
            float vocR = set.vocalsRight == null ? vocL : set.vocalsRight[i];
            float insL = set.instrumentalLeft[i];
            float insR = set.instrumentalRight == null ? insL : set.instrumentalRight[i];
            left[i] = (float) (insL + vocL * gain);
            right[i] = (float) (insR + vocR * gain);
        }
        return true;
    }

    public int vocalGainFrames() {
        return vocalGain == null ? 0 : vocalGain.length;
    }
}
