package dev.t1m3.qplayer.audio.mix;

public final class TempoRamp {

    public static final double SLEW_MAX = 0.025;

    public static final double TEMPO_JND = 0.027;

    public static final double MIN_WINDOW_SECS = 1.0;

    public final boolean active;

    public final double ratio;

    public final double userWindowSecs;

    public final double windowSecs;

    public final double startSpeed;

    public final String note;

    public final boolean halved;

    private TempoRamp(boolean active, double ratio, double userWindowSecs, double windowSecs,
                      double startSpeed, String note) {
        this(active, ratio, userWindowSecs, windowSecs, startSpeed, note, false);
    }

    private TempoRamp(boolean active, double ratio, double userWindowSecs, double windowSecs,
                      double startSpeed, String note, boolean halved) {
        this.active = active;
        this.ratio = ratio;
        this.userWindowSecs = userWindowSecs;
        this.windowSecs = windowSecs;
        this.startSpeed = startSpeed;
        this.note = note;
        this.halved = halved;
    }

    public static TempoRamp off(double userWindowSecs, String why) {
        return new TempoRamp(false, 1.0, userWindowSecs, userWindowSecs, 1.0, why);
    }

    public static TempoRamp derive(double ratio, double userWindowSecs) {
        double window = userWindowSecs > 0.0 ? userWindowSecs : MIN_WINDOW_SECS;
        if (!(ratio > 0.0)) {
            return off(window, "BPM cannot be computed, no ramp down this time");
        }

        double stop = ratio * (1.0 + TEMPO_JND);
        if (stop >= 1.0) {
            return off(window, "the beat-length difference on the two sides is " + pct(1.0 - ratio) + " already within the just-noticeable difference, no ramp down this time");
        }

        double cap = Math.abs(1.0 - ratio) / SLEW_MAX;
        double derived = Math.max(MIN_WINDOW_SECS, Math.min(window, cap));
        TempoRamp probe = new TempoRamp(true, ratio, window, derived, 1.0, "");
        return new TempoRamp(true, ratio, window, derived, 1.0,
                "gradual ramp down 1.0000->" + f4(probe.endSpeedFor(derived)) + "×"
                        + " (window " + f2(derived) + "s / mean " + f4(probe.meanSpeedFor(derived))
                        + "x; decrease only: BPM ratio " + f4(ratio) + ", user value " + f2(window) + "s）");
    }

    public static TempoRamp deriveHalved(double ratio, double userWindowSecs) {
        double window = userWindowSecs > 0.0 ? userWindowSecs : MIN_WINDOW_SECS;
        if (!(ratio > 0.0)) {
            return off(window, "BPM cannot be computed, no tempo change this time");
        }

        if (Math.abs(1.0 - ratio) <= TEMPO_JND) {
            return off(window, "the beat-length difference on the two sides is " + pct(1.0 - ratio) + " already within the just-noticeable difference, no tempo change this time");
        }
        double speedA = Math.sqrt(ratio);

        double cap = Math.abs(1.0 - ratio) / SLEW_MAX;
        double derived = Math.max(MIN_WINDOW_SECS, Math.min(window, cap));
        TempoRamp probe = new TempoRamp(true, ratio, window, derived, 1.0, "", true);
        double endA = probe.endSpeedFor(derived);
        return new TempoRamp(true, ratio, window, derived, 1.0,
                "gradual ramp down 1.0000->" + f4(endA) + "×" + f4(1.0 / endA) + "×）"
                        + " (window " + f2(derived) + "x; decrease only: BPM ratio " + f4(ratio)
                        + ", user value " + f2(window) + "s、" + pct(CONVERGE_FRAC)
                        + "BPM cannot be computed, no tempo change this time", true);
    }

    public static final double CONVERGE_FRAC = 0.25;

    public double endSpeedFor(double windowSecs) {
        if (!active) {
            return 1.0;
        }
        double w = Math.max(0.0, windowSecs);

        double stop = halved ? Math.sqrt(ratio) : ratio * (1.0 + TEMPO_JND);

        double reachable = 1.0 - SLEW_MAX * w;
        return Math.min(1.0, Math.max(stop, reachable));
    }

    public double meanSpeedFor(double windowSecs) {
        return active ? (startSpeed + endSpeedFor(windowSecs)) / 2.0 : 1.0;
    }

    public double speedAt(double windowSecs, double uSecs) {
        if (!active) {
            return 1.0;
        }
        double w = Math.max(1e-9, windowSecs);
        double u = Math.min(Math.max(0.0, uSecs), w);
        return startSpeed + (endSpeedFor(windowSecs) - startSpeed) * (u / w);
    }

    public double sourceSecsAt(double windowSecs, double uSecs) {
        if (!active) {
            return uSecs;
        }
        double w = Math.max(1e-9, windowSecs);
        double u = Math.min(Math.max(0.0, uSecs), w);
        double k = (endSpeedFor(windowSecs) - startSpeed) / w;
        return startSpeed * u + 0.5 * k * u * u;
    }

    public double absorbedFraction(double windowSecs) {
        if (!active) {
            return 0.0;
        }
        return (1.0 - endSpeedFor(windowSecs)) / Math.max(1e-9, 1.0 - ratio);
    }

    private static String f2(double v) {
        return String.format(java.util.Locale.US, "%.2f", Double.valueOf(v));
    }

    private static String f4(double v) {
        return String.format(java.util.Locale.US, "%.4f", Double.valueOf(v));
    }

    private static String pct(double v) {
        return String.format(java.util.Locale.US, "%.2f%%", Double.valueOf(v * 100.0));
    }

    @Override
    public String toString() {
        return "TempoRamp{" + (active
                ? ("1.0000→" + f4(endSpeedFor(windowSecs)) + "x - window " + f2(windowSecs) + "s")
                : "off") + (note.isEmpty() ? "" : " · " + note) + "}";
    }
}
