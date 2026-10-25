package dev.t1m3.qplayer.audio.studio;

public final class StereoParams {
    public final boolean enabled;
    public final double width;
    public final double bassMonoHz;
    public final double sideDelayMs;
    public final boolean channelSwap;
    public final double crossfeed;

    public StereoParams(
            boolean enabled,
            double width,
            double bassMonoHz,
            double sideDelayMs,
            boolean channelSwap,
            double crossfeed) {
        this.enabled = enabled;
        this.width = clamp(width, 0.0, 2.0);
        this.bassMonoHz = clamp(bassMonoHz, 0.0, 500.0);
        this.sideDelayMs = clamp(sideDelayMs, 0.0, 2.0);
        this.channelSwap = channelSwap;
        this.crossfeed = clamp(crossfeed, 0.0, 1.0);
    }

    public static StereoParams neutral() {
        return new StereoParams(false, 1.0, 0.0, 0.0, false, 0.0);
    }

    public boolean active() {
        if (!enabled) {
            return false;
        }
        return Math.abs(width - 1.0) > 1e-9
                || bassMonoHz > 0.0
                || sideDelayMs > 1e-9
                || channelSwap
                || crossfeed > 1e-9;
    }

    public int sideDelayFrames(int rate) {
        return (int) Math.round(sideDelayMs / 1000.0 * Math.max(1, rate));
    }

    public int latencyFrames(int rate) {
        return active() ? sideDelayFrames(rate) : 0;
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public String toString() {
        return String.format(
                java.util.Locale.ROOT,
                "StereoParams{%s width %.2f, bass mono %.0f Hz, side delay %.2f ms, swap %s, "
                        + "crossfeed %.2f}",
                enabled ? "on" : "off",
                Double.valueOf(width),
                Double.valueOf(bassMonoHz),
                Double.valueOf(sideDelayMs),
                channelSwap ? "yes" : "no",
                Double.valueOf(crossfeed));
    }
}
