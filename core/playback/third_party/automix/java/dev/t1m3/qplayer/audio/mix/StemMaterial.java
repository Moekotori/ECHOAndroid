package dev.t1m3.qplayer.audio.mix;

public final class StemMaterial {

    public final float[][] instrumental;

    public final float[] vocals;

    public StemMaterial(float[][] instrumental, float[] vocals) {
        if (instrumental == null || instrumental.length == 0) {
            throw new IllegalArgumentException("the instrumental track must not be empty");
        }
        this.instrumental = instrumental;
        this.vocals = vocals;
    }

    public int frames() {
        return instrumental[0] == null ? 0 : instrumental[0].length;
    }

    public boolean hasStems() {
        return vocals != null;
    }

    public float[][] mixWith(float[] gainPerFrame) {
        int n = frames();
        float[][] out = new float[instrumental.length][n];
        for (int c = 0; c < instrumental.length; c++) {
            System.arraycopy(instrumental[c], 0, out[c], 0, n);
        }
        if (vocals == null) {
            return out;
        }

        int m = Math.min(n, vocals.length);
        if (gainPerFrame != null) {
            m = Math.min(m, gainPerFrame.length);
        }

        for (int c = 0; c < out.length; c++) {
            for (int i = 0; i < m; i++) {
                float g = gainPerFrame == null ? 1.0f : gainPerFrame[i];
                if (g == 0.0f) {
                    continue;
                }
                out[c][i] += (float) (vocals[i] * g);
            }
        }
        return out;
    }

    public static StemMaterial withoutStems(float[][] material) {
        return new StemMaterial(material, null);
    }

    public static float[] rampGain(int frames, int from, int to, float gain) {
        float[] g = new float[frames];
        for (int i = 0; i < frames; i++) {
            g[i] = (i >= from && i < to) ? gain : 1.0f;
        }
        return g;
    }
}
