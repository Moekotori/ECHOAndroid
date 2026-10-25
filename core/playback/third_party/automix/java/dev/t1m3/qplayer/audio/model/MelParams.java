package dev.t1m3.qplayer.audio.model;

public final class MelParams {
    public int sampleRate;

    public int nFft;

    public int hop;

    public int mels;

    public float fmin;

    public float fmax;

    public float logK;

    public MelParams(
            int sampleRate, int nFft, int hop, int mels, float fmin, float fmax, float logK) {
        this.sampleRate = sampleRate;
        this.nFft = nFft;
        this.hop = hop;
        this.mels = mels;
        this.fmin = fmin;
        this.fmax = fmax;
        this.logK = logK;
    }

    public MelParams(MelParams other) {
        this(
                other.sampleRate,
                other.nFft,
                other.hop,
                other.mels,
                other.fmin,
                other.fmax,
                other.logK);
    }

    public static MelParams defaults() {
        return new MelParams(22_050, 2048, 441, 128, 30.0f, 11_025.0f, 1000.0f);
    }

    public static MelParams beatThis() {
        return new MelParams(22_050, 1024, 441, 128, 30.0f, 11_025.0f, 1000.0f);
    }

    public void validate() {
        if (sampleRate == 0) {
            throw new IllegalArgumentException("sample rate cannot be 0");
        }
        if (!isPowerOfTwo(nFft) || nFft < 64) {
            throw new IllegalArgumentException("n_fft must be a power of two ≥64, got " + nFft);
        }
        if (hop == 0 || hop > nFft) {
            throw new IllegalArgumentException("hop must be in 1..=" + nFft + ", got " + hop);
        }
        if (mels == 0 || mels > 512) {
            throw new IllegalArgumentException("mels must be in 1..=512, got " + mels);
        }
        if (!(fmin >= 0.0f && fmax > fmin)) {
            throw new IllegalArgumentException("invalid frequency range: " + fmin + " .. " + fmax);
        }
        if (fmax > (float) sampleRate / 2.0f) {
            throw new IllegalArgumentException(
                    "fmax " + fmax + " exceeds Nyquist " + ((float) sampleRate / 2.0f));
        }
    }

    public int featureLen() {
        return mels;
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MelParams)) {
            return false;
        }
        MelParams p = (MelParams) o;
        return sampleRate == p.sampleRate
                && nFft == p.nFft
                && hop == p.hop
                && mels == p.mels
                && Float.compare(fmin, p.fmin) == 0
                && Float.compare(fmax, p.fmax) == 0
                && Float.compare(logK, p.logK) == 0;
    }

    @Override
    public int hashCode() {
        int h = sampleRate;
        h = 31 * h + nFft;
        h = 31 * h + hop;
        h = 31 * h + mels;
        h = 31 * h + Float.floatToIntBits(fmin);
        h = 31 * h + Float.floatToIntBits(fmax);
        h = 31 * h + Float.floatToIntBits(logK);
        return h;
    }

    @Override
    public String toString() {
        return "MelParams { sample_rate: "
                + sampleRate
                + ", n_fft: "
                + nFft
                + ", hop: "
                + hop
                + ", mels: "
                + mels
                + ", fmin: "
                + fmin
                + ", fmax: "
                + fmax
                + ", log_k: "
                + logK
                + " }";
    }
}
