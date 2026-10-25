package dev.t1m3.qplayer.audio.mix;

public final class BeatRefine {

    public static final double SEARCH_SECS = 0.04;

    static final double WIN_SECS = 0.002;

    static final double PEAK_RATIO = 1.5;

    static final double ISOLATION_RATIO = 0.6;

    private BeatRefine() {
    }

    public static int refineFrame(float[] mono, int rate, int approxFrame) {
        if (mono == null || rate <= 0 || mono.length == 0) {
            return approxFrame;
        }
        int win = Math.max(1, (int) Math.round(rate * WIN_SECS));
        int half = Math.max(win, (int) Math.round(rate * SEARCH_SECS));
        int from = Math.max(win, approxFrame - half);
        int to = Math.min(mono.length - 1, approxFrame + half);
        if (to - from < win * 2) {
            return approxFrame;
        }

        int best = -1;
        double bestVal = 0.0;
        double sum = 0.0;
        int n = 0;
        for (int at = from; at <= to; at++) {
            double now = energy(mono, at - win, at);
            double prev = energy(mono, at - 2 * win, at - win);
            double onset = Math.max(0.0, now - prev);
            if (onset > bestVal) {
                bestVal = onset;
                best = at;
            }
            sum += onset;
            n++;
        }
        if (best < 0 || n == 0) {
            return approxFrame;
        }
        double mean = sum / n;
        if (mean <= 0.0 || bestVal < mean * PEAK_RATIO) {
            return approxFrame;
        }

        double runnerUp = 0.0;
        for (int at = from; at <= to; at++) {
            if (Math.abs(at - best) <= win) {
                continue;
            }
            double now = energy(mono, at - win, at);
            double prev = energy(mono, at - 2 * win, at - win);
            runnerUp = Math.max(runnerUp, Math.max(0.0, now - prev));
        }
        if (runnerUp > bestVal * ISOLATION_RATIO) {
            return approxFrame;
        }

        int leading = best;
        for (int at = best; at >= from; at--) {
            double now = energy(mono, at - win, at);
            double prev = energy(mono, at - 2 * win, at - win);
            if (Math.max(0.0, now - prev) < bestVal * 0.25) {
                break;
            }
            leading = at;
        }
        return leading - win;
    }

    private static double energy(float[] pcm, int from, int to) {
        int a = Math.max(0, from);
        int b = Math.min(pcm.length, to);
        if (b <= a) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = a; i < b; i++) {
            sum += (double) pcm[i] * pcm[i];
        }
        return sum / (b - a);
    }
}
