package dev.t1m3.qplayer.audio.mix;

public final class MaterialProfile {

    static final double FRAME_HZ = 50.0;

    static final double LOW_HZ = 200.0;

    public static final MaterialProfile UNKNOWN =
            new MaterialProfile(false, 0f, 0f, 0f, 0f, 0f, false, 0f, 0f);

    private final boolean known;
    private final float lowClash;
    private final float dropReliable;
    private final float tailDecayDbPerSec;
    private final float headOnset;
    private final float overlapSecs;
    private final boolean buildUp;

    private final float lowRatio;

    private final float onset;

    MaterialProfile(boolean known, float lowClash, float dropReliable,
                    float tailDecayDbPerSec, float headOnset, float overlapSecs,
                    boolean buildUp, float lowRatio, float onset) {
        this.known = known;
        this.lowClash = lowClash;
        this.dropReliable = dropReliable;
        this.tailDecayDbPerSec = tailDecayDbPerSec;
        this.headOnset = headOnset;
        this.overlapSecs = overlapSecs;
        this.buildUp = buildUp;
        this.lowRatio = lowRatio;
        this.onset = onset;
    }

    public float lowRatio() {
        return lowRatio;
    }

    public float onset() {
        return onset;
    }

    public boolean known() {
        return known;
    }

    public float lowClash() {
        return lowClash;
    }

    public float dropReliable() {
        return dropReliable;
    }

    public float tailDecayDbPerSec() {
        return tailDecayDbPerSec;
    }

    public float headOnset() {
        return headOnset;
    }

    public float overlapSecs() {
        return overlapSecs;
    }

    public boolean buildUp() {
        return buildUp;
    }

    @Override
    public String toString() {
        if (!known) {
            return "material profile: unknown";
        }
        return String.format(java.util.Locale.ROOT,
                "material profile: low-end clash %.2f, drop salience %.2f, A tail slope %+.1f dB/s, "
                        + "B jump %.2f, overlappable %.1fs, build-up %s",
                Float.valueOf(lowClash), Float.valueOf(dropReliable),
                Float.valueOf(tailDecayDbPerSec), Float.valueOf(headOnset),
                Float.valueOf(overlapSecs), buildUp ? "yes" : "no");
    }

    public static MaterialProfile of(float[] tail, float[] head, double tailSecs,
                                     double headSecs, int rate) {
        if (tail == null || head == null || rate <= 0 || tail.length < 2 || head.length < 2) {
            return UNKNOWN;
        }
        int hop = Math.max(1, (int) Math.round(rate / FRAME_HZ));
        float[] envTail = envelope(tail, hop);
        float[] envHead = envelope(head, hop);
        float[] lowTail = lowEnvelope(tail, hop, rate);
        float[] lowHead = lowEnvelope(head, hop, rate);
        if (envTail.length < 4 || envHead.length < 4) {
            return UNKNOWN;
        }
        float[] combined = new float[Math.min(envTail.length, envHead.length)];
        for (int i = 0; i < combined.length; i++) {
            combined[i] = envTail[i] + envHead[i];
        }
        float lowClash = lowClash(lowTail, envTail, lowHead, envHead);
        float drop = prominence(combined);
        float decay = slopeDbPerSec(envTail, FRAME_HZ);
        float onset = onsetFactor(envHead, FRAME_HZ * 2.0);
        float overlap = (float) Math.max(0.0, Math.min(tailSecs, headSecs));
        boolean buildUp = decay > -0.5f && rises(envTail);

        double ra = mean(lowTail) / Math.max(mean(envTail), 1e-9);
        double rb = mean(lowHead) / Math.max(mean(envHead), 1e-9);
        float lowRatio = (float) Math.max(0.0, Math.min(1.0, (ra + rb) / 2.0));
        float onsetStrength = onsetFactor(envTail, FRAME_HZ * 2.0);
        return new MaterialProfile(true, lowClash, drop, decay, onset, overlap, buildUp,
                lowRatio, onsetStrength);
    }

    static float[] envelope(float[] pcm, int hop) {
        int frames = pcm.length / hop;
        float[] out = new float[Math.max(0, frames)];
        for (int f = 0; f < out.length; f++) {
            double sum = 0.0;
            for (int i = 0; i < hop; i++) {
                float v = pcm[f * hop + i];
                sum += (double) v * v;
            }
            out[f] = (float) Math.sqrt(sum / hop);
        }
        return out;
    }

    static float[] lowEnvelope(float[] pcm, int hop, int rate) {
        double a = 1.0 - Math.exp(-2.0 * Math.PI * LOW_HZ / rate);
        float[] lp = new float[pcm.length];
        double y = 0.0;
        for (int i = 0; i < pcm.length; i++) {
            y += a * (pcm[i] - y);
            lp[i] = (float) y;
        }
        return envelope(lp, hop);
    }

    static float lowClash(float[] lowA, float[] allA, float[] lowB, float[] allB) {
        double ra = mean(lowA) / Math.max(mean(allA), 1e-9);
        double rb = mean(lowB) / Math.max(mean(allB), 1e-9);
        return (float) Math.max(0.0, Math.min(1.0, Math.min(ra, rb)));
    }

    static double mean(float[] v) {
        if (v == null || v.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (float x : v) {
            sum += x;
        }
        return sum / v.length;
    }

    static float meanProduct(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        if (n == 0) {
            return 0f;
        }
        double ma = 0.0;
        double mb = 0.0;
        for (int i = 0; i < n; i++) {
            ma += a[i];
            mb += b[i];
        }
        ma = Math.max(ma / n, 1e-9);
        mb = Math.max(mb / n, 1e-9);
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += (a[i] / ma) * (b[i] / mb);
        }

        return (float) Math.max(0.0, Math.min(1.0, sum / n - 1.0));
    }

    static float prominence(float[] env) {
        if (env.length == 0) {
            return 0f;
        }
        double peak = 0.0;
        double sum = 0.0;
        for (float v : env) {
            peak = Math.max(peak, v);
            sum += v;
        }
        double mean = sum / env.length;
        return (float) Math.max(0.0, (peak - mean) / Math.max(peak + mean, 1e-9));
    }

    static float slopeDbPerSec(float[] env, double frameHz) {
        int n = env.length;
        if (n < 4) {
            return 0f;
        }
        double sx = 0.0;
        double sy = 0.0;
        double sxx = 0.0;
        double sxy = 0.0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = 20.0 * Math.log10(Math.max(env[i], 1e-6));
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
        }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-9) {
            return 0f;
        }
        double slopePerFrame = (n * sxy - sx * sy) / denom;
        return (float) (slopePerFrame * frameHz);
    }

    static float onsetFactor(float[] env, double frames) {
        int n = Math.min(env.length, Math.max(2, (int) Math.round(frames)));
        double peak = 0.0;
        double sum = 0.0;
        for (int i = 1; i < n; i++) {
            double d = Math.max(0.0, env[i] - env[i - 1]);
            peak = Math.max(peak, d);
            sum += d;
        }
        double mean = sum / Math.max(1, n - 1);
        return (float) Math.max(0.0, (peak - mean) / Math.max(peak + mean, 1e-9));
    }

    static boolean rises(float[] env) {
        int n = env.length;
        if (n < 8) {
            return false;
        }
        double first = 0.0;
        double second = 0.0;
        for (int i = 0; i < n / 2; i++) {
            first += env[i];
        }
        for (int i = n / 2; i < n; i++) {
            second += env[i];
        }
        first /= Math.max(1, n / 2);
        second /= Math.max(1, n - n / 2);
        return second > first * 1.15;
    }
}
