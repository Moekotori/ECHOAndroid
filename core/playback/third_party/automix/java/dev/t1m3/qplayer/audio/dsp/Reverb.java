package dev.t1m3.qplayer.audio.dsp;

public final class Reverb {

    private static final int COMBS = 4;

    private static final int ALLPASSES = 2;

    private static final int[][] COMB_LEN_44K = {
        {1116, 1188, 1277, 1356},
        {1139, 1211, 1301, 1381},
    };
    private static final int[][] ALLPASS_LEN_44K = {
        {556, 441},
        {579, 464},
    };

    private static final float ALLPASS_G = 0.5f;

    private final int channels;
    private final float[][] combBuf;
    private final int[][] combSize;
    private final int[][] combIdx;
    private final float[][] combStore;
    private final float[] combG;
    private final float[][] apBuf;
    private final int[][] apSize;
    private final int[][] apIdx;
    private final float damp;

    public Reverb(int rate, int channels, float decaySecs, float damping) {
        this.channels = Math.max(1, channels);
        int r = Math.max(8000, rate);
        float decay = Math.max(0.1f, decaySecs);
        this.damp = Math.max(0f, Math.min(0.95f, damping));
        this.combBuf = new float[this.channels][];
        this.combSize = new int[this.channels][];
        this.combIdx = new int[this.channels][];
        this.combStore = new float[this.channels][];
        this.combG = new float[COMBS];
        this.apBuf = new float[this.channels][];
        this.apSize = new int[this.channels][];
        this.apIdx = new int[this.channels][];
        for (int c = 0; c < this.channels; c++) {
            int[] lens = COMB_LEN_44K[c % COMB_LEN_44K.length];
            combBuf[c] = new float[COMBS * 2048];
            combSize[c] = new int[COMBS];
            combIdx[c] = new int[COMBS];
            combStore[c] = new float[COMBS];
            for (int i = 0; i < COMBS; i++) {
                int len = Math.max(8, Math.round(lens[i] * (float) r / 44100f));
                combSize[c][i] = len;

                combIdx[c][i] = 0;
            }
            int[] alens = ALLPASS_LEN_44K[c % ALLPASS_LEN_44K.length];
            apBuf[c] = new float[ALLPASSES * 1024];
            apSize[c] = new int[ALLPASSES];
            apIdx[c] = new int[ALLPASSES];
            for (int i = 0; i < ALLPASSES; i++) {
                int len = Math.max(8, Math.round(alens[i] * (float) r / 44100f));
                apSize[c][i] = len;
                apIdx[c][i] = 0;
            }
        }
        for (int i = 0; i < COMBS; i++) {

            double len = COMB_LEN_44K[0][i];
            combG[i] = (float) Math.pow(10.0, -3.0 * len / (decay * 44100.0));
        }
    }

    public void reset() {
        for (int c = 0; c < channels; c++) {
            java.util.Arrays.fill(combBuf[c], 0f);
            java.util.Arrays.fill(combStore[c], 0f);
            java.util.Arrays.fill(apBuf[c], 0f);
            for (int i = 0; i < COMBS; i++) {
                combIdx[c][i] = 0;
            }
            for (int i = 0; i < ALLPASSES; i++) {
                apIdx[c][i] = 0;
            }
        }
    }

    public float process(int channel, float x) {
        int c = Math.min(Math.max(0, channel), channels - 1);
        float[] cb = combBuf[c];
        int[] cs = combSize[c];
        int[] ci = combIdx[c];
        float[] store = combStore[c];
        float sum = 0f;
        for (int i = 0; i < COMBS; i++) {
            int base = i * 2048;
            int idx = ci[i];
            float v = cb[base + idx];

            store[i] = v * (1f - damp) + store[i] * damp;
            cb[base + idx] = x + store[i] * combG[i];
            ci[i] = idx + 1 >= cs[i] ? 0 : idx + 1;
            sum += v;
        }
        sum *= 1f / COMBS;
        float[] ab = apBuf[c];
        int[] as = apSize[c];
        int[] ai = apIdx[c];
        for (int i = 0; i < ALLPASSES; i++) {
            int base = i * 1024;
            int idx = ai[i];
            float buffered = ab[base + idx];
            float out = -sum + buffered;
            ab[base + idx] = sum + buffered * ALLPASS_G;
            ai[i] = idx + 1 >= as[i] ? 0 : idx + 1;
            sum = out;
        }
        return sum;
    }
}
