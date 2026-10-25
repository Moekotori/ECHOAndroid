package dev.t1m3.qplayer.audio.dsp;

public final class Fft {

    private static final float PI = (float) Math.PI;

    private final int n;

    private final int[] rev;

    private final float[] cos;

    private final float[] sin;

    public Fft(int n) {
        if (n <= 0 || (n & (n - 1)) != 0) {
            throw new IllegalArgumentException("FFT length must be a power of two, got " + n);
        }

        int bits = Integer.numberOfTrailingZeros(n);
        int[] rev = new int[n];
        for (int i = 0; i < n; i++) {

            int x = i;
            int v = 0;
            for (int b = 0; b < bits; b++) {
                v = (v << 1) | (x & 1);
                x >>= 1;
            }
            rev[i] = v;
        }

        int half = n / 2;
        float[] cos = new float[half];
        float[] sin = new float[half];
        for (int k = 0; k < half; k++) {

            float theta = 2.0f * PI * k / n;
            cos[k] = (float) Math.cos(theta);

            sin[k] = -(float) Math.sin(theta);
        }

        this.n = n;
        this.rev = rev;
        this.cos = cos;
        this.sin = sin;
    }

    public int len() {
        return n;
    }

    public boolean isEmpty() {
        return false;
    }

    public void forward(float[] re, float[] im) {
        transform(re, im, false);
    }

    public void inverse(float[] re, float[] im) {
        transform(re, im, true);
        float inv = 1.0f / n;
        for (int i = 0; i < re.length; i++) {
            re[i] *= inv;
        }
        for (int i = 0; i < im.length; i++) {
            im[i] *= inv;
        }
    }

    private void transform(float[] re, float[] im, boolean conjugate) {
        if (re.length != n) {
            throw new IllegalArgumentException("real part length must equal transform length: " + re.length + " != " + n);
        }
        if (im.length != n) {
            throw new IllegalArgumentException("imaginary part length must equal transform length: " + im.length + " != " + n);
        }

        for (int i = 0; i < n; i++) {
            int j = rev[i];
            if (j > i) {
                float tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                float ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }

        for (long size = 2; size <= n; size <<= 1) {
            int half = (int) (size / 2);
            int step = n / (int) size;
            for (int i = 0; i < n; i += (int) size) {
                int k = 0;
                for (int j = i; j < i + half; j++) {
                    int t = k * step;
                    float wr = cos[t];

                    float wi = conjugate ? -sin[t] : sin[t];

                    float xr = re[j + half] * wr - im[j + half] * wi;
                    float xi = re[j + half] * wi + im[j + half] * wr;

                    re[j + half] = re[j] - xr;
                    im[j + half] = im[j] - xi;
                    re[j] += xr;
                    im[j] += xi;
                    k += 1;
                }
            }
        }
    }

    public void magnitude(float[] re, float[] im, float[] out) {
        int half = n / 2;
        int take = Math.min(out.length, half + 1);
        float inv = 1.0f / n;
        for (int k = 0; k < take; k++) {
            float m = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]) * inv;
            out[k] = (k == 0 || k == half) ? m : m * 2.0f;
        }
        for (int k = take; k < out.length; k++) {
            out[k] = 0.0f;
        }
    }

    public static void hann(float[] out) {
        int n = out.length;
        if (n == 0) {
            return;
        }
        if (n == 1) {
            out[0] = 1.0f;
            return;
        }
        for (int k = 0; k < n; k++) {
            float t = 2.0f * PI * k / n;

            out[k] = 0.5f - 0.5f * (float) Math.cos(t);
        }
    }

    public static void autocorrelate(float[] x, int maxLag, float[] out) {
        int n = x.length;
        int take = Math.min(out.length, maxLag);
        for (int lag = 0; lag < take; lag++) {
            if (lag >= n) {
                out[lag] = 0.0f;
                continue;
            }
            float s = 0.0f;
            for (int i = 0; i < n - lag; i++) {
                s += x[i] * x[i + lag];
            }

            out[lag] = s / (n - lag);
        }
    }

    @Override
    public String toString() {
        return "Fft{n=" + n + ", half=" + cos.length + "}";
    }
}
